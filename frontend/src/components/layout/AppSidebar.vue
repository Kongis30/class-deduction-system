<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': appStore.sidebarCollapsed }">
    <div class="sidebar__brand">
      <img v-if="!appStore.sidebarCollapsed" src="/logo.jpg" alt="logo" class="sidebar__logo-img" />
      <div v-else class="sidebar__logo sidebar__logo--collapsed">J</div>
      <div v-if="!appStore.sidebarCollapsed" class="sidebar__brand-copy">
        <strong>{{ APP_SHORT_NAME }}</strong>
        <span>JIN BEST TUTORING</span>
      </div>
    </div>

    <el-menu
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
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Calendar, DataAnalysis, Files, HomeFilled, Setting, Tickets } from '@element-plus/icons-vue';
import { useRoute } from 'vue-router';

import { APP_SHORT_NAME } from '@/config/app-config';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { getMenusForUser } from '@/utils/menu';

const appStore = useAppStore();
const route = useRoute();
const authStore = useAuthStore();

const resolvedMenus = computed(() => getMenusForUser(authStore.currentUser));

const iconMap = {
  HomeFilled,
  Files,
  Tickets,
  Calendar,
  DataAnalysis,
  Setting,
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
</style>
