<template>
  <section class="quick" aria-labelledby="quick-title">
    <header class="quick-head">
      <h2 id="quick-title">AI 模型</h2>
      <p>直接选用已保存的连接，或选择厂商、填写 API Key 来连接新模型。</p>
    </header>

    <div class="quick-status" :data-status="status.tone" data-testid="quick-status">
      <template v-if="currentState === 'loading'">
        <span class="status-mark" aria-hidden="true">…</span>
        <div><strong>正在读取当前模型…</strong></div>
      </template>
      <template v-else-if="currentState === 'error'">
        <span class="status-mark" aria-hidden="true">!</span>
        <div>
          <strong>暂时读不到当前模型</strong>
          <p>{{ currentError }}</p>
        </div>
        <div class="status-actions"><LpButton variant="quiet" size="sm" @click="loadCurrent">重试</LpButton></div>
      </template>
      <template v-else>
        <span class="status-mark" aria-hidden="true">{{ status.tone === 'connected' ? '✓' : status.tone === 'broken' ? '!' : '○' }}</span>
        <div>
          <strong>{{ status.title }}</strong>
          <p>{{ status.detail }}</p>
        </div>
        <div v-if="current?.status !== 'PLATFORM'" class="status-actions">
          <LpButton v-if="current?.status === 'CONNECTED' && !editing" variant="quiet" size="sm" :disabled="busy" @click="startEditing">更换模型</LpButton>
          <LpButton variant="quiet" size="sm" :disabled="busy" @click="switchToPlatform">改用平台默认</LpButton>
        </div>
      </template>
    </div>

    <p v-if="notice" class="quick-notice" role="status">{{ notice }}</p>

    <section v-if="showWizard" class="saved-connections" aria-labelledby="saved-connections-title">
      <h3 id="saved-connections-title">已保存的连接</h3>
      <p v-if="savedState === 'loading'" class="step-hint">正在读取已保存的连接…</p>
      <div v-else-if="savedState === 'error'" class="step-error" role="alert">
        暂时读不到已保存的连接。
        <LpButton variant="quiet" size="sm" :disabled="busy" @click="loadSaved">重试读取连接</LpButton>
      </div>
      <template v-else-if="availableSaved.length">
        <p class="step-hint">无需重填 API Key。选择后会发送一次极短的测试对话，验证通过后用于伙伴聊天。</p>
        <ul class="saved-list">
          <li v-for="item in availableSaved" :key="item.id" class="saved-item">
            <div><strong>{{ item.displayName }}</strong><p class="step-hint">{{ item.modelCode }}</p></div>
            <LpButton :aria-label="`使用 ${item.displayName} 聊天`" :disabled="busy" @click="useSaved(item.id)">
              {{ phase === 'connecting' ? '正在验证…' : '验证并用于聊天' }}
            </LpButton>
          </li>
        </ul>
      </template>
      <p v-else class="step-hint">{{ savedConnections.length ? '其他已保存连接需要先在高级设置中启用并绑定 API Key。' : '还没有已保存的连接，可以在下方添加。' }}</p>
    </section>

    <ol v-if="showWizard" class="quick-steps">
      <li class="step" :class="{ done: provider }">
        <h3><span class="step-no">1</span>选择厂商</h3>
        <div class="provider-grid" role="group" aria-label="模型厂商">
          <button v-for="item in providers" :key="item.code" type="button" class="provider"
                  :aria-pressed="provider === item.code" :disabled="busy" @click="chooseProvider(item.code)">
            <span class="provider-mark" aria-hidden="true">{{ item.label.slice(0, 1) }}</span>
            <span class="provider-name">{{ item.label }}</span>
            <span class="provider-tagline">{{ item.tagline }}</span>
          </button>
        </div>
        <p v-if="providersError" class="step-error" role="alert">{{ providersError }}</p>
      </li>

      <li v-if="provider" class="step" :class="{ done: discovery }">
        <h3><span class="step-no">2</span>填写 API Key</h3>
        <form class="key-form" @submit.prevent="discover">
          <label class="visually-hidden" for="quick-api-key">{{ selectedProvider?.label }} API Key</label>
          <input id="quick-api-key" v-model="apiKey" type="password" autocomplete="off" spellcheck="false"
                 :placeholder="`粘贴 ${selectedProvider?.label} 的 API Key`" :disabled="busy" @input="discovery = null">
          <LpButton type="submit" :disabled="busy || !apiKey.trim()">
            {{ phase === 'discovering' ? '正在连接…' : discovery ? '重新获取' : '获取模型' }}
          </LpButton>
        </form>
        <p class="step-hint">
          Key 只用于连接，确认后加密保存，页面上只会显示尾号。
          <a v-if="selectedProvider?.keyUrl" :href="selectedProvider.keyUrl" target="_blank" rel="noopener noreferrer">去{{ selectedProvider.label }}创建 Key ↗</a>
        </p>
      </li>

      <li v-if="discovery" class="step">
        <h3><span class="step-no">3</span>选择模型</h3>
        <p v-if="!discovery.listed" class="step-hint">{{ discovery.providerLabel }} 没有提供模型目录，下面是常用模型。</p>
        <input v-if="discovery.models.length > 8" v-model="modelQuery" class="model-filter" type="search"
               aria-label="筛选模型" placeholder="筛选模型">
        <div class="model-list" role="radiogroup" aria-label="可用模型" data-testid="quick-models">
          <label v-for="model in visibleModels" :key="model" class="model" :class="{ selected: model === selectedModel }">
            <input v-model="selectedModel" type="radio" name="quick-model" :value="model" :disabled="busy">
            <span class="model-code">{{ model }}</span>
            <span v-if="model === discovery.recommendedModel" class="model-badge">推荐</span>
          </label>
          <p v-if="!visibleModels.length" class="step-hint">没有匹配的模型。</p>
        </div>
        <div class="connect-row">
          <LpButton :disabled="busy || !selectedModel" @click="connect">
            {{ phase === 'connecting' ? '正在验证模型…' : '完成连接' }}
          </LpButton>
          <span class="step-hint">会用这个模型发一次极短的测试对话，确认它能聊天。</span>
        </div>
      </li>
    </ol>

    <p v-if="error" class="quick-error" role="alert">{{ error }}</p>
    <div v-if="editing" class="quick-cancel">
      <LpButton variant="quiet" size="sm" :disabled="busy" @click="cancelEditing">取消更换</LpButton>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import LpButton from '@/components/ui/LpButton.vue'
import { MODEL_PROVIDERS } from '@/constants/modelGateway'
import {
  connectQuickModel,
  discoverQuickModels,
  getQuickConnection,
  listQuickProviders,
  listModelConnections,
  useSavedQuickConnection,
  useQuickPlatform
} from '@/api/modelGateway'
import { describeQuickConnection, filterModels, quickConnectError } from '@/presentation/modelQuickConnect'

const emit = defineEmits(['changed'])

const current = ref(null)
const currentState = ref('loading')
const currentError = ref('')
const providers = ref(MODEL_PROVIDERS)
const providersError = ref('')
const provider = ref(null)
// The key lives only in this component until the connection is confirmed.
const apiKey = ref('')
const discovery = ref(null)
const selectedModel = ref('')
const modelQuery = ref('')
const phase = ref('idle')
const error = ref('')
const notice = ref('')
const editing = ref(false)
const savedConnections = ref([])
const savedState = ref('loading')
const availableSaved = computed(() => savedConnections.value.filter(item =>
  item.enabled && item.credentialId && String(item.id) !== String(current.value?.connectionId)))

const busy = computed(() => phase.value !== 'idle')
const status = computed(() => describeQuickConnection(current.value))
const selectedProvider = computed(() => providers.value.find(item => item.code === provider.value) || null)
const visibleModels = computed(() => filterModels(discovery.value?.models, modelQuery.value))
const showWizard = computed(() => currentState.value === 'ready' && (current.value?.status !== 'CONNECTED' || editing.value))

onMounted(() => {
  loadCurrent()
  loadProviders()
  loadSaved()
})

async function loadSaved() {
  savedState.value = 'loading'
  try {
    savedConnections.value = await listModelConnections()
    savedState.value = 'ready'
  } catch {
    savedState.value = 'error'
  }
}

async function loadCurrent() {
  currentState.value = 'loading'
  currentError.value = ''
  try {
    current.value = await getQuickConnection()
    currentState.value = 'ready'
  } catch (failure) {
    currentError.value = quickConnectError(failure, '请稍后重试')
    currentState.value = 'error'
  }
}

async function loadProviders() {
  try {
    const open = new Set((await listQuickProviders() ?? []).map(item => item.provider))
    providers.value = MODEL_PROVIDERS.filter(item => open.has(item.code))
    if (!providers.value.length) providersError.value = '平台暂未开放任何厂商，请联系管理员。'
  } catch {
    // Keep the full list; the server still rejects providers it has not opened.
  }
}

function resetWizard() {
  provider.value = null
  apiKey.value = ''
  discovery.value = null
  selectedModel.value = ''
  modelQuery.value = ''
}

function chooseProvider(code) {
  if (provider.value === code) return
  provider.value = code
  apiKey.value = ''
  discovery.value = null
  selectedModel.value = ''
  error.value = ''
}

function startEditing() {
  editing.value = true
  notice.value = ''
}

function cancelEditing() {
  editing.value = false
  resetWizard()
  error.value = ''
}

async function discover() {
  if (busy.value || !provider.value || !apiKey.value.trim()) return
  phase.value = 'discovering'
  error.value = ''
  notice.value = ''
  try {
    const result = await discoverQuickModels(provider.value, apiKey.value.trim())
    discovery.value = result
    modelQuery.value = ''
    selectedModel.value = result.recommendedModel || result.models[0] || ''
  } catch (failure) {
    discovery.value = null
    error.value = quickConnectError(failure, '连接失败，请检查 API Key 后重试')
  } finally {
    phase.value = 'idle'
  }
}

async function connect() {
  if (busy.value || !discovery.value || !selectedModel.value) return
  phase.value = 'connecting'
  error.value = ''
  try {
    current.value = await connectQuickModel(provider.value, apiKey.value.trim(), selectedModel.value)
    currentState.value = 'ready'
    editing.value = false
    resetWizard()
    notice.value = '已连接。伙伴聊天已切换为使用这个模型。'
    loadSaved()
    emit('changed')
  } catch (failure) {
    error.value = quickConnectError(failure, '连接失败，请稍后重试')
  } finally {
    phase.value = 'idle'
  }
}

async function useSaved(id) {
  if (busy.value) return
  phase.value = 'connecting'
  error.value = ''
  notice.value = ''
  try {
    current.value = await useSavedQuickConnection(id)
    currentState.value = 'ready'
    editing.value = false
    resetWizard()
    notice.value = '已连接。伙伴聊天已切换为使用这个模型。'
    emit('changed')
  } catch (failure) {
    error.value = quickConnectError(failure, '验证失败，请检查已有连接后重试')
  } finally {
    phase.value = 'idle'
  }
}

async function switchToPlatform() {
  if (busy.value) return
  phase.value = 'connecting'
  error.value = ''
  try {
    current.value = await useQuickPlatform()
    editing.value = false
    resetWizard()
    notice.value = '已改用平台默认。之前的连接仍保留在高级设置里。'
    emit('changed')
  } catch (failure) {
    error.value = quickConnectError(failure, '操作失败，请稍后重试')
  } finally {
    phase.value = 'idle'
  }
}

defineExpose({ reload: () => { loadCurrent(); loadSaved() } })
</script>

<style scoped>
.quick { display: grid; gap: var(--lp-space-5); padding: var(--lp-space-6); border: 1px solid var(--lp-border); border-radius: var(--lp-radius-l); background: var(--lp-surface-elevated); box-shadow: var(--lp-shadow-s); }
.quick-head h2 { font-size: var(--lp-font-size-h2); line-height: var(--lp-line-height-h2); }
.quick-head p { margin-top: var(--lp-space-2); color: var(--lp-text-secondary); font-size: var(--lp-font-size-small); }
.saved-connections { display: grid; gap: var(--lp-space-3); }
.saved-list { display: grid; gap: var(--lp-space-2); padding: 0; list-style: none; }
.saved-item { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: var(--lp-space-3); padding: var(--lp-space-3); border: 1px solid var(--lp-border); border-radius: var(--lp-radius-s); }
.saved-item > div { min-width: 0; overflow-wrap: anywhere; }

.quick-status { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: var(--lp-space-4); align-items: center; padding: var(--lp-space-4) var(--lp-space-5); border-radius: var(--lp-radius-m); background: var(--lp-bg-subtle); }
.quick-status[data-status="connected"] { background: var(--lp-success-soft); }
.quick-status[data-status="broken"] { background: var(--lp-danger-soft); }
.quick-status strong { display: block; font-weight: var(--lp-font-weight-semibold); overflow-wrap: anywhere; }
.quick-status p { margin-top: var(--lp-space-1); color: var(--lp-text-secondary); font-size: var(--lp-font-size-small); }
.status-mark { display: grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: var(--lp-radius-full); background: var(--lp-surface-elevated); color: var(--lp-text-secondary); font-weight: var(--lp-font-weight-semibold); }
[data-status="connected"] .status-mark { color: var(--lp-success); }
[data-status="broken"] .status-mark { color: var(--lp-danger); }
.status-actions { display: flex; flex-wrap: wrap; gap: var(--lp-space-2); justify-content: flex-end; }

.quick-steps { display: grid; gap: var(--lp-space-5); list-style: none; padding: 0; }
.step { display: grid; gap: var(--lp-space-3); }
.step h3 { display: flex; align-items: center; gap: var(--lp-space-2); font-size: var(--lp-font-size-body); font-weight: var(--lp-font-weight-semibold); }
.step-no { display: grid; place-items: center; width: 1.5rem; height: 1.5rem; border-radius: var(--lp-radius-full); background: var(--lp-accent); color: var(--lp-on-accent); font-size: var(--lp-font-size-caption); }
.step.done .step-no { background: var(--lp-accent-soft); color: var(--lp-accent-strong); }
.step-hint { color: var(--lp-text-secondary); font-size: var(--lp-font-size-caption); line-height: 1.7; }
.step-hint a { margin-left: var(--lp-space-1); color: var(--lp-accent-strong); text-decoration: underline; text-underline-offset: 3px; }
.step-error, .quick-error { padding: var(--lp-space-3) var(--lp-space-4); border-radius: var(--lp-radius-s); background: var(--lp-danger-soft); color: var(--lp-danger); font-size: var(--lp-font-size-small); }
.quick-notice { padding: var(--lp-space-3) var(--lp-space-4); border-radius: var(--lp-radius-s); background: var(--lp-success-soft); color: var(--lp-success); font-size: var(--lp-font-size-small); }

.provider-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr)); gap: var(--lp-space-3); }
.provider { display: grid; grid-template-columns: auto minmax(0, 1fr); grid-template-rows: auto auto; column-gap: var(--lp-space-3); align-items: center; min-height: 64px; padding: var(--lp-space-3); border: 1px solid var(--lp-border); border-radius: var(--lp-radius-m); background: var(--lp-surface); text-align: left; cursor: pointer; transition: border-color var(--lp-dur-fast) var(--lp-ease-standard), background-color var(--lp-dur-fast) var(--lp-ease-standard); }
.provider:hover:not(:disabled) { border-color: var(--lp-accent); }
.provider[aria-pressed="true"] { border-color: var(--lp-accent); background: var(--lp-accent-soft); box-shadow: inset 0 0 0 1px var(--lp-accent); }
.provider:focus-visible { outline: 2px solid var(--lp-focus-ring, var(--lp-accent)); outline-offset: 2px; }
.provider:disabled { cursor: not-allowed; opacity: .6; }
.provider-mark { grid-row: 1 / 3; display: grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: var(--lp-radius-m); background: var(--lp-bg-subtle); color: var(--lp-accent-strong); font-weight: var(--lp-font-weight-semibold); }
.provider[aria-pressed="true"] .provider-mark { background: var(--lp-surface-elevated); }
.provider-name { font-weight: var(--lp-font-weight-semibold); font-size: var(--lp-font-size-small); }
.provider-tagline { color: var(--lp-text-secondary); font-size: var(--lp-font-size-caption); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.key-form { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--lp-space-3); }
.key-form input, .model-filter { min-width: 0; min-height: 44px; padding: 0 var(--lp-space-4); border: 1px solid var(--lp-border-strong); border-radius: var(--lp-radius-s); background: var(--lp-surface); font: inherit; }
.key-form input:focus-visible, .model-filter:focus-visible { outline: 2px solid var(--lp-focus-ring, var(--lp-accent)); outline-offset: 1px; }

.model-list { display: grid; gap: var(--lp-space-2); max-block-size: 18rem; overflow-y: auto; overscroll-behavior: contain; padding: var(--lp-space-1); }
.model { display: flex; align-items: center; gap: var(--lp-space-3); min-height: 44px; padding: 0 var(--lp-space-4); border: 1px solid var(--lp-border); border-radius: var(--lp-radius-s); cursor: pointer; }
.model.selected { border-color: var(--lp-accent); background: var(--lp-accent-soft); }
.model input { accent-color: var(--lp-accent); }
.model-code { flex: 1; min-width: 0; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: var(--lp-font-size-small); overflow-wrap: anywhere; }
.model-badge { padding: 2px var(--lp-space-2); border-radius: var(--lp-radius-full); background: var(--lp-accent); color: var(--lp-on-accent); font-size: var(--lp-font-size-caption); }
.connect-row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--lp-space-3); }
.quick-cancel { display: flex; justify-content: flex-end; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

@media (max-width: 640px) {
  .quick { padding: var(--lp-space-5) var(--lp-space-4); }
  .quick-status { grid-template-columns: auto minmax(0, 1fr); }
  .status-actions { grid-column: 1 / -1; justify-content: flex-start; }
  .key-form { grid-template-columns: 1fr; }
  .provider-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (prefers-reduced-motion: reduce) { .provider { transition: none; } }
</style>
