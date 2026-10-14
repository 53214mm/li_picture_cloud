<template>
  <details class="observer" :class="{ floating: compact }" :open="!compact" data-testid="companion-observation" @toggle="$emit('expanded', $event.target.open)">
    <summary>绫页 · 独立观察 <span>{{ state?.enabled ? state.suppressed ? '授权保留 · 暂时让出观察' : state.page === 'UNKNOWN' || state.held ? '授权保留 · 本页暂不观察' : '已开启' : '默认关闭 / 已暂停' }}</span></summary>
    <div class="observer-body" v-if="state">
      <CompanionObservationSpeech @reply="$emit('reply')" />
      <p>操作只更新素材。明确开启后，前台约每 90 秒观察一次，可自己开启话题，也可沉默或做表情；沉默时退避至最多 600 秒。普通聊天优先。</p>
      <p>会发送本站可见图片编号/名称/标签、当前会话最近 6 条聊天、最多 2 条相关授权记忆及 10 分钟内的临时关注点。接收方为下列当前 BYOK，可能收费，取消也可能已收费；次数不等于金额上限。</p>
      <p data-testid="observer-receivers">文本：{{ receiver(state.status?.textReceiver) }}<br />视觉：{{ receiver(state.status?.visionReceiver) }}</p>
      <p v-if="!state.status?.realVisualReady" role="status" data-testid="visual-capture-unavailable">真实视觉区域暂不可用；灰色布局图不会发送模型。独立文本观察可继续使用。</p>
      <button type="button" :disabled="state.statusPhase === 'loading'" @click="coordinator.refreshStatus()">刷新接收方与额度（不调用模型）</button>
      <div class="options">
        <label>本轮文本次数（1–500）<input type="number" min="1" max="500" v-model.number="textLimit" :disabled="state.enabled" /></label>
        <label>强度<select v-model="intensity" :disabled="state.enabled"><option value="LOW">低 · 发言至少间隔 300 秒</option><option value="NORMAL">普通 · 发言至少间隔 120 秒</option></select></label>
        <label><input type="checkbox" v-model="vision" :disabled="visualBlocks.length > 0" :aria-describedby="visualBlocks.length ? visualReasonId : undefined" /> 开启本轮观察区域真实视觉（默认关闭，单独 BYOK 费用）</label>
        <div v-if="visualBlocks.length" :id="visualReasonId" class="disabled-notice" data-testid="observer-visual-reasons"><p v-for="notice in visualBlocks" :key="notice">{{ notice }}</p><router-link v-if="state.status && !state.status.visionReceiver" to="/model-gateway">前往模型设置配置视觉代理 →</router-link></div>
        <label v-if="vision">本轮视觉次数（1–100）<input type="number" min="1" max="100" v-model.number="visionLimit" :disabled="state.enabled" /></label>
      </div>
      <p>本轮预计 {{ duration }}，按 90–600 秒计算；普通聊天、窗口隐藏或不支持的页面暂不观察，返回后在原授权有效且有余额时按节奏继续，不补发旧结果。手动结束、注销、换号、额度耗尽或 24 小时授权到期会结束本轮。已经派发的请求可能继续计费，暂停不会退款。6/2 只是建议，没有开启或收费。</p>
      <p>服务端独立日硬限：文本 {{ state.status?.textDayMax ?? 1000 }} / 视觉 {{ state.status?.visionDayMax ?? 200 }}；当前已用 {{ state.status?.counts?.text ?? '待读取' }} / {{ state.status?.counts?.vision ?? '待读取' }}（UTC）。原手动试演 100 次不授权自动观察。</p>
      <p v-if="vision">观察区域截图包含最多 6 张当前可见、已授权图片的完整原图及标题/分类/标签，重新排版，不是原网页或桌面截图。不会采集页面表单、账号、支付区域、头像或其他 DOM 文字。图片自身文字也会发送，请只授权愿意发送的图片；过滤标签中的已知 Key 不等于对图片文字自动脱敏。仅支持本站 COS 的 JPEG/PNG 原图，原图不超过 2 MiB / 20 MP；WebP 展示图可使用同一图片的 JPEG/PNG 原图，WebP-only、跨域未知来源、读取失败及未知/敏感页面会明确跳过视觉，仍可文本观察。描述最多缓存 180 秒，不写长期记忆。</p>
      <div class="buttons">
        <button type="button" :disabled="!!startBlock" :aria-describedby="startBlock ? startReasonId : undefined" @click="start(true)">明确授权开启本轮自动观察</button>
        <button type="button" :disabled="!!startBlock" :aria-describedby="startBlock ? startReasonId : undefined" @click="start(false)">明确授权单次观察</button>
        <button type="button" :disabled="!state.enabled && state.phase !== 'opening'" @click="coordinator.pause()">暂停并结束本轮</button>
        <button v-if="vision" type="button" :disabled="visualBlocks.length > 0 || state.suppressed" @click="coordinator.previewOnly()">仅预览真实观察区域（无模型调用）</button>
      </div>
      <p v-if="startBlock" :id="startReasonId" class="disabled-notice" data-testid="observer-start-reason">{{ startBlock }}</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <p role="status">{{ activity }} · 本轮文本剩余 {{ state.textRemaining }} / 视觉剩余 {{ state.visionRemaining }}<span v-if="state.reason"> · {{ state.reason }}</span></p>
      <p v-if="state.visualSkip">视觉状态：{{ state.visualSkip }}</p>
      <figure v-if="state.preview" data-testid="real-observation-region"><img class="preview" :src="state.preview" alt="独立观察区域：已授权图片真实像素及允许标签，非原页面截图" /><figcaption>真实素材观察区域 · 完整图片重新排版，非网页/桌面截图</figcaption></figure>
      <p v-if="state.presentation?.action === 'EMOTE'" class="emote-state">模型表情：{{ state.presentation.emote }}</p>
      <details><summary>本账号观察记录 {{ state.records.length }} / 50</summary>
        <button type="button" @click="coordinator.clear()">清除记录并结束本轮</button>
        <p>仅当前账号的应用内存，跨页可回看；刷新/切号/注销后清除。EMPTY_OUTPUT 是未取得正文，不是模型选择沉默；不自动重试。费用金额未知，以供应商账单为准。</p>
        <details v-for="row in state.records" :key="row.id || `${row.request.sessionId}-${row.request.sequence}`"><summary>{{ row.response.parsed?.action || row.response.reason || '未决策' }} · 文本派发 {{ dispatched(row.response.modelDispatched) }} / 视觉派发 {{ dispatched(row.response.visionDispatched) }}</summary>
          <p v-if="row.kind === 'LOCAL_ATTEMPT'">本次阶段：{{ row.response.stage }}，本地尝试记录，未伪造模型正文或服务端回包。已发送但没有回包时，实际派发/费用未知；不自动重试。</p>
          <p v-else>实际快照、messages、正文/partial、parsed、展示原因和流终止元数据；不含 reasoning 内容。</p><pre>{{ JSON.stringify(row, null, 2) }}</pre>
        </details>
      </details>
    </div>
  </details>
</template>
<script setup>
import { ref, computed, watch, useId } from 'vue'
import { useCompanionObserverStore } from '@/stores/companionObserver'
import CompanionObservationSpeech from './CompanionObservationSpeech.vue'
import { observationActivity, observationStartBlock, observationVisualBlocks } from '@/presentation/observationPresentation'
defineProps({ compact: Boolean })
defineEmits(['reply', 'expanded'])
const store = useCompanionObserverStore()
const state = computed(() => store.state)
const coordinator = store.coordinator
const visualReasonId = useId(), startReasonId = useId()
const startBlock = computed(() => observationStartBlock(state.value))
const visualBlocks = computed(() => observationVisualBlocks(state.value))
const activity = computed(() => observationActivity(state.value))
const textLimit = ref(6), visionLimit = ref(2), vision = ref(false), intensity = ref('NORMAL'), error = ref('')
watch(vision, enabled => { if (!enabled) coordinator.withdrawVisualConsent() })
const duration = computed(() => `${Math.round(Number(textLimit.value) * 90 / 60)}–${Math.round(Number(textLimit.value) * 600 / 60)} 分钟`)
const receiver = value => value ? `${value.provider} · ${value.model} · ${value.host} (${value.name})` : '不可用，请配置当前 BYOK 能力'
const dispatched = value => value === true ? '是' : value === false ? '否' : '未知'
async function start(auto) {
  error.value = ''
  if (vision.value && (!Number.isInteger(visionLimit.value) || visionLimit.value < 1 || visionLimit.value > 100)) { error.value = '本轮视觉次数须为 1–100，未开启视觉时为 0。'; return }
  try {
    await coordinator.start({ textLimit: auto ? textLimit.value : 1, visionLimit: vision.value ? visionLimit.value : 0, intensity: intensity.value, auto })
    if (!auto) await coordinator.observeNow()
  } catch (failure) { error.value = failure?.message === 'RECEIVER_CHANGED' ? '接收方已更改，请核对窗口中的新接收方后再次明确授权。' : '开启失败：请核实本轮次数（文本 1–500 / 视觉 0–100）、接收方和当前聊天状态。超范围不会回退或夹限。' }
}
</script>
<style scoped>
.observer { border: 1px solid var(--lp-border); border-radius: 16px; background: var(--lp-surface); padding: 14px; font-size: .85rem; }
.floating { position: fixed; bottom: 20px; right: 20px; z-index: var(--lp-z-sticky); width: min(400px, calc(100vw - 32px)); max-height: min(75dvh, 700px); overflow: auto; box-shadow: 0 8px 32px #0002; }
summary { cursor: pointer; font-weight: 600; } summary span { color: var(--lp-text-secondary); font-weight: 400; }
.observer-body { display: grid; gap: 12px; padding-top: 12px; }.observer-body p { line-height: 1.6; }
.options { display: grid; gap: 10px; } label { display: flex; align-items: center; justify-content: space-between; gap: 8px; } input[type=number] { width: 76px; }
.buttons { display: flex; gap: 8px; flex-wrap: wrap; } button { border: 1px solid var(--lp-border); border-radius: 8px; padding: 8px; }button:disabled { opacity: .5; }
.preview { width: 100%; }.disabled-notice { color: var(--lp-text-secondary); line-height: 1.6; }.disabled-notice a { text-decoration: underline; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .75rem; max-height: 400px; overflow: auto; } details details { padding-top: 8px; }
@media(width < 768px) { .floating { bottom: 80px; } }
</style>
