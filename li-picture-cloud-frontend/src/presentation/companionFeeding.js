import { beginFeedAttempt, shouldRetrySameFeedKey } from '../utils/companion.js'
import { normalizePictureId } from './companionInteraction.js'

export const FEED_RECOVERY_KEY = 'li-picture-cloud:companion-feed:v1'
export const emptyFeedingState = () => ({ phase: 'empty', pictureId: null, attempt: null, result: null, error: '', retried: false, recovered: false, recoveryAvailable: true })

// A request/selection session, not a second source of Companion business state.
// Only unresolved IDs and the idempotency key survive this page's lifetime.
export function createFeedingSession({ actor, companionId, storage, send, onChange, keyFactory }) {
  actor = normalizePictureId(actor)
  companionId = normalizePictureId(companionId)
  if (!actor || !companionId) throw new Error('A feeding session requires an authenticated companion')
  let active = true
  let state = emptyFeedingState()
  const publish = patch => { state = { ...state, ...patch }; onChange(state) }
  const locked = () => state.phase === 'submitting' || state.phase === 'uncertain'
  function save(attempt) {
    try {
      if (!storage) throw new Error('storage unavailable')
      if (attempt) storage.setItem(FEED_RECOVERY_KEY, JSON.stringify({ version: 1, actor, companionId, ...attempt }))
      else storage.removeItem(FEED_RECOVERY_KEY)
    } catch { state = { ...state, recoveryAvailable: false } }
  }
  let raw
  try {
    if (!storage) throw new Error('storage unavailable')
    raw = storage.getItem(FEED_RECOVERY_KEY)
  } catch { state = { ...state, recoveryAvailable: false } }
  try {
    if (raw) {
      const recovery = JSON.parse(raw)
      if (recovery?.version === 1 && recovery.actor === actor && recovery.companionId === companionId &&
          typeof recovery.pictureId === 'string' && normalizePictureId(recovery.pictureId) === recovery.pictureId &&
          typeof recovery.idempotencyKey === 'string' && /^[a-z0-9_-]{16,64}$/.test(recovery.idempotencyKey)) {
        state = { ...state, phase: 'uncertain', pictureId: recovery.pictureId, recovered: true,
          attempt: { pictureId: recovery.pictureId, idempotencyKey: recovery.idempotencyKey } }
      } else save(null)
    }
  } catch {
    // Malformed data must not become a request, and unavailable storage must not
    // prevent same-page retry. A failed remove will mark recovery unavailable.
    save(null)
  }
  onChange(state)
  return {
    select(id) {
      const pictureId = normalizePictureId(id)
      if (!active || locked() || !pictureId) return false
      publish({ ...emptyFeedingState(), recoveryAvailable: state.recoveryAvailable, phase: 'selected', pictureId })
      return true
    },
    clear() {
      if (!active || locked()) return false
      publish({ ...emptyFeedingState(), recoveryAvailable: state.recoveryAvailable })
      return true
    },
    async submit() {
      if (!active || !['selected', 'uncertain', 'rejected'].includes(state.phase)) return null
      const retried = Boolean(state.attempt)
      const attempt = beginFeedAttempt(state.pictureId, state.attempt, keyFactory)
      save(attempt)
      publish({ phase: 'submitting', attempt, error: '', retried })
      try {
        const result = await send(attempt)
        if (!active) return null
        save(null)
        publish({ phase: 'succeeded', attempt: null, result })
        return result
      } catch (error) {
        if (!active) return null
        const uncertain = shouldRetrySameFeedKey(error)
        if (!uncertain) save(null)
        publish({ phase: uncertain ? 'uncertain' : 'rejected', attempt: uncertain ? attempt : null,
          error: error?.status == null ? '响应不确定，请用同一请求重试这次喂养' : error.message || '喂养请求暂未完成' })
        return null
      }
    },
    destroy({ clearRecovery = false } = {}) {
      active = false
      if (clearRecovery) save(null)
    }
  }
}
