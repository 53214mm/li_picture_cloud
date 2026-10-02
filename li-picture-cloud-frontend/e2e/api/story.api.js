import assert from 'node:assert/strict'
import test from 'node:test'
import { FixtureSession, requireFixtureEnvironment } from './http-fixture.js'

// Real HTTP/controller/service/H2 coverage with the owning launcher's language
// stub. This is not browser, real-model, or lineage-table acceptance evidence.
const PASSWORD = 'LocalUser123!'
const STORY_TEXT = '在晨光里，伙伴轻轻翻开了这些画面，把安静的清晨讲成了一段小小的故事。'
const CREATE = {
  pictureIds: ['102', '103'], idempotencyKey: '14141414-1111-4111-8111-000000000007'
}
const OPERATION_ERROR = { status: 500, code: 50001 }
const FORBIDDEN = { status: 403, code: 40300 }

function decimalId(value) {
  assert.equal(typeof value, 'string', 'Long identifiers must remain exact decimal strings')
  assert.match(value, /^[1-9]\d*$/)
  return value
}

function persisted(record) {
  const copy = { ...record }
  // Java mutation views retain Instant nanoseconds; Date-backed H2 rows retain
  // milliseconds. All non-time fields stay exact, including string Long IDs.
  for (const field of ['createdTime', 'updatedTime']) {
    const millis = Date.parse(copy[field])
    assert.ok(Number.isFinite(millis), `${field} must be a valid timestamp`)
    copy[field] = millis
  }
  return copy
}

function pictureContent({ id, name, introduction, category, tags, url, originalUrl, spaceId, userId }) {
  return { id, name, introduction, category, tags, url, originalUrl, spaceId, userId }
}

test('Story lifecycle through the isolated HTTP API (not browser acceptance)', { timeout: 120_000 }, async t => {
  const base = requireFixtureEnvironment() // Must precede the first HTTP request.
  const anonymous = new FixtureSession(base)
  let owner = new FixtureSession(base)
  const other = new FixtureSession(base)
  const sessions = [owner, other]
  let task
  let originalPictures
  let otherStories
  let originalUsage

  t.after(async () => {
    const outcomes = await Promise.allSettled(sessions.map(session =>
      session.json('/user/logout', { method: 'POST' })))
    for (const outcome of outcomes) assert.equal(outcome.status, 'fulfilled', 'Fixture logout failed')
  })

  async function login(session, account, id) {
    const user = await session.json('/user/login', {
      method: 'POST', body: { userAccount: account, userPassword: PASSWORD }
    })
    assert.equal(user.id, id)
    assert.equal(user.userAccount, account)
    assert.equal(user.userRole, 'user')
  }

  async function stage(name, work) {
    let passed = false
    await t.test(name, async () => { await work(); passed = true })
    assert.ok(passed, `Stopped dependent Story mutations after: ${name}`)
  }

  async function assertTask(expected) {
    const tasks = await owner.json('/creation/story')
    assert.equal(tasks.length, 1, 'Repeated actions must not create another Story task')
    assert.deepEqual(persisted(tasks[0]), persisted(expected))
  }

  async function assertModelCalls(count) {
    const usage = await owner.json('/model/usage')
    const originalIds = new Set(originalUsage.map(item => item.id))
    const added = usage.filter(item => !originalIds.has(item.id))
    assert.equal(usage.length, originalUsage.length + count)
    assert.equal(added.length, count, 'Only outline and draft may invoke the stub model')
    for (const item of added) {
      decimalId(item.id)
      assert.equal(item.task, 'LANGUAGE_AGENT')
      assert.equal(item.connectionId, null)
      assert.equal(item.provider, 'DASHSCOPE')
      assert.equal(item.modelCode, 'qwen-max')
      assert.equal(item.costSource, 'PLATFORM')
      assert.equal(item.success, true)
      assert.equal(item.safeErrorCode, null)
    }
  }

  await stage('authenticated fixture ownership, platform stub route, and original pictures', async () => {
    await anonymous.json('/creation/story', { status: 401, code: 40100 })
    await anonymous.json('/creation/story', {
      method: 'POST', body: CREATE, status: 401, code: 40100
    })
    await login(owner, 'companion_e2e', '7')
    await login(other, 'recipe_e2e', '9')
    for (const session of [owner, other]) {
      const home = await session.json('/companion/me')
      assert.ok(home.companion, 'The companion HTTP stage must awaken both fixture actors first')
      assert.equal(home.chatPolicy, 'DEMO_ONLY')
      assert.equal(home.nutrition.policy, 'DEMO_ONLY')
      assert.equal((await session.json('/companion/contract')).active, false)
    }
    const routes = await owner.json('/model/routing')
    assert.ok(routes.every(route => route.task !== 'LANGUAGE_AGENT' || route.connectionId === null),
      'The gateway stage must remove its user 7 BYOK route before Story generation')
    assert.deepEqual(await owner.json('/creation/story'), [], 'Story needs a fresh owner fixture')
    otherStories = await other.json('/creation/story')
    originalUsage = await owner.json('/model/usage')
    originalPictures = []
    for (const [id, category] of [['102', '旅行'], ['103', '花园']]) {
      const picture = await owner.json(`/picture/get/vo?id=${id}`)
      assert.equal(picture.id, id)
      assert.equal(picture.userId, '7')
      assert.equal(picture.spaceId, '10')
      assert.equal(picture.category, category)
      assert.equal(picture.introduction, '仅供端到端测试')
      assert.match(picture.url, /^\/images\/mosaic\//)
      originalPictures.push(pictureContent(picture))
    }
  })

  await stage('create replays the same key once; unauthorized pictures and premature transitions fail', async () => {
    await owner.json('/creation/story', {
      method: 'POST', body: { ...CREATE, pictureIds: [] }, status: 400, code: 40000
    })
    await other.json('/creation/story', { method: 'POST', body: CREATE, status: 403, code: 40101 })
    assert.deepEqual(await owner.json('/creation/story'), [])
    task = await owner.json('/creation/story', { method: 'POST', body: CREATE })
    decimalId(task.id)
    assert.deepEqual(Object.keys(task).sort(), [
      'id', 'kind', 'status', 'sourcePictureIds', 'outlineText', 'draftText', 'resultText',
      'modelConnectionId', 'idempotencyKey', 'revision', 'createdTime', 'updatedTime'
    ].sort())
    assert.equal(task.kind, 'STORY_DRAFT')
    assert.equal(task.status, 'PENDING')
    assert.equal(task.revision, '0')
    assert.equal(task.idempotencyKey, CREATE.idempotencyKey)
    assert.deepEqual(task.sourcePictureIds, CREATE.pictureIds)
    for (const field of ['outlineText', 'draftText', 'resultText', 'modelConnectionId']) {
      assert.equal(task[field], null)
    }
    const replay = await owner.json('/creation/story', { method: 'POST', body: CREATE })
    assert.deepEqual(persisted(replay), persisted(task))
    for (const operation of ['draft', 'save']) {
      await owner.json(`/creation/story/${task.id}/${operation}`, { method: 'POST', ...OPERATION_ERROR })
    }
    for (const operation of ['outline', 'confirm-outline', 'draft', 'save']) {
      await other.json(`/creation/story/${task.id}/${operation}`, { method: 'POST', ...FORBIDDEN })
    }
    await assertTask(task)
    await assertModelCalls(0)
    assert.deepEqual(await other.json('/creation/story'), otherStories)
  })

  await stage('outline persists awaiting confirmation; repeated generation does not call the model', async () => {
    task = await owner.json(`/creation/story/${task.id}/outline`, { method: 'POST' })
    assert.equal(task.status, 'AWAITING_CONFIRM')
    assert.equal(task.revision, '2')
    assert.equal(task.outlineText, STORY_TEXT)
    assert.equal(task.draftText, null)
    assert.equal(task.resultText, null)
    assert.equal(task.modelConnectionId, null)
    await owner.json(`/creation/story/${task.id}/outline`, { method: 'POST', ...OPERATION_ERROR })
    await owner.json(`/creation/story/${task.id}/save`, { method: 'POST', ...OPERATION_ERROR })
    await other.json(`/creation/story/${task.id}/draft`, { method: 'POST', ...FORBIDDEN })
    await assertTask(task)
    await assertModelCalls(1)
  })

  await stage('draft confirms the outline once and waits for an explicit save', async () => {
    // Match the shipped client: /draft performs outline confirmation itself.
    // Calling /confirm-outline before it would leave the task in DRAFTING.
    task = await owner.json(`/creation/story/${task.id}/draft`, { method: 'POST' })
    assert.equal(task.status, 'AWAITING_CONFIRM')
    assert.equal(task.revision, '4')
    assert.equal(task.outlineText, STORY_TEXT)
    assert.equal(task.draftText, STORY_TEXT)
    assert.equal(task.resultText, null)
    await owner.json(`/creation/story/${task.id}/draft`, { method: 'POST', ...OPERATION_ERROR })
    await other.json(`/creation/story/${task.id}/save`, { method: 'POST', ...FORBIDDEN })
    await assertTask(task)
    await assertModelCalls(2)
  })

  await stage('save is terminal, create-key replay stays saved, and a new login reads unchanged sources', async () => {
    task = await owner.json(`/creation/story/${task.id}/save`, { method: 'POST' })
    assert.equal(task.status, 'SAVED')
    assert.equal(task.revision, '6')
    assert.equal(task.resultText, STORY_TEXT)
    assert.deepEqual(task.sourcePictureIds, CREATE.pictureIds)
    for (const operation of ['save', 'outline', 'draft']) {
      await owner.json(`/creation/story/${task.id}/${operation}`, { method: 'POST', ...OPERATION_ERROR })
    }
    const replay = await owner.json('/creation/story', { method: 'POST', body: CREATE })
    assert.deepEqual(persisted(replay), persisted(task))
    await assertTask(task)
    await assertModelCalls(2)
    await owner.json('/user/logout', { method: 'POST' })
    await owner.json('/creation/story', { status: 401, code: 40100 })
    owner = new FixtureSession(base)
    sessions.push(owner)
    await login(owner, 'companion_e2e', '7')
    await assertTask(task)
    await assertModelCalls(2)
    assert.deepEqual(await other.json('/creation/story'), otherStories)
    for (const original of originalPictures) {
      assert.deepEqual(pictureContent(await owner.json(`/picture/get/vo?id=${original.id}`)), original,
        'Saving a Story must not overwrite its source pictures')
    }
  })
})
