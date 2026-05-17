<template>
  <div class="page-stack">
    <section class="schedule-panel">
      <div class="schedule-panel__header">
        <span class="schedule-panel__title">{{ schema.tableTitle }}</span>
        <div class="schedule-panel__actions">
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

      <el-form class="schedule-filters" :model="filterModel" inline>
        <el-form-item
          v-for="field in schema.filters"
          :key="field.key"
          :label="field.label"
          class="schedule-filters__item"
        >
          <el-input
            v-if="field.type === 'input'"
            v-model="filterModel[field.key]"
            :placeholder="field.placeholder"
            clearable
            style="width: 160px;"
          />

          <el-select
            v-else-if="field.type === 'select'"
            v-model="filterModel[field.key]"
            clearable
            :placeholder="field.placeholder"
            style="width: 160px;"
          >
            <el-option
              v-for="option in field.options"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>

          <el-date-picker
            v-else-if="field.type === 'date-range'"
            v-model="filterModel[field.key]"
            type="daterange"
            range-separator="to"
            start-placeholder="Start"
            end-placeholder="End"
            value-format="YYYY-MM-DD"
            style="width: 240px;"
          />
        </el-form-item>
      </el-form>

      <div class="schedule-legend">
        <el-tag v-for="tag in schema.legend" :key="tag.text" size="small" :type="tag.tone">
          {{ tag.text }}
        </el-tag>
      </div>

      <div class="schedule-board">
        <article v-for="column in schema.boardColumns" :key="column.title" class="schedule-column">
          <div class="schedule-column__header">
            <h4>{{ column.title }}</h4>
            <span>{{ column.subtitle }}</span>
          </div>

          <div class="schedule-column__content">
            <div v-for="card in column.cards" :key="`${column.title}-${card.time}-${card.title}`" class="schedule-card">
              <div class="schedule-card__header">
                <strong>{{ card.time }}</strong>
                <el-tag size="small" :type="card.status.tone">{{ card.status.text }}</el-tag>
              </div>
              <h5>{{ card.title }}</h5>
              <p>{{ card.subtitle }}</p>
              <ul>
                <li v-for="detail in card.details" :key="detail">{{ detail }}</li>
              </ul>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { reactive } from 'vue';

import type {
  ScheduleWorkspaceSchema,
  WorkspaceAction,
  WorkspaceFilterField,
} from '@/modules/shared/workspace-types';

type FilterModel = Record<string, string | string[]>;

const props = defineProps<{
  schema: ScheduleWorkspaceSchema;
}>();

const filterModel = reactive<FilterModel>(buildInitialFilterModel(props.schema.filters));

function buildInitialFilterModel(filters: WorkspaceFilterField[]): FilterModel {
  return filters.reduce<FilterModel>((model, filter) => {
    model[filter.key] = filter.type === 'date-range' ? [] : '';
    return model;
  }, {});
}

function handleActionClick(action: WorkspaceAction) {
  ElMessage.info(`${action.label} will be connected after the scheduling flow is finalized.`);
}
</script>

<style scoped>
.schedule-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.schedule-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.schedule-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.schedule-panel__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.schedule-filters {
  margin-bottom: 12px;
}

.schedule-filters__item {
  margin-right: 16px;
  margin-bottom: 0;
}

.schedule-filters__item .el-form-item__label {
  color: #606266;
  font-size: 13px;
}

.schedule-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.schedule-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.schedule-column {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #f8fafc;
  padding: 14px;
}

.schedule-column__header {
  margin-bottom: 12px;
}

.schedule-column__header h4 {
  margin: 0;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.schedule-column__header span {
  display: inline-block;
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
}

.schedule-column__content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.schedule-card {
  border-radius: 6px;
  background: #ffffff;
  padding: 12px;
  border: 1px solid #ebeef5;
}

.schedule-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.schedule-card__header strong {
  color: #303133;
  font-size: 12px;
}

.schedule-card h5 {
  margin: 0 0 4px;
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.schedule-card p {
  margin: 0 0 8px;
  color: #606266;
  font-size: 12px;
}

.schedule-card ul {
  margin: 0;
  padding-left: 16px;
  color: #606266;
  font-size: 12px;
}

.schedule-card li + li {
  margin-top: 4px;
}

@media (max-width: 1280px) {
  .schedule-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .schedule-board {
    grid-template-columns: 1fr;
  }
}
</style>
