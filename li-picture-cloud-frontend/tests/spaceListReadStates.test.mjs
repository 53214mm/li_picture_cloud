import test from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, nextTick, reactive } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

// Mount the compiled production views, SpaceCard, and AiAgentPanel, with real
// Vue directives, router links and grouping/format helpers. Only API, store and
// browser-storage/network boundaries are replaced; this is not browser QA.
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const sourceUrl = path => new URL(`../src/${path}`, import.meta.url).href
const apiNames = ['listSpaceVOByPage', 'listMyTeamSpaces', 'editSpace', 'updateSpace', 'deleteSpace', 'uploadPicture', 'useUserStore']
const writeNames = new Set(['editSpace', 'updateSpace', 'deleteSpace', 'uploadPicture', 'fetch', 'EventSource'])
let fixtureSequence = 0

async function compileComponent(path, overrides) {
  const boundaryUrl = moduleUrl(`
    export const fixture = ${++fixtureSequence};
    export const handlers = {};
    export const calls = [];
    ${apiNames.map(name => `export function ${name}(...args) {
      calls.push({ name: '${name}', args });
      if (!handlers['${name}']) throw new Error('Unexpected API call: ${name}');
      return handlers['${name}'](...args);
    }`).join('\n')}
    const saved = new Map();
    export const storage = {
      getItem: key => saved.get(key) || null,
      setItem: (key, value) => saved.set(key, value),
      removeItem: key => saved.delete(key)
    };
    export function fetch(...args) { calls.push({ name: 'fetch', args }); throw new Error('Unexpected network write'); }
    export function EventSource(...args) { calls.push({ name: 'EventSource', args }); throw new Error('Unexpected AI request'); }
    export function confirm(...args) { calls.push({ name: 'confirm', args }); return true; }
    export function alert(...args) { calls.push({ name: 'alert', args }); }
  `)
  const boundary = await import(boundaryUrl)
  Object.assign(boundary.handlers, overrides)
  const aliases = {
    vue: import.meta.resolve('vue'),
    'vue-router': import.meta.resolve('vue-router'),
    marked: import.meta.resolve('marked'),
    dompurify: import.meta.resolve('dompurify'),
    '@/constants/space': sourceUrl('constants/space.js'),
    '@/utils/spaceAccess': sourceUrl('utils/spaceAccess.js')
  }
  for (const alias of ['@/api/space', '@/api/spaceUser', '@/api/picture', '@/stores/user']) aliases[alias] = boundaryUrl
  async function compile(relativePath) {
    const filename = new URL(`../src/${relativePath}`, import.meta.url)
    const { descriptor, errors } = parse(await readFile(filename, 'utf8'), { filename: filename.pathname })
    assert.deepEqual(errors, [], `${relativePath} must parse`)
    let compiled = compileScript(descriptor, { id: `space-reads-${fixtureSequence}`, inlineTemplate: true }).content
      .replace(/from (['"])([^'"]+)\1/g, (_, _quote, specifier) => {
        assert.ok(aliases[specifier], `Unexpected dependency: ${specifier}`)
        return `from ${JSON.stringify(aliases[specifier])}`
      })
    if (relativePath === 'components/AiAgentPanel.vue') {
      compiled = `import { storage as localStorage, fetch, EventSource } from ${JSON.stringify(boundaryUrl)};\n${compiled}`
    }
    if (relativePath === 'views/SpaceManageView.vue') {
      compiled = `import { confirm, alert } from ${JSON.stringify(boundaryUrl)};\n${compiled}`
    }
    return moduleUrl(compiled)
  }
  if (path === 'views/MySpaceView.vue') {
    aliases['@/components/space/SpaceCard.vue'] = await compile('components/space/SpaceCard.vue')
    aliases['@/components/AiAgentPanel.vue'] = await compile('components/AiAgentPanel.vue')
  }
  return { component: (await import(await compile(path))).default, ...boundary }
}

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
function byId(root, id) { return descendants(root).find(node => node.props['data-testid'] === id) }
function byClass(root, name) { return descendants(root).find(node => String(node.props.class || '').split(' ').includes(name)) }
function button(root, label) {
  const found = descendants(root).find(node => node.tag === 'button' && text(node) === label)
  assert.ok(found, `Button must be rendered: ${label}`)
  return found
}
function card(root, name) {
  const found = descendants(root).find(node => String(node.props.class || '').split(' ').includes('space-card') && text(node).includes(name))
  assert.ok(found, `Space card must be rendered: ${name}`)
  return found
}
function alerts(root) { return descendants(root).filter(node => node.props.role === 'alert').map(text) }
function calls(fixture, name) { return fixture.calls.filter(call => call.name === name) }
function assertNoWrites(fixture) {
  assert.deepEqual(fixture.calls.filter(call => writeNames.has(call.name)), [], 'Loading/retry/refresh must not mutate data or start AI requests')
}
function assertSameNode(actual, expected) { assert.equal(actual === expected, true, 'Refresh must retain the rendered node, not remount it') }
async function flush() { for (let i = 0; i < 8; i++) await nextTick() }
async function click(node) {
  assert.notEqual(node.props.disabled, true, 'Do not bypass an actual disabled control')
  const result = node.props.onClick({ preventDefault() {}, stopPropagation() {}, button: 0 })
  await flush()
  return result
}
async function openCard(fixture, node) {
  const navigation = new Promise(resolve => {
    const remove = fixture.router.afterEach(() => { remove(); resolve() })
  })
  await click(node)
  await navigation
  await flush()
}

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function mount(t, path, overrides = {}, store = reactive({ currentUser: { id: '42' }, isLoggedIn: true, isAdmin: false })) {
  const fixture = await compileComponent(path, {
    listSpaceVOByPage: async () => ({ records: [], total: 0 }),
    listMyTeamSpaces: async () => [],
    useUserStore: () => store,
    ...overrides
  })
  const root = hostNode('root')
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }] })
  await router.push(path.includes('MySpace') ? '/space/my' : '/space')
  const app = renderer.createApp(fixture.component)
  app.use(router)
  app.mount(root)
  let mounted = true
  const unmount = () => { if (mounted) { app.unmount(); mounted = false } }
  t.after(unmount)
  return { ...fixture, root, store, router, unmount }
}

const managePath = 'views/SpaceManageView.vue'
const myPath = 'views/MySpaceView.vue'
const manageEmpty = '还没有空间，可以创建一个。'
const privateEmpty = '你还没有私有空间，可以创建一个用于个人图片管理。'
const ownedTeamEmpty = '你还没有创建团队空间。'
const joinedEmpty = '你还没有加入其他团队。'
const privateSpace = { id: '9223372036854775806', userId: '42', spaceName: '保留的私有空间 🌿', spaceType: 0, spaceLevel: 0, totalCount: 7, maxCount: 100, totalSize: 2048, maxSize: 104857600 }
const ownedTeam = { ...privateSpace, id: '9223372036854775805', spaceName: '保留的自建团队', spaceType: 1 }
const joinedTeam = { ...privateSpace, id: '9223372036854775804', userId: '9', spaceName: '保留的加入团队', spaceType: 1 }
const membership = { space: joinedTeam, spaceRole: 'viewer' }

function assertNoOwnedEmpty(root) {
  assert.ok(!text(root).includes(privateEmpty))
  assert.ok(!text(root).includes(ownedTeamEmpty))
}

test('R14/R16 space management shows delayed reads before genuine empty, scoped to the current user', async t => {
  const pending = deferred()
  const fixture = await mount(t, managePath, { listSpaceVOByPage: () => pending.promise })
  assert.match(text(fixture.root), /正在加载空间…/)
  assert.ok(!text(fixture.root).includes(manageEmpty))
  assert.equal(byClass(fixture.root, 'space-list'), undefined)
  assert.equal(button(fixture.root, '重新加载空间').props.disabled, true)
  pending.resolve({ records: [], total: 0 })
  await flush()
  assert.ok(text(fixture.root).includes(manageEmpty))
  assert.doesNotMatch(text(fixture.root), /正在加载/)
  assert.deepEqual(alerts(fixture.root), [])
  assert.deepEqual(calls(fixture, 'listSpaceVOByPage').map(call => call.args), [[{ current: 1, pageSize: 12, userId: '42' }]])
  assertNoWrites(fixture)
})

for (const failure of [new Error('空间服务暂时不可用'), null]) {
  test(`R14/R16 space management failure is not empty and explicit retry recovers (${failure ? 'server' : 'fallback'})`, async t => {
    const fixture = await mount(t, managePath, { listSpaceVOByPage: async () => { throw failure } })
    await flush()
    assert.deepEqual(alerts(fixture.root), [failure?.message || '加载空间失败，请重新加载。'])
    assert.ok(!text(fixture.root).includes(manageEmpty))
    const retry = button(fixture.root, '重新加载空间')
    const pending = deferred()
    fixture.handlers.listSpaceVOByPage = () => pending.promise
    await click(retry)
    // Even two event deliveries in the same render interval cannot duplicate a retry.
    retry.props.onClick()
    assert.equal(calls(fixture, 'listSpaceVOByPage').length, 2)
    assertSameNode(button(fixture.root, '重新加载空间'), retry)
    assert.equal(retry.props.disabled, true)
    assert.deepEqual(alerts(fixture.root), [])
    assert.ok(!text(fixture.root).includes(manageEmpty))
    pending.resolve({ records: [privateSpace], total: 1 })
    await flush()
    assert.match(text(fixture.root), /保留的私有空间 🌿/)
    const link = descendants(card(fixture.root, privateSpace.spaceName)).find(node => node.tag === 'a')
    assert.equal(link.props.href, `/space/${privateSpace.id}`)
    assert.equal(calls(fixture, 'listSpaceVOByPage').length, 2, 'No background retry or polling')
    assertNoWrites(fixture)
  })
}

for (const outcome of ['success', 'failure']) {
  test(`R14/R16 space management ${outcome} refresh keeps cards, edit controls and draft inputs mounted`, async t => {
    const snapshot = globalThis.structuredClone(privateSpace)
    const fixture = await mount(t, managePath, { listSpaceVOByPage: async () => ({ records: [privateSpace], total: 1 }) })
    await flush()
    const originalCard = card(fixture.root, privateSpace.spaceName)
    const edit = button(originalCard, '编辑'), remove = button(originalCard, '删除')
    const link = descendants(originalCard).find(node => node.tag === 'a' && text(node) === '查看图片')
    await click(edit)
    const input = descendants(fixture.root).find(node => node.tag === 'input')
    input.value = '尚未保存的名称'
    input.dispatch('input')
    await flush()
    const pending = deferred()
    fixture.handlers.listSpaceVOByPage = () => pending.promise
    await click(button(fixture.root, '重新加载空间'))
    const verifyNodes = () => {
      assertSameNode(card(fixture.root, privateSpace.spaceName), originalCard)
      assertSameNode(button(originalCard, '编辑'), edit)
      assertSameNode(button(originalCard, '删除'), remove)
      assertSameNode(descendants(originalCard).find(node => node.tag === 'a' && text(node) === '查看图片'), link)
      assertSameNode(descendants(fixture.root).find(node => node.tag === 'input'), input)
      assert.equal(input.value, '尚未保存的名称')
      assert.ok(!text(fixture.root).includes(manageEmpty))
    }
    verifyNodes()
    if (outcome === 'success') pending.resolve({ records: [{ ...privateSpace, totalCount: 8 }], total: 1 })
    else pending.reject(new Error('刷新读取失败'))
    await flush()
    verifyNodes()
    assert.deepEqual(alerts(fixture.root), outcome === 'failure' ? ['刷新读取失败'] : [])
    assert.deepEqual(privateSpace, snapshot)
    assertNoWrites(fixture)
  })
}

test('R14/R16 failed pagination preserves the displayed page and retries the intended page without relabeling old records', async t => {
  const fixture = await mount(t, managePath, { listSpaceVOByPage: async () => ({ records: [privateSpace], total: 13 }) })
  await flush()
  const original = card(fixture.root, privateSpace.spaceName)
  const pending = deferred()
  fixture.handlers.listSpaceVOByPage = () => pending.promise
  await click(button(fixture.root, '下一页'))
  assert.match(text(fixture.root), /第 2 页尚未加载，当前显示第 1 页/)
  assert.match(text(byClass(fixture.root, 'pagination')), /第 1 \/ 2 页/)
  assert.equal(button(fixture.root, '下一页').props.disabled, true)
  assertSameNode(card(fixture.root, privateSpace.spaceName), original)
  pending.reject(new Error('下一页读取失败'))
  await flush()
  assert.match(text(fixture.root), /第 2 页尚未加载，当前显示第 1 页/)
  assertSameNode(card(fixture.root, privateSpace.spaceName), original)
  fixture.handlers.listSpaceVOByPage = async () => ({ records: [ownedTeam], total: 13 })
  await click(button(fixture.root, '重新加载空间'))
  assert.match(text(byClass(fixture.root, 'pagination')), /第 2 \/ 2 页/)
  assert.ok(!text(fixture.root).includes(privateSpace.spaceName))
  assert.match(text(fixture.root), /保留的自建团队/)
  assert.deepEqual(calls(fixture, 'listSpaceVOByPage').map(call => call.args[0]), [
    { current: 1, pageSize: 12, userId: '42' },
    { current: 2, pageSize: 12, userId: '42' },
    { current: 2, pageSize: 12, userId: '42' }
  ])
  assertNoWrites(fixture)
})

test('R14/R16 my spaces resolves owned and joined reads independently, showing empty only after each success', async t => {
  const owned = deferred(), joined = deferred()
  const fixture = await mount(t, myPath, { listSpaceVOByPage: () => owned.promise, listMyTeamSpaces: () => joined.promise })
  assert.match(text(fixture.root), /正在加载已创建的空间….*正在加载加入的团队…/)
  assertNoOwnedEmpty(fixture.root)
  assert.ok(!text(fixture.root).includes(joinedEmpty))
  owned.resolve({ records: [] })
  await flush()
  assert.ok(text(fixture.root).includes(privateEmpty))
  assert.ok(text(fixture.root).includes(ownedTeamEmpty))
  assert.ok(!text(fixture.root).includes(joinedEmpty))
  assert.match(text(byId(fixture.root, 'joined-spaces')), /正在加载加入的团队…/)
  joined.resolve([])
  await flush()
  assert.ok(text(fixture.root).includes(joinedEmpty))
  assert.doesNotMatch(text(fixture.root), /正在加载/)
  assert.deepEqual(calls(fixture, 'listSpaceVOByPage').map(call => call.args), [[{ current: 1, pageSize: 20, userId: '42' }]])
  assert.deepEqual(calls(fixture, 'listMyTeamSpaces').map(call => call.args), [[]])
  assertNoWrites(fixture)
})

test('R14/R16 failed owned reads never claim empty while successfully joined teams remain usable', async t => {
  const fixture = await mount(t, myPath, {
    listSpaceVOByPage: async () => { throw new Error('已创建空间读取失败') },
    listMyTeamSpaces: async () => [membership]
  })
  await flush()
  assert.deepEqual(alerts(fixture.root), ['已创建空间读取失败'])
  assertNoOwnedEmpty(fixture.root)
  assert.ok(!text(fixture.root).includes(joinedEmpty))
  await openCard(fixture, card(fixture.root, joinedTeam.spaceName))
  assert.equal(fixture.router.currentRoute.value.path, `/space/${joinedTeam.id}`)
  assert.equal(calls(fixture, 'listSpaceVOByPage').length, 1)
  assert.equal(calls(fixture, 'listMyTeamSpaces').length, 1)
  assertNoWrites(fixture)
})

for (const target of ['owned', 'joined']) {
  test(`R14/R16 ${target} retry remains scoped, independent and read-only when both lists failed`, async t => {
    const fixture = await mount(t, myPath, {
      listSpaceVOByPage: () => { throw null },
      listMyTeamSpaces: () => { throw null }
    })
    await flush()
    assert.deepEqual(alerts(fixture.root), ['加载自己创建的空间失败', '加载加入的团队失败'])
    assertNoOwnedEmpty(fixture.root)
    assert.ok(!text(fixture.root).includes(joinedEmpty))
    const api = target === 'owned' ? 'listSpaceVOByPage' : 'listMyTeamSpaces'
    const otherApi = target === 'owned' ? 'listMyTeamSpaces' : 'listSpaceVOByPage'
    const label = target === 'owned' ? '重新加载已创建空间' : '重新加载加入的团队'
    const pending = deferred()
    fixture.handlers[api] = () => pending.promise
    const retry = button(fixture.root, label)
    const action = click(retry)
    retry.props.onClick()
    await flush()
    assert.equal(calls(fixture, api).length, 2)
    assert.equal(calls(fixture, otherApi).length, 1)
    assertSameNode(button(fixture.root, label), retry)
    assert.equal(retry.props.disabled, true)
    assert.deepEqual(alerts(fixture.root), [target === 'owned' ? '加载加入的团队失败' : '加载自己创建的空间失败'])
    pending.resolve(target === 'owned' ? { records: [ownedTeam] } : [membership])
    await action
    assert.ok(card(fixture.root, target === 'owned' ? ownedTeam.spaceName : joinedTeam.spaceName))
    assert.equal(calls(fixture, otherApi).length, 1)
    assertNoWrites(fixture)
  })
}

for (const outcome of ['success', 'failure']) {
  test(`R14/R16 my spaces ${outcome} refresh preserves all cards, scoped retry controls and the real AI draft`, async t => {
    const ownedRecords = [privateSpace, ownedTeam]
    const memberships = [membership]
    const snapshot = globalThis.structuredClone({ ownedRecords, memberships })
    const fixture = await mount(t, myPath, {
      listSpaceVOByPage: async () => ({ records: ownedRecords }),
      listMyTeamSpaces: async () => memberships
    })
    await flush()
    const originals = [privateSpace, ownedTeam, joinedTeam].map(space => card(fixture.root, space.spaceName))
    const clear = button(fixture.root, '清空对话')
    const input = descendants(fixture.root).find(node => node.tag === 'input' && node.props.placeholder === '输入消息…')
    assert.ok(input, 'The actual AiAgentPanel must be rendered')
    input.value = '未发送的草稿，不要丢失'
    input.dispatch('input')
    await flush()
    const owned = deferred(), joined = deferred()
    fixture.handlers.listSpaceVOByPage = () => owned.promise
    fixture.handlers.listMyTeamSpaces = () => joined.promise
    const actions = [click(button(fixture.root, '重新加载已创建空间')), click(button(fixture.root, '重新加载加入的团队'))]
    await flush()
    const verifyNodes = () => {
      for (const [index, space] of [privateSpace, ownedTeam, joinedTeam].entries()) assertSameNode(card(fixture.root, space.spaceName), originals[index])
      assertSameNode(button(fixture.root, '清空对话'), clear)
      assertSameNode(descendants(fixture.root).find(node => node.tag === 'input' && node.props.placeholder === '输入消息…'), input)
      assert.equal(input.value, '未发送的草稿，不要丢失')
      assertNoOwnedEmpty(fixture.root)
      assert.ok(!text(fixture.root).includes(joinedEmpty))
    }
    verifyNodes()
    if (outcome === 'success') {
      owned.resolve({ records: ownedRecords.map(space => ({ ...space, totalCount: 8 })) })
      joined.resolve([{ ...membership, space: { ...joinedTeam, totalCount: 8 } }])
    } else {
      owned.reject(new Error('已创建空间刷新失败'))
      joined.reject(new Error('加入团队刷新失败'))
    }
    await Promise.all(actions)
    verifyNodes()
    assert.deepEqual(alerts(fixture.root), outcome === 'success' ? [] : ['已创建空间刷新失败', '加入团队刷新失败'])
    assert.deepEqual({ ownedRecords, memberships }, snapshot, 'Do not rewrite returned content or membership roles')
    await openCard(fixture, originals[0])
    assert.equal(fixture.router.currentRoute.value.path, `/space/${privateSpace.id}`)
    assertNoWrites(fixture)
  })
}

for (const path of [managePath, myPath]) {
  for (const lateOutcome of ['success', 'failure']) {
    test(`R14/R16 ${path} ignores late ${lateOutcome} reads after a user change and never queries unscoped`, async t => {
      const oldOwned = deferred(), oldJoined = deferred(), newOwned = deferred(), newJoined = deferred()
      const fixture = await mount(t, path, { listSpaceVOByPage: () => oldOwned.promise, listMyTeamSpaces: () => oldJoined.promise })
      fixture.handlers.listSpaceVOByPage = () => newOwned.promise
      fixture.handlers.listMyTeamSpaces = () => newJoined.promise
      fixture.store.currentUser = { id: '99' }
      await flush()
      assert.equal(calls(fixture, 'listSpaceVOByPage').length, 2)
      if (lateOutcome === 'success') {
        oldOwned.resolve({ records: [privateSpace], total: 1 })
        oldJoined.resolve([membership])
      } else {
        oldOwned.reject(new Error('旧账号失败不应显示'))
        // SpaceManage does not request memberships.
        if (path === myPath) oldJoined.reject(new Error('旧团队失败不应显示'))
      }
      await flush()
      assert.doesNotMatch(text(fixture.root), /保留的私有空间|保留的加入团队|旧账号失败|旧团队失败/)
      assert.match(text(fixture.root), /正在加载/)
      assert.deepEqual(alerts(fixture.root), [])
      newOwned.resolve({ records: [{ ...privateSpace, userId: '99', spaceName: '新账号空间' }], total: 1 })
      newJoined.resolve([])
      await flush()
      assert.match(text(fixture.root), /新账号空间/)
      fixture.store.currentUser = null
      fixture.store.isLoggedIn = false
      await flush()
      assert.doesNotMatch(text(fixture.root), /新账号空间|保留的私有空间/)
      assert.match(text(fixture.root), /请先登录/)
      assert.deepEqual(calls(fixture, 'listSpaceVOByPage').map(call => call.args[0].userId), ['42', '99'])
      assert.equal(calls(fixture, 'listMyTeamSpaces').length, path === myPath ? 2 : 0)
      assertNoWrites(fixture)
    })
  }

  test(`R14/R16 ${path} performs no unscoped read before login and no automatic reads after unmount`, async t => {
    const fixture = await mount(t, path, {}, reactive({ currentUser: null, isLoggedIn: false, isAdmin: false }))
    await flush()
    assert.match(text(fixture.root), /请先登录/)
    assert.equal(calls(fixture, 'listSpaceVOByPage').length, 0)
    assert.equal(calls(fixture, 'listMyTeamSpaces').length, 0)
    const pending = deferred()
    fixture.handlers.listSpaceVOByPage = () => pending.promise
    fixture.store.currentUser = { id: '42' }
    fixture.store.isLoggedIn = true
    await flush()
    fixture.unmount()
    pending.resolve({ records: [privateSpace], total: 1 })
    await flush()
    assert.equal(fixture.root.children.length, 0)
    assert.equal(calls(fixture, 'listSpaceVOByPage').length, 1)
    assertNoWrites(fixture)
  })
}

for (const path of [managePath, myPath]) {
  test(`R14/R16 ${path} clears already-rendered account data before the next account read`, async t => {
    const fixture = await mount(t, path, {
      listSpaceVOByPage: async () => ({ records: [privateSpace, ownedTeam], total: 2 }),
      listMyTeamSpaces: async () => [membership]
    })
    await flush()
    assert.match(text(fixture.root), /保留的私有空间/)
    let input
    if (path === myPath) {
      input = descendants(fixture.root).find(node => node.props.placeholder === '输入消息…')
    } else {
      await click(button(card(fixture.root, privateSpace.spaceName), '编辑'))
      input = descendants(fixture.root).find(node => node.tag === 'input')
    }
    input.value = '旧账号未保存的输入'
    input.dispatch('input')
    const owned = deferred(), joined = deferred()
    fixture.handlers.listSpaceVOByPage = () => owned.promise
    fixture.handlers.listMyTeamSpaces = () => joined.promise
    fixture.store.currentUser = { id: '99' }
    await flush()
    assert.doesNotMatch(text(fixture.root), /保留的私有空间|保留的自建团队|保留的加入团队/)
    assert.ok(!descendants(fixture.root).includes(input), 'Inputs for the previous account must not remain mounted')
    assertNoOwnedEmpty(fixture.root)
    owned.resolve({ records: [{ ...privateSpace, userId: '99', spaceName: '新账号自己的空间' }], total: 1 })
    joined.resolve([])
    await flush()
    assert.match(text(fixture.root), /新账号自己的空间/)
    if (path === myPath) {
      const newInput = descendants(fixture.root).find(node => node.props.placeholder === '输入消息…')
      assert.equal(newInput.value, '')
      assert.equal(newInput === input, false)
    } else {
      assert.equal(byClass(fixture.root, 'modal-overlay'), undefined)
    }
    assert.deepEqual(calls(fixture, 'listSpaceVOByPage').map(call => call.args[0].userId), ['42', '99'])
    assertNoWrites(fixture)
  })

  test(`R14/R16 ${path} does not turn a failed refresh of a previously empty result into an empty claim`, async t => {
    const fixture = await mount(t, path)
    await flush()
    const label = path === myPath ? '重新加载已创建空间' : '重新加载空间'
    const empty = path === myPath ? privateEmpty : manageEmpty
    assert.ok(text(fixture.root).includes(empty))
    const pending = deferred()
    fixture.handlers.listSpaceVOByPage = () => pending.promise
    const action = click(button(fixture.root, label))
    await flush()
    assert.ok(!text(fixture.root).includes(empty))
    pending.reject(new Error('无法确认当前空间列表'))
    await action
    await flush()
    assert.ok(!text(fixture.root).includes(empty))
    assert.deepEqual(alerts(fixture.root), ['无法确认当前空间列表'])
    fixture.handlers.listSpaceVOByPage = async () => ({ records: [], total: 0 })
    await click(button(fixture.root, label))
    assert.ok(text(fixture.root).includes(empty))
    assert.deepEqual(alerts(fixture.root), [])
    assertNoWrites(fixture)
  })

  test(`R14/R16 ${path} refuses an empty user ID rather than issuing an unscoped read`, async t => {
    const fixture = await mount(t, path, {}, reactive({ currentUser: { id: '' }, isLoggedIn: true, isAdmin: false }))
    await flush()
    assert.match(text(fixture.root), /请先登录/)
    assert.equal(calls(fixture, 'listSpaceVOByPage').length, 0)
    assert.equal(calls(fixture, 'listMyTeamSpaces').length, 0)
    assertNoWrites(fixture)
  })
}

for (const actionName of ['editSpace', 'updateSpace', 'deleteSpace']) {
  for (const outcome of ['success', 'failure']) {
    test(`R14 space management ignores stale ${actionName} ${outcome} UI followups after an account change`, async t => {
      const pending = deferred()
      const store = reactive({ currentUser: { id: '42' }, isLoggedIn: true, isAdmin: actionName === 'updateSpace' })
      const fixture = await mount(t, managePath, {
        listSpaceVOByPage: async () => ({ records: [privateSpace], total: 1 }),
        [actionName]: () => pending.promise
      }, store)
      await flush()
      let action
      if (actionName === 'deleteSpace') {
        action = click(button(card(fixture.root, privateSpace.spaceName), '删除'))
      } else {
        await click(button(card(fixture.root, privateSpace.spaceName), '编辑'))
        action = click(button(fixture.root, '保存'))
      }
      await flush()
      assert.equal(calls(fixture, actionName).length, 1)
      const newSpace = { ...privateSpace, id: '9223372036854775803', userId: '99', spaceName: '新账号空间' }
      fixture.handlers.listSpaceVOByPage = async () => ({ records: [newSpace], total: 1 })
      store.currentUser = { id: '99' }
      await flush()
      await click(button(card(fixture.root, newSpace.spaceName), '编辑'))
      const modal = byClass(fixture.root, 'modal-overlay')
      const input = descendants(modal).find(node => node.tag === 'input')
      input.value = '新账号尚未保存的编辑'
      input.dispatch('input')
      if (outcome === 'success') pending.resolve({})
      else pending.reject(new Error('旧账号写入失败不得显示'))
      await action
      await flush()
      assertSameNode(byClass(fixture.root, 'modal-overlay'), modal)
      assertSameNode(descendants(modal).find(node => node.tag === 'input'), input)
      assert.equal(input.value, '新账号尚未保存的编辑')
      assert.doesNotMatch(text(fixture.root), /旧账号写入失败不得显示/)
      assert.equal(calls(fixture, 'alert').length, 0)
      assert.deepEqual(calls(fixture, 'listSpaceVOByPage').map(call => call.args[0].userId), ['42', '99'], 'An old-account write cannot cause a new-account reload')
      assert.deepEqual(fixture.calls.filter(call => writeNames.has(call.name)).map(call => call.name), [actionName], 'Only the explicit original mutation may run')
      assert.equal(calls(fixture, actionName)[0].args[0]?.id || calls(fixture, actionName)[0].args[0], privateSpace.id)
    })
  }
}
