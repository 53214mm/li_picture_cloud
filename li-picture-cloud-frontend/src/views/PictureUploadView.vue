<template>
  <div class="upload-page">
    <div class="container">
      <div class="upload-card">
        <h1>上传图片</h1>
        <p class="subtitle">支持 JPG/JPEG/PNG/WEBP 格式，单文件不超过 2MB。也可通过图片 URL 上传。</p>

        <!-- 模式切换 -->
        <div class="mode-tabs">
          <button
            class="mode-tab"
            :class="{ active: uploadMode === 'file' }"
            @click="switchMode('file')"
          >📁 文件上传</button>
          <button
            class="mode-tab"
            :class="{ active: uploadMode === 'url' }"
            @click="switchMode('url')"
          >🔗 URL 上传</button>
        </div>

        <form @submit.prevent="handleSubmit" class="upload-form">
          <!-- ===== 文件上传模式 ===== -->
          <template v-if="uploadMode === 'file'">
            <div
              class="drop-zone"
              :class="{ 'has-file': file, 'dragover': dragOver }"
              @dragover.prevent="dragOver = true"
              @dragleave="dragOver = false"
              @drop.prevent="onDrop"
              @click="fileInput?.click()"
            >
              <input
                ref="fileInput"
                type="file"
                accept="image/jpeg,image/jpg,image/png,image/webp,.jpg,.jpeg,.jpe,.jfif,.png,.webp"
                class="file-input-hidden"
                @change="onFileChange"
              />
              <template v-if="file">
                <img :src="previewUrl" class="preview-img" />
                <div class="file-info">
                  <strong>{{ file.name }}</strong>
                  <span>{{ formatSize(file.size) }}</span>
                </div>
                <button type="button" class="btn btn-outline btn-sm" @click.stop="openEditor">编辑</button>
                <button type="button" class="btn btn-outline btn-sm" @click.stop="clearFile">重新选择</button>
              </template>
              <template v-else>
                <div class="drop-prompt">
                  <span class="drop-icon">📁</span>
                  <p>拖拽图片到此处，或点击选择文件</p>
                </div>
              </template>
            </div>
          </template>

          <!-- ===== URL 上传模式 ===== -->
          <template v-if="uploadMode === 'url'">
            <div class="field">
              <span>图片 URL</span>
              <input v-model="urlInput" class="input" placeholder="https://example.com/image.jpg" />
            </div>
          </template>

          <!-- 图片名称（两种模式通用） -->
          <div class="field">
            <span>图片名称（可选）</span>
            <input v-model="form.name" class="input" placeholder="为图片命名" />
          </div>

          <!-- 空间选择（可选，留空上传至公共图库） -->
          <div class="field">
            <span>上传到空间（可选）</span>
            <select v-model="form.spaceId" class="input">
              <option :value="null">公共图库（无需空间）</option>
              <option v-for="sp in spaceList" :key="sp.id" :value="sp.id">
                {{ sp.spaceName }}（{{ sp.totalCount }}/{{ sp.maxCount }} 张）
              </option>
            </select>
          </div>

          <!-- 提交 -->
          <div v-if="error" class="form-error">{{ error }}</div>
          <div v-if="success" class="form-success">
            上传成功！
            <router-link :to="`/picture/${uploadedId}`">查看图片 &rarr;</router-link>
          </div>

          <div class="form-actions">
            <router-link to="/gallery" class="btn btn-outline">取消</router-link>
            <button type="submit" class="btn btn-primary" :disabled="!canSubmit || uploading">
              {{ uploading ? '上传中…' : '上传图片' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 图片编辑弹窗 -->
    <ImageEditModal
      :visible="showEditor"
      :image-src="previewUrl"
      @close="showEditor = false"
      @save="onEditSave"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { uploadPicture } from '@/api/picture'
import { listSpaceVOByPage } from '@/api/space'
import ImageEditModal from '@/components/ImageEditModal.vue'
import request from '@/api/request'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 权限守卫：需要登录
if (!userStore.isLoggedIn) router.replace('/login')

const uploadMode = ref('file')
const fileInput = ref(null)
const file = ref(null)
const previewUrl = ref('')
const urlInput = ref('')
const dragOver = ref(false)
const uploading = ref(false)
const error = ref('')
const success = ref(false)
const uploadedId = ref(null)
const spaceList = ref([])
const showEditor = ref(false)
// 从 URL query 读取 pre-selected spaceId
const form = reactive({
  name: '',
  // 保持 String，避免 Snowflake ID 精度丢失
  spaceId: route.query.spaceId || null
})

onMounted(async () => {
  try {
    const res = await listSpaceVOByPage({ current: 1, pageSize: 20, userId: userStore.currentUser?.id })
    spaceList.value = res.records || []
  } catch { /* 加载失败不影响上传 */ }
})

const canSubmit = computed(() => {
  if (uploadMode.value === 'file') return !!file.value
  return !!urlInput.value.trim()
})

function switchMode(mode) {
  uploadMode.value = mode
  error.value = ''
}

// ===== 文件模式 =====
function onDrop(e) {
  dragOver.value = false
  const f = e.dataTransfer.files[0]
  if (f) selectFile(f)
}

function onFileChange(e) {
  const f = e.target.files[0]
  if (f) selectFile(f)
}

function selectFile(f) {
  const allowed = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
  if (!allowed.includes(f.type)) {
    error.value = '仅支持 JPG、JPEG、PNG、WEBP 格式'
    return
  }
  if (f.size > 2 * 1024 * 1024) {
    error.value = '文件大小不能超过 2MB'
    return
  }
  error.value = ''
  file.value = f
  previewUrl.value = URL.createObjectURL(f)
}

function clearFile() {
  file.value = null
  previewUrl.value = ''
  showEditor.value = false
  if (fileInput.value) fileInput.value.value = ''
}

function openEditor() {
  if (!file.value) return
  showEditor.value = true
}

function onEditSave(blob) {
  // 用编辑后的 blob 替换原文件
  const newName = file.value.name.replace(/\.\w+$/, '') + '_edited.png'
  file.value = new File([blob], newName, { type: 'image/png' })
  previewUrl.value = URL.createObjectURL(blob)
  showEditor.value = false
}

// ===== 提交 =====
async function handleSubmit() {
  error.value = ''
  uploading.value = true
  try {
    let result
    if (uploadMode.value === 'file') {
      // 文件上传
      const formData = new FormData()
      formData.append('file', file.value)
      if (form.spaceId) {
        formData.append('spaceId', form.spaceId)
      }
      result = await uploadPicture(formData)
    } else {
      // URL 上传
      result = await request.post('/picture/upload/url', {
        fileUrl: urlInput.value.trim(),
        picName: form.name.trim(),
        spaceId: form.spaceId || undefined
      })
    }
    uploadedId.value = result.id
    success.value = true
    setTimeout(() => router.push(`/picture/${result.id}`), 2000)
  } catch (e) {
    error.value = e.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
.upload-page {
  min-height: calc(100vh - 4rem);
  display: flex; align-items: center; justify-content: center;
  padding: 3rem 0;
  background: var(--gray-100);
}
.upload-card {
  width: 100%; max-width: 560px;
  background: var(--white);
  border: 2px solid var(--black);
  padding: 3rem 2.5rem;
}
.upload-card h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; margin-bottom: 0.5rem; }
.subtitle { color: var(--gray-600); font-size: 0.9375rem; margin-bottom: 1.5rem; }
.upload-form { display: flex; flex-direction: column; gap: 1.25rem; }

/* 模式切换 */
.mode-tabs { display: flex; gap: 0; margin-bottom: 1rem; border: 2px solid var(--black); }
.mode-tab {
  flex: 1; padding: 0.625rem 1rem; font-size: 0.875rem; font-weight: 600;
  background: var(--white); border: none; cursor: pointer; transition: all 0.2s;
}
.mode-tab.active { background: var(--black); color: var(--white); }

/* 拖拽区域 */
.drop-zone {
  border: 2px dashed var(--gray-200);
  padding: 2rem; text-align: center; cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.drop-zone:hover,
.drop-zone.dragover { border-color: var(--black); background: var(--gray-100); }
.drop-zone.has-file { border-style: solid; display: flex; flex-direction: column; align-items: center; gap: 0.75rem; }
.file-input-hidden { display: none; }
.drop-prompt { color: var(--gray-400); }
.drop-icon { font-size: 2.5rem; display: block; margin-bottom: 0.5rem; }
.preview-img { max-height: 200px; max-width: 100%; object-fit: contain; }
.file-info { text-align: center; font-size: 0.875rem; }
.file-info strong { display: block; }
.file-info span { color: var(--gray-400); }
.btn-sm { padding: 0.375rem 1rem; font-size: 0.75rem; }

/* 表单 */
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field span { font-size: 0.8125rem; font-weight: 600; }
.form-error {
  padding: 0.75rem 1rem; background: #FFF0EF; color: var(--red);
  font-size: 0.875rem; font-weight: 500;
}
.form-success {
  padding: 0.75rem 1rem; background: #EFF9F0; color: #1A7A2E;
  font-size: 0.875rem; font-weight: 500;
}
.form-success a { font-weight: 600; text-decoration: underline; }
.form-actions { display: flex; gap: 0.75rem; justify-content: flex-end; }

@media (max-width: 767px) {
  .upload-page { min-height: calc(100dvh - 3.75rem); align-items: flex-start; padding: 1rem 0; }
  .upload-card { padding: 2rem 1.25rem; }
  .upload-card h1 { font-size: 1.75rem; }
  .drop-zone { padding: 1.5rem 1rem; }
  .form-actions { flex-direction: column; }
  .form-actions .btn { width: 100%; }
}
</style>
