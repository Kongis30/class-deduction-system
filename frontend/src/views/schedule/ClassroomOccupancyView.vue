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
            <div v-if="filteredClassroomGroups.length === 0" class="classroom-list-panel__empty">
              {{ classroomListEmptyText }}
            </div>
            <section
              v-for="group in filteredClassroomGroups"
              :key="group.campus"
              class="classroom-campus-group"
            >
              <div class="classroom-campus-group__header">
                <span>{{ group.campus }}</span>
                <small>{{ group.rooms.length }}</small>
              </div>
              <div
                v-for="c in group.rooms"
                :key="c.id"
                class="classroom-item"
                :class="{ 'classroom-item--active': selectedClassroomId === c.id }"
                @click="onClassroomSelect(c.id)"
              >
                <span class="classroom-item__name">{{ c.roomName }}</span>
                <el-icon v-if="selectedClassroomId === c.id" color="#409eff"><Check /></el-icon>
              </div>
            </section>
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
          <div class="pending-panel__heading">
            <strong>Room assignment queue</strong>
            <span>Open only when scheduled classes or lessons still need a classroom.</span>
          </div>
          <div class="pending-panel__summary-actions">
            <el-tag class="pending-count-tag" size="large" :type="pendingAssignmentRows.length ? 'warning' : 'info'">
              {{ pendingAssignmentRows.length }} pending
            </el-tag>
            <el-button size="small" :loading="pendingLoading" @click.stop="loadPendingAssignments">
              Refresh
            </el-button>
          </div>
        </summary>
        <div class="pending-panel__content">
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
            :data="pendingTreeRows"
            size="small"
            class="pending-table"
            row-key="rowKey"
            :indent="32"
            :tree-props="{ children: 'children' }"
            :row-class-name="pendingRowClassName"
            empty-text="No pending classroom assignments."
          >
            <el-table-column label="Product / Grade / Class" min-width="420">
              <template #default="{ row }">
                <div class="cell-stack pending-hierarchy-cell" :class="`pending-hierarchy-cell--${row.rowType}`">
                  <div class="pending-hierarchy-cell__top">
                    <strong>{{ pendingMainLabel(row) }}</strong>
                    <el-tag size="small" effect="plain" :type="pendingTypeTag(row)" round>
                      {{ pendingTypeLabel(row) }}
                    </el-tag>
                  </div>
                  <span>{{ row.secondaryLabel }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Next time" min-width="150">
              <template #default="{ row }">
                <span :class="{ 'pending-muted-cell': !isPendingAssignmentRow(row) }">
                  {{ isPendingAssignmentRow(row) ? row.timeLabel : '' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="Teacher" min-width="120">
              <template #default="{ row }">
                <span :class="{ 'pending-muted-cell': !isPendingAssignmentRow(row) }">
                  {{ isPendingAssignmentRow(row) ? row.teacherName : '' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="Classroom" min-width="260">
              <template #default="{ row }">
                <el-select
                  v-if="isPendingAssignmentRow(row)"
                  v-model="pendingClassroomSelections[row.rowKey]"
                  class="pending-room-select"
                  placeholder="Select room"
                  filterable
                >
                  <el-option-group
                    v-for="group in classroomGroups"
                    :key="group.campus"
                    :label="group.campus"
                  >
                    <el-option
                      v-for="room in group.rooms"
                      :key="room.id"
                      :label="room.roomName"
                      :value="room.id"
                    >
                      <span class="room-option__name">{{ room.roomName }}</span>
                      <small class="room-option__campus">{{ group.campus }}</small>
                    </el-option>
                  </el-option-group>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="Action" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="isPendingAssignmentRow(row)"
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
import type { ClassroomItem, ClassroomOccupancyItem, EntityId, PendingClassroomAssignmentItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';

const route = useRoute();
const router = useRouter();

// ─── Classrooms ────────────────────────────────
type ClassroomOption = Pick<ClassroomItem, 'id' | 'roomName' | 'campus' | 'campusId'>;

interface ClassroomCampusGroup {
  campus: string;
  rooms: ClassroomOption[];
}

const classrooms = ref<ClassroomOption[]>([]);
const classroomsLoading = ref(false);
const classroomLoadError = ref('');
const searchText = ref('');
const selectedClassroomId = ref<EntityId | null>(null);

// Monotonic token guarding against stale responses when switching classrooms/weeks quickly.
let occupancyRequestSeq = 0;

const filteredClassrooms = computed(() => {
  const q = searchText.value.trim().toLowerCase();
  if (!q) return classrooms.value;
  return classrooms.value.filter(c =>
    c.roomName.toLowerCase().includes(q)
      || campusLabel(c).toLowerCase().includes(q)
  );
});

const filteredClassroomGroups = computed<ClassroomCampusGroup[]>(() => groupClassroomsByCampus(filteredClassrooms.value));

const classroomGroups = computed<ClassroomCampusGroup[]>(() => groupClassroomsByCampus(classrooms.value));

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

function campusLabel(classroom: Pick<ClassroomItem, 'campus'>) {
  return classroom.campus?.trim() || 'Unassigned campus';
}

// ─── Pending classroom assignments ────────────
const pendingLoading = ref(false);
const pendingError = ref('');
const pendingAssignments = ref<PendingClassroomAssignmentItem[]>([]);
const pendingClassroomSelections = ref<Record<string, EntityId>>({});
const savingPendingKey = ref('');

interface PendingAssignmentRow extends PendingClassroomAssignmentItem {
  rowKey: string;
  rowType: 'assignment';
  productLabel: string;
  gradeLabel: string;
  classLabel: string;
  secondaryLabel: string;
  teacherName: string;
  timeLabel: string;
}

interface PendingGroupRow {
  rowKey: string;
  rowType: 'product' | 'grade';
  productLabel: string;
  gradeLabel?: string;
  secondaryLabel: string;
  teacherName: string;
  timeLabel: string;
  children: PendingQueueRow[];
}

type PendingQueueRow = PendingAssignmentRow | PendingGroupRow;

const pendingAssignmentRows = computed<PendingAssignmentRow[]>(() => pendingAssignments.value.map(item => {
  const timeLabel = item.plannedStartAt
    ? dayjs(item.plannedStartAt).format('MMM D HH:mm')
    : 'No generated lesson yet';
  return {
    ...item,
    rowKey: pendingRowKey(item),
    rowType: 'assignment',
    productLabel: pendingProductLabel(item),
    gradeLabel: pendingGradeLabel(item),
    classLabel: pendingClassLabel(item),
    secondaryLabel: pendingSecondaryLabel(item),
    teacherName: item.teacherName || '—',
    timeLabel,
  };
}));

const pendingTreeRows = computed<PendingQueueRow[]>(() => buildPendingQueueTree(pendingAssignmentRows.value));

function groupClassroomsByCampus(source: ClassroomOption[]) {
  const groups = new Map<string, ClassroomOption[]>();
  for (const classroom of source) {
    const campus = campusLabel(classroom);
    groups.set(campus, [...(groups.get(campus) ?? []), classroom]);
  }
  return Array.from(groups.entries())
    .map(([campus, rooms]) => ({
      campus,
      rooms: [...rooms].sort((left, right) => left.roomName.localeCompare(right.roomName)),
    }))
    .sort((left, right) => left.campus.localeCompare(right.campus));
}

function buildPendingQueueTree(rows: PendingAssignmentRow[]): PendingQueueRow[] {
  const productGroups = new Map<string, PendingGroupRow>();
  for (const row of rows) {
    const productKey = normalizedGroupKey(row.productLabel);
    let productGroup = productGroups.get(productKey);
    if (!productGroup) {
      productGroup = {
        rowKey: `product:${productKey}`,
        rowType: 'product',
        productLabel: row.productLabel,
        secondaryLabel: 'Product',
        teacherName: '—',
        timeLabel: '—',
        children: [],
      };
      productGroups.set(productKey, productGroup);
    }

    const gradeKey = normalizedGroupKey(row.gradeLabel);
    const gradeRowKey = `${productGroup.rowKey}:grade:${gradeKey}`;
    let gradeGroup = productGroup.children.find(
      child => child.rowType === 'grade' && child.rowKey === gradeRowKey,
    ) as PendingGroupRow | undefined;
    if (!gradeGroup) {
      gradeGroup = {
        rowKey: gradeRowKey,
        rowType: 'grade',
        productLabel: row.productLabel,
        gradeLabel: row.gradeLabel,
        secondaryLabel: 'Grade',
        teacherName: '—',
        timeLabel: '—',
        children: [],
      };
      productGroup.children.push(gradeGroup);
    }
    gradeGroup.children.push(row);
  }

  return [...productGroups.values()]
    .map(productGroup => ({
      ...productGroup,
      children: sortPendingGroups(productGroup.children),
      secondaryLabel: `${countPendingLeaves(productGroup)} pending`,
    }))
    .sort((left, right) => left.productLabel.localeCompare(right.productLabel, undefined, { numeric: true }));
}

function sortPendingGroups(rows: PendingQueueRow[]) {
  return rows
    .map(row => {
      if (row.rowType !== 'grade') return row;
      return {
        ...row,
        children: [...row.children].sort(sortPendingAssignmentRows),
        secondaryLabel: `${row.children.length} pending`,
      };
    })
    .sort((left, right) => pendingMainLabel(left).localeCompare(pendingMainLabel(right), undefined, { numeric: true }));
}

function sortPendingAssignmentRows(left: PendingQueueRow, right: PendingQueueRow) {
  if (!isPendingAssignmentRow(left) || !isPendingAssignmentRow(right)) return 0;
  return [
    left.classLabel.localeCompare(right.classLabel, undefined, { numeric: true }),
    (left.studentName || '').localeCompare(right.studentName || '', undefined, { numeric: true }),
    (left.plannedStartAt || '').localeCompare(right.plannedStartAt || ''),
  ].find(result => result !== 0) ?? 0;
}

function countPendingLeaves(row: PendingQueueRow): number {
  if (isPendingAssignmentRow(row)) return 1;
  return row.children.reduce((total, child) => total + countPendingLeaves(child), 0);
}

function normalizedGroupKey(label: string) {
  return label.trim().toLowerCase() || 'unset';
}

function pendingProductLabel(item: PendingClassroomAssignmentItem) {
  const productName = item.productName?.trim();
  if (productName) {
    return productName;
  }
  const className = item.className?.trim();
  const actualClass = item.actualClass?.trim();
  if (item.targetType === 'CLASS_GROUP' && className) {
    const separatorIndex = className.indexOf('·');
    if (separatorIndex > -1) {
      return className.slice(0, separatorIndex).trim() || className;
    }
    if (actualClass && className.endsWith(actualClass)) {
      return className.slice(0, -actualClass.length).replace(/[·\-\s]+$/u, '').trim() || className;
    }
    return item.packageName?.trim() || 'Product not set';
  }
  return item.packageName?.trim() || className || item.lessonNo || 'Product not set';
}

function pendingGradeLabel(item: PendingClassroomAssignmentItem) {
  return item.gradeBand?.trim() || 'Grade not set';
}

function pendingClassLabel(item: PendingClassroomAssignmentItem) {
  const actualClass = item.actualClass?.trim();
  const className = item.className?.trim();
  return className || actualClass || item.packageName || item.lessonNo || 'Pending assignment';
}

function pendingSecondaryLabel(item: PendingClassroomAssignmentItem) {
  const labels = [
    item.targetType === 'CLASS_GROUP' && item.actualClass !== item.className ? item.actualClass : item.studentName,
    item.targetType === 'CLASS_GROUP' ? item.scheduleDesc : item.packageName,
    item.targetType !== 'CLASS_GROUP' ? item.scheduleDesc : '',
    item.targetType !== 'CLASS_GROUP' ? item.lessonNo : '',
  ].filter(Boolean);
  return labels.join(' / ') || '—';
}

function pendingMainLabel(row: PendingQueueRow) {
  if (row.rowType === 'product') return row.productLabel;
  if (row.rowType === 'grade') return `Grade ${row.gradeLabel || 'not set'}`;
  return row.classLabel;
}

function pendingTypeLabel(row: PendingQueueRow) {
  if (row.rowType === 'product') return 'Product';
  if (row.rowType === 'grade') return 'Grade';
  return row.targetType === 'CLASS_GROUP' ? 'Class' : 'Lesson';
}

function pendingTypeTag(row: PendingQueueRow) {
  if (row.rowType === 'product') return 'success';
  if (row.rowType === 'grade') return 'primary';
  return row.targetType === 'CLASS_GROUP' ? 'warning' : 'info';
}

function isPendingAssignmentRow(row: PendingQueueRow): row is PendingAssignmentRow {
  return row.rowType === 'assignment';
}

function pendingRowClassName({ row }: { row: PendingQueueRow }) {
  return `pending-row pending-row--${row.rowType}`;
}

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
      subjectName: o.subjectName,
      contextLabel: [o.studentName, o.teacherName].filter(Boolean).join(' · '),
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
    classrooms.value = (res.data.data ?? []).map(c => ({
      id: String(c.id),
      roomName: c.roomName,
      campus: c.campus,
      campusId: c.campusId,
    }));
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
  border: 1px solid #d9e4ef;
  border-radius: 8px;
  background: #ffffff;
  flex-shrink: 0;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.pending-panel__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(180deg, #fbfdff 0%, #f7fafc 100%);
  border-bottom: 1px solid #e6edf5;
  cursor: pointer;
  user-select: none;
}

.pending-panel__heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pending-panel__summary strong {
  color: #303133;
  font-size: 15px;
}

.pending-panel__summary .pending-panel__heading > span {
  color: #606266;
  font-size: 12px;
}

.pending-panel__summary-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pending-count-tag {
  min-width: 88px;
  justify-content: center;
  font-weight: 700;
}

.pending-panel__content {
  padding: 14px 16px 16px;
  background: #ffffff;
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
  min-width: 1020px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.pending-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.pending-table :deep(.el-table__body td) {
  border-bottom-color: #e8eef5;
}

.pending-table :deep(.pending-row--product td) {
  background: #f3faf7;
}

.pending-table :deep(.pending-row--grade td) {
  background: #f7fbff;
}

.pending-table :deep(.pending-row--assignment td) {
  background: #ffffff;
}

.pending-table :deep(.pending-row--product .el-table__cell),
.pending-table :deep(.pending-row--grade .el-table__cell) {
  padding-top: 8px;
  padding-bottom: 8px;
}

.pending-table :deep(.pending-row--assignment .el-table__cell) {
  padding-top: 12px;
  padding-bottom: 12px;
}

.pending-table :deep(.pending-row--assignment td:first-child) {
  box-shadow: inset 3px 0 0 #f59e0b;
}

.pending-table :deep(.pending-row--product td:first-child) {
  box-shadow: inset 4px 0 0 #22c55e;
}

.pending-table :deep(.pending-row--grade td:first-child) {
  box-shadow: inset 4px 0 0 #3b82f6;
}

.pending-table :deep(.el-table__expand-icon) {
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  margin-right: 8px;
  border-radius: 999px;
  color: #64748b;
  transition: background 0.15s ease, color 0.15s ease;
}

.pending-table :deep(.el-table__expand-icon:hover) {
  background: #e2e8f0;
  color: #1f2937;
}

.pending-table :deep(.el-table__row td:first-child .cell) {
  display: flex;
  align-items: center;
  min-height: 40px;
}

.pending-table :deep(.pending-row--product td:first-child .cell),
.pending-table :deep(.pending-row--grade td:first-child .cell) {
  min-height: 36px;
}

.pending-table :deep(.el-table__indent) {
  flex: 0 0 auto;
  position: relative;
}

.pending-table :deep(.el-table__indent::after) {
  position: absolute;
  top: -18px;
  bottom: -18px;
  left: 15px;
  width: 1px;
  background: #dbe5f0;
  content: '';
}

.pending-hierarchy-cell {
  flex: 1;
  min-width: 0;
  gap: 5px;
}

.pending-hierarchy-cell__top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pending-hierarchy-cell strong {
  color: #1f2937;
  font-size: 13px;
  line-height: 1.2;
}

.pending-hierarchy-cell--product strong {
  color: #1f4e3d;
  font-weight: 700;
}

.pending-hierarchy-cell--grade strong {
  color: #264766;
}

.pending-hierarchy-cell--grade {
  padding-left: 2px;
}

.pending-hierarchy-cell--assignment strong {
  color: #111827;
  font-size: 14px;
}

.pending-hierarchy-cell--assignment {
  padding-left: 4px;
}

.pending-hierarchy-cell span {
  color: #8b95a1;
  font-size: 12px;
  line-height: 1.35;
}

.pending-hierarchy-cell--assignment span {
  color: #6b7280;
}

.pending-muted-cell {
  color: transparent;
}

.pending-room-select {
  width: min(100%, 280px);
}

.pending-table :deep(.pending-room-select .el-select__wrapper) {
  min-height: 38px;
  border-radius: 6px;
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}

.pending-table :deep(.pending-row--assignment .el-button--primary) {
  min-width: 72px;
  height: 34px;
  border-radius: 6px;
  font-weight: 700;
}

.room-option__name {
  font-weight: 600;
}

.room-option__campus {
  float: right;
  color: #909399;
  font-size: 12px;
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
  min-height: 0;
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

.classroom-list-panel__header :deep(.el-input) {
  width: 100%;
}

.classroom-list-panel__header :deep(.el-input__wrapper) {
  box-sizing: border-box;
  min-height: 36px;
  height: 36px;
  align-items: center;
}

.classroom-list-panel__header :deep(.el-input__inner) {
  height: 34px;
  line-height: 34px;
}

.classroom-list-panel__body {
  flex: 1;
  min-height: 0;
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

.classroom-campus-group {
  border-bottom: 1px solid #edf2f7;
}

.classroom-campus-group:last-child {
  border-bottom: none;
}

.classroom-campus-group__header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #eef2f7;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.classroom-campus-group__header small {
  min-width: 22px;
  padding: 1px 7px;
  border-radius: 999px;
  background: #e8eef7;
  color: #526171;
  text-align: center;
  font-size: 11px;
  font-weight: 700;
}

.classroom-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px 10px 18px;
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
  padding-left: 15px;
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
