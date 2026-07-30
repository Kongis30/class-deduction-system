<template>
  <div class="page-stack">
    <section class="classroom-calendar">
      <div class="classroom-calendar__header">
        <div>
          <span class="classroom-calendar__title">Room Calendar</span>
          <p class="classroom-calendar__note">Select a classroom to review its weekly occupancy. Missing rooms can be assigned from the queue below.</p>
        </div>
        <div class="classroom-calendar__actions">
          <el-button @click="goClassrooms">Manage rooms</el-button>
        </div>
      </div>

      <details class="classroom-help">
        <summary>How to use this page</summary>
        <div class="classroom-help__content">
          <ol>
            <li>Select a room from the left list to see its weekly occupancy calendar.</li>
            <li>Use the arrows or date picker to check another week. Marked slots show occupied or conflicting times.</li>
            <li>Open <strong>Room assignment queue</strong> only when scheduled classes or lessons still need a room.</li>
            <li>Click a timetable slot to view the lesson details. Use <strong>Manage rooms</strong> only to add, edit, or deactivate room records.</li>
          </ol>
        </div>
      </details>

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
              {{ classroomListEmptyText }}
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
            <el-alert
              v-if="occupancyError"
              class="calendar-alert"
              type="error"
              :closable="false"
              show-icon
              :title="occupancyError"
            />
            <div class="room-occupancy-scroll">
              <div class="calendar-help">
                <span><i class="calendar-help__mark calendar-help__mark--occupied" />Occupied or conflicting slots</span>
                <span><i class="calendar-help__mark calendar-help__mark--conflict" />Conflict: multiple lessons use this room at the same time</span>
                <span>Open the room assignment queue below only for lessons that still need a room.</span>
              </div>
              <section v-if="conflictGroups.length" class="conflict-summary">
                <div class="conflict-summary__header">
                  <strong>{{ conflictGroups.length }} room conflict{{ conflictGroups.length === 1 ? '' : 's' }} this week</strong>
                  <span>Resolve these before confirming attendance or adding more lessons.</span>
                </div>
                <div class="conflict-summary__list">
                  <button
                    v-for="group in conflictGroups"
                    :key="group.key"
                    class="conflict-card"
                    type="button"
                    @click="openConflictGroup(group)"
                  >
                    <span class="conflict-card__time">{{ group.timeLabel }}</span>
                    <span class="conflict-card__title">{{ group.items.length }} overlapping lessons</span>
                    <span class="conflict-card__detail">{{ group.items.map(item => item.courseLabel).join(' / ') }}</span>
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
              <div v-if="occupancyRows.length === 0 && !occupancyError" class="calendar-empty-state empty-state">
                <strong>No room occupancy for this week.</strong>
                <span>This is expected when no lessons use this room this week. Check the room assignment queue for lessons that still need a room.</span>
              </div>
              <el-table
                :data="occupancyRows"
                size="small"
                class="occupancy-table"
                empty-text="No occupancy rows for this week."
                :row-class-name="occupancyRowClassName"
              >
                <el-table-column label="Start" min-width="145" prop="startLabel" />
                <el-table-column label="End" min-width="145" prop="endLabel" />
                <el-table-column label="Course / Student" min-width="180">
                  <template #default="{ row }">
                    <div class="cell-stack">
                      <strong>{{ row.courseLabel }}</strong>
                      <span>{{ row.studentName || '—' }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="Teacher" min-width="120" prop="teacherName" />
                <el-table-column label="Status" min-width="110">
                  <template #default="{ row }">
                    <el-tag v-if="row.hasConflict" size="small" type="danger">Conflict</el-tag>
                    <el-tag v-else size="small" :type="planningStatusTag(row.planningStatus)">
                      {{ planningStatusLabel(row.planningStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </main>
      </div>

      <details class="pending-panel">
        <summary class="pending-panel__summary">
          <div>
            <strong>Room assignment queue</strong>
            <span>Open only when scheduled classes or lessons still need a classroom.</span>
          </div>
          <el-tag size="small" :type="pendingRows.length ? 'warning' : 'info'">
            {{ pendingRows.length }} pending
          </el-tag>
        </summary>
        <div class="pending-panel__content">
          <div class="pending-panel__toolbar">
            <el-button size="small" :loading="pendingLoading" @click="loadPendingAssignments">Refresh</el-button>
          </div>
          <el-alert
            v-if="pendingError"
            class="pending-panel__alert"
            type="error"
            :closable="false"
            show-icon
            :title="pendingError"
          />
          <el-table
            v-loading="pendingLoading"
            :data="pendingRows"
            size="small"
            class="pending-table"
            empty-text="No pending classroom assignments."
          >
            <el-table-column label="Type" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.targetType === 'CLASS_GROUP' ? 'warning' : 'info'">
                  {{ row.targetType === 'CLASS_GROUP' ? 'Class' : 'Lesson' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Course / Student" min-width="220">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong>{{ row.courseLabel }}</strong>
                  <span>{{ row.studentName || row.scheduleDesc || '—' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Teacher" min-width="120" prop="teacherName" />
            <el-table-column label="Next time" min-width="150" prop="timeLabel" />
            <el-table-column label="Classroom" min-width="190">
              <template #default="{ row }">
                <el-select
                  v-model="pendingClassroomSelections[row.rowKey]"
                  placeholder="Select room"
                  filterable
                  style="width: 170px;"
                >
                  <el-option
                    v-for="room in classrooms"
                    :key="room.id"
                    :label="room.roomName"
                    :value="room.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="Action" width="110" fixed="right">
              <template #default="{ row }">
                <el-button
                  size="small"
                  type="primary"
                  :loading="savingPendingKey === row.rowKey"
                  :disabled="!pendingClassroomSelections[row.rowKey]"
                  @click="savePendingAssignment(row)"
                >
                  Save
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </details>
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
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { extractErrorMessage } from '@/api/http';
import { fetchAllClassrooms } from '@/api/base-data';
import {
  backfillPendingClassroom,
  fetchClassroomOccupancy,
  fetchPendingClassroomAssignments,
  type ClassroomOccupancyQuery,
} from '@/api/schedule';
import type { ClassroomOccupancyItem, EntityId, PendingClassroomAssignmentItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';

const route = useRoute();
const router = useRouter();

// ─── Classrooms ────────────────────────────────
const classrooms = ref<{ id: EntityId; roomName: string }[]>([]);
const classroomsLoading = ref(false);
const classroomLoadError = ref('');
const searchText = ref('');
const selectedClassroomId = ref<EntityId | null>(null);

// Monotonic token guarding against stale responses when switching classrooms/weeks quickly.
let occupancyRequestSeq = 0;

const filteredClassrooms = computed(() => {
  const q = searchText.value.trim().toLowerCase();
  if (!q) return classrooms.value;
  return classrooms.value.filter(c => c.roomName.toLowerCase().includes(q));
});

const classroomListEmptyText = computed(() => {
  if (classroomsLoading.value) return 'Loading classrooms...';
  if (classroomLoadError.value) return classroomLoadError.value;
  if (classrooms.value.length === 0) return 'No classrooms yet. Add rooms when schedules are ready to backfill.';
  return 'No classrooms match this search.';
});

const selectedClassroomName = computed(() => {
  const c = classrooms.value.find(c => c.id === selectedClassroomId.value);
  return c?.roomName ?? '';
});

// ─── Pending classroom assignments ────────────
const pendingLoading = ref(false);
const pendingError = ref('');
const pendingAssignments = ref<PendingClassroomAssignmentItem[]>([]);
const pendingClassroomSelections = ref<Record<string, EntityId>>({});
const savingPendingKey = ref('');

const pendingRows = computed(() => pendingAssignments.value.map(item => {
  const courseLabel = item.className || item.packageName || item.lessonNo || 'Pending assignment';
  const timeLabel = item.plannedStartAt
    ? dayjs(item.plannedStartAt).format('MMM D HH:mm')
    : 'No generated lesson yet';
  return {
    ...item,
    rowKey: pendingRowKey(item),
    courseLabel,
    teacherName: item.teacherName || '—',
    timeLabel,
  };
}));

function pendingRowKey(item: Pick<PendingClassroomAssignmentItem, 'targetType' | 'targetId'>) {
  return `${item.targetType}:${item.targetId}`;
}

async function loadPendingAssignments() {
  pendingLoading.value = true;
  pendingError.value = '';
  try {
    const res = await fetchPendingClassroomAssignments();
    pendingAssignments.value = res.data.data ?? [];
    const nextSelections: Record<string, EntityId> = {};
    for (const item of pendingAssignments.value) {
      const key = pendingRowKey(item);
      if (pendingClassroomSelections.value[key]) {
        nextSelections[key] = pendingClassroomSelections.value[key];
      }
    }
    pendingClassroomSelections.value = nextSelections;
  } catch (error) {
    pendingError.value = `Failed to load pending assignments: ${extractErrorMessage(error)}`;
    ElMessage.error(pendingError.value);
  } finally {
    pendingLoading.value = false;
  }
}

async function savePendingAssignment(row: PendingClassroomAssignmentItem & { rowKey: string }) {
  const classroomId = pendingClassroomSelections.value[row.rowKey];
  if (!classroomId) return;
  savingPendingKey.value = row.rowKey;
  try {
    await backfillPendingClassroom({
      targetType: row.targetType,
      targetId: row.targetId,
      classroomId,
    });
    ElMessage.success('Classroom assigned.');
    await loadPendingAssignments();
    if (selectedClassroomId.value === classroomId) {
      await loadData();
    }
  } catch (error) {
    ElMessage.error(`Failed to assign classroom: ${extractErrorMessage(error)}`);
  } finally {
    savingPendingKey.value = '';
  }
}

function onClassroomSelect(id: EntityId) {
  selectedClassroomId.value = id;
  weekOffset.value = 0;
  router.replace({ path: route.path, query: { ...route.query, classroomId: id } });
  loadData();
}

function goClassrooms() {
  router.push('/classrooms');
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
const occupancyError = ref('');
const occupancyItems = ref<ClassroomOccupancyItem[]>([]);
const emptySlots = ref<SlotDef[]>([]);

interface OccupancyRow {
  occurrenceId: EntityId;
  courseLabel: string;
  studentName?: string;
  teacherName: string;
  planningStatus: string;
  startLabel: string;
  endLabel: string;
  hasConflict: boolean;
}

interface ConflictGroup {
  key: string;
  startAt: string;
  endAt: string;
  timeLabel: string;
  items: OccupancyRow[];
}

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

const conflictOccurrenceIds = computed(() => {
  const ids = new Set<EntityId>();
  for (const group of conflictGroups.value) {
    for (const item of group.items) {
      ids.add(item.occurrenceId);
    }
  }
  return ids;
});

const occupancyRows = computed<OccupancyRow[]>(() => occupancyItems.value
  .filter(o => o.planningStatus !== 'CANCELLED')
  .slice()
  .sort((a, b) => dayjs(a.plannedStartAt).valueOf() - dayjs(b.plannedStartAt).valueOf())
  .map(o => ({
    occurrenceId: o.occurrenceId,
    courseLabel: o.className || o.packageName || o.lessonNo || 'Lesson',
    studentName: o.studentName,
    teacherName: o.teacherName || '—',
    planningStatus: o.planningStatus,
    startLabel: dayjs(o.plannedStartAt).format('MMM D HH:mm'),
    endLabel: dayjs(o.plannedEndAt).format('MMM D HH:mm'),
    hasConflict: conflictOccurrenceIds.value.has(o.occurrenceId),
  })));

const conflictGroups = computed<ConflictGroup[]>(() => {
  const activeItems = occupancyItems.value.filter(o => o.planningStatus !== 'CANCELLED');
  const segmentMap = new Map<string, ClassroomOccupancyItem[]>();
  for (const item of activeItems) {
    const start = dayjs(item.plannedStartAt);
    const end = dayjs(item.plannedEndAt);
    for (let cursor = start; cursor.isBefore(end); cursor = cursor.add(30, 'minute')) {
      const key = cursor.format('YYYY-MM-DDTHH:mm');
      segmentMap.set(key, [...(segmentMap.get(key) || []), item]);
    }
  }

  const groups: ConflictGroup[] = [];
  const sortedSegments = [...segmentMap.entries()]
    .filter(([, items]) => items.length > 1)
    .sort(([a], [b]) => a.localeCompare(b));

  for (const [segmentStart, segmentItems] of sortedSegments) {
    const segmentIds = segmentItems.map(item => String(item.occurrenceId)).sort().join('|');
    const previous = groups[groups.length - 1];
    const canMerge = previous
      && previous.endAt === segmentStart
      && previous.items.map(item => String(item.occurrenceId)).sort().join('|') === segmentIds;

    if (canMerge) {
      previous.endAt = dayjs(segmentStart).add(30, 'minute').format('YYYY-MM-DDTHH:mm');
      previous.timeLabel = formatConflictTime(previous.startAt, previous.endAt);
      continue;
    }

    const startAt = segmentStart;
    const endAt = dayjs(segmentStart).add(30, 'minute').format('YYYY-MM-DDTHH:mm');
    const items = segmentItems
      .slice()
      .sort((a, b) => dayjs(a.plannedStartAt).valueOf() - dayjs(b.plannedStartAt).valueOf())
      .map(toOccupancyRow);
    groups.push({
      key: `${segmentStart}:${segmentIds}`,
      startAt,
      endAt,
      timeLabel: formatConflictTime(startAt, endAt),
      items,
    });
  }
  return groups;
});

function toOccupancyRow(o: ClassroomOccupancyItem): OccupancyRow {
  return {
    occurrenceId: o.occurrenceId,
    courseLabel: o.className || o.packageName || o.lessonNo || 'Lesson',
    studentName: o.studentName,
    teacherName: o.teacherName || '—',
    planningStatus: o.planningStatus,
    startLabel: dayjs(o.plannedStartAt).format('MMM D HH:mm'),
    endLabel: dayjs(o.plannedEndAt).format('MMM D HH:mm'),
    hasConflict: true,
  };
}

function formatConflictTime(startAt: string, endAt: string) {
  return `${dayjs(startAt).format('ddd MMM D HH:mm')}–${dayjs(endAt).format('HH:mm')}`;
}

function openConflictGroup(group: ConflictGroup) {
  const first = group.items[0];
  const item = occupancyItems.value.find(o => o.occurrenceId === first?.occurrenceId);
  if (!item) return;
  detailData.value = item;
  detailDialogVisible.value = true;
}

function occupancyRowClassName({ row }: { row: OccupancyRow }) {
  return row.hasConflict ? 'occupancy-row--conflict' : '';
}

async function loadData() {
  if (!selectedClassroomId.value) return;
  const requestId = ++occupancyRequestSeq;
  const classroomId = selectedClassroomId.value;
  loading.value = true;
  occupancyError.value = '';
  try {
    const monday = dayjs(weekMonday.value);
    const sunday = monday.add(6, 'day').endOf('day');
    const params: ClassroomOccupancyQuery = {
      classroomId,
      startAt: monday.format('YYYY-MM-DDTHH:mm:ss'),
      endAt: sunday.format('YYYY-MM-DDTHH:mm:ss'),
      pageNum: 1,
      pageSize: 200,
    };
    const res = await fetchClassroomOccupancy(params);
    // Discard if a newer selection/week has superseded this request.
    if (requestId !== occupancyRequestSeq) return;
    occupancyItems.value = res.data.data.records;
  } catch (error) {
    if (requestId !== occupancyRequestSeq) return;
    occupancyError.value = `Failed to load occupancy data: ${extractErrorMessage(error)}`;
    ElMessage.error(occupancyError.value);
    occupancyItems.value = [];
  } finally {
    if (requestId === occupancyRequestSeq) {
      loading.value = false;
    }
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
  classroomsLoading.value = true;
  classroomLoadError.value = '';
  try {
    const res = await fetchAllClassrooms();
    classrooms.value = (res.data.data as any[]).map(c => ({ id: c.id, roomName: c.roomName }));
    selectInitialClassroom();
    await loadPendingAssignments();
  } catch (error) {
    classroomLoadError.value = `Failed to load classrooms: ${extractErrorMessage(error)}`;
    ElMessage.error(classroomLoadError.value);
  } finally {
    classroomsLoading.value = false;
  }
});

function selectInitialClassroom() {
  const queryId = resolveQueryId(route.query.classroomId);
  if (!queryId) return;
  const match = classrooms.value.find(c => c.id === queryId);
  if (!match) return;
  selectedClassroomId.value = match.id;
  loadData();
}

function resolveQueryId(value: unknown): EntityId | null {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : null;
  }
  return typeof value === 'string' ? value : null;
}
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
  height: max(calc(100vh - 120px), 760px);
  min-height: 760px;
  overflow: hidden;
}

@media (max-width: 1080px) {
  .page-stack {
    overflow-x: auto;
  }

  .classroom-calendar {
    min-width: 1020px;
  }
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

.classroom-calendar__note {
  margin: 3px 0 0;
  color: #606266;
  font-size: 12px;
}

.classroom-calendar__actions {
  display: flex;
  gap: 8px;
}

.classroom-help {
  flex-shrink: 0;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fafcff;
}

.classroom-help summary {
  padding: 9px 12px;
  color: #334155;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  user-select: none;
}

.classroom-help__content {
  padding: 0 14px 10px 32px;
  border-top: 1px solid #eef2f7;
  color: #606266;
  font-size: 12px;
}

.classroom-help__content ol {
  margin: 9px 0 0;
  padding-left: 16px;
}

.classroom-help__content li {
  margin: 4px 0;
}

.classroom-help__content strong {
  color: #303133;
}

.pending-panel {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafcff;
  flex-shrink: 0;
  overflow: hidden;
}

.pending-panel__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  cursor: pointer;
  user-select: none;
}

.pending-panel__summary div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pending-panel__summary strong {
  color: #303133;
  font-size: 13px;
}

.pending-panel__summary div > span {
  color: #606266;
  font-size: 12px;
}

.pending-panel__content {
  padding: 0 12px 12px;
  overflow-x: auto;
}

.pending-panel__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.pending-panel__alert {
  margin-bottom: 10px;
}

.pending-table {
  min-width: 960px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.classroom-calendar__body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
  min-width: 980px;
  overflow: hidden;
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
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
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
  flex-shrink: 0;
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

.calendar-alert {
  flex-shrink: 0;
  margin-bottom: 10px;
}

.room-occupancy-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.room-occupancy-scroll :deep(.weekly-time-selector),
.occupancy-table {
  min-width: 760px;
}

.calendar-help {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 10px;
  color: #606266;
  font-size: 12px;
}

.calendar-help span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.calendar-help__mark {
  display: inline-block;
  width: 18px;
  height: 10px;
  border: 1px solid #ffd0cc;
  border-radius: 2px;
}

.calendar-help__mark--occupied {
  background: repeating-linear-gradient(
    -45deg,
    #fff1f0,
    #fff1f0 4px,
    #ffe8e6 4px,
    #ffe8e6 8px
  );
}

.calendar-help__mark--conflict {
  background: #fff1f0;
  box-shadow:
    inset 4px 0 0 #f56c6c,
    inset 0 0 0 1px #f56c6c;
}

.conflict-summary {
  margin-bottom: 12px;
  border: 1px solid #fecdca;
  border-radius: 8px;
  background: #fff7f6;
  padding: 10px 12px;
}

.conflict-summary__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.conflict-summary__header strong {
  color: #b42318;
  font-size: 13px;
}

.conflict-summary__header span {
  color: #667085;
  font-size: 12px;
}

.conflict-summary__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px;
}

.conflict-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  width: 100%;
  border: 1px solid #fda29b;
  border-radius: 6px;
  background: #fff;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
}

.conflict-card:hover {
  background: #fff1f0;
}

.conflict-card__time {
  color: #b42318;
  font-size: 12px;
  font-weight: 700;
}

.conflict-card__title {
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}

.conflict-card__detail {
  color: #667085;
  font-size: 12px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.calendar-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 12px;
}

.calendar-empty-state span {
  color: #606266;
}

.occupancy-table {
  margin-top: 14px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.occupancy-table :deep(.occupancy-row--conflict) {
  background: #fff7f6;
}

.occupancy-table :deep(.occupancy-row--conflict td:first-child) {
  box-shadow: inset 4px 0 0 #f56c6c;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cell-stack strong {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.cell-stack span {
  color: #909399;
  font-size: 12px;
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
