<template>
  <section class="metric-grid">
    <article
      v-for="metric in metrics"
      :key="metric.label"
      class="metric-card"
      :class="metric.tone ? `metric-card--${metric.tone}` : ''"
    >
      <span>{{ metric.label }}</span>
      <strong>{{ metric.value }}</strong>
      <small>{{ metric.hint }}</small>
    </article>
  </section>
</template>

<script setup lang="ts">
import type { WorkspaceMetricItem } from '@/modules/shared/workspace-types';

defineProps<{
  metrics: WorkspaceMetricItem[];
}>();
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid rgba(217, 226, 236, 0.92);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
  padding: 22px;
  box-shadow: 0 18px 40px rgba(15, 23, 36, 0.08);
}

.metric-card span {
  color: #486581;
  font-size: 13px;
}

.metric-card strong {
  color: #102a43;
  font-size: 34px;
  line-height: 1;
}

.metric-card small {
  color: #7b8794;
}

.metric-card--success {
  border-color: rgba(24, 181, 138, 0.18);
}

.metric-card--warning {
  border-color: rgba(245, 158, 11, 0.22);
}

.metric-card--danger {
  border-color: rgba(239, 68, 68, 0.2);
}

.metric-card--info {
  border-color: rgba(14, 165, 233, 0.18);
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
