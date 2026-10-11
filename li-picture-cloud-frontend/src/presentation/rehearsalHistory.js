import { failureReasons, presentPresenceResult } from './companionPresence.js'
import { snapshotRehearsalInput, snapshotRehearsalSettings, snapshotPresenceRequest, snapshotRehearsalTrace } from './rehearsalTrace.js'
const gates = new Set(['COOLDOWN', 'SESSION_BUDGET', 'SESSION_EXPIRED', 'DAILY_BUDGET', 'BYOK_REQUIRED', 'STALE_EVENT', 'DUPLICATE_EVENT', 'USER_MESSAGE_PRIORITY', 'MUTED', 'BUSY', 'SUPERSEDED', 'EVENT_LIMIT'])
export function classifyRehearsalResult(result) {
  if (!result) return 'NOT_REQUESTED'
  if (gates.has(result.reason)) return 'GATED'
  if (Object.hasOwn(failureReasons, result.reason)) return 'FAILED'
  if (result.source === 'SERVER_BYOK') return result.reason === 'DECIDED' ? 'DECIDED' : 'FAILED'
  return 'SIMULATED'
}
export function createRehearsalHistory({ onChange = () => {} } = {}) {
  let owner = null; let rows = []; let serial = 0; let cancel = () => {}
  const publish = next => { rows = Object.freeze(next.map(Object.freeze)); onChange(rows) }
  const update = (id, patch) => publish(rows.map(row => row.id === id && row.category === 'PENDING' ? { ...row, ...patch } : row))
  function leave(revoke = true) {
    cancel(revoke)
    publish(rows.map(row => row.category === 'PENDING' ? { ...row, category: 'CANCELLED', stage: 'CANCELLED', modelDispatched: null, notice: '已取消接收，服务端结果未确认；返回不会续跑。' } : row))
  }
  function clear() { leave(); publish([]) }
  return {
    get records() { return rows },
    bindCancel(callback) { cancel = callback; return () => { if (cancel === callback) cancel = () => {} } },
    ownerChanged(next) { const key = next == null ? null : String(next); if (key !== owner) { clear(); owner = key } },
    start({ kind, mode, at, input, settings }) {
      if (!owner || !['ARRIVAL', 'PICTURE_VIEW', 'UPLOAD_SUCCESS', 'GALLERY'].includes(kind) || !['LOCAL', 'SERVER', 'BYOK'].includes(mode)) return null
      const id = ++serial
      publish([...rows, { id, kind, mode, at, inputSnapshot: snapshotRehearsalInput(input), settingsSnapshot: snapshotRehearsalSettings(settings), protocolSnapshot: null, trace: null, category: 'PENDING', stage: 'PREPARING', modelDispatched: null }].slice(-50))
      return id
    },
    phase(id, stage, request) { if (['OPENING', 'SUBMITTED'].includes(stage)) update(id, { stage, ...(stage === 'SUBMITTED' ? { protocolSnapshot: snapshotPresenceRequest(request) } : {}) }) },
    finish(id, result, notice = '') {
      const category = classifyRehearsalResult(result)
      const row = rows.find(item => item.id === id && item.category === 'PENDING')
      if (!row) return
      const trace = row.protocolSnapshot && row.mode === 'BYOK' && result?.source === 'SERVER_BYOK' ? snapshotRehearsalTrace(result.trace, row.protocolSnapshot) : null
      const presentation = presentPresenceResult(result)
      update(id, { category, trace, presentationSnapshot: Object.freeze({ action: result?.action || null, label: presentation.label, text: presentation.text, reason: String(notice).slice(0, 180), hiddenText: result?.muted === true, suppression: result?.presentationSuppression === 'EXPIRED' ? 'EXPIRED' : null }), action: result?.originalAction || result?.action, muted: result?.muted === true,
        text: String(result?.text || '').slice(0, 180), source: result?.source, reason: result?.reason,
        stage: ['GATE', 'STATE', 'QUOTA', 'ROUTE', 'DISPATCH', 'COLLECT', 'PARSE', 'USAGE', 'DONE'].includes(result?.stage) ? result.stage : category,
        modelDispatched: category === 'NOT_REQUESTED' ? false : typeof result?.modelDispatched === 'boolean' ? result.modelDispatched : null,
        elapsedMs: result?.elapsedMs, outputChars: result?.outputChars, remainingDecisions: result?.remainingDecisions,
        dailyRemaining: result?.dailyRemaining, dailyLimit: result?.dailyLimit, expiresAt: result?.expiresAt,
        notice: String(notice).slice(0, 180) })
    },
    leave, clear
  }
}
