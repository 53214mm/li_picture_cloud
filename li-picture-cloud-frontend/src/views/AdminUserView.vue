<template>
  <div class="admin-page">
    <div class="container">
      <div class="admin-header">
        <h1>用户管理</h1>
        <button class="btn btn-primary" @click="showAddModal = true">+ 创建用户</button>
      </div>

      <!-- 搜索栏 -->
      <div class="toolbar">
        <input v-model="query.userAccount" class="input" style="max-width:200px" placeholder="账号筛选" @input="loadUsers" />
        <input v-model="query.userName" class="input" style="max-width:200px" placeholder="昵称筛选" @input="loadUsers" />
        <select v-model="query.userRole" class="input" style="max-width:150px" @change="loadUsers">
          <option value="">全部角色</option>
          <option value="user">普通用户</option>
          <option value="admin">管理员</option>
        </select>
      </div>

      <!-- 用户表格 -->
      <table class="table" v-if="!loading">
        <thead>
          <tr>
            <th>ID</th>
            <th>账号</th>
            <th>昵称</th>
            <th>角色</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td class="id-cell" data-label="ID">{{ u.id }}</td>
            <td data-label="账号">{{ u.userAccount }}</td>
            <td data-label="昵称">{{ u.userName }}</td>
            <td data-label="角色">
              <span class="role-badge" :class="u.userRole === 'admin' ? 'role-admin' : 'role-user'">
                {{ u.userRole === 'admin' ? '管理员' : '用户' }}
              </span>
            </td>
            <td data-label="创建时间">{{ formatDate(u.createTime) }}</td>
            <td class="action-cell" data-label="操作">
              <button class="btn-sm-text" @click="openEdit(u)">编辑</button>
              <button class="btn-sm-text danger" @click="handleDelete(u.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="loading">加载中…</div>

      <!-- 分页 -->
      <div class="pagination" v-if="total > query.pageSize">
        <button :disabled="query.current <= 1" @click="goPage(query.current - 1)">上一页</button>
        <span>第 {{ query.current }} / {{ totalPages }} 页</span>
        <button :disabled="query.current >= totalPages" @click="goPage(query.current + 1)">下一页</button>
        <span class="jumper">跳至
          <input v-model.number="jumpPage" class="jump-input" @keyup.enter="goPage(jumpPage)" placeholder="页数" />
          页
          <button class="btn-jump" @click="goPage(jumpPage)">GO</button>
        </span>
      </div>

      <!-- 创建 / 编辑弹窗 -->
      <div v-if="showAddModal || editingUser" class="modal-overlay" @click.self="closeModal">
        <div class="modal">
          <h2>{{ editingUser ? '编辑用户' : '创建用户' }}</h2>
          <form @submit.prevent="handleSave" class="modal-form">
            <div v-if="!editingUser" class="field">
              <span>账号</span>
              <input v-model="addForm.userAccount" class="input" placeholder="至少 4 位" required />
            </div>
            <div v-if="!editingUser" class="field">
              <span>密码</span>
              <input v-model="addForm.userPassword" class="input" type="password" placeholder="至少 8 位" required />
            </div>
            <div class="field">
              <span>昵称</span>
              <input v-model="addForm.userName" class="input" placeholder="用户昵称" />
            </div>
            <div class="field">
              <span>头像 URL</span>
              <input v-model="addForm.userAvatar" class="input" placeholder="头像链接" />
            </div>
            <div class="field">
              <span>简介</span>
              <input v-model="addForm.userProfile" class="input" placeholder="个人简介" />
            </div>
            <div class="field">
              <span>角色</span>
              <select v-model="addForm.userRole" class="input">
                <option value="user">普通用户</option>
                <option value="admin">管理员</option>
              </select>
            </div>
            <div v-if="modalError" class="form-error">{{ modalError }}</div>
            <div class="modal-actions">
              <button type="button" class="btn btn-outline" @click="closeModal">取消</button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                {{ saving ? '保存中…' : '保存' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listUserByPage, addUser, updateUser, deleteUser } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

// 权限守卫
if (!userStore.isAdmin) router.replace('/login')

const users = ref([])
const loading = ref(false)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const jumpPage = ref(null)
function goPage(page) {
  if (!page || page < 1) page = 1
  if (page > totalPages.value) page = totalPages.value
  query.current = page
  jumpPage.value = null
  loadUsers()
}
const query = reactive({ current: 1, pageSize: 10, userAccount: '', userName: '', userRole: '' })

const showAddModal = ref(false)
const editingUser = ref(null)
const saving = ref(false)
const modalError = ref('')
const addForm = reactive({ userAccount: '', userPassword: '', userName: '', userAvatar: '', userProfile: '', userRole: 'user' })

onMounted(() => loadUsers())

async function loadUsers() {
  loading.value = true
  try {
    const res = await listUserByPage({ ...query })
    users.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error('加载用户列表失败', e)
  } finally {
    loading.value = false
  }
}

function openEdit(u) {
  editingUser.value = u
  Object.assign(addForm, {
    userName: u.userName || '',
    userAvatar: u.userAvatar || '',
    userProfile: u.userProfile || '',
    userRole: u.userRole
  })
}

function closeModal() {
  showAddModal.value = false
  editingUser.value = null
  modalError.value = ''
  Object.assign(addForm, { userAccount: '', userPassword: '', userName: '', userAvatar: '', userProfile: '', userRole: 'user' })
}

async function handleSave() {
  modalError.value = ''
  saving.value = true
  try {
    if (editingUser.value) {
      await updateUser({ id: editingUser.value.id, ...addForm })
    } else {
      if (!addForm.userAccount || !addForm.userPassword) {
        modalError.value = '账号和密码必填'
        saving.value = false
        return
      }
      await addUser({ ...addForm })
    }
    closeModal()
    loadUsers()
  } catch (e) {
    modalError.value = e.message || '操作失败'
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  if (!confirm('确定删除该用户？')) return
  try {
    await deleteUser(id)
    loadUsers()
  } catch (e) {
    alert(e.message || '删除失败')
  }
}

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '-'
}
</script>

<style scoped>
.admin-page { padding: 3rem 0 5rem; }
.admin-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem; }
.admin-header h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; }
.toolbar { display: flex; gap: 0.75rem; margin-bottom: 1.5rem; flex-wrap: wrap; }

/* 表格 */
.table { width: 100%; border-collapse: collapse; }
.table th, .table td { text-align: left; padding: 0.875rem 1rem; border-bottom: 1px solid var(--gray-200); }
.table th { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--gray-400); }
.table td { font-size: 0.9375rem; }
.id-cell { font-family: monospace; font-size: 0.8125rem; max-width: 120px; overflow: hidden; text-overflow: ellipsis; }
.action-cell { display: flex; gap: 0.75rem; }
.btn-sm-text { font-size: 0.8125rem; font-weight: 500; text-decoration: underline; }
.btn-sm-text.danger { color: var(--red); }
.role-badge {
  display: inline-block; padding: 0.125rem 0.625rem;
  font-size: 0.75rem; font-weight: 600; letter-spacing: 0.03em;
}
.role-admin { background: var(--black); color: var(--white); }
.role-user { background: var(--gray-200); color: var(--gray-900); }
.loading { text-align: center; padding: 3rem; color: var(--gray-400); }

/* 分页 */
.pagination { display: flex; align-items: center; justify-content: center; gap: 1.5rem; margin-top: 2rem; font-size: 0.875rem; }
.pagination button { font-weight: 600; padding: 0.5rem 1rem; border: 2px solid var(--black); }
.pagination button:disabled { opacity: 0.3; cursor: default; }
.jumper { font-size: 0.8125rem; color: var(--gray-600); display: flex; align-items: center; gap: 0.375rem; }
.jump-input { width: 56px; padding: 0.375rem 0.5rem; text-align: center; border: 1.5px solid var(--gray-200); font-size: 0.8125rem; font-family: inherit; outline: none; }
.jump-input:focus { border-color: var(--black); }
.btn-jump { padding: 0.25rem 0.625rem; font-size: 0.75rem; font-weight: 600; border: 1.5px solid var(--black); background: var(--white); cursor: pointer; }
.btn-jump:hover { background: var(--black); color: var(--white); }

/* 弹窗 */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 200;
}
.modal {
  background: var(--white); width: 100%; max-width: 480px;
  padding: 2.5rem; border: 2px solid var(--black);
}
.modal h2 { font-size: 1.5rem; font-weight: 700; margin-bottom: 1.5rem; }
.modal-form { display: flex; flex-direction: column; gap: 1rem; }
.modal-actions { display: flex; gap: 0.75rem; margin-top: 0.5rem; }
.modal-actions .btn { flex: 1; }
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field span { font-size: 0.8125rem; font-weight: 600; }
.form-error { padding: 0.5rem 0.75rem; background: #FFF0EF; color: var(--red); font-size: 0.8125rem; font-weight: 500; }

@media (max-width: 767px) {
  .admin-page { padding-block: 2rem 3rem; }
  .admin-header { align-items: stretch; flex-direction: column; gap: 1rem; }
  .admin-header h1 { font-size: 1.75rem; }
  .admin-header .btn { width: 100%; }
  .toolbar { display: grid; grid-template-columns: 1fr; }
  .toolbar .input { max-width: none !important; }
  .table, .table tbody, .table tr, .table td { display: block; width: 100%; }
  .table thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  .table tr { margin-bottom: 1rem; padding: 0.5rem 1rem; border: 2px solid var(--gray-200); }
  .table td { display: grid; grid-template-columns: minmax(6.5rem, 35%) 1fr; gap: 0.75rem; padding: 0.75rem 0; overflow-wrap: anywhere; }
  .table td::before { content: attr(data-label); color: var(--gray-600); font-size: 0.75rem; font-weight: 700; }
  .id-cell { max-width: none; }
  .action-cell { align-items: center; }
  .btn-sm-text { min-height: 44px; padding-inline: 0.75rem; }
  .pagination { gap: 0.75rem; flex-wrap: wrap; }
  .jumper { display: none; }
  .modal-overlay { align-items: flex-end; }
  .modal { max-height: 90dvh; overflow-y: auto; padding: 1.5rem 1rem max(1rem, env(safe-area-inset-bottom)); border-inline: 0; border-bottom: 0; }
}
</style>
