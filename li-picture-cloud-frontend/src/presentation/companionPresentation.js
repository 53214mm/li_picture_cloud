import { resolveCompanionVisual } from '../components/companion/body/companionVisual.js'

// Presentation policy, not new domain rules. Keep numeric mood tie order aligned
// with CompanionViewAssembler.moodSummary; never interpret its localized text.
const moodAxes = ['energy', 'joy', 'loneliness', 'inspiration', 'irritation']
const moodAffects = ['energetic', 'cheerful', 'lonely', 'inspired', 'irritated']
const relationshipAxes = ['familiarity', 'trust', 'closeness', 'tacit', 'recentFeedback']

function numericAxis(value, min = 0) {
  if (typeof value !== 'number' && !(typeof value === 'string' && /^\d+(\.\d+)?$|^-\d+(\.\d+)?$/.test(value))) return null
  const number = Number(value)
  return Number.isFinite(number) && number >= min && number <= 100 ? number : null
}

function affectFor(mood) {
  const values = moodAxes.map(axis => numericAxis(mood?.[axis]))
  if (values.includes(null)) return 'neutral'
  const maximum = Math.max(...values)
  return maximum < 5 ? 'neutral' : moodAffects[values.indexOf(maximum)]
}

function rapportFor(relationship) {
  const values = relationshipAxes.map(axis => numericAxis(relationship?.[axis], axis === 'recentFeedback' ? -100 : 0))
  if (values.includes(null)) return 'neutral'
  const [familiarity, trust, closeness] = values
  return trust >= 60 && closeness >= 60 ? 'close' : familiarity >= 20 ? 'familiar' : 'neutral'
}

function companionId(value) {
  if (typeof value === 'number') return Number.isSafeInteger(value) && value > 0 ? String(value) : null
  return typeof value === 'string' && /^[1-9]\d*$/.test(value) ? value : null
}

function record(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

/**
 * v1: one pure projection of observed domain snapshots + request lifecycles.
 * No IO, text parsing, timers, mutation, storage or speculative domain events.
 * See docs/R06-Companion-Presentation-State.md for precedence and lifetime.
 */
export function mapCompanionPresentation(input = {}) {
  input = record(input)
  const { home, homeStatus = 'unobserved' } = input
  const feed = record(input.feed)
  const chat = record(input.chat)
  const proposal = record(input.proposal)
  let availability = input.enabled === false ? 'disabled' : homeStatus
  const id = companionId(home?.companion?.id)
  if (availability === 'ready') {
    if (home?.companion === null) availability = 'absent'
    else if (!id) availability = 'error'
  }
  if (!['disabled', 'unobserved', 'loading', 'unavailable', 'error', 'absent', 'ready'].includes(availability)) availability = 'error'
  const ready = availability === 'ready'
  const lifeStage = ready && typeof home.companion.lifeStage === 'string' ? home.companion.lifeStage : null
  const fresh = ready && input.homeFresh !== false
  const visual = resolveCompanionVisual(lifeStage)
  const issues = []
  if (availability === 'error') issues.push('home-failed')
  if (ready) {
    if (!fresh) issues.push('home-stale')
    if (feed.error === true) issues.push('feed-failed')
    if (chat.error === true) issues.push('chat-failed')
    if (proposal.error === true) issues.push('proposal-failed')
  }
  const activity = !ready ? 'idle'
    : chat.phase === 'streaming' ? 'responding'
      : chat.phase === 'waiting' ? 'thinking'
        : feed.pending === true ? 'feeding'
          : proposal.busy === true ? 'acknowledging' : 'idle'
  return Object.freeze({
    version: 1,
    availability,
    companionId: ready ? id : null,
    lifeStage,
    freshness: ready ? fresh ? 'fresh' : 'stale' : 'unknown',
    activity,
    attention: ready && proposal.status === 'PENDING' && proposal.loading !== true && proposal.error !== true ? 'proposal' : 'none',
    affect: fresh ? affectFor(home.mood) : 'neutral',
    rapport: fresh ? rapportFor(home.relationship) : 'neutral',
    issues: Object.freeze(issues),
    appearance: Object.freeze({ assetKey: visual.assetKey, visualStage: visual.visualStage, allowIdle: ready && visual.allowIdle })
  })
}
