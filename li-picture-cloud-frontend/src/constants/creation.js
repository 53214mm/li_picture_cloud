export const CREATION_STATUS = {
  PENDING: '待开始',
  OUTLINING: '正在写大纲',
  DRAFTING: '正在写草稿',
  AWAITING_CONFIRM: '等待确认',
  SAVING: '正在保存',
  SAVED: '已保存',
  FAILED: '生成失败',
  EXPIRED: '已过期'
}

export function creationStatusLabel(status) {
  return CREATION_STATUS[status] || status || '未知状态'
}
