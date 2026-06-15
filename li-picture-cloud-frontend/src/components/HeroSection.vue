<template>
  <section class="hero">
    <div class="hero-bg"></div>
    <div class="container hero-content">
      <!-- 大号标题 -->
      <h1 class="hero-title">
        <span class="line">你的视觉世界，</span>
        <span class="line accent">云上智能管理。</span>
      </h1>
      <p class="hero-desc">
        基于 AI 的云端图库平台。上传、搜索、策展，以瑞士设计的精确美学呈现你的每一张作品。
      </p>

      <!-- 智能搜索框 -->
      <div class="search-box">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="输入关键词搜索图片，如：城市夜景、黑白肖像…"
          @keyup.enter="handleSearch"
        />
        <button class="search-btn" @click="handleSearch">
          搜索
          <span class="arrow">&rarr;</span>
        </button>
      </div>

      <!-- 热门标签 -->
      <div class="tags">
        <span class="tag-label">热门：</span>
        <button v-for="t in hotTags" :key="t" class="tag" @click="keyword = t; handleSearch()">
          {{ t }}
        </button>
      </div>
    </div>

    <!-- 底部装饰条 -->
    <div class="hero-stripes">
      <div class="stripe" style="background: var(--red)"></div>
      <div class="stripe" style="background: var(--yellow)"></div>
      <div class="stripe" style="background: var(--blue)"></div>
      <div class="stripe" style="background: var(--black)"></div>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const keyword = ref('')
const hotTags = ['风景', '人物', '街拍', '黑白', '建筑', '抽象']

function handleSearch() {
  if (keyword.value.trim()) {
    router.push({ name: 'gallery', query: { q: keyword.value.trim() } })
  } else {
    router.push({ name: 'gallery' })
  }
}
</script>

<style scoped>
.hero {
  position: relative;
  min-height: 85vh;
  display: flex; flex-direction: column; justify-content: center;
  background: var(--white);
  overflow: hidden;
}
/* 装饰几何背景 */
.hero-bg {
  position: absolute; inset: 0;
  background:
    radial-gradient(circle at 20% 30%, rgba(224,58,48,0.06) 0%, transparent 50%),
    radial-gradient(circle at 75% 60%, rgba(0,90,255,0.06) 0%, transparent 50%),
    radial-gradient(circle at 50% 80%, rgba(255,200,0,0.04) 0%, transparent 40%);
}
.hero-content { position: relative; z-index: 1; padding: 4rem 0 2rem; }
.hero-title {
  font-size: clamp(3rem, 8vw, 6rem);
  font-weight: 900;
  letter-spacing: -0.05em;
  line-height: 1.05;
  margin-bottom: 1.5rem;
}
.line { display: block; }
.line.accent { color: var(--red); }
.hero-desc {
  font-size: 1.25rem; line-height: 1.6; color: var(--gray-600);
  max-width: 560px; margin-bottom: 2.5rem;
}

/* 搜索框 */
.search-box {
  display: flex;
  max-width: 640px;
  border: 2px solid var(--black);
  transition: box-shadow 0.2s;
}
.search-box:focus-within { box-shadow: 6px 6px 0 var(--black); }
.search-input {
  flex: 1; border: none; outline: none;
  padding: 1rem 1.5rem; font-size: 1.125rem;
}
.search-input::placeholder { color: var(--gray-400); }
.search-btn {
  display: flex; align-items: center; gap: 0.5rem;
  padding: 0 2rem;
  background: var(--black); color: var(--white);
  font-size: 0.9375rem; font-weight: 600; letter-spacing: 0.04em;
  text-transform: uppercase;
  transition: background 0.2s;
}
.search-btn:hover { background: var(--red); }
.arrow { font-size: 1.25rem; }

/* 标签 */
.tags { display: flex; align-items: center; gap: 0.5rem; margin-top: 1.5rem; flex-wrap: wrap; }
.tag-label { font-size: 0.8125rem; font-weight: 500; color: var(--gray-400); }
.tag {
  padding: 0.375rem 1rem;
  font-size: 0.8125rem; font-weight: 500;
  border: 1.5px solid var(--gray-200);
  transition: border-color 0.2s;
}
.tag:hover { border-color: var(--black); }

/* 底部三色条 */
.hero-stripes {
  display: grid; grid-template-columns: 3fr 1fr 1fr 3fr;
  height: 6px; width: 100%; margin-top: auto;
}
</style>
