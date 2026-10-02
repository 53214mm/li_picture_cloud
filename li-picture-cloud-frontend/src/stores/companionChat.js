import { defineStore } from 'pinia'
import { shallowRef, watch } from 'vue'
import { useUserStore } from './user'
import { listCompanionChatHistory } from '@/api/companion'
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
  watch(() => user.currentUser?.id, () => session.reset(), { flush: 'sync' })
  function acquire() { return user.currentUser?.id ? session.acquire() : () => {} }
  return { state, acquire, setDraft: session.setDraft, send: session.send, reload: session.reload }
})
