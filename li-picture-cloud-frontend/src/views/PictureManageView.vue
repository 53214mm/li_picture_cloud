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

      <!-- 搜索栏 -->
      <div class="toolbar">
        <input v-model="query.searchText" class="input" style="max-width:240px" placeholder="搜索名称/简介" @keyup.enter="loadPictures" />
        <select v-model="query.category" class="input" style="max-width:150px" @change="loadPictures">
          <option value="">全部分类</option>
          <option v-for="c in categoryList" :key="c" :value="c">{{ c }}</option>
        </select>
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
            <td>
              <img :src="pic.url" class="thumb" :alt="pic.name" />
            </td>
            <td>
              <router-link :to="'/picture/' + pic.id" class="pic-link">{{ pic.name || '(无名称)' }}</router-link>
            </td>
            <td>{{ pic.user?.userName || '-' }}</td>
            <td>
              <span class="review-badge" :class="reviewClass(pic.reviewStatus)">
                {{ reviewLabel(pic.reviewStatus) }}
              </span>
            </td>
            <td class="date-cell">{{ formatDate(pic.createTime) }}</td>
            <td class="action-cell">
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
        <button :disabled="query.current <= 1" @click="query.current--; loadPictures()">上一页</button>
        <span>第 {{ query.current }} 页 / 共 {{ totalPages }} 页 ({{ total }} 张)</span>
        <button :disabled="query.current >= totalPages" @click="query.current++; loadPictures()">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listPictureByPage, reviewPicture, deletePicture, getPictureTagCategory } from '@/api/picture'

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
  sortField: 'createTime',
  sortOrder: 'descend'
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))

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
    const res = await listPictureByPage({ ...query })
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

.toolbar { display: flex; gap: 0.75rem; margin-bottom: 1.5rem; flex-wrap: wrap; align-items: center; }
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
</style>
