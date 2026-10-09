// Rehearsal events contain only curated enums, never DOM text, URLs or form values.
export const presenceScenarios = Object.freeze([
  { kind: 'ARRIVAL', label: '进入伙伴页', eventType: 'PAGE_ENTER', page: 'COMPANION' },
  { kind: 'PICTURE_VIEW', label: '打开图片详情', eventType: 'PICTURE_OPENED', page: 'PICTURE_DETAIL' },
  { kind: 'UPLOAD_SUCCESS', label: '上传完成', eventType: 'UPLOAD_SUCCEEDED', page: 'UPLOAD' },
  { kind: 'GALLERY', label: '进入图库', eventType: 'PAGE_ENTER', page: 'GALLERY' }
].map(Object.freeze))

export function sanitizePresenceEvent(input, now = Date.now()) {
  const scenario = presenceScenarios.find(item => item.kind === input?.kind)
  if (!scenario || typeof input.id !== 'string' || !/^[a-zA-Z0-9_-]{1,80}$/.test(input.id)) return null
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

export function createPresenceSession({ decide = simulatePresence, now = Date.now, onChange = () => {}, cooldownMs = 5000 } = {}) {
  let state; let generation = 0; let controller; let lastRun = -Infinity
  const seen = new Set()
  function publish(patch) { state = Object.freeze({ ...state, ...patch }); onChange(state) }
  function cancel(patch = {}) { generation++; controller?.abort(); controller = null; publish({ busy: false, result: null, ...patch }) }
  function reset() {
    seen.clear(); lastRun = -Infinity
    cancel({ enabled: false, paused: false, muted: false, visible: true, userBusy: false, limit: 12, used: 0, input: null, reason: '', mode: 'SIMULATION', intensity: 'LOW' })
  }
  reset()
  function configure(patch) {
    const safe = {}
    for (const key of ['enabled', 'paused', 'muted', 'visible', 'userBusy']) if (typeof patch[key] === 'boolean') safe[key] = patch[key]
    if (Number.isInteger(patch.limit) && patch.limit >= 1 && patch.limit <= 12) safe.limit = patch.limit
    if (['LOW', 'NORMAL'].includes(patch.intensity)) safe.intensity = patch.intensity
    // A mode change is deliberately not accepted until a verified real adapter exists.
    cancel({ ...safe, reason: '', ...(safe.enabled === false || safe.visible === false ? { input: null } : {}) })
  }
  async function run(raw) {
    if (!state.enabled || state.paused || !state.visible || state.userBusy || state.busy) return false
    if (state.used >= state.limit) { publish({ result: null, reason: '本次试演次数已用完，请调整上限或关闭试演。' }); return false }
    const input = sanitizePresenceEvent(raw, now())
    if (!input) { publish({ result: null, reason: '事件无效或已过期，未处理。' }); return false }
    if (seen.has(input.id)) { publish({ result: null, reason: '重复事件已忽略。' }); return false }
    if (now() - lastRun < cooldownMs) { publish({ result: null, reason: '稍等一下，冷却期间不连续打扰。' }); return false }
    seen.add(input.id); lastRun = now()
    controller = new AbortController()
    const signal = controller.signal; const cycle = ++generation
    publish({ input, busy: true, result: null, reason: '', used: state.used + 1 })
    try {
      const result = await decide(input, { signal, intensity: state.intensity })
      if (cycle !== generation || signal.aborted) return false
      if (now() - input.occurredAt > 30000) { publish({ reason: '回应已过期，未展示。' }); return false }
      if (!['SILENT', 'EMOTE', 'SPEAK'].includes(result?.action)) throw new Error('invalid result')
      publish({ result: Object.freeze({ action: state.muted && result.action === 'SPEAK' ? 'SILENT' : result.action,
        text: state.muted && result.action === 'SPEAK' ? '' : String(result.text || '').slice(0, 180), source: result.source, reason: result.reason, remainingDecisions: result.remainingDecisions, expiresAt: result.expiresAt }), reason: state.muted && result.action === 'SPEAK' ? '静音中，短句未展示。' : '' })
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
