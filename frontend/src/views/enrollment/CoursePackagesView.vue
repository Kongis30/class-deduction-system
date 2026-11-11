<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span v-if="!appStore.isMobileViewport" class="panel-header__title">Course Packages</span>
        <div v-if="appStore.isMobileViewport" class="panel-header__actions">
          <button
            type="button"
            class="mobile-search-trigger"
            :aria-expanded="mobileSearchExpanded"
            aria-label="Open package search and filters"
            @click="mobileSearchExpanded = !mobileSearchExpanded"
          >
            <el-icon><Search /></el-icon>
          </button>
        </div>
      </div>

      <section v-show="!appStore.isMobileViewport || mobileSearchExpanded" class="mobile-search-panel">
      <div v-if="appStore.isMobileViewport" class="mobile-search-panel__header">
        <span>Search packages</span>
        <button type="button" aria-label="Close search" @click="mobileSearchExpanded = false">
          <el-icon><Close /></el-icon>
        </button>
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
        <el-form-item class="filters__item">
          <el-button
            :type="filters.onlyRenewalDue ? 'danger' : 'default'"
            plain
            @click="toggleRenewalDueFilter"
          >
            Renewal due
          </el-button>
        </el-form-item>
      </el-form>
      </section>

      <div v-loading="loading" class="package-group-list">
        <section v-for="group in groupedTableData" :key="group.key" class="package-group">
          <header class="package-group__header">
            <button
              type="button"
              class="package-group__toggle"
              :aria-expanded="!isPackageGroupCollapsed(group.key)"
              @click="togglePackageGroup(group.key)"
            >
              <span class="package-group__chevron" :class="{ 'is-collapsed': isPackageGroupCollapsed(group.key) }" aria-hidden="true" />
              <span class="package-group__title">
                <strong>{{ group.courseName }}</strong>
                <span class="package-group__grade">{{ group.gradeBand }}</span>
                <el-tag v-if="group.pinned" size="small" type="warning" :title="appStore.isMobileViewport ? 'Packages prioritized for renewal review' : 'Pinned for renewal review'">
                  {{ appStore.isMobileViewport ? 'Priority' : 'Pinned' }}
                </el-tag>
              </span>
            </button>
            <small>
              {{ group.packages.length }} packages
              <span v-if="appStore.isMobileViewport && group.renewalCount" class="package-group__renewal-count">
                · {{ group.renewalCount }} due
              </span>
            </small>
          </header>
          <div v-if="!isPackageGroupCollapsed(group.key)" class="package-group__content">
            <el-table
              v-if="!appStore.isMobileViewport"
              :data="group.packages"
              row-key="id"
              class="product-table"
              :show-empty="false"
              :row-class-name="oneOnOneRowClassName"
            >
              <el-table-column
                label="Student"
                min-width="150"
                prop="studentName"
                sortable
                :filters="textColumnFilters(group.packages, row => row.studentName)"
                :filter-method="filterStudentName"
              />
              <el-table-column
                label="Product / Package"
                min-width="230"
                sortable
                :sort-method="comparePackageNames"
                :filters="packageNameFilters(group.packages)"
                :filter-method="filterPackageName"
              >
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
              <el-table-column
                label="Available"
                min-width="210"
                align="center"
                sortable
                :sort-method="compareAvailableHours"
                :filters="availableHourFilters"
                :filter-method="filterAvailableHours"
              >
                <template #default="{ row }">
                  <div class="available-cell">
                    <div class="available-cell__value">
                      <strong :class="{ 'low-balance-renewal-text': isLowBalancePinned(row) }">{{ formatHours(row.remainingUnits) }}</strong>
                      <span>/ {{ formatHours(row.totalUnits) }}</span>
                    </div>
                    <el-progress class="available-cell__progress" :percentage="progressPercent(row)" :stroke-width="8" :show-text="false" />
                    <el-tag v-if="isLowBalancePinned(row)" class="renewal-reminder-tag" size="small" type="danger">
                      Renewal needed
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="Schedule setup"
                min-width="150"
                align="center"
                :filters="textColumnFilters(group.packages, row => row.scheduleStatus)"
                :filter-method="filterScheduleStatus"
              >
                <template #default="{ row }">
                  <el-tag size="small" :type="scheduleStatusType(row.scheduleStatus)">
                    {{ row.scheduleStatus }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                label="Next lesson"
                min-width="180"
                sortable
                :sort-method="compareNextLessonDates"
              >
                <template #default="{ row }">
                  <span v-if="row.nextLessonStartAt">{{ formatScheduleDate(row.nextLessonStartAt) }}</span>
                  <span v-else class="muted-text">—</span>
                </template>
              </el-table-column>
              <el-table-column
                label="Package status"
                min-width="110"
                align="center"
                :filters="textColumnFilters(group.packages, row => packageStatusLabel(row.packageStatus))"
                :filter-method="filterPackageStatus"
              >
                <template #default="{ row }">
                  <el-tag size="small" :type="packageStatusType(row.packageStatus)">
                    {{ packageStatusLabel(row.packageStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                label="Actions"
                width="104"
                fixed="right"
                align="center"
                label-class-name="package-actions-column-header"
              >
                <template #default="{ row }">
                  <el-dropdown trigger="click" placement="bottom-end" @click.stop>
                    <el-button class="package-actions-trigger" text circle :aria-label="`More actions for ${row.studentName}`" @click.stop>
                      <el-icon><MoreFilled /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item @click="openOneOnOneRenewal(row)">Renew</el-dropdown-item>
                        <el-dropdown-item @click="openDetailDialog(row)">Detail</el-dropdown-item>
                        <el-dropdown-item
                          v-if="isPackagePinned(row)"
                          divided
                          class="package-actions-menu__unpin"
                          :disabled="updatingPriorityPackageId !== null"
                          @click="setPackagePinned(row, false)"
                        >
                          Unpin
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-else
                          divided
                          :disabled="updatingPriorityPackageId !== null"
                          @click="setPackagePinned(row, true)"
                        >
                          Pin
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-table-column>
            </el-table>

            <div v-else class="mobile-package-list" role="list" :aria-label="`${group.courseName} package list`">
              <article
                v-for="pkg in group.packages"
                :key="pkg.id"
                class="mobile-package-row"
                role="listitem"
                :class="{
                  'mobile-package-row--low-balance': isLowBalancePinned(pkg),
                  'mobile-package-row--renewal-due': !isLowBalancePinned(pkg) && isOneOnOneRenewalDue(pkg),
                }"
              >
                <div class="mobile-package-row__main">
                  <div class="mobile-package-row__identity">
                    <strong>{{ pkg.studentName }}</strong>
                    <span>{{ packageDisplayName(pkg) }}</span>
                    <small v-if="pkg.packageStatus !== 'ACTIVE'" class="mobile-package-row__status">
                      {{ packageStatusLabel(pkg.packageStatus) }}
                    </small>
                  </div>
                  <div class="mobile-package-row__balance">
                    <strong :class="{ 'low-balance-renewal-text': isLowBalancePinned(pkg) }">
                      {{ formatHours(pkg.remainingUnits) }}
                    </strong>
                    <span>of {{ formatHours(pkg.totalUnits) }}</span>
                  </div>
                </div>
                <div class="mobile-package-row__progress-actions">
                  <div
                    class="mobile-package-row__progress"
                    role="meter"
                    aria-valuemin="0"
                    aria-valuemax="100"
                    :aria-valuenow="Math.round(progressPercent(pkg))"
                    :aria-label="`${Math.round(progressPercent(pkg))}% of package hours used`"
                  >
                    <span class="mobile-package-row__progress-track">
                      <i :style="{ width: `${progressPercent(pkg)}%` }" />
                    </span>
                    <small>{{ Math.round(progressPercent(pkg)) }}% used</small>
                  </div>
                  <div class="mobile-package-row__actions">
                    <el-button
                      v-if="isLowBalancePinned(pkg) || isOneOnOneRenewalDue(pkg)"
                      class="mobile-package-renew"
                      size="small"
                      type="primary"
                      plain
                      @click.stop="openOneOnOneRenewal(pkg)"
                    >Renew</el-button>
                    <el-dropdown trigger="click" placement="bottom-end" @click.stop>
                      <el-button class="package-actions-trigger" text circle :aria-label="`More actions for ${pkg.studentName}`" @click.stop>
                        <el-icon><MoreFilled /></el-icon>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item v-if="!isLowBalancePinned(pkg) && !isOneOnOneRenewalDue(pkg)" @click="openOneOnOneRenewal(pkg)">Renew</el-dropdown-item>
                          <el-dropdown-item @click="openDetailDialog(pkg)">Package details</el-dropdown-item>
                          <el-dropdown-item
                            v-if="isPackagePinned(pkg)"
                            divided
                            class="package-actions-menu__unpin"
                            :disabled="updatingPriorityPackageId !== null"
                            @click="setPackagePinned(pkg, false)"
                          >Unpin</el-dropdown-item>
                          <el-dropdown-item v-else divided :disabled="updatingPriorityPackageId !== null" @click="setPackagePinned(pkg, true)">Pin</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>
                <div class="mobile-package-row__footer">
                  <div class="mobile-package-row__next-lesson">
                    <el-tag size="small" :type="scheduleStatusType(pkg.scheduleStatus)">{{ pkg.scheduleStatus }}</el-tag>
                    <span>{{ pkg.nextLessonStartAt ? formatScheduleDate(pkg.nextLessonStartAt) : 'No next lesson' }}</span>
                  </div>
                </div>
              </article>
            </div>
          </div>
        </section>
        <el-empty v-if="!loading && groupedTableData.length === 0" description="No course packages found." />
      </div>

      <div
        v-if="appStore.isMobileViewport && mobileHasMore"
        ref="mobileLoadMoreSentinel"
        class="mobile-load-more"
        aria-live="polite"
      >
        <el-button link :loading="loadingMore" @click="loadMoreMobile">
          {{ loadingMore ? 'Loading more packages…' : mobileLoadMoreFailed ? 'Load more packages' : `Showing ${tableData.length} of ${pagination.total} packages · tap to load more` }}
        </el-button>
      </div>

      <div v-if="!appStore.isMobileViewport" class="table-footer">
        <span>Showing {{ pagination.start }}–{{ pagination.end }} of {{ pagination.total }}</span>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          :layout="appStore.isMobileViewport ? 'prev, pager, next' : 'prev, pager, next, sizes'"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          @current-change="() => loadData()"
          @size-change="() => loadData()"
        />
      </div>
    </section>

    <!-- Package detail dialog with tabs -->
    <el-dialog
      v-model="detailDialogVisible"
      width="900px"
      :close-on-click-modal="false"
      class="package-dialog"
      @closed="clearOneOnOnePrintFrame"
    >
      <template #header>
        <h3>Package Detail</h3>
      </template>

      <iframe
        ref="signaturePrintFrame"
        class="package-signature-print-frame"
        title="Signature sheet print document"
        aria-hidden="true"
        tabindex="-1"
      />

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
                  <label>Total hours</label>
                  <span>{{ formatHours(detailData.totalUnits) }}</span>
                </div>
                <div class="detail-field">
                  <label>Already deducted</label>
                  <span>{{ formatHours(detailData.consumedUnits) }}</span>
                </div>
                <div class="detail-field">
                  <label>Remaining</label>
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
                    <el-table-column
                      label="Time"
                      min-width="150"
                      sortable
                      :sort-method="compareScheduleTimes"
                      :filters="scheduleTimeFilters(packageScheduleRule.slots)"
                      :filter-method="filterScheduleTime"
                    >
                      <template #default="{ row }">
                        {{ weekdayLabel(row.weekday) }} · {{ row.startTime }}–{{ scheduleEndTimeForDisplay(row.startTime, row.endTime) }}
                      </template>
                    </el-table-column>
                    <el-table-column
                      label="Teacher"
                      min-width="150"
                      sortable
                      :sort-method="compareScheduleTeachers"
                      :filters="scheduleTeacherFilters(packageScheduleRule.slots)"
                      :filter-method="filterScheduleTeacher"
                    >
                      <template #default="{ row }">{{ scheduleTeacherName(row.teacherId) }}</template>
                    </el-table-column>
                    <el-table-column
                      label="Lesson name"
                      min-width="130"
                      sortable
                      :sort-method="compareLessonNames"
                      :filters="textColumnFilters(packageScheduleRule.slots, row => row.subjectName || '—')"
                      :filter-method="filterLessonName"
                    >
                      <template #default="{ row }">{{ row.subjectName || '—' }}</template>
                    </el-table-column>
                    <el-table-column
                      label="Each lesson"
                      width="110"
                      align="center"
                      prop="plannedUnits"
                      sortable
                      :filters="numberColumnFilters(packageScheduleRule.slots, row => row.plannedUnits, formatUnitsAsHours)"
                      :filter-method="filterScheduleUnits"
                    >
                      <template #default="{ row }">{{ formatUnitsAsHours(row.plannedUnits) }}</template>
                    </el-table-column>
                    <el-table-column
                      label="Allocated"
                      width="110"
                      align="center"
                      prop="allocatedUnits"
                      sortable
                      :sort-method="compareAllocatedUnits"
                      :filters="numberColumnFilters(packageScheduleRule.slots, row => row.allocatedUnits, formatUnitsAsHours)"
                      :filter-method="filterAllocatedUnits"
                    >
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
              <el-table-column
                label="Lesson No."
                min-width="130"
                prop="lessonNo"
                sortable
                :filters="textColumnFilters(lessonHistory, row => row.lessonNo)"
                :filter-method="filterLessonNumber"
              />
              <el-table-column
                label="Status"
                min-width="100"
                align="center"
                :filters="textColumnFilters(lessonHistory, row => resultStatusLabel(row.resultStatus))"
                :filter-method="filterLessonStatus"
              >
                <template #default="{ row }">
                  <el-tag size="small" :type="resultStatusTag(row.resultStatus)">
                    {{ resultStatusLabel(row.resultStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                label="Hours"
                min-width="70"
                align="center"
                prop="actualUnits"
                sortable
                :filters="numberColumnFilters(lessonHistory, row => row.actualUnits)"
                :filter-method="filterActualUnits"
              >
                <template #default="{ row }">{{ row.actualUnits }}</template>
              </el-table-column>
              <el-table-column
                label="Deducted"
                min-width="80"
                align="center"
                prop="deductedUnits"
                sortable
                :filters="numberColumnFilters(lessonHistory, row => row.deductedUnits)"
                :filter-method="filterDeductedUnits"
              >
                <template #default="{ row }">
                  <span :class="{ 'deducted-text': row.shouldDeduct }">{{ row.deductedUnits }}</span>
                </template>
              </el-table-column>
              <el-table-column
                label="Teacher"
                min-width="110"
                prop="actualTeacherName"
                sortable
                :filters="textColumnFilters(lessonHistory, row => row.actualTeacherName || '—')"
                :filter-method="filterActualTeacher"
              />
              <el-table-column
                label="Confirmed"
                min-width="150"
                prop="confirmedAt"
                sortable
                :sort-method="compareConfirmedDates"
                :filters="confirmedDateFilters(lessonHistory)"
                :filter-method="filterConfirmedDate"
              />
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
          <el-button
            v-if="detailData"
            plain
            :loading="signaturePrintLoading"
            @click="printOneOnOneSheet('parent')"
          >
            Print Student Sheet
          </el-button>
          <el-dropdown
            v-if="detailData"
            trigger="click"
            :disabled="signaturePrintLoading || detailTeacherPrintOptions.length === 0"
            @command="printTeacherSheet"
          >
            <el-button plain :loading="signaturePrintLoading" :disabled="detailTeacherPrintOptions.length === 0">
              Print Teacher Sheet
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="__all__">All teachers — one sheet</el-dropdown-item>
                <el-dropdown-item
                  v-for="option in detailTeacherPrintOptions"
                  :key="option.key"
                  :command="option.key"
                >
                  {{ option.teacher }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="detailData?.packageStatus === 'ACTIVE'" :disabled="packageStatusSaving" @click="pauseConfirmationVisible = true">
            Pause Package
          </el-button>
          <el-button v-if="detailData?.packageStatus === 'PAUSED'" type="primary" @click="openResumePackage">
            Resume Package
          </el-button>
          <el-button @click="detailDialogVisible = false">Close</el-button>
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
      <p>Resuming does not restore the previous schedule. Create a new schedule from the Schedule page to continue lessons.</p>
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

    <PauseConfirmationDialog
      v-model="pauseConfirmationVisible"
      title="Pause Package"
      message="Before pausing, please deduct lessons that have already started but are not yet deducted. You can still pause without deducting them. Pausing clears all future lessons from student and teacher schedules; after resuming, create a new schedule to continue."
      confirm-text="Pause Package"
      :loading="packageStatusSaving"
      @confirm="handlePausePackage"
    />
  </div>
</template>

<script setup lang="ts">
import { Close, MoreFilled, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { extractErrorMessage } from '@/api/http';
import {
  adjustCoursePackageHours,
  fetchCoursePackage,
  fetchCoursePackages,
  pauseCoursePackage,
  resumeCoursePackage,
  setCoursePackageRenewalPriority,
} from '@/api/enrollment';
import {
  fetchLessonRecord,
  fetchLessonRecords,
  type LessonRecordQuery,
} from '@/api/lesson';
import { fetchAllStudents, fetchAllTeachers } from '@/api/base-data';
import { fetchRecurringScheduleRule, fetchRecurringScheduleRules } from '@/api/schedule';
import { fetchOneOnOneDeductions, fetchPrivateSignatureHistory } from '@/api/one-on-one';
import { scheduleEndTimeForDisplay } from '@/utils/schedule-time';
import { renderOneOnOneSignatureSheetDocument } from '@/utils/signature-sheet-print';
import { findSignaturePackage, withSignatureHistory } from '@/utils/one-on-one-signature-data';
import PauseConfirmationDialog from '@/components/shared/PauseConfirmationDialog.vue';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import { useAppStore } from '@/stores/app';
import type { OccupiedSlotDef } from '@/components/WeeklyTimeSelector.vue';
import type {
  CoursePackageItem,
  EntityId,
  LessonRecordDetailItem,
  LessonRecordItem,
  RecurringScheduleRuleDetailItem,
  RecurringScheduleSlotItem,
  TeacherItem,
} from '@/types/domain';

const router = useRouter();
const appStore = useAppStore();
const mobileSearchExpanded = ref(false);
const RENEWAL_DUE_DAYS = 15;
const AUTO_PIN_BALANCE_THRESHOLD_HOURS = 10;
const PACKAGE_GROUP_COLLAPSE_STORAGE_KEY = 'course-packages.collapsed-groups.v1';
const loading = ref(false);
const detailDialogVisible = ref(false);
const detailTab = ref('overview');
const detailData = ref<CoursePackageItem | null>(null);
const signaturePrintLoading = ref(false);
const signaturePrintFrame = ref<HTMLIFrameElement | null>(null);
const updatingPriorityPackageId = ref<string | null>(null);
const detailTeacherPrintOptions = computed(() => {
  const options = new Map<string, { key: string; teacher: string }>();
  for (const assignment of detailData.value?.teacherAssignments ?? []) {
    const teacher = assignment.teacherName?.trim();
    if (!teacher) continue;
    const key = String(assignment.teacherId || '').trim() || teacher.toLowerCase();
    if (!options.has(key)) options.set(key, { key, teacher });
  }
  const primaryTeacher = detailData.value?.primaryTeacherName?.trim();
  if (primaryTeacher) {
    const key = primaryTeacher.toLowerCase();
    if (!options.has(key)) options.set(key, { key, teacher: primaryTeacher });
  }
  return [...options.values()].sort((left, right) => left.teacher.localeCompare(right.teacher));
});

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
const pauseConfirmationVisible = ref(false);
const packageStatusSaving = ref(false);
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
    endTime: scheduleEndTimeForDisplay(slot.startTime, slot.endTime),
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
  onlyRenewalDue: false,
});

const pagination = reactive({
  page: 1,
  size: appStore.isMobileViewport ? 200 : 100,
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

const tableData = ref<OneOnOnePackageRow[]>([]);
// 移动端加载更多状态：模板哨兵、观察器与分页请求共享这组响应式引用。
const mobilePage = ref(1);
const mobileHasMore = ref(false);
const loadingMore = ref(false);
const mobileLoadMoreFailed = ref(false);
const mobileLoadMoreSentinel = ref<HTMLElement | null>(null);
let mobileLoadMoreObserver: IntersectionObserver | null = null;
// 桌面端沿用用户保存的分组状态；移动端每次进入先折叠，减少首屏信息拥挤。
const collapsedPackageGroups = ref<Set<string>>(readCollapsedPackageGroups());
const expandedPackageGroups = ref<Set<string>>(new Set());

function readCollapsedPackageGroups(): Set<string> {
  try {
    const saved = window.localStorage.getItem(PACKAGE_GROUP_COLLAPSE_STORAGE_KEY);
    const keys: unknown = saved ? JSON.parse(saved) : [];
    return new Set(Array.isArray(keys) ? keys.filter((key): key is string => typeof key === 'string') : []);
  } catch {
    return new Set();
  }
}

function isPackageGroupCollapsed(groupKey: string): boolean {
  return appStore.isMobileViewport
    ? !expandedPackageGroups.value.has(groupKey)
    : collapsedPackageGroups.value.has(groupKey);
}

function togglePackageGroup(groupKey: string) {
  if (!appStore.isMobileViewport) {
    const next = new Set(collapsedPackageGroups.value);
    if (next.has(groupKey)) next.delete(groupKey);
    else next.add(groupKey);
    collapsedPackageGroups.value = next;
    try {
      window.localStorage.setItem(PACKAGE_GROUP_COLLAPSE_STORAGE_KEY, JSON.stringify([...next]));
    } catch {
      // 浏览器禁用本地存储时，当前页面中的折叠状态仍然有效。
    }
    return;
  }

  const next = new Set(expandedPackageGroups.value);
  if (next.has(groupKey)) next.delete(groupKey);
  else next.add(groupKey);
  expandedPackageGroups.value = next;
}

// 当前分页结果按课程和年级合并成组，课包仍保留原有分页、筛选及行操作。
const groupedTableData = computed(() => {
  const groups = new Map<string, {
    key: string;
    courseName: string;
    gradeBand: string;
    pinned: boolean;
    renewalCount: number;
    packages: OneOnOnePackageRow[];
  }>();

  for (const pkg of tableData.value) {
    const courseName = pkg.courseProductName?.trim() || pkg.packageName?.trim() || 'Unassigned course';
    const gradeBand = pkg.gradeBand?.trim() || 'General';
    const pinned = isPackagePinned(pkg);
    const key = JSON.stringify([normalizeLabel(courseName), normalizeLabel(gradeBand), pinned]);
    let group = groups.get(key);
    if (!group) {
      group = { key, courseName, gradeBand, pinned, renewalCount: 0, packages: [] };
      groups.set(key, group);
    }
    group.packages.push(pkg);
    if (isLowBalancePinned(pkg) || isOneOnOneRenewalDue(pkg)) group.renewalCount += 1;
  }

  return Array.from(groups.values())
    .map(group => ({
      ...group,
      packages: [...group.packages].sort((left, right) =>
        (group.pinned ? Number(left.remainingUnits) - Number(right.remainingUnits) : 0)
        || left.studentName.localeCompare(right.studentName)
        || left.packageName.localeCompare(right.packageName)),
    }))
    .sort((left, right) => Number(right.pinned) - Number(left.pinned)
      || left.courseName.localeCompare(right.courseName)
      || left.gradeBand.localeCompare(right.gradeBand, undefined, { numeric: true }));
});

function loadMoreMobile(): void {
  mobileLoadMoreFailed.value = false;
  void loadData(true);
}

watch(
  [mobileLoadMoreSentinel, () => appStore.isMobileViewport, mobileHasMore, loadingMore, mobileLoadMoreFailed, () => tableData.value.length],
  ([sentinel, isMobile, hasMore, isLoadingMore, hasLoadMoreFailed]) => {
    mobileLoadMoreObserver?.disconnect();
    if (!sentinel || !isMobile || !hasMore || isLoadingMore || hasLoadMoreFailed || typeof IntersectionObserver === 'undefined') return;

    mobileLoadMoreObserver = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) loadMoreMobile();
    }, { rootMargin: '240px 0px' });
    mobileLoadMoreObserver.observe(sentinel);
  },
  { flush: 'post' },
);

onBeforeUnmount(() => mobileLoadMoreObserver?.disconnect());

type TableFilterOption = { text: string; value: string };

function textColumnFilters<T>(rows: T[], getValue: (row: T) => string | undefined | null): TableFilterOption[] {
  const values = new Map<string, string>();
  rows.forEach((row) => {
    const label = getValue(row)?.trim() || '—';
    values.set(normalizeLabel(label), label);
  });
  return [...values.entries()].map(([value, text]) => ({ text, value }));
}

function numberColumnFilters<T>(
  rows: T[],
  getValue: (row: T) => number | undefined,
  formatValue: (value?: number) => string = value => value == null ? '—' : String(value),
): TableFilterOption[] {
  const values = new Map<string, string>();
  rows.forEach((row) => {
    const number = getValue(row);
    const value = number == null ? '__NONE__' : String(number);
    values.set(value, formatValue(number));
  });
  return [...values.entries()].map(([value, text]) => ({ text, value }));
}

function packageNameFilters(rows: OneOnOnePackageRow[]): TableFilterOption[] {
  const labels = rows.flatMap(row => [row.packageName || row.courseProductName || 'Unnamed package', row.courseProductName || '']);
  const values = new Map<string, string>();
  labels.filter(Boolean).forEach((label) => values.set(normalizeLabel(label), label));
  return [...values.entries()].map(([value, text]) => ({ text, value }));
}

const availableHourFilters: TableFilterOption[] = [
  { text: '10h or less', value: '0-10' },
  { text: 'Over 10h to 20h', value: '10-20' },
  { text: 'Over 20h', value: '20+' },
];

function scheduleTimeFilters(rows: RecurringScheduleSlotItem[]): TableFilterOption[] {
  return rows.map(row => ({
    text: `${weekdayLabel(row.weekday)} · ${row.startTime}–${scheduleEndTimeForDisplay(row.startTime, row.endTime)}`,
    value: scheduleTimeKey(row),
  }));
}

function scheduleTeacherFilters(rows: RecurringScheduleSlotItem[]): TableFilterOption[] {
  const values = new Map<string, string>();
  rows.forEach((row) => {
    const value = row.teacherId == null ? '__NONE__' : String(row.teacherId);
    values.set(value, scheduleTeacherName(row.teacherId));
  });
  return [...values.entries()].map(([value, text]) => ({ text, value }));
}

function confirmedDateFilters(rows: LessonRecordItem[]): TableFilterOption[] {
  const values = new Map<string, string>();
  rows.forEach((row) => {
    const value = row.confirmedAt || '__NONE__';
    values.set(value, row.confirmedAt || '—');
  });
  return [...values.entries()].map(([value, text]) => ({ text, value }));
}

function filterStudentName(value: string, row: OneOnOnePackageRow): boolean {
  return normalizeLabel(row.studentName) === value;
}

function filterPackageName(value: string, row: OneOnOnePackageRow): boolean {
  return [row.packageName, row.courseProductName].some(name => normalizeLabel(name) === value);
}

function filterAvailableHours(value: string, row: OneOnOnePackageRow): boolean {
  const hours = Number(row.remainingUnits);
  if (value === '0-10') return hours <= 10;
  if (value === '10-20') return hours > 10 && hours <= 20;
  return hours > 20;
}

function filterScheduleStatus(value: string, row: OneOnOnePackageRow): boolean {
  return normalizeLabel(row.scheduleStatus) === value;
}

function filterPackageStatus(value: string, row: OneOnOnePackageRow): boolean {
  return normalizeLabel(packageStatusLabel(row.packageStatus)) === value;
}

function filterScheduleTime(value: string, row: RecurringScheduleSlotItem): boolean {
  return scheduleTimeKey(row) === value;
}

function filterScheduleTeacher(value: string, row: RecurringScheduleSlotItem): boolean {
  return value === '__NONE__' ? row.teacherId == null : String(row.teacherId) === value;
}

function filterLessonName(value: string, row: RecurringScheduleSlotItem): boolean {
  return normalizeLabel(row.subjectName || '—') === value;
}

function filterScheduleUnits(value: string, row: RecurringScheduleSlotItem): boolean {
  return value === '__NONE__' ? row.plannedUnits == null : String(row.plannedUnits) === value;
}

function filterAllocatedUnits(value: string, row: RecurringScheduleSlotItem): boolean {
  return value === '__NONE__' ? row.allocatedUnits == null : String(row.allocatedUnits) === value;
}

function filterLessonNumber(value: string, row: LessonRecordItem): boolean {
  return normalizeLabel(row.lessonNo) === value;
}

function filterLessonStatus(value: string, row: LessonRecordItem): boolean {
  return normalizeLabel(resultStatusLabel(row.resultStatus)) === value;
}

function filterActualUnits(value: string, row: LessonRecordItem): boolean {
  return String(row.actualUnits) === value;
}

function filterDeductedUnits(value: string, row: LessonRecordItem): boolean {
  return String(row.deductedUnits) === value;
}

function filterActualTeacher(value: string, row: LessonRecordItem): boolean {
  return normalizeLabel(row.actualTeacherName || '—') === value;
}

function filterConfirmedDate(value: string, row: LessonRecordItem): boolean {
  return value === '__NONE__' ? !row.confirmedAt : row.confirmedAt === value;
}

function comparePackageNames(left: OneOnOnePackageRow, right: OneOnOnePackageRow): number {
  return packageDisplayName(left).localeCompare(packageDisplayName(right));
}

function packageDisplayName(row: OneOnOnePackageRow): string {
  return row.packageName || row.courseProductName || 'Unnamed package';
}

function compareAvailableHours(left: OneOnOnePackageRow, right: OneOnOnePackageRow): number {
  return Number(left.remainingUnits) - Number(right.remainingUnits);
}

function compareNextLessonDates(left: OneOnOnePackageRow, right: OneOnOnePackageRow): number {
  return dateSortValue(left.nextLessonStartAt) - dateSortValue(right.nextLessonStartAt);
}

function compareScheduleTimes(left: RecurringScheduleSlotItem, right: RecurringScheduleSlotItem): number {
  return left.weekday - right.weekday || left.startTime.localeCompare(right.startTime) || left.endTime.localeCompare(right.endTime);
}

function compareScheduleTeachers(left: RecurringScheduleSlotItem, right: RecurringScheduleSlotItem): number {
  return scheduleTeacherName(left.teacherId).localeCompare(scheduleTeacherName(right.teacherId));
}

function compareLessonNames(left: RecurringScheduleSlotItem, right: RecurringScheduleSlotItem): number {
  return (left.subjectName || '').localeCompare(right.subjectName || '');
}

function compareAllocatedUnits(left: RecurringScheduleSlotItem, right: RecurringScheduleSlotItem): number {
  return Number(left.allocatedUnits ?? -1) - Number(right.allocatedUnits ?? -1);
}

function compareConfirmedDates(left: LessonRecordItem, right: LessonRecordItem): number {
  return dateSortValue(left.confirmedAt) - dateSortValue(right.confirmedAt);
}

function dateSortValue(value?: string): number {
  if (!value) return Number.MAX_SAFE_INTEGER;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? Number.MAX_SAFE_INTEGER : time;
}

function scheduleTimeKey(row: RecurringScheduleSlotItem): string {
  return `${row.weekday}|${row.startTime}|${row.endTime}`;
}

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

function toggleRenewalDueFilter() {
  filters.onlyRenewalDue = !filters.onlyRenewalDue;
  handleFilterChange();
}

function progressPercent(row: { totalUnits: number; consumedUnits: number }): number {
  return row.totalUnits > 0 ? Math.round((row.consumedUnits / row.totalUnits) * 100) : 0;
}

function remainingProgressPercent(row: { totalUnits: number; remainingUnits: number }): number {
  return row.totalUnits > 0 ? Math.round((row.remainingUnits / row.totalUnits) * 100) : 0;
}

function parseDateValue(value?: string): number | undefined {
  if (!value) return undefined;
  const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const date = dateOnlyMatch
    ? new Date(Number(dateOnlyMatch[1]), Number(dateOnlyMatch[2]) - 1, Number(dateOnlyMatch[3]))
    : new Date(value);
  const time = date.getTime();
  return Number.isNaN(time) ? undefined : time;
}

function isRenewalDueByDate(endDateValue?: string): boolean {
  const endTime = parseDateValue(endDateValue);
  if (endTime == null) {
    return false;
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const dueLimit = new Date(today);
  dueLimit.setDate(dueLimit.getDate() + RENEWAL_DUE_DAYS);
  return endTime <= dueLimit.getTime();
}

function isOneOnOneRenewalDue(row: CoursePackageItem): boolean {
  return row.packageStatus === 'ACTIVE' && isRenewalDueByDate(row.estimatedEndDate);
}

function isPackagePinned(row: CoursePackageItem): boolean {
  if (row.renewalPriorityOverride != null) return row.renewalPriorityOverride;
  return isBelowAutoPinBalanceThreshold(row);
}

function isBelowAutoPinBalanceThreshold(row: CoursePackageItem): boolean {
  const remainingHours = Number(row.remainingUnits);
  return Number.isFinite(remainingHours) && remainingHours <= AUTO_PIN_BALANCE_THRESHOLD_HOURS;
}

function isLowBalancePinned(row: CoursePackageItem): boolean {
  return isPackagePinned(row) && isBelowAutoPinBalanceThreshold(row);
}

function oneOnOneRowClassName({ row }: { row: OneOnOnePackageRow }) {
  if (isLowBalancePinned(row)) return 'low-balance-renewal-row';
  if (isPackagePinned(row)) return '';
  return isOneOnOneRenewalDue(row) ? 'renewal-due-row' : '';
}

// 保存手动覆盖值后同步更新当前行，列表刷新时仍按服务端的优先级状态展示。
async function setPackagePinned(row: OneOnOnePackageRow, pinned: boolean) {
  const packageId = String(row.id);
  if (isPackagePinned(row) === pinned || updatingPriorityPackageId.value) return;
  updatingPriorityPackageId.value = packageId;
  try {
    await setCoursePackageRenewalPriority(row.id, pinned);
    row.renewalPriorityOverride = pinned;
    ElMessage.success(pinned ? 'Package pinned.' : 'Package unpinned.');
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    updatingPriorityPackageId.value = null;
  }
}

function normalizeLabel(value?: string | null): string {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLowerCase();
}

function shouldShowProductName(row: CoursePackageItem): boolean {
  return Boolean(row.courseProductName)
    && normalizeLabel(row.courseProductName) !== normalizeLabel(row.packageName);
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
async function loadData(appendMobile = false) {
  const isMobile = appStore.isMobileViewport;
  const shouldAppendMobile = appendMobile && isMobile;
  if (shouldAppendMobile && (loadingMore.value || !mobileHasMore.value)) return;

  if (shouldAppendMobile) {
    loadingMore.value = true;
  } else {
    loading.value = true;
    if (isMobile) {
      mobilePage.value = 1;
      mobileHasMore.value = false;
      mobileLoadMoreFailed.value = false;
    }
  }

  const requestedPage = isMobile ? (shouldAppendMobile ? mobilePage.value + 1 : 1) : pagination.page;
  try {
    const res = await fetchCoursePackages({
      keyword: filters.keyword || undefined,
      studentId: filters.studentId,
      packageStatus: filters.packageStatus || undefined,
      teachingMode: 'ONE_ON_ONE',
      renewalDueWithinDays: filters.onlyRenewalDue ? RENEWAL_DUE_DAYS : undefined,
      includeScheduleSummary: true,
      prioritizeLowBalance: true,
      pageNum: requestedPage,
      pageSize: isMobile ? 200 : pagination.size,
    });
    const data = res.data.data;
    pagination.total = data.total;
    const records = data.records.map((pkg) => ({
      ...pkg,
      scheduleStatus: pkg.scheduleStatus ?? 'Needs setup',
    }));
    tableData.value = shouldAppendMobile ? [...tableData.value, ...records] : records;
    if (isMobile) {
      mobilePage.value = requestedPage;
      mobileHasMore.value = records.length > 0 && tableData.value.length < data.total;
    }
  } catch {
    if (shouldAppendMobile) mobileLoadMoreFailed.value = true;
    ElMessage.error('Failed to load packages.');
  } finally {
    if (shouldAppendMobile) {
      loadingMore.value = false;
    } else {
      loading.value = false;
    }
  }
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

function openOneOnOneRenewal(row: CoursePackageItem) {
  router.push({
    name: 'registration',
    query: {
      renewPackageId: String(row.id),
      studentId: String(row.studentId),
    },
  });
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

/** 课包详情按钮直接调用浏览器打印；打印内容仍复用 1-on-1 签字表模板。 */
async function printOneOnOneSheet(mode: 'parent' | 'teacher', teacherKey = '__all__') {
  const coursePackage = detailData.value;
  if (!coursePackage || signaturePrintLoading.value) return;

  signaturePrintLoading.value = true;
  try {
    const [deductions, history] = await Promise.allSettled([
      fetchOneOnOneDeductions({
        studentId: coursePackage.studentId,
        packageId: coursePackage.id,
      }),
      fetchPrivateSignatureHistory(coursePackage.id),
    ]);
    if (deductions.status === 'rejected') throw deductions.reason;

    const match = findSignaturePackage(
      deductions.value.data.data,
      coursePackage.studentId,
      coursePackage.id,
    );
    if (!match) throw new Error('No signature sheet is available for this course.');

    if (history.status === 'rejected') {
      ElMessage.warning('Historical signature entries could not be loaded. The sheet will include scheduled lessons only.');
    }
    const printPackage = history.status === 'fulfilled'
      ? withSignatureHistory(match.coursePackage, history.value.data.data)
      : match.coursePackage;

    const frame = signaturePrintFrame.value;
    if (!frame) throw new Error('The print document is not ready.');
    const printWindow = await loadSignaturePrintDocument(frame, renderOneOnOneSignatureSheetDocument(
      match.student,
      printPackage,
      mode,
      teacherKey,
      { embedded: true },
    ));
    printWindow.focus();
    printWindow.print();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    signaturePrintLoading.value = false;
  }
}

/** 课包详情按所选老师打印；All teachers 保留合并打印入口。 */
function printTeacherSheet(command: string | number | object) {
  void printOneOnOneSheet('teacher', String(command || '__all__'));
}

/** 等隐藏 iframe 完成文档加载，再让浏览器直接打开这份签字表的打印预览。 */
function loadSignaturePrintDocument(frame: HTMLIFrameElement, html: string): Promise<Window> {
  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      frame.removeEventListener('load', handleLoad);
      reject(new Error('The print document took too long to load.'));
    }, 15000);
    const handleLoad = () => {
      window.clearTimeout(timeout);
      const printWindow = frame.contentWindow;
      if (!printWindow) {
        reject(new Error('The print document is not available.'));
        return;
      }
      resolve(printWindow);
    };

    frame.addEventListener('load', handleLoad, { once: true });
    frame.srcdoc = html;
  });
}

function clearOneOnOnePrintFrame() {
  if (signaturePrintFrame.value) signaturePrintFrame.value.srcdoc = '';
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
    // 详情仅展示当前可用计划；停课后结束的旧规则属于历史，复课需重新设置。
    if (detailData.value?.packageStatus === 'PAUSED') {
      packageScheduleRule.value = null;
      return;
    }
    const result = await fetchRecurringScheduleRules({ packageId, pageNum: 1, pageSize: 10 });
    const rule = result.data.data.records.find(item => item.ruleStatus === 'ACTIVE' || item.ruleStatus === 'PAUSED');
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

async function handlePausePackage() {
  if (!detailData.value || detailData.value.packageStatus !== 'ACTIVE' || packageStatusSaving.value) return;
  packageStatusSaving.value = true;
  try {
    await pauseCoursePackage(detailData.value.id);
    pauseConfirmationVisible.value = false;
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
  resumePackageVisible.value = true;
}

async function submitResumePackage() {
  if (!detailData.value) return;
  packageStatusSaving.value = true;
  try {
    await resumeCoursePackage(detailData.value.id);
    resumePackageVisible.value = false;
    await reloadPackageDetail();
    await loadData();
    ElMessage.success('Package resumed. Create a new schedule to continue lessons.');
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

watch(() => appStore.isMobileViewport, (isMobile) => {
  const nextSize = isMobile ? 200 : 100;
  if (pagination.size === nextSize) return;
  pagination.size = nextSize;
  pagination.page = 1;
  loadData();
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

.package-group-list {
  display: grid;
  gap: 14px;
  min-height: 160px;
}

.package-group {
  overflow: hidden;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #fff;
}

.package-group__header {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  border-bottom: 1px solid #d9e2ec;
  background: #f3f7fb;
}

.package-group__toggle {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.package-group__toggle:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: 3px;
  border-radius: 3px;
}

.package-group__chevron {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-right: 2px solid #718096;
  border-bottom: 2px solid #718096;
  transform: rotate(45deg) translateY(-2px);
  transition: transform 0.16s ease;
}

.package-group__chevron.is-collapsed {
  transform: rotate(-45deg);
}

.package-group__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.package-group__title strong {
  overflow: hidden;
  color: #26384c;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.package-group__grade {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.package-group__header small {
  flex: 0 0 auto;
  color: #718096;
  font-size: 11px;
}

.package-group__content { min-width: 0; }

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 0;
}

.available-cell {
  width: 100%;
  min-width: 0;
  text-align: center;
}

.available-cell__value {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 5px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  line-height: 1.35;
}

.available-cell__value strong {
  font-size: 16px;
}

.available-cell__value span {
  color: #606266;
}

.available-cell__progress {
  width: 100%;
  max-width: 180px;
  margin: 4px auto 0;
}

.product-table :deep(.renewal-due-row > td) {
  background: #fff1f2 !important;
}

.product-table :deep(.renewal-due-row:hover > td) {
  background: #ffe4e6 !important;
}

.product-table :deep(.low-balance-renewal-row > td) {
  background: #fff0f0 !important;
}

.product-table :deep(.low-balance-renewal-row:hover > td) {
  background: #ffe1e1 !important;
}

.mobile-package-list { min-width: 0; background: #fff; }
.mobile-package-row { display: grid; gap: 7px; padding: 10px 11px; border-bottom: 1px solid #e7edf3; }
.mobile-package-row:last-child { border-bottom: 0; }
.mobile-package-row--low-balance { border-left: 3px solid #e3a244; background: #fffaf2; }
.mobile-package-row--renewal-due { border-left: 3px solid #e6bd70; background: #fffdf7; }
.mobile-package-row__main { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px; }
.mobile-package-row__identity { display: grid; min-width: 0; gap: 2px; }
.mobile-package-row__identity strong,
.mobile-package-row__identity span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-package-row__identity strong { color: #26384c; font-size: 12px; font-weight: 700; }
.mobile-package-row__identity span { color: #64748b; font-size: 10px; }
.mobile-package-row__balance { display: flex; align-items: baseline; gap: 3px; white-space: nowrap; font-variant-numeric: tabular-nums; }
.mobile-package-row__balance strong { color: #26384c; font-size: 13px; }
.mobile-package-row__balance span { color: #8190a3; font-size: 9px; }
.mobile-package-row__progress-actions { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 8px; }
.mobile-package-row__progress { display: grid; width: 100%; max-width: 220px; min-width: 0; flex: 1 1 auto; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 7px; }
.mobile-package-row__progress-track { display: block; height: 4px; overflow: hidden; border-radius: 4px; background: #e9eef4; }
.mobile-package-row__progress-track i { display: block; height: 100%; border-radius: inherit; background: #7ca8e8; }
.mobile-package-row--low-balance .mobile-package-row__progress-track i { background: #e3a244; }
.mobile-package-row__progress small { color: #7b8797; font-size: 9px; white-space: nowrap; }
.mobile-package-row__footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-width: 0; }
.mobile-package-row__next-lesson { display: flex; min-width: 0; align-items: center; gap: 6px; }
.mobile-package-row__next-lesson :deep(.el-tag) { flex: 0 0 auto; max-width: 94px; height: 18px; padding: 0 5px; font-size: 9px; line-height: 16px; }
.mobile-package-row__next-lesson > span { overflow: hidden; color: #64748b; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.mobile-package-row__actions { display: flex; flex: 0 0 auto; align-items: center; gap: 2px; }
.mobile-package-row__actions .package-actions-trigger { width: 32px; height: 32px; font-size: 15px; }
.mobile-package-renew { min-height: 30px; padding: 0 8px; font-size: 10px; }
.package-group__renewal-count { color: #b7791f; font-size: 10px; font-weight: 650; }

.package-actions-trigger {
  width: 34px;
  height: 34px;
  color: #52657d;
  font-size: 18px;
}

.product-table :deep(.package-actions-column-header .cell) {
  white-space: nowrap;
}

.package-actions-trigger:hover,
.package-actions-trigger:focus-visible {
  color: #2563eb;
  background: #eff6ff;
}

:global(.package-actions-menu__unpin) {
  color: #dc2626;
}

.low-balance-renewal-text {
  color: #dc2626;
}

.renewal-reminder-tag {
  margin-top: 4px;
}

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
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.mobile-load-more {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  color: #718096;
  font-size: 12px;
}

.mobile-load-more .el-button {
  min-height: 40px;
  white-space: normal;
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
.package-signature-print-frame {
  position: fixed;
  top: 0;
  left: -10000px;
  width: 1000px;
  height: 900px;
  border: 0;
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

:global(html.mobile-layout) & {
  .filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 12px; }
  .filters__item, .filters__item :deep(.el-input), .filters__item :deep(.el-select) { width: 100% !important; }
  .panel-header { min-height: 36px; align-items: center; flex-direction: row; justify-content: flex-end; gap: 6px; margin-bottom: 6px; }
  .panel-header__actions { width: auto; }
  .package-group__header { align-items: flex-start; flex-wrap: wrap; gap: 8px; }
  .detail-grid { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 640px) {
  .course-product-panel { padding: 10px; }
  .package-group-list { min-height: 0; gap: 8px; }
  .package-group__header { min-height: 42px; align-items: center; flex-direction: row; flex-wrap: nowrap; gap: 7px; padding: 6px 9px; }
  .package-group__toggle { gap: 7px; }
  .package-group__title { flex-wrap: nowrap; gap: 5px; }
  .package-group__title strong { max-width: 32vw; font-size: 11px; }
  .package-group__grade { padding: 2px 5px; font-size: 9px; }
  .package-group__title :deep(.el-tag) { height: 17px; padding: 0 5px; font-size: 9px; line-height: 15px; }
  .package-group__header > small { font-size: 9px; }
  .package-group__renewal-count { font-size: 8px; }
  .mobile-package-row { gap: 6px; padding: 8px 9px; }
  .mobile-package-row__identity strong { font-size: 12px; }
  .mobile-package-row__balance strong { font-size: 12px; }
  .mobile-package-row__progress { max-width: 180px; }
  .mobile-package-row__next-lesson :deep(.el-tag) { max-width: 84px; }
  .mobile-package-row__actions .package-actions-trigger { width: 30px; height: 30px; }
  .mobile-package-renew { min-height: 30px; padding: 0 7px; font-size: 10px; }
  .filters { grid-template-columns: minmax(0, 1fr); }
  .filters :deep(.el-form-item) { display: grid; grid-template-columns: minmax(0, 1fr); gap: 4px; }
  .filters :deep(.el-form-item__label) {
    display: block;
    width: auto;
    float: none;
    padding: 0;
    line-height: 1.3;
    text-align: left;
  }
  .filters :deep(.el-form-item__content) { width: 100%; margin-left: 0 !important; }
  .detail-grid { grid-template-columns: 1fr; }
  .dialog-footer { flex-wrap: wrap; }
  .table-footer { align-items: stretch; flex-direction: column; gap: 8px; }
  .table-footer :deep(.el-pagination) { width: 100%; min-width: 0; }
  .lesson-dialog :deep(.el-dialog__body) { max-height: none; padding: 12px 14px; }
  .lesson-dialog :deep(.el-dialog__header), .lesson-dialog :deep(.el-dialog__footer) { padding-right: 14px; padding-left: 14px; }
}
</style>
