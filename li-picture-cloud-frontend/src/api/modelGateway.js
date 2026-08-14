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

export const listModelRouting = () => request.get('/model/routing')
export const upsertModelRouting = (task, connectionId) =>
  request.put(`/model/routing/${task}`, { connectionId })
export const deleteModelRouting = task => request.delete(`/model/routing/${task}`)

export const listModelUsage = (limit = 50) => request.get('/model/usage', { params: { limit } })
