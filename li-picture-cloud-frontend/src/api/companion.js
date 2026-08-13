import request from './request'

export const getCompanionHome = () => request.get('/companion/me')
export const awakenCompanion = () => request.post('/companion/awaken')
export const feedCompanion = data => request.post('/companion/feed', data)

export const listCompanionMemories = (limit = 50) =>
  request.get('/companion/memories', { params: { limit } })
export const confirmCompanionMemory = id => request.post(`/companion/memories/${id}/confirm`)
export const correctCompanionMemory = (id, content) =>
  request.post(`/companion/memories/${id}/correct`, { content })
export const dismissCompanionMemory = id => request.post(`/companion/memories/${id}/dismiss`)
export const deleteCompanionMemory = id => request.delete(`/companion/memories/${id}`)

export const listCompanionChatHistory = (limit = 50) =>
  request.get('/companion/chat/history', { params: { limit } })
