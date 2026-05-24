<template>
  <div class="tp-page">
    <!-- 左侧老师列表 -->
    <aside class="tp-sidebar">
      <div class="tp-sidebar__search">
        <el-input v-model="teacherKeyword" placeholder="Search teacher..." clearable size="small" />
      </div>
      <div class="tp-sidebar__list" v-loading="teachersLoading">
        <div
          v-for="t in filteredTeachers"
          :key="t.id"
          class="tp-sidebar__item"
          :class="{ 'tp-sidebar__item--active': selectedTeacherId === t.id }"
          @click="selectTeacher(t.id)"
        >
          <div class="tp-sidebar__name">{{ t.teacherName }}</div>
          <div class="tp-sidebar__meta">{{ t.teacherNo }}</div>
        </div>
        <div v-if="!teachersLoading && filteredTeachers.length === 0" class="tp-sidebar__empty">
          No teachers found.
        </div>
      </div>
    </aside>

    <!-- 右侧内容 -->
    <main class="tp-content">
      <div v-if="!selectedTeacherId" class="tp-content__placeholder">
        Select a teacher from the left panel to view their payment records.
      </div>

      <template v-else-if="selectedTeacher">
        <div class="tp-content__header">
          <div class="tp-content__title">
            <strong>{{ selectedTeacher.teacherName }}</strong>
            <span class="tp-content__no">{{ selectedTeacher.teacherNo }}</span>
          </div>
        </div>

        <!-- ─── 1. Period filter ─────────────────────── -->
        <section class="tp-section tp-section--filters">
          <div class="tp-filters">
            <div class="tp-filters__group">
              <label class="tp-filters__label">Period</label>
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="–"
                start-placeholder="Start"
                end-placeholder="End"
                value-format="YYYY-MM-DD"
                style="width: 260px;"
              />
            </div>
            <div class="tp-filters__actions">
              <el-button
                type="primary"
                :loading="unpaidLoading"
                @click="loadUnpaidLessons"
              >
                Load Unpaid Lessons
              </el-button>
            </div>
          </div>
        </section>

        <!-- ─── 2. Unpaid 1:1 Lessons ────────────────── -->
        <section v-if="individualLessons.length > 0" class="tp-section tp-section--card">
          <div class="tp-section__header">
            <span class="tp-section__title">1:1 Lessons</span>
            <span v-if="individualTotal" class="tp-section__subtitle">
              {{ individualLessons.length }} lesson(s) · {{ individualTotal }}
            </span>
          </div>

          <el-table
            v-loading="unpaidLoading"
            :data="individualLessons"
            row-key="lessonRecordId"
            class="tp-table"
            @selection-change="individualSelections = $event; updateMergedSelection()"
          >
            <el-table-column type="selection" width="36" />
            <el-table-column label="Date" min-width="105">
              <template #default="{ row }">{{ row.lessonDate }}</template>
            </el-table-column>
            <el-table-column label="Type" width="58" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="primary">1:1</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Student" min-width="110" prop="studentName" />
            <el-table-column label="Product" min-width="120" prop="productName" show-overflow-tooltip />
            <el-table-column label="Hours" width="65" align="center" prop="hours" />
            <el-table-column label="Rate" width="90" align="right">
              <template #default="{ row }">${{ row.hourlyRate?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Amount" width="100" align="right">
              <template #default="{ row }">
                <strong>${{ row.amount?.toFixed(2) }}</strong>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- ─── 3. Unpaid Group Class Lessons ─────────── -->
        <section v-if="groupLessons.length > 0" class="tp-section tp-section--card">
          <div class="tp-section__header">
            <span class="tp-section__title">Group Class Lessons</span>
            <span v-if="groupTotal" class="tp-section__subtitle">
              {{ groupLessons.length }} lesson(s) · {{ groupTotal }}
            </span>
          </div>

          <el-table
            v-loading="unpaidLoading"
            :data="groupLessons"
            row-key="lessonRecordId"
            class="tp-table"
            @selection-change="groupSelections = $event; updateMergedSelection()"
          >
            <el-table-column type="selection" width="36" />
            <el-table-column label="Date" min-width="105">
              <template #default="{ row }">{{ row.lessonDate }}</template>
            </el-table-column>
            <el-table-column label="Type" width="58" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="warning">G</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Class" min-width="120" prop="className" show-overflow-tooltip />
            <el-table-column label="Product" min-width="120" prop="productName" show-overflow-tooltip />
            <el-table-column label="Hours" width="65" align="center" prop="hours" />
            <el-table-column label="Rate" width="90" align="right">
              <template #default="{ row }">${{ row.hourlyRate?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Amount" width="100" align="right">
              <template #default="{ row }">
                <strong>${{ row.amount?.toFixed(2) }}</strong>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- ─── 4. Summary & Create Payment ────────────── -->
        <section v-if="selectedIds.length > 0" class="tp-section tp-section--card">
          <div class="tp-summary">
            <span>
              Selected <strong>{{ selectedIds.length }}</strong> lessons ·
              Hours: <strong>{{ selectedTotalHours }}</strong> ·
              Amount: <strong>${{ selectedTotalAmount }}</strong>
            </span>
            <el-button
              type="primary"
              :loading="creatingPayment"
              @click="handleCreatePayment"
            >
              Create Payment
            </el-button>
          </div>
        </section>

        <el-empty
          v-else-if="searched && !unpaidLoading && individualLessons.length === 0 && groupLessons.length === 0 && !hasPendingPayment"
          :description="'No unpaid lessons found for this period.'"
          class="tp-empty"
        />

        <!-- ─── 6. Payment History ─────────────────────── -->
        <section class="tp-section tp-section--card" style="margin-top: 4px;">
          <div class="tp-section__header">
            <div class="tp-section__header-left">
              <span class="tp-section__title">Payment History</span>
              <span class="tp-section__subtitle">{{ pagination.total }} record(s)</span>
            </div>
            <div class="tp-history-filters">
              <el-select
                v-model="historyFilter.status"
                placeholder="Status"
                clearable
                style="width: 120px;"
                @change="loadHistory(1)"
              >
                <el-option label="Pending" value="PENDING" />
                <el-option label="Paid" value="PAID" />
              </el-select>
            </div>
          </div>

          <el-table
            v-loading="historyLoading"
            :data="historyData"
            row-key="id"
            class="tp-table"
          >
            <el-table-column label="Payment No" min-width="140" prop="paymentNo" />
            <el-table-column label="Period" min-width="155">
              <template #default="{ row }">
                {{ row.periodStart }} – {{ row.periodEnd }}
              </template>
            </el-table-column>
            <el-table-column label="Hours" width="65" align="center" prop="totalHours" />
            <el-table-column label="Amount" width="110" align="right">
              <template #default="{ row }">${{ row.totalAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Status" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PAID' ? 'success' : 'warning'" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Paid At" min-width="140">
              <template #default="{ row }">
                {{ row.paidAt ? formatDateTime(row.paidAt) : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="130" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="viewDetail(row)">Detail</el-button>
                <el-button
                  v-if="row.status === 'PENDING'"
                  text
                  type="success"
                  size="small"
                  @click="handleConfirm(row)"
                >
                  Confirm
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="pagination.total > pagination.size" class="tp-pagination">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              background
              layout="prev, pager, next, sizes"
              :page-sizes="[10, 20, 50]"
              :total="pagination.total"
              @current-change="loadHistory"
              @size-change="loadHistory"
            />
          </div>
        </section>

        <!-- ─── 7. Pending payment banner ────────────── -->
        <section v-if="hasPendingPayment" class="tp-section tp-section--card">
          <div class="tp-section__header">
            <span class="tp-section__title">
              <el-tag type="warning" size="small" style="margin-right: 6px;">PENDING</el-tag>
              Outstanding Payment
            </span>
          </div>
          <el-table
            v-loading="pendingLoading"
            :data="pendingPayments"
            row-key="id"
            class="tp-table"
          >
            <el-table-column label="Payment No" min-width="140" prop="paymentNo" />
            <el-table-column label="Period" min-width="155">
              <template #default="{ row }">
                {{ row.periodStart }} – {{ row.periodEnd }}
              </template>
            </el-table-column>
            <el-table-column label="Amount" width="110" align="right">
              <template #default="{ row }">${{ row.totalAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Actions" width="100" fixed="right">
              <template #default="{ row }">
                <el-button text type="success" size="small" @click="handleConfirm(row)">
                  Confirm
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </template>
    </main>

    <!-- ─── 详情弹窗 ────────────────────────────────── -->
    <el-dialog
      v-model="detailVisible"
      width="750px"
      :close-on-click-modal="false"
      class="tp-dialog"
    >
      <template #header>
        <h3>Payment Detail — {{ detailData?.paymentNo }}</h3>
      </template>

      <template v-if="detailData">
        <div class="tp-detail-summary">
          <div class="tp-detail-summary__item">
            <span class="label">Teacher</span>
            <span class="value">{{ detailData.teacherName }}</span>
          </div>
          <div class="tp-detail-summary__item">
            <span class="label">Period</span>
            <span class="value">{{ detailData.periodStart }} – {{ detailData.periodEnd }}</span>
          </div>
          <div class="tp-detail-summary__item">
            <span class="label">Total Hours</span>
            <span class="value">{{ detailData.totalHours }}</span>
          </div>
          <div class="tp-detail-summary__item">
            <span class="label">Total Amount</span>
            <span class="value">${{ detailData.totalAmount?.toFixed(2) }}</span>
          </div>
          <div class="tp-detail-summary__item">
            <span class="label">Status</span>
            <span class="value">
              <el-tag :type="detailData.status === 'PAID' ? 'success' : 'warning'" size="small">
                {{ detailData.status }}
              </el-tag>
            </span>
          </div>
          <div class="tp-detail-summary__item" v-if="detailData.paidAt">
            <span class="label">Paid At</span>
            <span class="value">{{ formatDateTime(detailData.paidAt) }}</span>
          </div>
        </div>

        <el-table
          v-if="detailData.items?.length"
          :data="detailData.items"
          row-key="id"
          class="tp-table"
          style="margin-top: 14px;"
        >
          <el-table-column label="Type" width="60" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.className" size="small" type="warning">G</el-tag>
              <el-tag v-else size="small" type="primary">1</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Package" min-width="130" prop="packageName" />
          <el-table-column label="Product" min-width="110" prop="productName" />
          <el-table-column label="Class" min-width="100" prop="className">
            <template #default="{ row }">{{ row.className || '—' }}</template>
          </el-table-column>
          <el-table-column label="Hours" width="60" align="center" prop="hours" />
          <el-table-column label="Rate" width="85" align="right">
            <template #default="{ row }">${{ row.hourlyRate?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="Amount" width="95" align="right">
            <template #default="{ row }">${{ row.amount?.toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">Close</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import {
  confirmPayment,
  createPayment,
  fetchPaymentDetail,
  fetchPayments,
  fetchUnpaidLessons,
} from '@/api/finance';
import { fetchAllTeachers } from '@/api/base-data';
import type { TeacherPaymentItem, UnpaidLessonItem } from '@/types/domain';

// ─── 老师列表 ──────────────────────────────────────────

const teacherKeyword = ref('');
const teachersLoading = ref(false);
const allTeachers = ref<{ id: number; teacherName: string; teacherNo: string }[]>([]);
const selectedTeacherId = ref<number | null>(null);

const filteredTeachers = computed(() => {
  const kw = teacherKeyword.value.toLowerCase().trim();
  if (!kw) return allTeachers.value;
  return allTeachers.value.filter(t =>
    t.teacherName.toLowerCase().includes(kw) ||
    t.teacherNo.toLowerCase().includes(kw),
  );
});

const selectedTeacher = computed(() =>
  allTeachers.value.find(t => t.id === selectedTeacherId.value),
);

async function selectTeacher(id: number) {
  selectedTeacherId.value = id;
  searched.value = false;
  unpaidLessons.value = [];
  selectedIds.value = [];
  loadHistory(1);
}

// ─── 日期范围 ─────────────────────────────────────────

const dateRange = ref<[string, string] | null>(null);

// ─── 未结算课次 ────────────────────────────────────

const unpaidLoading = ref(false);
const searched = ref(false);
const unpaidLessons = ref<UnpaidLessonItem[]>([]);
const individualSelections = ref<UnpaidLessonItem[]>([]);
const groupSelections = ref<UnpaidLessonItem[]>([]);
const selectedIds = ref<number[]>([]);

const individualLessons = computed(() =>
  unpaidLessons.value.filter(r => !r.className),
);

const groupLessons = computed(() =>
  unpaidLessons.value.filter(r => !!r.className),
);

const individualTotal = computed(() => {
  if (!individualLessons.value.length) return '';
  const h = individualLessons.value.reduce((s, r) => s + r.hours, 0);
  const a = individualLessons.value.reduce((s, r) => s + r.amount, 0);
  return `${h.toFixed(1)}h · $${a.toFixed(2)}`;
});

const groupTotal = computed(() => {
  if (!groupLessons.value.length) return '';
  const h = groupLessons.value.reduce((s, r) => s + r.hours, 0);
  const a = groupLessons.value.reduce((s, r) => s + r.amount, 0);
  return `${h.toFixed(1)}h · $${a.toFixed(2)}`;
});

const hasPendingPayment = computed(() =>
  pendingPayments.value.length > 0,
);

function updateMergedSelection() {
  const allRows = [...individualSelections.value, ...groupSelections.value];
  selectedIds.value = allRows.map(r => r.lessonRecordId);
}

const selectedTotalHours = computed(() => {
  const allRows = [...individualSelections.value, ...groupSelections.value];
  return allRows.reduce((s, r) => s + r.hours, 0).toFixed(1);
});

const selectedTotalAmount = computed(() => {
  const allRows = [...individualSelections.value, ...groupSelections.value];
  return allRows.reduce((s, r) => s + r.amount, 0).toFixed(2);
});

async function loadUnpaidLessons() {
  if (!selectedTeacherId.value) return;
  unpaidLoading.value = true;
  searched.value = true;
  try {
    const res = await fetchUnpaidLessons({
      teacherId: selectedTeacherId.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
    });
    unpaidLessons.value = res.data.data;
    selectedIds.value = [];
  } catch {
    ElMessage.error('Failed to load unpaid lessons.');
  } finally {
    unpaidLoading.value = false;
  }
}

// ─── 创建结算 ──────────────────────────────────────

const creatingPayment = ref(false);

async function handleCreatePayment() {
  if (!selectedTeacherId.value || selectedIds.value.length === 0) return;

  try {
    await ElMessageBox.confirm(
      `Create payment for ${selectedIds.value.length} lesson(s) — total $${selectedTotalAmount.value}?`,
      'Confirm payment',
      { type: 'info' },
    );
  } catch {
    return;
  }

  creatingPayment.value = true;
  try {
    await createPayment({
      teacherId: selectedTeacherId.value,
      periodStart: dateRange.value?.[0] || '',
      periodEnd: dateRange.value?.[1] || '',
      lessonRecordIds: selectedIds.value,
    });
    ElMessage.success('Payment created.');
    unpaidLessons.value = [];
    individualSelections.value = [];
    groupSelections.value = [];
    selectedIds.value = [];
    searched.value = false;
    loadHistory(1);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to create payment.');
  } finally {
    creatingPayment.value = false;
  }
}

// ─── 结算历史 ──────────────────────────────────────

const historyFilter = reactive({ status: undefined as string | undefined });

const historyLoading = ref(false);
const historyData = ref<TeacherPaymentItem[]>([]);
const pagination = reactive({ page: 1, size: 10, total: 0 });

async function loadHistory(pageNum?: number) {
  if (pageNum) pagination.page = pageNum;
  if (!selectedTeacherId.value) return;
  historyLoading.value = true;
  try {
    const res = await fetchPayments({
      teacherId: selectedTeacherId.value,
      status: historyFilter.status,
      pageNum: pagination.page,
      pageSize: pagination.size,
    });
    const data = res.data.data;
    historyData.value = data.records;
    pagination.total = data.total;
    loadPendingPayments();
  } catch {
    ElMessage.error('Failed to load payment history.');
  } finally {
    historyLoading.value = false;
  }
}

// ─── 待结算 payment 列表（PENDING 状态）───────────────

const pendingLoading = ref(false);
const pendingPayments = ref<TeacherPaymentItem[]>([]);

async function loadPendingPayments() {
  if (!selectedTeacherId.value) return;
  pendingLoading.value = true;
  try {
    const res = await fetchPayments({
      teacherId: selectedTeacherId.value,
      status: 'PENDING',
      pageNum: 1,
      pageSize: 100,
    });
    pendingPayments.value = res.data.data.records;
  } catch {
    // silent
  } finally {
    pendingLoading.value = false;
  }
}

// ─── 详情 ──────────────────────────────────────────

const detailVisible = ref(false);
const detailData = ref<TeacherPaymentItem | null>(null);

async function viewDetail(row: TeacherPaymentItem) {
  try {
    const res = await fetchPaymentDetail(row.id);
    detailData.value = res.data.data;
    detailVisible.value = true;
  } catch {
    ElMessage.error('Failed to load payment detail.');
  }
}

// ─── 确认付款 ──────────────────────────────────────

async function handleConfirm(row: TeacherPaymentItem) {
  try {
    await ElMessageBox.confirm(
      `Confirm payment "${row.paymentNo}" for $${row.totalAmount?.toFixed(2)}?`,
      'Confirm payment',
      { type: 'info' },
    );
    await confirmPayment(row.id);
    ElMessage.success('Payment confirmed.');
    loadHistory();
  } catch { /* cancelled */ }
}

// ─── 工具函数 ──────────────────────────────────────

function formatDateTime(dateStr: string) {
  if (!dateStr) return '—';
  return dateStr.substring(0, 19).replace('T', ' ');
}

// ─── 初始化 ────────────────────────────────────────

onMounted(async () => {
  teachersLoading.value = true;
  try {
    const [teachersRes] = await Promise.all([fetchAllTeachers()]);
    allTeachers.value = teachersRes.data.data;
  } catch { /* silent */ }
  teachersLoading.value = false;
});
</script>

<style scoped>
.tp-page {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 500px;
}

/* ─── Sidebar ────────────────────────────────────────── */

.tp-sidebar {
  flex-shrink: 0;
  width: 220px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tp-sidebar__search {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
}

.tp-sidebar__list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.tp-sidebar__item {
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  border-left: 3px solid transparent;
}

.tp-sidebar__item:hover {
  background: #f5f7fa;
}

.tp-sidebar__item--active {
  background: #ecf5ff;
  border-left-color: #409eff;
}

.tp-sidebar__name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.tp-sidebar__meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.tp-sidebar__empty {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* ─── Content ────────────────────────────────────────── */

.tp-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
}

.tp-content__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #909399;
  font-size: 14px;
  border: 1px dashed #e8edf2;
  border-radius: 8px;
  background: #fff;
}

.tp-content__header {
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  padding: 14px 20px;
}

.tp-content__title {
  font-size: 16px;
}

.tp-content__no {
  color: #909399;
  font-size: 13px;
  margin-left: 8px;
  font-weight: normal;
}

/* ─── Section ─────────────────────────────────────────── */

.tp-section {
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  padding: 14px 16px;
}

.tp-section--filters {
  padding: 12px 16px;
}

.tp-section--card {
  padding: 14px 16px;
}

.tp-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.tp-section__header-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.tp-section__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.tp-section__subtitle {
  font-size: 12px;
  color: #909399;
}

/* ─── Filters ─────────────────────────────────────────── */

.tp-filters {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}

.tp-filters__group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tp-filters__label {
  font-size: 12px;
  color: #909399;
}

.tp-filters__actions {
  display: flex;
  gap: 8px;
}

/* ─── History status filter ────────────────────────────── */

.tp-history-filters {
  display: flex;
  gap: 8px;
}

/* ─── Table ───────────────────────────────────────────── */

.tp-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

/* ─── Summary bar ─────────────────────────────────────── */

.tp-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding: 8px 14px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}

/* ─── Pagination ──────────────────────────────────────── */

.tp-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

/* ─── Empty ───────────────────────────────────────────── */

.tp-empty {
  border: 1px dashed #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 30px 0;
}


/* ─── Detail Dialog ───────────────────────────────────── */

.tp-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
}

.tp-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.tp-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.tp-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.tp-detail-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px 14px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 6px;
}

.tp-detail-summary__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tp-detail-summary__item .label {
  font-size: 12px;
  color: #909399;
}

.tp-detail-summary__item .value {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
</style>
