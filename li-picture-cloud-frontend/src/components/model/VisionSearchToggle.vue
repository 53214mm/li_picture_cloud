<template>
  <section class="vision-search" aria-labelledby="vision-search-title" data-testid="vision-search">
    <div class="vision-copy">
      <h2 id="vision-search-title">识图联网</h2>
      <p>开启后，你喂图时视觉模型会先联网搜索图里的作品、角色或出处，再写描述，认得出的角色会更准。会更慢，搜索也可能额外计费。</p>
      <p v-if="view && !view.supported" class="vision-note">当前识图模型（{{ view.modelCode }}）不支持带图联网，开关会保留，换成百炼千问模型后生效。</p>
      <p v-if="error" class="vision-error" role="alert">{{ error }}</p>
    </div>
    <label class="vision-switch">
      <input type="checkbox" role="switch" :checked="view?.forcedSearch" :disabled="!view || busy" @change="toggle($event.target.checked)" />
      <span>{{ view?.forcedSearch ? '已开启' : '未开启' }}</span>
    </label>
  </section>
</template>

<script setup>
import { onMounted, ref, shallowRef } from 'vue'
import { getVisionSearch, setVisionSearch } from '@/api/modelGateway'

const view = shallowRef(null)
const busy = ref(false)
const error = ref('')

onMounted(async () => {
  try { view.value = await getVisionSearch() } catch { error.value = '暂时读不到识图设置，请稍后刷新。' }
})

async function toggle(forcedSearch) {
  busy.value = true
  error.value = ''
  try { view.value = await setVisionSearch(forcedSearch) } catch { error.value = '保存失败，请稍后重试。' }
  finally { busy.value = false }
}
</script>

<style scoped>
.vision-search { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--lp-space-4); padding: var(--lp-space-5) var(--lp-space-6); border: 1px solid var(--lp-border); border-radius: var(--lp-radius-l); background: var(--lp-surface-elevated); }
.vision-copy { flex: 1 1 22rem; min-width: 0; }
.vision-copy h2 { font-size: var(--lp-font-size-h3); line-height: var(--lp-line-height-h3); }
.vision-copy p { margin-top: var(--lp-space-2); color: var(--lp-text-secondary); font-size: var(--lp-font-size-small); line-height: 1.7; }
.vision-note { color: var(--lp-text-primary); }
.vision-error { color: var(--lp-danger); }
.vision-switch { display: inline-flex; align-items: center; gap: var(--lp-space-2); min-height: 44px; font-size: var(--lp-font-size-small); cursor: pointer; }
.vision-switch input { width: 20px; height: 20px; accent-color: var(--lp-accent); }
</style>
