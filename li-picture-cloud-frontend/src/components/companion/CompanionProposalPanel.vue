<template>
  <section class="proposal-card" aria-labelledby="proposal-title">
    <header>
      <div>
        <span class="eyebrow">主动提案</span>
        <h2 id="proposal-title">伙伴想对你说</h2>
      </div>
      <button class="contract-toggle" type="button" :aria-expanded="showContract"
              @click="showContract = !showContract">
        主动设置
      </button>
    </header>

    <div v-if="showContract" class="contract-panel">
      <label class="contract-row">
        <input v-model="contractDraft.active" type="checkbox" :disabled="contractSaving" />
        <span>允许伙伴主动提议（默认关闭；契约优先于伙伴性格）</span>
      </label>
      <div class="contract-row contract-times">
        <label>安静时段
          <input v-model="contractDraft.quietStart" type="time" :disabled="contractSaving" />
          <span>至</span>
          <input v-model="contractDraft.quietEnd" type="time" :disabled="contractSaving" />
        </label>
      </div>
      <label class="contract-row">提案频率上限（小时，0 = 完全关闭）
        <input v-model.number="contractDraft.maxFrequencyHours" type="number" min="0" max="720"
               :disabled="contractSaving" />
      </label>
      <div class="contract-actions">
        <button class="btn btn-primary" type="button" :disabled="contractSaving" @click="saveContract">
          {{ contractSaving ? '正在保存…' : '保存主动设置' }}
        </button>
        <p v-if="contractError" class="contract-error" role="alert">{{ contractError }}</p>
      </div>
    </div>

    <div v-if="loadError" class="proposal-state error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn btn-outline" type="button" @click="loadProposal">重试</button>
    </div>
    <div v-else-if="loading" class="proposal-state">伙伴正在想有没有话想对你说…</div>
    <div v-else-if="!proposal" class="proposal-state">
      伙伴现在没有主动提议。开启主动设置后，它会挑合适的时刻轻轻出现。
    </div>
    <div v-else class="proposal-body">
      <CompanionMessageBubble :message="proposal.content" />
      <div class="proposal-meta">
        <span>类型 {{ opportunityLabel(proposal.opportunityType) }}</span>
        <span>冲动得分 {{ Number(proposal.impulseScore).toFixed(2) }}</span>
      </div>
      <div class="proposal-actions">
        <button class="btn btn-primary" type="button" :disabled="busy" data-testid="proposal-accept"
                @click="react('accept')">好呀</button>
        <button class="btn btn-outline" type="button" :disabled="busy" data-testid="proposal-ignore"
                @click="react('ignore')">忽略</button>
        <button class="btn scold" type="button" :disabled="busy" data-testid="proposal-scold"
                title="敲打会立即止住这次提议" @click="react('scold')">敲打它</button>
      </div>
      <p v-if="actionError" class="proposal-error" role="alert">{{ actionError }}</p>
      <p v-else-if="actionNoticeText" class="proposal-notice" role="status">{{ actionNoticeText }}</p>
      <p class="scold-hint">敲打只抑制这一次提议；反复敲打才会缓慢影响它"好奇"的性格倾向。</p>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import {
  acceptCompanionProposal,
  getActiveCompanionProposal,
  getCompanionContract,
  ignoreCompanionProposal,
  scoldCompanionProposal,
  updateCompanionContract
} from '@/api/companion'
import CompanionMessageBubble from '@/components/companion/CompanionMessageBubble.vue'

const props = defineProps({ refreshKey: { type: Number, default: 0 } })

const proposal = ref(null)
const loading = ref(false)
const loadError = ref('')
const busy = ref(false)
const actionError = ref('')
const actionNoticeText = ref('')
const showContract = ref(false)
const contractSaving = ref(false)
const contractError = ref('')
const contractDraft = reactive({ active: false, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 72 })

const OPPORTUNITY_LABELS = Object.freeze({
  WEEKLY_REVIEW: '每周影像回顾',
  ANNIVERSARY: '纪念日提醒',
  SIMILAR_STORY: '相似图片故事'
})

function opportunityLabel(type) {
  return OPPORTUNITY_LABELS[type] || type || '伙伴的提议'
}

onMounted(() => {
  loadProposal()
  loadContract()
})
// 喂养等动作可能让守门状态变化，由父组件自增 refreshKey 触发重新评估。
watch(() => props.refreshKey, () => { loadProposal() })

async function loadProposal() {
  loading.value = true
  loadError.value = ''
  try {
    proposal.value = await getActiveCompanionProposal()
  } catch (error) {
    loadError.value = error.message || '提案读取失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function loadContract() {
  try {
    const contract = await getCompanionContract()
    contractDraft.active = contract.active
    contractDraft.quietStart = contract.quietStart
    contractDraft.quietEnd = contract.quietEnd
    contractDraft.maxFrequencyHours = contract.maxFrequencyHours
  } catch {
    // 契约加载失败不阻塞提案展示；打开设置面板时再重试。
  }
}

async function saveContract() {
  if (contractSaving.value) return
  contractSaving.value = true
  contractError.value = ''
  try {
    await updateCompanionContract({
      active: Boolean(contractDraft.active),
      quietStart: contractDraft.quietStart,
      quietEnd: contractDraft.quietEnd,
      maxFrequencyHours: Number(contractDraft.maxFrequencyHours)
    })
    showContract.value = false
    // 契约变化可能立刻允许/禁止提案，保存后立即按新契约重新评估一次。
    await loadProposal()
  } catch (error) {
    contractError.value = error.message || '主动设置保存失败，请稍后重试。'
  } finally {
    contractSaving.value = false
  }
}

async function react(kind) {
  if (busy.value || !proposal.value) return
  busy.value = true
  actionError.value = ''
  actionNoticeText.value = ''
  try {
    const updated = kind === 'accept'
      ? await acceptCompanionProposal(proposal.value.id)
      : kind === 'ignore'
        ? await ignoreCompanionProposal(proposal.value.id)
        : await scoldCompanionProposal(proposal.value.id)
    proposal.value = null
    if (kind === 'scold' && updated?.status === 'SUPPRESSED') {
      actionNoticeText.value = '伙伴安静了，这次提议已被止住。'
    } else if (kind === 'accept' && updated?.status === 'DONE') {
      actionNoticeText.value = '好呀，伙伴已经记下了。'
    } else if (kind === 'ignore' && updated?.status === 'IGNORED') {
      actionNoticeText.value = '已忽略，伙伴不会再提这件事。'
    }
  } catch (error) {
    actionError.value = error.message || '操作失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.proposal-card { border: 2px solid var(--black); background: var(--white); }
.proposal-card > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); }
.proposal-card h2 { font-size: 1.35rem; }
.eyebrow { color: var(--red); font-size: .68rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.contract-toggle { padding: .45rem .7rem; border: 2px solid var(--black); background: var(--white); font-size: .75rem; font-weight: 800; cursor: pointer; }
.contract-toggle:hover { background: var(--yellow); }
.contract-panel { display: grid; gap: .75rem; padding: 1.25rem 1.5rem; border-bottom: 2px solid var(--black); background: var(--gray-100); }
.contract-row { display: flex; flex-wrap: wrap; gap: .6rem; align-items: center; font-size: .85rem; }
.contract-times input { padding: .35rem .5rem; border: 2px solid var(--black); font: inherit; }
.contract-row input[type='number'] { width: 6rem; padding: .35rem .5rem; border: 2px solid var(--black); font: inherit; }
.contract-actions .btn { min-height: 40px; font-size: .8rem; }
.contract-error, .proposal-error { margin-top: .5rem; color: var(--red); font-size: .8rem; }
.proposal-notice { margin-top: .5rem; color: #075d2a; font-size: .8rem; font-weight: 700; }
.proposal-state { padding: 2rem 1.5rem; color: var(--gray-600); }
.proposal-state.error { color: var(--red); }
.proposal-state.error .btn { margin-top: 1rem; }
.proposal-body { padding: 1.25rem 1.5rem 1.5rem; }
.proposal-body :deep(.companion-message) { margin-top: 0; }
.proposal-meta { display: flex; flex-wrap: wrap; gap: .6rem; margin: .75rem 0 0 .65rem; color: var(--gray-600); font-size: .72rem; }
.proposal-actions { display: flex; flex-wrap: wrap; gap: .6rem; margin-top: 1rem; }
.proposal-actions .btn { min-height: 44px; font-size: .85rem; }
.proposal-actions .scold { border-color: var(--red); color: var(--red); }
.proposal-actions .scold:hover:not(:disabled) { background: #fdeaea; }
.scold-hint { margin-top: .75rem; color: var(--gray-600); font-size: .72rem; }
@media (max-width: 767px) {
  .proposal-card > header, .proposal-body, .contract-panel { padding-inline: 1.25rem; }
}
</style>
