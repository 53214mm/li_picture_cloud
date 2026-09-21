<template>
  <div class="unavailable">
    <LpStateBlock title="页面不可用" message="页面不存在，或当前版本尚未开放。">
      <template #action><router-link to="/gallery">返回图库</router-link></template>
    </LpStateBlock>
  </div>
</template>
<script setup>
import LpStateBlock from '@/components/ui/LpStateBlock.vue'
</script>
<style scoped>
.unavailable { padding: var(--lp-gutter); }
a { text-decoration: underline; }
</style>
