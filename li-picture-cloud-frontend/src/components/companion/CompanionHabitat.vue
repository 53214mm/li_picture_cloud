<template>
  <section class="habitat-scene" :class="{ 'is-empty': !inhabited }" aria-labelledby="habitat-title">
    <div class="room-decoration" aria-hidden="true">
      <div class="room-window"><span></span></div>
      <div class="window-light"></div>
      <div class="room-floor"></div>
      <div class="room-rug"></div>
      <div class="room-shelf"><i></i><i></i><i></i><i></i></div>
      <div class="room-plant"><i></i><i></i><i></i><span></span></div>
    </div>
    <header class="room-heading">
      <span class="room-eyebrow">LINGYE · A PLACE FOR MOMENTS</span>
      <h1 id="habitat-title" tabindex="-1">留影小屋<span aria-hidden="true">。</span></h1>
      <p>{{ inhabited ? '绫页与影像相伴的地方' : '为一段新的相处，留一个位置' }}</p>
    </header>
    <p v-if="inhabited" class="room-status" role="status">{{ describeHabitatActivity(presentation) }}</p>
    <div class="room-invitation">
      <p class="room-line">{{ inhabited ? '把片刻，\n慢慢留下。' : '小屋准备好了，\n等一次相遇。' }}</p>
      <p class="room-description">{{ inhabited ? '带一张照片来，或坐下来聊一会儿。' : '唤醒不会消耗图片，也不会移动你的图库。' }}</p>
      <div v-if="inhabited" class="room-actions">
        <button type="button" class="room-primary" @click="emit('visit', 'chat')">坐下聊聊 <span aria-hidden="true">↗</span></button>
        <button type="button" @click="emit('visit', 'feed')">带一张照片来 <span aria-hidden="true">→</span></button>
      </div>
      <button v-else type="button" class="room-primary awaken-button" :disabled="awakenBusy" @click="emit('awaken')">
        {{ awakenBusy ? '正在唤醒…' : '唤醒我的伙伴' }}
      </button>
    </div>
    <CompanionBody v-if="inhabited" class="room-resident" habitat :presentation="presentation" />
    <div v-else class="room-empty-note"><span aria-hidden="true">✧</span><p>还没有唤醒伙伴</p></div>
    <button v-if="inhabited" type="button" class="photo-nook" aria-label="照片留位，前往照片桌" @click="emit('visit', 'feed')">
      <span class="photo-frame">
        <img v-if="photoUrl && !photoFailed" :key="photoUrl" :src="photoUrl" :alt="picture.name || '当前选择的图片'"
             @error="photoFailed = true" />
        <span v-else class="photo-placeholder"><span aria-hidden="true">＋</span>{{ photoUrl ? '预览暂不可用' : '为照片留个位置' }}</span>
      </span>
      <span class="photo-caption">{{ picture ? '当前选择' : '照片留位' }}</span>
      <span class="photo-name">{{ picture ? picture.name || '未命名图片' : '从你的图库选一张' }}</span>
    </button>
    <span class="room-footnote" aria-hidden="true">一点留白，装下共同的片刻</span>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import CompanionBody from './body/CompanionBody.vue'
import { describeHabitatActivity } from '@/presentation/companionHabitat'

const props = defineProps({
  presentation: { type: Object, required: true },
  inhabited: Boolean,
  awakenBusy: Boolean,
  picture: { type: Object, default: null }
})
const emit = defineEmits(['visit', 'awaken'])
const photoUrl = computed(() => props.picture?.thumbnailUrl || props.picture?.url || '')
const photoFailed = ref(false)
watch(photoUrl, () => { photoFailed.value = false })
</script>

<style scoped>
.habitat-scene { position: relative; isolation: isolate; min-height: 540px; overflow: hidden; border: 1px solid #d8d4c5; border-radius: 10px 10px 2px 2px; background: #edeadd; color: #394438; }
.room-decoration { position: absolute; inset: 0; overflow: hidden; pointer-events: none; z-index: -1; background: repeating-linear-gradient(90deg, transparent 0 72px, #777a6210 73px, transparent 74px), linear-gradient(115deg, #f7f3e5 0%, #e6e6d5 72%, #d6dac9); }
.room-window { position: absolute; top: 29px; right: 19%; width: 35%; height: 300px; border: 11px solid #f8f6eb; border-radius: 150px 150px 2px 2px; background: linear-gradient(165deg, #b7c6bd, #e0e7d9 56%, #f8f0d8); box-shadow: 0 0 0 1px #bcc2af, 7px 9px 0 #aeb6a324; overflow: hidden; }
.room-window::before { content: ''; position: absolute; width: 180%; height: 130px; left: -30%; bottom: -30px; border-radius: 50%; background: #a5b19a55; box-shadow: 70px -30px 0 #94a89233; }
.room-window::after { content: ''; position: absolute; inset: 0; background: linear-gradient(90deg, transparent calc(50% - 3px), #f8f6eb calc(50% - 3px) calc(50% + 3px), transparent calc(50% + 3px)); }
.room-window span { position: absolute; left: 0; right: 0; top: 52%; height: 7px; background: #f8f6eb; z-index: 1; }
.window-light { position: absolute; width: 58%; height: 100%; top: 28%; right: 3%; background: linear-gradient(140deg, #fff9df55, transparent 75%); transform: skewX(-28deg); }
.room-floor { position: absolute; inset: 72% 0 0; border-top: 5px solid #beb69b; background: repeating-linear-gradient(0deg, transparent 0 40px, #8e806b12 41px, transparent 42px), linear-gradient(180deg, #d6ccb6, #e5dcc8); }
.room-rug { position: absolute; width: 47%; height: 106px; left: 34%; bottom: 36px; border: 2px solid #c5c6ac; border-radius: 50%; background: #d9dac2; box-shadow: inset 0 0 0 8px #e3e1ca, 0 6px 8px #80735112; }
.room-heading { position: absolute; left: 36px; top: 34px; }
.room-eyebrow { color: #697561; font-size: 9px; letter-spacing: .18em; }
.room-heading h1 { margin: 14px 0 9px; font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif; font-size: clamp(32px, 3.6vw, 48px); font-weight: 500; letter-spacing: .08em; line-height: 1.2; }
.room-heading h1 span { color: #8d9277; }
.room-heading > p { font-size: 12px; color: #6c7564; letter-spacing: .08em; }
.room-status { position: absolute; right: 24px; top: 24px; max-width: 180px; padding: 7px 12px; border: 1px solid #c0c6b4; border-radius: 20px; font-size: 11px; color: #51624e; background: #f4f5e8c9; }
.room-invitation { position: absolute; left: 36px; bottom: 70px; width: 210px; }
.room-line { white-space: pre-line; font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif; font-size: 25px; line-height: 1.65; letter-spacing: .1em; }
.room-description { max-width: 180px; margin-top: 12px; color: #64705f; font-size: 12px; line-height: 1.9; }
.room-actions { display: grid; gap: 6px; margin-top: 24px; }
.room-actions button, .awaken-button { min-height: 44px; padding: 8px 14px; border: 1px solid transparent; border-radius: 4px; font-size: 12px; text-align: left; }
.room-actions button { display: flex; align-items: center; justify-content: space-between; max-width: 168px; }
.room-actions button:hover { border-color: #76836b; }
.room-primary { color: #fffdf5; background: #4f6753; }
.awaken-button { margin-top: 24px; }
.awaken-button:disabled { opacity: .6; cursor: wait; }
.room-resident { position: absolute; left: 50%; bottom: 23px; width: 312px; transform: translateX(-40%); }
.room-empty-note { position: absolute; left: 54%; top: 48%; text-align: center; color: #717b63; }
.room-empty-note span { font-size: 42px; }
.room-empty-note p { font-size: 12px; margin-top: 12px; }
.photo-nook { position: absolute; right: 28px; bottom: 95px; width: 146px; padding: 9px 9px 14px; text-align: left; background: #faf7eccc; box-shadow: 2px 5px 9px #655d431a; border: 1px solid #fefcf4; transform: rotate(4deg); }
.photo-nook:hover, .photo-nook:focus-visible { outline: 2px solid #67765d; outline-offset: 4px; }
.photo-frame { display: grid; aspect-ratio: 5 / 4; background: #e5e6d8; }
.photo-frame img { width: 100%; height: 100%; aspect-ratio: 5 / 4; object-fit: cover; }
.photo-placeholder { display: grid; align-content: center; justify-items: center; gap: 4px; border: 1px dashed #bbc1ac; color: #606d55; font-size: 10px; }
.photo-placeholder > span { font-size: 23px; font-weight: 300; }
.photo-caption { display: block; margin-top: 12px; color: #5c6652; font-size: 10px; }
.photo-name { display: block; margin-top: 4px; font-size: 11px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.room-footnote { position: absolute; bottom: 19px; left: 36px; font-size: 9px; letter-spacing: .12em; color: #71745f; }
.room-shelf { position: absolute; right: 23px; top: 180px; width: 133px; height: 64px; border-bottom: 8px solid #b6a286; box-shadow: 0 5px 2px #6e665c15; display: flex; align-items: flex-end; padding-left: 10px; gap: 5px; }
.room-shelf i { width: 16px; height: 40px; background: #989f87; border: 3px solid #a9ad95; }
.room-shelf i:nth-child(2) { height: 50px; background: #e4d0a5; border-color: #ded8bd; }
.room-shelf i:nth-child(3) { background: #a5b4ac; border-color: #c0c7b4; transform: rotate(-12deg); margin-left: 4px; }
.room-shelf i:nth-child(4) { width: 22px; height: 35px; background: #bcaa8c; border-color: #cbba9c; }
.room-plant { position: absolute; bottom: 156px; left: 29%; width: 42px; height: 100px; }
.room-plant::before { content: ''; position: absolute; bottom: 27px; left: 21px; height: 68px; border-left: 2px solid #8a9575; }
.room-plant i { position: absolute; left: 4px; top: 25px; width: 19px; height: 33px; border-radius: 80% 0 80% 0; background: #99a384; transform: rotate(-55deg); }
.room-plant i:nth-child(2) { left: 23px; top: 8px; transform: rotate(20deg); background: #7e906d; }
.room-plant i:nth-child(3) { left: 22px; top: 40px; transform: rotate(40deg); }
.room-plant span { position: absolute; bottom: 0; width: 42px; height: 36px; border-radius: 3px 3px 14px 14px; background: linear-gradient(90deg, #b9b29a, #d6cbb0); border-top: 4px solid #c4b797; }
@media (max-width: 1100px) and (min-width: 768px) {
  .room-heading, .room-invitation { left: 24px; }
  .room-invitation { width: 170px; }
  .room-line { font-size: 22px; }
  .room-resident { width: 280px; left: 47%; }
  .photo-nook { width: 120px; right: 18px; }
  .room-window { right: 17%; }
  .room-plant { display: none; }
}
@media (max-width: 767px) {
  .habitat-scene { min-height: 680px; }
  .room-heading { top: 25px; left: 22px; }
  .room-heading h1 { font-size: 34px; margin-top: 10px; }
  .room-eyebrow { font-size: 8px; }
  .room-status { top: 143px; left: 22px; right: auto; font-size: 10px; }
  .room-window { width: 66%; height: 270px; top: 146px; right: 9%; border-width: 8px; }
  .room-floor { top: 66%; }
  .room-resident { width: 230px; left: 50%; bottom: 146px; transform: translateX(-50%); }
  .room-rug { left: 17%; width: 71%; bottom: 175px; height: 70px; }
  .room-invitation { left: 22px; bottom: 20px; width: calc(100% - 44px); }
  .room-line { font-size: 19px; white-space: normal; }
  .room-description { display: none; }
  .room-actions { grid-template-columns: 1fr 1fr; margin-top: 12px; gap: 8px; }
  .room-actions button { max-width: none; padding-inline: 10px; }
  .room-footnote, .room-shelf, .room-plant { display: none; }
  .photo-nook { width: 86px; right: 13px; bottom: 243px; padding: 5px 5px 8px; }
  .photo-placeholder { font-size: 8px; }
  .photo-placeholder > span { font-size: 16px; }
  .photo-caption { margin-top: 7px; font-size: 9px; }
  .photo-name { display: none; }
  .room-empty-note { left: 38%; top: 44%; }
  .awaken-button { margin-top: 12px; }
}
@media (max-width: 360px) {
  .room-resident { width: 204px; left: 44%; }
  .photo-nook { right: 10px; width: 74px; }
  .room-line { font-size: 17px; }
}
</style>
