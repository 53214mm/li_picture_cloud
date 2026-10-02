import test from 'node:test'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { parse, compileScript } from '@vue/compiler-sfc'
import { renderToString } from '@vue/server-renderer'
import { createSSRApp, h } from 'vue'
import { mapCompanionPresentation } from '../src/presentation/companionPresentation.js'

// Compile the actual SFC scripts and templates, then render them with Vue SSR.
// Only resolve the same Vue/constant imports that Vite resolves in production;
// no component template, mapper result, or rendering primitive is mocked.
async function compilePanel(name) {
  const filename = new URL(`../src/components/companion/${name}.vue`, import.meta.url)
  const { descriptor, errors } = parse(await readFile(filename, 'utf8'), { filename: filename.pathname })
  assert.deepEqual(errors, [], `${name} must parse`)
  const imports = {
    vue: import.meta.resolve('vue'),
    '@/constants/companion': new URL('../src/constants/companion.js', import.meta.url).href
  }
  const compiled = compileScript(descriptor, { id: name, inlineTemplate: true }).content
    .replace(/from (['"])(vue|@\/constants\/companion)\1/g, (_, _quote, source) => `from ${JSON.stringify(imports[source])}`)
  return (await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)).default
}

const panels = await Promise.all([
  ['mood', 'mood', 'CompanionMoodPanel'],
  ['relationship', 'relationship', 'CompanionRelationshipPanel'],
  ['traits', 'trait', 'CompanionStats']
].map(async ([key, axisPrefix, name]) => ({ key, axisPrefix, component: await compilePanel(name) })))

const rawText = 'untrusted-raw-domain-text'
function homeSnapshot() {
  return {
    companion: {
      id: '7001', lifeStage: 'COMPANION', level: 2, lifeExperience: 200,
      levelStartExperience: 100, nextLevelExperience: 500, skills: [],
      traits: { curiosity: -100, enthusiasm: 100, playfulness: 9.99, empathy: 0, creativity: 10, summary: rawText }
    },
    mood: { energy: 0, joy: '100.00', loneliness: 25, inspiration: 50, irritation: 1, summary: rawText },
    relationship: { familiarity: 0, trust: 100, closeness: 60, tacit: 30, recentFeedback: '-100.00', summary: rawText }
  }
}

function inputForStatus(status) {
  const home = homeSnapshot()
  const input = { homeStatus: 'ready', home }
  if (status === 'missing') {
    home.mood = null
    home.relationship = null
    home.companion.traits = null
  } else if (status === 'invalid') {
    home.mood.energy = NaN
    home.relationship.recentFeedback = Infinity
    home.companion.traits.curiosity = rawText
  } else if (status === 'stale') input.homeFresh = false
  else if (status === 'unavailable') input.homeStatus = 'error'
  return input
}

async function renderPanel(panel, input) {
  const presentation = mapCompanionPresentation(input)
  const props = { presentation }
  if (panel.key === 'traits') props.companion = input.home.companion
  const html = await renderToString(createSSRApp({ render: () => h(panel.component, props) }))
  return { html, presentation, view: presentation.disposition[panel.key] }
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function testIdText(html, id) {
  const match = html.match(new RegExp(`<([a-z][\\w-]*)\\b[^>]*data-testid="${escapeRegex(id)}"[^>]*>([\\s\\S]*?)<\\/\\1>`))
  assert.ok(match, `${id} must be present`)
  return match[2]
}

function axisMarkup(html, prefix, key) {
  const start = html.indexOf(`data-testid="${prefix}-axis-${key}"`)
  assert.notEqual(start, -1, `${prefix}.${key} must have its own rendered axis`)
  const next = html.indexOf(`data-testid="${prefix}-axis-`, start + 1)
  return html.slice(start, next === -1 ? undefined : next)
}

for (const status of ['known', 'missing', 'invalid', 'stale', 'unavailable']) {
  for (const panel of panels) {
    test(`R11 ${panel.key} panel renders the real mapper's ${status} snapshot`, async () => {
      const { html, view } = await renderPanel(panel, inputForStatus(status))
      assert.equal(view.status, status)
      assert.equal(testIdText(html, `${panel.key}-label`), view.label)
      assert.ok(html.includes(view.description), 'the central explanation must be rendered')
      assert.match(html, new RegExp(`data-state-status="${status}"`))
      assert.doesNotMatch(html, /NaN|Infinity|undefined|aria-live/)
      assert.equal(html.includes(rawText), false, 'raw domain text must never replace the central explanation')

      const details = [...html.matchAll(/<details\b([^>]*)>([\s\S]*?)<\/details>/g)]
      assert.equal(details.length, status === 'known' ? 1 : 0)
      if (status === 'known') {
        const [, attributes, content] = details[0]
        assert.doesNotMatch(attributes, /\sopen(?:\s|=|$)/, 'details must start collapsed')
        assert.match(attributes, new RegExp(`data-testid="${panel.key}-details"`))
        assert.match(content, /^\s*<summary>/, 'the disclosure uses a native summary')
        assert.ok(html.indexOf(`data-testid="${panel.key}-overview"`) < html.indexOf('<details'))
        for (const axis of view.axes) {
          const id = `${panel.axisPrefix}-value-${axis.key}`
          assert.equal(testIdText(content, id), String(axis.value), 'exact numeric values belong inside details')
          assert.ok(Number.isFinite(axis.value))
          assert.ok(Number.isFinite(axis.position))
          assert.ok(axisMarkup(content, panel.axisPrefix, axis.key).includes(`left:${axis.position}%`))
        }
      } else {
        assert.deepEqual(view.axes, [])
        assert.equal(html.includes(`data-testid="${panel.axisPrefix}-axis-`), false)
        assert.equal(html.includes(`data-testid="${panel.axisPrefix}-value-`), false)
      }
    })
  }
}

test('R11 known panels show central descriptors and mapped trait labels instead of raw text', async () => {
  const input = inputForStatus('known')
  const expectedLabels = { mood: '心情明朗', relationship: '相处渐近', traits: '明显偏谨慎 · 明显偏热情' }
  for (const panel of panels) {
    const { html, view, presentation } = await renderPanel(panel, input)
    assert.equal(testIdText(html, `${panel.key}-label`), expectedLabels[panel.key])
    if (panel.key === 'mood') assert.ok(html.includes(`data-affect="${presentation.affect}"`))
    if (panel.key === 'relationship') assert.ok(html.includes(`data-rapport="${presentation.rapport}"`))
    if (panel.key === 'traits') {
      assert.deepEqual(view.axes.map(axis => axis.label), ['明显偏谨慎', '明显偏热情', '保持中性', '保持中性', '略偏创造'])
      for (const axis of view.axes) {
        const row = axisMarkup(html, panel.axisPrefix, axis.key)
        assert.ok(row.includes(`<strong>${axis.label}</strong>`))
        assert.ok(row.includes(`aria-valuetext="${axis.label}"`))
        assert.ok(row.includes(`aria-valuenow="${axis.value}"`))
      }
    }
  }
})

test('R11 panels preserve zero and finite boundary numbers, including explicit decimal strings', async () => {
  for (const value of [0, 0.25, 100, '0.00', '100.00']) {
    const input = inputForStatus('known')
    input.home.mood.joy = value
    input.home.relationship.familiarity = value
    for (const [panel, key] of [[panels[0], 'joy'], [panels[1], 'familiarity']]) {
      const { html, view } = await renderPanel(panel, input)
      assert.equal(view.status, 'known')
      assert.equal(testIdText(html, `${panel.axisPrefix}-value-${key}`), String(Number(value)))
      assert.ok(axisMarkup(html, panel.axisPrefix, key).includes(`left:${Number(value)}%`))
    }
  }
  for (const value of [-100, -0.5, 0, 0.5, 100, '-100.00', '0.00', '100.00']) {
    const input = inputForStatus('known')
    input.home.relationship.recentFeedback = value
    input.home.companion.traits.curiosity = value
    for (const [panel, key] of [[panels[1], 'recentFeedback'], [panels[2], 'curiosity']]) {
      const { html, view } = await renderPanel(panel, input)
      const expectedPosition = (Number(value) + 100) / 2
      assert.equal(view.status, 'known')
      assert.equal(testIdText(html, `${panel.axisPrefix}-value-${key}`), String(Number(value)))
      const row = axisMarkup(html, panel.axisPrefix, key)
      assert.ok(row.includes(`left:${expectedPosition}%`))
      assert.ok(row.includes('class="neutral-mark"'), 'signed axes retain their visible midpoint')
    }
  }
})

test('R11 malformed snapshots render no raw text, non-finite values, or zero-filled detail axes', async () => {
  for (const bad of [NaN, Infinity, -Infinity, '', true, 'NaN', rawText, {}, [], 100.01, -100.01]) {
    const input = inputForStatus('known')
    input.home.mood.energy = bad
    input.home.relationship.recentFeedback = bad
    input.home.companion.traits.curiosity = bad
    for (const panel of panels) {
      const { html, view } = await renderPanel(panel, input)
      assert.equal(view.status, 'invalid')
      assert.equal(testIdText(html, `${panel.key}-label`), view.label)
      assert.ok(html.includes(view.description))
      assert.doesNotMatch(html, /<details|NaN|Infinity|undefined/)
      assert.equal(html.includes(rawText), false)
      assert.equal(html.includes(`data-testid="${panel.axisPrefix}-value-`), false)
    }
  }
})

test('R11 a stale observation removes formerly known details and all previous axis readings', async () => {
  const input = inputForStatus('known')
  for (const panel of panels) {
    const known = await renderPanel(panel, input)
    const stale = await renderPanel(panel, { ...input, homeFresh: false })
    assert.match(known.html, /<details/)
    assert.equal(stale.view.status, 'stale')
    assert.doesNotMatch(stale.html, /<details/)
    assert.equal(stale.html.includes(`data-testid="${panel.axisPrefix}-value-`), false)
    assert.equal(testIdText(stale.html, `${panel.key}-label`), stale.view.label)
    assert.notEqual(stale.view.label, known.view.label)
  }
})
