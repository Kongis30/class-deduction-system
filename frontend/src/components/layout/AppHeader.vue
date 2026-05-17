<template>
  <header class="app-header">
    <div class="app-header__left">
      <el-button text class="app-header__toggle" @click="appStore.toggleSidebar">
        <el-icon><Fold /></el-icon>
      </el-button>

      <div>
        <h1 class="app-header__title">{{ pageTitle }}</h1>
        <p class="app-header__breadcrumb">{{ sectionTitle }}</p>
      </div>
    </div>

    <div class="app-header__right">
      <el-input placeholder="Search students, instructors, classrooms, or packages" class="app-header__search" />
      <el-tag type="success" effect="light">{{ APP_TIMEZONE_LABEL }} timezone</el-tag>
      <span>{{ authStore.displayName }}</span>
      <el-avatar :size="34">{{ authStore.userInitials }}</el-avatar>
      <el-button text @click="handleSignOut">Sign out</el-button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { Fold } from '@element-plus/icons-vue';
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { APP_TIMEZONE_LABEL } from '@/config/app-config';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();
const authStore = useAuthStore();

const pageTitle = computed(() => String(route.meta.title ?? 'Dashboard'));
const sectionTitle = computed(() => String(route.meta.section ?? 'Admin'));

async function handleSignOut() {
  await authStore.signOut();
  await router.push('/login');
}
</script>
