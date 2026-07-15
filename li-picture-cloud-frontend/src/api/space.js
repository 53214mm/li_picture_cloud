import request from './request'

/** 创建空间 */
export function addSpace(data) {
  return request.post('/space/add', data)
}

/** 删除空间（仅本人或管理员） */
export function deleteSpace(id) {
  return request.post('/space/delete', { id })
}

/** 更新空间（管理员） */
export function updateSpace(data) {
  return request.post('/space/update', data)
}

/** 编辑空间名称（用户） */
export function editSpace(data) {
  return request.post('/space/edit', data)
}

/** 根据 ID 获取空间（管理员） */
export function getSpaceById(id) {
  return request.get('/space/get', { params: { id } })
}

/** 根据 ID 获取空间 VO */
export function getSpaceVOById(id) {
  return request.get('/space/get/vo', { params: { id } })
}

/** 分页获取空间列表（管理员） */
export function listSpaceByPage(data) {
  return request.post('/space/list/page', data)
}

/** 分页获取空间 VO 列表 */
export function listSpaceVOByPage(data) {
  return request.post('/space/list/page/vo', data)
}

/** 获取空间级别列表 */
export function listSpaceLevel() {
  return request.get('/space/list/level')
}
