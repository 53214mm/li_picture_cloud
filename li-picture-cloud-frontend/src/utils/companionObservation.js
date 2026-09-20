const STATUS_CLASS = {
  COMPLETED: 'is-success',
  FAILED: 'is-failed',
  REJECTED: 'is-failed',
  PROCESSING: 'is-processing'
}

const STAGE_CLASS = {
  SUCCESS: 'is-success',
  FAILED: 'is-failed',
  SKIPPED: 'is-skipped',
  PROCESSING: 'is-processing',
  DEGRADED: 'is-degraded',
  UNKNOWN: 'is-unknown'
}

const STAGE_STATUS_LABEL = {
  SUCCESS: '已完成',
  FAILED: '失败',
  SKIPPED: '未执行',
  PROCESSING: '处理中',
  DEGRADED: '已降级',
  UNKNOWN: '未知'
}

export function displayUser(row) {
  return row?.userName || row?.userAccount || `用户 #${row?.subjectId ?? '-'}`
}

export function displayPicture(row) {
  return row?.pictureName || `图片 #${row?.pictureId ?? '-'}`
}

export function displayActualNutrition(row) {
  return row?.actualNutritionLabel || '尚未产出'
}

export function formatObservationDuration(milliseconds) {
  if (milliseconds == null || Number.isNaN(Number(milliseconds)) || Number(milliseconds) < 0) return '-'
  const value = Number(milliseconds)
  if (value < 1000) return `${Math.round(value)} 毫秒`
  const seconds = Math.round(value / 100) / 10
  if (seconds < 60) return `${seconds.toFixed(1)} 秒`
  const minutes = Math.floor(seconds / 60)
  const remainder = Math.round(seconds - minutes * 60)
  return `${minutes} 分 ${remainder} 秒`
}

export function formatObservationDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}

export function statusClass(status) {
  return STATUS_CLASS[status] || 'is-unknown'
}

export function stageClass(status) {
  return STAGE_CLASS[status] || 'is-unknown'
}

export function stageStatusLabel(status) {
  return STAGE_STATUS_LABEL[status] || status || '未知'
}

export function growthAbsenceDescription(status) {
  if (status === 'PROCESSING') return '成长结算尚未完成。'
  if (status === 'REJECTED') return '授权未通过，本次没有进入成长结算。'
  if (status === 'FAILED') return '没有成长记录，说明结算没有生效。'
  return '当前没有成长记录，结算状态无法确认。'
}
