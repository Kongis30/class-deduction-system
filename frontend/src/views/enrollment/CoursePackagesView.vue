<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Course Packages</span>
      </div>

      <el-form class="filters" :model="filters" inline>
        <el-form-item label="Name / No." class="filters__item">
          <el-input
            v-model="filters.keyword"
            placeholder="Search by package name or no."
            clearable
            style="width: 180px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Student" class="filters__item">
          <el-select
            v-model="filters.studentId"
            placeholder="All students"
            clearable
            filterable
            style="width: 160px;"
            @change="handleFilterChange"
          >
            <el-option v-for="s in students" :key="s.id" :label="s.studentName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Status" class="filters__item">
          <el-select
            v-model="filters.packageStatus"
            placeholder="All"
            clearable
            style="width: 140px;"
            @change="handleFilterChange"
          >
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Closed" value="CLOSED" />
            <el-option label="Pending" value="PENDING" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="product-table"
        :show-empty="true"
        @sort-change="handleSortChange"
      >
        <el-table-column label="Package" min-width="180">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.packageName }}</strong>
              <span>{{ row.studentName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Subject" min-width="120" prop="subjectName" sortable="custom" />
        <el-table-column label="Hours" min-width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.remainingUnits }} / {{ row.totalUnits }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Progress" min-width="140" prop="progress" sortable="custom">
          <template #default="{ row }">
            <el-progress
              :percentage="row.totalUnits > 0 ? Math.round((row.consumedUnits / row.totalUnits) * 100) : 0"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <el-table-column label="Teacher" min-width="130" prop="primaryTeacherName" />
        <el-table-column label="Status" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="packageStatusType(row.packageStatus)">
              {{ packageStatusLabel(row.packageStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" min-width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDetailDialog(row)">Detail</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span>Showing {{ pagination.start }}–{{ pagination.end }} of {{ pagination.total }}</span>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          layout="prev, pager, next, sizes"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </section>

    <!-- Package detail dialog with tabs -->
    <el-dialog
      v-model="detailDialogVisible"
      width="680px"
      :close-on-click-modal="false"
      class="package-dialog"
    >
      <template #header>
        <h3>Package Detail</h3>
      </template>

      <el-tabs v-if="detailData" v-model="detailTab" class="detail-tabs">
        <!-- Tab 1: Basic Info -->
        <el-tab-pane label="Overview" name="overview">
          <div class="package-detail">
            <div class="detail-section">
              <h4 class="detail-section__title">Basic Info</h4>
              <div class="detail-grid">
                <div class="detail-field">
                  <label>Package</label>
                  <span>{{ detailData.packageName }}</span>
                </div>
                <div class="detail-field">
                  <label>Status</label>
                  <el-tag size="small" :type="packageStatusType(detailData.packageStatus)">
                    {{ packageStatusLabel(detailData.packageStatus) }}
                  </el-tag>
                </div>
                <div class="detail-field">
                  <label>Student</label>
                  <span>{{ detailData.studentName }}</span>
                </div>
                <div class="detail-field">
                  <label>Subject</label>
                  <span>{{ detailData.subjectName ?? '—' }}</span>
                </div>
                <div class="detail-field">
                  <label>Teacher</label>
                  <span>{{ detailData.primaryTeacherName ?? '—' }}</span>
                </div>
                <div class="detail-field">
                  <label>Teaching Mode</label>
                  <span>{{ detailData.teachingMode ?? '—' }}</span>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <h4 class="detail-section__title">Hours Summary</h4>
              <div class="detail-grid">
                <div class="detail-field">
                  <label>Total Hours</label>
                  <span>{{ detailData.totalUnits }}</span>
                </div>
                <div class="detail-field">
                  <label>Consumed</label>
                  <span>{{ detailData.consumedUnits }}</span>
                </div>
                <div class="detail-field">
                  <label>Remaining</label>
                  <strong class="remaining-badge">{{ detailData.remainingUnits }}</strong>
                </div>
                <div class="detail-field">
                  <label>Received</label>
                  <span>${{ (detailData.receivedAmount ?? 0).toFixed(2) }}</span>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <h4 class="detail-section__title">Dates</h4>
              <div class="detail-grid">
                <div class="detail-field">
                  <label>Start Date</label>
                  <span>{{ detailData.startDate ?? '—' }}</span>
                </div>
                <div class="detail-field">
                  <label>Est. End Date</label>
                  <span>{{ detailData.estimatedEndDate ?? '—' }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 2: Lesson History -->
        <el-tab-pane name="lessons">
          <template #label>
            Lesson History
            <el-tooltip content="Confirmed lesson records for this package" placement="top" raw-content>
              <span class="tab-info-icon">ⓘ</span>
            </el-tooltip>
          </template>

          <div v-loading="lessonHistoryLoading" class="lesson-history">
            <div v-if="lessonHistory.length === 0" class="empty-state">
              No confirmed lessons yet.
            </div>
            <el-table v-else :data="lessonHistory" row-key="id" size="small" class="lesson-history-table">
              <el-table-column label="Lesson No." min-width="130" prop="lessonNo" />
              <el-table-column label="Status" min-width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="resultStatusTag(row.resultStatus)">
                    {{ resultStatusLabel(row.resultStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Hours" min-width="70" align="center">
                <template #default="{ row }">{{ row.actualUnits }}</template>
              </el-table-column>
              <el-table-column label="Deducted" min-width="80" align="center">
                <template #default="{ row }">
                  <span :class="{ 'deducted-text': row.shouldDeduct }">{{ row.deductedUnits }}</span>
                </template>
              </el-table-column>
              <el-table-column label="Teacher" min-width="110" prop="actualTeacherName" />
              <el-table-column label="Confirmed" min-width="150" prop="confirmedAt" />
              <el-table-column label="" min-width="60" fixed="right">
                <template #default="{ row }">
                  <el-button text type="primary" size="small" @click="openLessonDetailDialog(row)">Detail</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailDialogVisible = false">Close</el-button>
          <el-button v-if="detailData?.packageStatus === 'ACTIVE'" type="danger" @click="handleClosePackage">
            Close Package
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Lesson record detail dialog -->
    <el-dialog
      v-model="lessonDetailDialogVisible"
      title="Lesson Record Detail"
      width="580px"
      :close-on-click-modal="false"
      class="lesson-dialog"
    >
      <div v-if="lessonDetailData" class="lesson-detail">
        <div class="detail-section">
          <h4 class="detail-section__title">Basic Info</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Lesson No.</label>
              <span>{{ lessonDetailData.lessonNo }}</span>
            </div>
            <div class="detail-field">
              <label>Status</label>
              <el-tag size="small" :type="resultStatusTag(lessonDetailData.resultStatus)">
                {{ resultStatusLabel(lessonDetailData.resultStatus) }}
              </el-tag>
            </div>
            <div class="detail-field">
              <label>Student</label>
              <span>{{ lessonDetailData.studentName }}</span>
            </div>
            <div class="detail-field">
              <label>Package</label>
              <span>{{ lessonDetailData.packageName || '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Planned Teacher</label>
              <span>{{ lessonDetailData.plannedTeacherName ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Actual Teacher</label>
              <span>{{ lessonDetailData.actualTeacherName ?? '—' }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="detail-section__title">Time & Units</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Actual Start</label>
              <span>{{ lessonDetailData.actualStartAt ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Actual End</label>
              <span>{{ lessonDetailData.actualEndAt ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Actual Units</label>
              <span>{{ lessonDetailData.actualUnits }}</span>
            </div>
            <div class="detail-field">
              <label>Deducted Units</label>
              <strong :class="{ 'deducted-text': lessonDetailData.shouldDeduct }">
                {{ lessonDetailData.deductedUnits }}
              </strong>
            </div>
            <div class="detail-field">
              <label>Should Deduct</label>
              <el-tag size="small" :type="lessonDetailData.shouldDeduct ? 'success' : 'info'">
                {{ lessonDetailData.shouldDeduct ? 'Yes' : 'No' }}
              </el-tag>
            </div>
            <div class="detail-field">
              <label>Extend End Date</label>
              <el-tag size="small" :type="lessonDetailData.shouldExtendEndDate ? 'warning' : 'info'">
                {{ lessonDetailData.shouldExtendEndDate ? 'Yes' : 'No' }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="detail-section__title">Confirmation</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Confirmed At</label>
              <span>{{ lessonDetailData.confirmedAt ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Source</label>
              <el-tag size="small" type="info">{{ lessonDetailData.recordSource }}</el-tag>
            </div>
          </div>
        </div>

        <div v-if="lessonDetailData.notes" class="detail-section">
          <h4 class="detail-section__title">Notes</h4>
          <p class="detail-notes">{{ lessonDetailData.notes }}</p>
        </div>
      </div>

      <template #footer>
        <el-button @click="lessonDetailDialogVisible = false">Close</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { reactive, ref } from 'vue';

import {
  closeCoursePackage,
  fetchCoursePackage,
  fetchCoursePackages,
  type CoursePackageQuery,
} from '@/api/enrollment';
import {
  fetchLessonRecord,
  fetchLessonRecords,
  type LessonRecordQuery,
} from '@/api/lesson';
import { fetchAllStudents } from '@/api/base-data';
import type { CoursePackageItem, LessonRecordDetailItem, LessonRecordItem } from '@/types/domain';

const loading = ref(false);
const detailDialogVisible = ref(false);
const detailTab = ref('overview');
const detailData = ref<CoursePackageItem | null>(null);

const students = ref<{ id: number; studentName: string }[]>([]);

// Lesson History
const lessonHistoryLoading = ref(false);
const lessonHistory = ref<LessonRecordItem[]>([]);

// Lesson detail dialog
const lessonDetailDialogVisible = ref(false);
const lessonDetailData = ref<LessonRecordDetailItem | null>(null);

// ─── Filters & Pagination ───────────────────────────
const filters = reactive({
  keyword: '',
  studentId: undefined as number | undefined,
  packageStatus: '',
});

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
  get start() {
    return this.total === 0 ? 0 : (this.page - 1) * this.size + 1;
  },
  get end() {
    return Math.min(this.page * this.size, this.total);
  },
});

const sortBy = ref<string | undefined>();
const sortOrder = ref<string | undefined>();

const tableData = ref<CoursePackageItem[]>([]);

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

function handleSortChange({ prop, order }: { prop?: string; order?: string }) {
  sortBy.value = prop;
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : undefined;
  pagination.page = 1;
  loadData();
}

function packageStatusType(status: string) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'CLOSED') return 'info';
  return 'warning';
}

function packageStatusLabel(status: string) {
  const map: Record<string, string> = {
    ACTIVE: 'Active', CLOSED: 'Closed', PENDING: 'Pending', SUSPENDED: 'Suspended',
  };
  return map[status] ?? status;
}

// ─── Load Data ──────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const params: CoursePackageQuery = {
      keyword: filters.keyword || undefined,
      studentId: filters.studentId,
      packageStatus: filters.packageStatus || undefined,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value,
      pageNum: pagination.page,
      pageSize: pagination.size,
    };
    const res = await fetchCoursePackages(params);
    const data = res.data.data;
    tableData.value = data.records;
    pagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load packages.');
  } finally {
    loading.value = false;
  }
}

async function loadStudents() {
  try {
    const res = await fetchAllStudents();
    students.value = res.data.data.map((s) => ({ id: s.id, studentName: s.studentName }));
  } catch {
    // Silent fail
  }
}

// ─── Package Detail ──────────────────────────────────
async function openDetailDialog(row: CoursePackageItem) {
  try {
    const res = await fetchCoursePackage(row.id);
    detailData.value = res.data.data;
    detailTab.value = 'overview';
    lessonHistory.value = [];
    detailDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load package detail.');
  }
}

async function handleClosePackage() {
  if (!detailData.value) return;
  try {
    await closeCoursePackage(detailData.value.id);
    ElMessage.success('Package closed.');
    detailDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  }
}

// ─── Lesson History Tab ────────────────────────────────
async function loadLessonHistory() {
  if (!detailData.value) return;
  lessonHistoryLoading.value = true;
  try {
    const params: LessonRecordQuery = {
      packageId: detailData.value.id,
      pageNum: 1,
      pageSize: 100,
    };
    const res = await fetchLessonRecords(params);
    lessonHistory.value = res.data.data.records;
  } catch {
    // silent fail
  } finally {
    lessonHistoryLoading.value = false;
  }
}

// Watch tab switch to load lesson history when tab is opened
import { watch } from 'vue';
watch(detailTab, (tab) => {
  if (tab === 'lessons' && lessonHistory.value.length === 0) {
    loadLessonHistory();
  }
});

// ─── Lesson Detail Dialog ─────────────────────────────
async function openLessonDetailDialog(row: LessonRecordItem) {
  try {
    const res = await fetchLessonRecord(row.id);
    lessonDetailData.value = res.data.data;
    lessonDetailDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load lesson detail.');
  }
}

// ─── Helpers ───────────────────────────────────────
function resultStatusTag(status: string) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FORMAL_LEAVE') return 'info';
  if (status === 'NO_SHOW') return 'danger';
  if (status === 'TEACHER_CANCEL') return 'warning';
  return '';
}

function resultStatusLabel(status: string) {
  const map: Record<string, string> = {
    COMPLETED: 'Completed', FORMAL_LEAVE: 'Formal Leave', NO_SHOW: 'No Show',
    TEACHER_CANCEL: 'Teacher Cancel', TEMP_CANCEL: 'Cancelled',
    MAKEUP_COMPLETED: 'Makeup', SUBSTITUTED: 'Substituted',
  };
  return map[status] ?? status;
}

// ─── Init ───────────────────────────────────────────
loadData();
loadStudents();
</script>

<style scoped>
.course-product-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.filters { margin-bottom: 12px; }
.filters__item { margin-right: 16px; margin-bottom: 0; }
.filters__item :deep(.el-form-item__label) { color: #606266; font-size: 13px; }

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.cell-stack { display: flex; flex-direction: column; gap: 2px; }
.cell-stack strong { color: #303133; font-size: 13px; font-weight: 500; }
.cell-stack span { color: #909399; font-size: 12px; }

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

/* Dialog */
.package-dialog :deep(.el-dialog__body) { padding: 0; }
.package-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}
.package-dialog :deep(.el-dialog__header h3) {
  margin: 0; font-size: 16px; font-weight: 600; color: #303133;
}
.package-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.detail-tabs :deep(.el-tabs__header) { margin: 0 0 -1px 0; padding: 0 16px; }
.detail-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.detail-tabs :deep(.el-tabs__item) { font-size: 14px; }
.detail-tabs :deep(.el-tab-pane) { padding: 16px 20px; }

/* Tabs info icon */
.tab-info-icon {
  display: inline-block;
  width: 14px;
  height: 14px;
  line-height: 14px;
  text-align: center;
  font-size: 11px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 50%;
  cursor: help;
  margin-left: 3px;
}

/* Detail layout */
.package-detail { display: flex; flex-direction: column; gap: 16px; }
.detail-section { display: flex; flex-direction: column; gap: 8px; }
.detail-section__title {
  margin: 0; font-size: 13px; font-weight: 600; color: #303133;
  padding-bottom: 6px; border-bottom: 1px solid #f0f0f0;
}
.detail-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 20px; }
.detail-field { display: flex; flex-direction: column; gap: 4px; }
.detail-field label { color: #909399; font-size: 12px; }
.detail-field span, .detail-field strong { color: #303133; font-size: 13px; }
.remaining-badge { color: #f56c6c; font-size: 14px; font-weight: 600; }

/* Lesson History */
.lesson-history { min-height: 80px; }
.empty-state { text-align: center; color: #909399; font-size: 13px; padding: 24px 0; }
.lesson-history-table { border-radius: 6px; overflow: hidden; border: 1px solid #ebeef5; }
.deducted-text { color: #f56c6c; font-weight: 600; }

/* Lesson detail dialog */
.lesson-dialog :deep(.el-dialog__body) {
  max-height: 65vh; overflow-y: auto; padding: 16px 20px;
}
.lesson-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5; padding: 16px 20px;
}
.lesson-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5; padding: 12px 20px;
}
.lesson-detail { display: flex; flex-direction: column; gap: 16px; }
.detail-notes { margin: 0; color: #606266; font-size: 13px; line-height: 1.5; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>