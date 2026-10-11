<template>
  <div class="add-to-space" data-testid="add-to-space">
    <button type="button" class="btn btn-outline" :disabled="busy" @click="start">
      {{ busy ? '处理中…' : '添加到我的空间' }}
    </button>
    <div v-if="choosing" class="space-picker" role="dialog" aria-label="选择要添加到的空间">
      <p>添加到哪个空间？</p>
      <button v-for="target in targets" :key="target.id" type="button" class="space-option" @click="add(target)">
        <strong>{{ target.name || '未命名空间' }}</strong><span>{{ target.kind }}</span>
      </button>
      <button type="button" class="link-button" @click="choosing = false">取消</button>
    </div>
    <p v-if="notice" class="notice" role="status">
      {{ notice }}
      <router-link v-if="addedId" :to="`/picture/${addedId}`">去看看</router-link>
      <router-link v-if="needsSpace" to="/space/create">创建空间</router-link>
    </p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { listMySpaces } from '@/api/space'
import { listMyTeamSpaces } from '@/api/spaceUser'
import { copyPictureToSpace } from '@/api/picture'
import { copyTargets, groupMySpaces } from '@/utils/spaceAccess'

const props = defineProps({ picture: { type: Object, required: true } })

const userStore = useUserStore()
const busy = ref(false)
const choosing = ref(false)
const targets = ref([])
const notice = ref('')
const addedId = ref(null)
const needsSpace = ref(false)

async function start() {
  if (busy.value) return
  busy.value = true
  notice.value = ''
  addedId.value = null
  needsSpace.value = false
  try {
    const userId = userStore.currentUser?.id
    const [owned, memberships] = await Promise.all([listMySpaces(userId), listMyTeamSpaces()])
    targets.value = copyTargets(groupMySpaces(owned?.records || [], memberships || [], userId), props.picture.spaceId)
    if (!targets.value.length) {
      notice.value = '你还没有可以放图的空间。'
      needsSpace.value = true
    } else if (targets.value.length === 1) {
      await add(targets.value[0])
    } else {
      choosing.value = true
    }
  } catch (error) {
    notice.value = error?.message || '读取空间失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}

async function add(target) {
  choosing.value = false
  busy.value = true
  try {
    const copied = await copyPictureToSpace(props.picture.id, target.id)
    addedId.value = copied?.id ?? null
    notice.value = `已添加到「${target.name || '未命名空间'}」。`
  } catch (error) {
    notice.value = error?.message || '添加失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.add-to-space { position: relative; display: inline-block; }
.space-picker { position: absolute; z-index: 20; top: calc(100% + .5rem); left: 0; min-width: 240px; padding: .75rem;
  background: var(--white, #fff); border: 1px solid var(--gray-300, #ddd); box-shadow: 0 8px 24px rgba(0, 0, 0, .12); }
.space-picker p { margin-bottom: .5rem; font-size: .875rem; }
.space-option { display: flex; justify-content: space-between; gap: 1rem; width: 100%; padding: .5rem .25rem;
  border: 0; border-bottom: 1px solid var(--gray-200, #eee); background: none; text-align: left; cursor: pointer; }
.space-option:hover { background: var(--gray-100, #f5f5f5); }
.space-option span { color: var(--gray-600, #666); font-size: .8rem; }
.link-button { margin-top: .5rem; border: 0; background: none; color: var(--gray-600, #666); cursor: pointer; }
.notice { position: absolute; z-index: 20; top: calc(100% + .4rem); left: 0; white-space: nowrap; padding: .4rem .6rem;
  background: var(--white, #fff); border: 1px solid var(--gray-200, #eee); font-size: .875rem; color: var(--gray-700, #444); }
.notice a { margin-left: .5rem; text-decoration: underline; }
</style>
