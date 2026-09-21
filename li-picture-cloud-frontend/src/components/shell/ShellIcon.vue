<template>
  <svg class="shell-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    <path :d="paths[name] || paths.spaces" />
  </svg>
</template>
<script setup>
// Small, consistent Shell-only glyphs. No icon dependency or companion avatar.
defineProps({ name: { type: String, required: true } })
const paths = {
  spaces: 'M3.5 7.5h17v12h-17z M3.5 7.5V4.5h6l2 3',
  gallery: 'M4 4.5h16v15H4z M4 16l5-5 4 4 3-3 4 4 M15.5 8h.01',
  search: 'M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z M16 16l5 5',
  upload: 'M12 16V3 M7 8l5-5 5 5 M4 14v6h16v-6',
  account: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z M4 21a8 8 0 0 1 16 0',
  tools: 'M5 4v4m0 4v8 M12 4v9m0 4v3 M19 4v2m0 4v10 M2.5 8h5v4h-5z M9.5 13h5v4h-5z M16.5 6h5v4h-5z',
  admin: 'M12 3l8 3v6c0 4-4 7-8 9-4-2-8-5-8-9V6z M8.5 12l2.5 2.5 4.5-5',
  companion: 'M12 21v-8 M12 15C6 15 3 12 3 7c6 0 9 3 9 8Z M12 12c0-5 3-8 9-8 0 5-3 8-9 8Z',
  more: 'M5 11a1 1 0 1 0 0 2 1 1 0 1 0 0-2 M12 11a1 1 0 1 0 0 2 1 1 0 1 0 0-2 M19 11a1 1 0 1 0 0 2 1 1 0 1 0 0-2',
  chevron: 'M9 5l7 7-7 7'
}
</script>
<style scoped>
.shell-icon { width: 20px; height: 20px; flex: 0 0 auto; }
</style>
