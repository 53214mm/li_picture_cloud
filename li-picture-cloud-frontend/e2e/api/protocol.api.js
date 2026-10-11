import assert from 'node:assert/strict'
import test from 'node:test'
import { Readable } from 'node:stream'
import { FIXTURE_API, consumeSse, createSseParser, fixtureUrl, requireFixtureEnvironment } from './http-fixture.js'

// Transport-harness tests, not claims about the backend or browser behavior.
test('fixture guard requires Node22, explicit opt-in, and the exact loopback API', () => {
  const allowed = { LPC_API_E2E: 'owned-test-e2e', LPC_API_E2E_BASE_URL: FIXTURE_API }
  assert.equal(requireFixtureEnvironment(allowed, '22.23.3'), FIXTURE_API)
  assert.throws(() => requireFixtureEnvironment({}, '22.23.3'))
  assert.throws(() => requireFixtureEnvironment(allowed, '24.0.0'))
  assert.throws(() => requireFixtureEnvironment({ ...allowed, LPC_API_E2E: 'production' }, '22.23.3'))
  for (const target of [
    'https://example.com/api', 'http://localhost:18124/api',
    'http://127.0.0.1:8123/api', `${FIXTURE_API}/`, `${FIXTURE_API}?proxy=other`,
    'http://user:password@127.0.0.1:18124/api'
  ]) {
    assert.throws(() => requireFixtureEnvironment({ ...allowed, LPC_API_E2E_BASE_URL: target }, '22.23.3'))
  }
})

test('fixture path guard refuses absolute URLs, traversal, fragments, and encoded escapes', () => {
  assert.equal(fixtureUrl(FIXTURE_API, '/picture/get/vo?id=102').href, `${FIXTURE_API}/picture/get/vo?id=102`)
  for (const path of ['https://example.com', '//example.com', '/../other', '/%2e%2e/other', '/user#other']) {
    assert.throws(() => fixtureUrl(FIXTURE_API, path))
  }
})

test('incremental SSE handles split UTF-8, CRLF, comments, multiline data and explicit done', async () => {
  const bytes = Buffer.from(': heartbeat\r\ndata: 你\r\ndata: 好\r\n\r\ndata: 呀\r\n\r\nevent: done\r\ndata:\r\n\r\n')
  const body = Readable.from([...bytes].map(byte => Uint8Array.of(byte)))
  assert.deepEqual(await consumeSse(body), { chunks: ['你\n好', '呀'], text: '你\n好呀', done: true })
})

test('SSE rejects EOF without done, truncated frames, errors, empty replies and post-done data', () => {
  for (const data of [
    'data: partial\n\n',
    'data: reply\n\nevent: done\ndata:',
    'data: reply\n\nevent: error\ndata: failure\n\n',
    'event: done\ndata:\n\n',
    'data: reply\n\nevent: done\ndata:\n\ndata: extra\n\n',
    'data: reply\n\nevent: done\ndata: unexpected\n\n'
  ]) {
    assert.throws(() => {
      const parser = createSseParser()
      parser.push(data)
      parser.finish()
    })
  }
})
