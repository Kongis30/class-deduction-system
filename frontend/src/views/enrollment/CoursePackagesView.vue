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
            <el-option label="Paused" value="PAUSED" />
            <el-option label="Closed" value="CLOSED" />
            <el-option label="Pending" value="PENDING" />
          </el-select>
        </el-form-item>
        <el-form-item class="filters__item">
          <el-button
            :type="filters.onlyRenewalDue ? 'danger' : 'default'"
            plain
            @click="toggleRenewalDueFilter"
          >
            Renewal due
          </el-button>
        </el-form-item>
      </el-form>

      <div v-loading="loading" class="package-group-list">
        <section v-for="group in groupedTableData" :key="group.key" class="package-group">
          <header class="package-group__header">
            <div class="package-group__title">
              <strong>{{ group.courseName }}</strong>
              <span>{{ group.gradeBand }}</span>
            </div>
            <small>{{ group.packages.length }} packages</small>
          </header>
          <el-table
            :data="group.packages"
            row-key="id"
            class="product-table"
            :show-empty="false"
            :row-class-name="oneOnOneRowClassName"
          >
            <el-table-column label="Student" min-width="150" prop="studentName" />
            <el-table-column label="Product / Package" min-width="230">
              <template #default="{ row }">
                <div class="package-name-cell">
                  <strong class="package-name-cell__title">
                    {{ row.packageName || row.courseProductName || 'Unnamed package' }}
                  </strong>
                  <span v-if="shouldShowProductName(row)" class="package-name-cell__context">
                    Product · {{ row.courseProductName }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Available" min-width="145" align="center">
              <template #default="{ row }">
                <strong>{{ formatHours(row.remainingUnits) }} / {{ formatHours(row.totalUnits) }}</strong>
                <el-progress :percentage="progressPercent(row)" :stroke-width="8" :show-text="false" />
              </template>
            </el-table-column>
            <el-table-column label="Schedule setup" min-width="150" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="scheduleStatusType(row.scheduleStatus)">
                  {{ row.scheduleStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Next lesson" min-width="180">
              <template #default="{ row }">
                <span v-if="row.nextLessonStartAt">{{ formatScheduleDate(row.nextLessonStartAt) }}</span>
                <span v-else class="muted-text">—</span>
              </template>
            </el-table-column>
            <el-table-column label="Period" min-width="170">
              <template #default="{ row }">
                <span :class="{ 'renewal-due-text': isOneOnOneRenewalDue(row) }">
                  {{ row.startDate ?? '—' }} ~ {{ row.estimatedEndDate ?? '—' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="Package status" min-width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="packageStatusType(row.packageStatus)">
                  {{ packageStatusLabel(row.packageStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="150" fixed="right">
              <template #default="{ row }">
                <el-button class="row-action-button" text type="primary" @click="openOneOnOneRenewal(row)">Renew</el-button>
                <el-button class="row-action-button" text type="primary" @click="openDetailDialog(row)">Detail</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
        <el-empty v-if="!loading && groupedTableData.length === 0" description="No course packages found." />
      </div>

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
      width="900px"
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
              </div>
            </div>

            <div class="detail-section">
              <div class="detail-section__heading">
                <h4 class="detail-section__title">Hours Summary</h4>
                <el-button size="small" plain @click="openHoursAdjustment">Adjust hours</el-button>
              </div>
              <div class="detail-grid">
                <div class="detail-field">
                  <label>Purchased</label>
                  <span>{{ formatHours(detailData.purchasedHours ?? detailData.totalUnits) }}</span>
                </div>
                <div class="detail-field">
                  <label>Added / Bonus</label>
                  <span>{{ formatHours(additionalPackageHours) }}</span>
                </div>
                <div class="detail-field">
                  <label>Total hours</label>
                  <span>{{ formatHours(detailData.totalUnits) }}</span>
                </div>
                <div class="detail-field">
                  <label>Already deducted</label>
                  <span>{{ formatHours(detailData.consumedUnits) }}</span>
                </div>
                <div class="detail-field">
                  <label>Remaining</label>
                  <strong class="remaining-badge">{{ formatHours(detailData.remainingUnits) }}</strong>
                </div>
                <div class="detail-field">
                  <label>Received</label>
                  <span>${{ (detailData.receivedAmount ?? 0).toFixed(2) }}</span>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <div class="detail-section__heading">
                <div>
                  <h4 class="detail-section__title">Weekly Schedule</h4>
                  <p class="detail-section__hint">
                    Teachers, lesson names, and allocated hours are set for each recurring time slot.
                  </p>
                </div>
                <el-button size="small" plain @click="openPackageSchedule">
                  Open schedule calendar
                </el-button>
              </div>
              <div v-loading="scheduleLoading" class="package-schedule">
                <template v-if="packageScheduleRule">
                  <WeeklyTimeSelector
                    :model-value="[]"
                    :occupied-slots="packageScheduleCalendarSlots"
                    :week-start-date="scheduleWeekStartDate"
                    readonly
                    hide-hint
                  />
                  <el-table :data="packageScheduleRule.slots" size="small" class="schedule-slot-table">
                    <el-table-column label="Time" min-width="150">
                      <template #default="{ row }">
                        {{ weekdayLabel(row.weekday) }} · {{ row.startTime }}–{{ row.endTime }}
                      </template>
                    </el-table-column>
                    <el-table-column label="Teacher" min-width="150">
                      <template #default="{ row }">{{ scheduleTeacherName(row.teacherId) }}</template>
                    </el-table-column>
                    <el-table-column label="Lesson name" min-width="130">
                      <template #default="{ row }">{{ row.subjectName || '—' }}</template>
                    </el-table-column>
                    <el-table-column label="Each lesson" width="110" align="center">
                      <template #default="{ row }">{{ formatUnitsAsHours(row.plannedUnits) }}</template>
                    </el-table-column>
                    <el-table-column label="Allocated" width="110" align="center">
                      <template #default="{ row }">{{ formatUnitsAsHours(row.allocatedUnits) }}</template>
                    </el-table-column>
                  </el-table>
                </template>
                <div v-else class="empty-state">
                  No recurring schedule has been set for this package.
                  <el-button text type="primary" @click="openPackageSchedule">Set schedule</el-button>
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
                  <el-button class="row-action-button" text type="primary" size="small" @click="openLessonDetailDialog(row)">Detail</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailDialogVisible = false">Close</el-button>
          <el-button v-if="detailData?.packageStatus === 'ACTIVE'" :loading="packageStatusSaving" @click="handlePausePackage">
            Pause Package
          </el-button>
          <el-button v-if="detailData?.packageStatus === 'PAUSED'" type="primary" @click="openResumePackage">
            Resume Package
          </el-button>
          <el-button v-if="detailData?.packageStatus === 'ACTIVE'" type="danger" @click="handleClosePackage">
            Close Package
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="hoursAdjustmentVisible"
      title="Adjust Available Hours"
      width="480px"
      :close-on-click-modal="false"
    >
      <div v-if="detailData" class="adjustment-current">
        <span>Purchased {{ detailData.purchasedHours ?? detailData.totalUnits }}h</span>
        <strong>Available {{ detailData.totalUnits }}h</strong>
      </div>
      <el-form label-width="130px">
        <el-form-item label="Adjustment type">
          <el-select v-model="hoursAdjustmentForm.adjustmentType" style="width: 100%">
            <el-option label="Bonus hours" value="BONUS_HOURS" />
            <el-option label="Manual correction" value="MANUAL_CORRECTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="Hour change">
          <el-input-number
            v-model="hoursAdjustmentForm.changeHours"
            :step="0.5"
            :precision="1"
            :min="-10000"
            :max="10000"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="Reason">
          <el-input
            v-model="hoursAdjustmentForm.reason"
            type="textarea"
            :rows="2"
            placeholder="Optional note"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hoursAdjustmentVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="hoursAdjustmentSaving" @click="submitHoursAdjustment">
          Save adjustment
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resumePackageVisible"
      title="Resume Package"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-width="120px">
        <el-form-item label="Starting date">
          <el-date-picker
            v-model="resumePackageForm.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resumePackageVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="packageStatusSaving" @click="submitResumePackage">
          Resume
        </el-button>
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
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  adjustCoursePackageHours,
  closeCoursePackage,
  fetchCoursePackage,
  fetchCoursePackages,
  pauseCoursePackage,
  resumeCoursePackage,
  type CoursePackageQuery,
} from '@/api/enrollment';
import {
  fetchLessonRecord,
  fetchLessonRecords,
  type LessonRecordQuery,
} from '@/api/lesson';
import { fetchAllStudents, fetchAllTeachers } from '@/api/base-data';
import { fetchRecurringScheduleRule, fetchRecurringScheduleRules } from '@/api/schedule';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef } from '@/components/WeeklyTimeSelector.vue';
import type {
  CoursePackageItem,
  EntityId,
  LessonRecordDetailItem,
  LessonRecordItem,
  RecurringScheduleRuleDetailItem,
  TeacherItem,
} from '@/types/domain';

const router = useRouter();
const RENEWAL_DUE_DAYS = 15;
const loading = ref(false);
const detailDialogVisible = ref(false);
const detailTab = ref('overview');
const detailData = ref<CoursePackageItem | null>(null);

const students = ref<{ id: EntityId; studentName: string }[]>([]);
const teachers = ref<TeacherItem[]>([]);
const scheduleLoading = ref(false);
const packageScheduleRule = ref<RecurringScheduleRuleDetailItem | null>(null);
const hoursAdjustmentVisible = ref(false);
const hoursAdjustmentSaving = ref(false);
const hoursAdjustmentForm = reactive({
  adjustmentType: 'BONUS_HOURS' as 'BONUS_HOURS' | 'MANUAL_CORRECTION',
  changeHours: 0.5,
  reason: '',
});
const resumePackageVisible = ref(false);
const packageStatusSaving = ref(false);
const resumePackageForm = reactive({
  startDate: '',
});
const scheduleWeekStartDate = computed(() => {
  const date = new Date();
  const offset = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - offset);
  return date.toISOString().slice(0, 10);
});
const packageScheduleCalendarSlots = computed<OccupiedSlotDef[]>(() =>
  (packageScheduleRule.value?.slots ?? []).map(slot => ({
    weekday: slot.weekday,
    startTime: slot.startTime,
    endTime: slot.endTime,
    courseLabel: slot.subjectName || 'Scheduled lesson',
    subjectName: slot.subjectName,
    teacherName: scheduleTeacherName(slot.teacherId),
    contextLabel: `${scheduleTeacherName(slot.teacherId)} · ${formatUnitsAsHours(slot.allocatedUnits)} allocated`,
    isCurrent: true,
  })),
);
const additionalPackageHours = computed(() => {
  if (!detailData.value) return 0;
  return Math.max(0, Number(detailData.value.totalUnits) - Number(detailData.value.purchasedHours ?? detailData.value.totalUnits));
});

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
  onlyRenewalDue: false,
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

interface OneOnOnePackageRow extends CoursePackageItem {
  scheduleStatus: 'Scheduled' | 'Needs setup' | 'Incomplete' | 'Paused';
  nextLessonStartAt?: string;
}

const tableData = ref<OneOnOnePackageRow[]>([]);
// 当前分页结果按课程和年级合并成组，课包仍保留原有分页、筛选及行操作。
const groupedTableData = computed(() => {
  const groups = new Map<string, {
    key: string;
    courseName: string;
    gradeBand: string;
    packages: OneOnOnePackageRow[];
  }>();

  for (const pkg of tableData.value) {
    const courseName = pkg.courseProductName?.trim() || pkg.packageName?.trim() || 'Unassigned course';
    const gradeBand = pkg.gradeBand?.trim() || 'General';
    const key = JSON.stringify([normalizeLabel(courseName), normalizeLabel(gradeBand)]);
    let group = groups.get(key);
    if (!group) {
      group = { key, courseName, gradeBand, packages: [] };
      groups.set(key, group);
    }
    group.packages.push(pkg);
  }

  return Array.from(groups.values())
    .map(group => ({
      ...group,
      packages: [...group.packages].sort((left, right) =>
        left.studentName.localeCompare(right.studentName)
        || left.packageName.localeCompare(right.packageName)),
    }))
    .sort((left, right) => left.courseName.localeCompare(right.courseName)
      || left.gradeBand.localeCompare(right.gradeBand, undefined, { numeric: true }));
});

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

function toggleRenewalDueFilter() {
  filters.onlyRenewalDue = !filters.onlyRenewalDue;
  handleFilterChange();
}

function progressPercent(row: { totalUnits: number; consumedUnits: number }): number {
  return row.totalUnits > 0 ? Math.round((row.consumedUnits / row.totalUnits) * 100) : 0;
}

function parseDateValue(value?: string): number | undefined {
  if (!value) return undefined;
  const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const date = dateOnlyMatch
    ? new Date(Number(dateOnlyMatch[1]), Number(dateOnlyMatch[2]) - 1, Number(dateOnlyMatch[3]))
    : new Date(value);
  const time = date.getTime();
  return Number.isNaN(time) ? undefined : time;
}

function isRenewalDueByDate(endDateValue?: string): boolean {
  const endTime = parseDateValue(endDateValue);
  if (endTime == null) {
    return false;
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const dueLimit = new Date(today);
  dueLimit.setDate(dueLimit.getDate() + RENEWAL_DUE_DAYS);
  return endTime <= dueLimit.getTime();
}

function isOneOnOneRenewalDue(row: CoursePackageItem): boolean {
  return row.packageStatus === 'ACTIVE' && isRenewalDueByDate(row.estimatedEndDate);
}

function oneOnOneRowClassName({ row }: { row: OneOnOnePackageRow }) {
  return isOneOnOneRenewalDue(row) ? 'renewal-due-row' : '';
}

function normalizeLabel(value?: string | null): string {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLowerCase();
}

function shouldShowProductName(row: CoursePackageItem): boolean {
  return Boolean(row.courseProductName)
    && normalizeLabel(row.courseProductName) !== normalizeLabel(row.packageName);
}

function packageStatusType(status: string) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'PAUSED') return 'warning';
  if (status === 'CLOSED') return 'info';
  return 'warning';
}

function packageStatusLabel(status: string) {
  const map: Record<string, string> = {
    ACTIVE: 'Active', PAUSED: 'Paused', CLOSED: 'Closed', PENDING: 'Pending', SUSPENDED: 'Suspended', INACTIVE: 'Inactive', MIXED: 'Mixed',
  };
  return map[status] ?? status;
}

// ─── Load Data ──────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const allPackages = await loadAllOneOnOnePackages();
    const packages = filters.onlyRenewalDue
      ? allPackages.filter(isOneOnOneRenewalDue)
      : allPackages;
    pagination.total = packages.length;
    const pagePackages = packages.slice((pagination.page - 1) * pagination.size, pagination.page * pagination.size);
    tableData.value = await enrichOneOnOnePackages(pagePackages);
  } catch {
    ElMessage.error('Failed to load packages.');
  } finally {
    loading.value = false;
  }
}

async function loadAllOneOnOnePackages(): Promise<CoursePackageItem[]> {
  const pageSize = 200;
  let pageNum = 1;
  let total = 0;
  const records: CoursePackageItem[] = [];

  do {
    const params: CoursePackageQuery = {
      keyword: filters.keyword || undefined,
      studentId: filters.studentId,
      packageStatus: filters.packageStatus || undefined,
      teachingMode: 'ONE_ON_ONE',
      pageNum,
      pageSize,
    };
    const res = await fetchCoursePackages(params);
    const data = res.data.data;
    records.push(...data.records);
    total = data.total;
    pageNum += 1;
  } while (records.length < total);

  return records;
}

async function enrichOneOnOnePackages(packages: CoursePackageItem[]): Promise<OneOnOnePackageRow[]> {
  const rows = await Promise.all(packages.map(async (pkg) => {
    try {
      const rulesResponse = await fetchRecurringScheduleRules({
        packageId: pkg.id,
        pageNum: 1,
        pageSize: 1,
      });
      const rules = rulesResponse.data.data.records;
      const ruleSummary = rules.find(item => item.ruleStatus === 'ACTIVE' || item.ruleStatus === 'PAUSED')
        ?? rules[0];
      if (!ruleSummary) {
        return {
          ...pkg,
          scheduleStatus: 'Needs setup' as const,
        };
      }

      const detailResponse = await fetchRecurringScheduleRule(ruleSummary.id);
      const rule = detailResponse.data.data;
      const isPaused = pkg.packageStatus === 'PAUSED' || rule.ruleStatus === 'PAUSED';
      const isIncomplete = rule.slots.some(slot =>
        !slot.teacherId
        || !slot.subjectName?.trim()
        || !slot.allocatedUnits
        || slot.allocatedUnits <= 0,
      );

      return {
        ...pkg,
        scheduleStatus: isPaused
          ? 'Paused' as const
          : isIncomplete
            ? 'Incomplete' as const
            : 'Scheduled' as const,
        nextLessonStartAt: ruleSummary.nextLessonStartAt,
      };
    } catch {
      return {
        ...pkg,
        scheduleStatus: 'Needs setup' as const,
      };
    }
  }));
  return rows;
}

function scheduleStatusType(status: OneOnOnePackageRow['scheduleStatus']) {
  if (status === 'Scheduled') return 'success';
  if (status === 'Paused') return 'warning';
  if (status === 'Incomplete') return 'danger';
  return 'info';
}

function formatScheduleDate(value?: string): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date);
}

async function loadStudents() {
  try {
    const res = await fetchAllStudents();
    students.value = res.data.data.map((s) => ({ id: s.id, studentName: s.studentName }));
  } catch {
    // Silent fail
  }
}

async function loadTeachers() {
  try {
    const res = await fetchAllTeachers();
    teachers.value = res.data.data;
  } catch {
    // Silent fail
  }
}

function openOneOnOneRenewal(row: CoursePackageItem) {
  router.push({
    name: 'registration',
    query: {
      renewPackageId: String(row.id),
      studentId: String(row.studentId),
    },
  });
}

// ─── Package Detail ──────────────────────────────────
async function openDetailDialog(row: CoursePackageItem) {
  try {
    const res = await fetchCoursePackage(row.id);
    detailData.value = res.data.data;
    detailTab.value = 'overview';
    lessonHistory.value = [];
    packageScheduleRule.value = null;
    detailDialogVisible.value = true;
    loadPackageSchedule(row.id);
  } catch {
    ElMessage.error('Failed to load package detail.');
  }
}

async function reloadPackageDetail() {
  if (!detailData.value) return;
  const res = await fetchCoursePackage(detailData.value.id);
  detailData.value = res.data.data;
  await loadPackageSchedule(detailData.value.id);
}

async function loadPackageSchedule(packageId: EntityId) {
  scheduleLoading.value = true;
  try {
    const result = await fetchRecurringScheduleRules({ packageId, pageNum: 1, pageSize: 10 });
    const rules = result.data.data.records;
    const rule = rules.find(item => item.ruleStatus === 'ACTIVE' || item.ruleStatus === 'PAUSED') ?? rules[0];
    packageScheduleRule.value = rule
      ? (await fetchRecurringScheduleRule(rule.id)).data.data
      : null;
  } catch {
    packageScheduleRule.value = null;
    ElMessage.error('Failed to load the package schedule.');
  } finally {
    scheduleLoading.value = false;
  }
}

function openPackageSchedule() {
  if (!detailData.value) return;
  detailDialogVisible.value = false;
  router.push({
    name: 'recurring-schedule',
    query: {
      studentId: String(detailData.value.studentId),
      packageId: String(detailData.value.id),
    },
  });
}

function scheduleTeacherName(teacherId?: EntityId): string {
  if (teacherId == null) return '—';
  return teachers.value.find(teacher => String(teacher.id) === String(teacherId))?.teacherName ?? 'Unknown teacher';
}

function weekdayLabel(weekday: number): string {
  return ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][weekday - 1] ?? '—';
}

function formatUnitsAsHours(units?: number): string {
  return units == null ? '—' : `${(units * 0.5).toFixed(1)}h`;
}

function formatHours(hours?: number): string {
  return hours == null ? '—' : `${Number(hours).toFixed(1)}h`;
}

function openHoursAdjustment() {
  hoursAdjustmentForm.adjustmentType = 'BONUS_HOURS';
  hoursAdjustmentForm.changeHours = 0.5;
  hoursAdjustmentForm.reason = '';
  hoursAdjustmentVisible.value = true;
}

async function submitHoursAdjustment() {
  if (!detailData.value) return;
  if (!hoursAdjustmentForm.changeHours) {
    ElMessage.warning('Hour change cannot be zero.');
    return;
  }
  hoursAdjustmentSaving.value = true;
  try {
    await adjustCoursePackageHours(detailData.value.id, {
      changeHours: hoursAdjustmentForm.changeHours,
      adjustmentType: hoursAdjustmentForm.adjustmentType,
      reason: hoursAdjustmentForm.reason.trim() || undefined,
    });
    await reloadPackageDetail();
    await loadData();
    hoursAdjustmentVisible.value = false;
    ElMessage.success('Available hours updated.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to adjust available hours.');
  } finally {
    hoursAdjustmentSaving.value = false;
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

async function handlePausePackage() {
  if (!detailData.value) return;
  packageStatusSaving.value = true;
  try {
    await pauseCoursePackage(detailData.value.id);
    await reloadPackageDetail();
    await loadData();
    ElMessage.success('Package paused.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to pause package.');
  } finally {
    packageStatusSaving.value = false;
  }
}

function openResumePackage() {
  resumePackageForm.startDate = new Date().toISOString().slice(0, 10);
  resumePackageVisible.value = true;
}

async function submitResumePackage() {
  if (!detailData.value) return;
  if (!resumePackageForm.startDate) {
    ElMessage.warning('Select a starting date.');
    return;
  }
  packageStatusSaving.value = true;
  try {
    await resumeCoursePackage(detailData.value.id, { startDate: resumePackageForm.startDate });
    resumePackageVisible.value = false;
    await reloadPackageDetail();
    await loadData();
    ElMessage.success('Package resumed.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to resume package.');
  } finally {
    packageStatusSaving.value = false;
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
loadTeachers();
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

.package-group-list {
  display: grid;
  gap: 14px;
  min-height: 160px;
}

.package-group {
  overflow: hidden;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #fff;
}

.package-group__header {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  border-bottom: 1px solid #d9e2ec;
  background: #f3f7fb;
}

.package-group__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.package-group__title strong {
  overflow: hidden;
  color: #26384c;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.package-group__title span {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.package-group__header small {
  flex: 0 0 auto;
  color: #718096;
  font-size: 11px;
}

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 0;
}

.product-table :deep(.renewal-due-row > td) {
  background: #fff1f2 !important;
}

.product-table :deep(.renewal-due-row:hover > td) {
  background: #ffe4e6 !important;
}

.renewal-due-text {
  color: #e11d48;
  font-weight: 700;
}

.row-action-button {
  height: 26px;
  border-radius: 999px;
  font-weight: 500;
  padding: 0 10px;
}

.row-action-button:hover {
  background: #eff6ff;
}

.package-name-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}
.package-name-cell__title {
  overflow: hidden;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.package-name-cell__context {
  overflow: hidden;
  color: #8a94a3;
  font-size: 11px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}
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
.detail-section__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 6px;
}
.detail-section__title {
  margin: 0; font-size: 13px; font-weight: 600; color: #303133;
  padding-bottom: 6px; border-bottom: 1px solid #f0f0f0;
}
.detail-section__heading .detail-section__title {
  border-bottom: 0;
  padding-bottom: 0;
}
.detail-section__hint {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
.detail-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 20px; }
.detail-field { display: flex; flex-direction: column; gap: 4px; }
.detail-field label { color: #909399; font-size: 12px; }
.detail-field span, .detail-field strong { color: #303133; font-size: 13px; }
.remaining-badge { color: #f56c6c; font-size: 14px; font-weight: 600; }
.package-schedule {
  min-height: 80px;
}
.package-schedule :deep(.weekly-time-selector) {
  min-width: 720px;
}
.schedule-slot-table {
  margin-top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}
.adjustment-current {
  display: flex;
  align-items: center;
  gap: 18px;
  color: #64748b;
  font-size: 12px;
}
.adjustment-current {
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 10px 12px;
  border: 1px solid #dbe7f5;
  border-radius: 6px;
  background: #f8fbff;
}
.adjustment-current strong {
  color: #303133;
}

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
