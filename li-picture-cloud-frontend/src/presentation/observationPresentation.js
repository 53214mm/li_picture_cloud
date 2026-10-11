// These are operation notices, never invented companion speech.
const failures = {
  EMPTY_OUTPUT: '模型没有返回可用正文（EMPTY_OUTPUT），本次失败；这不是模型选择沉默。',
  PREPARE_FAILED: '本次准备失败，尚未发送模型；本轮已停止，可在观察记录查看阶段。',
  MODEL_FAILED: '模型请求失败，未取得有效发言。', OBSERVATION_FAILED: '观察请求未完成，未取得有效发言。',
  INVALID_OUTPUT: '模型正文格式无效，未展示为发言。', OUTPUT_LIMIT: '模型正文超过限制，未展示为发言。',
  VISION_FAILED: '视觉请求失败，未取得有效发言。', CAPTURE_PIXELS_UNCONFIRMED: '观察区域像素未确认，本次未派发模型。'
}
export function observationActivity(state) {
  if (!state) return '正在读取观察状态，尚未派发模型。'
  if (state.enabled && state.page === 'UNKNOWN') return '本页暂不观察，原授权和余额保留；返回支持页面后按原间隔继续，不立即补发。'
  if (state.enabled && state.held) return '菜单、遮挡或页面未就绪时暂不观察；原授权保留，关闭后按原间隔继续。'
  if (state.phase === 'draining') return '正在结束旧素材的本次请求；旧正文不展示，本轮授权保留，不并发补发。'
  if (state.phase === 'opening') return '正在核对接收方并开启授权，尚未派发模型。'
  if (state.phase === 'preparing') return '正在准备本次观察素材，尚未派发文本模型。'
  if (state.phase === 'observing') return '本次观察请求中，尚未取得新的有效正文。'
  const last = state.records?.[0]?.response
  const reason = state.reason || last?.reason
  if (state.enabled && ['MATERIAL_CHANGED', 'CONTEXT_CHANGED', 'REVOKED', 'CHAT_BUSY', 'CHAT_CHANGED', 'OWNER_BUSY_OR_INTERVAL', 'IN_FLIGHT', 'SNAPSHOT_EXPIRED_OR_REPLAYED'].includes(reason)) return '本次素材或上下文已变化，旧结果已跳过并记录；原授权保留，按原间隔继续。'
  if (last?.stage === 'capture' && state.enabled) return '本次视觉区域未完成确认，已跳过本次请求，未发送模型；原授权保留，不立即重试。'
  let lastDecision = ''
  if (last?.modelDispatched === false) lastDecision = '上次观察未派发文本模型，没有新的模型发言。'
  if (last && !last.reason && last.modelDispatched !== false) {
    if (last.parsed?.action === 'SILENT') lastDecision = '上次模型选择沉默（SILENT），没有发言；这不是失败。'
    if (last.parsed?.action === 'SPEAK' && typeof last.parsed.text === 'string' && last.parsed.text.trim()) lastDecision = '上次模型已返回有效发言，可在近期发言中阅读。'
    if (last.parsed?.action === 'EMOTE') lastDecision = '上次模型选择表情（EMOTE），没有文字发言。'
  }
  if (state.suppressed) return '聊天、窗口隐藏或面板占用时观察暂停；不会接收迟到发言。' + lastDecision
  if (reason === 'PAUSED' || reason === 'CHAT_HIDDEN_OR_BLOCKED') return '本轮已暂停，没有新的请求；近期发言仍可回看。' + lastDecision
  if (reason === 'BUDGET_EXHAUSTED' || reason === 'VISION_BUDGET_EXHAUSTED') return '本轮次数已用完，需要重新明确授权；不会自动续跑。' + lastDecision
  if (reason === 'SESSION_CLOSED_OR_EXPIRED') return '本轮授权已关闭或过期，需要重新明确授权；近期发言仍可回看。'
  if (failures[reason]) return failures[reason] + ' 不自动重试。'
  if (reason === 'STATUS_UNAVAILABLE') return '接收方状态读取失败；请刷新接收方与额度，不会调用模型。'
  if (['SESSION_EXPIRED', 'GRANT_EXPIRED', 'EXPIRED'].includes(reason)) return '本轮授权已过期，需要重新明确授权；旧发言可回看。'
  if (last && !last.reason && last.modelDispatched !== false && last.parsed?.action === 'SILENT') return '模型选择沉默（SILENT），本次没有发言；这不是失败。'
  if (last?.modelDispatched === false) return '本次未派发文本模型，没有新的模型发言。'
  if (last && !last.reason && last.parsed?.action === 'SPEAK' && typeof last.parsed.text === 'string' && last.parsed.text.trim()) return '模型已返回有效发言，可在气泡和近期发言中阅读。' + (state.enabled ? '' : ' 本轮已结束。')
  if (last && !last.reason && last.parsed?.action === 'EMOTE') return '模型选择表情（EMOTE），本次没有文字发言。'
  if (state.enabled) return '本轮已开启，等待下一次观察；尚无新的有效发言。'
  if (reason && !['OFF', 'CLEARED'].includes(reason)) return '本轮已停止，未取得新的有效发言；请核对接收方和当前页面后重新授权。'
  return '独立观察默认关闭，尚未派发模型。'
}
export function observationStartBlock(state) {
  if (state?.enabled) return '本轮已开启；如需更改设置，请先暂停并结束本轮。'
  if (state?.phase === 'opening') return '正在开启本轮，请等待；不会重复提交。'
  if (state?.suppressed) return '当前普通聊天或其他面板占用中，请结束聊天并关闭面板后再开启。'
  if (state?.held) return '当前菜单或页面遮挡中，关闭后再明确开启。'
  if (state?.page === 'UNKNOWN') return '当前页面不在观察白名单（模型设置等敏感页面不采集）；请切换到图库或伙伴页再开启。'
  if (state?.statusPhase === 'error') return '接收方状态读取失败，请先刷新接收方与额度。'
  if (state?.statusPhase === 'loading') return '正在读取接收方与额度；读取完成后才能明确授权。'
  if (!state?.status) return '尚未读取接收方与额度，请先点击刷新（不调用模型）。'
  if (!state.status.enabled) return '服务器尚未开放独立观察。'
  if (!state.status.textReceiver) return '当前没有文本 BYOK 接收方，请前往模型设置配置语言代理。'
  return ''
}
export function observationVisualBlocks(state) {
  const reasons = []
  if (state?.enabled || state?.phase === 'opening') reasons.push('本轮已开启，视觉设置已锁定；请先暂停并结束本轮，再选择视觉并重新授权。')
  if (!state?.status || state.statusPhase === 'loading') reasons.push('正在读取视觉接收方；读取完成前不能勾选。')
  else {
    if (!state.status.realVisualReady) reasons.push('服务器的真实视觉区域能力尚不可用。')
    if (!state.status.visionReceiver) reasons.push('当前没有视觉 BYOK 接收方；文本聊天连接不会自动提供视觉能力，请前往模型设置配置视觉代理，再刷新接收方。')
  }
  return reasons
}
