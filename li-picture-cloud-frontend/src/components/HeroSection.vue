<template>
  <section class="landing-hero" aria-labelledby="landing-title">
    <div class="landing-hero__intro">
      <div>
        <span class="landing-kicker">你的图片空间</span>
        <h1 id="landing-title">管理图片，<br>也用图片培养伙伴。</h1>
      </div>
      <div class="landing-hero__copy">
        <p class="landing-hero__lead">
          这里可以上传、搜索和整理图片，管理个人与团队空间。数字伙伴有自己的成长记录，
          你可以用图片喂养它，也可以回来和它聊聊。
        </p>
        <div class="landing-actions" aria-label="首页主要操作">
          <router-link class="landing-action landing-action--primary" :to="presentation.primary.to">
            {{ presentation.primary.label }}
          </router-link>
          <router-link
            v-if="presentation.secondary"
            class="landing-action landing-action--quiet"
            :to="presentation.secondary.to"
          >
            {{ presentation.secondary.label }}
          </router-link>
        </div>
        <p v-if="presentation.companionAvailability" class="landing-availability">
          {{ presentation.companionAvailability }}
        </p>
      </div>
    </div>

    <div class="landing-canvas" aria-label="图片与伙伴栖居位概念示意">
      <figure class="landing-canvas__photo landing-canvas__photo--main">
        <img
          src="/images/mosaic/nature.jpg"
          alt="暮色中的河谷，本地展示图片"
          width="800"
          height="600"
          fetchpriority="high"
        >
      </figure>
      <figure class="landing-canvas__photo landing-canvas__photo--travel">
        <img src="/images/mosaic/travel.jpg" alt="旅行地图和相机，本地展示图片" width="800" height="600">
      </figure>
      <figure class="landing-canvas__photo landing-canvas__photo--city">
        <img src="/images/mosaic/city.jpg" alt="城市建筑，本地展示图片" width="800" height="600">
      </figure>
      <div class="landing-habitat">
        <span class="landing-habitat__shape" aria-hidden="true"></span>
        <span class="landing-habitat__ground" aria-hidden="true"></span>
        <strong>伙伴的位置</strong>
        <span>概念示意 · 非正式角色</span>
      </div>
    </div>
    <p class="landing-canvas__caption">
      图片与栖居位的关系示意 · 本地展示图片，不代表用户空间或伙伴喜好
    </p>
  </section>
</template>

<script setup>
defineProps({
  presentation: {
    type: Object,
    required: true
  }
})
</script>

<style scoped>
.landing-hero {
  width: calc(100% - var(--lp-gutter) * 2);
  max-width: var(--lp-content-wide);
  margin-inline: auto;
  padding-block: var(--lp-space-7) var(--lp-space-8);
}

.landing-hero__intro {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(20rem, 0.85fr);
  gap: clamp(var(--lp-space-6), 5vw, var(--lp-space-8));
  align-items: start;
  margin-bottom: var(--lp-space-7);
}

.landing-kicker {
  display: block;
  margin-bottom: var(--lp-space-4);
  color: var(--lp-accent-strong);
  font-size: var(--lp-font-size-small);
}

h1 {
  max-width: 10em;
  font-size: clamp(2.5rem, 4.6vw, 3.75rem);
  line-height: 1.12;
  letter-spacing: -0.045em;
}

.landing-hero__lead {
  max-width: 36rem;
  color: var(--lp-text-secondary);
  font-size: 1.0625rem;
  line-height: 1.9;
}

.landing-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-5);
}

.landing-action {
  display: inline-flex;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  padding: var(--lp-space-3) var(--lp-space-5);
  border: 1px solid transparent;
  border-radius: var(--lp-radius-s);
  font-weight: var(--lp-font-weight-semibold);
  transition: color var(--lp-dur-fast) var(--lp-ease-standard), background-color var(--lp-dur-fast) var(--lp-ease-standard), border-color var(--lp-dur-fast) var(--lp-ease-standard);
}

.landing-action--primary { background: var(--lp-accent); color: var(--lp-on-accent); }
.landing-action--primary:hover { background: var(--lp-accent-strong); }
.landing-action--quiet { border-color: var(--lp-border); background: var(--lp-surface); }
.landing-action--quiet:hover { border-color: var(--lp-border-strong); background: var(--lp-bg-subtle); }

.landing-availability {
  max-width: 36rem;
  margin-top: var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-font-size-small);
}

.landing-canvas {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(9rem, 0.85fr) minmax(9rem, 0.72fr);
  grid-template-rows: minmax(10rem, 12vw) minmax(10rem, 12vw);
  gap: var(--lp-space-4);
}

.landing-canvas__photo {
  min-width: 0;
  overflow: hidden;
  border-radius: var(--lp-radius-m);
  background: var(--lp-bg-subtle);
}

.landing-canvas__photo--main { grid-row: span 2; }

.landing-canvas img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.landing-habitat {
  grid-column: 3;
  grid-row: 1 / 3;
  min-width: 0;
  padding: var(--lp-space-5);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius-m);
  background: var(--lp-surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.landing-habitat__shape {
  width: 70px;
  height: 88px;
  border: 2px solid var(--lp-accent);
  border-radius: 52% 48% 43% 57% / 42% 45% 55% 58%;
  background: var(--lp-accent-soft);
}

.landing-habitat__ground {
  width: min(100%, 110px);
  height: 1px;
  margin-top: var(--lp-space-5);
  background: var(--lp-border-strong);
}

.landing-habitat strong { margin-top: var(--lp-space-5); font-size: 1rem; }

.landing-habitat > span:last-child {
  margin-top: var(--lp-space-1);
  color: var(--lp-text-secondary);
  font-size: var(--lp-font-size-caption);
}

.landing-canvas__caption {
  margin-top: var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-font-size-small);
}

@media (max-width: 1024px) {
  .landing-hero__intro { gap: var(--lp-space-6); }
  .landing-canvas {
    grid-template-columns: minmax(0, 1.4fr) minmax(8rem, 0.8fr) minmax(8rem, 0.8fr);
    grid-template-rows: 150px 150px;
  }
}

@media (max-width: 767px) {
  .landing-hero { padding-block: var(--lp-space-6) var(--lp-space-7); }
  .landing-hero__intro { grid-template-columns: 1fr; margin-bottom: var(--lp-space-6); }
  h1 { font-size: clamp(2.3rem, 10vw, 3.1rem); }
  .landing-hero__lead { font-size: 1rem; }
  .landing-canvas {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    grid-template-rows: minmax(210px, 58vw) 190px;
    gap: var(--lp-space-3);
  }
  .landing-canvas__photo--main { grid-column: 1 / 3; grid-row: 1; }
  .landing-canvas__photo--city { display: none; }
  .landing-habitat { grid-column: 2; grid-row: 2; padding: var(--lp-space-4); }
  .landing-habitat__shape { width: 48px; height: 60px; }
  .landing-habitat__ground, .landing-habitat strong { margin-top: var(--lp-space-3); }
}

@media (max-width: 480px) {
  .landing-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .landing-actions > :only-child { grid-column: 1 / 3; }
}
</style>
