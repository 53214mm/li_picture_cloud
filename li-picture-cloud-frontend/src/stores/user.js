import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCurrentUser, userLogin, userLogout } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const currentUser = ref(null)
  const isLoggedIn = computed(() => !!currentUser.value)
  const isAdmin = computed(() => currentUser.value?.userRole === 'admin')

  async function login(loginData) {
    const user = await userLogin(loginData)
    currentUser.value = user
    return user
  }

  async function logout() {
    await userLogout()
    currentUser.value = null
  }

  async function fetchCurrentUser() {
    try {
      const user = await getCurrentUser()
      currentUser.value = user
    } catch {
      currentUser.value = null
    }
  }

  return { currentUser, isLoggedIn, isAdmin, login, logout, fetchCurrentUser }
})
