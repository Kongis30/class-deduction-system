<template>
  <div class="page-stack">
    <section class="teacher-calendar">
      <div class="teacher-calendar__header">
        <span class="teacher-calendar__title">{{ isTeacher ? 'My Calendar' : 'Teacher Calendar' }}</span>
      </div>

      <div class="teacher-calendar__body">
        <!-- Left: Teacher list -->
        <aside v-if="!isTeacher" class="teacher-list-panel">
          <div class="teacher-list-panel__header">
            <el-input
              v-model="searchText"
              placeholder="Search teachers..."
              clearable
              :prefix-icon="Search"
            />
          </div>
          <el-scrollbar always class="teacher-list-panel__body calendar-roster-scroll">
            <div v-if="filteredTeachers.length === 0" class="teacher-list-panel__empty">
              {{ teachers.length === 0 ? 'Loading...' : 'No teachers found' }}
            </div>
            <div
              v-for="t in filteredTeachers"
              :key="t.id"
              class="teacher-item"
              :class="{ 'teacher-item--active': selectedTeacherId === t.id }"
              @click="onTeacherSelect(t.id)"
            >
              <span class="teacher-item__name">{{ t.teacherName }}</span>
              <el-icon v-if="selectedTeacherId === t.id" color="#409eff"><Check /></el-icon>
            </div>
          </el-scrollbar>
        </aside>

        <!-- Right: Calendar -->
        <main class="calendar-panel">
          <!-- No teacher selected -->
          <div v-if="!selectedTeacherId" class="calendar-empty">
            <el-icon :size="40" color="#c0c4cc"><Calendar /></el-icon>
            <p>{{ isTeacher ? 'No lessons are assigned to your account.' : 'Select a teacher to view their weekly schedule.' }}</p>
          </div>

          <!-- Loading -->
          <div v-else-if="loading" class="calendar-loading">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            <span>Loading schedule...</span>
          </div>

          <!-- Calendar -->
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
              <span class="calendar-controls__teacher">
                {{ selectedTeacherName }}
              </span>
            </div>
            <section v-if="conflictGroups.length" class="teacher-conflict-summary">
              <div class="teacher-conflict-summary__header">
                <strong>{{ conflictSummaryTitle }}</strong>
                <span>These lessons overlap for the selected teacher.</span>
              </div>
              <div class="teacher-conflict-summary__list">
                <button
                  v-for="group in conflictGroups"
                  :key="group.key"
                  class="teacher-conflict-card"
                  type="button"
                  @click="openDetailDialog(group.items[0])"
                >
                  <span class="teacher-conflict-card__time">{{ group.timeLabel }}</span>
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
import { fetchAllTeachers } from '@/api/base-data';
import {
  fetchLessonOccurrence,
  fetchLessonOccurrences,
} from '@/api/schedule';
import { useAuthStore } from '@/stores/auth';
import { isTeacherUser } from '@/types/auth';
import type { EntityId, LessonOccurrenceItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import { findScheduleConflictGroups } from '@/utils/schedule-conflicts';

const authStore = useAuthStore();
const isTeacher = computed(() => isTeacherUser(authStore.currentUser));

// ─── Teachers ───────────────────────────────────
const teachers = ref<{ id: EntityId; teacherName: string }[]>([]);
const searchText = ref('');
const selectedTeacherId = ref<EntityId | null>(null);

// Monotonic token guarding against stale responses when switching teachers/weeks quickly.
let scheduleRequestSeq = 0;

const filteredTeachers = computed(() => {
  const q = searchText.value.trim().toLowerCase();
  const sorted = [...teachers.value].sort((left, right) =>
    left.teacherName.localeCompare(right.teacherName, undefined, { sensitivity: 'base' })
      || String(left.id).localeCompare(String(right.id), undefined, { numeric: true }),
  );
  if (!q) return sorted;
  return sorted.filter(t => t.teacherName.toLowerCase().includes(q));
});

const selectedTeacherName = computed(() => {
  const t = teachers.value.find(t => t.id === selectedTeacherId.value);
  return t?.teacherName ?? authStore.currentUser?.displayName ?? '';
});

function onTeacherSelect(id: EntityId) {
  selectedTeacherId.value = id;
  weekOffset.value = 0;
  loadSchedule();
}

// ─── Week navigation ───────────────────────────
const weekOffset = ref(0);

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

// ─── Schedule data ─────────────────────────────
const loading = ref(false);
const occurrences = ref<LessonOccurrenceItem[]>([]);
const emptySlots = ref<SlotDef[]>([]);
const conflictGroups = computed(() => findScheduleConflictGroups(occurrences.value));
const conflictSummaryTitle = computed(() => {
  const count = conflictGroups.value.length;
  return `${count} teaching conflict${count === 1 ? '' : 's'} this week`;
});

const occupiedSlots = computed<OccupiedSlotDef[]>(() => {
  const result: OccupiedSlotDef[] = [];
  for (const o of occurrences.value) {
    if (o.planningStatus === 'CANCELLED') continue;
    const start = dayjs(o.plannedStartAt);
    const weekday = start.day() === 0 ? 7 : start.day();
    result.push({
      weekday,
      startTime: start.format('HH:mm'),
      endTime: dayjs(o.plannedEndAt).format('HH:mm'),
      courseLabel: o.className || o.packageName || o.lessonNo || '',
      subjectName: o.subjectName,
      contextLabel: [o.studentName, o.plannedClassroomName].filter(Boolean).join(' · '),
      studentName: o.studentName,
      teacherName: o.plannedTeacherName,
      classroomName: o.plannedClassroomName,
    });
  }
  return result;
});

async function loadSchedule() {
  if (!selectedTeacherId.value) return;
  const requestId = ++scheduleRequestSeq;
  const teacherId = selectedTeacherId.value;
  loading.value = true;
  try {
    const monday = dayjs(weekMonday.value);
    const sunday = monday.add(6, 'day').endOf('day');


    const res = await fetchLessonOccurrences({
      teacherId,
      startAt: monday.format('YYYY-MM-DDTHH:mm:ss'),
      endAt: sunday.format('YYYY-MM-DDTHH:mm:ss'),
      pageSize: 200,
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
  // Find occurrence matching the clicked cell
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
  return lesson.packageName || lesson.className || lesson.lessonNo || 'Lesson';
}

// ─── Init ──────────────────────────────────────
onMounted(async () => {
  if (isTeacher.value) {
    const teacherId = authStore.currentUser?.teacherId;
    if (teacherId) {
      selectedTeacherId.value = teacherId;
      teachers.value = [{ id: teacherId, teacherName: authStore.currentUser?.displayName || 'Me' }];
      loadSchedule();
    }
    return;
  }
  try {
    const res = await fetchAllTeachers();
    teachers.value = res.data.data.map(t => ({ id: t.id, teacherName: t.teacherName }));
  } catch { /* silent */ }
});
</script>

<style scoped>
.teacher-calendar {
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

.teacher-calendar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}

.teacher-calendar__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.teacher-calendar__body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

/* ── Left panel: Teacher list ─────────────── */
.teacher-list-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.teacher-list-panel__header {
  padding: 10px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.teacher-list-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  color: #909399;
  font-size: 13px;
}

.teacher-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.15s;
}

.teacher-item:hover {
  background: #f5f7fa;
}

.teacher-item--active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  padding-left: 11px;
}

.teacher-item--active:hover {
  background: #d9ecff;
}

.teacher-item__name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.teacher-item--active .teacher-item__name {
  color: #409eff;
}

/* ── Right panel: Calendar ────────────────── */
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

.calendar-controls__teacher {
  font-size: 13px;
  font-weight: 500;
  color: #409eff;
}

.teacher-conflict-summary {
  margin-bottom: 12px;
  border: 1px solid #fecdca;
  border-radius: 8px;
  background: #fff7f6;
  padding: 10px 12px;
}

.teacher-conflict-summary__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.teacher-conflict-summary__header strong {
  color: #b42318;
  font-size: 13px;
}

.teacher-conflict-summary__header span {
  color: #667085;
  font-size: 12px;
}

.teacher-conflict-summary__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px;
}

.teacher-conflict-card {
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
  cursor: pointer;
}

.teacher-conflict-card:hover {
  background: #fff1f0;
}

.teacher-conflict-card strong {
  color: #344054;
}

.teacher-conflict-card__time {
  color: #b42318;
  font-weight: 700;
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
</style>
