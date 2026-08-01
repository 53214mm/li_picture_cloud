import request from './request'

export function getMyPicturePermissions(pictureId) {
  return request.post('/spaceUser/permissions', null, { params: { pictureId } })
}
