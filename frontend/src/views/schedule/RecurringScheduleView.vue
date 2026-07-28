<template>
  <div class="page-stack schedule-page">
    <div class="schedule-mode-tabs">
      <button
        class="schedule-mode-tab"
        :class="{ 'schedule-mode-tab--active': scheduleMode === 'one-on-one' }"
        type="button"
        @click="scheduleMode = 'one-on-one'"
      >
        1-on-1
      </button>
      <button
        class="schedule-mode-tab"
        :class="{ 'schedule-mode-tab--active': scheduleMode === 'class' }"
        type="button"
        @click="scheduleMode = 'class'"
      >
        Group Class
      </button>
    </div>

    <section v-if="scheduleMode === 'one-on-one'" class="schedule-panel">
      <div class="schedule-panel__header">
        <span class="schedule-panel__title">Schedule 1-on-1 Lessons</span>
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
                      v-if="currentRule"
                      type="warning"
                      size="small"
                      plain
                      @click="openWeeklyAdjustmentDialog"
                    >
                      Adjust This Week
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
              placeholder="Assign later"
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

    <!-- This week adjustment dialog -->
    <el-dialog
      v-model="weeklyDialogVisible"
      :title="`Adjust This Week — ${selectedPackageName}`"
      width="860px"
      :close-on-click-modal="false"
      class="schedule-dialog"
    >
      <div v-loading="weeklyLoading" class="weekly-adjustment">
        <el-alert
          title="Only this week's lesson occurrences will be changed. The recurring weekly schedule remains unchanged."
          type="info"
          show-icon
          :closable="false"
        />

        <div class="weekly-summary">
          <div class="weekly-summary__item">
            <span>
              Original plan
              <el-tooltip
                content="Total planned hours for this week before any change."
                placement="top"
              >
                <el-icon class="weekly-summary__help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
            <strong>{{ formatUnitsAsHours(originalWeeklyUnits) }}</strong>
          </div>
          <div class="weekly-summary__item">
            <span>
              Adjusted
              <el-tooltip
                content="Total planned hours for this week after your changes (existing edits plus added lessons, minus removed ones)."
                placement="top"
              >
                <el-icon class="weekly-summary__help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
            <strong>{{ formatUnitsAsHours(adjustedWeeklyUnits) }}</strong>
          </div>
          <div class="weekly-summary__item" :class="{ 'weekly-summary__item--changed': unitDelta !== 0 }">
            <span>
              Delta
              <el-tooltip
                content="Adjusted minus original hours. Positive = more hours used this week (package may run out sooner); negative = fewer hours used."
                placement="top"
              >
                <el-icon class="weekly-summary__help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
            <strong>{{ formatSignedUnitsAsHours(unitDelta) }}</strong>
          </div>
          <div class="weekly-summary__item">
            <span>
              Added / Modified / Removed
              <el-tooltip
                content="Number of lessons added, lessons whose details were modified, and lessons removed in this adjustment."
                placement="top"
              >
                <el-icon class="weekly-summary__help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
            <strong>{{ addedCount }} / {{ modifiedCount }} / {{ removedCount }}</strong>
          </div>
        </div>

        <div class="weekly-cal__hint">
          Drag on an empty area to add a lesson. Click a lesson block to edit, move, or remove it. Striped grey blocks are the student's other lessons this week.
        </div>


        <div class="weekly-cal">
          <div class="weekly-cal__head">
            <span class="weekly-cal__corner"></span>
            <span
              v-for="day in weekDays"
              :key="day.date"
              class="weekly-cal__day"
              :class="{ 'weekly-cal__day--today': day.isToday }"
            >
              <span class="weekly-cal__day-name">{{ day.dayLabel }}</span>
              <span class="weekly-cal__day-date">{{ day.dateLabel }}</span>
            </span>
          </div>
          <div class="weekly-cal__body">

            <div class="weekly-cal__times">
              <span v-for="r in calendarRows" :key="r.label" class="weekly-cal__time">{{ r.label }}</span>
            </div>

            <div
              v-for="(day, dayIndex) in weekDays"
              :key="day.date"
              class="weekly-cal__col"
            >

              <div
                v-for="(r, slotIndex) in calendarRows"
                :key="r.label"
                class="weekly-cal__cell"
                :class="{ 'weekly-cal__cell--preview': isDragPreview(dayIndex, slotIndex) }"
                @mousedown.prevent="onCalMouseDown(dayIndex, slotIndex)"
                @mouseenter="onCalMouseEnter(dayIndex, slotIndex)"
              ></div>

              <div
                v-for="busy in busyForDay(dayIndex)"
                :key="`busy-${busy.id}`"
                class="weekly-cal__busy"
                :style="busyStyle(busy)"
              >
                <span class="weekly-cal__busy-label">{{ busyLabel(busy) }}</span>
              </div>

              <div
                v-for="row in rowsForDay(dayIndex)"
                :key="row.rowKey"
                class="weekly-cal__event"
                :class="{
                  'weekly-cal__event--new': row.rowType === 'NEW',
                  'weekly-cal__event--modified': row.rowType === 'EXISTING' && !row.markedForDelete && isWeeklyRowChanged(row),
                  'weekly-cal__event--deleted': row.rowType === 'EXISTING' && row.markedForDelete,
                  'weekly-cal__event--active': row.rowKey === selectedRowKey,
                }"
                :style="eventStyle(row)"
                @mousedown.stop
                @click="selectRow(row)"
              >
                <span class="weekly-cal__event-time">{{ eventTimeLabel(row) }}</span>
                <span class="weekly-cal__event-meta">{{ eventTeacherLabel(row) }}</span>
              </div>
            </div>
          </div>
        </div>


        <div v-if="sortedWeeklyRows.length > 0" class="weekly-list">
          <div
            v-for="row in sortedWeeklyRows"
            :key="row.rowKey"
            class="weekly-list__item"
            :class="{
              'weekly-list__item--new': row.rowType === 'NEW',
              'weekly-list__item--modified': row.rowType === 'EXISTING' && !row.markedForDelete && isWeeklyRowChanged(row),
              'weekly-list__item--deleted': row.rowType === 'EXISTING' && row.markedForDelete,
              'weekly-list__item--active': row.rowKey === selectedRowKey,
            }"
            @click="selectRow(row)"
          >
            <span class="weekly-list__when">{{ rowDayLabel(row) }} · {{ eventTimeLabel(row) }}</span>
            <span class="weekly-list__meta">{{ eventTeacherLabel(row) }} · {{ row.plannedHours }}h</span>
            <el-tag
              v-if="rowStatusTag(row)"
              size="small"
              :type="rowStatusTag(row)!.type"
              effect="plain"
            >{{ rowStatusTag(row)!.label }}</el-tag>
          </div>
        </div>


        <div v-if="selectedRow" class="weekly-editor" :class="{ 'weekly-editor--deleted': selectedRow.markedForDelete }">
          <div class="weekly-editor__title">
            <strong>{{ selectedRow.rowType === 'NEW' ? 'New lesson' : 'Edit lesson' }}</strong>
            <span v-if="selectedRow.rowType === 'EXISTING'">{{ formatDate(selectedRow.original?.plannedStartAt) }}</span>
          </div>
          <div class="weekly-editor__grid">
            <div class="weekly-editor__field">
              <label>Day</label>
              <el-select
                :model-value="rowDate(selectedRow)"
                :disabled="selectedRow.markedForDelete"
                @update:model-value="(v: string) => setRowDay(selectedRow!, v)"
              >
                <el-option
                  v-for="day in weekDays"
                  :key="day.date"
                  :label="`${day.dayLabel} ${day.dateLabel}`"
                  :value="day.date"
                />
              </el-select>
            </div>
            <div class="weekly-editor__field">
              <label>Start</label>
              <el-time-select
                :model-value="rowStartTime(selectedRow)"
                start="08:00"
                end="20:30"
                step="00:30"
                :disabled="selectedRow.markedForDelete"
                @update:model-value="(v: string) => setRowStartTime(selectedRow!, v)"
              />
            </div>
            <div class="weekly-editor__field">
              <label>Hours</label>
              <el-input-number
                :model-value="selectedRow.plannedHours"
                :min="0.5"
                :step="0.5"
                :step-strictly="true"
                :precision="1"
                :controls="false"
                :disabled="selectedRow.markedForDelete"
                @update:model-value="(v: number) => setRowHours(selectedRow!, v)"
              />
              <span class="weekly-editor__hint">Use 0.5h increments, for example 1.0h or 1.5h.</span>
            </div>
            <div class="weekly-editor__field">
              <label>Teacher</label>
              <el-select v-model="selectedRow.plannedTeacherId" clearable filterable placeholder="Teacher" :disabled="selectedRow.markedForDelete">
                <el-option v-for="t in teachers" :key="t.id" :label="t.teacherName" :value="t.id" />
              </el-select>
            </div>
            <div class="weekly-editor__field">
              <label>Classroom</label>
              <el-select v-model="selectedRow.plannedClassroomId" clearable filterable placeholder="Classroom" :disabled="selectedRow.markedForDelete">
                <el-option v-for="c in classrooms" :key="c.id" :label="c.roomName" :value="c.id" />
              </el-select>
            </div>
          </div>

          <div v-if="selectedRow.rowType === 'NEW' || selectedRow.markedForDelete" class="weekly-editor__reason">
            <el-input
              v-model="selectedRow.itemReason"
              size="small"
              :placeholder="selectedRow.rowType === 'NEW' ? 'Reason for adding this lesson (optional)' : 'Reason for removing this lesson (optional)'"
            />
          </div>
          <div class="weekly-editor__actions">
            <el-button
              v-if="selectedRow.rowType === 'EXISTING' && !selectedRow.markedForDelete"
              type="danger" plain :icon="Delete"
              @click="toggleWeeklyRowDelete(selectedRow)"
            >Remove this lesson</el-button>
            <el-button
              v-else-if="selectedRow.rowType === 'EXISTING' && selectedRow.markedForDelete"
              type="primary" plain :icon="RefreshLeft"
              @click="toggleWeeklyRowDelete(selectedRow)"
            >Restore</el-button>
            <el-button
              v-else
              type="info" plain :icon="Close"
              @click="removeWeeklyNewRow(selectedRow)"
            >Discard new lesson</el-button>
          </div>
        </div>


        <el-form label-width="80px" class="weekly-reason">
          <el-form-item label="Reason">
            <el-input v-model="weeklyReason" type="textarea" :rows="2" placeholder="Optional reason" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="weeklyDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!hasWeeklyChanges" @click="submitWeeklyAdjustment">
            Save This Week
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

    <ScheduleAdjustmentsView v-if="scheduleMode === 'class'" mode="class" embedded class="schedule-class-adjustments" />
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, Calendar, Search, QuestionFilled, Delete, RefreshLeft, Close, Loading } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import dayjs from 'dayjs';
import { useRoute } from 'vue-router';

import { extractErrorMessage } from '@/api/http';
import { fetchAllClassrooms, fetchAllStudents, fetchAllTeachers } from '@/api/base-data';
import { fetchCoursePackages } from '@/api/enrollment';
import {
  createRecurringScheduleRule,
  updateRecurringScheduleRule,
  fetchRecurringScheduleRule,
  fetchRecurringScheduleRules,
  fetchLessonOccurrences,
  fetchEnrolledClassGroups,
  adjustWeeklySchedule,
} from '@/api/schedule';
import type { WeeklyScheduleAdjustmentItemPayload } from '@/api/schedule';
import type { CoursePackageItem, EntityId, LessonOccurrenceItem, RecurringScheduleRuleItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import ScheduleAdjustmentsView from './ScheduleAdjustmentsView.vue';

interface WeeklyAdjustmentRow {

  rowKey: string;

  rowType: 'EXISTING' | 'NEW';
  occurrenceId?: EntityId;
  lessonNo: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedHours: number;
  plannedTeacherId?: EntityId;
  plannedClassroomId?: EntityId;

  markedForDelete: boolean;

  itemReason: string;
  original?: LessonOccurrenceItem;
}

const loadingPackages = ref(false);
const route = useRoute();
const scheduleMode = ref<'one-on-one' | 'class'>('one-on-one');
const editDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const weeklyDialogVisible = ref(false);
const weeklyLoading = ref(false);
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



const currentWeekOccurrences = ref<LessonOccurrenceItem[]>([]);

// ─── Selection state ────────────────────────────
const studentSearch = ref('');
const selectedStudentId = ref<EntityId | null>(null);
const selectedPackageId = ref<EntityId | null>(null);
const emptySlots = ref<SlotDef[]>([]);
const weeklyRows = ref<WeeklyAdjustmentRow[]>([]);
const weeklyReason = ref('');

// ─── Weekly adjustment calendar (Sat→Fri) ──────────────────

const CAL_START_HOUR = 8;
const CAL_END_HOUR = 21;
const CAL_SLOT_MINUTES = 30;
const CAL_SLOT_HEIGHT = 28;

const selectedRowKey = ref<string | null>(null);


const weeklyBusyOccurrences = ref<LessonOccurrenceItem[]>([]);


const weekDays = computed(() => {
  const { start } = currentNaturalWeekRange();
  const todayStr = dayjs().format('YYYY-MM-DD');
  return Array.from({ length: 7 }, (_, i) => {
    const d = start.add(i, 'day');
    return {
      date: d.format('YYYY-MM-DD'),
      dayLabel: d.format('ddd'),
      dateLabel: d.format('M/D'),
      isToday: d.format('YYYY-MM-DD') === todayStr,
    };
  });
});


const calendarRows = computed(() => {
  const rows: { label: string; minutes: number }[] = [];
  for (let h = CAL_START_HOUR; h < CAL_END_HOUR; h++) {
    rows.push({ label: `${String(h).padStart(2, '0')}:00`, minutes: h * 60 });
    rows.push({ label: `${String(h).padStart(2, '0')}:30`, minutes: h * 60 + 30 });
  }
  return rows;
});


const selectedRow = computed(() =>
  weeklyRows.value.find(r => r.rowKey === selectedRowKey.value) ?? null
);


function rowsForDay(dayIndex: number): WeeklyAdjustmentRow[] {
  const date = weekDays.value[dayIndex]?.date;
  if (!date) return [];
  return weeklyRows.value.filter(r => dayjs(r.plannedStartAt).format('YYYY-MM-DD') === date);
}


function busyForDay(dayIndex: number): LessonOccurrenceItem[] {
  const date = weekDays.value[dayIndex]?.date;
  if (!date) return [];
  return weeklyBusyOccurrences.value.filter(o => dayjs(o.plannedStartAt).format('YYYY-MM-DD') === date);
}


function busyStyle(occ: LessonOccurrenceItem) {
  const start = dayjs(occ.plannedStartAt);
  const end = dayjs(occ.plannedEndAt);
  const startMin = start.hour() * 60 + start.minute();
  const endMin = end.hour() * 60 + end.minute();
  const top = ((startMin - CAL_START_HOUR * 60) / CAL_SLOT_MINUTES) * CAL_SLOT_HEIGHT;
  const height = Math.max(((endMin - startMin) / CAL_SLOT_MINUTES) * CAL_SLOT_HEIGHT, CAL_SLOT_HEIGHT);
  return { top: `${top}px`, height: `${height - 2}px` };
}

function busyLabel(occ: LessonOccurrenceItem): string {
  return occ.packageName || occ.className || 'Busy';
}


function eventStyle(row: WeeklyAdjustmentRow) {
  const start = dayjs(row.plannedStartAt);
  const end = dayjs(row.plannedEndAt);
  const startMin = start.hour() * 60 + start.minute();
  const endMin = end.hour() * 60 + end.minute();
  const top = ((startMin - CAL_START_HOUR * 60) / CAL_SLOT_MINUTES) * CAL_SLOT_HEIGHT;
  const height = Math.max(((endMin - startMin) / CAL_SLOT_MINUTES) * CAL_SLOT_HEIGHT, CAL_SLOT_HEIGHT);
  return { top: `${top}px`, height: `${height - 2}px` };
}

function eventTimeLabel(row: WeeklyAdjustmentRow): string {
  return `${dayjs(row.plannedStartAt).format('HH:mm')}–${dayjs(row.plannedEndAt).format('HH:mm')}`;
}

function eventTeacherLabel(row: WeeklyAdjustmentRow): string {
  if (row.markedForDelete) return 'Removed';
  const teacher = teachers.value.find(t => t.id === row.plannedTeacherId);
  return teacher?.teacherName ?? `${row.plannedHours}h`;
}

function selectRow(row: WeeklyAdjustmentRow) {
  selectedRowKey.value = row.rowKey;
}


const sortedWeeklyRows = computed(() =>
  [...weeklyRows.value].sort((a, b) => dayjs(a.plannedStartAt).valueOf() - dayjs(b.plannedStartAt).valueOf())
);


function rowDayLabel(row: WeeklyAdjustmentRow): string {
  const d = dayjs(row.plannedStartAt);
  return `${d.format('ddd')} ${d.format('M/D')}`;
}


function rowStatusTag(row: WeeklyAdjustmentRow): { label: string; type: 'success' | 'warning' | 'danger' } | null {
  if (row.rowType === 'NEW') return { label: 'Added', type: 'success' };
  if (row.markedForDelete) return { label: 'Removed', type: 'danger' };
  if (isWeeklyRowChanged(row)) return { label: 'Modified', type: 'warning' };
  return null;
}

// ─── Editor panel helpers ──────────────────
function rowDate(row: WeeklyAdjustmentRow): string {
  return dayjs(row.plannedStartAt).format('YYYY-MM-DD');
}

function rowStartTime(row: WeeklyAdjustmentRow): string {
  return dayjs(row.plannedStartAt).format('HH:mm');
}


function setRowDay(row: WeeklyAdjustmentRow, date: string) {
  const start = dayjs(row.plannedStartAt);
  const target = dayjs(`${date}T${start.format('HH:mm:ss')}`);
  row.plannedStartAt = target.format('YYYY-MM-DDTHH:mm:ss');
  row.plannedEndAt = target.add(row.plannedHours, 'hour').format('YYYY-MM-DDTHH:mm:ss');
}


function setRowStartTime(row: WeeklyAdjustmentRow, time: string) {
  const date = dayjs(row.plannedStartAt).format('YYYY-MM-DD');
  const target = dayjs(`${date}T${time}:00`);
  row.plannedStartAt = target.format('YYYY-MM-DDTHH:mm:ss');
  row.plannedEndAt = target.add(row.plannedHours, 'hour').format('YYYY-MM-DDTHH:mm:ss');
}


function setRowHours(row: WeeklyAdjustmentRow, hours: number) {
  const normalizedHours = normalizeHalfHourHours(hours);
  row.plannedHours = normalizedHours;
  row.plannedEndAt = dayjs(row.plannedStartAt).add(normalizedHours, 'hour').format('YYYY-MM-DDTHH:mm:ss');
}

function normalizeHalfHourHours(hours: number): number {
  if (!Number.isFinite(hours)) {
    return 0.5;
  }
  return Math.max(0.5, Math.round(hours * 2) / 2);
}

function isHalfHourIncrement(hours: number): boolean {
  return Number.isFinite(hours) && Math.abs(hours * 2 - Math.round(hours * 2)) < 0.0001;
}

// ─── Drag-to-create on the calendar ──────────────────
const dragging = ref(false);
const dragDayIndex = ref<number | null>(null);
const dragStartSlot = ref<number | null>(null);
const dragEndSlot = ref<number | null>(null);

function onCalMouseDown(dayIndex: number, slotIndex: number) {
  dragging.value = true;
  dragDayIndex.value = dayIndex;
  dragStartSlot.value = slotIndex;
  dragEndSlot.value = slotIndex;
}

function onCalMouseEnter(dayIndex: number, slotIndex: number) {
  if (!dragging.value || dragDayIndex.value !== dayIndex) return;
  dragEndSlot.value = slotIndex;
}


function isDragPreview(dayIndex: number, slotIndex: number): boolean {
  if (!dragging.value || dragDayIndex.value !== dayIndex) return false;
  const lo = Math.min(dragStartSlot.value!, dragEndSlot.value!);
  const hi = Math.max(dragStartSlot.value!, dragEndSlot.value!);
  return slotIndex >= lo && slotIndex <= hi;
}


function onCalMouseUp() {
  if (!dragging.value || dragDayIndex.value === null) {
    dragging.value = false;
    return;
  }
  const lo = Math.min(dragStartSlot.value!, dragEndSlot.value!);
  const hi = Math.max(dragStartSlot.value!, dragEndSlot.value!);
  const slotCount = hi - lo + 1;
  const hours = (slotCount * CAL_SLOT_MINUTES) / 60;
  const date = weekDays.value[dragDayIndex.value].date;
  const startMinutes = calendarRows.value[lo].minutes;
  const startAt = dayjs(`${date}T00:00:00`).add(startMinutes, 'minute');
  const row = addWeeklyNewRow(startAt, hours);
  selectedRowKey.value = row.rowKey;

  dragging.value = false;
  dragDayIndex.value = null;
  dragStartSlot.value = null;
  dragEndSlot.value = null;
}


onMounted(() => document.addEventListener('mouseup', onCalMouseUp));
onUnmounted(() => document.removeEventListener('mouseup', onCalMouseUp));

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


const originalWeeklyUnits = computed(() =>
  weeklyRows.value
    .filter(row => row.rowType === 'EXISTING')
    .reduce((sum, row) => sum + (row.original?.plannedUnits ?? 0), 0)
);


const adjustedWeeklyUnits = computed(() =>
  weeklyRows.value
    .filter(row => !(row.rowType === 'EXISTING' && row.markedForDelete))
    .reduce((sum, row) => sum + Math.round((row.plannedHours ?? 0) * 2), 0)
);

const unitDelta = computed(() => adjustedWeeklyUnits.value - originalWeeklyUnits.value);


const addedCount = computed(() =>
  weeklyRows.value.filter(row => row.rowType === 'NEW').length
);


const modifiedCount = computed(() =>
  weeklyRows.value.filter(
    row => row.rowType === 'EXISTING' && !row.markedForDelete && isWeeklyRowChanged(row)
  ).length
);


const removedCount = computed(() =>
  weeklyRows.value.filter(row => row.rowType === 'EXISTING' && row.markedForDelete).length
);


const hasWeeklyChanges = computed(
  () => addedCount.value > 0 || modifiedCount.value > 0 || removedCount.value > 0
);

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

  for (const occ of currentWeekOccurrences.value) {

    if (occ.planningStatus === 'CANCELLED') continue;

    const start = dayjs(occ.plannedStartAt);

    const weekday = start.day() === 0 ? 7 : start.day();
    const isCurrent = String(occ.packageId) === String(selectedPackageId.value);


    const baseName = occ.packageName || occ.className || 'Lesson';
    const hours = ((occ.plannedUnits ?? 0) * 0.5).toFixed(1);
    let courseLabel = `${baseName} ${hours}h`;
    if (occ.occurrenceType === 'ADHOC') {
      courseLabel += ' (temp)';
    } else if (occ.planningStatus === 'ADJUSTED') {
      courseLabel += ' (adjusted)';
    }

    result.push({
      weekday,
      startTime: start.format('HH:mm'),
      endTime: dayjs(occ.plannedEndAt).format('HH:mm'),
      courseLabel,
      teacherName: occ.plannedTeacherName,
      classroomName: occ.plannedClassroomName,
      isCurrent,
    });
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
  currentWeekOccurrences.value = [];
  loadingPackages.value = true;
  try {
    const res = await fetchCoursePackages({
      studentId: id,
      packageStatus: 'ACTIVE',
      teachingMode: 'ONE_ON_ONE',
      pageSize: 50,
    });
    studentPackages.value = res.data.data.records;
    await loadAllPackageRules();
  } catch {
    studentPackages.value = [];
  } finally {
    loadingPackages.value = false;
  }
  loadEnrolledClassGroups(id);
  loadCurrentWeekOccurrences(id);
}

// ─── Load this week's actual occurrences (main calendar) ─

async function loadCurrentWeekOccurrences(studentId: EntityId) {
  const { start, end } = currentNaturalWeekRange();
  try {
    const res = await fetchLessonOccurrences({
      studentId,
      startAt: start.format('YYYY-MM-DDT00:00:00'),
      endAt: end.format('YYYY-MM-DDT23:59:59'),
      pageSize: 200,
    });
    currentWeekOccurrences.value = res.data.data.records;
  } catch {
    currentWeekOccurrences.value = [];
  }
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
  const monday = dayjs().startOf('week').add(1, 'day');
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
  const teacherId = route.query.teacherId;
  const selectedTeacher = typeof teacherId === 'string'
    ? teachers.value.find((teacher) => String(teacher.id) === teacherId)
    : undefined;
  Object.assign(editForm, {
    primaryTeacherId: selectedTeacher?.id,
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
      if (selectedStudentId.value) await loadCurrentWeekOccurrences(selectedStudentId.value);
    } catch (err) {
      ElMessage.error(extractErrorMessage(err));
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
      if (selectedStudentId.value) await loadCurrentWeekOccurrences(selectedStudentId.value);
    } catch (err) {
      ElMessage.error(extractErrorMessage(err));
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

// ─── This week temporary adjustment ─────────────
async function openWeeklyAdjustmentDialog() {
  if (!selectedPackageId.value) return;
  weeklyReason.value = '';
  weeklyRows.value = [];
  weeklyBusyOccurrences.value = [];
  weeklyDialogVisible.value = true;
  weeklyLoading.value = true;
  try {
    const { start, end } = currentNaturalWeekRange();
    const startAt = start.format('YYYY-MM-DDT00:00:00');
    const endAt = end.format('YYYY-MM-DDT23:59:59');

    const [pkgRes, studentRes] = await Promise.all([
      fetchLessonOccurrences({ packageId: selectedPackageId.value, startAt, endAt, pageSize: 100 }),
      selectedStudentId.value
        ? fetchLessonOccurrences({ studentId: selectedStudentId.value, startAt, endAt, pageSize: 200 })
        : Promise.resolve(null),
    ]);
    weeklyRows.value = pkgRes.data.data.records
      .filter(canEditWeeklyOccurrence)
      .map(toWeeklyAdjustmentRow);

    weeklyBusyOccurrences.value = (studentRes?.data.data.records ?? [])
      .filter(o => String(o.packageId) !== String(selectedPackageId.value) && o.planningStatus !== 'CANCELLED');
  } catch (err) {
    weeklyRows.value = [];
    weeklyBusyOccurrences.value = [];
    ElMessage.error(extractErrorMessage(err));
  } finally {
    weeklyLoading.value = false;
  }
}

async function submitWeeklyAdjustment() {
  if (!selectedPackageId.value) return;
  const validationMessage = validateWeeklyRows();
  if (validationMessage) {
    ElMessage.error(validationMessage);
    return;
  }


  const items = weeklyRows.value
    .map(row => buildWeeklyItem(row))
    .filter((item): item is WeeklyScheduleAdjustmentItemPayload => item !== null);

  if (items.length === 0) {
    ElMessage.warning('No changes to save.');
    return;
  }

  try {
    await ElMessageBox.confirm(weeklyImpactMessage.value, 'This Week Adjustment Impact', {
      confirmButtonText: 'Save This Week',
      cancelButtonText: 'Cancel',
      type: unitDelta.value === 0 ? 'info' : 'warning',
      dangerouslyUseHTMLString: true,
    });
  } catch {
    return;
  }

  submitting.value = true;
  try {
    await adjustWeeklySchedule({
      packageId: selectedPackageId.value,
      weekStartDate: currentNaturalWeekRange().start.format('YYYY-MM-DD'),
      reason: weeklyReason.value || undefined,
      items,
    });
    ElMessage.success('This week adjustment saved.');
    weeklyDialogVisible.value = false;
    await loadAllPackageRules();

    if (selectedStudentId.value) await loadCurrentWeekOccurrences(selectedStudentId.value);
  } catch (err) {
    ElMessage.error(extractErrorMessage(err));
  } finally {
    submitting.value = false;
  }
}


function buildWeeklyItem(row: WeeklyAdjustmentRow): WeeklyScheduleAdjustmentItemPayload | null {

  if (row.rowType === 'EXISTING' && row.markedForDelete) {
    return {
      action: 'DELETE',
      occurrenceId: row.occurrenceId,
      itemReason: row.itemReason.trim(),
    };
  }

  if (row.rowType === 'NEW') {
    return {
      action: 'CREATE',
      plannedStartAt: row.plannedStartAt,
      plannedEndAt: row.plannedEndAt,
      plannedUnits: Math.round(row.plannedHours * 2),
      plannedTeacherId: normalizeOptionalId(row.plannedTeacherId),
      plannedClassroomId: normalizeOptionalId(row.plannedClassroomId),
      itemReason: row.itemReason.trim(),
    };
  }

  if (!isWeeklyRowChanged(row)) return null;
  return {
    action: 'UPDATE',
    occurrenceId: row.occurrenceId,
    plannedStartAt: row.plannedStartAt,
    plannedEndAt: row.plannedEndAt,
    plannedUnits: Math.round(row.plannedHours * 2),
    plannedTeacherId: normalizeOptionalId(row.plannedTeacherId),
    plannedClassroomId: normalizeOptionalId(row.plannedClassroomId),
  };
}

const weeklyImpactMessage = computed(() => {

  const summary = `Added ${addedCount.value}, modified ${modifiedCount.value}, removed ${removedCount.value} lesson(s).`;
  let impact: string;
  if (unitDelta.value > 0) {
    impact = 'This week will use more package hours. The package balance may run out earlier and the estimated end date may move up.';
  } else if (unitDelta.value < 0) {
    impact = 'This week will use fewer package hours. The student may need makeup lessons and the estimated end date may be extended.';
  } else {
    impact = 'This week timing changes only. The recurring weekly schedule and package consumption pace stay unchanged.';
  }
  return `${summary}<br/><br/>${impact}`;
});

function currentNaturalWeekRange() {

  const today = dayjs().startOf('day');
  const daysFromSaturday = (today.day() + 1) % 7;
  const start = today.subtract(daysFromSaturday, 'day');
  return { start, end: start.add(6, 'day') };
}

function canEditWeeklyOccurrence(occ: LessonOccurrenceItem): boolean {
  return occ.planningStatus !== 'CANCELLED' && !occ.resultStatus;
}

function toWeeklyAdjustmentRow(occ: LessonOccurrenceItem): WeeklyAdjustmentRow {
  return {
    rowKey: `existing-${occ.id}`,
    rowType: 'EXISTING',
    occurrenceId: occ.id,
    lessonNo: occ.lessonNo,
    plannedStartAt: occ.plannedStartAt,
    plannedEndAt: occ.plannedEndAt,
    plannedHours: (occ.plannedUnits ?? 0) * 0.5,
    plannedTeacherId: occ.plannedTeacherId,
    plannedClassroomId: occ.plannedClassroomId,
    markedForDelete: false,
    itemReason: '',
    original: occ,
  };
}


let newRowSeq = 0;


function addWeeklyNewRow(startAt?: dayjs.Dayjs, hours = 1): WeeklyAdjustmentRow {
  const base = startAt ?? currentNaturalWeekRange().start.hour(9).minute(0).second(0);
  const row: WeeklyAdjustmentRow = {
    rowKey: `new-${++newRowSeq}`,
    rowType: 'NEW',
    lessonNo: 'New lesson',
    plannedStartAt: base.format('YYYY-MM-DDTHH:mm:ss'),
    plannedEndAt: base.add(hours, 'hour').format('YYYY-MM-DDTHH:mm:ss'),
    plannedHours: hours,
    plannedTeacherId: currentRule.value?.primaryTeacherId,
    plannedClassroomId: currentRule.value?.defaultClassroomId,
    markedForDelete: false,
    itemReason: '',
  };
  weeklyRows.value.push(row);
  return row;
}


function toggleWeeklyRowDelete(row: WeeklyAdjustmentRow) {
  row.markedForDelete = !row.markedForDelete;
}


function removeWeeklyNewRow(row: WeeklyAdjustmentRow) {
  weeklyRows.value = weeklyRows.value.filter(item => item.rowKey !== row.rowKey);
  if (selectedRowKey.value === row.rowKey) selectedRowKey.value = null;
}

function validateWeeklyRows(): string {
  const { start, end } = currentNaturalWeekRange();
  for (const row of weeklyRows.value) {

    if (row.rowType === 'EXISTING' && row.markedForDelete) {
      continue;
    }
    const startAt = dayjs(row.plannedStartAt);
    const endAt = dayjs(row.plannedEndAt);
    if (!startAt.isValid() || !endAt.isValid()) {
      return 'Please provide valid start and end times.';
    }
    if (startAt.isBefore(start) || startAt.isAfter(end.endOf('day'))) {
      return 'Adjusted lessons must stay in the current natural week.';
    }
    if (!endAt.isAfter(startAt)) {
      return 'End time must be later than start time.';
    }
    if (Math.round(row.plannedHours * 2) < 1) {
      return 'Lesson hours must be at least 0.5h.';
    }
    if (!isHalfHourIncrement(row.plannedHours)) {
      return 'Lesson hours must be in 0.5h increments.';
    }
    const minutes = endAt.diff(startAt, 'minute');
    if (minutes !== Math.round(row.plannedHours * 2) * 30) {
      return 'Lesson duration must match the selected hours.';
    }
  }
  return '';
}

function isWeeklyRowChanged(row: WeeklyAdjustmentRow): boolean {

  if (row.rowType === 'NEW') return true;
  const original = row.original;
  if (!original) return false;
  return row.plannedStartAt !== original.plannedStartAt
    || row.plannedEndAt !== original.plannedEndAt
    || Math.round(row.plannedHours * 2) !== original.plannedUnits
    || normalizeOptionalId(row.plannedTeacherId) !== normalizeOptionalId(original.plannedTeacherId)
    || normalizeOptionalId(row.plannedClassroomId) !== normalizeOptionalId(original.plannedClassroomId);
}

function normalizeOptionalId(id?: EntityId): EntityId | undefined {
  return id === '' || id == null ? undefined : id;
}

function formatUnitsAsHours(units: number): string {
  return `${(units * 0.5).toFixed(1)}h`;
}


function formatSignedUnitsAsHours(units: number): string {
  const sign = units > 0 ? '+' : '';
  return `${sign}${(units * 0.5).toFixed(1)}h`;
}

function formatDate(value?: string): string {
  return value ? dayjs(value).format('MMM D') : '—';
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
    await applyRouteScheduleContext();
  } catch { /* silent */ }
}

async function applyRouteScheduleContext() {
  const studentId = route.query.studentId;
  const packageId = route.query.packageId;
  if (typeof studentId !== 'string' || typeof packageId !== 'string') {
    return;
  }
  const student = students.value.find((item) => String(item.id) === studentId);
  if (!student) {
    ElMessage.warning('The new student record could not be found for scheduling.');
    return;
  }
  await onStudentSelect(student.id);
  const selectedPackage = studentPackages.value.find((item) => String(item.id) === packageId);
  if (!selectedPackage) {
    ElMessage.warning('The new course package could not be found for scheduling.');
    return;
  }
  onPackageSelect(selectedPackage);
  if (route.query.startSchedule === '1' && !currentRule.value) {
    await openCreateDialog();
  }
}

void init();

// ─── Date helpers ──────────────────────────────────────────
function disabledPastDate(date: Date) {
  return dayjs(date).startOf('day').isBefore(dayjs().startOf('day'));
}
</script>

<style scoped>
.schedule-page {
  gap: 10px;
  height: calc(100vh - 120px);
  min-height: 0;
  overflow: hidden;
}

.schedule-mode-tabs {
  display: inline-flex;
  align-self: flex-start;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  flex-shrink: 0;
}

.schedule-mode-tab {
  min-width: 120px;
  padding: 9px 18px;
  border: 0;
  border-right: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.schedule-mode-tab:last-child {
  border-right: 0;
}

.schedule-mode-tab--active {
  background: #409eff;
  color: #fff;
}

.schedule-class-adjustments {
  flex: 1;
  min-height: 0;
}

.schedule-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
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

/* This week adjustment */
.weekly-adjustment {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.weekly-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.weekly-summary__item {
  padding: 10px 12px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fafbfc;
}

.weekly-summary__item span {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #909399;
}

.weekly-summary__help {
  font-size: 13px;
  color: #c0c4cc;
  cursor: help;
}

.weekly-summary__item strong {
  font-size: 18px;
  color: #303133;
}

.weekly-summary__item--changed {
  border-color: #e6a23c;
  background: #fdf6ec;
}

/* ── Weekly calendar (Sat→Fri) ─────────────────── */
.weekly-cal__hint {
  font-size: 12px;
  color: #909399;
}

.weekly-cal {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
  user-select: none;
}


.weekly-cal__head {
  display: grid;
  grid-template-columns: 56px repeat(7, 1fr);
  background: #f5f7fa;
  border-bottom: 1px solid #e8edf2;
}

.weekly-cal__corner {
  border-right: 1px solid #e8edf2;
}

.weekly-cal__day {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 6px 0;
  border-right: 1px solid #eef1f5;
}

.weekly-cal__day:last-child {
  border-right: none;
}

.weekly-cal__day--today {
  background: #ecf5ff;
}

.weekly-cal__day-name {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
}

.weekly-cal__day-date {
  font-size: 11px;
  color: #909399;
}

.weekly-cal__body {
  display: grid;
  grid-template-columns: 56px repeat(7, 1fr);
  max-height: 420px;
  overflow-y: auto;
}


.weekly-cal__times {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e8edf2;
}

.weekly-cal__time {
  height: 28px;
  font-size: 11px;
  color: #909399;
  text-align: right;
  padding-right: 6px;
  transform: translateY(-6px);
}


.weekly-cal__col {
  position: relative;
  border-right: 1px solid #eef1f5;
}

.weekly-cal__col:last-child {
  border-right: none;
}

.weekly-cal__cell {
  height: 28px;
  border-bottom: 1px solid #f5f7fa;
  cursor: pointer;
}

.weekly-cal__cell--preview {
  background: #d9ecff;
}


.weekly-cal__busy {
  position: absolute;
  left: 2px;
  right: 2px;
  border-radius: 4px;
  padding: 2px 4px;
  font-size: 11px;
  line-height: 1.3;
  overflow: hidden;
  background: repeating-linear-gradient(
    45deg,
    #f0f2f5,
    #f0f2f5 6px,
    #e6e9ee 6px,
    #e6e9ee 12px
  );
  border: 1px dashed #c0c4cc;
  color: #909399;
  cursor: not-allowed;
  z-index: 1;
}

.weekly-cal__busy-label {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}


.weekly-cal__event {
  position: absolute;
  left: 2px;
  right: 2px;
  border-radius: 4px;
  padding: 2px 4px;
  font-size: 11px;
  line-height: 1.3;
  overflow: hidden;
  cursor: pointer;
  background: #ecf5ff;
  border: 1px solid #409eff;
  color: #303133;
}

.weekly-cal__event-time {
  display: block;
  font-weight: 600;
}

.weekly-cal__event-meta {
  display: block;
  color: #606266;
}


.weekly-cal__event--new {
  background: #f0f9eb;
  border-color: #67c23a;
}


.weekly-cal__event--modified {
  background: #fdf6ec;
  border-color: #e6a23c;
}


.weekly-cal__event--deleted {
  background: #f4f4f5;
  border-color: #c0c4cc;
  opacity: 0.7;
}

.weekly-cal__event--deleted .weekly-cal__event-time {
  text-decoration: line-through;
}


.weekly-cal__event--active {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.5);
  z-index: 2;
}

/* ── Weekly lessons list ─────────────────── */
.weekly-list {
  display: flex;
  flex-direction: column;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
}

.weekly-list__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-top: 1px solid #eef1f5;
  cursor: pointer;
  font-size: 13px;
}

.weekly-list__item:first-child {
  border-top: none;
}

.weekly-list__item:hover {
  background: #f5f7fa;
}

.weekly-list__when {
  font-weight: 600;
  color: #303133;
  min-width: 150px;
}

.weekly-list__meta {
  flex: 1;
  color: #606266;
}


.weekly-list__item--active {
  background: #ecf5ff;
}


.weekly-list__item--deleted .weekly-list__when {
  text-decoration: line-through;
  color: #909399;
}

/* ── Weekly editor panel ─────────────────── */
.weekly-editor {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  padding: 12px 14px;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.weekly-editor--deleted {
  background: #fef0f0;
}

.weekly-editor__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #303133;
}

.weekly-editor__grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.weekly-editor__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.weekly-editor__field label {
  font-size: 12px;
  color: #909399;
}

.weekly-editor__hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.weekly-editor__field :deep(.el-select),
.weekly-editor__field :deep(.el-input-number),
.weekly-editor__field :deep(.el-date-editor) {
  width: 100%;
}

.weekly-editor__actions {
  display: flex;
  justify-content: flex-end;
}

.weekly-reason {
  margin-top: 4px;
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
