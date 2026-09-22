<template>
  <div class="landing-home">
    <HeroSection :presentation="presentation" />

    <section class="landing-section landing-wrap" aria-labelledby="companion-title">
      <div class="landing-section__heading">
        <div>
          <span class="landing-kicker">图片与伙伴</span>
          <h2 id="companion-title">选一张图片，开始一次相处</h2>
          <p>图片先属于你的空间。你可以主动选择其中一张，用于伙伴成长。</p>
        </div>
        <router-link v-if="presentation.companion" class="landing-text-link" :to="presentation.companion.to">
          {{ presentation.companion.label }} →
        </router-link>
      </div>

      <div class="companion-story">
        <figure>
          <div class="landing-photo companion-story__photo">
            <img src="/images/mosaic/travel.jpg" alt="相机与旅行地图，本地展示图片" width="800" height="600" loading="lazy">
          </div>
          <figcaption><span>一张留在原空间的图片</span><span>示意，未执行喂养</span></figcaption>
        </figure>
        <div>
          <ol class="relationship-steps">
            <li><div><strong>选一张图片，喂养伙伴</strong><p>从自己的私有空间选择图片。喂养后，原图仍保留在原空间。</p></div></li>
            <li><div><strong>查看成长，确认记忆</strong><p>成长记录保留来源。完成真实图片理解后形成的候选记忆，可以由你确认或纠正。</p></div></li>
            <li><div><strong>以后再回来看看</strong><p>查看情绪、关系和性格状态，也可以和它聊天。主动提议由你选择是否开启。</p></div></li>
          </ol>
          <p v-if="presentation.companionAvailability" class="landing-availability">{{ presentation.companionAvailability }}</p>
        </div>
      </div>

      <div class="story-note">
        <h3>创作从图片故事开始</h3>
        <p v-if="companionEnabled">选择图片、生成大纲与草稿，再由你确认保存。可用性取决于当前模型与服务配置。</p>
        <p v-else>伙伴开放后，可在其中查看图片故事入口；具体能力以当时可用功能为准。</p>
        <span>表情草稿与多图融合目前未开放。</span>
      </div>
    </section>

    <section class="landing-section landing-section--subtle" aria-labelledby="space-title">
      <div class="landing-wrap space-story">
        <div>
          <span class="landing-kicker">图片的归属</span>
          <h2 id="space-title">一个人整理，<br>也可以和团队一起。</h2>
          <div class="space-story__item"><h3>个人空间</h3><p>存放自己的图片，查看容量和已有内容。</p></div>
          <div class="space-story__item"><h3>团队空间</h3><p>查看自己创建和加入的团队，按现有权限协作管理。</p></div>
          <router-link v-if="isMember" class="landing-text-link" to="/space/my">进入空间 →</router-link>
        </div>
        <figure>
          <div class="landing-photo space-story__photo">
            <img src="/images/mosaic/architecture.jpg" alt="建筑局部，本地展示图片" width="800" height="600" loading="lazy">
          </div>
          <figcaption><span>空间示意</span><span>不代表真实团队或相册</span></figcaption>
        </figure>
      </div>
    </section>

    <section class="landing-section landing-wrap" aria-labelledby="pictures-title">
      <div class="landing-section__heading">
        <div>
          <span class="landing-kicker">浏览与查找</span>
          <h2 id="pictures-title">先看看图片</h2>
          <p>不急着注册，也可以先浏览公共图库。</p>
        </div>
        <router-link class="landing-text-link" to="/gallery">图库搜索 →</router-link>
      </div>
      <div class="image-strip">
        <figure v-for="picture in galleryPictures" :key="picture.src">
          <div class="landing-photo image-strip__photo">
            <img :src="picture.src" :alt="picture.alt" width="800" height="600" loading="lazy">
          </div>
          <figcaption>{{ picture.label }} · 展示图片</figcaption>
        </figure>
      </div>
      <p class="landing-fine-print">以上为本地展示图片；搜索与筛选在图库中进行。</p>
    </section>

    <section class="landing-closing landing-wrap" aria-labelledby="closing-title">
      <div>
        <span class="landing-kicker">图片空间</span>
        <h2 id="closing-title">{{ isMember ? '回到你的图片空间' : '先建一个自己的图片空间' }}</h2>
        <p>{{ isMember ? '继续整理图片，或进入你加入的团队。' : '注册后创建个人空间，开始整理图片。' }}</p>
      </div>
      <div class="landing-actions">
        <router-link class="landing-action landing-action--primary" :to="presentation.primary.to">{{ presentation.primary.label }}</router-link>
        <router-link v-if="presentation.secondary" class="landing-action landing-action--quiet" :to="presentation.secondary.to">{{ presentation.secondary.label }}</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import HeroSection from '@/components/HeroSection.vue'
import { COMPANION_UI_ENABLED } from '@/config/features'
import { useUserStore } from '@/stores/user'
import { getLandingPresentation } from '@/utils/landingPresentation'

const user = useUserStore()
const companionEnabled = COMPANION_UI_ENABLED
const isMember = computed(() => user.authReady && user.isLoggedIn)
const presentation = computed(() => getLandingPresentation({ authReady: user.authReady, isLoggedIn: user.isLoggedIn, companionEnabled }))
const galleryPictures = [
  { src: '/images/mosaic/city.jpg', alt: '城市建筑与河流，本地展示图片', label: '城市' },
  { src: '/images/mosaic/nature.jpg', alt: '山谷、树林与河流，本地展示图片', label: '自然' },
  { src: '/images/mosaic/still-life.jpg', alt: '室内静物，本地展示图片', label: '静物' }
]
</script>

<style scoped>
.landing-home { background: var(--lp-bg); color: var(--lp-text-primary); }
.landing-wrap { width: calc(100% - var(--lp-gutter) * 2); max-width: var(--lp-content-wide); margin-inline: auto; }
.landing-section { padding-block: clamp(var(--lp-space-8), 8vw, 7rem); }
.landing-section--subtle { border-block: 1px solid var(--lp-border); background: var(--lp-bg-subtle); }
.landing-kicker { display: block; margin-bottom: var(--lp-space-4); color: var(--lp-accent-strong); font-size: var(--lp-font-size-small); }
h2 { max-width: 14em; font-size: clamp(1.75rem, 3vw, 2.6rem); line-height: 1.25; letter-spacing: -0.035em; }
h3 { font-size: var(--lp-font-size-h3); }
p { color: var(--lp-text-secondary); }
.landing-section__heading { display: flex; align-items: end; justify-content: space-between; gap: var(--lp-space-6); margin-bottom: var(--lp-space-7); }
.landing-section__heading p { max-width: 40rem; margin-top: var(--lp-space-4); font-size: 1rem; }
.landing-text-link { display: inline-flex; min-height: 44px; flex: none; align-items: center; color: var(--lp-accent-strong); font-weight: var(--lp-font-weight-semibold); }
.landing-text-link:hover { text-decoration: underline; }
.landing-photo { overflow: hidden; border-radius: var(--lp-radius-m); background: var(--lp-surface); }
.landing-photo img { width: 100%; height: 100%; object-fit: cover; }
figcaption { display: flex; justify-content: space-between; gap: var(--lp-space-4); margin-top: var(--lp-space-3); color: var(--lp-text-secondary); font-size: var(--lp-font-size-small); }
.companion-story { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(19rem, 1fr); gap: clamp(var(--lp-space-6), 5vw, var(--lp-space-8)); align-items: start; }
.companion-story__photo { aspect-ratio: 1.25; }
.relationship-steps { counter-reset: relationship-step; }
.relationship-steps li { counter-increment: relationship-step; display: grid; grid-template-columns: 34px 1fr; gap: var(--lp-space-4); padding-block: var(--lp-space-5); border-top: 1px solid var(--lp-border); }
.relationship-steps li::before { content: '0' counter(relationship-step); color: var(--lp-accent-strong); font-size: var(--lp-font-size-small); }
.relationship-steps strong { font-weight: var(--lp-font-weight-semibold); }
.relationship-steps p { margin-top: var(--lp-space-2); }
.landing-availability { margin-top: var(--lp-space-4); font-size: var(--lp-font-size-small); }
.story-note { max-width: 48rem; margin: var(--lp-space-7) 0 0 auto; padding: var(--lp-space-5) 0 var(--lp-space-5) var(--lp-space-6); border-left: 1px solid var(--lp-border-strong); }
.story-note p { margin-top: var(--lp-space-2); }
.story-note span { display: block; margin-top: var(--lp-space-3); color: var(--lp-text-secondary); font-size: var(--lp-font-size-small); }
.space-story { display: grid; grid-template-columns: minmax(17rem, 0.8fr) minmax(0, 1.2fr); gap: clamp(var(--lp-space-7), 6vw, 6rem); align-items: center; }
.space-story__item { padding-top: var(--lp-space-5); }
.space-story__item p { margin-top: var(--lp-space-1); }
.space-story .landing-text-link { margin-top: var(--lp-space-4); }
.space-story__photo { aspect-ratio: 1.8; }
.image-strip { display: grid; grid-template-columns: 1.25fr 1fr 0.8fr; gap: var(--lp-space-4); align-items: end; }
.image-strip__photo { aspect-ratio: 1.25; }
.image-strip figure:nth-child(2) .image-strip__photo { aspect-ratio: 1; }
.image-strip figure:nth-child(3) .image-strip__photo { aspect-ratio: 0.85; }
.landing-fine-print { margin-top: var(--lp-space-5); font-size: var(--lp-font-size-small); }
.landing-closing { display: flex; align-items: end; justify-content: space-between; gap: var(--lp-space-7); padding-block: var(--lp-space-8); border-top: 1px solid var(--lp-border); }
.landing-closing p { margin-top: var(--lp-space-3); }
.landing-actions { display: flex; flex-wrap: wrap; gap: var(--lp-space-3); }
.landing-action { display: inline-flex; min-height: 48px; align-items: center; justify-content: center; padding: var(--lp-space-3) var(--lp-space-5); border: 1px solid transparent; border-radius: var(--lp-radius-s); font-weight: var(--lp-font-weight-semibold); transition: color var(--lp-dur-fast) var(--lp-ease-standard), background-color var(--lp-dur-fast) var(--lp-ease-standard), border-color var(--lp-dur-fast) var(--lp-ease-standard); }
.landing-action--primary { background: var(--lp-accent); color: var(--lp-on-accent); }
.landing-action--primary:hover { background: var(--lp-accent-strong); }
.landing-action--quiet { border-color: var(--lp-border); background: var(--lp-surface); }
.landing-action--quiet:hover { border-color: var(--lp-border-strong); background: var(--lp-bg-subtle); }

@media (max-width: 1024px) { .companion-story, .space-story { gap: var(--lp-space-7); } }
@media (max-width: 767px) {
  .landing-section { padding-block: var(--lp-space-8); }
  .landing-section__heading, .landing-closing { align-items: flex-start; flex-direction: column; }
  .companion-story, .space-story { grid-template-columns: 1fr; }
  .space-story__photo { aspect-ratio: 1.5; }
  .image-strip { grid-template-columns: 1fr 1fr; }
  .image-strip figure:first-child { grid-column: 1 / 3; }
  .image-strip figure:first-child .image-strip__photo { aspect-ratio: 1.8; }
  .image-strip figure:nth-child(3) .image-strip__photo { aspect-ratio: 1; }
  .story-note { margin-top: var(--lp-space-6); padding-left: var(--lp-space-5); }
}
@media (max-width: 480px) {
  .landing-section { padding-block: var(--lp-space-7); }
  .landing-section__heading { margin-bottom: var(--lp-space-6); }
  figcaption { flex-direction: column; gap: var(--lp-space-1); }
  .relationship-steps li { grid-template-columns: 28px 1fr; gap: var(--lp-space-3); }
  .landing-actions { display: grid; grid-template-columns: 1fr 1fr; width: 100%; }
  .landing-actions > :only-child { grid-column: 1 / 3; }
}
</style>
