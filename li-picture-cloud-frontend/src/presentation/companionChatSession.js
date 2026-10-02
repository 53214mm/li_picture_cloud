const UNCERTAIN_NOTICE = '尚未确认这次回复是否完成，请先刷新对话记录，再决定是否发送。'
const LOAD_ERROR = '对话历史读取失败，请稍后重试。'
const SEND_ERROR = '伙伴暂时没法回应，请稍后再试。'

function snapshot(value) {
  return Object.freeze({
    ...value,
    messages: Object.freeze(value.messages.map(message => Object.freeze({ ...message })))
  })
}

export function emptyCompanionChatState() {
  return snapshot({ messages: [], draft: '', loading: false, loadError: '', phase: 'idle',
    sendError: '', notice: '', loaded: false, needsRefresh: false })
}

/**
 * One in-memory conversation per account. Owners share IO; the last release
 * forgets history and aborts client reads, without asserting a server rollback.
 * The account adapter must call reset() before another account can acquire it.
 */
export function createCompanionChatSession({ readHistory, stream, onChange }) {
  let state = emptyCompanionChatState()
  const owners = new Set()
  let generation = 0
  let keySeed = 0
  let historyRequest = null
  let sendRequest = null

  function publish(patch) {
    state = snapshot({ ...state, ...patch })
    onChange(state)
  }
  const withKey = (role, content) => ({ localKey: `chat-${++keySeed}`, role, content })
  const currentHistory = request => historyRequest === request && request.generation === generation && owners.size > 0
  const currentSend = request => sendRequest === request && request.generation === generation && owners.size > 0

  function invalidate() {
    generation += 1
    const history = historyRequest
    const sending = sendRequest
    historyRequest = null
    sendRequest = null
    // Invalidate callbacks before abort, which can synchronously notify adapters.
    history?.controller.abort()
    sending?.controller.abort()
  }

  function loadHistory(reconcile = false) {
    if (!owners.size || sendRequest || state.phase !== 'idle') return Promise.resolve(false)
    if (historyRequest) {
      historyRequest.reconcile ||= reconcile
      return historyRequest.promise
    }
    const request = { generation, controller: new AbortController(), reconcile, promise: null }
    historyRequest = request
    // Defer IO one microtask so a reentrant observer sees the shared promise.
    request.promise = Promise.resolve().then(async () => {
      if (!currentHistory(request)) return false
      try {
        const history = await readHistory({ signal: request.controller.signal })
        if (!currentHistory(request)) return false
        if (!Array.isArray(history?.records) || history.records.some(message =>
          !message || !['USER', 'COMPANION'].includes(message.role) || typeof message.content !== 'string')) {
          throw new Error(LOAD_ERROR)
        }
        historyRequest = null
        publish({ messages: history.records.map(message => withKey(message.role, message.content)),
          loaded: true, loading: false, loadError: '',
          ...(request.reconcile ? { needsRefresh: false, notice: '', sendError: '' } : {}) })
        return true
      } catch (error) {
        if (!currentHistory(request)) return false
        historyRequest = null
        publish({ loading: false, loadError: error?.message || LOAD_ERROR })
        return false
      }
    })
    publish({ loading: true, loadError: '' })
    return request.promise
  }

  function acquire() {
    const owner = Symbol('chat-owner')
    owners.add(owner)
    if (!state.loaded && !state.loading && !state.loadError) void loadHistory()
    return () => {
      if (!owners.delete(owner) || owners.size) return
      const uncertain = state.needsRefresh || Boolean(sendRequest && !sendRequest.done)
      const draft = state.draft
      invalidate()
      publish({ ...emptyCompanionChatState(), draft, needsRefresh: uncertain,
        notice: uncertain ? UNCERTAIN_NOTICE : '' })
    }
  }

  function setDraft(value) {
    if (!owners.size || typeof value !== 'string') return false
    publish({ draft: value })
    return true
  }

  async function send() {
    if (!owners.size || !state.loaded || state.loading || state.loadError ||
        state.phase !== 'idle' || sendRequest || state.needsRefresh) return false
    const content = state.draft.trim()
    if (!content) return false
    if ([...content].length > 500) {
      publish({ sendError: '消息需为 1–500 字。' })
      return false
    }
    const companion = withKey('COMPANION', '')
    const request = { generation, controller: new AbortController(), done: false, failed: false }
    sendRequest = request
    publish({ messages: [...state.messages, withKey('USER', content), companion], draft: '',
      phase: 'waiting', sendError: '', notice: '' })

    const withoutEmptyReply = () => state.messages.filter(message => message.localKey !== companion.localKey || message.content)
    function fail(error) {
      if (!currentSend(request) || request.done || request.failed) return
      request.failed = true
      publish({ messages: withoutEmptyReply(), phase: 'idle', sendError: error?.message || SEND_ERROR,
        needsRefresh: true, notice: UNCERTAIN_NOTICE })
    }
    try {
      if (!currentSend(request)) return false
      await stream(content, {
        signal: request.controller.signal,
        onChunk(chunk) {
          if (!currentSend(request) || request.done || request.failed || typeof chunk !== 'string' || !chunk) return
          publish({ phase: 'streaming', messages: state.messages.map(message => message.localKey === companion.localKey
            ? { ...message, content: message.content + chunk } : message) })
        },
        onError: fail,
        onDone() { if (currentSend(request) && !request.failed) request.done = true }
      })
      if (!currentSend(request)) return false
      if (request.failed) return false
      // The existing transport treats normal EOF as terminal too. Ending the
      // request is not an invented reply, mood, or persisted-success assertion.
      request.done = true
      sendRequest = null
      publish({ messages: withoutEmptyReply(), phase: 'idle' })
      return true
    } catch (error) {
      fail(error)
      return false
    } finally {
      if (currentSend(request)) {
        sendRequest = null
        if (state.phase !== 'idle') publish({ phase: 'idle' })
      }
    }
  }

  function reset() {
    owners.clear()
    invalidate()
    publish(emptyCompanionChatState())
  }

  onChange(state)
  return { acquire, setDraft, send, reload: () => loadHistory(true), reset }
}
