import { computed, shallowRef, watch } from 'vue'
import { defineStore } from 'pinia'
import { useUserStore } from './user'
import { COMPANION_UI_ENABLED } from '@/config/features'
import { mapCompanionPresentation } from '@/presentation/companionPresentation'
import { createPresentationChannel } from '@/presentation/companionPresentationChannel'

// Observation cache only. Existing components retain ownership of business IO.
export const useCompanionPresentationStore = defineStore('companionPresentation', () => {
  const user = useUserStore()
  const observation = shallowRef({ homeStatus: 'unobserved' })
  const channel = createPresentationChannel(snapshot => { observation.value = snapshot })
  watch(() => user.currentUser?.id, () => channel.reset(), { flush: 'sync' })
  const presentation = computed(() => mapCompanionPresentation({ ...observation.value, enabled: COMPANION_UI_ENABLED }))
  return { presentation, acquireSource: channel.acquire }
})
