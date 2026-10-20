<template>
  <div class="data-summary-page" v-loading="loading" @click="closeStudentContextMenu">
    <div class="workbook-toolbar">
      <div class="workbook-toolbar__month">
        <span class="workbook-toolbar__label">Month</span>
        <el-select
          v-model="selectedMonth"
          class="workbook-toolbar__select"
          size="small"
          filterable
        >
          <el-option label="All months" value="ALL" />
          <el-option
            v-for="month in availableMonths"
            :key="month.value"
            :label="month.label"
            :value="month.value"
          />
        </el-select>
        <el-button
          class="student-find-trigger"
          size="small"
          :type="studentFindOpen ? 'primary' : 'default'"
          aria-label="Find student (Ctrl+F)"
          title="Find student (Ctrl+F)"
          @click="openStudentFind"
        >
          <el-icon><Search /></el-icon>
          <span>Find</span>
        </el-button>
        <el-dropdown v-if="isMobileViewport" trigger="click" @command="handleMobileOverflowCommand">
          <el-button class="workbook-mobile-more" aria-label="More actions">
            <el-icon><MoreFilled /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="export-excel" :disabled="sortedRows.length + visibleRefundRows.length === 0">Export Excel</el-dropdown-item>
              <el-dropdown-item command="print-roster" :disabled="rosterPrintGroups.length === 0">Print class roster</el-dropdown-item>
              <template v-if="isSaturdayProductTab">
                <el-dropdown-item command="roster-fall">Fall roster</el-dropdown-item>
                <el-dropdown-item command="roster-spring">Spring roster</el-dropdown-item>
                <el-dropdown-item command="roster-all">All roster</el-dropdown-item>
              </template>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div v-if="studentFindOpen" class="student-find" role="search" aria-label="Find student in Data Summary">
        <el-input
          ref="studentFindInputRef"
          v-model="studentFindQuery"
          class="student-find__input"
          size="small"
          clearable
          placeholder="Find student in this course"
          aria-label="Find student name"
          @keydown.enter.prevent="stepStudentFind($event.shiftKey ? -1 : 1)"
          @keydown.esc.stop.prevent="closeStudentFind"
        />
        <span class="student-find__count" aria-live="polite">
          {{ studentFindQuery.trim() ? `${studentFindMatches.length ? studentFindIndex + 1 : 0} / ${studentFindMatches.length}` : 'Type a name' }}
        </span>
        <el-button-group class="student-find__navigation">
          <el-button size="small" :disabled="!studentFindMatches.length" aria-label="Previous match" title="Previous match (Shift+Enter)" @click="stepStudentFind(-1)">
            <el-icon><ArrowUp /></el-icon>
          </el-button>
          <el-button size="small" :disabled="!studentFindMatches.length" aria-label="Next match" title="Next match (Enter)" @click="stepStudentFind(1)">
            <el-icon><ArrowDown /></el-icon>
          </el-button>
        </el-button-group>
        <el-button text size="small" aria-label="Close find" title="Close find (Esc)" @click="closeStudentFind">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
      <div class="workbook-toolbar__stats">
        <span class="workbook-toolbar__stat">
          <em>Registrations</em>
          <strong>{{ monthStats.count }}</strong>
        </span>
        <span class="workbook-toolbar__stat">
          <template v-if="!isManager">
          <em>Paid</em>
          <strong>{{ formatMoney(monthStats.paid) }}</strong>
          </template>
        </span>
        <span class="workbook-toolbar__stat">
          <template v-if="!isManager">
          <em>Balance</em>
          <strong>{{ formatMoney(monthStats.balance) }}</strong>
          </template>
        </span>
      </div>
      <div v-if="!isMobileViewport" class="workbook-toolbar__export">
        <div v-if="isSaturdayProductTab" class="saturday-roster-control">
          <el-button-group aria-label="Saturday class roster">
            <el-button
              v-for="option in saturdayRosterOptions"
              :key="option.value"
              size="small"
              :type="saturdayRosterView === option.value ? 'primary' : 'default'"
              :aria-pressed="saturdayRosterView === option.value"
              @click="setSaturdayRosterView(option.value)"
            >
              {{ option.label }}
            </el-button>
          </el-button-group>
          <el-tooltip
            v-if="saturdayRosterView === 'ALL' && unclassifiedSaturdayRowCount > 0"
            content="Records without an identifiable season appear only in All roster."
            placement="top"
          >
            <span class="saturday-roster-control__hint">{{ unclassifiedSaturdayRowCount }} unclassified</span>
          </el-tooltip>
        </div>
        <el-button
          size="small"
          :disabled="rosterPrintGroups.length === 0"
          @click="printClassRoster"
        >
          Print class roster
        </el-button>
        <el-button
          size="small"
          type="primary"
          :disabled="sortedRows.length + visibleRefundRows.length === 0"
          @click="exportExcel"
        >
          Export Excel
        </el-button>
      </div>
    </div>
    <el-dialog
      v-model="rosterPrintDialogVisible"
      title="Print class roster"
      width="min(480px, calc(100vw - 32px))"
      append-to-body
      destroy-on-close
    >
      <div class="roster-print-picker">
        <p>Choose a class to print its student list.</p>
        <el-select
          v-model="selectedRosterPrintKey"
          class="roster-print-picker__select"
          popper-class="roster-print-campus-popper"
          filterable
          clearable
          placeholder="Select a class"
        >
          <el-option-group
            v-for="campusGroup in rosterPrintCampusGroups"
            :key="campusGroup.campusName"
            :label="campusGroup.campusName"
          >
            <el-option
              v-for="group in campusGroup.classes"
              :key="group.key"
              :label="rosterPrintOptionLabel(group)"
              :value="group.key"
            >
              <div class="roster-print-picker__option">
                <strong>{{ group.className }}</strong>
                <span>{{ group.projectName }} · {{ group.students.length }} students</span>
              </div>
            </el-option>
          </el-option-group>
        </el-select>
        <span v-if="selectedRosterPrintGroup" class="roster-print-picker__count">
          {{ selectedRosterPrintGroup.students.length }} students will be printed
        </span>
      </div>
      <template #footer>
        <el-button @click="rosterPrintDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :disabled="!selectedRosterPrintGroup" @click="confirmPrintClassRoster">
          Print
        </el-button>
      </template>
    </el-dialog>
    <div class="roster-print" aria-hidden="true">
      <section
        v-for="group in selectedRosterPrintGroups"
        :key="group.key"
        class="roster-print__page"
        :style="rosterPrintPageStyle(group.students.length)"
      >
        <div class="roster-print__content">
          <h1>{{ group.projectName }}</h1>
          <h2>{{ group.className }}<span v-if="group.teacherNames.length"> — {{ group.teacherNames.join(', ') }}</span></h2>
          <p class="roster-print__campus">{{ group.campusName }}</p>
          <table>
            <thead>
              <tr><th>Student</th><th>School</th></tr>
            </thead>
            <tbody>
              <tr v-for="student in group.students" :key="student.key">
                <td>{{ student.name }}</td>
                <td>{{ student.school }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
    <div ref="workbookScrollRef" class="workbook-scroll" :class="{ 'workbook-scroll--pinch-zoom': isMobileViewport }" data-testid="data-summary-workbook">
      <table class="workbook-table" :style="{ zoom: isMobileViewport ? mobileTableScale : undefined }">
        <colgroup>
          <col v-for="column in columns" :key="column.key" :style="{ width: `${column.width}px` }" />
        </colgroup>

        <thead>
          <tr>
            <th
              v-for="column in columns"
              :key="column.key"
              :class="[
                `workbook-column--${column.key}`,
                { 'workbook-header--active': hasColumnFilter(column.key) },
              ]"
            >
              <el-popover
                placement="bottom-start"
                trigger="click"
                :width="column.filterType === 'date' ? 330 : column.filterType === 'number' ? 300 : 260"
                popper-class="workbook-filter-popper"
              >
                <template #reference>
                  <button
                    class="workbook-header-button"
                    type="button"
                    :aria-label="`Filter ${column.label}`"
                  >
                    <span>{{ column.label }}</span>
                    <span class="workbook-header-icons">
                      <span v-if="sortState.key === column.key" class="workbook-sort-indicator">
                        {{ sortState.direction === 'asc' ? '↑' : '↓' }}
                      </span>
                      <span class="workbook-filter-arrow" aria-hidden="true" />
                    </span>
                  </button>
                </template>

                <div class="workbook-filter">
                  <strong>Filter {{ column.label }}</strong>

                  <el-input
                    v-if="column.filterType === 'text'"
                    v-model="columnFilters[column.key].text"
                    clearable
                    :placeholder="`Search ${column.label}`"
                  />

                  <el-select
                    v-else-if="column.filterType === 'select'"
                    v-model="columnFilters[column.key].selected"
                    multiple
                    clearable
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="All"
                  >
                    <el-option
                      v-for="option in selectOptions[column.key]"
                      :key="option"
                      :label="option"
                      :value="option"
                    />
                  </el-select>

                  <el-date-picker
                    v-else-if="column.filterType === 'date'"
                    v-model="columnFilters[column.key].dateRange"
                    type="daterange"
                    start-placeholder="From"
                    end-placeholder="To"
                    range-separator="-"
                    value-format="YYYY-MM-DD"
                    unlink-panels
                  />

                  <div class="workbook-filter-section">
                    <span class="workbook-filter-label">Sort</span>
                    <el-button-group class="workbook-sort-buttons">
                      <el-button
                        size="small"
                        :type="isColumnSorted(column.key, 'asc') ? 'primary' : 'default'"
                        @click="setSort(column.key, 'asc')"
                      >
                        Asc
                      </el-button>
                      <el-button
                        size="small"
                        :type="isColumnSorted(column.key, 'desc') ? 'primary' : 'default'"
                        @click="setSort(column.key, 'desc')"
                      >
                        Desc
                      </el-button>
                      <el-button size="small" @click="clearSort(column.key)">Clear</el-button>
                    </el-button-group>
                  </div>

                  <div v-if="column.filterType === 'number'" class="workbook-filter-section">
                    <span class="workbook-filter-label">Range</span>
                    <div class="workbook-number-filter">
                      <el-input-number
                        v-model="columnFilters[column.key].min"
                        :controls="false"
                        placeholder="Min"
                      />
                      <span>to</span>
                      <el-input-number
                        v-model="columnFilters[column.key].max"
                        :controls="false"
                        placeholder="Max"
                      />
                    </div>
                  </div>

                  <div class="workbook-filter-actions">
                    <el-button text size="small" @click="clearColumnFilter(column.key)">Clear column</el-button>
                    <el-button text size="small" @click="clearAllFilters">Clear all</el-button>
                  </div>
                </div>
              </el-popover>
            </th>
          </tr>
        </thead>

        <tbody v-if="groupedCampusRows.length">
          <template v-for="(campusGroup, campusIndex) in groupedCampusRows" :key="campusGroup.key">
            <tr v-if="campusIndex > 0" class="workbook-group-spacer" aria-hidden="true">
              <td :colspan="columns.length" />
            </tr>
            <template v-for="classGroup in campusGroup.classGroups" :key="classGroup.key">
              <tr
                v-for="(row, rowIndex) in classGroup.rows"
                :key="rowKey(row)"
                class="workbook-data-row"
                :class="{
                  'workbook-data-row--find-match': isStudentFindMatch(row),
                  'workbook-data-row--find-current': isCurrentStudentFindMatch(row),
                }"
                :data-student-find-key="rowKey(row)"
              >
                <td
                  v-if="classGroup.firstRowIndex + rowIndex === 0"
                  class="workbook-column--campus workbook-cell--group workbook-cell--merged"
                  :rowspan="campusGroup.rowspan"
                >
                  {{ campusGroup.campusName }}
                </td>
                <td
                  v-if="rowIndex === 0"
                  class="workbook-column--studentClass workbook-cell--group workbook-cell--center workbook-cell--merged"
                  :rowspan="classGroup.rows.length"
                >
                  {{ classGroup.className }}
                </td>
                <td
                  class="workbook-column--studentName workbook-student-cell"
                  @contextmenu.prevent.stop="openStudentContextMenu($event, row)"
                >
                  <span class="workbook-student-name">
                    {{ displayText(row.studentName) }}
                  </span>
                </td>
                <td>{{ displayText(row.school) }}</td>
                <td class="workbook-cell--center">{{ displayText(row.grade) }}</td>
                <td class="workbook-cell--center">{{ formatDate(row.signupDate) }}</td>
                <td>{{ formatUsPhoneDisplay(row.phone) || '—' }}</td>
                <td>{{ formatUsPhoneDisplay(row.phone2) || '—' }}</td>
                <td class="workbook-cell--center">
                  <span :class="['workbook-pill', row.pickupRequired ? 'workbook-pill--yes' : 'workbook-pill--no']">
                    {{ row.pickupRequired ? 'Yes' : 'No' }}
                  </span>
                </td>
                <td class="workbook-cell--center">
                  <span :class="['workbook-pill', row.dropoffRequired ? 'workbook-pill--yes' : 'workbook-pill--no']">
                    {{ row.dropoffRequired ? 'Yes' : 'No' }}
                  </span>
                </td>
                <td class="workbook-cell--center">
                  <span :class="['workbook-pill', paymentMethodClass(row.paymentMethod)]">
                    {{ displayText(row.paymentMethod) }}
                  </span>
                </td>
                <td class="workbook-cell--center">
                  {{ attendanceLabelRow(row) }}
                </td>
                <td class="workbook-cell--center" :class="{ 'workbook-cell--renewal-due': isPeriodRenewalDue(row) }">
                  {{ formatPeriod(row) }}
                </td>
                <td v-if="!isManager" class="workbook-cell--money">{{ formatMoney(row.linePriceAmount) }}</td>
                <td v-if="!isManager" class="workbook-cell--money">{{ formatMoney(row.pickupFeeAmount) }}</td>
                <td v-if="!isManager" class="workbook-cell--money">{{ formatMoney(row.dropoffFeeAmount) }}</td>
                <td v-if="!isManager" class="workbook-cell--money">{{ formatMoney(row.discountAmount) }}</td>
                <td v-if="!isManager" class="workbook-cell--money">{{ formatMoney(row.paidAmount) }}</td>
                <td
                  v-if="!isManager"
                  class="workbook-cell--money"
                  :class="{ 'workbook-cell--balance-due': Number(row.balanceAmount ?? 0) < 0 }"
                >
                  {{ formatMoney(row.balanceAmount) }}
                </td>
              </tr>
            </template>
          </template>
        </tbody>

        <tbody v-if="visibleRefundRows.length">
          <tr class="workbook-section-row" data-testid="refund-section-row">
            <td :colspan="columns.length">Refund Student</td>
          </tr>
          <tr
            v-for="row in visibleRefundRows"
            :key="rowKey(row)"
            class="workbook-data-row workbook-data-row--refund"
          >
            <td v-for="column in columns" :key="column.key" :class="stoppedCellClass(column.key)">
              <span v-if="column.key === 'period'">{{ formatPeriod(row) }}</span>
              <span v-else-if="column.key === 'signupDate'">{{ formatDate(row.signupDate) }}</span>
              <span v-else-if="column.key === 'phone'">{{ formatUsPhoneDisplay(row.phone) || '—' }}</span>
              <span v-else-if="column.key === 'phone2'">{{ formatUsPhoneDisplay(row.phone2) || '—' }}</span>
              <span v-else-if="column.key === 'pickup' || column.key === 'dropoff'" :class="['workbook-pill', columnValue(row, column.key) === 'Yes' ? 'workbook-pill--yes' : 'workbook-pill--no']">
                {{ columnValue(row, column.key) }}
              </span>
              <span v-else-if="column.key === 'paymentMethod'" :class="['workbook-pill', paymentMethodClass(row.paymentMethod)]">
                {{ displayText(row.paymentMethod) }}
              </span>
              <span v-else-if="isMoneyColumn(column.key)">{{ formatMoney(columnValue(row, column.key)) }}</span>
              <span v-else>{{ displayText(columnValue(row, column.key)) }}</span>
            </td>
          </tr>
        </tbody>

        <tbody v-if="!loading && !groupedCampusRows.length && !visibleRefundRows.length">
          <tr class="workbook-empty-row">
            <td :colspan="columns.length">
              <span>{{ emptyMessage }}</span>
              <el-button v-if="hasAnyFilter" text type="primary" @click="clearAllFilters">
                Clear all filters
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <section v-if="isMobileViewport" class="workbook-mobile-list" aria-label="Data summary records">
      <div v-if="sortedRows.length" class="workbook-mobile-list__section">
        <div class="workbook-mobile-list__heading">
          <strong>Active registrations</strong>
          <span>{{ sortedRows.length }}</span>
        </div>
        <article v-for="row in sortedRows" :key="`mobile-${rowKey(row)}`" class="workbook-mobile-card">
          <div class="workbook-mobile-card__header">
            <div class="workbook-mobile-card__identity">
              <strong>{{ displayText(row.studentName) }}</strong>
              <span>{{ displayText(displayClassName(row)) }} · {{ displayText(row.campusName) }}</span>
            </div>
            <button
              type="button"
              class="workbook-mobile-card__action"
              :aria-label="`Actions for ${displayText(row.studentName)}`"
              @click.stop="openStudentContextMenu($event, row)"
            >
              <el-icon><MoreFilled /></el-icon>
            </button>
          </div>
          <div class="workbook-mobile-card__summary">
            <div>
              <span>Paid</span>
              <strong>{{ formatMoney(row.paidAmount) }}</strong>
            </div>
            <div :class="{ 'workbook-mobile-card__metric--due': Number(row.balanceAmount ?? 0) < 0 }">
              <span>Balance</span>
              <strong>{{ formatMoney(row.balanceAmount) }}</strong>
            </div>
            <div>
              <span>Period</span>
              <strong>{{ formatPeriod(row) }}</strong>
            </div>
          </div>
          <dl class="workbook-mobile-card__details">
            <div><dt>School</dt><dd>{{ displayText(row.school) }}</dd></div>
            <div><dt>Grade</dt><dd>{{ displayText(row.grade) }}</dd></div>
            <div><dt>Phone</dt><dd>{{ formatUsPhoneDisplay(row.phone) || '—' }}</dd></div>
            <div><dt>Pickup / drop-off</dt><dd>{{ row.pickupRequired ? 'Pickup' : 'No pickup' }} · {{ row.dropoffRequired ? 'Drop-off' : 'No drop-off' }}</dd></div>
          </dl>
        </article>
      </div>

      <div v-if="visibleRefundRows.length" class="workbook-mobile-list__section">
        <div class="workbook-mobile-list__heading workbook-mobile-list__heading--refund">
          <strong>Refund students</strong>
          <span>{{ visibleRefundRows.length }}</span>
        </div>
        <article v-for="row in visibleRefundRows" :key="`mobile-refund-${rowKey(row)}`" class="workbook-mobile-card workbook-mobile-card--refund">
          <div class="workbook-mobile-card__header">
            <div class="workbook-mobile-card__identity">
              <strong>{{ displayText(row.studentName) }}</strong>
              <span>{{ displayText(displayClassName(row)) }} · {{ displayText(row.campusName) }}</span>
            </div>
            <button
              type="button"
              class="workbook-mobile-card__action"
              :aria-label="`Actions for ${displayText(row.studentName)}`"
              @click.stop="openStudentContextMenu($event, row)"
            >
              <el-icon><MoreFilled /></el-icon>
            </button>
          </div>
          <div class="workbook-mobile-card__summary">
            <div><span>Paid</span><strong>{{ formatMoney(row.paidAmount) }}</strong></div>
            <div><span>Balance</span><strong>{{ formatMoney(row.balanceAmount) }}</strong></div>
            <div><span>Period</span><strong>{{ formatPeriod(row) }}</strong></div>
          </div>
        </article>
      </div>

      <div v-if="!sortedRows.length && !visibleRefundRows.length" class="workbook-mobile-empty">
        <strong>{{ emptyMessage }}</strong>
        <el-button v-if="hasAnyFilter" text type="primary" @click="clearAllFilters">Clear all filters</el-button>
      </div>
    </section>
    <section v-if="visibleStoppedRows.length" class="stopped-records">
      <div class="stopped-records__heading">
        <h3>Stopped Records</h3>
        <span>{{ visibleStoppedRows.length }} record(s)</span>
      </div>
      <div class="stopped-records__scroll">
        <table class="stopped-records__table workbook-table">
          <thead>
            <tr>
              <th v-for="column in columns" :key="column.key" :style="{ minWidth: `${column.width}px` }">
                {{ column.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visibleStoppedRows" :key="rowKey(row)">
              <td v-for="column in columns" :key="column.key" :class="stoppedCellClass(column.key)">
                <span v-if="column.key === 'period'">{{ formatPeriod(row) }}</span>
                <span v-else-if="column.key === 'signupDate'">{{ formatDate(row.signupDate) }}</span>
                <span v-else-if="column.key === 'phone'">{{ formatUsPhoneDisplay(row.phone) || '—' }}</span>
                <span v-else-if="column.key === 'phone2'">{{ formatUsPhoneDisplay(row.phone2) || '—' }}</span>
                <span v-else-if="column.key === 'pickup' || column.key === 'dropoff'" :class="['workbook-pill', stoppedColumnValue(row, column.key) === 'Yes' ? 'workbook-pill--yes' : 'workbook-pill--no']">
                  {{ stoppedColumnValue(row, column.key) }}
                </span>
                <span v-else-if="column.key === 'paymentMethod'" :class="['workbook-pill', paymentMethodClass(row.paymentMethod)]">
                  {{ displayText(row.paymentMethod) }}
                </span>
                <span v-else-if="isMoneyColumn(column.key)">{{ formatMoney(stoppedColumnValue(row, column.key)) }}</span>
                <span v-else>{{ displayText(stoppedColumnValue(row, column.key)) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="visibleRefundRows.length" class="refund-records">
      <div class="refund-records__heading">
        <h3>Refund Student</h3>
        <span>{{ visibleRefundRows.length }} record(s)</span>
      </div>
      <div class="refund-records__scroll">
        <table class="refund-records__table workbook-table">
          <thead>
            <tr>
              <th v-for="column in columns" :key="column.key" :style="{ minWidth: `${column.width}px` }">
                {{ column.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visibleRefundRows" :key="rowKey(row)">
              <td v-for="column in columns" :key="column.key" :class="stoppedCellClass(column.key)">
                <span v-if="column.key === 'period'">{{ formatPeriod(row) }}</span>
                <span v-else-if="column.key === 'signupDate'">{{ formatDate(row.signupDate) }}</span>
                <span v-else-if="column.key === 'phone'">{{ formatUsPhoneDisplay(row.phone) || '—' }}</span>
                <span v-else-if="column.key === 'phone2'">{{ formatUsPhoneDisplay(row.phone2) || '—' }}</span>
                <span v-else-if="column.key === 'pickup' || column.key === 'dropoff'" :class="['workbook-pill', columnValue(row, column.key) === 'Yes' ? 'workbook-pill--yes' : 'workbook-pill--no']">
                  {{ columnValue(row, column.key) }}
                </span>
                <span v-else-if="column.key === 'paymentMethod'" :class="['workbook-pill', paymentMethodClass(row.paymentMethod)]">
                  {{ displayText(row.paymentMethod) }}
                </span>
                <span v-else-if="isMoneyColumn(column.key)">{{ formatMoney(columnValue(row, column.key)) }}</span>
                <span v-else>{{ displayText(columnValue(row, column.key)) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div
      v-if="studentContextMenu.visible"
      class="student-context-menu"
      role="menu"
      :style="{ left: `${studentContextMenu.x}px`, top: `${studentContextMenu.y}px` }"
      @click.stop
    >
      <button type="button" role="menuitem" @click="viewStudentReceipt">
        View receipt
      </button>
      <button
        v-if="canPayFromDataSummary(studentContextMenu.row)"
        type="button"
        role="menuitem"
        @click="openOrderAction('pay')"
      >
        Pay
      </button>
      <button
        v-if="!isManager && studentContextMenu.row?.orderPaymentStatus !== 'REFUNDED'"
        type="button"
        role="menuitem"
        :disabled="refundPreviewStatus === 'checking' || refundPreviewStatus === 'none' || refundPreviewStatus === 'review'"
        @click="openOrderAction('refund')"
      >
        {{ refundPreviewStatus === 'checking' ? 'Checking refund…'
          : refundPreviewStatus === 'review' ? 'Review payment allocation'
          : refundPreviewStatus === 'none' ? 'No payment to refund'
          : 'Refund' }}
      </button>
    </div>

    <nav
      v-if="tabs.length"
      class="product-tab-bar"
      data-testid="product-tab-bar"
      aria-label="Course product tabs"
    >
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        class="product-tab"
        :class="{
          'product-tab--active': tab.id === activeTabId,
          'product-tab--global': tab.global,
          'product-tab--unassigned': tab.unassigned,
          'product-tab--private': tab.privateLesson,
          'product-tab--dragging': dragState.fromId === tab.id,
          'product-tab--drop-target': dragState.overId === tab.id && dragState.fromId !== tab.id,
        }"
        :draggable="canMoveProductTab(tab)"
        :title="tab.global ? 'All registration data' : tab.unassigned ? 'Rows not linked to a current course product' : tab.label"
        :data-testid="`product-tab-${tab.id}`"
        :aria-current="tab.id === activeTabId ? 'page' : undefined"
        @click="selectTab(tab.id)"
        @dragstart="handleDragStart(tab, $event)"
        @dragenter.prevent="handleDragEnter(tab)"
        @dragover.prevent="handleDragOver(tab)"
        @drop.prevent="handleDrop(tab)"
        @dragend="handleDragEnd"
      >
        <span class="product-tab__label">{{ tab.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { ArrowDown, ArrowUp, Close, MoreFilled, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import * as XLSX from 'xlsx';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import { fetchDataSummary, type DataSummaryRow } from '@/api/data-summary';
import { fetchRefundPreview } from '@/api/enrollment';
import { fetchAllCourseProducts, saveCourseProductTabOrder } from '@/api/base-data';
import { extractErrorMessage } from '@/api/http';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { isAdminUser, isManagerUser } from '@/types/auth';
import type { CourseProductItem } from '@/types/domain';
import { useRouter } from 'vue-router';
import { formatUsPhoneDisplay, matchesPhoneSearch } from '@/utils/phone-format';
import { formatRoundedUsd } from '@/utils/money-display';
import { countUnclassifiedSaturdayRows, filterSaturdayRosterRows } from '@/utils/saturday-roster';

type ColumnKey =
  | 'campus'
  | 'studentClass'
  | 'studentName'
  | 'school'
  | 'grade'
  | 'signupDate'
  | 'phone'
  | 'phone2'
  | 'pickup'
  | 'dropoff'
  | 'paymentMethod'
  | 'attendance'
  | 'period'
  | 'tuition'
  | 'pickupFee'
  | 'dropoffFee'
  | 'discount'
  | 'paid'
  | 'balance';

type FilterType = 'text' | 'select' | 'date' | 'number';

interface WorkbookColumn {
  key: ColumnKey;
  label: string;
  width: number;
  filterType: FilterType;
}

interface ColumnFilter {
  text: string;
  selected: string[];
  dateRange: [string, string] | null;
  min?: number;
  max?: number;
}

type SortDirection = 'asc' | 'desc';

interface SortState {
  key: ColumnKey | null;
  direction: SortDirection;
}

interface WorkbookClassGroup {
  key: string;
  className: string;
  firstRowIndex: number;
  rows: DataSummaryDisplayRow[];
}

interface WorkbookCampusGroup {
  key: string;
  campusName: string;
  rowspan: number;
  classGroups: WorkbookClassGroup[];
}

interface RosterPrintGroup {
  key: string;
  projectName: string;
  className: string;
  campusName: string;
  teacherNames: string[];
  students: Array<{ key: string; name: string; school: string }>;
}

interface RosterPrintCampusGroup {
  campusName: string;
  classes: RosterPrintGroup[];
}

/** 每个 Product Tab 独立保留的视图状态（月份、筛选、排序）。 */
interface ProductViewState {
  selectedMonth: string;
  saturdayRosterView: SaturdayRosterView;
  columnFilters: Record<ColumnKey, ColumnFilter>;
  sortKey: ColumnKey | null;
  sortDirection: SortDirection;
}

interface ProductTab {
  id: string;
  label: string;
  global?: boolean;
  unassigned?: boolean;
  privateLesson?: boolean;
}

type SaturdayRosterView = 'FALL' | 'SPRING' | 'ALL';
type DataSummaryDisplayRow = DataSummaryRow;

const authStore = useAuthStore();
const appStore = useAppStore();
const isMobileViewport = computed(() => appStore.isMobileViewport);
const isManager = isManagerUser(authStore.currentUser);

const saturdayRosterOptions: Array<{ value: SaturdayRosterView; label: string }> = [
  { value: 'FALL', label: 'Fall roster' },
  { value: 'SPRING', label: 'Spring roster' },
  { value: 'ALL', label: 'All roster' },
];

/** 全局工作表页签固定在最左，展示全部报名数据。 */
const ALL_TAB_ID = '__ALL__';
/** 未关联到当前有效产品的历史行兜底页签，固定在末尾且不可拖拽。 */
const UNASSIGNED_TAB_ID = '__UNASSIGNED__';
const ORDER_SAVE_DEBOUNCE_MS = 300;
const MIN_MOBILE_TABLE_SCALE = 0.3;
const MAX_MOBILE_TABLE_SCALE = 1.6;
const CLASS_SORT_ORDER = ['G1', 'G2', 'G3', 'G4', 'G5', 'G6', 'G7', 'G8', 'ELT', 'SHSAT', 'PSAT', 'SAT', 'ESL', 'TBD'];

interface PinchZoomGesture {
  startDistance: number;
  startScale: number;
  contentX: number;
  contentY: number;
}

let pinchZoomGesture: PinchZoomGesture | null = null;
let pinchZoomFrame = 0;
let pendingPinchPosition: {
  scale: number;
  contentX: number;
  contentY: number;
  viewportX: number;
  viewportY: number;
} | null = null;

const allColumns: WorkbookColumn[] = [
  { key: 'campus', label: 'Campus', width: 112, filterType: 'select' },
  { key: 'studentClass', label: 'Class', width: 82, filterType: 'select' },
  { key: 'studentName', label: 'Student Name', width: 150, filterType: 'text' },
  { key: 'school', label: 'School', width: 110, filterType: 'text' },
  { key: 'grade', label: 'Grade', width: 76, filterType: 'select' },
  { key: 'signupDate', label: 'Sign Up Date', width: 116, filterType: 'date' },
  { key: 'phone', label: 'Phone Number', width: 132, filterType: 'text' },
  { key: 'phone2', label: 'Phone Number #2', width: 140, filterType: 'text' },
  { key: 'pickup', label: 'Pick up', width: 84, filterType: 'select' },
  { key: 'dropoff', label: 'Drop off', width: 86, filterType: 'select' },
  { key: 'paymentMethod', label: 'Pay Method', width: 108, filterType: 'select' },
  { key: 'attendance', label: 'AM/PM', width: 96, filterType: 'select' },
  { key: 'period', label: 'Period', width: 176, filterType: 'date' },
  { key: 'tuition', label: 'Tuition', width: 104, filterType: 'number' },
  { key: 'pickupFee', label: 'Pickup', width: 104, filterType: 'number' },
  { key: 'dropoffFee', label: 'Drop-off', width: 104, filterType: 'number' },
  { key: 'discount', label: 'Discount', width: 100, filterType: 'number' },
  { key: 'paid', label: 'Paid', width: 100, filterType: 'number' },
  { key: 'balance', label: 'Balance', width: 104, filterType: 'number' },
];

const columns: WorkbookColumn[] = isManager
  ? allColumns.filter((column) => !['tuition', 'pickupFee', 'dropoffFee', 'discount', 'paid', 'balance'].includes(column.key))
  : allColumns;

const router = useRouter();
const isAdmin = computed(() => isAdminUser(authStore.currentUser));
const canDragTabs = computed(() => isAdmin.value && !savingOrder.value);

const loading = ref(false);
const workbookScrollRef = ref<HTMLDivElement | null>(null);
const mobileTableScale = ref(0.8);
const savingOrder = ref(false);
const rows = ref<DataSummaryRow[]>([]);
const products = ref<CourseProductItem[]>([]);
const activeTabId = ref<string>('');
const rosterPrintDialogVisible = ref(false);
const selectedRosterPrintKey = ref('');
const selectedMonth = ref<string>('ALL');
const saturdayRosterView = ref<SaturdayRosterView>('ALL');
const studentFindOpen = ref(false);
const studentFindQuery = ref('');
const studentFindIndex = ref(0);
const studentFindInputRef = ref<{ focus: () => void } | null>(null);
const sortState = reactive<SortState>({
  key: null,
  direction: 'asc',
});
const columnFilters = reactive<Record<ColumnKey, ColumnFilter>>(
  Object.fromEntries(columns.map((column) => [column.key, createEmptyFilter()])) as Record<ColumnKey, ColumnFilter>,
);

/** Tab 视图状态快照池：切换 Product 时存入/恢复，仅存活于当前页面会话。 */
const tabStates = new Map<string, ProductViewState>();

const dragState = reactive<{ fromId: string | null; overId: string | null }>({
  fromId: null,
  overId: null,
});

const studentContextMenu = reactive<{
  visible: boolean;
  x: number;
  y: number;
  row: DataSummaryDisplayRow | null;
}>({
  visible: false,
  x: 0,
  y: 0,
  row: null,
});
const refundPreviewStatus = ref<'checking' | 'available' | 'none' | 'review' | 'error'>('checking');
let refundPreviewRequestId = 0;

const knownProductIds = computed(() => new Set(products.value.map((product) => String(product.id))));

function isAssignedRow(row: DataSummaryRow) {
  const productId = row.courseProductId == null ? null : String(row.courseProductId);
  return productId != null && knownProductIds.value.has(productId);
}

const productTabs = computed<ProductTab[]>(() =>
  products.value.map((product) => ({
    id: String(product.id),
    label: product.productName,
    privateLesson: product.teachingMode?.toUpperCase() === 'ONE_ON_ONE',
  })),
);

const activeProduct = computed(() =>
  products.value.find((product) => String(product.id) === activeTabId.value),
);

const hasUnassignedRows = computed(() => rows.value.some((row) => !isAssignedRow(row)));

const tabs = computed<ProductTab[]>(() => {
  const courseTabs = hasUnassignedRows.value
    ? [...productTabs.value, { id: UNASSIGNED_TAB_ID, label: 'Unassigned', unassigned: true }]
    : productTabs.value;
  // All Data 保留内部兼容逻辑，但不再作为用户可见页签；默认直接进入课程产品页签。
  return courseTabs;
});

/** 无产品可用时（如产品接口失败且无未分配行）回退为全量展示，保证页面可用。 */
const productRows = computed(() => {
  if (!activeTabId.value || activeTabId.value === ALL_TAB_ID || !tabs.value.some((tab) => tab.id === activeTabId.value)) {
    return rows.value;
  }
  if (activeTabId.value === UNASSIGNED_TAB_ID) {
    return rows.value.filter((row) => !isAssignedRow(row));
  }
  return rows.value.filter((row) => String(row.courseProductId ?? '') === activeTabId.value);
});

const refundedProductRows = computed(() => productRows.value.filter((row) => row.refunded));
const stoppedProductRows = computed(() => productRows.value.filter((row) => !row.refunded && row.itemStatus === 'STOPPED'));
const activeProductRows = computed(() => productRows.value.filter((row) => !row.refunded && row.itemStatus !== 'STOPPED'));
const displayStoppedRows = computed(() => isSaturdayProductTab.value
  ? filterSaturdayRosterRows(stoppedProductRows.value, saturdayRosterView.value)
  : stoppedProductRows.value);
const displayRefundRows = computed(() => isSaturdayProductTab.value
  ? filterSaturdayRosterRows(refundedProductRows.value, saturdayRosterView.value)
  : refundedProductRows.value);
const visibleStoppedRows = computed(() => displayStoppedRows.value.filter((row) =>
  selectedMonth.value === 'ALL' || monthKey(row) === selectedMonth.value,
));
const visibleRefundRows = computed(() => sortDataRows(displayRefundRows.value.filter((row) =>
  (selectedMonth.value === 'ALL' || monthKey(row) === selectedMonth.value)
    && columns.every((column) => matchesColumnFilter(row, column)),
)));

/** 周六班特殊显示的名称回退识别：产品/班级名里出现「周六」或「Saturday」即视为周六班。 */
const SATURDAY_NAME_PATTERN = /saturday|周六/i;

const isSaturdayProductTab = computed(() => {
  const product = activeProduct.value;
  if (!product) {
    // 全量/未分配视图不做周六班特殊显示，避免混合数据误触发拆行。
    return false;
  }
  if (product.courseType?.toUpperCase() === 'SATURDAY') {
    return true;
  }
  // course_product.course_type 无历史维护入口，产品名或行班级名兜底识别。
  if (SATURDAY_NAME_PATTERN.test(product.productName)) {
    return true;
  }
  return productRows.value.some((row) =>
    row.courseType?.toUpperCase() === 'SATURDAY'
    || SATURDAY_NAME_PATTERN.test(row.registerClass ?? '')
    || SATURDAY_NAME_PATTERN.test(row.className ?? '')
    || SATURDAY_NAME_PATTERN.test(row.courseProductName ?? ''));
});

const displayProductRows = computed<DataSummaryDisplayRow[]>(() => {
  if (!isSaturdayProductTab.value) {
    return activeProductRows.value;
  }
  return filterSaturdayRosterRows(activeProductRows.value, saturdayRosterView.value);
});

const unclassifiedSaturdayRowCount = computed(() => isSaturdayProductTab.value
  ? countUnclassifiedSaturdayRows([...activeProductRows.value, ...stoppedProductRows.value])
  : 0);

const selectOptions = computed<Record<ColumnKey, string[]>>(() => {
  return Object.fromEntries(columns.map((column) => {
    if (column.filterType !== 'select') {
      return [column.key, []];
    }
    const values = [...displayProductRows.value, ...displayRefundRows.value]
      .map((row) => selectValue(row, column.key));
    return [column.key, [...new Set(values)].sort(naturalCompare)];
  })) as Record<ColumnKey, string[]>;
});

// Month is derived from the sign-up date so staff can review one month at a time.
function monthKey(row: DataSummaryRow) {
  return row.signupDate ? dayjs(row.signupDate).format('YYYY-MM') : '';
}

const availableMonths = computed(() => {
  const keys = new Set<string>();
  [...displayProductRows.value, ...displayStoppedRows.value, ...displayRefundRows.value].forEach((row) => {
    const key = monthKey(row);
    if (key) {
      keys.add(key);
    }
  });
  return [...keys]
    .sort((left, right) => right.localeCompare(left))
    .map((value) => ({ value, label: dayjs(`${value}-01`).format('MMMM YYYY') }));
});

const monthFilteredRows = computed<DataSummaryDisplayRow[]>(() => {
  if (selectedMonth.value === 'ALL') {
    return displayProductRows.value;
  }
  return displayProductRows.value.filter((row) => monthKey(row) === selectedMonth.value);
});

const filteredRows = computed<DataSummaryDisplayRow[]>(() => monthFilteredRows.value.filter((row) => {
  return columns.every((column) => matchesColumnFilter(row, column));
}));

const monthStats = computed(() => {
  // 汇总跟随月份、课程和表格筛选；退款行按退款后的净收款计入。
  const rowsToSummarize = [...filteredRows.value, ...visibleRefundRows.value];
  return uniqueSourceRows(rowsToSummarize).reduce(
    (acc, row) => {
      acc.count += 1;
      acc.paid += Number(row.paidAmount ?? 0);
      acc.balance += Number(row.balanceAmount ?? 0);
      return acc;
    },
    { count: 0, paid: 0, balance: 0 },
  );
});

const sortedRows = computed<DataSummaryDisplayRow[]>(() => {
  return sortDataRows(filteredRows.value);
});

const studentFindMatches = computed(() => {
  const query = studentFindQuery.value.trim().toLocaleLowerCase();
  if (!query) return [];
  return sortedRows.value.filter((row) => (row.studentName ?? '').toLocaleLowerCase().includes(query));
});

const currentStudentFindMatch = computed(() => studentFindMatches.value[studentFindIndex.value] ?? null);
const studentFindMatchKeys = computed(() => new Set(studentFindMatches.value.map(rowKey)));
const currentStudentFindMatchKey = computed(() => currentStudentFindMatch.value ? rowKey(currentStudentFindMatch.value) : null);

watch(studentFindQuery, () => {
  studentFindIndex.value = 0;
  void nextTick(scrollToCurrentStudentFindMatch);
});

watch(studentFindMatches, (matches) => {
  if (!matches.length) {
    studentFindIndex.value = 0;
    return;
  }
  studentFindIndex.value = Math.min(studentFindIndex.value, matches.length - 1);
  void nextTick(scrollToCurrentStudentFindMatch);
});

function sortDataRows(rows: DataSummaryDisplayRow[]): DataSummaryDisplayRow[] {
  const source = [...rows];
  if (!sortState.key) {
    const oneOnOneGroupNames = oneOnOneSortGroupNames(source);
    return source.sort((left, right) => compareRowsByBusinessGroup(left, right, oneOnOneGroupNames));
  }
  const direction = sortState.direction === 'asc' ? 1 : -1;
  return source.sort((left, right) => {
    const leftValue = sortValue(left, sortState.key!);
    const rightValue = sortValue(right, sortState.key!);
    if (leftValue == null && rightValue == null) return 0;
    if (leftValue == null) return 1;
    if (rightValue == null) return -1;
    if (typeof leftValue === 'number' && typeof rightValue === 'number') {
      return (leftValue - rightValue) * direction;
    }
    return naturalCompare(String(leftValue), String(rightValue)) * direction;
  });
}

/** 不再分页：每个 Tab 一次性渲染全部数据行。 */
const pagedRows = computed(() => sortedRows.value);

const groupedCampusRows = computed<WorkbookCampusGroup[]>(() => {
  const groups = new Map<string, WorkbookCampusGroup>();
  pagedRows.value.forEach((row) => {
    const campusName = displayText(row.campusName);
    const className = displayText(displayClassName(row));
    const campusKey = campusName;
    let campusGroup = groups.get(campusKey);
    if (!campusGroup) {
      campusGroup = {
        key: campusKey,
        campusName,
        rowspan: 0,
        classGroups: [],
      };
      groups.set(campusKey, campusGroup);
    }

    const groupKey = isPrivateLessonRow(row)
      ? `private:${row.privateLessonGroupId ?? `order:${row.orderId}`}`
      : 'shared-class';
    const classKey = `${campusKey}::${className}::${groupKey}`;
    let classGroup = campusGroup.classGroups.find((group) => group.key === classKey);
    if (!classGroup) {
      classGroup = {
        key: classKey,
        className,
        firstRowIndex: campusGroup.rowspan,
        rows: [],
      };
      campusGroup.classGroups.push(classGroup);
    }
    classGroup.rows.push(row);
    campusGroup.rowspan += 1;
  });
  return [...groups.values()];
});

/** 打印名单沿用当前产品、月份和表格筛选结果，并按实际班级汇总。 */
const rosterPrintGroups = computed<RosterPrintGroup[]>(() => {
  const groups = new Map<string, {
    key: string;
    projectName: string;
    className: string;
    campusName: string;
    teacherNames: Set<string>;
    students: Map<string, { key: string; name: string; school: string }>;
  }>();

  uniqueSourceRows(sortedRows.value).forEach((row) => {
    const projectName = row.courseProductName?.trim() || activeProduct.value?.productName || 'Class roster';
    const className = displayText(displayClassName(row));
    const campusName = displayText(row.campusName);
    const classIdentity = row.classId == null ? className : String(row.classId);
    const key = [row.courseProductId ?? projectName, row.campusId ?? campusName, classIdentity].join('::');
    let group = groups.get(key);
    if (!group) {
      group = { key, projectName, className, campusName, teacherNames: new Set(), students: new Map() };
      groups.set(key, group);
    }

    const teacherName = row.teacherName?.trim();
    if (teacherName) group.teacherNames.add(teacherName);
    const studentKey = String(row.studentId ?? `${row.studentName ?? ''}:${row.school ?? ''}`);
    if (!group.students.has(studentKey)) {
      group.students.set(studentKey, {
        key: studentKey,
        name: displayText(row.studentName),
        school: displayText(row.school),
      });
    }
  });

  return [...groups.values()]
    .map((group) => ({
      ...group,
      teacherNames: [...group.teacherNames].sort(naturalCompare),
      students: [...group.students.values()].sort((left, right) => naturalCompare(left.name, right.name)),
    }))
    .sort((left, right) => naturalCompare(left.campusName, right.campusName)
      || naturalCompare(left.className, right.className)
      || naturalCompare(left.projectName, right.projectName));
});

/** 打印选择器先按校区归组，组内按班级和产品名称排序。 */
const rosterPrintCampusGroups = computed<RosterPrintCampusGroup[]>(() => {
  const groups = new Map<string, RosterPrintGroup[]>();
  rosterPrintGroups.value.forEach((group) => {
    const campusName = group.campusName === '—' ? 'Campus not set' : group.campusName;
    const classes = groups.get(campusName) ?? [];
    classes.push(group);
    groups.set(campusName, classes);
  });

  return [...groups.entries()]
    .map(([campusName, classes]) => ({ campusName, classes }))
    .sort((left, right) => naturalCompare(left.campusName, right.campusName));
});

function printClassRoster() {
  if (!rosterPrintGroups.value.length) return;
  selectedRosterPrintKey.value = '';
  rosterPrintDialogVisible.value = true;
}

const selectedRosterPrintGroup = computed(() =>
  rosterPrintGroups.value.find(group => group.key === selectedRosterPrintKey.value) ?? null,
);

const selectedRosterPrintGroups = computed(() =>
  selectedRosterPrintGroup.value ? [selectedRosterPrintGroup.value] : [],
);

function rosterPrintOptionLabel(group: RosterPrintGroup) {
  return [group.className, group.projectName].join(' · ');
}

/** 按名单人数缩放整张打印页，避免同一班级被浏览器拆到下一页。 */
function rosterPrintPageStyle(studentCount: number) {
  const scale = rosterPrintScale(studentCount);
  return {
    '--roster-print-scale': scale.toFixed(3),
    '--roster-print-content-width': `${(100 / scale).toFixed(3)}%`,
  };
}

function rosterPrintScale(studentCount: number) {
  const estimatedHeightMm = 52 + studentCount * 14;
  return Math.min(1, 230 / estimatedHeightMm);
}

async function confirmPrintClassRoster() {
  if (!selectedRosterPrintGroup.value) return;
  rosterPrintDialogVisible.value = false;
  await nextTick();
  window.print();
}

const hasAnyFilter = computed(() => columns.some((column) => hasColumnFilter(column.key)));

const emptyMessage = computed(() => {
  if (activeTabId.value && activeTabId.value !== ALL_TAB_ID) {
    return 'No matching records for this course.';
  }
  return 'No matching records.';
});

function createEmptyFilter(): ColumnFilter {
  return {
    text: '',
    selected: [],
    dateRange: null,
    min: undefined,
    max: undefined,
  };
}

function copyFilter(filter: ColumnFilter): ColumnFilter {
  return {
    text: filter.text,
    selected: [...filter.selected],
    dateRange: filter.dateRange ? ([...filter.dateRange] as [string, string]) : null,
    min: filter.min,
    max: filter.max,
  };
}

function snapshotCurrentState(): ProductViewState {
  return {
    selectedMonth: selectedMonth.value,
    saturdayRosterView: saturdayRosterView.value,
    columnFilters: Object.fromEntries(
      columns.map((column) => [column.key, copyFilter(columnFilters[column.key])]),
    ) as Record<ColumnKey, ColumnFilter>,
    sortKey: sortState.key,
    sortDirection: sortState.direction,
  };
}

function createDefaultState(): ProductViewState {
  return {
    selectedMonth: 'ALL',
    saturdayRosterView: 'ALL',
    columnFilters: Object.fromEntries(
      columns.map((column) => [column.key, createEmptyFilter()]),
    ) as Record<ColumnKey, ColumnFilter>,
    sortKey: null,
    sortDirection: 'asc',
  };
}

function restoreState(state: ProductViewState) {
  selectedMonth.value = state.selectedMonth;
  saturdayRosterView.value = state.saturdayRosterView ?? 'ALL';
  columns.forEach((column) => {
    Object.assign(columnFilters[column.key], copyFilter(state.columnFilters[column.key]));
  });
  sortState.key = state.sortKey;
  sortState.direction = state.sortDirection;
}

async function loadPage() {
  loading.value = true;
  const [productsResult, summaryResult] = await Promise.allSettled([
    fetchAllCourseProducts(),
    fetchDataSummary(),
  ]);

  if (productsResult.status === 'fulfilled') {
    products.value = productsResult.value.data.data ?? [];
    savedOrderProducts = [...products.value];
  } else {
    // 产品列表失败时保留空 Tab 并继续展示汇总，顺序不回写。
    products.value = [];
    ElMessage.error(extractErrorMessage(productsResult.reason));
  }

  if (summaryResult.status === 'fulfilled') {
    rows.value = summaryResult.value.data.data.rows ?? [];
  } else {
    rows.value = [];
    ElMessage.error(extractErrorMessage(summaryResult.reason));
  }

  loading.value = false;
  ensureActiveTab();
}

function ensureActiveTab() {
  if (!tabs.value.length) {
    activeTabId.value = '';
    return;
  }
  if (!tabs.value.some((tab) => tab.id === activeTabId.value)) {
    activeTabId.value = tabs.value[0].id;
    restoreState(createDefaultState());
  }
}

function selectTab(tabId: string) {
  if (tabId === activeTabId.value) {
    return;
  }
  if (activeTabId.value) {
    tabStates.set(activeTabId.value, snapshotCurrentState());
  }
  activeTabId.value = tabId;
  restoreState(tabStates.get(tabId) ?? createDefaultState());
}

function setSaturdayRosterView(view: SaturdayRosterView) {
  saturdayRosterView.value = view;
}

function openStudentContextMenu(event: MouseEvent, row: DataSummaryDisplayRow) {
  const menuWidth = 150;
  const menuHeight = 140;
  studentContextMenu.x = Math.max(8, Math.min(event.clientX, window.innerWidth - menuWidth - 8));
  studentContextMenu.y = Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8));
  studentContextMenu.row = row;
  studentContextMenu.visible = true;
  refundPreviewStatus.value = 'checking';
  const requestId = ++refundPreviewRequestId;
  if (isManager || !row.orderId || row.orderPaymentStatus === 'REFUNDED') return;
  // 右键时只读取当前报名的退款预览；课程可退金额与提交退款使用同一接口口径。
  void fetchRefundPreview(row.orderId, row.participantIndex ?? undefined).then(({ data }) => {
    if (requestId !== refundPreviewRequestId) return;
    const course = row.orderItemId == null ? null : data.data.items.find(
      (item) => String(item.orderItemId) === String(row.orderItemId));
    const refundable = row.orderItemId == null
      ? data.data.items.reduce((total, item) => total + Math.max(0, item.paidAmount), 0)
      : course?.paidAmount ?? 0;
    refundPreviewStatus.value = refundable > 0 ? 'available'
      : (data.data.unassignedPaymentAmount ?? 0) > 0 ? 'review' : 'none';
  }).catch(() => {
    if (requestId === refundPreviewRequestId) refundPreviewStatus.value = 'error';
  });
}

function closeStudentContextMenu() {
  refundPreviewRequestId += 1;
  studentContextMenu.visible = false;
  studentContextMenu.row = null;
}

function viewStudentReceipt() {
  const orderId = studentContextMenu.row?.orderId;
  closeStudentContextMenu();
  if (!orderId) {
    return;
  }
  router.push({ name: 'registration', query: { orderId: String(orderId) } });
}

function canPayFromDataSummary(row: DataSummaryDisplayRow | null) {
  if (!row || row.orderStatus !== 'CONFIRMED') return false;

  // 管理员的 Data Summary 金额字段会被后端脱敏，用未脱敏的余额判断会导致 Pay 入口消失。
  const hasOutstandingBalance = isManager
    ? row.orderPaymentStatus === 'UNPAID' || row.orderPaymentStatus === 'PARTIAL'
    : Number(row.balanceAmount ?? 0) < 0;

  return hasOutstandingBalance;
}

function openOrderAction(action: 'pay' | 'refund') {
  const row = studentContextMenu.row;
  closeStudentContextMenu();
  if (!row?.orderId) {
    return;
  }
  router.push({
    name: 'enrollment-orders',
    query: {
      orderId: String(row.orderId),
      action,
      productName: row.courseProductName || undefined,
      orderItemId: row.orderItemId != null
        ? String(row.orderItemId)
        : undefined,
      courseBalance: action === 'pay' && row.balanceAmount != null
        ? String(Math.abs(Number(row.balanceAmount)))
        : undefined,
      participantIndex: (action === 'pay' || action === 'refund') && row.participantIndex != null
        ? String(row.participantIndex)
        : undefined,
      courseOnly: action === 'refund' ? 'true' : undefined,
    },
  });
}

function openStudentFind() {
  studentFindOpen.value = true;
  void nextTick(() => {
    studentFindInputRef.value?.focus();
  });
}

function closeStudentFind() {
  studentFindOpen.value = false;
  studentFindQuery.value = '';
  studentFindIndex.value = 0;
}

function stepStudentFind(direction: -1 | 1) {
  const count = studentFindMatches.value.length;
  if (!count) return;
  studentFindIndex.value = (studentFindIndex.value + direction + count) % count;
  void nextTick(scrollToCurrentStudentFindMatch);
}

function isStudentFindMatch(row: DataSummaryDisplayRow) {
  return studentFindMatchKeys.value.has(rowKey(row));
}

function isCurrentStudentFindMatch(row: DataSummaryDisplayRow) {
  return currentStudentFindMatchKey.value === rowKey(row);
}

function scrollToCurrentStudentFindMatch() {
  const row = currentStudentFindMatch.value;
  const container = workbookScrollRef.value;
  if (!row || !container) return;
  const key = rowKey(row);
  const target = [...container.querySelectorAll<HTMLElement>('[data-student-find-key]')]
    .find((element) => element.dataset.studentFindKey === key);
  target?.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
}

function handleStudentContextMenuKeydown(event: KeyboardEvent) {
  const target = event.target;
  const isTextEntry = target instanceof HTMLElement
    && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
  const isStudentFindEntry = target instanceof HTMLElement && Boolean(target.closest('.student-find'));
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'f'
      && (!isTextEntry || isStudentFindEntry)) {
    event.preventDefault();
    openStudentFind();
    return;
  }
  if (event.key === 'Escape') {
    closeStudentContextMenu();
    if (studentFindOpen.value && !isTextEntry) closeStudentFind();
  }
}

// ── Tab 拖拽排序（管理员专用，顺序为系统级共享配置） ──

let orderSaveTimer: number | undefined;
/** 最近一次成功保存（或初始加载）的顺序，用于保存失败回滚。 */
let savedOrderProducts: CourseProductItem[] = [];

function handleDragStart(tab: ProductTab, event: DragEvent) {
  if (!canMoveProductTab(tab)) {
    event.preventDefault();
    return;
  }
  dragState.fromId = tab.id;
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', tab.id);
  }
}

function handleDragEnter(tab: ProductTab) {
  if (!dragState.fromId || !canMoveProductTab(tab) || tab.id === dragState.fromId) {
    return;
  }
  dragState.overId = tab.id;
}

function handleDragOver(tab: ProductTab) {
  if (!dragState.fromId || !canMoveProductTab(tab) || tab.id === dragState.fromId) {
    return;
  }
  dragState.overId = tab.id;
}

function handleDrop(tab: ProductTab) {
  const fromId = dragState.fromId;
  const overId = !canMoveProductTab(tab) || tab.id === dragState.fromId ? null : tab.id;
  handleDragEnd();
  if (!fromId || !overId) {
    return;
  }
  const fromIndex = products.value.findIndex((product) => String(product.id) === fromId);
  const toIndex = products.value.findIndex((product) => String(product.id) === overId);
  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) {
    return;
  }
  const next = [...products.value];
  const [moved] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, moved);
  products.value = next;
  scheduleOrderSave();
}

function handleDragEnd() {
  dragState.fromId = null;
  dragState.overId = null;
}

function canMoveProductTab(tab: ProductTab) {
  return canDragTabs.value && !tab.global && !tab.unassigned;
}

/** 连续快速拖拽时合并为最后一次顺序，只保存最终结果。 */
function scheduleOrderSave() {
  window.clearTimeout(orderSaveTimer);
  orderSaveTimer = window.setTimeout(() => {
    void persistTabOrder();
  }, ORDER_SAVE_DEBOUNCE_MS);
}

async function persistTabOrder() {
  if (!products.value.length) {
    return;
  }
  savingOrder.value = true;
  const requested = [...products.value];
  try {
    await saveCourseProductTabOrder(requested.map((product) => product.id));
    savedOrderProducts = requested;
    ElMessage.success('Tab order saved.');
  } catch (error) {
    // 保存失败回滚到最后一次已确认顺序，避免界面与系统配置不一致。
    products.value = [...savedOrderProducts];
    ElMessage.error(extractErrorMessage(error));
  } finally {
    savingOrder.value = false;
  }
}

onBeforeUnmount(() => {
  window.clearTimeout(orderSaveTimer);
  window.removeEventListener('keydown', handleStudentContextMenuKeydown);
  const scroll = workbookScrollRef.value;
  scroll?.removeEventListener('touchstart', handleWorkbookTouchStart);
  scroll?.removeEventListener('touchmove', handleWorkbookTouchMove);
  scroll?.removeEventListener('touchend', handleWorkbookTouchEnd);
  scroll?.removeEventListener('touchcancel', handleWorkbookTouchEnd);
  if (pinchZoomFrame) cancelAnimationFrame(pinchZoomFrame);
});

function matchesColumnFilter(row: DataSummaryDisplayRow, column: WorkbookColumn) {
  const filter = columnFilters[column.key];
  if (column.filterType === 'text') {
    const keyword = filter.text.trim().toLowerCase();
    if (column.key === 'phone' || column.key === 'phone2') {
      return matchesPhoneSearch(String(columnValue(row, column.key) ?? ''), keyword);
    }
    return !keyword || String(columnValue(row, column.key) ?? '').toLowerCase().includes(keyword);
  }
  if (column.filterType === 'select') {
    return filter.selected.length === 0 || filter.selected.includes(selectValue(row, column.key));
  }
  if (column.filterType === 'date') {
    return matchesDateRange(row, column.key, filter.dateRange);
  }

  const value = Number(columnValue(row, column.key) ?? 0);
  return (filter.min == null || value >= filter.min) && (filter.max == null || value <= filter.max);
}

function matchesDateRange(
  row: DataSummaryDisplayRow,
  key: ColumnKey,
  range: [string, string] | null,
) {
  if (!range) {
    return true;
  }
  if (key === 'period') {
    // 1-on-1 的 Period 展示课时而非日期，因此日期范围筛选不匹配这类记录。
    if (isPrivateLessonRow(row)) {
      return false;
    }
    const start = row.periodStartDate || row.periodEndDate;
    const end = row.periodEndDate || row.periodStartDate;
    return Boolean(start && end && start <= range[1] && end >= range[0]);
  }
  const value = String(columnValue(row, key) ?? '');
  return Boolean(value && value >= range[0] && value <= range[1]);
}

function columnValue(row: DataSummaryDisplayRow, key: ColumnKey): string | number | undefined {
  switch (key) {
    case 'campus':
      return row.campusName;
    case 'studentClass':
      return displayClassName(row);
    case 'studentName':
      return row.studentName;
    case 'school':
      return row.school;
    case 'grade':
      return row.grade;
    case 'signupDate':
      return row.signupDate;
    case 'phone':
      return row.phone;
    case 'phone2':
      return row.phone2;
    case 'pickup':
      return row.pickupRequired ? 'Yes' : 'No';
    case 'dropoff':
      return row.dropoffRequired ? 'Yes' : 'No';
    case 'paymentMethod':
      return displayText(row.paymentMethod);
    case 'attendance':
      return attendanceLabelRow(row);
    case 'period':
      if (isPrivateLessonRow(row)) {
        return row.totalHours == null ? undefined : Number(row.totalHours);
      }
      return row.periodStartDate || row.periodLabel;
    case 'tuition':
      return Number(row.linePriceAmount ?? 0);
    case 'pickupFee':
      return Number(row.pickupFeeAmount ?? 0);
    case 'dropoffFee':
      return Number(row.dropoffFeeAmount ?? 0);
    case 'discount':
      return Number(row.discountAmount ?? 0);
    case 'paid':
      return Number(row.paidAmount ?? 0);
    case 'balance':
      return Number(row.balanceAmount ?? 0);
  }
}

function stoppedColumnValue(row: DataSummaryDisplayRow, key: ColumnKey): string | number | undefined {
  switch (key) {
    case 'period':
      return formatPeriod(row);
    case 'tuition':
      return row.stoppedConsumedAmount ?? row.linePriceAmount;
    case 'pickupFee':
    case 'dropoffFee':
    case 'discount':
      return 0;
    default:
      return columnValue(row, key);
  }
}

function isMoneyColumn(key: ColumnKey) {
  return key === 'tuition' || key === 'pickupFee' || key === 'dropoffFee'
    || key === 'discount' || key === 'paid' || key === 'balance';
}

function stoppedCellClass(key: ColumnKey) {
  return {
    'workbook-cell--money': isMoneyColumn(key),
    'workbook-cell--center': ['grade', 'signupDate', 'pickup', 'dropoff', 'paymentMethod', 'attendance', 'period'].includes(key),
  };
}

function selectValue(row: DataSummaryDisplayRow, key: ColumnKey) {
  return String(columnValue(row, key) ?? '—');
}

function hasColumnFilter(key: ColumnKey) {
  const filter = columnFilters[key];
  return Boolean(
    filter.text.trim()
    || filter.selected.length
    || filter.dateRange
    || filter.min != null
    || filter.max != null,
  );
}

function clearColumnFilter(key: ColumnKey) {
  Object.assign(columnFilters[key], createEmptyFilter());
  clearSort(key);
}

function clearAllFilters() {
  columns.forEach((column) => clearColumnFilter(column.key));
  sortState.key = null;
}

function setSort(key: ColumnKey, direction: SortDirection) {
  sortState.key = key;
  sortState.direction = direction;
}

function clearSort(key?: ColumnKey) {
  if (!key || sortState.key === key) {
    sortState.key = null;
  }
}

function isColumnSorted(key: ColumnKey, direction: SortDirection) {
  return sortState.key === key && sortState.direction === direction;
}

function sortValue(row: DataSummaryRow, key: ColumnKey) {
  const value = columnValue(row, key);
  if (value == null || value === '') {
    return undefined;
  }
  return value;
}

function compareRowsByBusinessGroup(
  left: DataSummaryDisplayRow,
  right: DataSummaryDisplayRow,
  oneOnOneGroupNames: Map<string, string>,
) {
  const campusOrder = compareCampus(displayText(left.campusName), displayText(right.campusName));
  if (campusOrder !== 0) return campusOrder;

  const classOrder = compareClassName(displayText(displayClassName(left)), displayText(displayClassName(right)));
  if (classOrder !== 0) return classOrder;

  if (isPrivateLessonRow(left) && isPrivateLessonRow(right)) {
    return naturalCompare(displayText(left.gradeBand), displayText(right.gradeBand))
      || naturalCompare(oneOnOneGroupNames.get(oneOnOneSortGroupKey(left)) ?? displayText(left.studentName),
        oneOnOneGroupNames.get(oneOnOneSortGroupKey(right)) ?? displayText(right.studentName))
      || comparePrivateLessonGroup(left, right)
      || naturalCompare(displayText(left.studentName), displayText(right.studentName))
      || compareClassName(displayText(left.grade), displayText(right.grade))
      || naturalCompare(left.signupDate ?? '', right.signupDate ?? '')
      || naturalCompare(rowKey(left), rowKey(right));
  }

  return naturalCompare(displayText(left.studentName), displayText(right.studentName))
    || comparePrivateLessonGroup(left, right)
    || compareClassName(displayText(left.grade), displayText(right.grade))
    || naturalCompare(left.signupDate ?? '', right.signupDate ?? '')
    || naturalCompare(rowKey(left), rowKey(right));
}

/** 同一 1 对 2/3 组以组内最靠前的学生姓名排序，使组记录相邻且不因成员名字被拆开。 */
function oneOnOneSortGroupNames(source: DataSummaryDisplayRow[]) {
  const namesByGroup = new Map<string, string[]>();
  source.filter(isPrivateLessonRow).forEach((row) => {
    const key = oneOnOneSortGroupKey(row);
    const names = namesByGroup.get(key) ?? [];
    names.push(displayText(row.studentName));
    namesByGroup.set(key, names);
  });

  return new Map([...namesByGroup].map(([key, names]) => [
    key,
    names.sort(naturalCompare)[0] ?? '—',
  ]));
}

function oneOnOneSortGroupKey(row: DataSummaryDisplayRow) {
  return row.privateLessonGroupId != null
    ? `group:${row.privateLessonGroupId}`
    : `row:${rowKey(row)}`;
}

function comparePrivateLessonGroup(left: DataSummaryDisplayRow, right: DataSummaryDisplayRow) {
  const leftKey = isPrivateLessonRow(left)
    ? String(left.privateLessonGroupId ?? `order:${left.orderId}`)
    : '';
  const rightKey = isPrivateLessonRow(right)
    ? String(right.privateLessonGroupId ?? `order:${right.orderId}`)
    : '';
  return naturalCompare(leftKey, rightKey);
}

function compareCampus(left: string, right: string) {
  const leftRank = campusSortRank(left);
  const rightRank = campusSortRank(right);
  if (leftRank !== rightRank) {
    return leftRank - rightRank;
  }
  return naturalCompare(left, right);
}

function campusSortRank(value: string) {
  // 校区业务排序：实体校区互排在前，Online Class(线上/无实体校区)排在其后，不在表中的按字母序兜底。
  // 避免纯字母序把 "Online Class" 挤在实体校区中间（原字母序为 North → Online → South）。
  const normalized = value.trim().toUpperCase();
  const directIndex = CAMPUS_SORT_ORDER.indexOf(normalized);
  if (directIndex >= 0) {
    return directIndex;
  }
  return CAMPUS_SORT_ORDER.length + 1;
}

const CAMPUS_SORT_ORDER = ['SOUTH CAMPUS', 'NORTH CAMPUS', 'ONLINE CLASS'];

function compareClassName(left: string, right: string) {
  const leftRank = classSortRank(left);
  const rightRank = classSortRank(right);
  if (leftRank !== rightRank) {
    return leftRank - rightRank;
  }
  return naturalCompare(left, right);
}

function classSortRank(value: string) {
  const normalized = value.trim().toUpperCase();
  const directIndex = CLASS_SORT_ORDER.indexOf(normalized);
  if (directIndex >= 0) {
    return directIndex;
  }
  const gradeMatch = normalized.match(/^G(\d+)$/);
  if (gradeMatch) {
    return Number(gradeMatch[1]) - 1;
  }
  return CLASS_SORT_ORDER.length + 1;
}

function rowKey(row: DataSummaryDisplayRow) {
  return [
    row.orderId,
    row.orderItemId ?? [row.classId ?? '', row.courseProductId ?? '', row.enrollmentNo ?? ''].join(':'),
    row.studentId ?? row.studentName,
    row.participantIndex ?? '',
  ].join('-');
}

function uniqueSourceRows(sourceRows: DataSummaryDisplayRow[]) {
  const rowsBySource = new Map<string, DataSummaryDisplayRow>();
  sourceRows.forEach((row) => rowsBySource.set(rowKey(row), row));
  return [...rowsBySource.values()];
}

/** 汇总页 Class 只展示报名关联的 actual class，不读取学生档案中的旧 Class 字段。 */
/** 上课形态展示：全天 / AM / PM / 自定义时段；不适用显示 —。 */
function attendanceLabelRow(row: DataSummaryDisplayRow) {
  const option = row.attendanceOption?.toUpperCase();
  if (option === 'FULL_DAY') return 'Full day';
  if (option === 'MORNING') return 'AM';
  if (option === 'AFTERNOON') return 'PM';
  if (option === 'CUSTOM') return displayText(row.timeSlotLabel) || 'Custom';
  return '';
}

function displayClassName(row: DataSummaryRow) {
  if (isPrivateLessonRow(row)) {
    return privateLessonModeLabel(row.privateLessonParticipantCount);
  }
  return row.className || '';
}

function isPrivateLessonRow(row: DataSummaryRow) {
  return row.courseType?.toUpperCase() === 'ONE_ON_ONE';
}

function privateLessonModeLabel(participantCount?: number) {
  const count = Math.min(3, Math.max(1, Number(participantCount) || 1));
  return `1-on-${count}`;
}

function displayText(value?: string) {
  return value?.trim() || '—';
}

function formatDate(value?: string) {
  return value ? dayjs(value).format('M/D/YYYY') : '—';
}

function formatPeriod(row: DataSummaryRow) {
  if (isPrivateLessonRow(row)) {
    return row.totalHours == null ? '—' : `${Number(row.totalHours).toFixed(1)}h`;
  }
  if (!row.periodStartDate && !row.periodEndDate) {
    return displayText(row.periodLabel);
  }
  const start = row.periodStartDate ? dayjs(row.periodStartDate).format('M/D/YYYY') : '—';
  const end = row.periodEndDate ? dayjs(row.periodEndDate).format('M/D/YYYY') : '—';
  return `${start} - ${end}`;
}

function isPeriodRenewalDue(row: DataSummaryRow) {
  if (isPrivateLessonRow(row) || !row.periodEndDate) {
    return false;
  }
  return remainingBusinessDays(row.periodEndDate) <= 7;
}

function remainingBusinessDays(endDateValue: string) {
  const today = dayjs().startOf('day');
  const endDate = dayjs(endDateValue).startOf('day');
  if (!endDate.isValid()) {
    return Number.POSITIVE_INFINITY;
  }
  if (endDate.isBefore(today)) {
    return 0;
  }
  let days = 0;
  let cursor = today;
  while (cursor.isBefore(endDate) || cursor.isSame(endDate, 'day')) {
    const day = cursor.day();
    if (day !== 0 && day !== 6) {
      days += 1;
    }
    cursor = cursor.add(1, 'day');
  }
  return days;
}

function formatMoney(value?: number) {
  return formatRoundedUsd(value);
}

// ─── Excel 导出 ─────────────────────────────────
// 金额列导出为数值，便于在 Excel 中直接求和/计算；其余列导出为展示文本。
const EXPORT_NUMERIC_COLUMNS: ReadonlySet<ColumnKey> = new Set([
  'tuition',
  'pickupFee',
  'dropoffFee',
  'discount',
  'paid',
  'balance',
]);

function exportExcel() {
  const dataRows = sortedRows.value;
  const refundRows = visibleRefundRows.value;
  if (dataRows.length === 0 && refundRows.length === 0) {
    ElMessage.warning('No rows to export.');
    return;
  }
  const aoa: (string | number)[][] = [columns.map((column) => column.label)];
  dataRows.forEach((row) => {
    aoa.push(columns.map((column) => exportCell(row, column.key)));
  });
  if (refundRows.length) {
    aoa.push(['Refund Student']);
    refundRows.forEach((row) => {
      aoa.push(columns.map((column) => exportCell(row, column.key)));
    });
  }
  const sheet = XLSX.utils.aoa_to_sheet(aoa);
  // Excel 保留原始分值用于计算，仅将金额单元格的显示格式设为整数。
  columns.forEach((column, columnIndex) => {
    if (!EXPORT_NUMERIC_COLUMNS.has(column.key)) return;
    [...dataRows, ...refundRows].forEach((_, rowIndex) => {
      const rowOffset = rowIndex + 1 + (refundRows.length && rowIndex >= dataRows.length ? 1 : 0);
      const cell = sheet[XLSX.utils.encode_cell({ r: rowOffset, c: columnIndex })];
      if (cell) cell.z = '$#,##0;-$#,##0';
    });
  });
  sheet['!cols'] = columns.map((column) => ({
    wch: Math.max(8, Math.round(column.width / 6.6)),
  }));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, exportSheetName());
  XLSX.writeFile(workbook, exportFilename());
}

function exportCell(row: DataSummaryRow, key: ColumnKey): string | number {
  const value = columnValue(row, key);
  if (EXPORT_NUMERIC_COLUMNS.has(key)) {
    return Number(value ?? 0);
  }
  if (key === 'period') {
    return formatPeriod(row);
  }
  if (key === 'signupDate') {
    return formatDate(row.signupDate);
  }
  if (value == null) {
    return '—';
  }
  if (key === 'phone' || key === 'phone2') {
    return formatUsPhoneDisplay(String(value)) || '—';
  }
  return String(value).trim() || '—';
}

function exportLabel(): string {
  const monthPart = selectedMonth.value === 'ALL' ? 'AllMonths' : selectedMonth.value;
  if (activeTabId.value && activeTabId.value !== ALL_TAB_ID) {
    const tab = tabs.value.find((item) => item.id === activeTabId.value);
    if (tab) {
      return `${tab.label}_${monthPart}`;
    }
  }
  return `AllData_${monthPart}`;
}

/** 工作表名最长 31 字符，且不能含 []:*?/\。 */
function exportSheetName(): string {
  return exportLabel().replace(/[\\/?*[\]:]/g, '_').slice(0, 31);
}

function exportFilename(): string {
  const date = dayjs().format('YYYYMMDD');
  return `DataSummary_${exportLabel().replace(/[\\/?*[\]:]/g, '_')}_${date}.xlsx`;
}

function paymentMethodClass(value?: string) {
  const method = value?.trim().toLowerCase() ?? '';
  if (!method) return '';
  if (method.includes('cash')) return 'workbook-pill--cash';
  if (method.includes('zelle')) return 'workbook-pill--zelle';
  if (method.includes('check')) return 'workbook-pill--check';
  return 'workbook-pill--method';
}

function touchDistance(touches: TouchList) {
  return Math.hypot(touches[0].clientX - touches[1].clientX, touches[0].clientY - touches[1].clientY);
}

// 移动端两指缩放只作用于工作表，并将手指中心对应的单元格固定在视口中。
function handleWorkbookTouchStart(event: TouchEvent) {
  const scroll = workbookScrollRef.value;
  if (!isMobileViewport.value || !scroll || event.touches.length !== 2) {
    pinchZoomGesture = null;
    return;
  }

  const startDistance = touchDistance(event.touches);
  if (startDistance < 1) {
    pinchZoomGesture = null;
    return;
  }
  const rect = scroll.getBoundingClientRect();
  const viewportX = (event.touches[0].clientX + event.touches[1].clientX) / 2 - rect.left;
  const viewportY = (event.touches[0].clientY + event.touches[1].clientY) / 2 - rect.top;
  pinchZoomGesture = {
    startDistance,
    startScale: mobileTableScale.value,
    contentX: (scroll.scrollLeft + viewportX) / mobileTableScale.value,
    contentY: (scroll.scrollTop + viewportY) / mobileTableScale.value,
  };
}

function scheduleMobileTableScroll() {
  if (pinchZoomFrame) return;
  pinchZoomFrame = requestAnimationFrame(() => {
    const scroll = workbookScrollRef.value;
    const position = pendingPinchPosition;
    if (scroll && position) {
      scroll.scrollLeft = position.contentX * position.scale - position.viewportX;
      scroll.scrollTop = position.contentY * position.scale - position.viewportY;
    }
    pendingPinchPosition = null;
    pinchZoomFrame = 0;
  });
}

function handleWorkbookTouchMove(event: TouchEvent) {
  const scroll = workbookScrollRef.value;
  const gesture = pinchZoomGesture;
  if (!isMobileViewport.value || !scroll || !gesture || event.touches.length !== 2) return;

  event.preventDefault();
  const rect = scroll.getBoundingClientRect();
  const scale = Math.min(
    MAX_MOBILE_TABLE_SCALE,
    Math.max(MIN_MOBILE_TABLE_SCALE, gesture.startScale * touchDistance(event.touches) / gesture.startDistance),
  );
  pendingPinchPosition = {
    scale,
    contentX: gesture.contentX,
    contentY: gesture.contentY,
    viewportX: (event.touches[0].clientX + event.touches[1].clientX) / 2 - rect.left,
    viewportY: (event.touches[0].clientY + event.touches[1].clientY) / 2 - rect.top,
  };
  mobileTableScale.value = scale;
  scheduleMobileTableScroll();
}

function handleWorkbookTouchEnd(event: TouchEvent) {
  if (event.touches.length < 2) pinchZoomGesture = null;
}

function setMobileTableScale(scale: number) {
  const scroll = workbookScrollRef.value;
  const nextScale = Math.min(MAX_MOBILE_TABLE_SCALE, Math.max(MIN_MOBILE_TABLE_SCALE, scale));
  if (!scroll || nextScale === mobileTableScale.value) {
    mobileTableScale.value = nextScale;
    return;
  }

  const viewportX = scroll.clientWidth / 2;
  const viewportY = scroll.clientHeight / 2;
  pendingPinchPosition = {
    scale: nextScale,
    contentX: (scroll.scrollLeft + viewportX) / mobileTableScale.value,
    contentY: (scroll.scrollTop + viewportY) / mobileTableScale.value,
    viewportX,
    viewportY,
  };
  mobileTableScale.value = nextScale;
  scheduleMobileTableScroll();
}

function handleMobileOverflowCommand(command: string) {
  if (command === 'export-excel') exportExcel();
  if (command === 'print-roster') printClassRoster();
  if (command === 'roster-fall') setSaturdayRosterView('FALL');
  if (command === 'roster-spring') setSaturdayRosterView('SPRING');
  if (command === 'roster-all') setSaturdayRosterView('ALL');
}

function naturalCompare(left: string, right: string) {
  return left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' });
}

// Tab 列表变化（产品新增/删除/排序保存失败回滚）后校正当前选中 Tab。
watch(tabs, () => {
  ensureActiveTab();
});

onMounted(() => {
  void loadPage();
  window.addEventListener('keydown', handleStudentContextMenuKeydown);
  const scroll = workbookScrollRef.value;
  scroll?.addEventListener('touchstart', handleWorkbookTouchStart, { passive: true });
  scroll?.addEventListener('touchmove', handleWorkbookTouchMove, { passive: false });
  scroll?.addEventListener('touchend', handleWorkbookTouchEnd, { passive: true });
  scroll?.addEventListener('touchcancel', handleWorkbookTouchEnd, { passive: true });
});
</script>

<style scoped>
.data-summary-page {
  min-width: 0;
  height: calc(100vh - 88px);
  overflow-x: hidden;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.workbook-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 8px 12px;
  border: 1px solid #9eafa7;
  border-bottom: 0;
  background: #f4f8f5;
}

.data-summary-page .workbook-mobile-more {
  width: 40px;
  height: 32px;
  min-height: 32px;
  flex: 0 0 40px;
  padding: 0;
  border: 1px solid #dce5e0;
  border-radius: 10px;
  background: #fff;
  color: #48695b;
  box-shadow: none;
}

.data-summary-page .workbook-mobile-more .el-icon {
  font-size: 17px;
}

.data-summary-page .workbook-mobile-more:hover,
.data-summary-page .workbook-mobile-more:focus-visible {
  border-color: #9cb8aa;
  background: #f3f8f5;
  color: #285b43;
}

.workbook-toolbar__month {
  display: flex;
  align-items: center;
  gap: 8px;
}

.student-find-trigger {
  gap: 4px;
}

.student-find {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 3px 6px;
  border: 1px solid #c7d7cd;
  border-radius: 6px;
  background: #fff;
}

.student-find__input {
  width: 220px;
}

.student-find__count {
  min-width: 38px;
  color: #64756c;
  font-size: 11px;
  text-align: center;
  white-space: nowrap;
}

.student-find__navigation :deep(.el-button) {
  padding: 6px 8px;
}

.workbook-toolbar__label {
  font-size: 12px;
  font-weight: 700;
  color: #376451;
}

.workbook-toolbar__select {
  width: 180px;
}

.workbook-toolbar__stats {
  display: flex;
  align-items: center;
  gap: 20px;
}

.workbook-toolbar__export {
  margin-left: auto;
  align-self: center;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
}

.roster-print {
  display: none;
}

.roster-print-picker {
  display: grid;
  gap: 10px;
}

.roster-print-picker p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.roster-print-picker__select {
  width: 100%;
}

:global(.roster-print-campus-popper .el-select-group__title) {
  height: 30px;
  margin: 4px 8px;
  padding: 0 10px;
  border: 1px solid #d3dfec;
  border-left: 3px solid #648bb5;
  border-radius: 4px;
  background: #edf4fb;
  color: #34465d;
  font-size: 13px;
  font-weight: 700;
  line-height: 28px;
}

:global(.roster-print-campus-popper .el-select-group__wrap:not(:last-of-type)) {
  margin-bottom: 6px;
  padding-bottom: 6px;
  border-bottom: 1px solid #dce5ef;
}

.roster-print-picker__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.roster-print-picker__option strong {
  overflow: hidden;
  color: #243447;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-print-picker__option span,
.roster-print-picker__count {
  color: #7b8794;
  font-size: 12px;
}

.saturday-roster-control {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.saturday-roster-control__hint {
  color: #7b8794;
  font-size: 11px;
}

.workbook-toolbar__stat {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
}

.workbook-toolbar__stat em {
  color: #6b7684;
  font-style: normal;
}

.workbook-toolbar__stat strong {
  font-size: 14px;
  color: #263b44;
}

.workbook-scroll {
  width: 100%;
  height: auto;
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
  border: 1px solid #9eafa7;
  background: #fff;
}

.workbook-scroll--pinch-zoom {
  touch-action: pan-x pan-y;
  -webkit-overflow-scrolling: touch;
}

.stopped-records {
  flex: 0 0 auto;
  max-height: 260px;
  border: 1px solid #d8a36a;
  border-top: 0;
  background: #fffaf3;
}

.stopped-records__heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 10px 12px 6px;
}

.stopped-records__heading h3 {
  margin: 0;
  color: #8f541b;
  font-size: 13px;
}

.stopped-records__heading span {
  color: #8b8176;
  font-size: 11px;
}

.stopped-records__scroll {
  max-height: 215px;
  overflow: auto;
}

.stopped-records__table {
  width: 100%;
  border-collapse: collapse;
  color: #4b463f;
  font-size: 12px;
}

.stopped-records__table th,
.stopped-records__table td {
  padding: 7px 10px;
  border-top: 1px solid #eadbc9;
  text-align: left;
  white-space: nowrap;
}

.stopped-records__table th {
  position: sticky;
  top: 0;
  background: #fff2df;
  font-weight: 700;
}

.workbook-section-row td {
  height: 26px;
  border-top: 2px solid #9eafa7;
  border-right: 0;
  background: #eef4fb;
  color: #3d5d80;
  font-size: 11px;
  font-weight: 700;
}

.workbook-data-row.workbook-data-row--refund td {
  background: #fdf6ec;
}

.workbook-data-row.workbook-data-row--refund:hover td {
  background: #f8ecd9;
}

.workbook-table {
  width: max-content;
  min-width: 100%;
  margin-right: 18px;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
  color: #263b44;
  font-size: 12px;
}

.workbook-table th,
.workbook-table td {
  height: 30px;
  padding: 5px 8px;
  overflow: hidden;
  border-right: 1px solid #c3d3df;
  border-bottom: 1px solid #c3d3df;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbook-table .workbook-header-button {
  justify-content: center;
  text-align: center;
}

.workbook-table th {
  position: sticky;
  z-index: 4;
  top: 0;
  height: 36px;
  padding: 0;
  border-color: #517768;
  background: #376451;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.workbook-table th:last-child,
.workbook-table td:last-child {
  border-right: 0;
}

.workbook-filter {
  display: grid;
  gap: 12px;
}

.workbook-filter > strong {
  color: #303133;
  font-size: 13px;
}

.workbook-filter :deep(.el-select),
.workbook-filter :deep(.el-date-editor) {
  width: 100%;
}

.workbook-filter-section {
  display: grid;
  gap: 6px;
}

.workbook-filter-label {
  color: #606266;
  font-size: 12px;
  font-weight: 600;
}

.workbook-sort-buttons {
  width: 100%;
}

.workbook-sort-buttons :deep(.el-button) {
  flex: 1;
}

.workbook-number-filter {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  color: #909399;
  font-size: 12px;
}

.workbook-number-filter :deep(.el-input-number) {
  width: 100%;
}

.workbook-filter-actions {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid #ebeef5;
  padding-top: 6px;
}

.workbook-data-row td {
  background: #dcebf8;
}

.workbook-data-row:hover td {
  background: #d1e5f6;
}

.workbook-data-row--find-match td {
  background: #fff4c2;
}

.workbook-data-row--find-current td,
.workbook-data-row--find-current:hover td {
  background: #ffe18a;
  box-shadow: inset 0 1px 0 #d59d20, inset 0 -1px 0 #d59d20;
}

.workbook-group-spacer td {
  height: 18px;
  padding: 0;
  border-right: 0;
  border-bottom: 1px solid #9eafa7;
  background: #fff;
}

.workbook-cell--center {
  text-align: center;
}

.workbook-column--studentClass.workbook-cell--group {
  text-align: center;
  vertical-align: middle;
}

.workbook-cell--group {
  color: #1f3f36;
  font-weight: 800;
  text-align: left;
  vertical-align: top;
}

.workbook-cell--merged {
  padding-top: 14px;
  border-right-color: rgba(31, 75, 59, 0.25);
  background: #d9eefd;
}

.workbook-cell--money {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.workbook-student-cell {
  cursor: context-menu;
}

.student-context-menu {
  position: fixed;
  z-index: 3000;
  min-width: 150px;
  padding: 4px;
  border: 1px solid #d8e2ea;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(15 23 42 / 18%);
}

.student-context-menu button {
  display: block;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #1f2937;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.student-context-menu button:hover,
.student-context-menu button:focus-visible {
  background: #eef6ff;
  color: #1d4ed8;
  outline: none;
}

.workbook-cell--balance-due {
  color: #f21b2d;
  font-weight: 700;
}

.workbook-cell--renewal-due {
  color: #f21b2d;
  font-weight: 800;
}

.workbook-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 18px;
  padding: 0 7px;
  border-radius: 9px;
  background: #e9eef2;
  color: #50636f;
  font-size: 10px;
  font-weight: 700;
  line-height: 18px;
}

.workbook-pill--yes,
.workbook-pill--cash {
  background: #2d78ca;
  color: #fff;
}

.workbook-pill--no,
.workbook-pill--zelle {
  background: #e41f2b;
  color: #fff;
}

.workbook-pill--check {
  background: #f8d155;
  color: #775600;
}

.workbook-pill--method {
  background: #528f79;
  color: #fff;
}

.workbook-column--campus {
  position: sticky;
  z-index: 2;
  left: 0;
}

th.workbook-column--campus {
  z-index: 6;
}

.workbook-column--studentClass {
  position: sticky;
  z-index: 2;
  left: 112px;
}

th.workbook-column--studentClass {
  z-index: 6;
}

.workbook-column--studentName {
  position: sticky;
  z-index: 2;
  left: 194px;
  box-shadow: 2px 0 0 rgb(81 119 104 / 25%);
}

th.workbook-column--studentName {
  z-index: 6;
}

.workbook-empty-row td {
  height: 160px;
  background: #f7fbfe;
  color: #606266;
  text-align: center;
}

.workbook-empty-row span {
  margin-right: 8px;
}

.workbook-mobile-list {
  display: none;
}

@media (max-width: 920px) {
  .data-summary-page {
    height: calc(100dvh - 114px);
    min-height: 360px;
  }

  .data-summary-page .workbook-toolbar {
    align-items: center;
    flex-direction: row;
    gap: 6px 10px;
    padding: 6px 8px;
  }

  .data-summary-page .workbook-toolbar__month {
    display: flex;
    flex: 0 0 auto;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }

  .data-summary-page .student-find {
    order: 3;
    flex: 1 1 100%;
    width: 100%;
  }

  .data-summary-page .student-find__input {
    flex: 1 1 auto;
    width: auto;
  }

  .data-summary-page .workbook-toolbar__label {
    font-size: 11px;
  }

  .data-summary-page .workbook-toolbar__select {
    width: 150px;
    min-width: 0;
    flex: 0 1 150px;
  }

  .data-summary-page .workbook-toolbar__stats {
    display: flex;
    flex: 1 1 100%;
    justify-content: space-between;
    gap: 4px;
    width: 100%;
  }

  .data-summary-page .workbook-toolbar__stat {
    gap: 3px;
    font-size: 10px;
  }

  .data-summary-page .workbook-toolbar__stat strong {
    font-size: 11px;
  }

  .data-summary-page .workbook-toolbar__export {
    display: none;
  }

  .data-summary-page .workbook-scroll {
    display: none;
  }

  .data-summary-page .workbook-mobile-list {
    display: grid;
    gap: 14px;
    flex: 1 1 auto;
    min-height: 0;
    padding: 10px 10px 18px;
    overflow: auto;
    background: #f5f8fa;
  }

  .workbook-mobile-list__section {
    display: grid;
    gap: 8px;
  }

  .workbook-mobile-list__heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 2px;
    color: #285b43;
    font-size: 12px;
  }

  .workbook-mobile-list__heading span {
    min-width: 22px;
    padding: 2px 7px;
    border-radius: 999px;
    background: #dcebe3;
    color: #285b43;
    font-size: 11px;
    text-align: center;
  }

  .workbook-mobile-list__heading--refund {
    color: #8f541b;
  }

  .workbook-mobile-list__heading--refund span {
    background: #f8e4c8;
    color: #8f541b;
  }

  .workbook-mobile-card {
    display: grid;
    gap: 11px;
    padding: 13px;
    border: 1px solid #c9d9e2;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 2px 8px rgb(38 59 68 / 6%);
  }

  .workbook-mobile-card--refund {
    border-color: #e6cda9;
    background: #fffaf3;
  }

  .workbook-mobile-card__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  .workbook-mobile-card__identity {
    display: grid;
    min-width: 0;
    gap: 3px;
  }

  .workbook-mobile-card__identity strong {
    overflow: hidden;
    color: #1f3f36;
    font-size: 15px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .workbook-mobile-card__identity span {
    overflow: hidden;
    color: #71808a;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .workbook-mobile-card__action {
    display: inline-flex;
    flex: 0 0 34px;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    padding: 0;
    border: 1px solid #d5e1e7;
    border-radius: 9px;
    background: #f8fbfc;
    color: #48695b;
    cursor: pointer;
  }

  .workbook-mobile-card__action:active,
  .workbook-mobile-card__action:focus-visible {
    border-color: #7da891;
    background: #eef7f1;
    outline: none;
  }

  .workbook-mobile-card__summary {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .workbook-mobile-card__summary > div {
    display: grid;
    gap: 4px;
    min-width: 0;
    padding: 8px;
    border-radius: 8px;
    background: #f3f7f8;
  }

  .workbook-mobile-card__summary span {
    color: #71808a;
    font-size: 10px;
    white-space: nowrap;
  }

  .workbook-mobile-card__summary strong {
    overflow: hidden;
    color: #263b44;
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .workbook-mobile-card__metric--due strong {
    color: #c52b36;
  }

  .workbook-mobile-card__details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px 12px;
    margin: 0;
    padding-top: 10px;
    border-top: 1px solid #e8eef1;
  }

  .workbook-mobile-card__details div {
    min-width: 0;
  }

  .workbook-mobile-card__details dt {
    margin-bottom: 2px;
    color: #87949b;
    font-size: 10px;
  }

  .workbook-mobile-card__details dd {
    overflow: hidden;
    margin: 0;
    color: #40535d;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .workbook-mobile-empty {
    display: grid;
    justify-items: center;
    gap: 6px;
    padding: 42px 16px;
    border: 1px dashed #c6d5dc;
    border-radius: 12px;
    background: #fff;
    color: #657680;
    font-size: 13px;
    text-align: center;
  }

  .data-summary-page .stopped-records {
    max-height: none;
    margin: 0 10px 14px;
    border-top: 1px solid #d8a36a;
    border-radius: 10px;
  }

  .data-summary-page .stopped-records__scroll {
    max-height: 260px;
  }

  .data-summary-page > .product-tab-bar {
    position: sticky;
    bottom: 0;
    z-index: 20;
    padding-top: 5px;
    padding-bottom: 5px;
    border-top: 1px solid #9eafa7;
    background: rgb(220 232 226 / 96%);
    box-shadow: 0 -4px 12px rgb(38 59 68 / 8%);
  }

  .data-summary-page > .product-tab-bar .product-tab {
    min-height: 34px;
  }
}

@media print {
  @page data-summary-roster {
    size: portrait;
    margin: 16mm;
  }

  .data-summary-page * {
    visibility: hidden !important;
  }

  .data-summary-page .roster-print,
  .data-summary-page .roster-print * {
    visibility: visible !important;
  }

  .data-summary-page .roster-print {
    display: block !important;
    position: absolute;
    inset: 0;
    width: 100%;
    page: data-summary-roster;
    color: #111;
    font-family: Arial, sans-serif;
  }

  .data-summary-page .roster-print__page {
    min-height: 250mm;
    break-after: page;
    page-break-after: always;
  }

  .data-summary-page .roster-print__page:last-child {
    break-after: auto;
    page-break-after: auto;
  }

  .data-summary-page .roster-print h1,
  .data-summary-page .roster-print h2 {
    margin: 0;
    text-align: center;
    font-weight: 700;
  }

  .data-summary-page .roster-print h1 {
    padding-top: 8mm;
    font-size: 32pt;
  }

  .data-summary-page .roster-print h2 {
    margin-top: 12mm;
    font-size: 25pt;
  }

  .data-summary-page .roster-print__campus {
    margin: 5mm 0 3mm;
    text-align: center;
    font-size: 10pt;
    color: #555;
  }

  .data-summary-page .roster-print table {
    width: 84%;
    margin: 6mm auto 0;
    border-collapse: collapse;
    table-layout: fixed;
    font-size: 16pt;
  }

  .data-summary-page .roster-print th {
    width: 50%;
    padding: 4mm;
    border: 1px solid #b8c7d4;
    background: #e9f1f8;
    text-align: left;
    font-size: 11pt;
  }

  .data-summary-page .roster-print td {
    width: 50%;
    padding: 4mm;
    border: 1px solid #b8c7d4;
    background: #d2e4f4;
    vertical-align: middle;
  }

}
</style>
