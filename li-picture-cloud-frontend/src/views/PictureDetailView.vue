<template>
  <div class="detail-page">
    <div class="container">
      <button class="back-btn" @click="$router.back()">&larr; 返回图库</button>

      <div v-if="loading" class="loading">加载中…</div>

      <template v-else-if="picture">
        <div class="detail-layout">
          <!-- 图片展示区 -->
          <div class="image-area" @click="showFullscreen = true" title="点击全屏查看">
            <img :src="picture.url" :alt="picture.name" class="main-image"
                 @dragstart="interaction.startDrag($event, picture)" @dragend="interaction.cancelDrag" />
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
              <button v-if="canOfferToCompanion" type="button" class="btn btn-outline"
                      @click="interaction.open(picture.id)">选给绫页</button>
              <button class="btn btn-outline" @click="showShare = true">分享</button>
              <button v-if="canEdit || collaborationModeValue === 'view'" class="btn btn-outline" @click="openImageEditor">
                {{ collaborationButtonText }}
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

      <div v-else class="empty-state">{{ detailError || '图片不存在或已被删除' }}</div>
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
      v-if="showImageEditor"
      :visible="showImageEditor"
      :image-src="imageEditorSrc"
      :picture-id="picture?.id"
      :collaborative="Boolean(collaborationModeValue)"
      :read-only="collaborationModeValue === 'view'"
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
              <button class="fs-close" aria-label="退出全屏" @click="showFullscreen = false" title="退出全屏 (Esc)">✕</button>
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
import { ref, reactive, computed, inject, watch, onMounted, onBeforeUnmount, onUnmounted } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getPictureVOById, editPicture, deletePicture, reviewPicture, getPictureTagCategory, uploadPicture } from '@/api/picture'
import { getMyPicturePermissions } from '@/api/spaceUser'
import ShareModal from '@/components/ShareModal.vue'
import ImageEditModal from '@/components/ImageEditModal.vue'
import { getSpaceVOById } from '@/api/space'
import { SPACE_TYPE } from '@/constants/space'
import { collaborationMode } from '@/utils/spaceAccess'
import { COMPANION_UI_ENABLED } from '@/config/features'
import { useCompanionInteractionStore } from '@/stores/companionInteraction'
import { mayOfferCompanionPicture, normalizePictureId } from '@/presentation/companionInteraction'
import { SITE_PRESENCE_KEY } from '@/presentation/sitePresence'
import { onSiteNavigationIntent } from '@/presentation/sitePresenceNavigation'
import { formatDate } from '@/utils/date'
const companionEnabled = COMPANION_UI_ENABLED
const interaction = useCompanionInteractionStore()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const presence = inject(SITE_PRESENCE_KEY, null)

const picture = ref(null)
const loading = ref(false)
const categoryList = ref([])
const reviewMessage = ref('')
const reviewing = ref(false)
const showFullscreen = ref(false)
const showShare = ref(false)
const showImageEditor = ref(false)
const permissions = ref([])
const pictureSpaceType = ref(null)
const detailError = ref('')
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

// Same scope as the companion picker: only the viewer's own private-space pictures.
const canOfferToCompanion = computed(() => companionEnabled && userStore.isLoggedIn &&
  pictureSpaceType.value === SPACE_TYPE.PRIVATE && mayOfferCompanionPicture(picture.value, userStore.currentUser?.id))

const collaborationModeValue = computed(() => collaborationMode(
  permissions.value,
  pictureSpaceType.value === SPACE_TYPE.TEAM
))
const collaborationButtonText = computed(() => {
  if (collaborationModeValue.value === 'edit') return '协同编辑'
  if (collaborationModeValue.value === 'view') return '观看协同'
  return '编辑图片'
})

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

// A request belongs to one confirmed route/account and one navigation intent.
// Invalidating the UI never claims to cancel a write already sent to the server.
let active = true
let generation = 0
let pendingNavigation = null
let openedContext = null
let imageEditorContext = null

function resetDetail() {
  generation += 1
  picture.value = null
  permissions.value = []
  pictureSpaceType.value = null
  detailError.value = ''
  loading.value = false
  reviewing.value = false
  saving.value = false
  reviewMessage.value = ''
  modalError.value = ''
  showFullscreen.value = false
  showShare.value = false
  showImageEditor.value = false
  imageEditorContext = null
  showEditModal.value = false
  Object.assign(editForm, { name: '', introduction: '', category: '', tagsStr: '' })
  categoryList.value = []
}

function captureContext() {
  return {
    generation, route: router.currentRoute.value,
    actor: userStore.currentUser?.id ?? null,
    id: normalizePictureId(route.params.id)
  }
}

function isCurrent(context) {
  return active && !pendingNavigation && context.generation === generation &&
    context.route === router.currentRoute.value &&
    context.actor === (userStore.currentUser?.id ?? null) &&
    context.id === normalizePictureId(route.params.id)
}

function operationContext() {
  const context = captureContext()
  return context.id && isCurrent(context) &&
    normalizePictureId(picture.value?.id) === context.id ? context : null
}

function matchingPicture(result, context) {
  if (!result || normalizePictureId(result.id) !== context.id) throw new Error('图片详情与当前页面不匹配')
  return result
}

async function loadCategories(context) {
  try {
    const meta = await getPictureTagCategory()
    if (isCurrent(context)) categoryList.value = meta.categoryList || []
  } catch { /* Optional metadata must not delay or fail the authorized detail read. */ }
}

async function loadAccess(context, result) {
  if (!isCurrent(context) || !userStore.isLoggedIn || !result.spaceId) return
  try {
    const [permissionResult, spaceResult] = await Promise.all([
      getMyPicturePermissions(result.id),
      getSpaceVOById(result.spaceId)
    ])
    if (!isCurrent(context)) return
    permissions.value = permissionResult
    pictureSpaceType.value = spaceResult?.spaceType ?? null
  } catch {
    if (!isCurrent(context)) return
    permissions.value = []
    pictureSpaceType.value = null
  }
}

async function loadDetail() {
  resetDetail()
  if (!active || pendingNavigation || route.name !== 'picture-detail') return
  const context = captureContext()
  if (!openedContext || openedContext.id !== context.id || openedContext.actor !== context.actor) {
    openedContext = { id: context.id, actor: context.actor, emitted: false }
  }
  if (!context.id) return
  loading.value = true
  void loadCategories(context)
  try {
    const result = await getPictureVOById(context.id)
    if (!isCurrent(context)) return
    picture.value = matchingPicture(result, context)
    // Only the current authorized read signals an opening. Never send picture
    // data, IDs, page text or a second read to the presence coordinator.
    if (companionEnabled && !openedContext.emitted) {
      openedContext.emitted = true
      presence?.pictureOpened()
    }
    void loadAccess(context, result)
  } catch (e) {
    if (!isCurrent(context)) return
    picture.value = null
    detailError.value = e.message || '加载图片详情失败'
  } finally {
    if (isCurrent(context)) loading.value = false
  }
}

function navigationIntent(to, from) {
  if (!active || pendingNavigation?.to === to) return
  pendingNavigation = { to, from }
  resetDetail()
}
// Leave guards run first; the router broadcasts its first global guard before
// awaiting authentication, including same-component navigations later canceled.
onBeforeRouteLeave(navigationIntent)
onBeforeRouteUpdate(navigationIntent)
const removeNavigationIntent = onSiteNavigationIntent(to => navigationIntent(to, router.currentRoute.value))
function finishNavigation(to, from, failure) {
  if (!active || pendingNavigation?.to !== to) return
  pendingNavigation = null
  if (failure && router.currentRoute.value === from) void loadDetail()
}
const removeAfterEach = router.afterEach(finishNavigation)
const removeOnError = router.onError((_error, to, from) => finishNavigation(to, from, true))
watch(() => [router.currentRoute.value, userStore.currentUser?.id ?? null], () => {
  if (pendingNavigation && router.currentRoute.value === pendingNavigation.from) {
    resetDetail()
    return
  }
  pendingNavigation = null
  void loadDetail()
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => {
  active = false
  generation += 1
  removeNavigationIntent()
  removeAfterEach()
  removeOnError()
})

async function refreshPicture(context) {
  if (!isCurrent(context)) return
  const result = await getPictureVOById(context.id)
  if (isCurrent(context)) picture.value = matchingPicture(result, context)
}

async function handleReview(status) {
  const context = operationContext()
  if (!context || !userStore.isAdmin || reviewing.value) return
  reviewing.value = true
  try {
    await reviewPicture({
      id: context.id,
      reviewStatus: status,
      reviewMessage: reviewMessage.value || (status === 1 ? '通过' : '拒绝')
    })
    if (!isCurrent(context)) return
    await refreshPicture(context)
    if (isCurrent(context)) reviewMessage.value = ''
  } catch (e) {
    if (isCurrent(context)) alert(e.message || '审核操作失败')
  } finally {
    if (isCurrent(context)) reviewing.value = false
  }
}

function openEditModal() {
  if (!operationContext() || !canEdit.value) return
  editForm.name = picture.value.name || ''
  editForm.introduction = picture.value.introduction || ''
  editForm.category = picture.value.category || ''
  editForm.tagsStr = (picture.value.tags || []).join(', ')
  showEditModal.value = true
}

async function handleEdit() {
  const context = operationContext()
  if (!context || !canEdit.value || saving.value) return
  modalError.value = ''
  saving.value = true
  try {
    const tags = editForm.tagsStr.split(/[,，]/).map(t => t.trim()).filter(Boolean)
    await editPicture({
      id: context.id,
      name: editForm.name,
      introduction: editForm.introduction,
      category: editForm.category,
      tags
    })
    if (!isCurrent(context)) return
    showEditModal.value = false
    await refreshPicture(context)
  } catch (e) {
    if (isCurrent(context)) modalError.value = e.message || '编辑失败'
  } finally {
    if (isCurrent(context)) saving.value = false
  }
}

function openImageEditor() {
  const context = operationContext()
  if (!context) return
  imageEditorContext = context
  showImageEditor.value = true
}

async function onImageEditSave(blob) {
  const context = imageEditorContext
  if (!showImageEditor.value || !context || !isCurrent(context) || !canEdit.value) return
  // 用编辑后的图片替换当前图片
  const fd = new FormData()
  const ext = picture.value?.picFormat || 'png'
  fd.append('file', blob, `edited.${ext}`)
  fd.append('id', context.id)  // 更新模式：传入已有图片 ID
  try {
    await uploadPicture(fd)
    if (!isCurrent(context) || context !== imageEditorContext || !showImageEditor.value) return
    showImageEditor.value = false
    await refreshPicture(context)
  } catch (e) {
    if (isCurrent(context) && context === imageEditorContext && showImageEditor.value) alert(e.message || '更新图片失败')
  }
}

function handleDownload() {
  const context = operationContext()
  if (!context) return
  const a = document.createElement('a')
  a.href = `/api/picture/download/${context.id}`
  a.click()
}

async function handleDelete() {
  const context = operationContext()
  if (!context || !canEdit.value || !confirm('确定要删除这张图片吗？此操作不可恢复。')) return
  try {
    await deletePicture(context.id)
    if (isCurrent(context)) await router.replace('/gallery')
  } catch (e) {
    if (isCurrent(context)) alert(e.message || '删除失败')
  }
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

@media (max-width: 767px) {
  .detail-page { padding-block: 1.5rem 3rem; }
  .back-btn { margin-bottom: 1.25rem; }
  .detail-layout { grid-template-columns: 1fr; gap: 1.75rem; }
  .pic-name { font-size: 1.75rem; overflow-wrap: anywhere; }
  .meta-grid { grid-template-columns: 1fr; padding: 1rem; }
  .actions, .review-btns { flex-wrap: wrap; }
  .actions .btn, .review-btns .btn { flex: 1 1 9rem; }
  .modal-overlay { align-items: flex-end; padding: 1rem 0 0; }
  .modal { max-height: min(90dvh, 48rem); overflow-y: auto; padding: 1.5rem 1rem max(1rem, env(safe-area-inset-bottom)); }
  .modal-actions { position: sticky; bottom: 0; background: var(--white); }
  .fullscreen-toolbar { padding: max(0.75rem, env(safe-area-inset-top)) 1rem; }
  .fs-hint { bottom: max(1rem, env(safe-area-inset-bottom)); }
}
</style>
