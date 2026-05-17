<template>
  <div class="page-stack">
    <!-- 页面主体为表格，工具栏一行搞定：左侧标题 + 右侧操作按钮 -->
    <section class="workspace-panel">
      <div class="workspace-panel__header">
        <span class="workspace-panel__title">{{ schema.tableTitle }}</span>
        <div class="workspace-panel__actions">
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

      <el-form class="workspace-filters" :model="filterModel" inline>
        <el-form-item
          v-for="field in schema.filters"
          :key="field.key"
          :label="field.label"
          class="workspace-filters__item"
        >
          <el-input
            v-if="field.type === 'input'"
            v-model="filterModel[field.key]"
            :placeholder="field.placeholder"
            clearable
            style="width: 180px;"
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
        </el-form-item>
      </el-form>

      <!-- 表格主体，无右侧边栏，撑满宽度 -->
      <div class="workspace-content">
        <el-table :data="filteredRows" row-key="id" class="workspace-table" :show-empty="true">
          <el-table-column
            v-for="column in schema.columns"
            :key="column.key"
            :prop="column.key"
            :label="column.label"
            :min-width="column.minWidth"
            :width="column.width"
            :align="column.align ?? 'left'"
          >
            <template #default="{ row }">
              <template v-if="isStackCell(row[column.key])">
                <div class="table-stack">
                  <span class="table-stack__primary">{{ row[column.key].primary }}</span>
                  <span v-if="row[column.key].secondary" class="table-stack__secondary">{{ row[column.key].secondary }}</span>
                </div>
              </template>

              <template v-else-if="isTagCell(row[column.key])">
                <el-tag size="small" :type="row[column.key].tone">{{ row[column.key].text }}</el-tag>
              </template>

              <template v-else-if="isTagListCell(row[column.key])">
                <div class="table-tag-list">
                  <el-tag
                    v-for="tag in row[column.key].items"
                    :key="tag.text"
                    size="small"
                    :type="tag.tone"
                  >
                    {{ tag.text }}
                  </el-tag>
                </div>
              </template>

              <template v-else>
                {{ row[column.key] ?? '-' }}
              </template>
            </template>
          </el-table-column>
        </el-table>

        <div class="workspace-table__footer">
          <span>Showing {{ filteredRows.length }} record(s)</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, reactive } from 'vue';
import type {
  ListWorkspaceSchema,
  WorkspaceAction,
  WorkspaceCellValue,
  WorkspaceFilterField,
  WorkspaceStackCell,
  WorkspaceTableRow,
  WorkspaceTagCell,
  WorkspaceTagListCell,
} from '@/modules/shared/workspace-types';

type FilterModel = Record<string, string | string[]>;

const props = defineProps<{
  schema: ListWorkspaceSchema;
}>();

const filterModel = reactive<FilterModel>(buildInitialFilterModel(props.schema.filters));

const filteredRows = computed(() =>
  props.schema.rows.filter((row) => matchesAllFilters(row, filterModel, props.schema.filters)),
);

// Build a shared empty-state filter model for all list pages.
function buildInitialFilterModel(filters: WorkspaceFilterField[]): FilterModel {
  return filters.reduce<FilterModel>((model, filter) => {
    model[filter.key] = filter.type === 'date-range' ? [] : '';
    return model;
  }, {});
}

// The current list pages are mock-only, so lightweight local filtering is enough for preview work.
function matchesAllFilters(
  row: WorkspaceTableRow,
  model: FilterModel,
  filters: WorkspaceFilterField[],
): boolean {
  return filters.every((filter) => {
    const filterValue = model[filter.key];

    if (Array.isArray(filterValue)) {
      return filterValue.length === 0;
    }

    if (!filterValue) {
      return true;
    }

    const cellText = extractSearchText(row[filter.key]).toLowerCase();
    return cellText.includes(filterValue.toLowerCase());
  });
}

// Flatten complex cell content so local filtering can search it consistently.
function extractSearchText(value: WorkspaceCellValue | string | number | undefined): string {
  if (value === undefined) {
    return '';
  }

  if (typeof value === 'string' || typeof value === 'number') {
    return String(value);
  }

  if (isTagCell(value)) {
    return value.text;
  }

  if (isStackCell(value)) {
    return `${value.primary} ${value.secondary ?? ''}`.trim();
  }

  if (isTagListCell(value)) {
    return value.items.map((item) => item.text).join(' ');
  }

  return '';
}

// Top action buttons stay in preview mode until the real workflows are implemented.
function handleActionClick(action: WorkspaceAction) {
  ElMessage.info(`${action.label} will be connected after the final page flow is approved.`);
}

// Cell type guard for single tags.
function isTagCell(value: WorkspaceCellValue | string | number | undefined): value is WorkspaceTagCell {
  return typeof value === 'object' && value !== null && 'kind' in value && value.kind === 'tag';
}

// Cell type guard for stacked primary and secondary text.
function isStackCell(value: WorkspaceCellValue | string | number | undefined): value is WorkspaceStackCell {
  return typeof value === 'object' && value !== null && 'kind' in value && value.kind === 'stack';
}

// Cell type guard for tag collections.
function isTagListCell(
  value: WorkspaceCellValue | string | number | undefined,
): value is WorkspaceTagListCell {
  return typeof value === 'object' && value !== null && 'kind' in value && value.kind === 'tag-list';
}
</script>

<style scoped>
.workspace-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

/* 工具栏：标题 + 按钮行，不换行 */
.workspace-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.workspace-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.workspace-panel__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 筛选区：inline 行式布局，标签和输入框并排 */
.workspace-filters {
  margin-bottom: 12px;
}

.workspace-filters__item {
  margin-right: 16px;
  margin-bottom: 0;
}

.workspace-filters__item .el-form-item__label {
  color: #606266;
  font-size: 13px;
}

/* 表格区：无右侧边栏，全宽 */
.workspace-content {
  min-width: 0;
}

.workspace-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.table-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.table-stack__primary {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.table-stack__secondary {
  color: #909399;
  font-size: 12px;
}

.table-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

/* 表格底部 */
.workspace-table__footer {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  text-align: right;
}
</style>
