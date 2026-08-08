<template>
  <div class="admin-page">
    <div class="container">
      <div class="admin-header">
        <h1>图片管理</h1>
        <div class="header-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            class="tab-btn"
            :class="{ active: activeTab === tab.value }"
            @click="switchTab(tab.value)"
          >
            {{ tab.label }} {{ tab.count !== null ? '(' + tab.count + ')' : '' }}
          </button>
        </div>
      </div>

      <!-- 以图搜图 -->
      <details class="batch-panel">
        <summary class="batch-toggle">🔍 以图搜图（Bing）</summary>
        <div class="batch-form">
          <p class="form-hint">粘贴图片 URL，Bing 将搜索视觉相似的图片并返回结果。</p>
          <div class="batch-row">
            <input v-model="searchImageUrl" class="input" placeholder="粘贴图片 URL" style="flex:1" @keyup.enter="handleSearchImage" />
            <button class="btn btn-primary btn-sm" @click="handleSearchImage" :disabled="searching">
              {{ searching ? '搜索中…' : '搜索' }}
            </button>
          </div>
          <div v-if="searchError" class="form-error">{{ searchError }}</div>
          <!-- 搜索结果 -->
          <div v-if="searchResults.length" class="search-results">
            <div class="result-header">
              <span>找到 {{ searchResults.length }} 张相似图片</span>
              <button class="btn btn-outline btn-sm" @click="searchResults = []">清除</button>
            </div>
            <div class="result-grid">
              <div v-for="(img, idx) in searchResults" :key="idx" class="result-card">
                <img :src="img.thumbUrl" :alt="idx + 1" class="result-thumb" />
                <div class="result-actions">
                  <button class="btn-sm-text primary" @click="uploadSearchResult(img)">上传</button>
                  <a :href="img.fromUrl" target="_blank" rel="noopener noreferrer" class="btn-sm-text">来源</a>
                </div>
              </div>
            </div>
          </div>
        </div>
      </details>

      <!-- 批量抓取 -->
      <details class="batch-panel">
        <summary class="batch-toggle">🔍 从必应批量抓取图片</summary>
        <div class="batch-form">
          <div class="batch-row">
            <input v-model="batch.searchText" class="input" placeholder="搜索关键词（如：风景壁纸）" style="flex:1" @keyup.enter="handleBatchUpload" />
            <input v-model.number="batch.count" class="input" type="number" min="1" max="30" style="max-width:80px" placeholder="数量" title="每次抓取数量" />
            <input v-model.number="batch.offset" class="input" type="number" min="0" style="max-width:100px" placeholder="偏移量" title="从第几张开始（翻页用，默认0）" />
            <button class="btn btn-primary btn-sm" @click="handleBatchUpload" :disabled="batching">
              {{ batching ? '抓取中…' : '开始抓取' }}
            </button>
          </div>
          <div v-if="batchError" class="form-error">{{ batchError }}</div>
          <div v-if="batchSuccess" class="form-success">{{ batchSuccess }}</div>
        </div>
      </details>

      <!-- 搜索栏 -->
      <div class="toolbar">
        <input v-model="query.searchText" class="input" style="max-width:240px" placeholder="搜索名称/简介" @keyup.enter="loadPictures" />
        <select v-model="query.category" class="input" style="max-width:150px" @change="loadPictures">
          <option value="">全部分类</option>
          <option v-for="c in categoryList" :key="c" :value="c">{{ c }}</option>
        </select>
        <input v-model="query.startEditTime" type="date" class="input" style="max-width:160px" title="编辑时间从" @change="loadPictures" />
        <span class="date-sep">—</span>
        <input v-model="query.endEditTime" type="date" class="input" style="max-width:160px" title="编辑时间至" @change="loadPictures" />
        <button class="btn btn-outline btn-sm" @click="clearFilter">清除筛选</button>
        <button class="btn btn-outline btn-sm" @click="loadPictures">刷新</button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="loading">加载中…</div>

      <!-- 表格 -->
      <table v-else class="table">
        <thead>
          <tr>
            <th style="width:80px">缩略图</th>
            <th>名称</th>
            <th>上传者</th>
            <th style="width:100px">审核状态</th>
            <th style="width:140px">上传时间</th>
            <th style="width:200px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="pic in pictures" :key="pic.id">
            <td data-label="缩略图">
              <img :src="pic.thumbnailUrl || pic.url" class="thumb" :alt="pic.name" />
            </td>
            <td data-label="名称">
              <router-link :to="'/picture/' + pic.id" class="pic-link">{{ pic.name || '(无名称)' }}</router-link>
            </td>
            <td data-label="上传者">{{ pic.user?.userName || '-' }}</td>
            <td data-label="审核状态">
              <span class="review-badge" :class="reviewClass(pic.reviewStatus)">
                {{ reviewLabel(pic.reviewStatus) }}
              </span>
            </td>
            <td class="date-cell" data-label="上传时间">{{ formatDate(pic.createTime) }}</td>
            <td class="action-cell" data-label="操作">
              <template v-if="pic.reviewStatus === 0">
                <button class="btn-sm-text primary" @click="quickReview(pic.id, 1)">通过</button>
                <button class="btn-sm-text danger" @click="promptReject(pic.id)">拒绝</button>
              </template>
              <template v-else>
                <button class="btn-sm-text" @click="goDetail(pic.id)">查看</button>
                <button class="btn-sm-text danger" @click="handleDelete(pic.id)">删除</button>
              </template>
            </td>
          </tr>
          <tr v-if="pictures.length === 0">
            <td colspan="6" class="empty-cell">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="pagination" v-if="total > query.pageSize">
        <button :disabled="query.current <= 1" @click="goPage(query.current - 1)">上一页</button>
        <span>第 {{ query.current }} / {{ totalPages }} 页 ({{ total }} 张)</span>
        <button :disabled="query.current >= totalPages" @click="goPage(query.current + 1)">下一页</button>
        <span class="jumper">跳至
          <input v-model.number="jumpPage" class="jump-input" @keyup.enter="goPage(jumpPage)" placeholder="页数" />
          页
          <button class="btn-jump" @click="goPage(jumpPage)">GO</button>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listPictureByPage, reviewPicture, deletePicture, getPictureTagCategory, uploadPictureByBatch, getImageSearchUrl } from '@/api/picture'
import request from '@/api/request'

const router = useRouter()
const userStore = useUserStore()

// 权限守卫
if (!userStore.isAdmin) router.replace('/login')

const pictures = ref([])
const loading = ref(false)
const total = ref(0)
const categoryList = ref([])

const tabs = [
  { label: '全部', value: '' },
  { label: '待审核', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已拒绝', value: 2 },
]
const activeTab = ref('')

const query = reactive({
  current: 1,
  pageSize: 10,
  searchText: '',
  category: '',
  reviewStatus: null,
  startEditTime: '',
  endEditTime: '',
  sortField: 'createTime',
  sortOrder: 'descend'
})

function clearFilter() {
  query.searchText = ''
  query.category = ''
  query.startEditTime = ''
  query.endEditTime = ''
  query.current = 1
  loadPictures()
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const jumpPage = ref(null)
function goPage(page) {
  if (!page || page < 1) page = 1
  if (page > totalPages.value) page = totalPages.value
  query.current = page
  jumpPage.value = null
  loadPictures()
}

// 以图搜图
const searchImageUrl = ref('')
const searching = ref(false)
const searchError = ref('')
const searchResults = ref([])

async function handleSearchImage() {
  searchError.value = ''
  searchResults.value = []
  if (!searchImageUrl.value.trim()) {
    searchError.value = '请输入图片 URL'
    return
  }
  searching.value = true
  try {
    const results = await getImageSearchUrl(searchImageUrl.value.trim())
    searchResults.value = results || []
    if (searchResults.value.length === 0) {
      searchError.value = '未找到相似图片'
    }
  } catch (e) {
    searchError.value = e.message || '搜索失败'
  } finally {
    searching.value = false
  }
}

async function uploadSearchResult(img) {
  if (!img.fromUrl) return
  try {
    await request.post('/picture/upload/url', {
      fileUrl: img.fromUrl,
      picName: '以图搜图结果'
    })
    alert('上传成功！等待审核后可见')
    loadPictures()
  } catch (e) {
    alert(e.message || '上传失败')
  }
}

// 批量抓取
const batch = reactive({ searchText: '', count: 10, offset: 0 })
const batching = ref(false)
const batchError = ref('')
const batchSuccess = ref('')

async function handleBatchUpload() {
  batchError.value = ''
  batchSuccess.value = ''
  if (!batch.searchText.trim()) {
    batchError.value = '请输入搜索关键词'
    return
  }
  batching.value = true
  try {
    const count = await uploadPictureByBatch({
      searchText: batch.searchText.trim(),
      count: batch.count || 10,
      offset: batch.offset || 0
    })
    batchSuccess.value = `成功抓取 ${count} 张图片！`
    batch.searchText = ''
    loadPictures()
  } catch (e) {
    batchError.value = e.message || '批量抓取失败'
  } finally {
    batching.value = false
  }
}

onMounted(async () => {
  try {
    const meta = await getPictureTagCategory()
    categoryList.value = meta.categoryList || []
  } catch { /* ignore */ }
  loadPictures()
})

function switchTab(status) {
  activeTab.value = status
  query.current = 1
  query.reviewStatus = status === '' ? null : status
  loadPictures()
}

async function loadPictures() {
  loading.value = true
  try {
    // 将空字符串日期转为 undefined，避免后端解析空字符串报错
    const params = { ...query }
    if (!params.startEditTime) params.startEditTime = undefined
    if (!params.endEditTime) params.endEditTime = undefined
    const res = await listPictureByPage(params)
    pictures.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error('加载图片失败', e)
  } finally {
    loading.value = false
  }
}

async function quickReview(id, status) {
  try {
    await reviewPicture({ id, reviewStatus: status, reviewMessage: status === 1 ? '审核通过' : '已拒绝' })
    loadPictures()
  } catch (e) {
    alert(e.message || '审核失败')
  }
}

function promptReject(id) {
  const msg = prompt('请输入拒绝理由（可选）：')
  if (msg === null) return // 用户取消
  quickReview(id, 2)
}

function goDetail(id) {
  router.push(`/picture/${id}`)
}

async function handleDelete(id) {
  if (!confirm('确定删除该图片？')) return
  try {
    await deletePicture(id)
    loadPictures()
  } catch (e) {
    alert(e.message || '删除失败')
  }
}

function reviewClass(s) {
  if (s === 1) return 'review-pass'
  if (s === 2) return 'review-reject'
  return 'review-pending'
}

function reviewLabel(s) {
  if (s === 1) return '✅ 通过'
  if (s === 2) return '❌ 拒绝'
  return '⏳ 待审'
}

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '-'
}
</script>

<style scoped>
.admin-page { padding: 3rem 0 5rem; }
.admin-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 1.5rem; flex-wrap: wrap; gap: 1rem;
}
.admin-header h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; }

/* 标签页 */
.header-tabs { display: flex; gap: 0.5rem; }
.tab-btn {
  padding: 0.5rem 1.25rem; font-size: 0.8125rem; font-weight: 600;
  border: 2px solid var(--gray-200); background: var(--white);
  transition: all 0.2s; cursor: pointer;
}
.tab-btn:hover { border-color: var(--black); }
.tab-btn.active { background: var(--black); color: var(--white); border-color: var(--black); }

/* 批量抓取面板 */
.batch-panel {
  margin-bottom: 1.5rem; border: 2px solid var(--gray-200); padding: 1rem 1.25rem;
}
.batch-toggle {
  font-size: 0.875rem; font-weight: 600; cursor: pointer; user-select: none;
}
.batch-toggle:hover { color: var(--red); }
.batch-form { margin-top: 0.75rem; }
.batch-row { display: flex; gap: 0.75rem; align-items: center; }
.form-error {
  padding: 0.5rem 0.75rem; background: #FFF0EF; color: var(--red);
  font-size: 0.8125rem; font-weight: 500; margin-top: 0.5rem;
}
.form-success {
  padding: 0.5rem 0.75rem; background: #EFF9F0; color: #1A7A2E;
  font-size: 0.8125rem; font-weight: 500; margin-top: 0.5rem;
}

.form-hint { font-size: 0.8125rem; color: var(--gray-400); margin-bottom: 0.5rem; }
.btn-sm-text { font-size: 0.75rem; font-weight: 500; cursor: pointer; border: none; background: none; text-decoration: underline; }
.btn-sm-text.primary { color: var(--blue); }
.btn-sm-text:hover { opacity: 0.7; }

.toolbar { display: flex; gap: 0.75rem; margin-bottom: 1.5rem; flex-wrap: wrap; align-items: center; }
.date-sep { font-size: 0.875rem; color: var(--gray-400); }
.btn-sm { padding: 0.5rem 1rem; font-size: 0.75rem; }

/* 表格 */
.table { width: 100%; border-collapse: collapse; }
.table th, .table td { text-align: left; padding: 0.75rem 1rem; border-bottom: 1px solid var(--gray-200); }
.table th { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--gray-400); }
.table td { font-size: 0.9375rem; vertical-align: middle; }

.thumb { width: 60px; aspect-ratio: 4/3; object-fit: cover; border: 1px solid var(--gray-200); }
.pic-link { font-weight: 500; text-decoration: underline; }
.pic-link:hover { color: var(--red); }

.review-badge { display: inline-block; padding: 0.125rem 0.625rem; font-size: 0.75rem; font-weight: 600; }
.review-pending { background: var(--yellow); color: var(--black); }
.review-pass { background: #EFF9F0; color: #1A7A2E; border: 1px solid #1A7A2E; }
.review-reject { background: #FFF0EF; color: var(--red); border: 1px solid var(--red); }

.date-cell { font-size: 0.875rem; color: var(--gray-600); white-space: nowrap; }
.action-cell { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.btn-sm-text { font-size: 0.8125rem; font-weight: 500; text-decoration: underline; cursor: pointer; border: none; background: none; }
.btn-sm-text.primary { color: #1A7A2E; }
.btn-sm-text.danger { color: var(--red); }
.btn-sm-text:hover { opacity: 0.7; }
.empty-cell { text-align: center; padding: 3rem 0; color: var(--gray-400); }

.loading { text-align: center; padding: 4rem 0; color: var(--gray-400); }

/* 分页 */
.pagination { display: flex; align-items: center; justify-content: center; gap: 1.5rem; margin-top: 2rem; font-size: 0.875rem; }
.pagination button { font-weight: 600; padding: 0.5rem 1rem; border: 2px solid var(--black); }
.pagination button:disabled { opacity: 0.3; cursor: default; }
.jumper { font-size: 0.8125rem; color: var(--gray-600); display: flex; align-items: center; gap: 0.375rem; }
.jump-input { width: 56px; padding: 0.375rem 0.5rem; text-align: center; border: 1.5px solid var(--gray-200); font-size: 0.8125rem; font-family: inherit; outline: none; }
.jump-input:focus { border-color: var(--black); }
.btn-jump { padding: 0.25rem 0.625rem; font-size: 0.75rem; font-weight: 600; border: 1.5px solid var(--black); background: var(--white); cursor: pointer; }
.btn-jump:hover { background: var(--black); color: var(--white); }

@media (max-width: 767px) {
  .admin-page { padding-block: 2rem 3rem; }
  .admin-header { align-items: stretch; flex-direction: column; gap: 1rem; }
  .admin-header h1 { font-size: 1.75rem; }
  .header-tabs { width: 100%; overflow-x: auto; }
  .tab-btn { flex: 0 0 auto; min-height: 44px; }
  .batch-panel { padding: 0.75rem; }
  .batch-row, .toolbar { align-items: stretch; flex-direction: column; }
  .batch-row .input, .toolbar .input { max-width: none !important; }
  .date-sep { display: none; }
  .result-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .table, .table tbody, .table tr, .table td { display: block; width: 100%; }
  .table thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  .table tr { margin-bottom: 1rem; padding: 0.5rem 1rem; border: 2px solid var(--gray-200); }
  .table td { display: grid; grid-template-columns: minmax(6.5rem, 35%) 1fr; gap: 0.75rem; padding: 0.75rem 0; overflow-wrap: anywhere; }
  .table td::before { content: attr(data-label); color: var(--gray-600); font-size: 0.75rem; font-weight: 700; }
  .table .empty-cell { display: block; text-align: center; }
  .table .empty-cell::before { content: none; }
  .thumb { width: min(7rem, 100%); }
  .action-cell { align-items: center; flex-wrap: wrap; }
  .btn-sm-text { min-height: 44px; padding-inline: 0.75rem; }
  .pagination { gap: 0.75rem; flex-wrap: wrap; }
  .jumper { display: none; }
}
</style>
