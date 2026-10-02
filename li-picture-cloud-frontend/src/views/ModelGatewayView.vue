<template>
  <div class="model-gateway-page">
    <div class="container">
      <header class="gateway-hero">
        <div>
          <h1>模型连接</h1>
          <p>添加模型连接，并为不同任务配置使用的模型。</p>
        </div>
      </header>

      <p v-if="error" class="page-error" role="alert">{{ error }}</p>
      <p v-if="listStatus === 'loading'" class="read-state" role="status">正在加载模型连接…</p>
      <p v-else-if="listStatus === 'error'" class="page-error" role="alert">{{ listError }}</p>

      <section class="gateway-card" aria-labelledby="credential-title">
        <header>
          <div>
            <h2 id="credential-title">API Key</h2>
            <p class="section-helper">保存 API Key 后，可将它绑定到同一供应商的连接。密钥会加密保存，保存后仅显示尾号。</p>
          </div>
        </header>
        <form class="credential-form" @submit.prevent="submitCredential">
          <label>
            <span>供应商</span>
            <select v-model="credentialForm.provider" required>
              <option v-for="item in MODEL_PROVIDERS" :key="item.code" :value="item.code">
                {{ item.label }}
              </option>
            </select>
          </label>
          <label>
            <span>API Key</span>
            <input v-model="credentialForm.apiKey" type="password" autocomplete="off" required
                   placeholder="sk-…">
          </label>
          <button class="btn" type="submit" :disabled="credentialBusy">
            {{ credentialBusy ? '正在保存…' : '保存 API Key' }}
          </button>
        </form>
        <ul v-if="credentials.length" class="credential-list" data-testid="credential-list">
          <li v-for="credential in credentials" :key="credential.id">
            <span class="credential-provider">{{ providerLabel(credential.provider) }}</span>
            <code>尾号 {{ credential.tail4 }}</code>
            <details class="credential-meta">
              <summary>存储详情</summary>
              <span>加密算法 {{ credential.algorithm }} · 版本 {{ credential.revision }}</span>
            </details>
            <button class="btn btn-sm btn-outline" type="button"
                    :disabled="credentialBusy"
                    @click="removeCredential(credential.id)">删除</button>
          </li>
        </ul>
        <p v-else-if="listStatus === 'ready'" class="empty-state">还没有 API Key。填写供应商和密钥后保存。</p>
        <p v-if="credentials.length" class="section-note">删除 API Key 后，仍绑定它的连接将无法调用。</p>
      </section>

      <section class="gateway-card" aria-labelledby="connection-title">
        <header>
          <div>
            <h2 id="connection-title">连接配置</h2>
            <p class="section-helper">使用平台白名单内的 HTTPS 端点。连接绑定同一供应商的 API Key 后才能启用。</p>
          </div>
        </header>
        <form class="connection-form" @submit.prevent="submitConnection">
          <label>
            <span>供应商</span>
            <select v-model="connectionForm.provider" required>
              <option v-for="item in MODEL_PROVIDERS" :key="item.code" :value="item.code">
                {{ item.label }}
              </option>
            </select>
          </label>
          <label>
            <span>连接名称</span>
            <input v-model="connectionForm.displayName" required maxlength="64" placeholder="例如：主力">
          </label>
          <label>
            <span>端点（仅 HTTPS）</span>
            <input v-model="connectionForm.endpoint" type="url" required
                   placeholder="https://api.deepseek.com/v1">
          </label>
          <label>
            <span>模型编码</span>
            <input v-model="connectionForm.modelCode" required maxlength="64"
                   placeholder="deepseek-chat">
          </label>
          <label>
            <span>绑定 API Key（可选）</span>
            <select v-model="connectionForm.credentialId">
              <option :value="null">暂不绑定</option>
              <option v-for="credential in credentials" :key="credential.id" :value="credential.id">
                {{ providerLabel(credential.provider) }} · 尾号 {{ credential.tail4 }}
              </option>
            </select>
          </label>
          <button class="btn" type="submit" :disabled="connectionBusy">
            {{ connectionBusy ? '正在创建…' : '添加连接' }}
          </button>
        </form>
        <ul v-if="connections.length" class="connection-list" data-testid="connection-list">
          <li v-for="connection in connections" :key="connection.id" class="connection-row">
            <div class="connection-main">
              <strong>{{ connection.displayName }}</strong>
              <span class="connection-endpoint">{{ connection.endpointUri }}</span>
              <span class="connection-meta">
                {{ providerLabel(connection.provider) }} / {{ connection.modelCode }}
                · {{ connection.enabled ? '已启用' : '已停用' }}
              </span>
              <details class="connection-meta">
                <summary>连接详情</summary>
                <span>版本 {{ connection.revision }}</span>
              </details>
              <span v-if="connection.probeResult" class="probe-result"
                    :class="{ failed: !connection.probeResult.reachable }">
                {{ probeLabel(connection.probeResult) }}
              </span>
              <span v-if="connection.capability" class="capability-chips" data-testid="capability-chips">
                支持：{{ supportedCapabilities(connection.capability).join(' · ') || '暂无已知能力' }}
                <template v-if="connection.capability.maxContextTokens">
                  · 上下文 {{ connection.capability.maxContextTokens }}
                </template>
              </span>
            </div>
            <div class="connection-actions">
              <button v-if="!connection.enabled" class="btn btn-sm" type="button"
                      @click="toggleConnection(connection, true)">启用</button>
              <button v-else class="btn btn-sm btn-outline" type="button"
                      @click="toggleConnection(connection, false)">停用</button>
              <button class="btn btn-sm btn-outline" type="button"
                      :disabled="!connection.enabled || probingId === connection.id"
                      @click="probeConnection(connection)">
                {{ probingId === connection.id ? '正在测试…' : '测试连接' }}
              </button>
              <button class="btn btn-sm btn-outline" type="button"
                      @click="rotateCredentialFor(connection)">更换 API Key</button>
              <button class="btn btn-sm btn-danger-outline" type="button"
                      @click="removeConnection(connection.id)">删除</button>
            </div>
          </li>
        </ul>
        <p v-else-if="listStatus === 'ready'" class="empty-state">还没有模型连接。填写端点和模型信息，添加后可启用并测试。</p>

        <div v-if="rotating" class="rotate-panel" role="form" aria-label="更换 API Key">
          <p>为「{{ rotating.displayName }}」保存并绑定新 API Key，成功后此连接将使用新密钥。旧密钥仍会保留，其他连接不受影响。</p>
          <label>
            <span>新 API Key</span>
            <input v-model="rotateForm.apiKey" type="password" autocomplete="off" required>
          </label>
          <div class="rotate-actions">
            <button class="btn btn-sm" type="button" :disabled="connectionBusy"
                    @click="confirmRotate">确认更换</button>
            <button class="btn btn-sm btn-outline" type="button" @click="rotating = null">取消</button>
          </div>
        </div>
      </section>

      <section class="gateway-card" aria-labelledby="routing-title">
        <header>
          <div>
            <h2 id="routing-title">任务模型</h2>
            <p class="section-helper">为每类任务选择要使用的连接。</p>
          </div>
        </header>
        <ul v-if="hasLoadedLists && MODEL_TASKS.length" class="routing-list">
          <li v-for="task in MODEL_TASKS" :key="task.code" class="routing-row">
            <span class="routing-task">{{ task.label }}</span>
            <select :aria-label="`${task.label}使用的连接`"
                    :value="routingSelection(task.code)"
                    :disabled="routingBusy"
                    @change="saveRouting(task.code, $event.target.value)">
              <option value="">平台默认</option>
              <option v-for="connection in connections" :key="connection.id" :value="String(connection.id)">
                {{ connection.displayName }}（{{ providerLabel(connection.provider) }}）
              </option>
            </select>
            <button class="btn btn-sm btn-danger-outline" type="button"
                    :disabled="routingBusy || !routingSelection(task.code)"
                    @click="clearRouting(task.code)">清除规则</button>
          </li>
        </ul>
        <p class="routing-note">选择“平台默认”或清除规则后，将使用平台钱包。使用自带密钥（BYOK）的连接失败时，任务会报错，不会自动使用平台额度。</p>
      </section>

      <section v-if="userStore.isAdmin" class="gateway-card" aria-labelledby="mcp-title"
               data-testid="mcp-section">
        <header>
          <div>
            <h2 id="mcp-title">MCP 白名单</h2>
            <p class="section-helper">仅平台管理员可管理。只开放已登记并启用的服务和白名单工具，不支持未登记的服务地址。</p>
          </div>
        </header>
        <p v-if="mcpStatus === 'loading'" class="read-state" role="status">正在加载 MCP 服务…</p>
        <p v-else-if="mcpStatus === 'error'" class="page-error" role="alert">{{ mcpError }}</p>
        <form class="connection-form" @submit.prevent="submitMcpService">
          <label>
            <span>服务代码</span>
            <input v-model="mcpServiceForm.code" required maxlength="64"
                   placeholder="mxai-mcp-server">
          </label>
          <label>
            <span>服务名称</span>
            <input v-model="mcpServiceForm.displayName" required maxlength="64"
                   placeholder="MxAI 服务">
          </label>
          <label>
            <span>端点（仅 HTTPS）</span>
            <input v-model="mcpServiceForm.endpointUri" type="url" required
                   placeholder="https://mcp.example.cn">
          </label>
          <button class="btn" type="submit" :disabled="mcpBusy">登记服务</button>
        </form>
        <ul v-if="mcpServices.length" class="mcp-list">
          <li v-for="service in mcpServices" :key="service.id" class="mcp-row">
            <div class="mcp-main">
              <strong>{{ service.displayName }}</strong>
              <code>{{ service.code }}</code>
              <span class="connection-meta">
                {{ service.endpointUri }} · {{ service.enabled ? '已启用' : '已停用' }}
              </span>
              <span v-if="mcpStatus === 'ready' && (!service.tools || service.tools.length === 0)" class="mcp-note">
                还没有白名单工具，此服务的工具不会出现在伙伴能力列表中。
              </span>
            </div>
            <div class="connection-actions">
              <button v-if="!service.enabled" class="btn btn-sm" type="button"
                      :disabled="mcpBusy" @click="toggleMcpService(service, true)">启用</button>
              <button v-else class="btn btn-sm btn-outline" type="button"
                      :disabled="mcpBusy" @click="toggleMcpService(service, false)">停用</button>
            </div>
            <div v-if="service.tools" class="mcp-tools">
              <form class="mcp-tool-add" @submit.prevent="submitMcpTool(service)">
                <label>
                  <span class="visually-hidden">工具名</span>
                  <input v-model="service.newToolName" required maxlength="128"
                         placeholder="例如 generate_image">
                </label>
                <button class="btn btn-sm" type="submit" :disabled="mcpBusy">加入白名单</button>
              </form>
              <ul class="mcp-tool-list">
                <li v-for="tool in service.tools" :key="tool.id" class="mcp-tool-row">
                  <code>{{ tool.toolName }}</code>
                  <span :class="tool.enabled ? 'usage-success' : 'usage-failure'">
                    {{ tool.enabled ? '已启用' : '已停用' }}
                  </span>
                  <button class="btn btn-sm btn-outline" type="button" :disabled="mcpBusy"
                          @click="toggleMcpTool(service, tool, !tool.enabled)">
                    {{ tool.enabled ? '停用' : '启用' }}
                  </button>
                  <button class="btn btn-sm btn-danger-outline" type="button" :disabled="mcpBusy"
                          @click="removeMcpToolFor(service, tool)">移出白名单</button>
                </li>
              </ul>
            </div>
          </li>
        </ul>
        <p v-else-if="mcpStatus === 'ready'" class="empty-state">还没有 MCP 服务。登记服务后，再添加允许使用的工具。</p>
      </section>

      <section class="gateway-card" aria-labelledby="usage-title">
        <header>
          <div>
            <h2 id="usage-title">最近调用</h2>
          </div>
        </header>
        <p v-if="usageStatus === 'loading'" class="read-state" role="status">正在加载调用记录…</p>
        <p v-else-if="usageStatus === 'error'" class="page-error" role="alert">{{ usageError }}</p>
        <table v-if="usage.length" class="usage-table" data-testid="usage-table">
          <thead>
            <tr>
              <th scope="col">时间</th>
              <th scope="col">任务</th>
              <th scope="col">供应商 / 模型</th>
              <th scope="col">费用来源</th>
              <th scope="col">用量（输入 / 输出 / 图片）</th>
              <th scope="col">结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in usage" :key="record.id">
              <td>{{ formatTime(record.createdTime) }}</td>
              <td>{{ taskLabel(record.task) }}</td>
              <td>{{ providerLabel(record.provider) }} / {{ record.modelCode }}</td>
              <td>{{ costSourceLabel(record.costSource) }}</td>
              <td>{{ usageAmountLabel(record) }}</td>
              <td>
                <span v-if="record.success" class="usage-success">成功</span>
                <span v-else class="usage-failure">{{ safeErrorLabel(record.safeErrorCode) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else-if="usageStatus === 'ready'" class="empty-state">还没有调用记录。测试连接或使用模型后，可在这里查看。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import {
  MODEL_PROVIDERS,
  MODEL_TASKS,
  costSourceLabel,
  providerLabel,
  safeErrorLabel,
  supportedCapabilities,
  taskLabel
} from '@/constants/modelGateway'
import {
  addMcpTool,
  createModelConnection,
  createModelCredential,
  deleteModelConnection,
  deleteModelCredential,
  deleteModelRouting,
  disableMcpService,
  disableMcpTool,
  disableModelConnection,
  enableMcpService,
  enableMcpTool,
  enableModelConnection,
  getModelConnectionCapability,
  listMcpServices,
  listMcpTools,
  listModelConnections,
  listModelCredentials,
  listModelRouting,
  listModelUsage,
  removeMcpTool,
  rotateModelCredential,
  testModelConnection,
  upsertMcpService,
  upsertModelRouting
} from '@/api/modelGateway'

const userStore = useUserStore()
const error = ref('')
const listStatus = ref('loading')
const hasLoadedLists = ref(false)
const listError = ref('')
const usageStatus = ref('loading')
const usageError = ref('')
const mcpStatus = ref('loading')
const mcpError = ref('')
const credentials = ref([])
const connections = ref([])
const routing = ref([])
const usage = ref([])
const mcpServices = ref([])
const credentialBusy = ref(false)
const connectionBusy = ref(false)
const routingBusy = ref(false)
const mcpBusy = ref(false)
const probingId = ref(null)
const rotating = ref(null)

const credentialForm = reactive({ provider: 'DEEPSEEK', apiKey: '' })
const connectionForm = reactive({
  provider: 'DEEPSEEK',
  displayName: '',
  endpoint: '',
  modelCode: '',
  credentialId: null
})
const rotateForm = reactive({ apiKey: '' })
const mcpServiceForm = reactive({ code: '', displayName: '', endpointUri: '' })

onMounted(loadAll)

async function loadAll() {
  listStatus.value = 'loading'
  listError.value = ''
  usageStatus.value = 'loading'
  usageError.value = ''
  try {
    const tasks = [
      listModelCredentials(),
      listModelConnections(),
      listModelRouting(),
      listModelUsage()
    ]
    if (userStore.isAdmin) {
      tasks.push(loadMcpServices())
    }
    const [credentialList, connectionList, routingList, usageList] = await Promise.all(tasks)
    credentials.value = credentialList ?? []
    connections.value = connectionList ?? []
    routing.value = routingList ?? []
    usage.value = usageList ?? []
    hasLoadedLists.value = true
    listStatus.value = 'ready'
    usageStatus.value = 'ready'
    error.value = ''
  } catch (failure) {
    listStatus.value = 'error'
    usageStatus.value = 'error'
    listError.value = extractMessage(failure, '模型连接加载失败，请刷新重试')
    usageError.value = '调用记录未完成加载，请刷新重试'
  }
}

async function loadMcpServices() {
  mcpStatus.value = 'loading'
  mcpError.value = ''
  try {
    const services = await listMcpServices()
    const withTools = await Promise.all((services ?? []).map(async service => ({
      ...service,
      tools: (await listMcpTools(service.code)) ?? []
    })))
    mcpServices.value = withTools
    mcpStatus.value = 'ready'
  } catch (failure) {
    mcpStatus.value = 'error'
    mcpError.value = extractMessage(failure, 'MCP 服务加载失败，请刷新重试')
  }
}

async function submitCredential() {
  credentialBusy.value = true
  try {
    await createModelCredential({
      provider: credentialForm.provider,
      apiKey: credentialForm.apiKey
    })
    credentialForm.apiKey = ''
    await loadAll()
  } catch (failure) {
    error.value = extractMessage(failure, 'API Key 保存失败')
  } finally {
    credentialBusy.value = false
  }
}

async function removeCredential(id) {
  credentialBusy.value = true
  try {
    await deleteModelCredential(id)
    await loadAll()
  } catch (failure) {
    error.value = extractMessage(failure, 'API Key 删除失败')
  } finally {
    credentialBusy.value = false
  }
}

async function submitConnection() {
  connectionBusy.value = true
  try {
    await createModelConnection({
      provider: connectionForm.provider,
      displayName: connectionForm.displayName,
      endpoint: connectionForm.endpoint,
      modelCode: connectionForm.modelCode,
      credentialId: connectionForm.credentialId
    })
    connectionForm.displayName = ''
    connectionForm.endpoint = ''
    connectionForm.modelCode = ''
    connectionForm.credentialId = null
    await loadAll()
  } catch (failure) {
    error.value = extractMessage(failure, '连接创建失败')
  } finally {
    connectionBusy.value = false
  }
}

async function toggleConnection(connection, enabled) {
  connectionBusy.value = true
  try {
    await (enabled ? enableModelConnection(connection.id) : disableModelConnection(connection.id))
    await loadAll()
  } catch (failure) {
    error.value = extractMessage(failure, enabled ? '启用失败' : '停用失败')
  } finally {
    connectionBusy.value = false
  }
}

async function probeConnection(connection) {
  probingId.value = connection.id
  try {
    const response = await testModelConnection(connection.id)
    connection.probeResult = response
    connection.capability = null
    if (response?.reachable) {
      try {
        connection.capability = await getModelConnectionCapability(connection.id)
      } catch {
        // 画像缺失不掩盖探测结果。
        connection.capability = null
      }
    }
    await loadUsageOnly()
  } catch (failure) {
    connection.probeResult = { reachable: false, safeErrorCode: 'UPSTREAM_ERROR' }
    error.value = extractMessage(failure, '连接测试失败')
  } finally {
    probingId.value = null
  }
}

function rotateCredentialFor(connection) {
  rotating.value = connection
  rotateForm.apiKey = ''
}

async function confirmRotate() {
  connectionBusy.value = true
  try {
    await rotateModelCredential(rotating.value.id, rotateForm.apiKey)
    rotating.value = null
    rotateForm.apiKey = ''
    await loadAll()
  } catch (failure) {
    error.value = extractMessage(failure, 'API Key 更换失败')
  } finally {
    connectionBusy.value = false
  }
}

async function removeConnection(id) {
  connectionBusy.value = true
  try {
    await deleteModelConnection(id)
    await loadAll()
  } catch (failure) {
    error.value = extractMessage(failure, '连接删除失败')
  } finally {
    connectionBusy.value = false
  }
}

function routingSelection(task) {
  const rule = routing.value.find(item => item.task === task)
  return rule?.connectionId == null ? '' : String(rule.connectionId)
}

async function saveRouting(task, rawValue) {
  routingBusy.value = true
  try {
    // 后端雪花 ID 以字符串序列化，这里必须原样传递，转 Number 会丢精度。
    const connectionId = rawValue === '' ? null : rawValue
    await upsertModelRouting(task, connectionId)
    await loadAll()
  } catch (failure) {
    error.value = extractMessage(failure, '任务模型保存失败')
  } finally {
    routingBusy.value = false
  }
}

async function clearRouting(task) {
  routingBusy.value = true
  try {
    await deleteModelRouting(task)
    await loadAll()
  } catch (failure) {
    error.value = extractMessage(failure, '任务规则清除失败')
  } finally {
    routingBusy.value = false
  }
}

async function loadUsageOnly() {
  usageStatus.value = 'loading'
  usageError.value = ''
  try {
    const response = await listModelUsage()
    usage.value = response ?? []
    usageStatus.value = 'ready'
  } catch (failure) {
    usageStatus.value = 'error'
    usageError.value = extractMessage(failure, '调用记录加载失败，请刷新重试')
  }
}

function probeLabel(result) {
  return result.reachable ? '连接测试通过' : `连接测试失败：${safeErrorLabel(result.safeErrorCode)}`
}

async function submitMcpService() {
  mcpBusy.value = true
  try {
    await upsertMcpService({
      code: mcpServiceForm.code,
      displayName: mcpServiceForm.displayName,
      endpointUri: mcpServiceForm.endpointUri
    })
    mcpServiceForm.code = ''
    mcpServiceForm.displayName = ''
    mcpServiceForm.endpointUri = ''
    await loadMcpServices()
  } catch (failure) {
    error.value = extractMessage(failure, 'MCP 服务登记失败')
  } finally {
    mcpBusy.value = false
  }
}

async function toggleMcpService(service, enabled) {
  mcpBusy.value = true
  try {
    await (enabled ? enableMcpService(service.code) : disableMcpService(service.code))
    await loadMcpServices()
  } catch (failure) {
    error.value = extractMessage(failure, enabled ? 'MCP 服务启用失败' : 'MCP 服务停用失败')
  } finally {
    mcpBusy.value = false
  }
}

async function submitMcpTool(service) {
  mcpBusy.value = true
  try {
    await addMcpTool(service.code, service.newToolName)
    service.newToolName = ''
    await loadMcpServices()
  } catch (failure) {
    error.value = extractMessage(failure, '工具加入白名单失败')
  } finally {
    mcpBusy.value = false
  }
}

async function toggleMcpTool(service, tool, enabled) {
  mcpBusy.value = true
  try {
    await (enabled ? enableMcpTool(service.code, tool.toolName)
      : disableMcpTool(service.code, tool.toolName))
    await loadMcpServices()
  } catch (failure) {
    error.value = extractMessage(failure, '工具状态更新失败')
  } finally {
    mcpBusy.value = false
  }
}

async function removeMcpToolFor(service, tool) {
  mcpBusy.value = true
  try {
    await removeMcpTool(service.code, tool.toolName)
    await loadMcpServices()
  } catch (failure) {
    error.value = extractMessage(failure, '工具移出白名单失败')
  } finally {
    mcpBusy.value = false
  }
}

function formatTime(value) {
  if (!value) return ''
  return new Date(value).toLocaleString()
}

/** 最小统一用量展示：输入/输出 token 与图片张数；缺失显示 —。 */
function usageAmountLabel(record) {
  const parts = [
    record.inputTokens != null ? String(record.inputTokens) : '—',
    record.outputTokens != null ? String(record.outputTokens) : '—',
    record.imageCount != null ? `${record.imageCount} 张` : '—'
  ]
  return parts.join(' / ')
}

function extractMessage(failure, fallback) {
  return failure?.response?.data?.message || failure?.message || fallback
}
</script>

<style scoped>
.model-gateway-page { min-height: calc(100dvh - 4rem); padding: 2rem 0 5rem; background: var(--gray-100); }
.model-gateway-page .container { display: grid; gap: 1.5rem; }
.gateway-hero { padding: 1.25rem 1.5rem; border: 2px solid var(--black); background: var(--black); color: var(--white); }
.gateway-hero h1 { margin-top: .25rem; }
.gateway-hero p { margin-top: .5rem; max-width: 46rem; color: var(--gray-300); font-size: .9rem; }
.page-error { padding: .7rem .9rem; border-left: 4px solid var(--red); background: var(--white); color: var(--red); font-size: .85rem; }
.gateway-card { border: 2px solid var(--black); background: var(--white); }
.gateway-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.gateway-card h2 { font-size: 1.35rem; }
.section-helper { margin-top: .45rem; color: var(--gray-600); font-size: .85rem; }
.section-note { margin: 1rem 1.5rem; color: var(--gray-600); font-size: .8rem; }
.read-state { padding: 1rem 1.5rem; color: var(--gray-600); font-size: .85rem; }
summary { cursor: pointer; }
summary:focus-visible { outline: 2px solid var(--black); outline-offset: 3px; }
.credential-meta[open] summary, .connection-meta[open] summary { margin-bottom: .3rem; }
.empty-state { padding: 1.5rem; color: var(--gray-600); font-size: .9rem; }
.credential-form, .connection-form { display: grid; gap: .9rem; padding: 1.25rem 1.5rem; }
label { display: grid; gap: .3rem; font-size: .82rem; font-weight: 600; }
input, select { padding: .6rem .75rem; border: 2px solid var(--black); background: var(--white); font: inherit; min-height: 44px; }
.credential-form .btn, .connection-form .btn { justify-self: start; }
.credential-list, .connection-list { list-style: none; border-top: 2px solid var(--black); }
.credential-list li { display: flex; flex-wrap: wrap; gap: .7rem; align-items: center; padding: .8rem 1.5rem; border-bottom: 1px solid var(--gray-200); font-size: .85rem; }
.credential-provider { font-weight: 700; }
.credential-list code { padding: .15rem .4rem; background: var(--gray-100); border: 1px solid var(--gray-300); }
.credential-meta { color: var(--gray-600); }
.credential-list .btn { margin-left: auto; }
.connection-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .9rem; padding: 1rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.connection-main { display: grid; gap: .2rem; }
.connection-endpoint { font-size: .78rem; color: var(--gray-600); overflow-wrap: anywhere; }
.connection-meta { font-size: .75rem; color: var(--gray-600); }
.probe-result { justify-self: start; padding: .15rem .45rem; background: #e6f4ea; color: #075d2a; font-size: .75rem; font-weight: 700; }
.probe-result.failed { background: #fdeaea; color: var(--red); }
.capability-chips { justify-self: start; color: var(--gray-600); font-size: .75rem; }
.connection-actions { display: flex; flex-wrap: wrap; gap: .5rem; justify-content: flex-end; }
.rotate-panel { margin: 0 1.5rem 1.5rem; padding: 1rem; border: 2px dashed var(--black); display: grid; gap: .8rem; }
.rotate-panel p { font-size: .85rem; color: var(--gray-600); }
.rotate-actions { display: flex; gap: .6rem; }
.routing-list { list-style: none; border-top: 2px solid var(--black); }
.routing-row { display: grid; grid-template-columns: minmax(7rem, .4fr) minmax(0, 1fr) auto; gap: .8rem; align-items: center; padding: .9rem 1.5rem; border-bottom: 1px solid var(--gray-200); }
.routing-task { font-weight: 700; }
.routing-note { margin: 1rem 1.5rem 1.5rem; padding: .6rem .8rem; border-left: 4px solid var(--red); background: var(--gray-100); color: var(--gray-600); font-size: .75rem; }
.usage-table { width: 100%; border-collapse: collapse; font-size: .82rem; }
.usage-table th, .usage-table td { padding: .65rem .8rem; border-bottom: 1px solid var(--gray-200); text-align: left; }
.usage-table th { background: var(--gray-100); font-size: .72rem; text-transform: uppercase; letter-spacing: .06em; }
.usage-success { color: #075d2a; font-weight: 700; }
.usage-failure { color: var(--red); font-weight: 700; }
.mcp-list { list-style: none; border-top: 2px solid var(--black); }
.mcp-row { padding: 1rem 1.5rem; border-bottom: 1px solid var(--gray-200); display: grid; gap: .8rem; }
.mcp-main { display: grid; gap: .2rem; }
.mcp-main code { justify-self: start; padding: .15rem .4rem; background: var(--gray-100); border: 1px solid var(--gray-300); }
.mcp-note { padding: .5rem .7rem; border-left: 4px solid var(--red); background: var(--gray-100); color: var(--gray-600); font-size: .75rem; }
.mcp-tools { display: grid; gap: .6rem; border-top: 1px dashed var(--gray-300); padding-top: .8rem; }
.mcp-tool-add { display: flex; gap: .6rem; align-items: end; }
.mcp-tool-add label { flex: 1; }
.mcp-tool-list { list-style: none; display: grid; gap: .5rem; }
.mcp-tool-row { display: flex; flex-wrap: wrap; gap: .6rem; align-items: center; }
.mcp-tool-row code { padding: .15rem .4rem; background: var(--gray-100); border: 1px solid var(--gray-300); }
.btn-danger-outline { border-color: var(--red); color: var(--red); background: var(--white); }
.btn-danger-outline:hover { background: var(--red); color: var(--white); }
@media (max-width: 767px) {
  .connection-row, .routing-row { grid-template-columns: 1fr; }
  .connection-actions { justify-content: flex-start; }
}
</style>
