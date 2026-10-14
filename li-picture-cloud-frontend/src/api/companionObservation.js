import request from './request'

export const observationApi = {
  status: (options = {}) => request.get('/companion/observation/status', options),
  open: (data, options = {}) => request.post('/companion/observation/session', data, options),
  prepare: (data, options = {}) => request.post('/companion/observation/prepare', data, options),
  preview: (data, options = {}) => request.post('/companion/observation/preview', data, options),
  observe: (data, options = {}) => request.post('/companion/observation/observe', data, { timeout: 30000, ...options }),
  close: id => request.delete(`/companion/observation/session/${encodeURIComponent(id)}`),
  control: (id, data) => request.post(`/companion/observation/session/${encodeURIComponent(id)}/control`, data)
}

/** 管理员分页查看伙伴喂养运行。 */
export function listCompanionFeedRuns(data) {
  return request.post('/admin/companion/feed-runs/page', data)
}

/** 管理员查看单次伙伴喂养详情。 */
export function getCompanionFeedRun(runId) {
  return request.get(`/admin/companion/feed-runs/${runId}`)
}
