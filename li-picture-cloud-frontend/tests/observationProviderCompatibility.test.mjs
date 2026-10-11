import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { Buffer } from 'node:buffer'
import { observationRecord } from '../src/presentation/observationTrace.js'

test('bounded provider trace preserves truthful token parameter and only reasoning presence', () => {
  const request = { sessionId: 'fixture', snapshotId: 'snapshot', sequence: 1 }
  const record = observationRecord(request, { ...request, trace: {
    parameters: { provider: 'DASHSCOPE', model: 'qwen3.8-max', stream: true, thinkingMode: 'NON_THINKING',
      tokenLimitParameter: 'max_completion_tokens', maxTokens: null, maxCompletionTokens: 160, collectionTimeoutSeconds: 15, maxOutputChars: 1024 },
    stream: { reasoningPresent: true, terminal: 'COLLECTION_TIMEOUT', reasoning_content: 'PRIVATE_SYNTHETIC_REASONING' }
  } }, 1)
  assert.equal(record.response.trace.parameters.maxTokens, null)
  assert.equal(record.response.trace.parameters.maxCompletionTokens, 160)
  assert.equal(record.response.trace.parameters.thinkingMode, 'NON_THINKING')
  assert.equal(record.response.trace.stream.terminal, 'COLLECTION_TIMEOUT')
  assert.equal(JSON.stringify(record).includes('PRIVATE_SYNTHETIC_REASONING'), false)
})

test('observation timeout covers bounded vision plus text and retains explicit cancellation', async () => {
  const source = await readFile(new URL('../src/api/companionObservation.js', import.meta.url), 'utf8')
  const calls = []
  globalThis.__providerCompatibilityRequest = {
    post: (...args) => { calls.push(args); return Promise.resolve() },
    get: (...args) => { calls.push(args); return Promise.resolve() }
  }
  try {
    const code = source.replace("import request from './request'", 'const request = globalThis.__providerCompatibilityRequest')
    const { observationApi } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
    const signal = new globalThis.AbortController().signal
    await observationApi.observe({ fixture: true }, { signal })
    assert.equal(calls[0][2].timeout, 30000)
    assert.equal(calls[0][2].signal, signal)
    await observationApi.status({ signal })
    assert.equal(calls[1][1].timeout, undefined)
  } finally { delete globalThis.__providerCompatibilityRequest }
})
