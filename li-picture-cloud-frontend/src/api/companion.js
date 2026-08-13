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

export const getCompanionContract = () => request.get('/companion/contract')
export const updateCompanionContract = data => request.put('/companion/contract', data)
export const getActiveCompanionProposal = () => request.get('/companion/proposals/active')
export const acceptCompanionProposal = id => request.post(`/companion/proposals/${id}/accept`)
export const ignoreCompanionProposal = id => request.post(`/companion/proposals/${id}/ignore`)
export const scoldCompanionProposal = id => request.post(`/companion/proposals/${id}/scold`)
