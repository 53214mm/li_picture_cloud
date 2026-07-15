/**
 * 空间级别常量
 */
export const SPACE_LEVEL = {
  COMMON: 0,
  PROFESSIONAL: 1,
  FLAGSHIP: 2
}

/**
 * 空间级别元信息
 */
export const SPACE_LEVEL_MAP = {
  [SPACE_LEVEL.COMMON]: {
    text: '普通版',
    maxCount: 100,
    maxSize: 100 * 1024 * 1024       // 100 MB
  },
  [SPACE_LEVEL.PROFESSIONAL]: {
    text: '专业版',
    maxCount: 1000,
    maxSize: 1000 * 1024 * 1024      // ~1 GB
  },
  [SPACE_LEVEL.FLAGSHIP]: {
    text: '旗舰版',
    maxCount: 10000,
    maxSize: 10000 * 1024 * 1024     // ~10 GB
  }
}

/** 根据 level 值获取中文名称 */
export function spaceLevelText(level) {
  return SPACE_LEVEL_MAP[level]?.text || '未知'
}

/** 格式化字节为可读大小 */
export function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

/** 格式化日期 */
export function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '-'
}
