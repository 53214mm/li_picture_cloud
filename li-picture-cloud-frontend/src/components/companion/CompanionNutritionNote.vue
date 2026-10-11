<template>
  <section class="nutrition-banner" aria-label="当前图片营养分析模式">
    <div>
      <span class="nutrition-label">关于这张图片</span>
      <strong>实际来源会逐条写入成长档案</strong>
    </div>
    <p>
      {{ nutrition?.notice }}
      <span v-if="nutrition?.dailyLimit"> 每日视觉次数上限：{{ nutrition.dailyLimit }}。</span>
    </p>
  </section>
</template>

<script setup>
defineProps({ nutrition: { type: Object, default: null } })
</script>

<style scoped>
.nutrition-label, .nutrition-banner strong { display: block; }
.nutrition-label { font-size: 10px; letter-spacing: .06em; color: #7a7b64; margin-bottom: 12px; }
.nutrition-banner strong { font-size: 14px; font-weight: 500; line-height: 1.9; }
.nutrition-banner p { margin-top: 16px; font-size: 12px; color: #66705e; line-height: 1.9; }
@media (max-width: 767px) { .nutrition-banner strong { font-size: 13px; } }
</style>
