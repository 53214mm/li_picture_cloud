import assert from 'node:assert/strict'
import test from 'node:test'
import { FixtureSession, requireFixtureEnvironment } from './http-fixture.js'

// HTTP/H2/demo integration evidence only. No Playwright/browser, UI claims,
// external model, COS upload, production credential, or default npm test hook.
// The owning runner starts a fresh isolated backend and sets both guard values.
const FIXTURE_PASSWORD = 'LocalUser123!'
const DEMO_REPLY = '我在听。你可以和我聊聊图片，或者从图库里挑一张喂给我，我会慢慢记住我们的经历。'
const MOOD_AXES = ['energy', 'joy', 'loneliness', 'inspiration', 'irritation']

function decimalId(value) {
  assert.equal(typeof value, 'string', 'Long identifiers must arrive as decimal strings')
  assert.match(value, /^[1-9]\d*$/)
  return value
}

function assertDemoHome(home) {
  assert.equal(home.nutrition.policy, 'DEMO_ONLY', 'Refusing any non-demo nutrition policy')
  assert.equal(home.chatPolicy, 'DEMO_ONLY', 'Refusing any non-demo chat policy')
}

function assertMood(home, expected) {
  for (const axis of MOOD_AXES) assert.equal(home.mood[axis], expected, `Home mood.${axis}`)
}

// H2 stores these timestamps at millisecond precision; mutation responses can
// retain Instant nanoseconds. Compare persisted time at the actual storage
// precision while keeping every non-time field exact.
function assertPersistedRecord(actual, expected) {
  const fields = ['createdTime', 'updatedTime'].filter(field => field in expected)
  const withoutTimes = value => Object.fromEntries(Object.entries(value)
    .filter(([field]) => !fields.includes(field)))
  assert.deepEqual(withoutTimes(actual), withoutTimes(expected))
  for (const field of fields) {
    const actualMillis = Date.parse(actual[field])
    const expectedMillis = Date.parse(expected[field])
    assert.ok(Number.isFinite(actualMillis) && Number.isFinite(expectedMillis), `${field} must be a valid timestamp`)
    assert.equal(actualMillis, expectedMillis, `${field} must persist at millisecond precision`)
  }
}

function pictureMetadata(picture) {
  const { id, name, category, tags, url, originalUrl, spaceId, userId } = picture
  return { id, name, category, tags, url, originalUrl, spaceId, userId }
}

test('real local fixture API story (HTTP only; not browser acceptance)', { timeout: 120_000 }, async t => {
  // This must execute before creating a session or making any HTTP request.
  const base = requireFixtureEnvironment()
  const anonymous = new FixtureSession(base)
  let owner = new FixtureSession(base)
  const other = new FixtureSession(base)
  const sessions = [owner, other]
  let initialContract
  let grownHome
  let firstFeed

  t.after(async () => {
    // Only these test-created sessions are touched; the runner owns H2 disposal.
    for (const session of sessions) {
      await session.json('/user/logout', { method: 'POST' })
    }
  })

  async function login(session, account, id) {
    const user = await session.json('/user/login', {
      method: 'POST', body: { userAccount: account, userPassword: FIXTURE_PASSWORD }
    })
    assert.equal(user.id, id)
    assert.equal(user.userAccount, account)
    assert.equal(user.userRole, 'user')
  }

  // Stop dependent mutations after the first failed stage. Do not turn a failed
  // prerequisite into misleading cascade failures or continue a half-known story.
  async function stage(name, work) {
    let passed = false
    await t.test(name, async () => {
      await work()
      passed = true
    })
    assert.ok(passed, `Stopped the fixture story after: ${name}`)
  }

  await stage('login cookies isolate anonymous and two fixture users; require fresh demo fixtures', async () => {
    await anonymous.json('/user/current', { status: 401, code: 40100 })
    await anonymous.json('/companion/me', { status: 401, code: 40100 })
    await login(owner, 'companion_e2e', '7')
    await login(other, 'recipe_e2e', '9')
    assert.equal((await owner.json('/user/current')).id, '7')
    assert.equal((await other.json('/user/current')).id, '9')
    await anonymous.json('/user/current', { status: 401, code: 40100 })
    for (const session of [owner, other]) {
      const home = await session.json('/companion/me')
      assert.equal(home.nutrition.policy, 'DEMO_ONLY')
      assert.equal(home.chatPolicy, null, 'Unawakened Home has no chat policy view')
      assert.equal(home.companion, null, 'Restart the isolated H2 backend before rerunning this story')
      assert.deepEqual(home.recentGrowth, [])
    }
    for (const [session, id, spaceId, userId, name] of [
      [owner, '102', '10', '7', '旅行样片'],
      [owner, '103', '10', '7', '花园样片'],
      [other, '104', '11', '9', '旅行样片']
    ]) {
      const picture = await session.json(`/picture/get/vo?id=${id}`)
      assert.equal(picture.id, id)
      assert.equal(picture.spaceId, spaceId)
      assert.equal(picture.userId, userId)
      assert.equal(picture.name, name)
      assert.equal(picture.introduction, '仅供端到端测试')
      assert.match(picture.url, /^\/images\/mosaic\//)
    }
  })

  await stage('awaken is idempotent; authoritative Home is neutral and contract starts disabled', async () => {
    const home = await owner.json('/companion/awaken', { method: 'POST' })
    assertDemoHome(home)
    decimalId(home.companion.id)
    assert.equal(home.companion.lifeExperience, '0')
    assert.equal(home.companion.revision, '0')
    assertMood(home, 0)
    assert.equal(home.relationship, null)
    assert.deepEqual(home.recentGrowth, [])
    assert.deepEqual((await owner.json('/companion/awaken', { method: 'POST' })).companion, home.companion)
    const otherHome = await other.json('/companion/awaken', { method: 'POST' })
    assertDemoHome(otherHome)
    assert.notEqual(decimalId(otherHome.companion.id), home.companion.id)
    assert.equal((await owner.json('/companion/me?userId=9')).companion.id, home.companion.id)
    assert.equal((await other.json('/companion/me?userId=7')).companion.id, otherHome.companion.id)
    initialContract = await owner.json('/companion/contract')
    assert.deepEqual(initialContract, {
      active: false, quietStart: '23:00:00', quietEnd: '08:00:00', maxFrequencyHours: 72, revision: '0'
    })
    assert.equal(await owner.json('/companion/proposals/active'), null)
    assert.deepEqual((await owner.json('/companion/chat/history')).records, [])
    assert.deepEqual((await other.json('/companion/chat/history')).records, [])
  })

  await stage('same feed key replays one receipt with no double growth; Home mood and relationship are authoritative', async () => {
    const body = { pictureId: '102', idempotencyKey: 'r14-api-fixture-feed-102' }
    firstFeed = await owner.json('/companion/feed', { method: 'POST', body })
    const replay = await owner.json('/companion/feed', { method: 'POST', body })
    assert.deepEqual(replay, firstFeed, 'An idempotent replay must return the original result and receipt')
    assert.equal(firstFeed.outcome, 'GROWN')
    decimalId(firstFeed.growth.id)
    assert.ok(firstFeed.correlationId)
    assert.equal(firstFeed.growth.sourcePictureId, '102')
    assert.equal(firstFeed.growth.lifeExperienceDelta, '42')
    assert.equal(firstFeed.growth.nutritionMode, 'DEMO_DETERMINISTIC')
    assert.equal(firstFeed.growth.contentUnderstood, false)
    assert.equal(firstFeed.growth.providerCode, 'internal')
    assert.equal(firstFeed.growth.modelCode, 'demo-v1')
    grownHome = await owner.json('/companion/me')
    assertDemoHome(grownHome)
    assert.equal(grownHome.companion.lifeExperience, '42')
    assert.equal(grownHome.companion.revision, '1')
    assert.deepEqual(grownHome.companion, firstFeed.companion)
    assert.deepEqual(grownHome.recentGrowth, [firstFeed.growth])
    assertMood(grownHome, 2)
    assert.deepEqual(grownHome.relationship, { familiarity: 5, trust: 2, closeness: 1, tacit: 1, recentFeedback: 5 })
    const skills = Object.fromEntries(grownHome.companion.skills.map(skill => [skill.code, skill.experience]))
    assert.equal(skills.IMAGE_OBSERVATION, '18')
    assert.equal(skills.STORY_CREATION, '12')
    assert.equal(grownHome.companion.traits.curiosity, 0.6)
    assert.equal(await owner.json('/companion/proposals/active'), null, 'Feeding must not enable autonomy')
    await other.json('/companion/feed', {
      method: 'POST', body: { pictureId: '102', idempotencyKey: 'r14-api-other-denied-102' },
      status: 403, code: 40101
    })
    const otherHome = await other.json('/companion/me')
    assert.equal(otherHome.companion.lifeExperience, '0')
    assert.equal(otherHome.companion.revision, '0')
    assert.deepEqual(otherHome.recentGrowth, [])
    assert.equal(otherHome.relationship, null)
    assert.deepEqual((await owner.json('/companion/me')).companion, grownHome.companion)
  })

  await stage('SSE delivers a complete explicit done; a new login reads persisted ordered history', async () => {
    const stream = await owner.chat('你好呀')
    assert.equal(stream.done, true)
    assert.equal(stream.text, DEMO_REPLY)
    assert.deepEqual(stream.chunks, [DEMO_REPLY])
    const history = await owner.json('/companion/chat/history')
    assert.equal(history.records.length, 2)
    assert.deepEqual(history.records.map(({ role, content }) => ({ role, content })), [
      { role: 'USER', content: '你好呀' }, { role: 'COMPANION', content: DEMO_REPLY }
    ])
    for (const message of history.records) decimalId(message.id)
    assert.notEqual(history.records[0].id, history.records[1].id)
    assert.equal(history.records[1].modelProvider, 'internal')
    assert.equal(history.records[1].modelCode, 'demo-v1')
    await owner.json('/user/logout', { method: 'POST' })
    await owner.json('/user/current', { status: 401, code: 40100 })
    owner = new FixtureSession(base)
    sessions.push(owner)
    await login(owner, 'companion_e2e', '7')
    assert.deepEqual(await owner.json('/companion/chat/history'), history)
    assert.deepEqual((await other.json('/companion/chat/history')).records, [])
    assert.deepEqual((await owner.json('/companion/me')).recentGrowth, [firstFeed.growth])
  })

  await stage('memory ownership and confirm/correct/dismiss/confirm/delete lifecycle persist', async () => {
    const { records } = await owner.json('/companion/memories')
    assert.equal(records.length, 1)
    const memory = records[0]
    const id = decimalId(memory.id)
    assert.equal(memory.status, 'PENDING')
    assert.equal(memory.sourcePictureId, '102')
    assert.ok(memory.content)
    await other.json(`/companion/memories/${id}/confirm`, { method: 'POST', status: 404, code: 40400 })
    assert.deepEqual((await other.json('/companion/memories')).records, [])
    assert.equal((await owner.json(`/companion/memories/${id}/confirm`, { method: 'POST' })).status, 'CONFIRMED')
    const correction = '伙伴重新想起：那是安静的清晨。'
    const corrected = await owner.json(`/companion/memories/${id}/correct`, { method: 'POST', body: { content: correction } })
    assert.equal(corrected.content, correction)
    assert.equal(corrected.originalContent, memory.content)
    assert.equal(corrected.status, 'CONFIRMED')
    const persisted = (await owner.json('/companion/memories')).records
    assert.equal(persisted.length, 1)
    assertPersistedRecord(persisted[0], corrected)
    assert.equal((await owner.json(`/companion/memories/${id}/dismiss`, { method: 'POST' })).status, 'DISMISSED')
    assert.equal((await owner.json(`/companion/memories/${id}/confirm`, { method: 'POST' })).status, 'CONFIRMED')
    const deleted = await owner.json(`/companion/memories/${id}`, { method: 'DELETE' })
    assert.equal(deleted.status, 'DELETED')
    assert.equal(deleted.content, null)
    assert.equal(deleted.originalContent, null)
    assert.deepEqual((await owner.json('/companion/memories')).records, [])
  })

  await stage('opt-in contract gates a weekly proposal; ownership and terminal acceptance are enforced', async () => {
    assert.equal((await owner.json('/companion/contract')).active, false)
    assert.equal(await owner.json('/companion/proposals/active'), null)
    try {
      const enabled = await owner.json('/companion/contract', {
        method: 'PUT', body: { active: true, quietStart: '00:00', quietEnd: '00:00', maxFrequencyHours: 72 }
      })
      assert.equal(enabled.active, true)
      const proposal = await owner.json('/companion/proposals/active')
      const id = decimalId(proposal.id)
      assert.equal(proposal.status, 'PENDING')
      assert.equal(proposal.opportunityType, 'WEEKLY_REVIEW')
      assert.equal(proposal.content, '这周你喂了我 1 次。想听我讲一段我们的故事吗？')
      assertPersistedRecord(await owner.json('/companion/proposals/active'), proposal)
      await other.json(`/companion/proposals/${id}/accept`, { method: 'POST', status: 404, code: 40400 })
      assert.equal((await owner.json(`/companion/proposals/${id}/accept`, { method: 'POST' })).status, 'DONE')
      await owner.json(`/companion/proposals/${id}/accept`, { method: 'POST', status: 400, code: 40000 })
      assert.equal(await owner.json('/companion/proposals/active'), null)
    } finally {
      const { active, quietStart, quietEnd, maxFrequencyHours } = initialContract
      await owner.json('/companion/contract', { method: 'PUT', body: { active, quietStart, quietEnd, maxFrequencyHours } })
    }
    assert.equal((await owner.json('/companion/contract')).active, false)
    assert.equal((await other.json('/companion/contract')).active, false)
    assert.equal(await owner.json('/companion/proposals/active'), null)
    assert.equal(await other.json('/companion/proposals/active'), null)
  })

  await stage('batch accepts exact fixture string IDs and changes only the selected owned picture (small IDs)', async () => {
    // Existing SQL fixtures have small IDs. This proves real endpoint selection
    // and permissions, not >2^53 precision or the frontend batch serializer.
    const before = await owner.json('/picture/get/vo?id=102')
    const untouched = pictureMetadata(await owner.json('/picture/get/vo?id=103'))
    try {
      await other.json('/picture/edit/batch', {
        method: 'POST', body: { spaceId: '10', pictureIdList: ['102'], category: 'must-not-write' },
        status: 403, code: 40101
      })
      assert.equal(await owner.json('/picture/edit/batch', {
        method: 'POST', body: { spaceId: '10', pictureIdList: ['102'], category: 'API fixture check', tags: ['api-fixture'], nameRule: 'API fixture {序号}' }
      }), true)
      const changed = await owner.json('/picture/get/vo?id=102')
      assert.equal(changed.id, '102')
      assert.equal(changed.spaceId, '10')
      assert.equal(changed.name, 'API fixture 1')
      assert.equal(changed.category, 'API fixture check')
      assert.deepEqual(changed.tags, ['api-fixture'])
      assert.equal(changed.url, before.url)
      assert.equal(changed.originalUrl, before.originalUrl)
      assert.deepEqual(pictureMetadata(await owner.json('/picture/get/vo?id=103')), untouched)
    } finally {
      await owner.json('/picture/edit/batch', {
        method: 'POST', body: { spaceId: '10', pictureIdList: ['102'], category: before.category, tags: before.tags, nameRule: before.name }
      })
    }
    assert.deepEqual(pictureMetadata(await owner.json('/picture/get/vo?id=102')), pictureMetadata(before))
  })

  await stage('logout invalidates only the selected session; the other account remains isolated', async () => {
    assert.equal(await owner.json('/user/logout', { method: 'POST' }), true)
    await owner.json('/user/current', { status: 401, code: 40100 })
    await owner.json('/companion/me', { status: 401, code: 40100 })
    assert.equal((await other.json('/user/current')).id, '9')
    assert.equal((await other.json('/companion/me')).companion.lifeExperience, '0')
  })
})
