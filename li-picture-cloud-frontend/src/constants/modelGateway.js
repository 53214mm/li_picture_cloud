// keyUrl：厂商控制台里创建 API Key 的页面；tagline 只是帮助用户认出厂商的一句话。
export const MODEL_PROVIDERS = [
  { code: 'DEEPSEEK', label: 'DeepSeek', tagline: '深度求索', keyUrl: 'https://platform.deepseek.com/api_keys' },
  { code: 'OPENAI', label: 'OpenAI', tagline: 'GPT 系列', keyUrl: 'https://platform.openai.com/api-keys' },
  { code: 'ANTHROPIC', label: 'Anthropic', tagline: 'Claude 系列', keyUrl: 'https://console.anthropic.com/settings/keys' },
  { code: 'GOOGLE', label: 'Google', tagline: 'Gemini 系列', keyUrl: 'https://aistudio.google.com/apikey' },
  { code: 'MOONSHOT', label: 'Moonshot', tagline: 'Kimi 月之暗面', keyUrl: 'https://platform.moonshot.cn/console/api-keys' },
  { code: 'DASHSCOPE', label: '阿里云百炼', tagline: '通义千问', keyUrl: 'https://bailian.console.aliyun.com/' },
  { code: 'SILICONFLOW', label: '硅基流动', tagline: '多家开源模型', keyUrl: 'https://cloud.siliconflow.cn/account/ak' },
  { code: 'ZHIPU', label: '智谱', tagline: 'GLM 系列', keyUrl: 'https://open.bigmodel.cn/usercenter/apikeys' }
]

export function providerInfo(code) {
  return MODEL_PROVIDERS.find(item => item.code === code) || null
}

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
  UPSTREAM_ERROR: '上游错误',
  MODEL_UNAVAILABLE: '模型不可用于对话',
  QUOTA_EXHAUSTED: '余额不足或被限流'
}

export function safeErrorLabel(code) {
  return SAFE_ERROR_LABEL[code] || code || '未知错误'
}

const CAPABILITY_LABELS = [
  { key: 'text', label: '文本' },
  { key: 'vision', label: '视觉理解' },
  { key: 'toolCall', label: '工具调用' },
  { key: 'structuredOutput', label: '结构化输出' },
  { key: 'reasoning', label: '深度推理' },
  { key: 'embedding', label: '向量嵌入' },
  { key: 'imageGeneration', label: '图像生成' }
]

/** 画像只列出明确支持的能力；未知能力一律不展示（按不支持处理）。 */
export function supportedCapabilities(profile) {
  if (!profile) return []
  return CAPABILITY_LABELS.filter(item => profile[item.key] === true).map(item => item.label)
}
