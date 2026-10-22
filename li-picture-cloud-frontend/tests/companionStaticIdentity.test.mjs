import test from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, reactive } from 'vue'
import { mapCompanionPresentation } from '../src/presentation/companionPresentation.js'
import { mapCompanionAnimation, createCompanionAnimator } from '../src/presentation/companionAnimation.js'
import { LIFE_STAGE } from '../src/constants/companion.js'

const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const boundary = moduleUrl(`import { reactive } from ${JSON.stringify(import.meta.resolve('vue'))};
export const calls = []; export const interaction = reactive({ dragging: false, open: () => calls.push('open'), dragOver: () => calls.push('drag'), drop: () => calls.push('drop') });
export const policy = reactive({ compact: false, allowMotion: true, reason: 'full', label: '静态立绘' });
export const useCompanionInteractionStore = () => interaction;
export const useCompanionRenderPolicy = () => ({ policy });
export const lingyeAssets = { home: { src: 'verified-adult-home.webp', width: 576, height: 768 }, idle: { src: 'forbidden-idle.webp', columns: 2 } };`)
const shared = await import(boundary)
const aliases = {
  vue: import.meta.resolve('vue'),
  '@/presentation/companionAnimation': new URL('../src/presentation/companionAnimation.js', import.meta.url).href,
  '@/stores/companionInteraction': boundary,
  '@/composables/useCompanionRenderPolicy': boundary,
  './lingyeAssets': boundary,
  './SpritePlayer.vue': moduleUrl(`export default { setup() { throw new Error('Static artwork must never mount SpritePlayer') } }`)
}
async function compile(name) {
  const { descriptor, errors } = parse(await readFile(new URL(`../src/components/companion/body/${name}.vue`, import.meta.url), 'utf8'))
  assert.deepEqual(errors, [])
  return moduleUrl(compileScript(descriptor, { id: name, inlineTemplate: true }).content.replace(/from (['"])([^'"]+)\1/g, (_, _quote, source) => {
    assert.ok(aliases[source], source)
    return `from ${JSON.stringify(aliases[source])}`
  }))
}
aliases['./CompanionArtwork.vue'] = await compile('CompanionArtwork')
const Body = (await import(await compile('CompanionBody'))).default
function node(tag, text = '') { return { tag, text, props: {}, children: [], parent: null } }
const renderer = createRenderer({
  createElement: tag => node(tag), createText: text => node('#text', text), createComment: text => node('#comment', text),
  setText(n, text) { n.text = text }, setElementText(n, text) { n.text = text; n.children = [] },
  patchProp(n, key, _previous, value) { n.props[key] = value },
  insert(n, parent, anchor) { if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1); n.parent = parent; const index = parent.children.indexOf(anchor); if (index < 0) parent.children.push(n); else parent.children.splice(index, 0, n) },
  remove(n) { if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1) },
  parentNode: n => n.parent, nextSibling: n => n.parent?.children[n.parent.children.indexOf(n) + 1] || null
})
const descendants = n => [n, ...n.children.flatMap(descendants)]
const content = n => (n.tag === '#comment' ? '' : n.text) + n.children.map(content).join('')
function snapshot(stage = 'LIGHT', activity = 'idle', tone = 'neutral') {
  const mood = { energy: 0, joy: 0, loneliness: 0, inspiration: 0, irritation: 0 }
  const axis = { energetic: 'energy', cheerful: 'joy', lonely: 'loneliness', inspired: 'inspiration', irritated: 'irritation' }[tone]
  if (axis) mood[axis] = 90
  const observed = mapCompanionPresentation({ homeStatus: 'ready', home: { companion: { id: '7', lifeStage: stage }, mood } })
  return { ...observed, activity }
}

test('L06 every shipped stage, activity and mood is static, including direct greeting requests', () => {
  const timers = []
  const changes = []
  const animator = createCompanionAnimator({ onChange: value => changes.push(value), schedule: (...args) => timers.push(args) })
  for (const stage of ['LIGHT', 'SEEDLING', 'COMPANION', 'FUTURE', null]) {
    for (const activity of ['idle', 'feeding', 'thinking', 'responding']) {
      for (const tone of ['neutral', 'energetic', 'cheerful', 'lonely', 'inspired', 'irritated']) {
        const p = snapshot(stage, activity, tone)
        assert.equal(p.lifeStage, stage)
        assert.equal(p.appearance.visualStage, 'adult', 'Do not claim juvenile artwork exists')
        assert.equal(mapCompanionAnimation(p).state, 'static')
        animator.update(p, true, changes.length + 1)
        assert.deepEqual(changes.at(-1), { state: 'static', frame: 0, offsetY: 0, playing: false })
      }
    }
  }
  assert.deepEqual(timers, [])
  animator.destroy()
})

test('L06 real compiled Body stays static across live changes, interactions, budgets and image failure', async () => {
  const state = reactive({ presentation: snapshot() })
  const root = node('root')
  const app = renderer.createApp({ render: () => h(Body, { presentation: state.presentation }) })
  app.mount(root)
  try {
    for (const stage of ['LIGHT', 'SEEDLING', 'COMPANION', 'FUTURE']) for (const activity of ['idle', 'feeding', 'thinking', 'responding']) {
      state.presentation = snapshot(stage, activity, 'inspired')
      await nextTick()
      const nodes = descendants(root)
      assert.equal(nodes.find(n => n.tag === 'figure').props['data-animation-intent'], 'static')
      assert.equal(nodes.filter(n => n.tag === 'img').length, 1)
      assert.equal(nodes.find(n => n.tag === 'img').props.src, 'verified-adult-home.webp')
      assert.doesNotMatch(content(root), /光点|纸翼蛾|暂停动作|恢复动作/)
      assert.match(content(root), /绫页/)
    }
    const button = descendants(root).find(n => n.tag === 'button')
    assert.equal(button.props['aria-label'], '和绫页互动')
    button.props.onPointerenter({ pointerType: 'mouse' }); button.props.onFocus(); await nextTick()
    button.props.onClick(); button.props.onDragover({}); button.props.onDrop({ stopPropagation() {} })
    assert.deepEqual(shared.calls, ['open', 'drag', 'drop'])
    shared.policy.compact = true; shared.policy.allowMotion = false; shared.policy.reason = 'narrow'; shared.policy.label = '紧凑静态'
    await nextTick()
    assert.equal(descendants(root).find(n => n.tag === 'figure').props['data-render-mode'], 'compact')
    assert.match(content(root), /紧凑静态/)
    descendants(root).find(n => n.tag === 'img').props.onError(); await nextTick()
    const fallback = descendants(root).find(n => n.props['data-artwork-state'] === 'fallback')
    assert.ok(fallback)
    assert.equal(fallback.props['aria-label'], '绫页，当前静态全身像')
    assert.equal(descendants(root).filter(n => n.tag === 'img').length, 0)
    assert.ok(descendants(root).includes(button), 'Image failure preserves the interaction button')
  } finally { app.unmount() }
})

test('L06 public growth labels are distinct from the stable character name and internal stage enums', () => {
  assert.deepEqual(Object.keys(LIFE_STAGE), ['LIGHT', 'SEEDLING', 'COMPANION'])
  assert.deepEqual(Object.values(LIFE_STAGE).map(stage => stage.label), ['初始阶段', '成长阶段', '成熟阶段'])
})
