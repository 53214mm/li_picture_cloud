import request from './request'

export const createModelCredential = data => request.post('/model/credentials', data)
export const listModelCredentials = () => request.get('/model/credentials')
export const deleteModelCredential = id => request.delete(`/model/credentials/${id}`)

export const createModelConnection = data => request.post('/model/connections', data)
export const listModelConnections = () => request.get('/model/connections')
export const enableModelConnection = id => request.post(`/model/connections/${id}/enable`)
export const disableModelConnection = id => request.post(`/model/connections/${id}/disable`)
export const rotateModelCredential = (id, apiKey) =>
  request.post(`/model/connections/${id}/rotate-credential`, { apiKey })
export const deleteModelConnection = id => request.delete(`/model/connections/${id}`)
export const testModelConnection = id => request.post(`/model/connections/${id}/test`)
export const getModelConnectionCapability = id =>
  request.get(`/model/connections/${id}/capability`)

export const listModelRouting = () => request.get('/model/routing')
export const upsertModelRouting = (task, connectionId) =>
  request.put(`/model/routing/${task}`, { connectionId })
export const deleteModelRouting = task => request.delete(`/model/routing/${task}`)

// 快速连接：选厂商 → 填 Key 读取模型 → 选模型完成连接。Key 只随请求发送，不在前端保存。
export const listQuickProviders = () => request.get('/model/quick/providers')
export const discoverQuickModels = (provider, apiKey) =>
  request.post('/model/quick/models', { provider, apiKey })
export const connectQuickModel = (provider, apiKey, modelCode) =>
  request.post('/model/quick/connect', { provider, apiKey, modelCode })
export const getQuickConnection = () => request.get('/model/quick/current')
export const useSavedQuickConnection = id => request.post(`/model/quick/connections/${id}/use`)
export const useQuickPlatform = () => request.delete('/model/quick/current')
// 识图强制联网：只有百炼千问真正生效，supported 由后端按当前识图模型判断。
export const getVisionSearch = () => request.get('/model/quick/vision-search')
export const setVisionSearch = forcedSearch => request.put('/model/quick/vision-search', { forcedSearch })

export const listModelUsage = (limit = 50) => request.get('/model/usage', { params: { limit } })

