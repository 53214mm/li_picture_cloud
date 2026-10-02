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
    listMcpServices: async () => [],
    listMcpTools: async () => [],
    fusionPreviewUrl: id => `/creation/fusion/${id}/preview`,
    ...overrides
  })
  const filename = new URL(`../src/${path}`, import.meta.url)
  const { descriptor, errors } = parse(await readFile(filename, 'utf8'), { filename: filename.pathname })
  assert.deepEqual(errors, [], `${path} must parse`)
  const aliases = {
    vue: import.meta.resolve('vue'),
    '@/constants/recipe': sourceUrl('constants/recipe.js'),
    '@/constants/creation': sourceUrl('constants/creation.js'),
    '@/constants/modelGateway': sourceUrl('constants/modelGateway.js'),
    '@/utils/companion': sourceUrl('utils/companion.js')
  }
  for (const alias of ['@/api/recipe', '@/api/space', '@/api/picture', '@/api/creation', '@/api/modelGateway', '@/stores/user']) {
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

const workshopPath = 'views/RecipeWorkshopView.vue'
const recipe = { id: '8001', name: '我保存的配方名', status: 'ENABLED', latestVersion: 3 }
function detail(capability = 'STORY_DRAFT') {
  return {
    recipe: { ...recipe }, versions: [{ version: 3 }],
    latest: {
      version: 3, whenJson: JSON.stringify({ type: 'WEEKLY_REVIEW' }),
      ifJson: '[]', thenJson: JSON.stringify({ capability })
    }
  }
}
const privateSpaces = {
  records: [
    { id: '903', userId: '42', spaceType: 1, createTime: '2026-01-01T00:00:00Z' },
    { id: '902', userId: '42', spaceType: 0, createTime: '2026-02-01T00:00:00Z' },
    { id: '901', userId: '42', spaceType: 0, createTime: '2026-01-01T00:00:00Z' },
    { id: '900', userId: '7', spaceType: 0, createTime: '2025-01-01T00:00:00Z' }
  ]
}
const picture = { id: '9223372036854775806', name: '保存的图片名', url: '/saved-picture.webp' }
function selectedRecipeApis(extra = {}) {
  return {
    listRecipes: async () => [{ ...recipe }],
    getRecipeDetail: async () => detail(),
    listRecipeCapabilities: async () => [{ capability: 'STORY_DRAFT', open: true }],
    ...extra
  }
}
async function openRecipe(fixture) { await click(button(fixture.root, '查看')) }

const emptyTemplates = '暂无可用模板。'
const emptyRecipes = '还没有配方。选择上面的可用模板即可创建。'
const emptyPictures = '暂未找到可用于试运行的私有空间图片。请先上传图片，再重新加载页面。'

test('R16 workshop renders delayed list loads independently before genuine empty responses', async t => {
  const templates = deferred(), recipes = deferred()
  const fixture = await mount(t, workshopPath, {
    listRecipeTemplates: () => templates.promise,
    listRecipes: () => recipes.promise
  })
  assert.match(text(fixture.root), /正在加载模板…/)
  assert.match(text(fixture.root), /正在加载配方…/)
  assert.ok(!text(fixture.root).includes(emptyTemplates))
  assert.equal(byId(fixture.root, 'recipe-empty'), undefined)
  templates.resolve([])
  await flush()
  assert.ok(text(fixture.root).includes(emptyTemplates))
  assert.match(text(fixture.root), /正在加载配方…/)
  assert.equal(byId(fixture.root, 'recipe-empty'), undefined)
  recipes.resolve([])
  await flush()
  assert.equal(text(byId(fixture.root, 'recipe-empty')), emptyRecipes)
  assert.doesNotMatch(text(fixture.root), /正在加载/)
  assert.deepEqual(alerts(fixture.root), [])
  assert.equal(calls(fixture, 'listRecipeTemplates').length, 1)
  assert.equal(calls(fixture, 'listRecipes').length, 1)
  assertNoWrites(fixture)
})

test('R16 workshop rejection is an error rather than loading or a successful empty list', async t => {
  const templates = deferred(), recipes = deferred()
  const fixture = await mount(t, workshopPath, {
    listRecipeTemplates: () => templates.promise,
    listRecipes: () => recipes.promise
  })
  templates.reject(new Error('模板服务暂时不可用'))
  recipes.reject(new Error('配方服务暂时不可用'))
  await flush()
  assert.deepEqual(alerts(fixture.root), [
    '模板服务暂时不可用 重新加载模板', '配方服务暂时不可用 重新加载配方'
  ])
  assert.doesNotMatch(text(fixture.root), /正在加载/)
  assert.ok(!text(fixture.root).includes(emptyTemplates))
  assert.equal(byId(fixture.root, 'recipe-empty'), undefined)
  assert.equal(button(fixture.root, '重新加载模板').props.disabled, false)
  assert.equal(button(fixture.root, '重新加载配方').props.disabled, false)
  await flush()
  assert.equal(calls(fixture, 'listRecipeTemplates').length, 1, 'failure must not trigger an implicit retry')
  assert.equal(calls(fixture, 'listRecipes').length, 1)
  assertNoWrites(fixture)
})

test('R16 explicit list reloads recover independently and retain returned names and template descriptions', async t => {
  const fixture = await mount(t, workshopPath, {
    listRecipeTemplates: async () => { throw new Error('模板读取失败') },
    listRecipes: async () => { throw new Error('配方读取失败') }
  })
  await flush()
  const templates = deferred(), recipes = deferred()
  fixture.handlers.listRecipeTemplates = () => templates.promise
  fixture.handlers.listRecipes = () => recipes.promise
  const templateClick = click(button(fixture.root, '重新加载模板'))
  const recipeClick = click(button(fixture.root, '重新加载配方'))
  await flush()
  assert.match(text(fixture.root), /正在加载模板…/)
  assert.match(text(fixture.root), /正在加载配方…/)
  assert.deepEqual(alerts(fixture.root), [])
  templates.resolve([{
    code: 'saved-template', name: '服务端保留的模板名', description: '未改写的服务端模板描述',
    available: true, whenJson: '{"type":"WEEKLY_REVIEW"}', thenJson: '{"capability":"STORY_DRAFT"}'
  }])
  await templateClick
  assert.match(text(fixture.root), /服务端保留的模板名.*未改写的服务端模板描述/)
  assert.match(text(fixture.root), /正在加载配方…/)
  recipes.resolve([{ ...recipe }])
  await recipeClick
  assert.match(text(byId(fixture.root, 'recipe-list')), /我保存的配方名/)
  assert.doesNotMatch(text(fixture.root), /正在加载|读取失败/)
  assert.equal(calls(fixture, 'listRecipeTemplates').length, 2)
  assert.equal(calls(fixture, 'listRecipes').length, 2)
  assertNoWrites(fixture)
})

test('R16 selected recipe shows a delayed private-picture read before true empty picture guidance', async t => {
  const pictures = deferred()
  const fixture = await mount(t, workshopPath, selectedRecipeApis({
    listSpaceVOByPage: async () => privateSpaces,
    listPictureVOByPageUncached: () => pictures.promise
  }))
  await flush()
  await openRecipe(fixture)
  assert.match(text(fixture.root), /正在加载图片…/)
  assert.ok(!text(fixture.root).includes(emptyPictures))
  pictures.resolve({ records: [] })
  await flush()
  assert.ok(text(fixture.root).includes(emptyPictures))
  assert.doesNotMatch(text(fixture.root), /正在加载图片/)
  assert.deepEqual(alerts(fixture.root), [])
  assert.deepEqual(calls(fixture, 'listPictureVOByPageUncached')[0].args, [{
    current: 1, pageSize: 12, spaceId: '901', sortField: 'createTime', sortOrder: 'descend'
  }], 'the real helper keeps the read scoped to the oldest owned private space')
  assertNoWrites(fixture)
})

test('R16 explicit picture reload replaces its scoped error with loading and returned picture content', async t => {
  const fixture = await mount(t, workshopPath, selectedRecipeApis({
    listSpaceVOByPage: async () => privateSpaces,
    listPictureVOByPageUncached: async () => { throw new Error('图片读取失败') }
  }))
  await flush()
  await openRecipe(fixture)
  assert.deepEqual(alerts(fixture.root), ['图片读取失败 重新加载图片'])
  assert.ok(!text(fixture.root).includes(emptyPictures))
  assert.doesNotMatch(text(fixture.root), /正在加载图片/)
  const pictures = deferred()
  fixture.handlers.listPictureVOByPageUncached = () => pictures.promise
  const pending = click(button(fixture.root, '重新加载图片'))
  await flush()
  assert.match(text(fixture.root), /正在加载图片…/)
  assert.deepEqual(alerts(fixture.root), [])
  pictures.resolve({ records: [picture] })
  await pending
  assert.match(text(fixture.root), /保存的图片名/)
  assert.ok(!text(fixture.root).includes(emptyPictures))
  const image = descendants(fixture.root).find(node => node.tag === 'img')
  assert.equal(image.props.src, picture.url)
  assert.equal(image.props.alt, picture.name)
  assert.equal(button(fixture.root, '试运行').props.disabled, true)
  assert.equal(calls(fixture, 'listPictureVOByPageUncached').length, 2)
  assertNoWrites(fixture)
})

for (const noPictures of ['no signed-in user', 'no owned private space']) {
  test(`R16 ${noPictures} finishes picture loading without reading unrelated pictures`, async t => {
    const fixture = await mount(t, workshopPath, selectedRecipeApis(noPictures === 'no signed-in user'
      ? { useUserStore: () => ({ currentUser: null }) }
      : { listSpaceVOByPage: async () => ({ records: privateSpaces.records.filter(space => space.id === '900' || space.spaceType === 1) }) }))
    await flush()
    await openRecipe(fixture)
    assert.ok(text(fixture.root).includes(emptyPictures))
    assert.doesNotMatch(text(fixture.root), /正在加载图片/)
    assert.equal(calls(fixture, 'listPictureVOByPageUncached').length, 0)
    assert.equal(calls(fixture, 'listSpaceVOByPage').length, noPictures === 'no signed-in user' ? 0 : 1)
    assertNoWrites(fixture)
  })
}

test('R16 unavailable templates and capability options remain disabled while stored content stays visible', async t => {
  const fixture = await mount(t, workshopPath, selectedRecipeApis({
    listRecipeTemplates: async () => [{
      code: 'fusion-off', name: '保留模板名称', description: '保留模板描述', available: false,
      unavailableReason: '尚未配置多图生成服务', whenJson: '{"type":"WEEKLY_REVIEW"}', thenJson: '{"capability":"IMAGE_FUSION"}'
    }],
    getRecipeDetail: async () => detail('IMAGE_FUSION'),
    listRecipeCapabilities: async () => [
      { capability: 'STORY_DRAFT', open: true },
      { capability: 'IMAGE_FUSION', open: false, unavailableReason: '尚未配置多图生成服务' }
    ]
  }))
  await flush()
  assert.equal(button(fixture.root, '用这个模板创建').props.disabled, true)
  assert.match(text(byId(fixture.root, 'template-unavailable')), /尚未配置多图生成服务/)
  assert.match(text(fixture.root), /保留模板名称.*保留模板描述/)
  await openRecipe(fixture)
  const action = descendants(fixture.root).find(node => node.props['aria-label'] === '动作')
  assert.deepEqual(action.options.map(option => [option.props.value, option.props.disabled]), [
    ['STORY_DRAFT', false], ['EMOJI_DRAFT', true], ['IMAGE_FUSION', true]
  ])
  assert.match(text(byId(fixture.root, 'capability-unavailable')), /该能力尚未开放.*尚未配置多图生成服务/)
  assert.match(text(byId(fixture.root, 'recipe-definition')), /每周回顾时.*多图融合/)
  assert.match(text(fixture.root), /触发后仍需确认执行/)
  assertNoWrites(fixture)
})

test('R16 trial-run copy preserves manual execution and the real pinned-picture confirmation boundary', async t => {
  const execution = {
    id: 'execution-1', status: 'DRY_RUN', recipeVersion: 3, sourcePictureIds: [picture.id],
    triggeredTime: '2026-10-02 10:00', quoteJson: '{"capability":"STORY_DRAFT","platformUnits":2}'
  }
  let executions = [execution, { ...execution, id: 'missing-snapshot', sourcePictureIds: [] }]
  const fixture = await mount(t, workshopPath, selectedRecipeApis({
    listSpaceVOByPage: async () => privateSpaces,
    listPictureVOByPageUncached: async () => ({ records: [picture, { ...picture, id: '9223372036854775805', name: '另一张图片' }] }),
    listRecipeExecutions: async () => executions,
    dryRunRecipe: async () => { executions = [execution] },
    executeRecipe: async () => { executions = [{ ...execution, status: 'EXECUTED' }] }
  }))
  await flush()
  await openRecipe(fixture)
  assert.match(text(fixture.root), /试运行（不会产生真实创作）/)
  assert.match(text(fixture.root), /已有记录仍按记录中的版本确认执行/)
  assert.match(text(byId(fixture.root, 'snapshot-missing')), /缺少来源图片.*重新试运行/)
  assert.equal(button(fixture.root, '确认执行（使用试运行的 0 张图片）').props.disabled, true)
  assert.equal(button(fixture.root, '确认执行（使用试运行的 1 张图片）').props.disabled, false)
  assert.equal(calls(fixture, 'executeRecipe').length, 0, 'listing a confirmable execution cannot execute it')

  const checkboxes = descendants(fixture.root).filter(node => node.tag === 'input' && node.props.type === 'checkbox')
  checkboxes[1].checked = true
  checkboxes[1].dispatch('change')
  await flush()
  assert.equal(button(fixture.root, '确认执行（使用试运行的 1 张图片）').props.disabled, true)
  assert.match(text(byId(fixture.root, 'snapshot-changed')), /所选图片与试运行时不一致/)
  checkboxes[1].checked = false
  checkboxes[1].dispatch('change')
  await flush()
  checkboxes[0].checked = true
  checkboxes[0].dispatch('change')
  await flush()
  assert.equal(button(fixture.root, '试运行').props.disabled, false)
  await click(button(fixture.root, '试运行'))
  assert.deepEqual(calls(fixture, 'dryRunRecipe').map(call => call.args), [[recipe.id, { pictureIds: [picture.id] }]])
  assert.equal(calls(fixture, 'executeRecipe').length, 0, 'trial running must not execute automatically')
  await click(button(fixture.root, '确认执行（使用试运行的 1 张图片）'))
  assert.deepEqual(calls(fixture, 'executeRecipe').map(call => call.args), [[recipe.id, execution.id, {}]], 'confirmation uses the saved snapshot, never resubmits currently selected picture IDs')
  assert.match(text(byId(fixture.root, 'recipe-executions')), /已执行/)
  assert.ok(!text(fixture.root).includes('确认执行（使用试运行的'))
})

const creationPanels = [
  {
    name: 'fusion', path: 'components/companion/CompanionFusionPanel.vue', api: 'listFusionTasks', kind: 'IMAGE_FUSION',
    empty: '暂无融合作品记录。', unavailable: '多图融合暂未开放。', fallback: '融合任务加载失败'
  },
  {
    name: 'emoji', path: 'components/companion/CompanionEmojiPanel.vue', api: 'listEmojiTasks', kind: 'EMOJI_DRAFT',
    empty: '暂无表情草稿记录。',
    unavailable: '文字表情草稿暂未开放。', fallback: '表情草稿加载失败'
  }
]
function savedTask(panel, extra = {}) {
  return { id: `${panel.name}-1`, kind: panel.kind, status: 'SAVED', sourcePictureIds: [picture.id], revision: 7, resultText: panel.name === 'fusion' ? 'saved-picture-99' : '用户保留的表情文字 🌿', ...extra }
}

for (const panel of creationPanels) {
  test(`R16 ${panel.name} has loading then true empty, without presenting unavailable creation actions`, async t => {
    const pending = deferred()
    const fixture = await mount(t, panel.path, { [panel.api]: () => pending.promise })
    assert.match(text(fixture.root), /正在加载作品记录…/)
    assert.ok(text(byId(fixture.root, `${panel.name}-unavailable`)).includes(panel.unavailable))
    assert.ok(!text(fixture.root).includes(panel.empty))
    pending.resolve([{ ...savedTask(panel), kind: 'STORY_DRAFT' }])
    await flush()
    assert.ok(text(fixture.root).includes(panel.empty), 'unrelated task kinds do not count as this panel’s content')
    assert.doesNotMatch(text(fixture.root), /正在加载作品记录/)
    assert.deepEqual(alerts(fixture.root), [])
    assert.equal(descendants(fixture.root).filter(node => node.tag === 'button').length, 0)
    assert.equal(calls(fixture, panel.api).length, 1)
    assertNoWrites(fixture)
  })

  test(`R16 ${panel.name} read failure does not claim empty and refresh recovers saved content`, async t => {
    const fixture = await mount(t, panel.path, { [panel.api]: async () => { throw {} } })
    await flush()
    assert.deepEqual(alerts(fixture.root), [panel.fallback])
    assert.ok(!text(fixture.root).includes(panel.empty))
    assert.doesNotMatch(text(fixture.root), /正在加载作品记录/)
    const pending = deferred()
    fixture.handlers[panel.api] = () => pending.promise
    fixture.render({ refreshKey: 1 })
    await flush()
    assert.match(text(fixture.root), /正在加载作品记录…/)
    assert.ok(!text(fixture.root).includes(panel.empty))
    pending.resolve([savedTask(panel)])
    await flush()
    assert.deepEqual(alerts(fixture.root), [])
    assert.match(text(byId(fixture.root, `${panel.name}-list`)), /已保存/)
    assert.ok(text(byId(fixture.root, `${panel.name}-result`)).includes(savedTask(panel).resultText))
    assert.equal(calls(fixture, panel.api).length, 2)
    assertNoWrites(fixture)
  })

  test(`R16 ${panel.name} refresh failure preserves stored results and does not manufacture empty content`, async t => {
    const stored = savedTask(panel)
    const snapshot = globalThis.structuredClone(stored)
    const fixture = await mount(t, panel.path, { [panel.api]: async () => [stored] })
    await flush()
    assert.ok(text(byId(fixture.root, `${panel.name}-result`)).includes(stored.resultText))
    const pending = deferred()
    fixture.handlers[panel.api] = () => pending.promise
    fixture.render({ refreshKey: 1 })
    await flush()
    assert.match(text(fixture.root), /正在加载作品记录…/)
    assert.ok(!text(fixture.root).includes(panel.empty))
    pending.reject(new Error('刷新失败，保留已读取记录'))
    await flush()
    assert.deepEqual(alerts(fixture.root), ['刷新失败，保留已读取记录'])
    assert.ok(text(byId(fixture.root, `${panel.name}-result`)).includes(stored.resultText))
    assert.ok(!text(fixture.root).includes(panel.empty))
    assert.deepEqual(stored, snapshot, 'copy changes and failed refreshes cannot alter stored task content')
    if (panel.name === 'fusion') {
      const preview = descendants(fixture.root).find(node => node.tag === 'img')
      assert.equal(preview.props.src, `/creation/fusion/${stored.id}/preview?r=7`)
    }
    assert.equal(descendants(fixture.root).filter(node => node.tag === 'button').length, 0)
    assertNoWrites(fixture)
  })
}

test('R16 emoji keeps candidate loading distinct and preserves saved candidate, draft and result text', async t => {
  const pending = deferred()
  const tasks = [
    savedTask(creationPanels[1], { id: 'awaiting-1', status: 'AWAITING_CONFIRM', resultText: '' }),
    savedTask(creationPanels[1], { id: 'saving-1', status: 'SAVING', draftText: '尚在保存的原始草稿', resultText: '' }),
    savedTask(creationPanels[1])
  ]
  const snapshot = globalThis.structuredClone(tasks)
  const candidates = [{ seq: 1, text: '已有候选文本 (๑•̀ㅂ•́)و✧' }]
  const fixture = await mount(t, creationPanels[1].path, {
    listEmojiTasks: async () => tasks,
    listEmojiCandidates: () => pending.promise
  })
  await flush()
  assert.match(text(fixture.root), /正在加载作品记录…/)
  assert.equal(byId(fixture.root, 'emoji-list'), undefined)
  assert.ok(!text(fixture.root).includes(creationPanels[1].empty))
  pending.resolve(candidates)
  await flush()
  assert.match(text(fixture.root), /已有候选文本 \(๑•̀ㅂ•́\)و✧/)
  assert.match(text(fixture.root), /尚在保存的原始草稿/)
  assert.match(text(fixture.root), /用户保留的表情文字 🌿/)
  assert.match(text(fixture.root), /该任务等待的表情草稿能力尚未开放/)
  assert.deepEqual(calls(fixture, 'listEmojiCandidates').map(call => call.args), [['awaiting-1']])
  assert.deepEqual(tasks, snapshot)
  assert.deepEqual(candidates, [{ seq: 1, text: '已有候选文本 (๑•̀ㅂ•́)و✧' }])
  assert.equal(descendants(fixture.root).filter(node => node.tag === 'button').length, 0)
  assertNoWrites(fixture)
})

const gatewayPath = 'views/ModelGatewayView.vue'
const gatewayEmpty = [
  '还没有 API Key。填写供应商和密钥后保存。',
  '还没有模型连接。填写端点和模型信息，添加后可启用并测试。',
  '还没有调用记录。测试连接或使用模型后，可在这里查看。'
]
const mcpEmpty = '还没有 MCP 服务。登记服务后，再添加允许使用的工具。'
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

test('R16 admin MCP service loading resolves to genuine empty without claiming no services prematurely', async t => {
  const pending = deferred()
  const fixture = await mount(t, gatewayPath, {
    useUserStore: () => ({ isAdmin: true }),
    listMcpServices: () => pending.promise
  })
  await flush()
  const mcp = byId(fixture.root, 'mcp-section')
  assert.match(text(mcp), /正在加载 MCP 服务…/)
  assert.ok(!text(mcp).includes(mcpEmpty))
  pending.resolve([])
  await flush()
  assert.ok(text(mcp).includes(mcpEmpty))
  assert.doesNotMatch(text(mcp), /正在加载/)
  assert.deepEqual(alerts(fixture.root), [])
  assert.equal(calls(fixture, 'listMcpServices').length, 1)
  assert.equal(calls(fixture, 'listMcpTools').length, 0)
  assertNoWrites(fixture)
})

test('R16 MCP tool-read failure stays distinct from no tools while unrelated gateway reads succeed', async t => {
  const pending = deferred()
  const fixture = await mount(t, gatewayPath, populatedGatewayApis({
    useUserStore: () => ({ isAdmin: true }),
    listMcpServices: async () => [{ id: 'service-1', code: 'saved-service', displayName: '已登记的服务', enabled: true }],
    listMcpTools: () => pending.promise
  }))
  await flush()
  assert.match(text(byId(fixture.root, 'mcp-section')), /正在加载 MCP 服务…/)
  assert.ok(!text(fixture.root).includes(mcpEmpty))
  pending.reject(new Error('白名单工具读取失败'))
  await flush()
  assert.deepEqual(alerts(fixture.root), ['白名单工具读取失败'])
  assertGatewayPopulated(fixture)
  assert.ok(!text(fixture.root).includes(mcpEmpty))
  assert.doesNotMatch(text(fixture.root), /还没有白名单工具|正在加载/)
  assert.deepEqual(calls(fixture, 'listMcpTools').map(call => call.args), [['saved-service']])
  assertNoWrites(fixture)
})

for (const outcome of ['success', 'error']) {
  test(`R16 recipe toggle keeps its row and unaffected controls mounted through a ${outcome} refetch`, async t => {
    const fixture = await mount(t, workshopPath, selectedRecipeApis({ disableRecipe: async () => undefined }))
    await flush()
    const list = byId(fixture.root, 'recipe-list')
    const row = descendants(list).find(node => node.tag === 'li')
    const view = button(row, '查看')
    const disable = button(row, '停用')
    const pending = deferred()
    fixture.handlers.listRecipes = () => pending.promise
    const action = click(disable)
    await flush()
    assert.match(text(fixture.root), /正在加载配方…/)
    assertSameNode(byId(fixture.root, 'recipe-list'), list)
    assertSameNode(descendants(list).find(node => node.tag === 'li'), row)
    assertSameNode(button(row, '查看'), view)
    assertSameNode(button(row, '停用'), disable, 'the clicked control stays mounted while the read is pending')
    assert.equal(disable.props.disabled, true)
    assert.equal(byId(fixture.root, 'recipe-empty'), undefined)

    if (outcome === 'success') pending.resolve([{ ...recipe, status: 'DISABLED', name: '刷新后的配方名称' }])
    else pending.reject(new Error('状态更新后的列表读取失败'))
    await action
    assertSameNode(byId(fixture.root, 'recipe-list'), list)
    assertSameNode(descendants(list).find(node => node.tag === 'li'), row)
    assertSameNode(button(row, '查看'), view, 'an unchanged control keeps its identity across the read result')
    assert.equal(view.props.disabled, false)
    assert.equal(byId(fixture.root, 'recipe-empty'), undefined)
    assert.doesNotMatch(text(fixture.root), /正在加载配方/)
    if (outcome === 'success') {
      assert.equal(row.props['data-status'], 'DISABLED')
      assert.match(text(row), /刷新后的配方名称/)
      assert.equal(button(row, '启用') === disable, false, 'a genuinely changed status may replace its old action')
      assert.deepEqual(alerts(fixture.root), [])
    } else {
      assertSameNode(button(row, '停用'), disable)
      assert.match(alerts(fixture.root).join(' '), /状态更新后的列表读取失败/)
      const recovery = deferred()
      fixture.handlers.listRecipes = () => recovery.promise
      const reload = click(button(fixture.root, '重新加载配方'))
      await flush()
      assertSameNode(button(row, '查看'), view)
      assertSameNode(byId(fixture.root, 'recipe-list'), list)
      recovery.resolve([{ ...recipe, status: 'DISABLED' }])
      await reload
      assertSameNode(button(row, '查看'), view)
      assertSameNode(descendants(list).find(node => node.tag === 'li'), row)
      assert.deepEqual(alerts(fixture.root), [])
    }
    assert.deepEqual(fixture.calls.filter(call => writeNames.has(call.name)).map(call => [call.name, call.args]), [
      ['disableRecipe', [recipe.id]]
    ], 'refetch and recovery must not repeat the mutation')
  })
}

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
