<template>
  <div class="detail-page">
    <div class="container">
      <button class="back-btn" @click="$router.back()">&larr; 返回图库</button>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else-if="picture">
        <div class="detail-layout">
          <!-- 图片展示区 -->
          <div class="image-area" @click="showFullscreen = true" title="点击全屏查看">
            <img :src="picture.url" :alt="picture.name" class="main-image" />
            <span class="zoom-hint">🔍 点击全屏</span>
          </div>

          <!-- 信息区 -->
          <div class="info-area">
            <h1 class="pic-name">{{ picture.name || '未命名' }}</h1>

            <!-- 上传者 -->
            <div class="author" v-if="picture.user">
              <span class="author-avatar">{{ picture.user.userName?.charAt(0) }}</span>
              <span>{{ picture.user.userName }}</span>
            </div>

            <!-- 审核状态 -->
            <div class="review-badge" :class="reviewClass" v-if="picture.reviewStatus !== undefined">
              {{ reviewText }}
              <small v-if="picture.reviewMessage"> — {{ picture.reviewMessage }}</small>
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
              <div v-if="picture.reviewTime">
                <dt>审核时间</dt>
                <dd>{{ formatDate(picture.reviewTime) }}</dd>
              </div>
            </dl>

            <!-- 标签 -->
            <div class="tags" v-if="picture.tags && picture.tags.length">
              <span v-for="tag in picture.tags" :key="tag" class="tag-badge">{{ tag }}</span>
            </div>

            <!-- 管理员审核操作 -->
            <div class="review-actions" v-if="userStore.isAdmin && picture.reviewStatus === 0">
              <div class="field">
                <span>审核信息（可选）</span>
                <input v-model="reviewMessage" class="input" placeholder="通过或拒绝的理由" />
              </div>
              <div class="review-btns">
                <button class="btn btn-danger" @click="handleReview(2)" :disabled="reviewing">
                  {{ reviewing ? '处理中…' : '拒绝' }}
                </button>
                <button class="btn btn-primary" @click="handleReview(1)" :disabled="reviewing">
                  {{ reviewing ? '处理中…' : '通过' }}
                </button>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="actions">
              <button class="btn btn-outline" @click="showShare = true">分享</button>
              <button v-if="canEdit" class="btn btn-outline" @click="openImageEditor">
                {{ canCollaborate ? '协同编辑' : '编辑图片' }}
              </button>
              <button class="btn btn-outline" @click="handleDownload">下载图片</button>
              <template v-if="canEdit">
                <button class="btn btn-outline" @click="openEditModal">编辑信息</button>
                <button class="btn btn-danger" @click="handleDelete">删除图片</button>
              </template>
            </div>
          </div>
        </div>
      </template>

      <div v-else class="empty-state">图片不存在或已被删除</div>
    </div>

    <!-- 分享弹窗 -->
    <ShareModal
      :visible="showShare"
      :picture-id="picture?.id"
      :title="picture?.name"
      :image-url="picture?.url"
      :thumbnail-url="picture?.thumbnailUrl"
      @close="showShare = false"
    />

    <!-- 图片编辑弹窗（通过后端代理加载 COS 图片，解决跨域） -->
    <ImageEditModal
      :visible="showImageEditor"
      :image-src="imageEditorSrc"
      :picture-id="picture?.id"
      :collaborative="canCollaborate"
      @close="showImageEditor = false"
      @save="onImageEditSave"
    />

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

    <!-- 全屏查看 -->
    <Teleport to="body">
      <div
        v-if="showFullscreen"
        class="fullscreen-overlay"
        @click="showFullscreen = false"
        @keydown.esc="showFullscreen = false"
      >
        <div class="fullscreen-toolbar">
          <span class="fs-name">{{ picture?.name || '未命名' }}</span>
          <button class="fs-close" @click="showFullscreen = false" title="退出全屏 (Esc)">✕</button>
        </div>
        <img
          :src="picture?.url"
          :alt="picture?.name"
          class="fullscreen-img"
          @click.stop
        />
        <div class="fs-hint">点击背景或按 Esc 退出</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getPictureVOById, editPicture, deletePicture, reviewPicture, getPictureTagCategory, uploadPicture } from '@/api/picture'
import { getMyPicturePermissions } from '@/api/spaceUser'
import ShareModal from '@/components/ShareModal.vue'
import ImageEditModal from '@/components/ImageEditModal.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const picture = ref(null)
const loading = ref(false)
const categoryList = ref([])
const reviewMessage = ref('')
const reviewing = ref(false)
const showFullscreen = ref(false)
const showShare = ref(false)
const showImageEditor = ref(false)
const permissions = ref([])
// 通过后端代理加载 COS 图片，避免 Canvas 跨域 taint
const imageEditorSrc = computed(() => {
  if (!picture.value?.url) return ''
  return `/api/picture/image-proxy?url=${encodeURIComponent(picture.value.url)}`
})

// ESC 键退出全屏
function onKeydown(e) {
  if (e.key === 'Escape') showFullscreen.value = false
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

const canEdit = computed(() => {
  if (!userStore.isLoggedIn || !picture.value) return false
  return userStore.isAdmin || picture.value.userId === userStore.currentUser?.id || permissions.value.includes('picture:edit')
})

const canCollaborate = computed(() => permissions.value.includes('collaboration:edit'))

const reviewClass = computed(() => {
  const s = picture.value?.reviewStatus
  if (s === 1) return 'review-pass'
  if (s === 2) return 'review-reject'
  return 'review-pending'
})

const reviewText = computed(() => {
  const s = picture.value?.reviewStatus
  if (s === 1) return '✅ 已通过'
  if (s === 2) return '❌ 已拒绝'
  return '⏳ 待审核'
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
    if (userStore.isLoggedIn && picture.value?.spaceId) {
      try {
        permissions.value = await getMyPicturePermissions(picture.value.id)
      } catch {
        permissions.value = []
      }
    }
  } catch {
    picture.value = null
  } finally {
    loading.value = false
  }
})

async function handleReview(status) {
  reviewing.value = true
  try {
    await reviewPicture({
      id: picture.value.id,
      reviewStatus: status,
      reviewMessage: reviewMessage.value || (status === 1 ? '通过' : '拒绝')
    })
    // 刷新
    picture.value = await getPictureVOById(picture.value.id)
    reviewMessage.value = ''
  } catch (e) {
    alert(e.message || '审核操作失败')
  } finally {
    reviewing.value = false
  }
}

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
    const tags = editForm.tagsStr.split(/[,，]/).map(t => t.trim()).filter(Boolean)
    await editPicture({
      id: picture.value.id,
      name: editForm.name,
      introduction: editForm.introduction,
      category: editForm.category,
      tags
    })
    showEditModal.value = false
    picture.value = await getPictureVOById(picture.value.id)
  } catch (e) {
    modalError.value = e.message || '编辑失败'
  } finally {
    saving.value = false
  }
}

function openImageEditor() {
  showImageEditor.value = true
}

async function onImageEditSave(blob) {
  // 用编辑后的图片替换当前图片
  const fd = new FormData()
  const ext = picture.value?.picFormat || 'png'
  fd.append('file', blob, `edited.${ext}`)
  fd.append('id', picture.value.id)  // 更新模式：传入已有图片 ID
  try {
    await uploadPicture(fd)
    showImageEditor.value = false
    picture.value = await getPictureVOById(picture.value.id)
  } catch (e) {
    alert(e.message || '更新图片失败')
  }
}

function handleDownload() {
  if (!picture.value?.id) return
  const a = document.createElement('a')
  a.href = `/api/picture/download/${picture.value.id}`
  a.click()
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

.back-btn {
  display: inline-flex; align-items: center; gap: 0.5rem;
  font-size: 0.875rem; font-weight: 500; margin-bottom: 2rem;
}
.back-btn:hover { color: var(--red); }

.detail-layout {
  display: grid; grid-template-columns: 1fr 1fr; gap: 3rem;
  align-items: start;
}

.image-area {
  border: 2px solid var(--black);
  overflow: hidden;
}
.main-image { width: 100%; display: block; }

.pic-name { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; margin-bottom: 1rem; }
.author { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem; font-size: 0.9375rem; }
.author-avatar {
  width: 2rem; height: 2rem; border-radius: 50%;
  background: var(--black); color: var(--white);
  display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; font-weight: 600;
}

/* 审核状态 */
.review-badge {
  display: inline-block; padding: 0.375rem 1rem;
  font-size: 0.875rem; font-weight: 600; margin-bottom: 1rem;
}
.review-pending { background: var(--yellow); color: var(--black); }
.review-pass { background: #EFF9F0; color: #1A7A2E; border: 1px solid #1A7A2E; }
.review-reject { background: #FFF0EF; color: var(--red); border: 1px solid var(--red); }

.intro { font-size: 1rem; line-height: 1.7; margin-bottom: 1.5rem; color: var(--gray-900); }
.intro.empty { color: var(--gray-400); }

.meta-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;
  margin-bottom: 1.5rem; padding: 1.25rem; background: var(--gray-100);
}
.meta-grid dt { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--gray-400); margin-bottom: 0.25rem; }
.meta-grid dd { font-size: 0.9375rem; font-weight: 500; }

.tags { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 1.5rem; }
.tag-badge { padding: 0.375rem 1rem; font-size: 0.8125rem; font-weight: 500; background: var(--black); color: var(--white); }

/* 审核操作 */
.review-actions {
  padding: 1.25rem; background: var(--gray-100); margin-bottom: 1.5rem;
}
.review-btns { display: flex; gap: 0.75rem; margin-top: 0.75rem; }

.actions { display: flex; gap: 0.75rem; }

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
.textarea { resize: vertical; min-height: 80px; font-family: inherit; }

.loading { text-align: center; padding: 4rem 0; color: var(--gray-400); }
.empty-state { text-align: center; padding: 5rem 0; color: var(--gray-400); font-size: 1.125rem; }

/* 图片区全屏提示 */
.image-area { position: relative; cursor: zoom-in; }
.zoom-hint {
  position: absolute; top: 0.75rem; right: 0.75rem;
  padding: 0.25rem 0.75rem; font-size: 0.75rem; font-weight: 500;
  background: rgba(0,0,0,0.65); color: var(--white);
  opacity: 0; transition: opacity 0.2s;
}
.image-area:hover .zoom-hint { opacity: 1; }

/* 全屏查看 */
.fullscreen-overlay {
  position: fixed; inset: 0; z-index: 9999;
  background: rgba(0, 0, 0, 0.92);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.fullscreen-toolbar {
  position: absolute; top: 0; left: 0; right: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 1rem 2rem; z-index: 1;
}
.fs-name { color: rgba(255,255,255,0.7); font-size: 1rem; font-weight: 500; }
.fs-close {
  width: 48px; height: 48px; display: flex; align-items: center; justify-content: center;
  font-size: 1.5rem; color: var(--white); background: rgba(255,255,255,0.1);
  border: none; cursor: pointer; transition: background 0.2s;
}
.fs-close:hover { background: var(--red); }
.fullscreen-img {
  max-width: 95vw; max-height: 88vh;
  object-fit: contain; user-select: none;
}
.fs-hint {
  position: absolute; bottom: 1.5rem;
  color: rgba(255,255,255,0.35); font-size: 0.8125rem;
}

@media (max-width: 768px) {
  .detail-layout { grid-template-columns: 1fr; }
}
</style>
