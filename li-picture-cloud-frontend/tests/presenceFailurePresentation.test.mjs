import test from 'node:test'
import assert from 'node:assert/strict'
import * as presence from '../src/presentation/companionPresence.js'

test('failed real decisions never look like voluntary model silence', () => {
  assert.equal(typeof presence.presentPresenceResult, 'function')
  for (const reason of ['UNAVAILABLE', 'MODEL_CREDENTIAL_REJECTED', 'MODEL_REQUEST_REJECTED',
    'MODEL_UPSTREAM_ERROR', 'MODEL_NETWORK_ERROR', 'MODEL_TIMEOUT', 'MODEL_STREAM_INVALID',
    'MODEL_BUDGET', 'MODEL_RATE_LIMITED', 'EMPTY_OUTPUT', 'INVALID_DECISION', 'OUTPUT_LIMIT',
    'DAILY_BUDGET', 'QUOTA_UNAVAILABLE', 'ROUTE_UNAVAILABLE', 'STATE_UNAVAILABLE',
    'ACCOUNTING_FAILED', 'ADAPTER_UNSUPPORTED']) {
    const value = presence.presentPresenceResult({ action: 'SILENT', text: '', source: 'SERVER_BYOK', reason })
    assert.equal(value.label, '未完成', reason)
    assert.equal(value.sourceLabel, '真实试演 · BYOK', reason)
    assert.doesNotMatch(value.text, /这会儿先不插话/)
    assert.equal(value.canReply, false)
    assert.doesNotMatch(presence.describePresenceReason(reason), /未给出/)
  }
})

test('normal model silence remains a completed decision and gates are not model decisions', () => {
  assert.equal(typeof presence.presentPresenceResult, 'function')
  const normal = presence.presentPresenceResult({ action: 'SILENT', text: '', source: 'SERVER_BYOK', reason: 'DECIDED' })
  assert.equal(normal.label, 'SILENT')
  assert.equal(normal.sourceLabel, '真实模型 · BYOK')
  assert.match(normal.text, /这会儿先不插话/)
  for (const reason of ['COOLDOWN', 'USER_MESSAGE_PRIORITY', 'SESSION_BUDGET', 'BYOK_REQUIRED']) {
    const value = presence.presentPresenceResult({ action: 'SILENT', source: 'SERVER_BYOK', reason })
    assert.equal(value.label, '未生成')
    assert.doesNotMatch(value.text, /这会儿先不插话/)
  }
  assert.equal(presence.presentPresenceResult({ action: 'SPEAK', text: '夹具短句', source: 'SERVER_BYOK', reason: 'DECIDED' }).canReply, true)
})

test('failure wins over inconsistent action and text without exposing them', () => {
  assert.equal(typeof presence.presentPresenceResult, 'function')
  const value = presence.presentPresenceResult({ action: 'SPEAK', text: 'fixture-secret-response', source: 'SERVER_BYOK', reason: 'INVALID_DECISION' })
  assert.equal(value.label, '未完成')
  assert.equal(value.canReply, false)
  assert.doesNotMatch(value.text, /fixture-secret-response/)
})
