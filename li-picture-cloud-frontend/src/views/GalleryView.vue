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

      <!-- 搜索与筛选栏 -->
      <div class="search-bar">
        <div class="search-box-inline">
          <input
            v-model="query.searchText"
            class="input"
            placeholder="搜索图片名称或简介…"
            @keyup.enter="handleSearch"
          />
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
        </div>
        <div class="filter-row">
          <select v-model="query.category" class="input filter-select" @change="handleSearch">
            <option value="">全部分类</option>
            <option v-for="c in categoryList" :key="c" :value="c">{{ c }}</option>
          </select>
          <select v-model="query.picFormat" class="input filter-select" @change="handleSearch">
            <option value="">全部格式</option>
            <option value="jpg">JPG</option>
            <option value="jpeg">JPEG</option>
            <option value="png">PNG</option>
            <option value="webp">WEBP</option>
          </select>
          <select v-model="query.sortOrder" class="input filter-select" @change="handleSearch">
            <option value="descend">最新优先</option>
            <option value="ascend">最早优先</option>
          </select>
        </div>
        <!-- 标签筛选 -->
        <div class="tag-filters" v-if="tagList.length">
          <button
            v-for="t in tagList"
            :key="t"
            class="tag-chip"
            :class="{ active: query.tags && query.tags.includes(t) }"
            @click="toggleTag(t)"
          >
            {{ t }}
          </button>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="loading">加载中…</div>

      <!-- 空状态 -->
      <div v-else-if="pictures.length === 0" class="empty-state">
        <p>暂无图片，上传第一张吧！</p>
      </div>

      <!-- 图片网格 -->
      <div v-else class="gallery-grid">
        <div
          v-for="pic in pictures"
          :key="pic.id"
          class="gallery-card"
          @click="goDetail(pic.id)"
        >
          <img
            :src="pic.url"
            :alt="pic.name || '图片'"
            class="card-img"
            loading="lazy"
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
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="total > query.pageSize">
        <button :disabled="query.current <= 1" @click="goPage(query.current - 1)">上一页</button>
        <span>第 {{ query.current }} 页 / 共 {{ totalPages }} 页 ({{ total }} 张)</span>
        <button :disabled="query.current >= totalPages" @click="goPage(query.current + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listPictureVOByPage, getPictureTagCategory } from '@/api/picture'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const pictures = ref([])
const loading = ref(false)
const total = ref(0)
const tagList = ref([])
const categoryList = ref([])

const query = reactive({
  current: 1,
  pageSize: 12,
  searchText: route.query.q || '',
  category: '',
  picFormat: '',
  tags: null,
  sortField: 'createTime',
  sortOrder: 'descend'
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))

onMounted(async () => {
  try {
    const meta = await getPictureTagCategory()
    tagList.value = meta.tagList || []
    categoryList.value = meta.categoryList || []
  } catch { /* 标签加载失败不影响主流程 */ }
  loadPictures()
})

async function loadPictures() {
  loading.value = true
  try {
    const res = await listPictureVOByPage({ ...query })
    pictures.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error('加载图片失败', e)
    pictures.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.current = 1
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

function goPage(page) {
  query.current = page
  loadPictures()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goDetail(id) {
  router.push(`/picture/${id}`)
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
.search-box-inline { display: flex; gap: 0.5rem; margin-bottom: 1rem; }
.search-box-inline .input { flex: 1; max-width: 480px; }

.filter-row { display: flex; gap: 0.75rem; flex-wrap: wrap; margin-bottom: 1rem; }
.filter-select { max-width: 160px; }

/* 标签筛选 */
.tag-filters { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.tag-chip {
  padding: 0.375rem 1rem; font-size: 0.8125rem; font-weight: 500;
  border: 1.5px solid var(--gray-200); background: var(--white);
  transition: all 0.2s; cursor: pointer;
}
.tag-chip:hover { border-color: var(--black); }
.tag-chip.active { background: var(--black); color: var(--white); border-color: var(--black); }

/* 状态 */
.loading { text-align: center; padding: 4rem 0; color: var(--gray-400); font-size: 1.125rem; }
.empty-state { text-align: center; padding: 5rem 0; color: var(--gray-400); }
.empty-state p { font-size: 1.125rem; }

/* 图片网格 */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}
.gallery-card {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid var(--gray-200);
  transition: transform 0.3s, box-shadow 0.3s;
}
.gallery-card:hover { transform: translate(-2px, -2px); box-shadow: 6px 6px 0 var(--black); }
.card-img {
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
  margin-top: 3rem; font-size: 0.875rem;
}
.pagination button { font-weight: 600; padding: 0.5rem 1rem; border: 2px solid var(--black); }
.pagination button:disabled { opacity: 0.3; cursor: default; }
.pagination button:hover:not(:disabled) { background: var(--black); color: var(--white); }
</style>
