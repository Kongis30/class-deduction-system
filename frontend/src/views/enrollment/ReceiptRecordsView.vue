<template>
  <div class="page-stack receipt-records-page">
    <section class="hero-card table-page-header">
      <div>
        <span class="hero-card__eyebrow table-page-header__eyebrow">Receipts</span>
        <h2>Student Receipts</h2>
        <p>Review saved registration, payment, and refund receipts.</p>
      </div>
      <el-button class="action-primary" type="primary" :loading="studentLoading" @click="loadStudents">Refresh</el-button>
    </section>

    <el-row class="receipt-workspace" :gutter="16">
      <el-col :xs="24" :sm="8" :md="6" :lg="6" :xl="5">
        <section class="panel-card receipt-sidebar">
          <el-input
            v-model="studentQuery.keyword"
            class="student-search"
            placeholder="Search student"
            clearable
            @clear="handleStudentSearch"
            @keyup.enter="handleStudentSearch"
          >
            <template #append>
              <el-button type="primary" @click="handleStudentSearch">Search</el-button>
            </template>
          </el-input>

          <el-scrollbar v-loading="studentLoading" class="student-scroll">
            <el-menu
              v-if="students.length > 0"
              class="receipt-student-menu"
              :default-active="selectedStudent?.id"
              @select="selectStudentById"
            >
              <el-menu-item v-for="student in students" :key="student.id" :index="student.id">
                <span class="student-name">{{ student.studentName }}</span>
                <el-tag size="small" effect="plain">{{ student.enrollmentCount ?? 0 }}</el-tag>
              </el-menu-item>
            </el-menu>

            <el-empty v-else-if="!studentLoading" description="No students found" :image-size="72" />
          </el-scrollbar>

          <el-pagination
            class="student-pagination"
            small
            layout="prev, pager, next"
            :total="studentTotal"
            :page-size="studentQuery.pageSize"
            :current-page="studentQuery.pageNum"
            @current-change="handleStudentPageChange"
          />
        </section>
      </el-col>

      <el-col :xs="24" :sm="16" :md="18" :lg="18" :xl="19">
        <section class="panel-card records-panel">
          <el-tabs v-model="receiptType" class="receipt-type-tabs" @tab-change="handleReceiptTypeChange">
            <el-tab-pane label="Registration receipts" name="registration" />
            <el-tab-pane label="Payment receipts" name="payment" />
            <el-tab-pane label="Refund receipts" name="refund" />
          </el-tabs>

          <div class="records-header">
            <div>
              <span>Selected student</span>
              <strong>{{ selectedStudent?.studentName || 'No student selected' }}</strong>
            </div>
            <p v-if="selectedStudent">
              {{ recordTotal }} saved {{ receiptType }} receipt{{ recordTotal === 1 ? '' : 's' }}.
            </p>
            <p v-else>Select a student to view receipts.</p>
          </div>

          <el-table
            v-loading="receiptLoading"
            :data="receipts"
            row-key="id"
            class="receipt-order-table sheet-table"
            :empty-text="`No ${receiptType} receipts for this student.`"
          >
            <el-table-column prop="receiptNo" label="Receipt no." min-width="170" />
            <el-table-column label="Enrollment" min-width="170">
              <template #default="{ row }">{{ receiptPayload(row).enrollmentNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="Issued at" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.issuedAt) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType !== 'registration'" :label="receiptType === 'refund' ? 'This refund' : 'This payment'" min-width="120" align="right">
              <template #default="{ row }">{{ formatMoney(Math.abs(receiptPayload(row).paymentAmount)) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType !== 'registration'" label="Balance after" min-width="120" align="right">
              <template #default="{ row }">{{ formatMoney(receiptPayload(row).balanceAmount) }}</template>
            </el-table-column>
            <el-table-column label="Actions" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openReceipt(row)">View receipt</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="selectedStudent"
            class="records-pagination"
            layout="prev, pager, next, total"
            :total="recordTotal"
            :page-size="orderQuery.pageSize"
            :current-page="orderQuery.pageNum"
            @current-change="handleOrderPageChange"
          />
        </section>
      </el-col>
    </el-row>

    <el-dialog v-model="transactionReceiptDialogVisible" :title="transactionReceiptTitle" width="820px">
      <PaymentReceiptPreview v-if="selectedTransactionReceipt" :receipt="selectedTransactionReceipt" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { fetchStudents } from '@/api/base-data';
import { fetchReceipts } from '@/api/enrollment';
import { extractErrorMessage } from '@/api/http';
import PaymentReceiptPreview from '@/components/receipt/PaymentReceiptPreview.vue';
import type { ReceiptRecordItem, StudentItem } from '@/types/domain';

const router = useRouter();

const studentLoading = ref(false);
const receiptLoading = ref(false);
const students = ref<StudentItem[]>([]);
const studentTotal = ref(0);
const selectedStudent = ref<StudentItem | null>(null);
const receiptType = ref<'registration' | 'payment' | 'refund'>('registration');
const receipts = ref<ReceiptRecordItem[]>([]);
const receiptTotal = ref(0);
const transactionReceiptDialogVisible = ref(false);
const selectedTransactionReceipt = ref<ReceiptRecordItem | null>(null);
const recordTotal = computed(() => receiptTotal.value);

const studentQuery = reactive({
  keyword: '',
  pageNum: 1,
  pageSize: 20,
});

const orderQuery = reactive({
  pageNum: 1,
  pageSize: 10,
});

onMounted(loadStudents);

async function loadStudents() {
  studentLoading.value = true;
  try {
    const res = await fetchStudents({
      keyword: studentQuery.keyword.trim() || undefined,
      hasEnrollmentOrder: true,
      pageNum: studentQuery.pageNum,
      pageSize: studentQuery.pageSize,
    });
    const page = res.data.data;
    students.value = page.records ?? [];
    studentTotal.value = page.total ?? 0;

    if (!selectedStudent.value && students.value.length > 0) {
      await selectStudent(students.value[0]);
      return;
    }

    if (selectedStudent.value && !students.value.some((student) => student.id === selectedStudent.value?.id)) {
      clearSelection();
    }
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    studentLoading.value = false;
  }
}

async function selectStudent(student: StudentItem) {
  selectedStudent.value = student;
  orderQuery.pageNum = 1;
  await loadSelectedStudentRecords();
}

function selectStudentById(studentId: string) {
  const student = students.value.find((item) => item.id === studentId);
  if (student) {
    selectStudent(student);
  }
}

const receiptTemplateCodes = {
  registration: 'DEFAULT_RECEIPT',
  payment: 'PAYMENT_RECEIPT',
  refund: 'REFUND_RECEIPT',
} as const;

async function loadSelectedStudentReceipts() {
  if (!selectedStudent.value) {
    receipts.value = [];
    receiptTotal.value = 0;
    return;
  }
  receiptLoading.value = true;
  try {
    const res = await fetchReceipts({
      studentId: selectedStudent.value.id,
      templateCode: receiptTemplateCodes[receiptType.value],
      pageNum: orderQuery.pageNum,
      pageSize: orderQuery.pageSize,
    });
    receipts.value = res.data.data.records ?? [];
    receiptTotal.value = res.data.data.total ?? 0;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    receiptLoading.value = false;
  }
}

function loadSelectedStudentRecords() {
  return loadSelectedStudentReceipts();
}

function handleReceiptTypeChange() {
  orderQuery.pageNum = 1;
  loadSelectedStudentRecords();
}

function handleStudentSearch() {
  studentQuery.pageNum = 1;
  clearSelection();
  loadStudents();
}

function handleStudentPageChange(page: number) {
  studentQuery.pageNum = page;
  clearSelection();
  loadStudents();
}

function handleOrderPageChange(page: number) {
  orderQuery.pageNum = page;
  loadSelectedStudentRecords();
}

function clearSelection() {
  selectedStudent.value = null;
  clearOrders();
}

function clearOrders() {
  receipts.value = [];
  receiptTotal.value = 0;
}

const transactionReceiptTitle = computed(() =>
  receiptType.value === 'refund' ? 'Refund Receipt' : 'Payment Receipt'
);

function openReceipt(row: ReceiptRecordItem) {
  if (receiptType.value === 'registration') {
    const orderId = receiptPayload(row).enrollmentOrderId || row.enrollmentOrderId;
    router.push({ name: 'registration', query: { orderId: String(orderId) } });
    return;
  }
  selectedTransactionReceipt.value = row;
  transactionReceiptDialogVisible.value = true;
}

function receiptPayload(row: ReceiptRecordItem) {
  const payload = parseReceiptPayload(row.receiptPayload);
  return {
    enrollmentNo: String(payload.enrollmentNo ?? ''),
    paymentAmount: Number(payload.paymentAmount ?? 0),
    balanceAmount: Number(payload.balanceAmount ?? 0),
  };
}

function parseReceiptPayload(value: unknown): Record<string, unknown> {
  if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  if (typeof value !== 'string' || !value.trim()) return {};
  try {
    const parsed = JSON.parse(value);
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {};
  } catch {
    return {};
  }
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function formatMoney(value?: number) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}

</script>

<style scoped>
.receipt-workspace {
  align-items: stretch;
}

.receipt-sidebar,
.records-panel {
  height: 640px;
}

.student-search {
  margin-bottom: 12px;
}

.student-scroll {
  height: 520px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.receipt-student-menu {
  border-right: 0;
}

.receipt-student-menu :deep(.el-menu-item) {
  height: 44px;
  justify-content: space-between;
  gap: 8px;
  padding: 0 12px;
}

.student-name {
  overflow: hidden;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-pagination,
.records-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.records-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.records-header div {
  display: grid;
  gap: 4px;
}

.records-header span,
.records-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.records-header strong {
  font-size: 18px;
}

.receipt-order-table {
  width: 100%;
}

.row-actions {
  display: flex;
  gap: 4px;
}

@media (max-width: 980px) {
  .receipt-sidebar,
  .records-panel {
    height: auto;
  }

  .student-scroll {
    height: 280px;
  }
}
</style>
