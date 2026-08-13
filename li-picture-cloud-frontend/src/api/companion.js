import request from './request'

export const getCompanionHome = () => request.get('/companion/me')
export const awakenCompanion = () => request.post('/companion/awaken')
export const feedCompanion = data => request.post('/companion/feed', data)
