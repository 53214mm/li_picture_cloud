import request from './request'

export const createStory = data => request.post('/creation/story', data)
export const outlineStory = id => request.post(`/creation/story/${id}/outline`)
export const confirmStoryOutline = id => request.post(`/creation/story/${id}/confirm-outline`)
export const draftStory = id => request.post(`/creation/story/${id}/draft`)
export const saveStory = id => request.post(`/creation/story/${id}/save`)
export const listStories = (limit = 20) => request.get('/creation/story', { params: { limit } })
