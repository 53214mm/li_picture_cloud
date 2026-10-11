import assert from 'node:assert/strict'
import test from 'node:test'
import { FixtureSession, requireFixtureEnvironment } from './http-fixture.js'

// HTTP-only integration with fixture actors 9/7. The owner harness provides all
// external stubs. Recipe confirmation creates a PENDING Story, never generates it.
const PASSWORD = 'LocalUser123!'
const OPERATION_ERROR = { status: 500, code: 50001 }
const FORBIDDEN = { status: 403, code: 40300 }
const definition = (when, conditions = [], capability = 'STORY_DRAFT') => ({
  when: { type: when }, conditions, then: { capability }
})

function decimalId(value) {
  assert.equal(typeof value, 'string', 'Long identifiers must remain exact decimal strings')
  assert.match(value, /^[1-9]\d*$/)
  return value
}

function storedTimes(record) {
  const copy = { ...record }
  // H2's Date-backed creation/trigger timestamps store milliseconds, whereas
  // immediate mutation views can contain Java Instant nanoseconds.
  for (const field of ['createdTime', 'triggeredTime']) {
    const millis = Date.parse(copy[field])
    assert.ok(Number.isFinite(millis), `${field} must be a valid timestamp`)
    copy[field] = millis
  }
  return copy
}

function assertPreview(execution, recipeId, version, pictureIds, matched, when = 'SIMILAR_STORY') {
  decimalId(execution.id)
  assert.equal(execution.recipeId, recipeId)
  assert.equal(execution.recipeVersion, version)
  assert.equal(execution.status, 'DRY_RUN')
  assert.deepEqual(execution.sourcePictureIds, pictureIds)
  assert.equal(execution.creationTaskId, null)
  assert.equal(execution.opportunityKey, null)
  assert.equal(execution.safeErrorCode, null)
  assert.deepEqual(JSON.parse(execution.quoteJson), { capability: 'STORY_DRAFT', platformUnits: '5' })
  assert.deepEqual(JSON.parse(execution.matchedJson), {
    when, conditions: [{ type: 'SOURCE_CATEGORY', matched }]
  })
  storedTimes(execution)
}

function assertPendingStory(task, pictureIds) {
  decimalId(task.id)
  assert.equal(task.kind, 'STORY_DRAFT')
  assert.equal(task.status, 'PENDING', 'Confirming a Recipe must not run the generated Story')
  assert.equal(task.revision, '0')
  assert.deepEqual(task.sourcePictureIds, pictureIds)
  assert.match(task.idempotencyKey, /^[0-9a-f-]{36}$/)
  for (const field of ['outlineText', 'draftText', 'resultText', 'modelConnectionId']) {
    assert.equal(task[field], null)
  }
}

test('Recipe version, snapshot, and real WHEN integration (HTTP only)', { timeout: 120_000 }, async t => {
  const base = requireFixtureEnvironment() // Guard before credentials or HTTP.
  const anonymous = new FixtureSession(base)
  let owner = new FixtureSession(base)
  const other = new FixtureSession(base)
  const sessions = [owner, other]
  const recipes = new Set()
  let originalContract
  let restoreContract = false
  let originalUsage
  let otherStories
  let travelId
  let weeklyId
  let travelDetail
  let firstTrial
  let unmatchedTrial
  let gardenTrial
  let confirmed
  let opportunity
  let pending
  let weeklyConfirmed

  t.after(async () => {
    const errors = []
    // Clean only this test's recipes/contract/sessions. H2 disposal belongs to
    // the runner; there is no database reset or deletion of unrelated state.
    if (restoreContract) {
      try { await restoreAutonomy() } catch (error) { errors.push(error) }
    }
    for (const id of recipes) {
      try { await owner.json(`/recipe/${id}`, { method: 'DELETE' }) } catch (error) { errors.push(error) }
    }
    for (const session of sessions) {
      try { await session.json('/user/logout', { method: 'POST' }) } catch (error) { errors.push(error) }
    }
    assert.deepEqual(errors, [], 'Fixture cleanup failed')
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
    assert.ok(passed, `Stopped dependent Recipe mutations after: ${name}`)
  }

  async function assertNoModelCalls() {
    assert.deepEqual(await owner.json('/model/usage'), originalUsage,
      'Preview, WHEN observation, and confirmation must not invoke a model')
  }

  const executePath = (recipeId, executionId) => `/recipe/${recipeId}/executions/${executionId}/execute`

  async function restoreAutonomy() {
    const { active, quietStart, quietEnd, maxFrequencyHours } = originalContract
    const restored = await owner.json('/companion/contract', {
      method: 'PUT', body: { active, quietStart, quietEnd, maxFrequencyHours }
    })
    assert.deepEqual({ active: restored.active, quietStart: restored.quietStart,
      quietEnd: restored.quietEnd, maxFrequencyHours: restored.maxFrequencyHours },
    { active, quietStart, quietEnd, maxFrequencyHours })
    restoreContract = false
  }

  async function assertStoredTransition(actual, response, original) {
    // Transition responses and reloads must preserve row creation and trigger
    // times. Normalize only the known Instant-to-Date storage precision.
    assert.equal(Date.parse(response.createdTime), Date.parse(original.createdTime))
    assert.equal(Date.parse(response.triggeredTime), Date.parse(original.triggeredTime))
    assert.deepEqual(storedTimes(actual), storedTimes(response))
  }

  await stage('isolated login and fresh recipe fixtures with inactive autonomy', async () => {
    await anonymous.json('/recipe', { status: 401, code: 40100 })
    await login(owner, 'recipe_e2e', '9')
    await login(other, 'companion_e2e', '7')
    assert.deepEqual(await owner.json('/recipe'), [])
    assert.deepEqual(await owner.json('/recipe/executions'), [])
    assert.deepEqual(await owner.json('/creation/story'), [])
    otherStories = await other.json('/creation/story')
    originalUsage = await owner.json('/model/usage')
    const home = await owner.json('/companion/me')
    assert.ok(home.companion, 'Companion suite must awaken user 9 first')
    assert.equal(home.chatPolicy, 'DEMO_ONLY')
    assert.equal(home.nutrition.policy, 'DEMO_ONLY')
    assert.equal(home.companion.lifeExperience, '0')
    assert.deepEqual(home.recentGrowth, [])
    originalContract = await owner.json('/companion/contract')
    assert.equal(originalContract.active, false)
    for (const [id, category] of [['104', '旅行'], ['105', '花园']]) {
      const picture = await owner.json(`/picture/get/vo?id=${id}`)
      assert.equal(picture.id, id)
      assert.equal(picture.userId, '9')
      assert.equal(picture.spaceId, '11')
      assert.equal(picture.category, category)
      assert.equal(picture.introduction, '仅供端到端测试')
      assert.match(picture.url, /^\/images\/mosaic\//)
    }
  })

  await stage('unavailable template and version writes are rejected without partial recipes', async () => {
    const capabilities = await owner.json('/recipe/capabilities')
    assert.deepEqual(capabilities.map(({ capability, open }) => ({ capability, open })), [
      { capability: 'STORY_DRAFT', open: true },
      { capability: 'EMOJI_DRAFT', open: false },
      { capability: 'IMAGE_FUSION', open: false }
    ])
    const templates = await owner.json('/recipe/templates')
    assert.deepEqual(templates.map(template => template.code), [
      'travel_review', 'birthday_story', 'weekly_emoji', 'old_photo_remaster'
    ])
    for (const template of templates) {
      assert.equal(template.available, ['travel_review', 'birthday_story'].includes(template.code))
      if (!template.available) {
        assert.ok(template.unavailableReason)
        await owner.json('/recipe/from-template', {
          method: 'POST', body: { templateCode: template.code, name: '拒绝未开放模板' }, ...OPERATION_ERROR
        })
      }
    }
    assert.deepEqual(await owner.json('/recipe'), [])
    travelDetail = await owner.json('/recipe/from-template', {
      method: 'POST', body: { templateCode: 'travel_review', name: 'HTTP旅行回顾' }
    })
    travelId = decimalId(travelDetail.recipe.id)
    recipes.add(travelId)
    assert.equal(travelDetail.recipe.subjectId, '9')
    assert.equal(travelDetail.recipe.status, 'DRAFT')
    assert.equal(travelDetail.recipe.latestVersion, 1)
    assert.equal(travelDetail.versions.length, 1)
    assert.equal(travelDetail.latest.version, 1)
    for (const capability of ['EMOJI_DRAFT', 'IMAGE_FUSION']) {
      await owner.json(`/recipe/${travelId}/versions`, {
        method: 'POST', body: definition('SIMILAR_STORY', [], capability), ...FORBIDDEN
      })
    }
    await owner.json(`/recipe/${travelId}/versions`, {
      method: 'POST', body: { ...definition('SIMILAR_STORY'), allowUnauthorized: true },
      status: 400, code: 40000
    })
    assert.deepEqual(await owner.json(`/recipe/${travelId}`), travelDetail)
    for (const [path, method, body] of [
      [`/recipe/${travelId}`, 'GET'], [`/recipe/${travelId}`, 'DELETE'],
      [`/recipe/${travelId}/enable`, 'POST'], [`/recipe/${travelId}/executions`, 'GET'],
      [`/recipe/${travelId}/versions`, 'POST', definition('WEEKLY_REVIEW')],
      [`/recipe/${travelId}/dry-run`, 'POST', { pictureIds: ['102'] }]
    ]) {
      await other.json(path, { method, body, ...FORBIDDEN })
    }
    assert.deepEqual(await other.json('/recipe'), [])
    assert.deepEqual(await other.json('/recipe/executions'), [])
    assert.deepEqual(await owner.json(`/recipe/${travelId}`), travelDetail)
    await assertNoModelCalls()
  })

  await stage('trial binds sources, requires explicit enabled confirmation, and rejects changed or foreign sources', async () => {
    await owner.json(`/recipe/${travelId}/dry-run`, {
      method: 'POST', body: { pictureIds: ['102'] }, status: 403, code: 40101
    })
    assert.deepEqual(await owner.json(`/recipe/${travelId}/executions`), [])
    firstTrial = await owner.json(`/recipe/${travelId}/dry-run`, {
      method: 'POST', body: { pictureIds: ['104'] }
    })
    assertPreview(firstTrial, travelId, 1, ['104'], true)
    assert.deepEqual(storedTimes((await owner.json(`/recipe/${travelId}/executions`))[0]), storedTimes(firstTrial))
    await owner.json(executePath(travelId, firstTrial.id), {
      method: 'POST', body: { pictureIds: ['104'] }, ...OPERATION_ERROR
    })
    const enabled = await owner.json(`/recipe/${travelId}/enable`, { method: 'POST' })
    assert.equal(enabled.status, 'ENABLED')
    assert.equal(enabled.revision, '1')
    await owner.json(executePath(travelId, firstTrial.id), {
      method: 'POST', body: { pictureIds: ['105'] }, ...OPERATION_ERROR
    })
    await other.json(executePath(travelId, firstTrial.id), {
      method: 'POST', body: { pictureIds: ['104'] }, ...FORBIDDEN
    })
    assert.deepEqual(storedTimes((await owner.json(`/recipe/${travelId}/executions`))[0]), storedTimes(firstTrial))
    assert.deepEqual(await owner.json('/creation/story'), [])
    await assertNoModelCalls()
  })

  await stage('new trials use the latest version; older confirmation stays pinned and creates one pending Story', async () => {
    travelDetail = await owner.json(`/recipe/${travelId}/versions`, {
      method: 'POST', body: definition('SIMILAR_STORY', [{ type: 'SOURCE_CATEGORY', category: '花园' }])
    })
    assert.equal(travelDetail.recipe.latestVersion, 2)
    assert.deepEqual(travelDetail.versions.map(version => version.version).sort(), [1, 2])
    assert.equal(travelDetail.latest.version, 2)
    unmatchedTrial = await owner.json(`/recipe/${travelId}/dry-run`, {
      method: 'POST', body: { pictureIds: ['104'] }
    })
    assertPreview(unmatchedTrial, travelId, 2, ['104'], false)
    gardenTrial = await owner.json(`/recipe/${travelId}/dry-run`, {
      method: 'POST', body: { pictureIds: ['105'] }
    })
    assertPreview(gardenTrial, travelId, 2, ['105'], true)
    assert.deepEqual(await owner.json('/creation/story'), [])
    const rejected = await owner.json(executePath(travelId, unmatchedTrial.id), {
      method: 'POST', body: { pictureIds: ['104'] }
    })
    assert.equal(rejected.status, 'REJECTED')
    assert.equal(rejected.safeErrorCode, 'CONDITION_UNMATCHED')
    assert.equal(rejected.creationTaskId, null)
    assert.equal(rejected.recipeVersion, 2)
    assert.deepEqual(await owner.json('/creation/story'), [])
    confirmed = await owner.json(executePath(travelId, firstTrial.id), {
      method: 'POST', body: { pictureIds: ['104'] }
    })
    assert.equal(confirmed.status, 'EXECUTED')
    assert.equal(confirmed.recipeVersion, 1, 'Confirm uses the preview version, not latest version 2')
    assert.equal(confirmed.safeErrorCode, null)
    assert.deepEqual(confirmed.sourcePictureIds, ['104'])
    assert.deepEqual(JSON.parse(confirmed.matchedJson), JSON.parse(firstTrial.matchedJson))
    decimalId(confirmed.creationTaskId)
    const stories = await owner.json('/creation/story')
    assert.equal(stories.length, 1)
    assert.equal(stories[0].id, confirmed.creationTaskId)
    assertPendingStory(stories[0], ['104'])
    for (const executionId of [firstTrial.id, unmatchedTrial.id]) {
      await owner.json(executePath(travelId, executionId), {
        method: 'POST', body: { pictureIds: ['104'] }, ...OPERATION_ERROR
      })
    }
    await other.json(executePath(travelId, firstTrial.id), {
      method: 'POST', body: { pictureIds: ['104'] }, ...FORBIDDEN
    })
    assert.deepEqual(await owner.json('/creation/story'), stories)
    const executions = await owner.json(`/recipe/${travelId}/executions`)
    assert.equal(executions.length, 3)
    await assertStoredTransition(executions.find(item => item.id === firstTrial.id), confirmed, firstTrial)
    await assertStoredTransition(executions.find(item => item.id === unmatchedTrial.id), rejected, unmatchedTrial)
    assert.deepEqual(storedTimes(executions.find(item => item.id === gardenTrial.id)), storedTimes(gardenTrial))
    await assertNoModelCalls()
  })

  await stage('disabled recipes stop new trials and confirmations; execution IDs cannot cross recipes', async () => {
    assert.equal((await owner.json(`/recipe/${travelId}/disable`, { method: 'POST' })).status, 'DISABLED')
    const executions = await owner.json(`/recipe/${travelId}/executions`)
    await owner.json(`/recipe/${travelId}/dry-run`, {
      method: 'POST', body: { pictureIds: ['105'] }, ...OPERATION_ERROR
    })
    await owner.json(executePath(travelId, gardenTrial.id), {
      method: 'POST', body: { pictureIds: ['105'] }, ...OPERATION_ERROR
    })
    assert.deepEqual(await owner.json(`/recipe/${travelId}/executions`), executions)
    const weekly = await owner.json('/recipe', { method: 'POST', body: { name: 'HTTP每周回顾故事' } })
    weeklyId = decimalId(weekly.id)
    recipes.add(weeklyId)
    assert.equal(weekly.latestVersion, null)
    const published = await owner.json(`/recipe/${weeklyId}/versions`, {
      method: 'POST', body: definition('WEEKLY_REVIEW')
    })
    assert.equal(published.latest.version, 1)
    await owner.json(`/recipe/${weeklyId}/enable`, { method: 'POST' })
    await owner.json(executePath(weeklyId, gardenTrial.id), {
      method: 'POST', body: { pictureIds: ['105'] }, ...OPERATION_ERROR
    })
    assert.deepEqual(await owner.json(`/recipe/${weeklyId}/executions`), [])
    assert.deepEqual(await owner.json(`/recipe/${travelId}/executions`), executions)
    await assertNoModelCalls()
  })

  await stage('real weekly WHEN is contract-gated, deduplicated, and pending until owner confirmation', async () => {
    const storiesBefore = await owner.json('/creation/story')
    const feed = await owner.json('/companion/feed', {
      method: 'POST', body: { pictureId: '104', idempotencyKey: 'r14-api-recipe-feed-104' }
    })
    assert.equal(feed.outcome, 'GROWN')
    assert.equal(feed.growth.sourcePictureId, '104')
    assert.equal(feed.growth.nutritionMode, 'DEMO_DETERMINISTIC')
    assert.equal(feed.growth.contentUnderstood, false)
    assert.equal(await owner.json('/companion/proposals/active'), null)
    assert.deepEqual(await owner.json(`/recipe/${weeklyId}/executions`), [])
    restoreContract = true
    await owner.json('/companion/contract', {
      method: 'PUT', body: { active: true, quietStart: '00:00', quietEnd: '00:00', maxFrequencyHours: 72 }
    })
    // The opportunity is observed synchronously by this existing read endpoint;
    // no scheduler, sleeps, unbounded poll, or fabricated execution is involved.
    opportunity = await owner.json('/companion/proposals/active')
    decimalId(opportunity.id)
    assert.equal(opportunity.status, 'PENDING')
    assert.equal(opportunity.opportunityType, 'WEEKLY_REVIEW')
    const executions = await owner.json(`/recipe/${weeklyId}/executions`)
    assert.equal(executions.length, 1)
    pending = executions[0]
    decimalId(pending.id)
    assert.equal(pending.recipeId, weeklyId)
    assert.equal(pending.recipeVersion, 1)
    assert.equal(pending.status, 'PENDING_CONFIRM')
    assert.match(pending.opportunityKey, /^WEEKLY_REVIEW-\d{4}-W\d{1,2}$/)
    assert.deepEqual(pending.sourcePictureIds, ['104'])
    assert.equal(pending.creationTaskId, null)
    assert.equal(pending.safeErrorCode, null)
    assert.deepEqual(JSON.parse(pending.matchedJson), { when: 'WEEKLY_REVIEW', conditions: [] })
    assert.deepEqual(JSON.parse(pending.quoteJson), { capability: 'STORY_DRAFT', platformUnits: '5' })
    assert.equal((await owner.json('/companion/proposals/active')).id, opportunity.id)
    assert.deepEqual(await owner.json(`/recipe/${weeklyId}/executions`), executions)
    assert.deepEqual(await owner.json('/creation/story'), storiesBefore)
    await other.json(executePath(weeklyId, pending.id), {
      method: 'POST', body: {}, ...FORBIDDEN
    })
    await owner.json(executePath(weeklyId, pending.id), {
      method: 'POST', body: { pictureIds: ['105'] }, ...OPERATION_ERROR
    })
    assert.deepEqual(await owner.json(`/recipe/${weeklyId}/executions`), executions)
    weeklyConfirmed = await owner.json(executePath(weeklyId, pending.id), { method: 'POST', body: {} })
    assert.equal(weeklyConfirmed.status, 'EXECUTED')
    assert.equal(weeklyConfirmed.opportunityKey, pending.opportunityKey)
    assert.deepEqual(weeklyConfirmed.sourcePictureIds, ['104'])
    decimalId(weeklyConfirmed.creationTaskId)
    assert.notEqual(weeklyConfirmed.creationTaskId, confirmed.creationTaskId)
    const storiesAfter = await owner.json('/creation/story')
    assert.equal(storiesAfter.length, 2)
    assertPendingStory(storiesAfter.find(item => item.id === weeklyConfirmed.creationTaskId), ['104'])
    await owner.json(executePath(weeklyId, pending.id), { method: 'POST', body: {}, ...OPERATION_ERROR })
    assert.deepEqual(await owner.json('/creation/story'), storiesAfter)
    await assertStoredTransition((await owner.json(`/recipe/${weeklyId}/executions`))[0], weeklyConfirmed, pending)
    await assertNoModelCalls()
    // Clear this suite's visible companion proposal before restoring autonomy.
    assert.equal((await owner.json(`/companion/proposals/${opportunity.id}/ignore`, { method: 'POST' })).status,
      'IGNORED')
    await restoreAutonomy()
    assert.equal(await owner.json('/companion/proposals/active'), null)
  })

  await stage('new login reads persisted recipes and executions; cleanup leaves unrelated Story state alone', async () => {
    const recipesBefore = await owner.json('/recipe')
    const executionsBefore = await owner.json('/recipe/executions')
    const storiesBefore = await owner.json('/creation/story')
    assert.equal(recipesBefore.length, 2)
    assert.equal(executionsBefore.length, 4)
    const detailBefore = await owner.json(`/recipe/${travelId}`)
    await owner.json('/user/logout', { method: 'POST' })
    await owner.json('/recipe', { status: 401, code: 40100 })
    owner = new FixtureSession(base)
    sessions.push(owner)
    await login(owner, 'recipe_e2e', '9')
    assert.deepEqual(await owner.json('/recipe'), recipesBefore)
    assert.deepEqual(await owner.json('/recipe/executions'), executionsBefore)
    assert.deepEqual(await owner.json(`/recipe/${travelId}`), detailBefore)
    assert.deepEqual(await owner.json('/creation/story'), storiesBefore)
    for (const story of storiesBefore) assertPendingStory(story, ['104'])
    await assertNoModelCalls()
    for (const id of [...recipes]) {
      assert.equal(await owner.json(`/recipe/${id}`, { method: 'DELETE' }), true)
      recipes.delete(id)
      await owner.json(`/recipe/${id}`, { status: 404, code: 40400 })
      await owner.json(`/recipe/${id}/executions`, { status: 404, code: 40400 })
    }
    assert.deepEqual(await owner.json('/recipe'), [])
    assert.deepEqual(await owner.json('/recipe/executions'), [])
    assert.deepEqual(await owner.json('/creation/story'), storiesBefore,
      'Deleting recipes must not delete their independently created Story tasks')
    assert.deepEqual(await other.json('/creation/story'), otherStories)
    assert.equal((await owner.json('/companion/contract')).active, false)
  })
})
