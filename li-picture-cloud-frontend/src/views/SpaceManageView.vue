<template>
  <div class="space-page">
    <div class="container">
      <div class="page-header">
        <div>
          <h1>空间管理</h1>
          <p class="subtitle">查看自己创建的私有空间和团队空间，管理容量与基础信息。</p>
        </div>
        <div class="header-right">
          <router-link to="/space/my" class="btn btn-outline btn-sm">我的空间</router-link>
          <router-link to="/space/create" class="btn btn-primary btn-sm">+ 创建空间</router-link>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="loading">加载中…</div>

      <!-- 空状态 -->
      <div v-else-if="spaces.length === 0" class="empty-state">
        <p>暂无空间，创建第一个吧！</p>
        <router-link to="/space/create" class="btn btn-primary">创建空间</router-link>
      </div>

      <!-- 空间列表 -->
      <div v-else class="space-list">
        <div v-for="sp in spaces" :key="sp.id" class="space-card">
          <div class="card-left">
            <div class="space-info">
              <h2 class="space-name">
                <router-link :to="`/space/${sp.id}`">{{ sp.spaceName }}</router-link>
              </h2>
              <span class="badge" :class="'level-' + sp.spaceLevel">
                {{ spaceLevelText(sp.spaceLevel) }}
              </span>
              <span class="badge type">{{ spaceTypeText(sp.spaceType) }}</span>
              <span v-if="sp.userId === userStore.currentUser?.id" class="badge mine">我的</span>
            </div>

            <div class="space-meter">
              <div class="meter-item">
                <div class="meter-label">
                  <span>图片</span><span>{{ sp.totalCount }} / {{ sp.maxCount }}</span>
                </div>
                <div class="meter-bar">
                  <div class="meter-fill count-fill" :style="{ width: countPercent(sp) + '%' }"></div>
                </div>
              </div>
              <div class="meter-item">
                <div class="meter-label">
                  <span>容量</span><span>{{ formatSize(sp.totalSize) }} / {{ formatSize(sp.maxSize) }}</span>
                </div>
                <div class="meter-bar">
                  <div class="meter-fill size-fill" :style="{ width: sizePercent(sp) + '%' }"></div>
                </div>
              </div>
            </div>

            <p class="space-meta" v-if="sp.user">
              {{ sp.user.userName }} · {{ formatDate(sp.createTime) }}
            </p>
          </div>

          <div class="card-right">
            <router-link :to="`/space/${sp.id}`" class="btn btn-outline btn-sm">查看图片</router-link>
            <button
              v-if="sp.userId === userStore.currentUser?.id || userStore.isAdmin"
              class="btn btn-outline btn-sm"
              @click="openEdit(sp)"
            >编辑</button>
            <button
              v-if="sp.userId === userStore.currentUser?.id || userStore.isAdmin"
              class="btn btn-danger btn-sm"
              @click="handleDelete(sp)"
            >删除</button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="total > pageSize">
        <button :disabled="current <= 1" @click="goPage(current - 1)">上一页</button>
        <span>第 {{ current }} / {{ totalPages }} 页 (共 {{ total }} 条)</span>
        <button :disabled="current >= totalPages" @click="goPage(current + 1)">下一页</button>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="showEditModal" class="modal-overlay" @click.self="closeEditModal">
      <div class="modal-card">
        <h2>编辑空间</h2>
        <div class="field">
          <label>空间名称</label>
          <input v-model="editForm.spaceName" class="input" maxlength="30" />
        </div>
        <template v-if="userStore.isAdmin">
          <div class="field">
            <label>空间级别</label>
            <select v-model="editForm.spaceLevel" class="input">
              <option :value="0">普通版（100 张 / 100 MB）</option>
              <option :value="1">专业版（1,000 张 / 1 GB）</option>
              <option :value="2">旗舰版（10,000 张 / 10 GB）</option>
            </select>
          </div>
          <div class="field">
            <label>最大数量（可选覆盖）</label>
            <input v-model.number="editForm.maxCount" class="input" type="number" placeholder="留空使用级别默认" />
          </div>
          <div class="field">
            <label>最大容量（可选覆盖，字节）</label>
            <input v-model.number="editForm.maxSize" class="input" type="number" placeholder="留空使用级别默认" />
          </div>
        </template>
        <div v-if="editError" class="form-error">{{ editError }}</div>
        <div class="form-actions">
          <button class="btn btn-outline" @click="closeEditModal">取消</button>
          <button class="btn btn-primary" @click="handleEditSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { listSpaceVOByPage, editSpace, updateSpace, deleteSpace } from '@/api/space'
import { spaceLevelText, spaceTypeText, formatSize, formatDate } from '@/constants/space'

const userStore = useUserStore()

const spaces = ref([])
const loading = ref(false)
const current = ref(1)
const total = ref(0)
const pageSize = 12

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const showEditModal = ref(false)
const editError = ref('')
const editForm = ref({ id: null, spaceName: '', spaceLevel: 0, maxCount: null, maxSize: null })

onMounted(loadSpaces)

async function loadSpaces() {
  loading.value = true
  try {
    // 只查询当前用户自己的空间
    const res = await listSpaceVOByPage({
      current: current.value,
      pageSize,
      userId: userStore.currentUser?.id
    })
    spaces.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error('加载空间失败', e)
  } finally {
    loading.value = false
  }
}

function goPage(p) { current.value = p; loadSpaces() }

function countPercent(sp) {
  return sp.maxCount ? Math.min(100, Math.round((sp.totalCount / sp.maxCount) * 100)) : 0
}
function sizePercent(sp) {
  return sp.maxSize ? Math.min(100, Math.round((sp.totalSize / sp.maxSize) * 100)) : 0
}

// ===== 编辑 =====
function openEdit(sp) {
  editForm.value = {
    id: sp.id,
    spaceName: sp.spaceName,
    spaceLevel: sp.spaceLevel,
    maxCount: null,
    maxSize: null
  }
  editError.value = ''
  showEditModal.value = true
}

function closeEditModal() { showEditModal.value = false }

async function handleEditSave() {
  editError.value = ''
  try {
    const data = { id: editForm.value.id, spaceName: editForm.value.spaceName.trim() }
    if (userStore.isAdmin) {
      data.spaceLevel = editForm.value.spaceLevel
      if (editForm.value.maxCount) data.maxCount = editForm.value.maxCount
      if (editForm.value.maxSize) data.maxSize = editForm.value.maxSize
      await updateSpace(data)
    } else {
      await editSpace(data)
    }
    showEditModal.value = false
    loadSpaces()
  } catch (e) {
    editError.value = e.message || '保存失败'
  }
}

// ===== 删除 =====
async function handleDelete(sp) {
  if (!confirm(`确定删除空间"${sp.spaceName}"吗？空间内的图片不会被删除。`)) return
  try {
    await deleteSpace(sp.id)
    loadSpaces()
  } catch (e) {
    alert(e.message || '删除失败')
  }
}
</script>

<style scoped>
.space-page { padding: 3rem 0 5rem; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 2rem; flex-wrap: wrap; gap: 1rem; }
.page-header h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; }
.subtitle { color: var(--gray-600); font-size: 0.9375rem; margin-top: 0.25rem; }
.header-right { display: flex; gap: 0.5rem; }
.btn-sm { padding: 0.5rem 1.25rem; font-size: 0.75rem; }

.loading { text-align: center; padding: 4rem 0; color: var(--gray-400); }
.empty-state { text-align: center; padding: 5rem 0; color: var(--gray-400); }
.empty-state p { margin-bottom: 1rem; font-size: 1.125rem; }

/* 列表 */
.space-list { display: flex; flex-direction: column; gap: 1.25rem; }
.space-card {
  border: 2px solid var(--gray-200); padding: 1.5rem; background: var(--white);
  display: flex; justify-content: space-between; gap: 1rem;
  transition: border-color 0.2s;
}
.space-card:hover { border-color: var(--black); }
.card-left { flex: 1; display: flex; flex-direction: column; gap: 0.75rem; }
.card-right { display: flex; align-items: flex-start; gap: 0.5rem; flex-shrink: 0; }

.space-info { display: flex; align-items: center; gap: 0.625rem; flex-wrap: wrap; }
.space-name { font-size: 1.125rem; font-weight: 700; }
.space-name a { color: inherit; }
.space-name a:hover { color: var(--red); }
.badge { padding: 0.15rem 0.625rem; font-size: 0.6875rem; font-weight: 600; background: var(--black); color: var(--white); }
.badge.level-1 { background: var(--blue); }
.badge.level-2 { background: var(--red); }
.badge.mine { background: var(--yellow); color: var(--black); }

.space-meter { display: flex; gap: 1.5rem; max-width: 500px; }
.meter-item { flex: 1; display: flex; flex-direction: column; gap: 0.2rem; }
.meter-label { display: flex; justify-content: space-between; font-size: 0.75rem; font-weight: 500; }
.meter-bar { height: 5px; background: var(--gray-100); border-radius: 3px; overflow: hidden; }
.meter-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
.count-fill { background: var(--blue); }
.size-fill { background: var(--yellow); }
.space-meta { font-size: 0.75rem; color: var(--gray-400); }

/* 分页 */
.pagination { display: flex; align-items: center; justify-content: center; gap: 1.5rem; margin-top: 2rem; }
.pagination button { font-weight: 600; padding: 0.5rem 1rem; border: 2px solid var(--black); background: var(--white); cursor: pointer; }
.pagination button:disabled { opacity: 0.3; cursor: default; }
.pagination button:hover:not(:disabled) { background: var(--black); color: var(--white); }

/* 弹窗 */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 200; }
.modal-card { background: var(--white); border: 2px solid var(--black); padding: 2rem; width: 100%; max-width: 480px; display: flex; flex-direction: column; gap: 1rem; }
.modal-card h2 { font-size: 1.5rem; font-weight: 700; }
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field label { font-size: 0.8125rem; font-weight: 600; }
.form-error { padding: 0.5rem 0.75rem; background: #FFF0EF; color: var(--red); font-size: 0.8125rem; }
.form-actions { display: flex; gap: 0.75rem; justify-content: flex-end; margin-top: 0.25rem; }

@media (max-width: 767px) {
  .space-page { padding-block: 2rem 3rem; }
  .page-header h1 { font-size: 1.75rem; }
  .header-right { width: 100%; }
  .header-right .btn { flex: 1; }
  .space-card { flex-direction: column; padding: 1.25rem; }
  .card-right { width: 100%; flex-wrap: wrap; }
  .card-right .btn { flex: 1; }
  .space-meter { flex-direction: column; gap: 0.75rem; }
  .pagination { gap: 0.75rem; flex-wrap: wrap; }
  .modal-overlay { align-items: flex-end; }
  .modal-card { max-height: 90dvh; overflow-y: auto; padding: 1.5rem 1rem max(1rem, env(safe-area-inset-bottom)); border-inline: 0; border-bottom: 0; }
  .form-actions .btn { flex: 1; }
}
</style>
