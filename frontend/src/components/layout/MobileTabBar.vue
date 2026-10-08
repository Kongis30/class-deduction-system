<template>
  <nav v-show="!isKeyboardOpen" class="mobile-tab-bar" :style="{ '--mobile-tab-count': primaryItems.length + 1 }" aria-label="Primary navigation">
    <RouterLink
      v-for="item in primaryItems"
      :key="item.key"
      :to="item.path"
      class="mobile-tab-bar__item"
      :class="{ 'mobile-tab-bar__item--active': isActive(item.path) }"
      :aria-current="isActive(item.path) ? 'page' : undefined"
    >
      <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
      <span>{{ tabLabel(item) }}</span>
    </RouterLink>

    <button
      type="button"
      class="mobile-tab-bar__item"
      :class="{ 'mobile-tab-bar__item--active': menuOpen }"
      :aria-expanded="menuOpen"
      aria-controls="app-mobile-navigation"
      @click="toggleMenu"
    >
      <el-icon><MoreFilled /></el-icon>
      <span>Menu</span>
    </button>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Calendar, DataAnalysis, MoreFilled, Money, Tickets, Van } from '@element-plus/icons-vue';
import { RouterLink, useRoute } from 'vue-router';

import type { AppMenuItem } from '@/types/menu';
import { getUserWorkspace } from '@/types/auth';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { useMobileKeyboardState } from '@/composables/useMobileKeyboardState';
import { dailyMenus, getMenusForUser } from '@/utils/menu';

const route = useRoute();
const appStore = useAppStore();
const authStore = useAuthStore();
const { isKeyboardOpen } = useMobileKeyboardState();

const iconMap = { Calendar, DataAnalysis, Money, Tickets, Van };
const primaryItems = computed(() => {
  const workspace = getUserWorkspace(authStore.currentUser);
  const menus = getMenusForUser(authStore.currentUser);
  if (workspace === 'TEACHER') return menus.filter(item => ['my-teacher-calendar', 'my-lessons', 'my-payroll'].includes(item.key));
  if (workspace === 'STUDENT') return menus.filter(item => item.key === 'my-student-calendar');
  const keys = ['registration', 'calendar', 'one-on-one-deduction', 'data-summary'];
  return keys.map(key => dailyMenus.find(item => item.key === key)).filter((item): item is AppMenuItem => Boolean(item?.path));
});
const menuOpen = computed(() => !appStore.sidebarCollapsed);

function resolveIcon(icon?: string) {
  return icon && icon in iconMap ? iconMap[icon as keyof typeof iconMap] : Tickets;
}

function tabLabel(item: AppMenuItem) {
  const shortLabels: Record<string, string> = {
    'calendar': 'Calendar',
    'one-on-one-deduction': '1-on-1',
    'data-summary': 'Summary',
    'my-teacher-calendar': 'Calendar',
    'my-lessons': 'Lessons',
    'my-payroll': 'Pay',
    'my-student-calendar': 'Calendar',
  };
  return shortLabels[item.key] ?? item.title;
}

function isActive(path?: string) {
  return Boolean(path && (route.path === path || route.path.startsWith(`${path}/`)));
}

function toggleMenu() {
  appStore.sidebarCollapsed = !menuOpen.value;
}
</script>

<style scoped>
.mobile-tab-bar {
  position: fixed;
  z-index: 900;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  grid-template-columns: repeat(var(--mobile-tab-count, 5), minmax(0, 1fr));
  min-height: calc(60px + env(safe-area-inset-bottom));
  padding: 4px 6px env(safe-area-inset-bottom);
  border-top: 1px solid #e4e7ec;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 -6px 20px rgb(16 42 67 / 7%);
  backdrop-filter: blur(16px);
}

.mobile-tab-bar__item {
  display: flex;
  min-width: 0;
  min-height: 48px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #667085;
  font: inherit;
  text-decoration: none;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.mobile-tab-bar__item .el-icon {
  font-size: 20px;
}

.mobile-tab-bar__item span {
  overflow: hidden;
  max-width: 100%;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-tab-bar__item--active {
  background: #eef5ff;
  color: #2878d0;
}
</style>
