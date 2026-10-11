import { defineStore } from 'pinia'
import { shallowRef, watch } from 'vue'
import { useUserStore } from './user'
import { createRehearsalHistory } from '@/presentation/rehearsalHistory'
export const useCompanionRehearsalStore = defineStore('companionRehearsal', () => {
  const user = useUserStore()
  const records = shallowRef([])
  const journal = createRehearsalHistory({ onChange: rows => { records.value = rows } })
  watch(() => user.currentUser?.id, id => journal.ownerChanged(id), { immediate: true, flush: 'sync' })
  return { records, journal }
})
