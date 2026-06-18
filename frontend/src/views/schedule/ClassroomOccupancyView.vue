<template>
  <div class="page-stack">
    <section class="classroom-calendar">
      <div class="classroom-calendar__header">
        <span class="classroom-calendar__title">Classroom Occupancy</span>
      </div>

      <div class="classroom-calendar__body">
        <!-- Left: Classroom list -->
        <aside class="classroom-list-panel">
          <div class="classroom-list-panel__header">
            <el-input
              v-model="searchText"
              placeholder="Search classrooms..."
              clearable
              :prefix-icon="Search"
            />
          </div>
          <div class="classroom-list-panel__body">
            <div v-if="filteredClassrooms.length === 0" class="classroom-list-panel__empty">
              {{ classrooms.length === 0 ? 'Loading...' : 'No classrooms found' }}
            </div>
            <div
              v-for="c in filteredClassrooms"
              :key="c.id"
              class="classroom-item"
              :class="{ 'classroom-item--active': selectedClassroomId === c.id }"
              @click="onClassroomSelect(c.id)"
            >
              <span class="classroom-item__name">{{ c.roomName }}</span>
              <el-icon v-if="selectedClassroomId === c.id" color="#409eff"><Check /></el-icon>
            </div>
          </div>
        </aside>

        <!-- Right: Calendar -->
        <main class="calendar-panel">
          <!-- No classroom selected -->
          <div v-if="!selectedClassroomId" class="calendar-empty">
            <el-icon :size="40" color="#c0c4cc"><Calendar /></el-icon>
            <p>Select a classroom on the left to view its weekly occupancy.</p>
          </div>

          <!-- Loading -->
          <div v-else-if="loading" class="calendar-loading">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            <span>Loading occupancy...</span>
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
                <el-tag size="small" type="info">{{ occupancyItems.length }} lessons this week</el-tag>
              </div>
              <span class="calendar-controls__classroom">
                {{ selectedClassroomName }}
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
      <div v-if="detailData" class="lesson-detail">
        <div class="detail-section">
          <h4 class="detail-section__title">Lesson info</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Lesson No.</label>
              <span>{{ detailData.lessonNo }}</span>
            </div>
            <div class="detail-field">
              <label>Classroom</label>
              <span>{{ detailData.classroomName }}</span>
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
              <span>{{ detailData.teacherName ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Hours</label>
              <span>{{ (detailData.plannedUnits * 0.5).toFixed(1) }}</span>
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
              <label>Status</label>
              <el-tag size="small" :type="planningStatusTag(detailData.planningStatus)">
                {{ planningStatusLabel(detailData.planningStatus) }}
              </el-tag>
            </div>
          </div>
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
import { fetchAllClassrooms } from '@/api/base-data';
import { fetchClassroomOccupancy, type ClassroomOccupancyQuery } from '@/api/schedule';
import type { ClassroomOccupancyItem, EntityId } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';

// ─── Classrooms ────────────────────────────────
const classrooms = ref<{ id: EntityId; roomName: string }[]>([]);
const searchText = ref('');
const selectedClassroomId = ref<EntityId | null>(null);

const filteredClassrooms = computed(() => {
  const q = searchText.value.trim().toLowerCase();
  if (!q) return classrooms.value;
  return classrooms.value.filter(c => c.roomName.toLowerCase().includes(q));
});

const selectedClassroomName = computed(() => {
  const c = classrooms.value.find(c => c.id === selectedClassroomId.value);
  return c?.roomName ?? '';
});

function onClassroomSelect(id: EntityId) {
  selectedClassroomId.value = id;
  weekOffset.value = 0;
  loadData();
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
  loadData();
}

function nextWeek() {
  weekOffset.value++;
  pickerDate.value = weekMonday.value;
  loadData();
}

function jumpToDate(date: string | null) {
  if (!date) return;
  const pickedMonday = dayjs(date).day(1).format('YYYY-MM-DD');
  const thisMonday = dayjs().startOf('day').day(1).format('YYYY-MM-DD');
  weekOffset.value = dayjs(pickedMonday).diff(dayjs(thisMonday), 'day') / 7;
  pickerDate.value = weekMonday.value;
  loadData();
}

// ─── Occupancy data ────────────────────────────
const loading = ref(false);
const occupancyItems = ref<ClassroomOccupancyItem[]>([]);
const emptySlots = ref<SlotDef[]>([]);

const occupiedSlots = computed<OccupiedSlotDef[]>(() => {
  const result: OccupiedSlotDef[] = [];
  for (const o of occupancyItems.value) {
    if (o.planningStatus === 'CANCELLED') continue;
    const start = dayjs(o.plannedStartAt);
    const weekday = start.day() === 0 ? 7 : start.day();
    result.push({
      weekday,
      startTime: start.format('HH:mm'),
      endTime: dayjs(o.plannedEndAt).format('HH:mm'),
      courseLabel: o.className || o.packageName || o.lessonNo || '',
      studentName: o.studentName,
      teacherName: o.teacherName,
      classroomName: o.classroomName,
    });
  }
  return result;
});

async function loadData() {
  if (!selectedClassroomId.value) return;
  loading.value = true;
  try {
    const monday = dayjs(weekMonday.value);
    const sunday = monday.add(6, 'day').endOf('day');
    const params: ClassroomOccupancyQuery = {
      classroomId: selectedClassroomId.value,
      startAt: monday.format('YYYY-MM-DDTHH:mm:ss'),
      endAt: sunday.format('YYYY-MM-DDTHH:mm:ss'),
      pageNum: 1,
      pageSize: 200,
    };
    // 教室占用按实际课次返回，班课课次同样带 plannedClassroomId，已包含在内，
    // 无需再依赖班课的重复模板（scheduleSlots）。
    const res = await fetchClassroomOccupancy(params);
    occupancyItems.value = res.data.data.records;
  } catch {
    ElMessage.error('Failed to load occupancy data.');
    occupancyItems.value = [];
  } finally {
    loading.value = false;
  }
}

// ─── Cell click → detail dialog ────────────────
const detailDialogVisible = ref(false);
const detailData = ref<ClassroomOccupancyItem | null>(null);

function onCellClick(weekday: number, hour: number, minute: number) {
  const clickedMinutes = hour * 60 + minute;
  const item = occupancyItems.value.find(o => {
    const start = dayjs(o.plannedStartAt);
    const sWeekday = start.day() === 0 ? 7 : start.day();
    if (sWeekday !== weekday) return false;
    const sMin = start.hour() * 60 + start.minute();
    const end = dayjs(o.plannedEndAt);
    const eMin = end.hour() * 60 + end.minute();
    return clickedMinutes >= sMin && clickedMinutes < eMin;
  });
  if (item) {
    detailData.value = item;
    detailDialogVisible.value = true;
  }
}

// ─── Helpers ───────────────────────────────────
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
    const res = await fetchAllClassrooms();
    classrooms.value = (res.data.data as any[]).map(c => ({ id: c.id, roomName: c.roomName }));
  } catch { /* silent */ }
});
</script>

<style scoped>
.classroom-calendar {
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

.classroom-calendar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}

.classroom-calendar__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.classroom-calendar__body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

/* ── Left panel: Classroom list ─────────────── */
.classroom-list-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.classroom-list-panel__header {
  padding: 10px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.classroom-list-panel__body {
  flex: 1;
  overflow-y: auto;
}

.classroom-list-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  color: #909399;
  font-size: 13px;
}

.classroom-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.15s;
}

.classroom-item:hover {
  background: #f5f7fa;
}

.classroom-item--active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  padding-left: 11px;
}

.classroom-item--active:hover {
  background: #d9ecff;
}

.classroom-item__name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.classroom-item--active .classroom-item__name {
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

.calendar-controls__classroom {
  font-size: 13px;
  font-weight: 500;
  color: #409eff;
}

/* ── Dialog ─────────────────────────────────── */
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
</style>
