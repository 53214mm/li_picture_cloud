<template>
  <main class="rehearsal">
    <header class="intro">
      <span class="eyebrow">绫页 · 鲜活伙伴试演台</span>
      <h1>来，试试我什么时候接话</h1>
      <p>默认是确定性模拟，不是 AI 生成。也可单独选择真实模型，用自己的 BYOK 连接手动试演。</p>
      <router-link to="/companion">回到伙伴与普通聊天 →</router-link>
    </header>
    <section v-if="!user.currentUser?.id" class="card" role="status">
      <h2>请先确认登录状态</h2><p>登录后才可开启试演，当前没有采集或处理事件。</p>
      <router-link to="/login?redirect=/companion/rehearsal">前往登录</router-link>
    </section>
    <template v-else>
      <section class="card controls" aria-labelledby="controls-title">
        <h2 id="controls-title">由你决定什么时候开场</h2>
        <label><input type="checkbox" :checked="state.enabled" :disabled="transportMode === 'BYOK' && !realReady" @change="configure({ enabled: $event.target.checked })" /> {{ transportMode === 'BYOK' ? '开启本页真实模型试演（可能产生 BYOK 费用）' : '开启本页模拟试演（默认关闭）' }}</label>
        <div class="control-row">
          <label><input type="checkbox" :checked="state.paused" @change="configure({ paused: $event.target.checked })" /> 暂停</label>
          <label><input type="checkbox" :checked="state.muted" @change="configure({ muted: $event.target.checked })" /> 静音短句</label>
          <label>主动程度 <select :value="state.intensity" @change="configure({ intensity: $event.target.value })"><option value="LOW">低</option><option value="NORMAL">普通</option></select></label>
          <label>本页提交次数上限 <input class="limit" type="number" min="1" max="12" :value="state.limit" @change="configure({ limit: Number($event.target.value) })" /></label>
        </div>
        <p>已提交 {{ state.used }} / {{ state.limit }} 次 · 本地间隔至少 5 秒；服务端低 / 普通为 45 / 15 秒 · 关闭或离开不影响普通聊天</p>
        <fieldset><legend>主动回应模式（与普通聊天设置独立）</legend>
          <label><input type="radio" name="presence-mode" :checked="transportMode === 'LOCAL'" @change="changeTransport('LOCAL')" /> 本地确定性模拟 · 无模型费用</label>
          <label><input type="radio" name="presence-mode" :checked="transportMode === 'SERVER'" @change="changeTransport('SERVER')" /> 服务端演示 · 非真实模型</label>
          <label><input type="radio" name="presence-mode" :checked="transportMode === 'BYOK'" :disabled="!realReady" @change="changeTransport('BYOK')" /> 真实模型 · BYOK（可能收费）</label>
          <p>切换模式会关闭试演，需要重新开启并手动点选场景。本地模拟不联网；服务端模式仅提交下方白名单事件。真实模型使用模型设置中当前的语言代理（LANGUAGE_AGENT）连接，不自动改用平台额度。本页不读取密钥，也不改变普通聊天模式或全站浮层。</p>
          <div class="model-status" data-testid="presence-status" aria-live="polite">
            <p v-if="statusLoading">正在读取真实模型状态…</p>
            <p v-else-if="statusError" role="alert">{{ statusError }}</p>
            <p v-else>{{ describeStatus(modelStatus?.reason) }}</p>
            <p v-if="modelStatus?.connection">当前连接：{{ modelStatus.connection.provider }} · {{ modelStatus.connection.modelCode }}</p>
            <button type="button" :disabled="statusLoading" @click="refreshStatus">刷新模型状态</button>
            <router-link to="/model-gateway">前往模型设置 →</router-link>
            <p>状态查询只读，不会测试连接、开启会话或调用模型。真实试演每次手动提交都可能消耗 BYOK 用量，取消也可能已产生费用；静音仅隐藏短句，仍可能收费。服务端另有每日预算保护。</p>
          </div>
        </fieldset>
      </section>
      <div class="workspace">
        <section class="card stage" aria-labelledby="stage-title">
          <h2 id="stage-title">四个小场景</h2>
          <CompanionPortrait :presentation="presentation" />
          <p class="stage-note">沿用静态形象 · 动作先用文字标记</p>
          <div class="scenarios"><button v-for="scenario in presenceScenarios" :key="scenario.kind" type="button" :disabled="!state.enabled || state.paused || state.busy || state.userBusy" @click="run(scenario.kind)">{{ scenario.label }}</button></div>
          <div class="result" aria-live="polite" role="status">
            <template v-if="state.result"><strong>{{ state.result.action }}</strong><span> · {{ state.result.source === 'SERVER_BYOK' ? '真实模型 · BYOK' : state.result.source === 'SERVER_DEMO' ? '服务端演示' : '本地固定规则' }}</span><p>{{ state.result.text || '这会儿先不插话。' }}</p>
              <p v-if="['SERVER_DEMO', 'SERVER_BYOK'].includes(state.result.source)">服务端剩余 {{ state.result.remainingDecisions }} 次决策 · 到期 {{ formatExpiry(state.result.expiresAt) }}<br />{{ describeReason(state.result.reason) }}</p>
              <button v-if="state.result.action === 'SPEAK'" type="button" @click="openReply">回复绫页 · 打开普通聊天</button>
            </template>
            <p v-else>{{ state.busy ? '正在处理试演事件…' : state.enabled ? '选一个场景，看我如何回应。' : '试演尚未开启。' }}</p>
            <p v-if="state.reason">{{ state.reason }}</p>
          </div>
        </section>
        <section class="card inspector" aria-labelledby="input-title">
          <h2 id="input-title">脱敏事件检查器</h2>
          <p>这里只展示本页手动生成并净化后的事件编号、场景、页面枚举和时间。服务端提交时会转成协议字段、UTC 时间，并附临时会话编号与递增序号。不读取图片、网址、DOM、输入框或聊天内容。</p>
          <pre data-testid="presence-input">{{ state.input ? JSON.stringify(state.input, null, 2) : '尚无输入' }}</pre>
          <p>不保存到浏览器存储。账号切换或退出登录会清空试演状态；页面隐藏会取消接收并丢弃迟到回应。</p>
          <p>试演回应只展示在这里，不写入聊天历史。本地与服务端演示的固定台词不代表真实模型的人格验收。</p>
        </section>
      </div>
      <section class="card"><h2>普通聊天一直都在</h2><p>试演与聊天独立。发送消息仍走原有聊天服务，可能按你的普通聊天模式消耗额度。</p><button type="button" @click="openReply">{{ chatOpen ? '回到聊天输入框' : '打开普通聊天' }}</button></section>
      <section class="card" aria-labelledby="clear-title" data-testid="clear-chat">
        <h2 id="clear-title">从干净的对话开始</h2>
        <p>旧的聊天记录会作为上下文发给模型，旧版口吻容易把她带偏。清空后删除你和绫页的聊天消息，以及从聊天里整理出的经历；图片记忆、成长和用量记录都不受影响，且无法恢复。</p>
        <div v-if="!confirmingClear"><button type="button" :disabled="clearing || chat.state?.phase !== 'idle'" @click="confirmingClear = true">清空对话记录</button></div>
        <div v-else class="control-row"><strong>确定清空全部聊天记录？</strong>
          <button type="button" class="danger" :disabled="clearing" @click="clearHistory">{{ clearing ? '正在清空…' : '确定清空' }}</button>
          <button type="button" :disabled="clearing" @click="confirmingClear = false">取消</button></div>
        <p v-if="clearMessage" role="status">{{ clearMessage }}</p>
      </section>
      <div v-if="chatOpen" ref="chatHost"><button type="button" @click="closeReply">收起聊天并继续试演</button><p v-if="!chatMode && !chatError">正在读取聊天模式…</p><CompanionChatPanel v-if="chatMode" :key="String(user.currentUser.id)" :presentation="presentation" :chat-mode="chatMode" compact /></div>
      <p v-if="chatError" role="alert">{{ chatError }}</p>
    </template>
  </main>
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useCompanionChatStore } from '@/stores/companionChat'
import { getCompanionChatMode } from '@/api/companion'
import CompanionPortrait from '@/components/companion/body/CompanionPortrait.vue'
import CompanionChatPanel from '@/components/companion/CompanionChatPanel.vue'
import { mapCompanionPresentation } from '@/presentation/companionPresentation'
import { createPresenceSession, presenceScenarios, simulatePresence } from '@/presentation/companionPresence'
import { createPresenceTransport } from '@/presentation/companionPresenceTransport'
import { presenceApi } from '@/api/companionPresence'
const user = useUserStore()
const chat = useCompanionChatStore()
const state = shallowRef(null)
const transportMode = ref('LOCAL')
const transport = createPresenceTransport(presenceApi)
const realTransport = createPresenceTransport(presenceApi, { mode: 'BYOK' })
const modelStatus = shallowRef(null)
const statusLoading = ref(false)
const statusError = ref('')
const realReady = computed(() => !statusLoading.value && !statusError.value && modelStatus.value?.enabled === true && modelStatus.value?.realEnabled === true && modelStatus.value?.available === true && modelStatus.value?.reason === 'READY' && !!modelStatus.value?.connection)
const session = createPresenceSession({
  decide: (input, options) => {
    if (transportMode.value === 'BYOK') {
      if (!realReady.value) throw new Error('Presence model unavailable')
      return realTransport.decide(input, { ...options, limit: state.value.limit })
    }
    return transportMode.value === 'SERVER' ? transport.decide(input, { ...options, limit: state.value.limit }) : simulatePresence(input, options)
  },
  onChange: value => { state.value = value }
})
const presentation = mapCompanionPresentation()
const chatOpen = ref(false)
const chatHost = ref(null)
const chatMode = shallowRef(null)
const chatError = ref('')
const confirmingClear = ref(false)
const clearing = ref(false)
const clearMessage = ref('')
let eventSequence = 0; let ownerGeneration = 0; let statusGeneration = 0; let statusController
function formatExpiry(value) { const date = new Date(value); return Number.isNaN(date.getTime()) ? '未知' : date.toLocaleTimeString() }
function describeReason(reason) { return ({ DEMO: '固定演示规则，不是模型生成', DECIDED: '真实模型已完成本次决策', BYOK_REQUIRED: '当前语言代理 BYOK 连接不可用，请检查模型设置', COOLDOWN: '服务端冷却中，未生成新回应', SESSION_BUDGET: '服务端本次预算已用完', SESSION_EXPIRED: '服务端会话已到期，请关闭后重新开启', STALE_EVENT: '事件已过期', DUPLICATE_EVENT: '重复事件已忽略', USER_MESSAGE_PRIORITY: '优先等待用户聊天', UNAVAILABLE: '服务暂不可用，可能是连接、预算或模型响应问题；未自动重试', MUTED: '已静音', BUSY: '正在处理前一个事件', SUPERSEDED: '已被新事件替代', EVENT_LIMIT: '事件上限已到' })[reason] || '服务端未给出可显示的原因' }
function describeStatus(reason) { return ({ READY: '真实模型连接已配置，仍需你选择、开启并手动提交场景；尚未验证密钥、网络或模型健康。', DISABLED: '服务端主动回应未启用，本地模拟仍可使用。', REAL_DISABLED: '服务端尚未开放真实模型试演。', BYOK_REQUIRED: '请先在模型设置中配置并启用语言代理 BYOK 连接。', UNAVAILABLE: '暂时无法确认真实模型连接，请稍后刷新。' })[reason] || '尚未读取真实模型状态。' }
function stopTransport() { for (const current of [transport, realTransport]) current.stop().catch(() => { /* Server sessions expire after 15 minutes; no automatic decisions run. */ }) }
function configure(patch) { session.configure({ ...patch, ...(patch.enabled && (!user.currentUser?.id || transportMode.value === 'BYOK' && !realReady.value) ? { enabled: false } : {}) }); stopTransport() }
function changeTransport(mode) {
  if (!['LOCAL', 'SERVER', 'BYOK'].includes(mode) || mode === 'BYOK' && !realReady.value) return
  configure({ enabled: false }); transportMode.value = mode
}
function cancelStatus() { statusGeneration++; statusController?.abort(); statusController = null; statusLoading.value = false; modelStatus.value = null; statusError.value = '' }
async function refreshStatus() {
  cancelStatus()
  if (!user.currentUser?.id) return
  // Refresh can reflect a different selected connection. Never preserve paid opt-in.
  if (transportMode.value === 'BYOK') configure({ enabled: false })
  const generation = statusGeneration
  const controller = new AbortController(); statusController = controller; statusLoading.value = true
  try {
    const value = await presenceApi.status({ signal: controller.signal })
    if (generation !== statusGeneration || controller.signal.aborted) return
    const connection = value?.connection
    const validConnection = connection == null || ['provider', 'modelCode'].every(key => typeof connection[key] === 'string' && connection[key].trim() && connection[key].length <= 160)
    if (!value || !['enabled', 'realEnabled', 'available'].every(key => typeof value[key] === 'boolean') || !['READY', 'DISABLED', 'REAL_DISABLED', 'BYOK_REQUIRED', 'UNAVAILABLE'].includes(value.reason) || !validConnection || value.reason === 'READY' && (!value.enabled || !value.realEnabled || !value.available || !connection)) throw new Error('Invalid presence status')
    modelStatus.value = { enabled: value.enabled, realEnabled: value.realEnabled, available: value.available, reason: value.reason,
      connection: connection ? { provider: connection.provider, modelCode: connection.modelCode } : null }
  } catch {
    if (generation === statusGeneration && !controller.signal.aborted) statusError.value = '暂时无法读取真实模型状态，请刷新后重试。'
  } finally {
    if (generation === statusGeneration) { statusLoading.value = false; statusController = null }
  }
}
function closeReply() { chatOpen.value = false; configure({ userBusy: chat.state?.phase !== 'idle' }) }
function run(kind) { session.run({ id: `rehearsal-${++eventSequence}`, kind, occurredAt: Date.now() }) }
async function clearHistory() {
  const generation = ownerGeneration
  clearing.value = true
  clearMessage.value = ''
  try {
    const deleted = await chat.clearHistory()
    if (generation !== ownerGeneration) return
    clearMessage.value = deleted == null ? '绫页还在回复，等她说完再清空。' : `已清空 ${deleted} 条聊天记录。`
    confirmingClear.value = false
  } catch { if (generation === ownerGeneration) clearMessage.value = '清空失败，请稍后重试。' }
  finally { if (generation === ownerGeneration) clearing.value = false }
}
async function openReply() {
  configure({ userBusy: true })
  const generation = ownerGeneration
  chatError.value = ''
  chatOpen.value = true
  if (!chatMode.value) {
    try { const mode = await getCompanionChatMode(); if (generation === ownerGeneration) chatMode.value = mode }
    catch { if (generation === ownerGeneration) chatError.value = '暂时无法读取聊天模式，请回伙伴页确认后再发送。' }
  }
  await nextTick()
  if (generation === ownerGeneration) chatHost.value?.querySelector('input[type="text"]')?.focus()
}
watch(() => chat.state?.phase, phase => configure({ userBusy: chatOpen.value || phase !== 'idle' }), { flush: 'sync', immediate: true })
watch(() => user.currentUser?.id, () => {
  ownerGeneration++; session.reset(); session.configure({ userBusy: chat.state?.phase !== 'idle', visible: document.visibilityState === 'visible' }); stopTransport(); transportMode.value = 'LOCAL'; chatOpen.value = false; chatMode.value = null; chatError.value = ''
  confirmingClear.value = false; clearing.value = false; clearMessage.value = ''; refreshStatus()
}, { flush: 'sync', immediate: true })
function visibility() { configure({ visible: document.visibilityState === 'visible' }) }
onMounted(() => { visibility(); document.addEventListener('visibilitychange', visibility) })
onBeforeUnmount(() => { ownerGeneration++; cancelStatus(); session.reset(); stopTransport(); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.rehearsal { max-width: 1040px; margin: 0 auto; padding: 24px; color: #37463b; }
.model-status { overflow-wrap: anywhere; }.model-status a { display: inline-block; margin: 8px; }fieldset { min-width: 0; }
.intro { margin-bottom: 24px; }.eyebrow { color: #68765c; font-size: .8rem; }h1 { font-size: clamp(1.5rem, 4vw, 2.2rem); margin: .6rem 0; }h2 { font-size: 1.15rem; margin: 0 0 1rem; }p { line-height: 1.7; margin: .6rem 0; }a { color: #426c4a; }
.card { padding: 24px; border: 1px solid #dce1d2; border-radius: 18px; background: #fffdf7; margin-bottom: 20px; min-width: 0; }.workspace { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }.control-row,.scenarios { display: flex; gap: 12px; flex-wrap: wrap; margin: 16px 0; }label { display: inline-flex; gap: 8px; align-items: center; min-height: 44px; flex-wrap: wrap; }fieldset { border: 1px solid #dce1d2; border-radius: 10px; margin-top: 16px; }fieldset label { margin-right: 16px; }button, select, input[type=number] { min-height: 44px; border: 1px solid #87977f; border-radius: 8px; padding: 8px 12px; background: #fffefa; color: #37463b; font: inherit; }button { cursor: pointer; }button:hover:not(:disabled) { background: #edf0e3; }button:disabled { opacity: .5; cursor: not-allowed; }button.danger { border-color: #a6513d; color: #8a3b2a; }.limit { width: 5rem; }.stage-note { color: #68765c; font-size: .8rem; }.result { background: #eff2e8; border-radius: 12px; padding: 16px; min-height: 100px; }pre { white-space: pre-wrap; overflow-wrap: anywhere; background: #eff2e8; padding: 16px; border-radius: 10px; font-size: .8rem; }.inspector p,fieldset p { font-size: .85rem; }button:focus-visible,a:focus-visible,input:focus-visible,select:focus-visible { outline: 3px solid #617c56; outline-offset: 3px; }@media(max-width: 767px) { .rehearsal { padding: 12px; }.workspace { grid-template-columns: 1fr; gap: 0; }.card { padding: 18px; } }
</style>
