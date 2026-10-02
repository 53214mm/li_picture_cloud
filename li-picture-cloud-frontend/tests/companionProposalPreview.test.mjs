import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { selectCompanionProposalPreview } from '../src/presentation/companionProposalPreview.js'
import { mapCompanionPresentation } from '../src/presentation/companionPresentation.js'
import { createPresentationChannel } from '../src/presentation/companionPresentationChannel.js'

const observation = { homeStatus: 'ready', home: { companion: { id: '7', lifeStage: 'LIGHT' } }, proposal: { status: 'PENDING', preview: { id: '91', content: '一起看看最近的图片？' } } }
const select = input => selectCompanionProposalPreview(input, mapCompanionPresentation(input))

test('R12 preview displays only observed pending content without interpreting it', () => {
  const before = JSON.stringify(observation)
  const preview = select(observation)
  assert.deepEqual(preview, { id: '91', content: '一起看看最近的图片？' })
  assert.ok(Object.isFrozen(preview))
  assert.equal(JSON.stringify(observation), before)
  assert.equal(select({ ...observation, proposal: { ...observation.proposal, preview: { id: 91, content: '不据正文猜情绪' } } }).content, '不据正文猜情绪')
})

test('R12 absent, failed, loading, nonpending and unknown proposals cannot survive as a preview', () => {
  for (const status of [null, 'DONE', 'IGNORED', 'SUPPRESSED', 'EXPIRED', 'unknown']) assert.equal(select({ ...observation, proposal: { ...observation.proposal, status } }), null)
  for (const flag of ['loading', 'error']) assert.equal(select({ ...observation, proposal: { ...observation.proposal, [flag]: true } }), null)
  for (const homeStatus of ['unobserved', 'loading', 'error', 'unavailable']) assert.equal(select({ ...observation, homeStatus }), null)
  assert.equal(select({ ...observation, enabled: false }), null)
  assert.equal(selectCompanionProposalPreview(observation, { version: 2, availability: 'ready', attention: 'proposal' }), null)
  assert.equal(select(undefined), null)
})

test('R12 preview rejects missing or malformed identifiers and content without exposing arbitrary values', () => {
  for (const id of [null, undefined, '', 0, -1, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1, '0', '-1', 'a', {}]) assert.equal(select({ ...observation, proposal: { ...observation.proposal, preview: { id, content: 'known' } } }), null)
  for (const content of [undefined, null, '', '  ', {}, 1, false]) assert.equal(select({ ...observation, proposal: { ...observation.proposal, preview: { id: '91', content } } }), null)
})

test('R12 proposal preview is released with the Home lease and cannot leak across accounts or routes', () => {
  let value
  const channel = createPresentationChannel(snapshot => { value = select(snapshot) })
  const old = channel.acquire()
  old.publish(observation)
  assert.equal(value.id, '91')
  channel.reset()
  assert.equal(value, null)
  old.publish(observation)
  assert.equal(value, null)
  const next = channel.acquire()
  next.publish(observation)
  next.close()
  assert.equal(value, null)
})

test('R12 drawer performs no proposal/contract IO and retains explicit picture handoff', async () => {
  const read = path => readFile(new globalThis.URL(`../src/${path}`, import.meta.url), 'utf8')
  const quick = await read('components/companion/CompanionQuickChat.vue')
  const drawer = await read('components/companion/CompanionInteractionPanel.vue')
  const hint = await read('components/companion/CompanionProposalHint.vue')
  const chat = await read('components/companion/CompanionChatPanel.vue')
  const presence = await read('components/shell/CompanionPresenceSlot.vue')
  assert.match(presence, /\.presence__full, \.presence__arrow \{ display: none; \}/)
  assert.equal((presence.match(/\.presence__hint \{/g) || []).length, 1)
  for (const source of [quick, drawer, hint]) assert.doesNotMatch(source, /getActiveCompanionProposal|getCompanionContract|acceptCompanionProposal|ignoreCompanionProposal|scoldCompanionProposal|setInterval/)
  assert.match(drawer, /CompanionQuickChat v-if="!interaction\.inspection"/)
  assert.match(drawer, /go\('feed', interaction\.inspection\.picture\.id\)/)
  assert.match(quick, /onMounted\(load\)/)
  assert.match(quick, /onBeforeUnmount\([^]*controller\?\.abort\(\)/)
  assert.match(quick, /actor === String\(user\.currentUser\?\.id\)/)
  assert.match(hint, /presentation\.attention === 'proposal'/)
  assert.doesNotMatch(hint, /aria-live|role="(?:status|alert)"/)
  assert.match(chat, /companion-quick-chat-input' : 'companion-chat-input/)
  assert.match(chat, /ref="scroller"/)
  assert.doesNotMatch(chat, /document\.querySelector/)
})
