import request from './request'
export const presenceApi = {
  open: (data, options = {}) => request.post('/companion/presence/session', data, options),
  decide: (data, options = {}) => request.post('/companion/presence/decide', data, options),
  close: sessionId => request.delete(`/companion/presence/session/${encodeURIComponent(sessionId)}`)
}
