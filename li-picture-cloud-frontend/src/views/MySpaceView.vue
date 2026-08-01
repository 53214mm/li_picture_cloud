<template>
  <div class="my-space-page">
    <div class="container">
      <div class="page-header">
        <div>
          <h1>我的空间</h1>
          <p>集中查看个人空间、自己创建的团队，以及加入的其他团队。</p>
        </div>
        <router-link to="/space/create" class="btn btn-primary">+ 创建空间</router-link>
      </div>

      <div v-if="loading" class="state-box">正在加载空间…</div>
      <template v-else>
        <SpaceSection
          title="我的私有空间"
          empty-text="你还没有私有空间，可以创建一个用于个人图片管理。"
          :spaces="groups.privateSpaces"
          @open="openSpace"
        />
        <SpaceSection
          title="我创建的团队空间"
          empty-text="你还没有创建团队空间。"
          :spaces="groups.ownedTeamSpaces"
          @open="openSpace"
        />
        <section class="space-section">
          <div class="section-heading">
            <div>
              <h2>我加入的团队空间</h2>
              <p>这里展示其他人创建并邀请你加入的团队。</p>
            </div>
            <button v-if="joinedError" class="btn btn-outline btn-sm" @click="loadSpaces">重试</button>
          </div>
          <div v-if="joinedError" class="error-box">{{ joinedError }}</div>
          <div v-else-if="groups.joinedTeamSpaces.length" class="space-grid">
            <SpaceCard
              v-for="item in groups.joinedTeamSpaces"
              :key="item.id"
              :space="item"
              :role="item.currentRole"
              @open="openSpace"
            />
          </div>
          <div v-else class="empty-box">你还没有加入其他团队。</div>
        </section>

        <div v-if="ownedError" class="error-box owned-error">
          {{ ownedError }}
          <button class="btn btn-outline btn-sm" @click="loadSpaces">重新加载</button>
        </div>

        <section v-if="groups.privateSpaces.length" class="ai-section">
          <div class="section-heading">
            <div><h2>AI 助手</h2><p>用于个人图片的智能生成、分析与管理。</p></div>
            <button class="btn btn-outline btn-sm" @click="$refs.aiPanel?.clearHistory()">清空对话</button>
          </div>
          <div class="ai-card"><AiAgentPanel ref="aiPanel" :user-id="userStore.currentUser?.id" /></div>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue'
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
const loading = ref(true)

const groups = computed(() => groupMySpaces(
  ownedSpaces.value,
  memberships.value,
  userStore.currentUser?.id
))

const SpaceSection = defineComponent({
  props: {
    title: { type: String, required: true },
    emptyText: { type: String, required: true },
    spaces: { type: Array, required: true }
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
        : h('div', { class: 'empty-box' }, props.emptyText)
    ])
  }
})

onMounted(loadSpaces)

async function loadSpaces() {
  loading.value = true
  ownedError.value = ''
  joinedError.value = ''
  const [ownedResult, joinedResult] = await Promise.allSettled([
    listSpaceVOByPage({ current: 1, pageSize: 20, userId: userStore.currentUser?.id }),
    listMyTeamSpaces()
  ])
  if (ownedResult.status === 'fulfilled') ownedSpaces.value = ownedResult.value.records || []
  else ownedError.value = ownedResult.reason?.message || '加载自己创建的空间失败'
  if (joinedResult.status === 'fulfilled') memberships.value = joinedResult.value || []
  else joinedError.value = joinedResult.reason?.message || '加载加入的团队失败'
  loading.value = false
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
.space-section, .ai-section { margin-bottom: 2.5rem; }
.section-heading { margin-bottom: 0.9rem; }
.section-heading h2 { font-size: 1.25rem; }
.space-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; }
.empty-box, .state-box, .error-box { border: 2px dashed var(--gray-200); padding: 1.5rem; color: var(--gray-600); background: var(--gray-100); }
.error-box { border-style: solid; border-color: var(--red); color: var(--red); background: #fff0ef; }
.owned-error { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem; }
.ai-card { border: 2px solid var(--black); height: 520px; overflow: hidden; background: var(--white); }
.btn-sm { padding: 0.375rem 1rem; font-size: 0.75rem; }
@media (max-width: 900px) { .space-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .space-grid { grid-template-columns: 1fr; } }
</style>
