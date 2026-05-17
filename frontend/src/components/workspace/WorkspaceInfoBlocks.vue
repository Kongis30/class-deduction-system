<template>
  <div class="info-blocks">
    <article v-for="block in blocks" :key="block.title" class="info-card">
      <div class="info-card__header">
        <h3>{{ block.title }}</h3>
        <p v-if="block.description">{{ block.description }}</p>
      </div>

      <div class="info-card__list">
        <div v-for="item in block.items" :key="`${block.title}-${item.label}`" class="info-card__row">
          <span>{{ item.label }}</span>
          <strong v-if="!item.tone">{{ item.value }}</strong>
          <el-tag v-else size="small" :type="item.tone">{{ item.value }}</el-tag>
        </div>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import type { WorkspaceInfoBlock } from '@/modules/shared/workspace-types';

defineProps<{
  blocks: WorkspaceInfoBlock[];
}>();
</script>

<style scoped>
.info-blocks {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  border: 1px solid rgba(217, 226, 236, 0.92);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  padding: 22px;
  box-shadow: 0 18px 40px rgba(15, 23, 36, 0.08);
}

.info-card__header h3 {
  margin: 0;
  color: #102a43;
  font-size: 18px;
}

.info-card__header p {
  margin: 8px 0 0;
  color: #7b8794;
  font-size: 13px;
  line-height: 1.6;
}

.info-card__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.info-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.info-card__row span {
  color: #486581;
  font-size: 13px;
}

.info-card__row strong {
  color: #102a43;
  font-size: 14px;
  text-align: right;
}
</style>
