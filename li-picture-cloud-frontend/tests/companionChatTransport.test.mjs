import test from 'node:test'
import assert from 'node:assert/strict'
import { streamCompanionChat } from '../src/utils/companion.js'

const { AbortController, ReadableStream, TextEncoder } = globalThis
const encode = text => new TextEncoder().encode(text)

function body() {
  let controller
  let canceled = 0
  const stream = new ReadableStream({
    start(value) { controller = value },
    cancel() { canceled += 1 }
  })
  return { stream, push: text => controller.enqueue(encode(text)), close: () => controller.close(),
    fail: error => controller.error(error), get canceled() { return canceled } }
}

test('transport keeps request contract, shares AbortSignal, and releases reader after SSE done', async t => {
  const source = body()
  const abort = new AbortController()
  let sent
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    sent = { url, ...options }
    return { ok: true, body: source.stream }
  })
  const chunks = []
  let done = 0
  source.push('data: 你好\n\ndata: 世界\n\nevent: done\ndata:\n\ndata: 太迟\n\n')
  await streamCompanionChat('消息', { signal: abort.signal, onChunk: chunk => chunks.push(chunk), onDone: () => { done += 1 } })
  assert.equal(sent.url, '/api/companion/chat/stream')
  assert.equal(sent.method, 'POST')
  assert.equal(sent.credentials, 'include')
  assert.deepEqual(sent.headers, { 'Content-Type': 'application/json' })
  assert.deepEqual(JSON.parse(sent.body), { message: '消息' })
  assert.equal(sent.signal, abort.signal)
  assert.deepEqual(chunks, ['你好', '世界'])
  assert.equal(done, 1)
  assert.equal(source.canceled, 1)
  assert.equal(source.stream.locked, false)
})

test('transport preserves normal EOF termination and handles split UTF-8 SSE chunks', async t => {
  let controller
  const stream = new ReadableStream({ start(value) { controller = value } })
  const encoded = encode('data: 绫页\n\n')
  controller.enqueue(encoded.slice(0, 7))
  controller.enqueue(encoded.slice(7, 9))
  controller.enqueue(encoded.slice(9))
  controller.close()
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, body: stream }))
  const chunks = []
  let done = 0
  await streamCompanionChat('你好', { onChunk: chunk => chunks.push(chunk), onDone: () => { done += 1 } })
  assert.deepEqual(chunks, ['绫页'])
  assert.equal(done, 1)
  assert.equal(stream.locked, false)
})

test('SSE error is an error callback, stops trailing text, and cleans the reader', async t => {
  const source = body()
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, body: source.stream }))
  source.push('data: 一部分\n\nevent: error\ndata: 模型不可用\n\ndata: 无效内容\n\nevent: done\ndata:\n\n')
  const chunks = []
  const errors = []
  let done = 0
  await streamCompanionChat('问题', { onChunk: chunk => chunks.push(chunk), onError: error => errors.push(error), onDone: () => { done += 1 } })
  assert.deepEqual(chunks, ['一部分'])
  assert.equal(errors.length, 1)
  assert.equal(errors[0].message, '模型不可用')
  assert.equal(done, 0)
  assert.equal(source.canceled, 1)
  assert.equal(source.stream.locked, false)
})

test('aborting a pending read cancels transport and never emits done', async t => {
  const source = body()
  const abort = new AbortController()
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, body: source.stream }))
  const callbacks = []
  const pending = streamCompanionChat('问题', { signal: abort.signal,
    onChunk: chunk => callbacks.push(chunk), onError: () => callbacks.push('error'), onDone: () => callbacks.push('done') })
  await Promise.resolve()
  assert.equal(source.stream.locked, true)
  abort.abort()
  await assert.rejects(pending, { name: 'AbortError' })
  assert.deepEqual(callbacks, [])
  assert.equal(source.canceled, 1)
  assert.equal(source.stream.locked, false)
})

test('abort inside the first chunk blocks every remaining callback in that buffered batch', async t => {
  const source = body()
  const abort = new AbortController()
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, body: source.stream }))
  source.push('data: 第一段\n\ndata: 第二段\n\nevent: error\ndata: 迟到错误\n\nevent: done\ndata:\n\n')
  const chunks = []
  let done = 0
  let errors = 0
  await assert.rejects(streamCompanionChat('问题', { signal: abort.signal,
    onChunk: chunk => { chunks.push(chunk); abort.abort() }, onError: () => { errors += 1 }, onDone: () => { done += 1 } }),
  { name: 'AbortError' })
  assert.deepEqual(chunks, ['第一段'])
  assert.equal(done, 0)
  assert.equal(errors, 0)
  assert.equal(source.canceled, 1)
  assert.equal(source.stream.locked, false)
})

test('already aborted signal prevents fetch entirely', async t => {
  const fetch = t.mock.method(globalThis, 'fetch', async () => { throw new Error('should not fetch') })
  const abort = new AbortController()
  abort.abort()
  await assert.rejects(streamCompanionChat('问题', { signal: abort.signal }), { name: 'AbortError' })
  assert.equal(fetch.mock.callCount(), 0)
})

test('an abort before mocked fetch resolves still cleans its late reader without callbacks', async t => {
  const source = body()
  const abort = new AbortController()
  let resolve
  t.mock.method(globalThis, 'fetch', () => new Promise(done => { resolve = done }))
  const chunks = []
  let done = 0
  const pending = streamCompanionChat('问题', { signal: abort.signal, onChunk: chunk => chunks.push(chunk), onDone: () => { done += 1 } })
  abort.abort()
  source.push('data: 迟到回复\n\n')
  resolve({ ok: true, body: source.stream })
  await assert.rejects(pending, { name: 'AbortError' })
  assert.deepEqual(chunks, [])
  assert.equal(done, 0)
  assert.equal(source.canceled, 1)
  assert.equal(source.stream.locked, false)
})

test('read and callback exceptions always release the reader without inventing terminal success', async t => {
  for (const callbackFailure of [false, true]) {
    const source = body()
    t.mock.method(globalThis, 'fetch', async () => ({ ok: true, body: source.stream }))
    let done = 0
    if (callbackFailure) source.push('data: 回复\n\n')
    else source.fail(new Error('network failure'))
    await assert.rejects(streamCompanionChat('问题', {
      onChunk: () => { throw new Error('callback failure') }, onDone: () => { done += 1 }
    }), new RegExp(callbackFailure ? 'callback failure' : 'network failure'))
    assert.equal(done, 0)
    assert.equal(source.stream.locked, false)
  }
})

test('reader cleanup failures cannot hide the original SSE result', async t => {
  let released = 0
  let canceled = 0
  const reader = {
    read: async () => ({ done: false, value: encode('event: error\ndata: 原始错误\n\n') }),
    cancel: async () => { canceled += 1; throw new Error('cleanup failed') },
    releaseLock: () => { released += 1; throw new Error('release failed') }
  }
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, body: { getReader: () => reader } }))
  let error
  await streamCompanionChat('问题', { onError: value => { error = value } })
  assert.equal(error.message, '原始错误')
  assert.equal(canceled, 1)
  assert.equal(released, 1)
})

test('HTTP failures retain their status and do not call stream completion', async t => {
  t.mock.method(globalThis, 'fetch', async () => ({ ok: false, status: 403, json: async () => ({ message: '没有权限' }) }))
  let done = 0
  await assert.rejects(streamCompanionChat('问题', { onDone: () => { done += 1 } }), { status: 403, message: '没有权限' })
  assert.equal(done, 0)
})
