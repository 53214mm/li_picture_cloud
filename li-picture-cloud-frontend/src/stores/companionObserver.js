import { defineStore } from 'pinia'
import { shallowRef, watch } from 'vue'
import { useUserStore } from './user'
import { observationApi } from '@/api/companionObservation'
import { createCompanionObserver } from '@/presentation/companionObserver'
import { collectSafeLayout } from '@/presentation/safeVisualLayout'
export const useCompanionObserverStore = defineStore('companionObserver', () => {
  const user = useUserStore(); let routeName = null
  const state = shallowRef(null)
  const collect = visual => routeName ? collectSafeLayout(routeName, visual) : { page: 'UNKNOWN', pictureIds: [], rects: [] }
  const coordinator = createCompanionObserver({ api: observationApi, collect, onChange: value => { state.value = value } })
  state.value = coordinator.state()
  watch(() => user.currentUser?.id, id => coordinator.setOwner(id), { immediate: true, flush: 'sync' })
  const update = () => coordinator.update(collect(!!state.value.options?.visionLimit))
  function page(name) { routeName = name; update() }
  return { state, coordinator, page, update }
})
