import request from './request'

/** 空间使用分析 */
export function getSpaceUsageAnalyze(data) {
  return request.post('/space/analyze/usage', data)
}

/** 图片分类分析 */
export function getSpaceCategoryAnalyze(data) {
  return request.post('/space/analyze/category', data)
}

/** 图片标签分析 */
export function getSpaceTagAnalyze(data) {
  return request.post('/space/analyze/tag', data)
}

/** 图片大小分析 */
export function getSpaceSizeAnalyze(data) {
  return request.post('/space/analyze/size', data)
}

/** 用户上传分析 */
export function getSpaceUserAnalyze(data) {
  return request.post('/space/analyze/user', data)
}

/** 空间排行 */
export function getSpaceRankAnalyze(data) {
  return request.post('/space/analyze/rank', data)
}
