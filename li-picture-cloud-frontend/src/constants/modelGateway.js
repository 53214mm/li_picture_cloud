export const MODEL_PROVIDERS = [
  { code: 'DEEPSEEK', label: 'DeepSeek' },
  { code: 'OPENAI', label: 'OpenAI' },
  { code: 'ANTHROPIC', label: 'Anthropic' },
  { code: 'GOOGLE', label: 'Google' },
  { code: 'MOONSHOT', label: 'Moonshot' },
  { code: 'DASHSCOPE', label: '阿里云百炼' }
]

export const MODEL_TASKS = [
  { code: 'LANGUAGE_AGENT', label: '语言对话' },
  { code: 'VISION_UNDERSTANDING', label: '视觉理解' },
  { code: 'IMAGE_CREATION', label: '图像生成' }
]

const PROVIDER_LABEL = Object.fromEntries(MODEL_PROVIDERS.map(item => [item.code, item.label]))
const TASK_LABEL = Object.fromEntries(MODEL_TASKS.map(item => [item.code, item.label]))
const COST_SOURCE_LABEL = { BYOK: '用户自带密钥', PLATFORM: '平台钱包' }

export function providerLabel(code) {
  return PROVIDER_LABEL[code] || code || '未知供应商'
}

export function taskLabel(code) {
  return TASK_LABEL[code] || code || '未知任务'
}

export function costSourceLabel(code) {
  return COST_SOURCE_LABEL[code] || code || '未知来源'
}

const SAFE_ERROR_LABEL = {
  CREDENTIAL_REJECTED: '凭据被拒',
  UPSTREAM_TIMEOUT: '上游超时',
  UPSTREAM_ERROR: '上游错误'
}

export function safeErrorLabel(code) {
  return SAFE_ERROR_LABEL[code] || code || '未知错误'
}
