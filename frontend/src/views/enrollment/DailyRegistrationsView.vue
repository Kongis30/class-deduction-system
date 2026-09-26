<template>
  <div class="daily-registrations-page" v-loading="loading">
    <header class="page-heading">
      <div>
        <h2>Daily Registrations</h2>
        <p>Registration entries are grouped by the time they were saved.</p>
      </div>
      <div class="filters">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="to"
          start-placeholder="Start date"
          end-placeholder="End date"
          unlink-panels
        />
        <el-select v-model="operatorUserId" clearable placeholder="All operators" class="operator-filter">
          <el-option
            v-for="operator in operatorOptions"
            :key="String(operator.operatorUserId ?? operator.operatorName)"
            :label="operator.operatorName || 'Operator not recorded'"
            :value="String(operator.operatorUserId ?? '')"
          />
        </el-select>
        <el-button type="primary" @click="loadReport">Search</el-button>
      </div>
    </header>

    <section class="metrics">
      <article><span>Course registrations</span><strong>{{ report?.courseRegistrationCount ?? 0 }}</strong></article>
      <article><span>Orders</span><strong>{{ report?.orderCount ?? 0 }}</strong></article>
      <article><span>Students</span><strong>{{ report?.uniqueStudentCount ?? 0 }}</strong></article>
      <article><span>Amount due</span><strong>{{ money(report?.amountDue) }}</strong></article>
      <article><span>Collected</span><strong>{{ money(report?.amountCollected) }}</strong></article>
      <article><span>Outstanding</span><strong>{{ money(report?.outstandingAmount) }}</strong></article>
    </section>

    <section class="report-section">
      <div class="section-heading">
        <h3>By operator</h3>
        <span>Student count is de-duplicated per operator.</span>
      </div>
      <el-table :data="report?.operators ?? []" empty-text="No registrations for this period">
        <el-table-column prop="operatorName" label="Handled by" min-width="180">
          <template #default="{ row }">{{ row.operatorName || 'Operator not recorded' }}</template>
        </el-table-column>
        <el-table-column prop="uniqueStudentCount" label="Students" width="120" />
        <el-table-column prop="orderCount" label="Orders" width="110" />
        <el-table-column prop="courseRegistrationCount" label="Course registrations" width="170" />
        <el-table-column label="Amount due" width="140" align="right">
          <template #default="{ row }">{{ money(row.amountDue) }}</template>
        </el-table-column>
        <el-table-column label="Collected" width="140" align="right">
          <template #default="{ row }">{{ money(row.amountCollected) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="report-section">
      <div class="section-heading">
        <h3>Registration history</h3>
        <span>{{ report?.rows.length ?? 0 }} course record(s)</span>
      </div>
      <el-table :data="report?.rows ?? []" :row-key="rowKey" empty-text="No registrations for this period">
        <el-table-column label="Saved at" min-width="175">
          <template #default="{ row }">{{ dateTime(row.savedAt) }}</template>
        </el-table-column>
        <el-table-column prop="studentName" label="Student" min-width="150" />
        <el-table-column prop="courseName" label="Course" min-width="190" />
        <el-table-column prop="className" label="Class" min-width="150" />
        <el-table-column prop="grade" label="Grade" width="100" />
        <el-table-column prop="operatorName" label="Handled by" min-width="150">
          <template #default="{ row }">{{ row.operatorName || 'Not recorded' }}</template>
        </el-table-column>
        <el-table-column prop="paymentMethod" label="Payment method" min-width="150" />
        <el-table-column prop="paymentStatus" label="Payment status" min-width="145" />
        <el-table-column label="Amount due" width="140" align="right">
          <template #default="{ row }">{{ money(row.amountDue) }}</template>
        </el-table-column>
        <el-table-column label="Collected" width="140" align="right">
          <template #default="{ row }">{{ money(row.amountCollected) }}</template>
        </el-table-column>
        <el-table-column label="Outstanding" width="140" align="right">
          <template #default="{ row }">{{ money(row.outstandingAmount) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, ref } from 'vue';

import { fetchDailyRegistrations, type DailyRegistrationReport } from '@/api/daily-registrations';
import type { DailyRegistrationRow } from '@/api/daily-registrations';
import { extractErrorMessage } from '@/api/http';

const dateRange = ref<[string, string]>([todayInNewYork(), todayInNewYork()]);
const operatorUserId = ref('');
const report = ref<DailyRegistrationReport>();
const operatorOptions = ref<DailyRegistrationReport['operators']>([]);
const loading = ref(false);

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

function todayInNewYork() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/New_York', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

function money(value?: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value ?? 0);
}

function dateTime(value?: string) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York', dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value));
}

function rowKey(row: DailyRegistrationRow) {
  return `${row.orderId}:${row.orderItemId ?? 'order'}:${row.studentId ?? 'student'}`;
}
</script>

<style scoped>
.daily-registrations-page { display: grid; gap: 18px; padding: 20px; }
.page-heading, .filters, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.page-heading h2, .section-heading h3 { margin: 0; color: #17324d; }
.page-heading p, .section-heading span { margin: 5px 0 0; color: #718096; font-size: 13px; }
.filters { justify-content: flex-end; flex-wrap: wrap; }
.operator-filter { width: 190px; }
.metrics { display: grid; grid-template-columns: repeat(6, minmax(130px, 1fr)); gap: 12px; }
.metrics article, .report-section { border: 1px solid #e3eaf1; border-radius: 10px; background: #fff; }
.metrics article { display: grid; gap: 9px; padding: 16px; }
.metrics span { color: #64748b; font-size: 13px; }
.metrics strong { color: #17324d; font-size: 20px; }
.report-section { display: grid; gap: 14px; padding: 16px; }
@media (max-width: 1100px) { .metrics { grid-template-columns: repeat(3, minmax(130px, 1fr)); } }
@media (max-width: 700px) { .page-heading, .section-heading { align-items: flex-start; flex-direction: column; } .filters { width: 100%; justify-content: flex-start; } .metrics { grid-template-columns: repeat(2, minmax(120px, 1fr)); } }
</style>
