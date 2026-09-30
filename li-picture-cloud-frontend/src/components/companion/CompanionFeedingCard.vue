<template>
  <section class="feeding-card" :data-phase="state.phase" aria-labelledby="feed-confirm-title">
    <div class="feed-card-heading"><span class="desk-eyebrow">递给绫页</span><span class="feed-phase">{{ phaseLabel }}</span></div>
    <div class="feed-preview">
      <div class="preview-mat" aria-hidden="true">
        <img v-if="picture && !previewFailed" :src="picture.thumbnailUrl || picture.url" alt="" @error="previewFailed = true" />
        <span v-else>{{ state.pictureId ? '预览暂不可用' : '等待一张照片' }}</span>
      </div>
      <div class="preview-copy">
        <h3 id="feed-confirm-title">{{ picture?.name || (state.pictureId ? `图片 #${state.pictureId}` : '今天想和她分享什么？') }}</h3>
        <p v-if="state.phase === 'empty'">在上方选择图片，或从图库把一张照片带过来。</p>
        <p v-else-if="state.phase === 'selected' || state.phase === 'rejected'">图片仍留在原来的空间。确认后才开始这一次喂养。</p>
        <p v-else-if="state.phase === 'submitting'">正在等待本次喂养的结果。暂时保留这张图片，不重复递交。</p>
        <p v-else-if="state.phase === 'uncertain'">暂未拿到确定结果。重试会继续核对同一次喂养，不另开一次。</p>
        <p v-else>这张图片的喂养回执已收到。</p>
      </div>
    </div>

    <p v-if="state.recovered && state.phase === 'uncertain'" class="recovery-note" role="status">已找回上次未确认的喂养。请主动重试以确认结果。</p>
    <p v-if="state.error" class="feed-message error" role="alert">{{ state.error }}</p>
    <p v-if="!state.recoveryAvailable" class="recovery-note">浏览器未能保存恢复信息。请留在此页完成或重试，刷新后可能无法找回同一次请求。</p>

    <template v-if="state.phase === 'succeeded'">
      <div class="feed-receipt" aria-label="本次喂养回执">
        <p ref="receiptTitle" class="receipt-title" role="status" tabindex="-1">{{ state.retried ? '这次喂养已安全完成，没有重复成长。' : '伙伴完成了这次喂养。' }}</p>
        <dl>
          <div><dt>本次结果</dt><dd>{{ resultLabel }}</dd></div>
          <div v-if="growth.lifeExperienceDelta != null"><dt>生命经验变化</dt><dd>{{ Number(growth.lifeExperienceDelta) > 0 ? '+' : '' }}{{ growth.lifeExperienceDelta }}</dd></div>
          <div><dt>营养来源</dt><dd>{{ growth.nutritionLabel || '以成长档案中的来源为准' }}</dd></div>
          <div><dt>内容理解</dt><dd>{{ growth.contentUnderstood === true ? '本次已分析图片内容' : growth.contentUnderstood === false ? '本次未读取图片内容' : '回执未提供理解标记' }}</dd></div>
        </dl>
        <p v-if="growth.fallbackReasonCode === 'SKIPPED_FAMILIAR'" class="receipt-note">这是一张熟悉的图片，本次跳过视觉分析。</p>
        <p v-else-if="growth.fallbackReasonCode" class="receipt-note">本次使用了降级来源，实际来源已记录在成长档案。</p>
        <p v-if="growth.lifeExperienceDelta != null && Number(growth.lifeExperienceDelta) === 0" class="receipt-note">本次生命经验没有增加，具体变化以成长档案为准。</p>
      </div>
      <p v-if="!homeFresh" class="recovery-note" role="status">{{ refreshing ? '回执已收到，正在更新小屋状态…' : '回执已收到；小屋状态尚未刷新，不需要重新喂养。' }}</p>
      <div class="feed-card-actions">
        <button class="btn btn-primary" type="button" :disabled="refreshing" @click="$emit('clear')">再选一张</button>
        <button class="btn btn-outline" type="button" @click="$emit('visit-growth')">查看成长档案</button>
      </div>
    </template>
    <div v-else class="feed-card-actions">
      <button ref="confirmButton" class="btn btn-primary feed-button" type="button" :disabled="!state.pictureId || state.phase === 'submitting'"
              aria-describedby="feed-confirm-title" @click="$emit('submit')">
        {{ state.phase === 'submitting' ? '伙伴正在吸收…' : state.phase === 'uncertain' ? '重试这次喂养' : '喂给伙伴' }}
      </button>
      <button v-if="state.pictureId && !locked" class="btn btn-outline" type="button" @click="$emit('clear')">收回这张</button>
    </div>
    <p class="feed-live" role="status">{{ state.phase === 'submitting' ? '喂养请求已发出，正在等待回执…' : '' }}</p>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
const props = defineProps({ state: { type: Object, required: true }, picture: { type: Object, default: null }, homeFresh: { type: Boolean, default: true }, refreshing: { type: Boolean, default: false } })
defineEmits(['submit', 'clear', 'visit-growth'])
const previewFailed = ref(false)
const confirmButton = ref(null)
const receiptTitle = ref(null)
watch(() => props.state.phase, async phase => {
  // Preserve keyboard continuity only if the user was still at confirmation;
  // an async receipt must not steal focus from chat or another part of the room.
  if (phase !== 'succeeded' || document.activeElement !== confirmButton.value) return
  await nextTick()
  if (document.activeElement === document.body) receiptTitle.value?.focus({ preventScroll: true })
})
watch(() => [props.picture?.id, props.picture?.thumbnailUrl, props.picture?.url], () => { previewFailed.value = false })
const locked = computed(() => ['submitting', 'uncertain'].includes(props.state.phase))
const growth = computed(() => props.state.result?.growth || {})
const resultLabel = computed(() => props.state.result?.outcome === 'FAMILIARITY' ? '再次遇见熟悉的图片' : props.state.result?.outcome === 'GROWN' ? '完成一次图片喂养' : '以成长档案为准')
const phaseLabel = computed(() => ({ empty: '还未选择', selected: '等待你的确认', submitting: '等待回执', uncertain: '结果待确认', rejected: '此次请求未获准', succeeded: '已收到回执' })[props.state.phase])
</script>

<style scoped>
.feeding-card { min-width: 0; padding: 24px; border: 1px solid #c9d1bf; border-radius: 8px; background: #fffdf7; }
.feed-card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 20px; }
.desk-eyebrow { font-size: 12px; letter-spacing: .12em; color: #52664b; }
.feed-phase { font-size: 11px; color: #5d7154; background: #edf0e5; border-radius: 20px; padding: 6px 10px; }
.feed-preview { display: grid; grid-template-columns: 112px minmax(0, 1fr); gap: 20px; align-items: center; }
.preview-mat { aspect-ratio: 1; display: grid; place-items: center; padding: 7px; border: 1px solid #ddd8c8; background: #f5f1e6; transform: rotate(-2deg); }
.preview-mat img { width: 100%; height: 96px; object-fit: contain; }
.preview-mat span { text-align: center; color: #79826d; font-size: 11px; }
.preview-copy h3 { font: 500 18px/1.6 'Noto Serif SC', 'Songti SC', SimSun, serif; overflow-wrap: anywhere; }
.preview-copy p, .recovery-note, .feed-message, .receipt-note, .feed-live { color: #65705b; font-size: 12px; line-height: 1.85; margin-top: 8px; overflow-wrap: anywhere; }
.error { color: #8a4c36; }
.recovery-note { padding: 10px 12px; background: #f3efdf; border-radius: 4px; }
.feed-card-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 20px; }
.feed-card-actions .btn { min-height: 46px; flex: 1; }
.feed-receipt { margin-top: 20px; padding-top: 18px; border-top: 1px solid #dce1d0; }
.receipt-title { color: #386148; font-size: 13px; }
dl { display: grid; gap: 12px; margin-top: 16px; }
dl > div { display: grid; grid-template-columns: 86px minmax(0, 1fr); gap: 12px; font-size: 12px; line-height: 1.8; }
dt { color: #79826e; } dd { margin: 0; color: #45553d; overflow-wrap: anywhere; }
.feed-live:empty { display: none; }
@media (max-width: 480px) { .feeding-card { padding: 18px; } .feed-preview { grid-template-columns: 76px minmax(0, 1fr); gap: 14px; } .preview-mat img { height: 60px; } .preview-copy h3 { font-size: 16px; } .feed-card-actions { flex-direction: column; } }
</style>
