<template>
  <div class="home">
    <!-- ========== 英雄区 ========== -->
    <HeroSection />

    <!-- ========== 特色卡片 ========== -->
    <section class="section">
      <div class="container">
        <h2 class="section-title">智能图库，重新定义影像管理</h2>
        <div class="features-grid">
          <div class="feature-card" v-for="f in features" :key="f.title">
            <div class="feature-icon" :style="{ background: f.color }">{{ f.icon }}</div>
            <h3>{{ f.title }}</h3>
            <p>{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== 精选马赛克 ========== -->
    <section class="section mosaic-section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">精选合集</h2>
          <router-link to="/gallery" class="section-link">浏览全部 &rarr;</router-link>
        </div>
        <div class="mosaic-grid">
          <div
            v-for="(item, i) in mosaicItems"
            :key="i"
            class="mosaic-item"
            :class="'mosaic-' + item.size"
            :style="{ background: item.color }"
          >
            <span class="mosaic-label">{{ item.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== CTA ========== -->
    <section class="section cta-section">
      <div class="container cta-inner">
        <h2>准备好开始了吗？</h2>
        <p>免费注册，即刻体验下一代云端图库。</p>
        <router-link to="/register" class="btn btn-primary btn-lg">创建账号</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import HeroSection from '@/components/HeroSection.vue'

const features = [
  { icon: '⚡', title: '闪电搜索', desc: 'AI 驱动的智能语义搜索，毫秒级响应，精准定位每一张图。', color: 'var(--red)' },
  { icon: '☁️', title: '云端存储', desc: '无限容量，多副本冗余，随时随地访问你的数字资产。', color: 'var(--blue)' },
  { icon: '🎨', title: '策展排版', desc: '马赛克布局编辑器，像杂志一样展示你的精选作品集。', color: 'var(--yellow)' },
]

const mosaicItems = [
  { label: '城市剪影', size: 'lg', color: '#1A1A1A' },
  { label: '自然风光', size: 'sm', color: 'var(--blue)' },
  { label: '人像写真', size: 'sm', color: 'var(--red)' },
  { label: '抽象艺术', size: 'md', color: 'var(--yellow)' },
  { label: '建筑设计', size: 'sm', color: '#2D2D2D' },
  { label: '旅行日记', size: 'lg', color: 'var(--red)' },
  { label: '黑白纪实', size: 'sm', color: '#3A3A3A' },
  { label: '静物摄影', size: 'sm', color: 'var(--blue)' },
]
</script>

<style scoped>
.section { padding: 5rem 0; }
.section-title {
  font-size: 2rem; font-weight: 700; letter-spacing: -0.04em;
  margin-bottom: 0.5rem;
}
.section-header {
  display: flex; align-items: baseline; justify-content: space-between;
  margin-bottom: 2rem;
}
.section-link { font-size: 0.875rem; font-weight: 600; text-decoration: underline; }

/* 特色卡片 */
.features-grid {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 2rem;
  margin-top: 2.5rem;
}
.feature-card {
  border: 2px solid var(--black); padding: 2rem;
  transition: transform 0.2s, box-shadow 0.2s;
}
.feature-card:hover { transform: translate(-4px, -4px); box-shadow: 8px 8px 0 var(--black); }
.feature-icon {
  width: 3rem; height: 3rem; display: flex; align-items: center; justify-content: center;
  font-size: 1.25rem; margin-bottom: 1rem;
}
.feature-card h3 { font-size: 1.125rem; font-weight: 600; margin-bottom: 0.5rem; }
.feature-card p { color: var(--gray-600); font-size: 0.9375rem; line-height: 1.6; }

/* 马赛克 */
.mosaic-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-auto-rows: 160px;
  gap: 1rem;
}
.mosaic-item {
  position: relative; overflow: hidden;
  display: flex; align-items: flex-end;
  padding: 1.25rem;
  transition: transform 0.3s;
}
.mosaic-item:hover { transform: scale(0.97); }
.mosaic-lg { grid-column: span 2; grid-row: span 2; }
.mosaic-md { grid-column: span 2; grid-row: span 1; }
.mosaic-sm { grid-column: span 1; grid-row: span 1; }
.mosaic-label {
  color: var(--white); font-size: 0.9375rem; font-weight: 600;
  letter-spacing: -0.01em;
}

/* CTA */
.cta-section { background: var(--black); color: var(--white); text-align: center; }
.cta-inner h2 { font-size: 2.5rem; font-weight: 700; letter-spacing: -0.04em; margin-bottom: 0.5rem; }
.cta-inner p { color: var(--gray-400); margin-bottom: 2rem; font-size: 1.125rem; }
.btn-lg { padding: 1rem 3rem; font-size: 1rem; }

@media (max-width: 768px) {
  .features-grid { grid-template-columns: 1fr; }
  .mosaic-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
