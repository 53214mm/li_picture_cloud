import request from './request'

export const createStory = data => request.post('/creation/story', data)
export const outlineStory = id => request.post(`/creation/story/${id}/outline`)
export const confirmStoryOutline = id => request.post(`/creation/story/${id}/confirm-outline`)
export const draftStory = id => request.post(`/creation/story/${id}/draft`)
export const saveStory = id => request.post(`/creation/story/${id}/save`)
export const listStories = (limit = 20) => request.get('/creation/story', { params: { limit } })

export const createEmoji = data => request.post('/creation/emoji', data)
export const generateEmoji = id => request.post(`/creation/emoji/${id}/generate`)
export const listEmojiCandidates = id => request.get(`/creation/emoji/${id}/candidates`)
export const selectEmojiCandidate = (id, index) =>
  request.post(`/creation/emoji/${id}/select`, { index })
export const saveEmoji = id => request.post(`/creation/emoji/${id}/save`)
export const listEmojiTasks = (limit = 20) =>
  request.get('/creation/emoji', { params: { limit } })
