<template>
  <div class="companion-page">
    <div class="container">
      <section v-if="authError" class="state-panel state-error" role="alert">
        <span class="state-kicker">登录状态</span>
        <h1>暂时无法确认登录状态</h1>
        <p>网络响应不稳定。你可以重试，这不会创建或修改伙伴。</p>
        <button class="btn btn-primary" type="button" :disabled="pageLoading" @click="retryAuthentication">
          {{ pageLoading ? '正在重试…' : '重试' }}
        </button>
      </section>

      <section v-else-if="featureUnavailable" class="state-panel">
        <span class="state-kicker">图像伙伴</span>
        <h1>伙伴功能暂未开放</h1>
        <p>入口已经为未来版本准备好，当前环境尚未启用伙伴服务。</p>
        <router-link class="btn btn-outline" to="/gallery">先去看看图库</router-link>
      </section>

      <section v-else-if="loadError" class="state-panel state-error" role="alert">
        <span class="state-kicker">加载失败</span>
        <h1>暂时没能找到伙伴</h1>
        <p>{{ loadError }}</p>
        <button class="btn btn-primary" type="button" :disabled="pageLoading" @click="loadHome">
          {{ pageLoading ? '正在加载…' : '重新加载' }}
        </button>
      </section>

      <section v-else-if="pageLoading && !home" class="state-panel" role="status">
        <span class="state-kicker">图像伙伴</span>
        <h1>正在进入伙伴空间…</h1>
        <p>正在读取伙伴的成长状态与最近记录。</p>
      </section>

      <template v-else-if="home">
        <section class="companion-hero" :class="{ dormant: !home.companion }">
          <div class="hero-copy">
            <span class="hero-index">LIFE / 01</span>
            <h1>{{ home.companion ? '你的图片，正在成为它的经历。' : '让图库里的一张图，成为生命的第一束光。' }}</h1>
            <p v-if="home.companion">
              伙伴会保留每次成长的来源和规则。图片仍然属于原空间，不会被移动、改名或删除。
            </p>
            <p v-else>
              唤醒不会消耗图片。之后你可以从自己的私有空间选择图片，让伙伴获得经验、人格微调与技能成长。
            </p>
            <button v-if="!home.companion" class="btn hero-action" type="button"
                    :disabled="awakenBusy" @click="awaken">
              {{ awakenBusy ? '正在唤醒…' : '唤醒我的伙伴' }}
            </button>
          </div>
          <div class="life-orbit" aria-hidden="true">
            <span class="orbit orbit-one"></span>
            <span class="orbit orbit-two"></span>
            <span class="life-core"></span>
          </div>
        </section>

        <section class="nutrition-banner" aria-label="当前图片营养分析模式">
          <div>
            <span class="nutrition-label">当前请求策略</span>
            <strong>实际来源会逐条写入成长档案</strong>
          </div>
          <p>
            {{ home.nutrition?.notice }}
            <span v-if="home.nutrition?.dailyLimit"> 每日视觉次数上限：{{ home.nutrition.dailyLimit }}。</span>
          </p>
        </section>

        <template v-if="home.companion">
          <div class="companion-grid">
            <CompanionStats :companion="home.companion" />

            <section class="feeding-column" aria-labelledby="feeding-title">
              <div class="feeding-heading">
                <div>
                  <span class="section-index">LIFE / 02</span>
                  <h2 id="feeding-title">用一张图片喂养伙伴</h2>
                </div>
                <span v-if="privateSpace" class="space-name">{{ privateSpace.spaceName || '我的私有空间' }}</span>
              </div>

              <div v-if="sourceError" class="source-state error" role="alert">
                <p>{{ sourceError }}</p>
                <button class="btn btn-outline" type="button" @click="loadSources">重试读取图库</button>
              </div>
              <div v-else-if="!sourceLoading && !privateSpace" class="source-state">
                <p>你还没有私有空间。先创建一个空间，再把图片变成伙伴的成长素材。</p>
                <router-link class="btn btn-outline" to="/space/create">创建私有空间</router-link>
              </div>
              <template v-else>
                <CompanionPicturePicker
                  :pictures="pictures"
                  :selected-id="selectedPictureId"
                  :loading="sourceLoading"
                  :disabled="feedBusy"
                  @select="selectPicture"
                />
                <div v-if="!sourceLoading && privateSpace && !pictures.length" class="upload-link">
                  <router-link to="/upload">上传一张图片到这个空间 →</router-link>
                </div>
                <div class="feed-actions">
                  <button class="btn btn-primary feed-button" type="button"
                          :disabled="feedBusy || !selectedPictureId" @click="submitFeed">
                    {{ feedButtonLabel }}
                  </button>
                  <p v-if="feedError" class="feed-message error" role="alert">{{ feedError }}</p>
                  <p v-else-if="feedNotice" class="feed-message notice" aria-live="polite">{{ feedNotice }}</p>
                  <p v-else class="feed-helper">一次只选择一张。遇到网络中断时，可以安全重试同一次喂养。</p>
                </div>
              </template>
            </section>
          </div>

          <div class="state-grid">
            <CompanionMoodPanel :mood="home.mood" />
            <CompanionRelationshipPanel :relationship="home.relationship" />
          </div>

          <CompanionChatPanel />

          <CompanionMemoryPanel />

          <CompanionGrowthTimeline :records="home.recentGrowth || []" />
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCompanionHome, awakenCompanion, feedCompanion } from '@/api/companion'
import { listSpaceVOByPage } from '@/api/space'
import { listPictureVOByPageUncached } from '@/api/picture'
import CompanionStats from '@/components/companion/CompanionStats.vue'
import CompanionPicturePicker from '@/components/companion/CompanionPicturePicker.vue'
import CompanionGrowthTimeline from '@/components/companion/CompanionGrowthTimeline.vue'
import CompanionMoodPanel from '@/components/companion/CompanionMoodPanel.vue'
import CompanionRelationshipPanel from '@/components/companion/CompanionRelationshipPanel.vue'
import CompanionMemoryPanel from '@/components/companion/CompanionMemoryPanel.vue'
import CompanionChatPanel from '@/components/companion/CompanionChatPanel.vue'
import {
  applyFeedResult,
  beginFeedAttempt,
  buildCompanionPictureQuery,
  selectOldestPrivateSpace,
  shouldRetrySameFeedKey
} from '@/utils/companion'

const router = useRouter()
const userStore = useUserStore()
const home = ref(null)
const pageLoading = ref(false)
const awakenBusy = ref(false)
const loadError = ref('')
const featureUnavailable = ref(false)
const privateSpace = ref(null)
const pictures = ref([])
const sourceLoading = ref(false)
const sourceError = ref('')
const selectedPictureId = ref(null)
const pendingAttempt = ref(null)
const feedBusy = ref(false)
const feedError = ref('')
const feedNotice = ref('')

const authError = computed(() => userStore.authBootstrapError)
const feedButtonLabel = computed(() => {
  if (feedBusy.value) return '伙伴正在吸收…'
  if (pendingAttempt.value && feedError.value) return '重试这次喂养'
  return '喂给伙伴'
})

onMounted(() => {
  if (!authError.value) loadHome()
})

async function retryAuthentication() {
  pageLoading.value = true
  try {
    const user = await userStore.ensureCurrentUser()
    if (!user) {
      await router.replace({ name: 'login', query: { redirect: '/companion' } })
      return
    }
    await loadHome()
  } catch {
    // The store keeps the retryable error for this page.
  } finally {
    pageLoading.value = false
  }
}

async function loadHome() {
  pageLoading.value = true
  loadError.value = ''
  featureUnavailable.value = false
  try {
    home.value = await getCompanionHome()
    if (home.value?.companion) await loadSources()
  } catch (error) {
    if (Number(error.status) === 404) featureUnavailable.value = true
    else loadError.value = error.message || '伙伴状态加载失败，请稍后重试。'
  } finally {
    pageLoading.value = false
  }
}

async function awaken() {
  if (awakenBusy.value) return
  awakenBusy.value = true
  loadError.value = ''
  try {
    home.value = await awakenCompanion()
    await loadSources()
  } catch (error) {
    loadError.value = error.message || '唤醒失败，请稍后再试。'
  } finally {
    awakenBusy.value = false
  }
}

async function loadSources() {
  sourceLoading.value = true
  sourceError.value = ''
  privateSpace.value = null
  pictures.value = []
  try {
    // 这里只展示主人自己的私有空间；后端仍会在真正喂养时再次做图片权限校验。
    const spacesPage = await listSpaceVOByPage({
      current: 1,
      pageSize: 20,
      userId: String(userStore.currentUser.id),
      spaceType: 0,
      sortField: 'createTime',
      sortOrder: 'ascend'
    })
    privateSpace.value = selectOldestPrivateSpace(spacesPage.records || [], userStore.currentUser.id)
    if (privateSpace.value) {
      const picturePage = await listPictureVOByPageUncached(
        buildCompanionPictureQuery(privateSpace.value.id)
      )
      pictures.value = picturePage.records || []
    }
  } catch (error) {
    sourceError.value = error.message || '读取私有图库失败，请稍后重试。'
  } finally {
    sourceLoading.value = false
  }
}

function selectPicture(pictureId) {
  if (feedBusy.value) return
  selectedPictureId.value = String(pictureId)
  // 图片改变意味着业务意图改变，旧图片的幂等 key 绝不能带到新图片上。
  pendingAttempt.value = null
  feedError.value = ''
  feedNotice.value = ''
}

async function submitFeed() {
  // 按钮 disabled 会在下一次渲染才生效；这里先上函数级门闩，挡住同一事件循环里的连点。
  if (feedBusy.value || !selectedPictureId.value) return
  feedBusy.value = true
  const previousAttempt = pendingAttempt.value
  const attempt = beginFeedAttempt(selectedPictureId.value, previousAttempt)
  const wasRetry = attempt === previousAttempt
  pendingAttempt.value = attempt
  feedError.value = ''
  feedNotice.value = ''
  try {
    const result = await feedCompanion(pendingAttempt.value)
    // applyFeedResult 会合并回放记录，同时按 revision 防止旧回放把当前伙伴显示倒退。
    home.value = applyFeedResult(home.value, result)
    feedNotice.value = wasRetry
      ? '这次喂养已安全完成，没有重复成长。'
      : result.outcome === 'FAMILIARITY'
        ? '伙伴认出了这张图片，只获得了一点熟悉感。'
        : '伙伴完成了这次喂养。'
    pendingAttempt.value = null
  } catch (error) {
    // 结果不确定时留下 key，下一次重试由后端决定回放还是继续处理，前端绝不猜测是否已成长。
    const retrySameKey = shouldRetrySameFeedKey(error)
    if (!retrySameKey) pendingAttempt.value = null
    feedError.value = error.status == null
      ? '响应不确定，请用同一请求重试这次喂养'
      : error.message || '喂养失败'
  } finally {
    feedBusy.value = false
  }
}
</script>

<style scoped>
.companion-page { min-height: calc(100dvh - 4rem); padding: 2rem 0 5rem; background: var(--gray-100); }
.companion-page .container { display: grid; gap: 1.5rem; }
.state-panel { max-width: 48rem; margin: 4rem auto; padding: 2.5rem; border: 2px solid var(--black); background: var(--white); text-align: center; }
.state-panel h1 { margin-top: .35rem; font-size: clamp(2rem, 5vw, 3.5rem); line-height: 1.02; }
.state-panel p { max-width: 34rem; margin: 1rem auto 1.5rem; color: var(--gray-600); }
.state-panel .btn { min-height: 44px; }
.state-panel.state-error { border-color: var(--red); }
.state-kicker, .section-index { color: var(--red); font-size: .7rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.companion-hero { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(17rem, .5fr); min-height: 22rem; overflow: hidden; border: 2px solid var(--black); background: var(--blue); color: var(--white); }
.companion-hero.dormant { background: var(--black); }
.hero-copy { z-index: 1; padding: clamp(2rem, 5vw, 4.5rem); }
.hero-index { display: block; margin-bottom: 1.2rem; color: var(--yellow); font-size: .72rem; font-weight: 800; letter-spacing: .14em; }
.hero-copy h1 { max-width: 13ch; font-size: clamp(2.6rem, 6vw, 5.8rem); line-height: .94; letter-spacing: -.055em; }
.hero-copy p { max-width: 44rem; margin-top: 1.25rem; color: rgba(255, 255, 255, .88); font-size: 1rem; }
.hero-action { min-height: 48px; margin-top: 1.75rem; background: var(--yellow); color: var(--black); }
.hero-action:hover { background: var(--white); }
.life-orbit { position: relative; min-height: 100%; overflow: hidden; }
.orbit, .life-core { position: absolute; left: 50%; top: 50%; border: 2px solid currentColor; border-radius: 50%; transform: translate(-50%, -50%); }
.orbit-one { width: 18rem; height: 18rem; color: rgba(255, 255, 255, .5); }
.orbit-two { width: 12rem; height: 12rem; color: var(--yellow); }
.life-core { width: 5rem; height: 5rem; border-width: 8px; background: var(--red); color: var(--white); box-shadow: 0 0 0 1.5rem rgba(255, 255, 255, .1); }
.nutrition-banner { display: grid; grid-template-columns: minmax(13rem, .35fr) minmax(0, 1fr); gap: 1.5rem; align-items: center; padding: 1rem 1.25rem; border: 2px solid var(--black); background: var(--yellow); }
.nutrition-label, .nutrition-banner strong { display: block; }
.nutrition-label { font-size: .68rem; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
.nutrition-banner p { font-size: .85rem; }
.companion-grid { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(23rem, .85fr); gap: 1.5rem; align-items: start; }
.state-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.5rem; align-items: start; }
.feeding-column { min-width: 0; display: grid; gap: 1rem; }
.feeding-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.feeding-heading h2 { font-size: 1.5rem; }
.space-name { max-width: 14rem; padding: .35rem .55rem; overflow: hidden; background: var(--black); color: var(--white); font-size: .72rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.source-state { padding: 1.5rem; border: 2px dashed var(--gray-400); background: var(--white); }
.source-state.error { border: 2px solid var(--red); color: var(--red); }
.source-state .btn { margin-top: 1rem; }
.upload-link { margin-top: -1rem; padding: .75rem 1rem; border: 2px solid var(--black); border-top: 0; background: var(--white); color: var(--blue); font-size: .8rem; font-weight: 700; text-align: right; }
.feed-actions { padding: 1rem; border: 2px solid var(--black); background: var(--white); }
.feed-button { width: 100%; min-height: 48px; }
.feed-button:disabled, .hero-action:disabled { cursor: not-allowed; opacity: .5; }
.feed-message, .feed-helper { margin-top: .75rem; font-size: .8rem; }
.feed-message.error { color: var(--red); }
.feed-message.notice { color: #075d2a; font-weight: 700; }
.feed-helper { color: var(--gray-600); }
@media (max-width: 900px) {
  .companion-grid, .state-grid { grid-template-columns: 1fr; }
}
@media (max-width: 767px) {
  .companion-page { padding-block: 1rem 3rem; }
  .companion-page .container, .companion-grid, .nutrition-banner, .companion-hero { grid-template-columns: 1fr; }
  .companion-page .container { gap: 1rem; }
  .state-panel { margin-block: 2rem; padding: 1.5rem; }
  .companion-hero { min-height: 0; }
  .hero-copy { padding: 2rem 1.25rem; }
  .hero-copy h1 { font-size: clamp(2.5rem, 13vw, 4rem); }
  .life-orbit { min-height: 12rem; border-top: 2px solid rgba(255, 255, 255, .5); }
  .orbit-one { width: 11rem; height: 11rem; }
  .orbit-two { width: 7rem; height: 7rem; }
  .life-core { width: 3.5rem; height: 3.5rem; border-width: 6px; }
  .nutrition-banner { gap: .5rem; }
  .feeding-heading { flex-direction: column; }
  .space-name { max-width: 100%; }
}
</style>
