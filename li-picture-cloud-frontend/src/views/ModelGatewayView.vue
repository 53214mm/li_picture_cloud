<template>
  <div class="model-gateway-page">
    <div class="container">
      <header class="gateway-hero">
        <div>
          <span class="eyebrow">模型与 MCP 控制中心</span>
          <h1>你的模型连接，由你决定</h1>
          <p>在这里管理 API Key、模型连接与任务路由。明文密钥只在提交时出现一次，服务端只保存加密密文；BYOK 调用失败绝不静默改扣平台钱包。</p>
        </div>
      </header>

      <p v-if="error" class="page-error" role="alert">{{ error }}</p>

      <section class="gateway-card" aria-labelledby="credential-title">
        <header>
          <div>
            <span class="eyebrow">凭据保险库</span>
            <h2 id="credential-title">API Key 加密存放</h2>
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
            {{ credentialBusy ? '正在加密保存…' : '保存凭据' }}
          </button>
        </form>
        <ul v-if="credentials.length" class="credential-list" data-testid="credential-list">
          <li v-for="credential in credentials" :key="credential.id">
            <span class="credential-provider">{{ providerLabel(credential.provider) }}</span>
            <code>尾号 {{ credential.tail4 }}</code>
            <span class="credential-meta">{{ credential.algorithm }} · 版本 {{ credential.revision }}</span>
            <button class="btn btn-sm btn-outline" type="button"
                    :disabled="credentialBusy"
                    @click="removeCredential(credential.id)">删除</button>
          </li>
        </ul>
        <p v-else class="empty-state">还没有保存凭据。添加后，连接才能绑定它。</p>
      </section>

      <section class="gateway-card" aria-labelledby="connection-title">
        <header>
          <div>
            <span class="eyebrow">模型连接</span>
            <h2 id="connection-title">HTTPS 端点 + 平台白名单</h2>
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
            <span>绑定凭据（可选）</span>
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
                · {{ connection.enabled ? '已启用' : '已停用' }} · 版本 {{ connection.revision }}
              </span>
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
                {{ probingId === connection.id ? '探测中…' : '测试连接' }}
              </button>
              <button class="btn btn-sm btn-outline" type="button"
                      @click="rotateCredentialFor(connection)">轮换凭据</button>
              <button class="btn btn-sm btn-danger-outline" type="button"
                      @click="removeConnection(connection.id)">删除</button>
            </div>
          </li>
        </ul>
        <p v-else class="empty-state">还没有模型连接。添加连接后才能启用与测试。</p>

        <div v-if="rotating" class="rotate-panel" role="form" aria-label="轮换凭据">
          <p>为「{{ rotating.displayName }}」轮换凭据：新密钥加密保存后立即绑定，旧密钥不再使用。</p>
          <label>
            <span>新 API Key</span>
            <input v-model="rotateForm.apiKey" type="password" autocomplete="off" required>
          </label>
          <div class="rotate-actions">
            <button class="btn btn-sm" type="button" :disabled="connectionBusy"
                    @click="confirmRotate">确认轮换</button>
            <button class="btn btn-sm btn-outline" type="button" @click="rotating = null">取消</button>
          </div>
        </div>
      </section>

      <section class="gateway-card" aria-labelledby="routing-title">
        <header>
          <div>
            <span class="eyebrow">任务路由</span>
            <h2 id="routing-title">每个任务绑定一条连接</h2>
          </div>
        </header>
        <ul v-if="MODEL_TASKS.length" class="routing-list">
          <li v-for="task in MODEL_TASKS" :key="task.code" class="routing-row">
            <span class="routing-task">{{ task.label }}</span>
            <select :aria-label="`${task.label}路由`"
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
        <p class="routing-note">选择"平台默认"表示显式走平台钱包；一旦绑定用户连接，连接不可用时对话会直接报错，绝不静默回退扣费。</p>
      </section>

      <section class="gateway-card" aria-labelledby="usage-title">
        <header>
          <div>
            <span class="eyebrow">使用记录</span>
            <h2 id="usage-title">最近调用（只含安全字段）</h2>
          </div>
        </header>
        <table v-if="usage.length" class="usage-table" data-testid="usage-table">
          <thead>
            <tr>
              <th scope="col">时间</th>
              <th scope="col">任务</th>
              <th scope="col">供应商 / 模型</th>
              <th scope="col">费用来源</th>
              <th scope="col">结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in usage" :key="record.id">
              <td>{{ formatTime(record.createdTime) }}</td>
              <td>{{ taskLabel(record.task) }}</td>
              <td>{{ providerLabel(record.provider) }} / {{ record.modelCode }}</td>
              <td>{{ costSourceLabel(record.costSource) }}</td>
              <td>
                <span v-if="record.success" class="usage-success">成功</span>
                <span v-else class="usage-failure">{{ safeErrorLabel(record.safeErrorCode) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-state">还没有模型调用记录。测试连接或开启模型对话后会出现在这里。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
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
  createModelConnection,
  createModelCredential,
  deleteModelConnection,
  deleteModelCredential,
  deleteModelRouting,
  disableModelConnection,
  enableModelConnection,
  getModelConnectionCapability,
  listModelConnections,
  listModelCredentials,
  listModelRouting,
  listModelUsage,
  rotateModelCredential,
  testModelConnection,
  upsertModelRouting
} from '@/api/modelGateway'

const error = ref('')
const credentials = ref([])
const connections = ref([])
const routing = ref([])
const usage = ref([])
const credentialBusy = ref(false)
const connectionBusy = ref(false)
const routingBusy = ref(false)
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

onMounted(loadAll)

async function loadAll() {
  try {
    const [credentialList, connectionList, routingList, usageList] = await Promise.all([
      listModelCredentials(),
      listModelConnections(),
      listModelRouting(),
      listModelUsage()
    ])
    credentials.value = credentialList ?? []
    connections.value = connectionList ?? []
    routing.value = routingList ?? []
    usage.value = usageList ?? []
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '加载控制中心失败，请刷新重试')
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
    error.value = extractMessage(failure, '凭据保存失败')
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
    error.value = extractMessage(failure, '凭据删除失败')
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
    error.value = extractMessage(failure, '凭据轮换失败')
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
    error.value = extractMessage(failure, '路由保存失败')
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
    error.value = extractMessage(failure, '路由清除失败')
  } finally {
    routingBusy.value = false
  }
}

async function loadUsageOnly() {
  try {
    const response = await listModelUsage()
    usage.value = response ?? []
  } catch (failure) {
    error.value = extractMessage(failure, '使用记录加载失败')
  }
}

function probeLabel(result) {
  return result.reachable ? '探测通过' : `探测失败：${safeErrorLabel(result.safeErrorCode)}`
}

function formatTime(value) {
  if (!value) return ''
  return new Date(value).toLocaleString()
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
.eyebrow { color: #075d2a; font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.gateway-hero .eyebrow { color: #4aa16d; }
.page-error { padding: .7rem .9rem; border-left: 4px solid var(--red); background: var(--white); color: var(--red); font-size: .85rem; }
.gateway-card { border: 2px solid var(--black); background: var(--white); }
.gateway-card > header { padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.gateway-card h2 { font-size: 1.35rem; }
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
.btn-danger-outline { border-color: var(--red); color: var(--red); background: var(--white); }
.btn-danger-outline:hover { background: var(--red); color: var(--white); }
@media (max-width: 767px) {
  .connection-row, .routing-row { grid-template-columns: 1fr; }
  .connection-actions { justify-content: flex-start; }
}
</style>
