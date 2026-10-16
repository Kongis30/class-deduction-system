<template>
  <div class="data-summary-page daily-workbook-page" v-loading="loading">
    <div class="workbook-toolbar">
      <div class="workbook-toolbar__month daily-workbook-toolbar__filters">
        <span class="workbook-toolbar__label">Saved date</span>
        <el-button-group class="daily-date-navigation">
          <el-button size="small" aria-label="Previous day" title="Previous day" @click="shiftSelectedDate(-1)">‹</el-button>
          <el-date-picker
            v-model="selectedDate"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            :editable="false"
            size="small"
            aria-label="Registration date"
            @change="loadReport"
          />
          <el-button size="small" :disabled="selectedDate === todayInNewYork()" @click="selectToday">Today</el-button>
          <el-button size="small" aria-label="Next day" title="Next day" @click="shiftSelectedDate(1)">›</el-button>
        </el-button-group>
        <el-select v-model="operatorUserId" clearable placeholder="All operators" class="operator-filter" size="small" @change="loadReport">
          <el-option
            v-for="operator in operatorOptions"
            :key="String(operator.operatorUserId ?? operator.operatorName)"
            :label="operator.operatorName || 'Operator not recorded'"
            :value="String(operator.operatorUserId ?? '')"
          />
        </el-select>
      </div>

      <div class="workbook-toolbar__stats daily-workbook-toolbar__stats">
        <span class="workbook-toolbar__stat"><em>Course records</em><strong>{{ filteredStats.courses }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Orders</em><strong>{{ filteredStats.orders }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Students</em><strong>{{ filteredStats.students }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Due</em><strong>{{ money(filteredStats.due) }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Collected</em><strong>{{ money(filteredStats.collected) }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Outstanding</em><strong>{{ money(filteredStats.outstanding) }}</strong></span>
      </div>

      <div class="workbook-toolbar__export daily-workbook-toolbar__actions">
        <el-button size="small" :disabled="!hasAnyColumnFilter" @click="clearAllFilters">Clear filters</el-button>
        <el-button size="small" type="primary" :disabled="filteredRows.length === 0" @click="exportExcel">Export Excel</el-button>
      </div>
    </div>

    <div class="daily-workbook-layout">
      <section class="daily-sheet operator-sheet">
        <div class="daily-sheet__caption">
          <strong>Operator totals</strong>
          <span>Registrations saved on {{ formatDay(selectedDate) }}.</span>
        </div>
        <div class="workbook-scroll daily-sheet__scroll daily-sheet__scroll--compact">
          <table class="workbook-table operator-table">
            <colgroup>
              <col style="width: 220px">
              <col style="width: 125px">
              <col style="width: 125px">
              <col style="width: 165px">
              <col style="width: 145px">
              <col style="width: 145px">
            </colgroup>
            <thead>
              <tr>
                <th>Handled by</th>
                <th>Students</th>
                <th>Orders</th>
                <th>Course records</th>
                <th>Amount due</th>
                <th>Collected</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="operator in visibleOperators" :key="String(operator.operatorUserId ?? operator.operatorName)">
                <td>{{ operator.operatorName || 'Operator not recorded' }}</td>
                <td class="workbook-cell--center">{{ operator.uniqueStudentCount }}</td>
                <td class="workbook-cell--center">{{ operator.orderCount }}</td>
                <td class="workbook-cell--center">{{ operator.courseRegistrationCount }}</td>
                <td class="workbook-cell--money">{{ money(operator.amountDue) }}</td>
                <td class="workbook-cell--money">{{ money(operator.amountCollected) }}</td>
              </tr>
              <tr v-if="visibleOperators.length === 0" class="workbook-empty-row">
                <td colspan="6">No operator totals for this date.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="appStore.isMobileViewport" class="daily-mobile-list daily-mobile-operators">
          <article v-for="operator in visibleOperators" :key="String(operator.operatorUserId ?? operator.operatorName)" class="daily-mobile-card">
            <div class="daily-mobile-card__header"><strong>{{ operator.operatorName || 'Operator not recorded' }}</strong><span>{{ operator.courseRegistrationCount }} course records</span></div>
            <div class="daily-mobile-card__grid">
              <div><span>Students</span><strong>{{ operator.uniqueStudentCount }}</strong></div>
              <div><span>Orders</span><strong>{{ operator.orderCount }}</strong></div>
              <div><span>Amount due</span><strong>{{ money(operator.amountDue) }}</strong></div>
              <div><span>Collected</span><strong>{{ money(operator.amountCollected) }}</strong></div>
            </div>
          </article>
          <el-empty v-if="!loading && visibleOperators.length === 0" description="No operator totals" />
        </div>
      </section>

      <section class="daily-sheet history-sheet">
        <div class="daily-sheet__caption">
          <strong>Registration history</strong>
          <span>{{ filteredRows.length }} course record(s) saved on {{ formatDay(selectedDate) }}, ordered by saved time</span>
        </div>
        <div class="workbook-scroll daily-sheet__scroll history-sheet__scroll" data-testid="daily-registration-workbook">
          <table class="workbook-table history-table">
            <colgroup>
              <col v-for="column in columns" :key="column.key" :style="{ width: `${column.width}px` }">
            </colgroup>
            <thead>
              <tr>
                <th
                  v-for="column in columns"
                  :key="column.key"
                  :class="{ 'workbook-header--active': hasColumnFilter(column.key) }"
                >
                  <el-popover
                    placement="bottom-start"
                    trigger="click"
                    :width="column.filterType === 'number' ? 300 : column.filterType === 'date' ? 330 : 260"
                    popper-class="workbook-filter-popper"
                  >
                    <template #reference>
                      <button type="button" class="workbook-header-button">
                        <span>{{ column.label }}</span>
                        <span class="workbook-header-icons">
                          <span v-if="sortState.key === column.key" class="workbook-sort-indicator">{{ sortState.direction === 'asc' ? '↑' : '↓' }}</span>
                          <span class="workbook-filter-arrow" />
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
                        <el-option v-for="option in selectOptions[column.key]" :key="option" :label="option" :value="option" />
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
                      <div v-else class="workbook-number-filter">
                        <el-input-number v-model="columnFilters[column.key].min" :controls="false" placeholder="Min" />
                        <span>to</span>
                        <el-input-number v-model="columnFilters[column.key].max" :controls="false" placeholder="Max" />
                      </div>

                      <div class="workbook-filter-section">
                        <span class="workbook-filter-label">Sort</span>
                        <el-button-group class="workbook-sort-buttons">
                          <el-button size="small" :type="isSorted(column.key, 'asc') ? 'primary' : 'default'" @click="setSort(column.key, 'asc')">Asc</el-button>
                          <el-button size="small" :type="isSorted(column.key, 'desc') ? 'primary' : 'default'" @click="setSort(column.key, 'desc')">Desc</el-button>
                          <el-button size="small" @click="clearSort(column.key)">Clear</el-button>
                        </el-button-group>
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
              <template v-for="group in groupedRows" :key="group.key">
                <tr class="workbook-group-spacer daily-date-group">
                  <td :colspan="columns.length" class="daily-date-group__cell">
                    {{ group.label }}
                    <span>{{ group.rows.length }} course record(s)</span>
                  </td>
                </tr>
                <tr v-for="row in group.rows" :key="rowKey(row)" class="workbook-data-row">
                  <td>{{ dateTime(row.savedAt) }}</td>
                  <td>{{ display(row.operatorName, 'Operator not recorded') }}</td>
                  <td>{{ display(row.studentName) }}</td>
                  <td>{{ display(row.courseName) }}</td>
                  <td class="workbook-cell--center">{{ display(row.grade) }}</td>
                  <td>{{ display(row.className) }}</td>
                  <td class="workbook-cell--center">
                    <span :class="['workbook-pill', paymentMethodClass(row.paymentMethod)]">{{ display(row.paymentMethod) }}</span>
                  </td>
                  <td class="workbook-cell--center">
                    <span :class="['workbook-pill', paymentStatusClass(row.paymentStatus)]">{{ display(row.paymentStatus) }}</span>
                  </td>
                  <td class="workbook-cell--money">{{ money(row.amountDue) }}</td>
                  <td class="workbook-cell--money">{{ money(row.amountCollected) }}</td>
                  <td class="workbook-cell--money" :class="{ 'daily-registration-outstanding': Number(row.outstandingAmount ?? 0) > 0 }">
                    {{ money(row.outstandingAmount) }}
                  </td>
                </tr>
              </template>
            </tbody>
            <tbody v-else-if="!loading">
              <tr class="workbook-empty-row">
                <td :colspan="columns.length">No registrations match the current filters.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="appStore.isMobileViewport" class="daily-mobile-list">
          <article v-for="row in filteredSortedRows" :key="rowKey(row)" class="daily-mobile-card">
            <div class="daily-mobile-card__header"><div><strong>{{ display(row.studentName) }}</strong><span>{{ display(row.courseName) }}</span></div><span>{{ dateTime(row.savedAt) }}</span></div>
            <div class="daily-mobile-card__meta">{{ display(row.className) }} · {{ display(row.grade) }} · {{ display(row.operatorName, 'Operator not recorded') }}</div>
            <div class="daily-mobile-card__grid">
              <div><span>Pay method</span><strong>{{ display(row.paymentMethod) }}</strong></div>
              <div><span>Status</span><strong>{{ display(row.paymentStatus) }}</strong></div>
              <div><span>Amount due</span><strong>{{ money(row.amountDue) }}</strong></div>
              <div><span>Collected</span><strong>{{ money(row.amountCollected) }}</strong></div>
              <div><span>Outstanding</span><strong :class="{ 'daily-registration-outstanding': Number(row.outstandingAmount ?? 0) > 0 }">{{ money(row.outstandingAmount) }}</strong></div>
            </div>
          </article>
          <el-empty v-if="!loading && filteredSortedRows.length === 0" description="No registrations match the current filters" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import * as XLSX from 'xlsx';

import { fetchDailyRegistrations, type DailyRegistrationOperatorSummary, type DailyRegistrationReport, type DailyRegistrationRow } from '@/api/daily-registrations';
import { extractErrorMessage } from '@/api/http';
import { useAppStore } from '@/stores/app';

type ColumnKey = 'savedAt' | 'studentName' | 'courseName' | 'className' | 'grade' | 'operatorName' | 'paymentMethod' | 'paymentStatus' | 'amountDue' | 'amountCollected' | 'outstandingAmount';
type FilterType = 'text' | 'select' | 'date' | 'number';
type Column = { key: ColumnKey; label: string; width: number; filterType: FilterType };
type ColumnFilter = { text: string; selected: string[]; dateRange: [string, string] | null; min: number | undefined; max: number | undefined };

const columns: Column[] = [
  { key: 'savedAt', label: 'Time (ET)', width: 165, filterType: 'date' },
  { key: 'operatorName', label: 'Handled by', width: 155, filterType: 'select' },
  { key: 'studentName', label: 'Student', width: 155, filterType: 'text' },
  { key: 'courseName', label: 'Course', width: 200, filterType: 'text' },
  { key: 'grade', label: 'Grade', width: 90, filterType: 'select' },
  { key: 'className', label: 'Class', width: 180, filterType: 'text' },
  { key: 'paymentMethod', label: 'Pay method', width: 125, filterType: 'select' },
  { key: 'paymentStatus', label: 'Payment status', width: 135, filterType: 'select' },
  { key: 'amountDue', label: 'Amount due', width: 120, filterType: 'number' },
  { key: 'amountCollected', label: 'Collected', width: 120, filterType: 'number' },
  { key: 'outstandingAmount', label: 'Outstanding', width: 125, filterType: 'number' },
];

const selectedDate = ref(todayInNewYork());
const appStore = useAppStore();
const operatorUserId = ref('');
const report = ref<DailyRegistrationReport>();
const operatorOptions = ref<DailyRegistrationOperatorSummary[]>([]);
const loading = ref(false);
let latestRequestId = 0;
const sortState = ref<{ key: ColumnKey; direction: 'asc' | 'desc' }>({ key: 'savedAt', direction: 'desc' });
const columnFilters = reactive<Record<ColumnKey, ColumnFilter>>({
  savedAt: emptyFilter(), studentName: emptyFilter(), courseName: emptyFilter(), className: emptyFilter(),
  grade: emptyFilter(), operatorName: emptyFilter(), paymentMethod: emptyFilter(), paymentStatus: emptyFilter(),
  amountDue: emptyFilter(), amountCollected: emptyFilter(), outstandingAmount: emptyFilter(),
});

const sourceRows = computed(() => report.value?.rows ?? []);
const hasAnyColumnFilter = computed(() => columns.some((column) => hasColumnFilter(column.key)));
const selectOptions = computed(() => Object.fromEntries(
  columns.filter((column) => column.filterType === 'select').map((column) => [
    column.key,
    [...new Set(sourceRows.value.map((row) => textValue(row[column.key])).filter(Boolean))].sort(naturalCompare),
  ]),
) as Partial<Record<ColumnKey, string[]>>);

const filteredRows = computed(() => sourceRows.value.filter(matchesColumnFilters));
const filteredSortedRows = computed(() => {
  const { key, direction } = sortState.value;
  return [...filteredRows.value].sort((left, right) => compareRows(left, right, key) * (direction === 'asc' ? 1 : -1));
});
const groupedRows = computed(() => {
  const groups = new Map<string, { key: string; label: string; rows: DailyRegistrationRow[] }>();
  for (const row of filteredSortedRows.value) {
    const day = savedDateKey(row.savedAt) || 'unknown';
    const group = groups.get(day) ?? { key: day, label: day === 'unknown' ? 'Date not recorded' : formatDay(day), rows: [] };
    group.rows.push(row);
    groups.set(day, group);
  }
  return [...groups.values()];
});
const visibleOperators = computed(() => {
  if (!operatorUserId.value) return report.value?.operators ?? [];
  return (report.value?.operators ?? []).filter((operator) => String(operator.operatorUserId ?? '') === operatorUserId.value);
});
const filteredStats = computed(() => {
  const rows = filteredRows.value;
  return {
    courses: rows.length,
    orders: new Set(rows.map((row) => String(row.orderId))).size,
    students: new Set(rows.map((row) => row.studentId == null ? '' : String(row.studentId)).filter(Boolean)).size,
    due: rows.reduce((sum, row) => sum + Number(row.amountDue ?? 0), 0),
    collected: rows.reduce((sum, row) => sum + Number(row.amountCollected ?? 0), 0),
    outstanding: rows.reduce((sum, row) => sum + Number(row.outstandingAmount ?? 0), 0),
  };
});

onMounted(loadReport);

async function loadReport() {
  if (!selectedDate.value) {
    ElMessage.warning('Choose a date.');
    return;
  }
  const requestId = ++latestRequestId;
  loading.value = true;
  try {
    const response = await fetchDailyRegistrations({
      fromDate: selectedDate.value,
      toDate: selectedDate.value,
      ...(operatorUserId.value ? { operatorUserId: operatorUserId.value } : {}),
    });
    if (requestId !== latestRequestId) return;
    report.value = response.data.data;
    if (!operatorUserId.value) operatorOptions.value = response.data.data.operators;
  } catch (error) {
    if (requestId === latestRequestId) ElMessage.error(extractErrorMessage(error, 'Failed to load registration history.'));
  } finally {
    if (requestId === latestRequestId) loading.value = false;
  }
}

function selectToday() {
  selectedDate.value = todayInNewYork();
  loadReport();
}

function shiftSelectedDate(offset: number) {
  const [year, month, day] = selectedDate.value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day + offset));
  selectedDate.value = date.toISOString().slice(0, 10);
  loadReport();
}

function matchesColumnFilters(row: DailyRegistrationRow) {
  return columns.every(({ key, filterType }) => {
    const filter = columnFilters[key];
    const value = row[key];
    if (filterType === 'text' && filter.text.trim()
      && !textValue(value).toLocaleLowerCase().includes(filter.text.trim().toLocaleLowerCase())) return false;
    if (filterType === 'select' && filter.selected.length && !filter.selected.includes(textValue(value))) return false;
    if (filterType === 'date' && filter.dateRange) {
      const date = savedDateKey(row.savedAt);
      if (date < filter.dateRange[0] || date > filter.dateRange[1]) return false;
    }
    if (filterType === 'number') {
      const number = Number(value ?? 0);
      if (filter.min != null && number < filter.min) return false;
      if (filter.max != null && number > filter.max) return false;
    }
    return true;
  });
}

function compareRows(left: DailyRegistrationRow, right: DailyRegistrationRow, key: ColumnKey) {
  const leftValue = left[key];
  const rightValue = right[key];
  if (typeof leftValue === 'number' || typeof rightValue === 'number') {
    return Number(leftValue ?? 0) - Number(rightValue ?? 0);
  }
  return textValue(leftValue).localeCompare(textValue(rightValue), undefined, { numeric: true, sensitivity: 'base' });
}

function textValue(value: unknown) {
  return value == null ? '' : String(value).trim();
}

function setSort(key: ColumnKey, direction: 'asc' | 'desc') {
  sortState.value = { key, direction };
}

function clearSort(key: ColumnKey) {
  if (sortState.value.key === key) sortState.value = { key: 'savedAt', direction: 'desc' };
}

function isSorted(key: ColumnKey, direction: 'asc' | 'desc') {
  return sortState.value.key === key && sortState.value.direction === direction;
}

function hasColumnFilter(key: ColumnKey) {
  const filter = columnFilters[key];
  return Boolean(filter.text || filter.selected.length || filter.dateRange || filter.min != null || filter.max != null);
}

function clearColumnFilter(key: ColumnKey) {
  Object.assign(columnFilters[key], emptyFilter());
}

function clearAllFilters() {
  columns.forEach((column) => clearColumnFilter(column.key));
}

function emptyFilter(): ColumnFilter {
  return { text: '', selected: [], dateRange: null, min: undefined, max: undefined };
}

function exportExcel() {
  if (filteredSortedRows.value.length === 0) {
    ElMessage.warning('No rows to export.');
    return;
  }
  const sheetRows: (string | number)[][] = [columns.map((column) => column.label)];
  for (const row of filteredSortedRows.value) {
    sheetRows.push(columns.map((column) => {
      const value = row[column.key];
      return ['amountDue', 'amountCollected', 'outstandingAmount'].includes(column.key)
        ? Number(value ?? 0)
        : column.key === 'savedAt' ? dateTime(row.savedAt) : textValue(value) || '—';
    }));
  }
  const sheet = XLSX.utils.aoa_to_sheet(sheetRows);
  [8, 9, 10].forEach((columnIndex) => {
    for (let rowIndex = 1; rowIndex < sheetRows.length; rowIndex += 1) {
      const cell = sheet[XLSX.utils.encode_cell({ r: rowIndex, c: columnIndex })];
      if (cell) cell.z = '$#,##0.00;-$#,##0.00';
    }
  });
  sheet['!cols'] = columns.map((column) => ({ wch: Math.max(10, Math.round(column.width / 7)) }));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, 'Daily Registrations');
  XLSX.writeFile(workbook, `Daily_Registrations_${selectedDate.value}.xlsx`);
}

function todayInNewYork() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/New_York', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

function dateTime(value?: string) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York', dateStyle: 'short', timeStyle: 'short',
  }).format(new Date(value));
}

function savedDateKey(value?: string) {
  if (!value) return '';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/New_York', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date(value));
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function formatDay(value: string) {
  return new Intl.DateTimeFormat('en-US', { timeZone: 'America/New_York', dateStyle: 'full' }).format(new Date(`${value}T12:00:00`));
}

function money(value?: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value ?? 0);
}

function display(value?: string, fallback = '—') {
  return value?.trim() || fallback;
}

function paymentMethodClass(value?: string) {
  const method = value?.trim().toLowerCase() ?? '';
  if (method.includes('cash')) return 'workbook-pill--cash';
  if (method.includes('zelle')) return 'workbook-pill--zelle';
  if (method.includes('check')) return 'workbook-pill--check';
  return method ? 'workbook-pill--method' : '';
}

function paymentStatusClass(value?: string) {
  const status = value?.trim().toUpperCase() ?? '';
  if (status === 'PAID') return 'workbook-pill--cash';
  if (status === 'REFUNDED') return 'daily-registration-pill--refunded';
  if (status === 'PARTIAL' || status === 'PARTIALLY_PAID') return 'workbook-pill--check';
  return 'workbook-pill--zelle';
}

function naturalCompare(left: string, right: string) {
  return left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' });
}

function rowKey(row: DailyRegistrationRow) {
  return `${row.orderId}:${row.orderItemId ?? 'order'}:${row.studentId ?? 'student'}`;
}
</script>

<style scoped>
.daily-workbook-page { min-width: 0; }
.daily-workbook-toolbar__filters { flex-wrap: wrap; }
.daily-date-navigation :deep(.el-date-editor) { width: 150px; }
.daily-date-navigation :deep(.el-button + .el-button),
.daily-date-navigation :deep(.el-button + .el-date-editor),
.daily-date-navigation :deep(.el-date-editor + .el-button) { margin-left: 0; }
.daily-workbook-toolbar__stats { flex-wrap: wrap; gap: 14px 20px; }
.daily-workbook-toolbar__stat,
.daily-workbook-toolbar__stat em { white-space: nowrap; }
.daily-workbook-toolbar__actions { margin-left: auto; }
.operator-filter { width: 165px; }

.daily-workbook-layout { display: flex; flex: 1; min-height: 0; flex-direction: column; }
.daily-sheet { min-width: 0; border: 1px solid #9eafa7; border-top: 0; background: #fff; }
.daily-sheet__caption { display: flex; align-items: baseline; gap: 12px; padding: 8px 12px; border-bottom: 1px solid #c3d3df; color: #244438; }
.daily-sheet__caption strong { font-size: 12px; text-transform: uppercase; letter-spacing: .035em; }
.daily-sheet__caption span { color: #6b7684; font-size: 11px; }
.daily-sheet__scroll { min-width: 0; overflow: auto; }
.daily-sheet__scroll--compact { max-height: 180px; }
.operator-table { min-width: 100%; }
.history-sheet { display: flex; min-height: 0; flex: 1; flex-direction: column; }
.history-sheet__scroll { flex: 1; min-height: 0; }
.daily-date-group td.daily-date-group__cell { height: 28px; padding: 5px 8px; border-right: 0; background: #edf4f1; color: #315b49; font-weight: 700; }
.daily-date-group span { margin-left: 10px; color: #76857f; font-size: 11px; font-weight: 500; }
.daily-registration-outstanding { color: #bd2431; font-weight: 700; }
.daily-registration-pill--refunded { background: #6b7280; color: #fff; }
.daily-mobile-list { display: none; }

@media (max-width: 900px) {
  .daily-workbook-page { height: auto; min-height: calc(100vh - 88px); }
  .daily-workbook-layout { min-height: 60vh; }
  .daily-workbook-toolbar__filters { width: 100%; }
  .daily-workbook-toolbar__stats { margin-left: 0; }
  .daily-workbook-toolbar__actions { margin-left: 0; }
  .daily-sheet__scroll--compact { max-height: 220px; }
}

@media (max-width: 640px) {
  .daily-sheet__scroll { display: none; }
  .daily-mobile-list { display: grid; gap: 10px; padding: 10px; }
  .daily-mobile-card { padding: 12px; border: 1px solid #c9d9e2; border-radius: 10px; background: #fff; box-shadow: 0 2px 8px rgba(31, 63, 54, .05); }
  .daily-mobile-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
  .daily-mobile-card__header > div { min-width: 0; display: grid; gap: 3px; }
  .daily-mobile-card__header strong { color: #1f3f36; font-size: 14px; }
  .daily-mobile-card__header span { color: #71808a; font-size: 11px; text-align: right; }
  .daily-mobile-card__header > div span { overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
  .daily-mobile-card__meta { margin-top: 5px; color: #71808a; font-size: 11px; }
  .daily-mobile-card__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px 12px; margin-top: 12px; padding-top: 10px; border-top: 1px solid #edf1f4; }
  .daily-mobile-card__grid > div { display: grid; gap: 2px; }
  .daily-mobile-card__grid span { color: #87949b; font-size: 11px; }
  .daily-mobile-card__grid strong { color: #40535d; font-size: 12px; }
  .daily-mobile-operators { padding-top: 0; }
  .daily-workbook-toolbar__stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .daily-workbook-toolbar__stat { display: flex; align-items: baseline; justify-content: space-between; gap: 6px; min-width: 0; }
  .daily-workbook-toolbar__stat em { white-space: normal; }
  .daily-workbook-toolbar__stat strong { flex: 0 0 auto; }
}
</style>
