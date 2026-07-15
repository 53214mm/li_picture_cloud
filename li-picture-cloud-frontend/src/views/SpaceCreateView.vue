<template>
  <div class="create-page">
    <div class="container">
      <div class="form-card">
        <h1>创建私有空间</h1>
        <p class="subtitle">空间是独立的图片存储区域，拥有独立的容量和数量配额。</p>

        <!-- 空间级别选择 -->
        <div class="level-section">
          <h3>选择空间级别</h3>
          <div class="level-options">
            <div
              v-for="lv in spaceLevels"
              :key="lv.value"
              class="level-option"
              :class="{ active: form.spaceLevel === lv.value }"
              @click="selectLevel(lv)"
            >
              <div class="level-header">
                <span class="level-name">{{ lv.text }}</span>
                <span class="level-check" v-if="form.spaceLevel === lv.value">✓</span>
              </div>
              <div class="level-stats">
                <div class="lv-stat">
                  <span class="lv-val">{{ lv.maxCount }}</span>
                  <span class="lv-label">最多图片数</span>
                </div>
                <div class="lv-stat">
                  <span class="lv-val">{{ formatSize(lv.maxSize) }}</span>
                  <span class="lv-label">最大总容量</span>
                </div>
              </div>
              <p class="level-desc">{{ levelDescriptions[lv.value] }}</p>
            </div>
          </div>
          <p v-if="levelHint" class="level-hint">{{ levelHint }}</p>
        </div>

        <!-- 基本信息 -->
        <div class="field">
          <label>空间名称</label>
          <input
            v-model="form.spaceName"
            class="input"
            placeholder="给我的空间取个名字（不填默认「我的空间」）"
            maxlength="30"
          />
          <span class="field-hint">{{ (form.spaceName || '').length }} / 30</span>
        </div>

        <div v-if="error" class="form-error">{{ error }}</div>

        <div class="form-actions">
          <router-link to="/spaces" class="btn btn-outline">取消</router-link>
          <button class="btn btn-primary" :disabled="submitting" @click="handleCreate">
            {{ submitting ? '创建中…' : '创建空间' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { addSpace } from '@/api/space'
import { listSpaceLevel } from '@/api/space'
import { SPACE_LEVEL, formatSize } from '@/constants/space'

const router = useRouter()
const userStore = useUserStore()

if (!userStore.isLoggedIn) router.replace('/login')

const spaceLevels = ref([])
const form = reactive({ spaceName: '', spaceLevel: SPACE_LEVEL.COMMON })
const error = ref('')
const submitting = ref(false)

const levelDescriptions = {
  [SPACE_LEVEL.COMMON]: '适合个人日常使用，存储少量图片素材。',
  [SPACE_LEVEL.PROFESSIONAL]: '适合摄影爱好者，海量高清图片随心存。',
  [SPACE_LEVEL.FLAGSHIP]: '适合专业团队，超大容量不设限。'
}

const levelHint = computed(() => {
  if (form.spaceLevel !== SPACE_LEVEL.COMMON && !userStore.isAdmin) {
    return '提示：专业版和旗舰版需要管理员权限才能创建，当前将以普通版创建。'
  }
  return ''
})

onMounted(async () => {
  try {
    spaceLevels.value = await listSpaceLevel()
  } catch {
    // fallback to constants
    spaceLevels.value = [
      { value: 0, text: '普通版', maxCount: 100, maxSize: 100 * 1024 * 1024 },
      { value: 1, text: '专业版', maxCount: 1000, maxSize: 1000 * 1024 * 1024 },
      { value: 2, text: '旗舰版', maxCount: 10000, maxSize: 10000 * 1024 * 1024 }
    ]
  }
})

function selectLevel(lv) {
  if (lv.value !== SPACE_LEVEL.COMMON && !userStore.isAdmin) {
    // 非管理员只能选普通版，但允许点击查看信息
    form.spaceLevel = SPACE_LEVEL.COMMON
    return
  }
  form.spaceLevel = lv.value
}

async function handleCreate() {
  error.value = ''
  if (submitting.value) return
  submitting.value = true
  try {
    const newId = await addSpace({
      spaceName: form.spaceName || undefined,
      spaceLevel: form.spaceLevel
    })
    router.push(`/space/${newId}`)
  } catch (e) {
    error.value = e.message || '创建失败'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.create-page {
  min-height: calc(100vh - 4rem);
  padding: 3rem 0 5rem;
  background: var(--gray-100);
}
.form-card {
  max-width: 680px; margin: 0 auto;
  background: var(--white); border: 2px solid var(--black); padding: 3rem 2.5rem;
}
.form-card h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; margin-bottom: 0.5rem; }
.subtitle { color: var(--gray-600); font-size: 0.9375rem; margin-bottom: 2rem; }

/* 级别选择 */
.level-section { margin-bottom: 2rem; }
.level-section h3 { font-size: 1rem; font-weight: 600; margin-bottom: 0.75rem; }
.level-options { display: flex; flex-direction: column; gap: 0.75rem; }
.level-option {
  border: 2px solid var(--gray-200); padding: 1.25rem;
  cursor: pointer; transition: border-color 0.2s, background 0.2s;
}
.level-option:hover { border-color: var(--black); }
.level-option.active { border-color: var(--black); background: var(--gray-100); }
.level-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; }
.level-name { font-size: 1.125rem; font-weight: 700; }
.level-check {
  width: 24px; height: 24px; border-radius: 50%;
  background: var(--black); color: var(--white);
  display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; font-weight: 700;
}
.level-stats { display: flex; gap: 2rem; margin-bottom: 0.5rem; }
.lv-stat { display: flex; flex-direction: column; }
.lv-val { font-size: 1.25rem; font-weight: 700; }
.lv-label { font-size: 0.75rem; color: var(--gray-400); }
.level-desc { font-size: 0.8125rem; color: var(--gray-600); }
.level-hint { margin-top: 0.75rem; font-size: 0.8125rem; color: var(--yellow); background: #FFF9E6; padding: 0.5rem 0.75rem; }

/* 表单 */
.field { display: flex; flex-direction: column; gap: 0.375rem; margin-bottom: 1.5rem; }
.field label { font-size: 0.8125rem; font-weight: 600; }
.field-hint { font-size: 0.75rem; color: var(--gray-400); text-align: right; }
.form-error { padding: 0.75rem 1rem; background: #FFF0EF; color: var(--red); font-size: 0.875rem; font-weight: 500; margin-bottom: 1rem; }
.form-actions { display: flex; gap: 0.75rem; justify-content: flex-end; }
</style>
