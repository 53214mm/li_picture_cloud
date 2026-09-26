<template>
  <div class="companion-interaction">
    <div class="interaction-intro">
      <CompanionPortrait :presentation="presentation" />
      <p>从这里去和绫页聊聊，或为她挑一张图片。</p>
    </div>
    <div v-if="interaction.inspection" class="interaction-picture" aria-live="polite">
      <p v-if="interaction.inspection.phase === 'loading'" role="status">正在确认图片与所属空间…</p>
      <template v-else-if="interaction.inspection.phase === 'ready'">
        <strong>{{ interaction.inspection.picture.name || '未命名图片' }}</strong>
        <p>来自你的私有空间。这里只选择图片，还没有喂养。</p>
        <button type="button" class="btn btn-primary" @click="go('feed', interaction.inspection.picture.id)">在伙伴空间选择这张</button>
      </template>
      <p v-else role="alert">这张图片暂不可选择。此入口仅支持你自己的私有空间图片，请确认图片仍可访问。</p>
    </div>
    <div class="interaction-actions">
      <button type="button" @click="go('chat')">聊一聊 <span>前往站内对话</span></button>
      <button type="button" @click="go('feed')">选择图片 <span>在伙伴空间确认后喂养</span></button>
      <router-link to="/companion" @click="emit('close')">打开伙伴空间</router-link>
      <router-link to="/space/my" @click="emit('close')">前往我的空间选图</router-link>
    </div>
    <p class="interaction-help">桌面可把站内图片拖到绫页处；也可以在图片详情中点「选给绫页」。</p>
  </div>
</template>
<script setup>
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import CompanionPortrait from './body/CompanionPortrait.vue'
import { useCompanionPresentationStore } from '@/stores/companionPresentation'
import { useCompanionInteractionStore } from '@/stores/companionInteraction'
const { presentation } = storeToRefs(useCompanionPresentationStore())
const interaction = useCompanionInteractionStore()
const router = useRouter()
const emit = defineEmits(['close'])
async function go(target, pictureId = null) {
  interaction.navigate(target, pictureId)
  emit('close')
  await router.push('/companion')
}
</script>
<style scoped>
.companion-interaction { display: grid; gap: 24px; }
.interaction-intro { display: flex; align-items: center; gap: 16px; }
p { line-height: 1.7; font-size: .875rem; color: var(--lp-text-secondary); }
.interaction-picture { display: grid; gap: 12px; padding: 16px; border: 1px solid var(--lp-border-strong); border-radius: var(--lp-radius-m); overflow-wrap: anywhere; }
.interaction-actions { display: grid; gap: 8px; }
.interaction-actions > * { display: grid; gap: 4px; min-height: 48px; padding: 12px; text-align: left; border: 1px solid var(--lp-border); border-radius: var(--lp-radius-s); }
.interaction-actions > *:hover { background: var(--lp-accent-soft); }
.interaction-actions span, .interaction-help { font-size: .75rem; color: var(--lp-text-secondary); }
</style>
