<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Course Packages</span>
      </div>

      <el-form class="filters" :model="filters" inline>
        <el-form-item label="Name / No." class="filters__item">
          <el-input
            v-model="filters.keyword"
            placeholder="Search by package name or no."
            clearable
            style="width: 180px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Student" class="filters__item">
          <el-select
            v-model="filters.studentId"
            placeholder="All students"
            clearable
            filterable
            style="width: 160px;"
            @change="handleFilterChange"
          >
            <el-option v-for="s in students" :key="s.id" :label="s.studentName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Status" class="filters__item">
          <el-select
            v-model="filters.packageStatus"
            placeholder="All"
            clearable
            style="width: 140px;"
            @change="handleFilterChange"
          >
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Paused" value="PAUSED" />
            <el-option label="Closed" value="CLOSED" />
            <el-option label="Pending" value="PENDING" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-tabs v-model="activeTab" class="package-tabs">
        <el-tab-pane label="1 on 1" name="ONE_ON_ONE">
          <el-table
            v-loading="loading"
            :data="tableData"
            row-key="id"
            class="product-table"
            :show-empty="true"
          >
            <el-table-column label="Student" min-width="150" prop="studentName" />
            <el-table-column label="Product / Package" min-width="230">
              <template #default="{ row }">
                <div class="package-name-cell">
                  <strong class="package-name-cell__title">
                    {{ row.packageName || row.courseProductName || 'Unnamed package' }}
                  </strong>
                  <span v-if="shouldShowProductName(row)" class="package-name-cell__context">
                    Product · {{ row.courseProductName }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Available" min-width="145" align="center">
              <template #default="{ row }">
                <strong>{{ formatHours(row.remainingUnits) }} / {{ formatHours(row.totalUnits) }}</strong>
                <el-progress :percentage="progressPercent(row)" :stroke-width="8" :show-text="false" />
              </template>
            </el-table-column>
            <el-table-column label="Schedule setup" min-width="150" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="scheduleStatusType(row.scheduleStatus)">
                  {{ row.scheduleStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Next lesson" min-width="180">
              <template #default="{ row }">
                <span v-if="row.nextLessonStartAt">{{ formatScheduleDate(row.nextLessonStartAt) }}</span>
                <span v-else class="muted-text">—</span>
              </template>
            </el-table-column>
            <el-table-column label="Period" min-width="170">
              <template #default="{ row }">{{ row.startDate ?? '—' }} ~ {{ row.estimatedEndDate ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="Package status" min-width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="packageStatusType(row.packageStatus)">
                  {{ packageStatusLabel(row.packageStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="90" fixed="right">
              <template #default="{ row }">
                <el-button class="row-action-button" text type="primary" @click="openDetailDialog(row)">Detail</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-footer">
            <span>Showing {{ pagination.start }}–{{ pagination.end }} of {{ pagination.total }}</span>
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              background
              layout="prev, pager, next, sizes"
              :page-sizes="[10, 20, 50]"
              :total="pagination.total"
              @current-change="loadData"
              @size-change="loadData"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="Group Class" name="GROUP">
          <el-table
            v-loading="groupLoading"
            :data="groupProductRows"
            row-key="productKey"
            class="product-table"
            :show-empty="true"
            @expand-change="handleGroupExpandChange"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="nested-package-card">
                  <div class="nested-package-card__header">
                    <span>Students in</span>
                    <strong>{{ row.productName }}</strong>
                    <el-select
                      :model-value="selectedGroupClassId(row)"
                      size="small"
                      class="group-grade-select"
                      placeholder="Select grade"
                      @click.stop
                      @change="(value) => selectGroupGrade(row, value)"
                    >
                      <el-option
                        v-for="groupClass in row.classes"
                        :key="groupClass.classId"
                        :label="groupClass.className"
                        :value="groupClass.classId"
                      />
                    </el-select>
                    <el-tag v-if="selectedGroupClass(row)" size="small" type="info">
                      {{ classTypeLabel(selectedGroupClass(row)?.billingType) }}
                    </el-tag>
                  </div>
                  <div v-if="expandedGroupLoading[selectedGroupClassKey(row)]" class="expand-loading">
                    Loading student progress...
                  </div>
                  <el-table
                    v-else
                    :data="getVisibleGroupStudents(selectedGroupClassId(row))"
                    row-key="enrollmentId"
                    size="small"
                    class="nested-table"
                    empty-text="No active students in this grade."
                  >
                    <el-table-column label="Student" min-width="140" prop="studentName" />
                    <el-table-column label="Purchased" min-width="100" align="center">
                      <template #default="{ row: student }">
                        {{ student.totalUnits }} {{ groupUnitLabel(student.billingType) }}
                      </template>
                    </el-table-column>
                    <el-table-column label="Used" min-width="90" align="center" prop="consumedUnits" />
                    <el-table-column label="Remaining" min-width="110" align="center">
                      <template #header>
                        <button
                          class="progress-sort-header progress-sort-header--center"
                          type="button"
                          @click="toggleGroupStudentSort(selectedGroupClassId(row), 'remainingUnits')"
                        >
                          <span>Remaining</span>
                        </button>
                      </template>
                      <template #default="{ row: student }">
                        {{ student.remainingUnits }}
                      </template>
                    </el-table-column>
                    <el-table-column label="Progress" min-width="160">
                      <template #header>
                        <button
                          class="progress-sort-header"
                          type="button"
                          @click="toggleGroupStudentSort(selectedGroupClassId(row), 'progress')"
                        >
                          <span class="progress-sort-header__label">
                            Progress
                            <span class="progress-sort-header__hint">Sort by bar</span>
                          </span>
                        </button>
                      </template>
                      <template #default="{ row: student }">
                        <el-progress :percentage="progressPercent(student)" :stroke-width="10" />
                      </template>
                    </el-table-column>
                    <el-table-column label="Period" min-width="170">
                      <template #header>
                        <el-popover
                          trigger="hover"
                          placement="bottom-start"
                          width="188"
                          popper-class="package-header-popover"
                        >
                          <template #reference>
                            <button class="header-popover-trigger" type="button">
                              <span>Period</span>
                            </button>
                          </template>
                          <div class="header-popover-menu">
                            <button type="button" @click="setGroupStudentSort(selectedGroupClassId(row), 'enrollmentDate', 'asc')">Enrollment date Asc</button>
                            <button type="button" @click="setGroupStudentSort(selectedGroupClassId(row), 'enrollmentDate', 'desc')">Enrollment date Desc</button>
                            <button type="button" @click="setGroupStudentSort(selectedGroupClassId(row), 'expiryDate', 'asc')">Expiry date Asc</button>
                            <button type="button" @click="setGroupStudentSort(selectedGroupClassId(row), 'expiryDate', 'desc')">Expiry date Desc</button>
                          </div>
                        </el-popover>
                      </template>
                      <template #default="{ row: student }">
                        {{ student.enrollmentDate ?? '—' }} ~ {{ student.expiryDate ?? '—' }}
                      </template>
                    </el-table-column>
                    <el-table-column label="Status" min-width="90" align="center">
                      <template #header>
                        <el-popover
                          trigger="hover"
                          placement="bottom"
                          width="150"
                          popper-class="package-header-popover"
                        >
                          <template #reference>
                            <button class="header-popover-trigger header-popover-trigger--center" type="button">
                              <span>Status</span>
                            </button>
                          </template>
                          <div class="header-popover-menu">
                            <button type="button" @click="setGroupStudentStatusFilter(selectedGroupClassId(row), 'ACTIVE')">Active only</button>
                            <button type="button" @click="setGroupStudentStatusFilter(selectedGroupClassId(row), 'ALL')">All statuses</button>
                            <button type="button" @click="setGroupStudentStatusFilter(selectedGroupClassId(row), 'INACTIVE')">Inactive only</button>
                          </div>
                        </el-popover>
                      </template>
                      <template #default="{ row: student }">
                        <el-tag size="small" :type="packageStatusType(student.enrollmentStatus)">
                          {{ packageStatusLabel(student.enrollmentStatus) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Class / Product" min-width="190">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong class="class-title">
                    {{ row.productName }}
                    <span v-if="selectedGroupClass(row)" :class="['class-type-badge', classTypeClass(selectedGroupClass(row)?.billingType)]">
                      <span class="class-type-badge__icon">{{ classTypeInitial(selectedGroupClass(row)?.billingType) }}</span>
                      {{ classTypeLabel(selectedGroupClass(row)?.billingType) }}
                    </span>
                  </strong>
                  <span>{{ row.classes.length }} grade{{ row.classes.length === 1 ? '' : 's' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Grade" min-width="140">
              <template #default="{ row }">
                <el-select
                  :model-value="selectedGroupClassId(row)"
                  size="small"
                  placeholder="Select grade"
                  @click.stop
                  @change="(value) => selectGroupGrade(row, value)"
                >
                  <el-option
                    v-for="groupClass in row.classes"
                    :key="groupClass.classId"
                    :label="groupClass.className"
                    :value="groupClass.classId"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="Sessions" min-width="100" align="center">
              <template #default="{ row }">
                <span v-if="selectedGroupClass(row)">
                  {{ selectedGroupClass(row)?.remainingUnits }} / {{ selectedGroupClass(row)?.totalUnits }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="Progress" min-width="150">
              <template #default="{ row }">
                <el-progress
                  v-if="selectedGroupClass(row)"
                  :percentage="progressPercent(selectedGroupClass(row)!)"
                  :stroke-width="10"
                />
              </template>
            </el-table-column>
            <el-table-column label="Teacher" min-width="130">
              <template #default="{ row }">{{ selectedGroupClass(row)?.teacherName || '—' }}</template>
            </el-table-column>
            <el-table-column label="Students" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="selectedGroupClass(row)" size="small" type="success">
                  {{ selectedGroupClass(row)?.activeStudentCount }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Status" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="selectedGroupClass(row)" size="small" :type="packageStatusType(selectedGroupClass(row)!.status)">
                  {{ packageStatusLabel(selectedGroupClass(row)!.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-footer">
            <span>Showing {{ groupPagination.start }}–{{ groupPagination.end }} of {{ groupPagination.total }}</span>
            <el-pagination
              v-model:current-page="groupPagination.page"
              v-model:page-size="groupPagination.size"
              background
              layout="prev, pager, next, sizes"
              :page-sizes="[10, 20, 50]"
              :total="groupPagination.total"
              @current-change="loadGroupData"
              @size-change="loadGroupData"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- Package detail dialog with tabs -->
    <el-dialog
      v-model="detailDialogVisible"
      width="900px"
      :close-on-click-modal="false"
      class="package-dialog"
    >
      <template #header>
        <h3>Package Detail</h3>
      </template>

      <el-tabs v-if="detailData" v-model="detailTab" class="detail-tabs">
        <!-- Tab 1: Basic Info -->
        <el-tab-pane label="Overview" name="overview">
          <div class="package-detail">
            <div class="detail-section">
              <h4 class="detail-section__title">Basic Info</h4>
              <div class="detail-grid">
                <div class="detail-field">
                  <label>Package</label>
                  <span>{{ detailData.packageName }}</span>
                </div>
                <div class="detail-field">
                  <label>Status</label>
                  <el-tag size="small" :type="packageStatusType(detailData.packageStatus)">
                    {{ packageStatusLabel(detailData.packageStatus) }}
                  </el-tag>
                </div>
                <div class="detail-field">
                  <label>Student</label>
                  <span>{{ detailData.studentName }}</span>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <div class="detail-section__heading">
                <h4 class="detail-section__title">Hours Summary</h4>
                <el-button size="small" plain @click="openHoursAdjustment">Adjust hours</el-button>
              </div>
              <div class="detail-grid">
                <div class="detail-field">
                  <label>Purchased</label>
                  <span>{{ formatHours(detailData.purchasedHours ?? detailData.totalUnits) }}</span>
                </div>
                <div class="detail-field">
                  <label>Added / Bonus</label>
                  <span>{{ formatHours(additionalPackageHours) }}</span>
                </div>
                <div class="detail-field">
                  <label>Package Total</label>
                  <span>{{ formatHours(detailData.totalUnits) }}</span>
                </div>
                <div class="detail-field">
                  <label>Consumed</label>
                  <span>{{ formatHours(detailData.consumedUnits) }}</span>
                </div>
                <div class="detail-field">
                  <label>Available</label>
                  <strong class="remaining-badge">{{ formatHours(detailData.remainingUnits) }}</strong>
                </div>
                <div class="detail-field">
                  <label>Received</label>
                  <span>${{ (detailData.receivedAmount ?? 0).toFixed(2) }}</span>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <div class="detail-section__heading">
                <div>
                  <h4 class="detail-section__title">Weekly Schedule</h4>
                  <p class="detail-section__hint">
                    Teachers, lesson names, and allocated hours are set for each recurring time slot.
                  </p>
                </div>
                <el-button size="small" plain @click="openPackageSchedule">
                  Open schedule calendar
                </el-button>
              </div>
              <div v-loading="scheduleLoading" class="package-schedule">
                <template v-if="packageScheduleRule">
                  <WeeklyTimeSelector
                    :model-value="[]"
                    :occupied-slots="packageScheduleCalendarSlots"
                    :week-start-date="scheduleWeekStartDate"
                    readonly
                    hide-hint
                  />
                  <el-table :data="packageScheduleRule.slots" size="small" class="schedule-slot-table">
                    <el-table-column label="Time" min-width="150">
                      <template #default="{ row }">
                        {{ weekdayLabel(row.weekday) }} · {{ row.startTime }}–{{ row.endTime }}
                      </template>
                    </el-table-column>
                    <el-table-column label="Teacher" min-width="150">
                      <template #default="{ row }">{{ scheduleTeacherName(row.teacherId) }}</template>
                    </el-table-column>
                    <el-table-column label="Lesson name" min-width="130">
                      <template #default="{ row }">{{ row.subjectName || '—' }}</template>
                    </el-table-column>
                    <el-table-column label="Each lesson" width="110" align="center">
                      <template #default="{ row }">{{ formatUnitsAsHours(row.plannedUnits) }}</template>
                    </el-table-column>
                    <el-table-column label="Allocated" width="110" align="center">
                      <template #default="{ row }">{{ formatUnitsAsHours(row.allocatedUnits) }}</template>
                    </el-table-column>
                  </el-table>
                </template>
                <div v-else class="empty-state">
                  No recurring schedule has been set for this package.
                  <el-button text type="primary" @click="openPackageSchedule">Set schedule</el-button>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <h4 class="detail-section__title">Dates</h4>
              <div class="detail-grid">
                <div class="detail-field">
                  <label>Start Date</label>
                  <span>{{ detailData.startDate ?? '—' }}</span>
                </div>
                <div class="detail-field">
                  <label>Est. End Date</label>
                  <span>{{ detailData.estimatedEndDate ?? '—' }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 2: Lesson History -->
        <el-tab-pane name="lessons">
          <template #label>
            Lesson History
            <el-tooltip content="Confirmed lesson records for this package" placement="top" raw-content>
              <span class="tab-info-icon">ⓘ</span>
            </el-tooltip>
          </template>

          <div v-loading="lessonHistoryLoading" class="lesson-history">
            <div v-if="lessonHistory.length === 0" class="empty-state">
              No confirmed lessons yet.
            </div>
            <el-table v-else :data="lessonHistory" row-key="id" size="small" class="lesson-history-table">
              <el-table-column label="Lesson No." min-width="130" prop="lessonNo" />
              <el-table-column label="Status" min-width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="resultStatusTag(row.resultStatus)">
                    {{ resultStatusLabel(row.resultStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Hours" min-width="70" align="center">
                <template #default="{ row }">{{ row.actualUnits }}</template>
              </el-table-column>
              <el-table-column label="Deducted" min-width="80" align="center">
                <template #default="{ row }">
                  <span :class="{ 'deducted-text': row.shouldDeduct }">{{ row.deductedUnits }}</span>
                </template>
              </el-table-column>
              <el-table-column label="Teacher" min-width="110" prop="actualTeacherName" />
              <el-table-column label="Confirmed" min-width="150" prop="confirmedAt" />
              <el-table-column label="" min-width="60" fixed="right">
                <template #default="{ row }">
                  <el-button class="row-action-button" text type="primary" size="small" @click="openLessonDetailDialog(row)">Detail</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailDialogVisible = false">Close</el-button>
          <el-button v-if="detailData?.packageStatus === 'ACTIVE'" :loading="packageStatusSaving" @click="handlePausePackage">
            Pause Package
          </el-button>
          <el-button v-if="detailData?.packageStatus === 'PAUSED'" type="primary" @click="openResumePackage">
            Resume Package
          </el-button>
          <el-button v-if="detailData?.packageStatus === 'ACTIVE'" type="danger" @click="handleClosePackage">
            Close Package
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="hoursAdjustmentVisible"
      title="Adjust Available Hours"
      width="480px"
      :close-on-click-modal="false"
    >
      <div v-if="detailData" class="adjustment-current">
        <span>Purchased {{ detailData.purchasedHours ?? detailData.totalUnits }}h</span>
        <strong>Available {{ detailData.totalUnits }}h</strong>
      </div>
      <el-form label-width="130px">
        <el-form-item label="Adjustment type">
          <el-select v-model="hoursAdjustmentForm.adjustmentType" style="width: 100%">
            <el-option label="Bonus hours" value="BONUS_HOURS" />
            <el-option label="Manual correction" value="MANUAL_CORRECTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="Hour change">
          <el-input-number
            v-model="hoursAdjustmentForm.changeHours"
            :step="0.5"
            :precision="1"
            :min="-10000"
            :max="10000"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="Reason">
          <el-input
            v-model="hoursAdjustmentForm.reason"
            type="textarea"
            :rows="2"
            placeholder="Optional note"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hoursAdjustmentVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="hoursAdjustmentSaving" @click="submitHoursAdjustment">
          Save adjustment
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resumePackageVisible"
      title="Resume Package"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-width="120px">
        <el-form-item label="Starting date">
          <el-date-picker
            v-model="resumePackageForm.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resumePackageVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="packageStatusSaving" @click="submitResumePackage">
          Resume
        </el-button>
      </template>
    </el-dialog>

    <!-- Lesson record detail dialog -->
    <el-dialog
      v-model="lessonDetailDialogVisible"
      title="Lesson Record Detail"
      width="580px"
      :close-on-click-modal="false"
      class="lesson-dialog"
    >
      <div v-if="lessonDetailData" class="lesson-detail">
        <div class="detail-section">
          <h4 class="detail-section__title">Basic Info</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Lesson No.</label>
              <span>{{ lessonDetailData.lessonNo }}</span>
            </div>
            <div class="detail-field">
              <label>Status</label>
              <el-tag size="small" :type="resultStatusTag(lessonDetailData.resultStatus)">
                {{ resultStatusLabel(lessonDetailData.resultStatus) }}
              </el-tag>
            </div>
            <div class="detail-field">
              <label>Student</label>
              <span>{{ lessonDetailData.studentName }}</span>
            </div>
            <div class="detail-field">
              <label>Package</label>
              <span>{{ lessonDetailData.packageName || '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Planned Teacher</label>
              <span>{{ lessonDetailData.plannedTeacherName ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Actual Teacher</label>
              <span>{{ lessonDetailData.actualTeacherName ?? '—' }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="detail-section__title">Time & Units</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Actual Start</label>
              <span>{{ lessonDetailData.actualStartAt ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Actual End</label>
              <span>{{ lessonDetailData.actualEndAt ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Actual Units</label>
              <span>{{ lessonDetailData.actualUnits }}</span>
            </div>
            <div class="detail-field">
              <label>Deducted Units</label>
              <strong :class="{ 'deducted-text': lessonDetailData.shouldDeduct }">
                {{ lessonDetailData.deductedUnits }}
              </strong>
            </div>
            <div class="detail-field">
              <label>Should Deduct</label>
              <el-tag size="small" :type="lessonDetailData.shouldDeduct ? 'success' : 'info'">
                {{ lessonDetailData.shouldDeduct ? 'Yes' : 'No' }}
              </el-tag>
            </div>
            <div class="detail-field">
              <label>Extend End Date</label>
              <el-tag size="small" :type="lessonDetailData.shouldExtendEndDate ? 'warning' : 'info'">
                {{ lessonDetailData.shouldExtendEndDate ? 'Yes' : 'No' }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="detail-section__title">Confirmation</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Confirmed At</label>
              <span>{{ lessonDetailData.confirmedAt ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Source</label>
              <el-tag size="small" type="info">{{ lessonDetailData.recordSource }}</el-tag>
            </div>
          </div>
        </div>

        <div v-if="lessonDetailData.notes" class="detail-section">
          <h4 class="detail-section__title">Notes</h4>
          <p class="detail-notes">{{ lessonDetailData.notes }}</p>
        </div>
      </div>

      <template #footer>
        <el-button @click="lessonDetailDialogVisible = false">Close</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  adjustCoursePackageHours,
  closeCoursePackage,
  fetchCoursePackage,
  fetchCoursePackages,
  fetchGroupClassPackageProgress,
  fetchGroupClassStudentProgress,
  pauseCoursePackage,
  resumeCoursePackage,
  type CoursePackageQuery,
} from '@/api/enrollment';
import {
  fetchLessonRecord,
  fetchLessonRecords,
  type LessonRecordQuery,
} from '@/api/lesson';
import { fetchAllStudents, fetchAllTeachers } from '@/api/base-data';
import { fetchRecurringScheduleRule, fetchRecurringScheduleRules } from '@/api/schedule';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef } from '@/components/WeeklyTimeSelector.vue';
import type {
  CoursePackageItem,
  EntityId,
  GroupClassPackageProgressItem,
  GroupClassStudentProgressItem,
  LessonRecordDetailItem,
  LessonRecordItem,
  RecurringScheduleRuleDetailItem,
  TeacherItem,
} from '@/types/domain';

const router = useRouter();
const loading = ref(false);
const groupLoading = ref(false);
const activeTab = ref<'ONE_ON_ONE' | 'GROUP'>('ONE_ON_ONE');
const detailDialogVisible = ref(false);
const detailTab = ref('overview');
const detailData = ref<CoursePackageItem | null>(null);

const students = ref<{ id: EntityId; studentName: string }[]>([]);
const teachers = ref<TeacherItem[]>([]);
const scheduleLoading = ref(false);
const packageScheduleRule = ref<RecurringScheduleRuleDetailItem | null>(null);
const hoursAdjustmentVisible = ref(false);
const hoursAdjustmentSaving = ref(false);
const hoursAdjustmentForm = reactive({
  adjustmentType: 'BONUS_HOURS' as 'BONUS_HOURS' | 'MANUAL_CORRECTION',
  changeHours: 0.5,
  reason: '',
});
const resumePackageVisible = ref(false);
const packageStatusSaving = ref(false);
const resumePackageForm = reactive({
  startDate: '',
});
const scheduleWeekStartDate = computed(() => {
  const date = new Date();
  const offset = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - offset);
  return date.toISOString().slice(0, 10);
});
const packageScheduleCalendarSlots = computed<OccupiedSlotDef[]>(() =>
  (packageScheduleRule.value?.slots ?? []).map(slot => ({
    weekday: slot.weekday,
    startTime: slot.startTime,
    endTime: slot.endTime,
    courseLabel: slot.subjectName || 'Scheduled lesson',
    subjectName: slot.subjectName,
    teacherName: scheduleTeacherName(slot.teacherId),
    contextLabel: `${scheduleTeacherName(slot.teacherId)} · ${formatUnitsAsHours(slot.allocatedUnits)} allocated`,
    isCurrent: true,
  })),
);
const additionalPackageHours = computed(() => {
  if (!detailData.value) return 0;
  return Math.max(0, Number(detailData.value.totalUnits) - Number(detailData.value.purchasedHours ?? detailData.value.totalUnits));
});

// Lesson History
const lessonHistoryLoading = ref(false);
const lessonHistory = ref<LessonRecordItem[]>([]);

// Lesson detail dialog
const lessonDetailDialogVisible = ref(false);
const lessonDetailData = ref<LessonRecordDetailItem | null>(null);

// ─── Filters & Pagination ───────────────────────────
const filters = reactive({
  keyword: '',
  studentId: undefined as number | undefined,
  packageStatus: '',
});

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
  get start() {
    return this.total === 0 ? 0 : (this.page - 1) * this.size + 1;
  },
  get end() {
    return Math.min(this.page * this.size, this.total);
  },
});

const groupPagination = reactive({
  page: 1,
  size: 10,
  total: 0,
  get start() {
    return this.total === 0 ? 0 : (this.page - 1) * this.size + 1;
  },
  get end() {
    return Math.min(this.page * this.size, this.total);
  },
});

interface OneOnOnePackageRow extends CoursePackageItem {
  scheduleStatus: 'Scheduled' | 'Needs setup' | 'Incomplete' | 'Paused';
  nextLessonStartAt?: string;
}

type GroupStudentSortField = 'remainingUnits' | 'progress' | 'enrollmentDate' | 'expiryDate' | 'enrollmentStatus';
type PackageSortOrder = 'asc' | 'desc';
type PackageProgressFilter = 'ACTIVE' | 'ALL' | 'INACTIVE';

interface GroupStudentSortState {
  field: GroupStudentSortField;
  order: PackageSortOrder;
}

interface GroupProductRow {
  productKey: string;
  productName: string;
  classes: GroupClassPackageProgressItem[];
  selectedClassId: EntityId;
}

const tableData = ref<OneOnOnePackageRow[]>([]);
const groupTableData = ref<GroupClassPackageProgressItem[]>([]);
const groupProductRows = computed<GroupProductRow[]>(() => {
  const grouped = new Map<string, GroupProductRow>();
  for (const groupClass of groupTableData.value) {
    const key = String(groupClass.productId ?? `class-${groupClass.classId}`);
    const productName = groupClass.productName || groupClass.className;
    const existing = grouped.get(key);
    if (existing) {
      existing.classes.push(groupClass);
      continue;
    }
    grouped.set(key, {
      productKey: key,
      productName,
      classes: [groupClass],
      selectedClassId: groupClass.classId,
    });
  }
  return Array.from(grouped.values());
});
const expandedGroupLoading = reactive<Record<string, boolean>>({});
const expandedGroupStudents = reactive<Record<string, GroupClassStudentProgressItem[]>>({});
const groupStudentSortStates = reactive<Record<string, GroupStudentSortState>>({});
const groupStudentStatusFilters = reactive<Record<string, 'ACTIVE' | 'ALL' | 'INACTIVE'>>({});

function handleFilterChange() {
  pagination.page = 1;
  groupPagination.page = 1;
  if (activeTab.value === 'GROUP') {
    loadGroupData();
  } else {
    loadData();
  }
}

function progressPercent(row: { totalUnits: number; consumedUnits: number }): number {
  return row.totalUnits > 0 ? Math.round((row.consumedUnits / row.totalUnits) * 100) : 0;
}

function compareDateValue(left?: string, right?: string, order: PackageSortOrder = 'asc'): number {
  const leftTime = parseDateValue(left);
  const rightTime = parseDateValue(right);
  const leftMissing = leftTime == null;
  const rightMissing = rightTime == null;

  // Keep empty dates at the bottom for both directions.
  if (leftMissing && rightMissing) return 0;
  if (leftMissing) return 1;
  if (rightMissing) return -1;
  return order === 'asc' ? leftTime - rightTime : rightTime - leftTime;
}

function getGroupStudentSortState(classId: EntityId): GroupStudentSortState {
  return groupStudentSortStates[String(classId)] ?? { field: 'remainingUnits', order: 'desc' };
}

function toggleGroupStudentSort(classId: EntityId, field: GroupStudentSortField) {
  const key = String(classId);
  const current = getGroupStudentSortState(classId);
  groupStudentSortStates[key] = {
    field,
    order: current.field === field && current.order === 'asc' ? 'desc' : 'asc',
  };
}

function setGroupStudentSort(classId: EntityId, field: GroupStudentSortField, order: PackageSortOrder) {
  groupStudentSortStates[String(classId)] = { field, order };
}

function getGroupStudentStatusFilter(classId: EntityId): PackageProgressFilter {
  return groupStudentStatusFilters[String(classId)] ?? 'ACTIVE';
}

function setGroupStudentStatusFilter(classId: EntityId, value: PackageProgressFilter) {
  groupStudentStatusFilters[String(classId)] = value;
}

function getVisibleGroupStudents(classId: EntityId): GroupClassStudentProgressItem[] {
  const filter = getGroupStudentStatusFilter(classId);
  const studentsInClass = (expandedGroupStudents[String(classId)] ?? []).filter((student) => {
    if (filter === 'ALL') return true;
    if (filter === 'INACTIVE') return student.enrollmentStatus !== 'ACTIVE';
    return student.enrollmentStatus === 'ACTIVE';
  });
  return sortGroupStudents(studentsInClass, getGroupStudentSortState(classId));
}

function selectedGroupClass(row: GroupProductRow): GroupClassPackageProgressItem | undefined {
  return row.classes.find((groupClass) => String(groupClass.classId) === String(row.selectedClassId))
    ?? row.classes[0];
}

function selectedGroupClassId(row: GroupProductRow): EntityId {
  return selectedGroupClass(row)?.classId ?? row.selectedClassId;
}

function selectedGroupClassKey(row: GroupProductRow): string {
  return String(selectedGroupClassId(row));
}

function selectGroupGrade(row: GroupProductRow, classId: EntityId) {
  row.selectedClassId = classId;
  if (!expandedGroupStudents[String(classId)]) {
    loadGroupStudents(classId);
  }
}

function sortGroupStudents(
  studentsInClass: GroupClassStudentProgressItem[],
  state: GroupStudentSortState,
): GroupClassStudentProgressItem[] {
  return [...studentsInClass].sort((a, b) => {
    let result = 0;
    if (state.field === 'remainingUnits') {
      result = a.remainingUnits - b.remainingUnits;
    } else if (state.field === 'progress') {
      result = progressPercent(a) - progressPercent(b);
    } else if (state.field === 'enrollmentDate' || state.field === 'expiryDate') {
      result = compareDateValue(a[state.field], b[state.field], 'asc');
    } else {
      result = a.enrollmentStatus.localeCompare(b.enrollmentStatus);
    }

    const ordered = state.order === 'asc' ? result : -result;
    return ordered || a.studentName.localeCompare(b.studentName) || String(a.enrollmentId).localeCompare(String(b.enrollmentId));
  });
}

function parseDateValue(value?: string): number | undefined {
  if (!value) return undefined;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? undefined : time;
}

function groupUnitLabel(billingType?: string): string {
  return billingType === 'MONTHLY' ? 'sessions' : 'sessions';
}

function normalizeLabel(value?: string | null): string {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLowerCase();
}

function shouldShowProductName(row: CoursePackageItem): boolean {
  return Boolean(row.courseProductName)
    && normalizeLabel(row.courseProductName) !== normalizeLabel(row.packageName);
}

function classTypeInitial(billingType?: string): string {
  return billingType === 'MONTHLY' ? 'M' : 'S';
}

function classTypeLabel(billingType?: string): string {
  return billingType === 'MONTHLY' ? 'Monthly' : 'Per session';
}

function classTypeClass(billingType?: string): string {
  return billingType === 'MONTHLY' ? 'class-type-badge--monthly' : 'class-type-badge--session';
}

function packageStatusType(status: string) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'PAUSED') return 'warning';
  if (status === 'CLOSED') return 'info';
  return 'warning';
}

function packageStatusLabel(status: string) {
  const map: Record<string, string> = {
    ACTIVE: 'Active', PAUSED: 'Paused', CLOSED: 'Closed', PENDING: 'Pending', SUSPENDED: 'Suspended', INACTIVE: 'Inactive', MIXED: 'Mixed',
  };
  return map[status] ?? status;
}

// ─── Load Data ──────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const packages = await loadAllOneOnOnePackages();
    pagination.total = packages.length;
    const pagePackages = packages.slice((pagination.page - 1) * pagination.size, pagination.page * pagination.size);
    tableData.value = await enrichOneOnOnePackages(pagePackages);
  } catch {
    ElMessage.error('Failed to load packages.');
  } finally {
    loading.value = false;
  }
}

async function loadAllOneOnOnePackages(): Promise<CoursePackageItem[]> {
  const pageSize = 200;
  let pageNum = 1;
  let total = 0;
  const records: CoursePackageItem[] = [];

  do {
    const params: CoursePackageQuery = {
      keyword: filters.keyword || undefined,
      studentId: filters.studentId,
      packageStatus: filters.packageStatus || undefined,
      teachingMode: 'ONE_ON_ONE',
      pageNum,
      pageSize,
    };
    const res = await fetchCoursePackages(params);
    const data = res.data.data;
    records.push(...data.records);
    total = data.total;
    pageNum += 1;
  } while (records.length < total);

  return records;
}

async function enrichOneOnOnePackages(packages: CoursePackageItem[]): Promise<OneOnOnePackageRow[]> {
  const rows = await Promise.all(packages.map(async (pkg) => {
    try {
      const rulesResponse = await fetchRecurringScheduleRules({
        packageId: pkg.id,
        pageNum: 1,
        pageSize: 1,
      });
      const rules = rulesResponse.data.data.records;
      const ruleSummary = rules.find(item => item.ruleStatus === 'ACTIVE' || item.ruleStatus === 'PAUSED')
        ?? rules[0];
      if (!ruleSummary) {
        return {
          ...pkg,
          scheduleStatus: 'Needs setup' as const,
        };
      }

      const detailResponse = await fetchRecurringScheduleRule(ruleSummary.id);
      const rule = detailResponse.data.data;
      const isPaused = pkg.packageStatus === 'PAUSED' || rule.ruleStatus === 'PAUSED';
      const isIncomplete = rule.slots.some(slot =>
        !slot.teacherId
        || !slot.subjectName?.trim()
        || !slot.allocatedUnits
        || slot.allocatedUnits <= 0,
      );

      return {
        ...pkg,
        scheduleStatus: isPaused
          ? 'Paused' as const
          : isIncomplete
            ? 'Incomplete' as const
            : 'Scheduled' as const,
        nextLessonStartAt: ruleSummary.nextLessonStartAt,
      };
    } catch {
      return {
        ...pkg,
        scheduleStatus: 'Needs setup' as const,
      };
    }
  }));
  return rows;
}

function scheduleStatusType(status: OneOnOnePackageRow['scheduleStatus']) {
  if (status === 'Scheduled') return 'success';
  if (status === 'Paused') return 'warning';
  if (status === 'Incomplete') return 'danger';
  return 'info';
}

function formatScheduleDate(value?: string): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date);
}

async function loadGroupData() {
  groupLoading.value = true;
  try {
    const params: CoursePackageQuery = {
      keyword: filters.keyword || undefined,
      studentId: filters.studentId,
      packageStatus: filters.packageStatus || undefined,
      pageNum: groupPagination.page,
      pageSize: groupPagination.size,
    };
    const res = await fetchGroupClassPackageProgress(params);
    const data = res.data.data;
    groupTableData.value = data.records;
    groupPagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load group class packages.');
  } finally {
    groupLoading.value = false;
  }
}

async function loadGroupStudents(classId: EntityId) {
  const key = String(classId);
  expandedGroupLoading[key] = true;
  try {
    const res = await fetchGroupClassStudentProgress(classId);
    expandedGroupStudents[key] = res.data.data;
  } catch {
    ElMessage.error('Failed to load student progress.');
  } finally {
    expandedGroupLoading[key] = false;
  }
}

function handleGroupExpandChange(row: GroupProductRow, expandedRows: GroupProductRow[]) {
  const isExpanded = expandedRows.some((item) => item.productKey === row.productKey);
  const classId = selectedGroupClassId(row);
  const key = String(classId);
  if (isExpanded && !expandedGroupStudents[key]) {
    loadGroupStudents(classId);
  }
}

async function loadStudents() {
  try {
    const res = await fetchAllStudents();
    students.value = res.data.data.map((s) => ({ id: s.id, studentName: s.studentName }));
  } catch {
    // Silent fail
  }
}

async function loadTeachers() {
  try {
    const res = await fetchAllTeachers();
    teachers.value = res.data.data;
  } catch {
    // Silent fail
  }
}

// ─── Package Detail ──────────────────────────────────
async function openDetailDialog(row: CoursePackageItem) {
  try {
    const res = await fetchCoursePackage(row.id);
    detailData.value = res.data.data;
    detailTab.value = 'overview';
    lessonHistory.value = [];
    packageScheduleRule.value = null;
    detailDialogVisible.value = true;
    loadPackageSchedule(row.id);
  } catch {
    ElMessage.error('Failed to load package detail.');
  }
}

async function reloadPackageDetail() {
  if (!detailData.value) return;
  const res = await fetchCoursePackage(detailData.value.id);
  detailData.value = res.data.data;
  await loadPackageSchedule(detailData.value.id);
}

async function loadPackageSchedule(packageId: EntityId) {
  scheduleLoading.value = true;
  try {
    const result = await fetchRecurringScheduleRules({ packageId, pageNum: 1, pageSize: 10 });
    const rules = result.data.data.records;
    const rule = rules.find(item => item.ruleStatus === 'ACTIVE' || item.ruleStatus === 'PAUSED') ?? rules[0];
    packageScheduleRule.value = rule
      ? (await fetchRecurringScheduleRule(rule.id)).data.data
      : null;
  } catch {
    packageScheduleRule.value = null;
    ElMessage.error('Failed to load the package schedule.');
  } finally {
    scheduleLoading.value = false;
  }
}

function openPackageSchedule() {
  if (!detailData.value) return;
  detailDialogVisible.value = false;
  router.push({
    name: 'recurring-schedule',
    query: {
      studentId: String(detailData.value.studentId),
      packageId: String(detailData.value.id),
    },
  });
}

function scheduleTeacherName(teacherId?: EntityId): string {
  if (teacherId == null) return '—';
  return teachers.value.find(teacher => String(teacher.id) === String(teacherId))?.teacherName ?? 'Unknown teacher';
}

function weekdayLabel(weekday: number): string {
  return ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][weekday - 1] ?? '—';
}

function formatUnitsAsHours(units?: number): string {
  return units == null ? '—' : `${(units * 0.5).toFixed(1)}h`;
}

function formatHours(hours?: number): string {
  return hours == null ? '—' : `${Number(hours).toFixed(1)}h`;
}

function openHoursAdjustment() {
  hoursAdjustmentForm.adjustmentType = 'BONUS_HOURS';
  hoursAdjustmentForm.changeHours = 0.5;
  hoursAdjustmentForm.reason = '';
  hoursAdjustmentVisible.value = true;
}

async function submitHoursAdjustment() {
  if (!detailData.value) return;
  if (!hoursAdjustmentForm.changeHours) {
    ElMessage.warning('Hour change cannot be zero.');
    return;
  }
  hoursAdjustmentSaving.value = true;
  try {
    await adjustCoursePackageHours(detailData.value.id, {
      changeHours: hoursAdjustmentForm.changeHours,
      adjustmentType: hoursAdjustmentForm.adjustmentType,
      reason: hoursAdjustmentForm.reason.trim() || undefined,
    });
    await reloadPackageDetail();
    await loadData();
    hoursAdjustmentVisible.value = false;
    ElMessage.success('Available hours updated.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to adjust available hours.');
  } finally {
    hoursAdjustmentSaving.value = false;
  }
}

async function handleClosePackage() {
  if (!detailData.value) return;
  try {
    await closeCoursePackage(detailData.value.id);
    ElMessage.success('Package closed.');
    detailDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  }
}

async function handlePausePackage() {
  if (!detailData.value) return;
  packageStatusSaving.value = true;
  try {
    await pauseCoursePackage(detailData.value.id);
    await reloadPackageDetail();
    await loadData();
    ElMessage.success('Package paused.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to pause package.');
  } finally {
    packageStatusSaving.value = false;
  }
}

function openResumePackage() {
  resumePackageForm.startDate = new Date().toISOString().slice(0, 10);
  resumePackageVisible.value = true;
}

async function submitResumePackage() {
  if (!detailData.value) return;
  if (!resumePackageForm.startDate) {
    ElMessage.warning('Select a starting date.');
    return;
  }
  packageStatusSaving.value = true;
  try {
    await resumeCoursePackage(detailData.value.id, { startDate: resumePackageForm.startDate });
    resumePackageVisible.value = false;
    await reloadPackageDetail();
    await loadData();
    ElMessage.success('Package resumed.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to resume package.');
  } finally {
    packageStatusSaving.value = false;
  }
}

// ─── Lesson History Tab ────────────────────────────────
async function loadLessonHistory() {
  if (!detailData.value) return;
  lessonHistoryLoading.value = true;
  try {
    const params: LessonRecordQuery = {
      packageId: detailData.value.id,
      pageNum: 1,
      pageSize: 100,
    };
    const res = await fetchLessonRecords(params);
    lessonHistory.value = res.data.data.records;
  } catch {
    // silent fail
  } finally {
    lessonHistoryLoading.value = false;
  }
}

watch(detailTab, (tab) => {
  if (tab === 'lessons' && lessonHistory.value.length === 0) {
    loadLessonHistory();
  }
});

watch(activeTab, (tab) => {
  if (tab === 'GROUP' && groupTableData.value.length === 0) {
    loadGroupData();
  }
});

// ─── Lesson Detail Dialog ─────────────────────────────
async function openLessonDetailDialog(row: LessonRecordItem) {
  try {
    const res = await fetchLessonRecord(row.id);
    lessonDetailData.value = res.data.data;
    lessonDetailDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load lesson detail.');
  }
}

// ─── Helpers ───────────────────────────────────────
function resultStatusTag(status: string) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FORMAL_LEAVE') return 'info';
  if (status === 'NO_SHOW') return 'danger';
  if (status === 'TEACHER_CANCEL') return 'warning';
  return '';
}

function resultStatusLabel(status: string) {
  const map: Record<string, string> = {
    COMPLETED: 'Completed', FORMAL_LEAVE: 'Formal Leave', NO_SHOW: 'No Show',
    TEACHER_CANCEL: 'Teacher Cancel', TEMP_CANCEL: 'Cancelled',
    MAKEUP_COMPLETED: 'Makeup', SUBSTITUTED: 'Substituted',
  };
  return map[status] ?? status;
}

// ─── Init ───────────────────────────────────────────
loadData();
loadStudents();
loadTeachers();
</script>

<style scoped>
.course-product-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.filters { margin-bottom: 12px; }
.filters__item { margin-right: 16px; margin-bottom: 0; }
.filters__item :deep(.el-form-item__label) { color: #606266; font-size: 13px; }

.package-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.nested-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.nested-table :deep(.el-table__header th) {
  background: #f8fafc;
}

.nested-package-card {
  margin: 8px 12px 12px 46px;
  padding: 14px;
  border: 1px solid #e8edf7;
  border-radius: 10px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  box-shadow: 0 8px 22px rgba(31, 45, 61, 0.04);
}

.nested-package-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
}

.nested-package-card__header strong {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.group-grade-select {
  min-width: 180px;
}

.product-table :deep(.el-table__expanded-cell) {
  background: #f8fbff;
}

.nested-column-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 30px;
  width: 100%;
}

.progress-sort-header,
.header-popover-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  padding: 0 6px;
  transition: border-color .16s ease, color .16s ease, background .16s ease, box-shadow .16s ease;
}

.progress-sort-header:hover,
.header-popover-trigger:hover {
  background: #eff6ff;
  color: #2563eb;
}

.progress-sort-header:focus-visible,
.header-popover-trigger:focus-visible {
  outline: 2px solid rgba(37, 99, 235, 0.22);
  outline-offset: 2px;
}

.progress-sort-header__label {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.progress-sort-header--center {
  justify-content: center;
}

.progress-sort-header__hint {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 500;
}

.header-popover-trigger--center {
  justify-content: center;
}

.header-popover-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-popover-menu button {
  width: 100%;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  padding: 8px 10px;
  text-align: left;
  transition: background .16s ease, color .16s ease;
}

.header-popover-menu button:hover {
  background: #eff6ff;
  color: #2563eb;
}

:global(.package-header-popover) {
  padding: 8px !important;
  border: 1px solid #dce5f2 !important;
  border-radius: 10px !important;
  box-shadow: 0 12px 28px rgba(31, 45, 61, 0.12) !important;
}

.row-action-button {
  height: 26px;
  border-radius: 999px;
  font-weight: 500;
  padding: 0 10px;
}

.row-action-button:hover {
  background: #eff6ff;
}

.expand-loading {
  padding: 16px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.cell-stack { display: flex; flex-direction: column; gap: 2px; }
.cell-stack strong { color: #303133; font-size: 13px; font-weight: 500; }
.cell-stack span { color: #909399; font-size: 12px; }
.package-name-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}
.package-name-cell__title {
  overflow: hidden;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.package-name-cell__context {
  overflow: hidden;
  color: #8a94a3;
  font-size: 11px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.class-title { display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.class-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px 1px 3px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  line-height: 16px;
}
.class-type-badge__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  color: #fff !important;
  font-size: 10px !important;
  font-weight: 600;
}
.class-type-badge--session {
  color: #409eff;
  background: #ecf5ff;
}
.class-type-badge--session .class-type-badge__icon { background: #409eff; }
.class-type-badge--monthly {
  color: #67c23a;
  background: #f0f9eb;
}
.class-type-badge--monthly .class-type-badge__icon { background: #67c23a; }

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

/* Dialog */
.package-dialog :deep(.el-dialog__body) { padding: 0; }
.package-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}
.package-dialog :deep(.el-dialog__header h3) {
  margin: 0; font-size: 16px; font-weight: 600; color: #303133;
}
.package-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.detail-tabs :deep(.el-tabs__header) { margin: 0 0 -1px 0; padding: 0 16px; }
.detail-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.detail-tabs :deep(.el-tabs__item) { font-size: 14px; }
.detail-tabs :deep(.el-tab-pane) { padding: 16px 20px; }

/* Tabs info icon */
.tab-info-icon {
  display: inline-block;
  width: 14px;
  height: 14px;
  line-height: 14px;
  text-align: center;
  font-size: 11px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 50%;
  cursor: help;
  margin-left: 3px;
}

/* Detail layout */
.package-detail { display: flex; flex-direction: column; gap: 16px; }
.detail-section { display: flex; flex-direction: column; gap: 8px; }
.detail-section__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 6px;
}
.detail-section__title {
  margin: 0; font-size: 13px; font-weight: 600; color: #303133;
  padding-bottom: 6px; border-bottom: 1px solid #f0f0f0;
}
.detail-section__heading .detail-section__title {
  border-bottom: 0;
  padding-bottom: 0;
}
.detail-section__hint {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
.detail-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 20px; }
.detail-field { display: flex; flex-direction: column; gap: 4px; }
.detail-field label { color: #909399; font-size: 12px; }
.detail-field span, .detail-field strong { color: #303133; font-size: 13px; }
.remaining-badge { color: #f56c6c; font-size: 14px; font-weight: 600; }
.package-schedule {
  min-height: 80px;
}
.package-schedule :deep(.weekly-time-selector) {
  min-width: 720px;
}
.schedule-slot-table {
  margin-top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}
.adjustment-current {
  display: flex;
  align-items: center;
  gap: 18px;
  color: #64748b;
  font-size: 12px;
}
.adjustment-current {
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 10px 12px;
  border: 1px solid #dbe7f5;
  border-radius: 6px;
  background: #f8fbff;
}
.adjustment-current strong {
  color: #303133;
}

/* Lesson History */
.lesson-history { min-height: 80px; }
.empty-state { text-align: center; color: #909399; font-size: 13px; padding: 24px 0; }
.lesson-history-table { border-radius: 6px; overflow: hidden; border: 1px solid #ebeef5; }
.deducted-text { color: #f56c6c; font-weight: 600; }

/* Lesson detail dialog */
.lesson-dialog :deep(.el-dialog__body) {
  max-height: 65vh; overflow-y: auto; padding: 16px 20px;
}
.lesson-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5; padding: 16px 20px;
}
.lesson-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5; padding: 12px 20px;
}
.lesson-detail { display: flex; flex-direction: column; gap: 16px; }
.detail-notes { margin: 0; color: #606266; font-size: 13px; line-height: 1.5; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
