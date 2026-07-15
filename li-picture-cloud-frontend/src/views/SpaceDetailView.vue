<template>
  <div class="space-detail-page">
    <div class="container">
      <!-- 加载中 -->
      <div v-if="loading" class="loading">加载中…</div>

      <!-- 空间不存在 -->
      <div v-else-if="!space" class="empty-state">
        <p>空间不存在或已被删除</p>
        <router-link to="/spaces" class="btn btn-outline">返回空间列表</router-link>
      </div>

      <template v-else>
        <!-- 空间信息头 -->
        <div class="space-header">
          <div class="space-top">
            <div>
              <div class="title-row">
                <h1>{{ space.spaceName }}</h1>
                <span class="badge" :class="'level-' + space.spaceLevel">
                  {{ spaceLevelText(space.spaceLevel) }}
                </span>
              </div>
              <p class="space-meta" v-if="space.user">
                创建者：{{ space.user.userName }} · {{ formatDate(space.createTime) }}
              </p>
            </div>
            <div class="header-actions">
              <button v-if="isOwner" class="btn btn-primary btn-sm" @click="showUpload = !showUpload">
                {{ showUpload ? '取消上传' : '+ 上传图片' }}
              </button>
              <router-link to="/spaces" class="btn btn-outline btn-sm">返回列表</router-link>
            </div>
          </div>

          <!-- 用量进度条 -->
          <div class="space-meter">
            <div class="meter-item">
              <div class="meter-label">
                <span>图片数量</span>
                <span>{{ space.totalCount }} / {{ space.maxCount }}</span>
              </div>
              <div class="meter-bar">
                <div class="meter-fill count-fill" :style="{ width: countPercent + '%' }"></div>
              </div>
            </div>
            <div class="meter-item">
              <div class="meter-label">
                <span>存储容量</span>
                <span>{{ formatSize(space.totalSize) }} / {{ formatSize(space.maxSize) }}</span>
              </div>
              <div class="meter-bar">
                <div class="meter-fill size-fill" :style="{ width: sizePercent + '%' }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 上传面板 -->
        <div v-if="showUpload && isOwner" class="upload-panel">
          <div class="upload-tabs">
            <button
              :class="{ active: uploadMode === 'file' }"
              @click="uploadMode = 'file'"
            >📁 文件上传</button>
            <button
              :class="{ active: uploadMode === 'url' }"
              @click="uploadMode = 'url'"
            >🔗 URL 上传</button>
          </div>

          <!-- 文件模式 -->
          <div v-if="uploadMode === 'file'" class="upload-drop-zone" @click="fileInput?.click()">
            <input ref="fileInput" type="file" accept="image/jpeg,image/jpg,image/png,image/webp,.jpg,.jpeg,.jpe,.jfif,.png,.webp" hidden @change="onFileChange" />
            <template v-if="uploadFile">
              <img :src="uploadPreview" class="preview-img" />
              <span>{{ uploadFile.name }}</span>
              <button class="btn btn-outline btn-sm" @click.stop="clearFile">重新选择</button>
            </template>
            <template v-else>
              <span class="drop-icon">📁</span>
              <p>点击选择文件</p>
            </template>
          </div>

          <!-- URL 模式 -->
          <div v-if="uploadMode === 'url'" class="url-row">
            <input v-model="urlInput" class="input" placeholder="https://example.com/image.jpg" />
          </div>

          <!-- 名称 + 提交 -->
          <div class="upload-form-row">
            <input v-model="uploadName" class="input" placeholder="图片名称（可选）" style="max-width: 240px;" />
            <button
              class="btn btn-primary"
              :disabled="!canUpload || uploading"
              @click="handleUpload"
            >
              {{ uploading ? '上传中…' : '上传到空间' }}
            </button>
          </div>
          <div v-if="uploadError" class="form-error">{{ uploadError }}</div>
          <div v-if="uploadSuccess" class="form-success">上传成功！</div>
        </div>

        <!-- 图片列表（复用组件） -->
        <PictureList
          :pictures="pictures"
          :loading="picLoading"
          :total="total"
          :current="currentPage"
          :page-size="12"
          :category-list="categoryList"
          :empty-text="isOwner ? '空间暂无图片，点击上方按钮上传' : '空间暂无图片'"
          :show-actions="isOwner"
          @search="onSearch"
          @filter-change="onFilterChange"
          @page-change="onPageChange"
        >
          <template #toolbar-actions>
            <span v-if="isOwner" class="hint-text">鼠标悬停图片查看快捷操作</span>
          </template>
          <template #actions="{ picture }">
            <button class="action-btn" @click.stop="openEditDialog(picture)" title="编辑">
              <span class="action-icon">✏️</span>
            </button>
            <button class="action-btn danger" @click.stop="handleDeletePic(picture)" title="删除">
              <span class="action-icon">🗑️</span>
            </button>
          </template>
        </PictureList>
      </template>
    </div>

    <!-- 编辑图片弹窗 -->
    <div v-if="showEditDialog" class="modal-overlay" @click.self="showEditDialog = false">
      <div class="modal-card">
        <h2>编辑图片</h2>
        <div class="field">
          <label>名称</label>
          <input v-model="editForm.name" class="input" maxlength="128" />
        </div>
        <div class="field">
          <label>简介</label>
          <textarea v-model="editForm.introduction" class="input" rows="3" maxlength="800"></textarea>
        </div>
        <div class="field">
          <label>分类</label>
          <select v-model="editForm.category" class="input">
            <option value="">无</option>
            <option v-for="c in categoryList" :key="c" :value="c">{{ c }}</option>
          </select>
        </div>
        <div class="field">
          <label>标签（逗号分隔）</label>
          <input
            v-model="editForm.tagsStr"
            class="input"
            placeholder="风景, 城市, 夜景"
          />
        </div>
        <div v-if="editError" class="form-error">{{ editError }}</div>
        <div class="form-actions">
          <button class="btn btn-outline" @click="showEditDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleEditSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getSpaceVOById } from '@/api/space'
import { listPictureVOByPage, editPicture, deletePicture, getPictureTagCategory } from '@/api/picture'
import { uploadPicture } from '@/api/picture'
import request from '@/api/request'
import PictureList from '@/components/PictureList.vue'
import { spaceLevelText, formatSize, formatDate } from '@/constants/space'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 空间 ID 是 Snowflake 长整型，必须保持 String 避免 JS 精度丢失
const spaceId = computed(() => route.params.id)

// 空间信息
const space = ref(null)
const loading = ref(true)
const isOwner = computed(() => space.value?.userId === userStore.currentUser?.id)

// 图片列表
const pictures = ref([])
const picLoading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const categoryList = ref([])
const searchText = ref('')
const currentCategory = ref('')
const currentFormat = ref('')
const currentSort = ref('descend')

// 上传
const showUpload = ref(false)
const uploadMode = ref('file')
const fileInput = ref(null)
const uploadFile = ref(null)
const uploadPreview = ref('')
const urlInput = ref('')
const uploadName = ref('')
const uploading = ref(false)
const uploadError = ref('')
const uploadSuccess = ref(false)

const canUpload = computed(() => {
  if (uploadMode.value === 'file') return !!uploadFile.value
  return !!urlInput.value.trim()
})

// 编辑
const showEditDialog = ref(false)
const editError = ref('')
const editForm = reactive({ id: null, name: '', introduction: '', category: '', tagsStr: '' })

const countPercent = computed(() => {
  if (!space.value) return 0
  return space.value.maxCount ? Math.min(100, Math.round((space.value.totalCount / space.value.maxCount) * 100)) : 0
})
const sizePercent = computed(() => {
  if (!space.value) return 0
  return space.value.maxSize ? Math.min(100, Math.round((space.value.totalSize / space.value.maxSize) * 100)) : 0
})

onMounted(async () => {
  try {
    const meta = await getPictureTagCategory()
    categoryList.value = meta.categoryList || []
  } catch { /* ignore */ }

  try {
    space.value = await getSpaceVOById(spaceId.value)
  } catch (e) {
    console.error('加载空间失败', e)
  } finally {
    loading.value = false
  }

  loadPictures()
})

async function loadPictures() {
  picLoading.value = true
  try {
    const res = await listPictureVOByPage({
      current: currentPage.value,
      pageSize: 12,
      spaceId: spaceId.value,
      searchText: searchText.value || undefined,
      category: currentCategory.value || undefined,
      picFormat: currentFormat.value || undefined,
      sortField: 'createTime',
      sortOrder: currentSort.value
    })
    pictures.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error('加载图片失败', e)
  } finally {
    picLoading.value = false
  }
}

// ===== 上传 =====
function onFileChange(e) {
  const f = e.target.files[0]
  if (!f) return
  const allowed = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
  if (!allowed.includes(f.type)) { uploadError.value = '仅支持 JPG/PNG/WEBP 格式'; return }
  if (f.size > 2 * 1024 * 1024) { uploadError.value = '文件不能超过 2MB'; return }
  uploadError.value = ''
  uploadFile.value = f
  uploadPreview.value = URL.createObjectURL(f)
}

function clearFile() {
  uploadFile.value = null
  uploadPreview.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

async function handleUpload() {
  uploadError.value = ''
  uploadSuccess.value = false
  uploading.value = true
  try {
    if (uploadMode.value === 'file') {
      const fd = new FormData()
      fd.append('file', uploadFile.value)
      fd.append('spaceId', spaceId.value)
      if (uploadName.value.trim()) fd.append('picName', uploadName.value.trim())
      await uploadPicture(fd)
    } else {
      await request.post('/picture/upload/url', {
        fileUrl: urlInput.value.trim(),
        picName: uploadName.value.trim() || undefined,
        spaceId: spaceId.value
      })
    }
    uploadSuccess.value = true
    clearFile()
    urlInput.value = ''
    uploadName.value = ''
    setTimeout(() => { uploadSuccess.value = false }, 3000)
    loadPictures()
    // 更新空间用量（重新加载空间信息）
    space.value = await getSpaceVOById(spaceId.value)
  } catch (e) {
    uploadError.value = e.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

// ===== 搜索筛选 =====
function onSearch(text) {
  searchText.value = text
  currentPage.value = 1
  loadPictures()
}

function onFilterChange(filters) {
  currentCategory.value = filters.category
  currentFormat.value = filters.picFormat
  currentSort.value = filters.sortOrder
  currentPage.value = 1
  loadPictures()
}

function onPageChange(page) {
  currentPage.value = page
  loadPictures()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ===== 编辑图片 =====
function openEditDialog(pic) {
  editForm.id = pic.id
  editForm.name = pic.name || ''
  editForm.introduction = pic.introduction || ''
  editForm.category = pic.category || ''
  editForm.tagsStr = (pic.tags || []).join(', ')
  editError.value = ''
  showEditDialog.value = true
}

async function handleEditSave() {
  editError.value = ''
  try {
    const tags = editForm.tagsStr
      ? editForm.tagsStr.split(',').map(t => t.trim()).filter(Boolean)
      : []
    await editPicture({
      id: editForm.id,
      name: editForm.name || undefined,
      introduction: editForm.introduction || undefined,
      category: editForm.category || undefined,
      tags
    })
    showEditDialog.value = false
    loadPictures()
  } catch (e) {
    editError.value = e.message || '保存失败'
  }
}

// ===== 删除图片 =====
async function handleDeletePic(pic) {
  if (!confirm(`确定删除图片"${pic.name || '未命名'}"吗？`)) return
  try {
    await deletePicture(pic.id)
    loadPictures()
    // 更新空间用量
    space.value = await getSpaceVOById(spaceId.value)
  } catch (e) {
    alert(e.message || '删除失败')
  }
}
</script>

<style scoped>
.space-detail-page { padding: 3rem 0 5rem; }
.loading { text-align: center; padding: 4rem 0; color: var(--gray-400); }
.empty-state { text-align: center; padding: 5rem 0; color: var(--gray-400); }
.empty-state p { margin-bottom: 1rem; font-size: 1.125rem; }

/* 空间信息头 */
.space-header { margin-bottom: 2rem; }
.space-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.25rem; }
.title-row { display: flex; align-items: center; gap: 0.75rem; }
.title-row h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; }
.badge { padding: 0.2rem 0.75rem; font-size: 0.75rem; font-weight: 600; background: var(--black); color: var(--white); }
.badge.level-1 { background: var(--blue); }
.badge.level-2 { background: var(--red); }
.space-meta { color: var(--gray-600); font-size: 0.875rem; margin-top: 0.25rem; }
.header-actions { display: flex; gap: 0.5rem; }
.btn-sm { padding: 0.5rem 1.25rem; font-size: 0.75rem; }

/* 进度条 */
.space-meter { display: flex; gap: 2rem; }
.meter-item { flex: 1; display: flex; flex-direction: column; gap: 0.25rem; }
.meter-label { display: flex; justify-content: space-between; font-size: 0.8125rem; font-weight: 500; }
.meter-bar { height: 6px; background: var(--gray-100); border-radius: 3px; overflow: hidden; }
.meter-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
.count-fill { background: var(--blue); }
.size-fill { background: var(--yellow); }

/* 上传面板 */
.upload-panel { border: 2px solid var(--black); padding: 1.5rem; margin-bottom: 2rem; background: var(--gray-100); }
.upload-tabs { display: flex; border: 2px solid var(--black); margin-bottom: 1rem; }
.upload-tabs button { flex: 1; padding: 0.5rem; font-size: 0.875rem; font-weight: 600; border: none; background: var(--white); cursor: pointer; }
.upload-tabs button.active { background: var(--black); color: var(--white); }
.upload-drop-zone { border: 2px dashed var(--gray-400); padding: 1.5rem; text-align: center; cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem; }
.upload-drop-zone:hover { border-color: var(--black); background: var(--white); }
.preview-img { max-height: 150px; object-fit: contain; }
.drop-icon { font-size: 2rem; }
.url-row { margin-bottom: 0.75rem; }
.upload-form-row { display: flex; gap: 0.75rem; align-items: center; }
.form-error { padding: 0.5rem 0.75rem; background: #FFF0EF; color: var(--red); font-size: 0.8125rem; font-weight: 500; margin-top: 0.5rem; }
.form-success { padding: 0.5rem 0.75rem; background: #EFF9F0; color: #1A7A2E; font-size: 0.8125rem; font-weight: 500; margin-top: 0.5rem; }
.hint-text { font-size: 0.8125rem; color: var(--gray-400); }

/* 快捷操作按钮 */
.action-btn {
  width: 32px; height: 32px;
  border: none; background: rgba(0,0,0,0.6); color: var(--white);
  border-radius: 4px; cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; transition: background 0.2s;
}
.action-btn:hover { background: var(--blue); }
.action-btn.danger:hover { background: var(--red); }
.action-icon { font-size: 0.875rem; }

/* 弹窗 */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 200; }
.modal-card { background: var(--white); border: 2px solid var(--black); padding: 2rem; width: 100%; max-width: 500px; display: flex; flex-direction: column; gap: 1rem; max-height: 90vh; overflow-y: auto; }
.modal-card h2 { font-size: 1.5rem; font-weight: 700; }
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field label { font-size: 0.8125rem; font-weight: 600; }
.form-actions { display: flex; gap: 0.75rem; justify-content: flex-end; margin-top: 0.5rem; }
</style>
