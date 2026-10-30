<template>
  <div class="page-stack schedule-page">
    <section class="schedule-panel">
      <div v-if="!appStore.isMobileViewport" class="schedule-panel__header">
        <span class="schedule-panel__title">Schedule Private Lessons</span>
      </div>

      <div class="schedule-panel__body">
        <!-- Column 1: Students -->
        <aside
          class="col-panel col-students"
          :class="{ 'col-panel--mobile-collapsed': appStore.isMobileViewport && selectedStudentId && !mobileStudentListExpanded }"
        >
          <button
            v-if="appStore.isMobileViewport && selectedStudentId && !mobileStudentListExpanded"
            type="button"
            class="mobile-selection-summary"
            aria-label="Change selected student"
            aria-controls="mobile-schedule-student-list"
            :aria-expanded="mobileStudentListExpanded"
            @click="mobileStudentListExpanded = true"
          >
            <span class="mobile-selection-summary__step">STUDENT</span>
            <strong>{{ selectedStudentName }}</strong>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <div id="mobile-schedule-student-list" v-show="!appStore.isMobileViewport || !selectedStudentId || mobileStudentListExpanded" class="mobile-selection-list">
            <div class="col-panel__header schedule-student-search">
              <div v-if="appStore.isMobileViewport" class="schedule-student-list-heading">
                <span class="mobile-step-label">STEP 1</span>
                <strong>Choose a student and course</strong>
                <small>Select a course to view its weekly schedule.</small>
              </div>
              <button
                v-if="appStore.isMobileViewport"
                type="button"
                class="mobile-search-trigger"
                :aria-expanded="mobileStudentSearchExpanded"
                aria-label="Search students"
                @click="mobileStudentSearchExpanded = !mobileStudentSearchExpanded"
              >
                <el-icon><Search /></el-icon>
              </button>
              <el-input
                v-show="!appStore.isMobileViewport || mobileStudentSearchExpanded"
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
          </div>
        </aside>

        <!-- Column 2: Packages -->
        <aside
          class="col-panel col-packages"
          :class="{
            'col-panel--mobile-hidden': !selectedStudentId,
            'col-panel--mobile-collapsed': appStore.isMobileViewport && selectedPackageId && !mobilePackageListExpanded,
          }"
        >
          <button
            v-if="appStore.isMobileViewport && selectedPackageId && !mobilePackageListExpanded"
            type="button"
            class="mobile-selection-summary"
            aria-label="Change selected course package"
            aria-controls="mobile-schedule-package-list"
            :aria-expanded="mobilePackageListExpanded"
            @click="mobilePackageListExpanded = true"
          >
            <span class="mobile-selection-summary__step">COURSE PACKAGE</span>
            <strong>{{ selectedPackageName }}</strong>
            <small>{{ selectedPackage?.packageStatus }} · {{ selectedPackage ? remainingForPackage(selectedPackage) : 0 }}h remaining</small>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <div id="mobile-schedule-package-list" v-show="!appStore.isMobileViewport || !selectedPackageId || mobilePackageListExpanded" class="col-panel__body">
            <div v-if="appStore.isMobileViewport" class="mobile-step-label mobile-step-label--package">2 · COURSE PACKAGE</div>
            <div v-if="!selectedStudentId" class="col-panel__hint">
              <el-icon :size="28" color="#c0c4cc"><ArrowLeft /></el-icon>
              <span>Select a student</span>
            </div>
            <div v-else-if="loadingPackages" class="col-panel__hint">
              <el-icon class="is-loading" :size="20"><Loading /></el-icon>
              <span>Loading packages...</span>
            </div>
            <div v-else-if="packageLoadError" class="col-panel__hint col-panel__hint--error">
              <span>{{ packageLoadError }}</span>
              <el-button link type="primary" @click="retryLoadPackages">Retry</el-button>
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
                  <span class="col-item__sub">{{ pkg.packageStatus }} · {{ remainingForPackage(pkg) }}h remaining</span>
                </div>
                <el-icon v-if="selectedPackageId === pkg.id" color="#409eff"><ArrowRight /></el-icon>
              </div>
            </template>
          </div>
        </aside>

        <!-- Column 3: Weekly Schedule -->
        <main class="col-panel col-schedule" :class="{ 'col-panel--mobile-hidden': !selectedPackageId }">
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
                      v-if="selectedPackage?.packageStatus === 'ACTIVE' && currentRule"
                      type="primary"
                      size="small"
                      :loading="checkingEditDeduction"
                      :disabled="checkingEditDeduction"
                      @click="requestEditSchedule"
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
                      Adjust Schedule
                    </el-button>
                    <el-button
                      v-if="selectedPackage?.packageStatus === 'ACTIVE' && !currentRule"
                      type="primary"
                      size="small"
                      @click="openCreateDialog"
                    >
                      Create Schedule
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
                    <el-dropdown trigger="click" @command="handleScheduleMoreAction">
                      <el-button size="small">More</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item
                            v-if="selectedPackage?.packageStatus === 'ACTIVE'"
                            command="package-hours"
                          >
                            Package hours
                          </el-dropdown-item>
                          <el-dropdown-item command="print-parent">Print Student Sheet</el-dropdown-item>
                          <el-dropdown-item command="print-teacher">Print teacher sheets</el-dropdown-item>
                          <el-dropdown-item v-if="currentRule" command="detail">Schedule details</el-dropdown-item>
                          <el-dropdown-item
                            v-if="selectedPackage?.packageStatus === 'ACTIVE'"
                            command="pause"
                            :disabled="pausingPackageId !== null"
                            divided
                            type="danger"
                          >
                            Pause course
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
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
                    ? 'Course paused. Resume it to create a new schedule; completed lessons remain and future lessons stay cancelled.'
                    : 'No schedule yet for this package.' }}</span>
                </div>
              </div>

              <!-- Weekly calendar -->
              <div v-if="currentPackageOccupiedSlots.length > 0 || currentRule" class="schedule-calendar">
                <div class="schedule-calendar__heading">
                  <div>
                    <span class="schedule-calendar__eyebrow">3 · WEEKLY PLAN</span>
                    <strong class="schedule-calendar__label">Weekly Schedule</strong>
                  </div>
                  <span v-if="currentRuleSlots.length" class="schedule-calendar__count">{{ currentRuleSlots.length }} weekly slot(s)</span>
                </div>
                <WeeklyTimeSelector
                  v-model="emptySlots"
                  :occupied-slots="currentPackageOccupiedSlots"
                  :week-start-date="displayMonday"
                  :readonly="true"
                  :hide-hint="true"
                  :pinch-zoom-enabled="appStore.isMobileViewport"
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
      :close-on-press-escape="!savingStartingHours"
      :show-close="!savingStartingHours"
      class="schedule-dialog package-hours-dialog"
    >
      <section v-if="selectedPackage" class="package-hours-card">
        <div class="package-hours-card__heading">
          <div>
            <div class="package-hours-card__eyebrow">PACKAGE HOURS</div>
            <strong>Hours overview</strong>
          </div>
          <el-button type="primary" size="small" :loading="savingStartingHours" :disabled="!startingHoursChanged || startingHoursSaveBlocked" @click="saveStartingHours">
            Save hours
          </el-button>
        </div>
        <div class="package-hours-card__fields">
          <div><span>Total hours</span><strong>{{ packageHours(selectedPackage.totalUnits) }}h</strong></div>
          <div><span>Already deducted</span><strong>{{ confirmedDeductionHours }}h</strong></div>
          <div><span title="Past lesson hours without a final admin decision, including lessons the teacher has not submitted.">Pending deduction</span><strong>{{ pendingDeductionHours }}h</strong></div>
          <label class="package-hours-card__input">
            <span>Starting Hours</span>
            <el-input-number v-model="startingHoursDraft" :min="startingHoursMin" :max="startingHoursMax" :step="0.5" :precision="1" :disabled="savingStartingHours || startingHoursSaveBlocked" controls-position="right" />
          </label>
        </div>
        <p class="package-hours-card__hint">Starting Hours is the balance when tracking began. Already deducted hours are lessons confirmed in the system.</p>
        <div v-if="startingHoursChanged" class="package-hours-card__impact">
          <strong>After saving</strong>
          <span>Starting {{ packageHours(selectedPackage.startingHours).toFixed(1) }}h → {{ startingHoursDraft.toFixed(1) }}h</span>
          <span>Remaining {{ remainingForPackage(selectedPackage).toFixed(1) }}h → {{ projectedRemainingHours.toFixed(1) }}h</span>
          <small v-if="projectedRemainingHours === 0">Upcoming regular lessons will be removed. Review any unfinished, changed, makeup, or shared lessons first.</small>
          <small v-else>Future lessons may be shortened or removed when the balance decreases. Review the schedule after saving. Existing lesson history is kept.</small>
        </div>
      </section>
      <el-alert v-if="startingHoursCompleted && !selectedPackage" title="The hours were saved. The package has no remaining hours and is now completed." type="success" :closable="false" show-icon />
      <el-alert v-if="startingHoursError" :title="startingHoursError" type="error" :closable="false" show-icon class="package-hours-card__error" />

      <template #footer>
        <div class="dialog-footer">
          <el-button :disabled="savingStartingHours" @click="packageHoursDialogVisible = false">Close</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Create / Edit Schedule dialog -->
    <el-dialog
      v-model="editDialogVisible"
      :title="isEditing ? 'Edit Schedule' : 'Create Weekly Schedule'"
      width="min(980px, calc(100vw - 32px))"
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
        <div v-if="selectedPackage" class="edit-schedule__balance" aria-label="Package balance summary">
          <strong>Package hours</strong>
          <span>Starting Hours {{ packageHours(selectedPackage.startingHours).toFixed(1) }}h</span>
          <span>Allocated Hours {{ scheduleAllocatedHours == null ? '—' : `${scheduleAllocatedHours.toFixed(1)}h` }}</span>
          <span class="edit-schedule__balance-remaining">Remaining {{ packageHours(selectedPackage.remainingUnits).toFixed(1) }}h</span>
        </div>

        <el-radio-group v-if="isEditing" v-model="editMode" class="edit-schedule__mode" @change="resetEditModeDraft">
          <el-radio-button value="CURRENT">Change Teacher / Classroom</el-radio-button>
          <el-radio-button value="REPLAN">Reschedule Lesson</el-radio-button>
        </el-radio-group>
        <p v-if="isEditing" class="edit-schedule__hint">
          {{ editMode === 'CURRENT'
            ? 'Change a current slot’s teacher, allocated hours, lesson name, or classroom. Its day, time, and schedule period stay the same.'
            : 'Change the start date or weekly times. Assign each previous slot’s lesson history to one new slot before saving.' }}
        </p>
        <el-alert v-if="isEditing && editMode === 'REPLAN' && pendingStartedLessons.length"
          type="warning" :closable="false" show-icon
          :title="`${pendingStartedLessons.length} previous lessons are still unconfirmed`">
          <p>Review lessons from all previous schedule periods in 1-on-1 Lessons, or withdraw all ended, unconfirmed lessons and reuse their hours.</p>
          <ul class="edit-deduction-prompt__lessons">
            <li v-for="lesson in pendingStartedLessons" :key="lesson.occurrenceId">
              {{ lesson.plannedStartAt ? dayjs(lesson.plannedStartAt).format('MMM D, YYYY') : 'Date unavailable' }} ·
              {{ lesson.plannedStartAt && lesson.plannedEndAt ? scheduleOccurrenceTimeRange(lesson.plannedStartAt, lesson.plannedEndAt) : 'Time unavailable' }} ·
              {{ lesson.subjectName || 'Lesson' }} · {{ lesson.teacherName || 'Unassigned' }} ·
              {{ lesson.inProgress ? 'In progress' : lesson.status === 'PENDING_ADMIN' ? 'Awaiting admin' : lesson.status === 'PARTIALLY_CONFIRMED' ? 'Needs review' : lesson.status === 'REJECTED' ? 'Returned for revision' : 'Awaiting teacher' }} ·
              {{ Number(lesson.plannedHours || 0).toFixed(1) }}h
            </li>
          </ul>
          <p>Total: {{ pendingStartedLessons.reduce((sum, lesson) => sum + lesson.plannedHours, 0).toFixed(1) }}h</p>
          <el-checkbox v-model="reclaimEndedUnconfirmedLessons" :disabled="pendingStartedLessons.some(lesson => lesson.inProgress || lesson.status === 'PARTIALLY_CONFIRMED')">Withdraw these pending records and continue</el-checkbox>
          <el-button link type="primary" @click="goToOneOnOneDeduction">Open 1-on-1 Lessons</el-button>
        </el-alert>

        <el-form ref="editFormRef" :model="editForm" :rules="editFormRules" label-width="130px">
          <el-form-item v-if="!isEditing || editMode === 'REPLAN'" label="Start Date" prop="startDate">
            <el-date-picker
              v-model="editForm.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="Select start date"
              style="width: 100%;"
              :disabled-date="disabledPastDate"
            />
          </el-form-item>
          <el-form-item v-if="!isEditing" label="Notes">
            <el-input
              v-model="editForm.notes"
              type="textarea"
              :rows="2"
              placeholder="Optional notes"
              style="width: 100%;"
            />
          </el-form-item>
        </el-form>
        <el-alert v-if="isEditing && editMode === 'REPLAN'"
          type="warning" :closable="false" show-icon
          title="Saving ends the current recurring schedule immediately. New lessons start on the selected date; there may be a gap in lessons." />

        <!-- Weekly time selector -->
        <el-divider content-position="left">{{ isEditing && editMode === 'CURRENT' ? 'Current time slots' : 'Weekly time slots' }}</el-divider>
        <div v-if="isEditing && editMode === 'REPLAN'" class="edit-schedule__sources">
          <strong>Previous time slots and teachers</strong>
          <div v-for="source in originalEditSlots" :key="String(source.slotId)">
            {{ scheduleSlotLabel(source) }} · {{ teacherNameForSlot(source.teacherId) }} · {{ source.subjectName || 'Lesson' }}
            <span>{{ editForm.slots.some(slot => String(slot.slotId) === String(source.slotId)) ? '→ assigned' : '→ choose a successor below' }}</span>
          </div>
        </div>
        <p v-if="!isEditing" class="edit-schedule__hint">
          Click and drag on the calendar to select class time slots. For each slot, assign a teacher,
          lesson name, and its schedule total, including deducted and pending lessons. Each slot is scheduled independently until
          its schedule total runs out (the last class takes the remainder if it does not divide evenly).
          Existing lessons stay visible as a reference. A blue edge marks your choice over an existing lesson;
          saving checks conflicts on the actual lesson dates.
        </p>
        <el-alert v-if="editBusyError" :title="editBusyError" type="error" :closable="false" show-icon />
        <div v-if="editBusyLoading && (!isEditing || editMode === 'REPLAN')" class="edit-schedule__hint">Loading student availability...</div>
        <WeeklyTimeSelector
          v-if="isEditing && editMode === 'CURRENT'"
          :model-value="[]"
          :occupied-slots="currentScheduleCalendarSlots"
          :readonly="true"
          :hide-hint="true"
          :pinch-zoom-enabled="appStore.isMobileViewport"
        />
        <WeeklyTimeSelector
          v-if="!isEditing || editMode === 'REPLAN'"
          v-model="editForm.slots"
          :week-start-date="editWeekMonday"
          :occupied-slots="otherPackageOccupiedSlots"
          :readonly="false"
          :pinch-zoom-enabled="appStore.isMobileViewport"
        />
        <div v-if="editForm.slots.length > 0" class="slot-teacher-list">
          <div
            v-for="(slot, index) in editForm.slots"
            :key="`${slot.weekday}-${slot.startTime}-${index}`"
            class="slot-teacher-row"
          >
            <div class="slot-teacher-row__time">
              <strong>{{ scheduleSlotLabel(slot) }}</strong>
              <small>First class {{ firstOccurrenceDate(slot) }} · Each lesson {{ formatSlotHours(slot.plannedUnits) }} · {{ slotLessonPlan(slot) }}</small>
              <label v-if="isEditing && editMode === 'REPLAN'" class="slot-teacher-row__field">
                <span>Inherit previous lesson history</span>
                <el-select v-model="slot.slotId" clearable placeholder="New slot, no previous history">
                  <el-option
                    v-for="source in originalEditSlots.filter(item => !editForm.slots.some(other => other !== slot && String(other.slotId) === String(item.slotId)))"
                    :key="String(source.slotId)" :value="source.slotId!"
                    :label="`${scheduleSlotLabel(source)} · ${teacherNameForSlot(source.teacherId)} · ${source.subjectName || 'Lesson'}`"
                  />
                </el-select>
              </label>
            </div>
            <label v-if="isEditing && editMode === 'CURRENT'" class="slot-teacher-row__field">
              <span>Inherit previous lesson history</span>
              <el-select v-model="selectedUnlinkedBySlot[String(slot.slotId)]" multiple filterable clearable
                placeholder="Choose unassigned historical lessons" no-data-text="No unassigned historical lessons">
                <el-option v-for="item in availableUnlinkedHistory(String(slot.slotId))"
                  :key="String(item.occurrenceId)" :value="String(item.occurrenceId)"
                  :label="`${item.plannedStartAt?.replace('T', ' ').slice(0, 16) || 'Unknown date'} · ${item.subjectName || 'Lesson'} · ${item.teacherName || 'Unknown teacher'}`" />
              </el-select>
            </label>
            <div class="slot-teacher-row__fields">
              <label class="slot-teacher-row__field">
                <span>Teacher</span>
                <el-select
                  v-model="slot.teacherId"
                  filterable
                  placeholder="Select teacher"
                >
                  <el-option v-for="teacher in teachers" :key="String(teacher.id)" :label="teacher.teacherName" :value="String(teacher.id)" />
                </el-select>
              </label>
              <label class="slot-teacher-row__field">
                <span>Classroom</span>
                <el-select
                  v-model="slot.classroomSelection"
                  popper-class="classroom-campus-picker"
                  filterable
                  clearable
                  placeholder="No classroom or Online"
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
                      :key="String(c.id)"
                      :label="classroomSelectLabel(c)"
                      :value="`ROOM:${String(c.id)}`"
                    >
                      <div class="classroom-select-option"><span>{{ c.roomName }}</span></div>
                    </el-option>
                  </el-option-group>
                </el-select>
              </label>
              <label class="slot-teacher-row__field">
                <span>Lesson name</span>
                <el-input
                  v-model="slot.subjectName"
                  placeholder="Lesson name"
                  maxlength="128"
                />
              </label>
              <label class="slot-teacher-row__field slot-teacher-row__field--hours">
                <span>Allocated total (includes completed lessons)</span>
                <span class="slot-teacher-row__hours-control">
                  <el-input-number
                    v-model="slot.allocatedHours"
                    :min="0.5"
                    :step="0.5"
                    :precision="1"
                    controls-position="right"
                    placeholder="Hours"
                  />
                  <em>h</em>
                </span>
              </label>
            </div>
          </div>
        </div>
        <div v-if="editForm.slots.length > 0" class="edit-schedule__summary">
          {{ editForm.slots.length }} slot(s) · {{ (totalSlotUnits * 0.5).toFixed(1) }}h scheduled each week
          <span
            class="edit-schedule__allocation"
            :class="{ 'edit-schedule__allocation--over': allocationExceedsRemaining }"
          >
            · {{ isEditing ? 'Original Allocated' : 'Allocated' }} {{ (isEditing ? originalAllocatedHours : allocatedHours).toFixed(1) }}h
            <template v-if="isEditing && hasSchedulePlanChanges">
              · Adjusted Allocated {{ allocatedHours.toFixed(1) }}h
            </template>
            · Remaining {{ remainingPackageHours.toFixed(1) }}h
          </span>
        </div>
        <el-alert
          v-if="allocationSaveError"
          class="edit-schedule__allocation-error"
          type="error"
          :closable="false"
          show-icon
        >
          <template #title>
            Cannot save: {{ allocationSaveError.requestedAllocationHours.toFixed(1) }}h is allocated,
            but only {{ allocationSaveError.availableAllocationHours.toFixed(1) }}h is available
            ({{ allocationSaveError.shortfallHours.toFixed(1) }}h over).
          </template>
          <div class="allocation-error-details">
            <strong>Package hours</strong>
            <span>Total {{ allocationSaveError.packageTotalHours.toFixed(1) }}h</span>
            <span>Remaining {{ allocationSaveError.packageRemainingHours.toFixed(1) }}h</span>
            <span>Requested {{ allocationSaveError.requestedAllocationHours.toFixed(1) }}h</span>
            <span>Available {{ allocationSaveError.availableAllocationHours.toFixed(1) }}h</span>
            <strong>Hours reserved in this calculation</strong>
            <span>Pending admin review {{ allocationSaveError.pendingReviewHours.toFixed(1) }}h</span>
            <span>Other schedules {{ allocationSaveError.otherScheduleReservedHours.toFixed(1) }}h</span>
            <span>Preserved lessons {{ allocationSaveError.preservedLessonHours.toFixed(1) }}h</span>
            <span>Transferred future lessons +{{ allocationSaveError.transferredFutureLessonHours.toFixed(1) }}h</span>
            <span v-if="allocationSaveError.currentRuleRetainedHours > 0">
              Current schedule already used +{{ allocationSaveError.currentRuleRetainedHours.toFixed(1) }}h
            </span>
          </div>
          <p class="allocation-error-guidance">
            Available hours = remaining balance − pending reviews − other schedules − preserved lessons + transferred future lessons + hours already used by this schedule.
          </p>
          <p class="allocation-error-guidance">
            Reduce one or more slot allocations by {{ allocationSaveError.shortfallHours.toFixed(1) }}h. If the reserved hours are unexpected, review the student’s pending and preserved lesson records before retrying.
          </p>
        </el-alert>
        <el-alert
          v-if="isEditing && editMode === 'CURRENT' && hasTeacherHandoffChanges"
          class="edit-schedule__change-hint"
          type="info"
          :closable="false"
          title="The new teacher takes over this slot’s full lesson timeline. Completed and processed records keep their original teacher and deduction. Unprocessed, individually adjusted future lessons keep their date, time, classroom, and lesson name while moving to the new teacher; regular future lessons follow the edited weekly schedule."
        />
        <el-alert
          v-if="isEditing && editMode === 'CURRENT' && !hasSchedulePlanChanges"
          class="edit-schedule__change-hint"
          type="info"
          :closable="false"
          title="Changing a classroom or lesson name updates future lessons only. Existing lesson records and allocated hours stay unchanged."
        />
        <el-alert
          v-else-if="isEditing && editMode === 'REPLAN' && hasSchedulePlanChanges"
          class="edit-schedule__change-hint"
          type="warning"
          :closable="false"
          title="The new timetable starts a new schedule period. Previous lesson records follow the history assignment selected above; completed lessons keep their original teacher and deduction."
        />
      </div>

      <template #footer>
        <div class="dialog-footer edit-schedule__footer">
          <el-button @click="editDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting || editBusyLoading || checkingEditDeduction" :disabled="Boolean(editBusyError) || submitting || checkingEditDeduction" @click="submitForm">
            {{ isEditing ? 'Save Changes' : 'Create Schedule' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Selected natural week temporary adjustment -->
    <el-dialog
      v-model="weeklyDialogVisible"
      :title="`Adjust Schedule — ${selectedPackageName}`"
      width="860px"
      :close-on-click-modal="false"
      class="schedule-dialog"
    >
      <div v-loading="weeklyLoading" class="weekly-adjustment">
        <div class="weekly-adjustment__week-picker">
          <label for="weekly-adjustment-week">Week</label>
          <el-select
            id="weekly-adjustment-week"
            v-model="selectedWeekStartDate"
            :disabled="weeklyLoading || submitting"
            @change="onWeeklyWeekChange"
          >
            <el-option
              v-for="week in adjustableWeekOptions"
              :key="week.startDate"
              :label="week.label"
              :value="week.startDate"
            />
          </el-select>
          <span>Current week and previous four weeks</span>
        </div>
        <el-alert
          v-if="isSelectedWeekInPast"
          title="Past week adjustment"
          description="Only lessons without a lesson record can be changed. Enter a reason for the audit history."
          type="warning"
          show-icon
          :closable="false"
        />
        <el-alert
          title="You can edit lessons from the selected week and move them to any date. New lessons must stay in the selected week; the recurring schedule remains unchanged."
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
          <div class="weekly-summary__item" :class="{ 'weekly-summary__item--changed': weeklyUnitDelta !== 0 }">
            <span>
              Delta
              <el-tooltip
                content="Adjusted minus original hours in this week. Moving a lesson to another date changes this weekly total, but does not by itself change the package's total scheduled hours."
                placement="top"
              >
                <el-icon class="weekly-summary__help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
            <strong>{{ formatSignedUnitsAsHours(weeklyUnitDelta) }}</strong>
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
              {{ busy.busyStudentNames.join(' / ') }} · {{ dayjs(busy.plannedStartAt).format('ddd M/D') }} {{ occurrenceTimeRange(busy.plannedStartAt, busy.plannedEndAt) }} · {{ busy.packageName || busy.className || 'Lesson' }}
            </div>
          </div>
        </details>


        <div class="weekly-cal" :style="{ '--weekly-slot-height': `${CAL_SLOT_HEIGHT}px` }">
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
              <span
                v-for="(r, index) in calendarRows"
                :key="r.label"
                class="weekly-cal__time"
                :class="{ 'weekly-cal__time--last': index === calendarRows.length - 1 }"
              >
                <template v-if="index === calendarRows.length - 1">
                  <span>{{ r.label }}</span>
                  <span>{{ SCHEDULE_DAY_END_TIME }}</span>
                </template>
                <template v-else>{{ r.label }}</template>
              </span>
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
                  'weekly-cal__event--readonly': row.readOnly,
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
              'weekly-list__item--readonly': row.readOnly,
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
            <el-tag v-if="isOutsideSelectedWeek(row)" size="small" type="info" effect="plain">
              Outside selected week
            </el-tag>
          </div>
        </div>


        <div v-if="selectedRow && !selectedRow.readOnly" class="weekly-editor" :class="{ 'weekly-editor--deleted': selectedRow.markedForDelete }">
          <div class="weekly-editor__title">
            <strong>{{ selectedRow.rowType === 'NEW' ? 'New lesson' : 'Edit lesson' }}</strong>
            <span v-if="selectedRow.rowType === 'EXISTING'">{{ formatDate(selectedRow.original?.plannedStartAt) }}</span>
          </div>
          <div class="weekly-editor__grid">
            <div v-if="selectedRow.rowType === 'NEW'" class="weekly-editor__field">
              <label>Course name</label>
              <el-select
                v-model="selectedRow.subjectName"
                filterable
                allow-create
                default-first-option
                placeholder="Select or enter course name"
              >
                <el-option v-for="name in weeklySubjectOptions" :key="name" :label="name" :value="name" />
              </el-select>
              <span class="weekly-editor__hint">Choose a course from this package, or enter its name.</span>
            </div>
            <div class="weekly-editor__field">
              <label>Day</label>
              <el-date-picker
                :model-value="rowDate(selectedRow)"
                type="date"
                format="ddd, MMM D, YYYY"
                value-format="YYYY-MM-DD"
                placeholder="Choose lesson date"
                :clearable="false"
                :disabled-date="isDayDisabledForSelectedRow"
                :disabled="selectedRow.markedForDelete"
                @update:model-value="(v: string | null) => v && setRowDay(selectedRow!, v)"
              />
              <small v-if="isOutsideSelectedWeek(selectedRow)" class="weekly-editor__hint">
                This lesson will move outside the selected week. Its original week is used for this adjustment; conflicts are checked when saving.
              </small>
            </div>
            <div class="weekly-editor__field">
              <label>Start</label>
              <el-time-select
                :model-value="rowStartTime(selectedRow)"
                :start="SCHEDULE_DAY_START_TIME"
                :end="SCHEDULE_LAST_SLOT_START_TIME"
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
                <el-option v-for="t in teachers" :key="String(t.id)" :label="t.teacherName" :value="String(t.id)" />
              </el-select>
            </div>
            <div class="weekly-editor__field">
              <label>Classroom</label>
              <el-select v-model="selectedRow.classroomSelection" popper-class="classroom-campus-picker" filterable clearable placeholder="No classroom or Online" :disabled="selectedRow.markedForDelete">
                <el-option label="Online" value="ONLINE" />
                <el-option-group
                  v-for="group in classroomOptionGroups"
                  :key="group.campusKey"
                  :label="group.campusName"
                  class="classroom-campus-group"
                >
                  <el-option
                    v-for="c in group.classrooms"
                    :key="String(c.id)"
                    :label="classroomSelectLabel(c)"
                    :value="`ROOM:${String(c.id)}`"
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


        <section v-if="unitDelta > unallocatedUnitsBeforeAdjustment" class="weekly-future-release">
          <div class="weekly-future-release__heading">
            <strong>Choose future lessons to adjust</strong>
            <span>{{ formatUnitsAsHours(unallocatedUnitsBeforeAdjustment) }} unallocated · {{ formatUnitsAsHours(futureReleaseNeedUnits) }} more must be freed</span>
          </div>
          <p>Added hours exceed the package’s unallocated balance. Select upcoming unconfirmed lessons; the latest selected lessons are reduced first. No other future lesson will change.</p>
          <el-checkbox-group v-model="selectedFutureReleaseIds" class="weekly-future-release__list">
            <el-checkbox
              v-for="occurrence in futureReleaseCandidates"
              :key="occurrence.id"
              :value="String(occurrence.id)"
            >
              {{ dayjs(occurrence.plannedStartAt).format('ddd, MMM D, YYYY') }} · {{ occurrenceTimeRange(occurrence.plannedStartAt, occurrence.plannedEndAt) }} · {{ occurrence.subjectName || occurrence.packageName || 'Lesson' }} · {{ formatUnitsAsHours(occurrence.plannedUnits ?? 0) }}
            </el-checkbox>
          </el-checkbox-group>
          <el-empty v-if="futureReleaseCandidates.length === 0" description="No upcoming unconfirmed lessons are available to adjust." :image-size="48" />
          <div v-if="futureReleasePreview.length" class="weekly-future-release__preview">
            <span>Will be reduced:</span>
            <span v-for="item in futureReleasePreview" :key="item.id">{{ item.label }} ({{ formatUnitsAsHours(item.units) }})</span>
          </div>
          <el-alert
            v-if="selectedFutureReleaseUnits < futureReleaseNeedUnits"
            :title="`Select ${formatUnitsAsHours(futureReleaseNeedUnits - selectedFutureReleaseUnits)} more.`"
            type="error"
            :closable="false"
          />
        </section>

        <el-form label-width="80px" class="weekly-reason">
          <el-form-item label="Reason" :required="isSelectedWeekInPast">
            <el-input v-model="weeklyReason" type="textarea" :rows="2" :placeholder="isSelectedWeekInPast ? 'Required for past-week changes' : 'Optional reason'" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="weeklyDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting || weeklyLoading" :disabled="!hasWeeklyChanges || Boolean(weeklyBusyError) || isMissingPastWeekReason || selectedFutureReleaseUnits < futureReleaseNeedUnits" @click="submitWeeklyAdjustment">
            Save Adjustments
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
      message="Please deduct all lessons that have already started before pausing. Go to 1-on-1 Lessons to deduct them, or wait 5 seconds and pause without deducting. Pausing clears all future lessons from student and teacher schedules."
      confirm-text="Pause Without Deducting"
      :confirm-cooldown-seconds="5"
      alternate-action-text="Go to 1-on-1 Lessons"
      :loading="pausingPackageId !== null"
      @confirm="confirmPauseSelectedPackage"
      @alternate-action="goToOneOnOneDeduction"
    />
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, Calendar, Search, QuestionFilled, Delete, RefreshLeft, Close, Loading } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import dayjs from 'dayjs';
import { useRoute, useRouter } from 'vue-router';

import { extractErrorMessage } from '@/api/http';
import { showScheduleError } from '@/utils/schedule-conflict-feedback';
import { fetchAllClassrooms, fetchAllTeachers, fetchClassroom, fetchTeacher } from '@/api/base-data';
import { useAppStore } from '@/stores/app';
import { fetchCoursePackage, fetchCoursePackages, pauseCoursePackage, resumeCoursePackage, updateCoursePackageStartingHours } from '@/api/enrollment';
import PauseConfirmationDialog from '@/components/shared/PauseConfirmationDialog.vue';
import { fetchOneOnOneDeductions, fetchPrivateSignatureHistory } from '@/api/one-on-one';
import {
  createRecurringScheduleRule,
  updateRecurringScheduleRule,
  fetchRecurringScheduleRule,
  fetchRecurringScheduleRules,
  fetchSchedulePrintHistory,
  fetchOneOnOneScheduleStudents,
  fetchLessonOccurrences,
  adjustWeeklySchedule,
} from '@/api/schedule';
import type { LessonOccurrenceQuery, ScheduleStudentOption, WeeklyScheduleAdjustmentItemPayload } from '@/api/schedule';
import type { ClassroomItem, CoursePackageItem, EntityId, LessonOccurrenceItem, RecurringScheduleRuleItem, SchedulePendingLesson } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import { APP_TIMEZONE } from '@/config/app-config';
import { calendarWeekRange } from '@/utils/calendar-week';
import {
  SCHEDULE_DAY_END_MINUTES,
  SCHEDULE_DAY_END_TIME,
  SCHEDULE_DAY_START_MINUTES,
  SCHEDULE_DAY_START_TIME,
  SCHEDULE_LAST_SLOT_START_TIME,
  SCHEDULE_SLOT_MINUTES,
  scheduleEndTimeForDisplay,
  scheduleEndTimeForApi,
  scheduleOccurrenceTimeRange,
} from '@/utils/schedule-time';
import { renderSignatureSheetDocument } from '@/utils/signature-sheet-print';
import { findSignaturePackage, withSignatureHistory } from '@/utils/one-on-one-signature-data';

interface WeeklyAdjustmentRow {

  rowKey: string;

  rowType: 'EXISTING' | 'NEW';
  occurrenceId?: EntityId;
  lessonNo: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedHours: number;
  subjectName?: string;
  plannedTeacherId?: EntityId;
  classroomSelection?: string;

  markedForDelete: boolean;
  readOnly: boolean;

  itemReason: string;
  original?: LessonOccurrenceItem;
}

interface ScheduleAllocationErrorDetails {
  packageTotalHours: number;
  packageRemainingHours: number;
  requestedAllocationHours: number;
  availableAllocationHours: number;
  shortfallHours: number;
  pendingReviewHours: number;
  otherScheduleReservedHours: number;
  preservedLessonHours: number;
  transferredFutureLessonHours: number;
  currentRuleRetainedHours: number;
}

const loadingPackages = ref(false);
const packageLoadError = ref('');
let packageLoadSequence = 0;
let packageLoadAbortController: AbortController | null = null;
let preferredPackageForRetry: EntityId | undefined;
const route = useRoute();
const router = useRouter();
const editDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const weeklyDialogVisible = ref(false);
const weeklyLoading = ref(false);
const submitting = ref(false);
const allocationSaveError = ref<ScheduleAllocationErrorDetails | null>(null);
const isEditing = ref(false);
type EditMode = 'CURRENT' | 'REPLAN';
const editMode = ref<EditMode>('CURRENT');
let activeEditMode: EditMode = 'CURRENT';
const editModeDrafts: Partial<Record<EditMode, { startDate: string; slots: typeof editForm.slots }>> = {};
interface UnlinkedHistoryOption {
  occurrenceId: EntityId;
  subjectName?: string;
  teacherName?: string;
  plannedStartAt?: string;
}
const unlinkedHistory = ref<UnlinkedHistoryOption[]>([]);
const selectedUnlinkedBySlot = reactive<Record<string, string[]>>({});
function availableUnlinkedHistory(slotId: string) {
  const assignedElsewhere = new Set(Object.entries(selectedUnlinkedBySlot)
    .filter(([key]) => key !== slotId).flatMap(([, ids]) => ids));
  return unlinkedHistory.value.filter(item => !assignedElsewhere.has(String(item.occurrenceId)));
}
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

// 编辑排课时补齐 active 列表之外的已关联资源，避免选择框把原始 ID 当作标签显示。
async function ensureScheduleResourceOptions(
  slots: Array<{ teacherId?: EntityId; deliveryMode?: string; classroomId?: EntityId }>,
  defaultClassroomId?: EntityId,
) {
  const teacherIds = [...new Set(slots
    .map(slot => slot.teacherId == null ? '' : String(slot.teacherId))
    .filter(Boolean))];
  const classroomIds = [...new Set(slots
    .filter(slot => slot.deliveryMode !== 'ONLINE')
    .map(slot => slot.classroomId ?? defaultClassroomId)
    .filter((id): id is EntityId => id != null)
    .map(String))];
  const knownTeacherIds = new Set(teachers.value.map(teacher => String(teacher.id)));
  const knownClassroomIds = new Set(classrooms.value.map(classroom => String(classroom.id)));
  const missingTeacherIds = teacherIds.filter(id => !knownTeacherIds.has(id));
  const missingClassroomIds = classroomIds.filter(id => !knownClassroomIds.has(id));

  const [teacherResults, classroomResults] = await Promise.all([
    Promise.allSettled(missingTeacherIds.map(id => fetchTeacher(id))),
    Promise.allSettled(missingClassroomIds.map(id => fetchClassroom(id))),
  ]);
  const extraTeachers = teacherResults.flatMap(result =>
    result.status === 'fulfilled' ? [result.value.data.data] : [],
  );
  const extraClassrooms = classroomResults.flatMap(result =>
    result.status === 'fulfilled' ? [result.value.data.data] : [],
  );

  if (extraTeachers.length) {
    teachers.value = [...teachers.value, ...extraTeachers]
      .sort((left, right) => left.teacherName.localeCompare(right.teacherName, 'en', { sensitivity: 'base', numeric: true }));
  }
  if (extraClassrooms.length) classrooms.value = [...classrooms.value, ...extraClassrooms];
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
const appStore = useAppStore();
const mobileStudentSearchExpanded = ref(false);
const mobileStudentListExpanded = ref(true);
const mobilePackageListExpanded = ref(true);
const selectedStudentId = ref<EntityId | null>(null);
const selectedPackageId = ref<EntityId | null>(null);
const pausingPackageId = ref<EntityId | null>(null);
const pauseConfirmationVisible = ref(false);
const checkingEditDeduction = ref(false);
const pendingStartedLessons = ref<SchedulePendingLesson[]>([]);
const scheduleAllocatedHours = ref<number | null>(null);
const reclaimEndedUnconfirmedLessons = ref(false);
const resumingPackageId = ref<EntityId | null>(null);
const packageHoursDialogVisible = ref(false);
const startingHoursDraft = ref(0);
const savingStartingHours = ref(false);
const startingHoursError = ref('');
const startingHoursSaveBlocked = ref(false);
const startingHoursCompleted = ref(false);
const selectedPackage = computed(() => studentPackages.value.find(p => String(p.id) === String(selectedPackageId.value)) ?? null);
const packageHours = (hours?: number) => Number((hours ?? 0).toFixed(1));
const confirmedDeductionHours = computed(() => {
  const pkg = selectedPackage.value;
  return packageHours(Math.max(0, (pkg?.consumedUnits ?? 0) - (pkg?.openingUsedHours ?? 0)));
});
const pendingDeductionHours = computed(() => packageHours(selectedPackage.value?.pendingDeductionUnits));
const pendingAdminDeductionHours = computed(() => packageHours(selectedPackage.value?.pendingAdminDeductionUnits));
const startingHoursMin = computed(() => packageHours(
  confirmedDeductionHours.value + pendingAdminDeductionHours.value,
));
const startingHoursMax = computed(() => packageHours(selectedPackage.value?.totalUnits));
const startingHoursChanged = computed(() => Number.isFinite(startingHoursDraft.value)
  && startingHoursDraft.value !== packageHours(selectedPackage.value?.startingHours));
const projectedRemainingHours = computed(() => packageHours(Math.max(0,
  startingHoursDraft.value - confirmedDeductionHours.value)));
watch(selectedPackage, pkg => {
  startingHoursDraft.value = packageHours(pkg?.startingHours);
  startingHoursSaveBlocked.value = false;
}, { immediate: true });
watch(packageHoursDialogVisible, visible => {
  if (visible) {
    startingHoursCompleted.value = false;
    if (!startingHoursSaveBlocked.value) startingHoursError.value = '';
  }
});
watch(startingHoursDraft, () => { if (!startingHoursSaveBlocked.value) startingHoursError.value = ''; });
function remainingForPackage(pkg: CoursePackageItem): number {
  return packageHours(Math.max(0, pkg.remainingUnits));
}
const emptySlots = ref<SlotDef[]>([]);
const weeklyRows = ref<WeeklyAdjustmentRow[]>([]);
const weeklyReason = ref('');
const selectedWeekStartDate = ref(currentNaturalWeekStartDate());
const weeklyLoadedWeekStartDate = ref(selectedWeekStartDate.value);
const allWeeklyPackageOccurrences = ref<LessonOccurrenceItem[]>([]);
const selectedFutureReleaseIds = ref<string[]>([]);

const weeklySubjectOptions = computed(() => [...new Set([
  selectedPackage.value?.subjectName,
  ...(currentRule.value?.slots ?? []).map(slot => slot.subjectName),
].map(name => name?.trim()).filter((name): name is string => Boolean(name)))]);

// ─── Weekly adjustment calendar (Sat→Fri) ──────────────────

const CAL_SLOT_HEIGHT = 28;

const selectedRowKey = ref<string | null>(null);


const weeklyBusyOccurrences = ref<BusyOccurrence[]>([]);
const weeklyBusyError = ref('');

const adjustableWeekOptions = computed(() => Array.from({ length: 5 }, (_, index) => {
  const startDate = addCalendarDays(currentNaturalWeekStartDate(), -index * 7);
  const endDate = addCalendarDays(startDate, 6);
  return {
    startDate,
    label: `${formatCalendarDate(startDate)} – ${formatCalendarDate(endDate)}${index === 0 ? ' · Current week' : ''}`,
  };
}));

const isSelectedWeekInPast = computed(() => selectedWeekStartDate.value < currentNaturalWeekStartDate());
const isMissingPastWeekReason = computed(() => isSelectedWeekInPast.value && !weeklyReason.value.trim());


const weekDays = computed(() => {
  const { start } = currentNaturalWeekRange();
  const todayStr = todayInAppTimezone();
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
  for (let minutes = SCHEDULE_DAY_START_MINUTES; minutes < SCHEDULE_DAY_END_MINUTES; minutes += SCHEDULE_SLOT_MINUTES) {
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

const allocatedUnitsBeforeAdjustment = computed(() => allWeeklyPackageOccurrences.value
    .reduce((sum, occurrence) => sum + reservedUnitsForOccurrence(occurrence), 0));

const packageRemainingUnits = computed(() => Math.round(Number(selectedPackage.value?.remainingUnits ?? 0) * 2));
const unallocatedUnitsBeforeAdjustment = computed(() => Math.max(0, packageRemainingUnits.value - allocatedUnitsBeforeAdjustment.value));

const futureReleaseNeedUnits = computed(() => Math.max(
  0,
  allocatedUnitsBeforeAdjustment.value + unitDelta.value - packageRemainingUnits.value,
));

const futureReleaseCandidates = computed(() => {
  const weekEnd = addCalendarDays(selectedWeekStartDate.value, 7);
  return allWeeklyPackageOccurrences.value
    .filter(isFutureReleaseCandidate)
    .filter(occurrence => occurrence.plannedStartAt > nowInAppTimezone())
    .filter(occurrence => occurrence.plannedStartAt.slice(0, 10) >= weekEnd)
    .sort((left, right) => right.plannedStartAt.localeCompare(left.plannedStartAt));
});

const selectedFutureReleaseUnits = computed(() => {
  const selectedIds = new Set(selectedFutureReleaseIds.value);
  return futureReleaseCandidates.value
    .filter(occurrence => selectedIds.has(String(occurrence.id)))
    .reduce((sum, occurrence) => sum + Math.max(0, occurrence.plannedUnits ?? 0), 0);
});

const futureReleasePreview = computed(() => {
  const selectedIds = new Set(selectedFutureReleaseIds.value);
  let remaining = futureReleaseNeedUnits.value;
  const preview: Array<{ id: EntityId; label: string; units: number }> = [];
  for (const occurrence of futureReleaseCandidates.value) {
    if (!selectedIds.has(String(occurrence.id)) || remaining <= 0) continue;
    const units = Math.min(remaining, Math.max(0, occurrence.plannedUnits ?? 0));
    if (units > 0) {
      preview.push({
        id: occurrence.id,
        label: `${dayjs(occurrence.plannedStartAt).format('ddd, MMM D')} ${occurrenceTimeRange(occurrence.plannedStartAt, occurrence.plannedEndAt)}`,
        units,
      });
      remaining -= units;
    }
  }
  return preview;
});


function rowsForDay(dayIndex: number): WeeklyAdjustmentRow[] {
  const date = weekDays.value[dayIndex]?.date;
  if (!date) return [];
  return weeklyRows.value.filter(r =>
    dayjs(r.plannedStartAt).format('YYYY-MM-DD') === date && occurrenceOverlapsCalendar(r.plannedStartAt, r.plannedEndAt));
}


function busyForDay(dayIndex: number): BusyOccurrence[] {
  const date = weekDays.value[dayIndex]?.date;
  if (!date) return [];
  return weeklyBusyOccurrences.value.filter(o =>
    dayjs(o.plannedStartAt).format('YYYY-MM-DD') === date && occurrenceOverlapsCalendar(o.plannedStartAt, o.plannedEndAt));
}

// 将超出日历显示范围的课次裁切到可见区间，避免 06:00 前的课程被负 top 值遮掉。
function occurrenceOverlapsCalendar(startAt: string, endAt: string): boolean {
  const start = dayjs(startAt);
  const end = dayjs(endAt);
  const startMinutes = start.hour() * 60 + start.minute();
  const endMinutes = startMinutes + end.diff(start, 'minute');
  return endMinutes > SCHEDULE_DAY_START_MINUTES && startMinutes < SCHEDULE_DAY_END_MINUTES;
}

function calendarOccurrenceStyle(startAt: string, endAt: string) {
  const start = dayjs(startAt);
  const end = dayjs(endAt);
  const startMinutes = start.hour() * 60 + start.minute();
  const endMinutes = startMinutes + end.diff(start, 'minute');
  const visibleStart = Math.max(startMinutes, SCHEDULE_DAY_START_MINUTES);
  const visibleEnd = Math.min(endMinutes, SCHEDULE_DAY_END_MINUTES);
  const top = ((visibleStart - SCHEDULE_DAY_START_MINUTES) / SCHEDULE_SLOT_MINUTES) * CAL_SLOT_HEIGHT;
  const height = Math.max(((visibleEnd - visibleStart) / SCHEDULE_SLOT_MINUTES) * CAL_SLOT_HEIGHT, 1);
  return { top: `${top}px`, height: `${Math.max(height - 2, 1)}px` };
}


function busyStyle(occ: LessonOccurrenceItem) {
  return calendarOccurrenceStyle(occ.plannedStartAt, occ.plannedEndAt);
}

function busyLabel(occ: BusyOccurrence): string {
  return `${occ.busyStudentNames.join(' / ')} · ${occ.packageName || occ.className || 'Busy'}`;
}


function eventStyle(row: WeeklyAdjustmentRow) {
  return calendarOccurrenceStyle(row.plannedStartAt, row.plannedEndAt);
}

function eventTimeLabel(row: WeeklyAdjustmentRow): string {
  return occurrenceTimeRange(row.plannedStartAt, row.plannedEndAt);
}

function occurrenceTimeRange(startAt: string, endAt: string): string {
  return scheduleOccurrenceTimeRange(startAt, endAt);
}

function eventTeacherLabel(row: WeeklyAdjustmentRow): string {
  if (row.markedForDelete) return 'Removed';
  const teacher = teachers.value.find(t => t.id === row.plannedTeacherId);
  return teacher?.teacherName ?? `${row.plannedHours}h`;
}

function selectRow(row: WeeklyAdjustmentRow) {
  if (row.readOnly) return;
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
  if (row.readOnly) return { label: 'Recorded · read-only', type: 'success' };
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

function isDateWithinSelectedWeek(value: string): boolean {
  const date = value.slice(0, 10);
  const weekStart = selectedWeekStartDate.value;
  return date >= weekStart && date < addCalendarDays(weekStart, 7);
}

function isOutsideSelectedWeek(row: WeeklyAdjustmentRow): boolean {
  return row.rowType === 'EXISTING' && !isDateWithinSelectedWeek(row.plannedStartAt);
}

function isDayDisabledForSelectedRow(date: Date): boolean {
  return selectedRow.value?.rowType === 'NEW'
    && !isDateWithinSelectedWeek(dayjs(date).format('YYYY-MM-DD'));
}


function setRowDay(row: WeeklyAdjustmentRow, date: string) {
  if (!date) return;
  const start = dayjs(row.plannedStartAt);
  const target = dayjs(`${date}T${start.format('HH:mm:ss')}`);
  if (!target.isValid()) return;
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
  if (weeklyLoading.value) return;
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
  const hours = (slotCount * SCHEDULE_SLOT_MINUTES) / 60;
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
onUnmounted(() => packageLoadAbortController?.abort());

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

// 课程列表加载或筛选结果变化时默认收起所有分组，用户可按需展开查看学生。
watch([studentSections, () => appStore.isMobileViewport], ([sections, isMobile]) => {
  collapsedCourseGradeKeys.value = isMobile
    ? new Set(sections.flatMap(section => section.groups.map(group => `${section.key}:${group.key}`)))
    : new Set();
}, { immediate: true });

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
  slots: [] as Array<SlotDef & {
    slotId?: EntityId;
    teacherId?: EntityId;
    classroomSelection?: string;
    subjectName?: string;
    allocatedHours?: number;
    rolloverAllocatedHours?: number;
  }>,
});
// 编辑输入发生变化后清除旧的额度错误，避免用户调整后还看到过期的拦截结果。
watch([
  () => editForm.slots,
  () => editForm.startDate,
  () => editForm.ruleStatus,
  () => reclaimEndedUnconfirmedLessons.value,
], () => {
  allocationSaveError.value = null;
}, { deep: true });
const originalEditSlots = ref<Array<SlotDef & { slotId?: EntityId; allocatedHours?: number }>>([]);
const originalAllocatedHours = ref(0);
// 只读周历使用保存前的课位快照；调整老师或额度不改变固定时段。
const currentScheduleCalendarSlots = computed<OccupiedSlotDef[]>(() => originalEditSlots.value.map(slot => ({
  weekday: slot.weekday,
  startTime: slot.startTime,
  endTime: slot.endTime,
  courseLabel: slot.subjectName || 'Lesson',
  teacherName: teacherNameForSlot(slot.teacherId),
  isCurrent: true,
})));

function teacherNameForSlot(teacherId?: EntityId): string {
  return teachers.value.find(teacher => String(teacher.id) === String(teacherId))?.teacherName || 'Teacher';
}

// 两个编辑视图各自保存草稿，切换时不会把当前排课的字段带入重排请求。
function resetEditModeDraft(value: string | number | boolean | undefined) {
  const nextMode = value === 'REPLAN' ? 'REPLAN' : 'CURRENT';
  editModeDrafts[activeEditMode] = {
    startDate: editForm.startDate,
    slots: editForm.slots.map(slot => ({ ...slot })),
  };
  const draft = editModeDrafts[nextMode];
  if (draft) {
    editForm.startDate = draft.startDate;
    editForm.slots = draft.slots.map(slot => ({ ...slot }));
  }
  activeEditMode = nextMode;
  allocationSaveError.value = null;
}

const editFormRules: FormRules = {
  startDate: [{ required: true, message: 'Start date is required', trigger: 'change' }],
};


const originalWeeklyUnits = computed(() =>
  weeklyRows.value
    .filter(row => row.rowType === 'EXISTING')
    .reduce((sum, row) => sum + (row.original ? reservedUnitsForOccurrence(row.original) : 0), 0)
);


const adjustedWeeklyUnits = computed(() =>
  weeklyRows.value
    .reduce((sum, row) => {
      if (row.readOnly) return sum + (row.original ? reservedUnitsForOccurrence(row.original) : 0);
      if (row.rowType === 'EXISTING' && row.markedForDelete) return sum;
      if (!isDateWithinSelectedWeek(row.plannedStartAt)) return sum;
      return sum + Math.round((row.plannedHours ?? 0) * 2);
    }, 0)
);

// 周内汇总按课次最终日期计算；课包总占用变化只看课时增减，跨周移动本身不改变课包总课时。
const weeklyUnitDelta = computed(() => adjustedWeeklyUnits.value - originalWeeklyUnits.value);
const unitDelta = computed(() => weeklyRows.value.reduce((delta, row) => {
  if (row.readOnly) return delta;
  if (row.rowType === 'NEW') return delta + Math.round((row.plannedHours ?? 0) * 2);
  const originalUnits = row.original ? reservedUnitsForOccurrence(row.original) : 0;
  if (row.markedForDelete) return delta - originalUnits;
  return delta + Math.round((row.plannedHours ?? 0) * 2) - originalUnits;
}, 0));


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
    endTime: scheduleEndTimeForDisplay(s.startTime, s.endTime),
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
      occurrenceDate: start.format('YYYY-MM-DD'),
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

// 周模板选择的是星期和时间；展示首节课日期，避免把当前周的占用误解为同日冲突。
function firstOccurrenceDate(slot: SlotDef): string {
  if (!editForm.startDate) return '—';
  const start = dayjs(editForm.startDate);
  const startWeekday = start.day() === 0 ? 7 : start.day();
  const daysUntilSlot = (slot.weekday - startWeekday + 7) % 7;
  return start.add(daysUntilSlot, 'day').format('MMM D, YYYY');
}

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
async function fetchSchedulePackages(studentId: EntityId, signal?: AbortSignal): Promise<CoursePackageItem[]> {
  const pageSize = 200;
  const query = { studentId, packageStatuses: 'ACTIVE,PAUSED', teachingMode: 'ONE_ON_ONE', pageSize };
  const requestConfig = { signal, timeout: 8000 };
  const first = await fetchCoursePackages({ ...query, pageNum: 1 }, requestConfig);
  const { records, total } = first.data.data;
  if (total <= records.length) return records;
  const pages = Array.from({ length: Math.ceil(total / pageSize) - 1 }, (_, index) => index + 2);
  const remaining = await Promise.all(pages.map(pageNum => fetchCoursePackages({ ...query, pageNum }, requestConfig)));
  return records.concat(...remaining.map(response => response.data.data.records));
}

async function onStudentSelect(id: EntityId, preferredPackageId?: EntityId) {
  // 快速切换学生时丢弃较晚返回的旧请求，避免覆盖当前选择。
  packageLoadAbortController?.abort();
  const abortController = new AbortController();
  packageLoadAbortController = abortController;
  const requestSequence = ++packageLoadSequence;
  preferredPackageForRetry = preferredPackageId;
  selectedStudentId.value = id;
  mobileStudentListExpanded.value = false;
  mobileStudentSearchExpanded.value = false;
  mobilePackageListExpanded.value = true;
  selectedPackageId.value = null;
  studentPackages.value = [];
  allPackageRules.value = {};
  currentWeekOccurrences.value = [];
  editBusyOccurrences.value = [];
  packageLoadError.value = '';
  loadingPackages.value = true;
  let timedOut = false;
  const loadDeadline = window.setTimeout(() => {
    timedOut = true;
    abortController.abort();
    if (requestSequence === packageLoadSequence) {
      studentPackages.value = [];
      packageLoadError.value = 'Loading course packages timed out. Please try again.';
      loadingPackages.value = false;
    }
  }, 15000);
  try {
    const packages = await fetchSchedulePackages(id, abortController.signal);
    if (requestSequence !== packageLoadSequence) return;
    studentPackages.value = packages;
    selectedPackageId.value = preferredPackageId == null
      ? null
      : studentPackages.value.find(pkg => String(pkg.id) === String(preferredPackageId))?.id ?? null;
    if (selectedPackageId.value != null) mobilePackageListExpanded.value = false;
    loadingPackages.value = false;
    // 排课规则用于冲突检查，后台加载即可，不阻塞已取得的课包列表。
    void loadAllPackageRules(studentPackages.value, requestSequence);
  } catch (error) {
    if (requestSequence === packageLoadSequence && (!abortController.signal.aborted || timedOut)) {
      studentPackages.value = [];
      packageLoadError.value = timedOut
        ? 'Loading course packages timed out. Please try again.'
        : `Could not load course packages. ${extractErrorMessage(error)}`;
    }
  } finally {
    window.clearTimeout(loadDeadline);
    if (requestSequence === packageLoadSequence) loadingPackages.value = false;
  }
}

// 由课包加载失败提示调用，沿用当前学生和入口课包，避免用户重新走选择流程。
function retryLoadPackages() {
  if (selectedStudentId.value == null) return;
  void onStudentSelect(selectedStudentId.value, preferredPackageForRetry);
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
  if (packageId == null) return true;
  const { startDate, endExclusiveDate } = currentCalendarWeekRange();
  displayMonday.value = startDate;
  try {
    const records = await fetchAllOccurrences({
      packageId,
      startAt: `${startDate}T00:00:00`,
      endAt: `${endExclusiveDate}T00:00:00`,
    });
    if (sequence === currentWeekLoadSequence) currentWeekOccurrences.value = records;
    return true;
  } catch {
    if (sequence === currentWeekLoadSequence) currentWeekOccurrences.value = [];
    return false;
  }
}

watch(selectedPackageId, () => { void loadCurrentWeekOccurrences(); });

// ─── Package selection ─────────────────────────
function onPackageSelect(pkg: CoursePackageItem) {
  selectedPackageId.value = pkg.id;
  mobilePackageListExpanded.value = false;
}

// 打开停课确认窗；停课保留已开始课次，取消所有未来排课。
function pauseSelectedPackage() {
  const packageToPause = selectedPackage.value;
  const studentId = selectedStudentId.value;
  if (!packageToPause || packageToPause.packageStatus !== 'ACTIVE' || !studentId) return;
  pauseConfirmationVisible.value = true;
}

// 次级操作统一从 More 菜单分发，保留原有课时、打印、详情和停课流程。
function handleScheduleMoreAction(command: string | number | object) {
  if (typeof command !== 'string') return;
  switch (command) {
    case 'package-hours':
      if (selectedPackage.value?.packageStatus === 'ACTIVE') packageHoursDialogVisible.value = true;
      break;
    case 'print-parent':
      void openPrintSheet('parent');
      break;
    case 'print-teacher':
      void openPrintSheet('teacher');
      break;
    case 'detail':
      if (currentRule.value) void openDetailDialog(currentRule.value);
      break;
    case 'pause':
      pauseSelectedPackage();
      break;
    default:
      break;
  }
}

// 打开编辑窗口后读取待处理课次，重排视图内由管理员明确决定是否撤销并回收。
async function requestEditSchedule() {
  const packageToEdit = selectedPackage.value;
  const studentId = selectedStudentId.value;
  if (!packageToEdit || !studentId || !currentRule.value || checkingEditDeduction.value) return;

  reclaimEndedUnconfirmedLessons.value = false;
  pendingStartedLessons.value = [];
  scheduleAllocatedHours.value = null;
  checkingEditDeduction.value = true;
  try {
    await openEditDialog();
    const response = await fetchOneOnOneDeductions({
      studentId,
      packageId: packageToEdit.id,
    });
    if (String(selectedStudentId.value) !== String(studentId)
      || String(selectedPackageId.value) !== String(packageToEdit.id)) return;

    // 同一个课包可能由多位老师负责，必须汇总各老师入口才能列全待处理课次。
    const coursePackages = response.data.data
      .flatMap(teacher => teacher.students)
      .filter(student => String(student.studentId) === String(studentId))
      .flatMap(student => student.packages)
      .filter(item => String(item.packageId) === String(packageToEdit.id));

    scheduleAllocatedHours.value = coursePackages[0]?.allocatedUnits ?? null;

  } catch (error) {
    ElMessage.error(`Unable to check unconfirmed lessons. ${extractErrorMessage(error)}`);
  } finally {
    checkingEditDeduction.value = false;
  }
}

// 将当前学生和课包带到 1-on-1 消课页，便于管理员直接找到待核销课次。
function goToOneOnOneDeduction() {
  const packageToDeduct = selectedPackage.value;
  const studentId = selectedStudentId.value;
  if (!packageToDeduct || !studentId) return;

  pauseConfirmationVisible.value = false;
  void router.push({
    name: 'one-on-one-deduction',
    query: {
      focus: 'deduction',
      studentId: String(studentId),
      packageId: String(packageToDeduct.id),
    },
  });
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
    const match = findSignaturePackage(response.data.data, selectedStudentId.value, selectedPackageId.value);

    if (!match) throw new Error('No signature sheet is available for the selected course.');
    const [history, revisions] = await Promise.allSettled([
      fetchPrivateSignatureHistory(selectedPackageId.value),
      fetchSchedulePrintHistory(selectedPackageId.value),
    ]);
    const warnings: string[] = [];
    if (history.status === 'rejected') warnings.push('Verified historical signature entries could not be loaded.');
    if (revisions.status === 'rejected') warnings.push('Schedule change history could not be loaded.');
    const printPackage = history.status === 'fulfilled'
      ? withSignatureHistory(match.coursePackage, history.value.data.data)
      : match.coursePackage;
    if (preview.closed) return;
    preview.document.open();
    preview.document.write(renderSignatureSheetDocument(
      match.student, printPackage, mode,
      revisions.status === 'fulfilled' ? revisions.value.data.data : [], warnings));
    preview.document.close();
    preview.focus();
  } catch (err) {
    if (preview.closed) return;
    preview.document.body.textContent = extractErrorMessage(err);
  }
}

async function saveStartingHours() {
  const pkg = selectedPackage.value;
  if (!pkg || !selectedStudentId.value || savingStartingHours.value) return;
  startingHoursError.value = '';
  if (pkg.version == null) {
    startingHoursError.value = 'The hours could not be verified. Refresh the page and try again.';
    startingHoursSaveBlocked.value = true;
    return;
  }
  if (!Number.isInteger(startingHoursDraft.value * 2)
    || startingHoursDraft.value < startingHoursMin.value
    || startingHoursDraft.value > startingHoursMax.value) {
    startingHoursError.value = `Enter hours in 0.5-hour steps. The amount must be between ${startingHoursMin.value.toFixed(1)}h and ${startingHoursMax.value.toFixed(1)}h.`;
    return;
  }
  savingStartingHours.value = true;
  const willComplete = startingHoursDraft.value === confirmedDeductionHours.value;
  try {
    await updateCoursePackageStartingHours(pkg.id, startingHoursDraft.value, pkg.version);
  } catch (err) {
    const status = axios.isAxiosError(err) ? err.response?.status : undefined;
    const code = axios.isAxiosError(err) ? err.response?.data?.code : undefined;
    if (status && status >= 400 && status < 500) {
      const reason = packageHoursErrorReason(code, err);
      startingHoursError.value = `Hours were not saved. ${reason}`;
      if (code === 'PACKAGE_CONCURRENT_UPDATE') startingHoursSaveBlocked.value = true;
    } else {
      startingHoursError.value = 'Could not confirm whether the hours were saved. Refresh the page and check the current hours before trying again.';
      startingHoursSaveBlocked.value = true;
    }
    savingStartingHours.value = false;
    return;
  }
  try {
    studentPackages.value = await fetchSchedulePackages(selectedStudentId.value);
    startingHoursCompleted.value = willComplete && !selectedPackage.value;
    const refreshed = await Promise.all([loadAllPackageRules(), loadCurrentWeekOccurrences()]);
    if (refreshed.some(result => !result)) throw new Error('Schedule refresh failed');
    ElMessage.success('Package hours and future schedule updated.');
  } catch {
    startingHoursError.value = 'Hours were saved, but the page could not refresh. Refresh the page to see the latest balance and schedule.';
    startingHoursSaveBlocked.value = true;
  } finally {
    savingStartingHours.value = false;
  }
}

// 将课时保存失败转换成管理员能执行的下一步；未知错误不直接展示内部信息。
function packageHoursErrorReason(code: unknown, error: unknown): string {
  switch (code) {
    case 'ZERO_BALANCE_LESSON_REVIEW_REQUIRED':
    case 'ZERO_BALANCE_SHARED_SCHEDULE':
      return extractErrorMessage(error);
    case 'SCHEDULE_CONFLICT':
      return `${extractErrorMessage(error)} Check the dates and teachers in Edit Schedule, then try again.`;
    case 'INVALID_STARTING_HOURS':
      return 'Starting Hours must be between 0 and the total hours in this package.';
    case 'DEDUCTED_HOURS_BELOW_CONFIRMED':
      return `Starting Hours cannot be less than the ${confirmedDeductionHours.value.toFixed(1)}h already used.`;
    case 'DEDUCTED_HOURS_EXCEED_PACKAGE':
      return 'There are lessons waiting for approval. Review them in 1-on-1 Lessons or leave enough hours for them.';
    case 'INCOMPLETE_SLOT_ALLOCATION':
      return 'Some lessons are missing a teacher, subject, or hours. Complete them in Edit Schedule and try again.';
    case 'TEACHER_NOT_AVAILABLE':
      return 'A teacher on this schedule is no longer available. Choose another teacher in Edit Schedule and try again.';
    case 'LESSON_CHANGED':
    case 'SCHEDULE_CHANGED':
    case 'STALE_SCHEDULE_SLOT':
    case 'PACKAGE_CONCURRENT_UPDATE':
      return 'The hours or lessons changed while you were editing. Refresh the page and try again.';
    case 'AMBIGUOUS_SLOT_LINEAGE':
    case 'SCHEDULE_PERIOD_START_MISSING':
      return 'This student’s schedule needs review. Contact support for help.';
    default:
      return 'Please try again. If the problem continues, contact support for help.';
  }
}

// ─── Load rules for ALL packages ────────────────
async function loadAllPackageRules(
  packages: CoursePackageItem[] = studentPackages.value,
  requestSequence?: number,
) {
  if (packages.length === 0) {
    allPackageRules.value = {};
    return true;
  }
  const results = await Promise.allSettled(
    packages.filter(pkg => pkg.packageStatus === 'ACTIVE').map(async (pkg) => {
      const res = await fetchRecurringScheduleRules({ packageId: pkg.id, ruleStatus: 'ACTIVE', pageSize: 1 });
      const rule = res.data.data.records[0];
      if (!rule) return { packageId: pkg.id, rule: null };
      const detailRes = await fetchRecurringScheduleRule(rule.id);
      return { packageId: pkg.id, rule: { ...rule, ...detailRes.data.data } };
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
  return results.every(result => result.status === 'fulfilled');
}

const totalSlotUnits = computed(() =>
  editForm.slots.reduce((sum, s) => sum + (s.plannedUnits || 0), 0)
);

// 排课槽位保存累计分配额；确认消课只改变余额，不回写已分配课时。
const allocatedHours = computed(() =>
  editForm.slots.reduce((sum, s) => sum + (s.allocatedHours || 0), 0)
);

// 该提示覆盖额度、老师与固定时段变更；具体周期由服务端按实际排课定义判断。
const hasSchedulePlanChanges = computed(() => {
  if (!isEditing.value) return false;
  if (editForm.startDate !== currentRule.value?.startDate) return true;
  if (editForm.slots.length !== originalEditSlots.value.length) return true;
  const originalById = new Map(originalEditSlots.value.map(slot => [String(slot.slotId), slot]));
  return editForm.slots.some(slot => {
    if (slot.slotId == null) return true;
    const original = originalById.get(String(slot.slotId));
    return !original
      || slot.weekday !== original.weekday
      || slot.startTime !== original.startTime
      || slot.endTime !== original.endTime
      || slot.plannedUnits !== original.plannedUnits
      || slot.teacherId !== original.teacherId
      || Number(slot.allocatedHours || 0) !== Number(original.allocatedHours || 0);
  });
});

const hasTeacherHandoffChanges = computed(() => editForm.slots.some(slot => {
  if (slot.slotId == null) return false;
  const original = originalEditSlots.value.find(item => String(item.slotId) === String(slot.slotId));
  return Boolean(original && slot.teacherId !== original.teacherId);
}));

const remainingPackageHours = computed(() => selectedPackage.value
  ? remainingForPackage(selectedPackage.value) : 0);
const otherReservedHours = computed(() => isEditing.value
  ? (currentRule.value?.otherReservedHours ?? 0) : 0);
// 账本余额不变；编辑时释放旧规则的普通未来课次，只预留未处理历史及保留的例外课次。
const allocatableForNewScheduleHours = computed(() => packageHours(Math.max(0, Math.min(
  remainingPackageHours.value,
  (selectedPackage.value?.remainingUnits ?? 0)
    - pendingAdminDeductionHours.value
    - otherReservedHours.value
    - (isEditing.value ? (currentRule.value?.preservedReservedHours ?? 0) : 0),
))));
const allocationExceedsRemaining = computed(() =>
  !isEditing.value && allocatedHours.value > allocatableForNewScheduleHours.value);

async function openCreateDialog() {
  if (selectedPackage.value?.packageStatus !== 'ACTIVE') return;
  allocationSaveError.value = null;
  isEditing.value = false;
  editingRuleId.value = null;
  editFormRef.value?.resetFields();
  Object.assign(editForm, {
    ruleStatus: undefined,
    startDate: '',
    notes: '',
    slots: [],
  });
  originalEditSlots.value = [];
  originalAllocatedHours.value = 0;
  editDialogVisible.value = true;
}

async function openEditDialog() {
  const packageId = selectedPackageId.value;
  const ruleId = currentRule.value?.id;
  if (!packageId || !ruleId) return;
  let detail: any;
  try {
    const latestRules = await fetchRecurringScheduleRules({ packageId, ruleStatus: 'ACTIVE', pageSize: 1 });
    const latestRule = latestRules.data.data.records[0];
    if (!latestRule) {
      ElMessage.error('The schedule changed. Refresh this student before editing.');
      return;
    }
    const response = await fetchRecurringScheduleRule(latestRule.id);
    if (String(selectedPackageId.value) !== String(packageId)) return;
    detail = { ...latestRule, ...response.data.data };
    allPackageRules.value = { ...allPackageRules.value, [String(packageId)]: detail };
  } catch (error) {
    ElMessage.error(`Unable to load the latest schedule allocation. ${extractErrorMessage(error)}`);
    return;
  }
  await ensureScheduleResourceOptions(detail.slots || [], detail.defaultClassroomId);
  allocationSaveError.value = null;
  isEditing.value = true;
  editingRuleId.value = detail.id;
  editFormRef.value?.resetFields();
  Object.assign(editForm, {
    ruleStatus: detail.ruleStatus,
    startDate: detail.startDate,
    notes: detail.notes || '',
    slots: (detail.slots || []).map((s: any) => ({
      slotId: s.id,
      teacherId: s.teacherId == null ? undefined : String(s.teacherId),
      classroomSelection: s.deliveryMode === 'ONLINE'
        ? 'ONLINE'
        : s.classroomId != null || detail.defaultClassroomId != null
          ? `ROOM:${String(s.classroomId ?? detail.defaultClassroomId)}`
          : '',
      subjectName: s.subjectName,
      weekday: s.weekday,
      startTime: s.startTime,
      endTime: scheduleEndTimeForDisplay(s.startTime, s.endTime),
      plannedUnits: s.plannedUnits,
      allocatedHours: s.allocatedUnits != null ? s.allocatedUnits * 0.5 : undefined,
      rolloverAllocatedHours: s.rolloverAllocatedUnits != null ? s.rolloverAllocatedUnits * 0.5 : undefined,
    })),
  });
  unlinkedHistory.value = detail.unlinkedHistory || [];
  pendingStartedLessons.value = (detail.pendingReclaimLessons || [])
    .slice().sort((first: SchedulePendingLesson, second: SchedulePendingLesson) =>
      dayjs(first.plannedStartAt).valueOf() - dayjs(second.plannedStartAt).valueOf());
  Object.keys(selectedUnlinkedBySlot).forEach(key => delete selectedUnlinkedBySlot[key]);
  editForm.slots.forEach(slot => { selectedUnlinkedBySlot[String(slot.slotId)] = []; });
  originalEditSlots.value = editForm.slots.map(slot => ({ ...slot }));
  originalAllocatedHours.value = editForm.slots.reduce((sum, slot) => sum + Number(slot.allocatedHours || 0), 0);
  editMode.value = 'CURRENT';
  activeEditMode = 'CURRENT';
  editModeDrafts.CURRENT = { startDate: editForm.startDate, slots: editForm.slots.map(slot => ({ ...slot })) };
  // 重排的历史继承必须由用户指定，不能把原课位 ID 自动带入新周期。
  editModeDrafts.REPLAN = {
    startDate: todayInAppTimezone(),
    slots: editForm.slots.map(slot => ({ ...slot, slotId: undefined })),
  };
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

// 只拦截带有服务端额度快照的错误；其它排课错误继续走统一反馈逻辑。
function showAllocationSaveError(error: unknown): boolean {
  if (!error || typeof error !== 'object' || !('response' in error)) return false;
  const response = (error as { response?: { data?: { code?: unknown; data?: unknown } } }).response;
  if (response?.data?.code !== 'SLOT_ALLOCATION_EXCEEDS_PACKAGE'
      || !response.data.data || typeof response.data.data !== 'object') {
    return false;
  }

  const details = response.data.data as Record<keyof ScheduleAllocationErrorDetails, unknown>;
  const fields: Array<keyof ScheduleAllocationErrorDetails> = [
    'packageTotalHours', 'packageRemainingHours', 'requestedAllocationHours',
    'availableAllocationHours', 'shortfallHours', 'pendingReviewHours',
    'otherScheduleReservedHours', 'preservedLessonHours', 'transferredFutureLessonHours',
    'currentRuleRetainedHours',
  ];
  if (fields.some(field => !Number.isFinite(Number(details[field])))) return false;

  allocationSaveError.value = Object.fromEntries(
    fields.map(field => [field, Number(details[field])]),
  ) as ScheduleAllocationErrorDetails;
  void nextTick(() => {
    const alert = document.querySelector<HTMLElement>('.edit-schedule__allocation-error');
    const dialogBody = alert?.closest<HTMLElement>('.el-dialog__body');
    if (!alert || !dialogBody) return;
    const alertTop = alert.getBoundingClientRect().top;
    const bodyTop = dialogBody.getBoundingClientRect().top;
    const target = dialogBody.scrollTop + alertTop - bodyTop - 12;
    dialogBody.scrollTo({ top: Math.max(0, target), behavior: 'auto' });
  });
  return true;
}

function validateSlotAllocations(): boolean {
  const incompleteSlot = editForm.slots.find(slot => {
    return !slot.teacherId || !slot.subjectName?.trim()
      || !slot.allocatedHours || slot.allocatedHours <= 0;
  });
  if (!incompleteSlot) return true;
  ElMessage.warning('Each time slot requires a teacher, lesson name, and schedule total hours.');
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
    if (allocationExceedsRemaining.value) {
      ElMessage.warning(`Allocated ${allocatedHours.value.toFixed(1)}h exceeds the ${allocatableForNewScheduleHours.value.toFixed(1)}h available for this schedule.`);
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
          slotId: s.slotId,
          teacherId: s.teacherId,
          deliveryMode: s.classroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON',
          classroomId: s.classroomSelection?.startsWith('ROOM:') ? s.classroomSelection.slice(5) : undefined,
          subjectName: s.subjectName || undefined,
          weekday: s.weekday,
          startTime: s.startTime,
          endTime: scheduleEndTimeForApi(s.endTime),
          plannedUnits: s.plannedUnits,
          allocatedUnits: s.allocatedHours != null ? Math.round(s.allocatedHours * 2) : undefined,
        })),
      });
      ElMessage.success('Schedule created and lessons generated.');
      editDialogVisible.value = false;
      await Promise.all([loadAllPackageRules(), refreshScheduleStudentList()]);
      await loadCurrentWeekOccurrences();
    } catch (err) {
      if (!showAllocationSaveError(err)) showScheduleError(err);
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
    if (allocationExceedsRemaining.value) {
      ElMessage.warning(`Allocated ${allocatedHours.value.toFixed(1)}h exceeds the ${allocatableForNewScheduleHours.value.toFixed(1)}h available for this schedule.`);
      return;
    }
    if (!validateSlotAllocations()) return;
    if (editMode.value === 'REPLAN') {
      if (pendingStartedLessons.value.some(lesson => lesson.inProgress || lesson.status === 'PARTIALLY_CONFIRMED')) {
        ElMessage.warning('Finish or review the lessons marked in progress or needing review before rescheduling.');
        return;
      }
      if (pendingStartedLessons.value.length && !reclaimEndedUnconfirmedLessons.value) {
        ElMessage.warning('Review the unconfirmed lessons above, or choose to withdraw them before saving the new timetable.');
        return;
      }
      const assigned = new Set(editForm.slots.map(slot => String(slot.slotId)).filter(id => id !== 'undefined'));
      const missing = originalEditSlots.value.find(slot => !assigned.has(String(slot.slotId)));
      if (missing) {
        ElMessage.warning(`Assign the lesson history from ${scheduleSlotLabel(missing)} to one new time slot.`);
        return;
      }
    }
    submitting.value = true;
    try {
      const result = await updateRecurringScheduleRule(editingRuleId.value!, {
        editOperation: editMode.value,
        ruleStatus: editForm.ruleStatus,
        startDate: editForm.startDate,
        notes: editForm.notes || undefined,
        reclaimEndedUnconfirmedLessons: editMode.value === 'REPLAN'
          ? reclaimEndedUnconfirmedLessons.value || undefined : undefined,
        expectedPendingLessonSnapshots: editMode.value === 'REPLAN'
          ? pendingStartedLessons.value.map(lesson => lesson.snapshot) : undefined,
        slots: editForm.slots.map((s) => ({
          slotId: s.slotId,
          inheritedOccurrenceIds: editMode.value === 'CURRENT' ? selectedUnlinkedBySlot[String(s.slotId)] : undefined,
          teacherId: s.teacherId,
          deliveryMode: s.classroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON',
          classroomId: s.classroomSelection?.startsWith('ROOM:') ? s.classroomSelection.slice(5) : undefined,
          subjectName: s.subjectName || undefined,
          weekday: s.weekday,
          startTime: s.startTime,
          endTime: scheduleEndTimeForApi(s.endTime),
          plannedUnits: s.plannedUnits,
          allocatedUnits: s.allocatedHours != null ? Math.round(s.allocatedHours * 2) : undefined,
        })),
      });
      const data = result.data.data;
      ElMessage.success(
        `Schedule updated. ${data.generatedOccurrenceCount} new occurrences generated; ` +
        `${data.preservedOccurrenceCount} existing lessons preserved (${data.confirmedOccurrenceCount} confirmed); ` +
        `${data.reclaimedOccurrenceCount || 0} ended unconfirmed lessons reclaimed.`
      );
      reclaimEndedUnconfirmedLessons.value = false;
      editDialogVisible.value = false;
      await Promise.all([loadAllPackageRules(), refreshScheduleStudentList()]);
      await loadCurrentWeekOccurrences();
    } catch (err) {
      if (!showAllocationSaveError(err)) showScheduleError(err);
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
  selectedWeekStartDate.value = currentNaturalWeekStartDate();
  weeklyLoadedWeekStartDate.value = selectedWeekStartDate.value;
  selectedFutureReleaseIds.value = [];
  allWeeklyPackageOccurrences.value = [];
  weeklyBusyOccurrences.value = [];
  weeklyBusyError.value = '';
  weeklyDialogVisible.value = true;
  await loadWeeklyAdjustmentWeek();
}

async function onWeeklyWeekChange() {
  if (hasWeeklyChanges.value || weeklyReason.value.trim() || selectedFutureReleaseIds.value.length) {
    try {
      await ElMessageBox.confirm('Changing the week will discard unsaved adjustments. Continue?', 'Change selected week', {
        confirmButtonText: 'Discard changes',
        cancelButtonText: 'Stay here',
        type: 'warning',
      });
    } catch {
      selectedWeekStartDate.value = weeklyLoadedWeekStartDate.value;
      return;
    }
  }
  weeklyReason.value = '';
  await loadWeeklyAdjustmentWeek();
}

async function loadWeeklyAdjustmentWeek() {
  if (!selectedPackageId.value) return;
  weeklyLoading.value = true;
  selectedRowKey.value = null;
  selectedFutureReleaseIds.value = [];
  weeklyRows.value = [];
  allWeeklyPackageOccurrences.value = [];
  weeklyBusyOccurrences.value = [];
  weeklyBusyError.value = '';
  try {
    const { start, end } = currentNaturalWeekRange();
    const startAt = start.format('YYYY-MM-DDT00:00:00');
    const endAt = end.add(1, 'day').format('YYYY-MM-DDT00:00:00');

    const [allPackageOccurrences, participantBusy] = await Promise.all([
      fetchAllOccurrences({ packageId: selectedPackageId.value }),
      fetchParticipantBusyOccurrences(startAt, endAt),
    ]);
    allWeeklyPackageOccurrences.value = allPackageOccurrences;
    weeklyRows.value = allPackageOccurrences
      .filter(occurrence => occurrence.plannedStartAt >= startAt && occurrence.plannedStartAt < endAt)
      .filter(canShowWeeklyOccurrence)
      .map(toWeeklyAdjustmentRow);
    await ensureScheduleResourceOptions(weeklyRows.value.map(row => ({
      teacherId: row.plannedTeacherId,
      deliveryMode: row.classroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON',
      classroomId: row.classroomSelection?.startsWith('ROOM:')
        ? row.classroomSelection.slice(5)
        : undefined,
    })));

    weeklyBusyOccurrences.value = participantBusy;
    weeklyLoadedWeekStartDate.value = selectedWeekStartDate.value;
  } catch (err) {
    weeklyRows.value = [];
    allWeeklyPackageOccurrences.value = [];
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
      weekStartDate: selectedWeekStartDate.value,
      reason: weeklyReason.value.trim() || undefined,
      releaseFutureOccurrenceIds: futureReleasePreview.value.map(item => item.id),
      items,
    });
    ElMessage.success('Schedule adjustment saved.');
    weeklyDialogVisible.value = false;
    await loadAllPackageRules();

    await loadCurrentWeekOccurrences();
  } catch (err) {
    showScheduleError(err);
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
      subjectName: row.subjectName?.trim(),
      plannedStartAt: row.plannedStartAt,
      plannedEndAt: row.plannedEndAt,
      plannedUnits: Math.round(row.plannedHours * 2),
      plannedTeacherId: normalizeOptionalId(row.plannedTeacherId),
      plannedClassroomId: classroomIdFromSelection(row.classroomSelection),
      deliveryMode: deliveryModeFromSelection(row.classroomSelection),
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
    plannedClassroomId: classroomIdFromSelection(row.classroomSelection),
    deliveryMode: deliveryModeFromSelection(row.classroomSelection),
  };
}

const weeklyImpactMessage = computed(() => {

  const summary = `Added ${addedCount.value}, modified ${modifiedCount.value}, removed ${removedCount.value} lesson(s).`;
  let impact: string;
  if (futureReleaseNeedUnits.value > 0) {
    const rows = futureReleasePreview.value.map(item => `${item.label} ${formatUnitsAsHours(item.units)}`).join('; ');
    impact = `The package has ${formatUnitsAsHours(unallocatedUnitsBeforeAdjustment.value)} unallocated. These future lessons will be reduced by ${formatUnitsAsHours(futureReleaseNeedUnits.value)}: ${rows}.`;
  } else if (unitDelta.value > 0) {
    impact = `The added ${formatUnitsAsHours(unitDelta.value)} will use the package’s unallocated hours. Future lessons will stay unchanged.`;
  } else if (unitDelta.value < 0) {
    impact = `${formatUnitsAsHours(-unitDelta.value)} will return to the package’s unallocated hours. Future lessons will stay unchanged.`;
  } else {
    impact = 'This adjustment changes lesson details only. The recurring weekly schedule and other lesson occurrences will stay unchanged.';
  }
  return `${summary}<br/><br/>${impact}`;
});

function currentNaturalWeekRange() {
  const start = dayjs(`${selectedWeekStartDate.value}T00:00:00`);
  return { start, end: start.add(6, 'day') };
}

function currentNaturalWeekStartDate(): string {
  const today = todayInAppTimezone();
  const weekday = new Date(`${today}T00:00:00Z`).getUTCDay();
  return addCalendarDays(today, -((weekday + 1) % 7));
}

function todayInAppTimezone(): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: APP_TIMEZONE,
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function nowInAppTimezone(): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: APP_TIMEZONE,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}:${values.second}`;
}

function addCalendarDays(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return `${value.getUTCFullYear()}-${String(value.getUTCMonth() + 1).padStart(2, '0')}-${String(value.getUTCDate()).padStart(2, '0')}`;
}

function formatCalendarDate(date: string): string {
  return new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', month: 'short', day: 'numeric' })
    .format(new Date(`${date}T00:00:00Z`));
}

function canShowWeeklyOccurrence(occ: LessonOccurrenceItem): boolean {
  return occ.planningStatus !== 'CANCELLED';
}

function isPackageAllocation(occ: LessonOccurrenceItem): boolean {
  return reservedUnitsForOccurrence(occ) > 0;
}

function reservedUnitsForOccurrence(occ: LessonOccurrenceItem): number {
  if (occ.planningStatus === 'CANCELLED') return 0;
  if (occ.confirmStage === 'CONFIRMED') return 0;
  if (occ.confirmStage === 'PENDING_ADMIN') {
    if (occ.shouldDeduct === 0) return 0;
    if (occ.shouldDeduct === 1 && occ.deductedUnits != null) return Math.max(0, occ.deductedUnits);
  }
  return Math.max(0, occ.plannedUnits ?? 0);
}

function isFutureReleaseCandidate(occ: LessonOccurrenceItem): boolean {
  return ['SCHEDULED', 'ADJUSTED'].includes(occ.planningStatus)
    && isPackageAllocation(occ)
    && occ.lessonRecordId == null
    && occ.recordId == null;
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
    plannedTeacherId: occ.plannedTeacherId == null ? undefined : String(occ.plannedTeacherId),
    classroomSelection: occ.deliveryMode === 'ONLINE'
      ? 'ONLINE'
      : occ.plannedClassroomId == null ? undefined : `ROOM:${String(occ.plannedClassroomId)}`,
    markedForDelete: false,
    readOnly: Boolean(occ.resultStatus) || occ.planningStatus === 'MERGED',
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
    subjectName: weeklySubjectOptions.value.length === 1 ? weeklySubjectOptions.value[0] : '',
    plannedTeacherId: currentRule.value?.slots?.[0]?.teacherId,
    classroomSelection: defaultWeeklyClassroomSelection(),
    markedForDelete: false,
    readOnly: false,
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
  const weekStartDate = selectedWeekStartDate.value;
  const weekEndDate = addCalendarDays(weekStartDate, 7);
  for (const row of weeklyRows.value) {

    if (row.readOnly) continue;
    if (row.rowType === 'EXISTING' && row.markedForDelete) {
      continue;
    }
    const startAt = dayjs(row.plannedStartAt);
    const endAt = dayjs(row.plannedEndAt);
    if (!startAt.isValid() || !endAt.isValid()) {
      return 'Please provide valid start and end times.';
    }
    const startDate = row.plannedStartAt.slice(0, 10);
    const endDate = row.plannedEndAt.slice(0, 10);
    if (row.rowType === 'NEW' && (startDate < weekStartDate || startDate >= weekEndDate)) {
      return 'Adjusted lessons must stay in the selected natural week.';
    }
    if (row.rowType === 'NEW' && !row.subjectName?.trim()) {
      return 'Choose or enter a course name for each new lesson.';
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
    if (endDate !== startDate || endAt.isAfter(startAt.startOf('day').add(1, 'day'))) {
      return 'Lessons must finish by midnight.';
    }
    const busy = isDateWithinSelectedWeek(row.plannedStartAt) && weeklyBusyOccurrences.value.find(occurrence =>
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
    || row.classroomSelection !== (original.deliveryMode === 'ONLINE'
      ? 'ONLINE'
      : original.plannedClassroomId == null ? undefined : `ROOM:${original.plannedClassroomId}`);
}

function classroomIdFromSelection(selection?: string): EntityId | undefined {
  return selection?.startsWith('ROOM:') ? selection.slice(5) : undefined;
}

function deliveryModeFromSelection(selection?: string): 'IN_PERSON' | 'ONLINE' | undefined {
  if (selection === 'ONLINE') return 'ONLINE';
  return 'IN_PERSON';
}

function defaultWeeklyClassroomSelection(): string | undefined {
  const slot = currentRule.value?.slots?.[0];
  if (slot?.deliveryMode === 'ONLINE') return 'ONLINE';
  const classroomId = slot?.classroomId ?? currentRule.value?.defaultClassroomId;
  return classroomId == null ? undefined : `ROOM:${classroomId}`;
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
  const slotAllocatedHours = Number(slot.allocatedHours || 0);
  if (lessonHours <= 0 || slotAllocatedHours <= 0) return 'Set schedule total';
  const fullLessons = Math.floor((slotAllocatedHours + 0.0001) / lessonHours);
  const remainder = Number((slotAllocatedHours - fullLessons * lessonHours).toFixed(1));
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
  return dayjs(date).format('YYYY-MM-DD') < todayInAppTimezone();
}
</script>

<style scoped>
.edit-schedule__mode { margin: 16px 0 4px; }
.edit-schedule__sources { margin: 12px 0; padding: 12px 16px; border: 1px solid #d9e4ef; border-radius: 8px; background: #f7faff; }
.edit-schedule__sources > div { display: flex; justify-content: space-between; gap: 12px; padding-top: 8px; }
.edit-schedule__sources span { color: #64748b; white-space: nowrap; }
.edit-deduction-prompt__lessons {
  max-height: 180px;
  margin: 10px 0 0;
  padding: 0 0 0 20px;
  overflow-y: auto;
  color: #606266;
  font-size: 13px;
  line-height: 1.8;
}

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

.mobile-selection-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
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

.col-panel__hint--error { color: #b42318; }

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
.package-hours-card__impact { display: flex; flex-wrap: wrap; gap: 6px 16px; margin-top: 12px; padding: 10px; border-radius: 6px; background: #eaf3ff; color: #294665; font-size: 12px; }
.package-hours-card__impact small { flex-basis: 100%; color: #536a82; }
.package-hours-card__error { margin-top: 12px; white-space: pre-line; }

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
  flex: 1 1 auto;
  min-width: 160px;
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
  flex: 0 1 auto;
  max-width: 70%;
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

.edit-schedule__balance {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 18px;
  margin: 10px 0 16px;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 6px;
  background: #f5f9ff;
  color: #5d6b82;
  font-size: 12px;
}

.edit-schedule__balance strong,
.edit-schedule__balance-remaining {
  color: #1f3b61;
  font-weight: 600;
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

.edit-schedule__allocation-error {
  margin-top: 10px;
}

.edit-schedule__allocation-error :deep(.el-alert__icon) {
  align-self: flex-start;
  margin-top: 3px;
}

.allocation-error-details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
  margin-top: 6px;
  line-height: 1.5;
  color: #606266;
}

.allocation-error-details strong {
  grid-column: 1 / -1;
  margin-top: 4px;
  color: #303133;
}

.allocation-error-guidance {
  margin: 8px 0 0;
  line-height: 1.5;
  color: #606266;
  font-size: 12px;
}

.slot-teacher-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.slot-teacher-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fbff 0%, #f4f8fd 100%);
}

.slot-teacher-row__time {
  min-width: 0;
  align-self: center;
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.slot-teacher-row__time strong,
.slot-teacher-row__time small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.slot-teacher-row__time strong {
  line-height: 1.35;
  white-space: nowrap;
}

.slot-teacher-row__time small {
  margin-top: 4px;
  color: #7b8797;
  font-size: 11px;
  font-weight: 400;
  line-height: 1.3;
  white-space: nowrap;
}

.slot-teacher-row__fields {
  display: grid;
  grid-template-columns: minmax(145px, 1.1fr) minmax(145px, 1fr) minmax(135px, 0.95fr) minmax(150px, 0.9fr);
  align-items: end;
  gap: 10px;
}

.slot-teacher-row__field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
  margin: 0;
  color: #68788c;
  font-size: 11px;
  font-weight: 600;
}

.slot-teacher-row__field :deep(.el-select),
.slot-teacher-row__field :deep(.el-input),
.slot-teacher-row__field :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.slot-teacher-row__hours-control {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.slot-teacher-row__hours-control :deep(.el-input-number) {
  flex: 1;
}

.slot-teacher-row__hours-control em {
  flex: 0 0 auto;
  color: #7b8797;
  font-size: 12px;
  font-style: normal;
  font-weight: 500;
}

/* This week adjustment */
.weekly-adjustment {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.weekly-adjustment__week-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #606266;
}

.weekly-adjustment__week-picker label {
  font-size: 13px;
  font-weight: 600;
}

.weekly-adjustment__week-picker :deep(.el-select) {
  width: 270px;
}

.weekly-adjustment__week-picker > span {
  color: #909399;
  font-size: 12px;
}

.weekly-future-release {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e6a23c;
  border-radius: 8px;
  background: #fdf6ec;
}

.weekly-future-release__heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #303133;
}

.weekly-future-release__heading span,
.weekly-future-release p {
  color: #7b8797;
  font-size: 12px;
}

.weekly-future-release p {
  margin: 0;
}

.weekly-future-release__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-height: 180px;
  overflow: auto;
  gap: 4px 12px;
}

.weekly-future-release__list :deep(.el-checkbox) {
  margin-right: 0;
  white-space: normal;
}

.weekly-future-release__preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  color: #606266;
  font-size: 12px;
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
  max-height: min(85vh, 900px);
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
  user-select: none;
}


.weekly-cal__head {
  position: sticky;
  top: 0;
  z-index: 3;
  display: grid;
  grid-template-columns: 56px repeat(7, minmax(0, 1fr));
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
  grid-template-columns: 56px repeat(7, minmax(0, 1fr));
  align-items: start;
}


.weekly-cal__times {
  position: relative;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e8edf2;
}

.weekly-cal__time {
  box-sizing: border-box;
  flex: 0 0 var(--weekly-slot-height);
  height: var(--weekly-slot-height);
  font-size: 11px;
  color: #909399;
  text-align: right;
  padding-right: 6px;
  transform: translateY(-6px);
}

.weekly-cal__time--last {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-top: 1px;
  padding-bottom: 1px;
  line-height: 11px;
  transform: none;
}


.weekly-cal__col {
  position: relative;
  border-right: 1px solid #eef1f5;
}

.weekly-cal__col:last-child {
  border-right: none;
}

.weekly-cal__cell {
  box-sizing: border-box;
  height: var(--weekly-slot-height);
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

.weekly-cal__event--readonly {
  cursor: not-allowed;
  opacity: 0.65;
  filter: grayscale(0.25);
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

.weekly-list__item--readonly {
  cursor: not-allowed;
  background: #fafafa;
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

@media (max-width: 640px) {
  .allocation-error-details {
    grid-template-columns: 1fr;
  }

  .weekly-adjustment__week-picker {
    align-items: flex-start;
    flex-direction: column;
  }

  .weekly-adjustment__week-picker :deep(.el-select) {
    width: 100%;
  }

  .weekly-future-release__heading {
    flex-direction: column;
  }

  .weekly-future-release__list {
    grid-template-columns: 1fr;
  }
}

/* ── Dialog ─────────────────────────────────── */
.schedule-dialog :deep(.el-dialog__body) {
  max-height: 76vh;
  overflow-y: auto;
  overflow-x: hidden;
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

@media (max-width: 720px) {
  .slot-teacher-row__fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .schedule-page {
    height: auto;
    min-height: 0;
    overflow: visible;
    padding-bottom: 12px;
  }

  .schedule-panel {
    flex: 0 0 auto;
    min-height: 0;
    padding: 0;
    border: 0;
    background: transparent;
    overflow: visible;
  }

  .schedule-panel__body {
    display: flex;
    flex: 0 0 auto;
    flex-direction: column;
    gap: 6px;
    border: 0;
    overflow: visible;
    background: transparent;
  }

  .col-panel {
    width: 100%;
    flex: none;
    max-height: none;
    overflow: hidden;
    border: 1px solid #d3deea;
    border-radius: 10px;
    box-shadow: 0 1px 3px rgb(25 48 74 / 5%);
  }

  .col-panel--mobile-hidden {
    display: none;
  }

  .col-panel--mobile-collapsed {
    min-height: 48px;
    overflow: hidden;
    border: 0;
    border-bottom: 1px solid #dce5ee;
    border-radius: 0;
    box-shadow: none;
  }

  .col-students:not(.col-panel--mobile-collapsed) {
    min-height: min(58dvh, 520px);
    max-height: calc(100dvh - 220px);
  }

  .col-packages:not(.col-panel--mobile-collapsed) {
    max-height: min(32dvh, 250px);
  }

  .col-students,
  .col-packages {
    width: 100%;
  }

  .mobile-selection-list {
    display: flex;
    min-height: 0;
    flex: 1;
    flex-direction: column;
  }

  .mobile-selection-list .col-panel__body,
  .col-packages > .col-panel__body {
    min-height: 0;
    flex: 1;
    overflow-y: auto;
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
  }

  .mobile-selection-summary {
    position: relative;
    display: flex;
    min-height: 48px;
    width: 100%;
    flex-direction: row;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 6px 38px 6px 12px;
    border: 0;
    background: #fff;
    color: #25364c;
    cursor: pointer;
    text-align: left;
  }

  .mobile-selection-summary__step,
  .mobile-step-label {
    color: #60758f;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: .06em;
  }

  .mobile-selection-summary strong {
    max-width: 100%;
    overflow: hidden;
    color: #1d3048;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-selection-summary small {
    overflow: hidden;
    max-width: 42%;
    color: #687b91;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-selection-summary > .el-icon {
    position: absolute;
    top: 50%;
    right: 12px;
    color: #6481a0;
    transform: translateY(-50%);
  }

  .schedule-panel__header {
    display: none;
  }

  .schedule-panel__title {
    color: #203652;
    font-size: 15px;
  }

  .schedule-student-search {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 36px;
    align-items: start;
    gap: 6px 8px;
    min-height: 68px;
    padding: 9px 10px;
    border-color: #e1e8f0;
  }

  .schedule-student-list-heading {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 2px;
    grid-column: 1;
    grid-row: 1;
  }

  .schedule-student-list-heading strong {
    color: #263b53;
    font-size: 13px;
    line-height: 1.3;
  }

  .schedule-student-list-heading small {
    color: #708198;
    font-size: 11px;
    line-height: 1.35;
  }

  .schedule-student-search .mobile-search-trigger { grid-column: 2; grid-row: 1; }
  .schedule-student-search .el-input { grid-column: 1 / -1; width: 100%; grid-row: 2; }

  .mobile-step-label--package {
    padding: 10px 12px 7px;
  }

  .schedule-student-section-title {
    position: sticky;
    top: 0;
    z-index: 1;
    padding: 8px 10px;
    border-bottom: 1px solid #d8e2ec;
    background: #f1f5f9;
    color: #40566f;
  }

  .schedule-student-group__toggle,
  .schedule-student-group__students .col-item,
  .col-packages .col-item {
    min-height: 44px;
  }

  .col-schedule {
    min-height: 0;
    max-height: none;
    overflow: visible;
    border-color: #cbd8e6;
    border-radius: 10px;
  }

  .col-schedule .col-panel__body {
    display: flex;
    flex-direction: column;
    gap: 10px;
    overflow: visible;
  }

  .schedule-info {
    padding: 8px 10px 4px;
    border: 0;
    border-bottom: 1px solid #e2e9f0;
    border-radius: 0;
    background: #fff;
  }

  .schedule-info__header {
    align-items: center;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 7px 10px;
    margin-bottom: 6px;
  }

  .schedule-info__title {
    display: none;
  }

  .schedule-info__actions {
    display: flex;
    flex-wrap: nowrap;
    width: 100%;
    max-width: none;
    justify-content: flex-start;
    gap: 6px;
  }

  .schedule-info__actions > .el-button,
  .schedule-info__actions > .el-dropdown {
    flex: 0 1 auto;
    min-width: 0;
    margin: 0;
  }

  .schedule-info__actions > .el-button {
    min-height: 36px;
    padding: 0 10px;
    font-size: 12px;
  }

  .schedule-info__actions > .el-dropdown {
    flex: 0 0 auto;
    width: auto;
  }

  .schedule-info__actions > .el-dropdown > .el-button {
    min-height: 36px;
    padding: 0 10px;
  }

  .schedule-summary {
    gap: 0 12px;
  }

  .summary-field {
    min-width: 0;
    flex-direction: row;
    align-items: baseline;
    flex-wrap: wrap;
    gap: 3px 6px;
    padding: 6px 0;
    border-top: 1px solid #edf1f5;
  }

  .summary-field label {
    color: #73849a;
    font-size: 10px;
    white-space: nowrap;
  }

  .summary-field span {
    min-width: 0;
    color: #334861;
    font-size: 12px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }

  .summary-field :deep(.el-tag) {
    height: 22px;
    line-height: 20px;
  }

  .schedule-calendar {
    min-width: 0;
    padding: 12px;
    border: 1px solid #d3deea;
    border-radius: 9px;
    background: #fff;
  }

  .schedule-calendar__heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 10px;
  }

  .schedule-calendar__eyebrow {
    display: block;
    margin-bottom: 3px;
    color: #70849b;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: .06em;
  }

  .schedule-calendar__label {
    display: block;
    margin: 0;
    color: #243a55;
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0;
    text-transform: none;
  }

  .schedule-calendar__count {
    color: #60758f;
    font-size: 11px;
    white-space: nowrap;
  }

  .schedule-calendar :deep(.wts-grid-scroll--pinch) {
    max-height: min(52dvh, 480px);
    border: 1px solid #d8e2ec;
    border-radius: 7px;
    background: #fff;
  }

  .schedule-none {
    border: 1px dashed #cbd8e6;
    border-radius: 9px;
    background: #f8fafc;
  }

  .schedule-info__participants {
    color: #536981;
    line-height: 1.45;
  }

  .schedule-dialog :deep(.el-dialog__body) {
    padding: 12px;
  }

  /* Edit Schedule dialog — 移动端按钮/字号统一 */
  .edit-schedule__info { margin-bottom: 12px; padding: 10px 12px; }
  .edit-info-grid { gap: 16px; }
  .edit-schedule__balance { gap: 6px 14px; margin: 8px 0 12px; font-size: 12px; }
  .edit-schedule__hint { margin-bottom: 8px; font-size: 12px; line-height: 1.45; }

  .edit-schedule__mode {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    width: 100%;
    margin: 14px 0 8px;
  }
  .edit-schedule__mode :deep(.el-radio-button) { width: 100%; }
  .edit-schedule__mode :deep(.el-radio-button + .el-radio-button) { margin-left: 0; }
  .edit-schedule__mode :deep(.el-radio-button__inner) {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    min-height: 44px;
    padding: 6px 8px;
    border: 1px solid #dbe4ef !important;
    border-radius: 10px !important;
    background: #fff;
    color: #4b5b70;
    font-size: 12px;
    font-weight: 600;
    line-height: 1.3;
    white-space: normal;
    text-align: center;
    box-shadow: none !important;
  }
  .edit-schedule__mode :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
    background: #409eff;
    border-color: #409eff !important;
    color: #fff;
  }

  .edit-schedule__footer { width: 100%; gap: 10px; }
  .edit-schedule__footer .el-button { flex: 1 1 0; min-height: 44px; font-size: 14px; }

  .slot-teacher-row__fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 9px;
  }

  /* 时段卡片内的字号与弹窗其他文字对齐 */
  .slot-teacher-row__time strong { font-size: 13px; }
  .slot-teacher-row__time small { font-size: 11px; }
  .slot-teacher-row__field { font-size: 12px; }
  .slot-teacher-row__field :deep(.el-input__inner),
  .slot-teacher-row__field :deep(.el-select__placeholder),
  .slot-teacher-row__field :deep(.el-select__selected-item) { font-size: 13px; }
  .slot-teacher-row__hours-control em { font-size: 12px; }
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
