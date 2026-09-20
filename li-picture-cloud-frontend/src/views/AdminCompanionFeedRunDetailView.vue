<template>
  <div class="admin-page">
    <div class="container">
      <button class="back-link" type="button" @click="router.push('/admin/companion-feed-runs')">← 返回喂养日志</button>

      <div v-if="loading" class="loading">加载中…</div>
      <div v-else-if="error" class="notice error-notice">
        <strong>详情加载失败</strong>
        <span>{{ error }}</span>
        <button class="btn btn-outline btn-small" type="button" @click="loadDetail">重试</button>
      </div>
      <template v-else-if="detail">
        <header class="detail-header">
          <div>
            <p class="eyebrow">第 {{ detail.summary.runId }} 次运行</p>
            <h1>{{ displayUser(detail.summary) }} 喂养了 {{ displayPicture(detail.summary) }}</h1>
            <p class="detail-time">开始于 {{ formatObservationDateTime(detail.summary.createTime) }}</p>
          </div>
          <span class="status-badge" :class="statusClass(detail.summary.status)">{{ detail.summary.statusLabel }}</span>
        </header>

        <section class="summary-card" aria-labelledby="summary-title">
          <div class="summary-card-heading">
            <div>
              <p class="eyebrow">结果说明</p>
              <h2 id="summary-title">{{ detail.summary.summary }}</h2>
            </div>
            <span class="stage-label">停在：{{ detail.summary.stageLabel }}</span>
          </div>
          <div class="summary-facts">
            <div><span>实际营养方式</span><strong>{{ displayActualNutrition(detail.summary) }}</strong></div>
            <div><span>本次耗时</span><strong>{{ formatObservationDuration(detail.summary.durationMillis) }}</strong></div>
            <div><span>尝试次数</span><strong>{{ detail.summary.attemptCount }}</strong></div>
            <div><span>成长记录</span><strong>{{ detail.technical.resultGrowthRecordId ? '已生成' : '未生成' }}</strong></div>
          </div>
          <p v-if="detail.safeErrorMessage && detail.summary.status !== 'COMPLETED'" class="safe-error">
            {{ detail.safeErrorMessage }}
          </p>
          <p v-else-if="detail.safeErrorMessage" class="retry-history">
            历史尝试曾失败：{{ detail.safeErrorMessage }}
          </p>
        </section>

        <div class="detail-grid">
          <section class="panel timeline-panel" aria-labelledby="timeline-title">
            <div class="panel-heading">
              <div><p class="eyebrow">过程</p><h2 id="timeline-title">喂养链路</h2></div>
              <span class="panel-tip">阶段状态来自运行记录和成长记录</span>
            </div>
            <CompanionFeedStageTimeline :timeline="detail.timeline" />
          </section>

          <section class="panel" aria-labelledby="growth-title">
            <div class="panel-heading">
              <div><p class="eyebrow">业务结果</p><h2 id="growth-title">成长结算</h2></div>
            </div>
            <div v-if="detail.growth" class="growth-result">
              <div class="experience-result">+{{ detail.growth.lifeExperienceDelta }} <span>生命经验</span></div>
              <p class="growth-reason">{{ detail.growth.reason }}</p>
              <dl class="fact-list">
                <div><dt>事件</dt><dd>{{ growthEventLabel(detail.growth.eventType) }}</dd></div>
                <div><dt>营养来源</dt><dd>{{ detail.growth.nutritionLabel || '图片营养' }}</dd></div>
                <div><dt>来源模型</dt><dd>{{ modelLabel(detail.growth) }}</dd></div>
                <div><dt>置信度</dt><dd>{{ confidenceLabel(detail.growth.confidence) }}</dd></div>
                <div><dt>变化</dt><dd>{{ growthChanges(detail.growth) }}</dd></div>
              </dl>
            </div>
            <p v-else class="no-growth">{{ growthAbsenceDescription(detail.summary.status) }}</p>
          </section>
        </div>

        <details class="technical-panel">
          <summary>技术信息（用于进一步排查）</summary>
          <dl class="technical-grid">
            <div><dt>链路 ID</dt><dd>{{ detail.technical.correlationId }}</dd></div>
            <div><dt>幂等键</dt><dd>{{ detail.technical.idempotencyKey }}</dd></div>
            <div><dt>运行版本</dt><dd>{{ detail.technical.revision }}</dd></div>
            <div><dt>请求策略</dt><dd>{{ detail.summary.requestedPolicyLabel }}</dd></div>
            <div><dt>请求提供方 / 模型</dt><dd>{{ requestModelLabel(detail.technical) }}</dd></div>
            <div><dt>实际提供方 / 模型</dt><dd>{{ actualModelLabel(detail.technical) }}</dd></div>
            <div><dt>分析结果版本</dt><dd>{{ detail.technical.promptVersion || '-' }} / {{ detail.technical.resultSchemaVersion || '-' }}</dd></div>
            <div><dt>安全错误</dt><dd>{{ detail.technical.safeErrorCode || '-' }}</dd></div>
            <div><dt>安全错误时间</dt><dd>{{ formatObservationDateTime(detail.technical.safeErrorTime) }}</dd></div>
          </dl>
        </details>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCompanionFeedRun } from '@/api/companionObservation'
import CompanionFeedStageTimeline from '@/components/companion/CompanionFeedStageTimeline.vue'
import { SKILL_LABEL, TRAIT_AXES } from '@/constants/companion'
import {
  displayActualNutrition,
  displayPicture,
  displayUser,
  formatObservationDateTime,
  formatObservationDuration,
  growthAbsenceDescription,
  statusClass
} from '@/utils/companionObservation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = ref(null)
const loading = ref(false)
const error = ref('')

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
  await loadDetail()
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await getCompanionFeedRun(route.params.runId)
  } catch (e) {
    error.value = e.message || '无法读取这次喂养记录'
  } finally {
    loading.value = false
  }
}

function growthEventLabel(eventType) {
  return eventType === 'PICTURE_REVISITED' ? '再次遇见熟悉的图片' : '从图片获得成长'
}

function modelLabel(growth) {
  return growth.providerCode && growth.modelCode ? `${growth.providerCode} / ${growth.modelCode}` : '未记录'
}

function requestModelLabel(technical) {
  return technical.requestedProviderCode && technical.requestedModelCode
    ? `${technical.requestedProviderCode} / ${technical.requestedModelCode}`
    : '未指定'
}

function actualModelLabel(technical) {
  return technical.actualProviderCode && technical.actualModelCode
    ? `${technical.actualProviderCode} / ${technical.actualModelCode}`
    : '尚无结果'
}

function confidenceLabel(value) {
  return value == null ? '未记录' : Number(value).toFixed(2)
}

function growthChanges(growth) {
  const traits = TRAIT_AXES
    .map(axis => ({ label: axis.positive, value: Number(growth.traitDelta?.[axis.key] || 0) }))
    .filter(item => item.value !== 0)
    .map(item => `${item.label}${signed(item.value)}`)
  const skills = Object.entries(growth.skillExperienceDelta || {})
    .filter(([, value]) => Number(value) !== 0)
    .map(([key, value]) => `${SKILL_LABEL[key] || key}+${value}`)
  return [...traits, ...skills].join('、') || '无额外属性变化'
}

function signed(value) {
  const number = Number(value || 0)
  return number > 0 ? `+${number}` : String(number)
}
</script>

<style scoped>
.admin-page { padding: 2.5rem 0 5rem; }
.back-link { margin-bottom: 1.5rem; color: var(--gray-600); font-size: 0.85rem; font-weight: 700; text-decoration: underline; }
.detail-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; margin-bottom: 1.5rem; }
.eyebrow { margin: 0 0 0.45rem; color: var(--gray-400); font-size: 0.72rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.detail-header h1 { max-width: 48rem; font-size: 2rem; line-height: 1.2; letter-spacing: -0.04em; }
.detail-time { margin-top: 0.6rem; color: var(--gray-500); font-size: 0.85rem; }
.status-badge, .stage-label { display: inline-block; padding: 0.35rem 0.65rem; font-size: 0.78rem; font-weight: 700; white-space: nowrap; }
.status-badge.is-success { color: #24633d; background: #e9f6ed; }
.status-badge.is-failed { color: var(--red); background: #fff0ef; }
.status-badge.is-processing { color: #805800; background: #fff8df; }
.status-badge.is-unknown, .stage-label { color: var(--gray-600); background: var(--gray-100); }
.summary-card, .panel { border: 2px solid var(--black); background: var(--white); }
.summary-card { padding: 1.5rem; }
.summary-card-heading, .panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.summary-card h2 { max-width: 52rem; font-size: 1.2rem; line-height: 1.5; }
.summary-facts { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-top: 1.5rem; padding-top: 1.2rem; border-top: 1px solid var(--gray-200); }
.summary-facts div { display: flex; flex-direction: column; gap: 0.25rem; }
.summary-facts span, .fact-list dt, .technical-grid dt { color: var(--gray-400); font-size: 0.75rem; }
.summary-facts strong { font-size: 0.95rem; }
.safe-error { margin-top: 1.2rem; padding: 0.8rem; background: #fff0ef; color: var(--red); font-size: 0.85rem; }
.retry-history { margin-top: 1.2rem; padding: 0.8rem; background: var(--gray-100); color: var(--gray-600); font-size: 0.85rem; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(20rem, 0.9fr); gap: 1.5rem; margin-top: 1.5rem; }
.panel { padding: 1.5rem; }
.panel h2 { font-size: 1.25rem; }
.panel-tip { max-width: 13rem; color: var(--gray-400); font-size: 0.74rem; line-height: 1.5; text-align: right; }
.timeline-panel :deep(.feed-timeline) { margin-top: 1.5rem; }
.growth-result { margin-top: 1.5rem; }
.experience-result { color: var(--red); font-size: 2rem; font-weight: 800; }
.experience-result span { color: var(--gray-600); font-size: 0.85rem; font-weight: 600; }
.growth-reason { margin-top: 0.9rem; color: var(--gray-600); line-height: 1.65; }
.fact-list { margin-top: 1.4rem; }
.fact-list div { display: grid; grid-template-columns: 5.5rem 1fr; gap: 0.75rem; padding: 0.65rem 0; border-bottom: 1px solid var(--gray-200); }
.fact-list dd { min-width: 0; color: var(--gray-900); font-size: 0.85rem; overflow-wrap: anywhere; }
.no-growth { margin-top: 1.5rem; padding: 1rem; color: var(--gray-600); background: var(--gray-100); }
.technical-panel { margin-top: 1.5rem; border: 1px solid var(--gray-300); }
.technical-panel summary { padding: 1rem 1.2rem; cursor: pointer; font-size: 0.85rem; font-weight: 700; }
.technical-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem; padding: 0 1.2rem 1.2rem; }
.technical-grid div { min-width: 0; }
.technical-grid dd { margin-top: 0.25rem; color: var(--gray-900); font-family: monospace; font-size: 0.78rem; overflow-wrap: anywhere; }
.notice { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; padding: 1rem; }
.error-notice { background: #fff0ef; color: var(--red); }
.error-notice span { flex: 1; }
.btn-small { padding: 0.5rem 0.9rem; font-size: 0.75rem; }
.loading { padding: 4rem 1rem; text-align: center; color: var(--gray-500); }
@media (max-width: 820px) {
  .detail-grid { grid-template-columns: 1fr; }
  .summary-facts { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 560px) {
  .admin-page { padding-top: 1.5rem; }
  .detail-header, .summary-card-heading, .panel-heading { flex-direction: column; }
  .detail-header h1 { font-size: 1.6rem; }
  .panel-tip { max-width: none; text-align: left; }
  .summary-facts, .technical-grid { grid-template-columns: 1fr; }
  .technical-grid { gap: 0.8rem; }
}
</style>
