<template>
  <main class="rehearsal">
    <header class="intro">
      <span class="eyebrow">绫页 · 鲜活伙伴试演台</span>
      <h1>独立观察与协议调试</h1>
      <p>独立观察默认关闭；下方四场景保留为事件协议模拟 / 手动调试，不代表自动观察架构。下方确定性模拟，不是 AI 生成。</p>
      <router-link to="/companion">回到伙伴与普通聊天 →</router-link>
    </header>
    <section v-if="!user.currentUser?.id" class="card" role="status">
      <h2>请先确认登录状态</h2><p>登录后才可开启试演，当前没有采集或处理事件。</p>
      <router-link to="/login?redirect=/companion/rehearsal">前往登录</router-link>
    </section>
    <template v-else>
      <CompanionObservationPanel />
      <section class="card controls" aria-labelledby="controls-title">
        <h2 id="controls-title">由你决定什么时候开场</h2>
        <label><input type="checkbox" :checked="state.enabled" :disabled="transportMode === 'BYOK' && !realReady" @change="configure({ enabled: $event.target.checked })" /> {{ transportMode === 'BYOK' ? '开启本页真实模型试演（可能产生 BYOK 费用）' : '开启本页模拟试演（默认关闭）' }}</label>
        <div class="control-row">
          <label><input type="checkbox" :checked="state.paused" @change="configure({ paused: $event.target.checked })" /> 暂停</label>
          <label><input type="checkbox" :checked="state.muted" @change="configure({ muted: $event.target.checked })" /> 隐藏文字气泡</label>
          <label>主动程度 <select :value="state.intensity" @change="configure({ intensity: $event.target.value })"><option value="LOW">低</option><option value="NORMAL">普通</option></select></label>
          <label>本轮尝试上限（1–12，非每日上限） <input class="limit" type="number" min="1" max="12" :value="state.limit" @change="changeLimit($event)" /></label>
        <label>手动试演冷却（2–30 秒）<input type="number" min="2" max="30" :value="state.cooldownSeconds" @change="changeCooldown($event)" /></label>
        </div>
        <p v-if="parameterError" role="alert">{{ parameterError }}</p>
        <p>本轮已占用 {{ state.used }} / {{ state.limit }} 次尝试 · 手动冷却 {{ state.cooldownSeconds }} 秒（默认 5 秒）</p>
        <p data-testid="rehearsal-daily-budget">今日手动 BYOK 总剩余：{{ dailyBudget.remaining == null ? '未能确认，请刷新状态' : dailyBudget.remaining }} / {{ dailyBudget.limit ?? '待读取' }} 次（最近服务端读数，上海日期）。每日总上限由服务端配置，本轮始终为 1–12 次；次数不代表金额上限。</p>
        <button type="button" :disabled="state.busy" @click="newRound">新开本轮（保留试演记录，不清日额度）</button>
        <p>离开或新开本轮会关闭试演，需要再次手动开启；不会自动提交。</p>
        <fieldset><legend>主动回应模式（与普通聊天设置独立）</legend>
          <label><input type="radio" name="presence-mode" :checked="transportMode === 'LOCAL'" @change="changeTransport('LOCAL')" /> 本地确定性模拟 · 无模型费用</label>
          <label><input type="radio" name="presence-mode" :checked="transportMode === 'SERVER'" :disabled="modelStatus?.rehearsalEnabled === false" @change="changeTransport('SERVER')" /> 服务端演示 · 非真实模型</label>
          <label><input type="radio" name="presence-mode" :checked="transportMode === 'BYOK'" :disabled="!realReady" @change="changeTransport('BYOK')" /> 真实模型 · BYOK（可能收费）</label>
          <p>切换模式会关闭试演，需要重新开启并手动点选场景。本地模拟不联网；服务端模式仅提交下方白名单事件。真实模型使用模型设置中当前的语言代理（LANGUAGE_AGENT）连接，不自动改用平台额度。本页不读取密钥，也不改变普通聊天模式或全站浮层。</p>
          <div class="model-status" data-testid="presence-status" aria-live="polite">
            <p v-if="statusLoading">正在读取真实模型状态…</p>
            <p v-else-if="statusError" role="alert">{{ statusError }}</p>
            <p v-else>{{ describeStatus(modelStatus?.reason) }}</p>
            <p v-if="modelStatus?.rehearsalEnabled === false">服务端手动试演未开放；本地模拟仍可使用。</p>
            <p v-if="modelStatus?.connection">当前连接：{{ modelStatus.connection.provider }} · {{ modelStatus.connection.modelCode }}</p>
            <button type="button" :disabled="statusLoading" @click="refreshStatus">刷新模型状态</button>
            <router-link to="/model-gateway">前往模型设置 →</router-link>
            <p>状态查询只读，不会测试连接、开启会话或调用模型。真实试演每次手动提交都可能消耗 BYOK 用量，取消也可能已产生费用；隐藏文字气泡只隐藏 SPEAK 展示，不停止调用或费用。服务端另有每日预算保护。</p>
          </div>
        </fieldset>
      </section>
      <div class="workspace">
        <section class="card stage" aria-labelledby="stage-title">
          <h2 id="stage-title">四场景 · 事件协议调试</h2>
          <CompanionPortrait :presentation="presentation" />
          <p class="stage-note">沿用静态形象 · 动作先用文字标记</p>
          <p>四项仅模拟事件类型与页面枚举：没有图片像素、图片编号或名称，没有真实上传文件，没有聊天正文、用户经历或情绪。下面的说明是场景预览；每条历史以展开后的本次请求快照为准。</p>
          <ul data-testid="scenario-guide"><li v-for="scenario in presenceScenarios" :key="scenario.kind"><strong>{{ scenario.label }}</strong>：{{ scenarioDescription(scenario.kind) }}<code>eventType={{ scenario.eventType }};page={{ scenario.page }}</code></li></ul>
          <p>真实模式四项共用同一人格和 JSON 决策规则，提示明确“沉默通常更合适”。主动程度仅影响固定演示；真实模型不会收到该选项，手动冷却另行设置。SILENT 是合法决定，不是调用失败。</p>
          <div class="scenarios"><button v-for="scenario in presenceScenarios" :key="scenario.kind" type="button" :disabled="!state.enabled || state.paused || state.busy || state.userBusy || cooldownRemaining > 0" @click="run(scenario.kind)">{{ scenario.label }}</button></div>
          <p data-testid="rehearsal-cooldown">{{ cooldownRemaining > 0 ? `冷却中：${cooldownRemaining} 秒后可手动提交` : '冷却已结束，可手动提交' }}</p>
          <div class="result" aria-live="polite" role="status">
            <template v-if="state.result"><strong>{{ resultView.label }}</strong><span> · {{ resultView.sourceLabel }}</span><p>{{ resultView.text }}</p>
              <p v-if="['SERVER_DEMO', 'SERVER_BYOK'].includes(state.result.source)">服务端剩余 {{ state.result.remainingDecisions }} 次决策 · 到期 {{ formatExpiry(state.result.expiresAt) }}<br />{{ describeReason(state.result.reason) }}</p>
              <button v-if="resultView.canReply" type="button" @click="openReply">回复绫页 · 打开普通聊天</button>
            </template>
            <p v-else>{{ state.busy ? '正在处理试演事件…' : state.enabled ? '选一个场景，看我如何回应。' : '试演尚未开启。' }}</p>
            <p v-if="state.reason">{{ state.reason }}</p>
          </div>
        </section>
        <section class="card inspector" aria-labelledby="input-title">
          <h2 id="input-title">脱敏事件检查器</h2>
          <p>这里只展示本页手动生成并净化后的事件编号、场景、页面枚举和时间。服务端提交时会转成协议字段、UTC 时间，并附临时会话编号与递增序号。不读取图片、网址、DOM、输入框或聊天内容。</p>
          <pre data-testid="presence-input">{{ state.input ? JSON.stringify(state.input, null, 2) : '尚无输入' }}</pre>
          <p>仅在当前账号的本次应用内存中保留最多 50 条试演记录，跨页可查看；刷新浏览器、退出或切号清空。离开会取消接收并丢弃迟到回应，返回不续跑。</p>
          <p>试演回应只展示在这里，不写入聊天历史。本地与服务端演示的固定台词不代表真实模型的人格验收。</p>
        </section>
      </div>
      <section class="card" data-testid="rehearsal-history"><h2>本轮试演记录（跨页保留，最多 50 条）</h2>
        <button type="button" @click="clearRecords">清空本轮试演记录</button>
        <p>此按钮只清试演内存，不删除普通聊天、图片记忆或用量；过期记录仍可查看，不继续旧请求。</p>
        <p v-if="!history.records.length">尚无试演记录。</p>
        <p v-else>共 {{ history.records.length }} 条：{{ historySummary }}</p>
        <ol><li v-for="record in [...history.records].reverse()" :key="record.id" :data-category="record.category">
          <strong>{{ recordLabel(record) }}</strong> · {{ record.mode }} · {{ record.kind }} · {{ new Date(record.at).toLocaleTimeString() }}
          <p>{{ record.text || record.notice || describeReason(record.reason) }}{{ record.muted ? '（文字气泡被隐藏，原决策仍为 SPEAK）' : '' }}</p>
          <p>阶段：{{ record.stage }} · 模型调用：{{ record.modelDispatched === true ? '已派发' : record.modelDispatched === false ? '未派发' : '尚未确认' }}<template v-if="record.elapsedMs != null"> · {{ record.elapsedMs }} ms · 正文字数 {{ record.outputChars }}</template></p>
          <details data-testid="rehearsal-trace"><summary>查看本次请求与模型返回</summary>
            <h3>本次请求快照</h3><pre>{{ record.protocolSnapshot ? JSON.stringify(record.protocolSnapshot, null, 2) : record.inputSnapshot ? JSON.stringify(record.inputSnapshot, null, 2) : '旧记录没有请求快照，无法补回。' }}</pre>
            <p>点击时的本页设置（不代表全都发送给模型）：</p><pre>{{ record.settingsSnapshot ? JSON.stringify(record.settingsSnapshot, null, 2) : '旧记录没有设置快照，无法补回。' }}</pre>
            <template v-if="record.trace">
              <h3>实际组装的模型 messages</h3><pre>{{ record.trace.messages ? JSON.stringify(record.trace.messages, null, 2) : '此请求未组装模型 messages。' }}</pre>
              <h3>非敏感生成参数</h3><pre>{{ record.trace.parameters ? JSON.stringify(record.trace.parameters, null, 2) : '未发送模型生成请求。' }}</pre>
              <p>NOT_SENT 表示该选项没有发送，上游默认值未知；不显示凭据、Authorization、端点、内部账户或模型推理内容。</p>
              <h3>模型原始正文（仅 content）</h3><p v-if="record.trace.rawBody === null">未开始收集正文。</p><p v-else-if="record.trace.rawBody === ''">空正文（0 字），没有模板补写。</p><pre v-else>{{ record.trace.rawBody }}</pre>
              <p>正文状态：{{ record.trace.rawBodyComplete ? '已完成收集' : '未完整完成，只能作为已收到的部分正文' }}{{ record.trace.rawBodyTruncated ? '；已达到 1024 字符上限，显示有界前缀' : '' }}{{ record.trace.redactedFields.length ? '；凭据值已遮盖，转义格式可能因此改变' : '' }}。推理内容始终不收集、不展示。</p>
              <h3>解析后的模型决定</h3><pre>{{ record.trace.parsedDecision ? JSON.stringify(record.trace.parsedDecision, null, 2) : '没有可用解析决定；失败或门控产生的 SILENT 不冒充模型决定。' }}</pre>
              <h3>安全流元信息</h3><pre>{{ record.trace.stream ? JSON.stringify(record.trace.stream, null, 2) : '当前适配器未提供流元信息。' }}</pre>
              <p v-if="record.trace.stream?.finishReason === 'length'">上游报告 length：生成达到长度预算。若仅有推理字段且没有正文，这是可解释空正文的证据，不代表我们知道推理内容。</p>
            </template>
            <p v-else-if="record.mode !== 'BYOK'">固定演示，没有真实模型 messages 或原始返回；演示台词不冒充模型正文。</p>
            <p v-else-if="record.category === 'NOT_REQUESTED' || record.category === 'CANCELLED'">此条未请求或已取消接收，没有可确认的模型追踪；不会补发获取。</p>
            <p v-else>此记录未返回追踪信息，历史模型输入与原始正文无法补回；不会用当前页面设置代替。</p>
            <h3>最终呈现与抑制原因</h3><pre>{{ record.presentationSnapshot ? JSON.stringify(record.presentationSnapshot, null, 2) : record.notice || '尚未取得最终呈现快照。' }}</pre>
          </details>
          <p v-if="record.expiresAt && new Date(record.expiresAt).getTime() <= tick">会话已到期，仅保留记录。</p>
        </li></ol>
      </section>
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
import { useCompanionRehearsalStore } from '@/stores/companionRehearsal'
import { useCompanionChatStore } from '@/stores/companionChat'
import { getCompanionChatMode } from '@/api/companion'
import CompanionPortrait from '@/components/companion/body/CompanionPortrait.vue'
import CompanionChatPanel from '@/components/companion/CompanionChatPanel.vue'
import CompanionObservationPanel from '@/components/companion/CompanionObservationPanel.vue'
import { mapCompanionPresentation } from '@/presentation/companionPresentation'
import { createPresenceSession, presenceScenarios, simulatePresence, presentPresenceResult, describePresenceReason } from '@/presentation/companionPresence'
import { createPresenceTransport } from '@/presentation/companionPresenceTransport'
import { presenceApi } from '@/api/companionPresence'
const user = useUserStore()
const chat = useCompanionChatStore()
const history = useCompanionRehearsalStore()
const tick = ref(Date.now())
const parameterError = ref('')
const budgetSnapshot = shallowRef(null)
let activeRecord = null; let timer; let statusReadAt = 0
const state = shallowRef(null)
const resultView = computed(() => presentPresenceResult(state.value?.result))
const transportMode = ref('LOCAL')
const transport = createPresenceTransport(presenceApi, { scope: 'REHEARSAL' })
const realTransport = createPresenceTransport(presenceApi, { mode: 'BYOK', scope: 'REHEARSAL' })
const modelStatus = shallowRef(null)
const statusLoading = ref(false)
const statusError = ref('')
const realReady = computed(() => !statusLoading.value && !statusError.value && modelStatus.value?.rehearsalEnabled !== false && modelStatus.value?.enabled === true && modelStatus.value?.realEnabled === true && modelStatus.value?.available === true && modelStatus.value?.reason === 'READY' && !!modelStatus.value?.connection)
const session = createPresenceSession({
  manualDebug: true,
  decide: (input, options) => {
    if (transportMode.value === 'BYOK') {
      if (!realReady.value) throw new Error('Presence model unavailable')
      return realTransport.decide(input, { ...options, limit: state.value.limit, onPhase: (stage, request) => history.journal.phase(activeRecord, stage, request) })
    }
    if (transportMode.value === 'SERVER' && modelStatus.value?.rehearsalEnabled === false) throw new Error('Rehearsal disabled')
    return transportMode.value === 'SERVER' ? transport.decide(input, { ...options, limit: state.value.limit, onPhase: (stage, request) => history.journal.phase(activeRecord, stage, request) }) : simulatePresence(input, options)
  },
  onChange: value => { state.value = value }
})
const unbindCancel = history.journal.bindCancel(revoke => { session.cancel(revoke ? { enabled: false } : {}); stopTransport() })
const cooldownRemaining = computed(() => Math.max(0, Math.ceil((state.value.nextAllowedAt - tick.value) / 1000)))
const dailyBudget = computed(() => budgetSnapshot.value || { limit: modelStatus.value?.rehearsalDailyLimit, remaining: modelStatus.value?.rehearsalDailyRemaining })
const historySummary = computed(() => Object.entries(history.records.reduce((totals, row) => { const label = recordLabel(row); totals[label] = (totals[label] || 0) + 1; return totals }, {})).map(([label, count]) => `${label} ${count}`).join(' · '))
function scenarioDescription(kind) { return ({ ARRIVAL: '模拟刚进入伙伴页；', PICTURE_VIEW: '模拟打开详情，未读取图片；', UPLOAD_SUCCESS: '模拟上传成功，未执行上传；', GALLERY: '模拟进入图库，未读取图库内容；' })[kind] || '' }
function recordLabel(row) { return ({ PENDING: '处理中', CANCELLED: '已取消，结果未确认', NOT_REQUESTED: '未请求', GATED: '门禁 / 限额', FAILED: '调用失败', SIMULATED: `演示 ${row.action || ''}`, DECIDED: `真实决策 ${row.action || ''}` })[row.category] || '未确认' }
function changeLimit(event) { const value = Number(event.target.value); if (!Number.isInteger(value) || value < 1 || value > 12) { parameterError.value = '本轮只能设置 1–12 次；每日总上限由服务端配置，请看每日总剩余。'; event.target.value = state.value.limit; return }; parameterError.value = ''; configure({ limit: value }) }
function changeCooldown(event) { const value = Number(event.target.value); if (!Number.isInteger(value) || value < 2 || value > 30) { parameterError.value = '手动试演冷却只能设置 2–30 秒。'; event.target.value = state.value.cooldownSeconds; return }; parameterError.value = ''; configure({ cooldownSeconds: value }) }
function clearRecords() { history.journal.clear(); session.configure({ enabled: false }) }
function newRound() { const { limit, cooldownSeconds, intensity } = state.value; history.journal.leave(); session.reset(); session.configure({ limit, cooldownSeconds, intensity, enabled: false }); stopTransport() }
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
const describeReason = describePresenceReason
function describeStatus(reason) { return ({ READY: '真实模型连接已配置，仍需你选择、开启并手动提交场景；尚未验证密钥、网络或模型健康。', DISABLED: '服务端主动回应未启用，本地模拟仍可使用。', REAL_DISABLED: '服务端尚未开放真实模型试演。', BYOK_REQUIRED: '请先在模型设置中配置并启用语言代理 BYOK 连接。', UNAVAILABLE: '暂时无法确认真实模型连接，请稍后刷新。' })[reason] || '尚未读取真实模型状态。' }
function stopTransport() { for (const current of [transport, realTransport]) current.stop().catch(() => { /* Server sessions expire after 15 minutes; no automatic decisions run. */ }) }
function configure(patch) { history.journal.leave(false); session.configure({ ...patch, ...(patch.enabled && (!user.currentUser?.id || transportMode.value === 'BYOK' && !realReady.value) ? { enabled: false } : {}) }); stopTransport() }
function changeTransport(mode) {
  if (!['LOCAL', 'SERVER', 'BYOK'].includes(mode) || mode === 'BYOK' && !realReady.value || mode === 'SERVER' && modelStatus.value?.rehearsalEnabled === false) return
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
    statusReadAt = Date.now(); budgetSnapshot.value = null
    modelStatus.value = { rehearsalEnabled: value.rehearsalEnabled, rehearsalDailyLimit: value.rehearsalDailyLimit, rehearsalDailyRemaining: value.rehearsalDailyRemaining, enabled: value.enabled, realEnabled: value.realEnabled, available: value.available, reason: value.reason,
      connection: connection ? { provider: connection.provider, modelCode: connection.modelCode } : null }
  } catch {
    if (generation === statusGeneration && !controller.signal.aborted) statusError.value = '暂时无法读取真实模型状态，请刷新后重试。'
  } finally {
    if (generation === statusGeneration) { statusLoading.value = false; statusController = null }
  }
}
function closeReply() { chatOpen.value = false; configure({ userBusy: chat.state?.phase !== 'idle' }) }
async function run(kind) {
  if (state.value.busy) return
  const at = Date.now(); const scenario = presenceScenarios.find(item => item.kind === kind)
  const input = { id: `rehearsal-${at}-${++eventSequence}`, kind, eventType: scenario?.eventType, page: scenario?.page, occurredAt: at }
  const id = history.journal.start({ kind, mode: transportMode.value, at, input, settings: state.value }); activeRecord = id
  const ok = await session.run(input)
  history.journal.finish(id, state.value.result || (state.value.reason.includes('未完成') ? { reason: 'UNAVAILABLE', source: transportMode.value === 'BYOK' ? 'SERVER_BYOK' : 'SERVER_DEMO' } : null), state.value.reason)
  if (ok && state.value.result?.dailyRemaining != null && Date.now() >= statusReadAt) budgetSnapshot.value = { limit: state.value.result.dailyLimit, remaining: state.value.result.dailyRemaining }
  if (activeRecord === id) activeRecord = null
}
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
  history.journal.ownerChanged(user.currentUser?.id); budgetSnapshot.value = null
  ownerGeneration++; session.reset(); session.configure({ userBusy: chat.state?.phase !== 'idle', visible: document.visibilityState === 'visible' }); stopTransport(); transportMode.value = 'LOCAL'; chatOpen.value = false; chatMode.value = null; chatError.value = ''
  confirmingClear.value = false; clearing.value = false; clearMessage.value = ''; refreshStatus()
}, { flush: 'sync', immediate: true })
function visibility() { configure({ visible: document.visibilityState === 'visible' }) }
onMounted(() => { timer = setInterval(() => { tick.value = Date.now() }, 250); visibility(); document.addEventListener('visibilitychange', visibility) })
onBeforeUnmount(() => { history.journal.leave(); unbindCancel(); clearInterval(timer); ownerGeneration++; cancelStatus(); session.reset(); stopTransport(); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.rehearsal { max-width: 1040px; margin: 0 auto; padding: 24px; color: #37463b; }
.model-status { overflow-wrap: anywhere; }.model-status a { display: inline-block; margin: 8px; }fieldset { min-width: 0; }
.intro { margin-bottom: 24px; }.eyebrow { color: #68765c; font-size: .8rem; }h1 { font-size: clamp(1.5rem, 4vw, 2.2rem); margin: .6rem 0; }h2 { font-size: 1.15rem; margin: 0 0 1rem; }p { line-height: 1.7; margin: .6rem 0; }a { color: #426c4a; }
.card { padding: 24px; border: 1px solid #dce1d2; border-radius: 18px; background: #fffdf7; margin-bottom: 20px; min-width: 0; }.workspace { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }.control-row,.scenarios { display: flex; gap: 12px; flex-wrap: wrap; margin: 16px 0; }label { display: inline-flex; gap: 8px; align-items: center; min-height: 44px; flex-wrap: wrap; }fieldset { border: 1px solid #dce1d2; border-radius: 10px; margin-top: 16px; }fieldset label { margin-right: 16px; }button, select, input[type=number] { min-height: 44px; border: 1px solid #87977f; border-radius: 8px; padding: 8px 12px; background: #fffefa; color: #37463b; font: inherit; }button { cursor: pointer; }button:hover:not(:disabled) { background: #edf0e3; }button:disabled { opacity: .5; cursor: not-allowed; }button.danger { border-color: #a6513d; color: #8a3b2a; }.limit { width: 5rem; }.stage-note { color: #68765c; font-size: .8rem; }.result { background: #eff2e8; border-radius: 12px; padding: 16px; min-height: 100px; }pre { white-space: pre-wrap; overflow-wrap: anywhere; background: #eff2e8; padding: 16px; border-radius: 10px; font-size: .8rem; }.inspector p,fieldset p { font-size: .85rem; }button:focus-visible,a:focus-visible,input:focus-visible,select:focus-visible { outline: 3px solid #617c56; outline-offset: 3px; }@media(max-width: 767px) { .rehearsal { padding: 12px; }.workspace { grid-template-columns: 1fr; gap: 0; }.card { padding: 18px; } }
</style>
