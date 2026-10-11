<template>
  <aside class="site-presence" aria-label="绫页站内陪伴演示" data-testid="site-presence">
    <details>
      <summary><CompanionPortrait :presentation="presentation" /><span>绫页 · {{ state.enabled ? '演示已开启' : '站内陪伴' }}<small>服务端 DEMO · 默认关闭</small></span></summary>
      <div class="presence-controls">
        <p>只接收进入伙伴页、图库和成功打开图片详情的事件。不读取图片、网址、页面文字或输入内容；不是图片理解或模型人格。</p>
        <label><input type="checkbox" :checked="state.enabled" @change="$emit('configure', { enabled: $event.target.checked })" /> 开启站内演示</label>
        <label><input type="checkbox" :checked="state.paused" @change="$emit('configure', { paused: $event.target.checked })" /> 暂停</label>
        <label><input type="checkbox" :checked="state.muted" @change="$emit('configure', { muted: $event.target.checked })" /> 静音短句</label>
        <label>主动程度 <select :value="state.intensity" @change="$emit('configure', { intensity: $event.target.value })"><option value="LOW">低</option><option value="NORMAL">普通</option></select></label>
        <label>本次提交上限 <input type="number" min="1" max="12" :value="state.limit" @change="$emit('configure', { limit: Number($event.target.value) })" /></label>
        <p>已提交 {{ state.used }} / {{ state.limit }} 次；本地至少间隔 45 秒。跨页保留预算，刷新或换账号关闭。仅 DEMO，无模型费用；真实模型暂未开放。</p>
        <p v-if="!state.visible">当前页面或操作期间暂停接收；恢复后不补发旧事件。</p>
        <details><summary>脱敏输入检查器</summary><pre>{{ state.input ? JSON.stringify(state.input, null, 2) : '尚无输入' }}</pre><p>请求另附临时会话编号和递增序号，不保存浏览器存储。</p></details>
      </div>
    </details>
    <div v-if="state.enabled && (state.result || state.reason || state.busy)" class="presence-result" role="status" aria-live="polite">
      <template v-if="state.result"><strong>{{ state.result.action }}</strong> · 服务端固定演示
        <p>{{ state.result.text || '这会儿先不插话。' }}</p>
        <p>服务端剩余 {{ state.result.remainingDecisions ?? '未知' }} 次 · {{ state.result.reason || '无原因' }}</p>
        <button v-if="state.result.action !== 'SILENT'" type="button" @click="$emit('reply')">回复绫页 · 普通聊天</button>
        <small v-if="state.result.action !== 'SILENT'">按原聊天设置计费；演示回应不写入聊天历史</small>
      </template>
      <p v-if="state.busy">正在处理事件…</p><p v-if="state.reason">{{ state.reason }}</p>
    </div>
  </aside>
</template>
<script setup>
import CompanionPortrait from './body/CompanionPortrait.vue'
import { mapCompanionPresentation } from '@/presentation/companionPresentation'
defineProps({ state: { type: Object, required: true } })
defineEmits(['configure', 'reply'])
const presentation = mapCompanionPresentation()
</script>
<style scoped>
.site-presence { position: fixed; right: 16px; bottom: 20px; z-index: 20; width: min(320px, calc(100vw - 32px)); max-height: 65dvh; overflow: auto; border: 1px solid var(--lp-border-strong); border-radius: var(--lp-radius-m); background: var(--lp-surface); padding: 12px; box-shadow: 0 4px 20px #0001; font-size: .8rem; }
summary { cursor: pointer; } .site-presence > details > summary { display: flex; gap: 12px; align-items: center; min-height: 44px; } .site-presence :deep(.companion-portrait) { width: 48px; height: 48px; }
small { display: block; color: var(--lp-text-secondary); } p { line-height: 1.6; margin: 8px 0; } label { display: flex; align-items: center; gap: 8px; min-height: 44px; } input[type=number] { width: 64px; } select, input[type=number], button { min-height: 44px; padding: 6px; border: 1px solid var(--lp-border-strong); border-radius: 6px; } pre { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .7rem; } .presence-result { border-top: 1px solid var(--lp-border); margin-top: 8px; padding-top: 8px; }
@media (width < 768px) { .site-presence { bottom: calc(76px + env(safe-area-inset-bottom)); width: min(280px, calc(100vw - 24px)); right: 12px; } }
</style>
