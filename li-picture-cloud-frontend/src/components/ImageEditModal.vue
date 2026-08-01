<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @click.self="handleCancel">
      <div class="modal-panel">
        <div class="modal-header">
          <h2>{{ readOnly ? '观看协同' : '编辑图片' }}</h2>
          <button class="close-btn" @click="handleCancel">&times;</button>
        </div>

        <div v-if="collaborative" class="collaboration-bar">
          <span class="connection-dot" :class="connectionStatus"></span>
          {{ connectionText }}
          <span v-if="latestPrompt" class="operation-prompt">{{ latestPrompt }}</span>
        </div>
        <ImageEditor
          ref="editorRef"
          :image-src="imageSrc"
          :collaborative="collaborative"
          :read-only="readOnly"
          :collaboration-state="collaborationState"
          @operation="sendOperation"
        />

        <div class="modal-footer">
          <span v-if="!readOnly" class="note">编辑完成后点击"保存"，编辑后的图片将替换原图</span>
          <span v-else class="note">只读模式：你可以实时观看操作，但不能修改图片</span>
          <div class="footer-btns">
            <button class="btn btn-outline btn-sm" @click="handleCancel">{{ readOnly ? '关闭' : '取消' }}</button>
            <button v-if="!readOnly" class="btn btn-primary btn-sm" @click="handleSave">保存并替换</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import ImageEditor from './ImageEditor.vue'
import { CollaborationClient } from '@/services/collaborationClient'

const props = defineProps({
  visible: { type: Boolean, default: false },
  imageSrc: { type: String, default: '' },
  pictureId: { type: [String, Number], default: null },
  collaborative: { type: Boolean, default: false },
  readOnly: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'save'])

const editorRef = ref(null)
const collaborationState = ref({ rotation: 0, scale: 1, version: 0 })
const connectionStatus = ref('disconnected')
const latestPrompt = ref('')
let client = null
let unsubscribe = null

const connectionText = computed(() => ({
  connected: '协同已连接',
  error: '协同连接异常',
  disconnected: '协同正在重连'
}[connectionStatus.value]).replace('协同', props.readOnly ? '只读协同' : '协同'))

watch(() => [props.visible, props.collaborative, props.pictureId], ([visible, collaborative, pictureId]) => {
  disconnect()
  if (!visible || !collaborative || !pictureId) return
  client = new CollaborationClient()
  unsubscribe = client.subscribe(handleCollaborationEvent)
  client.connect(pictureId)
}, { immediate: true })

function handleCollaborationEvent(event) {
  if (event.type === 'CONNECTION') {
    connectionStatus.value = event.status
    return
  }
  if (event.state) collaborationState.value = event.state
  if (event.message && event.type !== 'STATE') latestPrompt.value = event.message
}

function sendOperation(operation) {
  if (props.readOnly) return
  try {
    client?.send(operation, collaborationState.value.version)
  } catch (error) {
    latestPrompt.value = error.message
  }
}

function disconnect() {
  unsubscribe?.()
  unsubscribe = null
  client?.close()
  client = null
  connectionStatus.value = 'disconnected'
}

onUnmounted(disconnect)

async function handleSave() {
  if (props.readOnly) return
  if (!editorRef.value) return
  const blob = await editorRef.value.exportBlob('image/png')
  emit('save', blob)
}

function handleCancel() {
  disconnect()
  emit('close')
}
</script>

<style scoped>
.modal-overlay {
  position: fixed; inset: 0; z-index: 300;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
  padding: 1rem;
}
.modal-panel {
  background: var(--white); border: 2px solid var(--black);
  width: 100%; max-width: 900px; max-height: 90vh;
  display: flex; flex-direction: column;
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 1rem 1.5rem; border-bottom: 1px solid var(--gray-200);
}
.modal-header h2 { font-size: 1.25rem; font-weight: 700; }
.close-btn {
  width: 32px; height: 32px; font-size: 1.25rem;
  border: 2px solid var(--black); background: var(--white);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
}
.close-btn:hover { background: var(--black); color: var(--white); }
.modal-footer {
  display: flex; justify-content: space-between; align-items: center;
  padding: 1rem 1.5rem; border-top: 1px solid var(--gray-200);
  flex-wrap: wrap; gap: 0.5rem;
}
.note { font-size: 0.8125rem; color: var(--gray-400); }
.footer-btns { display: flex; gap: 0.5rem; }
.btn-sm { padding: 0.5rem 1.25rem; font-size: 0.75rem; }
.collaboration-bar {
  display: flex; align-items: center; gap: 0.5rem;
  padding: 0.65rem 1rem; border-bottom: 1px solid var(--gray-200);
  font-size: 0.8125rem;
}
.connection-dot { width: 8px; height: 8px; border-radius: 50%; background: #999; }
.connection-dot.connected { background: #1a7a2e; }
.connection-dot.error { background: #c53030; }
.operation-prompt { margin-left: auto; color: var(--gray-600); }
</style>
