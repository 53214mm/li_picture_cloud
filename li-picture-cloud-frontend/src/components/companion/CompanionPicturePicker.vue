<template>
  <section class="picker" aria-labelledby="picture-picker-title">
    <header>
      <div>
        <span class="eyebrow">成长素材</span>
        <h2 id="picture-picker-title">选择一张图片</h2>
      </div>
      <span v-if="selectedId" class="selection-status" aria-live="polite">已选择 1 张</span>
    </header>
    <div v-if="loading" class="picker-state" role="status">正在读取你的私有图库…</div>
    <div v-else-if="!pictures.length" class="picker-state">这个空间里暂时没有可用图片。</div>
    <div v-else class="picture-grid">
      <button v-for="picture in pictures" :key="picture.id" type="button" class="picture-choice"
              :class="{ selected: String(picture.id) === String(selectedId) }"
              :aria-pressed="String(picture.id) === String(selectedId)" :disabled="disabled"
              @click="$emit('select', String(picture.id))">
        <img :src="picture.thumbnailUrl || picture.url" :alt="picture.name || '图片'"
             width="320" height="220" loading="lazy" />
        <span>{{ picture.name || '未命名图片' }}</span>
      </button>
    </div>
  </section>
</template>

<script setup>
defineProps({
  pictures: { type: Array, required: true },
  selectedId: { type: [String, Number], default: null },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})
defineEmits(['select'])
</script>

<style scoped>
.picker { border: 2px solid var(--black); background: var(--white); }
.picker header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.25rem; border-bottom: 2px solid var(--black); }
.picker h2 { font-size: 1.25rem; }
.eyebrow { display: block; color: var(--red); font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.selection-status { padding: .3rem .55rem; background: var(--black); color: var(--white); font-size: .75rem; font-weight: 700; }
.picker-state { min-height: 8rem; display: grid; place-items: center; padding: 1.5rem; color: var(--gray-600); text-align: center; }
.picture-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .75rem; padding: 1rem; }
.picture-choice { min-width: 0; min-height: 44px; border: 2px solid var(--gray-200); background: var(--white); text-align: left; transition: border-color .2s, box-shadow .2s; }
.picture-choice:hover:not(:disabled) { border-color: var(--black); }
.picture-choice.selected { border-color: var(--blue); box-shadow: 0 0 0 2px var(--blue); }
.picture-choice:disabled { cursor: not-allowed; opacity: .55; }
.picture-choice img { width: 100%; aspect-ratio: 4 / 3; object-fit: cover; background: var(--gray-100); }
.picture-choice span { display: block; min-height: 44px; padding: .65rem; overflow: hidden; font-size: .78rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 767px) {
  .picture-grid { grid-template-columns: 1fr; }
  .picture-choice { display: grid; grid-template-columns: 7rem minmax(0, 1fr); align-items: center; }
  .picture-choice img { width: 7rem; height: 5.5rem; }
  .picture-choice span { white-space: normal; }
}
</style>
