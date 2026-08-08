<template>
  <div class="analyze-page">
    <h2 class="page-title">图库分析</h2>

    <!-- 范围选择 -->
    <div class="toolbar">
      <div class="scope-tabs">
        <button :class="{ active: scope === 'space' }" @click="setScope('space')">我的空间</button>
        <button :class="{ active: scope === 'public' }" @click="setScope('public')">公共图库</button>
        <button v-if="isAdmin" :class="{ active: scope === 'all' }" @click="setScope('all')">全部空间</button>
      </div>
      <div class="time-tabs">
        <button :class="{ active: timeDim === 'day' }" @click="timeDim = 'day'; loadUser()">日</button>
        <button :class="{ active: timeDim === 'week' }" @click="timeDim = 'week'; loadUser()">周</button>
        <button :class="{ active: timeDim === 'month' }" @click="timeDim = 'month'; loadUser()">月</button>
      </div>
    </div>

    <!-- 使用概览 -->
    <div class="cards" v-if="usage">
      <div class="card">
        <div class="card-label">已用容量</div>
        <div class="card-value">{{ formatSize(usage.usedSize) }}</div>
        <div class="card-sub" v-if="usage.maxSize">/ {{ formatSize(usage.maxSize) }}</div>
      </div>
      <div class="card">
        <div class="card-label">容量使用率</div>
        <div class="card-value">{{ usage.sizeUsageRatio != null ? usage.sizeUsageRatio + '%' : '∞' }}</div>
      </div>
      <div class="card">
        <div class="card-label">图片数量</div>
        <div class="card-value">{{ usage.usedCount }}</div>
        <div class="card-sub" v-if="usage.maxCount">/ {{ usage.maxCount }}</div>
      </div>
      <div class="card">
        <div class="card-label">数量使用率</div>
        <div class="card-value">{{ usage.countUsageRatio != null ? usage.countUsageRatio + '%' : '∞' }}</div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="charts-grid">
      <div class="chart-box">
        <h3>图片分类分布</h3>
        <v-chart :option="categoryOption" autoresize v-if="categoryOption" />
        <p v-else class="hint">暂无数据</p>
      </div>
      <div class="chart-box">
        <h3>图片大小分布</h3>
        <v-chart :option="sizeOption" autoresize v-if="sizeOption" />
        <p v-else class="hint">暂无数据</p>
      </div>
      <div class="chart-box">
        <h3>热门标签 Top 20</h3>
        <v-chart :option="tagOption" autoresize v-if="tagOption" />
        <p v-else class="hint">暂无数据</p>
      </div>
      <div class="chart-box">
        <h3>上传趋势（{{ timeDimLabel }}）</h3>
        <v-chart :option="userOption" autoresize v-if="userOption" />
        <p v-else class="hint">暂无数据</p>
      </div>
      <div class="chart-box full-width" v-if="isAdmin">
        <h3>空间使用排行</h3>
        <v-chart :option="rankOption" autoresize v-if="rankOption" />
        <p v-else class="hint">暂无数据</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { use } from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import { useUserStore } from '@/stores/user'
import { listMySpaces } from '@/api/space'
import { SPACE_TYPE } from '@/constants/space'
import {
  getSpaceUsageAnalyze, getSpaceCategoryAnalyze, getSpaceTagAnalyze,
  getSpaceSizeAnalyze, getSpaceUserAnalyze, getSpaceRankAnalyze
} from '@/api/spaceAnalyze'

use([PieChart, BarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const scope = ref('space')
const timeDim = ref('day')
const mySpaces = ref([])
const usage = ref(null)

const categoryOption = ref(null)
const sizeOption = ref(null)
const tagOption = ref(null)
const userOption = ref(null)
const rankOption = ref(null)

const timeDimLabel = computed(() => ({ day: '按日', week: '按周', month: '按月' }[timeDim.value]))

function buildBaseRequest() {
  const req = {}
  if (scope.value === 'public') req.queryPublic = true
  else if (scope.value === 'all') req.queryAll = true
  else if (mySpaces.value.length) req.spaceId = mySpaces.value[0].id  // 自动取用户第一个空间
  return req
}

async function setScope(s) {
  scope.value = s
  if (s === 'space') await loadMySpaces()
  loadAll()
}

async function loadMySpaces() {
  try {
    const spaces = (await listMySpaces(userStore.currentUser?.id)).records || []
    // Prefer the personal space when the user also belongs to team spaces.
    mySpaces.value = [...spaces].sort((a, b) => Number(a.spaceType !== SPACE_TYPE.PRIVATE) - Number(b.spaceType !== SPACE_TYPE.PRIVATE))
  } catch { mySpaces.value = [] }
}

async function loadAll() {
  const req = buildBaseRequest()
  try { usage.value = await getSpaceUsageAnalyze(req) } catch { usage.value = null }
  try {
    const cat = await getSpaceCategoryAnalyze(req)
    if (cat && cat.length) {
      categoryOption.value = {
        tooltip: { trigger: 'item' },
        legend: { bottom: 0, textStyle: { fontSize: 11 } },
        series: [{
          type: 'pie', radius: ['45%', '75%'], center: ['50%', '45%'],
          label: { formatter: '{b}\n{d}%', fontSize: 11 },
          data: cat.map(c => ({ name: c.category, value: c.count }))
        }]
      }
    } else categoryOption.value = null
  } catch { categoryOption.value = null }

  try {
    const sz = await getSpaceSizeAnalyze(req)
    if (sz && sz.length) {
      sizeOption.value = {
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie', radius: ['45%', '75%'],
          label: { formatter: '{b}\n{d}%', fontSize: 11 },
          data: sz.map(s => ({ name: s.sizeRange, value: s.count }))
        }]
      }
    } else sizeOption.value = null
  } catch { sizeOption.value = null }

  try {
    const tags = await getSpaceTagAnalyze(req)
    if (tags && tags.length) {
      const top20 = tags.slice(0, 20)
      tagOption.value = {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 10, right: 20, top: 10, bottom: 20, containLabel: true },
        xAxis: { type: 'value' },
        yAxis: { type: 'category', data: top20.map(t => t.tag).reverse(), inverse: true, axisLabel: { fontSize: 10 } },
        series: [{ type: 'bar', data: top20.map(t => t.count).reverse(), itemStyle: { color: '#6366f1', borderRadius: [0, 4, 4, 0] } }]
      }
    } else tagOption.value = null
  } catch { tagOption.value = null }

  loadUser()
  if (isAdmin.value) loadRank()
}

async function loadUser() {
  try {
    const req = { ...buildBaseRequest(), timeDimension: timeDim.value }
    const data = await getSpaceUserAnalyze(req)
    if (data && data.length) {
      userOption.value = {
        tooltip: { trigger: 'axis' },
        grid: { left: 10, right: 20, top: 10, bottom: 20, containLabel: true },
        xAxis: { type: 'category', data: data.map(d => d.period), axisLabel: { fontSize: 10, rotate: 30 } },
        yAxis: { type: 'value' },
        series: [{ type: 'line', data: data.map(d => d.count), smooth: true, areaStyle: { opacity: 0.15 }, itemStyle: { color: '#8b5cf6' } }]
      }
    } else userOption.value = null
  } catch { userOption.value = null }
}

async function loadRank() {
  try {
    const data = await getSpaceRankAnalyze({ topN: 15 })
    if (data && data.length) {
      rankOption.value = {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 10, right: 20, top: 10, bottom: 20, containLabel: true },
        xAxis: { type: 'value', axisLabel: { formatter: v => formatSize(v) } },
        yAxis: { type: 'category', data: data.map(s => s.spaceName).reverse(), inverse: true, axisLabel: { fontSize: 11 } },
        series: [{ type: 'bar', data: data.map(s => s.totalSize).reverse(), itemStyle: { color: '#f59e0b', borderRadius: [0, 4, 4, 0] } }]
      }
    } else rankOption.value = null
  } catch { rankOption.value = null }
}

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB'
  return (bytes / 1073741824).toFixed(2) + ' GB'
}

// App.vue loads the current user asynchronously. The previous onMounted call
// could run first, query all spaces without userId, and never retry after login
// state became available.
const loadedUserId = ref(null)
watch(() => userStore.currentUser?.id, async (userId) => {
  if (!userId || loadedUserId.value === userId) return
  loadedUserId.value = userId
  await loadMySpaces()
  if (scope.value === 'space') loadAll()
}, { immediate: true })
</script>

<style scoped>
.analyze-page { max-width: 1400px; margin: 0 auto; padding: 2rem; }
.page-title { font-size: 1.75rem; font-weight: 700; margin-bottom: 1.5rem; }

.toolbar { display: flex; align-items: center; gap: 1.5rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
.scope-tabs { display: flex; gap: 0; border: 2px solid var(--black); border-radius: 6px; overflow: hidden; }
.scope-tabs button {
  padding: 0.5rem 1.25rem; font-size: 0.8125rem; font-weight: 600; border: none; background: var(--white); cursor: pointer;
}
.scope-tabs button.active { background: var(--black); color: var(--white); }
.space-select select, .time-tabs button {
  padding: 0.5rem 1rem; font-size: 0.8125rem; border: 2px solid var(--gray-200); border-radius: 6px; background: var(--white);
}
.time-tabs { display: flex; gap: 0.25rem; }
.time-tabs button { cursor: pointer; font-weight: 500; }
.time-tabs button.active { border-color: var(--black); background: var(--black); color: var(--white); }

.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 2rem; }
.card { padding: 1.25rem; border: 2px solid var(--gray-200); border-radius: 8px; }
.card-label { font-size: 0.75rem; color: var(--gray-600); text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 0.375rem; }
.card-value { font-size: 1.5rem; font-weight: 700; }
.card-sub { font-size: 0.8125rem; color: var(--gray-400); }

.charts-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
.chart-box { border: 2px solid var(--gray-200); border-radius: 8px; padding: 1.25rem; min-height: 320px; }
.chart-box h3 { font-size: 0.875rem; font-weight: 600; margin-bottom: 0.75rem; text-transform: uppercase; letter-spacing: 0.04em; }
.chart-box.full-width { grid-column: 1 / -1; }
.chart-box .hint { color: var(--gray-400); text-align: center; padding-top: 6rem; font-size: 0.875rem; }

.echarts { width: 100%; height: 300px; }

@media (max-width: 1023px) {
  .charts-grid { grid-template-columns: 1fr; }
  .chart-box.full-width { grid-column: auto; }
}

@media (max-width: 767px) {
  .analyze-page { padding: 2rem 1rem 3rem; }
  .page-title { font-size: 1.75rem; }
  .toolbar { align-items: stretch; gap: 0.75rem; }
  .scope-tabs, .time-tabs { display: flex; width: 100%; overflow-x: auto; }
  .scope-tabs button, .time-tabs button { min-height: 44px; flex: 1 0 auto; }
  .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.75rem; }
  .card { padding: 1rem; }
  .card-value { font-size: 1.5rem; overflow-wrap: anywhere; }
  .chart-box { min-height: 280px; padding: 1rem 0.75rem; }
  .echarts { height: 260px; }
}

@media (max-width: 480px) {
  .cards { grid-template-columns: 1fr; }
}
</style>
