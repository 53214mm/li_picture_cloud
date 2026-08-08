<template>
  <div class="panel-overlay" @click.self="emit('close')">
    <aside class="member-panel" aria-label="团队成员管理">
      <div class="panel-header">
        <div><h2>团队成员</h2><p>按用户 ID 添加成员并分配角色。</p></div>
        <button class="close-btn" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </div>

      <form class="add-form" @submit.prevent="handleAdd">
        <label>用户 ID<input v-model.trim="newUserId" class="input" inputmode="numeric" placeholder="例如：190000000000000001" /></label>
        <label>角色
          <select v-model="newRole" class="input">
            <option v-for="role in roles" :key="role" :value="role">{{ spaceRoleText(role) }}</option>
          </select>
        </label>
        <button class="btn btn-primary" :disabled="saving">{{ saving ? '添加中…' : '添加成员' }}</button>
      </form>

      <div v-if="error" class="form-error">{{ error }}</div>
      <div v-if="loading" class="panel-state">正在加载成员…</div>
      <div v-else-if="!members.length" class="panel-state">团队中还没有成员。</div>
      <div v-else class="member-list">
        <article v-for="member in members" :key="member.id" class="member-row">
          <div class="member-main">
            <img v-if="member.user?.userAvatar" :src="member.user.userAvatar" alt="" class="avatar" />
            <div class="member-copy">
              <strong>{{ member.user?.userName || `用户 ${member.userId}` }}</strong>
              <span>ID：{{ member.userId }}</span>
              <span v-if="isCreator(member)" class="creator-badge">创建者</span>
            </div>
          </div>
          <div class="member-actions">
            <select
              class="input role-select"
              :value="member.spaceRole"
              :disabled="saving || isCreator(member)"
              :title="isCreator(member) ? '团队创建者必须保留管理员角色' : '修改角色'"
              @change="handleRoleChange(member, $event.target.value)"
            >
              <option v-for="role in roles" :key="role" :value="role">{{ spaceRoleText(role) }}</option>
            </select>
            <button
              class="btn btn-danger btn-sm"
              type="button"
              :disabled="saving || isCreator(member) || isCurrentUser(member)"
              :title="removeReason(member)"
              @click="handleRemove(member)"
            >移除</button>
          </div>
        </article>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { addSpaceUser, deleteSpaceUser, editSpaceUser, listSpaceUsers } from '@/api/spaceUser'
import { SPACE_ROLE, spaceRoleText } from '@/constants/space'

const props = defineProps({
  spaceId: { type: [Number, String], required: true },
  creatorId: { type: [Number, String], required: true },
  currentUserId: { type: [Number, String], required: true },
  canManage: { type: Boolean, required: true }
})
const emit = defineEmits(['close'])

const roles = [SPACE_ROLE.VIEWER, SPACE_ROLE.EDITOR, SPACE_ROLE.ADMIN]
const members = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const newUserId = ref('')
const newRole = ref(SPACE_ROLE.VIEWER)

onMounted(loadMembers)

async function loadMembers() {
  if (!props.canManage) return
  loading.value = true
  error.value = ''
  try {
    members.value = await listSpaceUsers(props.spaceId) || []
  } catch (e) {
    error.value = e.message || '加载团队成员失败'
  } finally {
    loading.value = false
  }
}

async function handleAdd() {
  if (!/^\d+$/.test(newUserId.value)) {
    error.value = '请输入正确的数字用户 ID'
    return
  }
  await mutate(async () => {
    await addSpaceUser({ spaceId: props.spaceId, userId: newUserId.value, spaceRole: newRole.value })
    newUserId.value = ''
  })
}

async function handleRoleChange(member, role) {
  if (!window.confirm(`确定将该成员设为${spaceRoleText(role)}吗？`)) return
  await mutate(() => editSpaceUser({ id: member.id, spaceRole: role }))
}

async function handleRemove(member) {
  if (!window.confirm(`确定移除用户 ${member.user?.userName || member.userId} 吗？`)) return
  await mutate(() => deleteSpaceUser(member.id))
}

async function mutate(operation) {
  saving.value = true
  error.value = ''
  try {
    await operation()
    await loadMembers()
  } catch (e) {
    error.value = e.message || '操作失败'
  } finally {
    saving.value = false
  }
}

function isCreator(member) {
  return String(member.userId) === String(props.creatorId)
}

function isCurrentUser(member) {
  return String(member.userId) === String(props.currentUserId)
}

function removeReason(member) {
  if (isCreator(member)) return '不能移除团队创建者'
  if (isCurrentUser(member)) return '不能在成员管理中移除自己'
  return '移除成员'
}
</script>

<style scoped>
.panel-overlay { position: fixed; inset: 0; z-index: 300; background: rgba(0,0,0,.45); display: flex; justify-content: flex-end; }
.member-panel { width: min(620px, 100%); height: 100%; overflow-y: auto; background: var(--white); border-left: 2px solid var(--black); padding: 1.5rem; }
.panel-header { display: flex; justify-content: space-between; gap: 1rem; margin-bottom: 1.5rem; }
.panel-header h2 { font-size: 1.5rem; }
.panel-header p { color: var(--gray-600); font-size: .875rem; margin-top: .25rem; }
.close-btn { border: 0; background: transparent; font-size: 2rem; cursor: pointer; line-height: 1; }
.add-form { border: 2px solid var(--gray-200); padding: 1rem; display: grid; grid-template-columns: 1fr 150px auto; gap: .75rem; align-items: end; margin-bottom: 1rem; }
.add-form label { display: flex; flex-direction: column; gap: .35rem; font-size: .75rem; font-weight: 600; }
.form-error { padding: .75rem; color: var(--red); background: #fff0ef; margin-bottom: 1rem; }
.panel-state { padding: 2rem; text-align: center; color: var(--gray-600); }
.member-list { display: flex; flex-direction: column; gap: .75rem; }
.member-row { border: 2px solid var(--gray-200); padding: 1rem; display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
.member-main, .member-actions { display: flex; align-items: center; gap: .75rem; }
.avatar { width: 42px; height: 42px; object-fit: cover; border: 1px solid var(--black); }
.member-copy { display: flex; flex-direction: column; gap: .2rem; }
.member-copy span { color: var(--gray-600); font-size: .75rem; }
.creator-badge { color: var(--blue) !important; font-weight: 700; }
.role-select { width: 120px; }
.btn-sm { padding: .375rem .75rem; font-size: .75rem; }
@media (max-width: 640px) {
  .add-form { grid-template-columns: 1fr; }
  .member-row { align-items: flex-start; flex-direction: column; }
  .member-actions { width: 100%; }
}
@media (max-width: 767px) {
  .member-panel { width: 100%; padding: max(1rem, env(safe-area-inset-top)) 1rem max(1rem, env(safe-area-inset-bottom)); border-left: 0; }
  .close-btn { width: 44px; height: 44px; flex: 0 0 44px; }
  .add-form { grid-template-columns: 1fr; }
  .member-row { align-items: flex-start; flex-direction: column; }
  .member-main { width: 100%; min-width: 0; }
  .member-copy { min-width: 0; overflow-wrap: anywhere; }
  .member-actions { width: 100%; flex-wrap: wrap; }
  .role-select { width: auto; flex: 1 1 8rem; }
  .member-actions .btn { flex: 1; }
}
</style>
