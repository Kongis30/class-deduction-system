<template>
  <div class="oo-page">
    <section class="oo-toolbar no-print">
      <div class="oo-toolbar__title">
        <h2>{{ pageTitle }}</h2>
        <p>Find a student, choose their course, then confirm each lesson.</p>
      </div>
      <div class="oo-actions">
        <el-button v-if="!appStore.isMobileViewport" class="action-primary oo-refresh" type="primary" :loading="loading" @click="loadData">Refresh</el-button>
        <el-button v-if="!appStore.isMobileViewport" class="action-print" :disabled="!activePackage" @click="printCurrentPackage('parent')">Print Parent Sheet</el-button>
        <el-dropdown
          v-if="!appStore.isMobileViewport"
          trigger="click"
          :disabled="!activePackage || teacherPrintOptions.length === 0"
          @command="printTeacherSheet"
        >
          <el-button class="action-print" :disabled="!activePackage || teacherPrintOptions.length === 0">
            Print Teacher Sheet
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
            <el-dropdown-item command="__all__">All teachers — one sheet</el-dropdown-item>
              <el-dropdown-item
                v-for="option in teacherPrintOptions"
                :key="option.key"
                :command="option.key"
              >
                {{ option.teacher }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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

    <section
      v-if="!appStore.isMobileViewport || mobileFiltersExpanded"
      class="oo-selector-bar no-print"
      v-loading="loading"
    >
      <div v-if="appStore.isMobileViewport" class="oo-selector-bar__mobile-heading">
        <strong>Search &amp; filters</strong>
        <div class="oo-selector-bar__mobile-actions">
          <button
            v-if="hasActiveFilters"
            type="button"
            class="oo-clear-filters--mobile"
            @click="clearFilters"
          >
            Clear
          </button>
          <el-button text aria-label="Close search and filters" @click="mobileFiltersExpanded = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>
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
        <el-button
          class="oo-student-search__submit"
          type="primary"
          :loading="studentSearchLoading"
          aria-label="Search students"
          @click="searchStudents"
        >
          <span class="oo-student-search__label">Search</span>
          <el-icon><Search /></el-icon>
        </el-button>
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
        <span>Course product</span>
        <el-select
          v-model="selectedCourseType"
          filterable
          clearable
          placeholder="Select course product"
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
        <span>Grade level</span>
        <el-select
          v-model="selectedGradeBand"
          filterable
          clearable
          placeholder="Select grade level"
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

      <el-button v-if="hasActiveFilters && !appStore.isMobileViewport" class="oo-clear-filters" text @click="clearFilters">Clear</el-button>

    </section>

    <main class="oo-workspace" v-loading="loading">
      <div class="oo-split-workspace" :class="{ 'oo-split-workspace--collapsed': packagePanelCollapsed }">
        <aside v-if="!packagePanelCollapsed" ref="packagePanelRef" class="oo-package-panel">
          <div class="oo-package-panel__heading">
            <div>
              <strong>Student courses</strong>
              <span>{{ candidateScopeLabel }}</span>
            </div>
            <div class="oo-package-panel__tools">
              <button
                v-if="appStore.isMobileViewport"
                type="button"
                class="oo-filter-toggle"
                :aria-label="mobileFiltersExpanded ? 'Search and filters are open' : 'Open search and filters'"
                :aria-expanded="mobileFiltersExpanded"
                @click="mobileFiltersExpanded = !mobileFiltersExpanded"
              >
                <el-icon><Search /></el-icon>
              </button>
              <el-button
                class="oo-panel-toggle"
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
          <div v-if="isTeacher && teacherStudentGroups.length" class="oo-package-list oo-package-list--students">
            <section
              v-for="studentGroup in teacherStudentGroups"
              :key="studentGroup.key"
              class="oo-student-group"
            >
              <button
                type="button"
                class="oo-student-group__toggle"
                :class="{ 'oo-student-group__toggle--open': expandedStudentGroupKeys.has(studentGroup.key) }"
                :aria-expanded="expandedStudentGroupKeys.has(studentGroup.key)"
                @click="toggleStudentGroup(studentGroup.key)"
              >
                <strong>{{ studentGroup.studentName }}</strong>
                <small>{{ studentGroup.candidates.length }} courses</small>
                <span aria-hidden="true">›</span>
              </button>
              <div v-if="expandedStudentGroupKeys.has(studentGroup.key)" class="oo-student-group__packages">
                <button
                  v-for="candidate in studentGroup.candidates"
                  :key="candidate.pkg.packageId"
                  type="button"
                  class="oo-package-item oo-package-item--detailed"
                  :class="{ 'oo-package-item--active': isSelectedCandidate(candidate) }"
                  @click="selectCandidatePackage(candidate)"
                >
                  <span>{{ candidatePackageLabel(candidate.pkg) }}</span>
                  <small>{{ normalizedGradeBand(candidate.pkg.gradeBand) }} · {{ formatHours(candidate.pkg.remainingUnits) }}h remaining</small>
                </button>
              </div>
            </section>
          </div>
          <div v-else-if="teacherGradeGroups.length" class="oo-package-list">
            <section
              v-for="teacherGroup in teacherGradeGroups"
              :key="teacherGroup.key"
              class="oo-teacher-group"
            >
              <button
                type="button"
                class="oo-teacher-toggle"
                :class="{ 'oo-teacher-toggle--collapsed': collapsedTeacherKeys.has(teacherGroup.key) }"
                :aria-expanded="!collapsedTeacherKeys.has(teacherGroup.key)"
                @click="toggleTeacherGroup(teacherGroup.key)"
              >
                <span class="oo-teacher-toggle__title">
                  <strong>{{ teacherGroup.teacherName }}</strong>
                </span>
                <small>{{ teacherGroup.gradeGroups.length }} grade levels</small>
                <span aria-hidden="true">›</span>
              </button>
              <div v-if="!collapsedTeacherKeys.has(teacherGroup.key)" class="oo-teacher-grades">
                <section
                  v-for="gradeGroup in teacherGroup.gradeGroups"
                  :key="gradeGroup.key"
                  class="oo-grade-band-group"
                >
                  <button
                    type="button"
                    class="oo-grade-band-toggle"
                    :class="{ 'oo-grade-band-toggle--collapsed': collapsedGradeBandKeys.has(gradeGroup.key) }"
                    :aria-expanded="!collapsedGradeBandKeys.has(gradeGroup.key)"
                    @click="toggleGradeBandGroup(gradeGroup.key)"
                  >
                    <span class="oo-grade-band-toggle__title"><strong>{{ gradeGroup.gradeBand }}</strong></span>
                    <small>{{ gradeGroup.studentGroups.length }} students</small>
                    <span aria-hidden="true">›</span>
                  </button>
                  <div v-if="!collapsedGradeBandKeys.has(gradeGroup.key)" class="oo-grade-band-students">
                    <section
                      v-for="studentGroup in gradeGroup.studentGroups"
                      :key="studentGroup.key"
                      class="oo-student-group"
                    >
                      <button
                        type="button"
                        class="oo-student-group__toggle"
                        :class="{ 'oo-student-group__toggle--open': expandedStudentGroupKeys.has(studentGroup.key) }"
                        :aria-expanded="expandedStudentGroupKeys.has(studentGroup.key)"
                        @click="toggleStudentGroup(studentGroup.key)"
                      >
                        <strong>{{ studentGroup.studentName }}</strong>
                        <span aria-hidden="true">›</span>
                      </button>
                      <div v-if="expandedStudentGroupKeys.has(studentGroup.key)" class="oo-student-group__packages">
                        <button
                          v-for="candidate in studentGroup.candidates"
                          :key="candidate.pkg.packageId"
                          type="button"
                          class="oo-package-item"
                          :class="{ 'oo-package-item--active': isSelectedCandidate(candidate) }"
                          @click="selectCandidatePackage(candidate)"
                        >
                          <span>{{ candidatePackageLabel(candidate.pkg) }}</span>
                        </button>
                      </div>
                    </section>
                  </div>
                </section>
              </div>
            </section>
          </div>
          <div v-else class="oo-empty empty-state">
            {{ candidateEmptyMessage }}
          </div>
        </aside>

        <div v-if="packagePanelCollapsed" class="oo-collapsed-tools no-print">
          <template v-if="appStore.isMobileViewport">
            <button
              type="button"
              class="oo-filter-toggle"
              :aria-label="mobileFiltersExpanded ? 'Search and filters are open' : 'Open search and filters'"
              :aria-expanded="mobileFiltersExpanded"
              @click="mobileFiltersExpanded = !mobileFiltersExpanded"
            >
              <el-icon><Search /></el-icon>
            </button>
            <button type="button" class="oo-mobile-course-toggle" @click="showCourseList">
              Change course
            </button>
          </template>
          <el-button
            v-else
            class="oo-package-toggle"
            text
            title="Show course list"
            aria-label="Show course list"
            @click="packagePanelCollapsed = false"
          >
            ›
          </el-button>
        </div>

        <section
          v-if="activePackage && activeStudent"
          class="oo-print-area oo-record-panel"
          :class="[
            printMode === 'teacher' ? 'oo-print-area--teacher' : 'oo-print-area--parent',
          ]"
          ref="printAreaRef"
        >
          <div class="oo-student-context">
            <div>
              <strong>
                {{ activePackageStudentNames || activeStudent.studentName }}
                <el-tag
                  v-if="(activePackage.participantCount ?? 1) > 1"
                  size="small"
                  type="primary"
                  class="oo-private-group-tag"
                >1-on-{{ activePackage.participantCount }}</el-tag>
              </strong>
              <span>{{ activePackage.packageName }}</span>
            </div>
            <div class="oo-student-context__metrics">
              <div class="oo-student-context__metric-group" role="group" aria-label="Package hours">
                <span class="oo-student-context__metric">
                  <el-tooltip content="Total package hours, including bonus hours." placement="top" :show-after="200">
                    <small>Total Hours</small>
                  </el-tooltip>{{ formatHours(activePackage.totalUnits) }}h
                </span>
                <span class="oo-student-context__metric">
                  <el-tooltip content="Starting balance entered for this package." placement="top" :show-after="200">
                    <small>Starting Hours</small>
                  </el-tooltip>{{ formatHours(activePackage.startingHours) }}h
                </span>
                <span class="oo-student-context__metric">
                  <el-tooltip content="Package hours left after confirmed deductions." placement="top" :show-after="200">
                    <small>Remaining Hours</small>
                  </el-tooltip>{{ formatHours(activePackage.remainingUnits) }}h
                </span>
              </div>
              <div class="oo-student-context__metric-group oo-student-context__metric-group--teacher" role="group" :aria-label="`Hours for ${activeTeacher?.teacherName || 'teacher'}`">
                <span class="oo-student-context__metric">
                  <el-tooltip content="Hours assigned to this teacher at the last schedule change. This value stays fixed until the schedule changes again." placement="top" :show-after="200">
                    <small>Allocated to {{ activeTeacher?.teacherName || 'teacher' }}</small>
                  </el-tooltip>{{ formatHours(activeTeacherHours?.allocatedUnits ?? 0) }}h
                </span>
                <span class="oo-student-context__metric">
                  <el-tooltip content="Current schedule allocation minus hours finally deducted by the admin. Includes future and past lessons still awaiting final processing." placement="top" :show-after="200">
                    <small>Remaining with {{ activeTeacher?.teacherName || 'teacher' }}</small>
                  </el-tooltip>{{ formatHours(activeTeacherHours?.remainingUnits ?? 0) }}h
                </span>
                <span class="oo-student-context__metric">
                  <el-tooltip content="Hours finally deducted in the current schedule period, including student cancellations. Formal leave and pending reviews do not count." placement="top" :show-after="200">
                    <small>Deducted by {{ activeTeacher?.teacherName || 'teacher' }}</small>
                  </el-tooltip>{{ formatHours(activeTeacherHours?.deductedUnits ?? 0) }}h
                </span>
              </div>
            </div>
          </div>
          <el-alert
            v-if="currentTeacherRemainingOverhang > 0"
            type="warning"
            :closable="false"
            show-icon
            :title="`Teacher remaining allocations exceed the remaining package balance by ${formatHours(currentTeacherRemainingOverhang)}h. Review the allocation history.`"
          />
          <el-alert
            v-if="(activePackage.scheduledReservedUnits ?? 0) > activePackage.remainingUnits"
            type="warning"
            :closable="false"
            show-icon
            :title="`Scheduled and pending lesson hours exceed the remaining package balance by ${formatHours((activePackage.scheduledReservedUnits ?? 0) - activePackage.remainingUnits)}h. Review this package's schedule and pending lessons.`"
          />

          <section class="oo-signature-sheet oo-parent-signature-sheet print-only">
            <div class="oo-signature-sheet__meta">
              <p><strong>Student:</strong> {{ activePackageStudentNames || activeStudent.studentName }}</p>
              <div class="oo-signature-hours">
                <div class="oo-signature-hours__group">
                  <p><strong>Lessons on this sheet:</strong> {{ formatHours(signatureSheetHours) }}</p>
                  <p><strong>Package hours:</strong> {{ formatHours(signaturePackage?.totalUnits) }}</p>
                  <p><strong>Purchased Hours:</strong> {{ formatHours(signaturePackage?.purchasedUnits ?? signaturePackage?.totalUnits) }}</p>
                  <p><strong>Bonus Hours:</strong> {{ formatHours(signaturePackage?.bonusUnits ?? 0) }}</p>
                </div>
                <div class="oo-signature-hours__group oo-signature-hours__group--balance">
                  <p><strong>Already deducted:</strong> {{ formatHours(signaturePackage?.consumedUnits) }}</p>
                  <p><strong>Remaining:</strong> {{ formatHours(signaturePackage?.remainingUnits) }}</p>
                </div>
              </div>
            </div>

            <div class="oo-signature-subjects">
              <div
                v-for="summary in signatureSubjectSummaries"
                :key="summary.subject"
                class="oo-signature-subject-line"
              >
                <span><strong>Lesson name:</strong> {{ summary.subject }}</span>
                <span><strong>Teacher:</strong> {{ summary.teacher }}</span>
                <span><strong>Classtime:</strong> {{ summary.classTime }}</span>
                <span><strong>Hours on sheet:</strong> {{ formatHours(summary.hours) }}</span>
                <span><strong>Starting Date:</strong> {{ summary.startDate }}</span>
              </div>
            </div>

            <table class="oo-signature-table oo-signature-table--detail">
              <thead>
                <tr>
                  <th>Lesson name</th>
                  <th>Teacher</th>
                  <th>Date</th>
                  <th>Signature</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in signatureSheetRows" :key="row.occurrenceId">
                  <td>{{ signatureSubjectLabel(row) }}</td>
                  <td>{{ signatureLessonTeacher(row) }}</td>
                  <td>{{ signatureLessonDate(row) }}</td>
                  <td>{{ row.historicalSignatureText ?? '' }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section class="oo-signature-sheet oo-teacher-signature-sheet print-only">
            <div class="oo-teacher-sheet__meta">
              <p><strong>Student:</strong> {{ activePackageStudentNames || activeStudent.studentName }}</p>
              <p><strong>Course:</strong> {{ signaturePackage?.packageName }}</p>
              <p><strong>Teacher(s):</strong> {{ teacherPrintTeacherNames.join(', ') || '—' }}</p>
              <p><strong>Classtime:</strong> {{ subjectClassTime(teacherPrintRows) }}</p>
              <p><strong>Starting Date:</strong> {{ teacherPrintStartDate }}</p>
              <p v-for="hours in teacherPrintHoursSummaries" :key="`teacher-hours-${hours.teacherId}`">
                <strong>Total Hours:</strong> {{ formatHours(activePackage.totalUnits) }}h ·
                <strong>Starting Hours:</strong> {{ formatHours(activePackage.startingHours) }}h ·
                <strong>Allocated to {{ hours.teacherName }}:</strong> {{ formatHours(hours.allocatedUnits) }}h ·
                <strong>Current schedule deducted by {{ hours.teacherName }}:</strong> {{ formatHours(hours.deductedUnits) }}h ·
                <strong>Remaining with {{ hours.teacherName }}:</strong> {{ formatHours(hours.remainingUnits) }}h
              </p>
            </div>

            <table class="oo-signature-table oo-signature-table--teacher">
              <thead>
                <tr>
                  <th>Lesson name</th>
                  <th>Teacher</th>
                  <th>Date</th>
                  <th>Time</th>
                  <th>Hours</th>
                  <th>Signature</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in teacherPrintRows" :key="`teacher-${row.occurrenceId}`">
                  <td>{{ signatureSubjectLabel(row) }}</td>
                  <td>{{ signatureLessonTeacher(row) }}</td>
                  <td>{{ signatureLessonDate(row) }}</td>
                  <td>{{ signatureTimeOfDayKey(row) || '—' }}</td>
                  <td>{{ formatHours(row.plannedUnits) }}</td>
                  <td>{{ row.historicalSignatureText ?? '' }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <div
            v-if="!appStore.isMobileViewport || nextArrangementLesson"
            class="oo-action-line no-print"
            :class="{ 'oo-action-line--done': workflowComplete }"
          >
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
              Set teacher
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
            class="oo-renewal no-print"
            title="Renewal reminder: two lessons or fewer remain."
            type="warning"
            :closable="false"
            show-icon
          />

          <div class="oo-table-title no-print">
            <div>
              <strong>Lesson records</strong>
              <span>
                {{ visibleLessons.length }} records ·
                {{ pendingTeacherCount }} awaiting teacher ·
                {{ pendingAdminCount }} awaiting admin
              </span>
              <small>Each row shows one lesson and its next permitted action.</small>
            </div>
          </div>

          <el-table
            v-if="!appStore.isMobileViewport"
            :data="lessonTimelineRows"
            border
            stripe
            size="small"
            class="oo-table sheet-table no-print"
            :row-class-name="lessonRowClassName"
            :span-method="lessonTimelineSpanMethod"
          >
            <el-table-column label="Course" min-width="150">
              <template #default="{ row }">
                <div v-if="row.kind === 'change'" class="oo-schedule-change">
                  <span class="oo-schedule-change__line" aria-hidden="true"></span>
                  <strong>Schedule adjusted · {{ formatScheduleChangeTime(row.change.changedAt) }}</strong>
                  <span class="oo-schedule-change__line" aria-hidden="true"></span>
                </div>
                <div v-else class="oo-simple-cell">
                  <strong>{{ signatureCourseLabel(row.lesson) }}</strong>
                  <span v-if="row.lesson.actualTeacherName || row.lesson.plannedTeacherName">
                    {{ displayResourceName(row.lesson.actualTeacherName || row.lesson.plannedTeacherName) }}
                  </span>
                  <small
                    v-if="row.lesson.allocationOwnerTeacherName
                      && String(row.lesson.allocationOwnerTeacherId) !== String(row.lesson.actualTeacherId || row.lesson.plannedTeacherId)"
                  >
                    Hours remain allocated to {{ displayResourceName(row.lesson.allocationOwnerTeacherName) }}
                  </small>
                  <span v-if="isKeptFromPreviousSchedule(row.lesson)" class="oo-schedule-change__kept">Kept from previous schedule</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Date & time" min-width="150" align="center" header-align="center">
              <template #default="{ row }">
                <div v-if="row.kind === 'lesson'" class="oo-simple-cell oo-simple-cell--center">
                  <strong>{{ formatDate(row.lesson.plannedStartAt) }}</strong>
                  <span>{{ formatTimeRange(row.lesson.plannedStartAt, row.lesson.plannedEndAt) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Status" min-width="155" align="center" header-align="center">
              <template #default="{ row }">
                <el-tag v-if="row.kind === 'lesson'" class="status-pill" :class="statusPillClass(row.lesson)" size="small" :type="statusTag(row.lesson)">
                  {{ statusLabel(row.lesson) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="canConfirmLessons || canManageLessonResources"
              label="Action"
              width="350"
              fixed="right"
              class-name="no-print"
              align="center"
              header-align="center"
            >
              <template #default="{ row }">
                <div v-if="row.kind === 'lesson'" class="oo-action-cell">
                  <div class="oo-action-slot oo-action-slot--adjust">
                    <el-button
                      v-if="canAdjustLesson(row.lesson)"
                      size="small"
                      plain
                      @click="openAdjustDialog(row.lesson)"
                    >
                      Adjust
                    </el-button>
                  </div>
                  <div class="oo-action-slot oo-action-slot--primary">
                    <el-tooltip
                      v-if="needsLessonResources(row.lesson)"
                      content="Set a teacher before deduction."
                      placement="top"
                    >
                      <span class="oo-action-reason">
                        <el-icon><WarningFilled /></el-icon>
                      </span>
                    </el-tooltip>
                    <el-button
                      v-else-if="canAdminConfirm(row.lesson)"
                      class="action-primary"
                      type="warning"
                      size="small"
                      @click="openConfirm(row.lesson, 'admin')"
                    >
                      Review & deduct
                    </el-button>
                    <el-button
                      v-else-if="canInitialConfirm(row.lesson)"
                      class="action-primary"
                      type="success"
                      size="small"
                      plain
                      @click="openConfirm(row.lesson, 'normal')"
                    >
                      Submit attendance
                    </el-button>
                    <span v-else-if="!canAdjustLesson(row.lesson)" class="oo-no-action">{{ taskLabel(row.lesson) }}</span>
                    <el-button
                      v-if="canRevokeLesson(row.lesson)"
                      class="oo-revoke-button"
                      size="small"
                      type="danger"
                      plain
                      @click="openRevokeDialog(row.lesson)"
                    >
                      Undo
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-else class="oo-mobile-lessons no-print">
            <template v-for="item in lessonTimelineRows" :key="item.key">
              <div v-if="item.kind === 'change'" class="oo-schedule-change oo-schedule-change--mobile">
                <span class="oo-schedule-change__line" aria-hidden="true"></span>
                <strong>Schedule adjusted · {{ formatScheduleChangeTime(item.change.changedAt) }}</strong>
                <span class="oo-schedule-change__line" aria-hidden="true"></span>
              </div>
              <article
                v-else
                class="oo-mobile-lesson"
                :class="{ 'oo-mobile-lesson--overdue-unprocessed': isPastUnprocessed(item.lesson) }"
              >
                <div class="oo-mobile-lesson__heading">
                  <div class="oo-simple-cell">
                    <strong>{{ signatureCourseLabel(item.lesson) }}</strong>
                    <span>{{ displayResourceName(item.lesson.actualTeacherName || item.lesson.plannedTeacherName) }}</span>
                    <small
                      v-if="item.lesson.allocationOwnerTeacherName
                        && String(item.lesson.allocationOwnerTeacherId) !== String(item.lesson.actualTeacherId || item.lesson.plannedTeacherId)"
                    >
                      Hours remain allocated to {{ displayResourceName(item.lesson.allocationOwnerTeacherName) }}
                    </small>
                    <span v-if="isKeptFromPreviousSchedule(item.lesson)" class="oo-schedule-change__kept">Kept from previous schedule</span>
                  </div>
                  <el-tag class="status-pill" :class="statusPillClass(item.lesson)" size="small" :type="statusTag(item.lesson)">
                    {{ statusLabel(item.lesson) }}
                  </el-tag>
                </div>
                <div class="oo-mobile-lesson__time">
                  <strong>{{ formatDate(item.lesson.plannedStartAt) }}</strong>
                  <span>{{ formatTimeRange(item.lesson.plannedStartAt, item.lesson.plannedEndAt) }}</span>
                </div>
                <div v-if="canConfirmLessons || canManageLessonResources" class="oo-mobile-lesson__actions">
                  <el-button v-if="canAdjustLesson(item.lesson)" size="small" plain @click="openAdjustDialog(item.lesson)">Adjust</el-button>
                  <el-button v-if="!needsLessonResources(item.lesson) && canAdminConfirm(item.lesson)" class="action-primary" type="warning" @click="openConfirm(item.lesson, 'admin')">Review &amp; deduct</el-button>
                  <el-button v-else-if="!needsLessonResources(item.lesson) && canInitialConfirm(item.lesson)" class="action-primary" type="success" plain @click="openConfirm(item.lesson, 'normal')">Submit attendance</el-button>
                  <el-button v-if="canRevokeLesson(item.lesson)" size="small" type="danger" plain @click="openRevokeDialog(item.lesson)">Undo</el-button>
                  <span v-if="!canAdjustLesson(item.lesson) && !canAdminConfirm(item.lesson) && !canInitialConfirm(item.lesson) && !canRevokeLesson(item.lesson)" class="oo-no-action">{{ needsLessonResources(item.lesson) ? 'Set a teacher before deduction.' : taskLabel(item.lesson) }}</span>
                </div>
              </article>
            </template>
          </div>
        </section>

        <section v-else class="oo-record-panel oo-record-panel--empty">
          <div class="oo-empty empty-state">
            Choose a student course on the left to view its lesson records.
          </div>
        </section>
      </div>
    </main>

    <el-dialog v-model="confirmDialogVisible" :title="confirmDialogTitle" width="520px" :fullscreen="appStore.isMobileViewport" :close-on-click-modal="false" class="no-print">
      <el-form :model="confirmForm" :label-position="appStore.isMobileViewport ? 'top' : 'right'" :label-width="appStore.isMobileViewport ? 'auto' : '130px'">
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
            <el-option label="Student cancellation (deduct planned hours)" value="CANCELLED" />
            <el-option label="No show (deduct planned hours)" value="NO_SHOW" />
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
          v-if="confirmForm.resultStatus === 'FORMAL_LEAVE' && selectedLesson"
          :title="formalLeaveTeacherHoursHint"
          type="warning"
          :closable="false"
          show-icon
        />
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

    <el-dialog v-model="revokeDialogVisible" title="Undo lesson decision" width="520px" :fullscreen="appStore.isMobileViewport" :close-on-click-modal="false" class="no-print">
      <el-alert
        v-if="revokeLesson"
        :title="revokeImpactMessage(revokeLesson)"
        type="warning"
        :closable="false"
        show-icon
      />
      <p v-if="revokeLesson" class="oo-form-hint">
        {{ formatDate(revokeLesson.plannedStartAt) }} · {{ signatureCourseLabel(revokeLesson) }}
      </p>
      <el-form :label-position="appStore.isMobileViewport ? 'top' : 'right'" :label-width="appStore.isMobileViewport ? 'auto' : '100px'">
        <el-form-item label="Reason (optional)">
          <el-input v-model="revokeReason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="Add a note if helpful" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revokeDialogVisible = false">Keep decision</el-button>
        <el-button type="danger" :loading="revoking" @click="submitRevoke">Undo decision</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustDialogVisible" title="Adjust Lesson" width="560px" :fullscreen="appStore.isMobileViewport" :close-on-click-modal="false" class="no-print">
      <p class="oo-form-hint">
        {{ isTeacher
          ? "Change the time or duration of your own unconfirmed lesson. Duration changes may rebalance future unprocessed lessons in the same slot."
          : "Change this lesson's time, teacher, course name, or classroom. The recurring schedule and allocated hours stay unchanged. Changing duration may rebalance future unprocessed lessons in this same time slot." }}
      </p>
      <el-form :label-position="appStore.isMobileViewport ? 'top' : 'right'" :label-width="appStore.isMobileViewport ? 'auto' : '110px'">
        <el-form-item v-if="!isTeacher" label="Teacher">
          <el-select
            v-model="adjustForm.teacherId"
            clearable
            filterable
            placeholder="Select teacher"
            style="width: 100%"
          >
            <el-option v-for="teacher in teacherOptions" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="Teacher">
          <span>{{ displayResourceName(adjustLesson?.plannedTeacherName) }}</span>
        </el-form-item>
        <template v-if="!isTeacher">
          <el-form-item label="Course name">
            <el-input v-model="adjustForm.subjectName" maxlength="128" />
          </el-form-item>
          <el-form-item label="Delivery">
            <el-select v-model="adjustForm.deliveryMode" style="width: 100%">
              <el-option label="Online / no classroom" value="ONLINE" />
              <el-option label="In person" value="IN_PERSON" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="adjustForm.deliveryMode === 'IN_PERSON'" label="Classroom">
            <el-select v-model="adjustForm.classroomId" filterable clearable placeholder="Optional" style="width: 100%">
              <el-option v-for="classroom in classrooms" :key="classroom.id" :label="classroom.roomName" :value="classroom.id" />
            </el-select>
          </el-form-item>
        </template>
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
        <el-form-item label="Reason">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="2" placeholder="Optional note for this adjustment" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjustLesson">Save adjustment</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignmentDialogVisible" title="Assign Teacher" width="480px" :fullscreen="appStore.isMobileViewport" :close-on-click-modal="false" class="no-print">
      <p class="oo-form-hint">
        A teacher is required. The classroom is optional.
      </p>
      <el-form :label-position="appStore.isMobileViewport ? 'top' : 'right'" :label-width="appStore.isMobileViewport ? 'auto' : '100px'">
        <el-form-item label="Teacher" required>
          <el-select v-model="assignmentForm.teacherId" filterable placeholder="Select teacher" style="width: 100%">
            <el-option v-for="teacher in teacherOptions" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Classroom">
          <el-select v-model="assignmentForm.classroomId" filterable clearable placeholder="Optional" style="width: 100%">
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
import { Close, Search, WarningFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { fetchAllClassrooms, fetchAllTeachers, fetchStudents } from '@/api/base-data';
import { extractErrorMessage } from '@/api/http';
import { adminConfirmLessonRecord, confirmLesson, revokeLessonRecord } from '@/api/lesson';
import { fetchOneOnOneDeductions, fetchPrivateSignatureHistory, type OneOnOneLesson, type OneOnOnePackage, type OneOnOneScheduleChange, type OneOnOneStudent, type OneOnOneTeacher, type PrivateSignatureHistory } from '@/api/one-on-one';
import { createScheduleAdjustment } from '@/api/schedule';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import { isAdminOrStaffUser, isAdminUser, isManagerUser, isStudentUser, isTeacherUser } from '@/types/auth';
import type { EntityId } from '@/types/domain';
import { findSignaturePackage, signatureLessonRows, signatureSubjectName, withSignatureHistory } from '@/utils/one-on-one-signature-data';
import { createIsolatedPrinter } from '@/utils/isolated-print';

interface PackageCandidate {
  teacher: OneOnOneTeacher;
  student: OneOnOneStudent;
  pkg: OneOnOnePackage;
  studentKey: string;
  teacherKey: string;
  courseProductKey: string;
  gradeKey: string;
}

interface StudentPackageGroup {
  key: string;
  studentKey: string;
  studentName: string;
  candidates: PackageCandidate[];
}

interface GradeBandGroup {
  key: string;
  gradeBand: string;
  studentGroups: StudentPackageGroup[];
}

interface TeacherGradeGroup {
  key: string;
  teacherName: string;
  gradeGroups: GradeBandGroup[];
}

interface StudentEntry {
  teacher: OneOnOneTeacher;
  student: OneOnOneStudent;
}

interface SignatureSubjectSummary {
  key: string;
  subject: string;
  teacher: string;
  teacherKey?: string;
  classTime: string;
  hours: number;
  startDate: string;
  rows: OneOnOneLesson[];
}

type LessonTimelineRow =
  | { kind: 'lesson'; key: string; lesson: OneOnOneLesson }
  | { kind: 'change'; key: string; change: OneOnOneScheduleChange };

const authStore = useAuthStore();
const appStore = useAppStore();
const route = useRoute();
const isTeacher = computed(() => isTeacherUser(authStore.currentUser));
const isStudent = computed(() => isStudentUser(authStore.currentUser));
const isAdmin = computed(() => isAdminOrStaffUser(authStore.currentUser) || isManagerUser(authStore.currentUser));
const canUndoAdminDecision = computed(() => isAdminUser(authStore.currentUser));
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
const activeTeacherHours = computed(() => {
  const teacher = activeTeacher.value;
  const hours = activePackage.value?.teacherHours ?? [];
  const teacherId = entityKey(teacher?.teacherId);
  if (teacherId) return hours.find(item => entityKey(item.teacherId) === teacherId);
  const name = optionKey(teacher?.teacherName);
  return hours.find(item => !item.teacherId && optionKey(item.teacherName) === name);
});
// 老师当前周期未消课额度超过课包余额时提示核对历史分配，不改写已保存的排课。
const currentTeacherRemainingOverhang = computed(() => {
  const pkg = activePackage.value;
  if (!pkg) return 0;
  const teacherRemainingHours = (pkg.teacherHours ?? []).reduce(
    (sum, hours) => sum + (hours.remainingUnits ?? 0), 0,
  );
  return Math.max(0, teacherRemainingHours - (pkg.remainingUnits ?? 0));
});
const signatureHistory = ref<PrivateSignatureHistory[]>([]);
const signatureHistoryPackageId = ref('');
const printAreaRef = ref<HTMLElement>();
const signaturePrinter = createIsolatedPrinter(() => printAreaRef.value);
const packagePanelRef = ref<HTMLElement>();
const printMode = ref<'parent' | 'teacher'>('parent');
const selectedTeacherPrintKey = ref<string>('__all__');
const selectedTeacherId = ref<EntityId>();
const selectedCourseType = ref<string>();
const selectedGradeBand = ref<string>();
const studentSearchKeyword = ref('');
const studentSearchLoading = ref(false);
const appliedStudentSearch = ref('');
const mobileFiltersExpanded = ref(false);
const studentSearchResults = ref<StudentEntry[]>([]);
const matchedSearchStudentIds = ref<Set<string>>(new Set());
const packagePanelCollapsed = ref(false);
const collapsedTeacherKeys = ref(new Set<string>());
const collapsedGradeBandKeys = ref(new Set<string>());
const expandedStudentGroupKeys = ref(new Set<string>());
let initializedTeacherList = false;

const studentEntries = computed<StudentEntry[]>(() => teachers.value.flatMap(teacher =>
  teacher.students.map(student => ({ teacher, student })),
));
const teacherFilterOptions = computed(() => teachers.value.filter(teacher => Boolean(teacher.teacherId)));
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
  matchedSearchStudentIds.value,
));
const activePackageStudentNames = computed(() => (activePackage.value?.participants ?? [])
  .map(participant => participant.studentName?.trim())
  .filter((name): name is string => Boolean(name))
  .join(' / '));
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
const teacherGradeGroups = computed<TeacherGradeGroup[]>(() => {
  const teacherGroups = new Map<string, {
    key: string;
    teacherName: string;
    grades: Map<string, {
      key: string;
      gradeBand: string;
      students: Map<string, StudentPackageGroup>;
    }>;
  }>();
  for (const candidate of filteredPackageCandidates.value) {
    const teacherGroupKey = candidate.teacherKey || `teacher-name:${optionKey(candidate.teacher.teacherName)}`;
    let teacherGroup = teacherGroups.get(teacherGroupKey);
    if (!teacherGroup) {
      teacherGroup = {
        key: teacherGroupKey,
        teacherName: candidate.teacher.teacherName?.trim() || 'Unassigned teacher',
        grades: new Map(),
      };
      teacherGroups.set(teacherGroup.key, teacherGroup);
    }

    const gradeGroupKey = `${teacherGroup.key}::grade:${candidate.gradeKey}`;
    let gradeGroup = teacherGroup.grades.get(gradeGroupKey);
    if (!gradeGroup) {
      gradeGroup = {
        key: gradeGroupKey,
        gradeBand: normalizedGradeBand(candidate.pkg.gradeBand),
        students: new Map(),
      };
      teacherGroup.grades.set(gradeGroup.key, gradeGroup);
    }

    const privateGroupId = candidate.pkg.privateLessonGroupId;
    const studentGroupKey = privateGroupId == null
      ? `${gradeGroup.key}::student:${candidate.studentKey}`
      : `${gradeGroup.key}::private-group:${entityKey(privateGroupId)}`;
    const studentGroupId = privateGroupId == null ? candidate.studentKey : entityKey(privateGroupId);
    let studentGroup = gradeGroup.students.get(studentGroupId);
    if (!studentGroup) {
      studentGroup = {
        key: studentGroupKey,
        studentKey: studentGroupId,
        studentName: candidate.pkg.participantCount && candidate.pkg.participantCount > 1
          ? (candidate.pkg.participants ?? []).map(participant => participant.studentName?.trim()).filter(Boolean).join(' / ')
            || candidate.student.studentName
          : candidate.student.studentName,
        candidates: [],
      };
      gradeGroup.students.set(studentGroupId, studentGroup);
    }
    if (!studentGroup.candidates.some(existing => String(existing.pkg.packageId) === String(candidate.pkg.packageId))) {
      studentGroup.candidates.push(candidate);
    }
  }
  return Array.from(teacherGroups.values())
    .map(teacherGroup => ({
      key: teacherGroup.key,
      teacherName: teacherGroup.teacherName,
      gradeGroups: Array.from(teacherGroup.grades.values())
        .map(gradeGroup => ({
          key: gradeGroup.key,
          gradeBand: gradeGroup.gradeBand,
          studentGroups: Array.from(gradeGroup.students.values())
            .sort((left, right) => left.studentName.localeCompare(right.studentName)),
        }))
        .sort((left, right) => left.gradeBand.localeCompare(right.gradeBand, undefined, { numeric: true, sensitivity: 'base' })),
    }))
    .sort((left, right) => left.teacherName.localeCompare(right.teacherName));
});
const teacherStudentGroups = computed<StudentPackageGroup[]>(() => {
  const studentGroups = new Map<string, StudentPackageGroup>();
  for (const candidate of filteredPackageCandidates.value) {
    const privateGroupId = candidate.pkg.privateLessonGroupId;
    const groupId = privateGroupId == null
      ? `student:${candidate.studentKey}`
      : `private-group:${entityKey(privateGroupId)}`;
    let studentGroup = studentGroups.get(groupId);
    if (!studentGroup) {
      const participantNames = (candidate.pkg.participants ?? [])
        .map(participant => participant.studentName?.trim())
        .filter((name): name is string => Boolean(name));
      studentGroup = {
        key: `teacher:${groupId}`,
        studentKey: privateGroupId == null ? candidate.studentKey : entityKey(privateGroupId),
        studentName: (candidate.pkg.participantCount ?? 1) > 1 && participantNames.length > 0
          ? participantNames.join(' / ')
          : candidate.student.studentName,
        candidates: [],
      };
      studentGroups.set(groupId, studentGroup);
    }
    if (!studentGroup.candidates.some(existing => String(existing.pkg.packageId) === String(candidate.pkg.packageId))) {
      studentGroup.candidates.push(candidate);
    }
  }
  return Array.from(studentGroups.values())
    .map(group => {
      const primaryPackages = group.candidates.filter(candidate =>
        candidate.pkg.privateLessonGroupId == null || candidate.pkg.privateLessonGroupPrimary === true);
      // 1 对 2 的成员各有自己的余额包，但共享同一套排课和消课记录；左侧只列主课包，避免重复入口。
      const visiblePackages = primaryPackages.length > 0 ? primaryPackages : group.candidates;
      return {
        ...group,
        candidates: visiblePackages.sort((left, right) =>
          candidatePackageLabel(left.pkg).localeCompare(candidatePackageLabel(right.pkg), undefined, {
            sensitivity: 'base',
            numeric: true,
          })),
      };
    })
    .sort((left, right) => left.studentName.localeCompare(right.studentName, undefined, { sensitivity: 'base' }));
});
const candidateScopeLabel = computed(() => {
  if (!hasActiveFilters.value) return 'All 1-on-1 student courses';
  const filters = [
    activeStudentSearch.value && 'student',
    selectedTeacherKey.value && 'teacher',
    selectedCourseType.value && 'course product',
    selectedGradeBand.value && 'grade level',
  ].filter(Boolean);
  return `Matching ${filters.join(' + ')}`;
});
const candidateEmptyMessage = computed(() => {
  if (!hasActiveFilters.value) return 'No 1-on-1 student courses found.';
  if (activeStudentSearch.value && studentSearchResults.value.length === 0) {
    return 'No student matches this search.';
  }
  return 'No 1-on-1 student courses match this selection.';
});
const visibleLessons = computed(() =>
  activePackage.value?.lessons.filter(lesson => !isObsoleteScheduleOccurrence(lesson)) ?? []
);
const pendingTeacherCount = computed(() => visibleLessons.value.filter(isAwaitingTeacherConfirmation).length);
const pendingAdminCount = computed(() =>
  visibleLessons.value.filter(row => row.confirmStage === 'PENDING_ADMIN').length
);
// 优先按规则版本分组；旧数据复用规则 ID 时，使用 API 提供的课位快照边界拆分周期。
const lessonTimelineRows = computed<LessonTimelineRow[]>(() => {
  const pkg = activePackage.value;
  if (!pkg) return [];
  const changes = [...(pkg.scheduleChanges ?? [])].sort((left, right) =>
    scheduleChangeTimestamp(left) - scheduleChangeTimestamp(right)
      || entityKey(left.ruleId).localeCompare(entityKey(right.ruleId)));
  const periodIndexByRule = new Map(changes.map((change, index) => [entityKey(change.ruleId), index]));
  const legacyBoundaryByRule = new Map(changes
    .map((change, index) => ({ change, index }))
    .filter(({ change }) => change.legacySlotBoundary)
    .map(({ change, index }) => [entityKey(change.ruleId), { index, changedAt: scheduleChangeTimestamp(change) }]));
  const lessonsByPeriod = new Map<number, OneOnOneLesson[]>();
  for (const lesson of visibleLessons.value) {
    const ruleKey = lesson.scheduleRuleId == null ? null : entityKey(lesson.scheduleRuleId);
    const legacyBoundary = ruleKey == null ? undefined : legacyBoundaryByRule.get(ruleKey);
    const lessonTime = dayjs(lesson.plannedStartAt).valueOf();
    const periodIndex = legacyBoundary
      ? (lessonTime >= legacyBoundary.changedAt ? legacyBoundary.index : -1)
      : ruleKey == null ? -1 : periodIndexByRule.get(ruleKey) ?? -1;
    const periodLessons = lessonsByPeriod.get(periodIndex) ?? [];
    periodLessons.push(lesson);
    lessonsByPeriod.set(periodIndex, periodLessons);
  }
  const sortByLessonDate = (lessons: OneOnOneLesson[]) => lessons.sort((left, right) =>
    dayjs(left.plannedStartAt).valueOf() - dayjs(right.plannedStartAt).valueOf()
      || entityKey(left.occurrenceId).localeCompare(entityKey(right.occurrenceId)));
  const rows: LessonTimelineRow[] = [];
  const appendLessons = (periodIndex: number) => {
    for (const lesson of sortByLessonDate(lessonsByPeriod.get(periodIndex) ?? [])) {
      rows.push({ kind: 'lesson', key: `lesson-${entityKey(lesson.occurrenceId)}`, lesson });
    }
  };
  appendLessons(-1);
  changes.forEach((change, index) => {
    if (!(lessonsByPeriod.get(index)?.length)) return;
    rows.push({
      kind: 'change',
      key: `change-${entityKey(change.ruleId)}-${scheduleChangeTimestamp(change)}`,
      change,
    });
    appendLessons(index);
  });
  return rows;
});

// 修改排课时被替换的旧课次只用于计划审计，不是待消课或已收费的课程记录。
function isObsoleteScheduleOccurrence(lesson: OneOnOneLesson) {
  return lesson.planningStatus === 'CANCELLED' && lesson.confirmStage !== 'CONFIRMED';
}

function scheduleChangeTimestamp(change: OneOnOneScheduleChange) {
  const time = change.changedAt ? dayjs(change.changedAt).valueOf() : Number.NaN;
  return Number.isFinite(time) ? time : Number.POSITIVE_INFINITY;
}

function isKeptFromPreviousSchedule(lesson: OneOnOneLesson) {
  if (!lesson.scheduleRuleId) return false;
  const lessonTime = dayjs(lesson.plannedStartAt).valueOf();
  const latestChange = [...(activePackage.value?.scheduleChanges ?? [])]
    .filter(change => scheduleChangeTimestamp(change) <= lessonTime)
    .sort((left, right) => scheduleChangeTimestamp(right) - scheduleChangeTimestamp(left))[0];
  return latestChange != null && entityKey(lesson.scheduleRuleId) !== entityKey(latestChange.ruleId);
}

function lessonTimelineSpanMethod({ row, columnIndex }: { row: LessonTimelineRow; columnIndex: number }) {
  if (row.kind !== 'change') return [1, 1];
  return columnIndex === 0 ? [1, canConfirmLessons.value || canManageLessonResources.value ? 4 : 3] : [0, 0];
}

function formatScheduleChangeTime(value?: string) {
  return value ? dayjs(value).format('MMM D, YYYY HH:mm') : 'time unavailable';
}
const signaturePackage = computed(() => {
  if (!activeStudent.value || !activePackage.value) return null;
  const coursePackage = findSignaturePackage(
    teachers.value, activeStudent.value.studentId, activePackage.value.packageId,
  )?.coursePackage ?? activePackage.value;
  return signatureHistoryPackageId.value === String(coursePackage.packageId)
    ? withSignatureHistory(coursePackage, signatureHistory.value)
    : coursePackage;
});
const signatureSheetRows = computed(() => signaturePackage.value ? signatureLessonRows(signaturePackage.value) : []);
const signatureSheetHours = computed(() =>
  signatureSheetRows.value.reduce((total, row) => total + Number(row.plannedUnits ?? 0), 0)
);
const teacherPrintRows = computed(() => {
  const selectedKey = selectedTeacherPrintKey.value;
  return signatureSheetRows.value
    .filter((row) => selectedKey === '__all__'
      || scheduleTeacherKey(row) === selectedKey)
    .sort((left, right) => dayjs(left.plannedStartAt).valueOf() - dayjs(right.plannedStartAt).valueOf()
      || signatureSubjectLabel(left).localeCompare(signatureSubjectLabel(right))
      || signatureLessonTeacher(left).localeCompare(signatureLessonTeacher(right)));
});
const teacherPrintTeacherNames = computed(() => Array.from(new Set(
  teacherPrintRows.value.map(row => row.plannedTeacherName?.trim() || signatureLessonTeacher(row))
)));
const teacherPrintHoursSummaries = computed(() => {
  const selectedKey = selectedTeacherPrintKey.value;
  return teacherPrintOptions.value
    .filter(option => selectedKey === '__all__' || option.key === selectedKey)
    .map(option => {
      const hours = activePackage.value?.teacherHours?.find(item => entityKey(item.teacherId) === option.key)
        ?? activePackage.value?.teacherHours?.find(item =>
          !item.teacherId && optionKey(item.teacherName) === optionKey(option.teacher));
      return {
        teacherId: option.key,
        teacherName: option.teacher,
        allocatedUnits: hours?.allocatedUnits ?? 0,
        deductedUnits: hours?.deductedUnits ?? 0,
        remainingUnits: hours?.remainingUnits ?? 0,
      };
    });
});
const teacherPrintStartDate = computed(() =>
  formatSignatureDate(teacherPrintRows.value[0]?.plannedStartAt ?? '')
);
const signatureSubjectSummaries = computed<SignatureSubjectSummary[]>(() => {
  const groups = new Map<string, SignatureSubjectSummary>();
  signatureSheetRows.value.forEach((row) => {
    const subject = signatureSubjectLabel(row);
    const teacher = signatureLessonTeacher(row);
    // 按"科目 + 老师"聚合：同一科目若由多位老师授课，需分别成行，
    // 否则抬头老师与合计课时会与实际不符（如把两位老师的课时都算到第一位名下）。
    const teacherKey = signatureTeacherKey(row, teacher);
    const key = `${subject}__${teacherKey}`;
    const existing = groups.get(key);
    if (existing) {
      existing.rows.push(row);
      existing.hours += Number(row.plannedUnits ?? 0);
      return;
    }
    groups.set(key, {
      key,
      subject,
      teacher,
      teacherKey,
      classTime: '',
      hours: Number(row.plannedUnits ?? 0),
      startDate: formatSignatureDate(row.plannedStartAt),
      rows: [row],
    });
  });
  return Array.from(groups.values()).map((group) => ({
    ...group,
    classTime: subjectClassTime(group.rows),
    startDate: formatSignatureDate(group.rows[0]?.plannedStartAt ?? ''),
  }));
});
const teacherPrintOptions = computed(() => {
  const options = new Map<string, { key: string; teacher: string }>();
  for (const row of signatureSheetRows.value) {
    const teacher = row.plannedTeacherName?.trim() || signatureLessonTeacher(row);
    const key = scheduleTeacherKey(row);
    if (!options.has(key)) {
      options.set(key, { key, teacher });
    }
  }
  return Array.from(options.values()).sort((left, right) => left.teacher.localeCompare(right.teacher));
});
function signatureCourseLabel(row?: OneOnOneLesson) {
  const pkg = activePackage.value;
  if (!pkg) {
    return '—';
  }
  const subject = row?.subjectName?.trim() || 'Course name not recorded';
  const grade = pkg.gradeBand?.trim() || activeStudent.value?.grade?.trim();
  const label = [grade, subject].filter(Boolean).join(' ');
  return label || pkg.packageName;
}
function signatureSubjectLabel(row?: OneOnOneLesson) {
  return signatureSubjectName(row, signaturePackage.value);
}
function subjectClassTime(rows: OneOnOneLesson[]) {
  const schedules = new Map<string, Set<number>>();
  rows
    .filter(row => row.plannedStartAt && row.plannedEndAt)
    .forEach((row) => {
      const timeKey = signatureTimeOfDayKey(row);
      if (!timeKey) return;
      const days = schedules.get(timeKey) ?? new Set<number>();
      days.add(dayjs(row.plannedStartAt).day());
      schedules.set(timeKey, days);
    });
  if (schedules.size === 0) return 'To be scheduled';
  return Array.from(schedules.entries())
    .map(([timeKey, days]) => {
      const dayLabels = Array.from(days)
        .sort((left, right) => left - right)
        .map(signatureWeekdayLabel);
      return `${dayLabels.join('&')} ${timeKey}`;
    })
    .join(', ');
}
const workflowComplete = computed(() => !nextActionLesson.value
  && !nextArrangementLesson.value
  && pendingTeacherCount.value === 0
  && pendingAdminCount.value === 0
);
const nextArrangementLesson = computed(() => {
  if (!canManageLessonResources.value) return null;
  return activePackage.value?.lessons.find(row =>
    canAssignLessonResources(row) && !row.plannedTeacherId,
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
  if (nextArrangementLesson.value) return 'Set teacher before deduction';
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
    return `${formatDate(row.plannedStartAt)} ${formatTimeRange(row.plannedStartAt, row.plannedEndAt)} has no assigned teacher. Set a teacher before attendance or deduction can continue.`;
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
const formalLeaveTeacherHoursHint = computed(() => {
  const lesson = selectedLesson.value;
  if (!lesson) return '';
  const teacherName = displayResourceName(lesson.plannedTeacherName);
  return `Teacher allocation for this lesson: ${teacherName} · ${formatHours(lesson.plannedUnits)}h.`;
});
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
  teacherId: undefined as EntityId | undefined,
  subjectName: '',
  deliveryMode: 'ONLINE' as 'ONLINE' | 'IN_PERSON',
  classroomId: undefined as EntityId | undefined,
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
const revokeDialogVisible = ref(false);
const revoking = ref(false);
const revokeLesson = ref<OneOnOneLesson | null>(null);
const revokeReason = ref('');

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
    const printTarget = routePrintTarget();
    const res = await fetchOneOnOneDeductions(printTarget
      ? { studentId: printTarget.studentId, packageId: printTarget.packageId }
      : {});
    teachers.value = res.data.data;
    if (!initializedTeacherList) {
      collapsedTeacherKeys.value = new Set(teacherGradeGroups.value.map(group => group.key));
      initializedTeacherList = true;
    }
    restoreSelectedPackage();
  } catch (err) {
    ElMessage.error(extractErrorMessage(err));
    teachers.value = [];
    clearSelection();
  } finally {
    loading.value = false;
  }
}

function routePrintTarget() {
  const { studentId, packageId, printSheet } = route.query;
  if (typeof studentId !== 'string' || typeof packageId !== 'string'
    || (printSheet !== 'parent' && printSheet !== 'teacher')) return null;
  return { studentId, packageId, mode: printSheet };
}

/** 排课页带入消课目标：选中指定学生课包，落到现有消课明细流程。 */
function selectRouteDeductionTarget() {
  const { focus, studentId, packageId } = route.query;
  if (focus !== 'deduction' || typeof studentId !== 'string' || typeof packageId !== 'string') return;

  const candidate = allPackageCandidates.value.find(({ student, pkg }) =>
    String(student.studentId) === studentId && String(pkg.packageId) === packageId);
  if (!candidate) {
    ElMessage.warning('The selected student course is not available in the deduction list.');
    return;
  }
  selectCandidatePackage(candidate);
}

/** 排课页打开打印标签后，等目标课包加载并选中，再调用现有签字表打印流程。 */
async function printRoutePackage() {
  const target = routePrintTarget();
  if (!target) return;
  const candidate = allPackageCandidates.value.find(({ student, pkg }) =>
    String(student.studentId) === target.studentId && String(pkg.packageId) === target.packageId);
  if (!candidate) {
    ElMessage.warning('The selected student sheet is unavailable.');
    return;
  }
  selectCandidatePackage(candidate);
  if (target.mode === 'teacher' && teacherPrintOptions.value.length === 0) {
    ElMessage.warning('No teacher sheet is available for this package yet.');
    return;
  }
  await printCurrentPackage(target.mode);
}

async function searchStudents() {
  const keyword = studentSearchKeyword.value.trim();
  if (!keyword) {
    clearStudentSearch();
    clearSelection();
    return;
  }
  if (keyword.length < 2) {
    ElMessage.warning('Enter at least two characters to search students.');
    return;
  }

  studentSearchLoading.value = true;
  appliedStudentSearch.value = keyword;
  clearSelection();
  try {
    const response = await fetchStudents({ keyword, pageNum: 1, pageSize: 20 });
    const profiles = response.data.data.records;
    matchedSearchStudentIds.value = new Set(profiles.map(profile => String(profile.id)));
    const entriesByStudentId = new Map<string, StudentEntry>();
    studentEntries.value.forEach((entry) => {
      entriesByStudentId.set(String(entry.student.studentId), entry);
      entry.student.packages.forEach((coursePackage) => {
        coursePackage.participants?.forEach((participant) => {
          if (participant.studentId != null) entriesByStudentId.set(String(participant.studentId), entry);
        });
      });
    });
    const matchedEntries = new Map<string, StudentEntry>();
    profiles.forEach((profile) => {
      const entry = entriesByStudentId.get(String(profile.id));
      if (entry) matchedEntries.set(entityKey(entry.student.studentId), entry);
    });
    studentSearchResults.value = [...matchedEntries.values()];
    if (isTeacher.value) {
      expandedStudentGroupKeys.value = new Set(teacherStudentGroups.value.map(group => group.key));
    } else {
      const matchingTeacherKeys = new Set(teacherGradeGroups.value.map(group => group.key));
      collapsedTeacherKeys.value = new Set(
        [...collapsedTeacherKeys.value].filter(groupKey => !matchingTeacherKeys.has(groupKey)),
      );
    }
  } catch (err) {
    studentSearchResults.value = [];
    matchedSearchStudentIds.value = new Set();
    ElMessage.error(extractErrorMessage(err));
  } finally {
    studentSearchLoading.value = false;
  }
}

// 同一课包会出现在多个老师分组；刷新与列表高亮必须使用完整的选中身份。
function isSelectedCandidate(candidate: PackageCandidate) {
  return activeTeacher.value != null
    && activeStudent.value != null
    && activePackage.value != null
    && teacherKey(candidate.teacher) === teacherKey(activeTeacher.value)
    && sameId(candidate.student.studentId, activeStudent.value.studentId)
    && sameId(candidate.pkg.packageId, activePackage.value.packageId);
}

// 消课或调整后重载列表时，优先恢复原老师下的课包；原分组消失则清空选择。
function restoreSelectedPackage() {
  const selected = allPackageCandidates.value.find(isSelectedCandidate);
  if (selected) {
    selectPackage(selected.teacher, selected.student, selected.pkg);
  } else {
    clearSelection();
  }
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
  return pkg.courseProductName?.trim() || pkg.packageName;
}

function candidatePackageLabel(pkg: OneOnOnePackage) {
  const participantCount = pkg.participantCount ?? 1;
  if (participantCount <= 1) {
    return pkg.packageName;
  }
  const names = (pkg.participants ?? [])
    .map(participant => participant.studentName?.trim())
    .filter((name): name is string => Boolean(name));
  const studentSummary = names.length > 0 ? ` · ${names.join(' / ')}` : '';
  return `1-on-${participantCount} · ${pkg.packageName}${studentSummary}`;
}

function teacherStudentGroupKey(student: OneOnOneStudent, pkg: OneOnOnePackage) {
  return pkg.privateLessonGroupId == null
    ? `teacher:student:${entityKey(student.studentId)}`
    : `teacher:private-group:${entityKey(pkg.privateLessonGroupId)}`;
}

function matchesCandidateFilters(candidate: PackageCandidate) {
  if (activeStudentSearch.value
    && !searchedStudentKeys.value.has(candidate.studentKey)
    && !(candidate.pkg.participants ?? []).some(participant => searchedStudentKeys.value.has(entityKey(participant.studentId)))) return false;
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
  clearSelection();
}

function clearStudentSearch() {
  appliedStudentSearch.value = '';
  studentSearchResults.value = [];
  matchedSearchStudentIds.value = new Set();
}

function onScopeFilterChanged() {
  clearSelection();
}

function onCourseTypeChanged() {
  selectedGradeBand.value = undefined;
  onScopeFilterChanged();
}

function selectCandidatePackage(candidate: PackageCandidate) {
  selectPackage(candidate.teacher, candidate.student, candidate.pkg);
  if (!appStore.isMobileViewport) return;

  packagePanelCollapsed.value = true;
  // 等课次明细渲染完成后滚动到选中信息，用户进入列表时仍能确认当前学生和课程。
  void nextTick(() => {
    printAreaRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
}

/** 移动端从课次记录返回课程名单，并将新展开的名单滚入视口。 */
function showCourseList() {
  packagePanelCollapsed.value = false;
  void nextTick(() => {
    packagePanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
}

function toggleTeacherGroup(groupKey: string) {
  const next = new Set(collapsedTeacherKeys.value);
  if (next.has(groupKey)) {
    next.delete(groupKey);
  } else {
    next.add(groupKey);
  }
  collapsedTeacherKeys.value = next;
}

function toggleGradeBandGroup(groupKey: string) {
  const next = new Set(collapsedGradeBandKeys.value);
  if (next.has(groupKey)) {
    next.delete(groupKey);
  } else {
    next.add(groupKey);
  }
  collapsedGradeBandKeys.value = next;
}

function toggleStudentGroup(studentGroupKey: string) {
  const next = new Set(expandedStudentGroupKeys.value);
  if (next.has(studentGroupKey)) {
    next.delete(studentGroupKey);
  } else {
    next.add(studentGroupKey);
  }
  expandedStudentGroupKeys.value = next;
}

function selectPackage(teacher: OneOnOneTeacher, student: OneOnOneStudent, pkg: OneOnOnePackage) {
  activeTeacher.value = teacher;
  activeStudent.value = student;
  activePackage.value = pkg;
  if (isTeacher.value) {
    const groupKey = teacherStudentGroupKey(student, pkg);
    expandedStudentGroupKeys.value = new Set([...expandedStudentGroupKeys.value, groupKey]);
    return;
  }
  const teacherGroupKey = entityKey(teacher.teacherId)
    || `teacher-name:${optionKey(teacher.teacherName)}`;
  const gradeGroupKey = `${teacherGroupKey}::grade:${optionKey(normalizedGradeBand(pkg.gradeBand))}`;
  const collapsedTeacher = new Set(collapsedTeacherKeys.value);
  collapsedTeacher.delete(teacherGroupKey);
  collapsedTeacherKeys.value = collapsedTeacher;
  const collapsedGradeBands = new Set(collapsedGradeBandKeys.value);
  collapsedGradeBands.delete(gradeGroupKey);
  collapsedGradeBandKeys.value = collapsedGradeBands;
  const groupKey = pkg.privateLessonGroupId == null
    ? `${gradeGroupKey}::student:${entityKey(student.studentId)}`
    : `${gradeGroupKey}::private-group:${entityKey(pkg.privateLessonGroupId)}`;
  expandedStudentGroupKeys.value = new Set([...expandedStudentGroupKeys.value, groupKey]);
}

function clearSelection() {
  activeTeacher.value = null;
  activeStudent.value = null;
  activePackage.value = null;
  expandedStudentGroupKeys.value = new Set();
}

function canAdminConfirm(row: OneOnOneLesson) {
  if (!isAdmin.value || !canConfirmPackageLessons.value || row.planningStatus === 'CANCELLED') return false;
  if (row.confirmStage === 'CONFIRMED' || row.confirmStage === 'REJECTED') return false;
  if (row.confirmStage === 'PENDING_ADMIN') return Boolean(row.lessonRecordId);
  return isAwaitingTeacherConfirmation(row);
}

function canRevokeLesson(row: OneOnOneLesson) {
  return canUndoAdminDecision.value
    && row.confirmStage === 'CONFIRMED'
    && Boolean(row.lessonRecordId)
    && row.recordVersion != null;
}

function revokeImpactMessage(row: OneOnOneLesson) {
  const sharedLesson = activePackage.value?.privateLessonGroupId != null;
  const sharedNote = sharedLesson ? ' All student records in this shared lesson will be undone together.' : '';
  if (row.resultStatus === 'FORMAL_LEAVE') {
    return `The approved leave will be undone and its unprocessed makeup lesson removed. The original lesson will return to the review queue.${sharedNote}`;
  }
  const hours = formatHours(row.deductedUnits ?? 0);
  const packageCredit = Number(row.deductedUnits ?? 0) > 0
    ? `${hours}h will be returned to the package. `
    : '';
  return `${packageCredit}This lesson will return to the review queue.${sharedNote}`;
}

function canInitialConfirm(row: OneOnOneLesson) {
  if (!isTeacher.value || !canConfirmPackageLessons.value) return false;
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

function isPastUnprocessed(row: OneOnOneLesson) {
  const plannedEnd = Date.parse(row.plannedEndAt);
  return Number.isFinite(plannedEnd)
    && plannedEnd <= Date.now()
    && row.planningStatus !== 'CANCELLED'
    && row.planningStatus !== 'MERGED'
    && row.confirmStage !== 'CONFIRMED';
}

function canAssignLessonResources(row: OneOnOneLesson) {
  return activePackage.value?.packageStatus === 'ACTIVE'
    && row.planningStatus !== 'CANCELLED'
    && row.confirmStage !== 'PENDING_ADMIN'
    && row.confirmStage !== 'CONFIRMED';
}

function needsLessonResources(row: OneOnOneLesson) {
  return canAssignLessonResources(row)
    && !row.plannedTeacherId;
}

const canConfirmPackageLessons = computed(() => {
  const packageStatus = activePackage.value?.packageStatus;
  return packageStatus === 'ACTIVE' || packageStatus === 'PAUSED';
});

function canAdjustLesson(row: OneOnOneLesson) {
  const teacherOwnsLesson = isTeacher.value
    && String(row.plannedTeacherId ?? '') === String(authStore.currentUser?.teacherId ?? '');
  return activePackage.value?.packageStatus === 'ACTIVE'
    && (canManageLessonResources.value || teacherOwnsLesson)
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
  adjustForm.teacherId = findSelectableResourceId(teacherOptions.value, row.plannedTeacherId);
  adjustForm.subjectName = row.subjectName || '';
  adjustForm.deliveryMode = row.deliveryMode === 'IN_PERSON' || row.plannedClassroomId ? 'IN_PERSON' : 'ONLINE';
  adjustForm.classroomId = findSelectableResourceId(classrooms.value, row.plannedClassroomId);
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
  if (!assignmentLesson.value || !assignmentForm.teacherId) {
    ElMessage.warning('Select a teacher.');
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

  adjusting.value = true;
  try {
    await createScheduleAdjustment({
      occurrenceId: adjustLesson.value.occurrenceId,
      adjustmentType: 'RESCHEDULE',
      plannedTeacherId: adjustForm.teacherId,
      subjectName: adjustForm.subjectName,
      deliveryMode: adjustForm.deliveryMode,
      plannedClassroomId: adjustForm.deliveryMode === 'IN_PERSON' ? adjustForm.classroomId : undefined,
      plannedStartAt: adjustForm.plannedStartAt,
      plannedEndAt: adjustForm.plannedEndAt,
      plannedUnits,
      reason,
    });
    ElMessage.success('Lesson adjustment saved.');
    adjustDialogVisible.value = false;
    await loadData();
  } catch (err) {
    ElMessage.error(extractErrorMessage(err));
  } finally {
    adjusting.value = false;
  }
}

function lessonRowClassName({ row }: { row: LessonTimelineRow }) {
  if (row.kind === 'change') return 'oo-row--schedule-change';
  const lesson = row.lesson;
  if (isPastUnprocessed(lesson)) return 'oo-row--overdue-unprocessed';
  if (nextActionLesson.value?.occurrenceId === lesson.occurrenceId) return 'oo-row--next';
  if (lesson.confirmStage === 'PENDING_ADMIN') return 'oo-row--pending-admin';
  if (isRejectedOrAbnormal(lesson)) return 'oo-row--issue';
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
      // 此窗口不允许选择实际老师或教室，避免把列表中的旧快照覆盖到当前课次或老师提交记录。
      // 管理员直接确认由后端读取当前排课；审核老师提交则由后端读取待审核记录。
      actualTeacherId: undefined,
      actualClassroomId: undefined,
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

function openRevokeDialog(row: OneOnOneLesson) {
  if (!canRevokeLesson(row)) return;
  revokeLesson.value = row;
  revokeReason.value = '';
  revokeDialogVisible.value = true;
}

async function submitRevoke() {
  const row = revokeLesson.value;
  if (!row?.lessonRecordId || row.recordVersion == null) return;
  revoking.value = true;
  try {
    await revokeLessonRecord(row.lessonRecordId, {
      reason: revokeReason.value.trim() || undefined,
      expectedVersion: row.recordVersion,
    });
    revokeDialogVisible.value = false;
    ElMessage.success('Lesson decision undone. The lesson is available for review again.');
    await loadData();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    revoking.value = false;
  }
}

async function printCurrentPackage(mode: 'parent' | 'teacher') {
  if (!activePackage.value) return;
  if (!(await loadSignatureHistory(activePackage.value.packageId))) return;
  printMode.value = mode;
  selectedTeacherPrintKey.value = '__all__';
  // 打印当前课包的快照；先等模式更新后再复制 DOM，避免打印上一次的签字表。
  await nextTick();
  if (!signaturePrinter.print()) ElMessage.error('The signature sheet is not ready to print.');
}

async function printTeacherSheet(command: string | number | object) {
  if (!activePackage.value) return;
  if (!(await loadSignatureHistory(activePackage.value.packageId))) return;
  printMode.value = 'teacher';
  selectedTeacherPrintKey.value = String(command || '__all__');
  await nextTick();
  if (!signaturePrinter.print()) ElMessage.error('The signature sheet is not ready to print.');
}

/** 打印前补充有凭据的历史签字行；老师账户仍只看到自己可见的正式课次。 */
async function loadSignatureHistory(packageId: EntityId): Promise<boolean> {
  if (!isAdmin.value || signatureHistoryPackageId.value === String(packageId)) return true;
  try {
    const response = await fetchPrivateSignatureHistory(packageId);
    if (String(activePackage.value?.packageId) !== String(packageId)) return false;
    signatureHistory.value = response.data.data;
    signatureHistoryPackageId.value = String(packageId);
    return true;
  } catch (error) {
    if (String(activePackage.value?.packageId) !== String(packageId)) return false;
    // 历史签字接口不可用时仍打印已加载的正式排课，避免辅助数据服务故障阻断整张课表。
    ElMessage.warning('Historical signature entries could not be loaded. The sheet will include scheduled lessons only.');
    return true;
  }
}

function signatureTeacherKey(row: OneOnOneLesson, teacherName: string) {
  return entityKey(row.confirmStage === 'CONFIRMED'
    ? row.actualTeacherId || row.plannedTeacherId
    : row.plannedTeacherId) || optionKey(teacherName);
}

function scheduleTeacherKey(row: OneOnOneLesson) {
  return entityKey(row.plannedTeacherId) || optionKey(row.plannedTeacherName);
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
  if (row.resultStatus === 'CANCELLED') return 'Student cancellation (charged)';
  if (row.resultStatus === 'NO_SHOW') return 'No show (charged)';
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
    return 'Student cancellation deducts the full planned hours and does not create a replacement lesson.';
  }
  return 'No show deducts the full planned hours and does not create a replacement lesson.';
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

function formatSignatureDate(value: string) {
  return value ? dayjs(value).format('M/D/YYYY') : '—';
}

function signatureLessonDate(row: OneOnOneLesson) {
  if (!row.signatureHistory) return formatSignatureDate(row.plannedStartAt);
  const [year, month, day] = row.plannedStartAt.slice(0, 10).split('-');
  return year && month && day ? `${Number(month)}/${Number(day)}/${year}` : '—';
}

function signatureWeeklyTime(row: OneOnOneLesson) {
  if (!row.plannedStartAt || !row.plannedEndAt) return '';
  return `${signatureWeekdayLabel(dayjs(row.plannedStartAt).day())} ${signatureTimeOfDayKey(row)}`;
}

function signatureTimeOfDayKey(row: OneOnOneLesson) {
  if (row.signatureHistory) return '';
  if (!row.plannedStartAt || !row.plannedEndAt) return '';
  const start = dayjs(row.plannedStartAt).format('h:mma').toLowerCase();
  const end = dayjs(row.plannedEndAt).format('h:mma').toLowerCase().replace(':00', '');
  return `${start}-${end}`;
}

function signatureWeekdayLabel(day: number) {
  return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][day] ?? '';
}

function signatureLessonTeacher(row: OneOnOneLesson) {
  return displayResourceName(row.confirmStage === 'CONFIRMED'
    ? row.actualTeacherName || row.plannedTeacherName
    : row.plannedTeacherName);
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
  signaturePrinter.attach();
  await Promise.all([loadTeachers(), loadClassrooms()]);
  await loadData();
  selectRouteDeductionTarget();
  await printRoutePackage();
});
onBeforeUnmount(() => signaturePrinter.detach());
</script>

<style scoped>
.oo-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: calc(100vh - 92px);
  min-height: 0;
  overflow: hidden;
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

.oo-filter-toggle {
  display: inline-flex;
  width: 36px;
  min-width: 36px;
  height: 36px;
  min-height: 36px;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  background: #f8fafc;
  color: #486581;
  cursor: pointer;
  font-size: 16px;
}

.oo-filter-toggle:hover,
.oo-filter-toggle:focus-visible {
  border-color: #60a5fa;
  background: #eff6ff;
  color: #1d4ed8;
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

.oo-student-search__submit :deep(.el-icon) {
  display: none;
}

.oo-workspace {
  display: flex;
  min-height: 0;
  flex: 1 1 0;
  flex-direction: column;
  overflow: hidden;
  padding: 12px;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fff;
}

.oo-student-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
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

.oo-private-group-tag {
  margin-left: 6px;
  vertical-align: 1px;
}

.oo-student-context .oo-private-group-students {
  color: #2563eb;
  font-size: 11px;
  font-weight: 600;
}

.oo-student-context > div:first-child > span {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.oo-student-context__metrics {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
  max-width: 100%;
}

.oo-student-context__metric-group {
  display: grid;
  grid-template-columns: repeat(3, minmax(82px, 1fr));
  align-items: center;
  gap: 12px;
}

.oo-student-context__metric-group--teacher {
  grid-template-columns: repeat(4, minmax(82px, 1fr));
  border-left: 1px solid #cbd5e1;
  padding-left: 16px;
}

.oo-student-context__metric {
  color: #102a43;
  font-size: 15px;
  font-weight: 800;
  text-align: right;
  white-space: normal;
}

.oo-student-context__metrics small {
  display: block;
  margin-bottom: 2px;
  color: #64748b;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
}

.oo-student-context__metrics .oo-metric--overallocated {
  color: #c2410c;
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
  flex: 1 1 0;
  height: 100%;
  min-height: 0;
}

.oo-split-workspace--collapsed {
  grid-template-columns: 42px minmax(0, 1fr);
}

.oo-package-panel,
.oo-record-panel {
  min-width: 0;
  height: 100%;
  min-height: 0;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fff;
}

.oo-package-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
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

.oo-package-panel__tools .oo-filter-toggle,
.oo-package-panel__tools :deep(.oo-panel-toggle) {
  box-sizing: border-box;
  display: inline-flex;
  width: 40px;
  min-width: 40px;
  height: 40px;
  min-height: 40px;
  flex: 0 0 40px;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #f8fafc;
  color: #486581;
  line-height: 1;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.oo-package-panel__tools :deep(.oo-panel-toggle) {
  font-size: 24px;
  font-weight: 700;
}

.oo-package-panel__tools .oo-filter-toggle:hover,
.oo-package-panel__tools .oo-filter-toggle:focus-visible,
.oo-package-panel__tools :deep(.oo-panel-toggle:hover),
.oo-package-panel__tools :deep(.oo-panel-toggle:focus-visible) {
  border-color: #60a5fa;
  background: #eff6ff;
  color: #1d4ed8;
}

.oo-package-toggle {
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

.oo-collapsed-tools {
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.oo-package-toggle:hover,
.oo-package-panel__tools :deep(.oo-panel-toggle:hover) {
  border-color: #60a5fa;
  background: #eff6ff;
  color: #1d4ed8;
}

.oo-package-toggle {
  align-self: start;
  margin-top: 12px;
}

.oo-package-list {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  max-height: none;
  overflow-y: scroll;
  padding: 8px;
  scrollbar-gutter: stable;
  scrollbar-color: #94a3b8 #edf2f7;
  scrollbar-width: thin;
}

.oo-package-list--students {
  min-height: 0;
}

.oo-mobile-lessons {
  display: grid;
  gap: 8px;
}

.oo-schedule-change {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 8px 4px;
  color: #475569;
  font-size: 12px;
}

.oo-schedule-change strong {
  flex: none;
  font-weight: 600;
}

.oo-schedule-change__line {
  flex: 1;
  height: 1px;
  background: #94a3b8;
}

.oo-schedule-change__kept {
  color: #64748b !important;
  font-size: 11px !important;
}

.oo-schedule-change--mobile {
  padding: 10px 0;
}

.oo-mobile-lesson {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #fff;
}

.oo-mobile-lesson--overdue-unprocessed {
  border-color: #f59e0b;
  background: #fff3cd;
}

.oo-mobile-lesson__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.oo-mobile-lesson__heading .oo-simple-cell {
  min-width: 0;
}

.oo-mobile-lesson__heading .status-pill {
  flex-shrink: 0;
}

.oo-mobile-lesson__time {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  color: #475569;
  font-size: 12px;
}

.oo-mobile-lesson__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #edf2f7;
}

.oo-mobile-lesson__actions .el-button {
  flex: 1 1 140px;
  min-height: 44px;
  margin: 0;
}

.oo-package-list::-webkit-scrollbar {
  width: 10px;
}

.oo-package-list::-webkit-scrollbar-track {
  background: #edf2f7;
}

.oo-package-list::-webkit-scrollbar-thumb {
  border: 2px solid #edf2f7;
  border-radius: 8px;
  background: #94a3b8;
}

.oo-package-list::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}

.oo-teacher-group {
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #f8fafc;
}

.oo-teacher-toggle,
.oo-grade-band-toggle {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border: 0;
  background: #f1f5f9;
  color: #102a43;
  cursor: pointer;
  text-align: left;
}

.oo-teacher-toggle {
  background: #eaf1f8;
  color: #102a43;
}

.oo-teacher-toggle:hover,
.oo-grade-band-toggle:hover {
  background: #eaf1f8;
}

.oo-teacher-toggle__title,
.oo-grade-band-toggle__title {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 6px;
}

.oo-teacher-toggle__title strong,
.oo-grade-band-toggle__title strong {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oo-grade-band-toggle__title > span {
  flex: 0 0 auto;
  padding: 2px 5px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 10px;
  font-weight: 700;
}

.oo-teacher-toggle small,
.oo-grade-band-toggle small {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 10px;
}

.oo-teacher-toggle > span:last-child,
.oo-grade-band-toggle > span:last-child {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 18px;
  line-height: 1;
  transition: transform 0.15s ease;
}

.oo-teacher-toggle--collapsed > span:last-child,
.oo-grade-band-toggle--collapsed > span:last-child {
  transform: rotate(0deg);
}

.oo-teacher-toggle:not(.oo-teacher-toggle--collapsed) > span:last-child,
.oo-grade-band-toggle:not(.oo-grade-band-toggle--collapsed) > span:last-child {
  transform: rotate(90deg);
}

.oo-teacher-grades {
  display: grid;
  gap: 5px;
  padding: 6px;
  border-top: 1px solid #e2e8f0;
}

.oo-grade-band-group {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 5px;
  background: #fff;
}

.oo-grade-band-toggle {
  padding: 8px 9px;
  background: #f8fafc;
  color: #243b53;
}

.oo-grade-band-students {
  display: grid;
  gap: 5px;
  padding: 6px;
  border-top: 1px solid #e2e8f0;
}

.oo-student-group {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 5px;
  background: #fff;
}

.oo-student-group__toggle {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  border: 0;
  background: transparent;
  color: #102a43;
  cursor: pointer;
  text-align: left;
}

.oo-student-group__toggle:hover,
.oo-student-group__toggle--open {
  background: #f8fafc;
}

.oo-student-group__toggle strong {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oo-student-group__toggle small {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 10px;
}

.oo-student-group__toggle span {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 18px;
  line-height: 1;
  transition: transform 0.15s ease;
}

.oo-student-group__toggle--open span {
  transform: rotate(90deg);
}

.oo-student-group__packages {
  display: grid;
  gap: 4px;
  padding: 5px;
  border-top: 1px solid #e2e8f0;
}

.oo-package-item {
  padding: 7px 8px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #334155;
  cursor: pointer;
  font-size: 12px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oo-package-item:hover,
.oo-package-item--active {
  border-color: #60a5fa;
  background: #f0f7ff;
  color: #1d4ed8;
}

.oo-package-item--detailed {
  display: flex;
  flex-direction: column;
  gap: 3px;
  white-space: normal;
}

.oo-package-item--detailed > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oo-package-item--detailed small {
  color: #64748b;
  font-size: 10px;
}

.oo-record-panel {
  overflow-x: hidden;
  overflow-y: auto;
  padding: 12px;
  scrollbar-color: #94a3b8 #edf2f7;
  scrollbar-width: thin;
}

.oo-record-panel::-webkit-scrollbar {
  width: 10px;
}

.oo-record-panel::-webkit-scrollbar-track {
  background: #edf2f7;
}

.oo-record-panel::-webkit-scrollbar-thumb {
  border: 2px solid #edf2f7;
  border-radius: 8px;
  background: #94a3b8;
}

.oo-record-panel::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}

.oo-signature-sheet {
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid #111;
  border-radius: 6px;
  background: #fff;
  color: #111;
  font-family: Arial, Helvetica, sans-serif;
}

.oo-signature-sheet__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 8px;
}

.oo-signature-sheet__meta p {
  margin: 0;
  color: #111;
  font-size: 16px;
  line-height: 1.22;
}

.oo-signature-sheet__meta strong {
  font-weight: 800;
}

.oo-signature-hours {
  display: flex;
  flex: 1 0 100%;
  gap: 10px;
}

.oo-signature-hours__group {
  display: flex;
  flex: 1;
  flex-direction: column;
  flex-wrap: nowrap;
  align-items: flex-start;
  gap: 4px;
  padding: 7px 10px;
  border: 1px solid #777;
  border-radius: 4px;
}

.oo-signature-hours__group p {
  white-space: nowrap;
}

.oo-signature-hours__group--balance {
  border-left: 3px solid #222;
  background: #f2f2f2;
}

.oo-signature-subjects {
  display: grid;
  gap: 2px;
  margin-bottom: 30px;
}

.oo-signature-subject-line {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1.45fr 1fr 1fr;
  gap: 16px;
  color: #111;
  font-size: 15px;
  font-weight: 600;
}

.oo-signature-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  color: #111;
  font-size: 14px;
}

.oo-signature-table th,
.oo-signature-table td {
  height: 22px;
  padding: 2px 4px;
  border: 1px solid #111;
  text-align: left;
  vertical-align: middle;
  word-break: break-word;
}

.oo-signature-table th {
  font-weight: 700;
}

.oo-teacher-signature-sheet {
  display: none;
}

.oo-teacher-sheet__meta {
  display: grid;
  grid-template-columns: auto auto auto;
  gap: 6px 36px;
  margin-bottom: 32px;
}

.oo-teacher-sheet__meta p {
  margin: 0;
  color: #111;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
}

.oo-teacher-sheet__meta p:first-child {
  grid-column: 1 / -1;
}

.oo-teacher-sheet__meta p:nth-child(4) {
  grid-column: 1;
}

.oo-signature-sheet__remaining {
  margin-top: 34px;
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

.oo-table :deep(.oo-row--schedule-change td) {
  background: #f1f5f9 !important;
  border-top: 1px solid #94a3b8;
  border-bottom: 1px solid #cbd5e1;
}

.print-only {
  display: none;
}

.oo-simple-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.oo-simple-cell--center {
  align-items: center;
  text-align: center;
}

.oo-simple-cell strong {
  color: #102a43;
  font-size: 13px;
}

.oo-simple-cell span {
  color: #64748b;
  font-size: 12px;
}

.oo-action-cell {
  display: inline-grid;
  grid-template-columns: 96px minmax(0, 1fr);
  align-items: center;
  justify-content: stretch;
  gap: 8px;
  width: 100%;
}

.oo-action-slot {
  display: inline-flex;
  align-items: center;
  min-width: 0;
}

.oo-action-slot--adjust {
  justify-content: flex-end;
}

.oo-action-slot--primary {
  justify-content: center;
  gap: 8px;
}

.oo-action-cell :deep(.el-button) {
  margin-left: 0;
  white-space: nowrap;
}

.oo-action-slot--adjust :deep(.el-button) {
  min-width: 96px;
}

.oo-action-slot--primary :deep(.el-button) {
  min-width: 148px;
}

.oo-action-slot--primary :deep(.el-button.oo-revoke-button) {
  min-width: 78px;
}

.oo-no-action {
  display: inline-block;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.35;
}

.oo-action-reason {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  background: #fff7ed;
  color: #c2410c;
  line-height: 1;
  cursor: help;
  transform: translateX(8px);
}

.oo-action-reason :deep(.el-icon) {
  font-size: 14px;
}

:deep(.oo-row--next) {
  --el-table-tr-bg-color: #fffaf0;
}

:deep(.oo-row--overdue-unprocessed) {
  --el-table-tr-bg-color: #fff3cd;
}

:deep(.oo-row--overdue-unprocessed > td:first-child) {
  box-shadow: inset 4px 0 0 #f59e0b;
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

@media (max-width: 920px) {
  .oo-toolbar {
    display: none;
  }

  .oo-help {
    display: none;
  }

  .oo-selector-bar__mobile-heading {
    display: flex;
    grid-column: 1 / -1;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 1px;
    color: #334155;
    font-size: 12px;
  }

  .oo-selector-bar__mobile-actions {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .oo-clear-filters--mobile {
    min-height: 32px;
    padding: 0 9px;
    border: 1px solid #dbe4ee;
    border-radius: 999px;
    background: #f8fafc;
    color: #64748b;
    cursor: pointer;
    font-size: 11px;
    line-height: 1;
  }

  .oo-clear-filters--mobile:hover,
  .oo-clear-filters--mobile:focus-visible {
    border-color: #b8c7d8;
    background: #f1f5f9;
    color: #334155;
  }

  .oo-selector-bar__mobile-heading .el-button {
    width: 28px;
    height: 28px;
    min-height: 28px;
    margin: -4px -4px -4px 0;
    padding: 0;
  }

  .oo-selector-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: end;
    gap: 7px 8px;
    padding: 8px;
  }

  .oo-selector-bar .oo-field > span {
    font-size: 9px;
  }

  .oo-selector-bar .oo-field :deep(.el-input__inner),
  .oo-selector-bar .oo-field :deep(.el-select__selected-item),
  .oo-selector-bar .oo-field :deep(.el-select__placeholder) {
    font-size: 12px;
  }

  .oo-student-search {
    grid-column: 1 / -1;
    display: flex;
    align-items: stretch;
    flex-direction: row;
    gap: 6px;
  }

  .oo-student-search .oo-field {
    min-width: 0;
  }

  .oo-student-search > .el-button {
    align-self: flex-end;
    width: 40px;
    min-width: 40px;
    height: 34px;
    min-height: 34px;
    margin-bottom: 0;
    padding: 0;
  }

  .oo-student-search__submit .oo-student-search__label {
    display: none;
  }

  .oo-student-search__submit :deep(.el-icon) {
    display: inline-flex;
    font-size: 16px;
  }

  .oo-selector-bar .oo-field :deep(.el-input__wrapper),
  .oo-selector-bar .oo-field :deep(.el-select__wrapper) {
    min-height: 34px;
  }

  .oo-clear-filters {
    grid-column: 2;
    min-height: 32px;
    justify-self: end;
  }

  .oo-split-workspace {
    display: flex;
    align-items: stretch;
    flex-direction: column;
    height: auto;
    min-height: 0;
  }

  .oo-page {
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .oo-workspace {
    display: block;
    flex: initial;
    overflow: visible;
  }

  .oo-package-panel,
  .oo-record-panel {
    height: auto;
  }

  .oo-package-panel {
    max-height: none;
  }

  .oo-package-list {
    flex: 0 1 auto;
    max-height: 260px;
  }

  .oo-record-panel {
    overflow: visible;
  }

  .oo-workspace {
    padding: 8px;
  }

  .oo-action-line {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    align-items: stretch;
    gap: 6px;
    min-height: 0;
    margin-bottom: 8px;
    padding: 10px;
  }

  .oo-action-line__marker {
    display: none;
  }

  .oo-action-line strong {
    font-size: 14px;
    line-height: 1.3;
  }

  .oo-action-line div span {
    font-size: 11px;
    line-height: 1.35;
  }

  .oo-action-line > .el-button {
    width: 100%;
    min-height: 40px;
    margin: 0;
    padding: 0 10px;
    font-size: 14px;
  }

  .oo-student-context {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: start;
    margin-bottom: 8px;
    padding: 2px 0 10px;
  }

  .oo-collapsed-tools {
    width: 100%;
    align-items: center;
    justify-content: flex-start;
    gap: 8px;
    margin: 0 0 2px;
  }

  .oo-collapsed-tools .oo-filter-toggle {
    width: 36px;
    min-width: 36px;
    height: 36px;
    min-height: 36px;
    flex: 0 0 36px;
  }

  .oo-mobile-course-toggle {
    min-height: 36px;
    padding: 0 12px;
    border: 1px solid #d9e2ec;
    border-radius: 9px;
    background: #fff;
    color: #36516f;
    font: inherit;
    font-size: 12px;
    font-weight: 650;
  }

  .oo-mobile-course-toggle:active {
    border-color: #93c5fd;
    background: #eff6ff;
  }

  .oo-student-context > div:first-child > strong {
    font-size: 16px;
    line-height: 1.3;
  }

  .oo-student-context > div:first-child > span {
    margin-top: 2px;
    font-size: 12px;
  }

  .oo-student-context__metrics {
    grid-column: 1 / -1;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    margin-top: 10px;
    margin-left: 0;
    max-width: none;
  }

  .oo-student-context__metric-group {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
  }

  .oo-student-context__metric-group--teacher {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    border-top: 1px solid #cbd5e1;
    border-left: 0;
    padding-top: 8px;
    padding-left: 0;
  }

  .oo-student-context__metric {
    min-width: 0;
    font-size: 13px;
    text-align: left;
    white-space: normal;
  }

  .oo-student-context__metrics small {
    margin-bottom: 1px;
    font-size: 9px;
    line-height: 1.2;
  }

  .oo-table-title {
    margin: 10px 0 6px;
  }

  .oo-table-title strong {
    font-size: 15px;
  }

  .oo-table-title span {
    margin-top: 2px;
    font-size: 11px;
    line-height: 1.35;
  }

  .oo-table-title small {
    display: none;
  }

  .oo-mobile-lessons {
    gap: 7px;
  }

  .oo-mobile-lesson {
    gap: 7px;
    padding: 10px;
  }

  .oo-mobile-lesson__heading .oo-simple-cell strong {
    font-size: 13px;
    line-height: 1.3;
  }

  .oo-mobile-lesson__heading .oo-simple-cell span,
  .oo-mobile-lesson__time {
    font-size: 11px;
  }

  .oo-mobile-lesson__actions {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(104px, 1fr));
    gap: 6px;
    padding-top: 6px;
  }

  .oo-mobile-lesson__actions .el-button {
    width: 100%;
    min-width: 0;
    min-height: 38px;
    margin: 0;
    padding: 0 6px;
    font-size: 12px;
  }

  :deep(.el-dialog.is-fullscreen) {
    display: flex;
    flex-direction: column;
  }

  :deep(.el-dialog.is-fullscreen .el-dialog__body) {
    flex: 1;
  }

  :deep(.el-dialog.is-fullscreen .el-dialog__footer) {
    padding-bottom: max(14px, env(safe-area-inset-bottom));
  }
}

@media print {
  @page {
    size: letter portrait;
    margin: 0.5in 0.5in 0.5in 0.75in;
  }

  :global(.app-sidebar),
  :global(.app-header),
  .no-print {
    display: none !important;
  }

  .oo-page,
  .oo-workspace {
    display: block;
    width: 100%;
    border: 0;
    padding: 0;
    height: auto;
    min-height: auto;
    overflow: visible;
  }

  .oo-package-panel,
  .oo-package-toggle {
    display: none !important;
  }

  .oo-split-workspace,
  .oo-split-workspace--collapsed {
    display: block;
    min-height: auto;
  }

  .print-only {
    display: block !important;
  }

  .oo-print-area--parent .oo-teacher-signature-sheet {
    display: none !important;
  }

  .oo-print-area--teacher .oo-parent-signature-sheet {
    display: none !important;
  }

  .oo-print-area--teacher .oo-teacher-signature-sheet {
    display: block !important;
  }

  .oo-teacher-signature-sheet {
    break-inside: auto;
  }

  .oo-signature-table--teacher {
    break-inside: auto;
  }

  .oo-signature-table--teacher thead {
    display: table-header-group;
  }

  .oo-signature-table--teacher tr {
    break-inside: avoid;
  }

  .oo-print-area {
    color: #000;
  }

  .oo-student-context {
    display: none;
  }

  .oo-record-panel {
    width: 100%;
    border: 0;
    padding: 0;
    height: auto;
    overflow: visible;
  }

  .oo-signature-sheet {
    width: 100%;
    box-sizing: border-box;
    border: 0;
    padding: 0;
    break-inside: avoid;
    font-family: Arial, Helvetica, sans-serif;
  }

  .oo-signature-sheet__meta p {
    font-size: 16px;
  }

  .oo-signature-subject-line,
  .oo-teacher-sheet__meta p {
    font-size: 15px;
  }

  .oo-signature-subjects {
    margin-bottom: 28px;
  }

  .oo-signature-table {
    margin-top: 6px;
    font-size: 14px;
  }

  .oo-signature-table th,
  .oo-signature-table td {
    height: 22px;
    border: 1px solid #111;
  }

}
</style>
