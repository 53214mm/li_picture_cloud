import test from 'node:test'
import { createRehearsalHistory } from '../src/presentation/rehearsalHistory.js'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, nextTick, reactive } from 'vue'
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
let fixtureSequence = 0
function hostNode(tag, content = '') {
  const listeners = new Map()
  return {
    tag, tagName: tag.toUpperCase(), text: content, props: {}, children: [], parent: null,
    value: '', checked: false, selected: false, selectedIndex: -1,
    get textContent() { return this.tag === '#comment' ? '' : this.text + this.children.map(child => child.textContent).join('') },
    get options() { return descendants(this).filter(node => node.tag === 'option') },
    addEventListener(name, listener) {
      if (!listeners.has(name)) listeners.set(name, [])
      listeners.get(name).push(listener)
    },
    removeEventListener(name, listener) { listeners.set(name, (listeners.get(name) || []).filter(item => item !== listener)) },
    dispatch(name) { for (const listener of listeners.get(name) || []) listener({ target: this }) },
    querySelector() { return null },
    focus() { this.focused = true }
  }
}
const renderer = createRenderer({
  createElement: tag => hostNode(tag),
  createText: content => hostNode('#text', content),
  createComment: content => hostNode('#comment', content),
  setText(node, content) { node.text = content },
  setElementText(node, content) { node.text = content; node.children = [] },
  patchProp(node, key, _previous, value) {
    node.props[key] = value
    if (key === 'value') { node.value = value; node._value = value }
    else if (['checked', 'selected', 'type', 'multiple'].includes(key)) node[key] = value
  },
  insert(node, parent, anchor) {
    if (node.parent) node.parent.children = node.parent.children.filter(child => child !== node)
    node.parent = parent
    const index = parent.children.indexOf(anchor)
    if (index < 0) parent.children.push(node)
    else parent.children.splice(index, 0, node)
  },
  remove(node) {
    if (node.parent) node.parent.children = node.parent.children.filter(child => child !== node)
    node.parent = null
  },
  parentNode: node => node.parent,
  nextSibling(node) { return node.parent?.children[node.parent.children.indexOf(node) + 1] || null }
})
function descendants(node) { return [node, ...node.children.flatMap(descendants)] }
function text(node) { return node.textContent.replace(/\s+/g, ' ').trim() }
function button(root, label) {
  const found = descendants(root).find(node => node.tag === 'button' && text(node) === label)
  assert.ok(found, `Button must be rendered: ${label}`)
  return found
}
async function flush() { for (let i = 0; i < 40; i++) await nextTick() }
const readyStatus = () => ({ enabled: true, realEnabled: true, available: true, reason: 'READY', connection: { provider: 'OPENAI', modelCode: 'test-model' } })
const deferred = () => { let resolve; let reject; const promise = new Promise((done, fail) => { resolve = done; reject = fail }); return { promise, resolve, reject } }
async function fixture(overrides = {}) {
  const boundaryUrl = moduleUrl(`export const fixture = ${++fixtureSequence}; export const shared = {}; export const useUserStore = () => shared.user; export const useCompanionRehearsalStore = () => shared.rehearsal; export const useCompanionChatStore = () => shared.chat; export const getCompanionChatMode = () => shared.mode(); export const presenceApi = { status: (...a) => shared.status(...a), open: (...a) => shared.open(...a), decide: (...a) => shared.decide(...a), close: (...a) => shared.close(...a) };`)
  const { shared } = await import(boundaryUrl)
  const calls = []; const requests = []
  shared.rehearsal = reactive({ records: [], journal: null }); shared.rehearsal.journal = createRehearsalHistory({ onChange: rows => { shared.rehearsal.records = rows } })
  shared.user = reactive({ currentUser: { id: '1' } }); shared.chat = reactive({ state: { phase: 'idle' } })
  shared.mode = async () => { calls.push('mode'); return { mode: 'DEMO' } }
  shared.status = async options => { calls.push('status'); requests.push(['status', options]); return readyStatus() }
  shared.open = async data => { calls.push('open'); requests.push(['open', data]); return { mode: data.mode, sessionId: 's' } }
  shared.decide = async data => { calls.push('decide'); requests.push(['decide', data]); const mode = requests.findLast(([type]) => type === 'open')[1].mode; return { ...data, sequence: String(data.sequence), mode, action: 'EMOTE', emote: 'NOD', reason: mode === 'BYOK' ? 'DECIDED' : 'DEMO', remainingDecisions: 11 } }
  shared.close = async id => { calls.push('close'); requests.push(['close', id]) }
  Object.assign(shared, overrides)
  const stub = moduleUrl(`export default { props: ['presentation', 'chatMode'], render() { return null } }`)
  const aliases = { vue: import.meta.resolve('vue'), '@/stores/user': boundaryUrl, '@/stores/companionRehearsal': boundaryUrl, '@/stores/companionChat': boundaryUrl, '@/api/companion': boundaryUrl, '@/api/companionPresence': boundaryUrl,
    '@/components/companion/body/CompanionPortrait.vue': stub, '@/components/companion/CompanionChatPanel.vue': stub, '@/components/companion/CompanionObservationPanel.vue': stub }
  for (const name of ['companionPresence', 'companionPresenceTransport', 'companionPresentation']) aliases[`@/presentation/${name}`] = new URL(`../src/presentation/${name}.js`, import.meta.url).href
  const { descriptor } = parse(await readFile(new URL('../src/views/CompanionRehearsalView.vue', import.meta.url), 'utf8'))
  const compiled = compileScript(descriptor, { id: `rehearsal-${fixtureSequence}`, inlineTemplate: true }).content.replace(/from (['"])([^'"]+)\1/g, (_, _quote, specifier) => { assert.ok(aliases[specifier], specifier); return `from ${JSON.stringify(aliases[specifier])}` })
  const component = (await import(moduleUrl(compiled))).default
  const original = globalThis.document
  const listeners = new Map()
  globalThis.document = { visibilityState: 'visible', addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name) }
  const root = hostNode('root'); const app = renderer.createApp(component)
  app.component('router-link', { props: ['to'], template: '', render() { return null } })
  app.mount(root); await flush()
  return { root, calls, requests, shared, listeners, async destroy() { app.unmount(); await flush(); globalThis.document = original } }
}
function radio(f, index) { return descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'radio')[index] }
function checkbox(f, index = 0) { return descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'checkbox')[index] }
async function change(node, value) { await node.props.onChange({ target: { checked: value, value } }); await flush() }
async function click(node) { assert.notEqual(node.props.disabled, true); node.props.onClick(); await flush() }
test('real compiled rehearsal is default-off, privacy labeled, and only reads model status', async () => {
  const f = await fixture(); try {
    assert.match(text(f.root), /确定性模拟，不是 AI 生成/); assert.match(text(f.root), /尚无输入/)
    assert.equal(button(f.root, '上传完成').props.disabled, true); assert.deepEqual(f.calls, ['status'])
    const real = descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'radio').at(-1)
    assert.equal(real.props.disabled, false); assert.equal(real.props.checked, false)
    assert.match(text(f.root), /OPENAI · test-model/); assert.match(text(f.root), /状态查询只读/)
  } finally { await f.destroy() }
})
test('local scenario shows sanitized result with reply entry; logout removes all prior input and opt-in', async () => {
  const f = await fixture(); try {
    await change(checkbox(f), true); await click(button(f.root, '上传完成'))
    assert.match(text(f.root), /SPEAK/); assert.match(text(f.root), /UPLOAD_SUCCEEDED/); assert.deepEqual(f.calls, ['status'])
    f.shared.user.currentUser = null; await flush(); assert.match(text(f.root), /请先确认登录状态/); assert.doesNotMatch(text(f.root), /UPLOAD_SUCCEEDED/)
    f.shared.user.currentUser = { id: '2' }; await flush(); assert.equal(checkbox(f).props.checked, false); assert.match(text(f.root), /尚无输入/)
  } finally { await f.destroy() }
})
test('switching to server DEMO requires fresh opt-in and sends only after a scenario click', async () => {
  const f = await fixture(); try {
    await change(checkbox(f), true)
    const server = descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'radio')[1]
    await change(server, true); assert.equal(checkbox(f).props.checked, false); assert.deepEqual(f.calls, ['status'])
    await change(checkbox(f), true); await click(button(f.root, '打开图片详情'))
    assert.deepEqual(f.calls, ['status', 'open', 'decide']); assert.match(text(f.root), /服务端剩余 11 次决策/)
    await change(checkbox(f, 1), true); assert.deepEqual(f.calls, ['status', 'open', 'decide', 'close'])
  } finally { await f.destroy() }
})
test('hidden page clears visible input and user reply stops rehearsal while preserving ordinary chat boundary', async () => {
  const f = await fixture(); try {
    await change(checkbox(f), true); await click(button(f.root, '上传完成'))
    globalThis.document.visibilityState = 'hidden'; f.listeners.get('visibilitychange')(); await flush(); assert.match(text(f.root), /尚无输入/)
    globalThis.document.visibilityState = 'visible'; f.listeners.get('visibilitychange')(); await flush()
    await click(button(f.root, '打开普通聊天')); assert.deepEqual(f.calls, ['status', 'mode']); assert.equal(button(f.root, '上传完成').props.disabled, true)
    await click(button(f.root, '收起聊天并继续试演')); assert.equal(button(f.root, '上传完成').props.disabled, true)
    await click(button(f.root, '新开本轮（保留试演记录，不清日额度）')); await change(checkbox(f), true)
    assert.equal(button(f.root, '上传完成').props.disabled, false)
  } finally { await f.destroy() }
})

test('BYOK requires mode selection, fresh enable, and a manual scenario; status/enable never call a model', async () => {
  const f = await fixture(); try {
    await change(checkbox(f), true)
    await change(radio(f, 2), true)
    assert.equal(checkbox(f).props.checked, false); assert.deepEqual(f.calls, ['status'])
    assert.match(text(f.root), /开启本页真实模型试演/); assert.equal(button(f.root, '上传完成').props.disabled, true)
    await change(checkbox(f), true); assert.deepEqual(f.calls, ['status'])
    await click(button(f.root, '上传完成'))
    assert.deepEqual(f.calls, ['status', 'open', 'decide'])
    assert.deepEqual(f.requests.find(([type]) => type === 'open')[1], { enabled: true, mode: 'BYOK', maxDecisions: 12, muted: false, intensity: 'LOW', scope: 'REHEARSAL', cooldownSeconds: 5 })
    assert.match(text(f.root), /EMOTE · 真实模型 · BYOK/)
    assert.match(text(f.root), /服务端剩余 11 次决策/)
    await change(radio(f, 0), true)
    assert.equal(checkbox(f).props.checked, false); assert.equal(f.calls.at(-1), 'close')
    assert.doesNotMatch(text(f.root), /EMOTE · 真实模型 · BYOK/)
  } finally { await f.destroy() }
})

for (const [reason, copy] of [['DISABLED', /主动回应未启用/], ['REAL_DISABLED', /尚未开放真实模型/], ['BYOK_REQUIRED', /配置并启用语言代理 BYOK/], ['UNAVAILABLE', /暂时无法确认真实模型连接/]]) {
  test(`${reason} status blocks real opt-in without changing local simulation`, async () => {
    const f = await fixture({ status: async () => ({ enabled: reason !== 'DISABLED', realEnabled: !['DISABLED', 'REAL_DISABLED'].includes(reason), available: false, reason, connection: null }) })
    try {
      assert.match(text(f.root), copy); assert.equal(radio(f, 2).props.disabled, true)
      await change(radio(f, 2), true); assert.equal(radio(f, 0).props.checked, true)
      await change(checkbox(f), true); await click(button(f.root, '上传完成'))
      assert.match(text(f.root), /本地固定规则/); assert.deepEqual(f.calls, [])
    } finally { await f.destroy() }
  })
}

test('status failures show a safe retry state; refresh recovers without auto-selection or paid calls', async () => {
  const f = await fixture({ status: async () => { throw new Error('secret-token in provider exception') } })
  try {
    assert.match(text(f.root), /暂时无法读取真实模型状态/); assert.doesNotMatch(text(f.root), /secret-token/)
    assert.equal(radio(f, 2).props.disabled, true)
    f.shared.status = async () => readyStatus(); await click(button(f.root, '刷新模型状态'))
    assert.equal(radio(f, 2).props.disabled, false); assert.equal(radio(f, 0).props.checked, true)
    assert.equal(checkbox(f).props.checked, false); assert.deepEqual(f.calls, [])
  } finally { await f.destroy() }
})

for (const status of [null, {}, { ...readyStatus(), available: 'true' }, { ...readyStatus(), enabled: false }, { ...readyStatus(), connection: null }, { ...readyStatus(), connection: { provider: 'OPENAI', modelCode: '' } }, { ...readyStatus(), reason: 'SECRET_ERROR' }]) {
  test(`malformed or inconsistent status fails closed: ${JSON.stringify(status)}`, async () => {
    const f = await fixture({ status: async () => status }); try {
      assert.equal(radio(f, 2).props.disabled, true); assert.match(text(f.root), /暂时无法读取真实模型状态/)
      assert.equal(checkbox(f).props.checked, false); assert.deepEqual(f.calls, [])
    } finally { await f.destroy() }
  })
}

test('status response from an old account cannot unlock BYOK or expose its connection on the new account', async () => {
  const old = deferred(); let oldSignal
  const f = await fixture({ status: options => { oldSignal = options.signal; return old.promise } })
  try {
    assert.equal(radio(f, 2).props.disabled, true)
    f.shared.status = async () => ({ enabled: true, realEnabled: true, available: false, reason: 'BYOK_REQUIRED', connection: null })
    f.shared.user.currentUser = { id: '2' }; await flush(); assert.equal(oldSignal.aborted, true)
    old.resolve(readyStatus()); await flush()
    assert.equal(radio(f, 2).props.disabled, true); assert.doesNotMatch(text(f.root), /test-model/)
    assert.match(text(f.root), /配置并启用语言代理 BYOK/); assert.deepEqual(f.calls, [])
  } finally { await f.destroy() }
})

test('refresh during a paid request cancels output and revokes opt-in even when the connection is still ready', async () => {
  const pending = deferred(); let request; let signal
  const f = await fixture({ decide: (data, options) => { request = data; signal = options.signal; return pending.promise } })
  try {
    await change(radio(f, 2), true); await change(checkbox(f), true); await click(button(f.root, '上传完成'))
    assert.ok(request); await click(button(f.root, '刷新模型状态'))
    assert.equal(signal.aborted, true); assert.equal(checkbox(f).props.checked, false)
    pending.resolve({ ...request, sequence: '1', mode: 'BYOK', action: 'SPEAK', text: 'late paid response' }); await flush()
    assert.doesNotMatch(text(f.root), /late paid response/); assert.match(text(f.root), /尚无输入/)
    assert.equal(radio(f, 2).props.checked, true); assert.equal(button(f.root, '上传完成').props.disabled, true)
    assert.deepEqual(f.calls, ['status', 'open', 'status', 'close'])
  } finally { await f.destroy() }
})

for (const cause of ['mode', 'disable', 'hidden', 'account', 'logout', 'chat']) {
  test(`${cause} cancels an in-flight BYOK response without replay`, async () => {
    const pending = deferred(); let request; let signal
    const f = await fixture({ decide: (data, options) => { request = data; signal = options.signal; return pending.promise } })
    try {
      await change(radio(f, 2), true); await change(checkbox(f), true); await click(button(f.root, '上传完成'))
      assert.ok(request)
      if (cause === 'mode') await change(radio(f, 1), true)
      if (cause === 'disable') await change(checkbox(f), false)
      if (cause === 'hidden') { globalThis.document.visibilityState = 'hidden'; f.listeners.get('visibilitychange')(); await flush() }
      if (cause === 'account') { f.shared.user.currentUser = { id: '2' }; await flush() }
      if (cause === 'logout') { f.shared.user.currentUser = null; await flush() }
      if (cause === 'chat') await click(button(f.root, '打开普通聊天'))
      assert.equal(signal.aborted, true)
      pending.resolve({ ...request, sequence: '1', mode: 'BYOK', action: 'SPEAK', text: 'late paid response' }); await flush()
      assert.doesNotMatch(text(f.root), /late paid response/)
      assert.equal(f.requests.filter(([type]) => type === 'open').length, 1)
      if (cause === 'account') { assert.equal(radio(f, 0).props.checked, true); assert.equal(checkbox(f).props.checked, false); assert.match(text(f.root), /尚无输入/) }
      if (cause === 'logout') assert.match(text(f.root), /请先确认登录状态/)
    } finally { await f.destroy() }
  })
}

test('no logged-in owner means no status lookup; unmount aborts pending status', async () => {
  const pending = deferred(); let signal
  const f = await fixture({ user: reactive({ currentUser: null }), status: options => { signal = options.signal; return pending.promise } })
  assert.deepEqual(f.calls, []); assert.equal(signal, undefined)
  f.shared.user.currentUser = { id: '1' }; await flush(); assert.equal(signal.aborted, false)
  await f.destroy(); assert.equal(signal.aborted, true)
  pending.resolve(readyStatus()); await flush(); assert.equal(f.root.children.length, 0)
})

test('initially active chat and account reset keep user-message priority until chat becomes idle', async () => {
  const f = await fixture({ chat: reactive({ state: { phase: 'streaming' } }) })
  try {
    await change(radio(f, 2), true); await change(checkbox(f), true)
    assert.equal(button(f.root, '上传完成').props.disabled, true)
    f.shared.user.currentUser = { id: '2' }; await flush()
    await change(radio(f, 2), true); await change(checkbox(f), true)
    assert.equal(button(f.root, '上传完成').props.disabled, true)
    assert.deepEqual(f.calls, ['status', 'status'])
    f.shared.chat.state.phase = 'idle'; await flush()
    assert.equal(button(f.root, '上传完成').props.disabled, false)
    assert.deepEqual(f.calls, ['status', 'status'])
  } finally { await f.destroy() }
})

test('a newer same-owner status refresh wins over a stale ready response', async () => {
  const old = deferred(); const newer = deferred(); let oldSignal
  const f = await fixture({ status: options => { oldSignal = options.signal; return old.promise } })
  try {
    f.shared.status = () => newer.promise
    // Exercise the handler directly to cover queued/programmatic refreshes too.
    const updating = button(f.root, '刷新模型状态').props.onClick()
    await flush(); assert.equal(oldSignal.aborted, true)
    newer.resolve({ enabled: true, realEnabled: false, available: false, reason: 'REAL_DISABLED', connection: null })
    await updating; await flush()
    old.resolve(readyStatus()); await flush()
    assert.match(text(f.root), /尚未开放真实模型/); assert.doesNotMatch(text(f.root), /test-model/)
    assert.equal(radio(f, 2).props.disabled, true); assert.deepEqual(f.calls, [])
  } finally { await f.destroy() }
})

test('status renderer uses only provider/model summary and never exposes extra response fields', async () => {
  const f = await fixture({ status: async () => ({ ...readyStatus(), key: 'raw-secret-key', url: 'https://secret-endpoint', connection: { ...readyStatus().connection, apiKey: 'raw-secret-key', baseUrl: 'https://secret-endpoint', label: 'private-label' } }) })
  try {
    assert.match(text(f.root), /OPENAI · test-model/)
    assert.doesNotMatch(text(f.root), /raw-secret-key|secret-endpoint|private-label/)
    assert.deepEqual(f.calls, [])
  } finally { await f.destroy() }
})

test('account reset while hidden cannot permit a programmatic paid scenario before becoming visible', async () => {
  const f = await fixture(); try {
    globalThis.document.visibilityState = 'hidden'; f.listeners.get('visibilitychange')(); await flush()
    f.shared.user.currentUser = { id: '2' }; await flush()
    await change(radio(f, 2), true); await change(checkbox(f), true)
    await button(f.root, '上传完成').props.onClick(); await flush()
    assert.deepEqual(f.calls, ['status', 'status'])
    globalThis.document.visibilityState = 'visible'; f.listeners.get('visibilitychange')(); await flush()
    assert.deepEqual(f.calls, ['status', 'status'])
    await click(button(f.root, '上传完成')); assert.deepEqual(f.calls, ['status', 'status', 'open', 'decide'])
  } finally { await f.destroy() }
})

test('manual server gate blocks BYOK while local simulation remains available', async () => {
  const f = await fixture({ status: async () => ({ ...readyStatus(), rehearsalEnabled: false }) })
  try { assert.equal(radio(f, 2).props.disabled, true); assert.match(text(f.root), /手动试演未开放/); await change(checkbox(f), true); await click(button(f.root, '上传完成')); assert.equal(f.shared.rehearsal.records.length, 1) }
  finally { await f.destroy() }
})
test('new round retains results and clear is isolated from chat and daily counters; range does not silently clamp', async () => {
  const f = await fixture({ status: async () => ({ ...readyStatus(), rehearsalEnabled: true, rehearsalDailyLimit: 100, rehearsalDailyRemaining: 94 }) })
  try {
    assert.match(text(f.root), /94 \/ 100 次/)
    const limit = descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'number')[0]
    await change(limit, 100); assert.match(text(f.root), /本轮只能设置 1–12 次/)
    await change(checkbox(f), true); await click(button(f.root, '上传完成')); assert.equal(f.shared.rehearsal.records.length, 1)
    await click(button(f.root, '新开本轮（保留试演记录，不清日额度）')); assert.equal(f.shared.rehearsal.records.length, 1); assert.equal(checkbox(f).props.checked, false)
    await click(button(f.root, '清空本轮试演记录')); assert.equal(f.shared.rehearsal.records.length, 0); assert.deepEqual(f.calls, [])
    assert.match(text(f.root), /94 \/ 100 次/)
  } finally { await f.destroy() }
})

test('scenario meanings and hiding text are explained before a request without pretending to know old trace', async () => {
  const f = await fixture()
  try {
    assert.match(text(f.root), /隐藏文字气泡/)
    assert.match(text(f.root), /没有图片像素/)
    await change(radio(f, 2), true); await change(checkbox(f), true); await click(button(f.root, '上传完成'))
    assert.match(text(f.root), /无法补回/)
    assert.equal(f.shared.rehearsal.records[0].inputSnapshot.eventType, 'UPLOAD_SUCCEEDED')
  } finally { await f.destroy() }
})

test('compiled view renders raw content as text and retains its request trace across remount without replay', async () => {
  const f = await fixture({ decide: async data => ({ ...data, mode: 'BYOK', action: 'SILENT', reason: 'INVALID_DECISION', stage: 'PARSE', modelDispatched: true, debug: {
    event: { ...data, scope: 'REHEARSAL' }, messages: [{ role: 'system', content: 'BUSINESS_PROMPT_SENTINEL' }, { role: 'user', content: 'eventType=UPLOAD_SUCCEEDED;page=UPLOAD' }],
    parameters: { provider: 'OPENAI', model: 'trace-fixture', stream: true, maxTokens: 160, collectionTimeoutSeconds: 8, maxOutputChars: 1024, thinking: 'NOT_SENT', temperature: 'NOT_SENT', apiKey: 'PRIVATE_KEY' },
    rawBody: '<img src=x onerror="privateScript()">RAW_CONTENT_SENTINEL', rawBodyComplete: true, rawBodyTruncated: false, parsedDecision: null, stream: { supported: true, contentFrames: 1, reasoningPresent: false, finishReason: 'stop', terminal: 'FINISH_REASON', reasoning_content: 'PRIVATE_REASONING' }, redactedFields: [], Authorization: 'PRIVATE_HEADER'
  } }) })
  let next
  try {
    await change(radio(f, 2), true); await change(checkbox(f), true); await click(button(f.root, '上传完成'))
    assert.match(text(f.root), /RAW_CONTENT_SENTINEL/); assert.match(text(f.root), /BUSINESS_PROMPT_SENTINEL/)
    assert.match(text(f.root), /没有可用解析决定/); assert.doesNotMatch(text(f.root), /PRIVATE_KEY|PRIVATE_REASONING|PRIVATE_HEADER/)
    assert.equal(descendants(f.root).filter(node => node.tag === 'img').length, 0)
    const record = f.shared.rehearsal.records[0]; assert.equal(record.protocolSnapshot.eventType, 'UPLOAD_SUCCEEDED')
    await f.destroy(); next = await fixture({ rehearsal: f.shared.rehearsal })
    assert.match(text(next.root), /RAW_CONTENT_SENTINEL/); assert.deepEqual(next.calls, ['status'])
    await click(button(next.root, '清空本轮试演记录')); assert.doesNotMatch(text(next.root), /RAW_CONTENT_SENTINEL|BUSINESS_PROMPT_SENTINEL/)
    assert.equal(next.shared.rehearsal.records.length, 0)
  } finally { if (next) await next.destroy(); else await f.destroy() }
})
