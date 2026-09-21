<template>
  <nav class="app-navigation" :class="{ 'app-navigation--compact': compact }" aria-label="工作区导航">
    <template v-for="group in groups" :key="group.id">
      <section v-if="group.to" class="nav-group">
        <router-link :to="group.to" class="nav-primary" :class="{ 'is-section': section === group.id }" :aria-current="route.path === group.to ? 'page' : undefined">
          <ShellIcon :name="group.id" /><span>{{ group.label }}</span>
        </router-link>
        <div v-if="group.items && !compact" class="nav-secondary">
          <router-link v-for="item in group.items" :key="item.to" :to="item.to" :aria-current="route.path === item.to ? 'page' : undefined">{{ item.label }}</router-link>
        </div>
      </section>
      <button v-else-if="compact" class="nav-primary" :class="{ 'is-section': section === group.id }" type="button" aria-haspopup="dialog" @click="emit('expand')">
        <ShellIcon :name="group.id" /><span>{{ group.label }}</span>
      </button>
      <section v-else class="nav-group nav-disclosure">
        <button type="button" class="nav-primary" :class="{ 'is-section': section === group.id }" :aria-expanded="!!expanded[group.id]" :aria-controls="`${navigationId}-${group.id}`" @click="expanded[group.id] = !expanded[group.id]">
          <ShellIcon :name="group.id" /><span>{{ group.label }}</span>
          <ShellIcon name="chevron" class="nav-chevron" :class="{ 'is-open': expanded[group.id] }" />
        </button>
        <div :id="`${navigationId}-${group.id}`" class="nav-collapse" :class="{ 'is-open': expanded[group.id] }" :inert="!expanded[group.id]" :aria-hidden="!expanded[group.id]">
          <div class="nav-clip">
            <div class="nav-secondary">
              <router-link v-for="item in group.items" :key="item.to" :to="item.to" :aria-current="route.path === item.to ? 'page' : undefined">{{ item.label }}</router-link>
            </div>
          </div>
        </div>
      </section>
    </template>
    <button v-if="compact" type="button" class="nav-primary" aria-haspopup="dialog" @click="emit('expand')"><ShellIcon name="more" /><span>更多</span></button>
  </nav>
</template>
<script setup>
import { computed, ref, watch, useId } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { COMPANION_UI_ENABLED } from '@/config/features'
import { buildAppNavigation } from '@/constants/navigation'
import ShellIcon from '@/components/shell/ShellIcon.vue'
defineProps({ compact: Boolean })
const emit = defineEmits(['expand'])
const route = useRoute()
const user = useUserStore()
const navigationId = useId()
const section = computed(() => route.meta.section)
const expanded = ref({})
// Every navigation opens its current section; a manual toggle remains respected until navigation.
watch(() => route.name, () => {
  expanded.value = section.value ? { [section.value]: true } : {}
}, { immediate: true })
const groups = computed(() => buildAppNavigation({ isAdmin: user.isAdmin, companionEnabled: COMPANION_UI_ENABLED }))
</script>
<style scoped>
.app-navigation { display: flex; flex-direction: column; gap: var(--lp-space-2); }
.nav-primary { display: flex; align-items: center; gap: var(--lp-space-3); width: 100%; min-height: 48px; padding: 12px; color: var(--lp-text-secondary); border-radius: var(--lp-radius-m); font-size: 0.875rem; font-weight: 500; text-align: left; }
.nav-primary, .nav-secondary a { transition: background-color var(--lp-dur-fast) var(--lp-ease-standard), color var(--lp-dur-fast) var(--lp-ease-standard); }
.nav-primary > span { flex: 1; }
.nav-primary:hover, .nav-secondary a:hover { background: var(--lp-bg-subtle); color: var(--lp-text-primary); }
.nav-primary.is-section { color: var(--lp-accent-strong); background: var(--lp-accent-soft); font-weight: 600; }
.nav-secondary { display: grid; gap: var(--lp-space-1); margin: var(--lp-space-1) 0 var(--lp-space-2) 22px; padding-left: 10px; border-left: 1px solid var(--lp-border); }
.nav-secondary a { position: relative; display: flex; align-items: center; padding: 10px 12px; min-height: 44px; font-size: 0.8125rem; color: var(--lp-text-secondary); border-radius: var(--lp-radius-s); }
.nav-secondary a[aria-current="page"] { color: var(--lp-accent-strong); font-weight: 600; background: var(--lp-bg-subtle); }
.nav-secondary a[aria-current="page"]::before { content: ''; position: absolute; left: -12px; width: 3px; height: 16px; border-radius: var(--lp-radius-full); background: var(--lp-accent); }
.nav-chevron { width: 14px; height: 14px; transition: transform var(--lp-dur-base) var(--lp-ease-standard); }
.nav-chevron.is-open { transform: rotate(90deg); }
/* A tiny navigation disclosure needs a layout transition; never animate whole page dimensions. */
.nav-collapse { display: grid; grid-template-rows: 0fr; opacity: 0; visibility: hidden; transition: grid-template-rows var(--lp-dur-base) var(--lp-ease-standard), opacity var(--lp-dur-fast) var(--lp-ease-standard), visibility 0s var(--lp-dur-base); }
.nav-collapse.is-open { grid-template-rows: 1fr; opacity: 1; visibility: visible; transition-delay: 0s; }
.nav-clip { min-height: 0; overflow: hidden; }
.nav-disclosure { margin-top: var(--lp-space-1); }
.app-navigation--compact .nav-primary { flex-direction: column; gap: 6px; text-align: center; padding: 10px 4px; font-size: 0.75rem; }
@media (prefers-reduced-motion: reduce) {
  .nav-primary, .nav-secondary a, .nav-chevron, .nav-collapse { transition: none; }
}
</style>
