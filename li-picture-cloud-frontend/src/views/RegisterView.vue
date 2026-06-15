<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <h1>注册</h1>
        <p>创建你的 LiPictureCloud 账号</p>
      </div>
      <form @submit.prevent="handleRegister" class="auth-form">
        <div v-if="error" class="form-error">{{ error }}</div>
        <div v-if="success" class="form-success">
          注册成功！正在跳转登录…
        </div>
        <label class="field">
          <span>账号</span>
          <input v-model="form.userAccount" class="input" type="text" placeholder="至少 4 位字符" required />
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="form.userPassword" class="input" type="password" placeholder="至少 8 位字符" required />
        </label>
        <label class="field">
          <span>确认密码</span>
          <input v-model="form.checkPassword" class="input" type="password" placeholder="再次输入密码" required />
        </label>
        <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
          {{ loading ? '注册中…' : '注册' }}
        </button>
      </form>
      <p class="auth-footer">
        已有账号？
        <router-link to="/login">去登录 &rarr;</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/user'

const router = useRouter()
const form = reactive({ userAccount: '', userPassword: '', checkPassword: '' })
const error = ref('')
const success = ref(false)
const loading = ref(false)

async function handleRegister() {
  error.value = ''
  if (!form.userAccount || !form.userPassword || !form.checkPassword) {
    error.value = '请填写所有字段'
    return
  }
  if (form.userAccount.length < 4) {
    error.value = '账号至少 4 位'
    return
  }
  if (form.userPassword.length < 8) {
    error.value = '密码至少 8 位'
    return
  }
  if (form.userPassword !== form.checkPassword) {
    error.value = '两次密码不一致'
    return
  }
  loading.value = true
  try {
    await userRegister({
      userAccount: form.userAccount,
      userPassword: form.userPassword,
      checkPassword: form.checkPassword
    })
    success.value = true
    setTimeout(() => router.push('/login'), 1500)
  } catch (e) {
    error.value = e.message || '注册失败'
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
.form-success {
  padding: 0.75rem 1rem; background: #EFF9F0; color: #1A7A2E;
  font-size: 0.875rem; font-weight: 500;
}
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field span { font-size: 0.8125rem; font-weight: 600; letter-spacing: 0.02em; }
.btn-full { width: 100%; }
.auth-footer { text-align: center; margin-top: 2rem; font-size: 0.875rem; color: var(--gray-600); }
.auth-footer a { font-weight: 600; text-decoration: underline; }
.auth-footer a:hover { color: var(--red); }
</style>
