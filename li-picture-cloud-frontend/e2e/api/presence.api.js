import assert from 'node:assert/strict'
import test from 'node:test'
import { FixtureSession, requireFixtureEnvironment } from './http-fixture.js'

// Real localhost auth/controller/service/H2/Redis; DEMO only. No image understanding,
// browser acceptance, real credentials, paid providers or production data.
test('bounded presence real HTTP fixture and ordinary-chat coexistence', { timeout: 120_000 }, async t => {
  const base = requireFixtureEnvironment()
  const anonymous = new FixtureSession(base)
  const owner = new FixtureSession(base)
  const other = new FixtureSession(base)
  let active
  const open = body => owner.json('/companion/presence/session', { method: 'POST', body })
  const request = (session, sequence, eventType = 'PICTURE_OPENED') => ({
    sessionId: session.sessionId, eventId: `presence-http-${sequence}`, sequence,
    observedAt: new Date().toISOString(), eventType,
    page: eventType === 'PICTURE_OPENED' ? 'PICTURE_DETAIL' : eventType === 'UPLOAD_SUCCEEDED' ? 'UPLOAD' : 'COMPANION'
  })
  const decide = (body, extras = {}) => owner.json('/companion/presence/decide', { method: 'POST', body, ...extras })
  async function stage(name, work) {
    let passed = false
    await t.test(name, async () => { await work(); passed = true })
    assert.ok(passed, `Stopped presence fixture after ${name}`)
  }
  t.after(async () => {
    if (active) await owner.json(`/companion/presence/session/${active.sessionId}`, { method: 'DELETE' })
    await owner.json('/user/logout', { method: 'POST' })
    await other.json('/user/logout', { method: 'POST' })
  })
  await stage('anonymous rejected; explicit opt-in and separate real-model gate', async () => {
    await anonymous.json('/companion/presence/session', { method: 'POST', body: { enabled: true }, status: 401, code: 40100 })
    for (const [session, account] of [[owner, 'companion_e2e'], [other, 'recipe_e2e']]) {
      await session.json('/user/login', { method: 'POST', body: { userAccount: account, userPassword: 'LocalUser123!' } })
    }
    assert.equal((await open({})).mode, 'OFF')
    await owner.json('/companion/presence/session', { method: 'POST', body: { enabled: true, mode: 'BYOK' }, status: 403, code: 40300 })
    await owner.json('/companion/presence/session', { method: 'POST', body: { enabled: true, ownerId: '9' }, status: 400, code: 40000 })
  })
  await stage('DEMO decision ownership, numeric-string sequence, dedupe and budget', async () => {
    active = await open({ enabled: true, mode: 'DEMO', maxDecisions: 1, muted: false, intensity: 'NORMAL' })
    assert.equal(active.mode, 'DEMO')
    assert.equal(active.remainingDecisions, 1)
    assert.ok(Date.parse(active.expiresAt) > Date.now())
    const event = request(active, 1)
    await other.json('/companion/presence/decide', { method: 'POST', body: event, status: 404, code: 40400 })
    assert.equal(await other.json(`/companion/presence/session/${active.sessionId}`, { method: 'DELETE' }), false)
    const result = await decide(event)
    assert.equal(result.action, 'EMOTE')
    assert.equal(result.emote, 'NOD')
    assert.equal(result.sequence, '1', 'Long transport follows existing Jackson decimal-string contract')
    assert.equal(result.reason, 'DEMO')
    assert.equal(result.remainingDecisions, 0)
    assert.equal((await decide(event)).reason, 'DUPLICATE_EVENT')
    assert.equal((await decide(request(active, 2))).reason, 'SESSION_BUDGET')
    await decide({ ...request(active, 3), dom: 'not collected' }, { status: 400, code: 40000 })
  })
  await stage('muting and ordinary user messages are silent; close invalidates session', async () => {
    active = await open({ enabled: true, mode: 'DEMO', maxDecisions: 3, muted: true })
    assert.equal((await decide(request(active, 1))).reason, 'MUTED')
    assert.equal((await decide(request(active, 2, 'USER_MESSAGE'))).reason, 'USER_MESSAGE_PRIORITY')
    assert.equal(await owner.json(`/companion/presence/session/${active.sessionId}`, { method: 'DELETE' }), true)
    await decide(request(active, 3), { status: 404, code: 40400 })
    active = null
  })
  await stage('presence stays outside normal history; normal SSE chat still works after stopping', async () => {
    await owner.json('/companion/awaken', { method: 'POST' })
    await owner.json('/companion/chat/mode', { method: 'PUT', body: { mode: 'DEMO' } })
    const before = (await owner.json('/companion/chat/history?limit=100')).records
    active = await open({ enabled: true, mode: 'DEMO', maxDecisions: 2, intensity: 'NORMAL' })
    const result = await decide(request(active, 1, 'UPLOAD_SUCCEEDED'))
    assert.equal(result.action, 'SPEAK')
    assert.ok(result.text)
    assert.deepEqual((await owner.json('/companion/chat/history?limit=100')).records, before)
    assert.equal((await open({ enabled: false })).mode, 'OFF')
    active = null
    const reply = await owner.chat('你好呀')
    assert.equal(reply.done, true)
    assert.match(reply.text, /绫页.*演示回复/)
    const after = (await owner.json('/companion/chat/history?limit=100')).records
    assert.equal(after.length, before.length + 2)
    assert.equal(after.at(-2).content, '你好呀')
    assert.equal(after.at(-1).content, reply.text)
  })
})
