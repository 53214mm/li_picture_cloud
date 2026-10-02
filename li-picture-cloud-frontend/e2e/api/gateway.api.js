import assert from 'node:assert/strict'
import test from 'node:test'
import { FixtureSession, requireFixtureEnvironment } from './http-fixture.js'

// Real local HTTP/controllers/H2 only. The owning runner fixes test,e2e and the
// connectivity stub: the HTTPS endpoint below is metadata, never a live probe.
// Fixed dummy keys are submitted only to the disposable localhost fixture.
const FIXTURE_PASSWORD = 'LocalUser123!'
const INITIAL_KEY = 'sk-e2e-test-key-1234'
const ROTATED_KEY = 'sk-e2e-rotated-5678'
const ENDPOINT = 'https://api.deepseek.com/v1'
const TASK = 'LANGUAGE_AGENT'
const CREDENTIAL_FIELDS = ['id', 'subjectId', 'provider', 'tail4', 'algorithm', 'revision']
const CONNECTION_FIELDS = ['id', 'subjectId', 'provider', 'displayName', 'endpointUri', 'modelCode', 'credentialId', 'enabled', 'revision']
const ROUTING_FIELDS = ['id', 'task', 'connectionId', 'revision']
const CAPABILITY_FIELDS = ['id', 'connectionId', 'provider', 'modelCode', 'text', 'vision', 'toolCall', 'structuredOutput', 'reasoning', 'embedding', 'imageGeneration', 'maxContextTokens', 'syncAsync', 'costHint', 'createdTime']
const USAGE_FIELDS = ['id', 'task', 'connectionId', 'provider', 'modelCode', 'costSource', 'success', 'safeErrorCode', 'correlationId', 'createdTime', 'inputTokens', 'outputTokens', 'imageCount']

function decimalId(value) {
  assert.equal(typeof value, 'string', 'Long identifiers must arrive as decimal strings')
  assert.match(value, /^[1-9]\d*$/)
  return value
}

function fields(value, expected) {
  assert.ok(value && typeof value === 'object' && !Array.isArray(value), 'Expected a safe gateway view')
  assert.deepEqual(Object.keys(value).sort(), [...expected].sort(), 'Gateway view exposes only its documented safe fields')
}

// Check both success and error envelopes, before any assertion can print a body.
// Exact view allowlists below also reject ciphertext, prompts, raw usage and
// other unexpected fields. Tail numbers are allowed; complete keys never are.
async function gatewayJson(session, path, { status = 200, code = 0, ...options } = {}) {
  const response = await session.request(path, options)
  assert.match(response.headers.get('content-type') ?? '', /application\/json/i)
  const parts = []
  let bytes = 0
  for await (const part of response.body) {
    bytes += part.byteLength
    assert.ok(bytes <= 1024 * 1024, 'Gateway response exceeded the fixture limit')
    parts.push(Buffer.from(part))
  }
  const raw = Buffer.concat(parts).toString('utf8')
  for (const key of [INITIAL_KEY, ROTATED_KEY]) {
    assert.equal(raw.includes(key), false, 'Gateway must never echo a complete fixture key')
  }
  const envelope = JSON.parse(raw)
  const serialized = JSON.stringify(envelope)
  for (const key of [INITIAL_KEY, ROTATED_KEY]) {
    assert.equal(serialized.includes(key), false, 'Escaped response data must not expose a complete fixture key')
  }
  assert.equal(/"(?:apiKey|cipherText|plaintext|encryptedCredential|rawUsage|prompt|responseBody)"\s*:/i.test(serialized), false,
    'Gateway responses must not expose credential or raw model payload fields')
  fields(envelope, ['code', 'data', 'message'])
  assert.equal(response.status, status, `${options.method ?? 'GET'} ${path}: HTTP status`)
  assert.equal(envelope.code, code, `${options.method ?? 'GET'} ${path}: application code`)
  return envelope.data
}

function assertCredential(credential, tail) {
  fields(credential, CREDENTIAL_FIELDS)
  decimalId(credential.id)
  assert.equal(credential.subjectId, '7')
  assert.equal(credential.provider, 'DEEPSEEK')
  assert.equal(credential.tail4, tail)
  assert.equal(credential.algorithm, 'AES_GCM_V1')
  assert.equal(credential.revision, '0')
}

function assertConnection(connection, name, credentialId, enabled, revision) {
  fields(connection, CONNECTION_FIELDS)
  decimalId(connection.id)
  assert.deepEqual(connection, {
    id: connection.id, subjectId: '7', provider: 'DEEPSEEK', displayName: name,
    endpointUri: ENDPOINT, modelCode: 'deepseek-chat', credentialId, enabled, revision
  })
}

function assertCapability(profile, connectionId) {
  fields(profile, CAPABILITY_FIELDS)
  decimalId(profile.id)
  assert.ok(Number.isFinite(Date.parse(profile.createdTime)))
  assert.deepEqual(profile, {
    id: profile.id, connectionId, provider: 'DEEPSEEK', modelCode: 'deepseek-chat',
    text: true, vision: false, toolCall: true, structuredOutput: true,
    reasoning: false, embedding: false, imageGeneration: false,
    maxContextTokens: 64000, syncAsync: 'SYNC', costHint: 'CHEAP', createdTime: profile.createdTime
  })
}

function assertUsage(record, connectionId) {
  fields(record, USAGE_FIELDS)
  decimalId(record.id)
  assert.match(record.correlationId, /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/)
  assert.ok(Number.isFinite(Date.parse(record.createdTime)))
  assert.deepEqual(record, {
    id: record.id, task: 'CONNECTIVITY_CHECK', connectionId, provider: 'DEEPSEEK',
    modelCode: 'deepseek-chat', costSource: 'BYOK', success: true, safeErrorCode: null,
    correlationId: record.correlationId, createdTime: record.createdTime,
    inputTokens: null, outputTokens: null, imageCount: null
  })
}

test('model gateway real local API story (HTTP and stub only; not browser acceptance)', { timeout: 120_000 }, async t => {
  // Must run before constructing sessions or issuing the first request.
  const base = requireFixtureEnvironment()
  let owner = new FixtureSession(base)
  const other = new FixtureSession(base)
  const anonymous = new FixtureSession(base)
  const sessions = [owner, other]
  const credentials = new Set()
  const connections = new Set()
  const routes = new Set()
  let originalCredential
  let primary
  let secondary
  let routing
  let firstProfile
  let firstUsage
  let ownerUsageBefore
  let otherUsageBefore

  async function login(session, account, id) {
    const user = await session.json('/user/login', {
      method: 'POST', body: { userAccount: account, userPassword: FIXTURE_PASSWORD }
    })
    assert.equal(user.id, id)
    assert.equal(user.userAccount, account)
    assert.equal(user.userRole, 'user')
  }

  async function list(session, kind) {
    const result = await gatewayJson(session, `/model/${kind}`)
    assert.ok(Array.isArray(result))
    const allowed = { credentials: CREDENTIAL_FIELDS, connections: CONNECTION_FIELDS, routing: ROUTING_FIELDS, usage: USAGE_FIELDS }[kind]
    for (const item of result) fields(item, allowed)
    return result
  }

  async function cleanup(session) {
    // Clear only this story's tracked rules before their connection targets.
    // Never bulk-delete an account's pre-existing resources or audit history.
    for (const task of routes) {
      assert.equal(await gatewayJson(session, `/model/routing/${task}`, { method: 'DELETE' }), true)
      routes.delete(task)
    }
    for (const id of connections) {
      assert.equal(await gatewayJson(session, `/model/connections/${id}`, { method: 'DELETE' }), true)
      connections.delete(id)
    }
    for (const id of credentials) {
      assert.equal(await gatewayJson(session, `/model/credentials/${id}`, { method: 'DELETE' }), true)
      credentials.delete(id)
    }
  }

  t.after(async () => {
    try {
      if (routes.size || connections.size || credentials.size) {
        // A separate test-owned session can clean up even if relogin failed.
        const cleaner = new FixtureSession(base)
        sessions.push(cleaner)
        await login(cleaner, 'companion_e2e', '7')
        await cleanup(cleaner)
      }
    } finally {
      const results = await Promise.allSettled(sessions.map(session => session.json('/user/logout', { method: 'POST' })))
      assert.ok(results.every(result => result.status === 'fulfilled'), 'Every gateway test session must log out')
    }
  })

  async function stage(name, work) {
    let passed = false
    await t.test(name, async () => { await work(); passed = true })
    assert.ok(passed, `Stopped dependent gateway mutations after: ${name}`)
  }

  await stage('require isolated demo accounts and empty gateway state; anonymous reads are denied', async () => {
    for (const path of ['/model/credentials', '/model/connections', '/model/routing', '/model/usage']) {
      await gatewayJson(anonymous, path, { status: 401, code: 40100 })
    }
    await login(owner, 'companion_e2e', '7')
    await login(other, 'recipe_e2e', '9')
    for (const session of [owner, other]) {
      const home = await session.json('/companion/me')
      assert.equal(home.nutrition.policy, 'DEMO_ONLY')
      assert.equal(home.chatPolicy, 'DEMO_ONLY')
      assert.ok(home.companion, 'The owner harness runs companion.api.js first')
      for (const kind of ['credentials', 'connections', 'routing']) {
        assert.deepEqual(await list(session, kind), [], 'Refusing to overwrite pre-existing gateway resources')
      }
    }
    ownerUsageBefore = await list(owner, 'usage')
    otherUsageBefore = await list(other, 'usage')
  })

  await stage('store a safe credential view and bind two initially disabled connections to it', async () => {
    originalCredential = await gatewayJson(owner, '/model/credentials', {
      method: 'POST', body: { provider: 'DEEPSEEK', apiKey: INITIAL_KEY }
    })
    credentials.add(decimalId(originalCredential.id))
    assertCredential(originalCredential, '1234')
    const created = []
    for (const name of ['API gateway primary', 'API gateway secondary']) {
      const connection = await gatewayJson(owner, '/model/connections', {
        method: 'POST', body: { provider: 'DEEPSEEK', displayName: name, endpoint: ENDPOINT, modelCode: 'deepseek-chat', credentialId: originalCredential.id }
      })
      connections.add(decimalId(connection.id))
      assertConnection(connection, name, originalCredential.id, false, '0')
      created.push(connection)
    }
    ;[primary, secondary] = created
    assert.notEqual(primary.id, secondary.id)
    assert.deepEqual(await list(owner, 'credentials'), [originalCredential])
    assert.equal((await list(owner, 'connections')).length, 2)
    assert.deepEqual(await list(other, 'credentials'), [])
    assert.deepEqual(await list(other, 'connections'), [])
  })

  await stage('foreign credential and connection writes, probes, capabilities and routes are rejected without mutation', async () => {
    await gatewayJson(other, `/model/credentials/${originalCredential.id}`, { method: 'DELETE', status: 403, code: 40300 })
    for (const action of ['enable', 'disable', 'test', 'rotate-credential']) {
      await gatewayJson(other, `/model/connections/${primary.id}/${action}`, {
        method: 'POST', ...(action === 'rotate-credential' ? { body: { apiKey: ROTATED_KEY } } : {}), status: 403, code: 40300
      })
    }
    await gatewayJson(other, `/model/connections/${primary.id}/capability`, { status: 403, code: 40300 })
    await gatewayJson(other, `/model/connections/${primary.id}`, { method: 'DELETE', status: 403, code: 40300 })
    await gatewayJson(other, '/model/connections', {
      method: 'POST', body: { provider: 'DEEPSEEK', displayName: 'Must not be created', endpoint: ENDPOINT, modelCode: 'deepseek-chat', credentialId: originalCredential.id },
      status: 400, code: 40000
    })
    await gatewayJson(other, `/model/routing/${TASK}`, { method: 'PUT', body: { connectionId: primary.id }, status: 400, code: 40000 })
    assert.deepEqual(await list(owner, 'credentials'), [originalCredential])
    const unchanged = await list(owner, 'connections')
    assert.deepEqual(unchanged.find(item => item.id === primary.id), primary)
    assert.deepEqual(unchanged.find(item => item.id === secondary.id), secondary)
    for (const kind of ['credentials', 'connections', 'routing']) assert.deepEqual(await list(other, kind), [])
    assert.deepEqual(await list(other, 'usage'), otherUsageBefore)
    assert.deepEqual(await list(owner, 'usage'), ownerUsageBefore)
  })

  await stage('disabled probe is rejected; enabled stub probe persists safe usage and explicit capability flags', async () => {
    await gatewayJson(owner, `/model/connections/${primary.id}/test`, { method: 'POST', status: 500, code: 50001 })
    await gatewayJson(owner, `/model/connections/${primary.id}/capability`, { status: 404, code: 40400 })
    assert.deepEqual(await list(owner, 'usage'), ownerUsageBefore)
    primary = await gatewayJson(owner, `/model/connections/${primary.id}/enable`, { method: 'POST' })
    secondary = await gatewayJson(owner, `/model/connections/${secondary.id}/enable`, { method: 'POST' })
    assertConnection(primary, 'API gateway primary', originalCredential.id, true, '1')
    assertConnection(secondary, 'API gateway secondary', originalCredential.id, true, '1')
    assert.deepEqual(await gatewayJson(owner, `/model/connections/${primary.id}/test`, { method: 'POST' }), { reachable: true, safeErrorCode: null })
    firstProfile = await gatewayJson(owner, `/model/connections/${primary.id}/capability`)
    assertCapability(firstProfile, primary.id)
    const usage = await list(owner, 'usage')
    assert.equal(usage.length, ownerUsageBefore.length + 1)
    firstUsage = usage[0]
    assertUsage(firstUsage, primary.id)
    assert.deepEqual(usage.slice(1), ownerUsageBefore)
    assert.deepEqual(await list(other, 'usage'), otherUsageBefore)
  })

  await stage('language route survives explicit platform selection and relogin with connections, capability and usage', async () => {
    // This slot was proven empty before any mutation; track the attempted write
    // so a later assertion failure still removes only our rule.
    routes.add(TASK)
    routing = await gatewayJson(owner, `/model/routing/${TASK}`, { method: 'PUT', body: { connectionId: primary.id } })
    fields(routing, ROUTING_FIELDS)
    decimalId(routing.id)
    assert.deepEqual(routing, { id: routing.id, task: TASK, connectionId: primary.id, revision: '0' })
    const platform = await gatewayJson(owner, `/model/routing/${TASK}`, { method: 'PUT', body: { connectionId: null } })
    assert.deepEqual(platform, { ...routing, connectionId: null, revision: '1' })
    routing = await gatewayJson(owner, `/model/routing/${TASK}`, { method: 'PUT', body: { connectionId: primary.id } })
    assert.deepEqual(routing, { ...platform, connectionId: primary.id, revision: '2' })
    assert.deepEqual(await list(owner, 'routing'), [routing])
    assert.deepEqual(await list(other, 'routing'), [])
    assert.equal(await owner.json('/user/logout', { method: 'POST' }), true)
    await gatewayJson(owner, '/model/connections', { status: 401, code: 40100 })
    owner = new FixtureSession(base)
    sessions.push(owner)
    await login(owner, 'companion_e2e', '7')
    assert.equal((await other.json('/user/current')).id, '9')
    assert.deepEqual(await list(owner, 'credentials'), [originalCredential])
    const persisted = await list(owner, 'connections')
    assert.equal(persisted.length, 2)
    assert.deepEqual(persisted.find(item => item.id === primary.id), primary)
    assert.deepEqual(persisted.find(item => item.id === secondary.id), secondary)
    assert.deepEqual(await list(owner, 'routing'), [routing])
    assert.deepEqual(await gatewayJson(owner, `/model/connections/${primary.id}/capability`), firstProfile)
    assert.deepEqual(await list(owner, 'usage'), [firstUsage, ...ownerUsageBefore])
  })

  await stage('rotating one shared-credential connection creates a new vault entry and leaves the other connection unchanged', async () => {
    const before = primary
    primary = await gatewayJson(owner, `/model/connections/${primary.id}/rotate-credential`, { method: 'POST', body: { apiKey: ROTATED_KEY } })
    credentials.add(decimalId(primary.credentialId))
    assert.notEqual(primary.credentialId, originalCredential.id)
    assertConnection(primary, 'API gateway primary', primary.credentialId, true, '2')
    assert.deepEqual(primary, { ...before, credentialId: primary.credentialId, revision: '2' })
    const currentCredentials = await list(owner, 'credentials')
    assert.equal(currentCredentials.length, 2)
    assert.deepEqual(currentCredentials.find(item => item.id === originalCredential.id), originalCredential)
    assertCredential(currentCredentials.find(item => item.id === primary.credentialId), '5678')
    const currentConnections = await list(owner, 'connections')
    assert.equal(currentConnections.length, 2)
    assert.deepEqual(currentConnections.find(item => item.id === primary.id), primary)
    assert.deepEqual(currentConnections.find(item => item.id === secondary.id), secondary)
    assert.deepEqual(await list(owner, 'routing'), [routing])
    // Both the new binding and the retained old binding can be decrypted and
    // reach the configured success stub; this is not live-provider validation.
    for (const connection of [secondary, primary]) {
      assert.deepEqual(await gatewayJson(owner, `/model/connections/${connection.id}/test`, { method: 'POST' }), { reachable: true, safeErrorCode: null })
      assertCapability(await gatewayJson(owner, `/model/connections/${connection.id}/capability`), connection.id)
    }
    const usage = await list(owner, 'usage')
    assert.equal(usage.length, ownerUsageBefore.length + 3)
    assertUsage(usage[0], primary.id)
    assertUsage(usage[1], secondary.id)
    assert.deepEqual(usage.slice(2), [firstUsage, ...ownerUsageBefore])
    assert.equal(new Set(usage.slice(0, 3).map(item => item.id)).size, 3)
    assert.equal(new Set(usage.slice(0, 3).map(item => item.correlationId)).size, 3)
    for (const kind of ['credentials', 'connections', 'routing']) assert.deepEqual(await list(other, kind), [])
    assert.deepEqual(await list(other, 'usage'), otherUsageBefore)
  })

  await stage('clear only owned routes, connections and credentials; retain isolated audit history and log out', async () => {
    const usage = await list(owner, 'usage')
    await cleanup(owner)
    for (const session of [owner, other]) {
      for (const kind of ['credentials', 'connections', 'routing']) assert.deepEqual(await list(session, kind), [])
    }
    assert.deepEqual(await list(owner, 'usage'), usage, 'Resource cleanup must not erase usage audit history')
    assert.deepEqual(await list(other, 'usage'), otherUsageBefore)
    await gatewayJson(owner, `/model/connections/${primary.id}/capability`, { status: 404, code: 40400 })
    assert.equal(await owner.json('/user/logout', { method: 'POST' }), true)
    await gatewayJson(owner, '/model/credentials', { status: 401, code: 40100 })
    assert.equal((await other.json('/user/current')).id, '9')
  })
})
