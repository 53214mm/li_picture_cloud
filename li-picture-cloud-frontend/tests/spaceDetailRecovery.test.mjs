import test from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick } from 'vue'

// Mount the production views and PictureList with their actual templates,
// lifecycle hooks, directives and handlers. Only API/router/store boundaries
// and unrelated modal components are substituted; no production logic is copied.
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const sourceUrl = path => new URL(`../src/${path}`, import.meta.url).href
const detailPath = 'views/SpaceDetailView.vue'
const adminPath = 'views/AdminCompanionFeedRunsView.vue'
const spaceId = '9223372036854775806'
const otherSpaceId = '9223372036854775805'
const pictureId = '9223372036854775804'
const otherPictureId = '9223372036854775803'
const apiNames = [
  'getSpaceVOById', 'getMySpacePermissions', 'listMyTeamSpaces',
  'listPictureVOByPage', 'getPictureTagCategory', 'editPictureByBatch',
  'editPicture', 'deletePicture', 'uploadPicture', 'post', 'listCompanionFeedRuns'
]
const writeNames = new Set(['editPictureByBatch', 'editPicture', 'deletePicture', 'uploadPicture', 'post'])
const viewPermissions = ['picture:view']
const editPermissions = ['picture:view', 'picture:edit', 'picture:upload', 'picture:delete']
const savedPicture = { id: pictureId, name: '保留的图片名称', url: '/saved-picture.webp' }
const savedSpace = {
  id: spaceId, spaceName: '原有空间名称', spaceType: 0, spaceLevel: 0,
  userId: '42', totalCount: 1, maxCount: 100, totalSize: 1200, maxSize: 100000
}
let sequence = 0

// Vue's real text v-model checks these host classes when it changes a value.
// They are deliberately not a browser/document implementation.
const originalDocument = Object.getOwnPropertyDescriptor(globalThis, 'Document')
const originalShadowRoot = Object.getOwnPropertyDescriptor(globalThis, 'ShadowRoot')
test.before(() => {
  if (!globalThis.Document) globalThis.Document = class Document {}
  if (!globalThis.ShadowRoot) globalThis.ShadowRoot = class ShadowRoot {}
})
test.after(() => {
  if (originalDocument) Object.defineProperty(globalThis, 'Document', originalDocument)
  else delete globalThis.Document
  if (originalShadowRoot) Object.defineProperty(globalThis, 'ShadowRoot', originalShadowRoot)
  else delete globalThis.ShadowRoot
})

async function compileFixture(path, overrides = {}, routeId = spaceId) {
  const fixtureNumber = ++sequence
  const boundaryUrl = moduleUrl(`
    import { reactive } from ${JSON.stringify(import.meta.resolve('vue'))};
    export const fixtureNumber = ${fixtureNumber};
    export const handlers = {};
    export const calls = [];
    export const route = reactive({ params: { id: ${JSON.stringify(routeId)} } });
    export const router = { push: (...args) => calls.push({ name: 'push', args }),
      replace: async (...args) => calls.push({ name: 'replace', args }) };
    export const userStore = reactive({ currentUser: { id: '42' }, isAdmin: true,
      ensureCurrentUser: async () => ({ id: '42' }) });
    export const useRoute = () => route;
    export const useRouter = () => router;
    export const useUserStore = () => userStore;
    export const useCompanionInteractionStore = () => ({ startDrag() {}, cancelDrag() {} });
    ${apiNames.map(name => `export function ${name}(...args) {
      calls.push({ name: '${name}', args });
      if (!handlers['${name}']) throw new Error('Unexpected API call: ${name}');
      return handlers['${name}'](...args);
    }`).join('\n')}
    export default { post };
  `)
  const boundary = await import(boundaryUrl)
  Object.assign(boundary.handlers, {
    getSpaceVOById: async id => ({ ...savedSpace, id }),
    getMySpacePermissions: async () => viewPermissions,
    listMyTeamSpaces: async () => [],
    listPictureVOByPage: async () => ({ records: [], total: 0 }),
    getPictureTagCategory: async () => ({ categoryList: ['风景', '建筑'] }),
    listCompanionFeedRuns: async () => ({ records: [], total: 0 }),
    ...overrides
  })
  const stubUrl = moduleUrl(`export default { name: 'ModalBoundary', inheritAttrs: false, render() { return null; } };`)
  async function compile(path) {
    const filename = new URL(`../src/${path}`, import.meta.url)
    const { descriptor, errors } = parse(await readFile(filename, 'utf8'), { filename: filename.pathname })
    assert.deepEqual(errors, [], `${path} must parse`)
    const aliases = {
      vue: import.meta.resolve('vue'),
      'vue-router': boundaryUrl,
      '@/components/ShareModal.vue': stubUrl,
      '@/components/ImageEditModal.vue': stubUrl,
      '@/components/space/SpaceMemberPanel.vue': stubUrl
    }
    if (path === detailPath) aliases['@/components/PictureList.vue'] = await compile('components/PictureList.vue')
    const content = compileScript(descriptor, {
      id: `space-recovery-${fixtureNumber}`, inlineTemplate: true,
      // Keep static markup as ordinary nodes for this non-browser renderer.
      templateOptions: { compilerOptions: { hoistStatic: false } }
    }).content
      .replace(/from (['"])([^'"]+)\1/g, (_, _quote, specifier) => {
        const resolved = aliases[specifier]
          || (/^@\/(api|stores)\//.test(specifier) ? boundaryUrl : null)
          || (/^@\/(utils|constants)\//.test(specifier) ? sourceUrl(`${specifier.slice(2)}.js`) : null)
        assert.ok(resolved, `Unexpected dependency: ${specifier}`)
        return `from ${JSON.stringify(resolved)}`
      })
    return moduleUrl(content)
  }
  return { component: (await import(await compile(path))).default, ...boundary }
}

function hostNode(tag, text = '') {
  const listeners = new Map()
  return {
    tag, tagName: tag.toUpperCase(), text, props: {}, children: [], parent: null,
    value: '', checked: false, selected: false, selectedIndex: -1,
    get textContent() {
      return this.tag === '#comment' ? '' : this.text + this.children.map(child => child.textContent).join('')
    },
    get options() { return descendants(this).filter(node => node.tag === 'option') },
    getRootNode() { return this.parent?.getRootNode() || this },
    addEventListener(name, listener) {
      if (!listeners.has(name)) listeners.set(name, [])
      listeners.get(name).push(listener)
    },
    removeEventListener(name, listener) {
      listeners.set(name, (listeners.get(name) || []).filter(item => item !== listener))
    },
    dispatch(name) { for (const listener of listeners.get(name) || []) listener({ target: this }) }
  }
}
const renderer = createRenderer({
  createElement: tag => hostNode(tag),
  createText: text => hostNode('#text', text),
  createComment: text => hostNode('#comment', text),
  setText(node, text) { node.text = text },
  setElementText(node, text) { node.text = text; node.children = [] },
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
function hasClass(node, name) { return String(node.props.class || '').split(/\s+/).includes(name) }
function findClass(root, name) { return descendants(root).find(node => hasClass(node, name)) }
function findButton(root, label) { return descendants(root).find(node => node.tag === 'button' && text(node) === label) }
function button(root, label) {
  const node = findButton(root, label)
  assert.ok(node, `Button must be rendered: ${label}; got ${text(root)}`)
  return node
}
function input(root, label) {
  const node = descendants(root).find(node => node.tag === 'input' && (node.props['aria-label'] === label || node.props.placeholder === label))
  assert.ok(node, `Input must be rendered: ${label}`)
  return node
}
function calls(fixture, name) { return fixture.calls.filter(call => call.name === name) }
function assertNoWrites(fixture) {
  assert.deepEqual(fixture.calls.filter(call => writeNames.has(call.name)), [], 'reading and retrying must not issue a write')
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function flush() { for (let index = 0; index < 12; index++) await nextTick() }
function invoke(node, event, extra = {}) {
  assert.notEqual(node.props.disabled, true, 'Do not bypass the disabled UI control')
  return node.props[event]({ preventDefault() {}, stopPropagation() {}, target: node, ...extra })
}
async function click(node) { await invoke(node, 'onClick'); await flush() }
async function fill(node, value) { node.value = value; node.dispatch('input'); await flush() }
async function submit(root) {
  const form = descendants(root).find(node => node.tag === 'form')
  assert.ok(form)
  await invoke(form, 'onSubmit')
  await flush()
}
async function mount(t, path = detailPath, overrides = {}, routeId = spaceId) {
  const fixture = await compileFixture(path, overrides, routeId)
  const root = hostNode('root')
  const app = renderer.createApp(fixture.component)
  app.component('RouterLink', {
    props: ['to'],
    setup(props, { slots }) { return () => h('a', { href: props.to }, slots.default?.()) }
  })
  app.mount(root)
  let mounted = true
  const unmount = () => { if (mounted) { app.unmount(); mounted = false } }
  t.after(unmount)
  return { ...fixture, root, unmount }
}

async function selectAll(fixture) {
  await click(button(fixture.root, '批量管理'))
  const checkbox = descendants(fixture.root).find(node => node.tag === 'input' && node.props.type === 'checkbox')
  assert.ok(checkbox)
  await invoke(checkbox, 'onChange')
  await flush()
}

test('R14 batch editing preserves exact space and picture IDs above Number.MAX_SAFE_INTEGER', async t => {
  const fixture = await mount(t, detailPath, {
    getMySpacePermissions: async () => editPermissions,
    listPictureVOByPage: async () => ({ records: [savedPicture, { ...savedPicture, id: otherPictureId }], total: 2 }),
    editPictureByBatch: async () => true
  })
  await flush()
  assert.equal(calls(fixture, 'getSpaceVOById')[0].args[0], spaceId)
  assert.equal(calls(fixture, 'getMySpacePermissions')[0].args[0], spaceId)
  assert.equal(calls(fixture, 'listPictureVOByPage')[0].args[0].spaceId, spaceId)
  await selectAll(fixture)
  await fill(input(fixture.root, '分类'), '风景')
  await click(button(fixture.root, '应用'))
  assert.equal(calls(fixture, 'editPictureByBatch').length, 1)
  const payload = calls(fixture, 'editPictureByBatch')[0].args[0]
  assert.equal(payload.spaceId, spaceId)
  assert.deepEqual(payload.pictureIdList, [pictureId, otherPictureId])
  assert.equal(payload.category, '风景')
  assert.equal(typeof payload.spaceId, 'string')
  assert.ok(payload.pictureIdList.every(id => typeof id === 'string'))
})

test('R14 batch selection rejects unsafe numeric and malformed picture IDs without writes', async t => {
  for (const invalidId of [Number(pictureId), '3e4', '1.5', '-7', 'bad-id']) {
    await t.test(String(invalidId), async t => {
      const fixture = await mount(t, detailPath, {
        getMySpacePermissions: async () => editPermissions,
        listPictureVOByPage: async () => ({ records: [{ ...savedPicture, id: invalidId }], total: 1 })
      })
      await flush()
      await selectAll(fixture)
      const apply = findButton(fixture.root, '应用')
      if (apply) await click(apply)
      assertNoWrites(fixture)
      assert.match(text(fixture.root), /ID|标识|编号/)
    })
  }
})

test('R14 invalid space routes cannot trigger detail, permission, picture reads or writes', async t => {
  for (const invalidId of [Number(spaceId), '1e4', '1.5', '-7', 'bad-id']) {
    await t.test(String(invalidId), async t => {
      const fixture = await mount(t, detailPath, {}, invalidId)
      await flush()
      assert.equal(calls(fixture, 'getSpaceVOById').length, 0)
      assert.equal(calls(fixture, 'getMySpacePermissions').length, 0)
      assert.equal(calls(fixture, 'listPictureVOByPage').length, 0)
      assert.match(text(fixture.root), /ID|标识|编号/)
      assertNoWrites(fixture)
    })
  }
})

test('R14 admin picture filter transports a large decimal ID exactly and rejects invalid queries', async t => {
  const fixture = await mount(t, adminPath)
  await flush()
  assert.equal(calls(fixture, 'listCompanionFeedRuns').length, 1)
  await fill(input(fixture.root, '图片 ID'), pictureId)
  await submit(fixture.root)
  assert.equal(calls(fixture, 'listCompanionFeedRuns').length, 2)
  assert.equal(calls(fixture, 'listCompanionFeedRuns')[1].args[0].pictureId, pictureId)
  for (const invalidId of ['3e4', '1.5', '-1', 'not-a-number']) {
    await fill(input(fixture.root, '图片 ID'), invalidId)
    await submit(fixture.root)
    assert.equal(calls(fixture, 'listCompanionFeedRuns').length, 2, 'invalid filter must not broaden to an unfiltered request')
    assert.match(text(fixture.root), /ID|标识|编号/)
  }
  await fill(input(fixture.root, '图片 ID'), otherPictureId)
  await submit(fixture.root)
  assert.equal(calls(fixture, 'listCompanionFeedRuns').at(-1).args[0].pictureId, otherPictureId)
  assertNoWrites(fixture)
})

test('R14 delayed detail reads show loading, and failures offer a read-only explicit retry rather than absence', async t => {
  const detail = deferred()
  const fixture = await mount(t, detailPath, { getSpaceVOById: () => detail.promise })
  assert.match(text(fixture.root), /正在加载空间/)
  assert.doesNotMatch(text(fixture.root), /未找到空间|不存在|空间暂无图片/)
  assert.equal(calls(fixture, 'getMySpacePermissions').length, 0)
  detail.reject(new Error('空间读取暂时失败'))
  await flush()
  assert.match(text(fixture.root), /空间读取暂时失败/)
  assert.ok(descendants(fixture.root).some(node => node.props.role === 'alert'))
  assert.doesNotMatch(text(fixture.root), /正在加载空间|未找到空间|不存在|空间暂无图片/)
  assert.equal(calls(fixture, 'getMySpacePermissions').length, 0)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 0)
  const retry = deferred()
  fixture.handlers.getSpaceVOById = () => retry.promise
  const retryClick = click(button(fixture.root, '重新加载空间'))
  await flush()
  assert.equal(calls(fixture, 'getSpaceVOById').length, 2)
  assert.match(text(fixture.root), /正在加载空间/)
  assert.doesNotMatch(text(fixture.root), /空间读取暂时失败/)
  retry.resolve({ ...savedSpace })
  await retryClick
  assert.match(text(fixture.root), /原有空间名称/)
  assert.match(text(fixture.root), /空间暂无图片/)
  assert.equal(calls(fixture, 'getMySpacePermissions').length, 1)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 1)
  assertNoWrites(fixture)
})

test('R14 a successful absent detail is distinct from failed or loading detail', async t => {
  const detail = deferred()
  const fixture = await mount(t, detailPath, { getSpaceVOById: () => detail.promise })
  detail.resolve(null)
  await flush()
  assert.match(text(fixture.root), /未找到空间信息/)
  assert.doesNotMatch(text(fixture.root), /正在加载空间|重新加载空间|空间暂无图片/)
  assert.equal(calls(fixture, 'getMySpacePermissions').length, 0)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 0)
  assertNoWrites(fixture)
})

test('R14 permission failure preserves detail, denies picture reads and writes, and retries independently of empty state', async t => {
  const permission = deferred()
  const fixture = await mount(t, detailPath, { getMySpacePermissions: () => permission.promise })
  await flush()
  assert.match(text(fixture.root), /原有空间名称/)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 0)
  assert.equal(findButton(fixture.root, '批量管理'), undefined)
  assert.equal(findButton(fixture.root, '+ 上传图片'), undefined)
  permission.reject(new Error('无法读取空间权限'))
  await flush()
  assert.match(text(fixture.root), /原有空间名称.*无法读取空间权限/)
  assert.doesNotMatch(text(fixture.root), /未找到空间|不存在|空间暂无图片/)
  assert.equal(findButton(fixture.root, '重新加载空间'), undefined)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 0)
  const retry = deferred()
  fixture.handlers.getMySpacePermissions = () => retry.promise
  fixture.handlers.listPictureVOByPage = async () => ({ records: [savedPicture], total: 1 })
  const retryClick = click(button(fixture.root, '重新加载空间权限'))
  await flush()
  assert.equal(calls(fixture, 'getMySpacePermissions').length, 2)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 0)
  assert.equal(findButton(fixture.root, '批量管理'), undefined)
  retry.resolve(editPermissions)
  await retryClick
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 1)
  assert.match(text(fixture.root), /保留的图片名称/)
  assert.ok(button(fixture.root, '批量管理'))
  assert.doesNotMatch(text(fixture.root), /无法读取空间权限|空间暂无图片/)
  assertNoWrites(fixture)
})

test('R14 granted permissions without picture:view do not fabricate an empty gallery or expose mutations', async t => {
  const fixture = await mount(t, detailPath, { getMySpacePermissions: async () => [] })
  await flush()
  assert.match(text(fixture.root), /原有空间名称/)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 0)
  assert.equal(findClass(fixture.root, 'picture-list'), undefined)
  assert.equal(findButton(fixture.root, '批量管理'), undefined)
  assert.equal(findButton(fixture.root, '+ 上传图片'), undefined)
  assert.doesNotMatch(text(fixture.root), /空间暂无图片/)
  assertNoWrites(fixture)
})

test('R14 first picture read separates loading and failure from genuine empty and its retry stays read-only', async t => {
  const pictures = deferred()
  const fixture = await mount(t, detailPath, { listPictureVOByPage: () => pictures.promise })
  await flush()
  const list = findClass(fixture.root, 'picture-list')
  assert.ok(list)
  assert.match(text(fixture.root), /正在加载图片/)
  assert.doesNotMatch(text(fixture.root), /空间暂无图片/)
  pictures.reject(new Error('图片服务暂时不可用'))
  await flush()
  assert.match(text(fixture.root), /图片服务暂时不可用/)
  assert.doesNotMatch(text(fixture.root), /正在加载图片|空间暂无图片/)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 1, 'failure must not automatically retry')
  const retry = deferred()
  fixture.handlers.listPictureVOByPage = () => retry.promise
  const retryClick = click(button(fixture.root, '重新加载图片'))
  await flush()
  assert.equal(findClass(fixture.root, 'picture-list') === list, true)
  assert.match(text(fixture.root), /正在加载图片/)
  assert.doesNotMatch(text(fixture.root), /图片服务暂时不可用|空间暂无图片/)
  retry.resolve({ records: [], total: 0 })
  await retryClick
  await flush()
  assert.match(text(fixture.root), /空间暂无图片/)
  assert.doesNotMatch(text(fixture.root), /正在加载图片|图片服务暂时不可用/)
  assert.equal(calls(fixture, 'getSpaceVOById').length, 1)
  assert.equal(calls(fixture, 'getMySpacePermissions').length, 1)
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 2)
  assertNoWrites(fixture)
})

test('R14 picture refresh and retry preserve the actual list, controls and known picture nodes', async t => {
  const fixture = await mount(t, detailPath, {
    listPictureVOByPage: async () => ({ records: [savedPicture], total: 1 })
  })
  await flush()
  const list = findClass(fixture.root, 'picture-list')
  const card = findClass(fixture.root, 'gallery-card')
  const search = input(fixture.root, '搜索图片名称或简介…')
  const refresh = deferred()
  fixture.handlers.listPictureVOByPage = () => refresh.promise
  await fill(search, '保留的搜索词')
  await click(button(fixture.root, '搜索'))
  assert.match(text(fixture.root), /正在加载图片/)
  assert.match(text(fixture.root), /保留的图片名称/)
  assert.equal(findClass(fixture.root, 'picture-list') === list, true)
  assert.equal(findClass(fixture.root, 'gallery-card') === card, true)
  assert.equal(input(fixture.root, '搜索图片名称或简介…') === search, true)
  refresh.reject(new Error('刷新图片暂时失败'))
  await flush()
  assert.match(text(fixture.root), /刷新图片暂时失败.*正在显示上次成功加载的图片/)
  assert.match(text(fixture.root), /保留的图片名称/)
  assert.equal(findClass(fixture.root, 'gallery-card') === card, true)
  const retry = deferred()
  fixture.handlers.listPictureVOByPage = () => retry.promise
  const retryClick = click(button(fixture.root, '重新加载图片'))
  await flush()
  assert.equal(findClass(fixture.root, 'gallery-card') === card, true)
  assert.equal(input(fixture.root, '搜索图片名称或简介…') === search, true)
  assert.equal(search.value, '保留的搜索词')
  assert.equal(calls(fixture, 'listPictureVOByPage').at(-1).args[0].searchText, '保留的搜索词')
  retry.resolve({ records: [{ ...savedPicture, name: '更新后的图片名称' }], total: 1 })
  await retryClick
  await flush()
  assert.equal(findClass(fixture.root, 'gallery-card') === card, true)
  assert.match(text(fixture.root), /更新后的图片名称/)
  assert.doesNotMatch(text(fixture.root), /刷新图片暂时失败|正在加载图片/)
  assertNoWrites(fixture)
})

test('R14 changing routes while detail is pending gives the latest route sole ownership', async t => {
  const oldDetail = deferred()
  const fixture = await mount(t, detailPath, {
    getSpaceVOById: id => id === spaceId ? oldDetail.promise : Promise.resolve({ ...savedSpace, id, spaceName: '新路由空间' }),
    listPictureVOByPage: async () => ({ records: [{ ...savedPicture, name: '新路由图片' }], total: 1 })
  })
  fixture.route.params.id = otherSpaceId
  await flush()
  assert.match(text(fixture.root), /新路由空间.*新路由图片/)
  oldDetail.resolve({ ...savedSpace, spaceName: '过期的空间详情' })
  await flush()
  assert.match(text(fixture.root), /新路由空间.*新路由图片/)
  assert.doesNotMatch(text(fixture.root), /过期的空间详情/)
  assert.deepEqual(calls(fixture, 'getMySpacePermissions').map(call => call.args[0]), [otherSpaceId])
  assert.deepEqual(calls(fixture, 'listPictureVOByPage').map(call => call.args[0].spaceId), [otherSpaceId])
  assertNoWrites(fixture)
})

test('R14 a stale permission grant cannot authorize picture reads after route navigation', async t => {
  const oldPermission = deferred()
  const fixture = await mount(t, detailPath, {
    getMySpacePermissions: id => id === spaceId ? oldPermission.promise : Promise.resolve([])
  })
  await flush()
  fixture.route.params.id = otherSpaceId
  await flush()
  oldPermission.resolve(editPermissions)
  await flush()
  assert.equal(calls(fixture, 'listPictureVOByPage').length, 0)
  assert.equal(findClass(fixture.root, 'picture-list'), undefined)
  assert.equal(findButton(fixture.root, '批量管理'), undefined)
  assert.equal(findButton(fixture.root, '+ 上传图片'), undefined)
  assertNoWrites(fixture)
})

test('R14 out-of-order picture searches ignore both stale successes and stale failures', async t => {
  for (const lateOutcome of ['resolve', 'reject']) {
    await t.test(lateOutcome, async t => {
      const oldSearch = deferred(), newSearch = deferred()
      const fixture = await mount(t, detailPath, {
        listPictureVOByPage: query => query.searchText === '旧搜索' ? oldSearch.promise
          : query.searchText === '新搜索' ? newSearch.promise
            : Promise.resolve({ records: [savedPicture], total: 1 })
      })
      await flush()
      const search = input(fixture.root, '搜索图片名称或简介…')
      await fill(search, '旧搜索')
      await click(button(fixture.root, '搜索'))
      await fill(search, '新搜索')
      await click(button(fixture.root, '搜索'))
      newSearch.resolve({ records: [{ ...savedPicture, id: otherPictureId, name: '最新搜索结果' }], total: 1 })
      await flush()
      if (lateOutcome === 'resolve') oldSearch.resolve({ records: [{ ...savedPicture, name: '过期搜索结果' }], total: 1 })
      else oldSearch.reject(new Error('过期搜索错误'))
      await flush()
      assert.match(text(fixture.root), /最新搜索结果/)
      assert.doesNotMatch(text(fixture.root), /过期搜索|正在加载图片/)
      assertNoWrites(fixture)
    })
  }
})

test('R14 an old route picture response cannot repopulate a new route', async t => {
  const oldPictures = deferred()
  const fixture = await mount(t, detailPath, {
    getSpaceVOById: async id => ({ ...savedSpace, id, spaceName: id === spaceId ? '旧空间' : '新空间' }),
    listPictureVOByPage: query => query.spaceId === spaceId ? oldPictures.promise
      : Promise.resolve({ records: [{ ...savedPicture, name: '新空间图片' }], total: 1 })
  })
  await flush()
  fixture.route.params.id = otherSpaceId
  await flush()
  oldPictures.resolve({ records: [{ ...savedPicture, name: '旧空间图片' }], total: 1 })
  await flush()
  assert.match(text(fixture.root), /新空间.*新空间图片/)
  assert.doesNotMatch(text(fixture.root), /旧空间图片/)
  assertNoWrites(fixture)
})

test('R14 an account change clears old authorization and prevents pending reads from repopulating the view', async t => {
  const oldPictures = deferred()
  const fixture = await mount(t, detailPath, {
    getMySpacePermissions: async () => editPermissions,
    listPictureVOByPage: () => oldPictures.promise
  })
  await flush()
  assert.ok(button(fixture.root, '批量管理'))
  fixture.userStore.currentUser = null
  await flush()
  oldPictures.resolve({ records: [savedPicture], total: 1 })
  await flush()
  assert.equal(findClass(fixture.root, 'picture-list'), undefined)
  assert.equal(findButton(fixture.root, '批量管理'), undefined)
  assert.doesNotMatch(text(fixture.root), /保留的图片名称/)
  assert.equal(calls(fixture, 'getMySpacePermissions').length, 1)
  assertNoWrites(fixture)
})

test('R14 unmount ignores deferred detail, permission and picture callbacks without follow-up requests', async t => {
  for (const phase of ['detail', 'permission', 'picture']) {
    await t.test(phase, async t => {
      const pending = deferred()
      const name = { detail: 'getSpaceVOById', permission: 'getMySpacePermissions', picture: 'listPictureVOByPage' }[phase]
      const fixture = await mount(t, detailPath, { [name]: () => pending.promise })
      await flush()
      const before = fixture.calls.length
      fixture.unmount()
      pending.resolve({ detail: savedSpace, permission: editPermissions, picture: { records: [savedPicture], total: 1 } }[phase])
      await flush()
      assert.equal(fixture.root.children.length, 0)
      assert.equal(fixture.calls.length, before, 'late response must not start a follow-up read')
      assertNoWrites(fixture)
    })
  }
})

test('R14 batch submit is disabled in flight and an old-route success cannot refresh the new route', async t => {
  const batch = deferred()
  const fixture = await mount(t, detailPath, {
    getMySpacePermissions: async () => editPermissions,
    listPictureVOByPage: async () => ({ records: [savedPicture], total: 1 }),
    editPictureByBatch: () => batch.promise
  })
  await flush()
  await selectAll(fixture)
  const submission = click(button(fixture.root, '应用'))
  await flush()
  assert.equal(button(fixture.root, '正在应用…').props.disabled, true)
  assert.equal(calls(fixture, 'editPictureByBatch').length, 1)
  fixture.route.params.id = otherSpaceId
  await flush()
  const before = calls(fixture, 'listPictureVOByPage').length
  batch.resolve(true)
  await submission
  assert.equal(calls(fixture, 'listPictureVOByPage').length, before)
  assert.equal(findButton(fixture.root, '应用'), undefined)
  assert.ok(button(fixture.root, '批量管理'))
})

test('R14 safe integer IDs remain backward-compatible but batch transport uses canonical strings', async t => {
  const fixture = await mount(t, detailPath, {
    getMySpacePermissions: async () => editPermissions,
    listPictureVOByPage: async () => ({ records: [{ ...savedPicture, id: 17 }, savedPicture], total: 2 }),
    editPictureByBatch: async () => true
  }, 41)
  await flush()
  await selectAll(fixture)
  await click(button(fixture.root, '应用'))
  assert.equal(calls(fixture, 'getSpaceVOById')[0].args[0], '41')
  const payload = calls(fixture, 'editPictureByBatch')[0].args[0]
  assert.equal(payload.spaceId, '41')
  assert.deepEqual(payload.pictureIdList, ['17', pictureId])
})

test('R14 individual picture toggles preserve exact distinct neighboring long IDs', async t => {
  const fixture = await mount(t, detailPath, {
    getMySpacePermissions: async () => editPermissions,
    listPictureVOByPage: async () => ({ records: [savedPicture, { ...savedPicture, id: otherPictureId }], total: 2 }),
    editPictureByBatch: async () => true
  })
  await flush()
  await click(button(fixture.root, '批量管理'))
  const cardChecks = descendants(fixture.root).filter(node => hasClass(node, 'card-check'))
  const first = descendants(cardChecks[0]).find(node => node.tag === 'input')
  const second = descendants(cardChecks[1]).find(node => node.tag === 'input')
  await invoke(first, 'onChange')
  await flush()
  assert.equal(first.props.checked, true)
  assert.equal(second.props.checked, false)
  await invoke(second, 'onChange')
  await invoke(first, 'onChange')
  await flush()
  assert.equal(first.props.checked, false)
  assert.equal(second.props.checked, true)
  await click(button(fixture.root, '应用'))
  assert.deepEqual(calls(fixture, 'editPictureByBatch')[0].args[0].pictureIdList, [otherPictureId])
})

test('R14 admin invalid filters retain known rows and cannot claim a successful empty result', async t => {
  const run = { runId: 'run-1', userName: '原有用户', pictureId, pictureName: '原有日志图片', status: 'COMPLETED' }
  const fixture = await mount(t, adminPath, {
    listCompanionFeedRuns: async () => ({ records: [run], total: 1 })
  })
  await flush()
  const table = findClass(fixture.root, 'observation-table')
  for (const invalidId of ['9223372036854775808', '0', '01', '+1']) {
    await fill(input(fixture.root, '图片 ID'), invalidId)
    await submit(fixture.root)
    assert.equal(calls(fixture, 'listCompanionFeedRuns').length, 1)
    assert.equal(findClass(fixture.root, 'observation-table') === table, true)
    assert.match(text(fixture.root), /原有日志图片/)
    assert.doesNotMatch(text(fixture.root), /还没有匹配的喂养记录/)
  }
  await fill(input(fixture.root, '图片 ID'), `  ${pictureId}  `)
  await submit(fixture.root)
  assert.equal(calls(fixture, 'listCompanionFeedRuns').at(-1).args[0].pictureId, pictureId)
  assertNoWrites(fixture)
})

test('R14 the latest admin reload ignores older success and error responses', async t => {
  for (const lateOutcome of ['resolve', 'reject']) {
    await t.test(lateOutcome, async t => {
      const oldRuns = deferred(), newRuns = deferred()
      const fixture = await mount(t, adminPath, { listCompanionFeedRuns: () => oldRuns.promise })
      await flush()
      fixture.handlers.listCompanionFeedRuns = () => newRuns.promise
      await click(button(fixture.root, '重置'))
      newRuns.resolve({ records: [{ runId: 'new', pictureName: '最新喂养记录', status: 'COMPLETED' }], total: 1 })
      await flush()
      if (lateOutcome === 'resolve') oldRuns.resolve({ records: [{ runId: 'old', pictureName: '过期喂养记录' }], total: 1 })
      else oldRuns.reject(new Error('过期喂养读取错误'))
      await flush()
      assert.match(text(fixture.root), /最新喂养记录/)
      assert.doesNotMatch(text(fixture.root), /过期喂养|加载中|还没有匹配的喂养记录/)
      assertNoWrites(fixture)
    })
  }
})

test('R14 admin unmount ignores a pending read response', async t => {
  const runs = deferred()
  const fixture = await mount(t, adminPath, { listCompanionFeedRuns: () => runs.promise })
  await flush()
  const before = fixture.calls.length
  fixture.unmount()
  runs.resolve({ records: [{ runId: 'late', pictureName: '卸载后回调' }], total: 1 })
  await flush()
  assert.equal(fixture.root.children.length, 0)
  assert.equal(fixture.calls.length, before)
  assertNoWrites(fixture)
})
