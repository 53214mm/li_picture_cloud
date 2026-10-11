import test from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { setImmediate } from 'node:timers/promises'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, ref } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { siteNavigationIntent } from '../src/presentation/sitePresenceNavigation.js'

// Compile and mount the production SFC through a real RouterView. Substitute
// only API/store/modal boundaries; retain setup state to detect abandoned writes.
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const sourceUrl = path => new URL(`../src/${path}`, import.meta.url).href
const pictureId = '9223372036854775806'
const otherId = '9223372036854775805'
const accountId = '42'
const savedPicture = { id: pictureId, userId: accountId, spaceId: '77', name: '当前获准图片', url: '/picture.webp', reviewStatus: 0 }
const apiNames = ['getPictureVOById', 'getPictureTagCategory', 'getMyPicturePermissions', 'getSpaceVOById', 'editPicture', 'deletePicture', 'reviewPicture', 'uploadPicture']
let fixtureNumber = 0
const originalGlobals = new Map(['Document', 'ShadowRoot', 'window', 'alert', 'confirm'].map(name => [name, Object.getOwnPropertyDescriptor(globalThis, name)]))
test.before(() => {
  globalThis.Document = class Document {}
  globalThis.ShadowRoot = class ShadowRoot {}
  globalThis.window = { addEventListener() {}, removeEventListener() {} }
  globalThis.alert = () => assert.fail('A stale request must not alert')
  globalThis.confirm = () => true
})
test.after(() => {
  for (const [name, descriptor] of originalGlobals) {
    if (descriptor) Object.defineProperty(globalThis, name, descriptor)
    else delete globalThis[name]
  }
})

async function compileFixture(overrides) {
  const boundaryUrl = moduleUrl(`
    import { reactive } from ${JSON.stringify(import.meta.resolve('vue'))};
    export const fixtureNumber = ${++fixtureNumber};
    export const handlers = {};
    export const calls = [];
    export const editorInstances = [];
    export const SITE_PRESENCE_KEY = Symbol('site-presence');
    export const COMPANION_UI_ENABLED = true;
    export const userStore = reactive({ currentUser: { id: '${accountId}', userRole: 'admin' },
      get isLoggedIn() { return !!this.currentUser; }, get isAdmin() { return this.currentUser?.userRole === 'admin'; } });
    export const useUserStore = () => userStore;
    export const useCompanionInteractionStore = () => ({ startDrag() {}, cancelDrag() {}, open() {} });
    ${apiNames.map(name => `export function ${name}(...args) {
      calls.push({ name: '${name}', args });
      if (!handlers.${name}) throw new Error('Unexpected API call: ${name}');
      return handlers.${name}(...args);
    }`).join('\n')}
  `)
  const boundary = await import(boundaryUrl)
  Object.assign(boundary.handlers, {
    getPictureVOById: async id => ({ ...savedPicture, id }),
    getPictureTagCategory: async () => ({ categoryList: ['风景'] }),
    getMyPicturePermissions: async () => ['picture:view', 'picture:edit'],
    getSpaceVOById: async () => ({ id: '77', spaceType: 0 }),
    ...overrides
  })
  const stub = moduleUrl('export default { inheritAttrs: false, render() { return null; } };')
  const editorStub = moduleUrl(`
    import { editorInstances } from ${JSON.stringify(boundaryUrl)};
    export default { inheritAttrs: false, emits: ['save', 'close'],
      setup(_props, { emit }) { editorInstances.push({ emitSave: blob => emit('save', blob) }); return () => null; } };
  `)
  const aliases = {
    vue: import.meta.resolve('vue'), 'vue-router': import.meta.resolve('vue-router'),
    '@/components/ShareModal.vue': stub, '@/components/AddToSpaceButton.vue': stub, '@/components/ImageEditModal.vue': editorStub,
    '@/presentation/sitePresence': boundaryUrl, '@/config/features': boundaryUrl
  }
  const filename = new URL('../src/views/PictureDetailView.vue', import.meta.url)
  const { descriptor, errors } = parse(await readFile(filename, 'utf8'), { filename: filename.pathname })
  assert.deepEqual(errors, [])
  const id = `picture-detail-${fixtureNumber}`
  const script = compileScript(descriptor, { id, genDefaultAs: '__sfc__' })
  const template = compileTemplate({
    id, filename: filename.pathname, source: descriptor.template.content,
    compilerOptions: { bindingMetadata: script.bindings, hoistStatic: false }
  })
  assert.deepEqual(template.errors, [])
  const content = `${script.content}\n${template.code}\n__sfc__.render = render;\nexport default __sfc__;`
    .replace(/from (['"])([^'"]+)\1/g, (_, _quote, specifier) => {
      const resolved = aliases[specifier]
        || (/^@\/(api|stores)\//.test(specifier) ? boundaryUrl : null)
        || (/^@\/(utils|constants|presentation)\//.test(specifier) ? sourceUrl(`${specifier.slice(2)}.js`) : null)
      assert.ok(resolved, `Unexpected dependency: ${specifier}`)
      return `from ${JSON.stringify(resolved)}`
    })
  return { ...boundary, component: (await import(moduleUrl(content))).default }
}

function hostNode(tag, content = '') {
  const listeners = new Map()
  return {
    tag, tagName: tag.toUpperCase(), text: content, props: {}, children: [], parent: null,
    value: '', selected: false, selectedIndex: -1,
    get textContent() { return tag === '#comment' ? '' : this.text + this.children.map(child => child.textContent).join('') },
    get options() { return descendants(this).filter(node => node.tag === 'option') },
    getRootNode() { return this.parent?.getRootNode() || this },
    addEventListener(name, fn) { listeners.set(name, [...listeners.get(name) || [], fn]) },
    removeEventListener(name, fn) { listeners.set(name, (listeners.get(name) || []).filter(item => item !== fn)) }
  }
}
const body = hostNode('body')
const renderer = createRenderer({
  createElement: tag => hostNode(tag), createText: content => hostNode('#text', content),
  createComment: content => hostNode('#comment', content),
  setText(node, content) { node.text = content },
  setElementText(node, content) { node.text = content; node.children = [] },
  patchProp(node, key, _previous, value) {
    node.props[key] = value
    if (key === 'value') { node.value = value; node._value = value }
    else if (['type', 'selected', 'multiple'].includes(key)) node[key] = value
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
  nextSibling(node) { return node.parent?.children[node.parent.children.indexOf(node) + 1] || null },
  querySelector: selector => selector === 'body' ? body : null
})
function descendants(node) { return [node, ...node.children.flatMap(descendants)] }
function text(node) { return node.textContent.replace(/\s+/g, ' ').trim() }
function calls(fixture, name) { return fixture.calls.filter(call => call.name === name) }
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function flush() { await nextTick(); await setImmediate(); await nextTick() }
async function navigate(fixture, destination) { await fixture.router.push(destination); await flush() }
async function mount(t, overrides = {}, initial = `/picture/${pictureId}`, earlyGuard) {
  const fixture = await compileFixture(overrides)
  const root = hostNode('root')
  const activeView = ref(null)
  const router = createRouter({
    history: createMemoryHistory(), routes: [
      { path: '/picture/:id', name: 'picture-detail', component: fixture.component },
      { path: '/gallery', name: 'gallery', component: { render: () => h('main', '图库') } }
    ]
  })
  router.beforeEach(to => siteNavigationIntent(to))
  if (earlyGuard) router.beforeEach(earlyGuard)
  const events = []
  const app = renderer.createApp({ render: () => h(RouterView, {}, {
    default: ({ Component }) => Component ? h(Component, { ref: activeView }) : null
  }) })
  app.use(router)
  app.provide(fixture.SITE_PRESENCE_KEY, { pictureOpened: (...args) => events.push(args) })
  await router.push(initial); await router.isReady(); app.mount(root); await flush()
  let mounted = true
  const unmount = () => { if (mounted) { mounted = false; app.unmount() } }
  t.after(unmount)
  return { ...fixture, root, router, events, unmount,
    get instance() { return activeView.value?.$ }, get state() { return activeView.value?.$?.setupState }
  }
}

test('detail loads before optional metadata/permissions and emits only a zero-payload current opening', async t => {
  const meta = deferred(); const access = deferred()
  const f = await mount(t, { getPictureTagCategory: () => meta.promise, getMyPicturePermissions: () => access.promise })
  assert.match(text(f.root), /当前获准图片/)
  assert.equal(f.state.loading, false)
  assert.deepEqual(f.events, [[]])
  assert.deepEqual(calls(f, 'getPictureVOById').map(call => call.args), [[pictureId]])
  meta.resolve({ categoryList: ['建筑'] }); access.resolve(['picture:view']); await flush()
  assert.deepEqual(f.state.categoryList, ['建筑'])
  assert.deepEqual(f.state.permissions, ['picture:view'])
  assert.equal(f.events.length, 1)
})

for (const outcome of ['success', 'failure']) {
  test(`same-component ID navigation discards stale ${outcome} and loads the new exact ID`, async t => {
    const old = deferred(); const current = deferred()
    const f = await mount(t, { getPictureVOById: id => id === pictureId ? old.promise : current.promise })
    const instance = f.instance
    await navigate(f, `/picture/${otherId}`)
    assert.equal(f.instance, instance, 'RouterView must actually reuse the component')
    assert.deepEqual(calls(f, 'getPictureVOById').map(call => call.args[0]), [pictureId, otherId])
    current.resolve({ ...savedPicture, id: otherId, name: '新图片' }); await flush()
    if (outcome === 'success') old.resolve(savedPicture)
    else old.reject(new Error('旧错误'))
    await flush()
    assert.equal(f.state.picture.id, otherId)
    assert.match(text(f.root), /新图片/)
    assert.doesNotMatch(text(f.root), /旧错误|当前获准图片/)
    assert.equal(f.events.length, 1)
  })

  test(`account replacement discards stale ${outcome} before any picture/event/permission write`, async t => {
    const old = deferred(); const current = deferred(); let reads = 0
    const f = await mount(t, { getPictureVOById: () => ++reads === 1 ? old.promise : current.promise })
    f.userStore.currentUser = { id: '43', userRole: 'user' }
    assert.equal(f.state.picture, null, 'Account invalidation is synchronous')
    if (outcome === 'success') old.resolve(savedPicture)
    else old.reject(new Error('旧账号错误'))
    await flush()
    assert.equal(f.state.picture, null)
    assert.equal(f.state.loading, true)
    assert.deepEqual(f.events, [])
    assert.equal(calls(f, 'getMyPicturePermissions').length, 0)
    current.resolve({ ...savedPicture, name: '新账号获准图片' }); await flush()
    assert.match(text(f.root), /新账号获准图片/)
    assert.equal(f.events.length, 1)
  })

  test(`unmount rejects late ${outcome} and metadata without changing retained setup state`, async t => {
    const request = deferred(); const metadata = deferred()
    const f = await mount(t, { getPictureVOById: () => request.promise, getPictureTagCategory: () => metadata.promise })
    const state = f.state
    f.unmount()
    const loadingAfterUnmount = state.loading
    if (outcome === 'success') request.resolve(savedPicture)
    else request.reject(new Error('离页错误'))
    metadata.resolve({ categoryList: ['旧分类'] }); await flush()
    assert.equal(state.picture, null)
    assert.equal(state.loading, loadingAfterUnmount)
    assert.equal(state.detailError, '')
    assert.deepEqual(state.categoryList, [])
    assert.deepEqual(f.events, [])
    assert.equal(calls(f, 'getMyPicturePermissions').length, 0)
  })
}

test('failed, missing, mismatched and unsafe detail IDs never signal an opening', async t => {
  for (const result of [null, { ...savedPicture, id: otherId }, { ...savedPicture, id: Number(pictureId) }, new Error('无权查看')]) {
    await t.test(String(result?.id || result), async t => {
      const f = await mount(t, { getPictureVOById: async () => { if (result instanceof Error) throw result; return result } })
      assert.equal(f.state.picture, null)
      assert.equal(f.state.loading, false)
      assert.ok(f.state.detailError)
      assert.deepEqual(f.events, [])
      assert.equal(calls(f, 'getMyPicturePermissions').length, 0)
    })
  }
  const f = await mount(t, {}, '/picture/invalid-id')
  assert.deepEqual(calls(f, 'getPictureVOById'), [])
  assert.deepEqual(f.events, [])
})

test('stale permission/space and metadata replies cannot restore previous-account controls', async t => {
  const oldPermission = deferred(); const oldSpace = deferred(); const oldMeta = deferred()
  let permissions = 0; let spaces = 0; let metadata = 0
  const f = await mount(t, {
    getMyPicturePermissions: () => ++permissions === 1 ? oldPermission.promise : Promise.resolve(['picture:view']),
    getSpaceVOById: () => ++spaces === 1 ? oldSpace.promise : Promise.resolve({ spaceType: 1 }),
    getPictureTagCategory: () => ++metadata === 1 ? oldMeta.promise : Promise.resolve({ categoryList: ['当前分类'] })
  })
  f.state.showShare = true; f.state.showImageEditor = true; f.state.showFullscreen = true
  f.state.openEditModal()
  f.userStore.currentUser = { id: '43', userRole: 'user' }; await flush()
  oldPermission.resolve(['picture:edit']); oldSpace.resolve({ spaceType: 0 }); oldMeta.resolve({ categoryList: ['旧分类'] }); await flush()
  assert.deepEqual(f.state.permissions, ['picture:view'])
  assert.equal(f.state.pictureSpaceType, 1)
  assert.equal(f.state.canEdit, false)
  assert.equal(f.state.canOfferToCompanion, false)
  assert.deepEqual(f.state.categoryList, ['当前分类'])
  for (const name of ['showShare', 'showImageEditor', 'showFullscreen', 'showEditModal']) assert.equal(f.state[name], false)
})

for (const destination of ['/gallery', `/picture/${otherId}`, `/picture/${pictureId}?view=other`]) {
  test(`pending/canceled navigation to ${destination} invalidates callbacks and recovers without event replay`, async t => {
    const first = deferred(); let reads = 0
    const f = await mount(t, { getPictureVOById: () => ++reads === 1 ? first.promise : Promise.resolve(savedPicture) })
    const guard = deferred()
    const remove = f.router.beforeEach(to => to.fullPath === destination ? guard.promise : true)
    const navigation = f.router.push(destination); await flush()
    first.resolve(savedPicture); await flush()
    assert.equal(f.state.picture, null)
    assert.deepEqual(f.events, [])
    guard.resolve(false); await navigation; await flush()
    assert.equal(f.state.picture.id, pictureId)
    assert.equal(f.events.length, 1, 'A canceled first opening may emit after its fresh authorized read')
    remove()
    const stop = f.router.beforeEach(() => false)
    await f.router.push(destination); await flush(); stop()
    assert.equal(f.state.picture.id, pictureId)
    assert.equal(f.events.length, 1, 'Recovery of an already-open picture must not replay the opening')
  })
}

const mutations = {
  review: { api: 'reviewPicture', invoke: state => state.handleReview(1) },
  edit: { api: 'editPicture', invoke: state => { state.openEditModal(); return state.handleEdit() } },
  image: { api: 'uploadPicture', invoke: state => { state.openImageEditor(); return state.onImageEditSave(new globalThis.Blob(['picture'], { type: 'image/png' })) } },
  delete: { api: 'deletePicture', invoke: state => state.handleDelete() }
}
for (const [name, mutation] of Object.entries(mutations)) {
  for (const outcome of ['success', 'failure']) {
    test(`${name} ${outcome} after account switch never refreshes, alerts, closes a new modal or navigates`, async t => {
      const write = deferred()
      const f = await mount(t, { [mutation.api]: () => write.promise })
      const pending = mutation.invoke(f.state)
      assert.equal(calls(f, mutation.api).length, 1)
      const submitted = calls(f, mutation.api)[0].args[0]
      assert.equal(name === 'image' ? submitted.get('id') : name === 'delete' ? submitted : submitted.id, pictureId)
      f.userStore.currentUser = { id: '43', userRole: 'admin' }; await flush()
      f.state.openEditModal()
      const beforeReads = calls(f, 'getPictureVOById').length
      if (outcome === 'success') write.resolve(true)
      else write.reject(new Error('旧操作失败'))
      await pending; await flush()
      assert.equal(f.router.currentRoute.value.name, 'picture-detail')
      assert.equal(calls(f, 'getPictureVOById').length, beforeReads)
      assert.equal(f.state.showEditModal, true)
      assert.equal(f.state.modalError, '')
      assert.equal(f.events.length, 2, 'Only authorized account loads may signal openings')
    })
  }
}

test('mutation refresh is guarded across newer route and does not replay a picture-open event', async t => {
  const refresh = deferred(); let reads = 0
  const f = await mount(t, {
    editPicture: async () => true,
    getPictureVOById: async id => ++reads === 2 ? refresh.promise : { ...savedPicture, id }
  })
  f.state.openEditModal(); const pending = f.state.handleEdit(); await flush()
  assert.equal(reads, 2)
  assert.equal(f.events.length, 1)
  await navigate(f, `/picture/${otherId}`)
  refresh.resolve({ ...savedPicture, name: '旧刷新结果' }); await pending; await flush()
  assert.equal(f.state.picture.id, otherId)
  assert.doesNotMatch(text(f.root), /旧刷新结果/)
  assert.equal(f.events.length, 2)
})

test('current edit and review retain existing refresh behavior without extra presence events', async t => {
  const f = await mount(t, { editPicture: async () => true, reviewPicture: async () => true })
  f.state.openEditModal(); await f.state.handleEdit(); await flush()
  await f.state.handleReview(1); await flush()
  assert.equal(calls(f, 'getPictureVOById').length, 3)
  assert.equal(f.state.saving, false)
  assert.equal(f.state.reviewing, false)
  assert.equal(f.state.showEditModal, false)
  assert.deepEqual(f.events, [[]])
})


test('first router intent invalidates same-component callbacks before an earlier async authentication guard', async t => {
  const old = deferred(); const auth = deferred(); let reads = 0
  const f = await mount(t, {
    getPictureVOById: () => ++reads === 1 ? old.promise : Promise.resolve({ ...savedPicture, id: otherId })
  }, `/picture/${pictureId}`, to => to.params.id === otherId ? auth.promise : true)
  const pending = f.router.push(`/picture/${otherId}`); await flush()
  old.resolve(savedPicture); await flush()
  assert.equal(f.state.picture, null)
  assert.deepEqual(f.events, [])
  assert.equal(reads, 1)
  auth.resolve(true); await pending; await flush()
  assert.equal(f.state.picture.id, otherId)
  assert.equal(f.events.length, 1)
})

test('an older canceled navigation cannot settle a newer pending navigation or revive old detail reads', async t => {
  const old = deferred(); const firstGuard = deferred(); const secondGuard = deferred(); let reads = 0
  const f = await mount(t, {
    getPictureVOById: () => ++reads === 1 ? old.promise : Promise.resolve(savedPicture)
  })
  f.router.beforeEach(to => to.params.id === otherId ? firstGuard.promise : to.name === 'gallery' ? secondGuard.promise : true)
  const first = f.router.push(`/picture/${otherId}`); await flush()
  const second = f.router.push('/gallery'); await flush()
  firstGuard.resolve(true); await first; await flush()
  assert.equal(reads, 1, 'An older failure must not reload while the newer navigation is pending')
  old.resolve(savedPicture); await flush()
  assert.equal(f.state.picture, null)
  assert.deepEqual(f.events, [])
  secondGuard.resolve(false); await second; await flush()
  assert.equal(reads, 2)
  assert.equal(f.state.picture.id, pictureId)
  assert.deepEqual(f.events, [[]])
})

for (const [name, mutation] of Object.entries(mutations)) {
  for (const outcome of ['success', 'failure']) {
    test(`${name} ${outcome} after unmount has no late state write, refresh, alert or redirect`, async t => {
      const write = deferred()
      const f = await mount(t, { [mutation.api]: () => write.promise })
      const state = f.state
      const pending = mutation.invoke(state)
      f.unmount()
      const snapshot = [state.picture, state.loading, state.saving, state.reviewing, state.modalError, state.showImageEditor, state.showEditModal]
      if (outcome === 'success') write.resolve(true)
      else write.reject(new Error('已离页操作失败'))
      await pending; await flush()
      assert.deepEqual([state.picture, state.loading, state.saving, state.reviewing, state.modalError, state.showImageEditor, state.showEditModal], snapshot)
      assert.equal(calls(f, 'getPictureVOById').length, 1)
      assert.equal(f.router.currentRoute.value.name, undefined, 'Unmounted router must not navigate again')
      assert.deepEqual(f.events, [[]])
    })
  }

  test(`${name} completion during pending canceled navigation cannot refresh or redirect`, async t => {
    const write = deferred(); const guard = deferred()
    const f = await mount(t, { [mutation.api]: () => write.promise })
    const pending = mutation.invoke(f.state)
    f.router.beforeEach(() => guard.promise)
    const navigation = f.router.push(`/picture/${otherId}`); await flush()
    write.resolve(true); await pending; await flush()
    assert.equal(f.state.picture, null)
    assert.equal(calls(f, 'getPictureVOById').length, 1)
    assert.equal(f.router.currentRoute.value.params.id, pictureId)
    guard.resolve(false); await navigation; await flush()
    assert.equal(f.state.picture.id, pictureId)
    assert.equal(calls(f, 'getPictureVOById').length, 2)
    assert.deepEqual(f.events, [[]])
  })
}


test('an old editor async export cannot replace a new picture after its route and editor changed', async t => {
  const f = await mount(t, { uploadPicture: async () => true })
  f.state.openImageEditor(); await flush()
  const oldEditor = f.editorInstances[0]
  await navigate(f, `/picture/${otherId}`)
  f.state.openImageEditor(); await flush()
  assert.equal(f.editorInstances.length, 2, 'A changed picture must have a fresh modal lifetime')
  const blob = new globalThis.Blob(['picture'], { type: 'image/png' })
  oldEditor.emitSave(blob); await flush()
  assert.equal(calls(f, 'uploadPicture').length, 0)
  f.editorInstances[1].emitSave(blob); await flush()
  assert.equal(calls(f, 'uploadPicture').length, 1)
  assert.equal(calls(f, 'uploadPicture')[0].args[0].get('id'), otherId)
})

test('closing and reopening an editor rejects the prior upload callback and async export', async t => {
  const upload = deferred()
  const f = await mount(t, { uploadPicture: () => upload.promise })
  f.state.openImageEditor(); await flush()
  const oldEditor = f.editorInstances[0]
  const blob = new globalThis.Blob(['picture'], { type: 'image/png' })
  const pending = f.state.onImageEditSave(blob)
  f.state.showImageEditor = false; await flush()
  f.state.openImageEditor(); await flush()
  oldEditor.emitSave(blob); await flush()
  assert.equal(calls(f, 'uploadPicture').length, 1)
  upload.resolve(true); await pending; await flush()
  assert.equal(f.state.showImageEditor, true)
  assert.equal(calls(f, 'getPictureVOById').length, 1)
})
