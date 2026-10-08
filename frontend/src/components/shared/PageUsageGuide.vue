<template>
  <div class="page-usage-guide no-print">
    <el-popover
      placement="bottom-end"
      trigger="click"
      :width="460"
      :teleported="true"
      popper-class="page-usage-guide__popover"
    >
      <template #reference>
        <el-button class="page-usage-guide__trigger" aria-label="How to use this page">
          <el-icon><QuestionFilled /></el-icon>
          <span class="page-usage-guide__label">How to use</span>
        </el-button>
      </template>

      <section class="page-usage-guide__content">
        <span>{{ guide.eyebrow }}</span>
        <strong>{{ guide.title }}</strong>
        <p>{{ guide.description }}</p>

        <ol>
          <li v-for="step in guide.steps" :key="step">{{ step }}</li>
        </ol>

        <div class="page-usage-guide__actions">
          <el-button
            v-for="action in guide.actions"
            :key="action.label"
            size="small"
            plain
            type="primary"
            @click="go(action.to)"
          >
            {{ action.label }}
          </el-button>
        </div>
      </section>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue';
import { computed } from 'vue';
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router';

import { getPageUsageGuide } from '@/utils/page-usage-guides';

const route = useRoute();
const router = useRouter();
const guide = computed(() => getPageUsageGuide(route.name, route.meta.title));

function go(to: RouteLocationRaw) {
  router.push(to);
}
</script>

<style scoped>
.page-usage-guide {
  display: inline-flex;
}

.page-usage-guide__trigger {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 700;
  box-shadow: 0 8px 22px rgba(37, 99, 235, 0.12);
}

.page-usage-guide__content {
  display: grid;
  gap: 8px;
  color: #334155;
}

.page-usage-guide__content span {
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.page-usage-guide__content strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.4;
}

.page-usage-guide__content p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.page-usage-guide__content ol {
  display: grid;
  gap: 7px;
  margin: 2px 0 0;
  padding-left: 18px;
}

.page-usage-guide__content li {
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.page-usage-guide__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding-top: 4px;
  border-top: 1px solid #e2e8f0;
}

.page-usage-guide__actions :deep(.el-button) {
  margin-left: 0;
}

@media (max-width: 720px) {
  .page-usage-guide__trigger {
    padding: 8px 10px;
  }
}

@media (max-width: 640px) {
  .page-usage-guide__trigger {
    width: 44px;
    height: 44px;
    padding: 0;
  }

  .page-usage-guide__label {
    display: none;
  }
}
</style>
