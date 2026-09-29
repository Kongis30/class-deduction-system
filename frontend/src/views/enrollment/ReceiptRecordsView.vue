<template>
  <div class="page-stack receipt-records-page">
    <section class="hero-card table-page-header">
      <div>
        <span class="hero-card__eyebrow table-page-header__eyebrow">Receipts</span>
        <h2>Student Receipts</h2>
        <p>Review saved registration and refund receipts.</p>
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

          <el-scrollbar v-loading="studentLoading" always class="student-scroll">
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

        </section>
      </el-col>

      <el-col :xs="24" :sm="16" :md="18" :lg="18" :xl="19">
        <section class="panel-card records-panel">
          <el-tabs v-model="receiptType" class="receipt-type-tabs" @tab-change="handleReceiptTypeChange">
            <el-tab-pane label="Registration receipts" name="registration" />
            <el-tab-pane label="Refund receipts" name="refund" />
          </el-tabs>

          <div class="records-header">
            <div>
              <span>Selected student</span>
              <strong>{{ selectedStudent?.studentName || 'No student selected' }}</strong>
            </div>
            <p v-if="selectedStudent">
              {{ recordTotal }} saved {{ receiptType === 'refund' ? 'refund' : 'registration' }} receipt{{ recordTotal === 1 ? '' : 's' }}.
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
            <el-table-column prop="studentName" label="Student" min-width="150" />
            <el-table-column v-if="receiptType === 'registration'" label="Course" min-width="230">
              <template #default="{ row }">
                <div class="receipt-course-list">
                  <span v-for="course in receiptSummary(row).courses" :key="course.key">{{ course.label }}</span>
                  <span v-if="receiptSummary(row).courses.length === 0" class="muted-value">Credit top-up</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="receiptType === 'registration'" label="Amount due" min-width="120" align="right">
              <template #default="{ row }">{{ formatMoney(receiptSummary(row).amountDue) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType === 'registration'" label="Paid" min-width="110" align="right">
              <template #default="{ row }">{{ formatMoney(receiptSummary(row).paidAmount) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType === 'registration'" label="Balance" min-width="110" align="right">
              <template #default="{ row }">{{ formatMoney(receiptSummary(row).balanceAmount) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType !== 'registration'" label="Payment method" min-width="140">
              <template #default="{ row }">{{ receiptSummary(row).paymentMethod || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType !== 'registration'" :label="receiptType === 'refund' ? 'Refund amount' : 'Payment amount'" min-width="135" align="right">
              <template #default="{ row }">{{ formatMoney(Math.abs(receiptSummary(row).paymentAmount)) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType !== 'registration'" label="Balance after" min-width="130" align="right">
              <template #default="{ row }">{{ formatMoney(receiptSummary(row).balanceAmount) }}</template>
            </el-table-column>
            <el-table-column label="Date" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.issuedAt) }}</template>
            </el-table-column>
            <el-table-column label="Actions" :width="receiptType === 'registration' ? 180 : 110" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link type="primary" @click="openReceipt(row)">View</el-button>
                  <el-button
                    v-if="canDeleteCourse(row)"
                    link
                    type="danger"
                    @click="openDeleteCourseDialog(row)"
                  >
                    Delete course
                  </el-button>
                  <span
                    v-else-if="receiptType === 'registration' && hasReceiptCourses(row)"
                    class="muted-value"
                    :title="row.enrollmentOrderActive ? 'This receipt has no active course enrollment.' : 'The enrollment order is no longer active.'"
                  >
                    View only
                  </span>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <el-dialog
            v-model="deleteCourseDialogVisible"
            title="Delete a course from this receipt"
            width="520px"
            :close-on-click-modal="!deletingCourse"
            :close-on-press-escape="!deletingCourse"
            @closed="resetDeleteCourseDialog"
          >
            <el-alert
              type="warning"
              :closable="false"
              show-icon
              title="Only the selected course will be removed. Its enrollment and personal schedule records will be cleaned up; other courses on this receipt remain."
            />
            <p class="delete-course-student">
              {{ deleteTargetReceipt?.studentName }} · {{ deleteTargetReceipt?.receiptNo }}
            </p>
            <el-form label-position="top">
              <el-form-item label="Course to delete">
                <el-select v-model="deleteTargetItemId" class="delete-course-select" placeholder="Select a course">
                  <el-option
                    v-for="course in deleteCourseOptions"
                    :key="course.key"
                    :label="course.label"
                    :value="course.key"
                  />
                </el-select>
              </el-form-item>
            </el-form>
            <p class="delete-course-note">
              Refund this course first. The system will block deletion while it still has net payments.
            </p>
            <template #footer>
              <el-button :disabled="deletingCourse" @click="deleteCourseDialogVisible = false">Cancel</el-button>
              <el-button type="danger" :loading="deletingCourse" :disabled="!deleteTargetItemId" @click="confirmDeleteCourse">
                Delete course
              </el-button>
            </template>
          </el-dialog>

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
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { fetchStudents } from '@/api/base-data';
import { deleteEnrollmentOrderItem, fetchReceipts } from '@/api/enrollment';
import { extractErrorMessage } from '@/api/http';
import type { ReceiptRecordItem, StudentItem } from '@/types/domain';
import { formatRoundedUsd } from '@/utils/money-display';

const router = useRouter();

const studentLoading = ref(false);
const receiptLoading = ref(false);
const students = ref<StudentItem[]>([]);
const selectedStudent = ref<StudentItem | null>(null);
const receiptType = ref<'registration' | 'refund'>('registration');
const receipts = ref<ReceiptRecordItem[]>([]);
const receiptTotal = ref(0);
const recordTotal = computed(() => receiptTotal.value);
const deleteCourseDialogVisible = ref(false);
const deletingCourse = ref(false);
const deleteTargetReceipt = ref<ReceiptRecordItem | null>(null);
const deleteTargetItemId = ref('');

const deleteCourseOptions = computed(() => deleteTargetReceipt.value
  ? receiptCourseOptions(deleteTargetReceipt.value)
  : []);

const studentQuery = reactive({
  keyword: '',
});

const STUDENT_PAGE_SIZE = 200;
let studentLoadSequence = 0;

const orderQuery = reactive({
  pageNum: 1,
  pageSize: 10,
});

let receiptLoadSequence = 0;

onMounted(loadStudents);

async function loadStudents() {
  const requestSequence = ++studentLoadSequence;
  const selectedStudentIdAtStart = selectedStudent.value?.id;
  studentLoading.value = true;
  try {
    const query = {
      keyword: studentQuery.keyword.trim() || undefined,
      includeReceiptStudents: true,
      sortByName: true,
      pageSize: STUDENT_PAGE_SIZE,
    };
    const firstPageResponse = await fetchStudents({ ...query, pageNum: 1 });
    const firstPage = firstPageResponse.data.data;
    const total = firstPage.total ?? 0;
    const pageCount = Math.ceil(total / STUDENT_PAGE_SIZE);

    if (requestSequence !== studentLoadSequence) return;

    // 首屏學生到達後立即開啟其收據查詢，不等待左側完整名單全部載入。
    const firstPageStudents = firstPage.records ?? [];
    if (!selectedStudentIdAtStart) {
      students.value = firstPageStudents;
      if (firstPageStudents.length > 0) {
        void selectStudent(firstPageStudents[0]);
      }
    }

    const remainingPages = await Promise.all(
      Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) =>
        fetchStudents({ ...query, pageNum: index + 2 }),
      ),
    );

    if (requestSequence !== studentLoadSequence) return;

    const allStudents = [
      ...firstPageStudents,
      ...remainingPages.flatMap((response) => response.data.data.records ?? []),
    ];
    students.value = allStudents;

    const currentSelectedStudentId = selectedStudent.value?.id;
    const previouslySelectedStudent = currentSelectedStudentId
      ? allStudents.find((student) => student.id === currentSelectedStudentId)
      : undefined;
    if (previouslySelectedStudent) {
      selectedStudent.value = previouslySelectedStudent;
    } else if (currentSelectedStudentId) {
      clearSelection();
    }

    if (!selectedStudent.value && students.value.length > 0) {
      await selectStudent(students.value[0]);
    }
  } catch (error) {
    if (requestSequence === studentLoadSequence) {
      ElMessage.error(extractErrorMessage(error));
    }
  } finally {
    if (requestSequence === studentLoadSequence) {
      studentLoading.value = false;
    }
  }
}

async function selectStudent(student: StudentItem) {
  selectedStudent.value = student;
  orderQuery.pageNum = 1;
  clearOrders();
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
  refund: 'REFUND_RECEIPT',
} as const;

async function loadSelectedStudentReceipts() {
  if (!selectedStudent.value) {
    receipts.value = [];
    receiptTotal.value = 0;
    return;
  }
  const requestSequence = ++receiptLoadSequence;
  const studentId = selectedStudent.value.id;
  const templateCode = receiptTemplateCodes[receiptType.value];
  const pageNum = orderQuery.pageNum;
  receiptLoading.value = true;
  try {
    const res = await fetchReceipts({
      studentId,
      templateCode,
      pageNum,
      pageSize: orderQuery.pageSize,
    });
    if (requestSequence !== receiptLoadSequence) return;
    receipts.value = res.data.data.records ?? [];
    receiptTotal.value = res.data.data.total ?? 0;
  } catch (error) {
    if (requestSequence === receiptLoadSequence) {
      ElMessage.error(extractErrorMessage(error));
    }
  } finally {
    if (requestSequence === receiptLoadSequence) {
      receiptLoading.value = false;
    }
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
  receiptLoadSequence += 1;
  receiptLoading.value = false;
  receipts.value = [];
  receiptTotal.value = 0;
}

function openReceipt(row: ReceiptRecordItem) {
  if (row.templateCode === 'REFUND_RECEIPT') {
    void router.push({ name: 'refund-receipt', params: { receiptId: String(row.id) } });
    return;
  }

  // 报名收据依赖订单快照；退款收据走独立快照页，避免关联订单删除后无法查看。
  const orderId = receiptPayload(row).enrollmentOrderId || row.enrollmentOrderId;
  router.push({
    name: 'registration',
    query: { orderId: String(orderId), receiptId: String(row.id) },
  });
}

function receiptPayload(row: ReceiptRecordItem) {
  return parseReceiptPayload(row.receiptPayload);
}

function receiptSummary(row: ReceiptRecordItem) {
  const payload = receiptPayload(row);
  const payments = Array.isArray(payload.payments) ? payload.payments : [];
  const lastPayment = payments.length > 0 ? asRecord(payments[payments.length - 1]) : {};
  return {
    courses: receiptCourseOptions(row),
    amountDue: numberValue(payload.amountDue),
    paidAmount: numberValue(payload.paidAmount ?? payload.receivedAmount),
    balanceAmount: numberValue(payload.balanceAmount),
    paymentAmount: numberValue(payload.paymentAmount ?? lastPayment.amount),
    paymentMethod: String(payload.paymentMethod ?? lastPayment.paymentMethod ?? '').trim(),
  };
}

function receiptCourseOptions(row: ReceiptRecordItem) {
  const rawItems = receiptPayload(row).items;
  if (!Array.isArray(rawItems)) return [];
  const activeItemIds = new Set((row.activeCourseItemIds ?? []).map((id) => String(id)));
  return rawItems.flatMap((raw, index) => {
    const item = asRecord(raw);
    const itemId = item.orderItemId ?? item.id;
    if (itemId == null || String(itemId).trim() === '') return [];
    if (!activeItemIds.has(String(itemId))) return [];
    const label = [item.courseProductName, item.registerClass, item.detail, item.gradeBand, item.periodLabel]
      .map((value) => String(value ?? '').trim())
      .filter(Boolean)
      .filter((value, valueIndex, values) => values.indexOf(value) === valueIndex)
      .join(' · ') || 'Course';
    return [{ key: String(itemId), label: `${label}${rawItems.length > 1 ? ` (Course ${index + 1})` : ''}` }];
  });
}

function hasReceiptCourses(row: ReceiptRecordItem) {
  const rawItems = receiptPayload(row).items;
  return Array.isArray(rawItems) && rawItems.some((raw) => {
    const item = asRecord(raw);
    const itemId = item.orderItemId ?? item.id;
    return itemId != null && String(itemId).trim() !== '';
  });
}

function canDeleteCourse(row: ReceiptRecordItem) {
  return receiptType.value === 'registration'
    && row.enrollmentOrderActive === true
    && receiptCourseOptions(row).length > 0;
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function numberValue(value: unknown) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function openDeleteCourseDialog(row: ReceiptRecordItem) {
  deleteTargetReceipt.value = row;
  deleteTargetItemId.value = receiptCourseOptions(row)[0]?.key ?? '';
  deleteCourseDialogVisible.value = true;
}

function resetDeleteCourseDialog() {
  if (deletingCourse.value) return;
  deleteTargetReceipt.value = null;
  deleteTargetItemId.value = '';
}

async function confirmDeleteCourse() {
  const receipt = deleteTargetReceipt.value;
  if (!receipt || !deleteTargetItemId.value) return;
  deletingCourse.value = true;
  try {
    await deleteEnrollmentOrderItem(receipt.enrollmentOrderId, deleteTargetItemId.value);
    ElMessage.success('Course enrollment deleted.');
    deleteCourseDialogVisible.value = false;
    await loadSelectedStudentRecords();
    if (receipts.value.length === 0 && orderQuery.pageNum > 1) {
      orderQuery.pageNum -= 1;
      await loadSelectedStudentRecords();
    }
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    deletingCourse.value = false;
  }
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
  return formatRoundedUsd(value);
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
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.receipt-course-list {
  display: grid;
  gap: 3px;
  line-height: 1.35;
}

.muted-value,
.delete-course-note {
  color: var(--el-text-color-secondary);
}

.delete-course-student {
  margin: 16px 0 12px;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.delete-course-select {
  width: 100%;
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
