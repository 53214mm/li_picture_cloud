import request from './request'

export function addSpaceUser(data) {
  return request.post('/spaceUser/add', data)
}

export function deleteSpaceUser(id) {
  return request.post('/spaceUser/delete', { id })
}

export function editSpaceUser(data) {
  return request.post('/spaceUser/edit', data)
}

export function listSpaceUsers(spaceId) {
  return request.post('/spaceUser/list', { spaceId })
}

export function listMyTeamSpaces() {
  return request.post('/spaceUser/list/my')
}

export function getMySpacePermissions(spaceId) {
  return request.post('/spaceUser/permissions', null, { params: { spaceId } })
}

export function getMyPicturePermissions(pictureId) {
  return request.post('/spaceUser/permissions', null, { params: { pictureId } })
}
