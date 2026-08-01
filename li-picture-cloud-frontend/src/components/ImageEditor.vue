<template>
  <div class="editor-root">
    <!-- 工具栏 -->
    <div v-if="!readOnly" class="editor-toolbar">
      <button v-if="!collaborative" class="tool-btn" :class="{ active: mode === 'crop' }" @click="setMode('crop')" title="裁剪">✂️ 裁剪</button>
      <button class="tool-btn" @click="handleRotate(-90)" title="逆时针旋转">↺ 左旋</button>
      <button class="tool-btn" @click="handleRotate(90)" title="顺时针旋转">↻ 右旋</button>
      <button v-if="!collaborative" class="tool-btn" @click="flipH" title="水平翻转">⇔</button>
      <button v-if="!collaborative" class="tool-btn" @click="flipV" title="垂直翻转">⇕</button>
      <button v-if="!collaborative" class="tool-btn" @click="reset" title="恢复原图">↩ 重置</button>
      <span class="tool-sep"></span>
      <label class="tool-label">缩放</label>
      <template v-if="collaborative">
        <button class="tool-btn" @click="emitOperation('ZOOM_OUT')">− 缩小</button>
        <button class="tool-btn" @click="emitOperation('ZOOM_IN')">＋ 放大</button>
      </template>
      <input v-else type="range" min="10" max="200" :value="zoom" class="zoom-slider" @input="setZoom($event.target.value)" />
      <span class="zoom-val">{{ zoom }}%</span>
    </div>

    <!-- Canvas 区域 -->
    <div ref="canvasWrap" class="canvas-wrap" @mousedown="onMouseDown" @mousemove="onMouseMove" @mouseup="onMouseUp" @mouseleave="onMouseUp">
      <canvas ref="canvas"></canvas>
      <!-- 裁剪提示 -->
      <div v-if="mode === 'crop' && !cropStart" class="crop-hint">拖拽选取裁剪区域</div>
    </div>

    <!-- 裁剪模式提示 -->
    <div v-if="mode === 'crop' && cropRect" class="crop-info">
      裁剪区域: {{ Math.round(cropRect.w) }} × {{ Math.round(cropRect.h) }}
      <button class="tool-btn apply-btn" @click="applyCrop">✓ 应用裁剪</button>
      <button class="tool-btn" @click="cancelCrop">取消</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'

const props = defineProps({
  imageSrc: { type: String, required: true },
  collaborative: { type: Boolean, default: false },
  readOnly: { type: Boolean, default: false },
  collaborationState: { type: Object, default: null }
})

const emit = defineEmits(['done', 'operation'])

const canvas = ref(null)
const canvasWrap = ref(null)
const mode = ref('')
const zoom = ref(100)
const cropStart = ref(null)
const cropRect = ref(null)

let img = null
let originalImg = null  // 保存最原始图片，用于 reset
let displayScale = 1   // canvas 显示比例
let transformSequence = 0

onMounted(() => loadImage())

function loadImage() {
  const image = new Image()
  image.crossOrigin = 'anonymous'
  image.onload = () => {
    img = image
    originalImg = image
    if (props.collaborative && props.collaborationState) applyCollaborationState(props.collaborationState)
    else draw()
  }
  image.src = props.imageSrc
}

// ===== 绘制 =====
function draw() {
  nextTick(() => {
    const c = canvas.value
    if (!c || !img) return
    const wrap = canvasWrap.value
    // Canvas 填满容器
    const maxW = wrap.clientWidth
    const maxH = Math.min(wrap.clientHeight || 500, 600)
    const scaleX = maxW / img.width
    const scaleY = maxH / img.height
    displayScale = Math.min(scaleX, scaleY, 1) * (zoom.value / 100)
    c.width = img.width * displayScale
    c.height = img.height * displayScale
    const ctx = c.getContext('2d')
    ctx.clearRect(0, 0, c.width, c.height)
    ctx.drawImage(img, 0, 0, c.width, c.height)

    // 绘制裁剪框
    if (mode.value === 'crop' && cropRect.value) {
      ctx.strokeStyle = '#000'
      ctx.lineWidth = 2
      ctx.setLineDash([6, 3])
      ctx.strokeRect(cropRect.value.x, cropRect.value.y, cropRect.value.w, cropRect.value.h)
      // 半透明遮罩
      ctx.fillStyle = 'rgba(0,0,0,0.3)'
      ctx.fillRect(0, 0, c.width, cropRect.value.y)
      ctx.fillRect(0, cropRect.value.y, cropRect.value.x, cropRect.value.h)
      ctx.fillRect(cropRect.value.x + cropRect.value.w, cropRect.value.y, c.width - cropRect.value.x - cropRect.value.w, cropRect.value.h)
      ctx.fillRect(0, cropRect.value.y + cropRect.value.h, c.width, c.height - cropRect.value.y - cropRect.value.h)
    }
  })
}

// ===== 旋转 =====
function rotate(deg) {
  if (!img) return
  const c = document.createElement('canvas')
  const rad = (deg * Math.PI) / 180
  if (Math.abs(deg) === 90) {
    c.width = img.height; c.height = img.width
  } else {
    c.width = img.width; c.height = img.height
  }
  const ctx = c.getContext('2d')
  ctx.translate(c.width / 2, c.height / 2)
  ctx.rotate(rad)
  ctx.drawImage(img, -img.width / 2, -img.height / 2)
  img = new Image()
  img.src = c.toDataURL('image/png')
  img.onload = () => { cropRect.value = null; draw() }
}

function handleRotate(deg) {
  if (props.collaborative) emitOperation(deg < 0 ? 'ROTATE_LEFT' : 'ROTATE_RIGHT')
  else rotate(deg)
}

function emitOperation(operation) {
  if (props.readOnly) return
  emit('operation', operation)
}

function applyCollaborationState(state) {
  if (!originalImg || !state) return
  const sequence = ++transformSequence
  zoom.value = Math.round(state.scale * 100)
  const rotation = ((state.rotation % 360) + 360) % 360
  if (rotation === 0) {
    img = originalImg
    draw()
    return
  }
  const c = document.createElement('canvas')
  const swapSides = rotation === 90 || rotation === 270
  c.width = swapSides ? originalImg.height : originalImg.width
  c.height = swapSides ? originalImg.width : originalImg.height
  const ctx = c.getContext('2d')
  ctx.translate(c.width / 2, c.height / 2)
  ctx.rotate(rotation * Math.PI / 180)
  ctx.drawImage(originalImg, -originalImg.width / 2, -originalImg.height / 2)
  const transformed = new Image()
  transformed.onload = () => {
    if (sequence !== transformSequence) return
    img = transformed
    draw()
  }
  transformed.src = c.toDataURL('image/png')
}

watch(() => props.collaborationState, applyCollaborationState, { deep: true })

// ===== 翻转 =====
function flipH() { flip('h') }
function flipV() { flip('v') }
function flip(dir) {
  if (!img) return
  const c = document.createElement('canvas')
  c.width = img.width; c.height = img.height
  const ctx = c.getContext('2d')
  if (dir === 'h') { ctx.translate(c.width, 0); ctx.scale(-1, 1) }
  else { ctx.translate(0, c.height); ctx.scale(1, -1) }
  ctx.drawImage(img, 0, 0)
  img = new Image()
  img.src = c.toDataURL('image/png')
  img.onload = () => { cropRect.value = null; draw() }
}

// ===== 裁剪 =====
function setMode(m) { mode.value = mode.value === m ? '' : m }
function onMouseDown(e) {
  if (mode.value !== 'crop' || !img) return
  const rect = canvas.value.getBoundingClientRect()
  cropStart.value = {
    x: (e.clientX - rect.left) / displayScale,
    y: (e.clientY - rect.top) / displayScale
  }
}
function onMouseMove(e) {
  if (!cropStart.value) return
  const rect = canvas.value.getBoundingClientRect()
  const x = (e.clientX - rect.left) / displayScale
  const y = (e.clientY - rect.top) / displayScale
  cropRect.value = {
    x: Math.min(cropStart.value.x, x),
    y: Math.min(cropStart.value.y, y),
    w: Math.abs(x - cropStart.value.x),
    h: Math.abs(y - cropStart.value.y)
  }
  draw()
}
function onMouseUp() {
  if (!cropStart.value) return
  cropStart.value = null
}
function applyCrop() {
  if (!cropRect.value || !img) return
  const r = cropRect.value
  const c = document.createElement('canvas')
  c.width = r.w; c.height = r.h
  const ctx = c.getContext('2d')
  ctx.drawImage(img, r.x, r.y, r.w, r.h, 0, 0, r.w, r.h)
  img = new Image()
  img.src = c.toDataURL('image/png')
  img.onload = () => { cropRect.value = null; mode.value = ''; draw() }
}
function cancelCrop() { cropRect.value = null; draw() }

// ===== 缩放 =====
function setZoom(v) { zoom.value = Number(v); draw() }

// ===== 重置 =====
function reset() {
  img = originalImg
  cropRect.value = null
  mode.value = ''
  zoom.value = 100
  draw()
}

// ===== 导出 =====
function exportBlob(format = 'image/png', quality = 0.92) {
  return new Promise((resolve) => {
    const c = document.createElement('canvas')
    const outputScale = props.collaborative ? (props.collaborationState?.scale || 1) : 1
    c.width = Math.max(1, Math.round(img.width * outputScale))
    c.height = Math.max(1, Math.round(img.height * outputScale))
    c.getContext('2d').drawImage(img, 0, 0, c.width, c.height)
    c.toBlob(blob => resolve(blob), format, quality)
  })
}

defineExpose({ exportBlob, getImage: () => img })
</script>

<style scoped>
.editor-root {
  display: flex; flex-direction: column; gap: 0.75rem;
  background: var(--gray-100); padding: 1rem;
}
.editor-toolbar {
  display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;
}
.tool-btn {
  padding: 0.375rem 0.75rem; font-size: 0.8125rem; font-weight: 500;
  border: 1.5px solid var(--gray-200); background: var(--white);
  cursor: pointer; transition: all 0.15s;
}
.tool-btn:hover, .tool-btn.active { border-color: var(--black); }
.tool-btn.active { background: var(--black); color: var(--white); }
.apply-btn { border-color: #1A7A2E; color: #1A7A2E; }
.apply-btn:hover { background: #1A7A2E; color: var(--white); }
.tool-sep { width: 1px; height: 20px; background: var(--gray-200); margin: 0 0.25rem; }
.tool-label { font-size: 0.75rem; color: var(--gray-400); }
.zoom-slider { width: 80px; accent-color: var(--black); }
.zoom-val { font-size: 0.75rem; color: var(--gray-600); min-width: 36px; }

.canvas-wrap {
  display: flex; justify-content: center; align-items: center;
  min-height: 200px; max-height: 55vh; overflow: auto;
  background: repeating-conic-gradient(#e0e0e0 0% 25%, #fff 0% 50%) 50% / 20px 20px;
  position: relative; cursor: crosshair;
}
.crop-hint {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  padding: 0.5rem 1rem; background: rgba(0,0,0,0.7); color: #fff;
  font-size: 0.875rem; border-radius: 4px; pointer-events: none;
}
.crop-info {
  display: flex; align-items: center; gap: 0.75rem; font-size: 0.875rem; flex-wrap: wrap;
}
</style>
