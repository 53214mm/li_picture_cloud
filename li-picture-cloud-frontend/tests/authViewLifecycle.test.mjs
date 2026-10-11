import test from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { setImmediate } from 'node:timers/promises'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, ref } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

// Compile the real scripts and templates, mount them through a real RouterView,
// and run the real Pinia user store. Only HTTP API exports are substituted.
// Keeping setup bindings also lets us detect writes to an already-unmounted
// view, which a stopped Vue render effect would otherwise hide from DOM checks.
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const sourceUrl = path => new URL(`../src/${path}`, import.meta.url).href
const account = { userAccount: 'test-user', userPassword: 'password-123' }
const registeredAccount = { ...account, checkPassword: account.userPassword }
const loggedInUser = { id: '42', userAccount: account.userAccount, userRole: 'user' }
let sequence = 0

// The production text v-model directive expects these two host class names.
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

async function compileFixture(overrides = {}) {
  const fixtureNumber = ++sequence
  const apiUrl = moduleUrl(`
    export const fixtureNumber = ${fixtureNumber};
    export const handlers = {};
    export const calls = [];
    ${['userLogin', 'userRegister', 'getCurrentUser', 'userLogout'].map(name => `
      export function ${name}(...args) {
        calls.push({ name: '${name}', args });
        if (!handlers.${name}) throw new Error('Unexpected API call: ${name}');
        return handlers.${name}(...args);
      }
    `).join('\n')}
  `)
  const api = await import(apiUrl)
  Object.assign(api.handlers, {
    userLogin: async () => loggedInUser,
    userRegister: async () => '42',
    ...overrides
  })
  const aliases = {
    vue: import.meta.resolve('vue'),
    pinia: import.meta.resolve('pinia'),
    'vue-router': import.meta.resolve('vue-router'),
    '@/api/user': apiUrl
  }
  const resolveImports = content => content.replace(/from (['"])([^'"]+)\1/g, (_, _quote, specifier) => {
    const resolved = aliases[specifier]
      || (specifier.startsWith('@/utils/') ? sourceUrl(`${specifier.slice(2)}.js`) : null)
    assert.ok(resolved, `Unexpected dependency: ${specifier}`)
    return `from ${JSON.stringify(resolved)}`
  })
  const storeUrl = moduleUrl(resolveImports(await readFile(new URL('../src/stores/user.js', import.meta.url), 'utf8')))
  aliases['@/stores/user'] = storeUrl
  async function compile(name) {
    const filename = new URL(`../src/views/${name}View.vue`, import.meta.url)
    const { descriptor, errors } = parse(await readFile(filename, 'utf8'), { filename: filename.pathname })
    assert.deepEqual(errors, [], `${name}View must parse`)
    const id = `auth-lifecycle-${fixtureNumber}-${name}`
    const script = compileScript(descriptor, { id, genDefaultAs: '__sfc__' })
    const template = compileTemplate({
      id, filename: filename.pathname, source: descriptor.template.content,
      compilerOptions: { bindingMetadata: script.bindings, hoistStatic: false }
    })
    assert.deepEqual(template.errors, [], `${name}View template must compile`)
    return (await import(moduleUrl(resolveImports(`${script.content}\n${template.code}\n__sfc__.render = render;\nexport default __sfc__;`)))).default
  }
  return {
    ...api,
    useUserStore: (await import(storeUrl)).useUserStore,
    Login: await compile('Login'),
    Register: await compile('Register')
  }
}

function hostNode(tag, text = '') {
  const listeners = new Map()
  return {
    tag, tagName: tag.toUpperCase(), text, props: {}, children: [], parent: null, value: '',
    get textContent() { return tag === '#comment' ? '' : this.text + this.children.map(child => child.textContent).join('') },
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
    else if (key === 'type') node.type = value
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
function find(root, tag) {
  const node = descendants(root).find(node => node.tag === tag)
  assert.ok(node, `${tag} must be rendered; got ${text(root)}`)
  return node
}
function calls(fixture, name) { return fixture.calls.filter(call => call.name === name) }
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function flush() {
  await nextTick()
  await setImmediate()
  await nextTick()
}
async function fillForm(fixture) {
  const inputs = descendants(fixture.root).filter(node => node.tag === 'input')
  const values = [account.userAccount, account.userPassword, account.userPassword]
  for (let index = 0; index < inputs.length; index++) {
    inputs[index].value = values[index]
    inputs[index].dispatch('input')
  }
  await flush()
}
function submit(fixture) {
  // Submit the actual form event, including repeated keyboard/programmatic
  // submits that can occur even when the visual button is disabled.
  const form = find(fixture.root, 'form')
  return form.props.onSubmit({ preventDefault() {}, target: form })
}
async function navigate(fixture, to) {
  await fixture.router.push(to)
  await flush()
}
async function advance(t, milliseconds) { t.mock.timers.tick(milliseconds); await flush() }
async function historyBack(fixture) {
  const navigation = new Promise(resolve => {
    const remove = fixture.router.afterEach(() => { remove(); resolve() })
  })
  fixture.router.back()
  await navigation
  await flush()
}
async function mount(t, initial = '/login', overrides = {}) {
  const fixture = await compileFixture(overrides)
  const root = hostNode('root')
  const activeView = ref(null)
  const placeholder = { render: () => h('main', '选择的新页面') }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: fixture.Login },
      { path: '/register', name: 'register', component: fixture.Register },
      { path: '/gallery', name: 'gallery', component: placeholder },
      { path: '/space/my', name: 'my-space', component: placeholder },
      { path: '/upload', name: 'picture-upload', component: placeholder },
      { path: '/:pathMatch(.*)*', name: 'unavailable', component: placeholder }
    ]
  })
  const pinia = createPinia()
  const app = renderer.createApp({
    render: () => h(RouterView, {}, {
      default: ({ Component }) => Component ? h(Component, { ref: activeView }) : null
    })
  })
  app.use(pinia)
  app.use(router)
  await router.push(initial)
  await router.isReady()
  app.mount(root)
  await flush()
  const navigations = []
  const removeAfterEach = router.afterEach((to, from, failure) => {
    if (!failure) navigations.push({ to: to.fullPath, from: from.fullPath })
  })
  let mounted = true
  const unmount = () => {
    if (!mounted) return
    mounted = false
    removeAfterEach()
    app.unmount()
  }
  t.after(unmount)
  return {
    ...fixture, root, router, navigations, unmount,
    store: fixture.useUserStore(pinia),
    get instance() { return activeView.value?.$ },
    get state() { return activeView.value?.$?.setupState }
  }
}

for (const view of ['login', 'register']) {
  for (const outcome of ['success', 'failure']) {
    test(`R14 ${view} ${outcome} after route unmount cannot mutate the abandoned view or navigate`, async t => {
      t.mock.timers.enable({ apis: ['setTimeout'] })
      const pending = deferred()
      const apiName = view === 'login' ? 'userLogin' : 'userRegister'
      const fixture = await mount(t, `/${view}`, { [apiName]: () => pending.promise })
      await fillForm(fixture)
      const abandoned = fixture.state
      const instance = fixture.instance
      const submission = submit(fixture)
      await flush()
      assert.equal(find(fixture.root, 'button').props.disabled, true)
      await navigate(fixture, '/gallery?chosen=manual#pictures')
      assert.equal(instance.isUnmounted, true, 'navigation must really unmount the auth component')
      if (outcome === 'success') pending.resolve(view === 'login' ? loggedInUser : '42')
      else pending.reject(new Error('过期请求失败'))
      await submission
      await flush()
      await advance(t, 1500)
      assert.equal(fixture.router.currentRoute.value.fullPath, '/gallery?chosen=manual#pictures')
      assert.equal(abandoned.error, '', 'unmounted view must not receive an obsolete error')
      if (view === 'register') assert.equal(abandoned.success, false, 'unmounted registration must not start success/countdown')
      if (view === 'login' && outcome === 'success') {
        assert.deepEqual(fixture.store.currentUser, loggedInUser, 'view cleanup must not cancel completed session authentication')
        assert.equal(fixture.store.authReady, true)
      }
      assert.equal(calls(fixture, apiName).length, 1)
      assert.deepEqual(fixture.navigations.map(event => event.to), ['/gallery?chosen=manual#pictures'])
    })
  }
}

test('R14 registration countdown is cancelled by later navigation and does not add a history entry', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const fixture = await mount(t, '/register')
  await fillForm(fixture)
  await submit(fixture)
  await flush()
  assert.match(text(fixture.root), /注册成功/)
  await advance(t, 800)
  await navigate(fixture, '/gallery?chosen=manual')
  await advance(t, 700)
  assert.equal(fixture.router.currentRoute.value.fullPath, '/gallery?chosen=manual')
  assert.deepEqual(fixture.navigations.map(event => event.to), ['/gallery?chosen=manual'])
  await historyBack(fixture)
  assert.equal(fixture.router.currentRoute.value.path, '/register')
  assert.doesNotMatch(text(fixture.root), /注册成功/)
  assert.equal(find(fixture.root, 'button').props.disabled, false)
  assert.equal(calls(fixture, 'userRegister').length, 1)
})

for (const view of ['login', 'register']) {
  for (const outcome of ['success', 'failure']) {
    test(`R14 fresh ${view} mount ignores late old ${outcome} while its own request is pending`, async t => {
      t.mock.timers.enable({ apis: ['setTimeout'] })
      const oldPending = deferred()
      const newPending = deferred()
      let requests = 0
      const apiName = view === 'login' ? 'userLogin' : 'userRegister'
      const fixture = await mount(t, `/${view}?redirect=/space/my`, {
        [apiName]: () => ++requests === 1 ? oldPending.promise : newPending.promise
      })
      await fillForm(fixture)
      const oldState = fixture.state
      const oldInstance = fixture.instance
      const oldSubmission = submit(fixture)
      await navigate(fixture, '/gallery')
      await navigate(fixture, `/${view}?redirect=/upload#fresh`)
      assert.equal(oldInstance.isUnmounted, true)
      assert.notEqual(fixture.instance, oldInstance)
      assert.equal(find(fixture.root, 'input').value, '')
      await fillForm(fixture)
      const newSubmission = submit(fixture)
      if (outcome === 'success') oldPending.resolve(view === 'login' ? loggedInUser : '42')
      else oldPending.reject(new Error('上一次请求失败'))
      await oldSubmission
      await advance(t, 1500)
      assert.equal(fixture.router.currentRoute.value.fullPath, `/${view}?redirect=/upload#fresh`)
      assert.equal(fixture.state.error, '')
      assert.equal(oldState.error, '')
      assert.equal(find(fixture.root, 'button').props.disabled, true, 'old completion must not release the fresh request')
      if (view === 'register') {
        assert.equal(oldState.success, false)
        assert.equal(fixture.state.success, false)
      }
      if (view === 'login' && outcome === 'success') assert.deepEqual(fixture.store.currentUser, loggedInUser)
      newPending.resolve(view === 'login' ? loggedInUser : '43')
      await newSubmission
      await flush()
      if (view === 'register') await advance(t, 1500)
      assert.equal(fixture.router.currentRoute.value.path, view === 'login' ? '/upload' : '/login')
      assert.equal(calls(fixture, apiName).length, 2)
    })
  }
}

for (const view of ['login', 'register']) {
  for (const outcome of ['success', 'failure']) {
    test(`R14 reused ${view} view suppresses obsolete ${outcome} after query/hash navigation and permits retry`, async t => {
      t.mock.timers.enable({ apis: ['setTimeout'] })
      const pending = deferred()
      let requests = 0
      const apiName = view === 'login' ? 'userLogin' : 'userRegister'
      const fixture = await mount(t, `/${view}?redirect=/space/my`, {
        [apiName]: () => ++requests === 1 ? pending.promise : Promise.resolve(view === 'login' ? loggedInUser : '43')
      })
      await fillForm(fixture)
      const originalInstance = fixture.instance
      const firstSubmission = submit(fixture)
      await navigate(fixture, `/${view}?redirect=/upload#new-choice`)
      assert.equal(fixture.instance, originalInstance, 'query/hash navigation must reuse the actual component')
      await submit(fixture)
      const countWhilePending = calls(fixture, apiName).length
      if (outcome === 'success') pending.resolve(view === 'login' ? loggedInUser : '42')
      else pending.reject(new Error('旧地址的请求失败'))
      await firstSubmission
      await advance(t, 1500)
      assert.equal(countWhilePending, 1, 'same-component route changes must not unlock an in-flight submission')
      assert.equal(fixture.router.currentRoute.value.fullPath, `/${view}?redirect=/upload#new-choice`)
      assert.equal(fixture.state.error, '')
      assert.equal(find(fixture.root, 'button').props.disabled, false, 'a reused view must recover after its obsolete request settles')
      if (view === 'register') assert.equal(fixture.state.success, false)
      await submit(fixture)
      await flush()
      if (view === 'register') await advance(t, 1500)
      assert.equal(fixture.router.currentRoute.value.path, view === 'login' ? '/upload' : '/login')
      assert.equal(calls(fixture, apiName).length, 2)
    })
  }
}

test('R14 registration query/hash navigation cancels its countdown while preserving the successful submission lock', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const fixture = await mount(t, '/register?step=old')
  await fillForm(fixture)
  const originalInstance = fixture.instance
  await submit(fixture)
  await flush()
  assert.match(text(fixture.root), /注册成功/)
  await advance(t, 1000)
  await navigate(fixture, '/register?step=new#details')
  assert.equal(fixture.instance, originalInstance)
  await advance(t, 500)
  assert.equal(fixture.router.currentRoute.value.fullPath, '/register?step=new#details')
  assert.match(text(fixture.root), /注册成功/)
  assert.doesNotMatch(text(fixture.root), /正在前往登录页/)
  assert.equal(find(fixture.root, 'button').props.disabled, true)
  await submit(fixture)
  assert.equal(calls(fixture, 'userRegister').length, 1)
})

for (const view of ['login', 'register']) {
  test(`R14 ${view} blocks same-tick and later duplicate form submissions while pending`, async t => {
    t.mock.timers.enable({ apis: ['setTimeout'] })
    const pending = deferred()
    const apiName = view === 'login' ? 'userLogin' : 'userRegister'
    const fixture = await mount(t, `/${view}`, { [apiName]: () => pending.promise })
    await fillForm(fixture)
    const first = submit(fixture)
    const sameTick = submit(fixture)
    await flush()
    assert.equal(find(fixture.root, 'button').props.disabled, true)
    const later = submit(fixture)
    const requestCount = calls(fixture, apiName).length
    pending.resolve(view === 'login' ? loggedInUser : '42')
    await Promise.all([first, sameTick, later])
    await flush()
    assert.equal(requestCount, 1, 'the handler itself must prevent duplicate API writes')
    assert.deepEqual(calls(fixture, apiName)[0].args, [view === 'login' ? account : registeredAccount])
  })
}

test('R14 registration retains one success countdown, disables resubmission, and redirects at exactly 1.5 seconds', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const fixture = await mount(t, '/register')
  await fillForm(fixture)
  await submit(fixture)
  await flush()
  assert.match(text(fixture.root), /注册成功/)
  assert.equal(find(fixture.root, 'button').props.disabled, true, 'successful registration must remain locked during its countdown')
  await advance(t, 1000)
  await submit(fixture)
  assert.equal(calls(fixture, 'userRegister').length, 1, 'countdown form submissions cannot create another account')
  await advance(t, 499)
  assert.equal(fixture.router.currentRoute.value.path, '/register')
  await advance(t, 1)
  assert.equal(fixture.router.currentRoute.value.path, '/login')
  assert.deepEqual(fixture.navigations.map(event => event.to), ['/login'])
  await advance(t, 1500)
  assert.deepEqual(fixture.navigations.map(event => event.to), ['/login'])
})

test('R14 successful login keeps the existing safe redirect rules and completes the real session store', async t => {
  const cases = [
    [undefined, '/space/my'],
    ['/gallery?q=forest#images', '/gallery?q=forest#images'],
    ['/upload?spaceId=9223372036854775806', '/upload?spaceId=9223372036854775806'],
    [['/gallery', '/upload'], '/space/my'],
    ['//evil.test', '/space/my'],
    ['https://evil.test', '/space/my'],
    ['/\\evil.test', '/space/my'],
    ['/%2f%2fevil.test', '/space/my'],
    ['/gallery\n', '/space/my'],
    ['/login?redirect=/gallery', '/space/my'],
    ['/register', '/space/my'],
    ['/missing', '/space/my']
  ]
  for (const [redirect, destination] of cases) {
    await t.test(JSON.stringify(redirect) || 'no redirect', async t => {
      const fixture = await mount(t, { path: '/login', query: redirect === undefined ? {} : { redirect } })
      await fillForm(fixture)
      await submit(fixture)
      await flush()
      assert.equal(fixture.router.currentRoute.value.fullPath, destination)
      assert.deepEqual(fixture.store.currentUser, loggedInUser)
      assert.equal(fixture.store.authReady, true)
      assert.equal(fixture.store.isLoggedIn, true)
      assert.deepEqual(calls(fixture, 'userLogin').map(call => call.args), [[account]])
      assert.deepEqual(fixture.navigations.map(event => event.to), [destination])
    })
  }
})

for (const view of ['login', 'register']) {
  test(`R14 current ${view} failure still renders an error and permits a successful retry`, async t => {
    t.mock.timers.enable({ apis: ['setTimeout'] })
    let requests = 0
    const apiName = view === 'login' ? 'userLogin' : 'userRegister'
    const fixture = await mount(t, `/${view}`, {
      [apiName]: async () => {
        if (++requests === 1) throw new Error('暂时无法完成，请重试')
        return view === 'login' ? loggedInUser : '42'
      }
    })
    await fillForm(fixture)
    await submit(fixture)
    await flush()
    assert.match(text(fixture.root), /暂时无法完成，请重试/)
    assert.equal(find(fixture.root, 'button').props.disabled, false)
    assert.equal(fixture.router.currentRoute.value.path, `/${view}`)
    await submit(fixture)
    await flush()
    if (view === 'register') await advance(t, 1500)
    assert.equal(fixture.router.currentRoute.value.path, view === 'login' ? '/space/my' : '/login')
    assert.equal(calls(fixture, apiName).length, 2)
  })
}

for (const change of ['leave', 'update']) {
  for (const confirmed of [true, false]) {
    test(`R14 login completion cannot replace a pending ${change} navigation (${confirmed ? 'confirmed' : 'cancelled'})`, async t => {
      const pending = deferred()
      const navigationDecision = deferred()
      const guardEntered = deferred()
      const fixture = await mount(t, '/login?redirect=/upload', { userLogin: () => pending.promise })
      const destination = change === 'leave' ? '/gallery?manual=yes' : '/login?redirect=/gallery#manual'
      const removeGuard = fixture.router.beforeResolve(to => {
        if (to.fullPath !== destination) return
        guardEntered.resolve()
        return navigationDecision.promise
      })
      t.after(() => { removeGuard(); navigationDecision.resolve(false) })
      await fillForm(fixture)
      const submission = submit(fixture)
      const navigation = fixture.router.push(destination)
      await guardEntered.promise
      pending.resolve(loggedInUser)
      await submission
      await flush()
      assert.equal(fixture.router.currentRoute.value.fullPath, '/login?redirect=/upload', 'request completion must not replace a navigation waiting in beforeResolve')
      assert.deepEqual(fixture.store.currentUser, loggedInUser)
      assert.equal(find(fixture.root, 'button').props.disabled, false)
      assert.deepEqual(fixture.navigations, [])
      navigationDecision.resolve(confirmed)
      await navigation
      await flush()
      assert.equal(fixture.router.currentRoute.value.fullPath, confirmed ? destination : '/login?redirect=/upload')
      if (!confirmed) {
        assert.equal(fixture.state.error, '')
        await submit(fixture)
        await flush()
        assert.equal(fixture.router.currentRoute.value.path, '/upload', 'cancelled navigation must leave login usable for an explicit retry')
        assert.equal(calls(fixture, 'userLogin').length, 2)
      } else {
        assert.deepEqual(fixture.navigations.map(event => event.to), [destination])
      }
    })
  }
}

for (const change of ['leave', 'update']) {
  for (const confirmed of [true, false]) {
    test(`R14 registration countdown cannot replace a pending ${change} navigation (${confirmed ? 'confirmed' : 'cancelled'})`, async t => {
      t.mock.timers.enable({ apis: ['setTimeout'] })
      const navigationDecision = deferred()
      const guardEntered = deferred()
      const fixture = await mount(t, '/register?step=old')
      const destination = change === 'leave' ? '/gallery?manual=yes' : '/register?step=new#manual'
      const removeGuard = fixture.router.beforeResolve(to => {
        if (to.fullPath !== destination) return
        guardEntered.resolve()
        return navigationDecision.promise
      })
      t.after(() => { removeGuard(); navigationDecision.resolve(false) })
      await fillForm(fixture)
      await submit(fixture)
      await flush()
      assert.match(text(fixture.root), /注册成功/)
      const navigation = fixture.router.push(destination)
      await guardEntered.promise
      await advance(t, 1500)
      assert.equal(fixture.router.currentRoute.value.fullPath, '/register?step=old', 'timer must not replace the navigation waiting in beforeResolve')
      assert.match(text(fixture.root), /注册成功/)
      assert.doesNotMatch(text(fixture.root), /正在前往登录页/, 'cancelled countdown must show accurate manual-login guidance')
      assert.equal(find(fixture.root, 'button').props.disabled, true)
      await submit(fixture)
      assert.equal(calls(fixture, 'userRegister').length, 1)
      assert.deepEqual(fixture.navigations, [])
      navigationDecision.resolve(confirmed)
      await navigation
      await flush()
      assert.equal(fixture.router.currentRoute.value.fullPath, confirmed ? destination : '/register?step=old')
      if (!confirmed) {
        await advance(t, 3000)
        assert.equal(fixture.router.currentRoute.value.path, '/register')
        const loginLink = descendants(fixture.root).find(node => node.tag === 'a' && node.props.href === '/login')
        assert.ok(loginLink, 'successful registration must retain its manual login link')
        await loginLink.props.onClick({ preventDefault() {}, button: 0, currentTarget: loginLink })
        await flush()
        assert.equal(fixture.router.currentRoute.value.path, '/login')
        assert.equal(calls(fixture, 'userRegister').length, 1)
      } else {
        assert.deepEqual(fixture.navigations.map(event => event.to), [destination])
      }
    })
  }
}

for (const confirmed of [true, false]) {
  test(`R14 registration request completing during a pending navigation never starts a stale countdown (${confirmed ? 'confirmed' : 'cancelled'})`, async t => {
    t.mock.timers.enable({ apis: ['setTimeout'] })
    const pending = deferred()
    const navigationDecision = deferred()
    const guardEntered = deferred()
    const fixture = await mount(t, '/register', { userRegister: () => pending.promise })
    const removeGuard = fixture.router.beforeResolve(to => {
      if (to.path !== '/gallery') return
      guardEntered.resolve()
      return navigationDecision.promise
    })
    t.after(() => { removeGuard(); navigationDecision.resolve(false) })
    await fillForm(fixture)
    const submission = submit(fixture)
    const navigation = fixture.router.push('/gallery')
    await guardEntered.promise
    pending.resolve('42')
    await submission
    await advance(t, 1500)
    assert.equal(fixture.router.currentRoute.value.path, '/register')
    assert.match(text(fixture.root), /注册成功/)
    assert.equal(find(fixture.root, 'button').props.disabled, true)
    assert.deepEqual(fixture.navigations, [])
    navigationDecision.resolve(confirmed)
    await navigation
    await flush()
    assert.equal(fixture.router.currentRoute.value.path, confirmed ? '/gallery' : '/register')
    await advance(t, 1500)
    assert.equal(fixture.router.currentRoute.value.path, confirmed ? '/gallery' : '/register')
    assert.equal(calls(fixture, 'userRegister').length, 1)
  })
}
