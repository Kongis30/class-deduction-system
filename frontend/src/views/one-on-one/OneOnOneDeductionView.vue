<template>
  <div class="oo-page">
    <section class="oo-toolbar no-print">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>Find a student, choose their course, then confirm each lesson.</p>
      </div>
      <div class="oo-actions">
        <el-button class="action-primary" type="primary" :loading="loading" @click="loadData">Refresh</el-button>
        <el-button class="action-print" :disabled="!activePackage" @click="printCurrentPackage">Print Student Sheet</el-button>
      </div>
    </section>

    <details class="oo-help no-print">
      <summary>How to use this page</summary>
      <div class="oo-help__content">
        <ol>
          <li>Find a student or teacher, then narrow the list by course and grade if needed.</li>
          <li>Choose the student's course from the list on the left.</li>
          <li>Use the lesson records on the right to set resources, submit attendance, or review and deduct hours.</li>
        </ol>
      </div>
    </details>

    <section class="oo-selector-bar no-print" v-loading="loading">
      <div class="oo-student-search">
        <label class="oo-field oo-field--student">
          <span>Find student</span>
          <el-input
            v-model="studentSearchKeyword"
            clearable
            placeholder="Name, guardian, phone, or student number (leave empty for all)"
            @keyup.enter="searchStudents"
            @clear="clearStudentSearch"
          />
        </label>
        <el-button type="primary" :loading="studentSearchLoading" @click="searchStudents">Search</el-button>
      </div>

      <label class="oo-field oo-scope-select">
        <span>Teacher</span>
        <el-select
          v-model="selectedTeacherId"
          filterable
          clearable
          placeholder="Select teacher"
          @change="onScopeFilterChanged"
        >
          <el-option
            v-for="teacher in teacherFilterOptions"
            :key="teacherKey(teacher)"
            :label="teacher.teacherName"
            :value="teacher.teacherId"
          />
        </el-select>
      </label>

      <label class="oo-field oo-scope-select">
        <span>Course type</span>
        <el-select
          v-model="selectedCourseType"
          filterable
          clearable
          placeholder="Select course type"
          @change="onCourseTypeChanged"
        >
          <el-option
            v-for="courseType in courseTypeOptions"
            :key="courseType"
            :label="courseType"
            :value="courseType"
          />
        </el-select>
      </label>

      <label class="oo-field oo-scope-select">
        <span>Grade</span>
        <el-select
          v-model="selectedGradeBand"
          filterable
          clearable
          placeholder="Select grade"
          :disabled="!selectedCourseType"
          @change="onScopeFilterChanged"
        >
          <el-option
            v-for="grade in gradeOptions"
            :key="grade"
            :label="grade"
            :value="grade"
          />
        </el-select>
      </label>

      <el-button v-if="hasActiveFilters" class="oo-clear-filters" text @click="clearFilters">Clear</el-button>

    </section>

    <main class="oo-workspace" v-loading="loading">
      <div class="oo-split-workspace" :class="{ 'oo-split-workspace--collapsed': packagePanelCollapsed }">
        <aside v-if="!packagePanelCollapsed" class="oo-package-panel">
          <div class="oo-package-panel__heading">
            <div>
              <strong>Student courses</strong>
              <span>{{ candidateScopeLabel }}</span>
            </div>
            <div class="oo-package-panel__tools">
              <el-button
                text
                size="small"
                title="Hide course list"
                aria-label="Hide course list"
                @click="packagePanelCollapsed = true"
              >
                ‹
              </el-button>
            </div>
          </div>
          <div v-if="visiblePackageCandidates.length" class="oo-package-list">
            <button
              v-for="candidate in visiblePackageCandidates"
              :key="candidate.pkg.packageId"
              type="button"
              class="oo-package-item"
              :class="{ 'oo-package-item--active': activePackage?.packageId === candidate.pkg.packageId }"
              @click="selectCandidatePackage(candidate)"
            >
              <strong>{{ candidate.student.studentName }}</strong>
              <span>{{ candidate.pkg.packageName }}</span>
              <small>{{ candidate.teacher.teacherName || 'Unassigned' }} · {{ formatHours(candidate.pkg.remainingUnits) }}h remaining</small>
            </button>
          </div>
          <div v-else class="oo-empty empty-state">
            {{ candidateEmptyMessage }}
          </div>
          <el-pagination
            v-if="filteredPackageCandidates.length > packagePageSize"
            v-model:current-page="packagePage"
            class="oo-package-pagination"
            small
            layout="prev, pager, next"
            :page-size="packagePageSize"
            :total="filteredPackageCandidates.length"
          />
        </aside>

        <el-button
          v-if="packagePanelCollapsed"
          class="oo-package-toggle no-print"
          text
          title="Show course list"
          aria-label="Show course list"
          @click="packagePanelCollapsed = false"
        >
          ›
        </el-button>

        <section v-if="activePackage && activeStudent" class="oo-print-area oo-record-panel" ref="printAreaRef">
          <div class="oo-student-context">
            <div>
              <strong>{{ activeStudent.studentName }}</strong>
              <span>{{ activePackage.packageName }}</span>
            </div>
            <span class="oo-student-context__balance">
              {{ formatHours(activePackage.remainingUnits) }}h remaining
            </span>
          </div>

          <div class="oo-action-line" :class="{ 'oo-action-line--done': workflowComplete }">
            <span class="oo-action-line__marker" aria-hidden="true"></span>
            <div>
              <strong>{{ nextActionTitle }}</strong>
              <span>{{ nextActionHint }}</span>
            </div>
            <el-button
              v-if="nextArrangementLesson"
              class="action-primary"
              type="primary"
              @click="openAssignmentDialog(nextArrangementLesson)"
            >
              Set teacher & room
            </el-button>
            <el-button
              v-else-if="nextActionLesson && canConfirmLessons"
              class="action-primary"
              :type="nextActionMode === 'admin' ? 'warning' : 'success'"
              @click="openConfirm(nextActionLesson, nextActionMode)"
            >
              {{ nextActionButtonLabel }}
            </el-button>
          </div>

          <el-alert
            v-if="activePackage.renewalReminder"
            class="oo-renewal"
            title="Renewal reminder: two lessons or fewer remain."
            type="warning"
            :closable="false"
            show-icon
          />

          <div class="oo-table-title">
            <div>
              <strong>Lesson records</strong>
              <span>
                {{ activePackage.lessons.length }} records ·
                {{ pendingTeacherCount }} awaiting teacher ·
                {{ pendingAdminCount }} awaiting admin
              </span>
              <small>Each row shows one lesson and its next permitted action.</small>
            </div>
          </div>

          <el-table
            :data="activePackage.lessons"
            :max-height="520"
            border
            stripe
            size="small"
            class="oo-table sheet-table"
            :row-class-name="lessonRowClassName"
          >
            <el-table-column label="Lesson details" min-width="210" align="center" header-align="center">
              <template #default="{ row }">
                <div class="oo-lesson-main">
                  <strong>{{ formatDate(row.plannedStartAt) }}</strong>
                  <span>{{ formatTimeRange(row.plannedStartAt, row.plannedEndAt) }}</span>
                  <el-tag class="status-pill" :class="statusPillClass(row)" size="small" :type="statusTag(row)">
                    {{ statusLabel(row) }}
                  </el-tag>
                  <span>Teacher: {{ displayResourceName(row.actualTeacherName || row.plannedTeacherName) }}</span>
                  <span>Room: {{ displayResourceName(row.actualClassroomName || row.plannedClassroomName) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="canConfirmLessons || canManageLessonResources"
              label="Action"
              width="250"
              fixed="right"
              class-name="no-print"
              align="center"
              header-align="center"
            >
              <template #default="{ row }">
                <div class="oo-action-cell">
                  <el-button
                    v-if="canAdjustLesson(row)"
                    size="small"
                    plain
                    @click="openAdjustDialog(row)"
                  >
                    Adjust
                  </el-button>
                  <el-button
                    v-if="canManageLessonResources && needsLessonResources(row)"
                    class="action-primary"
                    type="primary"
                    size="small"
                    @click="openAssignmentDialog(row)"
                  >
                    Set teacher & room
                  </el-button>
                  <el-button
                    v-else-if="canAdminConfirm(row)"
                    class="action-primary"
                    type="warning"
                    size="small"
                    @click="openConfirm(row, 'admin')"
                  >
                    Review & deduct
                  </el-button>
                  <el-button
                    v-else-if="canInitialConfirm(row)"
                    class="action-primary"
                    type="success"
                    size="small"
                    plain
                    @click="openConfirm(row, 'normal')"
                  >
                    Submit attendance
                  </el-button>
                  <span v-else-if="!canAdjustLesson(row)" class="oo-no-action">{{ taskLabel(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="hasNotes" label="Notes" min-width="160" fixed="right" show-overflow-tooltip align="center" header-align="center">
              <template #default="{ row }">
                <span class="oo-note-cell">{{ row.rejectReason || row.notes || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-else class="oo-record-panel oo-record-panel--empty">
          <div class="oo-empty empty-state">
            Choose a student course on the left to view its lesson records.
          </div>
        </section>
      </div>
    </main>

    <el-dialog v-model="confirmDialogVisible" :title="confirmDialogTitle" width="520px" :close-on-click-modal="false" class="no-print">
      <el-form :model="confirmForm" label-width="130px">
        <el-alert
          v-if="showAdminDirectConfirmWarning"
          title="Teacher has not submitted attendance for this lesson. If you continue, admin will finally confirm and deduct it directly."
          type="warning"
          :closable="false"
          show-icon
        />
        <el-form-item label="Result">
          <el-select v-model="confirmForm.resultStatus" style="width: 100%">
            <el-option label="Completed" value="COMPLETED" />
            <el-option label="Formal Leave" value="FORMAL_LEAVE" />
            <el-option label="No Show" value="NO_SHOW" />
            <el-option v-if="isAdmin" label="Cancelled" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="confirmForm.resultStatus === 'COMPLETED'" label="Actual Start">
          <el-date-picker
            v-model="confirmForm.actualStartAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="confirmForm.resultStatus === 'COMPLETED'" label="Actual Hours">
          <el-input-number v-model="confirmForm.actualHours" :min="0" :max="10" :precision="1" :step="0.5" />
          <span class="oo-form-hint">Default is 2h; 1.5h and other 0.5h increments are allowed.</span>
        </el-form-item>
        <el-form-item v-if="confirmForm.resultStatus === 'COMPLETED'" label="Actual End">
          <span>{{ computedActualEndAt || '—' }}</span>
        </el-form-item>
        <el-form-item label="Notes">
          <el-input v-model="confirmForm.notes" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert
          v-if="isNonAttendanceStatus(confirmForm.resultStatus)"
          :title="nonAttendanceNotice(confirmForm.resultStatus)"
          type="info"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="confirmDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConfirm">{{ confirmDialogSubmitLabel }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustDialogVisible" title="Adjust Lesson Time" width="520px" :close-on-click-modal="false" class="no-print">
      <p class="oo-form-hint">
        Change only this lesson's class time. The recurring schedule will not be changed.
      </p>
      <el-form label-width="110px">
        <el-form-item label="Start Time" required>
          <el-date-picker
            v-model="adjustForm.plannedStartAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="End Time" required>
          <el-date-picker
            v-model="adjustForm.plannedEndAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="Reason" required>
          <el-input v-model="adjustForm.reason" type="textarea" :rows="2" placeholder="Why is this lesson adjusted?" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjustLesson">Save adjustment</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignmentDialogVisible" title="Assign Teacher & Classroom" width="480px" :close-on-click-modal="false" class="no-print">
      <p class="oo-form-hint">
        Set the teacher and classroom for this lesson. Students will see the update on their calendar.
      </p>
      <el-form label-width="100px">
        <el-form-item label="Teacher" required>
          <el-select v-model="assignmentForm.teacherId" filterable placeholder="Select teacher" style="width: 100%">
            <el-option v-for="teacher in teacherOptions" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Classroom" required>
          <el-select v-model="assignmentForm.classroomId" filterable placeholder="Select classroom" style="width: 100%">
            <el-option v-for="classroom in classrooms" :key="classroom.id" :label="classroom.roomName" :value="classroom.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignmentDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="assigning" @click="submitAssignment">Save assignment</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { fetchAllClassrooms, fetchAllTeachers, fetchStudents } from '@/api/base-data';
import { extractErrorMessage } from '@/api/http';
import { adminConfirmLessonRecord, confirmLesson } from '@/api/lesson';
import { fetchOneOnOneDeductions, type OneOnOneLesson, type OneOnOnePackage, type OneOnOneStudent, type OneOnOneTeacher } from '@/api/one-on-one';
import { createScheduleAdjustment } from '@/api/schedule';
import { useAuthStore } from '@/stores/auth';
import { isAdminOrStaffUser, isStudentUser, isTeacherUser } from '@/types/auth';
import type { EntityId } from '@/types/domain';

interface PackageCandidate {
  teacher: OneOnOneTeacher;
  student: OneOnOneStudent;
  pkg: OneOnOnePackage;
  studentKey: string;
  teacherKey: string;
  courseProductKey: string;
  gradeKey: string;
}

interface StudentEntry {
  teacher: OneOnOneTeacher;
  student: OneOnOneStudent;
}

const authStore = useAuthStore();
const isTeacher = computed(() => isTeacherUser(authStore.currentUser));
const isStudent = computed(() => isStudentUser(authStore.currentUser));
const isAdmin = computed(() => isAdminOrStaffUser(authStore.currentUser));
const canConfirmLessons = computed(() => isTeacher.value || isAdmin.value);
const canManageLessonResources = computed(() => !isTeacher.value && !isStudent.value);

const pageTitle = computed(() => isStudent.value ? 'My Courses & Hours' : '1-on-1 Lessons');
const loading = ref(false);
const submitting = ref(false);
const teacherOptions = ref<{ id: EntityId; teacherName: string }[]>([]);
const classrooms = ref<{ id: EntityId; roomName: string }[]>([]);
const teachers = ref<OneOnOneTeacher[]>([]);
const activeTeacher = ref<OneOnOneTeacher | null>(null);
const activeStudent = ref<OneOnOneStudent | null>(null);
const activePackage = ref<OneOnOnePackage | null>(null);
const printAreaRef = ref<HTMLElement>();
const selectedTeacherId = ref<EntityId>();
const selectedCourseType = ref<string>();
const selectedGradeBand = ref<string>();
const studentSearchKeyword = ref('');
const studentSearchLoading = ref(false);
const appliedStudentSearch = ref('');
const studentSearchResults = ref<StudentEntry[]>([]);
const packagePage = ref(1);
const packagePageSize = 10;
const packagePanelCollapsed = ref(false);

const studentEntries = computed<StudentEntry[]>(() => teachers.value.flatMap(teacher =>
  teacher.students.map(student => ({ teacher, student })),
));
const teacherFilterOptions = computed(() => teachers.value.filter(teacher => Boolean(teacher.teacherId)));
const hasNotes = computed(() => activePackage.value?.lessons.some(row => Boolean(row.rejectReason || row.notes)));
const allPackageCandidates = computed<PackageCandidate[]>(() => {
  const candidates: PackageCandidate[] = [];
  for (const teacher of teachers.value) {
    for (const student of teacher.students) {
      for (const pkg of student.packages) {
        candidates.push({
          teacher,
          student,
          pkg,
          studentKey: entityKey(student.studentId),
          teacherKey: entityKey(teacher.teacherId),
          courseProductKey: optionKey(courseTypeLabel(pkg)),
          gradeKey: optionKey(normalizedGradeBand(pkg.gradeBand)),
        });
      }
    }
  }
  return candidates;
});
const activeStudentSearch = computed(() => appliedStudentSearch.value.trim());
const searchedStudentKeys = computed(() => new Set(
  studentSearchResults.value.map(entry => entityKey(entry.student.studentId)),
));
const selectedTeacherKey = computed(() => entityKey(selectedTeacherId.value));
const courseTypeOptions = computed(() => {
  const options = new Set<string>();
  for (const candidate of allPackageCandidates.value) {
    options.add(courseTypeLabel(candidate.pkg));
  }
  return Array.from(options).sort((left, right) => left.localeCompare(right));
});
const gradeOptions = computed(() => {
  const grades = new Set<string>();
  for (const candidate of allPackageCandidates.value) {
    if (selectedCourseType.value && candidate.courseProductKey !== optionKey(selectedCourseType.value)) continue;
    grades.add(normalizedGradeBand(candidate.pkg.gradeBand));
  }
  return Array.from(grades).sort((left, right) => left.localeCompare(right));
});
const hasActiveFilters = computed(() => Boolean(
  activeStudentSearch.value || selectedTeacherKey.value || selectedCourseType.value || selectedGradeBand.value,
));
const filteredPackageCandidates = computed(() =>
  hasActiveFilters.value
    ? allPackageCandidates.value.filter(matchesCandidateFilters)
    : allPackageCandidates.value,
);
const visiblePackageCandidates = computed(() => {
  const start = (packagePage.value - 1) * packagePageSize;
  return filteredPackageCandidates.value.slice(start, start + packagePageSize);
});
const candidateScopeLabel = computed(() => {
  if (!hasActiveFilters.value) return 'All active 1-on-1 student courses';
  const filters = [
    activeStudentSearch.value && 'student',
    selectedTeacherKey.value && 'teacher',
    selectedCourseType.value && 'course type',
    selectedGradeBand.value && 'grade',
  ].filter(Boolean);
  return `Matching ${filters.join(' + ')}`;
});
const candidateEmptyMessage = computed(() => {
  if (!hasActiveFilters.value) return 'No active 1-on-1 student courses found.';
  if (activeStudentSearch.value && studentSearchResults.value.length === 0) {
    return 'No student matches this search.';
  }
  return 'No active 1-on-1 student courses match this selection.';
});
const pendingTeacherCount = computed(() =>
  activePackage.value?.lessons.filter(isAwaitingTeacherConfirmation).length ?? 0
);
const pendingAdminCount = computed(() =>
  activePackage.value?.lessons.filter(row => row.confirmStage === 'PENDING_ADMIN').length ?? 0
);
const workflowComplete = computed(() => !nextActionLesson.value
  && !nextArrangementLesson.value
  && pendingTeacherCount.value === 0
  && pendingAdminCount.value === 0
);
const nextArrangementLesson = computed(() => {
  if (!canManageLessonResources.value) return null;
  return activePackage.value?.lessons.find(row =>
    canAssignLessonResources(row) && (!row.plannedTeacherId || !row.plannedClassroomId),
  ) ?? null;
});
const nextActionLesson = computed(() => {
  const lessons = activePackage.value?.lessons ?? [];
  return lessons.find(row => canAdminConfirm(row))
    ?? lessons.find(row => canInitialConfirm(row))
    ?? null;
});
const nextActionMode = computed<'normal' | 'admin'>(() =>
  nextActionLesson.value && canAdminConfirm(nextActionLesson.value) ? 'admin' : 'normal'
);
const nextActionTitle = computed(() => {
  if (!activePackage.value) return 'Select a student sheet';
  if (nextArrangementLesson.value) return 'Set teacher and room';
  if (nextActionLesson.value) {
    return nextActionMode.value === 'admin' ? 'Review lesson and deduct hours' : 'Submit lesson attendance';
  }
  if (isAdmin.value && pendingTeacherCount.value > 0) return 'Waiting for teacher confirmation';
  if (isTeacher.value && pendingAdminCount.value > 0) return 'Waiting for admin review';
  return 'No lesson needs confirmation';
});
const nextActionHint = computed(() => {
  if (!activePackage.value) return 'Choose a course above to start.';
  if (nextArrangementLesson.value) {
    const row = nextArrangementLesson.value;
    return `${formatDate(row.plannedStartAt)} ${formatTimeRange(row.plannedStartAt, row.plannedEndAt)} needs an arrangement before attendance can be submitted.`;
  }
  const row = nextActionLesson.value;
  if (!row) {
    if (isAdmin.value && pendingTeacherCount.value > 0) {
      return 'The teacher must submit attendance before this lesson can be reviewed and deducted.';
    }
    if (isTeacher.value && pendingAdminCount.value > 0) {
      return 'The admin must review the lesson before hours are deducted.';
    }
    return 'All visible lessons are confirmed, cancelled, or waiting for a schedule update.';
  }
  return `${formatDate(row.plannedStartAt)} ${formatTimeRange(row.plannedStartAt, row.plannedEndAt)} · ${statusLabel(row)}`;
});
const nextActionButtonLabel = computed(() =>
  nextActionMode.value === 'admin' ? 'Review & deduct' : 'Submit attendance'
);

const confirmDialogVisible = ref(false);
const confirmMode = ref<'normal' | 'admin'>('normal');
const selectedLesson = ref<OneOnOneLesson | null>(null);
const confirmDialogTitle = computed(() =>
  confirmMode.value === 'admin' ? 'Review & Deduct Lesson' : 'Submit Lesson Attendance'
);
const confirmDialogSubmitLabel = computed(() =>
  confirmMode.value === 'admin' ? 'Review & deduct' : 'Submit for review'
);
const showAdminDirectConfirmWarning = computed(() =>
  confirmMode.value === 'admin'
  && Boolean(selectedLesson.value)
  && isAwaitingTeacherConfirmation(selectedLesson.value!)
);
const assignmentDialogVisible = ref(false);
const assigning = ref(false);
const assignmentLesson = ref<OneOnOneLesson | null>(null);
const adjustDialogVisible = ref(false);
const adjusting = ref(false);
const adjustLesson = ref<OneOnOneLesson | null>(null);
const assignmentForm = reactive({
  teacherId: undefined as EntityId | undefined,
  classroomId: undefined as EntityId | undefined,
});
const adjustForm = reactive({
  plannedStartAt: '',
  plannedEndAt: '',
  reason: '',
});
const confirmForm = reactive({
  resultStatus: 'COMPLETED',
  actualStartAt: '',
  actualHours: 2,
  notes: '',
});

const computedActualEndAt = computed(() => {
  if (!confirmForm.actualStartAt || confirmForm.actualHours == null) return '';
  return dayjs(confirmForm.actualStartAt).add(confirmForm.actualHours, 'hour').format('YYYY-MM-DD HH:mm');
});

async function loadTeachers() {
  if (!canManageLessonResources.value) return;
  try {
    const res = await fetchAllTeachers();
    teacherOptions.value = res.data.data.map(item => ({ id: item.id, teacherName: item.teacherName }));
  } catch {
    teacherOptions.value = [];
  }
}

async function loadClassrooms() {
  if (!canManageLessonResources.value) return;
  try {
    const res = await fetchAllClassrooms();
    classrooms.value = res.data.data.map(item => ({ id: item.id, roomName: item.roomName }));
  } catch {
    classrooms.value = [];
  }
}

async function loadData() {
  loading.value = true;
  try {
    const res = await fetchOneOnOneDeductions();
    teachers.value = res.data.data;
    resetPackagePage();
    keepOrSelectFirstPackage();
  } catch (err) {
    ElMessage.error(extractErrorMessage(err));
    teachers.value = [];
    clearSelection();
  } finally {
    loading.value = false;
  }
}

async function searchStudents() {
  const keyword = studentSearchKeyword.value.trim();
  if (!keyword) {
    clearStudentSearch();
    resetPackagePage();
    clearSelection();
    return;
  }
  if (keyword.length < 2) {
    ElMessage.warning('Enter at least two characters to search students.');
    return;
  }

  studentSearchLoading.value = true;
  appliedStudentSearch.value = keyword;
  resetPackagePage();
  clearSelection();
  try {
    const response = await fetchStudents({ keyword, pageNum: 1, pageSize: 20 });
    const profiles = response.data.data.records;
    const entriesByStudentId = new Map(studentEntries.value.map(entry => [String(entry.student.studentId), entry]));
    studentSearchResults.value = profiles
      .map(profile => entriesByStudentId.get(String(profile.id)))
      .filter((entry): entry is StudentEntry => Boolean(entry));
  } catch (err) {
    studentSearchResults.value = [];
    ElMessage.error(extractErrorMessage(err));
  } finally {
    studentSearchLoading.value = false;
  }
}

function keepOrSelectFirstPackage() {
  if (activePackage.value && findPackage(activePackage.value.packageId)) {
    const found = findPackage(activePackage.value.packageId);
    if (found) selectPackage(found.teacher, found.student, found.pkg);
    return;
  }
  clearSelection();
}

function findPackage(packageId: EntityId) {
  for (const teacher of teachers.value) {
    for (const student of teacher.students) {
      const pkg = student.packages.find(item => item.packageId === packageId);
      if (pkg) return { teacher, student, pkg };
    }
  }
  return null;
}

function teacherKey(teacher: OneOnOneTeacher) {
  return String(teacher.teacherId ?? teacher.teacherName);
}

function sameId(left?: EntityId, right?: EntityId) {
  return left != null && right != null && String(left) === String(right);
}

function entityKey(value?: EntityId) {
  return value == null || value === '' ? '' : String(value);
}

function optionKey(value?: string) {
  return value?.trim().toLowerCase() ?? '';
}

function normalizedGradeBand(value?: string) {
  return value?.trim() || 'General';
}

function courseTypeLabel(pkg: OneOnOnePackage) {
  return pkg.courseProductName?.trim() || pkg.subjectName?.trim() || pkg.packageName;
}

function matchesCandidateFilters(candidate: PackageCandidate) {
  if (activeStudentSearch.value && !searchedStudentKeys.value.has(candidate.studentKey)) return false;
  if (selectedTeacherKey.value && candidate.teacherKey !== selectedTeacherKey.value) return false;
  if (selectedCourseType.value && candidate.courseProductKey !== optionKey(selectedCourseType.value)) return false;
  if (selectedGradeBand.value && candidate.gradeKey !== optionKey(selectedGradeBand.value)) return false;
  return true;
}

function clearFilters() {
  selectedTeacherId.value = undefined;
  selectedCourseType.value = undefined;
  selectedGradeBand.value = undefined;
  studentSearchKeyword.value = '';
  clearStudentSearch();
  resetPackagePage();
  clearSelection();
}

function clearStudentSearch() {
  appliedStudentSearch.value = '';
  studentSearchResults.value = [];
}

function onScopeFilterChanged() {
  resetPackagePage();
  clearSelection();
}

function onCourseTypeChanged() {
  selectedGradeBand.value = undefined;
  onScopeFilterChanged();
}

function resetPackagePage() {
  packagePage.value = 1;
}

function selectCandidatePackage(candidate: PackageCandidate) {
  selectPackage(candidate.teacher, candidate.student, candidate.pkg);
}

function selectPackage(teacher: OneOnOneTeacher, student: OneOnOneStudent, pkg: OneOnOnePackage) {
  activeTeacher.value = teacher;
  activeStudent.value = student;
  activePackage.value = pkg;
}

function clearSelection() {
  activeTeacher.value = null;
  activeStudent.value = null;
  activePackage.value = null;
}

function canAdminConfirm(row: OneOnOneLesson) {
  if (!isAdmin.value || row.planningStatus === 'CANCELLED') return false;
  if (row.confirmStage === 'CONFIRMED' || row.confirmStage === 'REJECTED') return false;
  if (row.confirmStage === 'PENDING_ADMIN') return Boolean(row.lessonRecordId);
  return isAwaitingTeacherConfirmation(row);
}

function canInitialConfirm(row: OneOnOneLesson) {
  if (!isTeacher.value) return false;
  if (row.planningStatus === 'CANCELLED') return false;
  if (row.confirmStage === 'PENDING_ADMIN' || row.confirmStage === 'CONFIRMED') return false;
  if (row.resultStatus && row.confirmStage !== 'REJECTED') return false;
  return canConfirmLessons.value;
}

function isAwaitingTeacherConfirmation(row: OneOnOneLesson) {
  return row.planningStatus !== 'CANCELLED'
    && row.confirmStage !== 'PENDING_ADMIN'
    && row.confirmStage !== 'CONFIRMED'
    && row.confirmStage !== 'REJECTED'
    && !row.resultStatus;
}

function canAssignLessonResources(row: OneOnOneLesson) {
  return row.planningStatus !== 'CANCELLED'
    && row.confirmStage !== 'PENDING_ADMIN'
    && row.confirmStage !== 'CONFIRMED';
}

function needsLessonResources(row: OneOnOneLesson) {
  return canAssignLessonResources(row)
    && (!row.plannedTeacherId || !row.plannedClassroomId);
}

function canAdjustLesson(row: OneOnOneLesson) {
  return canManageLessonResources.value
    && row.planningStatus !== 'CANCELLED'
    && row.confirmStage !== 'PENDING_ADMIN'
    && row.confirmStage !== 'CONFIRMED'
    && row.confirmStage !== 'REJECTED'
    && !row.lessonRecordId
    && !row.resultStatus;
}

function taskLabel(row: OneOnOneLesson) {
  if (row.planningStatus === 'CANCELLED') return 'Cancelled';
  if (row.confirmStage === 'PENDING_ADMIN') return 'Awaiting admin review';
  if (row.confirmStage === 'CONFIRMED') {
    return row.deductedUnits == null ? 'Completed' : `${formatHours(row.deductedUnits)}h deducted`;
  }
  if (isAwaitingTeacherConfirmation(row)) return 'Awaiting teacher';
  return 'No task';
}

function openAssignmentDialog(row: OneOnOneLesson) {
  assignmentLesson.value = row;
  assignmentForm.teacherId = findSelectableResourceId(teacherOptions.value, row.plannedTeacherId);
  assignmentForm.classroomId = findSelectableResourceId(classrooms.value, row.plannedClassroomId);
  assignmentDialogVisible.value = true;
}

function openAdjustDialog(row: OneOnOneLesson) {
  adjustLesson.value = row;
  adjustForm.plannedStartAt = row.plannedStartAt;
  adjustForm.plannedEndAt = row.plannedEndAt;
  adjustForm.reason = '';
  adjustDialogVisible.value = true;
}

function findSelectableResourceId(
  options: Array<{ id: EntityId }>,
  resourceId?: EntityId,
) {
  return options.find(option => String(option.id) === String(resourceId))?.id;
}

async function submitAssignment() {
  if (!assignmentLesson.value || !assignmentForm.teacherId || !assignmentForm.classroomId) {
    ElMessage.warning('Select both a teacher and a classroom.');
    return;
  }

  assigning.value = true;
  try {
    await createScheduleAdjustment({
      occurrenceId: assignmentLesson.value.occurrenceId,
      adjustmentType: 'ASSIGN_RESOURCES',
      plannedTeacherId: assignmentForm.teacherId,
      plannedClassroomId: assignmentForm.classroomId,
      reason: 'Lesson resource assignment',
    });
    ElMessage.success('Lesson arrangement saved. The student calendar is updated.');
    assignmentDialogVisible.value = false;
    await loadData();
  } catch (err) {
    ElMessage.error(extractErrorMessage(err));
  } finally {
    assigning.value = false;
  }
}

function resolveHalfHourUnits(startAt: string, endAt: string) {
  const minutes = dayjs(endAt).diff(dayjs(startAt), 'minute');
  if (minutes <= 0) return null;
  if (minutes % 30 !== 0) return null;
  return minutes / 30;
}

function resolveAdjustedUnits() {
  return resolveHalfHourUnits(adjustForm.plannedStartAt, adjustForm.plannedEndAt);
}

async function submitAdjustLesson() {
  if (!adjustLesson.value) return;
  if (!adjustForm.plannedStartAt || !adjustForm.plannedEndAt) {
    ElMessage.warning('Select both start and end time.');
    return;
  }
  const plannedUnits = resolveAdjustedUnits();
  if (!plannedUnits) {
    ElMessage.warning('Adjusted lesson duration must use 0.5-hour increments.');
    return;
  }
  const reason = adjustForm.reason.trim();
  if (!reason) {
    ElMessage.warning('Adjustment reason is required.');
    return;
  }

  adjusting.value = true;
  try {
    await createScheduleAdjustment({
      occurrenceId: adjustLesson.value.occurrenceId,
      adjustmentType: 'RESCHEDULE',
      plannedStartAt: adjustForm.plannedStartAt,
      plannedEndAt: adjustForm.plannedEndAt,
      plannedUnits,
      reason,
    });
    ElMessage.success('Lesson time adjusted.');
    adjustDialogVisible.value = false;
    await loadData();
  } catch (err) {
    ElMessage.error(extractErrorMessage(err));
  } finally {
    adjusting.value = false;
  }
}

function lessonRowClassName({ row }: { row: OneOnOneLesson }) {
  if (nextActionLesson.value?.occurrenceId === row.occurrenceId) return 'oo-row--next';
  if (row.confirmStage === 'PENDING_ADMIN') return 'oo-row--pending-admin';
  if (isRejectedOrAbnormal(row)) return 'oo-row--issue';
  return '';
}

function openConfirm(row: OneOnOneLesson, mode: 'normal' | 'admin') {
  selectedLesson.value = row;
  confirmMode.value = mode;
  confirmForm.resultStatus = row.resultStatus || 'COMPLETED';
  confirmForm.actualStartAt = row.actualStartAt || row.plannedStartAt;
  confirmForm.actualHours = row.actualUnits ?? row.plannedUnits ?? 2;
  confirmForm.notes = row.notes || '';
  confirmDialogVisible.value = true;
}

async function submitConfirm() {
  if (!selectedLesson.value) return;
  const nonAttendance = isNonAttendanceStatus(confirmForm.resultStatus);
  if (!nonAttendance && !confirmForm.actualStartAt) {
    ElMessage.error('Actual start time is required for a completed lesson.');
    return;
  }
  if (!nonAttendance && (!confirmForm.actualHours || confirmForm.actualHours <= 0)) {
    ElMessage.error('Actual hours must be greater than zero.');
    return;
  }
  if (!nonAttendance && !Number.isInteger(confirmForm.actualHours * 2)) {
    ElMessage.error('Actual hours must use 0.5-hour increments.');
    return;
  }

  submitting.value = true;
  try {
    const row = selectedLesson.value;
    const actualEndAt = confirmForm.actualStartAt
      ? dayjs(confirmForm.actualStartAt).add(confirmForm.actualHours, 'hour').format('YYYY-MM-DDTHH:mm:ss')
      : undefined;
    const payload = {
      occurrenceId: row.occurrenceId,
      resultStatus: confirmForm.resultStatus,
      actualTeacherId: nonAttendance ? undefined : row.actualTeacherId || row.plannedTeacherId,
      actualClassroomId: nonAttendance ? undefined : row.actualClassroomId || row.plannedClassroomId,
      actualStartAt: nonAttendance ? undefined : confirmForm.actualStartAt || undefined,
      actualEndAt: nonAttendance ? undefined : actualEndAt,
      actualUnits: nonAttendance ? 0 : Math.round((confirmForm.actualHours || 0) * 2),
      notes: confirmForm.notes || undefined,
    };
    if (confirmMode.value === 'admin' && row.lessonRecordId) {
      await adminConfirmLessonRecord(row.lessonRecordId, payload);
      ElMessage.success('Review complete. Hours and any related schedule changes are now updated.');
    } else if (confirmMode.value === 'admin') {
      await confirmLesson(payload);
      ElMessage.success('Lesson directly confirmed by admin. Hours are now updated.');
    } else {
      await confirmLesson(payload);
      ElMessage.success('Attendance submitted for admin review.');
    }
    confirmDialogVisible.value = false;
    await loadData();
  } catch (err) {
    ElMessage.error(isFutureScheduleReconciliationError(err)
      ? 'No future lesson can be rebalanced. Open Schedule Adjustments to update the future schedule, renew the package, or reject this change.'
      : extractErrorMessage(err));
  } finally {
    submitting.value = false;
  }
}

function printCurrentPackage() {
  if (!activePackage.value) return;
  window.print();
}

function statusTag(row: OneOnOneLesson) {
  if (row.confirmStage === 'PENDING_ADMIN') return 'warning';
  if (isRejectedOrAbnormal(row)) return 'danger';
  if (row.confirmStage === 'CONFIRMED' && row.resultStatus === 'COMPLETED') return 'success';
  return 'primary';
}

function statusLabel(row: OneOnOneLesson) {
  if (row.confirmStage === 'PENDING_ADMIN') return 'Awaiting admin review';
  if (row.confirmStage === 'REJECTED') return 'Rejected';
  if (row.planningStatus === 'CANCELLED') return 'Cancelled';
  if (row.confirmStage === 'CONFIRMED' && row.resultStatus === 'COMPLETED') return 'Completed';
  if (row.confirmStage === 'CONFIRMED') return row.resultStatusName || 'Completed';
  if (row.resultStatusName) return row.resultStatusName;
  if (row.resultStatus) return row.resultStatus;
  return 'Awaiting teacher';
}

function statusPillClass(row: OneOnOneLesson) {
  if (row.confirmStage === 'PENDING_ADMIN') return 'status-pill--pending-admin';
  if (isRejectedOrAbnormal(row)) return 'status-pill--abnormal';
  if (row.confirmStage === 'CONFIRMED' && row.resultStatus === 'COMPLETED') return 'status-pill--completed';
  return 'status-pill--pending-teacher';
}

function isRejectedOrAbnormal(row: OneOnOneLesson) {
  return row.confirmStage === 'REJECTED'
    || row.planningStatus === 'CANCELLED'
    || row.resultStatus === 'FORMAL_LEAVE'
    || row.resultStatus === 'NO_SHOW'
    || row.resultStatus === 'CANCELLED';
}

function isNonAttendanceStatus(status: string) {
  return status === 'FORMAL_LEAVE' || status === 'NO_SHOW' || status === 'CANCELLED';
}

function nonAttendanceNotice(status: string) {
  if (status === 'FORMAL_LEAVE') {
    return 'Formal Leave does not deduct hours. The system automatically appends a replacement lesson to preserve the package total.';
  }
  if (status === 'CANCELLED') {
    return 'Cancelled lessons deduct the full planned hours and do not create a replacement lesson.';
  }
  return 'No Show deducts the full planned hours and does not create a replacement lesson.';
}

function isFutureScheduleReconciliationError(error: unknown) {
  const response = (error as { response?: { data?: { code?: string } } })?.response;
  return response?.data?.code === 'FUTURE_SCHEDULE_RECONCILIATION_REQUIRED';
}

function formatHours(value?: number) {
  return Number(value ?? 0).toFixed(1);
}

function formatDate(value: string) {
  return value ? dayjs(value).format('MM/DD/YYYY') : '—';
}

function formatDateTime(value: string) {
  return value ? dayjs(value).format('MM/DD HH:mm') : '—';
}

function formatTimeRange(startAt: string, endAt: string) {
  if (!startAt || !endAt) return '—';
  return `${dayjs(startAt).format('HH:mm')} - ${dayjs(endAt).format('HH:mm')}`;
}

function displayResourceName(name?: string) {
  return name?.split(/\r?\n/)[0]?.trim() || 'To be assigned';
}

onMounted(async () => {
  await Promise.all([loadTeachers(), loadClassrooms()]);
  await loadData();
});
</script>

<style scoped>
.oo-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.oo-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 42px;
}

.oo-toolbar h2 {
  margin: 0;
  font-size: 18px;
  color: #1f2937;
}

.oo-toolbar p {
  margin: 2px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.oo-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.oo-help {
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fff;
}

.oo-help summary {
  padding: 9px 12px;
  color: #334155;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  user-select: none;
}

.oo-help__content {
  padding: 0 14px 12px 32px;
  border-top: 1px solid #eef2f7;
  color: #486581;
  font-size: 12px;
}

.oo-help__content ol {
  margin: 10px 0 6px;
  padding-left: 16px;
}

.oo-help__content li {
  margin: 4px 0;
}

.oo-help__content p {
  margin: 6px 0 0;
}

.oo-help__content strong {
  color: #102a43;
}

.oo-selector-bar {
  display: grid;
  grid-template-columns:
    minmax(320px, 1.35fr)
    minmax(180px, 0.9fr)
    minmax(200px, 1fr)
    minmax(150px, 0.75fr)
    92px;
  align-items: end;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fff;
}

.oo-field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.oo-field > span {
  color: #486581;
  font-size: 11px;
  font-weight: 700;
}

.oo-field :deep(.el-select),
.oo-field :deep(.el-input) {
  width: 100%;
}

.oo-student-search {
  display: flex;
  align-items: end;
  gap: 8px;
}

.oo-student-search .oo-field {
  flex: 1;
}

.oo-workspace {
  min-height: calc(100vh - 176px);
  padding: 12px;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fff;
}

.oo-student-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #e2e8f0;
}

.oo-student-context strong,
.oo-student-context span {
  display: block;
}

.oo-student-context strong {
  color: #102a43;
  font-size: 15px;
}

.oo-student-context div > span {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.oo-student-context__balance {
  color: #102a43;
  font-size: 15px;
  font-weight: 800;
  white-space: nowrap;
}

.oo-empty {
  padding: 24px 12px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.oo-clear-filters {
  align-self: end;
  width: 92px;
  margin-bottom: 1px;
  padding: 0;
  white-space: nowrap;
}

.oo-split-workspace {
  display: grid;
  grid-template-columns: minmax(240px, 280px) minmax(0, 1fr);
  gap: 12px;
  min-height: 560px;
}

.oo-split-workspace--collapsed {
  grid-template-columns: 42px minmax(0, 1fr);
}

.oo-package-panel,
.oo-record-panel {
  min-width: 0;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fff;
}

.oo-package-panel {
  display: flex;
  flex-direction: column;
}

.oo-package-panel__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.oo-package-panel__heading strong,
.oo-package-panel__heading span {
  display: block;
}

.oo-package-panel__heading strong {
  color: #102a43;
  font-size: 14px;
}

.oo-package-panel__heading div span,
.oo-package-panel__heading > span {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.oo-package-panel__tools {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 4px;
}

.oo-package-toggle,
.oo-package-panel__tools :deep(.el-button) {
  width: 36px;
  min-width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #f8fafc;
  color: #334155;
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.oo-package-toggle:hover,
.oo-package-panel__tools :deep(.el-button:hover) {
  border-color: #60a5fa;
  background: #eff6ff;
  color: #1d4ed8;
}

.oo-package-toggle {
  align-self: start;
  margin-top: 12px;
}

.oo-package-list {
  display: grid;
  gap: 6px;
  max-height: 560px;
  overflow-y: auto;
  padding: 8px;
}

.oo-package-pagination {
  justify-content: center;
  padding: 8px 6px 10px;
  border-top: 1px solid #e2e8f0;
}

.oo-package-item {
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 5px;
  background: #fff;
  color: #102a43;
  cursor: pointer;
  text-align: left;
}

.oo-package-item:hover,
.oo-package-item--active {
  border-color: #60a5fa;
  background: #f0f7ff;
}

.oo-package-item strong,
.oo-package-item span,
.oo-package-item small {
  display: block;
}

.oo-package-item strong {
  font-size: 13px;
}

.oo-package-item span {
  margin-top: 3px;
  color: #334155;
  font-size: 12px;
}

.oo-package-item small {
  margin-top: 4px;
  color: #64748b;
  font-size: 11px;
}

.oo-record-panel {
  padding: 12px;
}

.oo-record-panel--empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.oo-action-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  margin-bottom: 10px;
  padding: 7px 10px;
  border: 1px solid #f2d19b;
  border-radius: 5px;
  background: #fffdf6;
}

.oo-action-line--done {
  border-color: #b7eb8f;
  background: #f8fff3;
}

.oo-action-line__marker {
  width: 4px;
  align-self: stretch;
  flex: 0 0 auto;
  border-radius: 2px;
  background: #d97706;
}

.oo-action-line--done .oo-action-line__marker {
  background: #18b58a;
}

.oo-action-line > div {
  min-width: 0;
  flex: 1;
}

.oo-action-line strong,
.oo-action-line span {
  display: block;
}

.oo-action-line strong {
  color: #102a43;
  font-size: 13px;
}

.oo-action-line div span {
  margin-top: 2px;
  color: #64748b;
  font-size: 11px;
}

.oo-renewal {
  margin-bottom: 12px;
}

.oo-table-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 0 8px;
}

.oo-table-title strong,
.oo-table-title span,
.oo-table-title small {
  display: block;
}

.oo-table-title strong {
  color: #1f2937;
  font-size: 15px;
}

.oo-table-title span {
  color: #94a3b8;
  font-size: 12px;
}

.oo-table-title small {
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
}

.status-pill--pending-teacher {
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.status-pill--pending-admin {
  border-color: #f2d19b;
  color: #b45309;
}

.status-pill--completed {
  border-color: #b7eb8f;
  color: #237804;
}

.status-pill--abnormal {
  border-color: #f4b6b6;
  color: #b42318;
}

.oo-table {
  width: 100%;
}

.oo-lesson-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  text-align: center;
}

.oo-lesson-main strong {
  color: #102a43;
  font-size: 13px;
}

.oo-lesson-main span {
  color: #64748b;
  font-size: 12px;
}

.oo-lesson-main .status-pill {
  align-self: center;
  margin: 1px 0;
}

.oo-note-cell {
  display: block;
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oo-action-cell {
  display: flex;
  flex-wrap: nowrap;
  justify-content: center;
  gap: 6px;
}

.oo-action-cell :deep(.el-button) {
  margin-left: 0;
  white-space: nowrap;
}

.oo-no-action {
  display: inline-block;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.35;
}

:deep(.oo-row--next) {
  --el-table-tr-bg-color: #fffaf0;
}

:deep(.oo-row--pending-admin) {
  --el-table-tr-bg-color: #fffdf6;
}

:deep(.oo-row--issue) {
  --el-table-tr-bg-color: #fff5f5;
}

.oo-form-hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .oo-selector-bar {
    grid-template-columns: repeat(2, minmax(240px, 1fr));
  }

  .oo-clear-filters {
    width: auto;
    justify-self: start;
  }

}

@media (max-width: 760px) {
  .oo-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .oo-actions {
    justify-content: flex-start;
  }

  .oo-selector-bar {
    grid-template-columns: 1fr;
  }

  .oo-student-search,
  .oo-split-workspace {
    align-items: stretch;
    flex-direction: column;
  }

  .oo-clear-filters {
    justify-self: stretch;
  }

  .oo-split-workspace {
    display: flex;
    min-height: 0;
  }

  .oo-package-list {
    max-height: 260px;
  }

  .oo-workspace {
    padding: 8px;
  }

  .oo-action-line {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}

@media print {
  :global(.app-sidebar),
  :global(.app-header),
  .no-print {
    display: none !important;
  }

  .oo-page,
  .oo-workspace {
    display: block;
    border: 0;
    padding: 0;
    min-height: auto;
  }

  .oo-print-area {
    color: #000;
  }

  .oo-student-context,
  .oo-action-line,
  .oo-table-title {
    break-inside: avoid;
  }

  .oo-action-line {
    border-color: #999;
    background: #fff;
  }
}
</style>
