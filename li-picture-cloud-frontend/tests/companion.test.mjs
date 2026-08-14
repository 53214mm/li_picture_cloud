import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import {
  applyFeedResult,
  beginFeedAttempt,
  buildCompanionPictureQuery,
  describeTrait,
  parseSse,
  selectOldestPrivateSpace,
  shouldRetrySameFeedKey,
  traitPosition
} from '../src/utils/companion.js'
import {
  createAuthSessionGate,
  createSingleFlightLoader,
  isTerminalAuthFailure
} from '../src/utils/authBootstrap.js'

test('keeps one idempotency key through ambiguous retries', () => {
  const first = beginFeedAttempt('102', null, () => 'feed-key-0000001')
  const retry = beginFeedAttempt('102', first, () => 'feed-key-0000002')
  const changed = beginFeedAttempt('103', first, () => 'feed-key-0000003')
  assert.deepEqual(first, { pictureId: '102', idempotencyKey: 'feed-key-0000001' })
  assert.equal(retry, first)
  assert.equal(changed.idempotencyKey, 'feed-key-0000003')
})

test('renders only the server-provided nutrition label and safe provenance fields', async () => {
  const picker = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionPicturePicker.vue', import.meta.url)), 'utf8')
  const timeline = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionGrowthTimeline.vue', import.meta.url)), 'utf8')
  const bubble = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionMessageBubble.vue', import.meta.url)), 'utf8')
  const page = await readFile(fileURLToPath(new globalThis.URL('../src/views/CompanionView.vue', import.meta.url)), 'utf8')

  assert.match(timeline, /record\.nutritionLabel/)
  assert.match(timeline, /record\.providerCode/)
  assert.match(timeline, /record\.modelCode/)
  assert.doesNotMatch(timeline, /nutritionModeLabel/)
  assert.doesNotMatch(page, /nutritionModeLabel/)
  assert.match(page, /每日视觉次数上限/)
  assert.match(timeline, /CompanionMessageBubble/)
  assert.match(timeline, /:message="record\.reason"/)
  assert.match(bubble, /role="group"/)
  assert.match(bubble, /伙伴说/)
  assert.match(picker, /MAX_MATERIAL_PICTURES = 12/)
  assert.match(picker, /visiblePictures/)
  assert.match(picker, /max-block-size:\s*29rem/)
  assert.match(picker, /overflow-y:\s*auto/)
  assert.match(timeline, /MAX_ARCHIVE_RECORDS = 20/)
  assert.match(timeline, /visibleRecords/)
  assert.match(timeline, /max-block-size:\s*46rem/)
  assert.match(timeline, /overflow-y:\s*auto/)
})

test('mood relationship and memory panels reuse the shared message bubble language', async () => {
  const mood = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionMoodPanel.vue', import.meta.url)), 'utf8')
  const relationship = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionRelationshipPanel.vue', import.meta.url)), 'utf8')
  const memory = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionMemoryPanel.vue', import.meta.url)), 'utf8')
  const page = await readFile(fileURLToPath(new globalThis.URL('../src/views/CompanionView.vue', import.meta.url)), 'utf8')
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/companion.js', import.meta.url)), 'utf8')
  const constants = await readFile(fileURLToPath(new globalThis.URL('../src/constants/companion.js', import.meta.url)), 'utf8')

  // 情绪面板展示服务端摘要，不自行解释数值。
  assert.match(mood, /MOOD_AXES/)
  assert.match(mood, /mood\.summary/)
  assert.match(mood, /CompanionMessageBubble/)
  assert.match(mood, /:message="mood\.summary"/)
  assert.match(mood, /随时间自然回落/)
  // 关系面板支持近期反馈的负向展示。
  assert.match(relationship, /RELATIONSHIP_AXES/)
  assert.match(relationship, /recentFeedback/)
  assert.match(relationship, /negative/)
  // 记忆面板：五个接口、状态机操作与失效隐藏内容。
  assert.match(memory, /CompanionMessageBubble/)
  assert.match(memory, /MEMORY_STATUS/)
  assert.match(memory, /listCompanionMemories/)
  assert.match(memory, /confirmCompanionMemory/)
  assert.match(memory, /correctCompanionMemory/)
  assert.match(memory, /dismissCompanionMemory/)
  assert.match(memory, /deleteCompanionMemory/)
  assert.match(memory, /availableActions/)
  assert.match(memory, /来源图片已不可用/)
  assert.match(memory, /memory\.content/)
  // 主页集成三个面板，服务端 mood/relationship 驱动展示。
  assert.match(page, /CompanionMoodPanel/)
  assert.match(page, /CompanionRelationshipPanel/)
  assert.match(page, /CompanionMemoryPanel/)
  assert.match(page, /:mood="home\.mood"/)
  assert.match(page, /:relationship="home\.relationship"/)
  // API 与常量契约。
  assert.match(api, /\/companion\/memories/)
  assert.match(constants, /MOOD_AXES/)
  assert.match(constants, /RELATIONSHIP_AXES/)
  assert.match(constants, /MEMORY_STATUS/)
  assert.match(constants, /INVALIDATED/)
})

test('applies the server result once and de-duplicates history', () => {
  const home = { companion: { revision: '0' }, recentGrowth: [] }
  const result = {
    companion: { revision: '1', lifeExperience: '42' },
    growth: { id: '31', eventType: 'PICTURE_FED', createdTime: '2026-08-11T08:00:00Z' }
  }
  const twice = applyFeedResult(applyFeedResult(home, result), result)
  assert.equal(twice.companion.revision, '1')
  assert.deepEqual(twice.recentGrowth.map(item => item.id), ['31'])
})

test('an old idempotent replay cannot roll back the visible companion or timeline', () => {
  const home = {
    companion: { revision: '2', lifeExperience: '43' },
    recentGrowth: [
      { id: '32', createdTime: '2026-08-11T08:01:00Z' },
      { id: '31', createdTime: '2026-08-11T08:00:00Z' }
    ]
  }
  const replay = {
    companion: { revision: '1', lifeExperience: '42' },
    growth: { id: '31', createdTime: '2026-08-11T08:00:00Z' }
  }
  const merged = applyFeedResult(home, replay)
  assert.equal(merged.companion.revision, '2')
  assert.equal(merged.companion.lifeExperience, '43')
  assert.deepEqual(merged.recentGrowth.map(item => item.id), ['32', '31'])
})

test('orders growth instants by time even when fractional precision differs', () => {
  const home = {
    companion: { revision: '2' },
    recentGrowth: [{ id: '31', createdTime: '2026-08-11T08:00:00Z' }]
  }
  const result = {
    companion: { revision: '3' },
    growth: { id: '32', createdTime: '2026-08-11T08:00:00.500Z' }
  }
  assert.deepEqual(applyFeedResult(home, result).recentGrowth.map(item => item.id), ['32', '31'])
})

test('selects the oldest owned private space and builds an authorized picture query', () => {
  const spaces = [
    { id: '12', userId: '7', spaceType: 0, createTime: '2026-01-01T00:00:00.500Z' },
    { id: '15', userId: '7', spaceType: 0, createTime: '2026-01-01T00:00:00Z' },
    { id: '11', userId: '7', spaceType: 0, createTime: '2026-01-01T00:00:00Z' },
    { id: '13', userId: '8', spaceType: 0, createTime: '2025-01-01T00:00:00Z' },
    { id: '14', userId: '7', spaceType: 1, createTime: '2025-01-01T00:00:00Z' }
  ]
  assert.equal(selectOldestPrivateSpace(spaces, '7').id, '11')
  assert.deepEqual(buildCompanionPictureQuery('11'), {
    current: 1, pageSize: 12, spaceId: '11', sortField: 'createTime', sortOrder: 'descend'
  })
})

test('describes bipolar traits without presenting a maximize score', () => {
  const axis = { negative: '谨慎', positive: '好奇' }
  assert.equal(describeTrait(0, axis), '保持中性')
  assert.equal(describeTrait(24, axis), '略偏好奇')
  assert.equal(describeTrait(-72, axis), '明显偏谨慎')
  assert.equal(traitPosition(-100), 0)
  assert.equal(traitPosition(0), 50)
  assert.equal(traitPosition(100), 100)
})

test('retains a feed key only when retrying can recover the same run', () => {
  assert.equal(shouldRetrySameFeedKey({}), true)
  assert.equal(shouldRetrySameFeedKey({ status: 500 }), true)
  assert.equal(shouldRetrySameFeedKey({ status: 429 }), true)
  assert.equal(shouldRetrySameFeedKey({ status: 403 }), false)
  assert.equal(shouldRetrySameFeedKey({ status: 400 }), false)
})

test('auth loading is single-flight and retries a transient failure', async () => {
  let calls = 0
  const load = createSingleFlightLoader(async () => {
    calls += 1
    if (calls === 1) throw Object.assign(new Error('temporary'), { status: 500 })
    return { id: '7' }
  })
  const first = load()
  const concurrent = load()
  assert.equal(first, concurrent)
  await assert.rejects(first)
  assert.deepEqual(await load(), { id: '7' })
  assert.equal(calls, 2)
  assert.equal(isTerminalAuthFailure({ status: 401 }), true)
  assert.equal(isTerminalAuthFailure({ status: 403 }), true)
  assert.equal(isTerminalAuthFailure({ status: 500 }), false)
  assert.equal(isTerminalAuthFailure({}), false)
})

test('a late bootstrap result cannot clobber an explicit login or logout', async () => {
  const gate = createAuthSessionGate()
  let settleBootstrap
  const bootstrapResult = new Promise(resolve => { settleBootstrap = resolve })
  const captured = gate.capture()
  let currentUser = null

  currentUser = { id: '7' }
  gate.invalidate()
  settleBootstrap(null)
  const staleResult = await bootstrapResult
  if (gate.isCurrent(captured)) currentUser = staleResult
  assert.deepEqual(currentUser, { id: '7' })

  const logoutCapture = gate.capture()
  currentUser = null
  gate.invalidate()
  if (gate.isCurrent(logoutCapture)) currentUser = { id: '7' }
  assert.equal(currentUser, null)
})

test('parses sse chunks across fragmented buffers and names events', () => {
  const first = parseSse('data:你\n\ndata:好')
  assert.deepEqual(first.parsed, [{ name: 'message', data: '你' }])
  assert.equal(first.remainder, 'data:好')

  const second = parseSse(first.remainder + '\n\nevent:done\ndata:\n\n')
  assert.deepEqual(second.parsed, [
    { name: 'message', data: '好' },
    { name: 'done', data: '' }
  ])
  assert.equal(second.remainder, '')

  const error = parseSse('event:error\ndata:伙伴走神了\n\n')
  assert.deepEqual(error.parsed, [{ name: 'error', data: '伙伴走神了' }])

  // CRLF 变体（部分代理/网关会转换换行）同样可解析。
  const crlf = parseSse('data:第一段\r\n\r\ndata:第二段\r\n\r\n')
  assert.deepEqual(crlf.parsed, [
    { name: 'message', data: '第一段' },
    { name: 'message', data: '第二段' }
  ])
  assert.equal(crlf.remainder, '')
})

test('chat panel reuses the companion bubble and streams replies', async () => {
  const chat = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionChatPanel.vue', import.meta.url)), 'utf8')
  const page = await readFile(fileURLToPath(new globalThis.URL('../src/views/CompanionView.vue', import.meta.url)), 'utf8')
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/companion.js', import.meta.url)), 'utf8')
  const utils = await readFile(fileURLToPath(new globalThis.URL('../src/utils/companion.js', import.meta.url)), 'utf8')

  assert.match(chat, /CompanionMessageBubble/)
  assert.match(chat, /streamCompanionChat/)
  assert.match(chat, /listCompanionChatHistory/)
  assert.match(chat, /role === 'COMPANION'/)
  assert.match(chat, /user-bubble/)
  // 发送失败时恢复草稿，避免用户输入丢失。
  assert.match(chat, /draft\.value = content/)
  assert.match(page, /CompanionChatPanel/)
  assert.match(api, /\/companion\/chat\/history/)
  assert.match(utils, /parseSse/)
  assert.match(utils, /\/companion\/chat\/stream/)
  assert.match(utils, /credentials: 'include'/)
})

test('proposal panel gates by contract and supports accept ignore scold', async () => {
  const panel = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionProposalPanel.vue', import.meta.url)), 'utf8')
  const memory = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionMemoryPanel.vue', import.meta.url)), 'utf8')
  const page = await readFile(fileURLToPath(new globalThis.URL('../src/views/CompanionView.vue', import.meta.url)), 'utf8')
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/companion.js', import.meta.url)), 'utf8')

  assert.match(panel, /CompanionMessageBubble/)
  assert.match(panel, /getActiveCompanionProposal/)
  assert.match(panel, /acceptCompanionProposal/)
  assert.match(panel, /ignoreCompanionProposal/)
  assert.match(panel, /scoldCompanionProposal/)
  assert.match(panel, /敲打只抑制这一次提议/)
  assert.match(panel, /getCompanionContract/)
  assert.match(panel, /updateCompanionContract/)
  assert.match(panel, /quietStart/)
  assert.match(panel, /maxFrequencyHours/)
  assert.match(panel, /OPPORTUNITY_LABELS/)
  assert.match(panel, /ANNIVERSARY/)
  assert.match(panel, /SIMILAR_STORY/)
  // 契约保存后立即按新契约重新评估提案；反馈用正向语气而非错误样式。
  assert.match(panel, /await loadProposal\(\)/)
  assert.match(panel, /伙伴安静了，这次提议已被止住。/)
  assert.match(panel, /已忽略，伙伴不会再提这件事。/)
  // 喂养成功后通过 refreshKey 通知面板刷新。
  assert.match(panel, /refreshKey/)
  assert.match(memory, /refreshKey/)
  assert.match(page, /panelsRefreshKey/)
  assert.match(page, /:refresh-key="panelsRefreshKey"/)
  assert.match(page, /CompanionProposalPanel/)
  assert.match(api, /\/companion\/contract/)
  assert.match(api, /\/companion\/proposals\/active/)
  assert.match(api, /\/companion\/proposals\/\$\{id\}\/scold/)
})
