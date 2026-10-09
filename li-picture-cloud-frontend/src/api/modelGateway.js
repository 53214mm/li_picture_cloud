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
export const useQuickPlatform = () => request.delete('/model/quick/current')

export const listModelUsage = (limit = 50) => request.get('/model/usage', { params: { limit } })

export const listMcpServices = () => request.get('/model/mcp/services')
export const upsertMcpService = data => request.post('/model/mcp/services', data)
export const enableMcpService = code => request.post(`/model/mcp/services/${code}/enable`)
export const disableMcpService = code => request.post(`/model/mcp/services/${code}/disable`)
export const listMcpTools = code => request.get(`/model/mcp/services/${code}/tools`)
export const addMcpTool = (code, toolName) =>
  request.post(`/model/mcp/services/${code}/tools`, { toolName })
export const enableMcpTool = (code, toolName) =>
  request.post(`/model/mcp/services/${code}/tools/${toolName}/enable`)
export const disableMcpTool = (code, toolName) =>
  request.post(`/model/mcp/services/${code}/tools/${toolName}/disable`)
export const removeMcpTool = (code, toolName) =>
  request.delete(`/model/mcp/services/${code}/tools/${toolName}`)
