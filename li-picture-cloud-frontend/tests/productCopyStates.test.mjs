import test from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick } from 'vue'

// Compile and mount the production SFCs, including their lifecycle hooks,
// watchers, v-model directives and event handlers. Only API/store boundaries
// are replaced. No DOM, network, component template or domain helper is mocked.
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const sourceUrl = path => new URL(`../src/${path}`, import.meta.url).href
const apiNames = [
  'createRecipeFromTemplate', 'deleteRecipe', 'disableRecipe', 'dryRunRecipe',
  'enableRecipe', 'executeRecipe', 'getRecipeDetail', 'listRecipeCapabilities',
  'listRecipeExecutions', 'listRecipes', 'listRecipeTemplates', 'publishRecipeVersion',
  'listSpaceVOByPage', 'listPictureVOByPageUncached', 'useUserStore',
  'fusionPreviewUrl', 'listFusionTasks', 'listEmojiCandidates', 'listEmojiTasks',
  'addMcpTool', 'createModelConnection', 'createModelCredential', 'deleteModelConnection',
  'deleteModelCredential', 'deleteModelRouting', 'disableMcpService', 'disableMcpTool',
  'disableModelConnection', 'enableMcpService', 'enableMcpTool', 'enableModelConnection',
  'getModelConnectionCapability', 'listMcpServices', 'listMcpTools', 'listModelConnections',
  'listModelCredentials', 'listModelRouting', 'listModelUsage', 'removeMcpTool',
  'rotateModelCredential', 'testModelConnection', 'upsertMcpService', 'upsertModelRouting'
]
const writeNames = new Set([
  'createRecipeFromTemplate', 'deleteRecipe', 'disableRecipe', 'dryRunRecipe',
  'enableRecipe', 'executeRecipe', 'publishRecipeVersion',
  'addMcpTool', 'createModelConnection', 'createModelCredential', 'deleteModelConnection',
  'deleteModelCredential', 'deleteModelRouting', 'disableMcpService', 'disableMcpTool',
  'disableModelConnection', 'enableMcpService', 'enableMcpTool', 'enableModelConnection',
  'removeMcpTool', 'rotateModelCredential', 'testModelConnection', 'upsertMcpService', 'upsertModelRouting'
])
let fixtureSequence = 0

async function compileComponent(path, overrides = {}) {
  const boundaryUrl = moduleUrl(`
    export const fixture = ${++fixtureSequence};
    export const handlers = {};
    export const calls = [];
    ${apiNames.map(name => `export function ${name}(...args) {
      calls.push({ name: '${name}', args });
      if (!handlers['${name}']) throw new Error('Unexpected API call: ${name}');
      return handlers['${name}'](...args);
    }`).join('\n')}
  `)
  const boundary = await import(boundaryUrl)
  Object.assign(boundary.handlers, {
    listRecipeTemplates: async () => [],
    listRecipeCapabilities: async () => [],
    listRecipes: async () => [],
    listRecipeExecutions: async () => [],
    listSpaceVOByPage: async () => ({ records: [] }),
    listPictureVOByPageUncached: async () => ({ records: [] }),
    useUserStore: () => ({ currentUser: { id: '42' } }),
    listFusionTasks: async () => [],
    listEmojiTasks: async () => [],
    listEmojiCandidates: async () => [],
    listModelConnections: async () => [],
    listModelCredentials: async () => [],
    listModelRouting: async () => [],
    listModelUsage: async () => [],
    ...overrides
  })
  const filename = new URL(`../src/${path}`, import.meta.url)
  const { descriptor, errors } = parse(await readFile(filename, 'utf8'), { filename: filename.pathname })
  assert.deepEqual(errors, [], `${path} must parse`)
  const aliases = {
    vue: import.meta.resolve('vue'),
    '@/constants/modelGateway': sourceUrl('constants/modelGateway.js'),
    '@/utils/companion': sourceUrl('utils/companion.js')
  }
  for (const alias of ['@/api/space', '@/api/picture', '@/api/modelGateway', '@/stores/user']) {
    aliases[alias] = boundaryUrl
  }
  const compiled = compileScript(descriptor, { id: `copy-states-${fixtureSequence}`, inlineTemplate: true }).content
    .replace(/from (['"])([^'"]+)\1/g, (_, _quote, specifier) => {
      assert.ok(aliases[specifier], `Unexpected dependency: ${specifier}`)
      return `from ${JSON.stringify(aliases[specifier])}`
    })
  return { component: (await import(moduleUrl(compiled))).default, ...boundary }
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
    addEventListener(name, listener) {
      if (!listeners.has(name)) listeners.set(name, [])
      listeners.get(name).push(listener)
    },
    removeEventListener(name, listener) {
      listeners.set(name, (listeners.get(name) || []).filter(item => item !== listener))
    },
    dispatch(name) {
      for (const listener of listeners.get(name) || []) listener({ target: this })
    }
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
function byId(root, id) { return descendants(root).find(node => node.props['data-testid'] === id) }
function assertSameNode(actual, expected, message = 'The rendered node must keep its identity') {
  assert.equal(actual === expected, true, message)
}
function button(root, label) {
  const found = descendants(root).find(node => node.tag === 'button' && text(node) === label)
  assert.ok(found, `Button must be rendered: ${label}`)
  return found
}
function alerts(root) { return descendants(root).filter(node => node.props.role === 'alert').map(text) }
function calls(fixture, name) { return fixture.calls.filter(call => call.name === name) }
function assertNoWrites(fixture) {
  assert.deepEqual(fixture.calls.filter(call => writeNames.has(call.name)), [], 'loading and recovery must remain read-only')
}
async function flush() { for (let i = 0; i < 8; i++) await nextTick() }
async function click(node) {
  assert.notEqual(node.props.disabled, true, 'Do not bypass the actual disabled control')
  await node.props.onClick({ preventDefault() {}, stopPropagation() {} })
  await flush()
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function mount(t, path, overrides = {}, props = {}) {
  const fixture = await compileComponent(path, overrides)
  const root = hostNode('root')
  const render = nextProps => renderer.render(h(fixture.component, nextProps), root)
  render(props)
  t.after(() => renderer.render(null, root))
  return { ...fixture, root, render }
}

const gatewayPath = 'components/model/ModelGatewayAdvanced.vue'
const gatewayEmpty = [
  '还没有 API Key。填写供应商和密钥后保存。',
  '还没有模型连接。填写端点和模型信息，添加后可启用并测试。',
  '还没有调用记录。测试连接或使用模型后，可在这里查看。'
]
const gatewayReads = ['listModelCredentials', 'listModelConnections', 'listModelRouting', 'listModelUsage']
const credential = { id: 'credential-1', provider: 'DEEPSEEK', tail4: 'abcd', algorithm: 'AES-GCM', revision: 2 }
const connection = {
  id: '9223372036854775806', provider: 'DEEPSEEK', displayName: '用户保存的主力连接',
  endpointUri: 'https://api.example.test/v1', modelCode: 'saved-model-code', enabled: false, revision: 4
}
const usageRecord = {
  id: 'usage-1', createdTime: '2026-10-02T10:00:00Z', task: 'LANGUAGE_AGENT',
  provider: 'DEEPSEEK', modelCode: 'saved-model-code', costSource: 'BYOK',
  inputTokens: 12, outputTokens: 34, imageCount: 0, success: true
}
function populatedGatewayApis(extra = {}) {
  return {
    listModelCredentials: async () => [{ ...credential }],
    listModelConnections: async () => [{ ...connection }],
    listModelRouting: async () => [{ task: 'LANGUAGE_AGENT', connectionId: connection.id }],
    listModelUsage: async () => [{ ...usageRecord }],
    ...extra
  }
}
function assertGatewayPopulated(fixture) {
  assert.match(text(byId(fixture.root, 'credential-list')), /DeepSeek.*尾号 abcd.*AES-GCM/)
  assert.match(text(byId(fixture.root, 'connection-list')), /用户保存的主力连接.*https:\/\/api\.example\.test\/v1.*saved-model-code/)
  assert.match(text(byId(fixture.root, 'usage-table')), /saved-model-code.*用户自带密钥.*12 \/ 34 \/ 0 张.*成功/)
  for (const empty of gatewayEmpty) assert.ok(!text(fixture.root).includes(empty))
}

test('R16 model connection read displays loading instead of false empty lists or default routing', async t => {
  const pending = deferred()
  const fixture = await mount(t, gatewayPath, { listModelCredentials: () => pending.promise })
  await flush()
  assert.match(text(fixture.root), /正在加载模型连接…/)
  assert.match(text(fixture.root), /正在加载调用记录…/)
  for (const empty of gatewayEmpty) assert.ok(!text(fixture.root).includes(empty))
  assert.equal(descendants(fixture.root).find(node => node.props['aria-label'] === '语言对话使用的连接'), undefined)
  assert.equal(byId(fixture.root, 'mcp-section'), undefined)
  assert.equal(calls(fixture, 'listMcpServices').length, 0)
  pending.resolve([])
  await flush()
  for (const empty of gatewayEmpty) assert.ok(text(fixture.root).includes(empty))
  assert.doesNotMatch(text(fixture.root), /正在加载/)
  assert.deepEqual(alerts(fixture.root), [])
  assert.ok(descendants(fixture.root).find(node => node.props['aria-label'] === '语言对话使用的连接'))
  for (const name of gatewayReads) assert.equal(calls(fixture, name).length, 1)
  assertNoWrites(fixture)
})

test('R16 failed model connection read renders server error and unknown history instead of successful empty data', async t => {
  const pending = deferred()
  const fixture = await mount(t, gatewayPath, { listModelCredentials: () => pending.promise })
  pending.reject({ response: { data: { message: '读取密钥记录暂时失败' } } })
  await flush()
  assert.deepEqual(alerts(fixture.root), ['读取密钥记录暂时失败', '调用记录未完成加载，请刷新重试'])
  assert.doesNotMatch(text(fixture.root), /正在加载/)
  for (const empty of gatewayEmpty) assert.ok(!text(fixture.root).includes(empty))
  assert.equal(descendants(fixture.root).find(node => node.props['aria-label'] === '语言对话使用的连接'), undefined)
  for (const name of gatewayReads) assert.equal(calls(fixture, name).length, 1, 'a read failure cannot implicitly retry or bill a model call')
  assertNoWrites(fixture)
})

test('R16 successful model connection read preserves stored labels, routing IDs and collapsed technical details', async t => {
  const fixture = await mount(t, gatewayPath, populatedGatewayApis())
  await flush()
  assertGatewayPopulated(fixture)
  assert.deepEqual(alerts(fixture.root), [])
  assert.equal(button(byId(fixture.root, 'connection-list'), '测试连接').props.disabled, true)
  const routing = descendants(fixture.root).find(node => node.props['aria-label'] === '语言对话使用的连接')
  assert.equal(routing.props.value, connection.id, 'routing IDs retain precision and existing selection')
  const disclosures = descendants(fixture.root).filter(node => node.tag === 'details')
  assert.equal(disclosures.length, 2)
  assert.ok(disclosures.every(node => node.props.open !== true))
  assert.match(text(fixture.root), /使用自带密钥（BYOK）的连接失败时，任务会报错，不会自动使用平台额度/)
  assertNoWrites(fixture)
})

test('R16 model connection mutation failure preserves known read data and does not become a read error', async t => {
  const pending = deferred()
  const fixture = await mount(t, gatewayPath, populatedGatewayApis({ deleteModelCredential: () => pending.promise }))
  await flush()
  const action = click(button(byId(fixture.root, 'credential-list'), '删除'))
  await flush()
  assertGatewayPopulated(fixture)
  pending.reject(new Error('这个密钥仍在使用，删除失败'))
  await action
  assert.deepEqual(alerts(fixture.root), ['这个密钥仍在使用，删除失败'])
  assertGatewayPopulated(fixture)
  assert.doesNotMatch(text(fixture.root), /正在加载|调用记录未完成加载/)
  assert.ok(descendants(fixture.root).find(node => node.props['aria-label'] === '语言对话使用的连接'))
  assert.deepEqual(calls(fixture, 'deleteModelCredential').map(call => call.args), [[credential.id]])
  for (const name of gatewayReads) assert.equal(calls(fixture, name).length, 1)
})

test('R16 failed model connection refetch retains the last read records without claiming an empty result', async t => {
  const pending = deferred()
  const fixture = await mount(t, gatewayPath, populatedGatewayApis({ deleteModelCredential: async () => undefined }))
  await flush()
  fixture.handlers.listModelCredentials = () => pending.promise
  const action = click(button(byId(fixture.root, 'credential-list'), '删除'))
  await flush()
  assert.match(text(fixture.root), /正在加载模型连接…/)
  assertGatewayPopulated(fixture)
  pending.reject(new Error('更新列表失败'))
  await action
  assert.deepEqual(alerts(fixture.root), ['更新列表失败', '调用记录未完成加载，请刷新重试'])
  assertGatewayPopulated(fixture)
  assert.doesNotMatch(text(fixture.root), /正在加载/)
  for (const name of gatewayReads) assert.equal(calls(fixture, name).length, 2)
})

for (const outcome of ['success', 'error']) {
  for (const mutation of ['save', 'clear']) {
  test(`R16 gateway routing selection keeps node identity through ${mutation} and ${outcome} refetch`, async t => {
    const fixture = await mount(t, gatewayPath, populatedGatewayApis({ upsertModelRouting: async () => undefined, deleteModelRouting: async () => undefined }))
    await flush()
    const findRouting = () => descendants(fixture.root).find(node => node.props['aria-label'] === '语言对话使用的连接')
    const select = findRouting()
    const row = select.parent
    const clear = button(row, '清除规则')
    assert.equal(select.props.value, connection.id)
    const pending = deferred()
    fixture.handlers.listModelRouting = () => pending.promise
    select.value = ''
    const action = mutation === 'save' ? select.props.onChange({ target: select }) : click(clear)
    await flush()
    assert.match(text(fixture.root), /正在加载模型连接…/)
    assertSameNode(findRouting(), select, 'saving must not unmount the active routing selector')
    assertSameNode(select.parent, row)
    assertSameNode(button(row, '清除规则'), clear)
    assert.equal(select.props.disabled, true)
    assertGatewayPopulated(fixture)
    if (outcome === 'success') pending.resolve([{ task: 'LANGUAGE_AGENT', connectionId: null }])
    else pending.reject(new Error('任务规则已提交，读取新配置失败'))
    await action
    await flush()
    assertSameNode(findRouting(), select)
    assertSameNode(select.parent, row)
    assertSameNode(button(row, '清除规则'), clear)
    assert.equal(select.props.disabled, false)
    assertGatewayPopulated(fixture)
    assert.doesNotMatch(text(fixture.root), /正在加载/)
    if (outcome === 'success') {
      assert.equal(select.props.value, '')
      assert.equal(clear.props.disabled, true)
      assert.deepEqual(alerts(fixture.root), [])
    } else {
      assert.equal(select.props.value, connection.id, 'a failed refetch cannot overwrite the last known routing rule')
      assert.deepEqual(alerts(fixture.root), ['任务规则已提交，读取新配置失败', '调用记录未完成加载，请刷新重试'])
    }
    const mutationApi = mutation === 'save' ? 'upsertModelRouting' : 'deleteModelRouting'
    assert.deepEqual(calls(fixture, mutationApi).map(call => call.args), [mutation === 'save' ? ['LANGUAGE_AGENT', null] : ['LANGUAGE_AGENT']])
    for (const name of gatewayReads) assert.equal(calls(fixture, name).length, 2)
    assert.deepEqual(fixture.calls.filter(call => writeNames.has(call.name)).map(call => call.name), [mutationApi])
  })
}
}
