<template>
  <div class="model-gateway-page">
    <div class="gateway-container">
      <header class="gateway-hero">
        <h1>模型连接</h1>
        <p>连接你自己的 AI 模型，伙伴就会用它来聊天。</p>
      </header>

      <ModelQuickConnect ref="quick" @changed="quickChanged" />

      <VisionSearchToggle />

      <details class="advanced-panel" data-testid="gateway-advanced" @toggle="advancedOpen = $event.target.open">
        <summary>
          <span>高级设置</span>
          <span class="advanced-hint">多个连接、按任务分配模型、调用记录</span>
        </summary>
        <ModelGatewayAdvanced v-if="advancedOpen" :key="advancedRevision" @changed="advancedChanged" />
      </details>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ModelQuickConnect from '@/components/model/ModelQuickConnect.vue'
import VisionSearchToggle from '@/components/model/VisionSearchToggle.vue'
import ModelGatewayAdvanced from '@/components/model/ModelGatewayAdvanced.vue'

// The advanced panel loads only when opened; quick and advanced views refresh each other.
const quick = ref(null)
const advancedOpen = ref(false)
const advancedRevision = ref(0)

function quickChanged() {
  advancedRevision.value += 1
}

function advancedChanged() {
  quick.value?.reload()
}
</script>

<style scoped>
.model-gateway-page { min-height: calc(100dvh - 4rem); padding: var(--lp-space-7) var(--lp-gutter) var(--lp-space-8); background: var(--lp-bg); }
.gateway-container { display: grid; gap: var(--lp-space-5); max-width: 56rem; margin-inline: auto; }
.gateway-hero h1 { font-family: var(--lp-font-display); font-size: var(--lp-font-size-h1); line-height: var(--lp-line-height-h1); }
.gateway-hero p { margin-top: var(--lp-space-2); color: var(--lp-text-secondary); }
.advanced-panel { border: 1px solid var(--lp-border); border-radius: var(--lp-radius-l); background: var(--lp-surface); }
.advanced-panel > summary { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--lp-space-2) var(--lp-space-3); min-height: 52px; padding: var(--lp-space-4) var(--lp-space-5); cursor: pointer; font-weight: var(--lp-font-weight-semibold); }
.advanced-panel > summary:focus-visible { outline: 2px solid var(--lp-focus-ring, var(--lp-accent)); outline-offset: 2px; border-radius: var(--lp-radius-l); }
.advanced-hint { color: var(--lp-text-secondary); font-size: var(--lp-font-size-caption); font-weight: var(--lp-font-weight-regular); }
.advanced-panel[open] > summary { border-bottom: 1px solid var(--lp-border); }
.advanced-panel > :deep(.gateway-advanced) { padding: var(--lp-space-5); }
</style>
