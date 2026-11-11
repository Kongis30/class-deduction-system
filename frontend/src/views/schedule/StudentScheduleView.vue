<template>
  <div class="page-stack">
    <section class="student-calendar">
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
          <el-scrollbar always class="student-list-panel__body calendar-roster-scroll">
            <div v-if="studentsLoading" class="student-list-panel__empty">
              Loading students...
            </div>
            <div v-else-if="filteredStudentGroups.length === 0" class="student-list-panel__empty">
              {{ students.length === 0 ? 'No active 1-on-1 students' : 'No students found' }}
            </div>
            <section v-for="product in filteredStudentGroups" :key="product.key" class="student-product-group">
              <button
                type="button"
                class="student-group__header student-product-group__header"
                :class="{ 'student-group__header--collapsed': collapsedStudentGroupKeys.has(product.key) }"
                :aria-expanded="!collapsedStudentGroupKeys.has(product.key)"
                @click="toggleStudentGroup(product.key)"
              >
                <el-icon class="student-group__caret"><ArrowRight /></el-icon>
                <span class="student-group__name">{{ product.name }}</span>
                <span class="student-group__count">{{ product.studentCount }}</span>
              </button>
              <div v-if="!collapsedStudentGroupKeys.has(product.key)">
                <section v-for="grade in product.grades" :key="grade.key" class="student-grade-group">
                  <button
                    type="button"
                    class="student-group__header student-grade-group__header"
                    :class="{ 'student-group__header--collapsed': collapsedStudentGroupKeys.has(grade.key) }"
                    :aria-expanded="!collapsedStudentGroupKeys.has(grade.key)"
                    @click="toggleStudentGroup(grade.key)"
                  >
                    <el-icon class="student-group__caret"><ArrowRight /></el-icon>
                    <span class="student-group__name">{{ grade.name }}</span>
                    <span class="student-group__count">{{ grade.students.length }}</span>
                  </button>
                  <div v-if="!collapsedStudentGroupKeys.has(grade.key)">
                    <button
                      v-for="s in grade.students"
                      :key="s.id"
                      type="button"
                      class="student-item"
                      :class="{ 'student-item--active': selectedStudentId === s.id }"
                      @click="onStudentSelect(s.id)"
                    >
                      <span class="student-item__name">{{ s.studentName }}</span>
                      <el-icon v-if="selectedStudentId === s.id" color="#409eff"><Check /></el-icon>
                    </button>
                  </div>
                </section>
              </div>
            </section>
          </el-scrollbar>
        </aside>

        <!-- Right: Calendar -->
        <main class="calendar-panel">
          <div v-if="!isStudent && appStore.isMobileViewport" class="calendar-mobile-roster">
            <button
              type="button"
              class="calendar-mobile-roster__trigger"
              :aria-expanded="mobileRosterOpen"
              @click="toggleMobileRoster"
            >
              <span>{{ selectedStudentName || 'Select a student' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <div v-if="mobileRosterOpen" class="calendar-mobile-roster__panel">
              <div v-loading="studentsLoading" class="calendar-mobile-roster__groups">
                <div v-if="!studentsLoading && filteredStudentGroups.length === 0" class="student-list-panel__empty">
                  {{ students.length === 0 ? 'No active 1-on-1 students' : 'No students found' }}
                </div>
                <section v-for="product in filteredStudentGroups" :key="product.key" class="student-product-group">
                  <button
                    type="button"
                    class="student-group__header student-product-group__header"
                    :class="{ 'student-group__header--collapsed': mobileCollapsedStudentGroupKeys.has(product.key) }"
                    :aria-expanded="!mobileCollapsedStudentGroupKeys.has(product.key)"
                    @click="toggleMobileStudentGroup(product.key)"
                  >
                    <el-icon class="student-group__caret"><ArrowRight /></el-icon>
                    <span class="student-group__name">{{ product.name }}</span>
                    <span class="student-group__count">{{ product.studentCount }}</span>
                  </button>
                  <div v-if="!mobileCollapsedStudentGroupKeys.has(product.key)">
                    <section v-for="grade in product.grades" :key="grade.key" class="student-grade-group">
                      <button
                        type="button"
                        class="student-group__header student-grade-group__header"
                        :class="{ 'student-group__header--collapsed': mobileCollapsedStudentGroupKeys.has(grade.key) }"
                        :aria-expanded="!mobileCollapsedStudentGroupKeys.has(grade.key)"
                        @click="toggleMobileStudentGroup(grade.key)"
                      >
                        <el-icon class="student-group__caret"><ArrowRight /></el-icon>
                        <span class="student-group__name">{{ grade.name }}</span>
                        <span class="student-group__count">{{ grade.students.length }}</span>
                      </button>
                      <div v-if="!mobileCollapsedStudentGroupKeys.has(grade.key)">
                        <button
                          v-for="student in grade.students"
                          :key="student.id"
                          type="button"
                          class="student-item calendar-mobile-roster__item"
                          :class="{ 'student-item--active': selectedStudentId === student.id }"
                          @click="selectMobileStudent(student.id)"
                        >
                          <span class="student-item__name">{{ student.studentName }}</span>
                          <el-icon v-if="selectedStudentId === student.id" color="#409eff"><Check /></el-icon>
                        </button>
                      </div>
                    </section>
                  </div>
                </section>
              </div>
            </div>
          </div>

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
                  <template v-if="!appStore.isMobileViewport">
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
                  </template>
                  <template v-else>
                    <el-button class="calendar-week-step" :icon="ArrowLeft" aria-label="Previous week" @click="prevWeek" />
                    <div class="calendar-week-current">
                      <span class="calendar-controls__label" aria-live="polite">{{ compactWeekLabel }}</span>
                      <span
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
                  </template>
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
                :pinch-zoom-enabled="appStore.isMobileViewport"
                @cell-click="onCellClick"
              />
            </template>

            <template v-else>
              <div class="calendar-controls">
                <div class="calendar-controls__nav">
                  <template v-if="!appStore.isMobileViewport">
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
                  </template>
                  <template v-else>
                    <el-button class="calendar-week-step" :icon="ArrowLeft" aria-label="Previous week" @click="prevWeek" />
                    <div class="calendar-week-current">
                      <span class="calendar-controls__label" aria-live="polite">{{ compactWeekLabel }}</span>
                      <span
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
                  </template>
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
                :pinch-zoom-enabled="appStore.isMobileViewport"
                @cell-click="onCellClick"
              />
            </template>
          </template>
        </main>
      </div>
    </section>

    <el-dialog v-model="dailyVisible" title="Daily 1-on-1 lessons" width="900px" :fullscreen="appStore.isMobileViewport" class="daily-lessons-dialog" destroy-on-close>
      <div class="daily-lessons__controls">
        <el-button :icon="ArrowLeft" aria-label="Previous day" @click="stepDailyDate(-1)" />
        <el-date-picker v-model="dailyDate" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="loadDailyPrivateLessons" />
        <el-button :icon="ArrowRight" aria-label="Next day" @click="stepDailyDate(1)" />
        <span v-if="!dailyLoading && !dailyError">{{ dailyLessons.length }} lesson(s) · {{ dailyStudentCount }} student(s)</span>
      </div>
      <p class="daily-lessons__note">Times are Eastern Time. The list shows scheduled lessons already generated.</p>
      <el-alert v-if="dailyError" type="error" :closable="false" title="Could not load lessons. Please try again.">
        <el-button text type="primary" @click="loadDailyPrivateLessons">Retry</el-button>
      </el-alert>
      <el-table v-else v-loading="dailyLoading" :data="dailyLessons" empty-text="No 1-on-1 lessons on this date.">
        <el-table-column label="Time (ET)" min-width="135">
          <template #default="{ row }">{{ dayjs(row.plannedStartAt).format('h:mm A') }} – {{ dayjs(row.plannedEndAt).format('h:mm A') }}</template>
        </el-table-column>
        <el-table-column label="Student" min-width="150">
          <template #default="{ row }">{{ row.participantNames.join(', ') || '—' }}</template>
        </el-table-column>
        <el-table-column label="Lesson" min-width="140">
          <template #default="{ row }">{{ row.subjectName || row.packageName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="plannedTeacherName" label="Teacher" min-width="130" />
        <el-table-column prop="plannedClassroomName" label="Classroom" min-width="120" />
      </el-table>
    </el-dialog>

    <!-- Detail dialog -->
    <el-dialog
      v-model="detailDialogVisible"
      title="Lesson Detail"
      width="540px"
      :fullscreen="appStore.isMobileViewport"
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
import { fetchCoursePackages, fetchMyCoursePackages } from '@/api/enrollment';
import {
  fetchLessonOccurrence,
  fetchLessonOccurrences,
  fetchDailyPrivateLessons,
  type DailyPrivateLesson,
  fetchOneOnOneScheduleStudents,
} from '@/api/schedule';
import type { CoursePackageItem, EntityId, LessonOccurrenceItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import { useAuthStore } from '@/stores/auth';
import { isStudentUser } from '@/types/auth';
import { findScheduleConflictGroups } from '@/utils/schedule-conflicts';
import { useAppStore } from '@/stores/app';

const authStore = useAuthStore();
const appStore = useAppStore();
const isStudent = computed(() => isStudentUser(authStore.currentUser));
const canViewDailyPrivateLessons = computed(() => isAdminOrStaffUser(authStore.currentUser) || isManagerUser(authStore.currentUser));
const dailyVisible = ref(false);
const dailyDate = ref('');
const dailyLoading = ref(false);
const dailyError = ref(false);
const dailyLessons = ref<DailyPrivateLesson[]>([]);
const dailyStudentCount = ref(0);
let dailyRequestId = 0;

function openDailyPrivateLessons() {
  dailyDate.value ||= new Intl.DateTimeFormat('en-CA', { timeZone: 'America/New_York', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  dailyVisible.value = true;
  void loadDailyPrivateLessons();
}

defineExpose({ openDailyPrivateLessons });

function stepDailyDate(days: number) {
  dailyDate.value = dayjs(dailyDate.value).add(days, 'day').format('YYYY-MM-DD');
  void loadDailyPrivateLessons();
}

// 每次仅请求所选日期，忽略较早返回的请求，避免快速切换日期时显示旧数据。
async function loadDailyPrivateLessons() {
  const requestId = ++dailyRequestId;
  dailyLoading.value = true;
  dailyError.value = false;
  dailyLessons.value = [];
  try {
    const response = await fetchDailyPrivateLessons(dailyDate.value);
    if (requestId !== dailyRequestId) return;
    const nameOrder = new Intl.Collator('en', { sensitivity: 'base' });
    // 弹窗以 Time 列为主视图，按开始时间从早到晚排序；同一时段再按学生名稳定排序。
    dailyLessons.value = response.data.data.lessons
      .map((lesson) => ({
        ...lesson,
        participantNames: [...lesson.participantNames].sort(nameOrder.compare),
      }))
      .sort((left, right) => {
        const byStart = left.plannedStartAt.localeCompare(right.plannedStartAt);
        if (byStart !== 0) return byStart;
        const byEnd = left.plannedEndAt.localeCompare(right.plannedEndAt);
        if (byEnd !== 0) return byEnd;
        return nameOrder.compare(left.participantNames[0] ?? '', right.participantNames[0] ?? '');
      });
    dailyStudentCount.value = response.data.data.studentCount;
  } catch {
    if (requestId === dailyRequestId) dailyError.value = true;
  } finally {
    if (requestId === dailyRequestId) dailyLoading.value = false;
  }
}

const loading = ref(false);
const studentsLoading = ref(false);
const students = ref<StudentRosterItem[]>([]);
const searchText = ref('');
const selectedStudentId = ref<EntityId | null>(null);
const mobileRosterOpen = ref(false);
const mobileCollapsedStudentGroupKeys = ref(new Set<string>());
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
interface StudentCourseGrade {
  courseName: string;
  gradeBand: string;
}

interface StudentRosterItem {
  id: EntityId;
  studentName: string;
  studentGrade?: string | null;
  courseGrades: StudentCourseGrade[];
}

interface StudentGradeGroup {
  key: string;
  name: string;
  students: StudentRosterItem[];
}

interface StudentProductGroup {
  key: string;
  name: string;
  studentCount: number;
  grades: StudentGradeGroup[];
}

const gradeBandOrder = ['G1-G5', 'G6-G8', 'G9-12', 'AP LEVEL', 'OTHER'];

function gradeBandRank(name: string) {
  const rank = gradeBandOrder.indexOf(name.trim().toUpperCase());
  return rank === -1 ? gradeBandOrder.length : rank;
}

function compareRosterNames(left: string, right: string) {
  return left.localeCompare(right, 'en', { sensitivity: 'base', numeric: true });
}

const filteredStudentGroups = computed<StudentProductGroup[]>(() => {
  const q = searchText.value.trim().toLowerCase();
  const products = new Map<string, {
    key: string;
    name: string;
    studentIds: Set<string>;
    grades: Map<string, StudentGradeGroup>;
  }>();

  for (const student of students.value) {
    if (q && !student.studentName.toLowerCase().includes(q)) continue;
    const courseGrades = student.courseGrades.length > 0
      ? student.courseGrades
      : [{ courseName: 'Other', gradeBand: student.studentGrade || 'Other' }];
    for (const course of courseGrades) {
      const productName = course.courseName?.trim() || 'Other';
      const gradeName = course.gradeBand?.trim() || 'Other';
      const productKey = `product:${productName.toLowerCase()}`;
      let product = products.get(productKey);
      if (!product) {
        product = { key: productKey, name: productName, studentIds: new Set(), grades: new Map() };
        products.set(productKey, product);
      }
      product.studentIds.add(String(student.id));

      const gradeKey = `grade:${productKey}:${gradeName.toLowerCase()}`;
      let grade = product.grades.get(gradeKey);
      if (!grade) {
        grade = { key: gradeKey, name: gradeName, students: [] };
        product.grades.set(gradeKey, grade);
      }
      grade.students.push(student);
    }
  }

  return [...products.values()]
    .sort((left, right) => compareRosterNames(left.name, right.name))
    .map(product => ({
      key: product.key,
      name: product.name,
      studentCount: product.studentIds.size,
      grades: [...product.grades.values()]
        .sort((left, right) =>
          gradeBandRank(left.name) - gradeBandRank(right.name) || compareRosterNames(left.name, right.name))
        .map(grade => ({
          ...grade,
          students: grade.students
            .slice()
            .sort((left, right) => compareRosterNames(left.studentName, right.studentName)),
        })),
    }));
});

const collapsedStudentGroupKeys = ref(new Set<string>());

function toggleStudentGroup(key: string) {
  const next = new Set(collapsedStudentGroupKeys.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  collapsedStudentGroupKeys.value = next;
}

function toggleMobileRoster() {
  if (mobileRosterOpen.value) {
    mobileRosterOpen.value = false;
    searchText.value = '';
    return;
  }
  searchText.value = '';
  mobileCollapsedStudentGroupKeys.value = new Set(
    filteredStudentGroups.value.flatMap(product => [product.key, ...product.grades.map(grade => grade.key)]),
  );
  mobileRosterOpen.value = true;
}

function toggleMobileStudentGroup(key: string) {
  const next = new Set(mobileCollapsedStudentGroupKeys.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  mobileCollapsedStudentGroupKeys.value = next;
}

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

function selectMobileStudent(id: EntityId) {
  void onStudentSelect(id);
  mobileRosterOpen.value = false;
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
      isOnline: o.deliveryMode === 'ONLINE',
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
  // 后端单次 JOIN 查询有有效 1-on-1 课包的学生，避免按学生逐个请求课包。
  studentsLoading.value = true;
  try {
    const res = await fetchOneOnOneScheduleStudents();
    students.value = res.data.data.map(({ id, studentName, studentGrade, courseGrades }) => ({
      id,
      studentName,
      studentGrade,
      courseGrades: courseGrades ?? [],
    }));
  } catch {
    ElMessage.error('Failed to load student list.');
  } finally {
    studentsLoading.value = false;
  }
});
</script>

<style scoped>
:global(.daily-lessons-dialog) {
  display: flex;
  flex-direction: column;
  height: min(760px, 78vh);
  max-height: 78vh;
}
:global(.daily-lessons-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.daily-lessons__controls { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; color: #475569; font-size: 14px; }
.daily-lessons__note { margin: 10px 0; color: #718096; font-size: 12px; }
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

.student-list-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  color: #909399;
  font-size: 13px;
}

.student-product-group + .student-product-group {
  border-top: 1px solid #dfe6ee;
}

.student-group__header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  background: #f5f7fa;
  color: #34465b;
  cursor: pointer;
  text-align: left;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.35;
}

.student-group__header:hover {
  background: #eef3f9;
}

.student-group__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-group__caret {
  flex-shrink: 0;
  color: #718096;
  transition: transform 0.15s ease;
}

.student-group__header:not(.student-group__header--collapsed) .student-group__caret {
  transform: rotate(90deg);
}

.student-group__count {
  flex-shrink: 0;
  color: #909399;
  font-size: 12px;
  font-weight: 500;
}

.student-grade-group__header {
  padding-left: 24px;
  background: #f8fafc;
}

.student-grade-group + .student-grade-group {
  border-top: 1px solid #eef2f6;
}

.student-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  color: inherit;
  text-align: left;
  background: #fff;
  border: 0;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.15s;
}

.student-item:hover {
  background: #f5f7fa;
}

.student-item:focus-visible {
  position: relative;
  outline: 2px solid #409eff;
  outline-offset: -2px;
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

.calendar-mobile-roster {
  display: none;
}

:global(html.mobile-layout) & {
  .student-calendar {
    height: auto;
    min-height: 0;
    padding: 12px;
  }

  .student-calendar .student-calendar__body {
    display: block;
    overflow: visible;
  }

  .student-calendar .calendar-panel {
    overflow: visible;
  }

  .student-list-panel {
    display: none;
  }

  .calendar-mobile-roster {
    position: relative;
    z-index: 30;
    display: block;
    margin-bottom: 10px;
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
  .calendar-mobile-roster .student-group__header { min-height: 38px; padding: 8px 12px; border-bottom: 1px solid #dce4ed; background: #f3f6fa; font-size: 13px; line-height: 20px; }
  .calendar-mobile-roster .student-grade-group__header { padding-left: 24px; }
  .calendar-mobile-roster .student-item { min-height: 42px; padding: 10px 12px; border-bottom: 1px solid #e4eaf1; }
  .calendar-mobile-roster .student-item--active { padding-left: 9px; }
  .calendar-mobile-roster .student-item__name { font-size: 14px; line-height: 20px; }

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
  .calendar-controls__student { display: none; }

  :deep(.el-dialog.is-fullscreen) {
    display: flex;
    flex-direction: column;
  }

  :deep(.el-dialog.is-fullscreen .el-dialog__body) {
    flex: 1;
  }
}

@media (max-width: 360px) {
  .calendar-controls__count { display: none; }
}
</style>
