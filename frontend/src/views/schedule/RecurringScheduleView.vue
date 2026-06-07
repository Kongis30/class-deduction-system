<template>
  <div class="page-stack">
    <section class="schedule-panel">
      <div class="schedule-panel__header">
        <span class="schedule-panel__title">Recurring Schedule</span>
      </div>

      <div class="schedule-panel__body">
        <!-- Column 1: Students -->
        <aside class="col-panel col-students">
          <div class="col-panel__header">
            <el-input
              v-model="studentSearch"
              placeholder="Search students..."
              clearable
              :prefix-icon="Search"
            />
          </div>
          <div class="col-panel__body">
            <div v-if="filteredStudents.length === 0" class="col-panel__empty">
              {{ students.length === 0 ? 'Loading...' : 'No students found' }}
            </div>
            <div
              v-for="s in filteredStudents"
              :key="s.id"
              class="col-item"
              :class="{ 'col-item--active': selectedStudentId === s.id }"
              @click="onStudentSelect(s.id)"
            >
              <span class="col-item__name">{{ s.studentName }}</span>
              <el-icon v-if="selectedStudentId === s.id" color="#409eff"><ArrowRight /></el-icon>
            </div>
          </div>
        </aside>

        <!-- Column 2: Packages -->
        <aside class="col-panel col-packages">
          <div class="col-panel__body">
            <div v-if="!selectedStudentId" class="col-panel__hint">
              <el-icon :size="28" color="#c0c4cc"><ArrowLeft /></el-icon>
              <span>Select a student</span>
            </div>
            <div v-else-if="loadingPackages" class="col-panel__hint">
              <el-icon class="is-loading" :size="20"><Loading /></el-icon>
              <span>Loading packages...</span>
            </div>
            <div v-else-if="studentPackages.length === 0" class="col-panel__hint">
              No active packages for this student.
            </div>
            <div
              v-for="pkg in studentPackages"
              :key="pkg.id"
              class="col-item"
              :class="{ 'col-item--active': selectedPackageId === pkg.id }"
              @click="onPackageSelect(pkg)"
            >
              <div class="col-item__stack">
                <span class="col-item__name">{{ pkg.packageName }}</span>
                <span class="col-item__sub">{{ pkg.packageStatus }} · {{ pkg.remainingUnits ?? pkg.totalUnits }}h</span>
              </div>
              <el-icon v-if="selectedPackageId === pkg.id" color="#409eff"><ArrowRight /></el-icon>
            </div>
          </div>
        </aside>

        <!-- Column 3: Weekly Schedule -->
        <main class="col-panel col-schedule">
          <div class="col-panel__body">
            <!-- No package selected -->
            <div v-if="!selectedPackageId" class="col-panel__hint">
              <el-icon :size="28" color="#c0c4cc"><ArrowLeft /></el-icon>
              <span>{{ selectedStudentId ? 'Select a package' : 'Select a student, then a package' }}</span>
            </div>

            <!-- Schedule content -->
            <template v-else>
              <div class="schedule-info">
                <div class="schedule-info__header">
                  <span class="schedule-info__title">{{ selectedPackageName }}</span>
                  <div class="schedule-info__actions">
                    <el-button
                      v-if="currentRule"
                      type="primary"
                      size="small"
                      @click="openEditDialog"
                    >
                      Edit Schedule
                    </el-button>
                    <el-button
                      v-else
                      type="primary"
                      size="small"
                      @click="openCreateDialog"
                    >
                      Create Schedule
                    </el-button>
                    <el-button
                      v-if="currentRule"
                      size="small"
                      plain
                      @click="openDetailDialog(currentRule)"
                    >
                      Detail
                    </el-button>
                  </div>
                </div>

                <!-- Schedule summary -->
                <div v-if="currentRule" class="schedule-summary">
                  <div class="summary-field">
                    <label>Teacher</label>
                    <span>{{ currentRule.primaryTeacherName ?? '—' }}</span>
                  </div>
                  <div class="summary-field">
                    <label>Classroom</label>
                    <span>{{ currentRule.defaultClassroomName ?? '—' }}</span>
                  </div>
                  <div class="summary-field">
                    <label>Period</label>
                    <span>{{ currentRule.startDate }}{{ currentRule.endDate ? ' – ' + currentRule.endDate : ' (ongoing)' }}</span>
                  </div>
                  <div class="summary-field">
                    <label>Status</label>
                    <el-tag size="small" :type="ruleStatusType(currentRule.ruleStatus)">
                      {{ ruleStatusLabel(currentRule.ruleStatus) }}
                    </el-tag>
                  </div>
                  <div class="summary-field">
                    <label>Slots</label>
                    <span>{{ currentRuleSlots.length }} slot(s)/week · {{ currentRule.generatedOccurrenceCount ?? 0 }} lessons</span>
                  </div>
                  <div class="summary-field" v-if="currentRule.nextLessonStartAt">
                    <label>Next Lesson</label>
                    <span>{{ currentRule.nextLessonStartAt }}</span>
                  </div>
                </div>

                <!-- No schedule state -->
                <div v-else class="schedule-none">
                  <el-icon :size="28" color="#c0c4cc"><Calendar /></el-icon>
                  <span>No schedule yet for this package.</span>
                </div>
              </div>

              <!-- Weekly calendar -->
              <div v-if="allStudentOccupiedSlots.length > 0 || currentRule" class="schedule-calendar">
                <div class="schedule-calendar__label">Weekly Schedule</div>
                <WeeklyTimeSelector
                  v-model="emptySlots"
                  :occupied-slots="allStudentOccupiedSlots"
                  :week-start-date="displayMonday"
                  :readonly="false"
                  :hide-hint="true"
                />
              </div>
              <div v-else class="schedule-none">
                <el-icon :size="28" color="#c0c4cc"><Calendar /></el-icon>
                <span>No schedule yet for this package.</span>
              </div>
            </template>
          </div>
        </main>
      </div>
    </section>

    <!-- Create / Edit Schedule dialog -->
    <el-dialog
      v-model="editDialogVisible"
      :title="isEditing ? 'Edit Weekly Schedule' : 'Create Weekly Schedule'"
      width="720px"
      :close-on-click-modal="false"
      class="schedule-dialog"
    >
      <div class="edit-schedule">
        <!-- Basic info -->
        <div class="edit-schedule__info">
          <div class="edit-info-grid">
            <div class="edit-info-field">
              <label>Package</label>
              <span class="edit-info-value">{{ selectedPackageName }}</span>
            </div>
            <div class="edit-info-field">
              <label>Student</label>
              <span class="edit-info-value">{{ selectedStudentName }}</span>
            </div>
          </div>
        </div>

        <el-form ref="editFormRef" :model="editForm" :rules="editFormRules" label-width="130px">
          <el-form-item label="Primary Teacher" prop="primaryTeacherId">
            <el-select
              v-model="editForm.primaryTeacherId"
              placeholder="Select teacher"
              clearable
              filterable
              style="width: 100%;"
            >
              <el-option v-for="t in teachers" :key="t.id" :label="t.teacherName" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="Classroom" prop="defaultClassroomId">
            <el-select
              v-model="editForm.defaultClassroomId"
              placeholder="Select classroom"
              clearable
              filterable
              style="width: 100%;"
            >
              <el-option v-for="c in classrooms" :key="c.id" :label="c.roomName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="Start Date" prop="startDate">
            <el-date-picker
              v-model="editForm.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="Select start date"
              style="width: 100%;"
              :disabled-date="disabledPastDate"
            />
          </el-form-item>
          <el-form-item v-if="isEditing" label="Status">
            <el-select v-model="editForm.ruleStatus" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Paused" value="PAUSED" />
              <el-option label="Ended" value="ENDED" />
            </el-select>
          </el-form-item>
          <el-form-item label="Notes">
            <el-input
              v-model="editForm.notes"
              type="textarea"
              :rows="2"
              placeholder="Optional notes"
              style="width: 100%;"
            />
          </el-form-item>
        </el-form>

        <!-- Weekly time selector -->
        <el-divider content-position="left">Weekly Time Slots</el-divider>
        <p class="edit-schedule__hint">
          Click and drag on the calendar to select class time slots. Each slot = one recurring weekly class.
        </p>
        <WeeklyTimeSelector
          v-model="editForm.slots"
          :week-start-date="editWeekMonday"
          :occupied-slots="otherPackageOccupiedSlots"
          :readonly="false"
        />
        <div v-if="editForm.slots.length > 0" class="edit-schedule__summary">
          {{ editForm.slots.length }} slot(s) · {{ totalSlotUnits }} total units/week
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">
            {{ isEditing ? 'Save Changes' : 'Create Schedule' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Detail dialog -->
    <el-dialog
      v-model="detailDialogVisible"
      title="Schedule Rule Detail"
      width="560px"
      :close-on-click-modal="false"
      class="schedule-dialog"
    >
      <div v-if="detailData" class="rule-detail">
        <div class="detail-section">
          <h4 class="detail-section__title">Basic info</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Package</label>
              <span>{{ detailData.packageName || '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Status</label>
              <el-tag size="small" :type="ruleStatusType(detailData.ruleStatus)">
                {{ ruleStatusLabel(detailData.ruleStatus) }}
              </el-tag>
            </div>
            <div class="detail-field">
              <label>Student</label>
              <span>{{ detailData.studentName }}</span>
            </div>
            <div class="detail-field">
              <label>Teacher</label>
              <span>{{ detailData.primaryTeacherName ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Classroom</label>
              <span>{{ detailData.defaultClassroomName ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Generated Lessons</label>
              <span>{{ detailData.generatedOccurrenceCount }}</span>
            </div>
            <div class="detail-field">
              <label>Period</label>
              <span>{{ detailData.startDate }}{{ detailData.endDate ? ' – ' + detailData.endDate : ' (ongoing)' }}</span>
            </div>
            <div class="detail-field">
              <label>Next Lesson</label>
              <span>{{ detailData.nextLessonStartAt ?? '—' }}</span>
            </div>
          </div>
        </div>

        <div v-if="detailData.slots?.length" class="detail-section">
          <h4 class="detail-section__title">Weekly Slots ({{ detailData.slots.length }})</h4>
          <el-table :data="detailData.slots" size="small" class="slots-table">
            <el-table-column label="Day" width="120">
              <template #default="{ row }">{{ weekdayLabel(row.weekday) }}</template>
            </el-table-column>
            <el-table-column label="Time" width="180">
              <template #default="{ row }">{{ row.startTime }} – {{ row.endTime }}</template>
            </el-table-column>
            <el-table-column label="Hours" width="80" align="center">
              <template #default="{ row }">{{ (row.plannedUnits * 0.5).toFixed(1) }}</template>
            </el-table-column>
            <el-table-column label="Status" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.slotStatus === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.slotStatus }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
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
import { ArrowLeft, ArrowRight, Calendar, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import dayjs from 'dayjs';

import { fetchAllClassrooms, fetchAllStudents, fetchAllTeachers } from '@/api/base-data';
import { fetchCoursePackages } from '@/api/enrollment';
import {
  createRecurringScheduleRule,
  updateRecurringScheduleRule,
  fetchRecurringScheduleRule,
  fetchRecurringScheduleRules,
  fetchLessonOccurrences,
  fetchEnrolledClassGroups,
} from '@/api/schedule';
import type { CoursePackageItem, EntityId, RecurringScheduleRuleItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';

const loadingPackages = ref(false);
const editDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const submitting = ref(false);
const isEditing = ref(false);
const editingRuleId = ref<EntityId | null>(null);
const editFormRef = ref<FormInstance>();

const detailData = ref<any>(null);
const students = ref<{ id: EntityId; studentName: string }[]>([]);
const studentPackages = ref<CoursePackageItem[]>([]);
const teachers = ref<{ id: EntityId; teacherName: string }[]>([]);
const classrooms = ref<{ id: EntityId; roomName: string }[]>([]);
const enrolledClassGroups = ref<any[]>([]);
const classGroupLoading = ref(false);
const classGroupOccupiedSlots = ref<OccupiedSlotDef[]>([]);

// ─── All package rules (preloaded per student) ──
const allPackageRules = ref<Record<string, any>>({});

// ─── Selection state ────────────────────────────
const studentSearch = ref('');
const selectedStudentId = ref<EntityId | null>(null);
const selectedPackageId = ref<EntityId | null>(null);
const emptySlots = ref<SlotDef[]>([]);

const displayMonday = computed(() => dayjs().startOf('day').day(1).format('YYYY-MM-DD'));

const filteredStudents = computed(() => {
  const q = studentSearch.value.trim().toLowerCase();
  if (!q) return students.value;
  return students.value.filter(s => s.studentName.toLowerCase().includes(q));
});

const selectedPackageName = computed(() => {
  const pkg = studentPackages.value.find(p => p.id === selectedPackageId.value);
  return pkg?.packageName ?? '';
});

const selectedStudentName = computed(() => {
  const s = students.value.find(s => s.id === selectedStudentId.value);
  return s?.studentName ?? '';
});

// ─── Package name lookup ──────────────────────
const packageNameMap = computed(() => {
  const map: Record<string, string> = {};
  for (const pkg of studentPackages.value) {
    map[String(pkg.id)] = pkg.packageName;
  }
  return map;
});

// ─── Current rule/slots (derived from preloaded map) ─
const currentRule = computed(() => {
  if (!selectedPackageId.value) return null;
  return allPackageRules.value[String(selectedPackageId.value)] || null;
});

const currentRuleSlots = computed(() => {
  const rule = currentRule.value;
  if (!rule?.slots) return [];
  return rule.slots.map((s: any) => ({
    weekday: s.weekday,
    startTime: s.startTime,
    endTime: s.endTime,
    plannedUnits: s.plannedUnits,
  }));
});

// ─── Helper: get package name for display ──────
function getPackageName(pkgId: EntityId): string {
  const packageKey = String(pkgId);
  return packageNameMap.value[packageKey] || studentPackages.value.find(p => String(p.id) === packageKey)?.packageName || `#${packageKey}`;
}

// ─── All-student occupied slots (main calendar) ─
const allStudentOccupiedSlots = computed<OccupiedSlotDef[]>(() => {
  const result: OccupiedSlotDef[] = [];

  for (const [pkgIdStr, rule] of Object.entries(allPackageRules.value)) {
    const isCurrent = pkgIdStr === String(selectedPackageId.value);

    // Determine which slots to show for this package
    let displaySlots: SlotDef[] = [];

    if (editDialogVisible.value && isCurrent) {
      // When edit dialog is open for this package, show the in-edit slots
      displaySlots = editForm.slots;
    } else {
      // Show the saved slots from the rule
      displaySlots = rule.slots || [];
    }

    const pkgName = rule.packageName || getPackageName(pkgIdStr);

    for (const slot of displaySlots) {
      result.push({
        weekday: slot.weekday,
        startTime: slot.startTime,
        endTime: slot.endTime,
        courseLabel: `${pkgName} ${(slot.plannedUnits * 0.5).toFixed(1)}h`,
        isCurrent,
      });
    }
  }
  // 添加班课占用
  for (const slot of classGroupOccupiedSlots.value) {
    result.push(slot);
  }
  return result;
});

// ─── Helper: time string to minutes ───────────────
function parseTimeToMin(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

// ─── Other-package occupied slots (edit dialog) ─
const otherPackageOccupiedSlots = computed<OccupiedSlotDef[]>(() => {
  const result: OccupiedSlotDef[] = [];
  for (const [pkgIdStr, rule] of Object.entries(allPackageRules.value)) {
    if (pkgIdStr === String(selectedPackageId.value)) continue;
    const pkgName = rule.packageName || getPackageName(pkgIdStr);
    for (const slot of (rule.slots || [])) {
      result.push({
        weekday: slot.weekday,
        startTime: slot.startTime,
        endTime: slot.endTime,
        courseLabel: `${pkgName} ${(slot.plannedUnits * 0.5).toFixed(1)}h`,
      });
    }
  }
  // 添加班课占用（编辑对话框也显示）
  for (const slot of classGroupOccupiedSlots.value) {
    result.push(slot);
  }
  return result;
});

// ─── Edit dialog week Monday ────────────────────
const editWeekMonday = computed(() => {
  if (editForm.startDate) {
    return dayjs(editForm.startDate).day(1).format('YYYY-MM-DD');
  }
  return dayjs().startOf('day').day(1).format('YYYY-MM-DD');
});

// ─── Student selection ─────────────────────────
async function onStudentSelect(id: EntityId) {
  selectedStudentId.value = id;
  selectedPackageId.value = null;
  allPackageRules.value = {};
  classGroupOccupiedSlots.value = [];
  loadingPackages.value = true;
  try {
    const res = await fetchCoursePackages({ studentId: id, packageStatus: 'ACTIVE', pageSize: 50 });
    studentPackages.value = res.data.data.records;
    await loadAllPackageRules();
  } catch {
    studentPackages.value = [];
  } finally {
    loadingPackages.value = false;
  }
  loadEnrolledClassGroups(id);
}

// ─── Package selection ─────────────────────────
function onPackageSelect(pkg: CoursePackageItem) {
  selectedPackageId.value = pkg.id;
}

// ─── Load rules for ALL packages ────────────────
async function loadAllPackageRules() {
  const packages = studentPackages.value;
  if (packages.length === 0) {
    allPackageRules.value = {};
    return;
  }
  const results = await Promise.allSettled(
    packages.map(async (pkg) => {
      const res = await fetchRecurringScheduleRules({ packageId: pkg.id, pageSize: 1 });
      const rule = res.data.data.records[0];
      if (!rule) return { packageId: pkg.id, rule: null };
      try {
        const detailRes = await fetchRecurringScheduleRule(rule.id);
        return { packageId: pkg.id, rule: { ...rule, ...detailRes.data.data } };
      } catch {
        return { packageId: pkg.id, rule: null };
      }
    })
  );
    const map: Record<string, any> = {};
  for (const r of results) {
    if (r.status === 'fulfilled' && r.value.rule) {
        map[String(r.value.packageId)] = r.value.rule;
    }
  }
  allPackageRules.value = map;
}

// ─── Load enrolled class group slots ────────────
const classGroupSlotCache = new Map<string, OccupiedSlotDef[]>();

async function loadEnrolledClassGroups(studentId: EntityId) {
  classGroupLoading.value = true;
  try {
    const res = await fetchEnrolledClassGroups(studentId);
    enrolledClassGroups.value = res.data.data;
    await loadClassGroupSlots();
  } catch {
    enrolledClassGroups.value = [];
    classGroupOccupiedSlots.value = [];
  } finally {
    classGroupLoading.value = false;
  }
}

async function loadClassGroupSlots() {
  const monday = dayjs().startOf('week').add(1, 'day'); // 本周一
  const sunday = monday.add(6, 'day').endOf('day');
  const weekStart = monday.format('YYYY-MM-DDTHH:mm:ss');
  const weekEnd = sunday.format('YYYY-MM-DDTHH:mm:ss');

  const allSlots: OccupiedSlotDef[] = [];
  for (const cls of enrolledClassGroups.value) {
    try {
      const res = await fetchLessonOccurrences({
        classGroupId: cls.id,
        startAt: weekStart,
        endAt: weekEnd,
        pageSize: 50,
      });
      for (const occ of res.data.data.records) {
        if (occ.planningStatus === 'CANCELLED') continue;
        const start = dayjs(occ.plannedStartAt);
        const weekday = start.day() === 0 ? 7 : start.day();
        allSlots.push({
          weekday,
          startTime: start.format('HH:mm'),
          endTime: dayjs(occ.plannedEndAt).format('HH:mm'),
          courseLabel: cls.className,
          teacherName: occ.plannedTeacherName,
          classroomName: occ.plannedClassroomName,
        });
      }
    } catch { /* skip failed class group */ }
  }
  classGroupOccupiedSlots.value = allSlots;
}

// ─── Edit Form ──────────────────────────────────
const editForm = reactive({
  primaryTeacherId: undefined as EntityId | undefined,
  defaultClassroomId: undefined as EntityId | undefined,
  ruleStatus: undefined as string | undefined,
  startDate: '',
  notes: '',
  slots: [] as SlotDef[],
});

const editFormRules: FormRules = {
  startDate: [{ required: true, message: 'Start date is required', trigger: 'change' }],
};

const totalSlotUnits = computed(() =>
  editForm.slots.reduce((sum, s) => sum + (s.plannedUnits || 0), 0)
);

async function openCreateDialog() {
  isEditing.value = false;
  editingRuleId.value = null;
  editFormRef.value?.resetFields();
  Object.assign(editForm, {
    primaryTeacherId: undefined,
    defaultClassroomId: undefined,
    ruleStatus: undefined,
    startDate: '',
    notes: '',
    slots: [],
  });
  editDialogVisible.value = true;
}

async function openEditDialog() {
  if (!currentRule.value) return;
  const detail = currentRule.value;
  isEditing.value = true;
  editingRuleId.value = detail.id;
  editFormRef.value?.resetFields();
  Object.assign(editForm, {
    primaryTeacherId: detail.primaryTeacherId,
    defaultClassroomId: detail.defaultClassroomId,
    ruleStatus: detail.ruleStatus,
    startDate: detail.startDate,
    notes: detail.notes || '',
    slots: (detail.slots || []).map((s: any) => ({
      weekday: s.weekday,
      startTime: s.startTime,
      endTime: s.endTime,
      plannedUnits: s.plannedUnits,
    })),
  });
  editDialogVisible.value = true;
}

function submitForm() {
  if (isEditing.value) {
    submitEditForm();
  } else {
    submitCreateForm();
  }
}

async function submitCreateForm() {
  if (!editFormRef.value) return;
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return;
    if (editForm.slots.length === 0) {
      ElMessage.warning('Please select at least one class time slot on the calendar.');
      return;
    }
    submitting.value = true;
    try {
      await createRecurringScheduleRule({
        packageId: selectedPackageId.value!,
        primaryTeacherId: editForm.primaryTeacherId,
        defaultClassroomId: editForm.defaultClassroomId,
        startDate: editForm.startDate,
        notes: editForm.notes || undefined,
        slots: editForm.slots.map((s) => ({
          weekday: s.weekday,
          startTime: s.startTime,
          endTime: s.endTime,
          plannedUnits: s.plannedUnits,
        })),
      });
      ElMessage.success('Schedule created and lessons generated.');
      editDialogVisible.value = false;
      await loadAllPackageRules();
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message ?? 'Failed to create schedule.');
    } finally {
      submitting.value = false;
    }
  });
}

async function submitEditForm() {
  if (!editFormRef.value || !editingRuleId.value) return;
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return;
    if (editForm.slots.length === 0) {
      ElMessage.warning('Please select at least one class time slot on the calendar.');
      return;
    }
    submitting.value = true;
    try {
      const result = await updateRecurringScheduleRule(editingRuleId.value!, {
        primaryTeacherId: editForm.primaryTeacherId,
        defaultClassroomId: editForm.defaultClassroomId,
        ruleStatus: editForm.ruleStatus,
        startDate: editForm.startDate,
        notes: editForm.notes || undefined,
        slots: editForm.slots.map((s) => ({
          weekday: s.weekday,
          startTime: s.startTime,
          endTime: s.endTime,
          plannedUnits: s.plannedUnits,
        })),
      });
      const data = result.data.data;
      ElMessage.success(
        `Schedule updated. ${data.generatedOccurrenceCount} occurrences regenerated, ` +
        `${data.preservedOccurrenceCount} preserved (${data.confirmedOccurrenceCount} confirmed).`
      );
      editDialogVisible.value = false;
      await loadAllPackageRules();
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message ?? 'Failed to update schedule.');
    } finally {
      submitting.value = false;
    }
  });
}

// ─── Detail Dialog ───────────────────────────────
async function openDetailDialog(row: RecurringScheduleRuleItem) {
  try {
    const res = await fetchRecurringScheduleRule(row.id);
    detailData.value = res.data.data;
    detailDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load rule detail.');
  }
}

// ─── Helpers ────────────────────────────────────
function ruleStatusType(status: string) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'PAUSED') return 'warning';
  return 'info';
}

function ruleStatusLabel(status: string) {
  return { ACTIVE: 'Active', PAUSED: 'Paused', ENDED: 'Ended' }[status] ?? status;
}

function weekdayLabel(w: number) {
  return ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][w] ?? w;
}

// ─── Init ────────────────────────────────────────
async function init() {
  try {
    const [sRes, tRes, cRes] = await Promise.all([
      fetchAllStudents(),
      fetchAllTeachers(),
      fetchAllClassrooms(),
    ]);
    students.value = sRes.data.data.map(s => ({ id: s.id, studentName: s.studentName }));
    teachers.value = tRes.data.data;
    classrooms.value = cRes.data.data;
  } catch { /* silent */ }
}

init();

// ─── Date helpers ──────────────────────────────────────────
function disabledPastDate(date: Date) {
  return dayjs(date).startOf('day').isBefore(dayjs().startOf('day'));
}
</script>

<style scoped>
.schedule-panel {
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

.schedule-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}

.schedule-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.schedule-panel__body {
  display: flex;
  gap: 1px;
  flex: 1;
  min-height: 0;
  background: #ebeef5;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

/* ── Columns ──────────────────────────────────── */
.col-panel {
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.col-panel__header {
  padding: 10px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.col-panel__body {
  flex: 1;
  overflow-y: auto;
}

.col-panel__empty,
.col-panel__hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 16px;
  color: #909399;
  font-size: 13px;
  text-align: center;
}

.col-students { width: 220px; flex-shrink: 0; }
.col-packages { width: 220px; flex-shrink: 0; }
.col-schedule { flex: 1; min-width: 0; }

/* ── List items ───────────────────────────────── */
.col-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.15s;
}

.col-item:hover { background: #f5f7fa; }

.col-item--active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  padding-left: 9px;
}

.col-item--active:hover { background: #d9ecff; }

.col-item__name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.col-item--active .col-item__name { color: #409eff; }

.col-item__stack {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.col-item__sub {
  font-size: 11px;
  color: #909399;
}

/* ── Schedule display ────────────────────────── */
.schedule-info {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
}

.schedule-info__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.schedule-info__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.schedule-info__actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.schedule-summary {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px 16px;
}

.summary-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-field label {
  font-size: 11px;
  color: #909399;
}

.summary-field span {
  font-size: 12px;
  color: #606266;
}

.schedule-calendar {
  padding: 12px;
}

.schedule-none {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 12px;
  color: #909399;
  font-size: 13px;
}

.schedule-calendar__label {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

/* ── Edit dialog ─────────────────────────────── */
.edit-schedule__info {
  margin-bottom: 16px;
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 6px;
}

.edit-info-grid {
  display: flex;
  gap: 24px;
}

.edit-info-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.edit-info-field label {
  font-size: 11px;
  color: #909399;
}

.edit-info-value {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.edit-schedule__hint {
  margin: 0 0 10px;
  color: #909399;
  font-size: 12px;
}

.edit-schedule__summary {
  margin-top: 8px;
  color: #409eff;
  font-size: 12px;
  font-weight: 500;
}

/* ── Dialog ─────────────────────────────────── */
.schedule-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
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

/* Detail */
.rule-detail {
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

.slots-table {
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
</style>
