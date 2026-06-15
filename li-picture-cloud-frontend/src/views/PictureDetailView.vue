<template>
  <div class="detail-page">
    <div class="container">
      <!-- 返回 -->
      <button class="back-btn" @click="$router.back()">&larr; 返回图库</button>

      <!-- 加载 -->
      <div v-if="loading" class="loading">加载中…</div>

      <!-- 详情 -->
      <template v-else-if="picture">
        <div class="detail-layout">
          <!-- 图片展示区 -->
          <div class="image-area">
            <img :src="picture.url" :alt="picture.name" class="main-image" />
          </div>

          <!-- 信息区 -->
          <div class="info-area">
            <h1 class="pic-name">{{ picture.name || '未命名' }}</h1>

            <!-- 上传者 -->
            <div class="author" v-if="picture.user">
              <span class="author-avatar">{{ picture.user.userName?.charAt(0) }}</span>
              <span>{{ picture.user.userName }}</span>
            </div>

            <!-- 简介 -->
            <p class="intro" v-if="picture.introduction">{{ picture.introduction }}</p>
            <p class="intro empty" v-else>暂无简介</p>

            <!-- 元数据 -->
            <dl class="meta-grid">
              <div>
                <dt>分类</dt>
                <dd>{{ picture.category || '-' }}</dd>
              </div>
              <div>
                <dt>格式</dt>
                <dd>{{ picture.picFormat?.toUpperCase() || '-' }}</dd>
              </div>
              <div>
                <dt>尺寸</dt>
                <dd>{{ picture.picWidth }} × {{ picture.picHeight }}</dd>
              </div>
              <div>
                <dt>文件大小</dt>
                <dd>{{ formatSize(picture.picSize) }}</dd>
              </div>
              <div>
                <dt>上传时间</dt>
                <dd>{{ formatDate(picture.createTime) }}</dd>
              </div>
              <div v-if="picture.editTime">
                <dt>编辑时间</dt>
                <dd>{{ formatDate(picture.editTime) }}</dd>
              </div>
            </dl>

            <!-- 标签 -->
            <div class="tags" v-if="picture.tags && picture.tags.length">
              <span v-for="tag in picture.tags" :key="tag" class="tag-badge">{{ tag }}</span>
            </div>

            <!-- 操作按钮 -->
            <div class="actions" v-if="canEdit">
              <button class="btn btn-outline" @click="openEditModal">编辑信息</button>
              <button class="btn btn-danger" @click="handleDelete">删除图片</button>
            </div>
          </div>
        </div>
      </template>

      <!-- 不存在 -->
      <div v-else class="empty-state">图片不存在或已被删除</div>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
      <div class="modal">
        <h2>编辑图片信息</h2>
        <form @submit.prevent="handleEdit" class="modal-form">
          <div class="field">
            <span>名称</span>
            <input v-model="editForm.name" class="input" placeholder="图片名称" />
          </div>
          <div class="field">
            <span>简介</span>
            <textarea v-model="editForm.introduction" class="input textarea" placeholder="图片简介（≤800字）" rows="3"></textarea>
          </div>
          <div class="field">
            <span>分类</span>
            <select v-model="editForm.category" class="input">
              <option value="">无分类</option>
              <option v-for="c in categoryList" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="field">
            <span>标签（逗号分隔）</span>
            <input v-model="editForm.tagsStr" class="input" placeholder="如：风景, 建筑, 黑白" />
          </div>
          <div v-if="modalError" class="form-error">{{ modalError }}</div>
          <div class="modal-actions">
            <button type="button" class="btn btn-outline" @click="showEditModal = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="saving">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getPictureVOById, editPicture, deletePicture, getPictureTagCategory } from '@/api/picture'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const picture = ref(null)
const loading = ref(false)
const categoryList = ref([])

const canEdit = computed(() => {
  if (!userStore.isLoggedIn || !picture.value) return false
  return userStore.isAdmin || picture.value.userId === userStore.currentUser?.id
})

// 编辑弹窗
const showEditModal = ref(false)
const saving = ref(false)
const modalError = ref('')
const editForm = reactive({ name: '', introduction: '', category: '', tagsStr: '' })

onMounted(async () => {
  try {
    const meta = await getPictureTagCategory()
    categoryList.value = meta.categoryList || []
  } catch { /* ignore */ }

  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    picture.value = await getPictureVOById(id)
  } catch {
    picture.value = null
  } finally {
    loading.value = false
  }
})

function openEditModal() {
  editForm.name = picture.value.name || ''
  editForm.introduction = picture.value.introduction || ''
  editForm.category = picture.value.category || ''
  editForm.tagsStr = (picture.value.tags || []).join(', ')
  showEditModal.value = true
}

async function handleEdit() {
  modalError.value = ''
  saving.value = true
  try {
    const tags = editForm.tagsStr
      .split(/[,，]/)
      .map(t => t.trim())
      .filter(Boolean)
    await editPicture({
      id: picture.value.id,
      name: editForm.name,
      introduction: editForm.introduction,
      category: editForm.category,
      tags
    })
    showEditModal.value = false
    // 刷新数据
    picture.value = await getPictureVOById(picture.value.id)
  } catch (e) {
    modalError.value = e.message || '编辑失败'
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  if (!confirm('确定要删除这张图片吗？此操作不可恢复。')) return
  try {
    await deletePicture(picture.value.id)
    router.replace('/gallery')
  } catch (e) {
    alert(e.message || '删除失败')
  }
}

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '-'
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
.detail-page { padding: 2rem 0 5rem; }

/* 返回按钮 */
.back-btn {
  display: inline-flex; align-items: center; gap: 0.5rem;
  font-size: 0.875rem; font-weight: 500; margin-bottom: 2rem;
}
.back-btn:hover { color: var(--red); }

/* 布局 */
.detail-layout {
  display: grid; grid-template-columns: 1fr 1fr; gap: 3rem;
  align-items: start;
}

/* 图片 */
.image-area {
  border: 2px solid var(--black);
  overflow: hidden;
}
.main-image { width: 100%; display: block; }

/* 信息 */
.pic-name { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; margin-bottom: 1rem; }
.author { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1.5rem; font-size: 0.9375rem; }
.author-avatar {
  width: 2rem; height: 2rem; border-radius: 50%;
  background: var(--black); color: var(--white);
  display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; font-weight: 600;
}
.intro { font-size: 1rem; line-height: 1.7; margin-bottom: 1.5rem; color: var(--gray-900); }
.intro.empty { color: var(--gray-400); }

/* 元数据网格 */
.meta-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;
  margin-bottom: 1.5rem;
  padding: 1.25rem; background: var(--gray-100);
}
.meta-grid dt { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--gray-400); margin-bottom: 0.25rem; }
.meta-grid dd { font-size: 0.9375rem; font-weight: 500; }

/* 标签 */
.tags { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 2rem; }
.tag-badge {
  padding: 0.375rem 1rem; font-size: 0.8125rem; font-weight: 500;
  background: var(--black); color: var(--white);
}

/* 操作 */
.actions { display: flex; gap: 0.75rem; }

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
.form-error {
  padding: 0.5rem 0.75rem; background: #FFF0EF; color: var(--red);
  font-size: 0.8125rem; font-weight: 500;
}
.textarea { resize: vertical; min-height: 80px; font-family: inherit; }

/* 状态 */
.loading { text-align: center; padding: 4rem 0; color: var(--gray-400); }
.empty-state { text-align: center; padding: 5rem 0; color: var(--gray-400); font-size: 1.125rem; }

@media (max-width: 768px) {
  .detail-layout { grid-template-columns: 1fr; }
}
</style>
