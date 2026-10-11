// Rehearsal events contain only curated enums, never DOM text, URLs or form values.
export const presenceScenarios = Object.freeze([
  { kind: 'ARRIVAL', label: '进入伙伴页', eventType: 'PAGE_ENTER', page: 'COMPANION' },
  { kind: 'PICTURE_VIEW', label: '打开图片详情', eventType: 'PICTURE_OPENED', page: 'PICTURE_DETAIL' },
  { kind: 'UPLOAD_SUCCESS', label: '上传完成', eventType: 'UPLOAD_SUCCEEDED', page: 'UPLOAD' },
  { kind: 'GALLERY', label: '进入图库', eventType: 'PAGE_ENTER', page: 'GALLERY' }
].map(Object.freeze))

export function sanitizePresenceEvent(input, now = Date.now()) {
  const scenario = presenceScenarios.find(item => item.kind === input?.kind)
  if (!scenario || typeof input.id !== 'string' || !/^[a-zA-Z0-9_-]{1,64}$/.test(input.id)) return null
  if (!Number.isFinite(input.occurredAt) || input.occurredAt > now || now - input.occurredAt > 30000) return null
  return Object.freeze({ id: input.id, kind: scenario.kind, eventType: scenario.eventType, page: scenario.page, occurredAt: input.occurredAt })
}

export function simulatePresence(event, { intensity = 'LOW' } = {}) {
  const responses = {
    ARRIVAL: intensity === 'LOW' ? { action: 'SILENT', text: '' } : { action: 'EMOTE', text: '挥挥手' },
    PICTURE_VIEW: { action: 'EMOTE', text: '点点头（没有读取图片内容）' },
    UPLOAD_SUCCESS: { action: 'SPEAK', text: '上传完成！这一小步，值得我在旁边比个耶。' },
    GALLERY: { action: 'SILENT', text: '' }
  }
  return { ...responses[event.kind], source: 'DETERMINISTIC_SIMULATION' }
}

export const failureReasons = Object.freeze({
  UNAVAILABLE: '服务暂不可用，尚无法确认失败类别；未自动重试',
  MODEL_CREDENTIAL_REJECTED: '上游拒绝了模型凭据，请在模型设置中检查连接',
  MODEL_REQUEST_REJECTED: '上游拒绝了本次请求，请检查模型与接口是否兼容',
  MODEL_UPSTREAM_ERROR: '模型上游服务发生错误；未自动重试',
  MODEL_NETWORK_ERROR: '模型网络连接失败；未自动重试',
  MODEL_TIMEOUT: '等待模型响应超时；未自动重试',
  MODEL_STREAM_INVALID: '模型流未完整结束或协议不合规；未自动重试',
  MODEL_BUDGET: '模型上游预算不足，请检查提供方账户',
  MODEL_RATE_LIMITED: '模型上游限制了请求频率；未自动重试',
  EMPTY_OUTPUT: '模型没有返回可用正文，未取得主动决策',
  INVALID_DECISION: '模型正文不符合主动决策 JSON 格式，已安全拒绝',
  OUTPUT_LIMIT: '模型正文超过主动决策长度上限，已安全拒绝',
  DAILY_BUDGET: '今日伙伴主动判断次数已用完，本次未调用模型',
  QUOTA_UNAVAILABLE: '无法确认主动判断预算，本次未调用模型',
  ROUTE_UNAVAILABLE: '当前语言代理连接无法用于本次试演，请检查模型设置',
  STATE_UNAVAILABLE: '无法读取试演所需状态，本次试演未完成',
  ACCOUNTING_FAILED: '本次用量记录未完成；模型可能已经调用，请勿连续重试',
  ADAPTER_UNSUPPORTED: '当前调用适配器不支持有界试演，本次未调用模型'
})

export function describePresenceReason(reason) {
  return failureReasons[reason] || ({ DEMO: '固定演示规则，不是模型生成', DECIDED: '真实模型已完成本次决策',
    BYOK_REQUIRED: '当前语言代理 BYOK 连接不可用，请检查模型设置', COOLDOWN: '服务端冷却中，未生成新回应',
    SESSION_BUDGET: '服务端本次预算已用完', SESSION_EXPIRED: '服务端会话已到期，请关闭后重新开启',
    STALE_EVENT: '事件已过期', DUPLICATE_EVENT: '重复事件已忽略', USER_MESSAGE_PRIORITY: '优先等待用户聊天',
    MUTED: '已静音', BUSY: '正在处理前一个事件', SUPERSEDED: '已被新事件替代', EVENT_LIMIT: '事件上限已到'
  })[reason] || '服务端未给出可显示的原因'
}

export function presentPresenceResult(result) {
  if (!result) return { label: '', sourceLabel: '', text: '', canReply: false }
  const real = result.source === 'SERVER_BYOK'
  const sourceLabel = real ? '真实模型 · BYOK' : result.source === 'SERVER_DEMO' ? '服务端演示' : '本地固定规则'
  if (result.presentationSuppression === 'EXPIRED') return { label: '回应过期，未展示', sourceLabel,
    text: '已保留服务端响应与派发状态；回应过期，未展示气泡。', canReply: false }
  if (Object.hasOwn(failureReasons, result.reason)) return { label: '未完成',
    sourceLabel: real ? '真实试演 · BYOK' : sourceLabel,
    text: '本次试演未取得可用决策，不能据此判断绫页是否想回应。', canReply: false }
  if (real && result.reason !== 'DECIDED') return { label: '未生成', sourceLabel: '真实试演 · BYOK',
    text: '本次没有取得新的模型决策。', canReply: false }
  if (result.muted) return { label: '已隐藏文字气泡', sourceLabel, text: '模型原决策 SPEAK，文字气泡被本页设置隐藏。', canReply: false }
  return { label: result.action, sourceLabel, text: result.text || '这会儿先不插话。', canReply: result.action === 'SPEAK' }
}

export function createPresenceSession({ decide = simulatePresence, now = Date.now, onChange = () => {}, cooldownMs = 5000, manualDebug = false } = {}) {
  let state; let generation = 0; let controller; let lastRun = -Infinity
  const seen = new Set()
  function publish(patch) { state = Object.freeze({ ...state, ...patch }); onChange(state) }
  function cancel(patch = {}) { generation++; controller?.abort(); controller = null; publish({ busy: false, result: null, ...patch }) }
  function reset() {
    seen.clear(); lastRun = -Infinity
    cancel({ enabled: false, paused: false, muted: false, visible: true, userBusy: false, limit: 12, used: 0, input: null, reason: '', mode: 'SIMULATION', intensity: 'LOW', cooldownSeconds: 5, nextAllowedAt: 0, rejection: '' })
  }
  reset()
  function configure(patch) {
    const safe = {}
    for (const key of ['enabled', 'paused', 'muted', 'visible', 'userBusy']) if (typeof patch[key] === 'boolean') safe[key] = patch[key]
    if (Number.isInteger(patch.limit) && patch.limit >= 1 && patch.limit <= 12) safe.limit = patch.limit
    if (Number.isInteger(patch.cooldownSeconds) && patch.cooldownSeconds >= 2 && patch.cooldownSeconds <= 30) safe.cooldownSeconds = patch.cooldownSeconds
    if (['LOW', 'NORMAL'].includes(patch.intensity)) safe.intensity = patch.intensity
    // Transport mode is selected outside this local safety/budget controller.
    // Arbitrary settings must never select a paid adapter.
    cancel({ ...safe, reason: '', ...(safe.enabled === false || safe.visible === false ? { input: null } : {}) })
  }
  async function run(raw) {
    if (!state.enabled || state.paused || !state.visible || state.userBusy || state.busy) { publish({ rejection: 'NOT_REQUESTED', reason: '未请求：试演未开启、暂停、页面不可见或用户正在聊天。' }); return false }
    if (state.used >= state.limit) { publish({ result: null, reason: '本次试演次数已用完，请调整上限或关闭试演。' }); return false }
    const input = sanitizePresenceEvent(raw, now())
    if (!input) { publish({ result: null, reason: '事件无效或已过期，未处理。' }); return false }
    if (seen.has(input.id)) { publish({ result: null, reason: '重复事件已忽略。' }); return false }
    if (now() < state.nextAllowedAt || now() - lastRun < (manualDebug ? state.cooldownSeconds * 1000 : cooldownMs)) { publish({ result: null, reason: '稍等一下，冷却期间不连续打扰。' }); return false }
    seen.add(input.id); lastRun = now()
    controller = new AbortController()
    const signal = controller.signal; const cycle = ++generation
    publish({ input, busy: true, result: null, reason: '', rejection: '', used: state.used + 1, nextAllowedAt: now() + (manualDebug ? state.cooldownSeconds * 1000 : cooldownMs) })
    try {
      const result = await decide(input, { signal, intensity: state.intensity, cooldownSeconds: state.cooldownSeconds })
      if (cycle !== generation || signal.aborted) return false
      const expired = now() - input.occurredAt > 30000
      if (expired && !manualDebug) { publish({ reason: '回应已过期，未展示。' }); return false }
      if (!['SILENT', 'EMOTE', 'SPEAK'].includes(result?.action)) throw new Error('invalid result')
      publish({ result: Object.freeze({ action: expired || (state.muted && result.action === 'SPEAK') ? 'SILENT' : result.action,
        trace: manualDebug && result.source === 'SERVER_BYOK' ? result.trace : null, emote: expired ? null : result.emote, text: expired || (state.muted && result.action === 'SPEAK') ? '' : String(result.text || '').slice(0, 180), source: result.source, reason: result.reason, originalAction: result.action, presentationSuppression: expired ? 'EXPIRED' : null, muted: state.muted && result.action === 'SPEAK', remainingDecisions: result.remainingDecisions, expiresAt: result.expiresAt, stage: result.stage, modelDispatched: result.modelDispatched, attemptConsumed: result.attemptConsumed, elapsedMs: result.elapsedMs, outputChars: result.outputChars, dailyRemaining: result.dailyRemaining, dailyLimit: result.dailyLimit }), reason: expired ? '回应已过期，未展示；已保留实际派发与响应详情。' : state.muted && result.action === 'SPEAK' ? '隐藏文字气泡已开启：只隐藏 SPEAK 展示，不停止调用或费用。' : '' })
      const serverNext = Date.parse(result.nextAllowedAt)
      publish({ used: result.attemptConsumed === false ? Math.max(0, state.used - 1) : state.used, nextAllowedAt: Number.isFinite(serverNext) ? serverNext : state.nextAllowedAt })
      return true
    } catch {
      if (cycle === generation && !signal.aborted) publish({ reason: '这次试演未完成，可以稍后重试。' })
      return false
    } finally {
      if (cycle === generation) { controller = null; publish({ busy: false }) }
    }
  }
  return { configure, run, reset, cancel, get state() { return state } }
}
