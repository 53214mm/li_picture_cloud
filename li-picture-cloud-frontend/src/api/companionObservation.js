import request from './request'

/** 管理员分页查看伙伴喂养运行。 */
export function listCompanionFeedRuns(data) {
  return request.post('/admin/companion/feed-runs/page', data)
}

/** 管理员查看单次伙伴喂养详情。 */
export function getCompanionFeedRun(runId) {
  return request.get(`/admin/companion/feed-runs/${runId}`)
}
