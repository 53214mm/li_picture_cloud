import test from 'node:test'
import assert from 'node:assert/strict'
import { createCompanionChatSession, emptyCompanionChatState } from '../src/presentation/companionChatSession.js'

function deferred() {
  let resolve; let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function setup(overrides = {}) {
  let state
  const changes = []
  const histories = []
  const streams = []
  const session = createCompanionChatSession({
    readHistory: options => {
      const pending = deferred()
      histories.push({ ...pending, ...options })
      return pending.promise
    },
    stream: (content, options) => {
      const pending = deferred()
      streams.push({ ...pending, content, ...options })
      return pending.promise
    },
    ...overrides,
    onChange: value => { state = value; changes.push(value) }
  })
  return { session, changes, histories, streams, get state() { return state } }
}

async function ready(f, records = []) {
  const release = f.session.acquire()
  await Promise.resolve()
  f.histories.at(-1).resolve({ records })
  await f.histories.at(-1).promise
  return release
}

test('first owner reads once; another owner shares loading, history, and draft', async () => {
  const f = setup()
  const first = f.session.acquire()
  const second = f.session.acquire()
  assert.equal(f.state.loading, true)
  assert.equal(f.state.loaded, false)
  await Promise.resolve()
  assert.equal(f.histories.length, 1)
  f.session.setDraft('尚未发出')
  assert.equal(await f.session.send(), false)
  first(); first()
  assert.equal(f.histories[0].signal.aborted, false)
  f.histories[0].resolve({ records: [{ role: 'COMPANION', content: '历史' }] })
  await f.histories[0].promise
  assert.equal(f.state.loaded, true)
  assert.equal(f.state.loading, false)
  assert.equal(f.state.messages[0].content, '历史')
  assert.equal(f.state.draft, '尚未发出')
  const third = f.session.acquire()
  await Promise.resolve()
  assert.equal(f.histories.length, 1)
  second(); third()
})

test('send trims once, synchronously locks duplicates, and publishes immutable partial replies', async () => {
  const f = setup()
  await ready(f, [{ role: 'COMPANION', content: '已有对话' }])
  f.session.acquire()
  f.session.setDraft('  你好  ')
  const pending = f.session.send()
  assert.equal(f.state.phase, 'waiting')
  assert.equal(f.state.draft, '')
  assert.equal(await f.session.send(), false)
  assert.equal(await f.session.reload(), false)
  assert.equal(f.streams.length, 1)
  assert.equal(f.streams[0].content, '你好')
  assert.deepEqual(f.state.messages.map(message => message.role), ['COMPANION', 'USER', 'COMPANION'])
  const waiting = f.state
  f.streams[0].onChunk('你')
  const first = f.state
  f.streams[0].onChunk('好呀')
  const second = f.state
  assert.equal(first.phase, 'streaming')
  assert.equal(waiting.messages.at(-1).content, '')
  assert.equal(first.messages.at(-1).content, '你')
  assert.equal(second.messages.at(-1).content, '你好呀')
  for (const value of f.changes) {
    assert.equal(Object.isFrozen(value), true)
    assert.equal(Object.isFrozen(value.messages), true)
    assert.equal(value.messages.every(Object.isFrozen), true)
  }
  assert.notEqual(first.messages[0], second.messages[0])
  assert.notEqual(first.messages.at(-1), second.messages.at(-1))
  assert.equal(first.messages.at(-1).localKey, second.messages.at(-1).localKey)
  assert.throws(() => { first.messages.at(-1).content = 'mutated' }, TypeError)
  f.streams[0].onDone()
  f.streams[0].onChunk('不应追加')
  f.streams[0].resolve()
  assert.equal(await pending, true)
  assert.equal(f.state.phase, 'idle')
  assert.equal(f.state.messages.at(-1).content, '你好呀')
  assert.equal(f.state.needsRefresh, false)
})

test('one release keeps an active stream; final release aborts, clears history, and ignores late callbacks', async () => {
  const f = setup()
  const first = await ready(f)
  const second = f.session.acquire()
  f.session.setDraft('已经提交')
  const pending = f.session.send()
  f.streams[0].onChunk('收到一部分')
  f.session.setDraft('另一个未发送草稿')
  first()
  assert.equal(f.streams[0].signal.aborted, false)
  f.streams[0].onChunk('，仍在读取')
  assert.equal(f.state.messages.at(-1).content, '收到一部分，仍在读取')
  second()
  assert.equal(f.streams[0].signal.aborted, true)
  assert.equal(f.state.loaded, false)
  assert.deepEqual(f.state.messages, [])
  assert.equal(f.state.draft, '另一个未发送草稿')
  assert.equal(f.state.phase, 'idle')
  assert.equal(f.state.needsRefresh, true)
  assert.match(f.state.notice, /未确认.*刷新/)
  const closed = f.state
  f.streams[0].onChunk('迟到文本')
  f.streams[0].onError(new Error('迟到错误'))
  f.streams[0].onDone()
  f.streams[0].resolve()
  assert.equal(await pending, false)
  assert.equal(f.state, closed)
  first(); second()
  assert.equal(f.state, closed)
})

test('last release during history aborts it, preserves draft, and a new owner gets an independent read', async () => {
  const f = setup()
  const release = f.session.acquire()
  await Promise.resolve()
  f.session.setDraft('回来继续写')
  release()
  assert.equal(f.histories[0].signal.aborted, true)
  assert.equal(f.state.draft, '回来继续写')
  assert.equal(f.state.needsRefresh, false)
  f.session.acquire()
  await Promise.resolve()
  assert.equal(f.histories.length, 2)
  f.histories[0].resolve({ records: [{ role: 'USER', content: '过期历史' }] })
  await f.histories[0].promise
  assert.equal(f.state.loading, true)
  assert.deepEqual(f.state.messages, [])
  f.histories[1].resolve({ records: [{ role: 'COMPANION', content: '新读取' }] })
  await f.histories[1].promise
  assert.equal(f.state.messages[0].content, '新读取')
  assert.equal(f.state.draft, '回来继续写')
})

test('release before queued history starts prevents any request', async () => {
  const f = setup()
  f.session.acquire()()
  await Promise.resolve()
  assert.equal(f.histories.length, 0)
  assert.deepEqual(f.state, emptyCompanionChatState())
})

test('history failure blocks send and does not repeatedly load when another surface mounts', async () => {
  const f = setup()
  f.session.acquire()
  await Promise.resolve()
  f.histories[0].reject(new Error('历史暂不可用'))
  await f.histories[0].promise.catch(() => {})
  assert.equal(f.state.loadError, '历史暂不可用')
  assert.equal(f.state.loaded, false)
  assert.equal(f.state.loading, false)
  f.session.setDraft('不该发出')
  assert.equal(await f.session.send(), false)
  f.session.acquire()
  await Promise.resolve()
  assert.equal(f.histories.length, 1)
  const reload = f.session.reload()
  const duplicate = f.session.reload()
  assert.equal(reload, duplicate)
  await Promise.resolve()
  f.histories[1].resolve({ records: [] })
  assert.equal(await reload, true)
  assert.equal(f.state.loaded, true)
  assert.equal(f.state.loadError, '')
  assert.equal(f.streams.length, 0)
  assert.equal(f.state.draft, '不该发出')
})

test('missing or malformed history is an error rather than a successful empty conversation', async () => {
  for (const history of [null, {}, { records: null }, { records: {} }, { records: [null] },
    { records: [{ role: 'SYSTEM', content: 'not dialogue' }] },
    { records: [{ role: 'USER', content: 42 }] }]) {
    const f = setup({ readHistory: async () => history })
    f.session.acquire()
    assert.equal(await f.session.reload(), false)
    assert.equal(f.state.loaded, false)
    assert.equal(f.state.loading, false)
    assert.ok(f.state.loadError)
    f.session.setDraft('不该发出')
    assert.equal(await f.session.send(), false)
  }
})

test('reload serializes history before a new send and never overwrites a sent message', async () => {
  const f = setup()
  await ready(f, [{ role: 'USER', content: '旧历史' }])
  f.session.setDraft('接着说')
  const reload = f.session.reload()
  assert.equal(await f.session.send(), false)
  await Promise.resolve()
  f.histories[1].resolve({ records: [{ role: 'USER', content: '权威历史' }] })
  await reload
  const sending = f.session.send()
  assert.deepEqual(f.state.messages.map(message => message.content), ['权威历史', '接着说', ''])
  assert.equal(await f.session.reload(), false)
  f.streams[0].resolve()
  await sending
  assert.deepEqual(f.state.messages.map(message => message.content), ['权威历史', '接着说'])
})

test('SSE errors retain real partial text, never quote errors, and require explicit authoritative refresh', async () => {
  for (const partial of ['', '真实的部分回复']) {
    const f = setup()
    await ready(f)
    f.session.setDraft('请回答')
    const pending = f.session.send()
    if (partial) f.streams[0].onChunk(partial)
    f.streams[0].onError(new Error('服务暂不可用'))
    f.streams[0].onChunk('错误后的无效文本')
    f.streams[0].onDone()
    f.streams[0].resolve()
    assert.equal(await pending, false)
    assert.equal(f.state.phase, 'idle')
    assert.equal(f.state.sendError, '服务暂不可用')
    assert.equal(f.state.needsRefresh, true)
    assert.equal(f.state.messages.some(message => message.content.includes('服务暂不可用')), false)
    assert.deepEqual(f.state.messages.map(message => message.content), partial ? ['请回答', partial] : ['请回答'])
    assert.equal(f.state.draft, '')
    f.session.setDraft('用户自己的新草稿')
    assert.equal(await f.session.send(), false)
    const reload = f.session.reload()
    await Promise.resolve()
    f.histories[1].resolve({ records: [{ role: 'USER', content: '请回答' }, { role: 'COMPANION', content: '最终历史' }] })
    assert.equal(await reload, true)
    assert.equal(f.state.needsRefresh, false)
    assert.equal(f.state.sendError, '')
    assert.equal(f.state.notice, '')
    assert.equal(f.state.draft, '用户自己的新草稿')
    assert.equal(f.streams.length, 1)
    assert.deepEqual(f.state.messages.map(message => message.content), ['请回答', '最终历史'])
  }
})

test('network rejection never restores a submitted draft or retries automatically', async () => {
  const f = setup()
  await ready(f)
  f.session.setDraft('已发出的内容')
  const pending = f.session.send()
  f.streams[0].reject(new Error('network lost'))
  assert.equal(await pending, false)
  assert.equal(f.state.draft, '')
  assert.equal(f.state.needsRefresh, true)
  assert.equal(f.state.messages.length, 1)
  assert.equal(f.state.messages[0].role, 'USER')
  assert.equal(await f.session.send(), false)
  assert.equal(f.streams.length, 1)
  const reload = f.session.reload()
  await Promise.resolve()
  f.histories[1].reject(new Error('still offline'))
  assert.equal(await reload, false)
  assert.equal(f.state.needsRefresh, true)
  assert.match(f.state.notice, /刷新/)
})

test('reopening reads fresh history without silently clearing interrupted-send uncertainty', async () => {
  const f = setup()
  const release = await ready(f)
  f.session.setDraft('一次请求')
  const pending = f.session.send()
  release()
  f.streams[0].reject(new Error('aborted'))
  await pending
  await ready(f, [{ role: 'USER', content: '一次请求' }])
  assert.equal(f.state.needsRefresh, true)
  f.session.setDraft('新的内容')
  assert.equal(await f.session.send(), false)
  const reload = f.session.reload()
  await Promise.resolve()
  f.histories[2].resolve({ records: [{ role: 'USER', content: '一次请求' }, { role: 'COMPANION', content: '已完成' }] })
  await reload
  assert.equal(f.state.needsRefresh, false)
  assert.equal(f.streams.length, 1)
})

test('explicit reload can reconcile an already running automatic history read', async () => {
  const f = setup()
  const release = await ready(f)
  f.session.setDraft('会中断')
  const sending = f.session.send()
  release()
  f.streams[0].resolve()
  await sending
  f.session.acquire()
  const reload = f.session.reload()
  await Promise.resolve()
  assert.equal(f.histories.length, 2)
  f.histories[1].resolve({ records: [] })
  await reload
  assert.equal(f.state.needsRefresh, false)
})

test('account reset clears all private state; old releases and stream callbacks cannot affect new owners', async () => {
  const f = setup()
  const oldRelease = await ready(f, [{ role: 'COMPANION', content: '旧账号历史' }])
  f.session.setDraft('旧账号已发送')
  const oldSending = f.session.send()
  f.streams[0].onChunk('旧账号部分回复')
  f.session.setDraft('旧账号草稿')
  f.session.reset()
  assert.equal(f.streams[0].signal.aborted, true)
  assert.deepEqual(f.state, emptyCompanionChatState())
  assert.equal(f.session.setDraft('未持有者不能写入'), false)
  const newRelease = f.session.acquire()
  await Promise.resolve()
  oldRelease(); oldRelease()
  assert.equal(f.histories[1].signal.aborted, false)
  f.streams[0].onChunk('迟到的私人文本')
  f.streams[0].onError(new Error('旧账号错误'))
  f.streams[0].onDone()
  f.streams[0].resolve()
  assert.equal(await oldSending, false)
  assert.deepEqual(f.state.messages, [])
  f.histories[1].resolve({ records: [{ role: 'COMPANION', content: '新账号历史' }] })
  await f.histories[1].promise
  assert.equal(f.state.messages[0].content, '新账号历史')
  assert.equal(f.state.draft, '')
  assert.equal(f.state.needsRefresh, false)
  newRelease()
  assert.deepEqual(f.state, emptyCompanionChatState())
})

test('account reset invalidates both successful and failed late history reads', async () => {
  for (const lateError of [false, true]) {
    const f = setup()
    const release = f.session.acquire()
    await Promise.resolve()
    f.session.setDraft('旧草稿')
    f.session.reset()
    assert.equal(f.histories[0].signal.aborted, true)
    f.session.acquire()
    await Promise.resolve()
    release()
    if (lateError) f.histories[0].reject(new Error('旧错误'))
    else f.histories[0].resolve({ records: [{ role: 'USER', content: '旧历史' }] })
    await f.histories[0].promise.catch(() => {})
    assert.equal(f.state.loading, true)
    assert.equal(f.state.loadError, '')
    assert.equal(f.state.draft, '')
    assert.deepEqual(f.state.messages, [])
    f.histories[1].resolve({ records: [] })
    await f.histories[1].promise
    assert.equal(f.state.loaded, true)
  }
})

test('blank and over-limit drafts never send; 500 Unicode code points are accepted', async () => {
  const f = setup()
  assert.equal(await f.session.send(), false)
  assert.equal(await f.session.reload(), false)
  await ready(f)
  for (const value of ['', '   ', '字'.repeat(501), '😀'.repeat(501)]) {
    f.session.setDraft(value)
    assert.equal(await f.session.send(), false)
    assert.equal(f.state.draft, value)
  }
  assert.equal(f.streams.length, 0)
  assert.equal(f.state.messages.length, 0)
  assert.match(f.state.sendError, /500/)
  f.session.setDraft(` ${'😀'.repeat(500)} `)
  const pending = f.session.send()
  assert.equal(f.streams.length, 1)
  assert.equal(f.state.sendError, '')
  f.streams[0].resolve()
  assert.equal(await pending, true)
})

test('normal EOF with no chunks ends the request without inventing a companion quote', async () => {
  const f = setup({ stream: async () => {} })
  const release = await ready(f)
  f.session.setDraft('空回应')
  assert.equal(await f.session.send(), true)
  assert.deepEqual(f.state.messages.map(message => message.content), ['空回应'])
  assert.equal(f.state.phase, 'idle')
  assert.equal(f.state.sendError, '')
  assert.equal(f.state.notice, '')
  assert.equal(f.state.needsRefresh, false)
  release()
  assert.equal(f.state.needsRefresh, false)
})

test('a completed terminal callback is not reclassified as uncertain during client cleanup', async () => {
  const f = setup()
  const release = await ready(f)
  f.session.setDraft('已收到终态')
  const pending = f.session.send()
  f.streams[0].onChunk('回复')
  f.streams[0].onDone()
  release()
  assert.equal(f.state.needsRefresh, false)
  f.streams[0].resolve()
  assert.equal(await pending, false)
})
