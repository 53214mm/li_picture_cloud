import { defineStore } from 'pinia'
import { ref, shallowRef, watch } from 'vue'
import { useUserStore } from './user'
import { clearCompanionChatHistory, listCompanionChatHistory, setCompanionChatMode } from '@/api/companion'
import { streamCompanionChat } from '@/utils/companion'
import { createCompanionChatSession } from '@/presentation/companionChatSession'

// One conversation per authenticated app instance; no persistence or background IO.
export const useCompanionChatStore = defineStore('companionChat', () => {
  const user = useUserStore()
  const state = shallowRef(null)
  const session = createCompanionChatSession({
    readHistory: ({ signal }) => listCompanionChatHistory(50, { signal }),
    stream: streamCompanionChat,
    onChange: value => { state.value = value }
  })
  // Chat mode (DEMO / MODEL) as last reported by the server; shared by Home and the drawer.
  const mode = shallowRef(null)
  const modeBusy = ref(false)
  const modeError = ref('')
  let modeGeneration = 0
  // The drawer may re-present the Home snapshot it reuses; that same object must
  // not undo a switch made after it was read. A fresh server read is a new object.
  let lastAdopted = null
  watch(() => user.currentUser?.id, () => {
    session.reset()
    modeGeneration += 1
    mode.value = null
    lastAdopted = null
    modeBusy.value = false
    modeError.value = ''
  }, { flush: 'sync' })
  function acquire() { return user.currentUser?.id ? session.acquire() : () => {} }
  /** Adopt a snapshot from a Home read unless a switch is in flight or it was seen already. */
  function adoptMode(view) {
    if (!view || typeof view.mode !== 'string' || modeBusy.value || view === lastAdopted) return
    lastAdopted = view
    mode.value = view
  }
  async function chooseMode(next) {
    if (modeBusy.value || state.value?.phase !== 'idle' || mode.value?.mode === next) return
    const cycle = ++modeGeneration
    modeBusy.value = true
    modeError.value = ''
    try {
      const view = await setCompanionChatMode(next)
      if (cycle === modeGeneration) mode.value = view
    } catch (failure) {
      if (cycle === modeGeneration) modeError.value = failure?.message || '切换失败，请稍后重试。'
    } finally {
      if (cycle === modeGeneration) modeBusy.value = false
    }
  }
  /** Delete the server history, then re-read it for any open panel. Refused while a reply is pending. */
  async function clearHistory() {
    if (state.value?.phase !== 'idle') return null
    const deleted = await clearCompanionChatHistory()
    await session.reload()
    return deleted
  }
  return { state, acquire, setDraft: session.setDraft, send: session.send, reload: session.reload,
    mode, modeBusy, modeError, adoptMode, chooseMode, clearHistory }
})
