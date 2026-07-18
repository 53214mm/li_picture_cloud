<template>
  <Teleport to="body">
    <div v-if="visible" class="share-overlay" @click.self="close">
      <div class="share-card">
        <div class="share-header">
          <h2>分享图片</h2>
          <button class="close-btn" @click="close">&times;</button>
        </div>

        <!-- 图片预览 -->
        <div class="share-preview">
          <img :src="thumbnailUrl || imageUrl" :alt="title" />
        </div>
        <p class="share-title">{{ title || '未命名图片' }}</p>

        <!-- 链接复制 -->
        <div class="share-section">
          <label>分享链接</label>
          <div class="link-row">
            <input
              ref="linkInput"
              :value="shareUrl"
              class="input"
              readonly
              @click="$refs.linkInput.select()"
            />
            <button class="btn btn-primary btn-sm" @click="copyLink">
              {{ copied ? '已复制 ✓' : '复制' }}
            </button>
          </div>
        </div>

        <!-- 二维码 -->
        <div class="share-section">
          <label>手机扫码查看</label>
          <div class="qrcode-wrapper">
            <img
              v-if="shareUrl"
              :src="qrCodeUrl"
              alt="二维码"
              class="qrcode-img"
            />
            <span class="qr-hint">打开相机或微信扫描二维码</span>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  pictureId: { type: [String, Number], required: true },
  title: { type: String, default: '' },
  imageUrl: { type: String, default: '' },
  thumbnailUrl: { type: String, default: '' }
})

const emit = defineEmits(['close'])

const linkInput = ref(null)
const copied = ref(false)

const baseUrl = window.location.origin
const shareUrl = computed(() => `${baseUrl}/picture/${props.pictureId}`)

// 使用免费 QR Code API 生成二维码
const qrCodeUrl = computed(() => {
  const encoded = encodeURIComponent(shareUrl.value)
  return `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encoded}`
})

function close() {
  copied.value = false
  emit('close')
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    copied.value = true
    setTimeout(() => copied.value = false, 2000)
  } catch {
    // 降级：选中输入框内容让用户手动复制
    linkInput.value?.select()
  }
}
</script>

<style scoped>
.share-overlay {
  position: fixed; inset: 0; z-index: 300;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
}
.share-card {
  background: var(--white); border: 2px solid var(--black);
  padding: 2rem; width: 100%; max-width: 400px;
  max-height: 90vh; overflow-y: auto;
  display: flex; flex-direction: column; gap: 1.25rem;
}
.share-header { display: flex; justify-content: space-between; align-items: center; }
.share-header h2 { font-size: 1.5rem; font-weight: 700; }
.close-btn {
  width: 32px; height: 32px; font-size: 1.25rem;
  border: 2px solid var(--black); background: var(--white);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.close-btn:hover { background: var(--black); color: var(--white); }

/* 预览 */
.share-preview {
  width: 100%; aspect-ratio: 4/3;
  overflow: hidden; border: 2px solid var(--gray-200);
  background: var(--gray-100); display: flex; align-items: center; justify-content: center;
}
.share-preview img { max-width: 100%; max-height: 100%; object-fit: contain; }
.share-title { font-size: 1rem; font-weight: 600; text-align: center; }

/* 链接 */
.share-section { display: flex; flex-direction: column; gap: 0.5rem; }
.share-section label { font-size: 0.8125rem; font-weight: 600; }
.link-row { display: flex; gap: 0.5rem; }
.link-row .input { flex: 1; font-size: 0.8125rem; cursor: text; }
.btn-sm { padding: 0.5rem 1rem; font-size: 0.75rem; flex-shrink: 0; }

/* 二维码 */
.qrcode-wrapper {
  display: flex; flex-direction: column; align-items: center; gap: 0.5rem;
  padding: 1rem; background: var(--gray-100);
}
.qrcode-img { width: 180px; height: 180px; border: 4px solid var(--white); }
.qr-hint { font-size: 0.75rem; color: var(--gray-400); }
</style>
