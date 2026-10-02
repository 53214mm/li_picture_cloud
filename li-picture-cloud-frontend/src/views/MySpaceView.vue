<template>
  <div class="my-space-page">
    <div class="container">
      <div class="page-header">
        <div>
          <h1>我的空间</h1>
          <p>集中查看个人空间、自己创建的团队，以及加入的其他团队。</p>
          <p class="user-id">我的用户 ID：<code>{{ userStore.currentUser?.id }}</code>（其他人添加你时需要）</p>
        </div>
        <router-link to="/space/create" class="btn btn-primary">+ 创建空间</router-link>
      </div>

      <div class="owned-spaces" data-testid="owned-spaces" :aria-busy="ownedLoading">
        <div class="read-actions">
          <button class="btn btn-outline btn-sm" :disabled="ownedLoading" @click="loadOwnedSpaces">
            重新加载已创建空间
          </button>
        </div>
        <p v-if="ownedLoading" class="state-box" role="status">正在加载已创建的空间…</p>
        <p v-if="ownedError" class="error-box" role="alert">{{ ownedError }}</p>
        <SpaceSection
          title="我的私有空间"
          empty-text="你还没有私有空间，可以创建一个用于个人图片管理。"
          :spaces="groups.privateSpaces"
          :show-empty="ownedLoaded && !ownedLoading && !ownedError"
          @open="openSpace"
        />
        <SpaceSection
          title="我创建的团队空间"
          empty-text="你还没有创建团队空间。"
          :spaces="groups.ownedTeamSpaces"
          :show-empty="ownedLoaded && !ownedLoading && !ownedError"
          @open="openSpace"
        />
      </div>

      <section class="space-section" data-testid="joined-spaces" :aria-busy="joinedLoading">
        <div class="section-heading">
          <div>
            <h2>我加入的团队空间</h2>
            <p>这里展示其他人创建并邀请你加入的团队。</p>
          </div>
          <button class="btn btn-outline btn-sm" :disabled="joinedLoading" @click="loadJoinedSpaces">
            重新加载加入的团队
          </button>
        </div>
        <p v-if="joinedLoading" class="state-box" role="status">正在加载加入的团队…</p>
        <p v-if="joinedError" class="error-box" role="alert">{{ joinedError }}</p>
        <div v-if="groups.joinedTeamSpaces.length" class="space-grid">
          <SpaceCard
            v-for="item in groups.joinedTeamSpaces"
            :key="item.id"
            :space="item"
            :role="item.currentRole"
            @open="openSpace"
          />
        </div>
        <div v-else-if="joinedLoaded && !joinedLoading && !joinedError" class="empty-box">你还没有加入其他团队。</div>
      </section>

      <section v-if="groups.privateSpaces.length" class="ai-section">
        <div class="section-heading">
          <div><h2>AI 助手</h2><p>用于个人图片的智能生成、分析与管理。</p></div>
          <button class="btn btn-outline btn-sm" @click="$refs.aiPanel?.clearHistory()">清空对话</button>
        </div>
        <div class="ai-card"><AiAgentPanel :key="userStore.currentUser?.id" ref="aiPanel" :user-id="userStore.currentUser?.id" /></div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listSpaceVOByPage } from '@/api/space'
import { listMyTeamSpaces } from '@/api/spaceUser'
import { groupMySpaces } from '@/utils/spaceAccess'
import SpaceCard from '@/components/space/SpaceCard.vue'
import AiAgentPanel from '@/components/AiAgentPanel.vue'

const router = useRouter()
const userStore = useUserStore()

if (!userStore.isLoggedIn) router.replace('/login')

const ownedSpaces = ref([])
const memberships = ref([])
const ownedError = ref('')
const joinedError = ref('')
const ownedLoading = ref(false)
const joinedLoading = ref(false)
const ownedLoaded = ref(false)
const joinedLoaded = ref(false)
let ownedRequest = 0
let joinedRequest = 0

const groups = computed(() => groupMySpaces(
  ownedSpaces.value,
  memberships.value,
  userStore.currentUser?.id
))

const SpaceSection = defineComponent({
  props: {
    title: { type: String, required: true },
    emptyText: { type: String, required: true },
    spaces: { type: Array, required: true },
    showEmpty: { type: Boolean, default: false }
  },
  emits: ['open'],
  setup(props, { emit }) {
    return () => h('section', { class: 'space-section' }, [
      h('div', { class: 'section-heading' }, [h('div', [h('h2', props.title)])]),
      props.spaces.length
        ? h('div', { class: 'space-grid' }, props.spaces.map((space) => h(SpaceCard, {
          key: space.id,
          space,
          role: space.currentRole || '',
          onOpen: (id) => emit('open', id)
        })))
        : props.showEmpty ? h('div', { class: 'empty-box' }, props.emptyText) : null
    ])
  }
})

// Each list owns its loading and retry state; another slow or failed list
// must not erase a successful read or turn an unknown result into an empty one.
watch(() => userStore.currentUser?.id, (userId) => {
  ownedRequest++
  joinedRequest++
  ownedSpaces.value = []
  memberships.value = []
  ownedLoaded.value = false
  joinedLoaded.value = false
  ownedLoading.value = false
  joinedLoading.value = false
  ownedError.value = ''
  joinedError.value = ''
  if (userId == null || userId === '') {
    ownedError.value = '请先登录后查看空间。'
    joinedError.value = '请先登录后查看加入的团队。'
    return
  }
  loadOwnedSpaces()
  loadJoinedSpaces()
}, { immediate: true, flush: 'sync' })

onBeforeUnmount(() => { ownedRequest++; joinedRequest++ })

async function loadOwnedSpaces() {
  const userId = userStore.currentUser?.id
  if (ownedLoading.value || userId == null || userId === '') return
  const request = ++ownedRequest
  const isCurrent = () => request === ownedRequest && userId === userStore.currentUser?.id
  ownedLoading.value = true
  ownedError.value = ''
  try {
    const result = await listSpaceVOByPage({ current: 1, pageSize: 20, userId })
    if (!isCurrent()) return
    ownedSpaces.value = result.records || []
    ownedLoaded.value = true
  } catch (error) {
    if (isCurrent()) ownedError.value = error?.message || '加载自己创建的空间失败'
  } finally {
    if (isCurrent()) ownedLoading.value = false
  }
}

async function loadJoinedSpaces() {
  const userId = userStore.currentUser?.id
  if (joinedLoading.value || userId == null || userId === '') return
  const request = ++joinedRequest
  const isCurrent = () => request === joinedRequest && userId === userStore.currentUser?.id
  joinedLoading.value = true
  joinedError.value = ''
  try {
    const result = await listMyTeamSpaces()
    if (!isCurrent()) return
    memberships.value = result || []
    joinedLoaded.value = true
  } catch (error) {
    if (isCurrent()) joinedError.value = error?.message || '加载加入的团队失败'
  } finally {
    if (isCurrent()) joinedLoading.value = false
  }
}

function openSpace(id) {
  router.push(`/space/${id}`)
}
</script>

<style scoped>
.my-space-page { padding: 3rem 0 5rem; }
.page-header, .section-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap; }
.page-header { margin-bottom: 2.5rem; }
.page-header h1 { font-size: 2rem; margin-bottom: 0.35rem; }
.page-header p, .section-heading p { color: var(--gray-600); font-size: 0.875rem; }
.page-header .user-id { margin-top: .4rem; color: var(--black); }
.user-id code { user-select: all; font-weight: 700; }
.space-section, .ai-section { margin-bottom: 2.5rem; }
.section-heading { margin-bottom: 0.9rem; }
.section-heading h2 { font-size: 1.25rem; }
.space-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; }
.empty-box, .state-box, .error-box { border: 2px dashed var(--gray-200); padding: 1.5rem; color: var(--gray-600); background: var(--gray-100); }
.error-box { border-style: solid; border-color: var(--red); color: var(--red); background: #fff0ef; }
.read-actions { display: flex; justify-content: flex-end; margin-bottom: 0.9rem; }
.state-box, .error-box { margin-bottom: 1rem; }
.ai-card { border: 2px solid var(--black); height: 520px; overflow: hidden; background: var(--white); }
.btn-sm { padding: 0.375rem 1rem; font-size: 0.75rem; }
@media (max-width: 900px) { .space-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 767px) {
  .my-space-page { padding-block: 2rem 3rem; }
  .page-header { margin-bottom: 2rem; }
  .page-header h1 { font-size: 1.75rem; }
  .page-header > .btn { width: 100%; }
  .space-grid { grid-template-columns: 1fr; }
  .read-actions .btn { width: 100%; }
  .ai-card { height: min(70dvh, 40rem); min-height: 30rem; }
}
</style>
