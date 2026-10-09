import test from 'node:test'
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
async function flush() { for (let i = 0; i < 8; i++) await nextTick() }
async function fixture() {
  const boundaryUrl = moduleUrl(`export const fixture = ${++fixtureSequence}; export const shared = {}; export const useUserStore = () => shared.user; export const useCompanionChatStore = () => shared.chat; export const getCompanionChatMode = () => shared.mode(); export const presenceApi = { open: (...a) => shared.open(...a), decide: (...a) => shared.decide(...a), close: (...a) => shared.close(...a) };`)
  const { shared } = await import(boundaryUrl)
  const calls = []
  shared.user = reactive({ currentUser: { id: '1' } }); shared.chat = reactive({ state: { phase: 'idle' } })
  shared.mode = async () => { calls.push('mode'); return { mode: 'DEMO' } }
  shared.open = async () => { calls.push('open'); return { mode: 'DEMO', sessionId: 's' } }
  shared.decide = async data => { calls.push('decide'); return { ...data, sequence: String(data.sequence), mode: 'DEMO', action: 'EMOTE', emote: 'NOD', remainingDecisions: 11 } }
  shared.close = async () => { calls.push('close') }
  const stub = moduleUrl(`export default { props: ['presentation', 'chatMode'], render() { return null } }`)
  const aliases = { vue: import.meta.resolve('vue'), '@/stores/user': boundaryUrl, '@/stores/companionChat': boundaryUrl, '@/api/companion': boundaryUrl, '@/api/companionPresence': boundaryUrl,
    '@/components/companion/body/CompanionPortrait.vue': stub, '@/components/companion/CompanionChatPanel.vue': stub }
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
  return { root, calls, shared, listeners, async destroy() { app.unmount(); await flush(); globalThis.document = original } }
}
function checkbox(f, index = 0) { return descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'checkbox')[index] }
async function change(node, value) { await node.props.onChange({ target: { checked: value, value } }); await flush() }
async function click(node) { assert.notEqual(node.props.disabled, true); await node.props.onClick(); await flush() }
test('real compiled rehearsal is default-off, privacy labeled, and performs no network IO', async () => {
  const f = await fixture(); try {
    assert.match(text(f.root), /确定性模拟，不是 AI 生成/); assert.match(text(f.root), /尚无输入/)
    assert.equal(button(f.root, '上传完成').props.disabled, true); assert.deepEqual(f.calls, [])
    const real = descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'radio').at(-1)
    assert.ok(real.props.disabled === '' || real.props.disabled === true)
  } finally { await f.destroy() }
})
test('local scenario shows sanitized result with reply entry; logout removes all prior input and opt-in', async () => {
  const f = await fixture(); try {
    await change(checkbox(f), true); await click(button(f.root, '上传完成'))
    assert.match(text(f.root), /SPEAK/); assert.match(text(f.root), /UPLOAD_SUCCEEDED/); assert.deepEqual(f.calls, [])
    f.shared.user.currentUser = null; await flush(); assert.match(text(f.root), /请先确认登录状态/); assert.doesNotMatch(text(f.root), /UPLOAD_SUCCEEDED/)
    f.shared.user.currentUser = { id: '2' }; await flush(); assert.equal(checkbox(f).props.checked, false); assert.match(text(f.root), /尚无输入/)
  } finally { await f.destroy() }
})
test('switching to server DEMO requires fresh opt-in and sends only after a scenario click', async () => {
  const f = await fixture(); try {
    await change(checkbox(f), true)
    const server = descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'radio')[1]
    await change(server, true); assert.equal(checkbox(f).props.checked, false); assert.deepEqual(f.calls, [])
    await change(checkbox(f), true); await click(button(f.root, '打开图片详情'))
    assert.deepEqual(f.calls, ['open', 'decide']); assert.match(text(f.root), /服务端剩余 11 次决策/)
    await change(checkbox(f, 1), true); assert.deepEqual(f.calls, ['open', 'decide', 'close'])
  } finally { await f.destroy() }
})
test('hidden page clears visible input and user reply stops rehearsal while preserving ordinary chat boundary', async () => {
  const f = await fixture(); try {
    await change(checkbox(f), true); await click(button(f.root, '上传完成'))
    globalThis.document.visibilityState = 'hidden'; f.listeners.get('visibilitychange')(); await flush(); assert.match(text(f.root), /尚无输入/)
    globalThis.document.visibilityState = 'visible'; f.listeners.get('visibilitychange')(); await flush()
    await click(button(f.root, '打开普通聊天')); assert.deepEqual(f.calls, ['mode']); assert.equal(button(f.root, '上传完成').props.disabled, true)
    await click(button(f.root, '收起聊天并继续试演')); assert.equal(button(f.root, '上传完成').props.disabled, false)
  } finally { await f.destroy() }
})
