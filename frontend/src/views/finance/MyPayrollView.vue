<template>
  <div class="mp-view">
    <section class="table-page-header">
      <div>
        <span class="table-page-header__eyebrow">Payment history</span>
        <h2>Payment History</h2>
        <p>Review your payment history and the lesson details included in each payment.</p>
      </div>
    </section>

    <section class="mp-panel">
      <div class="mp-toolbar">
        <el-select v-model="statusFilter" clearable placeholder="All statuses" class="mp-status-filter" @change="loadPayroll(1)">
          <el-option label="Pending" value="PENDING" />
          <el-option label="Paid" value="PAID" />
        </el-select>
        <el-button :loading="loading" @click="loadPayroll()">Refresh</el-button>
      </div>

      <el-table v-if="!appStore.isMobileViewport" :data="payrollRows" border stripe v-loading="loading" empty-text="No payroll records found.">
        <el-table-column prop="paymentNo" label="Payment #" min-width="160" />
        <el-table-column label="Period" min-width="190">
          <template #default="{ row }">
            {{ formatDate(row.periodStart) }} - {{ formatDate(row.periodEnd) }}
          </template>
        </el-table-column>
        <el-table-column label="Hours" width="110" align="right">
          <template #default="{ row }">{{ formatHours(row.totalHours) }}</template>
        </el-table-column>
        <el-table-column label="Amount" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="Status" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PAID' ? 'success' : 'warning'" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Paid at" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.paidAt) }}</template>
        </el-table-column>
        <el-table-column label="Action" width="110" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">Details</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else v-loading="loading" class="mp-cards">
        <article v-for="row in payrollRows" :key="row.id" class="mp-card">
          <div class="mp-card__heading">
            <strong>{{ row.paymentNo }}</strong>
            <el-tag :type="row.status === 'PAID' ? 'success' : 'warning'" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </div>
          <dl class="mp-card__details">
            <div><dt>Period</dt><dd>{{ formatDate(row.periodStart) }} – {{ formatDate(row.periodEnd) }}</dd></div>
            <div><dt>Hours</dt><dd>{{ formatHours(row.totalHours) }}</dd></div>
            <div><dt>Amount</dt><dd>{{ formatMoney(row.totalAmount) }}</dd></div>
            <div><dt>Paid at</dt><dd>{{ formatDateTime(row.paidAt) }}</dd></div>
          </dl>
          <el-button class="mp-card__action" type="primary" plain @click="openDetail(row)">View details</el-button>
        </article>
        <el-empty v-if="!loading && payrollRows.length === 0" description="No payroll records found." />
      </div>

      <el-pagination
        v-if="pagination.total > pagination.size"
        v-model:current-page="pagination.page"
        class="mp-pagination"
        layout="prev, pager, next"
        :page-size="pagination.size"
        :total="pagination.total"
        @current-change="loadPayroll"
      />
    </section>

    <el-dialog v-model="detailVisible" title="Payment details" width="780px" :fullscreen="appStore.isMobileViewport">
      <div v-if="detailData" class="mp-detail">
        <div class="mp-detail__summary">
          <span><strong>Payment #:</strong> {{ detailData.paymentNo }}</span>
          <span><strong>Period:</strong> {{ formatDate(detailData.periodStart) }} - {{ formatDate(detailData.periodEnd) }}</span>
          <span><strong>Total:</strong> {{ formatHours(detailData.totalHours) }} · {{ formatMoney(detailData.totalAmount) }}</span>
          <span><strong>Status:</strong> {{ statusLabel(detailData.status) }}</span>
        </div>

        <el-table v-if="!appStore.isMobileViewport" :data="detailData.items || []" border size="small" empty-text="No lesson details.">
          <el-table-column label="Course" min-width="180">
            <template #default="{ row }">
              {{ row.className || row.packageName || row.productName || 'Course' }}
            </template>
          </el-table-column>
          <el-table-column prop="subjectName" label="Subject" min-width="130" />
          <el-table-column label="Type" width="110">
            <template #default="{ row }">{{ occurrenceTypeLabel(row.occurrenceType) }}</template>
          </el-table-column>
          <el-table-column label="Hours" width="90" align="right">
            <template #default="{ row }">{{ formatHours(row.hours) }}</template>
          </el-table-column>
          <el-table-column label="Rate" width="100" align="right">
            <template #default="{ row }">{{ formatMoney(row.hourlyRate) }}</template>
          </el-table-column>
          <el-table-column label="Amount" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="mp-detail-cards">
          <article v-for="item in detailData.items || []" :key="item.id" class="mp-detail-card">
            <strong>{{ item.className || item.packageName || item.productName || 'Course' }}</strong>
            <span>{{ item.subjectName || '—' }} · {{ occurrenceTypeLabel(item.occurrenceType) }}</span>
            <dl>
              <div><dt>Hours</dt><dd>{{ formatHours(item.hours) }}</dd></div>
              <div><dt>Rate</dt><dd>{{ formatMoney(item.hourlyRate) }}</dd></div>
              <div><dt>Amount</dt><dd>{{ formatMoney(item.amount) }}</dd></div>
            </dl>
          </article>
          <el-empty v-if="!(detailData.items || []).length" description="No lesson details." />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fetchMyPayroll, fetchMyPayrollDetail } from '@/api/finance';
import { useAppStore } from '@/stores/app';
import type { TeacherPaymentItem } from '@/types/domain';

const appStore = useAppStore();
const loading = ref(false);
const statusFilter = ref<string>();
const payrollRows = ref<TeacherPaymentItem[]>([]);
const pagination = reactive({ page: 1, size: 10, total: 0 });
const detailVisible = ref(false);
const detailData = ref<TeacherPaymentItem | null>(null);

async function loadPayroll(pageNum?: number) {
  if (pageNum) pagination.page = pageNum;
  loading.value = true;
  try {
    const res = await fetchMyPayroll({
      status: statusFilter.value,
      pageNum: pagination.page,
      pageSize: pagination.size,
    });
    const data = res.data.data;
    payrollRows.value = data.records;
    pagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load payment history.');
  } finally {
    loading.value = false;
  }
}

async function openDetail(row: TeacherPaymentItem) {
  try {
    const res = await fetchMyPayrollDetail(row.id);
    detailData.value = res.data.data;
    detailVisible.value = true;
  } catch {
    ElMessage.error('Failed to load payment details.');
  }
}

function formatMoney(value?: number) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}

function formatHours(value?: number) {
  return `${Number(value ?? 0).toFixed(1)}h`;
}

function formatDate(value?: string) {
  return value || '-';
}

function formatDateTime(value?: string) {
  return value ? value.substring(0, 19).replace('T', ' ') : '-';
}

function statusLabel(status?: string) {
  if (status === 'PAID') return 'Paid';
  if (status === 'PENDING') return 'Pending';
  return status || '-';
}

function occurrenceTypeLabel(type?: string) {
  if (type === 'GROUP_CLASS') return 'Group';
  if (type === 'ONE_ON_ONE') return '1-on-1';
  return type || '-';
}

onMounted(() => {
  loadPayroll();
});
</script>

<style scoped>
.mp-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mp-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.mp-cards {
  display: grid;
  gap: 10px;
}

.mp-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #d9e2ec;
  border-radius: 10px;
  background: #fff;
}

.mp-card__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.mp-card__heading strong {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.mp-card__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.mp-card__details > div {
  min-width: 0;
}

.mp-card__details dt {
  margin-bottom: 3px;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.mp-card__details dd {
  margin: 0;
  color: #334155;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.mp-card__action {
  min-height: 42px;
}

.mp-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.mp-status-filter {
  width: 180px;
}

.mp-pagination {
  justify-content: flex-end;
}

.mp-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mp-detail__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
  font-size: 13px;
}

.mp-detail-cards {
  display: grid;
  gap: 8px;
}

.mp-detail-card {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.mp-detail-card > strong {
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.mp-detail-card > span {
  color: #64748b;
  font-size: 12px;
}

.mp-detail-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 4px 0 0;
}

.mp-detail-card dt {
  color: #64748b;
  font-size: 10px;
  text-transform: uppercase;
}

.mp-detail-card dd {
  margin: 2px 0 0;
  color: #334155;
  font-size: 12px;
}

@media (max-width: 760px) {
  .mp-toolbar {
    justify-content: stretch;
  }

  .mp-status-filter {
    width: 100%;
  }

  .mp-panel {
    padding: 10px;
  }

  .mp-toolbar {
    flex-direction: column;
  }

  .mp-pagination {
    justify-content: center;
  }

  .mp-card__details {
    grid-template-columns: 1fr;
  }

  :deep(.el-dialog.is-fullscreen) {
    display: flex;
    flex-direction: column;
  }

  :deep(.el-dialog.is-fullscreen .el-dialog__body) {
    flex: 1;
  }

  .mp-detail__summary {
    grid-template-columns: 1fr;
  }
}
</style>
