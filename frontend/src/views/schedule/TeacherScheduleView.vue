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
            <div v-if="teacherRosterLoading" class="teacher-list-panel__empty">
              Loading teachers...
            </div>
            <div v-else-if="filteredTeacherGroups.length === 0" class="teacher-list-panel__empty">
              No teachers found
            </div>
            <section v-for="group in filteredTeacherGroups" :key="group.key" class="teacher-group">
              <button
                type="button"
                class="teacher-group__header"
                :class="{ 'teacher-group__header--collapsed': collapsedTeacherGroupKeys.has(group.key) }"
                :aria-expanded="!collapsedTeacherGroupKeys.has(group.key)"
                @click="toggleTeacherGroup(group.key)"
              >
                <el-icon class="teacher-group__caret"><ArrowRight /></el-icon>
                <span class="teacher-group__name">{{ group.name }}</span>
                <span class="teacher-group__count">{{ group.teachers.length }}</span>
              </button>
              <div v-if="!collapsedTeacherGroupKeys.has(group.key)">
                <div
                  v-for="t in group.teachers"
                  :key="t.id"
                  class="teacher-item"
                  :class="{ 'teacher-item--active': selectedTeacherId === t.id }"
                  @click="onTeacherSelect(t.id)"
                >
                  <span class="teacher-item__name">{{ t.teacherName }}</span>
                  <el-tag v-if="t.status !== 'ACTIVE'" size="small" type="warning" effect="plain">Inactive</el-tag>
                  <el-icon v-if="selectedTeacherId === t.id" color="#409eff"><Check /></el-icon>
                </div>
              </div>
            </section>
          </el-scrollbar>
        </aside>

        <!-- Right: Calendar -->
        <main class="calendar-panel">
          <div v-if="!isTeacher && appStore.isMobileViewport" class="calendar-mobile-roster">
            <button
              type="button"
              class="calendar-mobile-roster__trigger"
              :aria-expanded="mobileRosterOpen"
              @click="toggleMobileRoster"
            >
              <span>{{ selectedTeacherId ? selectedTeacherName : 'Select a teacher' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <div v-if="mobileRosterOpen" class="calendar-mobile-roster__panel">
              <div v-loading="teacherRosterLoading" class="calendar-mobile-roster__groups">
                <div v-if="!teacherRosterLoading && filteredTeacherGroups.length === 0" class="teacher-list-panel__empty">
                  No teachers found
                </div>
                <section v-for="group in filteredTeacherGroups" :key="group.key" class="teacher-group">
                  <button
                    type="button"
                    class="teacher-group__header"
                    :class="{ 'teacher-group__header--collapsed': mobileCollapsedTeacherGroupKeys.has(group.key) }"
                    :aria-expanded="!mobileCollapsedTeacherGroupKeys.has(group.key)"
                    @click="toggleMobileTeacherGroup(group.key)"
                  >
                    <el-icon class="teacher-group__caret"><ArrowRight /></el-icon>
                    <span class="teacher-group__name">{{ group.name }}</span>
                    <span class="teacher-group__count">{{ group.teachers.length }}</span>
                  </button>
                  <div v-if="!mobileCollapsedTeacherGroupKeys.has(group.key)">
                    <button
                      v-for="teacher in group.teachers"
                      :key="teacher.id"
                      type="button"
                      class="teacher-item calendar-mobile-roster__item"
                      :class="{ 'teacher-item--active': selectedTeacherId === teacher.id }"
                      @click="selectMobileTeacher(teacher.id)"
                    >
                      <span class="teacher-item__name">{{ teacher.teacherName }}</span>
                      <el-tag v-if="teacher.status !== 'ACTIVE'" size="small" type="warning" effect="plain">Inactive</el-tag>
                      <el-icon v-if="selectedTeacherId === teacher.id" color="#409eff"><Check /></el-icon>
                    </button>
                  </div>
                </section>
              </div>
            </div>
          </div>

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
                <el-button class="calendar-week-step" :icon="ArrowLeft" aria-label="Previous week" @click="prevWeek" />
                <div class="calendar-week-current">
                  <span class="calendar-controls__label" aria-live="polite">
                    {{ appStore.isMobileViewport ? compactWeekLabel : weekLabel }}
                  </span>
                  <span
                    v-if="appStore.isMobileViewport"
                    class="calendar-controls__count"
                    :aria-label="`${occurrences.length} lessons this week`"
                  >
                    {{ occurrences.length }} lesson{{ occurrences.length === 1 ? '' : 's' }}
                  </span>
                  <el-date-picker
                    v-model="pickerDate"
                    type="date"
                    aria-label="Choose a date"
                    placeholder="Jump to week"
                    value-format="YYYY-MM-DD"
                    format="MMM D, YYYY"
                    :clearable="false"
                    :editable="false"
                    class="calendar-date-picker"
                    @change="jumpToDate"
                  />
                </div>
                <el-button class="calendar-week-step" :icon="ArrowRight" aria-label="Next week" @click="nextWeek" />
                <el-tag v-if="!appStore.isMobileViewport" size="small" type="info">{{ occurrences.length }} lessons this week</el-tag>
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
              :pinch-zoom-enabled="appStore.isMobileViewport"
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
import { ArrowDown, ArrowLeft, ArrowRight, Calendar, Check, Loading, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import dayjs from 'dayjs';
import { fetchTeachers } from '@/api/base-data';
import {
  fetchLessonOccurrence,
  fetchLessonOccurrences,
  fetchTeacherCalendarCategories,
} from '@/api/schedule';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import { isTeacherUser } from '@/types/auth';
import type { EntityId, LessonOccurrenceItem, TeacherItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import { findScheduleConflictGroups } from '@/utils/schedule-conflicts';

const authStore = useAuthStore();
const appStore = useAppStore();
const isTeacher = computed(() => isTeacherUser(authStore.currentUser));

// ─── Teachers ───────────────────────────────────
const teachers = ref<Array<Pick<TeacherItem, 'id' | 'teacherName' | 'status'>>>([]);
const teacherCategories = ref<Array<{ teacherId: EntityId; categoryKey: string; categoryName: string }>>([]);
const teacherRosterLoading = ref(false);
const teacherCategoryLoadFailed = ref(false);
const searchText = ref('');
const selectedTeacherId = ref<EntityId | null>(null);
const mobileRosterOpen = ref(false);
const mobileCollapsedTeacherGroupKeys = ref(new Set<string>());

// Monotonic token guarding against stale responses when switching teachers/weeks quickly.
let scheduleRequestSeq = 0;

const collapsedTeacherGroupKeys = ref(new Set<string>());

function toggleTeacherGroup(key: string) {
  const next = new Set(collapsedTeacherGroupKeys.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  collapsedTeacherGroupKeys.value = next;
}

function toggleMobileRoster() {
  if (mobileRosterOpen.value) {
    mobileRosterOpen.value = false;
    searchText.value = '';
    return;
  }
  searchText.value = '';
  mobileCollapsedTeacherGroupKeys.value = new Set(filteredTeacherGroups.value.map(group => group.key));
  mobileRosterOpen.value = true;
}

function toggleMobileTeacherGroup(key: string) {
  const next = new Set(mobileCollapsedTeacherGroupKeys.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  mobileCollapsedTeacherGroupKeys.value = next;
}

const sortedTeachers = computed(() => [...teachers.value].sort((left, right) =>
  left.teacherName.localeCompare(right.teacherName, undefined, { sensitivity: 'base' })
    || String(left.id).localeCompare(String(right.id), undefined, { numeric: true }),
));

const oneOnOneCategoryKeys = new Set([
  'ONE_ON_ONE:IN_PERSON',
  'ONE_ON_ONE:ONLINE',
  'ONE_ON_ONE:UNSPECIFIED',
]);

// 教师可同时属于多个课程分组；仅将 1-on-1 的授课方式归并成同一分组。
const filteredTeacherGroups = computed(() => {
  const q = searchText.value.trim().toLowerCase();
  const categoriesByTeacher = new Map<string, Map<string, string>>();
  for (const item of teacherCategories.value) {
    const teacherKey = String(item.teacherId);
    const teacherCategories = categoriesByTeacher.get(teacherKey) ?? new Map<string, string>();
    teacherCategories.set(item.categoryKey, item.categoryName);
    categoriesByTeacher.set(teacherKey, teacherCategories);
  }

  const groups = new Map<string, { key: string; name: string; teachers: typeof sortedTeachers.value }>();
  for (const teacher of sortedTeachers.value) {
    if (q && !teacher.teacherName.toLowerCase().includes(q)) continue;
    const categories = categoriesByTeacher.get(String(teacher.id));
    const memberships = teacherCategoryLoadFailed.value
      ? [['ALL', 'All teachers'] as [string, string]]
      : categories?.size
      ? [...categories.entries()]
      : [['UNASSIGNED', 'Other / no course assignment'] as [string, string]];
    for (const [key, name] of memberships) {
      const normalizedKey = oneOnOneCategoryKeys.has(key) ? 'ONE_ON_ONE' : key;
      const normalizedName = normalizedKey === 'ONE_ON_ONE' ? '1-on-1' : name;
      const group = groups.get(normalizedKey) ?? { key: normalizedKey, name: normalizedName, teachers: [] };
      // 同一位老师可能同时承担线上、线下课程，合并后只在 1-on-1 分组中显示一次。
      if (!group.teachers.some(item => item.id === teacher.id)) group.teachers.push(teacher);
      groups.set(normalizedKey, group);
    }
  }

  return [...groups.values()].sort((left, right) =>
    categoryOrder(left.key, left.name) - categoryOrder(right.key, right.name)
      || left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }),
  );
});

function categoryOrder(key: string, name: string) {
  const normalizedName = name.toLowerCase().replace(/[\s_-]/g, '');
  if (key === 'ONE_ON_ONE') return 0;
  if (key === 'ALL') return 1;
  if (normalizedName.includes('afterschool') || name.includes('课后')) return 10;
  if (normalizedName.includes('saturday') || name.includes('周六')) return 20;
  if (key === 'UNASSIGNED') return 100;
  return 30;
}

const selectedTeacherName = computed(() => {
  const t = teachers.value.find(t => t.id === selectedTeacherId.value);
  return t?.teacherName ?? authStore.currentUser?.displayName ?? '';
});

function onTeacherSelect(id: EntityId) {
  selectedTeacherId.value = id;
  weekOffset.value = 0;
  loadSchedule();
}

function selectMobileTeacher(id: EntityId) {
  onTeacherSelect(id);
  mobileRosterOpen.value = false;
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

const compactWeekLabel = computed(() => {
  const monday = dayjs(weekMonday.value);
  const sunday = monday.add(6, 'day');
  if (sunday.year() === dayjs().year()) {
    return `${monday.format('MMM D')} – ${sunday.format('MMM D')}`;
  }
  return `${monday.format('MMM D, YYYY')} – ${sunday.format('MMM D, YYYY')}`;
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
      isOnline: o.deliveryMode === 'ONLINE',
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
      planningStatus: 'SCHEDULED,ADJUSTED,MERGED',
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
    const eMin = sMin + end.diff(start, 'minute');
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
  teacherRosterLoading.value = true;
  try {
    // 教师名单和产品分类互不依赖，并行加载，避免首屏被串行请求拉长。
    const categoryPromise = fetchTeacherCalendarCategories().then(
      response => response.data.data,
      () => null,
    );
    const firstPage = await fetchTeachers({ pageNum: 1, pageSize: 200 });
    const activeAndInactive = [...firstPage.data.data.records];
    teachers.value = activeAndInactive.map(teacher => ({
      id: teacher.id,
      teacherName: teacher.teacherName,
      status: teacher.status,
    }));
    const totalPages = Math.ceil(firstPage.data.data.total / 200);
    const [remainingPagesResult, categoryResult] = await Promise.allSettled([
      Promise.all(Array.from({ length: Math.max(totalPages - 1, 0) }, (_, index) =>
        fetchTeachers({ pageNum: index + 2, pageSize: 200 }),
      )),
      categoryPromise,
    ]);
    if (remainingPagesResult.status === 'fulfilled') {
      for (const page of remainingPagesResult.value) activeAndInactive.push(...page.data.data.records);
    }
    teachers.value = activeAndInactive.map(teacher => ({
      id: teacher.id,
      teacherName: teacher.teacherName,
      status: teacher.status,
    }));
    if (categoryResult.status === 'fulfilled' && categoryResult.value) {
      teacherCategories.value = categoryResult.value;
    } else {
      teacherCategoryLoadFailed.value = true;
    }
  } catch { /* Keep the calendar usable if the roster request fails. */ }
  finally {
    teacherRosterLoading.value = false;
    collapsedTeacherGroupKeys.value = new Set(filteredTeacherGroups.value.map(group => group.key));
  }
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

.calendar-mobile-roster {
  display: none;
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

.teacher-group + .teacher-group {
  border-top: 1px solid #e8edf2;
}

.teacher-group__header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  background: #f7f9fc;
  color: #34465b;
  cursor: pointer;
  text-align: left;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.35;
}

.teacher-group__header:hover {
  background: #eef3f9;
}

.teacher-group__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.teacher-group__caret {
  flex-shrink: 0;
  color: #718096;
  transition: transform 0.15s ease;
}

.teacher-group__header:not(.teacher-group__header--collapsed) .teacher-group__caret {
  transform: rotate(90deg);
}

.teacher-group__count {
  flex-shrink: 0;
  color: #909399;
  font-size: 12px;
  font-weight: 500;
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

@media (max-width: 920px) {
  .teacher-calendar {
    height: auto;
    min-height: 0;
    padding: 12px;
  }

  .teacher-calendar__body {
    display: block;
    overflow: visible;
  }

  .teacher-calendar .calendar-panel {
    overflow: visible;
  }

  .teacher-list-panel {
    display: none;
  }

  .calendar-mobile-roster {
    position: relative;
    z-index: 30;
    display: block;
    margin-bottom: 8px;
  }

  .calendar-mobile-roster__trigger {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-height: 42px;
    padding: 0 12px;
    border: 1px solid #dcdfe6;
    border-radius: 6px;
    background: #fff;
    color: #606266;
    font: inherit;
    font-size: 14px;
    line-height: 20px;
    text-align: left;
  }

  .calendar-mobile-roster__trigger .el-icon { color: #909399; }

  .calendar-mobile-roster__panel {
    position: absolute;
    z-index: 31;
    top: calc(100% + 5px);
    right: 0;
    left: 0;
    display: flex;
    flex-direction: column;
    height: min(55dvh, 440px);
    box-sizing: border-box;
    min-height: 0;
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior-y: contain;
    -webkit-overflow-scrolling: touch;
    touch-action: pan-y;
    scrollbar-width: none;
    border: 1px solid #aebed0;
    border-radius: 8px;
    background: #fff;
    box-shadow: 0 12px 28px rgba(31, 42, 68, 0.2), 0 0 0 2px rgba(174, 190, 208, 0.16);
  }
  .calendar-mobile-roster__panel::-webkit-scrollbar { display: none; }

  .calendar-mobile-roster__groups {
    flex: 0 0 auto;
    min-height: 0;
    padding-bottom: 12px;
    overflow: visible;
  }
  .calendar-mobile-roster__item { width: 100%; min-height: 42px; box-sizing: border-box; border: 0; background: #fff; font: inherit; text-align: left; }
  .calendar-mobile-roster .teacher-group__header { min-height: 38px; padding: 8px 12px; border-bottom: 1px solid #dce4ed; background: #f3f6fa; font-size: 13px; line-height: 20px; }
  .calendar-mobile-roster .teacher-item { min-height: 42px; padding: 10px 12px; border-bottom: 1px solid #e4eaf1; }
  .calendar-mobile-roster .teacher-item--active { padding-left: 9px; }
  .calendar-mobile-roster .teacher-item__name { font-size: 14px; line-height: 20px; }

  .calendar-controls {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .calendar-controls__label {
    flex: 1 1 auto;
    text-align: center;
  }
}


.calendar-controls__nav {
  display: flex;
  align-items: center;
  gap: 10px;
}

.calendar-week-current {
  display: flex;
  align-items: center;
  gap: 10px;
}

.calendar-date-picker {
  width: 150px;
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

@media (max-width: 920px) {
  .calendar-controls { display: grid; grid-template-columns: minmax(0, 1fr); align-items: stretch; gap: 0; margin: 0 0 4px; }
  .calendar-controls__nav { display: grid; width: 100%; min-width: 0; grid-template-columns: 44px minmax(0, 1fr) 44px; grid-template-rows: 44px; gap: 4px 6px; }
  .calendar-controls__nav > * { min-width: 0; max-width: 100%; }
  .calendar-week-step { width: 44px; height: 44px; min-height: 44px; margin: 0; padding: 0; border-radius: 10px; }
  .calendar-week-step:first-child { grid-column: 1; grid-row: 1; }
  .calendar-week-step:nth-child(3) { grid-column: 3; grid-row: 1; }
  .calendar-week-current { display: flex; align-items: center; justify-content: space-between; gap: 6px; grid-column: 2; grid-row: 1; min-width: 0; padding: 0 4px 0 8px; border: 1px solid #dce5ef; border-radius: 10px; background: #fff; }
  .calendar-controls__label { flex: 1 1 auto; min-width: 0; overflow: hidden; font-size: 12px; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
  .calendar-controls__count { flex: 0 0 auto; padding: 3px 5px; border-radius: 10px; background: #f1f5f9; color: #64748b; font-size: 10px; line-height: 1.2; white-space: nowrap; }
  .calendar-date-picker { display: inline-flex; flex: 0 0 44px; width: 44px !important; min-width: 44px !important; max-width: 44px; height: 44px; overflow: hidden; }
  .calendar-controls__nav :deep(.calendar-date-picker.el-date-editor) { display: inline-flex; width: 44px !important; min-width: 44px !important; height: 44px; }
  .calendar-date-picker :deep(.el-input__wrapper) { position: relative; justify-content: center; width: 44px; min-height: 44px; padding: 0; border: 0; border-radius: 8px; box-shadow: none !important; background: transparent; }
  .calendar-date-picker :deep(.el-input__prefix) { display: flex; align-items: center; justify-content: center; width: 44px; height: 44px; margin: 0; }
  .calendar-date-picker :deep(.el-input__prefix-inner) { display: flex; align-items: center; justify-content: center; }
  .calendar-date-picker :deep(.el-input__inner) { position: absolute; width: 1px; height: 1px; padding: 0; opacity: 0; pointer-events: none; }
  .calendar-date-picker :deep(.el-input__suffix) { display: none; }
  .calendar-controls__teacher { display: none; }
}

@media (max-width: 360px) {
  .calendar-controls__count { display: none; }
}
</style>
