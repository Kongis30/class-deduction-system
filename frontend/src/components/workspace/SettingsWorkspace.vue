<template>
  <div class="page-stack">
    <section class="settings-panel">
      <div class="settings-panel__header">
        <span class="settings-panel__title">{{ schema.tableTitle }}</span>
        <div class="settings-panel__actions">
          <el-button
            v-for="action in schema.actions"
            :key="action.label"
            :type="action.buttonType"
            :plain="action.plain"
            @click="handleActionClick(action)"
          >
            {{ action.label }}
          </el-button>
        </div>
      </div>

      <div class="settings-content">
        <article v-for="section in schema.sections" :key="section.title" class="settings-card">
          <div class="settings-card__header">
            <h3>{{ section.title }}</h3>
            <p>{{ section.description }}</p>
          </div>

          <div class="settings-grid">
            <div v-for="field in section.fields" :key="`${section.title}-${field.label}`" class="settings-field">
              <span>{{ field.label }}</span>
              <strong>{{ field.value }}</strong>
              <small v-if="field.hint">{{ field.hint }}</small>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';

import type { SettingsWorkspaceSchema, WorkspaceAction } from '@/modules/shared/workspace-types';

defineProps<{
  schema: SettingsWorkspaceSchema;
}>();

function handleActionClick(action: WorkspaceAction) {
  ElMessage.success(`${action.label} is ready in the UI and can be connected to the save flow next.`);
}
</script>

<style scoped>
.settings-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.settings-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.settings-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.settings-panel__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.settings-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.settings-card {
  border: 1px solid #e8edf2;
  border-radius: 6px;
  background: #fff;
  padding: 18px;
}

.settings-card__header {
  margin-bottom: 14px;
}

.settings-card__header h3 {
  margin: 0 0 4px;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.settings-card__header p {
  margin: 0;
  color: #909399;
  font-size: 12px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.settings-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-radius: 6px;
  background: #f8fafc;
  padding: 12px;
}

.settings-field span {
  color: #909399;
  font-size: 12px;
}

.settings-field strong {
  color: #303133;
  font-size: 14px;
}

.settings-field small {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 920px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
