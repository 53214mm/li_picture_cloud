<template>
  <div class="admin-page">
    <div class="container">
      <header class="admin-header">
        <div>
          <p class="eyebrow">链路观测</p>
          <h1>伙伴喂养日志</h1>
          <p class="page-intro">查看一次喂养经过了哪些阶段、最终是否成长，以及失败停在哪里。</p>
        </div>
      </header>

      <form class="observation-toolbar" @submit.prevent="applyFilters">
        <input v-model="query.userKeyword" class="input" placeholder="用户账号 / 昵称" aria-label="用户账号或昵称" />
        <input v-model="query.pictureId" class="input short-input" inputmode="numeric" placeholder="图片 ID" aria-label="图片 ID" />
        <input v-model="query.correlationId" class="input correlation-input" placeholder="链路 ID" aria-label="链路 ID" />
        <select v-model="query.status" class="input short-input" aria-label="运行状态">
          <option value="">全部状态</option>
          <option value="COMPLETED">已完成</option>
          <option value="FAILED">失败</option>
          <option value="REJECTED">已拒绝</option>
          <option value="PROCESSING">处理中</option>
        </select>
        <button class="btn btn-primary" type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
        <button class="btn btn-outline" type="button" @click="resetFilters">重置</button>
      </form>

      <div v-if="error" class="notice error-notice">{{ error }}</div>
      <div class="result-meta">共 {{ total }} 次喂养运行 <span>· 失败位置依据已保存的安全状态推导</span></div>

      <div v-if="loading" class="loading">加载中…</div>
      <div v-else-if="!runs.length" class="empty-state">
        <strong>还没有匹配的喂养记录</strong>
        <span>可以先清空筛选条件，或者等待用户完成一次伙伴喂养。</span>
      </div>
      <table v-else class="observation-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>用户 / 图片</th>
            <th>结果</th>
            <th>停留阶段</th>
            <th>营养方式</th>
            <th>耗时</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="run in runs" :key="run.runId">
            <td data-label="时间" class="time-cell">{{ formatObservationDateTime(run.createTime) }}</td>
            <td data-label="用户 / 图片">
              <div class="primary-text">{{ displayUser(run) }}</div>
              <div class="secondary-text">{{ displayPicture(run) }}</div>
            </td>
            <td data-label="结果">
              <span class="status-badge" :class="statusClass(run.status)">{{ run.statusLabel }}</span>
              <div class="summary-text">{{ run.summary }}</div>
            </td>
            <td data-label="停留阶段">
              <span class="stage-label">{{ run.stageLabel }}</span>
            </td>
            <td data-label="营养方式">
              <span>{{ displayActualNutrition(run) }}</span>
              <div v-if="run.degraded" class="secondary-text">视觉分析降级</div>
            </td>
            <td data-label="耗时" class="time-cell">{{ formatObservationDuration(run.durationMillis) }}</td>
            <td data-label="操作" class="action-cell">
              <router-link class="btn-sm-text" :to="`/admin/companion-feed-runs/${run.runId}`">查看详情</router-link>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="totalPages > 1" class="pagination">
        <button :disabled="query.current <= 1 || loading" @click="goPage(query.current - 1)">上一页</button>
        <span>第 {{ query.current }} / {{ totalPages }} 页</span>
        <button :disabled="query.current >= totalPages || loading" @click="goPage(query.current + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listCompanionFeedRuns } from '@/api/companionObservation'
import {
  displayActualNutrition,
  displayPicture,
  displayUser,
  formatObservationDateTime,
  formatObservationDuration,
  statusClass
} from '@/utils/companionObservation'

const router = useRouter()
const userStore = useUserStore()

const runs = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ current: 1, pageSize: 10, userKeyword: '', pictureId: '', correlationId: '', status: '' })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))

onMounted(initialize)

async function initialize() {
  try {
    await userStore.ensureCurrentUser()
  } catch (e) {
    error.value = e.message || '无法确认登录状态，请稍后重试'
    return
  }
  if (!userStore.isAdmin) {
    await router.replace('/login')
    return
  }
  await loadRuns()
}

async function loadRuns() {
  loading.value = true
  error.value = ''
  try {
    const result = await listCompanionFeedRuns({
      current: query.current,
      pageSize: query.pageSize,
      userKeyword: query.userKeyword.trim() || null,
      pictureId: query.pictureId ? Number(query.pictureId) : null,
      correlationId: query.correlationId.trim() || null,
      status: query.status || null
    })
    runs.value = result?.records || []
    total.value = result?.total || 0
  } catch (e) {
    error.value = e.message || '加载喂养记录失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  query.current = 1
  loadRuns()
}

function resetFilters() {
  Object.assign(query, { current: 1, userKeyword: '', pictureId: '', correlationId: '', status: '' })
  loadRuns()
}

function goPage(page) {
  query.current = Math.min(Math.max(page, 1), totalPages.value)
  loadRuns()
}
</script>

<style scoped>
.admin-page { padding: 3rem 0 5rem; }
.admin-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 1.5rem; }
.eyebrow { margin: 0 0 0.45rem; color: var(--gray-400); font-size: 0.72rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.admin-header h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; }
.page-intro { max-width: 42rem; margin: 0.65rem 0 0; color: var(--gray-600); line-height: 1.65; }
.observation-toolbar { display: flex; gap: 0.7rem; flex-wrap: wrap; align-items: center; margin-bottom: 1rem; padding: 1rem; border: 1px solid var(--gray-200); background: var(--gray-100); }
.observation-toolbar .input { min-width: 12rem; background: var(--white); }
.observation-toolbar .short-input { min-width: 8rem; width: 8rem; }
.observation-toolbar .correlation-input { min-width: 18rem; }
.notice { padding: 0.8rem 1rem; margin-bottom: 1rem; font-size: 0.875rem; }
.error-notice { background: #fff0ef; color: var(--red); }
.result-meta { margin: 1rem 0; color: var(--gray-600); font-size: 0.85rem; }
.result-meta span { color: var(--gray-400); }
.observation-table { width: 100%; border-collapse: collapse; }
.observation-table th, .observation-table td { padding: 0.9rem 0.75rem; border-bottom: 1px solid var(--gray-200); text-align: left; vertical-align: top; }
.observation-table th { color: var(--gray-400); font-size: 0.72rem; letter-spacing: 0.05em; text-transform: uppercase; }
.observation-table td { font-size: 0.86rem; }
.time-cell { white-space: nowrap; color: var(--gray-600); }
.primary-text { font-weight: 700; }
.secondary-text { margin-top: 0.3rem; color: var(--gray-500); font-size: 0.78rem; }
.status-badge, .stage-label { display: inline-block; padding: 0.22rem 0.55rem; font-size: 0.75rem; font-weight: 700; }
.status-badge.is-success { color: #24633d; background: #e9f6ed; }
.status-badge.is-failed { color: var(--red); background: #fff0ef; }
.status-badge.is-processing { color: #805800; background: #fff8df; }
.status-badge.is-unknown { color: var(--gray-600); background: var(--gray-100); }
.stage-label { color: var(--gray-900); background: var(--gray-100); }
.summary-text { max-width: 20rem; margin-top: 0.4rem; color: var(--gray-600); line-height: 1.5; }
.error-code { margin-top: 0.35rem; color: var(--red); font-family: monospace; font-size: 0.72rem; }
.action-cell { white-space: nowrap; }
.btn-sm-text { color: var(--gray-900); font-size: 0.8rem; font-weight: 700; text-decoration: underline; }
.loading, .empty-state { padding: 4rem 1rem; text-align: center; color: var(--gray-500); }
.empty-state { display: flex; flex-direction: column; gap: 0.5rem; border: 1px dashed var(--gray-300); }
.empty-state strong { color: var(--gray-900); }
.pagination { display: flex; align-items: center; justify-content: center; gap: 1.5rem; margin-top: 2rem; font-size: 0.875rem; }
.pagination button { padding: 0.5rem 1rem; border: 2px solid var(--black); font-weight: 600; }
.pagination button:disabled { cursor: default; opacity: 0.3; }
@media (max-width: 760px) {
  .admin-page { padding-top: 2rem; }
  .observation-toolbar .input, .observation-toolbar .short-input, .observation-toolbar .correlation-input { width: 100%; min-width: 0; }
  .observation-toolbar .btn { flex: 1; }
  .observation-table thead { display: none; }
  .observation-table, .observation-table tbody, .observation-table tr, .observation-table td { display: block; width: 100%; }
  .observation-table tr { margin-bottom: 1rem; padding: 0.8rem; border: 1px solid var(--gray-200); }
  .observation-table td { display: grid; grid-template-columns: 6.5rem 1fr; gap: 0.75rem; padding: 0.55rem 0; border-bottom: 0; }
  .observation-table td::before { content: attr(data-label); color: var(--gray-400); font-size: 0.74rem; font-weight: 700; }
  .summary-text { max-width: none; }
}
</style>
