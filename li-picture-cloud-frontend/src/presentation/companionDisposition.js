import { MOOD_AXES, RELATIONSHIP_AXES, TRAIT_AXES } from '../constants/companion.js'

// Display policy only. A missing/malformed snapshot is never a zero-valued one.
export function numericCompanionAxis(value, min = 0) {
  if (typeof value !== 'number' && !(typeof value === 'string' && /^-?\d+(\.\d+)?$/.test(value))) return null
  const number = Number(value)
  return Number.isFinite(number) && number >= min && number <= 100 ? number : null
}

const moodCopy = Object.freeze({
  neutral: ['平静相伴', '此刻没有明显的情绪波动。'],
  energetic: ['有些活跃', '精力是此刻更明显的情绪。'],
  cheerful: ['心情明朗', '愉悦是此刻更明显的情绪。'],
  lonely: ['有些孤单', '孤独是此刻更明显的情绪。要不要互动，由你决定。'],
  inspired: ['有了些灵感', '灵感是此刻更明显的情绪。'],
  irritated: ['有些烦躁', '烦躁是此刻更明显的情绪。']
})
const rapportCopy = Object.freeze({
  neutral: ['相处之中', '每次互动都会留下相处的记录。'],
  familiar: ['渐渐熟悉', '已经积累了一些熟悉感。'],
  close: ['相处渐近', '信任与亲近都在相处中积累。']
})
const emptyCopy = Object.freeze({
  missing: ['暂无记录', '还没有可展示的记录。'],
  invalid: ['状态待更新', '这次状态不完整，暂不展示数值。'],
  stale: ['等待状态更新', '新的状态尚未确认。'],
  unavailable: ['状态暂不可用', '读取伙伴状态后再来看看。']
})

function snapshot(raw, axes, available, fresh, signed = false) {
  const status = !available ? 'unavailable' : !fresh ? 'stale'
    : raw == null ? 'missing' : typeof raw !== 'object' || Array.isArray(raw) ? 'invalid' : 'known'
  if (status !== 'known') return { status, axes: [] }
  const values = axes.map(axis => numericCompanionAxis(raw[axis.key], signed || axis.key === 'recentFeedback' ? -100 : 0))
  if (values.includes(null)) return { status: 'invalid', axes: [] }
  return { status, axes: axes.map((axis, index) => ({ ...axis, value: values[index],
    position: signed || axis.key === 'recentFeedback' ? (values[index] + 100) / 2 : values[index] })) }
}

function seal(view, copy, extra = {}) {
  const [label, description] = view.status === 'known' ? copy : emptyCopy[view.status]
  return Object.freeze({ ...view, label, description, ...extra,
    axes: Object.freeze(view.axes.map(axis => Object.freeze(axis))) })
}

export function mapCompanionDisposition(home, { availability, freshness, affect, rapport }) {
  const available = availability === 'ready'
  const fresh = freshness === 'fresh'
  const mood = snapshot(home?.mood, MOOD_AXES, available, fresh)
  const relationship = snapshot(home?.relationship, RELATIONSHIP_AXES, available, fresh)
  const traits = snapshot(home?.companion?.traits, TRAIT_AXES, available, fresh, true)
  for (const axis of traits.axes) {
    const strength = Math.abs(axis.value)
    axis.label = strength < 10 ? '保持中性' : `${strength >= 60 ? '明显' : '略'}偏${axis.value > 0 ? axis.positive : axis.negative}`
  }
  const prominent = [...traits.axes].filter(axis => Math.abs(axis.value) >= 10)
    .sort((left, right) => Math.abs(right.value) - Math.abs(left.value)).slice(0, 2)
  // A high, complete and current mood permits a small idle variation. Zero
  // energy is neutral in the domain, never tiredness, sleep or a request to act.
  const idleTone = mood.status === 'known' && Math.max(...mood.axes.map(axis => axis.value)) >= 60
    && Object.hasOwn(moodCopy, affect) ? affect : 'neutral'
  return Object.freeze({
    mood: seal(mood, moodCopy[affect] || moodCopy.neutral, { idleTone }),
    relationship: seal(relationship, rapportCopy[rapport] || rapportCopy.neutral),
    traits: seal(traits, [prominent.length ? prominent.map(axis => axis.label).join(' · ') : '倾向平衡',
      '长期倾向来自成长积累，没有高低之分。'])
  })
}
