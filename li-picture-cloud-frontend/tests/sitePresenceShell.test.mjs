import test from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, nextTick, reactive, ref } from 'vue'
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
import { createRouter, createMemoryHistory } from 'vue-router'
import { siteNavigationIntent } from '../src/presentation/sitePresenceNavigation.js'
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
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
async function fixture({ enabled = true } = {}) {
  const boundaryUrl = moduleUrl(`export const fixture = ${++fixtureSequence}; export const shared = {}; export const useUserStore = () => shared.user; export const useCompanionChatStore = () => shared.chat; export const useCompanionInteractionStore = () => shared.interaction; export const useLegacyOverlayIsolation = () => shared.overlay; export const COMPANION_UI_ENABLED = ${enabled}; export const presenceApi = { open: (...a) => shared.open(...a), decide: (...a) => shared.decide(...a), close: (...a) => shared.close(...a) };`)
  const { shared } = await import(boundaryUrl); const calls = []; const listeners = new Map()
  shared.user = reactive({ currentUser: { id: '1' }, logout: async () => { calls.push('logout') } })
  shared.chat = reactive({ state: { phase: 'idle', draft: '' }, modeBusy: false })
  shared.overlay = ref(false)
  shared.interaction = reactive({ requestNumber: 0, open() { calls.push('chat-open'); this.requestNumber++ }, close() {}, reset() {}, cancelDrag() {}, takeDestination() {} })
  shared.open = async data => { calls.push(['open', data]); return { mode: 'DEMO', sessionId: 's' } }
  shared.decide = async data => { calls.push(['decide', data]); return { ...data, sequence: String(data.sequence), mode: 'DEMO', action: 'EMOTE', emote: 'NOD' } }
  shared.close = async id => { calls.push(['close', id]) }
  const empty = moduleUrl('export default { render() { return null } }')
  const shell = moduleUrl(`export default { inheritAttrs: false, props: ['open'], emits: ['close'], render() { return this.open ? this.$slots.default?.() : null } }`)
  const aliases = { vue: import.meta.resolve('vue'), 'vue-router': import.meta.resolve('vue-router') }
  for (const path of ['stores/user', 'stores/companionChat', 'stores/companionInteraction', 'api/companionPresence', 'config/features', 'composables/useLegacyOverlayIsolation']) aliases[`@/${path}`] = boundaryUrl
  for (const path of ['sitePresence', 'sitePresenceNavigation', 'companionPresentation']) aliases[`@/presentation/${path}`] = new URL(`../src/presentation/${path}.js`, import.meta.url).href
  const rewrite = source => source.replace(/from (['"])([^'"]+)\1/g, (_, _quote, specifier) => { assert.ok(aliases[specifier], specifier); return `from ${JSON.stringify(aliases[specifier])}` })
  aliases['@/composables/useSitePresence'] = moduleUrl(rewrite(await readFile(new URL('../src/composables/useSitePresence.js', import.meta.url), 'utf8')))
  aliases['./body/CompanionPortrait.vue'] = empty
  async function compile(path) {
    const { descriptor } = parse(await readFile(new URL(`../src/${path}`, import.meta.url), 'utf8'))
    return moduleUrl(rewrite(compileScript(descriptor, { id: `${path}-${fixtureSequence}`, inlineTemplate: true }).content))
  }
  aliases['@/components/companion/SitePresenceWidget.vue'] = await compile('components/companion/SitePresenceWidget.vue')
  for (const path of ['shell/AppNavigation', 'shell/AppToolbar', 'shell/AppMain', 'shell/CompanionPresenceSlot', 'shell/SiteFooter', 'shell/ShellIcon', 'ui/LpButton', 'companion/CompanionInteractionPanel']) aliases[`@/components/${path}.vue`] = empty
  aliases['@/components/shell/ShellDialog.vue'] = shell
  const component = (await import(await compile('layouts/AppLayout.vue'))).default
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/gallery', name: 'gallery', component: {} }, { path: '/space/my', name: 'my-space', component: {} }, { path: '/companion', name: 'companion', component: {} },
    { path: '/picture/:id', name: 'picture-detail', component: {} }, { path: '/admin', name: 'admin-users', component: {} },
    { path: '/companion/rehearsal', name: 'companion-rehearsal', component: {} }
  ] })
  router.beforeEach(to => siteNavigationIntent(to))
  await router.push('/gallery'); await router.isReady()
  const originals = { document: globalThis.document, window: globalThis.window }
  globalThis.document = { hidden: false, addEventListener: (name, fn) => { if (!listeners.has(name)) listeners.set(name, new Set()); listeners.get(name).add(fn) }, removeEventListener: (name, fn) => listeners.get(name)?.delete(fn), querySelector: () => null, querySelectorAll: () => [] }
  globalThis.window = { matchMedia: () => ({ addEventListener() {}, removeEventListener() {} }) }
  const root = hostNode('root'); const app = renderer.createApp(component); app.use(router); app.mount(root); await flush()
  return { root, router, shared, calls, emit(name, event = {}) { for (const listener of listeners.get(name) || []) listener(event) }, async destroy() { app.unmount(); await flush(); Object.assign(globalThis, originals) } }
}
function checkbox(f, index = 0) { return descendants(f.root).filter(n => n.tag === 'input' && n.props.type === 'checkbox')[index] }
async function change(node, checked) { node.props.onChange({ target: { checked } }); await flush() }
test('compiled AppLayout and real widget stay default-off, including feature-off build branch', async () => {
  for (const enabled of [true, false]) {
    const f = await fixture({ enabled }); try {
      await f.router.push('/companion'); await flush(); assert.deepEqual(f.calls, [])
      assert.equal(!!checkbox(f), enabled)
      if (enabled) { assert.match(text(f.root), /服务端 DEMO/); assert.match(text(f.root), /真实模型暂未开放/) }
    } finally { await f.destroy() }
  }
})
test('compiled shell opts in only via widget and opens existing ordinary interaction without autosend', async () => {
  const f = await fixture(); try {
    await change(checkbox(f), true); await f.router.push('/companion'); await flush()
    assert.equal(f.calls.filter(x => x[0] === 'decide').length, 1); assert.match(text(f.root), /EMOTE/)
    button(f.root, '回复绫页 · 普通聊天').props.onClick(); await flush()
    assert.equal(f.calls.filter(x => x === 'chat-open').length, 1); assert.equal(checkbox(f), undefined)
    assert.equal(f.calls.some(x => /send|history/.test(String(x[0]))), false)
  } finally { await f.destroy() }
})
test('compiled shell suppresses pending output on hidden, overlay, chat draft, phase and account change', async () => {
  for (const cause of ['hidden','overlay','draft','phase','account']) {
    const f = await fixture(); const pending = deferred(); let sent
    try {
      f.shared.decide = data => { sent = data; return pending.promise }
      await change(checkbox(f), true); await f.router.push('/companion'); await flush(); assert.ok(sent)
      if (cause === 'hidden') { globalThis.document.hidden = true; f.emit('visibilitychange') }
      if (cause === 'overlay') f.shared.overlay.value = true
      if (cause === 'draft') f.shared.chat.state.draft = 'private words'
      if (cause === 'phase') f.shared.chat.state.phase = 'waiting'
      if (cause === 'account') f.shared.user.currentUser = { id: '2' }
      pending.resolve({ ...sent, sequence: '1', mode: 'DEMO', action: 'SPEAK', text: 'stale-output' }); await flush()
      assert.doesNotMatch(text(f.root), /stale-output|private words/)
      if (cause === 'account') assert.equal(checkbox(f).props.checked, false)
    } finally { await f.destroy() }
  }
})
test('overlapping real router guards cannot let old cancellation settle latest navigation intent', async () => {
  const f = await fixture(); const first = deferred(); const second = deferred()
  try {
    await change(checkbox(f), true)
    f.router.beforeEach(to => to.name === 'companion' ? first.promise : to.name === 'picture-detail' ? second.promise : true)
    const old = f.router.push('/companion'); await flush()
    const latest = f.router.push('/picture/2'); await flush()
    first.resolve(false); await old; await flush()
    assert.match(text(f.root), /当前页面或操作期间暂停接收/)
    await change(checkbox(f), true); assert.equal(checkbox(f).props.checked, false)
    second.resolve(false); await latest; await flush()
    assert.deepEqual(f.calls, [])
    assert.doesNotMatch(text(f.root), /当前页面或操作期间暂停接收/)
  } finally { first.resolve(false); second.resolve(false); await f.destroy() }
})
