<template>
  <nav class="navbar" aria-label="主导航">
    <div class="container nav-inner">
      <router-link to="/" class="logo" @click="closeMobileNav">
        <span class="logo-mark">●</span> LiPictureCloud
      </router-link>

      <div class="nav-links">
        <template v-for="item in desktopItems" :key="item.to || item.action">
          <router-link
            v-if="item.to"
            :to="item.to"
            :class="desktopLinkClass(item)"
          >{{ item.label }}</router-link>
          <button v-else class="nav-btn" @click="handleNavigationItem(item)">{{ item.label }}</button>
        </template>
        <span v-if="userStore.isLoggedIn" class="nav-user">{{ userStore.currentUser?.userName }}</span>
      </div>

      <button
        ref="menuButton"
        class="mobile-menu-btn"
        type="button"
        aria-label="打开导航菜单"
        aria-controls="mobile-navigation"
        :aria-expanded="mobileOpen"
        @click="openMobileNav"
      >
        <span></span><span></span><span></span>
      </button>
    </div>

    <div
      v-if="mobileOpen"
      class="mobile-nav-overlay"
      tabindex="-1"
      @click.self="closeMobileNav({ restoreFocus: true })"
      @keydown.esc="closeMobileNav({ restoreFocus: true })"
    >
      <aside id="mobile-navigation" class="mobile-nav-drawer" aria-label="移动端主导航">
        <div class="mobile-nav-header">
          <div>
            <span class="mobile-nav-kicker">LiPictureCloud</span>
            <strong>{{ userStore.isLoggedIn ? (userStore.currentUser?.userName || '已登录用户') : '访客' }}</strong>
          </div>
          <button ref="closeButton" class="mobile-nav-close" type="button" aria-label="关闭导航菜单" @click="closeMobileNav({ restoreFocus: true })">×</button>
        </div>

        <section v-for="group in navigationGroups" :key="group.id" class="mobile-nav-group">
          <h2>{{ group.label }}</h2>
          <template v-for="item in group.items" :key="item.to || item.action">
            <router-link
              v-if="item.to"
              :to="item.to"
              class="mobile-nav-item"
              @click="closeMobileNav({ restoreFocus: true })"
            >
              <span>{{ item.label }}</span><span aria-hidden="true">→</span>
            </router-link>
            <button
              v-else
              type="button"
              class="mobile-nav-item mobile-nav-action"
              :class="{ danger: item.danger }"
              @click="handleNavigationItem(item)"
            >{{ item.label }}</button>
          </template>
        </section>
      </aside>
    </div>
  </nav>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { buildNavigationGroups } from '@/constants/navigation'
import { COMPANION_UI_ENABLED } from '@/config/features'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const mobileOpen = ref(false)
const menuButton = ref(null)
const closeButton = ref(null)
let previousBodyOverflow = ''

const navigationGroups = computed(() => buildNavigationGroups({
  isLoggedIn: userStore.isLoggedIn,
  isAdmin: userStore.isAdmin,
  companionEnabled: COMPANION_UI_ENABLED
}))
const desktopItems = computed(() => navigationGroups.value.flatMap(group => group.items))

function desktopLinkClass(item) {
  if (!item.to || userStore.isLoggedIn) return undefined
  return item.to === '/register' ? 'btn btn-primary btn-sm' : 'btn btn-outline btn-sm'
}

function openMobileNav() {
  mobileOpen.value = true
  nextTick(() => closeButton.value?.focus())
}

function closeMobileNav({ restoreFocus = false } = {}) {
  if (!mobileOpen.value) return
  mobileOpen.value = false
  if (restoreFocus) nextTick(() => menuButton.value?.focus())
}

async function handleNavigationItem(item) {
  if (item.action === 'logout') await handleLogout()
  closeMobileNav({ restoreFocus: true })
}

async function handleLogout() {
  await userStore.logout()
  await router.push('/')
}

watch(mobileOpen, (open) => {
  if (open) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = previousBodyOverflow
  }
})

watch(() => route.fullPath, () => closeMobileNav())

function handleViewportChange(event) {
  if (event.matches) closeMobileNav()
}

const desktopMedia = window.matchMedia('(min-width: 1024px)')
desktopMedia.addEventListener('change', handleViewportChange)

onBeforeUnmount(() => {
  desktopMedia.removeEventListener('change', handleViewportChange)
  document.body.style.overflow = previousBodyOverflow
})
</script>

<style scoped>
.navbar {
  position: sticky; top: 0; z-index: 100;
  background: var(--white);
  border-bottom: 2px solid var(--black);
}
.nav-inner {
  display: flex; align-items: center; justify-content: space-between;
  min-height: 4rem;
}
.logo {
  font-size: 1.25rem; font-weight: 700; letter-spacing: -0.02em;
  display: flex; align-items: center; gap: 0.5rem;
}
.logo-mark { color: var(--red); font-size: 0.625rem; }
.nav-links { display: flex; align-items: center; gap: 1.5rem; font-size: 0.875rem; font-weight: 500; }
.nav-links a:hover { color: var(--red); }
.nav-user { color: var(--gray-600); order: 2; }
.nav-btn { font-size: 0.875rem; font-weight: 500; color: var(--gray-600); order: 3; }
.nav-btn:hover { color: var(--red); }
.btn-sm { padding: 0.5rem 1.25rem; font-size: 0.75rem; }

.mobile-menu-btn,
.mobile-nav-overlay { display: none; }

@media (max-width: 1023px) {
  .nav-inner { min-height: 3.75rem; }
  .logo { font-size: 1.0625rem; }
  .nav-links { display: none; }
  .mobile-menu-btn {
    display: flex; width: 44px; height: 44px; padding: 11px;
    flex-direction: column; justify-content: space-around;
    border: 2px solid var(--black); background: var(--white);
  }
  .mobile-menu-btn span { display: block; width: 100%; height: 2px; background: var(--black); }
  .mobile-nav-overlay {
    position: fixed; inset: 0; z-index: 200; display: flex; justify-content: flex-end;
    background: rgba(10, 10, 10, 0.55);
  }
  .mobile-nav-drawer {
    width: min(86vw, 24rem); height: 100dvh; overflow-y: auto;
    padding: max(1rem, env(safe-area-inset-top)) max(1rem, env(safe-area-inset-right)) max(1rem, env(safe-area-inset-bottom)) 1rem;
    background: var(--white); border-left: 2px solid var(--black);
  }
  .mobile-nav-header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding-bottom: 1rem; border-bottom: 2px solid var(--black); }
  .mobile-nav-header strong { display: block; overflow-wrap: anywhere; }
  .mobile-nav-kicker { display: block; color: var(--gray-400); font-size: 0.6875rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; }
  .mobile-nav-close { width: 44px; height: 44px; flex: 0 0 44px; border: 2px solid var(--black); font-size: 1.5rem; line-height: 1; }
  .mobile-nav-group { margin-top: 1.25rem; }
  .mobile-nav-group h2 { margin-bottom: 0.375rem; color: var(--gray-400); font-size: 0.6875rem; letter-spacing: 0.1em; text-transform: uppercase; }
  .mobile-nav-item {
    display: flex; width: 100%; min-height: 48px; align-items: center; justify-content: space-between;
    padding: 0.625rem 0.75rem; border-bottom: 1px solid var(--gray-200);
    font-size: 1rem; font-weight: 600; text-align: left;
  }
  .mobile-nav-item.router-link-active { background: var(--black); color: var(--white); }
  .mobile-nav-action.danger { margin-top: 0.5rem; border: 2px solid var(--red); color: var(--red); }
}

@media (min-width: 768px) and (max-width: 1023px) {
  .mobile-nav-drawer { width: min(62vw, 28rem); }
}
</style>
