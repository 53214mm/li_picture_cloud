<template>
  <button type="button" class="space-card" @click="emit('open', space.id)">
    <span class="card-header">
      <strong>{{ space.spaceName }}</strong>
      <span class="badges">
        <span class="badge">{{ spaceTypeText(space.spaceType) }}</span>
        <span v-if="role" class="badge role">{{ spaceRoleText(role) }}</span>
      </span>
    </span>
    <span class="card-meta">{{ spaceLevelText(space.spaceLevel) }} · {{ space.totalCount || 0 }} 张图片</span>
    <span class="usage">{{ formatSize(space.totalSize || 0) }} / {{ formatSize(space.maxSize || 0) }}</span>
  </button>
</template>

<script setup>
import { formatSize, spaceLevelText, spaceRoleText, spaceTypeText } from '@/constants/space'

defineProps({
  space: { type: Object, required: true },
  role: { type: String, default: '' }
})

const emit = defineEmits(['open'])
</script>

<style scoped>
.space-card {
  width: 100%; min-height: 150px; padding: 1.25rem; border: 2px solid var(--gray-200);
  background: var(--white); color: inherit; text-align: left; cursor: pointer;
  display: flex; flex-direction: column; gap: 0.75rem; transition: 0.2s ease;
}
.space-card:hover { border-color: var(--black); transform: translateY(-2px); }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.75rem; }
.card-header strong { font-size: 1.125rem; }
.badges { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.35rem; }
.badge { padding: 0.2rem 0.5rem; background: var(--black); color: var(--white); font-size: 0.7rem; white-space: nowrap; }
.badge.role { background: var(--blue); }
.card-meta { color: var(--gray-600); font-size: 0.875rem; }
.usage { margin-top: auto; font-size: 0.8125rem; color: var(--gray-400); }

@media (max-width: 767px) {
  .space-card { min-height: 132px; padding: 1rem; }
  .card-header { align-items: flex-start; flex-direction: column; }
  .badges { justify-content: flex-start; }
}
</style>
