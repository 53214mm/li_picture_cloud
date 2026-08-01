<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @click.self="handleCancel">
      <div class="modal-panel">
        <div class="modal-header">
          <h2>编辑图片</h2>
          <button class="close-btn" @click="handleCancel">&times;</button>
        </div>

        <ImageEditor ref="editorRef" :image-src="imageSrc" />

        <div class="modal-footer">
          <span class="note">编辑完成后点击"保存"，编辑后的图片将替换原图</span>
          <div class="footer-btns">
            <button class="btn btn-outline btn-sm" @click="handleCancel">取消</button>
            <button class="btn btn-primary btn-sm" @click="handleSave">保存并替换</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'
import ImageEditor from './ImageEditor.vue'

defineProps({
  visible: { type: Boolean, default: false },
  imageSrc: { type: String, default: '' }
})

const emit = defineEmits(['close', 'save'])

const editorRef = ref(null)

async function handleSave() {
  if (!editorRef.value) return
  const blob = await editorRef.value.exportBlob('image/png')
  emit('save', blob)
}

function handleCancel() {
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
</style>
