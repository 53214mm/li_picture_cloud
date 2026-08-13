import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCurrentUser, userLogin, userLogout } from '@/api/user'
import {
  createAuthSessionGate,
  createSingleFlightLoader,
  isTerminalAuthFailure
} from '@/utils/authBootstrap'

export const useUserStore = defineStore('user', () => {
  const currentUser = ref(null)
  const authReady = ref(false)
  const authBootstrapError = ref(null)
  const isLoggedIn = computed(() => !!currentUser.value)
  const isAdmin = computed(() => currentUser.value?.userRole === 'admin')
  const loadCurrentUserOnce = createSingleFlightLoader(getCurrentUser)
  const authSessionGate = createAuthSessionGate()

  async function login(loginData) {
    const user = await userLogin(loginData)
    // 先推进会话代数，再写入登录结果；正在飞行的 bootstrap 不能覆盖它。
    authSessionGate.invalidate()
    currentUser.value = user
    authReady.value = true
    authBootstrapError.value = null
    return user
  }

  async function logout() {
    await userLogout()
    authSessionGate.invalidate()
    currentUser.value = null
    authReady.value = true
    authBootstrapError.value = null
  }

  async function ensureCurrentUser() {
    if (authReady.value) return currentUser.value
    const generation = authSessionGate.capture()
    try {
      const user = await loadCurrentUserOnce()
      if (!authSessionGate.isCurrent(generation)) return currentUser.value
      currentUser.value = user
      authReady.value = true
      authBootstrapError.value = null
      return user
    } catch (error) {
      if (!authSessionGate.isCurrent(generation)) return currentUser.value
      if (isTerminalAuthFailure(error)) {
        currentUser.value = null
        authReady.value = true
        authBootstrapError.value = null
        return null
      }
      // 网络/5xx 不等于“未登录”：保持未就绪状态并允许页面主动重试。
      authReady.value = false
      authBootstrapError.value = error
      throw error
    }
  }

  function fetchCurrentUser() {
    return ensureCurrentUser()
  }

  return {
    currentUser, authReady, authBootstrapError, isLoggedIn, isAdmin,
    login, logout, fetchCurrentUser, ensureCurrentUser
  }
})
