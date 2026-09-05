<template>
  <div class="page-stack">
    <section class="student-calendar">
      <div class="student-calendar__header">
        <span class="student-calendar__title">{{ isStudent ? 'My Schedule' : 'Student Schedule' }}</span>
      </div>

      <div class="student-calendar__body">
        <!-- Left: Student list (hidden for student users) -->
        <aside v-if="!isStudent" class="student-list-panel">
          <div class="student-list-panel__header">
            <el-input
              v-model="searchText"
              placeholder="Search students..."
              clearable
              :prefix-icon="Search"
            />
          </div>
          <div class="student-list-panel__body">
            <div v-if="filteredStudents.length === 0" class="student-list-panel__empty">
              {{ students.length === 0 ? 'Loading...' : 'No students found' }}
            </div>
            <div
              v-for="s in filteredStudents"
              :key="s.id"
              class="student-item"
              :class="{ 'student-item--active': selectedStudentId === s.id }"
              @click="onStudentSelect(s.id)"
            >
              <span class="student-item__name">{{ s.studentName }}</span>
              <el-icon v-if="selectedStudentId === s.id" color="#409eff"><Check /></el-icon>
            </div>
          </div>
        </aside>

        <!-- Right: Calendar -->
        <main class="calendar-panel">
          <!-- No student selected -->
          <div v-if="!selectedStudentId" class="calendar-empty">
            <el-icon :size="40" color="#c0c4cc"><Calendar /></el-icon>
            <p>{{ isStudent ? 'No schedule is available for your account.' : 'Select a student to view their weekly schedule.' }}</p>
          </div>

          <!-- Loading -->
          <div v-else-if="loading" class="calendar-loading">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            <span>Loading schedule...</span>
          </div>

          <!-- Schedule calendar -->
          <template v-else>
            <template v-if="isStudent">
              <div class="calendar-controls">
                <div class="calendar-controls__nav">
                  <el-button-group>
                    <el-button :icon="ArrowLeft" @click="prevWeek" />
                    <el-button :icon="ArrowRight" @click="nextWeek" />
                  </el-button-group>
                  <el-date-picker
                    v-model="pickerDate"
                    type="date"
                    placeholder="Jump to week"
                    value-format="YYYY-MM-DD"
                    style="width: 150px;"
                    @change="jumpToDate"
                  />
                  <span class="calendar-controls__label">{{ weekLabel }}</span>
                  <el-tag size="small" type="info">{{ occurrences.length }} lessons this week</el-tag>
                </div>
              </div>

              <div v-if="studentPackages.length > 0" class="package-info-bar">
                <div v-for="pkg in studentPackages" :key="pkg.id" class="package-info-chip">
                  <span class="package-info-chip__name">{{ pkg.packageName }}</span>
                  <span class="package-info-chip__meta">{{ pkg.remainingUnits ?? pkg.totalUnits }}h remaining</span>
                </div>
              </div>

              <section v-if="conflictGroups.length" class="student-conflict-summary">
                <div class="student-conflict-summary__header">
                  <strong>{{ conflictSummaryTitle }}</strong>
                  <span>Contact the school before attending either lesson.</span>
                </div>
                <div class="student-conflict-summary__list">
                  <div v-for="group in conflictGroups" :key="group.key" class="student-conflict-card">
                    <span class="student-conflict-card__time">{{ group.timeLabel }}</span>
                    <strong>{{ group.items.length }} overlapping lessons</strong>
                    <span>{{ group.items.map(lessonCourseLabel).join(' / ') }}</span>
                  </div>
                </div>
              </section>

              <WeeklyTimeSelector
                v-model="emptySlots"
                :occupied-slots="occupiedSlots"
                :week-start-date="weekMonday"
                :readonly="true"
                :hide-hint="true"
                @cell-click="onCellClick"
              />
            </template>

            <template v-else>
              <div class="calendar-controls">
                <div class="calendar-controls__nav">
                  <el-button-group>
                    <el-button :icon="ArrowLeft" @click="prevWeek" />
                    <el-button :icon="ArrowRight" @click="nextWeek" />
                  </el-button-group>
                  <el-date-picker
                    v-model="pickerDate"
                    type="date"
                    placeholder="Jump to week"
                    value-format="YYYY-MM-DD"
                    style="width: 150px;"
                    @change="jumpToDate"
                  />
                  <span class="calendar-controls__label">{{ weekLabel }}</span>
                  <el-tag size="small" type="info">{{ occurrences.length }} lessons this week</el-tag>
                </div>
                <span class="calendar-controls__student">
                  {{ selectedStudentName }}
                </span>
              </div>

              <div v-if="studentPackages.length > 0" class="package-info-bar">
                <div
                  v-for="pkg in studentPackages"
                  :key="pkg.id"
                  class="package-info-chip"
                >
                  <span class="package-info-chip__name">{{ pkg.packageName }}</span>
                  <span class="package-info-chip__meta">
                    {{ pkg.remainingUnits ?? pkg.totalUnits }}h remaining
                    <template v-if="packageLastDates[pkg.id]">
                      · Last lesson {{ packageLastDates[pkg.id] }}
                    </template>
                  </span>
                </div>
              </div>

              <section v-if="conflictGroups.length" class="student-conflict-summary">
                <div class="student-conflict-summary__header">
                  <strong>{{ conflictSummaryTitle }}</strong>
                  <span>These lessons overlap for the selected student.</span>
                </div>
                <div class="student-conflict-summary__list">
                  <button
                    v-for="group in conflictGroups"
                    :key="group.key"
                    class="student-conflict-card"
                    type="button"
                    @click="openDetailDialog(group.items[0])"
                  >
                    <span class="student-conflict-card__time">{{ group.timeLabel }}</span>
                    <strong>{{ group.items.length }} overlapping lessons</strong>
                    <span>{{ group.items.map(lessonCourseLabel).join(' / ') }}</span>
                  </button>
                </div>
              </section>

              <WeeklyTimeSelector
                v-model="emptySlots"
                :occupied-slots="occupiedSlots"
                :week-start-date="weekMonday"
                :readonly="true"
                :hide-hint="true"
                @cell-click="onCellClick"
              />
            </template>
          </template>
        </main>
      </div>
    </section>

    <!-- Detail dialog -->
    <el-dialog
      v-model="detailDialogVisible"
      title="Lesson Detail"
      width="540px"
      :close-on-click-modal="false"
      class="schedule-dialog"
    >
      <div v-if="detailData" class="lesson-detail" v-loading="detailLoading">
        <div class="detail-section">
          <h4 class="detail-section__title">Lesson info</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Lesson No.</label>
              <span>{{ detailData.lessonNo }}</span>
            </div>
            <div class="detail-field">
              <label>Type</label>
              <el-tag size="small" :type="occurrenceTypeTag(detailData.occurrenceType)">
                {{ occurrenceTypeLabel(detailData.occurrenceType) }}
              </el-tag>
            </div>
            <div class="detail-field">
              <label>{{ detailData.className ? 'Class' : 'Student' }}</label>
              <span>{{ detailData.className || detailData.studentName }}</span>
            </div>
            <div class="detail-field">
              <label>Package</label>
              <span>{{ detailData.packageName || '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Teacher</label>
              <span>{{ detailData.plannedTeacherName ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Classroom</label>
              <span>{{ detailData.plannedClassroomName ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Start time</label>
              <span>{{ detailData.plannedStartAt }}</span>
            </div>
            <div class="detail-field">
              <label>End time</label>
              <span>{{ detailData.plannedEndAt }}</span>
            </div>
            <div class="detail-field">
              <label>Hours</label>
              <span>{{ (detailData.plannedUnits * 0.5).toFixed(1) }}</span>
            </div>
            <div class="detail-field">
              <label>Status</label>
              <el-tag size="small" :type="planningStatusTag(detailData.planningStatus)">
                {{ planningStatusLabel(detailData.planningStatus) }}
              </el-tag>
            </div>
          </div>
        </div>
        <div v-if="detailData.notes" class="detail-section">
          <h4 class="detail-section__title">Notes</h4>
          <p class="detail-notes">{{ detailData.notes }}</p>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailDialogVisible = false">Close</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, Calendar, Check, Loading, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import dayjs from 'dayjs';
import { fetchAllStudents } from '@/api/base-data';
import { fetchCoursePackages, fetchMyCoursePackages } from '@/api/enrollment';
import {
  fetchLessonOccurrence,
  fetchLessonOccurrences,
} from '@/api/schedule';
import type { CoursePackageItem, EntityId, LessonOccurrenceItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import { useAuthStore } from '@/stores/auth';
import { isStudentUser } from '@/types/auth';
import { findScheduleConflictGroups } from '@/utils/schedule-conflicts';

const authStore = useAuthStore();
const isStudent = computed(() => isStudentUser(authStore.currentUser));

const loading = ref(false);
const students = ref<{ id: EntityId; studentName: string }[]>([]);
const searchText = ref('');
const selectedStudentId = ref<EntityId | null>(null);
const weekOffset = ref(0);
const occurrences = ref<LessonOccurrenceItem[]>([]);
const emptySlots = ref<SlotDef[]>([]);

// ─── Package info bar ──────────────────────────
const studentPackages = ref<CoursePackageItem[]>([]);
const packageLastDates = ref<Record<string, string>>({});

// Monotonic token guarding against stale responses: when the user switches
// students quickly, only the latest request may write shared state.
let scheduleRequestSeq = 0;
let packageRequestSeq = 0;

// ─── Student list ──────────────────────────────
const filteredStudents = computed(() => {
  const q = searchText.value.trim().toLowerCase();
  if (!q) return students.value;
  return students.value.filter(s => s.studentName.toLowerCase().includes(q));
});

const selectedStudentName = computed(() => {
  const s = students.value.find(s => s.id === selectedStudentId.value);
  return s?.studentName ?? '';
});

async function onStudentSelect(id: EntityId) {
  selectedStudentId.value = id;
  weekOffset.value = 0;
  studentPackages.value = [];
  packageLastDates.value = {};
  loadSchedule();
  loadPackageInfo();
}

async function loadPackageInfo() {
  if (!selectedStudentId.value) return;
  const requestId = ++packageRequestSeq;
  const studentId = selectedStudentId.value;
  try {
    const pkgRes = await (isStudent.value ? fetchMyCoursePackages({
      packageStatus: 'ACTIVE',
      pageSize: 50,
    }) : fetchCoursePackages({
      studentId,
      packageStatus: 'ACTIVE',
      pageSize: 50,
    }));
    // Discard if a newer selection has superseded this request.
    if (requestId !== packageRequestSeq) return;
    studentPackages.value = pkgRes.data.data.records;

    // Query recent occurrences to find each package's last lesson date
    const dates: Record<string, string> = {};
    try {
      const occRes = await fetchLessonOccurrences({
        studentId,
        startAt: dayjs().subtract(90, 'day').format('YYYY-MM-DDT00:00:00'),
        endAt: dayjs().add(90, 'day').format('YYYY-MM-DDT23:59:59'),
        pageSize: 200,
      });
      if (requestId !== packageRequestSeq) return;
      const occs = occRes.data.data.records;
      for (const occ of occs) {
        const pkgKey = String(occ.packageId);
        if (!dates[pkgKey] || occ.plannedStartAt > dates[pkgKey]) {
          dates[pkgKey] = occ.plannedStartAt;
        }
      }
    } catch { /* silent */ }

    // Format dates, keep string keys to avoid snowflake precision loss
    const formatted: Record<string, string> = {};
    for (const [pkgKey, dt] of Object.entries(dates)) {
      formatted[pkgKey] = dayjs(dt).format('MMM D, YYYY');
    }
    if (requestId !== packageRequestSeq) return;
    packageLastDates.value = formatted;
  } catch {
    if (requestId !== packageRequestSeq) return;
    studentPackages.value = [];
  }
}

// ─── Week navigation ───────────────────────────
const weekMonday = computed(() => {
  const now = dayjs().startOf('day');
  const thisMonday = now.day(1);
  return thisMonday.add(weekOffset.value * 7, 'day').format('YYYY-MM-DD');
});

const weekLabel = computed(() => {
  const monday = dayjs(weekMonday.value);
  const sunday = monday.add(6, 'day');
  return `${monday.format('MMM D')} – ${sunday.format('MMM D, YYYY')}`;
});

const pickerDate = ref<string | null>(null);

function prevWeek() {
  weekOffset.value--;
  pickerDate.value = weekMonday.value;
  loadSchedule();
}

function nextWeek() {
  weekOffset.value++;
  pickerDate.value = weekMonday.value;
  loadSchedule();
}

function jumpToDate(date: string | null) {
  if (!date) return;
  const pickedMonday = dayjs(date).day(1).format('YYYY-MM-DD');
  const thisMonday = dayjs().startOf('day').day(1).format('YYYY-MM-DD');
  weekOffset.value = dayjs(pickedMonday).diff(dayjs(thisMonday), 'day') / 7;
  pickerDate.value = weekMonday.value;
  loadSchedule();
}

// ─── Student schedule conflicts ───────────────
const conflictGroups = computed(() => findScheduleConflictGroups(occurrences.value));

const conflictOccurrenceIds = computed(() => {
  const ids = new Set<EntityId>();
  for (const group of conflictGroups.value) {
    for (const lesson of group.items) {
      ids.add(lesson.id);
    }
  }
  return ids;
});

const conflictSummaryTitle = computed(() => {
  const count = conflictGroups.value.length;
  return `${count} schedule conflict${count === 1 ? '' : 's'} this week`;
});

// ─── Occupied slots ────────────────────────────
const occupiedSlots = computed<OccupiedSlotDef[]>(() => {
  const result: OccupiedSlotDef[] = [];

  for (const o of occurrences.value) {
    if (o.planningStatus === 'CANCELLED') continue;
    const start = dayjs(o.plannedStartAt);
    const weekday = start.day() === 0 ? 7 : start.day();
    const lessonTitle = o.subjectName || o.className || o.packageName || 'Lesson';
    result.push({
      weekday,
      startTime: start.format('HH:mm'),
      endTime: dayjs(o.plannedEndAt).format('HH:mm'),
      courseLabel: o.className || o.packageName || o.lessonNo || 'Lesson',
      displayTitle: lessonTitle,
      subjectName: o.subjectName,
      studentName: o.studentName,
      teacherName: o.plannedTeacherName,
      classroomName: o.plannedClassroomName,
      productName: o.productName || o.packageName,
      showTimeInEvent: false,
    });
  }
  return result;
});

// ─── Data loading ──────────────────────────────
async function loadSchedule() {
  if (!selectedStudentId.value) return;
  const requestId = ++scheduleRequestSeq;
  const studentId = selectedStudentId.value;
  loading.value = true;
  try {
    const startAt = dayjs(weekMonday.value).format('YYYY-MM-DDTHH:mm:ss');
    const endAt = dayjs(weekMonday.value).add(6, 'day').endOf('day').format('YYYY-MM-DDTHH:mm:ss');

    const res = await fetchLessonOccurrences({
      studentId,
      startAt,
      endAt,
      pageSize: 100,
    });
    // Discard if a newer selection/week has superseded this request.
    if (requestId !== scheduleRequestSeq) return;
    occurrences.value = res.data.data.records;
  } catch {
    if (requestId !== scheduleRequestSeq) return;
    ElMessage.error('Failed to load schedule.');
    occurrences.value = [];
  } finally {
    if (requestId === scheduleRequestSeq) {
      loading.value = false;
    }
  }
}

// ─── Cell click → detail dialog ────────────────
const detailDialogVisible = ref(false);
const detailData = ref<LessonOccurrenceItem | null>(null);
const detailLoading = ref(false);

function onCellClick(weekday: number, hour: number, minute: number) {
  const clickedMinutes = hour * 60 + minute;
  const occ = occurrences.value.find(o => {
    const start = dayjs(o.plannedStartAt);
    const sWeekday = start.day() === 0 ? 7 : start.day();
    if (sWeekday !== weekday) return false;
    const sMin = start.hour() * 60 + start.minute();
    const end = dayjs(o.plannedEndAt);
    const eMin = end.hour() * 60 + end.minute();
    return clickedMinutes >= sMin && clickedMinutes < eMin;
  });
  if (occ) {
    openDetailDialog(occ);
  }
}

async function openDetailDialog(row: LessonOccurrenceItem) {
  detailDialogVisible.value = true;
  detailLoading.value = true;
  try {
    const res = await fetchLessonOccurrence(row.id);
    detailData.value = res.data.data;
  } catch {
    detailData.value = row;
  } finally {
    detailLoading.value = false;
  }
}

// ─── Helpers ───────────────────────────────────
function occurrenceTypeTag(type: string) {
  if (type === 'MAKEUP') return 'warning';
  if (type === 'MANUAL') return 'info';
  return '';
}

function occurrenceTypeLabel(type: string) {
  return { REGULAR: 'Regular', MAKEUP: 'Makeup', MANUAL: 'Manual' }[type] ?? type;
}

function planningStatusTag(status: string) {
  if (status === 'SCHEDULED') return 'success';
  if (status === 'ADJUSTED') return 'warning';
  if (status === 'CANCELLED') return 'danger';
  return 'info';
}

function planningStatusLabel(status: string) {
  return { SCHEDULED: 'Scheduled', ADJUSTED: 'Adjusted', CANCELLED: 'Cancelled', MERGED: 'Merged' }[status] ?? status;
}

function lessonCourseLabel(lesson: LessonOccurrenceItem) {
  if (lesson.packageName && lesson.className && lesson.packageName !== lesson.className) {
    return `${lesson.packageName} · ${lesson.className}`;
  }
  return lesson.packageName || lesson.className || 'Lesson';
}

// ─── Init ──────────────────────────────────────
onMounted(async () => {
  if (isStudent.value) {
    // Student user: auto-select their own profile
    const studentId = authStore.currentUser?.studentId;
    if (studentId) {
      selectedStudentId.value = studentId;
      students.value = [{ id: studentId, studentName: authStore.currentUser?.displayName || 'Me' }];
      loadSchedule();
      loadPackageInfo();
    }
    return;
  }
  // Admin/staff/teacher: show student list
  try {
    const res = await fetchAllStudents();
    // 只保留拥有 ≥1 个 ACTIVE 1-on-1 课时包的学生。
    // 班课学生只写 class_enrollment、不写 course_package，不应出现在 1-on-1 学生日历列表里。
    const packageCheck = await Promise.all(
      res.data.data.map(async (s) => {
        try {
          const pkgRes = await fetchCoursePackages({
            studentId: s.id,
            packageStatus: 'ACTIVE',
            teachingMode: 'ONE_ON_ONE',
            pageSize: 1,
          });
          const hasOneOnOne = (pkgRes.data.data.total ?? pkgRes.data.data.records.length) > 0;
          return { id: s.id, studentName: s.studentName, hasOneOnOne };
        } catch {
          // 查询失败时保守保留该学生，避免误隐藏正常学生
          return { id: s.id, studentName: s.studentName, hasOneOnOne: true };
        }
      })
    );
    students.value = packageCheck
      .filter(x => x.hasOneOnOne)
      .map(x => ({ id: x.id, studentName: x.studentName }));
  } catch { /* silent */ }
});
</script>

<style scoped>
.student-calendar {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: calc(100vh - 120px);
  min-height: 600px;
}

.student-calendar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}

.student-calendar__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.student-calendar__body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

/* ── Left panel: Student list ────────────────── */
.student-list-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.student-list-panel__header {
  padding: 10px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.student-list-panel__body {
  flex: 1;
  overflow-y: auto;
}

.student-list-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  color: #909399;
  font-size: 13px;
}

.student-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.15s;
}

.student-item:hover {
  background: #f5f7fa;
}

.student-item--active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  padding-left: 11px;
}

.student-item--active:hover {
  background: #d9ecff;
}

.student-item__name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.student-item--active .student-item__name {
  color: #409eff;
}

/* ── Right panel: Calendar ──────────────────── */
.calendar-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.calendar-empty,
.calendar-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 64px 0;
  color: #909399;
  font-size: 14px;
}

.calendar-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.calendar-controls__nav {
  display: flex;
  align-items: center;
  gap: 10px;
}

.calendar-controls__label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.calendar-controls__student {
  font-size: 13px;
  font-weight: 500;
  color: #409eff;
}

/* ── Student lesson list ───────────────────── */
.student-lessons__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.student-lessons__range {
  color: #909399;
  font-size: 13px;
}

.student-lessons__empty {
  padding: 48px 16px;
  color: #909399;
  font-size: 14px;
  text-align: center;
}

.student-lessons__list {
  display: grid;
  gap: 10px;
}

.student-conflict-summary {
  margin-bottom: 12px;
  border: 1px solid #fecdca;
  border-radius: 8px;
  background: #fff7f6;
  padding: 10px 12px;
}

.student-conflict-summary__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.student-conflict-summary__header strong {
  color: #b42318;
  font-size: 13px;
}

.student-conflict-summary__header span {
  color: #667085;
  font-size: 12px;
}

.student-conflict-summary__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px;
}

.student-conflict-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  width: 100%;
  border: 1px solid #fda29b;
  border-radius: 6px;
  background: #fff;
  padding: 8px 10px;
  color: #667085;
  font: inherit;
  font-size: 12px;
  text-align: left;
}

button.student-conflict-card {
  cursor: pointer;
}

button.student-conflict-card:hover {
  background: #fff1f0;
}

.student-conflict-card strong {
  color: #344054;
}

.student-conflict-card__time {
  color: #b42318;
  font-weight: 700;
}

.student-lesson-card {
  display: grid;
  grid-template-columns: minmax(180px, 1.2fr) minmax(180px, 1fr) minmax(220px, 1.2fr) auto;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.student-lesson-card--conflict {
  border-color: #fda29b;
  background: #fff7f6;
  box-shadow: inset 4px 0 0 #f56c6c;
}

.student-lesson-card__time,
.student-lesson-card__course {
  color: #303133;
  font-size: 13px;
}

.student-lesson-card__time {
  font-weight: 600;
}

.student-lesson-card__course {
  font-weight: 500;
}

.student-lesson-card__detail {
  display: flex;
  gap: 12px;
  color: #606266;
  font-size: 13px;
  flex-wrap: wrap;
}

/* ── Package info bar ────────────────────── */
.package-info-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.package-info-chip {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 6px 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  min-width: 160px;
}

.package-info-chip__name {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
}

.package-info-chip__meta {
  font-size: 11px;
  color: #909399;
}

/* ── Dialog ───────────────────────────────── */
.schedule-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.schedule-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.schedule-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.lesson-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-section__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 20px;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-field label {
  color: #909399;
  font-size: 12px;
}

.detail-field span {
  color: #303133;
  font-size: 13px;
}

.detail-notes {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

@media (max-width: 880px) {
  .student-lesson-card {
    grid-template-columns: 1fr auto;
  }

  .student-lesson-card__detail {
    grid-column: 1 / -1;
  }
}
</style>
