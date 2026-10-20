<template>
  <nav
    ref="navElement"
    v-show="!isKeyboardOpen"
    class="mobile-tab-bar"
    aria-label="Primary navigation. Swipe or drag horizontally to see more pages."
    @pointerdown="onDragStart"
    @pointermove="onDragMove"
    @pointerup="onDragEnd"
    @pointercancel="onDragEnd"
    @click.capture="blockClickAfterDrag"
  >
    <RouterLink
      v-for="item in navigationItems"
      :key="item.key"
      :to="item.path"
      class="mobile-tab-bar__item"
      :class="{ 'mobile-tab-bar__item--active': isActive(item.path) }"
      :aria-current="isActive(item.path) ? 'page' : undefined"
    >
      <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
      <span>{{ tabLabel(item) }}</span>
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { Calendar, DataAnalysis, Files, Money, Setting, Tickets, User, Van } from '@element-plus/icons-vue';
import { RouterLink, useRoute } from 'vue-router';

import type { AppMenuItem } from '@/types/menu';
import { useAuthStore } from '@/stores/auth';
import { useMobileKeyboardState } from '@/composables/useMobileKeyboardState';
import { getMenusForUser, getSecondaryMenusForUser } from '@/utils/menu';

const route = useRoute();
const authStore = useAuthStore();
const { isKeyboardOpen } = useMobileKeyboardState();
const navElement = ref<HTMLElement | null>(null);

const iconMap = { Calendar, DataAnalysis, Files, Money, Setting, Tickets, User, Van };
const navigationItems = computed(() => {
  const primaryItems = getMenusForUser(authStore.currentUser);
  const secondaryItems = getSecondaryMenusForUser(authStore.currentUser).flatMap(group =>
    (group.children ?? []).map(item => ({ ...item, icon: item.icon ?? group.icon })),
  );
  const seenPaths = new Set<string>();

  return [...primaryItems, ...secondaryItems].filter((item): item is AppMenuItem & { path: string } => {
    if (!item.path || seenPaths.has(item.path)) return false;
    seenPaths.add(item.path);
    return true;
  });
});

let dragPointerId: number | null = null;
let dragStartX = 0;
let dragStartScrollLeft = 0;
let isDragging = false;
let suppressClick = false;

function resolveIcon(icon?: string) {
  return icon && icon in iconMap ? iconMap[icon as keyof typeof iconMap] : Tickets;
}

function tabLabel(item: AppMenuItem) {
  const shortLabels: Record<string, string> = {
    'calendar': 'Calendar',
    'one-on-one-deduction': '1-on-1',
    'data-summary': 'Summary',
    'pickup-dropoff-list': 'Pickup list',
    'student-credit': 'Credit',
    'daily-registrations': 'Daily reg.',
    'admin-guide': 'Guide',
    'config-people': 'People',
    'enrollment-orders': 'Enrollments',
    'course-packages': 'Packages',
    'receipt-history': 'Receipts',
    'course-products': 'Products',
    'course-prices': 'Prices',
    'schedule-rules': 'Schedule',
    'config-teacher-pay-rates': 'Pay rates',
    'teacher-payments': 'Payroll',
    'config-payment-methods': 'Methods',
    'config-receipt': 'Brand',
    'config-campuses': 'Campuses',
    'config-notifications': 'Alerts',
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

function onDragStart(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || event.button !== 0 || !navElement.value) return;
  dragPointerId = event.pointerId;
  dragStartX = event.clientX;
  dragStartScrollLeft = navElement.value.scrollLeft;
  isDragging = false;
  suppressClick = false;
}

function onDragMove(event: PointerEvent) {
  const nav = navElement.value;
  if (!nav || event.pointerId !== dragPointerId) return;

  const distance = event.clientX - dragStartX;
  if (!isDragging && Math.abs(distance) < 5) return;

  if (!isDragging) {
    isDragging = true;
    nav.setPointerCapture(event.pointerId);
    nav.classList.add('mobile-tab-bar--dragging');
  }
  nav.scrollLeft = dragStartScrollLeft - distance;
  event.preventDefault();
}

function onDragEnd(event: PointerEvent) {
  const nav = navElement.value;
  if (event.pointerId !== dragPointerId) return;
  if (isDragging) {
    suppressClick = true;
    nav?.classList.remove('mobile-tab-bar--dragging');
  }
  dragPointerId = null;
  isDragging = false;
}

function blockClickAfterDrag(event: MouseEvent) {
  if (!suppressClick) return;
  event.preventDefault();
  event.stopPropagation();
  suppressClick = false;
}

async function scrollActiveItemIntoView() {
  await nextTick();
  const nav = navElement.value;
  const activeItem = nav?.querySelector<HTMLElement>('[aria-current="page"]');
  if (!nav || !activeItem) return;

  const left = activeItem.offsetLeft - (nav.clientWidth - activeItem.offsetWidth) / 2;
  nav.scrollTo({ left: Math.max(0, left), behavior: 'smooth' });
}

onMounted(() => void scrollActiveItemIntoView());
watch(() => route.path, () => void scrollActiveItemIntoView());
</script>

<style scoped>
.mobile-tab-bar {
  position: fixed;
  z-index: 900;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  gap: 4px;
  overflow-x: auto;
  overflow-y: hidden;
  min-height: calc(60px + env(safe-area-inset-bottom));
  padding: 4px 8px env(safe-area-inset-bottom);
  border-top: 1px solid #e4e7ec;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 -6px 20px rgb(16 42 67 / 7%);
  backdrop-filter: blur(16px);
  overscroll-behavior-x: contain;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-x;
  cursor: grab;
}

.mobile-tab-bar::-webkit-scrollbar {
  display: none;
}

.mobile-tab-bar__item {
  display: flex;
  flex: 0 0 76px;
  min-width: 76px;
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
  user-select: none;
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

.mobile-tab-bar--dragging {
  cursor: grabbing;
  user-select: none;
}
</style>
