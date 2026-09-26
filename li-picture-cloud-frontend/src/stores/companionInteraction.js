import { defineStore } from 'pinia'
import { ref, shallowRef, watch } from 'vue'
import { useUserStore } from './user'
import { COMPANION_UI_ENABLED } from '@/config/features'
import { getPictureVOById } from '@/api/picture'
import { getSpaceVOById } from '@/api/space'
import { createPictureDragSession, createPictureInspector, PICTURE_DRAG_TYPE } from '@/presentation/companionInteraction'

export const useCompanionInteractionStore = defineStore('companionInteraction', () => {
  const user = useUserStore()
  const requestNumber = ref(0)
  const inspection = shallowRef(null)
  const destination = shallowRef(null)
  const dragging = ref(false)
  const drag = createPictureDragSession()
  const inspector = createPictureInspector({ readPicture: getPictureVOById, readSpace: getSpaceVOById, onChange: value => { inspection.value = value } })
  const enabled = () => COMPANION_UI_ENABLED && !!user.currentUser?.id
  function cancelDrag() { drag.clear(); dragging.value = false }
  function close() { inspector.clear(); cancelDrag() }
  function reset() { close(); destination.value = null }
  watch(() => user.currentUser?.id, reset, { flush: 'sync' })
  function open(pictureId = null) {
    if (!enabled()) return
    inspector.clear()
    requestNumber.value += 1
    if (pictureId !== null) inspector.inspect(pictureId, user.currentUser.id)
  }
  function startDrag(event, pictureId) {
    if (!enabled() || !event.dataTransfer) return
    const ticket = drag.start(pictureId, user.currentUser.id)
    if (!ticket) { event.preventDefault(); return }
    event.dataTransfer.clearData()
    event.dataTransfer.setData(PICTURE_DRAG_TYPE, ticket)
    event.dataTransfer.effectAllowed = 'copy'
    dragging.value = true
  }
  function canDrop(event) { return enabled() && drag.accepts(event.dataTransfer?.types, user.currentUser.id) }
  function dragOver(event) {
    if (!canDrop(event)) return
    event.preventDefault()
    event.dataTransfer.dropEffect = 'copy'
  }
  function drop(event) {
    // Even unsupported drops must not navigate the browser to a local file/URL.
    event.preventDefault()
    if (!canDrop(event)) return
    const id = drag.take(event.dataTransfer.getData(PICTURE_DRAG_TYPE), user.currentUser.id)
    cancelDrag()
    if (id) open(id)
  }
  function navigate(target, pictureId = null) {
    if (enabled()) destination.value = { target, pictureId, actor: String(user.currentUser.id) }
  }
  function takeDestination() { const value = destination.value; destination.value = null; return value }
  return { requestNumber, inspection, destination, dragging, open, close, reset, startDrag, cancelDrag, dragOver, drop, navigate, takeDestination }
})
