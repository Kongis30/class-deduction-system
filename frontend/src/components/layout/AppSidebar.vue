<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': appStore.sidebarCollapsed }">
    <div class="sidebar__brand">
      <img v-if="!appStore.sidebarCollapsed" :src="logoUrl" alt="logo" class="sidebar__logo-img" />
      <div v-else class="sidebar__logo sidebar__logo--collapsed">J</div>
      <div v-if="!appStore.sidebarCollapsed" class="sidebar__brand-copy">
        <strong>{{ APP_SHORT_NAME }}</strong>
        <span>JIN BEST TUTORING</span>
      </div>
    </div>

    <div class="sidebar__menu-area">
      <el-menu
        class="sidebar__daily-menu"
        :default-active="route.path"
        :collapse="appStore.sidebarCollapsed"
        background-color="transparent"
        text-color="rgba(255,255,255,0.78)"
        active-text-color="#ffffff"
        unique-opened
        router
      >
        <template v-for="menu in resolvedMenus" :key="menu.key">
          <el-menu-item v-if="menu.path" :index="menu.path">
            <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
            <span>{{ menu.title }}</span>
          </el-menu-item>

          <el-sub-menu v-else :index="menu.key">
            <template #title>
              <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>

            <el-menu-item
              v-for="child in menu.children"
              :key="child.key"
              :index="child.path ?? child.key"
            >
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </div>

    <div class="sidebar__footer">
      <el-popover placement="right-end" :width="280" trigger="click">
        <template #reference>
          <button type="button" class="sidebar__settings-button" aria-label="Open settings">
            <el-icon><Setting /></el-icon>
            <span>Settings</span>
          </button>
        </template>
        <div class="sidebar__settings-panel">
          <div class="sidebar__settings-heading">
            <strong>Display settings</strong>
            <span>Adjust the text size across the system.</span>
          </div>
          <el-radio-group
            :model-value="appStore.fontSize"
            class="sidebar__font-size-options"
            @update:model-value="appStore.setFontSize"
          >
            <el-radio-button value="small">Small</el-radio-button>
            <el-radio-button value="medium">Default</el-radio-button>
            <el-radio-button value="large">Large</el-radio-button>
          </el-radio-group>
        </div>
      </el-popover>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Calendar, DataAnalysis, Files, Money, Setting, Tickets, Van } from '@element-plus/icons-vue';
import { useRoute } from 'vue-router';

import { APP_SHORT_NAME } from '@/config/app-config';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { getMenusForUser } from '@/utils/menu';

const appStore = useAppStore();
const route = useRoute();
const authStore = useAuthStore();
const logoUrl = `${import.meta.env.BASE_URL}logo.jpg`;

const resolvedMenus = computed(() => getMenusForUser(authStore.currentUser));

const iconMap = {
  Files,
  Tickets,
  Calendar,
  DataAnalysis,
  Money,
  Van,
};

function resolveIcon(icon?: keyof typeof iconMap) {
  return icon ? iconMap[icon] : Files;
}
</script>

<style scoped>
.sidebar__logo-img {
  width: 28px;
  height: 28px;
  object-fit: contain;
  border-radius: 4px;
}

.sidebar__logo--collapsed {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  color: #18b58a;
  font-size: 16px;
  font-weight: 700;
}

.sidebar__footer {
  flex-shrink: 0;
  margin-top: auto;
  padding: 12px 4px 2px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar__settings-button {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  padding: 10px 12px;
  color: rgba(255, 255, 255, 0.72);
  font: inherit;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.sidebar__settings-button:hover,
.sidebar__settings-button:focus-visible {
  outline: none;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.sidebar__settings-panel {
  display: grid;
  gap: 16px;
}

.sidebar__settings-heading {
  display: grid;
  gap: 3px;
}

.sidebar__settings-heading strong {
  color: #102a43;
  font-size: 14px;
}

.sidebar__settings-heading span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.sidebar__font-size-options {
  display: flex;
  width: 100%;
}

.sidebar__font-size-options :deep(.el-radio-button) {
  flex: 1;
}

.sidebar__font-size-options :deep(.el-radio-button__inner) {
  width: 100%;
  padding-right: 8px;
  padding-left: 8px;
}

</style>
