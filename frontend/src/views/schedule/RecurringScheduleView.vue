<template>
  <div class="page-stack schedule-page">
    <section class="schedule-panel">
      <div class="schedule-panel__header">
        <span class="schedule-panel__title">Schedule Private Lessons</span>
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
            <div v-if="studentListLoading && students.length === 0" class="col-panel__empty">Loading...</div>
            <div v-else-if="studentListError && students.length === 0" class="col-panel__empty">
              Could not load students. <el-button link type="primary" @click="refreshScheduleStudentList">Retry</el-button>
            </div>
            <template v-else>
              <section v-for="section in studentSections" :key="section.key" class="schedule-student-section">
                <div class="schedule-student-section-title">{{ section.title }}</div>
                <div v-if="section.groups.length === 0" class="schedule-student-section__empty">
                  {{ section.key === 'pending' ? '暂无待排课课程' : section.key === 'scheduled' ? '暂无已排课课程' : '暂无停课课程' }}
                </div>
                <template v-for="group in section.groups" :key="`${section.key}-${group.key}`">
                  <button
                    type="button"
                    class="schedule-student-group__toggle"
                    :class="{ 'schedule-student-group__toggle--collapsed': collapsedCourseGradeKeys.has(`${section.key}:${group.key}`) }"
                    :aria-expanded="!collapsedCourseGradeKeys.has(`${section.key}:${group.key}`)"
                    @click="toggleCourseGradeGroup(`${section.key}:${group.key}`)"
                  >
                    <span class="schedule-student-group__title">
                      <strong>{{ group.courseName }}</strong>
                      <span>{{ group.gradeBand }}</span>
                    </span>
                    <small>{{ group.students.length }}</small>
                    <el-icon><ArrowRight /></el-icon>
                  </button>
                  <div v-if="!collapsedCourseGradeKeys.has(`${section.key}:${group.key}`)" class="schedule-student-group__students">
                    <button
                      v-for="entry in group.students"
                      :key="`${entry.student.id}-${entry.course.packageId ?? entry.course.privateLessonGroupId ?? entry.course.courseName}`"
                      type="button"
                      class="col-item"
                      :class="{ 'col-item--active': String(selectedPackageId) === String(entry.course.packageId) }"
                      :title="entry.course.participantNames || entry.student.studentName"
                      @click="onScheduleCourseSelect(entry)"
                    >
                      <span class="col-item__name">{{ entry.course.participantNames || entry.student.studentName }}</span>
                      <el-icon v-if="String(selectedPackageId) === String(entry.course.packageId)" color="#409eff"><ArrowRight /></el-icon>
                    </button>
                  </div>
                </template>
              </section>
            </template>
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
              No active or paused packages for this student.
            </div>
            <template v-else>
              <div
                v-for="pkg in studentPackages"
                :key="pkg.id"
                class="col-item"
                :class="{ 'col-item--active': selectedPackageId === pkg.id }"
                @click="onPackageSelect(pkg)"
              >
                <div class="col-item__stack">
                  <span class="col-item__name">{{ pkg.packageName }}</span>
                  <span class="col-item__sub">{{ pkg.packageStatus }} · {{ projectedRemainingForPackage(pkg) }}h remaining</span>
                </div>
                <el-icon v-if="selectedPackageId === pkg.id" color="#409eff"><ArrowRight /></el-icon>
              </div>
            </template>
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
                    <el-button v-if="selectedPackage?.packageStatus === 'ACTIVE'" size="small" @click="packageHoursDialogVisible = true">
                      Package hours
                    </el-button>
                    <el-dropdown trigger="click" :disabled="!selectedStudentId || !selectedPackageId" @command="openPrintSheet">
                      <el-button size="small">Print Sheet</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="parent">Student sheet</el-dropdown-item>
                          <el-dropdown-item command="teacher">Teacher sheets</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                    <el-button
                      v-if="selectedPackage?.packageStatus === 'ACTIVE' && currentRule"
                      type="primary"
                      size="small"
                      @click="openEditDialog"
                    >
                      Edit Schedule
                    </el-button>
                    <el-button
                      v-if="selectedPackage?.packageStatus === 'ACTIVE' && currentRule"
                      type="warning"
                      size="small"
                      plain
                      @click="openWeeklyAdjustmentDialog"
                    >
                      Adjust This Week
                    </el-button>
                    <el-button
                      v-else-if="selectedPackage?.packageStatus === 'ACTIVE'"
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
                    <el-button
                      v-if="selectedPackage?.packageStatus === 'ACTIVE'"
                      type="danger"
                      plain
                      size="small"
                      :loading="pausingPackageId === selectedPackageId"
                      :disabled="pausingPackageId !== null"
                      @click="pauseSelectedPackage"
                    >
                      Pause Course
                    </el-button>
                    <el-button
                      v-if="selectedPackage?.packageStatus === 'PAUSED'"
                      type="success"
                      size="small"
                      :loading="resumingPackageId === selectedPackageId"
                      :disabled="resumingPackageId !== null"
                      @click="resumeSelectedPackage"
                    >
                      Resume Course
                    </el-button>
                  </div>
                </div>
                <div v-if="isPrivateGroupCourseSelected" class="schedule-info__participants">
                  Students: {{ selectedStudentName }} · One shared schedule
                </div>

                <!-- Schedule summary -->
                <div v-if="currentRule" class="schedule-summary">
                  <div class="summary-field">
                    <label>Slot Teachers</label>
                    <span>{{ slotTeacherNames(currentRule) }}</span>
                  </div>
                  <div class="summary-field">
                    <label>Classrooms</label>
                    <span>{{ slotClassroomNames(currentRule) }}</span>
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
                  <span>{{ selectedPackage?.packageStatus === 'PAUSED'
                    ? 'Course paused. Resume it to create a new schedule; completed lessons remain and cancelled future lessons stay cancelled.'
                    : 'No schedule yet for this package.' }}</span>
                </div>
              </div>

              <!-- Weekly calendar -->
              <div v-if="currentPackageOccupiedSlots.length > 0 || currentRule" class="schedule-calendar">
                <div class="schedule-calendar__label">Weekly Schedule</div>
                <WeeklyTimeSelector
                  v-model="emptySlots"
                  :occupied-slots="currentPackageOccupiedSlots"
                  :week-start-date="displayMonday"
                  :readonly="true"
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

    <!-- Package hours are available on demand so the schedule stays focused. -->
    <el-dialog
      v-model="packageHoursDialogVisible"
      :title="`Package hours — ${selectedPackageName}`"
      width="640px"
      :close-on-click-modal="false"
      class="schedule-dialog package-hours-dialog"
    >
      <section v-if="selectedPackage" class="package-hours-card">
        <div class="package-hours-card__heading">
          <div>
            <div class="package-hours-card__eyebrow">PACKAGE HOURS</div>
            <strong>Hours overview</strong>
          </div>
          <el-button type="primary" size="small" :loading="savingDeductedHours" @click="saveDeductedHours">
            Save hours
          </el-button>
        </div>
        <div class="package-hours-card__fields">
          <div><span>Total hours</span><strong>{{ packageHours(selectedPackage.totalUnits) }}h</strong></div>
          <label class="package-hours-card__input">
            <span>Already deducted</span>
            <el-input-number v-model="deductedHoursDraft" :min="confirmedDeductionHours" :max="deductedHoursMax" :step="0.5" :precision="1" controls-position="right" />
          </label>
          <div><span>Pending deduction</span><strong>{{ pendingDeductionHours }}h</strong></div>
          <div><span>Remaining</span><strong>{{ projectedRemainingHours }}h</strong></div>
        </div>
        <p class="package-hours-card__hint">Already deducted includes confirmed lessons. Pending deduction includes teacher-submitted lessons awaiting admin review. Remaining excludes both.</p>
      </section>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="packageHoursDialogVisible = false">Close</el-button>
        </div>
      </template>
    </el-dialog>

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
              <label>{{ isPrivateGroupCourseSelected ? 'Students' : 'Student' }}</label>
              <span class="edit-info-value">{{ selectedStudentName }}</span>
            </div>
          </div>
        </div>

        <el-form ref="editFormRef" :model="editForm" :rules="editFormRules" label-width="130px">
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
          Click and drag on the calendar to select class time slots. For each slot, assign a teacher,
          lesson name, and the total hours allocated to it. Each slot is scheduled independently until
          its allocated hours run out (the last class takes the remainder if it does not divide evenly).
          Other lessons for every student are shown for the week of the start date. Saving checks the full schedule.
        </p>
        <el-alert v-if="editBusyError" :title="editBusyError" type="error" :closable="false" show-icon />
        <div v-if="editBusyLoading" class="edit-schedule__hint">Loading student availability...</div>
        <WeeklyTimeSelector
          v-model="editForm.slots"
          :week-start-date="editWeekMonday"
          :occupied-slots="otherPackageOccupiedSlots"
          :readonly="false"
        />
        <div v-if="editForm.slots.length > 0" class="slot-teacher-list">
          <div
            v-for="(slot, index) in editForm.slots"
            :key="`${slot.weekday}-${slot.startTime}-${index}`"
            class="slot-teacher-row"
          >
            <div class="slot-teacher-row__time">
              <strong>{{ scheduleSlotLabel(slot) }}</strong>
              <small>Each lesson {{ formatSlotHours(slot.plannedUnits) }} · {{ slotLessonPlan(slot) }}</small>
            </div>
            <el-select
              v-model="slot.teacherId"
              filterable
              placeholder="Select teacher"
            >
              <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
            </el-select>
            <el-select
              v-model="slot.classroomSelection"
              popper-class="classroom-campus-picker"
              filterable
              placeholder="Select classroom or Online"
            >
              <el-option label="Online" value="ONLINE" />
              <el-option-group
                v-for="group in classroomOptionGroups"
                :key="group.campusKey"
                :label="group.campusName"
                class="classroom-campus-group"
              >
                <el-option
                  v-for="c in group.classrooms"
                  :key="c.id"
                  :label="classroomSelectLabel(c)"
                  :value="`ROOM:${c.id}`"
                >
                  <div class="classroom-select-option"><span>{{ c.roomName }}</span></div>
                </el-option>
              </el-option-group>
            </el-select>
            <el-input
              v-model="slot.subjectName"
              placeholder="Lesson name"
              maxlength="128"
            />
            <el-input-number
              v-model="slot.allocatedHours"
              :min="0.5"
              :step="0.5"
              :precision="1"
              controls-position="right"
              placeholder="Hours"
            />
            <span class="slot-teacher-row__unit">h</span>
          </div>
        </div>
        <div v-if="editForm.slots.length > 0" class="edit-schedule__summary">
          {{ editForm.slots.length }} slot(s) · {{ (totalSlotUnits * 0.5).toFixed(1) }}h scheduled each week
          <span
            class="edit-schedule__allocation"
            :class="{ 'edit-schedule__allocation--over': allocationExceedsRemaining }"
          >
            · Available {{ allocatablePackageHours.toFixed(1) }}h · Allocated {{ allocatedTotalHours.toFixed(1) }}h
            ({{ remainingAllocationHours >= 0 ? 'unallocated' : 'over by' }}
            {{ Math.abs(remainingAllocationHours).toFixed(1) }}h)
          </span>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting || editBusyLoading" :disabled="Boolean(editBusyError)" @click="submitForm">
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
        <div v-if="isPrivateGroupCourseSelected" class="edit-schedule__hint">Students: {{ selectedStudentName }} · Changes apply to their shared lesson.</div>

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
          Drag on an empty area to add a lesson. Click a lesson block to edit, move, or remove it. Striped grey blocks are other lessons for any student in this course.
        </div>
        <details v-if="weeklyBusyOccurrences.length" class="weekly-busy-list">
          <summary>Other student lessons this week ({{ weeklyBusyOccurrences.length }})</summary>
          <div class="weekly-busy-list__items">
            <div v-for="busy in weeklyBusyOccurrences" :key="busy.id">
              {{ busy.busyStudentNames.join(' / ') }} · {{ dayjs(busy.plannedStartAt).format('ddd M/D HH:mm') }}–{{ dayjs(busy.plannedEndAt).format('HH:mm') }} · {{ busy.packageName || busy.className || 'Lesson' }}
            </div>
          </div>
        </details>


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
                :title="busyLabel(busy)"
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
                start="07:30"
                end="22:30"
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
              <el-select v-model="selectedRow.plannedClassroomId" popper-class="classroom-campus-picker" clearable filterable placeholder="Classroom" :disabled="selectedRow.markedForDelete">
                <el-option-group
                  v-for="group in classroomOptionGroups"
                  :key="group.campusKey"
                  :label="group.campusName"
                  class="classroom-campus-group"
                >
                  <el-option
                    v-for="c in group.classrooms"
                    :key="c.id"
                    :label="classroomSelectLabel(c)"
                    :value="c.id"
                  >
                    <div class="classroom-select-option">
                      <span>{{ c.roomName }}</span>
                    </div>
                  </el-option>
                </el-option-group>
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
          <el-button type="primary" :loading="submitting || weeklyLoading" :disabled="!hasWeeklyChanges || Boolean(weeklyBusyError)" @click="submitWeeklyAdjustment">
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
              <label>{{ isPrivateGroupCourseSelected ? 'Students' : 'Student' }}</label>
              <span>{{ selectedStudentName || detailData.studentName }}</span>
            </div>
            <div class="detail-field">
              <label>Slot Teachers</label>
              <span>{{ slotTeacherNames(detailData) }}</span>
            </div>
            <div class="detail-field">
              <label>Classrooms</label>
              <span>{{ slotClassroomNames(detailData) }}</span>
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

    <PauseConfirmationDialog
      v-model="pauseConfirmationVisible"
      title="Pause Course"
      message="Pausing this course cancels its future lessons that have not been recorded. After resuming, you must create a new schedule to continue lessons."
      confirm-text="Pause Course"
      :loading="pausingPackageId !== null"
      @confirm="confirmPauseSelectedPackage"
    />
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, Calendar, Search, QuestionFilled, Delete, RefreshLeft, Close, Loading } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import dayjs from 'dayjs';
import { useRoute } from 'vue-router';

import { extractErrorMessage } from '@/api/http';
import { fetchAllClassrooms, fetchAllTeachers } from '@/api/base-data';
import { fetchCoursePackage, fetchCoursePackages, pauseCoursePackage, resumeCoursePackage, updateCoursePackageDeductedHours } from '@/api/enrollment';
import PauseConfirmationDialog from '@/components/shared/PauseConfirmationDialog.vue';
import { fetchOneOnOneDeductions } from '@/api/one-on-one';
import {
  createRecurringScheduleRule,
  updateRecurringScheduleRule,
  fetchRecurringScheduleRule,
  fetchRecurringScheduleRules,
  fetchOneOnOneScheduleStudents,
  fetchLessonOccurrences,
  adjustWeeklySchedule,
} from '@/api/schedule';
import type { LessonOccurrenceQuery, ScheduleStudentOption, WeeklyScheduleAdjustmentItemPayload } from '@/api/schedule';
import type { ClassroomItem, CoursePackageItem, EntityId, LessonOccurrenceItem, RecurringScheduleRuleItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import { APP_TIMEZONE } from '@/config/app-config';
import { calendarWeekRange } from '@/utils/calendar-week';
import { renderSignatureSheetDocument } from '@/utils/signature-sheet-print';

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
let packageLoadSequence = 0;
const route = useRoute();
const editDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const weeklyDialogVisible = ref(false);
const weeklyLoading = ref(false);
const submitting = ref(false);
const isEditing = ref(false);
const editingRuleId = ref<EntityId | null>(null);
const editFormRef = ref<FormInstance>();

const detailData = ref<any>(null);
const students = ref<ScheduleStudentOption[]>([]);
const studentListLoading = ref(true);
const studentListError = ref(false);
interface ScheduleCourseGradeGroup {
  key: string;
  courseName: string;
  gradeBand: string;
  students: ScheduleSidebarEntry[];
}
interface ScheduleSidebarEntry {
  student: ScheduleStudentOption;
  course: ScheduleStudentOption['courseGrades'][number];
}
interface ScheduleParticipant {
  id: EntityId;
  name: string;
}
interface BusyOccurrence extends LessonOccurrenceItem {
  busyStudentNames: string[];
}
const collapsedCourseGradeKeys = ref(new Set<string>());
const studentPackages = ref<CoursePackageItem[]>([]);
const teachers = ref<{ id: EntityId; teacherName: string }[]>([]);
const classrooms = ref<ClassroomItem[]>([]);
const editBusyOccurrences = ref<BusyOccurrence[]>([]);
const editBusyLoading = ref(false);
const editBusyError = ref('');
let editBusyLoadSequence = 0;

interface ClassroomOptionGroup {
  campusKey: string;
  campusName: string;
  classrooms: ClassroomItem[];
}

// Edit Weekly Schedule 的两个教室下拉共用这里的分组，保证用户选择时能直接看到校区。
const classroomOptionGroups = computed<ClassroomOptionGroup[]>(() => {
  const groups = new Map<string, ClassroomOptionGroup>();

  classrooms.value.forEach((classroom) => {
    const campusName = classroom.campus?.trim() || 'Unassigned campus';
    const campusKey = String(classroom.campusId ?? campusName);
    const existing = groups.get(campusKey);

    if (existing) {
      existing.classrooms.push(classroom);
      return;
    }

    groups.set(campusKey, {
      campusKey,
      campusName,
      classrooms: [classroom],
    });
  });

  return [...groups.values()]
    .map(group => ({
      ...group,
      classrooms: [...group.classrooms].sort((a, b) => a.roomName.localeCompare(b.roomName)),
    }))
    .sort((a, b) => a.campusName.localeCompare(b.campusName));
});

function classroomSelectLabel(classroom: ClassroomItem) {
  const campusName = classroom.campus?.trim();
  return campusName ? `${classroom.roomName} · ${campusName}` : classroom.roomName;
}

function slotTeacherNames(rule: { slots?: Array<{ teacherId?: EntityId }> }): string {
  const teacherNames = (rule.slots ?? [])
    .map(slot => teachers.value.find(teacher => String(teacher.id) === String(slot.teacherId))?.teacherName)
    .filter((name): name is string => Boolean(name));
  return [...new Set(teacherNames)].join(', ') || '—';
}

function slotClassroomNames(rule: {
  slots?: Array<{ deliveryMode?: string; classroomId?: EntityId }>;
  defaultClassroomId?: EntityId;
  defaultClassroomName?: string;
}): string {
  const names = (rule.slots ?? []).map(slot => {
    if (slot.deliveryMode === 'ONLINE') return 'Online';
    const classroomId = slot.classroomId ?? rule.defaultClassroomId;
    if (classroomId == null) return null;
    return classrooms.value.find(classroom => String(classroom.id) === String(classroomId))?.roomName
      ?? (String(classroomId) === String(rule.defaultClassroomId) ? rule.defaultClassroomName : null)
      ?? 'Classroom';
  }).filter((name): name is string => Boolean(name));
  return [...new Set(names)].join(' · ') || '—';
}

// ─── All package rules (preloaded per student) ──
const allPackageRules = ref<Record<string, any>>({});



const currentWeekOccurrences = ref<LessonOccurrenceItem[]>([]);

// ─── Selection state ────────────────────────────
const studentSearch = ref('');
const selectedStudentId = ref<EntityId | null>(null);
const selectedPackageId = ref<EntityId | null>(null);
const pausingPackageId = ref<EntityId | null>(null);
const pauseConfirmationVisible = ref(false);
const resumingPackageId = ref<EntityId | null>(null);
const packageHoursDialogVisible = ref(false);
const deductedHoursDraft = ref(0);
const savingDeductedHours = ref(false);
const selectedPackage = computed(() => studentPackages.value.find(p => String(p.id) === String(selectedPackageId.value)) ?? null);
const packageHours = (hours?: number) => Number((hours ?? 0).toFixed(1));
const confirmedDeductionHours = computed(() => {
  const pkg = selectedPackage.value;
  return packageHours(Math.max(0, (pkg?.consumedUnits ?? 0) - (pkg?.openingUsedHours ?? 0)));
});
const pendingDeductionHours = computed(() => packageHours(selectedPackage.value?.pendingDeductionUnits));
const deductedHoursMax = computed(() => Math.max(0,
  packageHours(selectedPackage.value?.totalUnits) - pendingDeductionHours.value,
));
const projectedRemainingHours = computed(() => Number(Math.max(0,
  packageHours(selectedPackage.value?.totalUnits) - deductedHoursDraft.value - pendingDeductionHours.value,
).toFixed(1)));
watch(selectedPackage, pkg => {
  deductedHoursDraft.value = pkg?.consumedUnits ?? 0;
}, { immediate: true });
function projectedRemainingForPackage(pkg: CoursePackageItem): number {
  return packageHours(Math.max(0, pkg.totalUnits - pkg.consumedUnits - (pkg.pendingDeductionUnits ?? 0)));
}
const emptySlots = ref<SlotDef[]>([]);
const weeklyRows = ref<WeeklyAdjustmentRow[]>([]);
const weeklyReason = ref('');

// ─── Weekly adjustment calendar (Sat→Fri) ──────────────────

const CAL_START_MINUTES = 7 * 60 + 30;
const CAL_END_MINUTES = 23 * 60;
const CAL_SLOT_MINUTES = 30;
const CAL_SLOT_HEIGHT = 28;

const selectedRowKey = ref<string | null>(null);


const weeklyBusyOccurrences = ref<BusyOccurrence[]>([]);
const weeklyBusyError = ref('');


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
  for (let minutes = CAL_START_MINUTES; minutes < CAL_END_MINUTES; minutes += CAL_SLOT_MINUTES) {
    const hour = Math.floor(minutes / 60);
    const minute = minutes % 60;
    rows.push({
      label: `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`,
      minutes,
    });
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


function busyForDay(dayIndex: number): BusyOccurrence[] {
  const date = weekDays.value[dayIndex]?.date;
  if (!date) return [];
  return weeklyBusyOccurrences.value.filter(o => dayjs(o.plannedStartAt).format('YYYY-MM-DD') === date);
}


function busyStyle(occ: LessonOccurrenceItem) {
  const start = dayjs(occ.plannedStartAt);
  const end = dayjs(occ.plannedEndAt);
  const startMin = start.hour() * 60 + start.minute();
  const endMin = end.hour() * 60 + end.minute();
  const top = ((startMin - CAL_START_MINUTES) / CAL_SLOT_MINUTES) * CAL_SLOT_HEIGHT;
  const height = Math.max(((endMin - startMin) / CAL_SLOT_MINUTES) * CAL_SLOT_HEIGHT, CAL_SLOT_HEIGHT);
  return { top: `${top}px`, height: `${height - 2}px` };
}

function busyLabel(occ: BusyOccurrence): string {
  return `${occ.busyStudentNames.join(' / ')} · ${occ.packageName || occ.className || 'Busy'}`;
}


function eventStyle(row: WeeklyAdjustmentRow) {
  const start = dayjs(row.plannedStartAt);
  const end = dayjs(row.plannedEndAt);
  const startMin = start.hour() * 60 + start.minute();
  const endMin = end.hour() * 60 + end.minute();
  const top = ((startMin - CAL_START_MINUTES) / CAL_SLOT_MINUTES) * CAL_SLOT_HEIGHT;
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

const displayMonday = ref(currentCalendarWeekRange().startDate);

function currentCalendarWeekRange() {
  return calendarWeekRange(new Date(), APP_TIMEZONE);
}

type ScheduleStudentSectionKey = 'pending' | 'scheduled' | 'paused';

// 每个课包作为一个排课单位，再按课程年级及排课状态分组；共享课包只由主成员返回一次。
const studentSections = computed(() => {
  const q = studentSearch.value.trim().toLowerCase();
  const sections = {
    pending: new Map<string, ScheduleCourseGradeGroup & { entryMap: Map<string, ScheduleSidebarEntry> }>(),
    scheduled: new Map<string, ScheduleCourseGradeGroup & { entryMap: Map<string, ScheduleSidebarEntry> }>(),
    paused: new Map<string, ScheduleCourseGradeGroup & { entryMap: Map<string, ScheduleSidebarEntry> }>(),
  };
  students.value.forEach((student) => {
    student.courseGrades.forEach((course) => {
      const displayNames = course.participantNames || student.studentName;
      if (q && !displayNames.toLowerCase().includes(q)) return;
      const section: ScheduleStudentSectionKey = course.packageStatus === 'PAUSED'
        ? 'paused' : course.hasSchedule === true ? 'scheduled' : 'pending';
      const { courseName, gradeBand } = course;
      const groups = sections[section];
      const normalizedCourse = courseName.trim().toLowerCase();
      const normalizedGrade = gradeBand.trim().toLowerCase();
      const key = JSON.stringify([normalizedCourse, normalizedGrade]);
      let group = groups.get(key);
      if (!group) {
        group = { key, courseName, gradeBand, students: [], entryMap: new Map() };
        groups.set(key, group);
      }
      const entryKey = String(course.packageId ?? course.privateLessonGroupId ?? `${student.id}:${courseName}:${gradeBand}`);
      group.entryMap.set(entryKey, { student, course });
    });
  });
  return (['pending', 'scheduled', 'paused'] as const)
    .map(key => ({
      key,
      title: key === 'pending' ? '待排课' : key === 'scheduled' ? '已排课' : '停课',
      groups: Array.from(sections[key].values())
        .map(({ entryMap, ...group }) => ({
          ...group,
          students: Array.from(entryMap.values()).sort((left, right) =>
            (left.course.participantNames || left.student.studentName).localeCompare(
              right.course.participantNames || right.student.studentName,
              'en', { sensitivity: 'base', numeric: true },
            )),
        }))
        .sort((left, right) => left.courseName.localeCompare(right.courseName)
          || left.gradeBand.localeCompare(right.gradeBand, undefined, { numeric: true })),
    }));
});

function toggleCourseGradeGroup(groupKey: string) {
  const next = new Set(collapsedCourseGradeKeys.value);
  if (next.has(groupKey)) next.delete(groupKey);
  else next.add(groupKey);
  collapsedCourseGradeKeys.value = next;
}

const selectedPackageName = computed(() => {
  const pkg = studentPackages.value.find(p => p.id === selectedPackageId.value);
  return pkg?.packageName ?? '';
});

const selectedScheduleEntry = computed(() => selectedPackageId.value == null ? undefined : students.value
    .flatMap(student => student.courseGrades.map(course => ({ student, course })))
    .find(({ course }) => String(course.packageId) === String(selectedPackageId.value)));
const isPrivateGroupCourseSelected = computed(() => selectedScheduleEntry.value?.course.privateLessonGroupId != null);
const selectedStudentName = computed(() => {
  if (selectedScheduleEntry.value) {
    return selectedScheduleEntry.value.course.participantNames || selectedScheduleEntry.value.student.studentName;
  }
  return students.value.find(student => String(student.id) === String(selectedStudentId.value))?.studentName ?? '';
});

// 共享课以课包成员为准；侧栏数据为轻量列表，课包尚未带成员时用其聚合字段回退。
const selectedParticipants = computed<ScheduleParticipant[]>(() => {
  const packageParticipants = selectedPackage.value?.privateLessonParticipants;
  if (packageParticipants?.length) {
    return packageParticipants
      .filter(participant => participant.memberStatus !== 'INACTIVE')
      .map(participant => ({ id: participant.studentId, name: participant.studentName || `Student #${participant.studentId}` }));
  }
  const course = selectedScheduleEntry.value?.course;
  const ids = course?.participantStudentIds?.split(',').map(id => id.trim()).filter(Boolean) ?? [];
  const names = course?.participantNames?.split(' / ').map(name => name.trim()) ?? [];
  if (ids.length) {
    return ids.map((id, index) => ({ id, name: names[index] || `Student #${id}` }));
  }
  return selectedStudentId.value == null ? [] : [{
    id: selectedStudentId.value,
    name: students.value.find(student => String(student.id) === String(selectedStudentId.value))?.studentName || 'Student',
  }];
});

// 编辑弹窗的状态必须先于 editWeekMonday 计算属性声明；初始化 watch 会立即读取该计算属性。
const editForm = reactive({
  ruleStatus: undefined as string | undefined,
  startDate: '',
  notes: '',
  slots: [] as Array<SlotDef & { teacherId?: EntityId; classroomSelection?: string; subjectName?: string; allocatedHours?: number }>,
});

const editFormRules: FormRules = {
  startDate: [{ required: true, message: 'Start date is required', trigger: 'change' }],
};


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

// 主周历只展示当前排课单位；共享私教课在数据库中只有一条课次。
const currentPackageOccupiedSlots = computed<OccupiedSlotDef[]>(() => {
  const result: OccupiedSlotDef[] = [];

  for (const occ of currentWeekOccurrences.value) {

    if (occ.planningStatus === 'CANCELLED') continue;

    const start = dayjs(occ.plannedStartAt);

    const weekday = start.day() === 0 ? 7 : start.day();
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
      studentName: selectedStudentName.value,
      isCurrent: true,
    });
  }

  return result;
});

// 编辑窗口用选定周的真实课次，含全部组员的班课和其他私教课；后端保存仍校验完整排课周期。
const otherPackageOccupiedSlots = computed<OccupiedSlotDef[]>(() => {
  return editBusyOccurrences.value.map(occ => {
    const start = dayjs(occ.plannedStartAt);
    return {
      weekday: start.day() === 0 ? 7 : start.day(),
      startTime: start.format('HH:mm'),
      endTime: dayjs(occ.plannedEndAt).format('HH:mm'),
      courseLabel: occ.packageName || occ.className || 'Lesson',
      studentName: occ.busyStudentNames.join(' / '),
      teacherName: occ.plannedTeacherName,
      classroomName: occ.plannedClassroomName,
    };
  });
});

// ─── Edit dialog week Monday ────────────────────
const editWeekMonday = computed(() => {
  const start = editForm.startDate ? dayjs(editForm.startDate) : dayjs();
  return start.subtract((start.day() + 6) % 7, 'day').format('YYYY-MM-DD');
});

// 弹窗与开课日期变化时刷新所有成员的占用；旧请求不得覆盖新日期的预览。
watch(
  [editDialogVisible, editWeekMonday, selectedPackageId, () => selectedParticipants.value.map(item => item.id).join(',')],
  async () => {
    const sequence = ++editBusyLoadSequence;
    editBusyOccurrences.value = [];
    editBusyError.value = '';
    if (!editDialogVisible.value || selectedPackageId.value == null) {
      editBusyLoading.value = false;
      return;
    }
    editBusyLoading.value = true;
    try {
      const weekEnd = dayjs(editWeekMonday.value).add(7, 'day').format('YYYY-MM-DD');
      const occurrences = await fetchParticipantBusyOccurrences(
        `${editWeekMonday.value}T00:00:00`, `${weekEnd}T00:00:00`,
      );
      if (sequence === editBusyLoadSequence) editBusyOccurrences.value = occurrences;
    } catch (error) {
      if (sequence === editBusyLoadSequence) editBusyError.value = `Could not load student availability: ${extractErrorMessage(error)}`;
    } finally {
      if (sequence === editBusyLoadSequence) editBusyLoading.value = false;
    }
  },
);

// ─── Student selection ─────────────────────────
// 一次取得当前学生的在读与停课课包；分页取全，避免课包多时漏掉停课入口。
async function fetchSchedulePackages(studentId: EntityId): Promise<CoursePackageItem[]> {
  const pageSize = 200;
  const query = { studentId, packageStatuses: 'ACTIVE,PAUSED', teachingMode: 'ONE_ON_ONE', pageSize };
  const first = await fetchCoursePackages({ ...query, pageNum: 1 });
  const { records, total } = first.data.data;
  if (total <= records.length) return records;
  const pages = Array.from({ length: Math.ceil(total / pageSize) - 1 }, (_, index) => index + 2);
  const remaining = await Promise.all(pages.map(pageNum => fetchCoursePackages({ ...query, pageNum })));
  return records.concat(...remaining.map(response => response.data.data.records));
}

async function onStudentSelect(id: EntityId, preferredPackageId?: EntityId) {
  // 快速切换学生时丢弃较晚返回的旧请求，避免覆盖当前选择。
  const requestSequence = ++packageLoadSequence;
  selectedStudentId.value = id;
  selectedPackageId.value = null;
  studentPackages.value = [];
  allPackageRules.value = {};
  currentWeekOccurrences.value = [];
  editBusyOccurrences.value = [];
  loadingPackages.value = true;
  try {
    const packages = await fetchSchedulePackages(id);
    if (requestSequence !== packageLoadSequence) return;
    studentPackages.value = packages;
    selectedPackageId.value = preferredPackageId == null
      ? null
      : studentPackages.value.find(pkg => String(pkg.id) === String(preferredPackageId))?.id ?? null;
    loadingPackages.value = false;
    // 排课规则用于冲突检查，后台加载即可，不阻塞已取得的课包列表。
    void loadAllPackageRules(studentPackages.value, requestSequence);
  } catch {
    if (requestSequence === packageLoadSequence) studentPackages.value = [];
  } finally {
    if (requestSequence === packageLoadSequence) loadingPackages.value = false;
  }
}

/** 选择侧栏的单个排课单位；1 对 2 传入主课包和成员姓名，排课规则仍只创建一份。 */
function onScheduleCourseSelect(entry: ScheduleSidebarEntry) {
  void onStudentSelect(entry.student.id, entry.course.packageId);
}

// 分页取全一周的课次，避免 200 条上限截断成员冲突预览。
async function fetchAllOccurrences(query: LessonOccurrenceQuery): Promise<LessonOccurrenceItem[]> {
  const first = await fetchLessonOccurrences({ ...query, pageNum: 1, pageSize: 200 });
  const { records, total } = first.data.data;
  if (total <= records.length) return records;
  const pages = Array.from({ length: Math.ceil(total / 200) - 1 }, (_, index) => index + 2);
  const remaining = await Promise.all(pages.map(pageNum =>
    fetchLessonOccurrences({ ...query, pageNum, pageSize: 200 })));
  return records.concat(...remaining.map(response => response.data.data.records));
}

// 同一共享课次可能被多个成员查询到，按课次 ID 合并并保留相关成员姓名。
async function fetchParticipantBusyOccurrences(startAt: string, endAt: string): Promise<BusyOccurrence[]> {
  if (selectedParticipants.value.length === 0) throw new Error('No active students found for this course.');
  const participantResults = await Promise.all(selectedParticipants.value.map(async participant => ({
    participant,
    occurrences: await fetchAllOccurrences({ studentId: participant.id, startAt, endAt }),
  })));
  const selectedGroupId = selectedPackage.value?.privateLessonGroupId
    ?? selectedScheduleEntry.value?.course.privateLessonGroupId;
  const byOccurrenceId = new Map<string, BusyOccurrence>();
  for (const { participant, occurrences } of participantResults) {
    for (const occurrence of occurrences) {
      if (occurrence.planningStatus === 'CANCELLED') continue;
      if (selectedGroupId != null && String(occurrence.privateLessonGroupId) === String(selectedGroupId)) continue;
      if (String(occurrence.packageId) === String(selectedPackageId.value)) continue;
      const key = String(occurrence.id);
      const existing = byOccurrenceId.get(key);
      if (existing) {
        if (!existing.busyStudentNames.includes(participant.name)) existing.busyStudentNames.push(participant.name);
      } else {
        byOccurrenceId.set(key, { ...occurrence, busyStudentNames: [participant.name] });
      }
    }
  }
  return [...byOccurrenceId.values()];
}

// 主日历按课包查询，防止把主学生其他课程误显示为当前共享课程。
let currentWeekLoadSequence = 0;
async function loadCurrentWeekOccurrences() {
  const packageId = selectedPackageId.value;
  const sequence = ++currentWeekLoadSequence;
  currentWeekOccurrences.value = [];
  if (packageId == null) return;
  const { startDate, endExclusiveDate } = currentCalendarWeekRange();
  displayMonday.value = startDate;
  try {
    const records = await fetchAllOccurrences({
      packageId,
      startAt: `${startDate}T00:00:00`,
      endAt: `${endExclusiveDate}T00:00:00`,
    });
    if (sequence === currentWeekLoadSequence) currentWeekOccurrences.value = records;
  } catch {
    if (sequence === currentWeekLoadSequence) currentWeekOccurrences.value = [];
  }
}

watch(selectedPackageId, () => { void loadCurrentWeekOccurrences(); });

// ─── Package selection ─────────────────────────
function onPackageSelect(pkg: CoursePackageItem) {
  selectedPackageId.value = pkg.id;
}

// 打开停课确认窗；停课将保留已记录课次并取消未来未记录课次。
function pauseSelectedPackage() {
  const packageToPause = selectedPackage.value;
  const studentId = selectedStudentId.value;
  if (!packageToPause || packageToPause.packageStatus !== 'ACTIVE' || !studentId) return;
  pauseConfirmationVisible.value = true;
}

// 倒计时结束后才执行停课请求。
async function confirmPauseSelectedPackage() {
  const packageToPause = selectedPackage.value;
  const studentId = selectedStudentId.value;
  if (!packageToPause || packageToPause.packageStatus !== 'ACTIVE' || !studentId || pausingPackageId.value !== null) return;
  pausingPackageId.value = packageToPause.id;
  try {
    await pauseCoursePackage(packageToPause.id);
    pauseConfirmationVisible.value = false;
    ElMessage.success('Course paused.');
    await Promise.all([
      onStudentSelect(studentId, packageToPause.id),
      refreshScheduleStudentList(),
    ]);
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    pausingPackageId.value = null;
  }
}

// 恢复课包后保留原选中项，并直接打开新排课；暂停的旧规则与课次不会重新启用。
async function resumeSelectedPackage() {
  const packageToResume = selectedPackage.value;
  const studentId = selectedStudentId.value;
  if (!packageToResume || packageToResume.packageStatus !== 'PAUSED' || !studentId) return;
  resumingPackageId.value = packageToResume.id;
  try {
    await resumeCoursePackage(packageToResume.id);
    await Promise.all([
      onStudentSelect(studentId, packageToResume.id),
      refreshScheduleStudentList(),
    ]);
    ElMessage.success('Course resumed. Create a new schedule to continue lessons.');
    if (String(selectedPackageId.value) === String(packageToResume.id)) await openCreateDialog();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    resumingPackageId.value = null;
  }
}

/** 从消课数据接口读取完整课次，在独立签字表预览中展示，不离开排课页面。 */
async function openPrintSheet(command: string | number | object) {
  if (!selectedStudentId.value || !selectedPackageId.value) return;
  const mode = command === 'teacher' ? 'teacher' : 'parent';
  const preview = window.open('', '_blank');
  if (!preview) {
    ElMessage.warning('Allow pop-ups to open the signature sheet preview.');
    return;
  }
  preview.opener = null;
  preview.document.write('<!doctype html><html><head><title>Loading signature sheet</title></head><body style="font:16px Arial;padding:32px">Loading signature sheet…</body></html>');
  preview.document.close();

  try {
    const response = await fetchOneOnOneDeductions({
      studentId: selectedStudentId.value,
      packageId: selectedPackageId.value,
    });
    const match = response.data.data
      .flatMap(teacher => teacher.students)
      .flatMap(student => student.packages.map(coursePackage => ({ student, coursePackage })))
      .find(({ student, coursePackage }) =>
        String(student.studentId) === String(selectedStudentId.value)
        && String(coursePackage.packageId) === String(selectedPackageId.value));

    if (!match) throw new Error('No signature sheet is available for the selected course.');
    if (preview.closed) return;
    preview.document.open();
    preview.document.write(renderSignatureSheetDocument(match.student, match.coursePackage, mode));
    preview.document.close();
    preview.focus();
  } catch (err) {
    if (preview.closed) return;
    preview.document.body.textContent = extractErrorMessage(err);
  }
}

async function saveDeductedHours() {
  const pkg = selectedPackage.value;
  if (!pkg || !selectedStudentId.value || savingDeductedHours.value) return;
  if (!Number.isInteger(deductedHoursDraft.value * 2)
    || deductedHoursDraft.value < confirmedDeductionHours.value
    || deductedHoursDraft.value > deductedHoursMax.value) {
    ElMessage.warning('Already deducted hours must use 0.5-hour increments and fit within the package total after pending deduction.');
    return;
  }
  savingDeductedHours.value = true;
  try {
    await updateCoursePackageDeductedHours(pkg.id, deductedHoursDraft.value);
    studentPackages.value = await fetchSchedulePackages(selectedStudentId.value);
    await Promise.all([loadAllPackageRules(), loadCurrentWeekOccurrences()]);
    ElMessage.success('Package hours and future schedule updated.');
  } catch (err) {
    ElMessage.error(extractErrorMessage(err));
  } finally {
    savingDeductedHours.value = false;
  }
}

// ─── Load rules for ALL packages ────────────────
async function loadAllPackageRules(
  packages: CoursePackageItem[] = studentPackages.value,
  requestSequence?: number,
) {
  if (packages.length === 0) {
    allPackageRules.value = {};
    return;
  }
  const results = await Promise.allSettled(
    packages.filter(pkg => pkg.packageStatus === 'ACTIVE').map(async (pkg) => {
      const res = await fetchRecurringScheduleRules({ packageId: pkg.id, ruleStatus: 'ACTIVE', pageSize: 1 });
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
  if (requestSequence == null || requestSequence === packageLoadSequence) {
    allPackageRules.value = map;
  }
}

const totalSlotUnits = computed(() =>
  editForm.slots.reduce((sum, s) => sum + (s.plannedUnits || 0), 0)
);

// 编辑分配只使用课包当前剩余课时，与上方课包余额卡片保持一致。
const allocatablePackageHours = computed(() => selectedPackage.value
  ? projectedRemainingForPackage(selectedPackage.value) : 0);

// 已在各时段分配的课时合计（小时）。
const allocatedTotalHours = computed(() =>
  editForm.slots.reduce((sum, s) => sum + (s.allocatedHours || 0), 0)
);

const remainingAllocationHours = computed(() =>
  Number((allocatablePackageHours.value - allocatedTotalHours.value).toFixed(1))
);

const allocationExceedsRemaining = computed(() => remainingAllocationHours.value < 0);

async function openCreateDialog() {
  if (selectedPackage.value?.packageStatus !== 'ACTIVE') return;
  isEditing.value = false;
  editingRuleId.value = null;
  editFormRef.value?.resetFields();
  Object.assign(editForm, {
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
    ruleStatus: detail.ruleStatus,
    startDate: detail.startDate,
    notes: detail.notes || '',
    slots: (detail.slots || []).map((s: any) => ({
      teacherId: s.teacherId,
      classroomSelection: s.deliveryMode === 'ONLINE'
        ? 'ONLINE'
        : s.classroomId != null || detail.defaultClassroomId != null
          ? `ROOM:${s.classroomId ?? detail.defaultClassroomId}`
          : '',
      subjectName: s.subjectName,
      weekday: s.weekday,
      startTime: s.startTime,
      endTime: s.endTime,
      plannedUnits: s.plannedUnits,
      allocatedHours: s.allocatedUnits != null ? s.allocatedUnits * 0.5 : undefined,
    })),
  });
  editDialogVisible.value = true;
}

function submitForm() {
  if (editBusyLoading.value || editBusyError.value) {
    ElMessage.warning('Wait for student availability to load before saving.');
    return;
  }
  if (isEditing.value) {
    submitEditForm();
  } else {
    submitCreateForm();
  }
}

function validateSlotAllocations(): boolean {
  const incompleteSlot = editForm.slots.find(slot =>
    !slot.teacherId || !slot.subjectName?.trim() || !slot.allocatedHours || slot.allocatedHours <= 0
  );
  if (!incompleteSlot) return true;
    ElMessage.warning('Each time slot requires a teacher, lesson name, and allocated hours.');
  return false;
}

async function submitCreateForm() {
  if (!editFormRef.value) return;
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return;
    if (editForm.slots.length === 0) {
      ElMessage.warning('Please select at least one class time slot on the calendar.');
      return;
    }
    if (editForm.slots.some(slot => !slot.classroomSelection)) {
      ElMessage.warning('Choose a classroom or Online for every time slot.');
      return;
    }
    if (allocationExceedsRemaining.value) {
      ElMessage.warning('Allocated hours exceed the remaining package hours. Please reduce the allocation.');
      return;
    }
    if (!validateSlotAllocations()) return;
    submitting.value = true;
    try {
      await createRecurringScheduleRule({
        packageId: selectedPackageId.value!,
        startDate: editForm.startDate,
        notes: editForm.notes || undefined,
        slots: editForm.slots.map((s) => ({
          teacherId: s.teacherId,
          deliveryMode: s.classroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON',
          classroomId: s.classroomSelection?.startsWith('ROOM:') ? s.classroomSelection.slice(5) : undefined,
          subjectName: s.subjectName || undefined,
          weekday: s.weekday,
          startTime: s.startTime,
          endTime: s.endTime,
          plannedUnits: s.plannedUnits,
          allocatedUnits: s.allocatedHours != null ? Math.round(s.allocatedHours * 2) : undefined,
        })),
      });
      ElMessage.success('Schedule created and lessons generated.');
      editDialogVisible.value = false;
      await Promise.all([loadAllPackageRules(), refreshScheduleStudentList()]);
      await loadCurrentWeekOccurrences();
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
    if (editForm.slots.some(slot => !slot.classroomSelection)) {
      ElMessage.warning('Choose a classroom or Online for every time slot.');
      return;
    }
    if (allocationExceedsRemaining.value) {
      ElMessage.warning('Allocated hours exceed the remaining package hours. Please reduce the allocation.');
      return;
    }
    if (!validateSlotAllocations()) return;
    submitting.value = true;
    try {
      const result = await updateRecurringScheduleRule(editingRuleId.value!, {
        ruleStatus: editForm.ruleStatus,
        startDate: editForm.startDate,
        notes: editForm.notes || undefined,
        slots: editForm.slots.map((s) => ({
          teacherId: s.teacherId,
          deliveryMode: s.classroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON',
          classroomId: s.classroomSelection?.startsWith('ROOM:') ? s.classroomSelection.slice(5) : undefined,
          subjectName: s.subjectName || undefined,
          weekday: s.weekday,
          startTime: s.startTime,
          endTime: s.endTime,
          plannedUnits: s.plannedUnits,
          allocatedUnits: s.allocatedHours != null ? Math.round(s.allocatedHours * 2) : undefined,
        })),
      });
      const data = result.data.data;
      ElMessage.success(
        `Schedule updated. ${data.generatedOccurrenceCount} occurrences regenerated, ` +
        `${data.preservedOccurrenceCount} preserved (${data.confirmedOccurrenceCount} confirmed).`
      );
      editDialogVisible.value = false;
      await Promise.all([loadAllPackageRules(), refreshScheduleStudentList()]);
      await loadCurrentWeekOccurrences();
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
  weeklyBusyError.value = '';
  weeklyDialogVisible.value = true;
  weeklyLoading.value = true;
  try {
    const { start, end } = currentNaturalWeekRange();
    const startAt = start.format('YYYY-MM-DDT00:00:00');
    const endAt = end.format('YYYY-MM-DDT23:59:59');

    const [packageOccurrences, participantBusy] = await Promise.all([
      fetchAllOccurrences({ packageId: selectedPackageId.value, startAt, endAt }),
      fetchParticipantBusyOccurrences(startAt, endAt),
    ]);
    weeklyRows.value = packageOccurrences
      .filter(canEditWeeklyOccurrence)
      .map(toWeeklyAdjustmentRow);

    weeklyBusyOccurrences.value = participantBusy;
  } catch (err) {
    weeklyRows.value = [];
    weeklyBusyOccurrences.value = [];
    weeklyBusyError.value = extractErrorMessage(err);
    ElMessage.error(extractErrorMessage(err));
  } finally {
    weeklyLoading.value = false;
  }
}

async function submitWeeklyAdjustment() {
  if (!selectedPackageId.value) return;
  if (weeklyLoading.value || weeklyBusyError.value) return;
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

    await loadCurrentWeekOccurrences();
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
    plannedTeacherId: currentRule.value?.slots?.[0]?.teacherId,
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
    const busy = weeklyBusyOccurrences.value.find(occurrence =>
      startAt.isBefore(dayjs(occurrence.plannedEndAt))
      && endAt.isAfter(dayjs(occurrence.plannedStartAt)));
    if (busy) {
      return `${busy.busyStudentNames.join(' / ')} already has ${busy.packageName || busy.className || 'a lesson'} at this time.`;
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

function scheduleSlotLabel(slot: SlotDef) {
  return `${weekdayLabel(slot.weekday)} ${slot.startTime}-${slot.endTime}`;
}

function formatSlotHours(units?: number) {
  return units == null ? '—' : `${(units * 0.5).toFixed(1)}h`;
}

function slotLessonPlan(slot: SlotDef & { allocatedHours?: number }) {
  const lessonHours = Number(slot.plannedUnits || 0) * 0.5;
  const allocatedHours = Number(slot.allocatedHours || 0);
  if (lessonHours <= 0 || allocatedHours <= 0) return 'Set allocated hours';
  const fullLessons = Math.floor((allocatedHours + 0.0001) / lessonHours);
  const remainder = Number((allocatedHours - fullLessons * lessonHours).toFixed(1));
  if (remainder <= 0) {
    return `${fullLessons} lesson${fullLessons === 1 ? '' : 's'}`;
  }
  return `${fullLessons + 1} lessons · last ${remainder.toFixed(1)}h`;
}

// ─── Init ────────────────────────────────────────
// 单独加载学生名单，避免老师或教室接口失败时把已返回的学生名单一并丢弃。
async function refreshScheduleStudentList() {
  studentListLoading.value = true;
  studentListError.value = false;
  try {
    const response = await fetchOneOnOneScheduleStudents();
    students.value = response.data.data;
  } catch {
    studentListError.value = true;
    if (students.value.length > 0) ElMessage.error('Could not refresh students. Please retry.');
  } finally {
    studentListLoading.value = false;
  }
}

async function init() {
  const [, teacherResult, classroomResult] = await Promise.allSettled([
    refreshScheduleStudentList(),
    fetchAllTeachers(),
    fetchAllClassrooms(),
  ]);
  if (teacherResult.status === 'fulfilled') {
    teachers.value = [...teacherResult.value.data.data].sort((left, right) =>
      left.teacherName.localeCompare(right.teacherName, 'en', { sensitivity: 'base', numeric: true }),
    );
  }
  if (classroomResult.status === 'fulfilled') classrooms.value = classroomResult.value.data.data;
  if (!studentListError.value) await applyRouteScheduleContext();
}

async function applyRouteScheduleContext() {
  const studentId = route.query.studentId;
  const packageId = route.query.packageId;
  if (typeof studentId !== 'string' || typeof packageId !== 'string') {
    return;
  }
  let targetStudentId = studentId;
  let targetPackageId = packageId;
  const sidebarHasPackage = students.value.some(student =>
    student.courseGrades.some(course => String(course.packageId) === packageId));
  if (!sidebarHasPackage) {
    try {
      const response = await fetchCoursePackage(packageId);
      const primary = response.data.data.privateLessonParticipants
        ?.find(participant => participant.participantIndex === 1 && participant.memberStatus !== 'INACTIVE');
      if (primary) {
        targetStudentId = String(primary.studentId);
        targetPackageId = String(primary.packageId);
      }
    } catch {
      ElMessage.warning('The course package could not be loaded for scheduling.');
      return;
    }
  }
  const student = students.value.find((item) => String(item.id) === targetStudentId);
  if (!student) {
    ElMessage.warning('The new student record could not be found for scheduling.');
    return;
  }
  await onStudentSelect(student.id, targetPackageId);
  const selectedPackage = studentPackages.value.find((item) => String(item.id) === targetPackageId);
  if (!selectedPackage) {
    ElMessage.warning('The new course package could not be found for scheduling.');
    return;
  }
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
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  cursor: pointer;
  border: 0;
  border-bottom: 1px solid #f5f7fa;
  background: #fff;
  color: inherit;
  font: inherit;
  text-align: left;
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

.schedule-student-group {
  border-bottom: 1px solid #e7edf4;
}

.schedule-student-section-title {
  padding: 10px;
  border-bottom: 1px solid #e7edf4;
  background: #eaf1f8;
  color: #34465b;
  font-size: 12px;
  font-weight: 700;
}

.schedule-student-section + .schedule-student-section {
  border-top: 3px solid #e3eaf2;
}

.schedule-student-section__empty {
  padding: 12px 10px;
  color: #909399;
  font-size: 12px;
}

.schedule-student-group__toggle {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border: 0;
  background: #f5f8fc;
  color: #34465b;
  cursor: pointer;
  text-align: left;
}

.schedule-student-group__toggle:hover {
  background: #eaf1f8;
}

.schedule-student-group__title {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 6px;
}

.schedule-student-group__title strong {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-student-group__title > span {
  flex: 0 0 auto;
  padding: 2px 5px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 10px;
  font-weight: 700;
}

.schedule-student-group__toggle small {
  color: #718096;
  font-size: 10px;
}

.schedule-student-group__toggle :deep(.el-icon) {
  color: #718096;
  transition: transform 0.15s ease;
}

.schedule-student-group__toggle:not(.schedule-student-group__toggle--collapsed) :deep(.el-icon) {
  transform: rotate(90deg);
}

.schedule-student-group__students .col-item {
  padding-left: 18px;
}

.schedule-student-group__students .col-item--active {
  padding-left: 15px;
}

/* ── Schedule display ────────────────────────── */
.package-hours-card {
  margin: 12px;
  padding: 12px 14px;
  border: 1px solid #cfe3f8;
  border-radius: 8px;
  background: #f7fbff;
}
.package-hours-dialog .package-hours-card { margin: 0; }
.package-hours-card__heading { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.package-hours-card__fields { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: center; gap: 14px; }
.package-hours-card__eyebrow { margin-bottom: 3px; color: #7d8da1; font-size: 10px; letter-spacing: .08em; }
.package-hours-card__fields { margin-top: 12px; }
.package-hours-card__fields > div,
.package-hours-card__input { display: flex; min-width: 0; flex-direction: column; gap: 5px; color: #7d8da1; font-size: 11px; }
.package-hours-card__fields strong { color: #273b53; font-size: 14px; }
.package-hours-card__input :deep(.el-input-number) { width: 100%; }
.package-hours-card__hint { margin: 10px 0 0; color: #8795a5; font-size: 11px; }

.schedule-info {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
}

.schedule-info__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.schedule-info__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.schedule-info__participants {
  margin: -2px 0 12px;
  color: #52657a;
  font-size: 12px;
}

.schedule-info__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
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

.classroom-select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.classroom-select-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.edit-schedule__allocation {
  color: #67c23a;
}

.edit-schedule__allocation--over {
  color: #f56c6c;
}

.slot-teacher-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.slot-teacher-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) repeat(3, minmax(135px, 1fr)) 110px auto;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid #dbeafe;
  border-radius: 6px;
  background: #f8fbff;
}

.slot-teacher-row__time {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.slot-teacher-row__unit {
  color: #909399;
  font-size: 13px;
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

.weekly-busy-list {
  border: 1px solid #dce6ef;
  border-radius: 6px;
  color: #52657a;
  font-size: 12px;
}

.weekly-busy-list summary {
  padding: 8px 10px;
  cursor: pointer;
}

.weekly-busy-list__items {
  max-height: 140px;
  overflow: auto;
  padding: 0 10px 8px;
  line-height: 1.8;
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

<!-- el-select 的下拉面板会 teleport 到 body，校区分组样式需放在非 scoped 样式中。 -->
<style>
.classroom-campus-picker .el-select-group__wrap {
  margin: 6px 8px;
  overflow: hidden;
  border: 1px solid #d7e3f1;
  border-radius: 6px;
  background: #fff;
}

.classroom-campus-picker .el-select-group__wrap + .el-select-group__wrap {
  margin-top: 10px;
}

.classroom-campus-picker .el-select-group__title {
  height: 32px;
  padding: 0 12px;
  border-bottom: 1px solid #d7e3f1;
  background: #eef6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  line-height: 32px;
}

.classroom-campus-picker .el-select-group__wrap .el-select-dropdown__item {
  min-height: 34px;
  padding: 6px 12px 6px 24px;
  font-size: 13px;
  line-height: 1.35;
}
</style>
