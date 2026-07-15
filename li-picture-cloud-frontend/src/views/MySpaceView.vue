<template>
  <div class="my-space-page">
    <div class="container">
      <!-- 有空间时：空间概览 -->
      <template v-if="mySpace">
        <div class="page-header">
          <div>
            <h1>{{ mySpace.spaceName }}</h1>
            <p class="subtitle">
              <span class="badge" :class="'level-' + mySpace.spaceLevel">{{ spaceLevelText(mySpace.spaceLevel) }}</span>
              创建于 {{ formatDate(mySpace.createTime) }}
            </p>
          </div>
          <div class="header-right">
            <button class="btn btn-primary" @click="$router.push(`/space/${mySpace.id}`)">
              进入空间
            </button>
          </div>
        </div>

        <!-- 用量概览 -->
        <div class="stats-grid">
          <div class="stat-card">
            <span class="stat-num">{{ mySpace.totalCount }}</span>
            <span class="stat-label">图片数量</span>
            <div class="stat-bar">
              <div class="stat-fill count-fill" :style="{ width: countPercent + '%' }"></div>
            </div>
            <span class="stat-cap">上限 {{ mySpace.maxCount }} 张</span>
          </div>
          <div class="stat-card">
            <span class="stat-num">{{ formatSize(mySpace.totalSize) }}</span>
            <span class="stat-label">已用容量</span>
            <div class="stat-bar">
              <div class="stat-fill size-fill" :style="{ width: sizePercent + '%' }"></div>
            </div>
            <span class="stat-cap">上限 {{ formatSize(mySpace.maxSize) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-num">{{ usageRate }}%</span>
            <span class="stat-label">综合使用率</span>
            <div class="stat-bar">
              <div class="stat-fill all-fill" :style="{ width: usageRate + '%' }"></div>
            </div>
            <span class="stat-cap">
              {{ usageRate >= 80 ? '⚠️ 接近上限' : usageRate >= 50 ? '正常使用中' : '空间充裕' }}
            </span>
          </div>
        </div>

        <!-- 快捷操作 -->
        <div class="quick-actions">
          <button class="action-card" @click="$router.push(`/upload?spaceId=${mySpace.id}`)">
            <span class="action-icon">📤</span>
            <span class="action-title">上传图片</span>
            <span class="action-desc">上传图片到此空间</span>
          </button>
          <button class="action-card" @click="$router.push(`/space/${mySpace.id}`)">
            <span class="action-icon">🖼️</span>
            <span class="action-title">浏览图片</span>
            <span class="action-desc">查看空间内所有图片</span>
          </button>
          <button class="action-card" @click="showEditModal = true">
            <span class="action-icon">✏️</span>
            <span class="action-title">修改名称</span>
            <span class="action-desc">给空间重新命名</span>
          </button>
        </div>
      </template>

      <!-- 无空间时：引导创建 -->
      <div v-else-if="!loading" class="empty-hero">
        <div class="empty-icon">📦</div>
        <h2>还没有私有空间</h2>
        <p>创建空间后，你可以将图片上传到专属的私有存储区域，享有独立的容量和数量配额。</p>
        <div class="empty-actions">
          <router-link to="/space/create" class="btn btn-primary">创建我的空间</router-link>
          <router-link to="/spaces" class="btn btn-outline">查看所有空间</router-link>
        </div>
      </div>

      <div v-else class="loading">加载中…</div>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
      <div class="modal-card">
        <h2>修改空间名称</h2>
        <div class="field">
          <label>空间名称</label>
          <input v-model="editName" class="input" maxlength="30" placeholder="输入新名称" />
        </div>
        <div v-if="editError" class="form-error">{{ editError }}</div>
        <div class="form-actions">
          <button class="btn btn-outline" @click="showEditModal = false">取消</button>
          <button class="btn btn-primary" :disabled="!editName.trim()" @click="handleEdit">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listSpaceVOByPage, editSpace } from '@/api/space'
import { spaceLevelText, formatSize, formatDate } from '@/constants/space'

const router = useRouter()
const userStore = useUserStore()

if (!userStore.isLoggedIn) router.replace('/login')

const mySpace = ref(null)
const loading = ref(true)
const showEditModal = ref(false)
const editName = ref('')
const editError = ref('')

const countPercent = computed(() => {
  if (!mySpace.value) return 0
  return mySpace.value.maxCount ? Math.min(100, Math.round((mySpace.value.totalCount / mySpace.value.maxCount) * 100)) : 0
})

const sizePercent = computed(() => {
  if (!mySpace.value) return 0
  return mySpace.value.maxSize ? Math.min(100, Math.round((mySpace.value.totalSize / mySpace.value.maxSize) * 100)) : 0
})

const usageRate = computed(() => Math.max(countPercent.value, sizePercent.value))

onMounted(async () => {
  try {
    // 按 userId 查询当前用户的空间（每个用户仅一个空间）
    const res = await listSpaceVOByPage({
      current: 1,
      pageSize: 20,
      userId: userStore.currentUser?.id
    })
    const spaces = res.records || []
    mySpace.value = spaces.length > 0 ? spaces[0] : null
  } catch (e) {
    console.error('加载空间失败', e)
  } finally {
    loading.value = false
  }
})

async function handleEdit() {
  editError.value = ''
  try {
    await editSpace({ id: mySpace.value.id, spaceName: editName.value.trim() })
    mySpace.value.spaceName = editName.value.trim()
    showEditModal.value = false
  } catch (e) {
    editError.value = e.message || '保存失败'
  }
}
</script>

<style scoped>
.my-space-page { padding: 3rem 0 5rem; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 2rem; flex-wrap: wrap; gap: 1rem; }
.page-header h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; }
.subtitle { color: var(--gray-600); font-size: 0.9375rem; margin-top: 0.25rem; display: flex; align-items: center; gap: 0.5rem; }
.badge { padding: 0.15rem 0.625rem; font-size: 0.75rem; font-weight: 600; background: var(--black); color: var(--white); }
.badge.level-1 { background: var(--blue); }
.badge.level-2 { background: var(--red); }

/* 用量卡片 */
.stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1.25rem; margin-bottom: 2rem; }
.stat-card {
  border: 2px solid var(--gray-200); padding: 1.5rem; text-align: center; background: var(--white);
}
.stat-num { font-size: 1.75rem; font-weight: 700; display: block; margin-bottom: 0.25rem; }
.stat-label { font-size: 0.8125rem; color: var(--gray-600); display: block; margin-bottom: 0.75rem; }
.stat-bar { height: 6px; background: var(--gray-100); margin-bottom: 0.375rem; border-radius: 3px; overflow: hidden; }
.stat-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
.count-fill { background: var(--blue); }
.size-fill { background: var(--yellow); }
.all-fill { background: var(--red); }
.stat-cap { font-size: 0.75rem; color: var(--gray-400); }

/* 快捷操作 */
.quick-actions { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
.action-card {
  border: 2px solid var(--gray-200); padding: 1.5rem; text-align: center;
  background: var(--white); cursor: pointer; transition: border-color 0.2s, transform 0.2s;
  display: flex; flex-direction: column; align-items: center; gap: 0.5rem;
}
.action-card:hover { border-color: var(--black); transform: translateY(-2px); }
.action-icon { font-size: 2rem; }
.action-title { font-size: 1rem; font-weight: 600; }
.action-desc { font-size: 0.8125rem; color: var(--gray-400); }

/* 空状态 */
.empty-hero { text-align: center; padding: 6rem 0; }
.empty-icon { font-size: 4rem; margin-bottom: 1rem; }
.empty-hero h2 { font-size: 1.75rem; font-weight: 700; margin-bottom: 0.75rem; }
.empty-hero p { color: var(--gray-600); max-width: 480px; margin: 0 auto 2rem; font-size: 1rem; }
.empty-actions { display: flex; gap: 0.75rem; justify-content: center; }
.loading { text-align: center; padding: 4rem 0; color: var(--gray-400); }

/* 弹窗 */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 200; }
.modal-card { background: var(--white); border: 2px solid var(--black); padding: 2rem; width: 100%; max-width: 440px; display: flex; flex-direction: column; gap: 1rem; }
.modal-card h2 { font-size: 1.5rem; font-weight: 700; }
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field label { font-size: 0.8125rem; font-weight: 600; }
.form-error { padding: 0.75rem 1rem; background: #FFF0EF; color: var(--red); font-size: 0.875rem; font-weight: 500; }
.form-actions { display: flex; gap: 0.75rem; justify-content: flex-end; }
</style>
