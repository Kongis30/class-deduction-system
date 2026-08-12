<template>
  <div class="data-summary-page" v-loading="loading">
    <div class="workbook-scroll" data-testid="data-summary-workbook">
      <table class="workbook-table">
        <colgroup>
          <col v-for="column in columns" :key="column.key" :style="{ width: `${column.width}px` }" />
        </colgroup>

        <thead>
          <tr>
            <th
              v-for="column in columns"
              :key="column.key"
              :class="[
                `workbook-column--${column.key}`,
                { 'workbook-header--active': hasColumnFilter(column.key) },
              ]"
            >
              <el-popover
                placement="bottom-start"
                trigger="click"
                :width="column.filterType === 'date' ? 330 : column.filterType === 'number' ? 300 : 260"
                popper-class="workbook-filter-popper"
              >
                <template #reference>
                  <button
                    class="workbook-header-button"
                    type="button"
                    :aria-label="`Filter ${column.label}`"
                  >
                    <span>{{ column.label }}</span>
                    <span class="workbook-header-icons">
                      <span v-if="sortState.key === column.key" class="workbook-sort-indicator">
                        {{ sortState.direction === 'asc' ? '↑' : '↓' }}
                      </span>
                      <span class="workbook-filter-arrow" aria-hidden="true" />
                    </span>
                  </button>
                </template>

                <div class="workbook-filter">
                  <strong>Filter {{ column.label }}</strong>

                  <el-input
                    v-if="column.filterType === 'text'"
                    v-model="columnFilters[column.key].text"
                    clearable
                    :placeholder="`Search ${column.label}`"
                  />

                  <el-select
                    v-else-if="column.filterType === 'select'"
                    v-model="columnFilters[column.key].selected"
                    multiple
                    clearable
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="All"
                  >
                    <el-option
                      v-for="option in selectOptions[column.key]"
                      :key="option"
                      :label="option"
                      :value="option"
                    />
                  </el-select>

                  <el-date-picker
                    v-else-if="column.filterType === 'date'"
                    v-model="columnFilters[column.key].dateRange"
                    type="daterange"
                    start-placeholder="From"
                    end-placeholder="To"
                    range-separator="-"
                    value-format="YYYY-MM-DD"
                    unlink-panels
                  />

                  <div v-else class="workbook-number-tools">
                    <div class="workbook-filter-section">
                      <span class="workbook-filter-label">Sort</span>
                      <el-button-group class="workbook-sort-buttons">
                        <el-button
                          size="small"
                          :type="isColumnSorted(column.key, 'asc') ? 'primary' : 'default'"
                          @click="setSort(column.key, 'asc')"
                        >
                          Asc
                        </el-button>
                        <el-button
                          size="small"
                          :type="isColumnSorted(column.key, 'desc') ? 'primary' : 'default'"
                          @click="setSort(column.key, 'desc')"
                        >
                          Desc
                        </el-button>
                        <el-button size="small" @click="clearSort(column.key)">Clear</el-button>
                      </el-button-group>
                    </div>

                    <div class="workbook-filter-section">
                      <span class="workbook-filter-label">Range</span>
                      <div class="workbook-number-filter">
                        <el-input-number
                          v-model="columnFilters[column.key].min"
                          :controls="false"
                          placeholder="Min"
                        />
                        <span>to</span>
                        <el-input-number
                          v-model="columnFilters[column.key].max"
                          :controls="false"
                          placeholder="Max"
                        />
                      </div>
                    </div>
                  </div>

                  <div class="workbook-filter-actions">
                    <el-button text size="small" @click="clearColumnFilter(column.key)">Clear column</el-button>
                    <el-button text size="small" @click="clearAllFilters">Clear all</el-button>
                  </div>
                </div>
              </el-popover>
            </th>
          </tr>
        </thead>

        <tbody v-if="groupedRows.length">
          <template v-for="(group, groupIndex) in groupedRows" :key="group.label">
            <tr v-if="groupIndex > 0" class="workbook-group-spacer" aria-hidden="true">
              <td :colspan="columns.length" />
            </tr>
            <tr
              v-for="(row, rowIndex) in group.rows"
              :key="rowKey(row)"
              class="workbook-data-row"
            >
              <td class="workbook-column--class workbook-class-cell">
                {{ rowIndex === 0 ? group.label : '' }}
              </td>
              <td class="workbook-cell--center">{{ displayText(row.campusName) }}</td>
              <td class="workbook-column--studentName">{{ displayText(row.studentName) }}</td>
              <td>{{ displayText(row.school) }}</td>
              <td class="workbook-cell--center">{{ displayText(row.grade) }}</td>
              <td class="workbook-cell--center">{{ formatDate(row.signupDate) }}</td>
              <td>{{ displayText(row.phone) }}</td>
              <td>{{ displayText(row.phone2) }}</td>
              <td class="workbook-cell--center">
                <span :class="['workbook-pill', row.pickupRequired ? 'workbook-pill--yes' : 'workbook-pill--no']">
                  {{ row.pickupRequired ? 'Yes' : 'No' }}
                </span>
              </td>
              <td class="workbook-cell--center">
                <span :class="['workbook-pill', row.dropoffRequired ? 'workbook-pill--yes' : 'workbook-pill--no']">
                  {{ row.dropoffRequired ? 'Yes' : 'No' }}
                </span>
              </td>
              <td class="workbook-cell--center">
                <span :class="['workbook-pill', paymentMethodClass(row.paymentMethod)]">
                  {{ displayText(row.paymentMethod) }}
                </span>
              </td>
              <td class="workbook-cell--center">{{ formatPeriod(row) }}</td>
              <td class="workbook-cell--money">{{ formatMoney(row.linePriceAmount) }}</td>
              <td class="workbook-cell--money">{{ formatMoney(row.pickupFeeAmount) }}</td>
              <td class="workbook-cell--money">{{ formatMoney(row.discountAmount) }}</td>
              <td class="workbook-cell--money">{{ formatMoney(row.paidAmount) }}</td>
              <td
                class="workbook-cell--money"
                :class="{ 'workbook-cell--balance-due': Number(row.balanceAmount ?? 0) < 0 }"
              >
                {{ formatMoney(row.balanceAmount) }}
              </td>
            </tr>
          </template>
        </tbody>

        <tbody v-else-if="!loading">
          <tr class="workbook-empty-row">
            <td :colspan="columns.length">
              <span>No matching records.</span>
              <el-button v-if="hasAnyFilter" text type="primary" @click="clearAllFilters">
                Clear all filters
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import { fetchDataSummary, type DataSummaryRow } from '@/api/data-summary';
import { extractErrorMessage } from '@/api/http';

type ColumnKey =
  | 'class'
  | 'campus'
  | 'studentName'
  | 'school'
  | 'grade'
  | 'signupDate'
  | 'phone'
  | 'phone2'
  | 'pickup'
  | 'dropoff'
  | 'paymentMethod'
  | 'period'
  | 'tuition'
  | 'transportFee'
  | 'discount'
  | 'paid'
  | 'balance';

type FilterType = 'text' | 'select' | 'date' | 'number';

interface WorkbookColumn {
  key: ColumnKey;
  label: string;
  width: number;
  filterType: FilterType;
}

interface ColumnFilter {
  text: string;
  selected: string[];
  dateRange: [string, string] | null;
  min?: number;
  max?: number;
}

type SortDirection = 'asc' | 'desc';

interface SortState {
  key: ColumnKey | null;
  direction: SortDirection;
}

interface WorkbookGroup {
  label: string;
  rows: DataSummaryRow[];
}

const columns: WorkbookColumn[] = [
  { key: 'class', label: 'Class', width: 90, filterType: 'select' },
  { key: 'campus', label: 'Campus', width: 112, filterType: 'select' },
  { key: 'studentName', label: 'Student Name', width: 150, filterType: 'text' },
  { key: 'school', label: 'School', width: 110, filterType: 'text' },
  { key: 'grade', label: 'Grade', width: 76, filterType: 'select' },
  { key: 'signupDate', label: 'Sign Up Date', width: 116, filterType: 'date' },
  { key: 'phone', label: 'Phone Number', width: 132, filterType: 'text' },
  { key: 'phone2', label: 'Phone Number #2', width: 140, filterType: 'text' },
  { key: 'pickup', label: 'Pick up', width: 84, filterType: 'select' },
  { key: 'dropoff', label: 'Drop off', width: 86, filterType: 'select' },
  { key: 'paymentMethod', label: 'Pay Method', width: 108, filterType: 'select' },
  { key: 'period', label: 'Period', width: 176, filterType: 'date' },
  { key: 'tuition', label: 'Tuition', width: 104, filterType: 'number' },
  { key: 'transportFee', label: 'Pickup/Dropoff', width: 128, filterType: 'number' },
  { key: 'discount', label: 'Discount', width: 100, filterType: 'number' },
  { key: 'paid', label: 'Paid', width: 100, filterType: 'number' },
  { key: 'balance', label: 'Balance', width: 104, filterType: 'number' },
];

const loading = ref(false);
const rows = ref<DataSummaryRow[]>([]);
const sortState = reactive<SortState>({
  key: null,
  direction: 'asc',
});
const columnFilters = reactive<Record<ColumnKey, ColumnFilter>>(
  Object.fromEntries(columns.map((column) => [column.key, createEmptyFilter()])) as Record<ColumnKey, ColumnFilter>,
);

const selectOptions = computed<Record<ColumnKey, string[]>>(() => {
  return Object.fromEntries(columns.map((column) => {
    if (column.filterType !== 'select') {
      return [column.key, []];
    }
    const values = rows.value.map((row) => selectValue(row, column.key));
    return [column.key, [...new Set(values)].sort(naturalCompare)];
  })) as Record<ColumnKey, string[]>;
});

const filteredRows = computed(() => rows.value.filter((row) => {
  return columns.every((column) => matchesColumnFilter(row, column));
}));

const sortedRows = computed(() => {
  const source = [...filteredRows.value];
  if (!sortState.key) {
    return source;
  }
  const direction = sortState.direction === 'asc' ? 1 : -1;
  return source.sort((left, right) => {
    const leftValue = sortValue(left, sortState.key!);
    const rightValue = sortValue(right, sortState.key!);
    if (leftValue == null && rightValue == null) return 0;
    if (leftValue == null) return 1;
    if (rightValue == null) return -1;
    if (typeof leftValue === 'number' && typeof rightValue === 'number') {
      return (leftValue - rightValue) * direction;
    }
    return naturalCompare(String(leftValue), String(rightValue)) * direction;
  });
});

const groupedRows = computed<WorkbookGroup[]>(() => {
  const groups = new Map<string, DataSummaryRow[]>();
  sortedRows.value.forEach((row) => {
    const label = classLabel(row);
    const group = groups.get(label) ?? [];
    group.push(row);
    groups.set(label, group);
  });
  return [...groups.entries()].map(([label, groupRows]) => ({ label, rows: groupRows }));
});

const hasAnyFilter = computed(() => columns.some((column) => hasColumnFilter(column.key)));

function createEmptyFilter(): ColumnFilter {
  return {
    text: '',
    selected: [],
    dateRange: null,
    min: undefined,
    max: undefined,
  };
}

async function loadSummary() {
  loading.value = true;
  try {
    const response = await fetchDataSummary();
    rows.value = response.data.data.rows ?? [];
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

function matchesColumnFilter(row: DataSummaryRow, column: WorkbookColumn) {
  const filter = columnFilters[column.key];
  if (column.filterType === 'text') {
    const keyword = filter.text.trim().toLowerCase();
    return !keyword || String(columnValue(row, column.key) ?? '').toLowerCase().includes(keyword);
  }
  if (column.filterType === 'select') {
    return filter.selected.length === 0 || filter.selected.includes(selectValue(row, column.key));
  }
  if (column.filterType === 'date') {
    return matchesDateRange(row, column.key, filter.dateRange);
  }

  const value = Number(columnValue(row, column.key) ?? 0);
  return (filter.min == null || value >= filter.min) && (filter.max == null || value <= filter.max);
}

function matchesDateRange(
  row: DataSummaryRow,
  key: ColumnKey,
  range: [string, string] | null,
) {
  if (!range) {
    return true;
  }
  if (key === 'period') {
    const start = row.periodStartDate || row.periodEndDate;
    const end = row.periodEndDate || row.periodStartDate;
    return Boolean(start && end && start <= range[1] && end >= range[0]);
  }
  const value = String(columnValue(row, key) ?? '');
  return Boolean(value && value >= range[0] && value <= range[1]);
}

function columnValue(row: DataSummaryRow, key: ColumnKey): string | number | undefined {
  switch (key) {
    case 'class':
      return classLabel(row);
    case 'campus':
      return row.campusName;
    case 'studentName':
      return row.studentName;
    case 'school':
      return row.school;
    case 'grade':
      return row.grade;
    case 'signupDate':
      return row.signupDate;
    case 'phone':
      return row.phone;
    case 'phone2':
      return row.phone2;
    case 'pickup':
      return row.pickupRequired ? 'Yes' : 'No';
    case 'dropoff':
      return row.dropoffRequired ? 'Yes' : 'No';
    case 'paymentMethod':
      return displayText(row.paymentMethod);
    case 'period':
      return row.periodStartDate;
    case 'tuition':
      return Number(row.linePriceAmount ?? 0);
    case 'transportFee':
      return Number(row.pickupFeeAmount ?? 0);
    case 'discount':
      return Number(row.discountAmount ?? 0);
    case 'paid':
      return Number(row.paidAmount ?? 0);
    case 'balance':
      return Number(row.balanceAmount ?? 0);
  }
}

function selectValue(row: DataSummaryRow, key: ColumnKey) {
  return String(columnValue(row, key) ?? '—');
}

function hasColumnFilter(key: ColumnKey) {
  const filter = columnFilters[key];
  return Boolean(
    filter.text.trim()
    || filter.selected.length
    || filter.dateRange
    || filter.min != null
    || filter.max != null,
  );
}

function clearColumnFilter(key: ColumnKey) {
  Object.assign(columnFilters[key], createEmptyFilter());
  clearSort(key);
}

function clearAllFilters() {
  columns.forEach((column) => clearColumnFilter(column.key));
  sortState.key = null;
}

function setSort(key: ColumnKey, direction: SortDirection) {
  sortState.key = key;
  sortState.direction = direction;
}

function clearSort(key?: ColumnKey) {
  if (!key || sortState.key === key) {
    sortState.key = null;
  }
}

function isColumnSorted(key: ColumnKey, direction: SortDirection) {
  return sortState.key === key && sortState.direction === direction;
}

function sortValue(row: DataSummaryRow, key: ColumnKey) {
  const value = columnValue(row, key);
  if (value == null || value === '') {
    return undefined;
  }
  return value;
}

function classLabel(row: DataSummaryRow) {
  return row.className || row.registerClass || row.courseProductName || 'Unassigned';
}

function rowKey(row: DataSummaryRow) {
  return [row.orderId, row.orderItemId ?? row.classId ?? row.courseProductId ?? row.enrollmentNo].join('-');
}

function displayText(value?: string) {
  return value?.trim() || '—';
}

function formatDate(value?: string) {
  return value ? dayjs(value).format('M/D/YYYY') : '—';
}

function formatPeriod(row: DataSummaryRow) {
  const start = row.periodStartDate ? dayjs(row.periodStartDate).format('M/D/YY') : '—';
  const end = row.periodEndDate ? dayjs(row.periodEndDate).format('M/D/YY') : '—';
  return `${start} - ${end}`;
}

function formatMoney(value?: number) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));
}

function paymentMethodClass(value?: string) {
  const method = value?.trim().toLowerCase() ?? '';
  if (!method) return '';
  if (method.includes('cash')) return 'workbook-pill--cash';
  if (method.includes('zelle')) return 'workbook-pill--zelle';
  if (method.includes('check')) return 'workbook-pill--check';
  return 'workbook-pill--method';
}

function naturalCompare(left: string, right: string) {
  return left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' });
}

onMounted(loadSummary);
</script>

<style scoped>
.data-summary-page {
  min-width: 0;
  height: calc(100vh - 88px);
  background: #fff;
}

.workbook-scroll {
  width: 100%;
  height: 100%;
  overflow: auto;
  border: 1px solid #9eafa7;
  background: #fff;
}

.workbook-table {
  width: max-content;
  min-width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
  color: #263b44;
  font-size: 12px;
}

.workbook-table th,
.workbook-table td {
  height: 30px;
  padding: 5px 8px;
  overflow: hidden;
  border-right: 1px solid #c3d3df;
  border-bottom: 1px solid #c3d3df;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbook-table th {
  position: sticky;
  z-index: 4;
  top: 0;
  height: 36px;
  padding: 0;
  border-color: #517768;
  background: #376451;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.workbook-table th:last-child,
.workbook-table td:last-child {
  border-right: 0;
}

.workbook-header-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  width: 100%;
  height: 100%;
  padding: 5px 7px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.workbook-header-button:hover,
.workbook-header--active .workbook-header-button {
  background: #2d5544;
}

.workbook-header-icons {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
}

.workbook-sort-indicator {
  color: #ffd95a;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.workbook-header--active .workbook-filter-arrow {
  border-top-color: #ffd95a;
}

.workbook-filter-arrow {
  flex: 0 0 auto;
  width: 0;
  height: 0;
  border-top: 5px solid #d9ebe3;
  border-right: 4px solid transparent;
  border-left: 4px solid transparent;
}

.workbook-filter {
  display: grid;
  gap: 12px;
}

.workbook-filter > strong {
  color: #303133;
  font-size: 13px;
}

.workbook-filter :deep(.el-select),
.workbook-filter :deep(.el-date-editor) {
  width: 100%;
}

.workbook-number-tools {
  display: grid;
  gap: 12px;
}

.workbook-filter-section {
  display: grid;
  gap: 6px;
}

.workbook-filter-label {
  color: #606266;
  font-size: 12px;
  font-weight: 600;
}

.workbook-sort-buttons {
  width: 100%;
}

.workbook-sort-buttons :deep(.el-button) {
  flex: 1;
}

.workbook-number-filter {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  color: #909399;
  font-size: 12px;
}

.workbook-number-filter :deep(.el-input-number) {
  width: 100%;
}

.workbook-filter-actions {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid #ebeef5;
  padding-top: 6px;
}

.workbook-data-row td {
  background: #dcebf8;
}

.workbook-data-row:hover td {
  background: #d1e5f6;
}

.workbook-group-spacer td {
  height: 18px;
  padding: 0;
  border-right: 0;
  border-bottom: 1px solid #9eafa7;
  background: #fff;
}

.workbook-class-cell {
  font-weight: 700;
  vertical-align: top;
}

.workbook-cell--center {
  text-align: center;
}

.workbook-cell--money {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.workbook-cell--balance-due {
  color: #f21b2d;
  font-weight: 700;
}

.workbook-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 18px;
  padding: 0 7px;
  border-radius: 9px;
  background: #e9eef2;
  color: #50636f;
  font-size: 10px;
  font-weight: 700;
  line-height: 18px;
}

.workbook-pill--yes,
.workbook-pill--cash {
  background: #2d78ca;
  color: #fff;
}

.workbook-pill--no,
.workbook-pill--zelle {
  background: #e41f2b;
  color: #fff;
}

.workbook-pill--check {
  background: #f8d155;
  color: #775600;
}

.workbook-pill--method {
  background: #528f79;
  color: #fff;
}

.workbook-column--class {
  position: sticky;
  z-index: 2;
  left: 0;
}

th.workbook-column--class {
  z-index: 6;
}

.workbook-column--studentName {
  position: sticky;
  z-index: 2;
  left: 90px;
  box-shadow: 2px 0 0 rgb(81 119 104 / 25%);
}

th.workbook-column--studentName {
  z-index: 6;
}

.workbook-empty-row td {
  height: 160px;
  background: #f7fbfe;
  color: #606266;
  text-align: center;
}

.workbook-empty-row span {
  margin-right: 8px;
}

@media (max-width: 720px) {
  .data-summary-page {
    height: calc(100vh - 76px);
  }
}
</style>
