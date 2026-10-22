<template>
  <div
    class="app-shell"
    :class="{
      'app-shell--mobile': appStore.isMobileViewport,
      'app-shell--data-summary': route.name === 'data-summary',
      'app-shell--desktop-override': appStore.layoutPreference === 'desktop' && appStore.detectedMobileViewport,
    }"
  >
    <AppSidebar />
    <button
      v-if="appStore.isMobileViewport && !appStore.sidebarCollapsed"
      type="button"
      class="app-shell__nav-backdrop"
      aria-label="Close navigation"
      @click="appStore.sidebarCollapsed = true"
    />
    <div class="app-shell__main">
      <AppHeader />
      <main class="app-shell__content" :class="{ 'app-shell__content--one-on-one': appStore.isMobileViewport && route.name === 'one-on-one-deduction' }">
        <router-view />
      </main>
      <MobileTabBar v-if="appStore.isMobileViewport" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAppStore } from '@/stores/app';

import AppHeader from './AppHeader.vue';
import AppSidebar from './AppSidebar.vue';
import MobileTabBar from './MobileTabBar.vue';

const appStore = useAppStore();
const route = useRoute();
const mobileNavigationQuery = window.matchMedia('(max-width: 920px)');
const printQuery = window.matchMedia('print');
let isPrinting = false;

function updateViewportMode(event?: MediaQueryListEvent) {
  // 打印预览会暂时改变媒体查询宽度；此时切换移动布局会重建收据和签字表 DOM。
  if (isPrinting || printQuery.matches) return;
  appStore.setMobileViewport(event?.matches ?? mobileNavigationQuery.matches);
}

function beginPrint() {
  isPrinting = true;
}

function endPrint() {
  isPrinting = false;
  updateViewportMode();
}

appStore.initializePreferences();

watch(
  () => [appStore.layoutPreference, appStore.detectedMobileViewport] as const,
  ([preference, detectedMobile]) => {
    if (typeof document !== 'undefined') {
      document.body.classList.toggle('app-desktop-override', preference === 'desktop' && detectedMobile);
    }
  },
  { immediate: true },
);

onMounted(() => {
  mobileNavigationQuery.addEventListener('change', updateViewportMode);
  window.addEventListener('beforeprint', beginPrint);
  window.addEventListener('afterprint', endPrint);
});
onBeforeUnmount(() => {
  mobileNavigationQuery.removeEventListener('change', updateViewportMode);
  window.removeEventListener('beforeprint', beginPrint);
  window.removeEventListener('afterprint', endPrint);
  document.body.classList.remove('app-desktop-override');
});

watch(() => route.path, () => {
  if (appStore.isMobileViewport) appStore.sidebarCollapsed = true;
});
</script>

<style scoped>
.app-shell__nav-backdrop {
  position: fixed;
  inset: 0;
  z-index: 999;
  border: 0;
  background: rgb(15 23 42 / 42%);
}
</style>
