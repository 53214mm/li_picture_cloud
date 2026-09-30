<template>
  <div class="companion-page">
    <div class="habitat-container">
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
        <CompanionHabitat :inhabited="!!home.companion" :presentation="presentation" :picture="selectedPicture"
                          :awaken-busy="awakenBusy" @awaken="awaken" @visit="visitHabitat" />
        <template v-if="home.companion">
          <nav class="habitat-navigation" aria-label="小屋区域">
            <span class="navigation-caption">在小屋里</span>
            <a href="#habitat-chat" @click.prevent="visitHabitat('chat')"><span>01</span> 窗边对话</a>
            <a href="#habitat-feed" @click.prevent="visitHabitat('feed')"><span>02</span> 照片桌</a>
            <a href="#habitat-journal" @click.prevent="visitHabitat('journal')"><span>03</span> 留影手记</a>
            <a href="#habitat-growth" @click.prevent="visitHabitat('growth')"><span>04</span> 相处与成长</a>
          </nav>
          <div class="habitat-content">
            <section id="habitat-chat" class="home-zone" aria-labelledby="window-title">
              <header class="zone-heading"><div><span>01 / BY THE WINDOW</span><h2 id="window-title" tabindex="-1">在窗边，聊一会儿</h2></div><p>你说的话，她的回应。</p></header>
              <div class="conversation-layout">
                <CompanionChatPanel :chat-policy="home?.chatPolicy" :presentation="presentation"
                                    @presentation-change="chatSignal = $event" />
                <CompanionProposalPanel :refresh-key="panelsRefreshKey" @presentation-change="proposalSignal = $event" />
              </div>
            </section>
            <section id="habitat-feed" class="home-zone" aria-labelledby="desk-title">
              <header class="zone-heading"><div><span>02 / PHOTO DESK</span><h2 id="desk-title" tabindex="-1">把照片放到桌上</h2></div><p>从自己的图库里，挑一张想分享的图片。</p></header>
              <div class="desk-layout">
                <section class="feeding-column" aria-labelledby="feeding-title">
              <div class="feeding-heading">
                <div>
                  <span class="section-index">PHOTO DESK</span>
                  <h2 id="feeding-title" tabindex="-1">用一张图片喂养伙伴</h2>
                </div>
                <span v-if="privateSpace" class="space-name">{{ privateSpace.spaceName || '我的私有空间' }}</span>
              </div>

              <p v-if="interactionNotice" class="feed-message" role="status">{{ interactionNotice }}</p>

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
                  :space-id="privateSpace?.id"
                  :selected-id="selectedPictureId"
                  :loading="sourceLoading"
                  :disabled="feedLocked"
                  @select="selectPicture"
                />
                <div v-if="!sourceLoading && privateSpace && !pictures.length" class="upload-link">
                  <router-link :to="{ path: '/upload', query: { spaceId: privateSpace.id } }">上传一张图片到这个空间 →</router-link>
                </div>
              </template>
              <CompanionFeedingCard :state="feedState" :picture="selectedPicture" :home-fresh="homeFresh" :refreshing="homeSyncBusy"
                                    @submit="submitFeed" @clear="clearSelection" @visit-growth="visitGrowth" />
            </section>
                <aside class="desk-note"><section class="nutrition-banner" aria-label="当前图片营养分析模式">
          <div>
            <span class="nutrition-label">关于这张图片</span>
            <strong>实际来源会逐条写入成长档案</strong>
          </div>
          <p>
            {{ home.nutrition?.notice }}
            <span v-if="home.nutrition?.dailyLimit"> 每日视觉次数上限：{{ home.nutrition.dailyLimit }}。</span>
          </p>
        </section><p class="desk-footnote">图片仍留在原来的空间。选择图片后，由你确认是否喂养。</p></aside>
              </div>
            </section>
            <section id="habitat-journal" class="home-zone" aria-labelledby="journal-title">
              <header class="zone-heading"><div><span>03 / NOTES & STORIES</span><h2 id="journal-title" tabindex="-1">留影手记</h2></div><p>记忆可以确认与纠正，故事由你决定是否保存。</p></header>
              <div class="journal-layout">
                <CompanionMemoryPanel :refresh-key="panelsRefreshKey" />
                <CompanionStoryPanel :pictures="pictures" :refresh-key="panelsRefreshKey" />
              </div>
              <div class="creation-notes">
                <CompanionEmojiPanel :pictures="pictures" :refresh-key="panelsRefreshKey" />
                <CompanionFusionPanel :pictures="pictures" :refresh-key="panelsRefreshKey" />
              </div>
            </section>
            <section id="habitat-growth" class="home-zone" aria-labelledby="habitat-growth-title">
              <header class="zone-heading"><div><span>04 / GROWING TOGETHER</span><h2 id="habitat-growth-title" tabindex="-1">相处，慢慢有了痕迹</h2></div><p>此刻的状态，与一路积累的成长。</p></header>
              <div class="state-grid">
                <CompanionMoodPanel :mood="home.mood" />
                <CompanionRelationshipPanel :relationship="home.relationship" />
              </div>
              <CompanionStats :companion="home.companion" />
              <CompanionGrowthTimeline :records="home.recentGrowth || []" />
            </section>
          </div>
          <footer class="habitat-footer"><span>留影小屋 · 绫页</span><a href="#habitat-title" @click.prevent="visitHabitat('home')">回到小屋 ↑</a></footer>
        </template>
        <div v-else class="uninhabited-note"><section class="nutrition-banner" aria-label="当前图片营养分析模式">
          <div>
            <span class="nutrition-label">关于这张图片</span>
            <strong>实际来源会逐条写入成长档案</strong>
          </div>
          <p>
            {{ home.nutrition?.notice }}
            <span v-if="home.nutrition?.dailyLimit"> 每日视觉次数上限：{{ home.nutrition.dailyLimit }}。</span>
          </p>
        </section></div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, watchEffect } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCompanionPresentationStore } from '@/stores/companionPresentation'
import { getCompanionHome, awakenCompanion, feedCompanion } from '@/api/companion'
import { getSpaceVOById, listSpaceVOByPage } from '@/api/space'
import { getPictureVOById, listPictureVOByPageUncached } from '@/api/picture'
import { useCompanionInteractionStore } from '@/stores/companionInteraction'
import { inspectCompanionPicture } from '@/presentation/companionInteraction'
import CompanionStats from '@/components/companion/CompanionStats.vue'
import CompanionHabitat from '@/components/companion/CompanionHabitat.vue'
import CompanionPicturePicker from '@/components/companion/CompanionPicturePicker.vue'
import CompanionFeedingCard from '@/components/companion/CompanionFeedingCard.vue'
import { createFeedingSession, emptyFeedingState } from '@/presentation/companionFeeding'
import CompanionGrowthTimeline from '@/components/companion/CompanionGrowthTimeline.vue'
import CompanionMoodPanel from '@/components/companion/CompanionMoodPanel.vue'
import CompanionRelationshipPanel from '@/components/companion/CompanionRelationshipPanel.vue'
import CompanionMemoryPanel from '@/components/companion/CompanionMemoryPanel.vue'
import CompanionStoryPanel from '@/components/companion/CompanionStoryPanel.vue'
import CompanionEmojiPanel from '@/components/companion/CompanionEmojiPanel.vue'
import CompanionFusionPanel from '@/components/companion/CompanionFusionPanel.vue'
import CompanionChatPanel from '@/components/companion/CompanionChatPanel.vue'
import CompanionProposalPanel from '@/components/companion/CompanionProposalPanel.vue'
import {
  adoptAuthoritativeHome,
  applyFeedResult,
  buildCompanionPictureQuery,
  selectOldestPrivateSpace
} from '@/utils/companion'

const router = useRouter()
const userStore = useUserStore()
const interaction = useCompanionInteractionStore()
const interactionNotice = ref('')
let selectionGeneration = 0
let sourceGeneration = 0
let pageActive = true
let pageActor = userStore.currentUser?.id == null ? null : String(userStore.currentUser.id)
const isCurrentPage = () => pageActive && pageActor === String(userStore.currentUser?.id)
let feedingSession = null
onBeforeUnmount(() => { pageActive = false; selectionGeneration += 1; sourceGeneration += 1; feedingSession?.destroy() })
const home = ref(null)
const pageLoading = ref(false)
const awakenBusy = ref(false)
const loadError = ref('')
const featureUnavailable = ref(false)
const privateSpace = ref(null)
const pictures = ref([])
const sourceLoading = ref(false)
const sourceError = ref('')
const feedState = ref(emptyFeedingState())
const selectedPictureId = computed(() => feedState.value.pictureId)
const pendingAttempt = computed(() => feedState.value.attempt)
const homeSyncBusy = ref(false)
const feedBusy = computed(() => feedState.value.phase === 'submitting' || homeSyncBusy.value)
const feedLocked = computed(() => feedBusy.value || feedState.value.phase === 'uncertain')
const feedError = computed(() => feedState.value.error || (feedState.value.phase === 'uncertain' ? '结果待确认' : ''))
const panelsRefreshKey = ref(0)
const homeFresh = ref(true)
const chatSignal = ref({})
const proposalSignal = ref({})
const presentationStore = useCompanionPresentationStore()
const { presentation } = storeToRefs(presentationStore)
const presentationSource = presentationStore.acquireSource()
onBeforeUnmount(() => presentationSource.close())

// Publish facts already observed by this page; no extra Shell/API requests.
watchEffect(() => presentationSource.publish({
  home: home.value,
  homeStatus: userStore.authBootstrapError || loadError.value ? 'error'
    : featureUnavailable.value ? 'unavailable'
      : home.value ? 'ready' : pageLoading.value ? 'loading' : 'unobserved',
  homeFresh: homeFresh.value,
  feed: { pending: feedBusy.value, error: Boolean(feedError.value) },
  chat: chatSignal.value,
  proposal: proposalSignal.value
}))

const authError = computed(() => userStore.authBootstrapError)
const selectedPicture = computed(() => pictures.value.find(picture => String(picture.id) === selectedPictureId.value) || null)
watch(() => userStore.currentUser?.id, id => {
  // Authentication-bootstrap retry may populate the actor for the first time.
  if (pageActor === null && id != null) { pageActor = String(id); return }
  if (isCurrentPage()) return
  pageActive = false
  feedingSession?.destroy({ clearRecovery: true })
  home.value = null
  pictures.value = []
  feedState.value = emptyFeedingState()
}, { flush: 'sync' })

function initializeFeeding() {
  if (feedingSession || !home.value?.companion || !isCurrentPage()) return
  let storage
  try { storage = window.sessionStorage } catch { /* Same-page retry still works. */ }
  feedingSession = createFeedingSession({ actor: pageActor, companionId: home.value.companion.id, storage,
    send: feedCompanion, onChange: state => {
      if (!isCurrentPage()) return
      feedState.value = state
      if (state.phase === 'uncertain') homeFresh.value = false
    } })
}
function visitHabitat(area) {
  const ids = { home: 'habitat-title', chat: 'companion-chat-input', feed: 'feeding-title', journal: 'journal-title', growth: 'habitat-growth-title' }
  let target = document.getElementById(ids[area])
  if (area === 'chat' && target?.disabled) target = document.getElementById('window-title')
  target?.scrollIntoView({ block: 'start', behavior: 'instant' })
  target?.focus({ preventScroll: true })
}
function visitGrowth() {
  const target = document.getElementById('growth-title')
  target?.scrollIntoView({ block: 'start', behavior: 'instant' })
  target?.focus({ preventScroll: true })
}

// Consume a short-lived navigation command, never a feed command. Recheck server
// visibility/ownership on arrival and preserve any uncertain idempotent attempt.
watch([() => interaction.destination, () => home.value?.companion?.id, sourceLoading], async () => {
  if (!interaction.destination || !home.value?.companion) return
  if (interaction.destination.pictureId !== null && sourceLoading.value) return
  const command = interaction.takeDestination()
  const cycle = ++selectionGeneration
  if (command.actor !== String(userStore.currentUser?.id)) return
  if (command.pictureId !== null) {
    if (feedBusy.value || pendingAttempt.value) {
      interactionNotice.value = '请先完成或重试当前喂养，再选择新图片。当前选择已保留。'
      return
    }
    interactionNotice.value = '正在重新确认图片…'
    try {
      const candidate = await inspectCompanionPicture(command.pictureId, command.actor, { readPicture: getPictureVOById, readSpace: getSpaceVOById })
      if (cycle !== selectionGeneration || command.actor !== String(userStore.currentUser?.id)) return
      if (feedBusy.value || pendingAttempt.value) {
        interactionNotice.value = '当前喂养尚未结束，已保留原来的选择。'
        return
      }
      const sameSpace = String(privateSpace.value?.id) === String(candidate.space.id)
      privateSpace.value = candidate.space
      pictures.value = [candidate.picture, ...(sameSpace ? pictures.value : []).filter(p => String(p.id) !== String(candidate.picture.id))].slice(0, 12)
      sourceError.value = ''
      selectPicture(candidate.picture.id)
      interactionNotice.value = '已选择这张图片；点击「喂给伙伴」才会开始喂养。'
    } catch {
      if (cycle !== selectionGeneration || command.actor !== String(userStore.currentUser?.id)) return
      interactionNotice.value = '图片已不可用或不在你的私有空间，未更改当前选择。'
    }
  }
  const focusGeneration = selectionGeneration
  await nextTick()
  if (focusGeneration !== selectionGeneration || command.actor !== String(userStore.currentUser?.id)) return
  const target = document.getElementById(command.target === 'chat' ? 'companion-chat-input' : 'feeding-title')
  target?.scrollIntoView({ block: 'center' })
  target?.focus({ preventScroll: true })
}, { flush: 'post' })

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
    const result = await getCompanionHome()
    if (!isCurrentPage()) return
    home.value = result
    homeFresh.value = true
    initializeFeeding()
    if (home.value?.companion) await loadSources()
  } catch (error) {
    if (!isCurrentPage()) return
    if (Number(error.status) === 404) featureUnavailable.value = true
    else loadError.value = error.message || '伙伴状态加载失败，请稍后重试。'
  } finally {
    pageLoading.value = false
  }
}

/**
 * 喂养成功后再取一次权威主页：情绪与关系只随 /companion/me 返回，不随喂养回执下发。
 * 静默执行，失败时保留已合并的本次喂养结果，只影响面板的即时性。
 */
async function refreshAuthoritativeHome() {
  try {
    const authoritative = await getCompanionHome()
    if (!isCurrentPage()) return
    home.value = adoptAuthoritativeHome(home.value, authoritative)
    homeFresh.value = Boolean(authoritative?.companion)
  } catch (error) {
    if (!isCurrentPage()) return
    // 不把刷新失败误报成喂养失败：成长与记忆已经展示，下一次读取会补上最新情绪/关系。
    homeFresh.value = false
    console.warn('[companion] 喂养后权威主页刷新失败，情绪与关系面板可能停留在旧值', error)
  }
}

async function awaken() {
  if (awakenBusy.value) return
  awakenBusy.value = true
  loadError.value = ''
  try {
    const result = await awakenCompanion()
    if (!isCurrentPage()) return
    home.value = result
    homeFresh.value = true
    initializeFeeding()
    await loadSources()
  } catch (error) {
    loadError.value = error.message || '唤醒失败，请稍后再试。'
  } finally {
    awakenBusy.value = false
  }
}

async function loadSources() {
  if (sourceLoading.value) return
  const cycle = ++sourceGeneration
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
    if (!isCurrentPage() || cycle !== sourceGeneration) return
    privateSpace.value = selectOldestPrivateSpace(spacesPage.records || [], userStore.currentUser.id)
    if (privateSpace.value) {
      const picturePage = await listPictureVOByPageUncached(
        buildCompanionPictureQuery(privateSpace.value.id)
      )
      if (!isCurrentPage() || cycle !== sourceGeneration) return
      pictures.value = picturePage.records || []
    }
    // Recovery stores no image data. A picture outside the recent window must
    // pass the same R08 owner/private-space check before its preview is shown.
    if (pendingAttempt.value && !selectedPicture.value) {
      try {
        const candidate = await inspectCompanionPicture(selectedPictureId.value, pageActor, { readPicture: getPictureVOById, readSpace: getSpaceVOById })
        if (!isCurrentPage() || cycle !== sourceGeneration) return
        const sameSpace = String(privateSpace.value?.id) === String(candidate.space.id)
        privateSpace.value = candidate.space
        pictures.value = [candidate.picture, ...(sameSpace ? pictures.value : [])].slice(0, 12)
      } catch { /* A missing preview does not lose the key; Feed rechecks permission. */ }
    }
  } catch (error) {
    if (!isCurrentPage() || cycle !== sourceGeneration) return
    sourceError.value = error.message || '读取私有图库失败，请稍后重试。'
  } finally {
    if (isCurrentPage() && cycle === sourceGeneration) sourceLoading.value = false
  }
}

function selectPicture(pictureId) {
  if (feedLocked.value || !feedingSession?.select(pictureId)) return
  selectionGeneration += 1
  interactionNotice.value = ''
}

async function clearSelection() {
  if (feedLocked.value || !feedingSession?.clear()) return
  selectionGeneration += 1
  interactionNotice.value = ''
  await nextTick()
  if (!isCurrentPage()) return
  const target = document.getElementById('picture-picker-title') || document.getElementById('feeding-title')
  target?.scrollIntoView({ block: 'start', behavior: 'instant' })
  target?.focus({ preventScroll: true })
}

async function submitFeed() {
  if (feedBusy.value || !feedingSession) return
  selectionGeneration += 1
  interactionNotice.value = ''
  const result = await feedingSession.submit()
  if (!result || !isCurrentPage()) return
  // Feed receipts contain no Mood/Relationship. Preserve the revision guard and
  // serialize Home refresh before another feed can start.
  homeSyncBusy.value = true
  home.value = applyFeedResult(home.value, result)
  homeFresh.value = false
  panelsRefreshKey.value += 1
  try { await refreshAuthoritativeHome() }
  finally { if (isCurrentPage()) homeSyncBusy.value = false }
}
</script>

<style scoped>
.companion-page { --black: #374339; --white: #fffdf8; --gray-100: #f4f1e7; --gray-200: #e4e2d5; --gray-400: #b5b8a8; --gray-600: #686f61; --gray-900: #394337; --blue: #466653; --red: #94553f; --yellow: #ede4c8; min-height: calc(100dvh - 4rem); padding: 24px 24px 52px; background: #f7f5ed; color: #394337; }
.habitat-container { max-width: 1200px; margin-inline: auto; min-width: 0; }
.state-panel { max-width: 640px; margin: 64px auto; padding: 40px; border: 1px solid #d4d8c8; border-radius: 8px; background: #fffdf7; text-align: center; }
.state-panel h1 { margin-block: 16px; font-size: clamp(24px, 4vw, 36px); line-height: 1.5; font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif; }
.state-panel p { margin: 16px auto 24px; color: #68715f; line-height: 1.9; }
.state-panel.state-error { border-color: #b88b76; }
.state-kicker, .section-index { font-size: 10px; letter-spacing: .12em; color: #748068; }
.habitat-navigation { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 20px; padding: 16px 24px; border: 1px solid #d8d4c5; border-top: 0; border-radius: 0 0 8px 8px; background: #fffdf6; }
.navigation-caption { margin-right: auto; color: #77816c; font-size: 11px; }
.habitat-navigation a { display: flex; gap: 9px; align-items: center; min-height: 44px; font-size: 12px; padding-inline: 4px; }
.habitat-navigation a span { color: #859179; font-family: Georgia, serif; font-size: 11px; }
.habitat-navigation a:hover, .habitat-footer a:hover { color: #45644e; text-decoration: underline; text-underline-offset: 5px; }
.habitat-content { display: grid; gap: 56px; margin-top: 48px; }
.home-zone { min-width: 0; display: grid; gap: 20px; scroll-margin-top: 24px; }
:deep(#companion-chat-input), :deep(#habitat-title), :deep(#picture-picker-title), :deep(#growth-title), #window-title, #feeding-title, #journal-title, #habitat-growth-title { scroll-margin-top: 96px; }
.zone-heading { display: flex; justify-content: space-between; gap: 24px; align-items: flex-end; }
.zone-heading > div > span { display: block; font-size: 9px; color: #6f7e63; letter-spacing: .16em; margin-bottom: 10px; }
.zone-heading h2 { font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif; font-size: 25px; font-weight: 500; letter-spacing: .06em; }
.zone-heading > p { max-width: 280px; color: #737969; font-size: 12px; line-height: 1.9; }
.conversation-layout { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, .85fr); align-items: start; gap: 20px; }
.desk-layout { display: grid; grid-template-columns: minmax(0, 1fr) 230px; gap: 28px; align-items: start; }
.journal-layout, .creation-notes, .state-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; align-items: start; }
.creation-notes { margin-top: 4px; }
.feeding-column { display: grid; gap: 16px; min-width: 0; }
.feeding-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.feeding-heading h2 { margin-top: 6px; font-size: 18px; font-weight: 500; }
.space-name { max-width: 180px; padding: 5px 9px; color: #536449; background: #e9eddf; font-size: 11px; border-radius: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.desk-note { padding: 24px 20px; border: 1px solid #dedccf; background: #efebdd; border-radius: 3px 20px 3px 3px; }
.nutrition-label, .nutrition-banner strong { display: block; }
.nutrition-label { font-size: 10px; letter-spacing: .06em; color: #7a7b64; margin-bottom: 12px; }
.nutrition-banner strong { font-size: 14px; font-weight: 500; line-height: 1.9; }
.nutrition-banner p, .desk-footnote { margin-top: 16px; font-size: 12px; color: #66705e; line-height: 1.9; }
.desk-footnote { padding-top: 18px; border-top: 1px solid #d5d3c0; }
.uninhabited-note { max-width: 640px; margin: 28px auto; padding: 24px; }
.source-state { padding: 24px; border: 1px dashed #b5bda9; border-radius: 6px; background: #fffdf7; font-size: 13px; line-height: 1.8; }
.source-state.error { border-color: #b07d68; color: #8d4c35; }
.source-state .btn { margin-top: 16px; }
.upload-link { font-size: 12px; text-align: right; text-decoration: underline; }
.feed-message { margin-top: 10px; font-size: 12px; line-height: 1.7; }
.habitat-content .home-zone :deep(.chat-card), .habitat-content .home-zone :deep(.proposal-card), .habitat-content .home-zone :deep(.memory-card), .habitat-content .home-zone :deep(.story-card), .habitat-content .home-zone :deep(.emoji-card), .habitat-content .home-zone :deep(.fusion-card), .habitat-content .home-zone :deep(.mood-card), .habitat-content .home-zone :deep(.relationship-card), .habitat-content .home-zone :deep(.stats-card), .habitat-content .home-zone :deep(.timeline-card), .habitat-content .home-zone :deep(.picker) { min-width: 0; border: 1px solid #d5d9c9; border-radius: 7px; overflow: hidden; }
.habitat-content .home-zone :deep(section > header) { border-bottom: 1px solid #e1e3d6; }
.habitat-content .home-zone :deep(section > header h2) { font-size: 17px; font-weight: 500; }
.habitat-content .home-zone :deep(.eyebrow) { color: #708066; font-size: 9px; font-weight: 500; letter-spacing: .1em; }
.habitat-content .home-zone :deep(.stats-header) { background: #edeedf; }
.habitat-content .home-zone :deep(.btn) { box-shadow: none; border-width: 1px; border-radius: 4px; min-height: 44px; }
.habitat-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 40px; padding-top: 24px; border-top: 1px solid #d9ddcf; color: #6f7a63; font-size: 11px; }
.habitat-footer a { display: grid; align-items: center; min-height: 44px; }
@media (max-width: 1100px) {
  .conversation-layout, .journal-layout, .creation-notes { grid-template-columns: 1fr; }
  .desk-layout { grid-template-columns: minmax(0, 1fr) 200px; gap: 20px; }
  .habitat-navigation { gap: 6px 16px; padding-inline: 20px; }
}
@media (max-width: 767px) {
  .companion-page { padding: 12px 12px 36px; }
  .state-panel { padding: 24px; margin: 24px auto; }
  .habitat-navigation { display: grid; grid-template-columns: 1fr 1fr; padding: 12px 18px; gap: 0 8px; }
  .navigation-caption { display: none; }
  .habitat-content { margin-top: 32px; gap: 40px; }
  .zone-heading { display: grid; gap: 10px; }
  .zone-heading h2 { font-size: 22px; }
  .zone-heading > p { max-width: none; }
  .desk-layout, .state-grid { grid-template-columns: 1fr; }
  .feeding-heading { align-items: flex-start; flex-direction: column; gap: 12px; }
  .desk-note { padding: 20px; }
  .nutrition-banner strong { font-size: 13px; }
  .habitat-content .home-zone :deep(section > header) { padding: 16px; flex-wrap: wrap; gap: 12px; }
}
</style>
