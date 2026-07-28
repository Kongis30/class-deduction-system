<template>
  <div class="page-stack receipt-records-page">
    <section class="hero-card table-page-header">
      <div>
        <span class="hero-card__eyebrow table-page-header__eyebrow">Receipts</span>
        <h2>Registration Receipts</h2>
        <p>Search a student on the left, then open or print any saved registration form on the right.</p>
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
          <div class="records-header">
            <div>
              <span>Selected student</span>
              <strong>{{ selectedStudent?.studentName || 'No student selected' }}</strong>
            </div>
            <p v-if="selectedStudent">
              {{ orderTotal }} saved registration receipt{{ orderTotal === 1 ? '' : 's' }}.
            </p>
            <p v-else>Select a student to view registration receipts.</p>
          </div>

          <el-table
            v-loading="orderLoading"
            :data="orders"
            row-key="id"
            class="receipt-order-table sheet-table"
            empty-text="No registration receipts for this student."
          >
            <el-table-column prop="enrollmentNo" label="Registration no." min-width="170" />
            <el-table-column label="Date" min-width="110">
              <template #default="{ row }">{{ formatDate(row.signupDate || row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="Courses" width="90" align="center">
              <template #default="{ row }">{{ courseCount(row) }}</template>
            </el-table-column>
            <el-table-column label="Course summary" min-width="260">
              <template #default="{ row }">{{ courseSummary(row) }}</template>
            </el-table-column>
            <el-table-column label="Amount due" min-width="120" align="right">
              <template #default="{ row }">{{ formatMoney(row.amountDue ?? row.originalAmount) }}</template>
            </el-table-column>
            <el-table-column label="Paid" min-width="110" align="right">
              <template #default="{ row }">{{ formatMoney(row.receivedAmount) }}</template>
            </el-table-column>
            <el-table-column label="Balance" min-width="110" align="right">
              <template #default="{ row }">{{ formatMoney(row.balanceAmount) }}</template>
            </el-table-column>
            <el-table-column label="Status" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="paymentStatusType(row.paymentStatus)">
                  {{ paymentStatusLabel(row.paymentStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="140" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link type="primary" @click="openRegistration(row)">View</el-button>
                  <el-button link type="primary" @click="printRegistration(row)">Print</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="selectedStudent"
            class="records-pagination"
            layout="prev, pager, next, total"
            :total="orderTotal"
            :page-size="orderQuery.pageSize"
            :current-page="orderQuery.pageNum"
            @current-change="handleOrderPageChange"
          />
        </section>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { fetchStudents } from '@/api/base-data';
import { fetchEnrollmentOrders } from '@/api/enrollment';
import { extractErrorMessage } from '@/api/http';
import type { EnrollmentOrderItem, StudentItem } from '@/types/domain';

const router = useRouter();

const studentLoading = ref(false);
const orderLoading = ref(false);
const students = ref<StudentItem[]>([]);
const studentTotal = ref(0);
const selectedStudent = ref<StudentItem | null>(null);
const orders = ref<EnrollmentOrderItem[]>([]);
const orderTotal = ref(0);

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
  await loadSelectedStudentOrders();
}

function selectStudentById(studentId: string) {
  const student = students.value.find((item) => item.id === studentId);
  if (student) {
    selectStudent(student);
  }
}

async function loadSelectedStudentOrders() {
  if (!selectedStudent.value) {
    clearOrders();
    return;
  }
  orderLoading.value = true;
  try {
    const res = await fetchEnrollmentOrders({
      studentId: selectedStudent.value.id,
      pageNum: orderQuery.pageNum,
      pageSize: orderQuery.pageSize,
    });
    const page = res.data.data;
    orders.value = page.records ?? [];
    orderTotal.value = page.total ?? 0;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    orderLoading.value = false;
  }
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
  loadSelectedStudentOrders();
}

function clearSelection() {
  selectedStudent.value = null;
  clearOrders();
}

function clearOrders() {
  orders.value = [];
  orderTotal.value = 0;
}

function openRegistration(row: EnrollmentOrderItem) {
  router.push({ name: 'registration', query: { orderId: row.id } });
}

function printRegistration(row: EnrollmentOrderItem) {
  router.push({ name: 'registration', query: { orderId: row.id, print: '1' } });
}

function courseCount(row: EnrollmentOrderItem) {
  return row.items?.length || row.packageCount || (row.classId ? 1 : 0) || 1;
}

function courseSummary(row: EnrollmentOrderItem) {
  const summaries = (row.items ?? [])
    .map((item) => item.detail || item.registerClass || item.courseType)
    .filter(Boolean);
  return summaries.length > 0 ? summaries.join(' / ') : row.className || row.courseProductName || '-';
}

function formatDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 10) : '-';
}

function formatMoney(value?: number) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}

function paymentStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    PAID: 'Paid',
    PARTIAL: 'Partial',
    UNPAID: 'Unpaid',
    REFUNDED: 'Refunded',
  };
  return labels[status || ''] || status || '-';
}

function paymentStatusType(status?: string) {
  if (status === 'PAID') {
    return 'success';
  }
  if (status === 'PARTIAL') {
    return 'warning';
  }
  if (status === 'REFUNDED') {
    return 'info';
  }
  return 'danger';
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
