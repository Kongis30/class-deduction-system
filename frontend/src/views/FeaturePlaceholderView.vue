<template>
  <div class="page-stack">
    <section class="placeholder-panel">
      <div class="placeholder-panel__header">
        <span class="placeholder-panel__title">{{ title }}</span>
        <span class="placeholder-panel__badge">Coming next</span>
      </div>
      <p class="placeholder-panel__desc">{{ description }}</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();

const title = computed(() => String(route.meta.title ?? 'Feature Page'));
const description = computed(() =>
  String(route.meta.description ?? 'This page will be built in a future iteration.'),
);
</script>

<style scoped>
.placeholder-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.placeholder-panel__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.placeholder-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.placeholder-panel__badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #f0f0f0;
  color: #909399;
}

.placeholder-panel__desc {
  margin: 0;
  font-size: 13px;
  color: #606266;
}
</style>
