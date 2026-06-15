import request from './request'

/** 用户登录 */
export function userLogin(data) {
  return request.post('/user/login', data)
}

/** 用户注册 */
export function userRegister(data) {
  return request.post('/user/register', data)
}

/** 用户注销 */
export function userLogout() {
  return request.post('/user/logout')
}

/** 获取当前登录用户 */
export function getCurrentUser() {
  return request.get('/user/current')
}

/** 根据 ID 获取脱敏用户 */
export function getUserVOById(id) {
  return request.get('/user/get/vo', { params: { id } })
}

// ==================== 管理员接口 ====================

/** 管理员创建用户 */
export function addUser(data) {
  return request.post('/user/add', data)
}

/** 管理员删除用户 */
export function deleteUser(id) {
  return request.post('/user/delete', { id })
}

/** 管理员更新用户 */
export function updateUser(data) {
  return request.post('/user/update', data)
}

/** 管理员分页获取用户列表 */
export function listUserByPage(data) {
  return request.post('/user/list/page', data)
}

/** 管理员根据 ID 获取完整用户 */
export function getUserById(id) {
  return request.get('/user/get', { params: { id } })
}
