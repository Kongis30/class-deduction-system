<template>
  <div class="daily-workbook-page" v-loading="loading">
    <div class="daily-workbook-toolbar">
      <div class="daily-workbook-toolbar__filters">
        <span class="daily-workbook-toolbar__label">Saved date</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="to"
          start-placeholder="Start date"
          end-placeholder="End date"
          unlink-panels
          size="small"
        />
        <el-select v-model="operatorUserId" clearable placeholder="All operators" class="operator-filter" size="small">
          <el-option
            v-for="operator in operatorOptions"
            :key="String(operator.operatorUserId ?? operator.operatorName)"
            :label="operator.operatorName || 'Operator not recorded'"
            :value="String(operator.operatorUserId ?? '')"
          />
        </el-select>
        <el-button size="small" type="primary" @click="loadReport">Search</el-button>
      </div>

      <div class="daily-workbook-toolbar__stats">
        <span><em>Course records</em><strong>{{ filteredStats.courses }}</strong></span>
        <span><em>Orders</em><strong>{{ filteredStats.orders }}</strong></span>
        <span><em>Students</em><strong>{{ filteredStats.students }}</strong></span>
        <span><em>Due</em><strong>{{ money(filteredStats.due) }}</strong></span>
        <span><em>Collected</em><strong>{{ money(filteredStats.collected) }}</strong></span>
        <span><em>Outstanding</em><strong>{{ money(filteredStats.outstanding) }}</strong></span>
      </div>

      <div class="daily-workbook-toolbar__actions">
        <el-button size="small" :disabled="filteredRows.length === 0" @click="clearAllFilters">Clear filters</el-button>
        <el-button size="small" type="primary" :disabled="filteredRows.length === 0" @click="exportExcel">Export Excel</el-button>
      </div>
    </div>

    <div class="daily-workbook-layout">
      <section class="daily-sheet operator-sheet">
        <div class="daily-sheet__caption">
          <strong>Operator totals</strong>
          <span>Counts summarize registrations saved during the selected date range.</span>
        </div>
        <div class="daily-sheet__scroll daily-sheet__scroll--compact">
          <table class="daily-workbook-table operator-table">
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
                <th class="number-cell">Students</th>
                <th class="number-cell">Orders</th>
                <th class="number-cell">Course records</th>
                <th class="money-cell">Amount due</th>
                <th class="money-cell">Collected</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="operator in visibleOperators" :key="String(operator.operatorUserId ?? operator.operatorName)">
                <td>{{ operator.operatorName || 'Operator not recorded' }}</td>
                <td class="number-cell">{{ operator.uniqueStudentCount }}</td>
                <td class="number-cell">{{ operator.orderCount }}</td>
                <td class="number-cell">{{ operator.courseRegistrationCount }}</td>
                <td class="money-cell">{{ money(operator.amountDue) }}</td>
                <td class="money-cell">{{ money(operator.amountCollected) }}</td>
              </tr>
              <tr v-if="visibleOperators.length === 0" class="daily-workbook-table__empty">
                <td colspan="6">No operator totals for this date range.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="daily-sheet history-sheet">
        <div class="daily-sheet__caption">
          <strong>Registration history</strong>
          <span>{{ filteredRows.length }} course record(s), ordered by saved time</span>
        </div>
        <div class="daily-sheet__scroll history-sheet__scroll" data-testid="daily-registration-workbook">
          <table class="daily-workbook-table history-table">
            <colgroup>
              <col v-for="column in columns" :key="column.key" :style="{ width: `${column.width}px` }">
            </colgroup>
            <thead>
              <tr>
                <th
                  v-for="column in columns"
                  :key="column.key"
                  :class="{ 'daily-workbook-table__header--active': hasColumnFilter(column.key) }"
                >
                  <el-popover
                    placement="bottom-start"
                    trigger="click"
                    :width="column.filterType === 'number' ? 300 : column.filterType === 'date' ? 330 : 260"
                    popper-class="daily-workbook-filter-popper"
                  >
                    <template #reference>
                      <button type="button" class="daily-workbook-table__header-button">
                        <span>{{ column.label }}</span>
                        <span class="daily-workbook-table__header-icons">
                          <span v-if="sortState.key === column.key">{{ sortState.direction === 'asc' ? '↑' : '↓' }}</span>
                          <span class="daily-workbook-table__filter-arrow" />
                        </span>
                      </button>
                    </template>

                    <div class="daily-workbook-filter">
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
                      <div v-else class="daily-workbook-filter__number-range">
                        <el-input-number v-model="columnFilters[column.key].min" :controls="false" placeholder="Min" />
                        <span>to</span>
                        <el-input-number v-model="columnFilters[column.key].max" :controls="false" placeholder="Max" />
                      </div>

                      <div class="daily-workbook-filter__sort">
                        <span>Sort</span>
                        <el-button-group>
                          <el-button size="small" :type="isSorted(column.key, 'asc') ? 'primary' : 'default'" @click="setSort(column.key, 'asc')">Asc</el-button>
                          <el-button size="small" :type="isSorted(column.key, 'desc') ? 'primary' : 'default'" @click="setSort(column.key, 'desc')">Desc</el-button>
                          <el-button size="small" @click="clearSort(column.key)">Clear</el-button>
                        </el-button-group>
                      </div>
                      <div class="daily-workbook-filter__actions">
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
                <tr class="daily-workbook-table__date-group">
                  <td :colspan="columns.length">
                    {{ group.label }}
                    <span>{{ group.rows.length }} course record(s)</span>
                  </td>
                </tr>
                <tr v-for="row in group.rows" :key="rowKey(row)" class="daily-workbook-table__data-row">
                  <td>{{ dateTime(row.savedAt) }}</td>
                  <td>{{ display(row.studentName) }}</td>
                  <td>{{ display(row.courseName) }}</td>
                  <td>{{ display(row.className) }}</td>
                  <td class="daily-workbook-table__center">{{ display(row.grade) }}</td>
                  <td>{{ display(row.operatorName, 'Operator not recorded') }}</td>
                  <td class="daily-workbook-table__center">
                    <span :class="['daily-pill', paymentMethodClass(row.paymentMethod)]">{{ display(row.paymentMethod) }}</span>
                  </td>
                  <td class="daily-workbook-table__center">
                    <span :class="['daily-pill', paymentStatusClass(row.paymentStatus)]">{{ display(row.paymentStatus) }}</span>
                  </td>
                  <td class="money-cell">{{ money(row.amountDue) }}</td>
                  <td class="money-cell">{{ money(row.amountCollected) }}</td>
                  <td class="money-cell" :class="{ 'daily-workbook-table__outstanding': Number(row.outstandingAmount ?? 0) > 0 }">
                    {{ money(row.outstandingAmount) }}
                  </td>
                </tr>
              </template>
            </tbody>
            <tbody v-else-if="!loading">
              <tr class="daily-workbook-table__empty">
                <td :colspan="columns.length">No registrations match the current filters.</td>
              </tr>
            </tbody>
          </table>
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

type ColumnKey = 'savedAt' | 'studentName' | 'courseName' | 'className' | 'grade' | 'operatorName' | 'paymentMethod' | 'paymentStatus' | 'amountDue' | 'amountCollected' | 'outstandingAmount';
type FilterType = 'text' | 'select' | 'date' | 'number';
type Column = { key: ColumnKey; label: string; width: number; filterType: FilterType };
type ColumnFilter = { text: string; selected: string[]; dateRange: [string, string] | null; min: number | undefined; max: number | undefined };

const columns: Column[] = [
  { key: 'savedAt', label: 'Saved at (ET)', width: 165, filterType: 'date' },
  { key: 'studentName', label: 'Student', width: 155, filterType: 'text' },
  { key: 'courseName', label: 'Course', width: 200, filterType: 'text' },
  { key: 'className', label: 'Class', width: 180, filterType: 'text' },
  { key: 'grade', label: 'Grade', width: 90, filterType: 'select' },
  { key: 'operatorName', label: 'Handled by', width: 155, filterType: 'select' },
  { key: 'paymentMethod', label: 'Pay method', width: 125, filterType: 'select' },
  { key: 'paymentStatus', label: 'Payment status', width: 135, filterType: 'select' },
  { key: 'amountDue', label: 'Amount due', width: 120, filterType: 'number' },
  { key: 'amountCollected', label: 'Collected', width: 120, filterType: 'number' },
  { key: 'outstandingAmount', label: 'Outstanding', width: 125, filterType: 'number' },
];

const dateRange = ref<[string, string]>([todayInNewYork(), todayInNewYork()]);
const operatorUserId = ref('');
const report = ref<DailyRegistrationReport>();
const operatorOptions = ref<DailyRegistrationOperatorSummary[]>([]);
const loading = ref(false);
const sortState = ref<{ key: ColumnKey; direction: 'asc' | 'desc' }>({ key: 'savedAt', direction: 'desc' });
const columnFilters = reactive<Record<ColumnKey, ColumnFilter>>({
  savedAt: emptyFilter(), studentName: emptyFilter(), courseName: emptyFilter(), className: emptyFilter(),
  grade: emptyFilter(), operatorName: emptyFilter(), paymentMethod: emptyFilter(), paymentStatus: emptyFilter(),
  amountDue: emptyFilter(), amountCollected: emptyFilter(), outstandingAmount: emptyFilter(),
});

const sourceRows = computed(() => report.value?.rows ?? []);
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
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('Choose a date range.');
    return;
  }
  loading.value = true;
  try {
    const response = await fetchDailyRegistrations({
      fromDate: dateRange.value[0],
      toDate: dateRange.value[1],
      ...(operatorUserId.value ? { operatorUserId: operatorUserId.value } : {}),
    });
    report.value = response.data.data;
    if (!operatorUserId.value) operatorOptions.value = response.data.data.operators;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, 'Failed to load registration history.'));
  } finally {
    loading.value = false;
  }
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
  XLSX.writeFile(workbook, `Daily_Registrations_${dateRange.value[0]}_${dateRange.value[1]}.xlsx`);
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
  if (method.includes('cash')) return 'daily-pill--cash';
  if (method.includes('zelle')) return 'daily-pill--zelle';
  if (method.includes('check')) return 'daily-pill--check';
  return method ? 'daily-pill--method' : '';
}

function paymentStatusClass(value?: string) {
  const status = value?.trim().toUpperCase() ?? '';
  if (status === 'PAID') return 'daily-pill--paid';
  if (status === 'REFUNDED') return 'daily-pill--refunded';
  if (status === 'PARTIAL') return 'daily-pill--partial';
  return 'daily-pill--pending';
}

function naturalCompare(left: string, right: string) {
  return left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' });
}

function rowKey(row: DailyRegistrationRow) {
  return `${row.orderId}:${row.orderItemId ?? 'order'}:${row.studentId ?? 'student'}`;
}
</script>

<style scoped>
.daily-workbook-page {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: calc(100vh - 88px);
  background: #fff;
}

.daily-workbook-toolbar {
  display: flex;
  align-items: center;
  gap: 14px 22px;
  flex-wrap: wrap;
  padding: 8px 12px;
  border: 1px solid #9eafa7;
  border-bottom: 0;
  background: #f4f8f5;
}

.daily-workbook-toolbar__filters,
.daily-workbook-toolbar__stats,
.daily-workbook-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.daily-workbook-toolbar__filters :deep(.el-date-editor) { width: 280px; }
.daily-workbook-toolbar__label { color: #376451; font-size: 12px; font-weight: 700; }
.operator-filter { width: 165px; }
.daily-workbook-toolbar__stats { gap: 14px; }
.daily-workbook-toolbar__stats span { display: inline-flex; align-items: baseline; gap: 5px; white-space: nowrap; }
.daily-workbook-toolbar__stats em { color: #6b7684; font-size: 11px; font-style: normal; }
.daily-workbook-toolbar__stats strong { color: #263b44; font-size: 13px; font-variant-numeric: tabular-nums; }
.daily-workbook-toolbar__actions { margin-left: auto; }

.daily-workbook-layout { display: flex; flex: 1; min-height: 0; flex-direction: column; }
.daily-sheet { min-width: 0; border: 1px solid #9eafa7; border-top: 0; background: #fff; }
.daily-sheet__caption { display: flex; align-items: baseline; gap: 12px; padding: 8px 12px; border-bottom: 1px solid #c3d3df; color: #244438; }
.daily-sheet__caption strong { font-size: 12px; text-transform: uppercase; letter-spacing: .035em; }
.daily-sheet__caption span { color: #6b7684; font-size: 11px; }
.daily-sheet__scroll { min-width: 0; overflow: auto; }
.daily-sheet__scroll--compact { max-height: 180px; }
.operator-table { width: max-content; min-width: 100%; }
.history-sheet { display: flex; min-height: 0; flex: 1; flex-direction: column; }
.history-sheet__scroll { flex: 1; min-height: 0; }

.daily-workbook-table {
  width: max-content;
  min-width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
  color: #263b44;
  font-size: 12px;
}

.daily-workbook-table th,
.daily-workbook-table td {
  height: 30px;
  padding: 5px 8px;
  overflow: hidden;
  border-right: 1px solid #c3d3df;
  border-bottom: 1px solid #c3d3df;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.daily-workbook-table th {
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

.daily-workbook-table th:last-child,
.daily-workbook-table td:last-child { border-right: 0; }
.daily-workbook-table__header--active { background: #284f40 !important; }
.daily-workbook-table__header-button { display: flex; width: 100%; height: 100%; align-items: center; justify-content: space-between; gap: 8px; padding: 0 8px; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
.daily-workbook-table__header-icons { display: inline-flex; align-items: center; gap: 6px; }
.daily-workbook-table__filter-arrow { width: 0; height: 0; border-top: 4px solid currentColor; border-right: 4px solid transparent; border-left: 4px solid transparent; opacity: .7; }
.daily-workbook-table__data-row td { background: #dcebf8; }
.daily-workbook-table__data-row:hover td { background: #d1e5f6; }
.daily-workbook-table__date-group td { height: 25px; background: #edf4f1; color: #315b49; font-weight: 700; }
.daily-workbook-table__date-group span { margin-left: 10px; color: #76857f; font-size: 11px; font-weight: 500; }
.daily-workbook-table__center { text-align: center; }
.number-cell { text-align: center; font-variant-numeric: tabular-nums; }
.money-cell { text-align: right !important; font-variant-numeric: tabular-nums; }
.daily-workbook-table__outstanding { color: #bd2431; font-weight: 700; }
.daily-workbook-table__empty td { height: 140px; background: #f7fbfe; color: #687b86; text-align: center; }

.daily-pill { display: inline-flex; min-width: 34px; height: 18px; align-items: center; justify-content: center; padding: 0 7px; border-radius: 9px; background: #e9eef2; color: #50636f; font-size: 10px; font-weight: 700; line-height: 18px; }
.daily-pill--cash, .daily-pill--paid { background: #2d78ca; color: #fff; }
.daily-pill--zelle, .daily-pill--pending { background: #e41f2b; color: #fff; }
.daily-pill--check, .daily-pill--partial { background: #f8d155; color: #775600; }
.daily-pill--method { background: #528f79; color: #fff; }
.daily-pill--refunded { background: #6b7280; color: #fff; }

:global(.daily-workbook-filter-popper) { padding: 12px !important; }
.daily-workbook-filter { display: grid; gap: 12px; }
.daily-workbook-filter > strong { color: #303133; font-size: 13px; }
.daily-workbook-filter :deep(.el-select),
.daily-workbook-filter :deep(.el-date-editor) { width: 100%; }
.daily-workbook-filter__number-range { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: 6px; color: #909399; font-size: 12px; }
.daily-workbook-filter__number-range :deep(.el-input-number) { width: 100%; }
.daily-workbook-filter__sort { display: grid; gap: 6px; color: #606266; font-size: 12px; font-weight: 600; }
.daily-workbook-filter__sort :deep(.el-button-group) { display: flex; }
.daily-workbook-filter__sort :deep(.el-button) { flex: 1; }
.daily-workbook-filter__actions { display: flex; justify-content: space-between; border-top: 1px solid #ebeef5; padding-top: 6px; }

@media (max-width: 900px) {
  .daily-workbook-page { height: auto; min-height: calc(100vh - 88px); }
  .daily-workbook-layout { min-height: 60vh; }
  .daily-workbook-toolbar__actions { margin-left: 0; }
  .daily-sheet__scroll--compact { max-height: 220px; }
}
</style>
