<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <h1>登录</h1>
        <p>欢迎回到 LiPictureCloud</p>
      </div>
      <form @submit.prevent="handleLogin" class="auth-form">
        <div v-if="error" class="form-error">{{ error }}</div>
        <label class="field">
          <span>账号</span>
          <input v-model="form.userAccount" class="input" type="text" placeholder="输入账号" required />
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="form.userPassword" class="input" type="password" placeholder="输入密码" required />
        </label>
        <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
      <p class="auth-footer">
        还没有账号？
        <router-link to="/register">立即注册 &rarr;</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const form = reactive({ userAccount: '', userPassword: '' })
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  if (!form.userAccount || !form.userPassword) {
    error.value = '请填写账号和密码'
    return
  }
  loading.value = true
  try {
    await userStore.login({ userAccount: form.userAccount, userPassword: form.userPassword })
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: calc(100vh - 4rem);
  display: flex; align-items: center; justify-content: center;
  background: var(--gray-100);
}
.auth-card {
  width: 100%; max-width: 440px;
  background: var(--white);
  border: 2px solid var(--black);
  padding: 3rem 2.5rem;
}
.auth-header { margin-bottom: 2rem; }
.auth-header h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.04em; }
.auth-header p { color: var(--gray-600); margin-top: 0.25rem; }
.auth-form { display: flex; flex-direction: column; gap: 1.25rem; }
.form-error {
  padding: 0.75rem 1rem; background: #FFF0EF; color: var(--red);
  font-size: 0.875rem; font-weight: 500;
}
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field span { font-size: 0.8125rem; font-weight: 600; letter-spacing: 0.02em; }
.btn-full { width: 100%; }
.auth-footer { text-align: center; margin-top: 2rem; font-size: 0.875rem; color: var(--gray-600); }
.auth-footer a { font-weight: 600; text-decoration: underline; }
.auth-footer a:hover { color: var(--red); }
</style>
