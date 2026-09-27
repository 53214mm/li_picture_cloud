const activityLabels = Object.freeze({
  feeding: '正在处理这次喂养',
  thinking: '正在等待回复',
  responding: '正在接收回复',
  acknowledging: '正在处理这条提议'
})

// Format the existing protocol only. The room never infers a mood, event or clock.
export function describeHabitatActivity(presentation) {
  if (presentation?.version !== 1 || presentation.availability !== 'ready') return '等待伙伴状态'
  if (Object.hasOwn(activityLabels, presentation.activity)) return activityLabels[presentation.activity]
  if (presentation.activity !== 'idle') return '状态待更新'
  if (presentation.attention === 'proposal') return '有一条待回应的提议'
  return presentation.freshness === 'fresh' ? '此刻在小屋里' : '状态待更新'
}
