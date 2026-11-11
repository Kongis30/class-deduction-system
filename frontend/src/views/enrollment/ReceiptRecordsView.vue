<template>
  <div class="page-stack receipt-records-page">
    <section class="hero-card table-page-header">
      <div>
        <span class="hero-card__eyebrow table-page-header__eyebrow">Receipts</span>
        <h2>Student Receipts</h2>
        <p>Review saved registration and refund receipts.</p>
      </div>
      <el-button class="action-primary" type="primary" :loading="studentLoading || receiptLoading" @click="refreshReceiptData">Refresh</el-button>
    </section>

    <section v-if="appStore.isMobileViewport" class="mobile-receipt-toolbar" aria-label="Receipt filters">
      <el-button class="mobile-student-picker-trigger" @click="studentPickerVisible = true">
        <span class="mobile-student-picker-trigger__text">
          <small>Student</small>
          <strong>{{ selectedStudent?.studentName || 'Select student' }}</strong>
        </span>
        <el-tag v-if="selectedStudent" size="small" effect="plain">{{ selectedStudent.receiptCount ?? 0 }}</el-tag>
        <el-icon><ArrowDown /></el-icon>
      </el-button>
      <el-button class="mobile-refresh-button" :loading="studentLoading || receiptLoading" aria-label="Refresh receipts" @click="refreshReceiptData">
        <el-icon><Refresh /></el-icon>
      </el-button>
    </section>

    <el-row class="receipt-workspace" :gutter="16">
      <el-col v-if="!appStore.isMobileViewport" :xs="24" :sm="8" :md="6" :lg="6" :xl="5">
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
                <el-tag size="small" effect="plain">{{ student.receiptCount ?? 0 }}</el-tag>
              </el-menu-item>
            </el-menu>

            <el-empty v-else-if="!studentLoading" description="No students found" :image-size="72" />
          </el-scrollbar>

        </section>
      </el-col>

      <el-col :xs="24" :sm="16" :md="18" :lg="18" :xl="19">
        <section class="panel-card records-panel">
          <el-tabs v-model="receiptType" class="receipt-type-tabs" @tab-change="handleReceiptTypeChange">
            <el-tab-pane :label="appStore.isMobileViewport ? 'Registration' : 'Registration receipts'" name="registration" />
            <el-tab-pane :label="appStore.isMobileViewport ? 'Refund' : 'Refund receipts'" name="refund" />
          </el-tabs>

          <div class="records-header" :class="{ 'records-header--mobile': appStore.isMobileViewport }">
            <div v-if="!appStore.isMobileViewport">
              <span>Selected student</span>
              <strong>{{ selectedStudent?.studentName || 'No student selected' }}</strong>
            </div>
            <p v-if="selectedStudent">
              {{ recordTotal }} {{ receiptType === 'refund' ? 'refund' : 'registration' }} receipt{{ recordTotal === 1 ? '' : 's' }}
            </p>
            <p v-else>Select a student to view receipts.</p>
            <span v-if="appStore.isMobileViewport && recordTotal > 0" class="mobile-table-scroll-hint">Swipe · pinch to zoom</span>
          </div>

          <div
            ref="receiptTableScrollRef"
            class="receipt-table-scroll mobile-pinch-scroll"
            :class="{ 'receipt-table-scroll--empty': receipts.length === 0 }"
            role="region"
            aria-label="Receipt records table"
            tabindex="0"
          >
          <el-table
            v-loading="receiptLoading"
            :data="receipts"
            row-key="id"
            :class="[
              'receipt-order-table',
              'product-table',
              { 'mobile-receipt-table': appStore.isMobileViewport },
            ]"
            :style="appStore.isMobileViewport ? { zoom: mobileTableScale } : undefined"
            :empty-text="`No ${receiptType} receipts for this student.`"
          >
            <el-table-column v-if="appStore.isMobileViewport" label="Receipt / date" min-width="150" fixed="left">
              <template #default="{ row }">
                <div class="receipt-mobile-id">
                  <strong>{{ row.receiptNo }}</strong>
                  <small>{{ formatDateTime(row.issuedAt) }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-else prop="receiptNo" label="Receipt no." min-width="170" />
            <el-table-column v-if="!appStore.isMobileViewport" prop="studentName" label="Student" min-width="150" />
            <el-table-column v-if="receiptType === 'registration'" label="Course" :min-width="appStore.isMobileViewport ? 150 : 230">
              <template #default="{ row }">
                <el-tooltip
                  v-if="appStore.isMobileViewport"
                  :content="receiptSummary(row).courses.map((course) => course.label).join(' · ') || 'Credit top-up'"
                  placement="top"
                  trigger="click"
                >
                  <span class="receipt-course-name">
                    {{ receiptSummary(row).courses[0]?.label || 'Credit top-up' }}<small v-if="receiptSummary(row).courses.length > 1"> +{{ receiptSummary(row).courses.length - 1 }}</small>
                  </span>
                </el-tooltip>
                <div v-else class="receipt-course-list">
                  <span v-for="course in receiptSummary(row).courses" :key="course.key">{{ course.label }}</span>
                  <span v-if="receiptSummary(row).courses.length === 0" class="muted-value">Credit top-up</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="receiptType === 'registration' && !appStore.isMobileViewport" label="Amount due" min-width="120" align="right">
              <template #default="{ row }">{{ formatMoney(receiptSummary(row).amountDue) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType === 'registration' && !appStore.isMobileViewport" label="Paid" min-width="110" align="right">
              <template #default="{ row }">{{ formatMoney(receiptSummary(row).paidAmount) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType === 'registration' && !appStore.isMobileViewport" label="Balance" min-width="110" align="right">
              <template #default="{ row }">{{ formatMoney(receiptSummary(row).balanceAmount) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType === 'registration' && appStore.isMobileViewport" label="Paid / bal." min-width="104" align="center">
              <template #default="{ row }">
                <div class="receipt-mobile-finance">
                  <strong>{{ formatMoney(receiptSummary(row).paidAmount) }}</strong>
                  <small>Bal. {{ formatMoney(receiptSummary(row).balanceAmount) }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="receiptType !== 'registration'" label="Payment method" :min-width="appStore.isMobileViewport ? 140 : 140">
              <template #default="{ row }">{{ receiptSummary(row).paymentMethod || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType !== 'registration'" :label="receiptType === 'refund' ? 'Refund amount' : 'Payment amount'" :min-width="appStore.isMobileViewport ? 104 : 135" align="right">
              <template #default="{ row }">{{ formatMoney(Math.abs(receiptSummary(row).paymentAmount)) }}</template>
            </el-table-column>
            <el-table-column v-if="receiptType !== 'registration'" label="Balance after" :min-width="appStore.isMobileViewport ? 104 : 130" align="right">
              <template #default="{ row }">{{ formatMoney(receiptSummary(row).balanceAmount) }}</template>
            </el-table-column>
            <el-table-column v-if="!appStore.isMobileViewport" label="Date" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.issuedAt) }}</template>
            </el-table-column>
            <el-table-column label="Actions" :width="appStore.isMobileViewport ? 56 : receiptType === 'registration' ? 220 : 170" fixed="right" align="center">
              <template #default="{ row }">
                <div v-if="!appStore.isMobileViewport" class="row-actions">
                  <el-button link type="primary" @click="openReceipt(row)">View</el-button>
                  <el-button v-if="canDeleteRefund(row)" link type="danger" @click="confirmDeleteRefund(row)">
                    Undo refund
                  </el-button>
                  <el-button
                    v-if="canDeleteCourse(row)"
                    link
                    type="danger"
                    @click="openDeleteCourseDialog(row)"
                  >
                    Delete course
                  </el-button>
                  <el-button
                    v-if="canDeleteCreditTopUp(row)"
                    link
                    type="danger"
                    @click="openDeleteCreditTopUpDialog(row)"
                  >
                    Delete top-up
                  </el-button>
                  <span
                    v-if="receiptType === 'registration' && !canDeleteCourse(row) && !canDeleteCreditTopUp(row) && hasReceiptCourses(row)"
                    class="muted-value"
                    :title="row.enrollmentOrderActive ? 'This receipt has no active course enrollment.' : 'The enrollment order is no longer active.'"
                  >
                    View only
                  </span>
                </div>
                <el-dropdown v-else trigger="click" placement="bottom-end">
                  <el-button class="mobile-row-actions-trigger" text circle :aria-label="`More receipt actions for ${row.receiptNo}`">
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="openReceipt(row)">View receipt</el-dropdown-item>
                      <el-dropdown-item v-if="canDeleteRefund(row)" divided @click="confirmDeleteRefund(row)">Undo refund</el-dropdown-item>
                      <el-dropdown-item v-if="canDeleteCourse(row)" divided @click="openDeleteCourseDialog(row)">Delete course</el-dropdown-item>
                      <el-dropdown-item v-if="canDeleteCreditTopUp(row)" divided @click="openDeleteCreditTopUpDialog(row)">Delete top-up</el-dropdown-item>
                      <el-dropdown-item v-if="receiptType === 'registration' && !canDeleteCourse(row) && !canDeleteCreditTopUp(row) && hasReceiptCourses(row)" disabled>View only</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
          </div>

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

          <el-dialog
            v-model="deleteCreditTopUpDialogVisible"
            title="Delete Credit top-up receipt"
            width="520px"
            :close-on-click-modal="!deletingCreditTopUp"
            :close-on-press-escape="!deletingCreditTopUp"
            @closed="resetDeleteCreditTopUpDialog"
          >
            <el-alert
              v-if="creditBalance != null && deleteCreditTopUpTarget"
              type="warning"
              :closable="false"
              show-icon
              :title="creditTopUpReceiptOnly
                ? 'This receipt will be hidden and available Credit cleared to $0. The payment, order, and Credit already used for courses will remain.'
                : 'The receipt, payment, and order will be removed. The Credit added by this top-up will be reversed.'"
            />
            <p class="delete-course-student">
              {{ deleteCreditTopUpTarget?.studentName }} · {{ deleteCreditTopUpTarget?.receiptNo }}
            </p>
            <div class="top-up-delete-balance" v-loading="creditBalanceLoading">
              <div><span>Top-up amount</span><strong>{{ formatMoney(deleteCreditTopUpTarget ? creditTopUpAmount(deleteCreditTopUpTarget) : 0) }}</strong></div>
              <div><span>Available Credit</span><strong>{{ creditBalance == null ? 'Loading…' : formatMoney(creditBalance) }}</strong></div>
            </div>
            <p v-if="creditTopUpReceiptOnly" class="delete-course-note">
              The unused {{ formatMoney(creditBalance ?? 0) }} Credit will be removed. Previous course payments stay paid.
            </p>
            <template #footer>
              <el-button :disabled="deletingCreditTopUp" @click="deleteCreditTopUpDialogVisible = false">Cancel</el-button>
              <el-button
                type="danger"
                :loading="deletingCreditTopUp"
                :disabled="creditBalanceLoading || creditBalance == null || !deleteCreditTopUpTarget"
                @click="confirmDeleteCreditTopUp"
              >
                {{ creditTopUpReceiptOnly ? 'Delete receipt' : 'Delete top-up' }}
              </el-button>
            </template>
          </el-dialog>

          <el-pagination
            v-if="selectedStudent && (!appStore.isMobileViewport || recordTotal > orderQuery.pageSize)"
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

    <el-drawer
      v-if="appStore.isMobileViewport"
      v-model="studentPickerVisible"
      direction="btt"
      size="82%"
      :with-header="false"
      class="receipt-student-picker"
    >
      <section class="receipt-student-picker__content">
        <header class="receipt-student-picker__header">
          <div><strong>Select student</strong><span>{{ students.length }} students with receipts</span></div>
          <el-button text circle aria-label="Close student picker" @click="studentPickerVisible = false"><el-icon><Close /></el-icon></el-button>
        </header>
        <el-input
          v-model="studentQuery.keyword"
          class="student-search"
          placeholder="Find a student"
          clearable
          @clear="handleStudentSearch"
          @keyup.enter="handleStudentSearch"
        >
          <template #append><el-button type="primary" @click="handleStudentSearch">Search</el-button></template>
        </el-input>
        <el-scrollbar v-loading="studentLoading" class="receipt-student-picker__list">
          <button
            v-for="student in students"
            :key="student.id"
            type="button"
            class="receipt-student-picker__row"
            :class="{ 'is-selected': student.id === selectedStudent?.id }"
            @click="chooseStudent(student.id)"
          >
            <strong>{{ student.studentName }}</strong>
            <span>{{ student.receiptCount ?? 0 }}</span>
          </button>
          <el-empty v-if="!studentLoading && students.length === 0" description="No students found" :image-size="64" />
        </el-scrollbar>
      </section>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, Close, MoreFilled, Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAppStore } from '@/stores/app';

import { fetchStudents } from '@/api/base-data';
import { deleteCreditTopUpReceipt, deleteEnrollmentOrderItem, deleteRefundReceipt, fetchReceipts } from '@/api/enrollment';
import { extractErrorMessage, preprocessLargeIds } from '@/api/http';
import { fetchStudentCreditBalance } from '@/api/student-credit';
import { useAuthStore } from '@/stores/auth';
import { isAdminOrStaffUser } from '@/types/auth';
import type { ReceiptRecordItem, StudentItem } from '@/types/domain';
import { formatRoundedUsd } from '@/utils/money-display';
import { useMobileTablePinchZoom } from '@/composables/useMobileTablePinchZoom';

const router = useRouter();
const appStore = useAppStore();
const authStore = useAuthStore();
const canManageCreditTopUpReceipts = computed(() => isAdminOrStaffUser(authStore.currentUser));
const studentPickerVisible = ref(false);
const receiptTableScrollRef = ref<HTMLElement | null>(null);
const { scale: mobileTableScale } = useMobileTablePinchZoom(
  receiptTableScrollRef,
  computed(() => appStore.isMobileViewport),
  0.72,
);

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
const deleteCreditTopUpDialogVisible = ref(false);
const deletingCreditTopUp = ref(false);
const creditBalanceLoading = ref(false);
const creditBalance = ref<number | null>(null);
const deleteCreditTopUpTarget = ref<ReceiptRecordItem | null>(null);
let creditBalanceLoadSequence = 0;
const creditTopUpReceiptOnly = computed(() => deleteCreditTopUpTarget.value != null
  && creditBalance.value != null
  && creditBalance.value < creditTopUpAmount(deleteCreditTopUpTarget.value));

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

async function refreshReceiptData() {
  await loadStudents();
  if (selectedStudent.value) await loadSelectedStudentRecords();
}

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

function chooseStudent(studentId: string) {
  selectStudentById(studentId);
  studentPickerVisible.value = false;
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
  // 退款收据属于订单的财务流水；View 始终回到该订单的原始报名收据页面。
  const orderId = receiptPayload(row).enrollmentOrderId || row.enrollmentOrderId;
  if (!orderId) {
    ElMessage.error('The original enrollment receipt could not be found.');
    return;
  }
  router.push({
    name: 'registration',
    query: {
      orderId: String(orderId),
      ...(row.templateCode === 'REFUND_RECEIPT' ? {} : { receiptId: String(row.id) }),
    },
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

function creditTopUpAmount(row: ReceiptRecordItem) {
  return numberValue(receiptPayload(row).creditTopUpAmount);
}

function isCreditTopUpReceipt(row: ReceiptRecordItem) {
  const payload = receiptPayload(row);
  return row.templateCode === 'DEFAULT_RECEIPT'
    && creditTopUpAmount(row) > 0
    && Array.isArray(payload.items)
    && payload.items.length === 0;
}

function canDeleteCreditTopUp(row: ReceiptRecordItem) {
  return canManageCreditTopUpReceipts.value
    && receiptType.value === 'registration'
    && row.enrollmentOrderActive === true
    && row.studentId != null
    && isCreditTopUpReceipt(row);
}

function canDeleteRefund(row: ReceiptRecordItem) {
  return canManageCreditTopUpReceipts.value
    && receiptType.value === 'refund'
    && row.templateCode === 'REFUND_RECEIPT'
    && row.paymentRecordId != null
    && row.enrollmentOrderActive === true;
}

async function confirmDeleteRefund(row: ReceiptRecordItem) {
  try {
    await ElMessageBox.confirm(
      'Undo this refund? The course, package, and future lessons will return to their state before the refund, and the related student Credit will be reversed. The undo is rejected if lessons or schedule changes were recorded after the refund. Money already sent back outside the system is not recovered.',
      `Undo refund receipt ${row.receiptNo}?`,
      {
        type: 'warning',
        confirmButtonText: 'Undo refund',
        cancelButtonText: 'Cancel',
        distinguishCancelAndClose: true,
      },
    );
  } catch {
    return;
  }

  receiptLoading.value = true;
  try {
    const response = await deleteRefundReceipt(row.id);
    ElMessage.success(response.data.message || 'Refund undone.');
    await loadStudents();
    await loadSelectedStudentRecords();
    if (receipts.value.length === 0 && orderQuery.pageNum > 1) {
      orderQuery.pageNum -= 1;
      await loadSelectedStudentRecords();
    }
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    receiptLoading.value = false;
  }
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

async function openDeleteCreditTopUpDialog(row: ReceiptRecordItem) {
  deleteCreditTopUpTarget.value = row;
  creditBalance.value = null;
  deleteCreditTopUpDialogVisible.value = true;
  const requestSequence = ++creditBalanceLoadSequence;
  creditBalanceLoading.value = true;
  try {
    const response = await fetchStudentCreditBalance(row.studentId);
    if (requestSequence === creditBalanceLoadSequence) {
      const balance = Number(response.data.data);
      creditBalance.value = Number.isFinite(balance) ? balance : null;
    }
  } catch (error) {
    if (requestSequence === creditBalanceLoadSequence) {
      ElMessage.error(extractErrorMessage(error));
    }
  } finally {
    if (requestSequence === creditBalanceLoadSequence) creditBalanceLoading.value = false;
  }
}

function resetDeleteCreditTopUpDialog() {
  if (deletingCreditTopUp.value) return;
  creditBalanceLoadSequence += 1;
  deleteCreditTopUpTarget.value = null;
  creditBalance.value = null;
}

async function confirmDeleteCreditTopUp() {
  const receipt = deleteCreditTopUpTarget.value;
  if (!receipt || creditBalance.value == null) return;
  deletingCreditTopUp.value = true;
  try {
    const response = await deleteCreditTopUpReceipt(receipt.enrollmentOrderId, receipt.id, creditBalance.value.toFixed(2));
    ElMessage.success(response.data.message);
    deleteCreditTopUpDialogVisible.value = false;
    await Promise.all([loadSelectedStudentRecords(), loadStudents()]);
    if (receipts.value.length === 0 && orderQuery.pageNum > 1) {
      orderQuery.pageNum -= 1;
      await loadSelectedStudentRecords();
    }
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    deletingCreditTopUp.value = false;
    if (!deleteCreditTopUpDialogVisible.value) resetDeleteCreditTopUpDialog();
  }
}

function parseReceiptPayload(value: unknown): Record<string, unknown> {
  if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  if (typeof value !== 'string' || !value.trim()) return {};
  try {
    // 收据快照中的雪花 ID 可能超过 JS 安全整数范围；先转为字符串再解析。
    const parsed = JSON.parse(preprocessLargeIds(value));
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
.mobile-row-actions-trigger { width: 32px; height: 32px; margin: 0; }
.receipt-table-scroll { width: 100%; min-width: 0; overflow: auto; overscroll-behavior: contain; -webkit-overflow-scrolling: touch; }
.mobile-table-scroll-hint { margin: 0 0 4px; color: #7b8797; font-size: 10px; }
.mobile-receipt-toolbar { display: none; }

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

.top-up-delete-balance {
  display: grid;
  gap: 10px;
  margin: 16px 0;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.top-up-delete-balance > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.top-up-delete-balance span {
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

html.mobile-layout & {
  .receipt-records-page { gap: 8px; padding-bottom: calc(78px + env(safe-area-inset-bottom)); }
  .receipt-records-page > .table-page-header { display: none; }
  .mobile-receipt-toolbar { display: flex; align-items: stretch; gap: 8px; }
  .mobile-student-picker-trigger { display: flex; min-width: 0; height: 44px; flex: 1 1 auto; align-items: center; justify-content: flex-start; gap: 8px; padding: 4px 10px; border-color: #d6e0ec; background: #fff; }
  .mobile-student-picker-trigger__text { display: grid; min-width: 0; flex: 1 1 auto; gap: 1px; text-align: left; }
  .mobile-student-picker-trigger__text small { color: #77869a; font-size: 10px; line-height: 1.1; }
  .mobile-student-picker-trigger__text strong { overflow: hidden; color: #20364d; font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
  .mobile-student-picker-trigger :deep(.el-tag) { flex: 0 0 auto; }
  .mobile-student-picker-trigger :deep(.el-icon) { color: #73849a; }
  .mobile-refresh-button { width: 44px; height: 44px; flex: 0 0 44px; padding: 0; border-color: #d6e0ec; background: #fff; }
  .receipt-workspace { margin: 0 !important; }
  .receipt-workspace > .el-col { width: 100%; max-width: 100%; padding: 0 !important; }
  .records-panel { display: flex; height: auto; min-height: 0; flex-direction: column; gap: 2px; padding: 8px; }
  .receipt-type-tabs { position: sticky; top: 0; z-index: 5; margin: -2px 0 0; padding: 0 2px; background: #fff; }
  .page-stack.receipt-records-page .records-panel .receipt-type-tabs :deep(.el-tabs__header) { margin: 0 0 3px !important; padding: 2px; }
  .page-stack.receipt-records-page .records-panel .receipt-type-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
  .page-stack.receipt-records-page .records-panel .receipt-type-tabs :deep(.el-tabs__nav-wrap) { overflow: visible; }
  .page-stack.receipt-records-page .records-panel .receipt-type-tabs :deep(.el-tabs__nav) { display: flex; width: 100%; min-width: 0; gap: 3px; padding: 2px; border-radius: 8px; background: #eef3f8; }
  .page-stack.receipt-records-page .records-panel .receipt-type-tabs :deep(.el-tabs__item) { height: 32px; flex: 1 1 50%; padding: 0 8px; border-radius: 6px; color: #5e6e82; font-size: 12px; line-height: 32px; }
  .page-stack.receipt-records-page .records-panel .receipt-type-tabs :deep(.el-tabs__item.is-active) { background: #fff; color: #3478df; box-shadow: 0 1px 3px rgb(31 52 74 / 12%); }
  .page-stack.receipt-records-page .records-panel .receipt-type-tabs :deep(.el-tabs__active-bar) { display: none; }
  .receipt-type-tabs :deep(.el-tabs__content) { display: none; }
  .records-header { min-height: 18px; align-items: center; justify-content: space-between; gap: 8px; margin: 0 2px 3px; }
  .records-header p { color: #708197; font-size: 11px; white-space: nowrap; }
  .mobile-table-scroll-hint { margin: 0; color: #8391a3; font-size: 9px; white-space: nowrap; }
  .receipt-table-scroll { max-height: max(260px, calc(100dvh - 340px)); min-height: 0; touch-action: pan-x pan-y; }
  .receipt-table-scroll--empty { min-height: 88px; }
  .mobile-receipt-table { width: max-content; min-width: 100%; max-width: none; font-size: 11px; }
  .mobile-receipt-table :deep(.el-table__header-wrapper th) { height: 36px; padding: 4px 6px; white-space: nowrap; }
  .mobile-receipt-table :deep(.el-table__cell) { padding: 4px 6px; }
  .mobile-receipt-table :deep(.cell) { padding: 0; font-size: 11px; white-space: nowrap; }
  .mobile-receipt-table :deep(.el-tag) { height: 20px; padding: 0 5px; font-size: 10px; line-height: 18px; }
  .mobile-receipt-table :deep(.el-table__header-wrapper),
  .mobile-receipt-table :deep(.el-table__fixed-header-wrapper) { position: sticky; top: 0; z-index: 4; }
  .mobile-receipt-table :deep(.el-table__fixed-left),
  .mobile-receipt-table :deep(.el-table__fixed-right) { z-index: 6; }
  .mobile-receipt-table :deep(.el-table__fixed-right) { box-shadow: -4px 0 8px rgb(31 63 54 / 8%); }
  .mobile-receipt-table :deep(.el-table__row) { height: 42px; }
  .receipt-mobile-id,
  .receipt-mobile-finance { display: grid; min-width: 0; gap: 1px; line-height: 1.15; }
  .receipt-course-name { display: block; overflow: hidden; color: #33485f; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
  .receipt-course-name small { margin-left: 3px; color: #708197; font-size: 10px; }
  .receipt-mobile-id strong,
  .receipt-mobile-finance strong { overflow: hidden; color: #31445b; font-size: 11px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
  .receipt-mobile-id small,
  .receipt-mobile-finance small { overflow: hidden; color: #8190a3; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
  .records-pagination { justify-content: center; margin-top: 8px; }
  .records-pagination :deep(.el-pagination__total) { margin-left: 4px; }
  :global(.el-drawer.receipt-student-picker) { border-radius: 14px 14px 0 0; }
  :global(.el-drawer.receipt-student-picker .el-drawer__body) { display: flex; min-height: 0; flex-direction: column; padding: 0; }
  .receipt-student-picker__content { display: flex; min-height: 0; height: 100%; flex-direction: column; padding: 12px 14px calc(10px + env(safe-area-inset-bottom)); }
  .receipt-student-picker__header { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
  .receipt-student-picker__header > div { display: grid; gap: 2px; }
  .receipt-student-picker__header strong { color: #20364d; font-size: 16px; }
  .receipt-student-picker__header span { color: #8492a5; font-size: 11px; }
  .receipt-student-picker__content .student-search { margin: 0 0 8px; }
  .receipt-student-picker__list { min-height: 0; flex: 1 1 auto; border-top: 1px solid #e5eaf1; }
  .receipt-student-picker__row { display: flex; width: 100%; min-height: 42px; align-items: center; justify-content: space-between; gap: 10px; padding: 0 8px; border: 0; border-bottom: 1px solid #edf0f4; background: #fff; color: #2b3c50; text-align: left; }
  .receipt-student-picker__row strong { overflow: hidden; font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
  .receipt-student-picker__row span { min-width: 26px; color: #71839a; font-size: 11px; text-align: right; }
  .receipt-student-picker__row.is-selected { background: #eef5ff; color: #3478df; }
}
</style>
