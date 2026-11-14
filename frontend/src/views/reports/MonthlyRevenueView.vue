<template>
  <div class="data-summary-page monthly-revenue-page" v-loading="loading">
    <div class="workbook-toolbar">
      <div class="workbook-toolbar__month">
        <span class="workbook-toolbar__label">Monthly revenue</span>
        <el-date-picker
          v-model="fromMonth"
          type="month"
          size="small"
          value-format="YYYY-MM"
          :clearable="false"
          placeholder="From month"
          aria-label="First month"
          @change="loadReport"
        />
        <span class="workbook-toolbar__label">to</span>
        <el-date-picker
          v-model="toMonth"
          type="month"
          size="small"
          value-format="YYYY-MM"
          :clearable="false"
          placeholder="To month"
          aria-label="Last month"
          @change="loadReport"
        />
        <el-button size="small" @click="loadReport">Refresh</el-button>
      </div>
      <div class="workbook-toolbar__stats">
        <span class="workbook-toolbar__stat"><em>Collected</em><strong>{{ formatMoney(rangeTotal) }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Refunded</em><strong>{{ formatMoney(rangeRefunds) }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Net</em><strong>{{ formatMoney(rangeTotal + rangeRefunds) }}</strong></span>
      </div>
      <div class="workbook-toolbar__export">
        <el-button type="primary" size="small" :disabled="!hasRows" @click="exportExcel">Export Excel</el-button>
      </div>
    </div>

    <p v-if="unassignedPaymentCount > 0" class="monthly-revenue-notice">
      {{ unassignedPaymentCount }} payment{{ unassignedPaymentCount === 1 ? '' : 's' }} could not be matched to a
      course product and {{ unassignedPaymentCount === 1 ? 'appears' : 'appear' }} as Unassigned product.
    </p>

    <div ref="tableScrollRef" class="workbook-scroll mobile-pinch-scroll">
      <table
        class="workbook-table monthly-revenue-table"
        :style="{ zoom: appStore.isMobileViewport ? tableScale : undefined }"
      >
        <colgroup>
          <col class="monthly-revenue-label-column" />
          <col v-for="month in months" :key="month.key" class="monthly-revenue-month-column" />
        </colgroup>
        <thead>
          <tr>
            <th class="monthly-revenue-label-cell">Campus / Product</th>
            <th v-for="month in months" :key="month.key" class="monthly-revenue-amount-cell">
              {{ month.label }}
            </th>
          </tr>
        </thead>
        <tbody v-if="sections.length">
          <template v-for="section in sections" :key="section.key">
            <tr class="monthly-revenue-group-row">
              <td class="monthly-revenue-label-cell">{{ section.title }}</td>
              <td v-for="month in months" :key="`${section.key}-${month.key}`" />
            </tr>
            <tr v-for="row in section.rows" :key="`${section.key}-${String(row.productId)}`"
                class="monthly-revenue-product-row">
              <td class="monthly-revenue-label-cell monthly-revenue-product-label">{{ row.productName }}</td>
              <td v-for="(amount, index) in row.amounts" :key="`${section.key}-${String(row.productId)}-${index}`"
                  class="monthly-revenue-amount-cell"
                  :class="{ 'monthly-revenue-amount--negative': amount < 0 }">
                {{ formatMoney(amount) }}
              </td>
            </tr>
          </template>
        </tbody>
        <tbody v-else-if="!loading">
          <tr class="workbook-empty-row">
            <td :colspan="months.length + 1">No payments in this range.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import * as XLSX from 'xlsx';
import dayjs from 'dayjs';

import {
  fetchMonthlyRevenue,
  type MonthlyRevenueMonthColumn,
  type MonthlyRevenueSection,
} from '@/api/reports';
import { extractErrorMessage } from '@/api/http';
import { useAppStore } from '@/stores/app';
import { useMobileTablePinchZoom } from '@/composables/useMobileTablePinchZoom';
import { formatExactUsd } from '@/utils/money-display';
import { ElMessage } from 'element-plus';

const DEFAULT_MONTH_SPAN = 6;

const toMonth = ref(dayjs().format('YYYY-MM'));
const fromMonth = ref(dayjs().subtract(DEFAULT_MONTH_SPAN - 1, 'month').format('YYYY-MM'));
const months = ref<MonthlyRevenueMonthColumn[]>([]);
const sections = ref<MonthlyRevenueSection[]>([]);
const unassignedPaymentCount = ref(0);
const loading = ref(false);

const appStore = useAppStore();
const tableScrollRef = ref<HTMLElement | null>(null);
const { scale: tableScale } = useMobileTablePinchZoom(tableScrollRef, computed(() => appStore.isMobileViewport));

const hasRows = computed(() => sections.value.some((section) => section.rows.length > 0));

const rangeTotal = computed(() => {
  let total = 0;
  sections.value.forEach((section) => {
    section.rows.forEach((row) => {
      row.amounts.forEach((amount) => {
        const value = Number(amount ?? 0);
        if (value > 0) total += value;
      });
    });
  });
  return total;
});

const rangeRefunds = computed(() => {
  let total = 0;
  sections.value.forEach((section) => {
    section.rows.forEach((row) => {
      row.amounts.forEach((amount) => {
        const value = Number(amount ?? 0);
        if (value < 0) total += value;
      });
    });
  });
  return total;
});

function formatMoney(value?: number | null) {
  if (!value) return formatExactUsd(0);
  return formatExactUsd(value);
}

async function loadReport() {
  if (fromMonth.value && toMonth.value && fromMonth.value > toMonth.value) {
    ElMessage.warning('The start month must not be after the end month.');
    return;
  }
  loading.value = true;
  try {
    const response = await fetchMonthlyRevenue({
      fromMonth: fromMonth.value || undefined,
      toMonth: toMonth.value || undefined,
    });
    months.value = response.data.data.months;
    sections.value = response.data.data.sections;
    unassignedPaymentCount.value = response.data.data.unassignedPaymentCount;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

function exportExcel() {
  if (!hasRows.value) {
    ElMessage.warning('No revenue to export.');
    return;
  }
  const header = ['Campus / Product', ...months.value.map((month) => month.label)];
  const sheetRows: (string | number)[][] = [header];
  sections.value.forEach((section) => {
    sheetRows.push([section.title]);
    section.rows.forEach((row) => {
      sheetRows.push([`  ${row.productName}`, ...row.amounts.map((amount) => Number(amount ?? 0))]);
    });
  });
  const sheet = XLSX.utils.aoa_to_sheet(sheetRows);
  for (let rowIndex = 1; rowIndex < sheetRows.length; rowIndex += 1) {
    for (let columnIndex = 1; columnIndex < header.length; columnIndex += 1) {
      const cell = sheet[XLSX.utils.encode_cell({ r: rowIndex, c: columnIndex })];
      if (cell) cell.z = '$#,##0.00;-$#,##0.00';
    }
  }
  sheet['!cols'] = header.map((_, index) => ({ wch: index === 0 ? 32 : 14 }));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, 'Monthly Revenue');
  XLSX.writeFile(workbook, `Monthly_Revenue_${fromMonth.value}_${toMonth.value}.xlsx`);
}

onMounted(loadReport);
</script>

<style scoped>
.monthly-revenue-notice {
  margin: 0;
  padding: 6px 12px;
  border: 1px solid #9eafa7;
  border-bottom: 0;
  background: #fff8e6;
  color: #7a5b12;
  font-size: 12px;
}

.monthly-revenue-table {
  table-layout: auto;
}

/* 首列固定，横向滚动月份列时仍能看到校区与产品名。 */
.monthly-revenue-label-column {
  width: 260px;
  min-width: 220px;
}

.monthly-revenue-month-column {
  width: 120px;
  min-width: 108px;
}

.monthly-revenue-table th.monthly-revenue-label-cell,
.monthly-revenue-table td.monthly-revenue-label-cell {
  position: sticky;
  left: 0;
  z-index: 3;
  text-align: left;
}

.monthly-revenue-table th.monthly-revenue-label-cell {
  z-index: 5;
}

.monthly-revenue-table td.monthly-revenue-label-cell {
  background: #fff;
}

.monthly-revenue-amount-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.monthly-revenue-group-row td {
  background: #eef3ef;
  color: #1f3d2f;
  font-weight: 700;
}

.monthly-revenue-group-row td.monthly-revenue-label-cell {
  background: #eef3ef;
}

.monthly-revenue-product-row td {
  background: #fff;
  color: #263b44;
}

.monthly-revenue-product-row:hover td {
  background: #f4f8f5;
}

.monthly-revenue-product-row:hover td.monthly-revenue-label-cell {
  background: #f4f8f5;
}

.monthly-revenue-product-label {
  padding-left: 22px;
}

.monthly-revenue-amount--negative {
  color: #b42318;
}
</style>
