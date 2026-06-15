<template>
  <div id="app-root">
    <NavBar />
    <main>
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import NavBar from '@/components/NavBar.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
userStore.fetchCurrentUser()
</script>

<style scoped>
#app-root { min-height: 100vh; display: flex; flex-direction: column; }
main { flex: 1; }
</style>
