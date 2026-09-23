<template>
  <div class="app-shell">
    <AppSidebar />
    <div class="app-shell__main">
      <AppHeader />
      <main class="app-shell__content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app';

import AppHeader from './AppHeader.vue';
import AppSidebar from './AppSidebar.vue';

useAppStore().initializePreferences();
</script>
