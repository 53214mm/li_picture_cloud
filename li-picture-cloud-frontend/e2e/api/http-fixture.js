import assert from 'node:assert/strict'

export const FIXTURE_API = 'http://127.0.0.1:18124/api'
const MAX_RESPONSE_BYTES = 1024 * 1024

// Deliberately no default target and no automatic server startup. Both settings are
// required even when invoked directly with node --test. This suite mutates only a
// fresh, disposable test,e2e H2 process, never an arbitrary local/remote API.
export function requireFixtureEnvironment(env = process.env, nodeVersion = process.versions.node) {
  assert.equal(nodeVersion.split('.')[0], '22', 'API checks require Node 22')
  assert.equal(env.LPC_API_E2E, 'owned-test-e2e',
    'Set LPC_API_E2E=owned-test-e2e only for a fresh disposable H2 fixture backend')
  assert.equal(env.LPC_API_E2E_BASE_URL, FIXTURE_API,
    `LPC_API_E2E_BASE_URL must be exactly ${FIXTURE_API}; alternate targets are refused`)
  return FIXTURE_API
}

export function fixtureUrl(base, path) {
  assert.equal(base, FIXTURE_API, 'Only the isolated fixture API is permitted')
  assert.match(path, /^\/[a-z][a-z0-9/?=&_-]*$/i, 'Expected a relative fixture API path')
  const url = new URL(`${base}${path}`)
  assert.equal(url.origin, 'http://127.0.0.1:18124')
  assert.ok(url.pathname.startsWith('/api/'))
  return url
}

// Incremental framing is intentional: TCP chunks need not align with SSE frames,
// CRLF pairs, or UTF-8 code points. EOF without an explicit done event is failure.
export function createSseParser() {
  let pending = ''
  let terminal = false
  const chunks = []
  function frame(value) {
    let event = 'message'
    const data = []
    for (const line of value.split('\n')) {
      if (!line || line.startsWith(':')) continue
      const colon = line.indexOf(':')
      const field = colon < 0 ? line : line.slice(0, colon)
      let content = colon < 0 ? '' : line.slice(colon + 1)
      if (content.startsWith(' ')) content = content.slice(1)
      if (field === 'event') event = content
      if (field === 'data') data.push(content)
    }
    if (!data.length) return
    assert.equal(terminal, false, 'SSE data arrived after done')
    assert.notEqual(event, 'error', 'SSE emitted an error event')
    if (event === 'done') {
      assert.equal(data.join('\n'), '', 'Expected an empty done event')
      terminal = true
    } else {
      assert.equal(event, 'message', `Unexpected SSE event: ${event}`)
      chunks.push(data.join('\n'))
    }
  }
  return {
    push(text) {
      pending += text
      // Normalize complete CRLF pairs only; a trailing CR may belong to the next chunk.
      pending = pending.replaceAll('\r\n', '\n')
      let boundary
      while ((boundary = pending.indexOf('\n\n')) >= 0) {
        frame(pending.slice(0, boundary))
        pending = pending.slice(boundary + 2)
      }
    },
    finish() {
      assert.equal(pending.trim(), '', 'SSE ended with an incomplete frame')
      assert.equal(terminal, true, 'SSE ended without the explicit done event')
      assert.ok(chunks.join('').length > 0, 'SSE completed without reply content')
      return { chunks, text: chunks.join(''), done: true }
    }
  }
}

export async function consumeSse(body) {
  assert.ok(body, 'SSE response has no body')
  const decoder = new TextDecoder('utf-8', { fatal: true })
  const parser = createSseParser()
  let bytes = 0
  for await (const chunk of body) {
    bytes += chunk.byteLength
    assert.ok(bytes <= MAX_RESPONSE_BYTES, 'SSE response exceeded the fixture limit')
    parser.push(decoder.decode(chunk, { stream: true }))
  }
  parser.push(decoder.decode())
  return parser.finish()
}

async function readJson(response) {
  assert.match(response.headers.get('content-type') ?? '', /application\/json/i)
  let bytes = 0
  const parts = []
  for await (const part of response.body) {
    bytes += part.byteLength
    assert.ok(bytes <= MAX_RESPONSE_BYTES, 'JSON response exceeded the fixture limit')
    parts.push(Buffer.from(part))
  }
  return JSON.parse(Buffer.concat(parts).toString('utf8'))
}

export class FixtureSession {
  #base
  #cookies = new Map()

  constructor(base) {
    assert.equal(base, FIXTURE_API)
    this.#base = base
  }

  async request(path, { method = 'GET', body, sse = false } = {}) {
    // Match the shipped stream client: the controller inherits a JSON produces
    // declaration, so an explicit Accept: text/event-stream is rejected (406).
    // A successful stream must still return text/event-stream below.
    const headers = sse ? {} : { Accept: 'application/json' }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (this.#cookies.size) {
      headers.Cookie = [...this.#cookies].map(([name, value]) => `${name}=${value}`).join('; ')
    }
    const response = await fetch(fixtureUrl(this.#base, path), {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'error', signal: AbortSignal.timeout(sse ? 15_000 : 10_000)
    })
    // Never follow a redirect with fixture credentials or cookies.
    assert.ok(response.status < 300 || response.status >= 400, 'Fixture redirects are refused')
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0]
      const equals = pair.indexOf('=')
      if (equals < 1) continue
      const name = pair.slice(0, equals)
      const value = pair.slice(equals + 1)
      if (/;\s*max-age=0(?:;|$)/i.test(cookie) || !value) this.#cookies.delete(name)
      else this.#cookies.set(name, value)
    }
    return response
  }

  async json(path, { status = 200, code = 0, ...options } = {}) {
    const response = await this.request(path, options)
    const envelope = await readJson(response)
    assert.equal(response.status, status, `${options.method ?? 'GET'} ${path}: HTTP status`)
    assert.equal(envelope.code, code, `${options.method ?? 'GET'} ${path}: application code`)
    return envelope.data
  }

  async chat(message) {
    const response = await this.request('/companion/chat/stream', {
      method: 'POST', body: { message }, sse: true
    })
    assert.equal(response.status, 200, 'SSE HTTP status')
    assert.match(response.headers.get('content-type') ?? '', /^text\/event-stream(?:;|$)/i)
    return consumeSse(response.body)
  }
}
