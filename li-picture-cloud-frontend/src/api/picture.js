import request from './request'

// ==================== 公开接口 ====================

/** 根据 ID 获取图片 VO（含关联用户信息） */
export function getPictureVOById(id) {
  return request.get('/picture/get/vo', { params: { id } })
}

/** 分页获取图片 VO 列表（公开，最多 20 条/页，走缓存） */
export function listPictureVOByPage(data) {
  return request.post('/picture/list/page/vo/cache', data)
}

/** 获取预设标签与分类 */
export function getPictureTagCategory() {
  return request.get('/picture/tag_category')
}

// ==================== 登录用户接口 ====================

/** 编辑图片（仅本人或管理员） */
export function editPicture(data) {
  return request.post('/picture/edit', data)
}

/** 删除图片（仅本人或管理员） */
export function deletePicture(id) {
  return request.post('/picture/delete', { id })
}

// ==================== 登录用户接口 ====================

/** 上传图片（所有登录用户均可上传，非管理员需审核） */
export function uploadPicture(formData) {
  return request.post('/picture/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== 管理员接口 ====================

/** 批量抓取图片（管理员，从必应抓取，超时 5 分钟） */
export function uploadPictureByBatch(data) {
  return request.post('/picture/upload/batch', data, { timeout: 5 * 60 * 1000 })
}

/** 审核图片（管理员） */
export function reviewPicture(data) {
  return request.post('/picture/review', data)
}

/** 更新图片（管理员可修改任意图片） */
export function updatePicture(data) {
  return request.post('/picture/update', data)
}

/** 分页获取原始图片列表（管理员，可查看全部审核状态） */
export function listPictureByPage(data) {
  return request.post('/picture/list/page', data)
}

/** 获取原始图片实体（管理员） */
export function getPictureById(id) {
  return request.get('/picture/get', { params: { id } })
}

/** 以图搜图 — 获取 Bing 识图搜索 URL */
export function getImageSearchUrl(imageUrl) {
  return request.post('/picture/search/image', { fileUrl: imageUrl })
}

/** 批量编辑图片（分类/标签/命名） */
export function editPictureByBatch(data) {
  return request.post('/picture/edit/batch', data)
}
