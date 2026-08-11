<template>
  <div class="adj-page" :class="{ 'adj-page--group-lessons': isGroupClassLessonsPage, 'adj-page--embedded': embedded }">
    <section v-if="isGroupClassLessonsPage && !embedded" class="adj-group-header">
      <div>
        <h2>Group Class Lessons</h2>
        <p>Select a class, then confirm each lesson to record attendance and deduct the lesson for eligible students.</p>
      </div>
    </section>

    <div v-if="!isGroupClassLessonsPage" class="adj-tabs">
      <button
        class="adj-tab"
        :class="{ 'adj-tab--active': activeTab === 'one-on-one' }"
        @click="activeTab = 'one-on-one'"
      >1-on-1 Lessons</button>
      <button
        class="adj-tab"
        :class="{ 'adj-tab--active': activeTab === 'class' }"
        @click="activeTab = 'class'"
      >Group Classes</button>
    </div>

    <!-- ====== 1v1 Tab ====== -->
    <div v-if="activeTab === 'one-on-one'" class="adj-workspace">

      <aside class="adj-sidebar">
        <div class="adj-sidebar__search">
          <el-input v-model="studentKeyword" placeholder="Search student..." clearable size="small" />
        </div>
        <div class="adj-sidebar__list" v-loading="studentsLoading">
          <div
            v-for="s in filteredStudents"
            :key="s.id"
            class="adj-sidebar__item"
            :class="{ 'adj-sidebar__item--active': selectedStudentId === s.id }"
            @click="selectStudent(s.id)"
          >
            <div class="adj-sidebar__name">{{ s.studentName }}</div>
          </div>
          <div v-if="!studentsLoading && filteredStudents.length === 0" class="adj-sidebar__empty">
            No students found.
          </div>
        </div>
      </aside>


      <main class="adj-content">
        <div v-if="!selectedStudentId" class="adj-content__placeholder">
          Select a student from the left panel to view their packages.
        </div>

        <div v-else-if="packagesLoading" class="adj-content__loading" v-loading="packagesLoading" />

        <div v-else-if="packages.length === 0" class="adj-content__placeholder">
          No 1v1 course packages for this student.
        </div>


        <div v-else class="adj-packages">
          <div
            v-for="pkg in packages"
            :key="pkg.id"
            class="pkg-card"
            :class="{ 'pkg-card--expanded': selectedPackageId === String(pkg.id) }"
          >

            <div class="pkg-card__header" @click="selectPackage(pkg.id)">
              <div class="pkg-card__info">
                <span class="pkg-card__name">{{ pkg.packageName }}</span>
                <span class="pkg-card__badge">{{ pkg.remainingUnits }}h left</span>
                <span v-if="occurrenceCounts[String(pkg.id)]" class="pkg-card__count">{{ occurrenceCounts[String(pkg.id)] }} lessons</span>
              </div>
              <div class="pkg-card__meta">
                <span v-if="packageLastDates[String(pkg.id)]" class="pkg-card__last">
                  Last: {{ packageLastDates[String(pkg.id)] }}
                </span>
                <el-icon class="pkg-card__arrow" :class="{ 'pkg-card__arrow--open': selectedPackageId === String(pkg.id) }">
                  <ArrowDown />
                </el-icon>
              </div>
            </div>


            <div v-if="selectedPackageId === String(pkg.id)" class="pkg-card__body">
              <div class="pkg-card__toolbar">
                <div>
                  <div class="pkg-card__toolbar-title">This week lessons</div>
                  <div class="pkg-card__toolbar-hint">Temporary changes here only affect the current natural week.</div>
                </div>
              </div>
              <div v-loading="occurrencesLoading" class="pkg-occ-list">
                <div v-for="occ in occurrences" :key="occ.id" class="pkg-occ-row" :class="dayMarkerClass(occ.plannedStartAt)">

                  <div class="pkg-occ__marker">
                    <el-tag v-if="isToday(occ.plannedStartAt)" size="small" type="success" effect="dark">today</el-tag>
                    <el-tag v-else-if="isPast(occ.plannedStartAt)" size="small" type="info">prev</el-tag>
                    <el-tag v-else size="small">next</el-tag>
                  </div>

                  <div class="pkg-occ__time">
                    <div class="pkg-occ__date">{{ formatDate(occ.plannedStartAt) }}</div>
                    <div class="pkg-occ__range">{{ formatTimeRange(occ.plannedStartAt, occ.plannedEndAt) }}</div>
                  </div>

                  <el-tooltip :content="occ.plannedTeacherName || 'No teacher assigned'" placement="top" :show-after="300">
                    <div class="pkg-occ__teacher">{{ occ.plannedTeacherName || '—' }}</div>
                  </el-tooltip>
                  <el-tooltip :content="occ.plannedClassroomName || 'No classroom assigned'" placement="top" :show-after="300">
                    <div class="pkg-occ__room">{{ occ.plannedClassroomName || '—' }}</div>
                  </el-tooltip>

                  <div class="pkg-occ__hours">{{ (occ.plannedUnits * 0.5).toFixed(1) }}h</div>

                  <div class="pkg-occ__status">
                    <el-tag v-if="isPendingAdmin(occ)" size="small" type="warning">
                      Pending Admin Review
                    </el-tag>
                    <el-tooltip
                      v-else-if="isRejected(occ)"
                      :content="occ.rejectReason ? `Reason: ${occ.rejectReason}` : 'Admin rejected this confirmation. Teacher can submit again.'"
                      placement="top"
                    >
                      <el-tag size="small" type="danger">Rejected - Teacher Action Required</el-tag>
                    </el-tooltip>
                    <el-tooltip v-else-if="occ.resultStatus" :content="resultStatusLabel(occ.resultStatus)" placement="top" :show-after="300">
                      <el-tag size="small" :type="resultStatusTag(occ.resultStatus)">
                        {{ resultStatusLabel(occ.resultStatus) }}
                      </el-tag>
                    </el-tooltip>
                    <el-tag v-else-if="occ.planningStatus === 'CANCELLED'" size="small" type="danger">Cancelled</el-tag>
                    <el-tooltip v-else :content="unconfirmedStatusHint(occ)" placement="top" :show-after="300">
                      <el-tag size="small" :type="unconfirmedStatusTag(occ)">
                        {{ unconfirmedStatusLabel(occ) }}
                      </el-tag>
                    </el-tooltip>
                  </div>

                  <div class="pkg-occ__actions">
                    <div v-if="canAdminReviewOccurrence(occ)" class="admin-review-action">
                      <el-tooltip :content="adminConfirmTooltip(occ)" placement="top">
                        <el-button :type="adminReviewButtonType(occ)" size="small" @click="openAdminConfirmDialog(occ)">
                          {{ adminReviewButtonLabel }}
                        </el-button>
                      </el-tooltip>
                    </div>
                    <el-button
                      v-if="canAdminRejectOccurrence(occ)"
                      type="danger" size="small" plain
                      @click="openRejectDialog(occ)"
                    >Reject</el-button>
                    <el-button
                      v-if="isTeacherRole && isPendingAdmin(occ)"
                      type="warning" size="small" plain disabled
                    >Pending Admin Review</el-button>
                    <el-button
                      v-if="canOpenNormalConfirm(occ)"
                      type="success" size="small" plain
                      @click="openConfirmDialog(occ)"
                    >{{ isRejected(occ) ? 'Confirm Again' : 'Submit attendance' }}</el-button>
                    <el-button
                      v-if="occ.resultStatus && !isRejected(occ)"
                      size="small" plain
                      @click="openDetailDialog(occ)"
                    >Detail</el-button>
                  </div>
                </div>

                <div v-if="!occurrencesLoading && occurrences.length === 0" class="pkg-occ-empty">
                  No lesson records in this package.
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- ====== Class Tab ====== -->
    <div v-if="activeTab === 'class'" class="adj-workspace">

      <aside class="adj-sidebar">
        <div class="adj-sidebar__search">
          <el-input v-model="classKeyword" placeholder="Search course, grade, class..." clearable size="small" />
        </div>
        <div class="adj-sidebar__list" v-loading="classesLoading">
          <div class="class-drill-header">
            <button
              v-if="classDrillLevel !== 'course'"
              type="button"
              class="class-drill-back"
              @click="goBackClassDrill"
            >
              ← Back
            </button>
            <div>
              <strong>{{ classDrillTitle }}</strong>
              <span>{{ classDrillSubtitle }}</span>
            </div>
          </div>
          <div
            v-if="classDrillLevel === 'course'"
            class="class-drill-list"
          >
            <button
              v-for="group in classCourseGroups"
              :key="group.key"
              type="button"
              class="class-drill-card"
              @click="selectClassCourse(group.key)"
            >
              <span class="class-drill-card__main">{{ group.label }}</span>
            </button>
          </div>

          <div
            v-else-if="classDrillLevel === 'grade'"
            class="class-drill-list"
          >
            <button
              v-for="group in classGradeGroups"
              :key="group.key"
              type="button"
              class="class-drill-card"
              @click="selectClassGrade(group.key)"
            >
              <span class="class-drill-card__main">{{ group.label }}</span>
            </button>
          </div>

          <div v-else class="class-drill-list">
            <button
              v-for="clz in drilldownClasses"
              :key="clz.id"
              type="button"
              class="adj-sidebar__item class-drill-class"
              :class="{ 'adj-sidebar__item--active': selectedClassId === String(clz.id) }"
              @click="selectClass(clz.id)"
            >
              <span class="class-drill-class__times">
                <span
                  v-for="slot in classTimeSlotLabels(clz)"
                  :key="slot"
                  class="class-time-chip"
                >
                  {{ slot }}
                </span>
              </span>
            </button>
          </div>

          <div v-if="!classesLoading && filteredClasses.length === 0" class="adj-sidebar__empty">
            No classes found.
          </div>
        </div>
      </aside>


      <main class="adj-content">
        <div v-if="!selectedClassId" class="adj-content__placeholder">
          Select a class from the left panel to manage.
        </div>

        <div v-else class="class-detail">

          <div class="class-detail__header">
            <div class="class-detail__title-row">
              <div class="class-detail__title">{{ selectedClass?.className }}</div>
              <el-button
                v-if="isNonTeacherRole"
                type="primary"
                size="small"
                plain
                @click="openClassScheduleDialog"
              >
                Edit Schedule
              </el-button>
            </div>
            <div class="class-detail__info-grid">
              <div class="class-detail__info-item" v-if="selectedClass?.teacherName">
                <span class="class-detail__info-label">Teacher</span>
                <span class="class-detail__info-value">{{ selectedClass.teacherName }}</span>
              </div>
              <div class="class-detail__info-item" v-if="selectedClass?.classroomName">
                <span class="class-detail__info-label">Classroom</span>
                <span class="class-detail__info-value">{{ selectedClass.classroomName }}</span>
              </div>
              <div class="class-detail__info-item" v-if="selectedClass?.scheduleDesc">
                <span class="class-detail__info-label">Schedule</span>
                <span class="class-detail__info-value">{{ selectedClass.scheduleDesc }}</span>
              </div>
            </div>
            <div v-if="selectedClass?.description" class="class-detail__desc">{{ selectedClass.description }}</div>
          </div>


          <div class="class-section">
            <div class="class-section__header">
              <h3>Enrolled Students</h3>
              <el-button size="small" plain :disabled="!hasExpiringStudents" @click="openRenewalReminders">
                Review Renewal Reminders
              </el-button>
            </div>
            <el-table :data="enrolledStudents" v-loading="classStudentsLoading" size="small" max-height="220" stripe>
              <el-table-column label="Student" min-width="130">
                <template #default="{ row }">
                  <el-button class="student-link" text type="primary" @click.stop="openStudentProfile(row)">
                    {{ row.studentName }}
                  </el-button>
                </template>
              </el-table-column>
              <el-table-column label="Sessions" min-width="130">
                <template #default="{ row }">
                  <div class="session-balance">
                    <strong>{{ formatClassSessionBalance(row) }}</strong>
                    <span>{{ classSessionBalanceHint(row) }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="Enrolled" prop="enrollmentDate" min-width="100" sortable>
                <template #default="{ row }">{{ formatDateShort(row.enrollmentDate) }}</template>
              </el-table-column>
              <el-table-column label="Expiry" prop="expiryDate" min-width="100" sortable>
                <template #default="{ row }">{{ formatDateShort(row.expiryDate) }}</template>
              </el-table-column>
              <el-table-column label="Expiry Status" width="130"
                :filters="expiryStatusFilters" :filter-method="filterExpiryStatus">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.expiryStatus === 'EXPIRED' ? 'danger' : row.expiryStatus === 'EXPIRING_SOON' ? 'warning' : 'success'"
                  >
                    {{ row.expiryStatus === 'EXPIRED' ? 'Expired' : row.expiryStatus === 'EXPIRING_SOON' ? 'Expiring Soon' : 'Normal' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>


          <div class="class-section">
            <div class="class-section__header">
              <h3>Lesson Occurrences</h3>
            </div>
            <div v-if="classOccurrences.length === 0 && !occurrencesLoading" class="class-empty">
              No occurrences for this class.
            </div>
            <el-table
              v-loading="occurrencesLoading"
              :data="classOccurrences"
              :max-height="520"
              border
              stripe
              size="small"
              class="class-occ-table sheet-table"
              :row-class-name="classOccurrenceRowClassName"
            >
              <el-table-column label="Lesson details" min-width="220" align="center" header-align="center">
                <template #default="{ row }">
                  <div class="class-lesson-main">
                    <strong>{{ formatDate(row.plannedStartAt) }}</strong>
                    <span>{{ formatTimeRange(row.plannedStartAt, row.plannedEndAt) }}</span>
                    <el-tag class="status-pill" size="small" :type="classOccurrenceStatusTag(row)">
                      {{ classOccurrenceStatusLabel(row) }}
                    </el-tag>
                    <span>Teacher: {{ displayResourceName(row.plannedTeacherName) }}</span>
                    <span>Room: {{ displayResourceName(row.plannedClassroomName) }}</span>
                    <span>Hours: {{ (row.plannedUnits * 0.5).toFixed(1) }}h</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="Action"
                width="250"
                fixed="right"
                class-name="no-print"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <div class="class-action-cell">
                    <el-button
                      v-if="canAdjustClassOccurrence(row)"
                      size="small"
                      plain
                      @click="openClassAdjustDialog(row)"
                    >
                      Adjust
                    </el-button>
                    <el-tooltip v-if="canAdminReviewOccurrence(row)" :content="adminConfirmTooltip(row)" placement="top">
                      <el-button
                        class="action-primary"
                        :type="adminReviewButtonType(row)"
                        size="small"
                        @click="openAdminConfirmDialog(row)"
                      >
                        {{ adminReviewButtonLabel }}
                      </el-button>
                    </el-tooltip>
                    <el-button
                      v-if="canAdminRejectOccurrence(row)"
                      type="danger"
                      size="small"
                      plain
                      @click="openRejectDialog(row)"
                    >
                      Reject
                    </el-button>
                    <el-button
                      v-if="isTeacherRole && isPendingAdmin(row)"
                      type="warning"
                      size="small"
                      plain
                      disabled
                    >
                      Pending Admin Review
                    </el-button>
                    <el-button
                      v-if="canOpenNormalConfirm(row)"
                      class="action-primary"
                      type="success"
                      size="small"
                      plain
                      @click="openClassConfirmDialog(row)"
                    >
                      {{ isRejected(row) ? 'Confirm Again' : 'Submit attendance' }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </main>
    </div>

    <!-- ====== Class Weekly Schedule Dialog ====== -->
    <el-dialog
      v-model="classScheduleDialogVisible"
      title="Edit Class Schedule"
      width="920px"
      :close-on-click-modal="false"
      class="adj-dialog class-schedule-dialog"
    >
      <el-alert
        title="Saving will rebuild future unconfirmed lessons. Confirmed lessons stay unchanged."
        type="info"
        :closable="false"
        show-icon
        class="class-schedule-alert"
      />
      <el-form ref="classScheduleFormRef" :model="classScheduleForm" :rules="classScheduleFormRules" label-width="110px">
        <div class="class-schedule-form-grid">
          <el-form-item label="Default teacher">
            <el-select v-model="classScheduleForm.teacherId" placeholder="No default teacher" clearable filterable style="width: 100%;">
              <el-option v-for="t in teachers" :key="t.id" :label="t.teacherName" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="Classroom">
            <el-select v-model="classScheduleForm.classroomId" placeholder="No default classroom" clearable filterable style="width: 100%;">
              <el-option v-for="c in classrooms" :key="c.id" :label="c.roomName" :value="c.id" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="Weekly Times" prop="slots">
          <WeeklyTimeSelector v-model="classScheduleForm.slots" hide-hint />
        </el-form-item>
        <div v-if="classScheduleForm.slots.length > 0" class="class-slot-teacher-list">
          <div
            v-for="(slot, index) in classScheduleForm.slots"
            :key="`${slot.weekday}-${slot.startTime}-${index}`"
            class="class-slot-teacher-row"
          >
            <span>{{ classScheduleSlotLabel(slot) }}</span>
            <el-select v-model="slot.teacherId" clearable filterable placeholder="Use default teacher">
              <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
            </el-select>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="classScheduleDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitClassSchedule">Save Schedule</el-button>
      </template>
    </el-dialog>

    <!-- ====== Class Schedule Adjustment Dialog ====== -->
    <el-dialog
      v-model="classAdjustDialogVisible"
      :title="`Adjust Class Lesson — ${selectedOcc?.lessonNo}`"
      width="560px"
      :close-on-click-modal="false"
      class="adj-dialog"
    >
      <el-form ref="classAdjustFormRef" :model="classAdjustForm" :rules="classAdjustFormRules" label-width="130px">
        <el-form-item label="Teacher">
          <el-select v-model="classAdjustForm.plannedTeacherId" placeholder="Keep current teacher" clearable filterable style="width: 100%;">
            <el-option v-for="t in teachers" :key="t.id" :label="t.teacherName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Classroom">
          <el-select v-model="classAdjustForm.plannedClassroomId" placeholder="Keep current classroom" clearable filterable style="width: 100%;">
            <el-option v-for="c in classrooms" :key="c.id" :label="c.roomName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Start Time" prop="plannedStartAt">
          <el-date-picker
            v-model="classAdjustForm.plannedStartAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="Select start time"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="End Time" prop="plannedEndAt">
          <el-date-picker
            v-model="classAdjustForm.plannedEndAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="Select end time"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="Reason" prop="reason">
          <el-input v-model="classAdjustForm.reason" type="textarea" :rows="2" placeholder="Why is this class lesson adjusted?" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classAdjustDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitClassAdjustment">Save Adjustment</el-button>
      </template>
    </el-dialog>

    <!-- ====== Confirm Dialog ====== -->
    <el-dialog
      v-model="confirmDialogVisible"
      :title="confirmDialogTitle"
      width="620px"
      :close-on-click-modal="false"
      class="adj-dialog"
      @close="classConfirmMode = false"
    >
      <el-form ref="confirmFormRef" :model="confirmForm" :rules="confirmFormRules" label-width="160px">
        <el-alert
          v-if="showAdminDirectConfirmWarning"
          :title="adminDirectConfirmWarningTitle"
          type="error"
          :closable="false"
          show-icon
          class="admin-direct-alert"
        />
        <el-form-item label="Result Status" prop="resultStatus">
          <el-select v-model="confirmForm.resultStatus" placeholder="Select status" style="width: 100%;">
            <el-option label="Completed" value="COMPLETED" />
            <el-option v-if="!classConfirmMode" label="Formal Leave" value="FORMAL_LEAVE" />
            <el-option v-if="!classConfirmMode" label="No Show" value="NO_SHOW" />
            <el-option v-if="isNonTeacherRole" label="Cancelled" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="Actual Teacher" prop="actualTeacherId">
          <el-select v-model="confirmForm.actualTeacherId" placeholder="Select teacher" filterable style="width: 100%;">
            <el-option v-for="t in teachers" :key="t.id" :label="t.teacherName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Actual Classroom">
          <el-select v-model="confirmForm.actualClassroomId" placeholder="Defaults to planned classroom" clearable filterable style="width: 100%;">
            <el-option v-for="c in classrooms" :key="c.id" :label="c.roomName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="confirmForm.resultStatus === 'COMPLETED'" label="Actual Start Time">
          <el-date-picker
            v-model="confirmForm.actualStartAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="Defaults to planned start"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item v-if="confirmForm.resultStatus === 'COMPLETED'" label="Actual End Time">
          <div class="end-time-display">{{ computedEndTime || '—' }}</div>
        </el-form-item>
        <el-form-item v-if="confirmForm.resultStatus === 'COMPLETED'" label="Hours">
          <div class="units-input-row">
            <el-input-number
              v-model="confirmForm.actualHours"
              :min="0" :max="10" :precision="1" :step="0.5"
              :class="{ 'hours-input--changed': hoursChangedByTeacher }"
              style="width: 120px;"
            />
          </div>
          <div v-if="hoursChangedByTeacher" class="form-hint form-hint--warning">
            Teacher changed hours from {{ plannedHoursForReview?.toFixed(1) }}h to {{ (confirmForm.actualHours ?? 0).toFixed(1) }}h
          </div>
          <div v-else class="form-hint">0.5h increments</div>
        </el-form-item>
        <el-form-item label="Business Effect">
          <el-alert :title="deductionPreview" type="info" :closable="false" show-icon />
        </el-form-item>
        <el-form-item label="Notes">
          <el-input v-model="confirmForm.notes" type="textarea" :rows="2" placeholder="Optional notes" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConfirm">{{ confirmDialogSubmitLabel }}</el-button>
      </template>
    </el-dialog>

    <!-- ====== Reject Dialog ====== -->
    <el-dialog
      v-model="rejectDialogVisible"
      :title="`Reject Lesson — ${selectedOcc?.lessonNo}`"
      width="480px"
      :close-on-click-modal="false"
      class="adj-dialog"
    >
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectFormRules" label-width="110px">
        <el-form-item label="Reason" prop="rejectReason">
          <el-input
            v-model="rejectForm.rejectReason"
            type="textarea"
            :rows="3"
            placeholder="Enter reject reason"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">Cancel</el-button>
        <el-button type="danger" :loading="submitting" @click="submitReject">Reject</el-button>
      </template>
    </el-dialog>

    <!-- ====== Detail Dialog ====== -->
    <el-dialog
      v-model="detailDialogVisible"
      title="Lesson Detail"
      width="540px"
      :close-on-click-modal="false"
      class="adj-dialog"
    >
      <div v-if="detailData" class="lesson-detail" v-loading="detailLoading">
        <div class="detail-section">
          <h4 class="detail-section__title">Lesson info</h4>
          <div class="detail-grid">
            <div class="detail-field"><label>Lesson No.</label><span>{{ detailData.lessonNo }}</span></div>
            <div class="detail-field">
              <label>Type</label>
              <el-tag size="small" :type="detailData.occurrenceType === 'MAKEUP' ? 'warning' : ''">
                {{ detailData.occurrenceType === 'MAKEUP' ? 'Makeup' : detailData.occurrenceType === 'MANUAL' ? 'Manual' : 'Regular' }}
              </el-tag>
            </div>
            <div class="detail-field"><label>Student</label><span>{{ detailData.studentName }}</span></div>
            <div class="detail-field"><label>Package</label><span>{{ detailData.packageName || '—' }}</span></div>
            <div class="detail-field"><label>Teacher</label><span>{{ detailData.plannedTeacherName ?? '—' }}</span></div>
            <div class="detail-field"><label>Classroom</label><span>{{ detailData.plannedClassroomName ?? '—' }}</span></div>
            <div class="detail-field"><label>Start time</label><span>{{ detailData.plannedStartAt }}</span></div>
            <div class="detail-field"><label>End time</label><span>{{ detailData.plannedEndAt }}</span></div>
            <div class="detail-field"><label>Hours</label><span>{{ (detailData.plannedUnits * 0.5).toFixed(1) }}</span></div>
            <div class="detail-field">
              <label>Status</label>
              <el-tag size="small" :type="detailData.planningStatus === 'SCHEDULED' ? '' : 'warning'">
                {{ detailData.planningStatus === 'SCHEDULED' ? 'Scheduled' : detailData.planningStatus === 'ADJUSTED' ? 'Adjusted' : detailData.planningStatus }}
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
        <el-button @click="detailDialogVisible = false">Close</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ArrowDown } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import dayjs from 'dayjs';

import { extractErrorMessage } from '@/api/http';
import { fetchAllClassrooms, fetchAllStudents, fetchAllTeachers } from '@/api/base-data';
import { createScheduleAdjustment, fetchLessonOccurrences, fetchClassGroups, fetchClassStudents, fetchClassOccurrences, confirmClassLesson, rejectClassLesson, deleteClassGroup, updateClassSchedule } from '@/api/schedule';
import { fetchCoursePackages } from '@/api/enrollment';
import { adminConfirmLessonRecord, confirmLesson, fetchLessonRecords, rejectLessonRecord, type LessonConfirmPayload } from '@/api/lesson';
import { useAuthStore } from '@/stores/auth';
import { isAdminOrStaffUser, isTeacherUser } from '@/types/auth';
import type { CoursePackageItem, EntityId, LessonOccurrenceItem, ClassGroupItem, ClassEnrolledStudentItem, ClassOccurrenceItem, LessonRecordItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { SlotDef } from '@/components/WeeklyTimeSelector.vue';
import { ElMessageBox } from 'element-plus';

const props = withDefaults(defineProps<{
  mode?: 'auto' | 'all' | 'one-on-one' | 'class';
  embedded?: boolean;
}>(), {
  mode: 'auto',
  embedded: false,
});

// ─── State ──────────────────────────────────────────────
const router = useRouter();
const route = useRoute();
const embedded = computed(() => props.embedded);
const isGroupClassLessonsPage = computed(() => props.mode === 'class' || route.path === '/group-class-lessons');
const activeTab = ref<'one-on-one' | 'class'>(
  props.mode === 'class' || isGroupClassLessonsPage.value ? 'class' : 'one-on-one'
);


const studentsLoading = ref(false);
const students = ref<{ id: EntityId; studentName: string }[]>([]);
const studentKeyword = ref('');
const selectedStudentId = ref<EntityId | null>(null);


const packagesLoading = ref(false);
const packages = ref<CoursePackageItem[]>([]);
const selectedPackageId = ref<EntityId | null>(null);


const occurrencesLoading = ref(false);
const occurrences = ref<LessonOccurrenceItem[]>([]);


const packageLastDates = reactive<Record<string, string>>({});

const occurrenceCounts = reactive<Record<string, number>>({});


const classKeyword = ref('');
const classesLoading = ref(false);
const classes = ref<ClassGroupItem[]>([]);
const selectedClassId = ref<EntityId | null>(null);
const classDrillLevel = ref<'course' | 'grade' | 'class'>('course');
const selectedClassCourseKey = ref('');
const selectedClassGradeKey = ref('');
const selectedClass = computed(() => classes.value.find(c => String(c.id) === selectedClassId.value) ?? null);
const enrolledStudents = ref<ClassEnrolledStudentItem[]>([]);
const classOccurrences = ref<ClassOccurrenceItem[]>([]);
const classStudentsLoading = ref(false);
const deleting = ref(false);


const teachers = ref<{ id: EntityId; teacherName: string }[]>([]);
const classrooms = ref<{ id: EntityId; roomName: string }[]>([]);


const authStore = useAuthStore();
const submitting = ref(false);
const selectedOcc = ref<ConfirmableOccurrence | null>(null);
const confirmSubmitMode = ref<'normal' | 'admin-final'>('normal');
const confirmDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const detailData = ref<LessonOccurrenceItem | null>(null);
const rejectDialogVisible = ref(false);
const classScheduleDialogVisible = ref(false);
const classAdjustDialogVisible = ref(false);
const confirmFormRef = ref<FormInstance>();
const rejectFormRef = ref<FormInstance>();
const classScheduleFormRef = ref<FormInstance>();
const classAdjustFormRef = ref<FormInstance>();

const isTeacherRole = computed(() => isTeacherUser(authStore.currentUser));
const isAdminRole = computed(() => isAdminOrStaffUser(authStore.currentUser));
const isNonTeacherRole = computed(() => !isTeacherRole.value);


const filteredStudents = computed(() => {
  if (!studentKeyword.value) return students.value;
  const kw = studentKeyword.value.toLowerCase();
  return students.value.filter(s => s.studentName.toLowerCase().includes(kw));
});


const filteredClasses = computed(() => {
  if (!classKeyword.value) return classes.value;
  const kw = classKeyword.value.toLowerCase();
  return classes.value.filter(c => [
    c.className,
    classProductLabel(c),
    c.productName,
    c.registerClass,
    c.courseType,
    classGradeLabel(c),
    c.teacherName,
    c.classroomName,
    classScheduleSummary(c),
    classTimeSlotLabel(c),
  ].some(value => String(value ?? '').toLowerCase().includes(kw)));
});

type ClassGroupSummary = {
  key: string;
  label: string;
};

const classCourseGroups = computed(() =>
  summarizeClassGroups(filteredClasses.value, classProductKey, classProductLabel)
);

const classGradeGroups = computed(() => {
  const scopedClasses = filteredClasses.value.filter(clz => classProductKey(clz) === selectedClassCourseKey.value);
  return summarizeClassGroups(scopedClasses, classGradeKey, classGradeLabel);
});

const drilldownClasses = computed(() =>
  filteredClasses.value.filter(clz =>
    classProductKey(clz) === selectedClassCourseKey.value
    && classGradeKey(clz) === selectedClassGradeKey.value
  )
);

const classDrillTitle = computed(() => {
  if (classDrillLevel.value === 'course') return 'Choose class type';
  if (classDrillLevel.value === 'grade') return selectedClassCourseKey.value;
  return selectedClassGradeKey.value;
});

const classDrillSubtitle = computed(() => {
  if (classDrillLevel.value === 'course') return `${filteredClasses.value.length} active class(es)`;
  if (classDrillLevel.value === 'grade') return 'Choose a grade group';
  return 'Choose a time slot';
});

type ConfirmableOccurrence = LessonOccurrenceItem | ClassOccurrenceItem;

function isPendingAdmin(occ: ConfirmableOccurrence): boolean {
  return occ.confirmStage === 'PENDING_ADMIN';
}

function isRejected(occ: ConfirmableOccurrence): boolean {
  return occ.confirmStage === 'REJECTED';
}

function getLessonRecordId(occ: ConfirmableOccurrence): EntityId | undefined {
  return occ.lessonRecordId ?? occ.recordId;
}

function canAdminReviewOccurrence(occ: ConfirmableOccurrence): boolean {
  if (!isAdminRole.value || occ.planningStatus === 'CANCELLED') return false;
  if (occ.confirmStage === 'CONFIRMED' || isRejected(occ)) return false;
  if (isPendingAdmin(occ)) return Boolean(getLessonRecordId(occ));
  return isAwaitingTeacherConfirmation(occ);
}

function canAdminRejectOccurrence(occ: ConfirmableOccurrence): boolean {
  return isAdminRole.value && isPendingAdmin(occ) && Boolean(getLessonRecordId(occ));
}

function canOpenNormalConfirm(occ: ConfirmableOccurrence): boolean {
  if (isAdminRole.value) return false;
  if (occ.planningStatus === 'CANCELLED' || isPendingAdmin(occ)) return false;
  if (isRejected(occ)) return isTeacherRole.value;
  if ('confirmed' in occ && occ.confirmed) return false;
  return !occ.resultStatus && (isTeacherRole.value || isNonTeacherRole.value);
}

function isAwaitingTeacherConfirmation(occ: ConfirmableOccurrence): boolean {
  return occ.planningStatus !== 'CANCELLED'
    && occ.confirmStage !== 'PENDING_ADMIN'
    && occ.confirmStage !== 'CONFIRMED'
    && occ.confirmStage !== 'REJECTED'
    && !occ.resultStatus
    && (!('confirmed' in occ) || !occ.confirmed);
}

function adminConfirmTooltip(occ: ConfirmableOccurrence): string {
  return isAwaitingTeacherConfirmation(occ)
    ? 'Admin can confirm this lesson directly.'
    : 'Teacher confirmed. Pending admin review.';
}

function mergeRecordIntoOccurrence<T extends ConfirmableOccurrence>(occ: T, record?: LessonRecordItem): T {
  if (!record) return occ;
  return {
    ...occ,
    lessonRecordId: record.id,
    recordId: record.id,
    resultStatus: record.resultStatus,
    confirmStage: record.confirmStage,
    confirmedAt: record.confirmedAt,
    teacherConfirmedAt: record.teacherConfirmedAt,
    teacherConfirmedByUserId: record.teacherConfirmedByUserId,
    adminConfirmedAt: record.adminConfirmedAt,
    adminConfirmedByUserId: record.adminConfirmedByUserId,
    rejectReason: record.rejectReason,
    rejectedAt: record.rejectedAt,
    rejectedByUserId: record.rejectedByUserId,
    actualTeacherId: record.actualTeacherId,
    actualTeacherName: record.actualTeacherName,
    actualClassroomId: record.actualClassroomId,
    actualClassroomName: record.actualClassroomName,
    actualStartAt: record.actualStartAt,
    actualEndAt: record.actualEndAt,
    actualUnits: record.actualUnits,
    notes: record.notes,
  };
}

function latestActiveRecordByOccurrence(records: LessonRecordItem[]) {
  const map = new Map<string, LessonRecordItem>();
  records.forEach(record => {
    const occurrenceKey = String(record.occurrenceId);
    if (!map.has(occurrenceKey)) {
      map.set(occurrenceKey, record);
    }
  });
  return map;
}

async function enrichPackageOccurrences(pkgId: EntityId, items: LessonOccurrenceItem[]) {
  try {
    const recordsRes = await fetchLessonRecords({ packageId: pkgId, pageSize: 200 });
    const recordMap = latestActiveRecordByOccurrence(recordsRes.data.data.records);
    return items.map(item => mergeRecordIntoOccurrence(item, recordMap.get(String(item.id))));
  } catch {
    return items;
  }
}

async function enrichClassOccurrences(items: ClassOccurrenceItem[]) {
  if (items.length === 0 || items.some(item => item.confirmStage || getLessonRecordId(item))) {
    return items;
  }

  try {
    const sorted = [...items].sort((a, b) => a.plannedStartAt.localeCompare(b.plannedStartAt));
    const recordsRes = await fetchLessonRecords({
      confirmedFrom: dayjs(sorted[0].plannedStartAt).subtract(1, 'day').format('YYYY-MM-DDT00:00:00'),
      confirmedTo: dayjs(sorted[sorted.length - 1].plannedStartAt).add(1, 'day').format('YYYY-MM-DDT23:59:59'),
      pageSize: 200,
    });
    const occurrenceIds = new Set(items.map(item => String(item.id)));
    const recordMap = latestActiveRecordByOccurrence(
      recordsRes.data.data.records.filter(record => occurrenceIds.has(String(record.occurrenceId)))
    );
    return items.map(item => mergeRecordIntoOccurrence(item, recordMap.get(String(item.id))));
  } catch {

    return items;
  }
}

async function refreshCurrentOccurrences() {
  if (activeTab.value === 'class') {
    if (selectedClassId.value) await loadClassOccurrences(selectedClassId.value);
    return;
  }
  if (selectedPackageId.value) await loadPackageOccurrences(selectedPackageId.value);
}




async function selectStudent(id: EntityId) {
  selectedStudentId.value = id;
  selectedPackageId.value = null;
  occurrences.value = [];
  packagesLoading.value = true;
  try {
    const res = await fetchCoursePackages({
      studentId: id,
      packageStatus: 'ACTIVE',
      pageSize: 50,
    });
    packages.value = res.data.data.records.filter(pkg =>
      (!pkg.teachingMode || pkg.teachingMode === 'ONE_ON_ONE') && pkg.packageStatus === 'ACTIVE'
    );

    await computeLastDates(id);
  } catch {
    packages.value = [];
  } finally {
    packagesLoading.value = false;
  }
}


async function selectPackage(pkgId: string | number) {
  const packageKey = String(pkgId);
  if (selectedPackageId.value === packageKey) {

    selectedPackageId.value = null;
    occurrences.value = [];
    return;
  }
  selectedPackageId.value = packageKey;
  await loadPackageOccurrences(packageKey);
}

async function loadPackageOccurrences(pkgId: EntityId) {
  const packageKey = String(pkgId);
  occurrencesLoading.value = true;
  try {

    const pkg = packages.value.find(p => String(p.id) === packageKey);
    const pkgStart = pkg?.startDate ? dayjs(pkg.startDate).format('YYYY-MM-DDT00:00:00') : null;
    const pkgEnd = pkg?.estimatedEndDate ? dayjs(pkg.estimatedEndDate).format('YYYY-MM-DDT23:59:59') : null;
    const res = await fetchLessonOccurrences({
      packageId: packageKey,
      startAt: pkgStart ?? dayjs().subtract(90, 'day').format('YYYY-MM-DDT00:00:00'),
      endAt: pkgEnd ?? dayjs().add(90, 'day').format('YYYY-MM-DDT23:59:59'),
      pageSize: 200,
    });
    occurrences.value = await enrichPackageOccurrences(packageKey, res.data.data.records);

    occurrenceCounts[packageKey] = res.data.data.records.length;
  } catch {
    occurrences.value = [];
  } finally {
    occurrencesLoading.value = false;
  }
}


async function computeLastDates(studentId: EntityId) {
  try {

    const startAt = dayjs().subtract(89, 'day').format('YYYY-MM-DDT00:00:00');
    const endAt = dayjs().add(89, 'day').format('YYYY-MM-DDT23:59:59');
    const res = await fetchLessonOccurrences({
      studentId,
      startAt,
      endAt,
      pageSize: 200,
    });
    const allOccs = res.data.data.records;

    const byPkg = new Map<string, string>();
    const counts = new Map<string, number>();
    for (const occ of allOccs) {
      if (!occ.packageId) continue;
      const packageKey = String(occ.packageId);
      const date = dayjs(occ.plannedStartAt).format('MM/DD');
      if (!byPkg.has(packageKey) || occ.plannedStartAt > byPkg.get(packageKey)!) {
        byPkg.set(packageKey, date);
      }
      counts.set(packageKey, (counts.get(packageKey) ?? 0) + 1);
    }
    byPkg.forEach((v, k) => { packageLastDates[k] = v; });
    counts.forEach((v, k) => { occurrenceCounts[k] = v; });
  } catch {
    // ignore
  }
}


const hasExpiringStudents = computed(() =>
  enrolledStudents.value.some(s => s.expiryStatus === 'EXPIRING_SOON' || s.expiryStatus === 'EXPIRED')
);

const expiryStatusFilters = [
  { text: 'Normal', value: 'NORMAL' },
  { text: 'Expiring Soon', value: 'EXPIRING_SOON' },
  { text: 'Expired', value: 'EXPIRED' },
];

function filterExpiryStatus(value: string, row: ClassEnrolledStudentItem): boolean {
  return row.expiryStatus === value;
}

function normalizeGroupKey(value: string): string {
  return value.trim().replace(/\s+/g, ' ') || 'Other';
}

function classGradeLabel(clz: ClassGroupItem): string {
  const name = clz.className;
  const rangeMatch = name.match(/\bG\s*\d+\s*[-–]\s*G?\s*\d+\b/i);
  if (rangeMatch) {
    return rangeMatch[0].replace(/\s+/g, '').replace(/g/gi, 'G');
  }
  const singleMatch = name.match(/\bG\s*\d+\b/i);
  if (singleMatch) {
    return singleMatch[0].replace(/\s+/g, '').replace(/g/gi, 'G');
  }
  return 'Other grades';
}

function classGradeKey(clz: ClassGroupItem): string {
  return normalizeGroupKey(classGradeLabel(clz));
}

function classProductLabel(clz: ClassGroupItem): string {
  return clz.registerClass || formatCourseTypeLabel(clz.courseType) || 'Class type not set';
}

function classProductKey(clz: ClassGroupItem): string {
  return normalizeGroupKey(classProductLabel(clz));
}

function formatCourseTypeLabel(courseType?: string): string {
  if (!courseType) return '';
  return courseType
    .toLowerCase()
    .split('_')
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function summarizeClassGroups(
  source: ClassGroupItem[],
  keyGetter: (clz: ClassGroupItem) => string,
  labelGetter: (clz: ClassGroupItem) => string,
): ClassGroupSummary[] {
  const map = new Map<string, ClassGroupSummary>();
  source.forEach((clz) => {
    const key = keyGetter(clz);
    const existing = map.get(key);
    if (existing) {
      return;
    }
    map.set(key, {
      key,
      label: labelGetter(clz),
    });
  });
  return Array.from(map.values()).sort((a, b) => a.label.localeCompare(b.label));
}

function selectClassCourse(key: string) {
  selectedClassCourseKey.value = key;
  selectedClassGradeKey.value = '';
  classDrillLevel.value = 'grade';
}

function selectClassGrade(key: string) {
  selectedClassGradeKey.value = key;
  classDrillLevel.value = 'class';
}

function goBackClassDrill() {
  if (classDrillLevel.value === 'class') {
    classDrillLevel.value = 'grade';
    selectedClassGradeKey.value = '';
    return;
  }
  classDrillLevel.value = 'course';
  selectedClassCourseKey.value = '';
}

function classScheduleSummary(clz: ClassGroupItem) {
  return clz.scheduleDesc || `${clz.scheduleSlots?.length ?? 0} weekly time(s)`;
}

const weekdayLabels = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function classTimeSlotLabel(clz: ClassGroupItem) {
  return classTimeSlotLabels(clz).join(' ');
}

function classTimeSlotLabels(clz: ClassGroupItem) {
  const slots = clz.scheduleSlots ?? [];
  if (slots.length === 0) {
    return [clz.scheduleDesc || 'Time not set'];
  }
  return slots
    .map(slot => `${weekdayLabels[slot.weekday] ?? 'Day'} ${slot.startTime.slice(0, 5)}-${slot.endTime.slice(0, 5)}`);
}

function openStudentProfile(row: ClassEnrolledStudentItem) {
  router.push({
    name: 'admin-config-people',
    query: { tab: 'students', studentId: row.studentId },
  });
}

function formatClassSessionBalance(row: ClassEnrolledStudentItem): string {
  if (row.billingType === 'MONTHLY') {
    return 'Monthly';
  }
  if (row.billingType !== 'PER_SESSION' || row.remainingSessions == null || row.purchasedQuantity == null) {
    return '—';
  }
  return `${row.remainingSessions} / ${row.purchasedQuantity} left`;
}

function classSessionBalanceHint(row: ClassEnrolledStudentItem): string {
  if (row.billingType === 'MONTHLY') {
    return row.purchasedQuantity ? `${row.purchasedQuantity} month(s)` : 'No session cap';
  }
  if (row.billingType !== 'PER_SESSION' || row.usedSessions == null) {
    return 'Not tracked';
  }
  return `${row.usedSessions} used`;
}

async function selectClass(id: string | number) {
  const classKey = String(id);
  selectedClassId.value = classKey;
  classOccurrences.value = [];
  enrolledStudents.value = [];

  await Promise.all([loadClassStudents(classKey), loadClassOccurrences(classKey)]);
}

async function loadClassStudents(id: EntityId) {
  classStudentsLoading.value = true;
  try {
    const res = await fetchClassStudents(id);
    enrolledStudents.value = res.data.data;
  } catch {
    enrolledStudents.value = [];
  } finally {
    classStudentsLoading.value = false;
  }
}

async function loadClassOccurrences(id: EntityId) {
  occurrencesLoading.value = true;
  try {
    const res = await fetchClassOccurrences(id);
    classOccurrences.value = await enrichClassOccurrences(res.data.data);
  } catch {
    classOccurrences.value = [];
  } finally {
    occurrencesLoading.value = false;
  }
}

function openClassConfirmDialog(occ: ClassOccurrenceItem) {
  confirmSubmitMode.value = 'normal';
  selectedOcc.value = occ;
  confirmFormRef.value?.resetFields();
  Object.assign(confirmForm, {
    resultStatus: 'COMPLETED',
    actualTeacherId: occ.plannedTeacherId ?? selectedClass.value?.teacherId,
    actualClassroomId: occ.plannedClassroomId ?? selectedClass.value?.classroomId,
    actualStartAt: occ.plannedStartAt || '',
    actualHours: (occ.plannedUnits ?? 0) * 0.5,
    notes: '',
  });
  confirmDialogVisible.value = true;

  classConfirmMode.value = true;
}

const classConfirmMode = ref(false);

async function submitClassConfirm(occId: string | number) {
  if (!selectedClassId.value) return;
  if (!confirmFormRef.value) return;
  await confirmFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
        const payload = buildConfirmPayload(selectedOcc.value!);
        await confirmClassLesson(selectedClassId.value!, occId, payload);
      ElMessage.success('Class lesson confirmed successfully.');
      confirmDialogVisible.value = false;
      classConfirmMode.value = false;
        confirmSubmitMode.value = 'normal';
      await loadClassOccurrences(selectedClassId.value!);
    } catch (err) {
      ElMessage.error(extractErrorMessage(err));
    } finally {
      submitting.value = false;
    }
  });
}

function openRenewalReminders() {
  router.push({ name: 'renewal-reminders' });
}

async function handleDeleteClass() {
  if (!selectedClassId.value) return;
  try {
    await ElMessageBox.confirm(
      'Are you sure you want to delete this class? This will cancel all future occurrences and remove all enrollments.',
      'Delete Class',
      { confirmButtonText: 'Delete', cancelButtonText: 'Cancel', type: 'warning' }
    );
  } catch {
    return; // user cancelled
  }
  deleting.value = true;
  try {
    await deleteClassGroup(selectedClassId.value);
    ElMessage.success('Class group deleted.');
    selectedClassId.value = null;
    enrolledStudents.value = [];
    classOccurrences.value = [];
    await loadClasses();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
  } finally {
    deleting.value = false;
  }
}

async function loadClasses() {
  classesLoading.value = true;
  try {
    const res = await fetchClassGroups();
    classes.value = res.data.data;
  } catch {
    classes.value = [];
  } finally {
    classesLoading.value = false;
  }
}

function formatDateShort(dt: string): string {
  return dt ? dayjs(dt).format('MM/DD/YYYY') : '—';
}


function isToday(dt: string): boolean {
  return dayjs(dt).format('YYYY-MM-DD') === dayjs().format('YYYY-MM-DD');
}
function isPast(dt: string): boolean {
  return dayjs(dt).isBefore(dayjs(), 'day');
}
function dayMarkerClass(dt: string): string {
  if (isToday(dt)) return 'pkg-occ-row--today';
  if (isPast(dt)) return 'pkg-occ-row--past';
  return 'pkg-occ-row--future';
}
function formatDate(dt: string): string {
  return dt ? dayjs(dt).format('MM/DD (ddd)') : '—';
}
function formatTimeRange(startAt: string, endAt: string): string {
  if (!startAt || !endAt) return '—';
  const s = startAt.includes('T') ? startAt.split('T')[1].slice(0, 5) : startAt.slice(0, 5);
  const e = endAt.includes('T') ? endAt.split('T')[1].slice(0, 5) : endAt.slice(0, 5);
  return `${s} – ${e}`;
}

function resultStatusTag(status: string) {
  return { COMPLETED: 'success', FORMAL_LEAVE: 'warning', NO_SHOW: 'danger', CANCELLED: 'info' }[status] ?? '';
}
function resultStatusLabel(status: string) {
  return { COMPLETED: 'Completed', FORMAL_LEAVE: 'Leave', NO_SHOW: 'No Show', CANCELLED: 'Cancelled' }[status] ?? status;
}

function unconfirmedStatusTag(occ: ConfirmableOccurrence) {
  if (isPast(occ.plannedStartAt)) return 'danger';
  if (isToday(occ.plannedStartAt)) return 'warning';
  return 'info';
}

function unconfirmedStatusLabel(occ: ConfirmableOccurrence) {
  if (isPast(occ.plannedStartAt)) return 'Past - Unconfirmed';
  if (isToday(occ.plannedStartAt)) return 'Due Today';
  return 'Scheduled';
}

function unconfirmedStatusHint(occ: ConfirmableOccurrence) {
  if (isPast(occ.plannedStartAt)) return 'This lesson has passed but has not been confirmed.';
  if (isToday(occ.plannedStartAt)) return 'This lesson is scheduled for today and still needs confirmation.';
  return 'This future lesson is scheduled and not ready for confirmation yet.';
}

function adminReviewButtonType(occ: ConfirmableOccurrence) {
  return isPendingAdmin(occ) ? 'success' : 'warning';
}

function classOccurrenceStatusTag(occ: ClassOccurrenceItem) {
  if (isPendingAdmin(occ)) return 'warning';
  if (isRejected(occ)) return 'danger';
  if (occ.resultStatus) return resultStatusTag(occ.resultStatus);
  if (occ.planningStatus === 'CANCELLED') return 'danger';
  return unconfirmedStatusTag(occ);
}

function classOccurrenceStatusLabel(occ: ClassOccurrenceItem) {
  if (isPendingAdmin(occ)) return 'Awaiting admin review';
  if (isRejected(occ)) return 'Rejected';
  if (occ.resultStatus) return resultStatusLabel(occ.resultStatus);
  if (occ.planningStatus === 'CANCELLED') return 'Cancelled';
  return unconfirmedStatusLabel(occ);
}

function classOccurrenceRowClassName({ row }: { row: ClassOccurrenceItem }) {
  if (isToday(row.plannedStartAt)) return 'class-occ-row--today';
  if (isPast(row.plannedStartAt)) return 'class-occ-row--past';
  return '';
}

function displayResourceName(name?: string) {
  return name?.trim() || 'Unassigned';
}

const classAdjustForm = reactive({
  plannedTeacherId: undefined as string | number | undefined,
  plannedClassroomId: undefined as string | number | undefined,
  plannedStartAt: '',
  plannedEndAt: '',
  reason: '',
});

const classScheduleForm = reactive({
  teacherId: undefined as string | number | undefined,
  classroomId: undefined as string | number | undefined,
  slots: [] as Array<SlotDef & { teacherId?: EntityId }>,
});

const classScheduleFormRules: FormRules = {
  slots: [
    {
      validator: (_rule: unknown, value: SlotDef[], callback: (error?: Error) => void) => {
        if (value?.length) {
          callback();
          return;
        }
        callback(new Error('Add at least one weekly class time.'));
      },
      trigger: 'change',
    },
  ],
};

const classAdjustFormRules: FormRules = {
  plannedStartAt: [{ required: true, message: 'Start time is required', trigger: 'change' }],
  plannedEndAt: [
    { required: true, message: 'End time is required', trigger: 'change' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (!classAdjustForm.plannedStartAt || !value || dayjs(value).isAfter(dayjs(classAdjustForm.plannedStartAt))) {
          callback();
          return;
        }
        callback(new Error('End time must be later than start time.'));
      },
      trigger: 'change',
    },
  ],
  reason: [{ required: true, message: 'Reason is required', trigger: 'blur' }],
};

function canAdjustClassOccurrence(occ: ClassOccurrenceItem) {
  return isNonTeacherRole.value
    && occ.planningStatus !== 'CANCELLED'
    && !occ.resultStatus
    && !isPendingAdmin(occ)
    && !getLessonRecordId(occ);
}

function normalizeClassScheduleSlots(
  slots: ClassGroupItem['scheduleSlots'],
): Array<SlotDef & { teacherId?: EntityId }> {
  return (slots ?? []).map(slot => {
    const start = slot.startTime.slice(0, 5);
    const end = slot.endTime.slice(0, 5);
    return {
      weekday: slot.weekday,
      teacherId: slot.teacherId,
      startTime: start,
      endTime: end,
      plannedUnits: slot.plannedUnits ?? Math.max(1, dayjs(`2026-01-01T${end}`).diff(dayjs(`2026-01-01T${start}`), 'minute') / 30),
    };
  });
}

function classScheduleSlotLabel(slot: SlotDef) {
  const weekday = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][slot.weekday] ?? '';
  return `${weekday} ${slot.startTime}-${slot.endTime}`;
}

function openClassScheduleDialog() {
  if (!selectedClass.value) return;
  classScheduleFormRef.value?.resetFields();
  Object.assign(classScheduleForm, {
    teacherId: selectedClass.value.teacherId,
    classroomId: selectedClass.value.classroomId,
    slots: normalizeClassScheduleSlots(selectedClass.value.scheduleSlots),
  });
  classScheduleDialogVisible.value = true;
}

async function submitClassSchedule() {
  if (!classScheduleFormRef.value || !selectedClassId.value) return;
  await classScheduleFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      await updateClassSchedule(selectedClassId.value!, {
        teacherId: classScheduleForm.teacherId,
        classroomId: classScheduleForm.classroomId,
        slots: classScheduleForm.slots.map(({ teacherId, weekday, startTime, endTime }) => ({
          teacherId, weekday, startTime, endTime,
        })),
      });
      ElMessage.success('Class schedule updated.');
      classScheduleDialogVisible.value = false;
      await loadClasses();
      if (selectedClassId.value) {
        await loadClassOccurrences(selectedClassId.value);
      }
    } catch (err) {
      ElMessage.error(extractErrorMessage(err));
    } finally {
      submitting.value = false;
    }
  });
}

function openClassAdjustDialog(occ: ClassOccurrenceItem) {
  selectedOcc.value = occ;
  classAdjustFormRef.value?.resetFields();
  Object.assign(classAdjustForm, {
    plannedTeacherId: occ.plannedTeacherId ?? selectedClass.value?.teacherId,
    plannedClassroomId: occ.plannedClassroomId ?? selectedClass.value?.classroomId,
    plannedStartAt: occ.plannedStartAt || '',
    plannedEndAt: occ.plannedEndAt || '',
    reason: '',
  });
  classAdjustDialogVisible.value = true;
}

async function submitClassAdjustment() {
  if (!classAdjustFormRef.value || !selectedOcc.value || !selectedClassId.value) return;
  await classAdjustFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      const startAt = dayjs(classAdjustForm.plannedStartAt);
      const endAt = dayjs(classAdjustForm.plannedEndAt);
      const plannedUnits = Math.max(1, Math.round(endAt.diff(startAt, 'minute') / 30));
      await createScheduleAdjustment({
        occurrenceId: selectedOcc.value!.id,
        adjustmentType: 'RESCHEDULE',
        plannedTeacherId: classAdjustForm.plannedTeacherId,
        plannedClassroomId: classAdjustForm.plannedClassroomId,
        plannedStartAt: classAdjustForm.plannedStartAt,
        plannedEndAt: classAdjustForm.plannedEndAt,
        plannedUnits,
        reason: classAdjustForm.reason.trim(),
      });
      ElMessage.success('Class lesson schedule adjusted.');
      classAdjustDialogVisible.value = false;
      await loadClassOccurrences(selectedClassId.value!);
    } catch (err) {
      ElMessage.error(extractErrorMessage(err));
    } finally {
      submitting.value = false;
    }
  });
}

const confirmForm = reactive({
  resultStatus: 'COMPLETED',
  actualTeacherId: undefined as string | number | undefined,
  actualClassroomId: undefined as string | number | undefined,
  actualStartAt: '',
  actualHours: undefined as number | undefined,
  notes: '',
});
const confirmFormRules: FormRules = {
  resultStatus: [{ required: true, message: 'Result status is required', trigger: 'change' }],
  actualTeacherId: [{ required: true, message: 'Teacher is required for payment', trigger: 'change' }],
};


const plannedHoursForReview = ref<number | null>(null);

const hoursChangedByTeacher = computed(() => {
  if (confirmSubmitMode.value !== 'admin-final' || plannedHoursForReview.value == null) {
    return false;
  }
  return (confirmForm.actualHours ?? 0) !== plannedHoursForReview.value;
});

const showAdminDirectConfirmWarning = computed(() =>
  confirmSubmitMode.value === 'admin-final'
  && Boolean(selectedOcc.value)
  && isAwaitingTeacherConfirmation(selectedOcc.value!)
);
const adminReviewButtonLabel = computed(() => 'Review & deduct');
const adminDirectConfirmWarningTitle = computed(() =>
  classConfirmMode.value
    ? 'Teacher has not confirmed this lesson. Admin can still confirm this class lesson directly.'
    : 'Teacher has not confirmed this lesson. Admin can still review and deduct directly.'
);
const confirmDialogTitle = computed(() => {
  const action = confirmSubmitMode.value === 'admin-final' ? 'Review & Deduct Lesson' : 'Submit Attendance';
  return `${action} — ${selectedOcc.value?.lessonNo ?? ''}`;
});
const confirmDialogSubmitLabel = computed(() =>
  confirmSubmitMode.value === 'admin-final' ? adminReviewButtonLabel.value : 'Submit for review'
);

const selectedPlannedHours = computed(() =>
  plannedHoursForReview.value ?? ((selectedOcc.value?.plannedUnits ?? 0) * 0.5)
);

const deductionPreview = computed(() => {
  const status = confirmForm.resultStatus;
  if (classConfirmMode.value) {
    return status === 'CANCELLED'
      ? 'The cancelled class lesson consumes one purchased session. No replacement session is created.'
      : 'The class lesson consumes one purchased session. No replacement session is created.';
  }
  if (status === 'FORMAL_LEAVE') {
    return 'No hours are deducted. A replacement lesson is appended automatically.';
  }
  if (status === 'CANCELLED') {
    return `The cancelled lesson deducts the full planned ${selectedPlannedHours.value.toFixed(1)}h.`;
  }
  if (status === 'NO_SHOW') {
    return `The no-show deducts the full planned ${selectedPlannedHours.value.toFixed(1)}h.`;
  }
  const hours = confirmForm.actualHours;
  if (hours == null || hours <= 0) return 'No hours will be deducted.';
  return `This lesson will deduct ${hours.toFixed(1)}h.`;
});

const computedEndTime = computed(() => {
  if (!confirmForm.actualStartAt || !confirmForm.actualHours) return '';
  return dayjs(confirmForm.actualStartAt).add(confirmForm.actualHours, 'hour').format('YYYY-MM-DD HH:mm');
});

function openConfirmDialog(occ: LessonOccurrenceItem) {
  classConfirmMode.value = false;
  confirmSubmitMode.value = 'normal';
  selectedOcc.value = occ;
  plannedHoursForReview.value = null;
  confirmFormRef.value?.resetFields();
  Object.assign(confirmForm, {
    resultStatus: 'COMPLETED',
    actualTeacherId: occ.plannedTeacherId,
    actualClassroomId: occ.plannedClassroomId,
    actualStartAt: occ.plannedStartAt || '',
    actualHours: (occ.plannedUnits ?? 0) * 0.5,
    notes: '',
  });


  if (authStore.currentUser?.userType === 'TEACHER' && occ.plannedTeacherId && authStore.currentUser.teacherId != null && occ.plannedTeacherId !== authStore.currentUser.teacherId) {
    ElMessageBox.confirm(
      'This lesson is assigned to another teacher. Are you sure you want to submit attendance?',
      'Submit as Another Teacher',
      { confirmButtonText: 'Submit', cancelButtonText: 'Cancel', type: 'warning' },
    ).then(() => { confirmDialogVisible.value = true; }).catch(() => {});
    return;
  }
  confirmDialogVisible.value = true;
}

function openAdminConfirmDialog(occ: ConfirmableOccurrence) {
  selectedOcc.value = occ;
  confirmSubmitMode.value = 'admin-final';
  classConfirmMode.value = activeTab.value === 'class';
  confirmFormRef.value?.resetFields();

  plannedHoursForReview.value = (occ.plannedUnits ?? 0) * 0.5;
  Object.assign(confirmForm, {
    resultStatus: occ.resultStatus || 'COMPLETED',
    actualTeacherId: occ.actualTeacherId ?? occ.plannedTeacherId ?? (isClassOccurrence(occ) ? selectedClass.value?.teacherId : undefined),
    actualClassroomId: occ.actualClassroomId ?? occ.plannedClassroomId ?? (isClassOccurrence(occ) ? selectedClass.value?.classroomId : undefined),
    actualStartAt: occ.actualStartAt || occ.plannedStartAt || '',
    actualHours: resolveActualHours(occ),
    notes: occ.notes || '',
  });
  confirmDialogVisible.value = true;
}

function resolveActualHours(occ: ConfirmableOccurrence): number {
  if (occ.actualStartAt && occ.actualEndAt) {
    const minutes = dayjs(occ.actualEndAt).diff(dayjs(occ.actualStartAt), 'minute');
    if (minutes > 0) return minutes / 60;
  }
  if (occ.actualUnits != null) {
    // Class occurrence API returns raw half-hour units, while lesson record API returns hours.
    // After enrichment from lesson records, recordId marks the value as already converted.
    if (isClassOccurrence(occ)) {
      return getLessonRecordId(occ) ? occ.actualUnits : occ.actualUnits * 0.5;
    }
    return occ.actualUnits;
  }
  return ((occ.plannedUnits ?? 0) * 0.5);
}

function isClassOccurrence(occ: ConfirmableOccurrence): occ is ClassOccurrenceItem {
  return !('packageId' in occ);
}

function buildConfirmPayload(occ: ConfirmableOccurrence): LessonConfirmPayload {
  const hours = confirmForm.actualHours ?? 0;
  const startAt = confirmForm.actualStartAt || undefined;
  const endAt = startAt && hours > 0
    ? dayjs(startAt).add(hours, 'hour').format('YYYY-MM-DDTHH:mm:ss')
    : undefined;
  return {
    occurrenceId: occ.id,
    resultStatus: confirmForm.resultStatus,
    actualTeacherId: confirmForm.actualTeacherId,
    actualClassroomId: confirmForm.actualClassroomId,
    actualStartAt: startAt,
    actualEndAt: endAt,
    actualUnits: hours * 2,
    notes: confirmForm.notes || undefined,
  };
}

async function submitConfirm() {

  if (classConfirmMode.value && selectedClassId.value) {
    await submitClassConfirm(selectedOcc.value!.id);
    return;
  }
  if (!confirmFormRef.value) return;
  await confirmFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      if (confirmSubmitMode.value === 'admin-final') {
        const recordId = getLessonRecordId(selectedOcc.value!);
        const result = recordId
          ? await adminConfirmLessonRecord(recordId, buildConfirmPayload(selectedOcc.value!))
          : await confirmLesson(buildConfirmPayload(selectedOcc.value!));
        const data = result.data.data;
        ElMessage.success(`Lesson finally confirmed. Deducted ${data.deductedUnits.toFixed(1)}h, remaining ${data.remainingUnits.toFixed(1)}h.`);
        confirmDialogVisible.value = false;
        confirmSubmitMode.value = 'normal';
        await refreshCurrentOccurrences();
        return;
      }
        const result = await confirmLesson(buildConfirmPayload(selectedOcc.value!));
      const data = result.data.data;
      ElMessage.success(`Lesson confirmed. Deducted ${data.deductedUnits.toFixed(1)}h, remaining ${data.remainingUnits.toFixed(1)}h.`);
      confirmDialogVisible.value = false;
      confirmSubmitMode.value = 'normal';

      if (selectedPackageId.value) await loadPackageOccurrences(selectedPackageId.value);
    } catch (err) {
      ElMessage.error(extractErrorMessage(err));
    } finally {
      submitting.value = false;
    }
  });
}

const rejectForm = reactive({
  rejectReason: '',
});
const rejectFormRules: FormRules = {
  rejectReason: [{ required: true, message: 'Reject reason is required', trigger: 'blur' }],
};

function openRejectDialog(occ: ConfirmableOccurrence) {
  const recordId = getLessonRecordId(occ);
  if (!recordId) {
    ElMessage.warning('Missing pending lesson record id.');
    return;
  }
  selectedOcc.value = occ;
  rejectFormRef.value?.resetFields();
  rejectForm.rejectReason = '';
  rejectDialogVisible.value = true;
}

async function submitReject() {
  if (!rejectFormRef.value || !selectedOcc.value) return;
  await rejectFormRef.value.validate(async (valid) => {
    if (!valid) return;
    const recordId = getLessonRecordId(selectedOcc.value!);
    if (!recordId) {
      ElMessage.warning('Missing pending lesson record id.');
      return;
    }
    submitting.value = true;
    try {
      if (activeTab.value === 'class' && selectedClassId.value) {
        await rejectClassLesson(selectedClassId.value, selectedOcc.value!.id, { rejectReason: rejectForm.rejectReason.trim() });
      } else {
        await rejectLessonRecord(recordId, { rejectReason: rejectForm.rejectReason.trim() });
      }
      ElMessage.success('Lesson confirmation rejected.');
      rejectDialogVisible.value = false;
      await refreshCurrentOccurrences();
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message ?? 'Reject failed.');
    } finally {
      submitting.value = false;
    }
  });
}


async function openDetailDialog(occ: LessonOccurrenceItem) {
  detailData.value = occ;
  detailDialogVisible.value = true;
}

// ─── Init ──────────────────────────────
onMounted(async () => {
  studentsLoading.value = true;
  classesLoading.value = true;
  try {
    const [sRes, tRes, cRes, clzRes] = await Promise.all([
      fetchAllStudents(),
      fetchAllTeachers(),
      fetchAllClassrooms(),
      fetchClassGroups(),
    ]);
    students.value = sRes.data.data;
    teachers.value = tRes.data.data;
    classrooms.value = cRes.data.data;
    classes.value = clzRes.data.data;
  } catch {
    ElMessage.error('Failed to load initial data.');
  } finally {
    studentsLoading.value = false;
    classesLoading.value = false;
  }
});

</script>

<style scoped>
/* ── Page Layout ─────────────────────── */
.adj-page {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: calc(100vh - 120px);
}

.adj-page--embedded {
  height: 100%;
  min-height: 0;
}

.adj-page--group-lessons {
  gap: 10px;
}

.adj-group-header {
  flex-shrink: 0;
  padding: 2px 2px 0;
}

.adj-group-header h2 {
  margin: 0;
  color: #1f2937;
  font-size: 18px;
}

.adj-group-header p {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 12px;
}

.adj-page--group-lessons .adj-workspace {
  border-top: 1px solid #e8edf2;
  border-radius: 8px;
}

.class-occ-table {
  width: 100%;
}

.class-lesson-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #64748b;
  line-height: 1.35;
}

.class-lesson-main strong {
  color: #1f2937;
  font-size: 14px;
}

.class-action-cell {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.class-action-cell :deep(.el-button) {
  margin-left: 0;
}

.class-occ-row--today :deep(td) {
  background: #f0faf6 !important;
}

.class-occ-row--past :deep(td) {
  color: #909399;
}

/* ── Tab bar ──────────────────────────── */
.adj-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid #e8edf2;
  margin-bottom: 0;
  flex-shrink: 0;
}
.adj-tab {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 500;
  color: #909399;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.adj-tab:hover { color: #18b58a; }
.adj-tab--active {
  color: #18b58a;
  border-bottom-color: #18b58a;
}

/* ── Workspace (left + right) ─────────── */
.adj-workspace {
  display: flex;
  flex: 1;
  min-height: 0;
  border: 1px solid #e8edf2;
  border-top: none;
  border-radius: 0 0 8px 8px;
  background: #fff;
  overflow: hidden;
}

/* ── Left sidebar ─────────────────────── */
.adj-sidebar {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #e8edf2;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
}
.adj-sidebar__search {
  padding: 12px;
  border-bottom: 1px solid #e8edf2;
}
.adj-sidebar__list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
}
.adj-sidebar__item {
  padding: 10px 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background 0.1s;
}
.adj-sidebar__item:hover { background: #f0faf6; }
.adj-sidebar__item--active {
  background: #eefaf5;
  border-left-color: #18b58a;
}
.adj-sidebar__name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
.adj-sidebar__empty {
  padding: 24px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.class-drill-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #e8edf2;
  background: #fff;
}

.class-drill-header strong,
.class-drill-header span {
  display: block;
}

.class-drill-header strong {
  color: #1f2937;
  font-size: 13px;
}

.class-drill-header span {
  margin-top: 2px;
  color: #909399;
  font-size: 11px;
}

.class-drill-back {
  flex: 0 0 auto;
  padding: 4px 8px;
  color: #409eff;
  font-size: 12px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 999px;
  cursor: pointer;
}

.class-drill-back:hover {
  color: #1d4ed8;
  background: #dbeafe;
}

.class-drill-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
}

.class-drill-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 10px 12px;
  text-align: left;
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.12s, box-shadow 0.12s, background 0.12s;
}

.class-drill-card:hover {
  background: #f7fbff;
  border-color: #bfdbfe;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.08);
}

.class-drill-card__main {
  color: #1f2937;
  font-size: 13px;
  font-weight: 700;
}

.class-drill-class {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  text-align: left;
  background: transparent;
  border-top: none;
  border-right: none;
  border-bottom: none;
}

.class-drill-class__times {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.class-time-chip {
  display: inline-flex;
  max-width: 100%;
  padding: 3px 8px;
  overflow: hidden;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
}

/* ── Right content ──────────────────── */
.adj-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.adj-content__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  font-size: 14px;
}
.adj-content__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

/* ── Package cards ───────────────────── */
.adj-packages {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pkg-card {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.15s;
}
.pkg-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.pkg-card--expanded {
  border-color: #c0e6d8;
  box-shadow: 0 2px 12px rgba(24, 181, 138, 0.08);
}
.pkg-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  cursor: pointer;
  background: #fafbfc;
  transition: background 0.1s;
}
.pkg-card__header:hover { background: #f0faf6; }
.pkg-card__info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pkg-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.pkg-card__badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(24, 181, 138, 0.12);
  color: #0d8f6c;
  font-weight: 600;
}
.pkg-card__count {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
  font-weight: 500;
}
.pkg-card__meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pkg-card__last {
  font-size: 12px;
  color: #909399;
}
.pkg-card__arrow {
  font-size: 14px;
  color: #909399;
  transition: transform 0.2s;
}
.pkg-card__arrow--open { transform: rotate(180deg); }

/* ── Occurrence list ─────────────────── */
.pkg-card__body {
  border-top: 1px solid #e8edf2;
}
.pkg-card__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e8edf2;
}
.pkg-card__toolbar-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.pkg-card__toolbar-hint {
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
}
.pkg-occ-list {
  display: flex;
  flex-direction: column;
  overflow-x: auto;
  overflow-y: hidden;
}
.pkg-occ-row {
  display: grid;
  grid-template-columns: 54px 150px 130px 120px 58px 130px 190px;
  align-items: center;
  gap: 12px;
  min-width: 900px;
  padding: 10px 16px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 13px;
  transition: background 0.1s;
}
.pkg-occ-row:last-child { border-bottom: none; }
.pkg-occ-row--today { background: #f0faf6; }
.pkg-occ-row--past { color: #909399; }
.pkg-occ-row--future { background: #fafbfc; }
.pkg-occ-row:hover { background: #f8f9fc; }

.pkg-occ__marker {
  min-width: 0;
}

.pkg-occ__marker .el-tag { width: 100%; text-align: center; }

.pkg-occ__time,
.pkg-occ__teacher,
.pkg-occ__room,
.pkg-occ__hours,
.pkg-occ__status {
  min-width: 0;
}

.pkg-occ__date,
.pkg-occ__range,
.pkg-occ__teacher,
.pkg-occ__room {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pkg-occ__date { font-weight: 500; color: #303133; }
.pkg-occ__range { font-size: 12px; color: #909399; }
.pkg-occ__teacher { color: #303133; font-weight: 500; }
.pkg-occ__room { font-size: 12px; color: #606266; }
.pkg-occ__hours { text-align: center; color: #606266; }

.pkg-occ__status {
  text-align: center;
}
.pkg-occ__status :deep(.el-tag) {
  max-width: 100%;
  justify-content: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pkg-occ__actions {
  text-align: right;
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  justify-content: flex-end;
  align-items: center;
}
.pkg-occ__actions :deep(.el-button) {
  margin-left: 0;
  flex: 0 0 auto;
  white-space: nowrap;
}

@media (max-width: 1180px) {
  .pkg-occ-row {
    grid-template-columns: 54px 150px 130px 120px 58px 130px 190px;
  }

  .pkg-occ__room {
    display: block;
  }

  .pkg-occ__actions {
    justify-content: flex-end;
    text-align: right;
  }
}

.admin-review-action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}

.admin-direct-alert {
  margin-bottom: 14px;
}

.pkg-occ-empty {
  padding: 24px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* ── Class Detail ─────────────────────── */
.class-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.class-detail__header {
  padding: 14px 16px;
  background: linear-gradient(135deg, #f0faf6, #f6f9fc);
  border-radius: 8px;
  border: 1px solid #e8edf2;
}
.class-detail__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.class-detail__title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}
.class-detail__info-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 32px;
  margin-top: 8px;
}
.class-detail__info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.class-detail__info-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: #909399;
  letter-spacing: 0.3px;
}
.class-detail__info-value {
  font-size: 14px;
  color: #303133;
}
.class-detail__desc {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}
.class-section {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
}
.class-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fafbfc;
  border-bottom: 1px solid #e8edf2;
}
.class-section__header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.student-link {
  padding: 0;
  height: auto;
  font-weight: 600;
}
.session-balance {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.25;
}
.session-balance strong {
  color: #1f2937;
  font-size: 13px;
}
.session-balance span {
  color: #909399;
  font-size: 12px;
}
.class-empty {
  padding: 32px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* ── Dialog ──────────────────────────── */
.adj-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}
.adj-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}
.adj-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}
.class-schedule-alert {
  margin-bottom: 14px;
}
.class-schedule-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.class-schedule-dialog :deep(.weekly-time-selector) {
  min-width: 760px;
}
.class-slot-teacher-list {
  display: grid;
  gap: 8px;
  margin: 0 0 4px 110px;
}
.class-slot-teacher-row {
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(220px, 1.2fr);
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid #dbeafe;
  border-radius: 6px;
  background: #f8fbff;
}
.class-slot-teacher-row > span {
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}
.makeup-hint {
  margin: 0 0 12px;
  color: #606266;
  font-size: 13px;
}
.units-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.form-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.form-hint--warning {
  color: #f56c6c;
  font-weight: 500;
}

.hours-input--changed :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
.end-time-display {
  padding: 6px 0;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
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
.detail-field label { color: #909399; font-size: 12px; }
.detail-field span { color: #303133; font-size: 13px; }
.detail-notes {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
</style>
