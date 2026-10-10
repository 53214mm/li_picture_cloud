import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { describeQuickConnection, filterModels, quickConnectError } from '../src/presentation/modelQuickConnect.js'
import { describeChatMode } from '../src/presentation/companionChatMode.js'
import { MODEL_PROVIDERS, providerInfo, safeErrorLabel } from '../src/constants/modelGateway.js'

test('quick connection status describes connected, broken and platform states', () => {
  const connected = describeQuickConnection({ status: 'CONNECTED', providerLabel: 'DeepSeek', modelCode: 'deepseek-chat' })
  assert.equal(connected.tone, 'connected')
  assert.equal(connected.title, 'DeepSeek · deepseek-chat')
  assert.equal(describeQuickConnection({ status: 'BROKEN' }).tone, 'broken')
  for (const view of [{ status: 'PLATFORM' }, null, undefined, {}]) {
    assert.equal(describeQuickConnection(view).tone, 'platform')
  }
})

test('model filter is case-insensitive and tolerates bad input', () => {
  const models = ['deepseek-chat', 'deepseek-ai/DeepSeek-V3', 'Qwen/Qwen2.5-7B-Instruct']
  assert.deepEqual(filterModels(models, ' deepseek '), ['deepseek-chat', 'deepseek-ai/DeepSeek-V3'])
  assert.deepEqual(filterModels(models, ''), models)
  assert.deepEqual(filterModels(null, 'x'), [])
  assert.deepEqual(filterModels(models, 42), models)
})

test('quick connect errors prefer the server message', () => {
  assert.equal(quickConnectError({ response: { data: { message: 'API Key 无效' } } }, 'x'), 'API Key 无效')
  assert.equal(quickConnectError({ message: '网络错误' }, 'x'), '网络错误')
  assert.equal(quickConnectError(null, '兜底'), '兜底')
})

test('chat mode copy follows the server view and never guesses a mode', () => {
  assert.deepEqual(describeChatMode(null), { note: '', link: null })
  assert.equal(describeChatMode({ mode: 'DEMO', modelAvailable: true }).link, null)
  assert.match(describeChatMode({ mode: 'DEMO', modelAvailable: false }).link, /去连接模型/)
  assert.match(describeChatMode({ mode: 'DEMO', modelAvailable: false, modelSource: 'NONE' }).note, /平台模型未启用或暂不可用/)
  const own = describeChatMode({ mode: 'MODEL', modelSource: 'OWN', providerLabel: 'DeepSeek', modelCode: 'deepseek-chat' })
  assert.match(own.note, /你的 DeepSeek · deepseek-chat/)
  assert.equal(own.link, null)
  assert.match(describeChatMode({ mode: 'MODEL', modelSource: 'OWN', modelBroken: true }).note, /不可用/)
  assert.match(describeChatMode({ mode: 'MODEL', modelSource: 'PLATFORM' }).note, /试用额度/)
})

test('providers include the new vendors with key pages and new safe error labels', () => {
  assert.deepEqual(MODEL_PROVIDERS.map(item => item.code),
    ['DEEPSEEK', 'OPENAI', 'ANTHROPIC', 'GOOGLE', 'MOONSHOT', 'DASHSCOPE', 'SILICONFLOW', 'ZHIPU'])
  for (const item of MODEL_PROVIDERS) assert.match(item.keyUrl, /^https:\/\//)
  assert.equal(providerInfo('ZHIPU').label, '智谱')
  assert.equal(providerInfo('NOPE'), null)
  assert.equal(safeErrorLabel('MODEL_UNAVAILABLE'), '模型不可用于对话')
  assert.equal(safeErrorLabel('QUOTA_EXHAUSTED'), '余额不足或被限流')
})

test('quick connect keeps the API key in a password field and out of storage', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/components/model/ModelQuickConnect.vue', import.meta.url)), 'utf8')
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/modelGateway.js', import.meta.url)), 'utf8')
  assert.match(view, /v-model="apiKey" type="password" autocomplete="off"/)
  assert.doesNotMatch(view, /localStorage|sessionStorage/)
  assert.match(view, /rel="noopener noreferrer"/)
  assert.match(api, /request\.post\('\/model\/quick\/models', \{ provider, apiKey \}\)/)
  assert.match(api, /request\.post\('\/model\/quick\/connect', \{ provider, apiKey, modelCode \}\)/)
  assert.match(api, /request\.delete\('\/model\/quick\/current'\)/)
})
