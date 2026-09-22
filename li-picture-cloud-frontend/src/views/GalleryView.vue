<template>
  <div class="gallery-page">
    <div class="container">
      <!-- 页头：标题 + 上传按钮 -->
      <div class="gallery-header">
        <h1>探索图库</h1>
        <div class="header-right">
          <button v-if="userStore.isAdmin" class="btn btn-primary btn-sm" @click="$router.push('/upload')">
            + 上传图片
          </button>
        </div>
      </div>

      <!-- 只整理检索控件；标签继续全部常驻。 -->
      <div class="search-bar">
        <div class="retrieval-controls">
          <form class="search-box-inline" role="search" aria-label="图库搜索" @submit.prevent="handleSearch">
            <label class="search-shell">
              <span class="lp-visually-hidden">搜索图片名称或简介</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
                <circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4.5 4.5" />
              </svg>
              <input v-model="query.searchText" type="search" placeholder="搜索图片名称或简介…" />
            </label>
            <button type="submit" class="btn btn-primary search-submit">搜索</button>
          </form>
          <div class="filter-row">
            <select v-model="query.category" class="filter-select" aria-label="分类" @change="handleSearch">
              <option value="">全部分类</option>
              <option v-for="c in categoryList" :key="c" :value="c">{{ c }}</option>
            </select>
            <select v-model="query.picFormat" class="filter-select" aria-label="格式" @change="handleSearch">
              <option value="">全部格式</option>
              <option value="jpg">JPG</option>
              <option value="jpeg">JPEG</option>
              <option value="png">PNG</option>
              <option value="webp">WEBP</option>
            </select>
            <select v-model="query.sortOrder" class="filter-select" aria-label="排序" @change="handleSearch">
              <option value="descend">最新优先</option>
              <option value="ascend">最早优先</option>
            </select>
            <button class="clear-filters" :disabled="!hasFilters" @click="clearFilters">清除筛选</button>
          </div>
        </div>
        <div v-if="tagList.length" class="tag-filters" role="group" aria-label="标签筛选（同时满足）">
          <button
            v-for="t in tagList"
            :key="t"
            class="tag-chip"
            :class="{ active: query.tags?.includes(t) }"
            :aria-pressed="Boolean(query.tags?.includes(t))"
            @click="toggleTag(t)"
          >
            <span v-if="query.tags?.includes(t)" aria-hidden="true">✓ </span>
            {{ t }}
          </button>
        </div>
        <p v-if="metadataError" class="metadata-error" role="status">
          分类和标签暂时无法加载。
          <button class="clear-filters" :disabled="metadataLoading" @click="loadMetadata">
            {{ metadataLoading ? '重试中…' : '重试筛选项' }}
          </button>
        </p>
        <p v-if="appliedConditions.length" class="applied-summary" role="status">
          {{ appliedConditions.join(' · ') }}<span v-if="!loading && !loadError"> · {{ total }} 张</span>
        </p>
      </div>

      <div v-if="loading" role="status" aria-busy="true">
        <p class="lp-visually-hidden">正在加载图库…</p>
        <div class="gallery-grid" aria-hidden="true">
          <div v-for="index in query.pageSize" :key="index" class="gallery-skeleton" />
        </div>
      </div>

      <LpStateBlock v-else-if="loadError" class="gallery-state" status="error" title="图库加载失败" message="暂时无法获取图片，请稍后重试。筛选条件已保留。">
        <template #action><button class="btn btn-outline" @click="loadPictures">重试</button></template>
      </LpStateBlock>

      <LpStateBlock
        v-else-if="pictures.length === 0"
        class="gallery-state"
        :title="hasAppliedFilters ? '没有找到匹配的图片' : '图库暂时没有图片'"
        :message="hasAppliedFilters ? '试试其他关键词，或清除筛选条件。多个标签需要同时满足。' : '稍后再来看看。'"
      >
        <template v-if="hasAppliedFilters" #action><button class="btn btn-outline" @click="clearFilters">清除筛选</button></template>
      </LpStateBlock>

      <!-- 图片网格 -->
      <div v-else class="gallery-grid">
        <RouterLink
          v-for="pic in pictures"
          :key="pic.id"
          class="gallery-card"
          :to="`/picture/${pic.id}`"
          :aria-label="`查看图片：${pic.name || '未命名'}`"
        >
          <div v-if="failedImages.has(pic.id)" class="image-fallback">预览暂不可用<br />可进入详情查看</div>
          <img
            v-else
            :src="pic.thumbnailUrl || pic.url"
            :alt="pic.name || '图片'"
            class="card-img"
            loading="lazy"
            decoding="async"
            @error="failedImages.add(pic.id)"
          />
          <div class="card-overlay">
            <h3 class="card-name">{{ pic.name || '未命名' }}</h3>
            <p class="card-meta" v-if="pic.user">
              {{ pic.user.userName }} · {{ formatDate(pic.createTime) }}
            </p>
            <div class="card-tags" v-if="pic.tags && pic.tags.length">
              <span v-for="tag in pic.tags.slice(0, 3)" :key="tag" class="mini-tag">{{ tag }}</span>
            </div>
          </div>
        </RouterLink>
      </div>

      <!-- 手机同样保留页码输入 + Enter / GO。 -->
      <nav class="pagination" aria-label="图库分页" v-if="!loading && !loadError && total > query.pageSize">
        <button :disabled="query.current <= 1" @click="goPage(query.current - 1)">上一页</button>
        <span class="page-summary" aria-live="polite">第 {{ query.current }} / {{ totalPages }} 页 ({{ total }} 张)</span>
        <button :disabled="query.current >= totalPages" @click="goPage(query.current + 1)">下一页</button>
        <form class="jumper" @submit.prevent="goPage(jumpPage)">跳至
          <input v-model="jumpPage" class="jump-input" inputmode="numeric" aria-label="跳转页码" placeholder="页数" @focus="keepJumpVisible" />
          页
          <button type="submit" class="btn-jump">GO</button>
        </form>
      </nav>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listPictureVOByPage, getPictureTagCategory } from '@/api/picture'
import LpStateBlock from '@/components/ui/LpStateBlock.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const pictures = ref([])
const loading = ref(true)
const loadError = ref(false)
const metadataLoading = ref(false)
const metadataError = ref(false)
const failedImages = ref(new Set())
const total = ref(0)
const tagList = ref([])
const categoryList = ref([])

const query = reactive({
  current: normalizePage(route.query.page),
  pageSize: 12,
  searchText: typeof route.query.q === 'string' ? route.query.q : '',
  category: '',
  picFormat: '',
  tags: null,
  sortField: 'createTime',
  sortOrder: 'descend'
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const jumpPage = ref('')
const appliedQuery = ref({ ...query })
const hasFilters = computed(() => Boolean(query.searchText || query.category || query.picFormat || query.tags?.length || query.sortOrder !== 'descend'))
const hasAppliedFilters = computed(() => Boolean(appliedQuery.value.searchText || appliedQuery.value.category || appliedQuery.value.picFormat || appliedQuery.value.tags?.length))
const appliedConditions = computed(() => {
  const applied = appliedQuery.value
  return [
    applied.searchText && `关键词：${applied.searchText}`,
    applied.category && `分类：${applied.category}`,
    applied.picFormat && `格式：${applied.picFormat.toUpperCase()}`,
    applied.sortOrder === 'ascend' && '最早优先',
    applied.tags?.length && `标签同时满足：${applied.tags.join('、')}`
  ].filter(Boolean)
})
let requestId = 0
let active = true

function normalizePage(value) {
  const page = Number(value)
  return Number.isFinite(page) ? Math.max(1, Math.floor(page)) : 1
}

function syncUrl() {
  const q = {}
  if (query.current > 1) q.page = query.current
  if (query.searchText) q.q = query.searchText
  router.replace({ query: q })
}

function goPage(page) {
  query.current = Math.min(normalizePage(page), totalPages.value)
  jumpPage.value = ''
  syncUrl()
  loadPictures()
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: reduceMotion ? 'instant' : 'smooth' })
}

function keepJumpVisible(event) {
  // Leave room for the existing mobile bottom bar when input blur restores it.
  if (window.matchMedia('(max-width: 767px)').matches) {
    event.target.scrollIntoView({ block: 'nearest', behavior: 'instant' })
  }
}

onMounted(() => {
  loadMetadata()
  loadPictures()
})

onBeforeUnmount(() => {
  active = false
  requestId += 1
})

async function loadMetadata() {
  metadataLoading.value = true
  try {
    const meta = await getPictureTagCategory()
    if (!active) return
    tagList.value = meta.tagList || []
    categoryList.value = meta.categoryList || []
    metadataError.value = false
  } catch {
    if (active) metadataError.value = true
  } finally {
    if (active) metadataLoading.value = false
  }
}

async function loadPictures() {
  // A slower, superseded request must not replace the newest filter results/state.
  const currentRequest = ++requestId
  const snapshot = { ...query, tags: query.tags?.length ? [...query.tags] : null }
  appliedQuery.value = snapshot
  loading.value = true
  loadError.value = false
  failedImages.value = new Set()
  try {
    const res = await listPictureVOByPage(snapshot)
    if (currentRequest !== requestId) return
    pictures.value = res.records || []
    total.value = res.total || 0
    // A saved page may no longer exist after records were removed.
    if (query.current > totalPages.value) {
      query.current = totalPages.value
      syncUrl()
      return loadPictures()
    }
  } catch {
    if (currentRequest !== requestId) return
    pictures.value = []
    total.value = 0
    loadError.value = true
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

function handleSearch() {
  query.current = 1
  jumpPage.value = ''
  syncUrl()
  loadPictures()
}

function toggleTag(tag) {
  if (!query.tags) query.tags = []
  const idx = query.tags.indexOf(tag)
  if (idx >= 0) {
    query.tags.splice(idx, 1)
    if (query.tags.length === 0) query.tags = null
  } else {
    query.tags.push(tag)
  }
  handleSearch()
}

function clearFilters() {
  Object.assign(query, { searchText: '', category: '', picFormat: '', tags: null, sortOrder: 'descend' })
  handleSearch()
}

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '-'
}
</script>

<style scoped>
.gallery-page { padding: 3rem 0 5rem; }

/* 页头 */
.gallery-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 1.5rem; flex-wrap: wrap; gap: 1rem;
}
.gallery-header h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; }
.header-right { display: flex; gap: 0.75rem; }
.btn-sm { padding: 0.5rem 1.25rem; font-size: 0.75rem; }

/* 搜索栏 */
.search-bar { margin-bottom: 2rem; }
.retrieval-controls { display: flex; flex-wrap: wrap; gap: 0.75rem 1rem; margin-bottom: 1rem; }
.search-box-inline { display: flex; flex: 1 1 360px; min-width: 0; max-width: 580px; gap: 0.5rem; }
.search-shell {
  display: flex; flex: 1; align-items: center; gap: 10px; min-width: 0; height: 48px; padding: 0 14px;
  border: 1px solid var(--lp-border); border-radius: var(--lp-radius-m); background: var(--lp-surface);
  transition: border-color var(--lp-dur-fast), box-shadow var(--lp-dur-fast);
}
.search-shell:focus-within { border-color: var(--lp-focus-ring); box-shadow: 0 0 0 3px var(--lp-accent-soft); }
.search-shell svg { width: 18px; height: 18px; flex: none; color: var(--lp-text-secondary); }
.search-shell input { width: 100%; min-width: 0; height: 44px; padding: 0; border: 0; background: transparent; font-size: 0.9375rem; }
/* The whole input shell provides the focus indicator, including keyboard focus. */
.search-shell input:focus { outline: none; }
.search-shell input::placeholder { color: var(--lp-text-secondary); }
.search-submit { height: 48px; flex: none; padding: 10px 24px; border-radius: var(--lp-radius-m); }
.filter-row { display: flex; flex: 0 1 520px; gap: 10px; flex-wrap: wrap; }
.filter-select {
  flex: 1 1 116px; min-width: 116px; max-width: 144px; height: 48px; padding: 8px 12px;
  border: 1px solid var(--lp-border); border-radius: var(--lp-radius-m);
  background: var(--lp-surface); font-size: 0.9375rem;
}
.filter-select:focus-visible { border-color: var(--lp-focus-ring); }
.clear-filters { min-height: 48px; padding: 8px 12px; color: var(--lp-accent-strong); font-size: 0.875rem; }
.clear-filters:hover:not(:disabled) { text-decoration: underline; }
.clear-filters:disabled { color: var(--lp-text-secondary); opacity: 0.6; cursor: default; }
.applied-summary, .metadata-error { margin-top: 0.75rem; color: var(--lp-text-secondary); font-size: 0.8125rem; overflow-wrap: anywhere; }

/* 标签筛选 */
.tag-filters { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.tag-chip {
  min-height: 44px; max-width: 100%; padding: 0.5rem 1rem; font-size: 0.8125rem; font-weight: 500;
  border: 1px solid var(--lp-border); border-radius: var(--lp-radius-s); background: var(--lp-surface);
  overflow-wrap: anywhere;
  transition: border-color var(--lp-dur-fast), background-color var(--lp-dur-fast); cursor: pointer;
}
.tag-chip:hover { border-color: var(--lp-border-strong); }
.tag-chip.active { background: var(--lp-accent-soft); color: var(--lp-accent-strong); border-color: var(--lp-accent); }

/* 状态 */
.gallery-state { padding: 5rem 0; background: transparent; }
.gallery-skeleton { aspect-ratio: 4 / 3; min-width: 0; background: var(--lp-bg-subtle); border: 2px solid var(--lp-border); }
.image-fallback { position: absolute; inset: 0; padding: 1rem; color: var(--lp-text-secondary); font-size: 0.8125rem; text-align: center; }

/* 图片网格 */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}
.gallery-card {
  display: block;
  min-width: 0;
  min-height: 0;
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid var(--gray-200);
  background: var(--lp-bg-subtle);
  transition: transform 0.3s, box-shadow 0.3s;
}
.gallery-card:hover { transform: translate(-2px, -2px); box-shadow: 6px 6px 0 var(--black); }
.gallery-card:focus-visible { outline: 2px solid var(--lp-focus-ring); outline-offset: 4px; box-shadow: 6px 6px 0 var(--black); }
.card-img {
  position: absolute; inset: 0; display: block;
  width: 100%; height: 100%;
  object-fit: cover;
  transition: transform 0.4s;
}
.gallery-card:hover .card-img { transform: scale(1.05); }

.card-overlay {
  position: absolute; bottom: 0; left: 0; right: 0;
  padding: 1.5rem 1.25rem;
  background: linear-gradient(transparent, rgba(0,0,0,0.75));
  color: var(--white);
  overflow-wrap: anywhere;
}
.card-name { font-size: 1rem; font-weight: 600; margin-bottom: 0.25rem; }
.card-meta { font-size: 0.75rem; opacity: 0.85; margin-bottom: 0.5rem; }
.card-tags { display: flex; flex-wrap: wrap; gap: 0.375rem; }
.mini-tag {
  padding: 0.125rem 0.5rem; font-size: 0.6875rem;
  background: rgba(255,255,255,0.2);
}

/* 分页 */
.pagination {
  display: flex; align-items: center; justify-content: center; gap: 1.5rem;
  flex-wrap: wrap;
  margin-top: 3rem; font-size: 0.875rem;
}
.pagination button { min-height: 44px; font-weight: 600; padding: 0.5rem 1rem; border: 2px solid var(--black); }
.pagination button:disabled { opacity: 0.3; cursor: default; }
.pagination button:hover:not(:disabled) { background: var(--black); color: var(--white); }
.jumper { font-size: 0.8125rem; color: var(--gray-600); display: flex; align-items: center; gap: 0.375rem; }
.jump-input {
  width: 64px; min-height: 44px; padding: 0.375rem 0.5rem; text-align: center;
  border: 1.5px solid var(--gray-200); font-size: 0.8125rem; font-family: inherit; background: var(--lp-surface);
}
.jump-input:focus { border-color: var(--black); }
.btn-jump { padding: 0.25rem 0.625rem; font-size: 0.75rem; font-weight: 600; border: 1.5px solid var(--black); background: var(--white); cursor: pointer; }
.btn-jump:hover { background: var(--black); color: var(--white); }

@media (max-width: 767px) {
  .gallery-page { padding-block: 2rem 3rem; }
  .gallery-header h1 { font-size: 1.75rem; }
  .search-box-inline { flex-basis: 100%; width: 100%; max-width: none; }
  .filter-row { flex-basis: 100%; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-select { width: 100%; min-width: 0; max-width: none; }
  .gallery-grid { grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 1rem; }
  .pagination { gap: 0.75rem; margin-top: 2rem; flex-wrap: wrap; }
  .page-summary { flex-basis: 100%; text-align: center; order: -1; }
  .jumper { flex-basis: 100%; justify-content: center; }
  .jump-input { scroll-margin-block: calc(80px + env(safe-area-inset-bottom)); }
}

@media (max-width: 480px) {
  .gallery-grid { grid-template-columns: 1fr; }
}

@media (hover: none) {
  .gallery-card:hover { transform: none; box-shadow: none; }
  .gallery-card:hover .card-img { transform: none; }
  .gallery-card:focus-visible { box-shadow: 6px 6px 0 var(--black); }
}

@media (prefers-reduced-motion: reduce) {
  .gallery-card, .card-img { transition: none; }
  .gallery-card:hover, .gallery-card:hover .card-img { transform: none; }
}
</style>
