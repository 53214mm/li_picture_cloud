import { spawn } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { createWriteStream } from 'node:fs'
import { mkdtemp, readFile, readdir, writeFile } from 'node:fs/promises'
import { connect } from 'node:net'
import { tmpdir } from 'node:os'
import { delimiter, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import { API_E2E_OPT_IN, API_E2E_URL, apiE2eFiles, backendArguments, fixtureEnvironment } from './lib/api-e2e-policy.mjs'

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repository = resolve(frontend, '..')
const children = []
let stopping = false
let stopPromise
let output
let testFiles = []
const startedAt = new Date().toISOString()
const portChecks = []

function stop() {
  if (stopPromise) return stopPromise
  stopping = true
  stopPromise = (async () => {
    for (const item of [...children].reverse()) {
      if (item.closed) continue
      item.child.kill('SIGTERM')
      await Promise.race([item.done, delay(5000, undefined, { ref: false })])
      if (!item.closed) {
        item.child.kill('SIGKILL')
        await item.done
      }
    }
  })()
  return stopPromise
}
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.once(signal, () => {
    process.exitCode = 130
    void stop()
  })
}

function launch(label, command, args, env, cwd = repository) {
  if (stopping) throw new Error('Fixture run interrupted')
  const log = createWriteStream(join(output, `${label}.log`))
  const child = spawn(command, args, { cwd, env, stdio: ['ignore', 'pipe', 'pipe'], shell: false })
  const item = { label, child, tail: '', closed: false, code: null, error: null }
  children.push(item)
  function capture(chunk) {
    log.write(chunk)
    item.tail = (item.tail + chunk.toString()).slice(-32_768)
  }
  child.stdout.on('data', capture)
  child.stderr.on('data', capture)
  item.done = new Promise(resolveDone => {
    child.on('error', error => { item.error = error })
    child.on('close', code => {
      item.closed = true
      item.code = code
      log.end(resolveDone)
    })
  })
  return item
}

async function waitUntil(item, ready, timeout) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    if (stopping) throw new Error('Fixture run interrupted')
    if (item.closed) throw new Error(`Fixture process exited (${item.code}): ${item.error?.message || item.tail.slice(-4000)}`)
    if (await ready()) return
    await delay(150)
  }
  throw new Error('Timed out waiting for owned fixture process; inspect saved logs')
}

async function ensureUnused(port) {
  await new Promise((resolveCheck, reject) => {
    const socket = connect({ host: '127.0.0.1', port })
    socket.setTimeout(2000)
    socket.once('connect', () => { socket.destroy(); reject(new Error(`Port ${port} is occupied; refusing to reuse any existing service`)) })
    socket.once('timeout', () => { socket.destroy(); reject(new Error(`Cannot verify local port ${port}`)) })
    socket.once('error', error => {
      socket.destroy()
      if (error.code === 'ECONNREFUSED') { portChecks.push(port); resolveCheck() }
      else reject(error)
    })
  })
}

try {
  if (process.platform === 'win32') throw new Error('This isolated runner currently requires a POSIX Maven wrapper environment')
  if (Number(process.versions.node.split('.')[0]) !== 22) throw new Error('Run this fixture harness with Node 22')
  const javaHome = process.env.LPC_API_JAVA_HOME || process.env.JAVA_HOME
  const mavenHome = process.env.LPC_API_MAVEN_HOME
  const mavenRepo = process.env.LPC_API_MAVEN_REPO
  const redisBinary = process.env.LPC_API_REDIS_SERVER
  if (!javaHome || !mavenHome || !mavenRepo || !redisBinary) {
    throw new Error('Set LPC_API_JAVA_HOME (or JAVA_HOME), LPC_API_MAVEN_HOME, LPC_API_MAVEN_REPO and LPC_API_REDIS_SERVER to prepared local tools; this offline runner installs nothing')
  }
  output = await mkdtemp(join(tmpdir(), 'lpc-api-e2e-'))
  console.log(`API-only fixture evidence: ${output}`)
  // A partial checkout must fail before any backend or test mutations start.
  testFiles = apiE2eFiles(await readdir(join(frontend, 'e2e/api')))
  const env = fixtureEnvironment(process.env, javaHome, mavenHome)
  await ensureUnused(6380)
  await ensureUnused(18124)
  const classpathFile = join(output, 'classpath.txt')
  const prepare = launch('prepare', './mvnw', [
    '-o', '-B', '-ntp', `-Dmaven.repo.local=${mavenRepo}`, '-DskipTests',
    '-Dspring.profiles.active=test,e2e', `-Dmdep.outputFile=${classpathFile}`,
    '-DincludeScope=test', 'test-compile', 'dependency:build-classpath'
  ], env)
  await Promise.race([prepare.done, delay(180_000, undefined, { ref: false }).then(() => { throw new Error('Offline preparation timed out') })])
  if (prepare.error || prepare.code !== 0) throw new Error(`Offline preparation failed; see ${join(output, 'prepare.log')}`)
  const classpath = [join(repository, 'target/test-classes'), join(repository, 'target/classes'), (await readFile(classpathFile, 'utf8')).trim()].join(delimiter)
  const redis = launch('redis', redisBinary, [
    '--bind', '127.0.0.1', '--port', '6380', '--protected-mode', 'yes',
    '--save', '', '--appendonly', 'no', '--daemonize', 'no', '--dir', output
  ], env, output)
  await waitUntil(redis, () => redis.tail.includes('Ready to accept connections'), 15_000)
  const backend = launch('backend', join(javaHome, 'bin/java'), backendArguments(classpath, `lpc_api_${randomBytes(8).toString('hex')}`), env)
  await waitUntil(backend, async () => {
    // Readiness belongs to this child, never merely to an existing listener.
    if (!backend.tail.includes('Started LiPictureCloudApplication')) return false
    const response = await fetch(`${API_E2E_URL}/v3/api-docs`, { redirect: 'error', signal: AbortSignal.timeout(2000) }).catch(() => null)
    return response?.ok === true
  }, 180_000)
  if (redis.closed) throw new Error('Owned Redis exited before API checks')
  const checks = launch('checks', process.execPath, ['--test', '--test-concurrency=1', ...testFiles], {
    ...env, LPC_API_E2E: API_E2E_OPT_IN, LPC_API_E2E_BASE_URL: API_E2E_URL
  }, frontend)
  await Promise.race([checks.done, delay(120_000, undefined, { ref: false }).then(() => { throw new Error('API checks timed out') })])
  process.stdout.write(await readFile(join(output, 'checks.log'), 'utf8'))
  if (checks.error || checks.code !== 0) throw new Error('API checks failed')
  if (backend.closed || redis.closed) throw new Error('Owned fixture exited during API checks')
  console.log('API-only checks passed. Browser UI, layout and browser-to-API acceptance remain separate.')
} catch (error) {
  console.error(error.message)
  process.exitCode ||= 1
} finally {
  await stop()
  if (output) await writeFile(join(output, 'run.json'), JSON.stringify({
    startedAt, finishedAt: new Date().toISOString(), api: API_E2E_URL,
    profile: 'test,e2e', fixtures: 'fresh in-memory H2 and owned localhost Redis',
    testFiles, portChecks, exitCode: process.exitCode || 0,
    children: children.map(({ label, child, closed, code, error }) => ({
      label, pid: child.pid, closed, exitCode: code, error: error?.message || null
    }))
  }, null, 2) + '\n')
}
