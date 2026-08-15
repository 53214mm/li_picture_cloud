export const RECIPE_STATUS = {
  DRAFT: '草稿',
  ENABLED: '已启用',
  DISABLED: '已停用'
}

export const RECIPE_EXECUTION_STATUS = {
  DRY_RUN: '试运行',
  EXECUTED: '已执行',
  FAILED: '执行失败',
  REJECTED: '条件未命中'
}

export const RECIPE_WHEN_LABEL = {
  WEEKLY_REVIEW: '每周回顾时',
  ANNIVERSARY: '纪念日到来时',
  SIMILAR_STORY: '空间出现相似图片时'
}

export const RECIPE_CONDITION_LABEL = {
  SOURCE_SPACE_PRIVATE: '仅私有空间图片',
  SOURCE_CATEGORY: '仅指定分类',
  MAX_TRIAL_COST: '试用额度上限'
}

export const RECIPE_CAPABILITY_LABEL = {
  STORY_DRAFT: '生成故事草稿',
  EMOJI_DRAFT: '生成表情候选',
  IMAGE_FUSION: '多图融合'
}

export function recipeStatusLabel(status) {
  return RECIPE_STATUS[status] || status || '未知状态'
}

export function recipeExecutionStatusLabel(status) {
  return RECIPE_EXECUTION_STATUS[status] || status || '未知状态'
}

export function recipeWhenLabel(type) {
  return RECIPE_WHEN_LABEL[type] || type || '未知触发'
}

export function recipeConditionLabel(type) {
  return RECIPE_CONDITION_LABEL[type] || type || '未知条件'
}

export function recipeCapabilityLabel(capability) {
  return RECIPE_CAPABILITY_LABEL[capability] || capability || '未知能力'
}
