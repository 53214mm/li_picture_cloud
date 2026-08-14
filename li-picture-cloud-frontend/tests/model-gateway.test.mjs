import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import {
  costSourceLabel,
  providerLabel,
  safeErrorLabel,
  supportedCapabilities,
  taskLabel
} from '../src/constants/modelGateway.js'

test('model gateway labels resolve known codes and fall back safely', () => {
  assert.equal(providerLabel('DEEPSEEK'), 'DeepSeek')
  assert.equal(providerLabel('DASHSCOPE'), '阿里云百炼')
  assert.equal(providerLabel('UNKNOWN'), 'UNKNOWN')
  assert.equal(taskLabel('LANGUAGE_AGENT'), '语言对话')
  assert.equal(taskLabel('CONNECTIVITY_CHECK'), 'CONNECTIVITY_CHECK')
  assert.equal(costSourceLabel('BYOK'), '用户自带密钥')
  assert.equal(costSourceLabel('PLATFORM'), '平台钱包')
  assert.equal(safeErrorLabel('CREDENTIAL_REJECTED'), '凭据被拒')
  assert.equal(safeErrorLabel('UPSTREAM_TIMEOUT'), '上游超时')
  assert.equal(safeErrorLabel(null), '未知错误')
})

test('capability chips list only explicitly supported capabilities', () => {
  const profile = {
    text: true,
    vision: false,
    toolCall: true,
    structuredOutput: true,
    reasoning: false,
    embedding: false,
    imageGeneration: false,
    maxContextTokens: 64000
  }
  assert.deepEqual(supportedCapabilities(profile), ['文本', '工具调用', '结构化输出'])
  assert.deepEqual(supportedCapabilities(null), [])
  assert.deepEqual(supportedCapabilities({}), [])
})

test('control center api module mirrors the backend endpoints', async () => {
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/modelGateway.js', import.meta.url)), 'utf8')

  assert.match(api, /request\.post\('\/model\/credentials'/)
  assert.match(api, /request\.get\('\/model\/credentials'\)/)
  assert.match(api, /request\.delete\(`\/model\/credentials\/\$\{id\}`\)/)
  assert.match(api, /request\.post\('\/model\/connections'/)
  assert.match(api, /request\.post\(`\/model\/connections\/\$\{id\}\/enable`\)/)
  assert.match(api, /request\.post\(`\/model\/connections\/\$\{id\}\/disable`\)/)
  assert.match(api, /request\.post\(`\/model\/connections\/\$\{id\}\/rotate-credential`/)
  assert.match(api, /request\.post\(`\/model\/connections\/\$\{id\}\/test`\)/)
  assert.match(api, /request\.get\(`\/model\/connections\/\$\{id\}\/capability`\)/)
  assert.match(api, /request\.put\(`\/model\/routing\/\$\{task\}`/)
  assert.match(api, /request\.get\('\/model\/usage'/)
})

test('control center never renders ciphertext and only collects api keys in inputs', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/views/ModelGatewayView.vue', import.meta.url)), 'utf8')

  // 永不渲染密文或明文回显：保险库只展示尾号。
  assert.match(view, /尾号 \{\{ credential\.tail4 \}\}/)
  assert.doesNotMatch(view, /cipherText/)
  assert.doesNotMatch(view, /\{\{ credential\.apiKey \}\}/)
  // API Key 只出现在 type=password 输入框。
  assert.match(view, /v-model="credentialForm\.apiKey" type="password"/)
  assert.match(view, /v-model="rotateForm\.apiKey" type="password"/)
  // BYOK 失败绝不静默回退的承诺在界面上可见。
  assert.match(view, /绝不静默改扣平台钱包/)
  assert.match(view, /绝不静默回退扣费/)
  // 探测结果只展示安全错误码映射后的文案。
  assert.match(view, /safeErrorLabel\(result\.safeErrorCode\)/)
  // 能力画像只列出明确支持的能力。
  assert.match(view, /supportedCapabilities\(connection\.capability\)\.join\(' · '\)/)
  assert.match(view, /getModelConnectionCapability\(connection\.id\)/)
  // 拦截器已解包 data，页面不得再出现 .data 二次解包。
  assert.doesNotMatch(view, /\.data \?\? \[\]/)
  assert.doesNotMatch(view, /response\.data/)
})

test('control center routing keeps platform default explicit', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/views/ModelGatewayView.vue', import.meta.url)), 'utf8')

  assert.match(view, /平台默认/)
  assert.match(view, /rule\?\.connectionId == null \? '' : String\(rule\.connectionId\)/)
  assert.match(view, /upsertModelRouting\(task, connectionId\)/)
  assert.match(view, /deleteModelRouting\(task\)/)
  // 每个路由选择器都必须有可访问名称。
  assert.match(view, /:aria-label="`\$\{task\.label\}路由`"/)
  // 雪花 ID 必须原样以字符串传递，任何 Number() 转换都会丢精度。
  assert.match(view, /const connectionId = rawValue === '' \? null : rawValue/)
  assert.doesNotMatch(view, /Number\(rawValue\)/)
})

test('mcp admin section is admin-gated and fail-closed', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/views/ModelGatewayView.vue', import.meta.url)), 'utf8')
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/modelGateway.js', import.meta.url)), 'utf8')

  // 仅平台管理员可见。
  assert.match(view, /v-if="userStore\.isAdmin".*data-testid="mcp-section"/s)
  // fail-closed 语义明确展示。
  assert.match(view, /fail-closed/)
  assert.match(view, /未登记即不可达，任意 URL 不开放/)
  assert.match(api, /request\.get\('\/model\/mcp\/services'\)/)
  assert.match(api, /request\.post\(`\/model\/mcp\/services\/\$\{code\}\/enable`\)/)
  assert.match(api, /request\.post\(`\/model\/mcp\/services\/\$\{code\}\/tools\/\$\{toolName\}\/disable`\)/)
  assert.match(api, /request\.delete\(`\/model\/mcp\/services\/\$\{code\}\/tools\/\$\{toolName\}`\)/)
})
