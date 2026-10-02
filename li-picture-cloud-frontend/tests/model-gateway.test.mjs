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
  // 费用来源和连接失败后不会自动扣平台额度的结果直接可见。
  const routingNote = view.match(/<p class="routing-note">([^<]+)<\/p>/)?.[1]
  assert.match(routingNote, /选择“平台默认”或清除规则后，将使用平台钱包/)
  assert.match(routingNote, /使用自带密钥（BYOK）的连接失败时，任务会报错，不会自动使用平台额度/)
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
  assert.match(view, /:aria-label="`\$\{task\.label\}使用的连接`"/)
  // 雪花 ID 必须原样以字符串传递，任何 Number() 转换都会丢精度。
  assert.match(view, /const connectionId = rawValue === '' \? null : rawValue/)
  assert.doesNotMatch(view, /Number\(rawValue\)/)
})

test('mcp admin section is admin-gated and fail-closed', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/views/ModelGatewayView.vue', import.meta.url)), 'utf8')
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/modelGateway.js', import.meta.url)), 'utf8')

  // 仅平台管理员可见。
  assert.match(view, /v-if="userStore\.isAdmin".*data-testid="mcp-section"/s)
  // 保留服务、工具和地址的拒绝边界，用操作后果替代内部术语。
  assert.match(view, /仅平台管理员可管理/)
  assert.match(view, /只开放已登记并启用的服务和白名单工具，不支持未登记的服务地址/)
  assert.match(view, /还没有白名单工具，此服务的工具不会出现在伙伴能力列表中/)
  assert.doesNotMatch(view, /fail-closed/)
  assert.match(api, /request\.get\('\/model\/mcp\/services'\)/)
  assert.match(api, /request\.post\(`\/model\/mcp\/services\/\$\{code\}\/enable`\)/)
  assert.match(api, /request\.post\(`\/model\/mcp\/services\/\$\{code\}\/tools\/\$\{toolName\}\/disable`\)/)
  assert.match(api, /request\.delete\(`\/model\/mcp\/services\/\$\{code\}\/tools\/\$\{toolName\}`\)/)
})

test('usage table exposes the minimal unified usage snapshot columns', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/views/ModelGatewayView.vue', import.meta.url)), 'utf8')

  // 控制中心使用记录展示最小统一用量（输入/输出 token 与图片张数）。
  assert.match(view, /data-testid="usage-table"/)
  assert.match(view, /用量（输入 \/ 输出 \/ 图片）/)
  assert.match(view, /usageAmountLabel\(record\)/)
  assert.match(view, /function usageAmountLabel\(record\)/)
  assert.match(view, /record\.inputTokens/)
  assert.match(view, /record\.outputTokens/)
  assert.match(view, /record\.imageCount/)
  // 供应商原始计量不进入展示视图（只落库，供审计核算）。
  assert.doesNotMatch(view, /record\.rawUsage/)
})

test('model connection copy keeps actionable safety rules and folds technical metadata', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/views/ModelGatewayView.vue', import.meta.url)), 'utf8')
  const pageHeader = view.match(/<header class="gateway-hero">([\s\S]*?)<\/header>/)?.[1]

  assert.match(pageHeader, /<h1>模型连接<\/h1>/)
  assert.match(pageHeader, /添加模型连接，并为不同任务配置使用的模型/)
  assert.doesNotMatch(pageHeader, /你的模型连接|密钥|加密|BYOK|MCP/)
  assert.match(view, /密钥会加密保存，保存后仅显示尾号/)
  assert.match(view, /删除 API Key 后，仍绑定它的连接将无法调用/)
  assert.match(view, /使用平台白名单内的 HTTPS 端点/)
  assert.match(view, /连接绑定同一供应商的 API Key 后才能启用/)
  assert.match(view, /成功后此连接将使用新密钥。旧密钥仍会保留，其他连接不受影响/)
  assert.doesNotMatch(view, /旧密钥不再使用/)
  // 算法和修订版本依然可查，默认折叠且保留原生键盘操作。
  assert.match(view, /<details class="credential-meta">\s*<summary>存储详情<\/summary>\s*<span>加密算法 \{\{ credential\.algorithm \}\} · 版本 \{\{ credential\.revision \}\}<\/span>\s*<\/details>/)
  assert.match(view, /<details class="connection-meta">\s*<summary>连接详情<\/summary>\s*<span>版本 \{\{ connection\.revision \}\}<\/span>\s*<\/details>/)
  assert.doesNotMatch(view, /<details[^>]*\sopen(?:[\s=>])/)
  assert.match(view, /summary:focus-visible/)
  // 仍展示后端具体错误，文案调整不替换权限或调用结果。
  assert.match(view, /return failure\?\.response\?\.data\?\.message \|\| failure\?\.message \|\| fallback/)
})

test('model gateway emptiness requires a successful read rather than a mutation outcome', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/views/ModelGatewayView.vue', import.meta.url)), 'utf8')

  for (const status of ['listStatus', 'usageStatus', 'mcpStatus']) {
    assert.match(view, new RegExp(`const ${status} = ref\\('loading'\\)`))
    assert.match(view, new RegExp(`${status}\\.value = 'ready'`))
    assert.match(view, new RegExp(`${status}\\.value = 'error'`))
  }
  assert.match(view, /v-else-if="listStatus === 'ready'" class="empty-state">还没有 API Key/)
  assert.match(view, /v-else-if="listStatus === 'ready'" class="empty-state">还没有模型连接/)
  assert.match(view, /v-if="hasLoadedLists && MODEL_TASKS.length" class="routing-list"/)
  assert.match(view, /const hasLoadedLists = ref\(false\)/)
  assert.equal((view.match(/hasLoadedLists\.value = true/g) ?? []).length, 1)
  assert.doesNotMatch(view, /hasLoadedLists\.value = false/)
  assert.match(view, /v-else-if="usageStatus === 'ready'" class="empty-state">还没有调用记录/)
  assert.match(view, /v-else-if="mcpStatus === 'ready'" class="empty-state">还没有 MCP 服务/)
  assert.match(view, /v-if="mcpStatus === 'ready' && \(!service.tools \|\| service.tools.length === 0\)"/)
  assert.match(view, /v-if="listStatus === 'loading'" class="read-state" role="status"/)
  assert.match(view, /v-else-if="listStatus === 'error'" class="page-error" role="alert">\{\{ listError \}\}/)
  // 已知列表不会因写入失败变成未知；只由读取函数更新读取状态。
  for (const name of ['submitCredential', 'removeCredential', 'submitConnection', 'confirmRotate', 'submitMcpService', 'submitMcpTool']) {
    const body = view.match(new RegExp(`async function ${name}\\([^)]*\\) \\{([\\s\\S]*?)\\n\\}`))?.[1]
    assert.ok(body, `${name} is present`)
    assert.doesNotMatch(body, /(?:list|usage|mcp)Status\.value\s*=/)
  }
})
