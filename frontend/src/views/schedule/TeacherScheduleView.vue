<template>
  <div class="page-stack">
    <section class="teacher-calendar">
      <div class="teacher-calendar__header">
        <span class="teacher-calendar__title">Teacher Calendar</span>
      </div>

      <div class="teacher-calendar__body">
        <!-- Left: Teacher list -->
        <aside class="teacher-list-panel">
          <div class="teacher-list-panel__header">
            <el-input
              v-model="searchText"
              placeholder="Search teachers..."
              clearable
              :prefix-icon="Search"
            />
          </div>
          <div class="teacher-list-panel__body">
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
          </div>
        </aside>

        <!-- Right: Calendar -->
        <main class="calendar-panel">
          <!-- No teacher selected -->
          <div v-if="!selectedTeacherId" class="calendar-empty">
            <el-icon :size="40" color="#c0c4cc"><Calendar /></el-icon>
            <p>Select a teacher on the left to view their weekly class schedule.</p>
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
  fetchClassGroupsByTeacher,
} from '@/api/schedule';
import type { EntityId, LessonOccurrenceItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';

// ─── Teachers ───────────────────────────────────
const teachers = ref<{ id: EntityId; teacherName: string }[]>([]);
const searchText = ref('');
const selectedTeacherId = ref<EntityId | null>(null);

const filteredTeachers = computed(() => {
  const q = searchText.value.trim().toLowerCase();
  if (!q) return teachers.value;
  return teachers.value.filter(t => t.teacherName.toLowerCase().includes(q));
});

const selectedTeacherName = computed(() => {
  const t = teachers.value.find(t => t.id === selectedTeacherId.value);
  return t?.teacherName ?? '';
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
const teacherClassGroups = ref<any[]>([]);
const teacherClassGroupSlots = ref<OccupiedSlotDef[]>([]);

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
      studentName: o.studentName,
      teacherName: o.plannedTeacherName,
      classroomName: o.plannedClassroomName,
    });
  }
  // 添加班课占用
  for (const slot of teacherClassGroupSlots.value) {
    result.push(slot);
  }
  return result;
});

async function loadSchedule() {
  if (!selectedTeacherId.value) return;
  loading.value = true;
  teacherClassGroupSlots.value = [];
  try {
    const monday = dayjs(weekMonday.value);
    const sunday = monday.add(6, 'day').endOf('day');
    // 1v1 课次
    const res = await fetchLessonOccurrences({
      teacherId: selectedTeacherId.value,
      startAt: monday.format('YYYY-MM-DDTHH:mm:ss'),
      endAt: sunday.format('YYYY-MM-DDTHH:mm:ss'),
      pageSize: 100,
    });
    occurrences.value = res.data.data.records;

    // 班课占用（解析 scheduleDesc）
    const clsRes = await fetchClassGroupsByTeacher(selectedTeacherId.value);
    teacherClassGroups.value = clsRes.data.data;
    teacherClassGroupSlots.value = parseClassGroupSlots(clsRes.data.data);
  } catch {
    ElMessage.error('Failed to load schedule.');
    occurrences.value = [];
    teacherClassGroupSlots.value = [];
  } finally {
    loading.value = false;
  }
}

function parseClassGroupSlots(classGroups: any[]): OccupiedSlotDef[] {
  const slots: OccupiedSlotDef[] = [];
  for (const cls of classGroups) {
    const scheduleSlots = cls.scheduleSlots || [];
    for (const s of scheduleSlots) {
      slots.push({
        weekday: s.weekday,
        startTime: s.startTime,
        endTime: s.endTime,
        courseLabel: cls.className,
        teacherName: cls.teacherName,
        classroomName: cls.classroomName,
      });
    }
  }
  return slots;
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

// ─── Init ──────────────────────────────────────
onMounted(async () => {
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

.teacher-list-panel__body {
  flex: 1;
  overflow-y: auto;
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
