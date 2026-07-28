<template>
  <div class="data-summary-page">
    <el-tabs v-model="activeTab" class="summary-tabs" @tab-change="handleSummaryTabChange">
      <el-tab-pane label="Registration checks" name="checks" />
      <el-tab-pane label="Renewal follow-up" name="renewal" />
    </el-tabs>

    <section class="summary-hero table-page-header">
      <div>
        <span class="summary-hero__eyebrow table-page-header__eyebrow">Data Summary</span>
        <h2>{{ activePageTitle }}</h2>
        <p>{{ activePageDescription }}</p>
      </div>
    </section>

    <template v-if="activeTab === 'checks'">
    <section class="report-summary-strip">
      <article v-for="metric in metrics" :key="metric.label" class="report-summary-strip__item" :class="metric.tone">
        <span>{{ metric.label }}</span>
        <strong>{{ maskedMetricValue(metric.value) }}</strong>
        <small>{{ metric.hint }}</small>
      </article>
      <button
        class="metric-privacy-toggle"
        type="button"
        :aria-label="metricsVisible ? 'Hide summary values' : 'Show summary values'"
        @click="metricsVisible = !metricsVisible"
      >
        <el-icon><component :is="metricsVisible ? Hide : View" /></el-icon>
      </button>
    </section>

    <section class="summary-search-bar" :class="{ 'summary-search-bar--with-advanced': advancedFiltersVisible }">
      <div class="summary-search-bar__main">
        <el-input
          v-model="filters.keyword"
          placeholder="Search student, guardian phone, or course"
          clearable
          @keyup.enter="loadSummary"
          @clear="loadSummary"
        />
        <el-button type="primary" class="summary-search-bar__search" @click="loadSummary">Search</el-button>
      </div>
      <div class="summary-search-bar__actions">
        <el-checkbox v-model="filters.onlyAlerts" @change="loadSummary">Show alerts</el-checkbox>
        <el-button plain class="summary-search-bar__filters" @click="advancedFiltersVisible = !advancedFiltersVisible">
          Filters
          <span v-if="activeAdvancedFilterCount" class="summary-search-bar__filter-count">{{ activeAdvancedFilterCount }}</span>
        </el-button>
        <el-button v-if="hasActiveFilters" text class="summary-search-bar__reset" @click="resetFilters">Clear</el-button>
      </div>
    </section>

    <section v-if="advancedFiltersVisible" class="summary-advanced-filters">
      <el-select v-model="filters.campusId" placeholder="All campuses" clearable filterable @change="loadSummary">
        <el-option v-for="campus in campusOptions" :key="campus.id" :label="campus.campusName" :value="campus.id" />
      </el-select>
      <el-date-picker
        v-model="periodRange"
        type="daterange"
        start-placeholder="Registration from"
        end-placeholder="Registration to"
        value-format="YYYY-MM-DD"
        unlink-panels
        @change="loadSummary"
      />
    </section>

    <section class="workbook-panel table-panel">
      <div class="workbook-panel__header">
        <div>
          <h3>Registration check list</h3>
          <span>{{ workbookRows.length }} matching registration{{ workbookRows.length === 1 ? '' : 's' }}</span>
        </div>
        <span class="check-table-hint">Sorted by attention first</span>
      </div>

      <div v-loading="loading" class="check-table-wrap">
        <div class="check-table">
          <div class="check-table__head check-table__row">
            <el-popover trigger="click" placement="bottom-start" width="190">
              <template #reference>
                <button class="check-header-filter" :class="{ 'check-header-filter--active': filters.courseType }" type="button">
                  <span>Student / course</span>
                  <small>{{ courseTypeFilterLabel }}</small>
                </button>
              </template>
              <div class="check-filter-menu">
                <button type="button" :class="{ active: !filters.courseType }" @click="setCourseTypeFilter('')">All types</button>
                <button
                  v-for="item in courseTypeOptions"
                  :key="item.value"
                  type="button"
                  :class="{ active: filters.courseType === item.value }"
                  @click="setCourseTypeFilter(item.value)"
                >
                  {{ item.label }}
                </button>
              </div>
            </el-popover>
            <el-popover trigger="click" placement="bottom-start" width="210">
              <template #reference>
                <button class="check-header-filter" :class="{ 'check-header-filter--active': issueFilter }" type="button">
                  <span>Key issue</span>
                  <small>{{ issueFilterLabel }}</small>
                </button>
              </template>
              <div class="check-filter-menu">
                <button type="button" :class="{ active: issueFilter === '' }" @click="setIssueFilter('')">All issues</button>
                <button type="button" :class="{ active: issueFilter === 'payment' }" @click="setIssueFilter('payment')">Needs payment</button>
                <button type="button" :class="{ active: issueFilter === 'renewal' }" @click="setIssueFilter('renewal')">Renewal follow-up</button>
                <button type="button" :class="{ active: issueFilter === 'hours' }" @click="setIssueFilter('hours')">Low 1-on-1 hours</button>
                <button type="button" :class="{ active: issueFilter === 'clear' }" @click="setIssueFilter('clear')">No action needed</button>
              </div>
            </el-popover>
            <el-popover trigger="click" placement="bottom-start" width="180">
              <template #reference>
                <button class="check-header-filter" :class="{ 'check-header-filter--active': filters.paymentStatus }" type="button">
                  <span>Payment</span>
                  <small>{{ paymentFilterLabel }}</small>
                </button>
              </template>
              <div class="check-filter-menu">
                <button type="button" :class="{ active: !filters.paymentStatus }" @click="setPaymentFilter('')">All payments</button>
                <button type="button" :class="{ active: filters.paymentStatus === 'UNPAID' }" @click="setPaymentFilter('UNPAID')">Unpaid</button>
                <button type="button" :class="{ active: filters.paymentStatus === 'PARTIAL' }" @click="setPaymentFilter('PARTIAL')">Partial</button>
                <button type="button" :class="{ active: filters.paymentStatus === 'PAID' }" @click="setPaymentFilter('PAID')">Paid</button>
              </div>
            </el-popover>
            <span>Registration</span>
            <span>1-on-1</span>
            <span>Action</span>
          </div>
          <div
            v-for="row in workbookRows"
            :key="checkRowKey(row)"
            class="check-table__row"
            :class="checkRowClass(row)"
          >
            <div class="check-primary">
              <strong>{{ row.studentName || '—' }}</strong>
              <el-tooltip :content="courseLabel(row)" placement="top" :show-after="300">
                <span>{{ courseLabel(row) }}</span>
              </el-tooltip>
              <el-tooltip :content="row.enrollmentNo || '—'" placement="top" :show-after="300">
                <small>{{ row.enrollmentNo || '—' }}</small>
              </el-tooltip>
            </div>
            <el-tooltip :content="keyIssueTooltip(row)" placement="top" :show-after="300">
              <div class="check-issue" :class="`check-issue--${keyIssue(row).tone}`">
                <span class="check-issue__dot" />
                <div>
                  <strong>{{ keyIssue(row).title }}</strong>
                  <small>{{ keyIssue(row).detail }}</small>
                </div>
              </div>
            </el-tooltip>
            <div class="check-stack">
              <strong :class="{ 'money-cell--danger': row.balanceAmount < 0 }">
                {{ row.balanceAmount < 0 ? formatMoney(row.balanceAmount) : 'Paid' }}
              </strong>
              <el-tooltip :content="`${paymentLabel(row.paymentStatus)} · ${row.paymentMethod || 'No method'}`" placement="top" :show-after="300">
                <small>{{ paymentLabel(row.paymentStatus) }} · {{ row.paymentMethod || 'No method' }}</small>
              </el-tooltip>
              <el-tooltip :content="`Paid ${formatMoney(row.paidAmount)} / ${formatMoney(row.amountDue)}`" placement="top" :show-after="300">
                <small>Paid {{ formatMoney(row.paidAmount) }} / {{ formatMoney(row.amountDue) }}</small>
              </el-tooltip>
            </div>
            <div class="check-stack">
              <strong :class="{ 'check-warning': row.expiringSoon }">Ends {{ formatDate(row.periodEndDate) }}</strong>
              <el-tooltip :content="`Signed ${formatDate(row.signupDate)}`" placement="top" :show-after="300">
                <small>Signed {{ formatDate(row.signupDate) }}</small>
              </el-tooltip>
            </div>
            <div v-if="isOneOnOneRow(row)" class="check-stack">
              <strong :class="{ 'check-warning': row.lowOneOnOneHours }">{{ formatHours(row.remainingHours) }}h left</strong>
              <el-tooltip :content="`${formatHours(row.usedHours)}h used / ${formatHours(row.totalHours)}h total`" placement="top" :show-after="300">
                <small>{{ formatHours(row.usedHours) }}h used / {{ formatHours(row.totalHours) }}h total</small>
              </el-tooltip>
            </div>
            <span v-else class="check-muted">—</span>
            <div class="check-action">
              <el-button class="review-link" link type="primary" @click="openReviewDrawer(row)">
                Details
              </el-button>
            </div>
          </div>
          <div v-if="!loading && workbookRows.length === 0" class="check-empty-row">
            <strong>No matching registrations found.</strong>
            <span>Adjust the column filters above or clear filters to see more results.</span>
          </div>
        </div>
      </div>
    </section>

    <el-collapse v-model="summaryDetailPanels" class="summary-detail-collapse">
      <el-collapse-item name="group-summary">
        <template #title>
          <span class="summary-detail-collapse__title">Auxiliary summary</span>
          <span class="summary-detail-collapse__meta">
            {{ summary.campusSummaries.length }} campus groups | {{ summary.courseTypeSummaries.length }} course groups
          </span>
        </template>

        <section class="summary-grid summary-grid--compact">
          <article class="summary-card">
            <div class="summary-card__header">
              <h3>Campuses</h3>
              <span>{{ summary.campusSummaries.length }} groups</span>
            </div>
            <div class="summary-card__list">
              <div v-for="group in summary.campusSummaries" :key="group.groupKey" class="summary-group">
                <div>
                  <strong>{{ group.groupName }}</strong>
                  <span>{{ group.studentCount }} students · {{ group.recordCount }} rows</span>
                </div>
                <div class="summary-group__amount">
                  <span class="money-cell">{{ formatMoney(group.incomeAmount) }}</span>
                  <small :class="{ 'money-cell--danger': group.outstandingAmount < 0 }">
                    Balance {{ formatMoney(group.outstandingAmount) }}
                  </small>
                </div>
              </div>
              <div v-if="summary.campusSummaries.length === 0" class="empty-line empty-state">No campus data.</div>
            </div>
          </article>

          <article class="summary-card">
            <div class="summary-card__header">
              <h3>Course Types</h3>
              <span>{{ summary.courseTypeSummaries.length }} groups</span>
            </div>
            <div class="summary-card__list">
              <div v-for="group in summary.courseTypeSummaries" :key="group.groupKey" class="summary-group">
                <div>
                  <strong>{{ courseTypeLabel(group.groupName) }}</strong>
                  <span>{{ group.studentCount }} students · {{ group.recordCount }} rows</span>
                </div>
                <div class="summary-group__amount">
                  <span class="money-cell">{{ formatMoney(group.incomeAmount) }}</span>
                  <small :class="{ 'money-cell--danger': group.outstandingAmount < 0 }">
                    Balance {{ formatMoney(group.outstandingAmount) }}
                  </small>
                </div>
              </div>
              <div v-if="summary.courseTypeSummaries.length === 0" class="empty-line empty-state">No course type data.</div>
            </div>
          </article>
        </section>
      </el-collapse-item>
    </el-collapse>

    <el-drawer v-model="reviewDrawerVisible" title="Registration details" size="640px">
      <template v-if="reviewRow">
        <section class="review-section">
          <h4>Student and registration</h4>
          <div class="review-grid">
            <span>Student</span><strong>{{ reviewRow.studentName || '—' }}</strong>
            <span>School / grade</span><strong>{{ reviewRow.school || '—' }} · Grade {{ reviewRow.grade || '—' }}</strong>
            <span>Phone</span><strong>{{ [reviewRow.phone, reviewRow.phone2].filter(Boolean).join(' / ') || '—' }}</strong>
            <span>Registration period</span>
            <strong>{{ formatDate(reviewRow.periodStartDate) }} - {{ formatDate(reviewRow.periodEndDate) }}</strong>
            <span>Pickup / dropoff</span><strong>{{ reviewRow.pickupDropoffLabel || 'None' }}</strong>
            <span>Address</span><strong>{{ pickupAddress(reviewRow) }}</strong>
            <span>Course detail</span><strong>{{ reviewRow.detail || '—' }}</strong>
          </div>
        </section>

        <el-skeleton v-if="reviewLoading" :rows="6" animated />
        <template v-else-if="reviewOrder">
          <section class="review-section">
            <div class="review-section__header">
              <div>
                <h4>Order details</h4>
              <span>{{ reviewOrder.studentName }}</span>
              </div>
              <el-button link type="primary" @click="openRegistrationForm">Open registration form</el-button>
            </div>
            <div class="review-grid">
              <span>Order status</span><strong>{{ orderStatusLabel(reviewOrder.orderStatus) }}</strong>
              <span>Amount due</span><strong>{{ formatMoney(reviewOrder.amountDue) }}</strong>
              <span>Received</span><strong>{{ formatMoney(reviewOrder.receivedAmount) }}</strong>
              <span>Balance</span><strong :class="{ 'money-cell--danger': reviewOrder.balanceAmount > 0 }">{{ formatDisplayBalance(reviewOrder.balanceAmount) }}</strong>
            </div>
          </section>

          <section class="review-section">
            <h4>Payment timeline</h4>
            <el-table :data="reviewOrder.payments" size="small" empty-text="No payment records.">
              <el-table-column label="Type" min-width="90">
                <template #default="{ row }">{{ paymentTypeLabel(row.paymentType) }}</template>
              </el-table-column>
              <el-table-column label="Method" min-width="100" prop="paymentMethod" />
              <el-table-column label="Amount" min-width="90" align="right">
                <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="Paid at" min-width="110" prop="paidAt" />
            </el-table>
          </section>

          <section class="review-section">
            <h4>Course packages and hour ledger</h4>
            <div v-if="reviewOrder.packages.length" class="review-package-list">
              <article v-for="coursePackage in reviewOrder.packages" :key="coursePackage.id" class="review-package">
                <div>
                  <strong>{{ coursePackage.packageName }}</strong>
                  <span>Remaining {{ formatHours(coursePackage.remainingUnits) }}h</span>
                </div>
                <div class="review-package__actions">
                  <el-button link type="primary" @click="openPackageDialog(coursePackage.id)">Package details</el-button>
                  <el-button link type="primary" @click="openLedgerDialog(coursePackage.id)">Hour ledger</el-button>
                </div>
              </article>
            </div>
            <span v-else class="review-empty">This order has no 1-on-1 course package.</span>
          </section>
        </template>
        <el-empty v-else description="Order details are unavailable." />
      </template>
    </el-drawer>

    <el-dialog v-model="packageDialogVisible" title="Course package details" width="620px">
      <el-skeleton v-if="packageLoading" :rows="5" animated />
      <div v-else-if="reviewPackage" class="review-grid">
        <span>Package</span><strong>{{ reviewPackage.packageName }}</strong>
        <span>Student</span><strong>{{ reviewPackage.studentName }}</strong>
        <span>Status</span><strong>{{ reviewPackage.packageStatus }}</strong>
        <span>Total hours</span><strong>{{ formatHours(reviewPackage.totalUnits) }}</strong>
        <span>Used hours</span><strong>{{ formatHours(reviewPackage.consumedUnits) }}</strong>
        <span>Remaining hours</span><strong>{{ formatHours(reviewPackage.remainingUnits) }}</strong>
      </div>
      <el-empty v-else-if="!packageLoading" description="Course package details are unavailable." />
    </el-dialog>

    <el-dialog v-model="ledgerDialogVisible" title="Hour ledger" width="760px">
      <el-skeleton v-if="ledgerLoading" :rows="5" animated />
      <el-table v-else :data="ledgerRows" size="small" empty-text="No hour ledger records.">
        <el-table-column label="Date" min-width="100" prop="businessDate" />
        <el-table-column label="Type" min-width="90" prop="ledgerType" />
        <el-table-column label="Change" min-width="90" align="right">
          <template #default="{ row }">{{ formatHours(row.changeUnits) }}h</template>
        </el-table-column>
        <el-table-column label="Balance" min-width="90" align="right">
          <template #default="{ row }">{{ formatHours(row.balanceAfterUnits) }}h</template>
        </el-table-column>
        <el-table-column label="Reason" min-width="180">
          <template #default="{ row }">{{ row.reasonDesc || row.reasonCode || '—' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
    </template>

    <RenewalRemindersView v-else class="summary-renewal-tab" />
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';
import { Hide, View } from '@element-plus/icons-vue';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { fetchCampuses } from '@/api/base-data';
import { fetchDataSummary, type DataSummaryQuery, type DataSummaryResponse, type DataSummaryRow } from '@/api/data-summary';
import { fetchEnrollmentOrder, fetchCoursePackage } from '@/api/enrollment';
import { extractErrorMessage } from '@/api/http';
import { fetchHourLedger, type HourLedgerItem } from '@/api/lesson';
import type { CampusItem, CoursePackageItem, EnrollmentOrderDetailItem, EntityId } from '@/types/domain';
import RenewalRemindersView from '@/views/enrollment/RenewalRemindersView.vue';

const courseTypeOptions = [
  { label: 'After School', value: 'AFTER_SCHOOL' },
  { label: 'Saturday', value: 'SATURDAY' },
  { label: 'Summer', value: 'SUMMER' },
  { label: 'Sprint', value: 'SPRINT' },
  { label: '1 on 1', value: 'ONE_ON_ONE' },
  { label: 'Group', value: 'GROUP' },
];

type KeyIssue = {
  title: string;
  detail: string;
  tone: 'danger' | 'warning' | 'success';
};

type IssueFilter = '' | 'payment' | 'renewal' | 'hours' | 'clear';

const emptySummary: DataSummaryResponse = {
  totalRegistrationCount: 0,
  totalIncomeAmount: 0,
  totalOutstandingAmount: 0,
  campusSummaries: [],
  courseTypeSummaries: [],
  classCourseSummaries: [],
  paymentStatusSummaries: [],
  rows: [],
};

const loading = ref(false);
const campusOptions = ref<CampusItem[]>([]);
const summary = ref<DataSummaryResponse>({ ...emptySummary });
const summaryDetailPanels = ref<string[]>([]);
const advancedFiltersVisible = ref(false);
const periodRange = ref<[string, string] | null>(null);
const route = useRoute();
const router = useRouter();
const activeTab = ref(route.query.tab === 'renewal' ? 'renewal' : 'checks');
const activePageTitle = computed(() =>
  activeTab.value === 'renewal' ? 'Renewal follow-up' : 'Daily registration checks'
);
const activePageDescription = computed(() =>
  activeTab.value === 'renewal'
    ? 'Review students who may need renewal, start a new registration, and optionally send reviewed guardian emails.'
    : 'Check payment balances, expiring registrations, and 1-on-1 renewal risks in one place.'
);
const reviewDrawerVisible = ref(false);
const reviewLoading = ref(false);
const reviewRow = ref<DataSummaryRow | null>(null);
const reviewOrder = ref<EnrollmentOrderDetailItem | null>(null);
const packageDialogVisible = ref(false);
const packageLoading = ref(false);
const reviewPackage = ref<CoursePackageItem | null>(null);
const ledgerDialogVisible = ref(false);
const ledgerLoading = ref(false);
const ledgerRows = ref<HourLedgerItem[]>([]);
const issueFilter = ref<IssueFilter>('');
const metricsVisible = ref(false);
const filters = reactive<DataSummaryQuery>({
  keyword: '',
  campusId: undefined,
  courseType: '',
  paymentStatus: '',
  onlyAlerts: false,
});

const metrics = computed(() => [
  {
    label: 'Students',
    value: String(summary.value.totalRegistrationCount),
    hint: 'Students in current view',
    tone: '',
  },
  {
    label: 'Collected',
    value: formatMoney(summary.value.totalIncomeAmount),
    hint: 'Payment already received',
    tone: '',
  },
  {
    label: 'Unpaid balance',
    value: formatMoney(summary.value.totalOutstandingAmount),
    hint: 'Negative values need follow-up',
    tone: summary.value.totalOutstandingAmount < 0 ? 'metric-card--danger' : '',
  },
  {
    label: 'Follow-up Rows',
    value: String(alertCount.value),
    hint: 'Rows that need attention',
    tone: alertCount.value > 0 ? 'metric-card--warning' : '',
  },
]);

const alertCount = computed(() => summary.value.rows.filter((row) => row.alertTypes?.length).length);
const activeAdvancedFilterCount = computed(() => [
  filters.campusId,
  filters.periodStartDate,
  filters.periodEndDate,
].filter(Boolean).length);
const hasActiveFilters = computed(() => Boolean(
  filters.keyword
  || filters.courseType
  || filters.paymentStatus
  || issueFilter.value
  || filters.onlyAlerts
  || activeAdvancedFilterCount.value,
));
const courseTypeFilterLabel = computed(() => {
  return courseTypeOptions.find((item) => item.value === filters.courseType)?.label || 'All types';
});
const issueFilterLabel = computed(() => {
  const labels: Record<IssueFilter, string> = {
    '': 'All issues',
    payment: 'Needs payment',
    renewal: 'Renewal follow-up',
    hours: 'Low 1-on-1 hours',
    clear: 'No action needed',
  };
  return labels[issueFilter.value];
});
const paymentFilterLabel = computed(() => {
  const labels: Record<string, string> = {
    UNPAID: 'Unpaid',
    PARTIAL: 'Partial',
    PAID: 'Paid',
  };
  return labels[filters.paymentStatus || ''] || 'All payments';
});
const workbookRows = computed(() => summary.value.rows.filter(matchesIssueFilter).sort((left, right) => {
  return rowAttentionPriority(right) - rowAttentionPriority(left)
    || String(left.studentName ?? '').localeCompare(String(right.studentName ?? ''))
    || String(left.enrollmentNo ?? '').localeCompare(String(right.enrollmentNo ?? ''));
}));

watch(periodRange, (value) => {
  filters.periodStartDate = value?.[0];
  filters.periodEndDate = value?.[1];
});

watch(() => route.query.tab, (tab) => {
  activeTab.value = tab === 'renewal' ? 'renewal' : 'checks';
});

function handleSummaryTabChange(tabName: string | number) {
  switchSummaryTab(tabName === 'renewal' ? 'renewal' : 'checks');
}

function switchSummaryTab(tab: 'checks' | 'renewal') {
  activeTab.value = tab;
  router.replace({
    name: 'data-summary',
    query: tab === 'renewal' ? { tab } : {},
  });
}

async function loadSummary() {
  loading.value = true;
  try {
    const response = await fetchDataSummary(normalizedFilters());
    summary.value = response.data.data;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function loadCampuses() {
  try {
    const response = await fetchCampuses();
    campusOptions.value = response.data.data;
  } catch (error) {
    ElMessage.error(`Failed to load campus options: ${extractErrorMessage(error)}`);
  }
}

function normalizedFilters(): DataSummaryQuery {
  return {
    keyword: filters.keyword || undefined,
    campusId: filters.campusId || undefined,
    courseType: filters.courseType || undefined,
    paymentStatus: filters.paymentStatus || undefined,
    periodStartDate: filters.periodStartDate || undefined,
    periodEndDate: filters.periodEndDate || undefined,
    onlyAlerts: filters.onlyAlerts || undefined,
  };
}

function resetFilters() {
  filters.keyword = '';
  filters.campusId = undefined;
  filters.courseType = '';
  filters.paymentStatus = '';
  issueFilter.value = '';
  filters.onlyAlerts = false;
  periodRange.value = null;
  filters.periodStartDate = undefined;
  filters.periodEndDate = undefined;
  loadSummary();
}

function setCourseTypeFilter(value: string) {
  filters.courseType = value;
  loadSummary();
}

function setIssueFilter(value: IssueFilter) {
  issueFilter.value = value;
}

function setPaymentFilter(value: string) {
  filters.paymentStatus = value;
  loadSummary();
}

function checkRowClass(row: DataSummaryRow) {
  const classes: string[] = [];
  if (row.overdue) classes.push('check-row--danger');
  if (!row.overdue && (row.expiringSoon || row.lowOneOnOneHours)) classes.push('check-row--warning');
  return classes.join(' ');
}

function checkRowKey(row: DataSummaryRow) {
  return [row.orderId, row.orderItemId || row.classId || row.courseProductId || row.enrollmentNo].filter(Boolean).join('-');
}

function rowAttentionPriority(row: DataSummaryRow) {
  return Number(row.overdue) * 8
    + Number(row.expiringSoon) * 2
    + Number(row.lowOneOnOneHours);
}

function matchesIssueFilter(row: DataSummaryRow) {
  if (!issueFilter.value) return true;
  if (issueFilter.value === 'payment') return row.overdue;
  if (issueFilter.value === 'renewal') return row.expiringSoon;
  if (issueFilter.value === 'hours') return row.lowOneOnOneHours;
  return !row.overdue && !row.expiringSoon && !row.lowOneOnOneHours;
}

function keyIssue(row: DataSummaryRow): KeyIssue {
  if (row.overdue) {
    return {
      title: 'Collect payment',
      detail: `${formatMoney(Math.abs(row.balanceAmount))} still needs to be paid.`,
      tone: 'danger',
    };
  }
  if (row.expiringSoon) {
    return {
      title: 'Follow up renewal',
      detail: `Registration ends on ${formatDate(row.periodEndDate)}.`,
      tone: 'warning',
    };
  }
  if (row.lowOneOnOneHours) {
    return {
      title: 'Plan next lessons',
      detail: `${formatHours(row.remainingHours)}h left in this 1-on-1 package.`,
      tone: 'warning',
    };
  }
  return {
    title: 'No action needed',
    detail: 'Payment, dates, and hours look normal.',
    tone: 'success',
  };
}

function keyIssueTooltip(row: DataSummaryRow) {
  const issue = keyIssue(row);
  return `${issue.title}: ${issue.detail}`;
}

function isOneOnOneRow(row: DataSummaryRow) {
  return row.totalHours != null;
}

function courseLabel(row: DataSummaryRow) {
  return row.className || row.courseProductName || row.registerClass || '—';
}

function pickupAddress(row: DataSummaryRow) {
  const values: string[] = [];
  if (row.pickupRequired && row.pickupAddress) values.push(`Pick up: ${row.pickupAddress}`);
  if (row.dropoffRequired && row.dropoffAddress) values.push(`Drop off: ${row.dropoffAddress}`);
  return values.length ? values.join(' | ') : '—';
}

function paymentLabel(status?: string) {
  return ({ UNPAID: 'Unpaid', PARTIAL: 'Partial', PAID: 'Paid' } as Record<string, string>)[status || ''] || status || '—';
}

function maskedMetricValue(value: string) {
  return metricsVisible.value ? value : '****';
}

function orderStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Draft',
    CONFIRMED: 'Confirmed',
    CANCELLED: 'Cancelled',
    CLOSED: 'Closed',
  };
  return labels[status || ''] || status || '—';
}

async function openReviewDrawer(row: DataSummaryRow) {
  reviewRow.value = row;
  reviewOrder.value = null;
  reviewDrawerVisible.value = true;
  reviewLoading.value = true;
  try {
    const response = await fetchEnrollmentOrder(row.orderId);
    reviewOrder.value = response.data.data;
  } catch (error) {
    ElMessage.error(`Failed to load review details: ${extractErrorMessage(error)}`);
  } finally {
    reviewLoading.value = false;
  }
}

function openRegistrationForm() {
  if (!reviewRow.value) return;
  router.push({ name: 'registration', query: { orderId: reviewRow.value.orderId } });
}

async function openPackageDialog(packageId: EntityId) {
  packageDialogVisible.value = true;
  packageLoading.value = true;
  reviewPackage.value = null;
  try {
    const response = await fetchCoursePackage(packageId);
    reviewPackage.value = response.data.data;
  } catch (error) {
    ElMessage.error(`Failed to load course package: ${extractErrorMessage(error)}`);
  } finally {
    packageLoading.value = false;
  }
}

async function openLedgerDialog(packageId: EntityId) {
  ledgerDialogVisible.value = true;
  ledgerLoading.value = true;
  ledgerRows.value = [];
  try {
    const response = await fetchHourLedger(packageId);
    ledgerRows.value = response.data.data.records;
  } catch (error) {
    ElMessage.error(`Failed to load hour ledger: ${extractErrorMessage(error)}`);
  } finally {
    ledgerLoading.value = false;
  }
}

function paymentTypeLabel(value?: string) {
  return value === 'REFUND' ? 'Refund' : 'Payment';
}

function courseTypeLabel(value?: string) {
  const match = courseTypeOptions.find((item) => item.value === value);
  return match?.label || value || 'Unknown Type';
}

function formatMoney(value?: number) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}

function formatDisplayBalance(value?: number) {
  const balance = Number(value ?? 0);
  return formatMoney(balance > 0 ? -balance : balance);
}

function formatHours(value?: number) {
  return Number(value ?? 0).toFixed(1);
}

function formatDate(value?: string) {
  return value ? dayjs(value).format('MM/DD/YYYY') : '—';
}

onMounted(() => {
  loadCampuses();
  loadSummary();
});
</script>

<style scoped>
.data-summary-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.summary-hero,
.report-summary-strip,
.summary-card,
.workbook-panel,
.summary-detail-collapse {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
}

.summary-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  background: #f0faf6;
  border-color: #d4f0e4;
}

.summary-hero.table-page-header {
  padding: 14px 16px;
  border-top: 0;
  border-color: #e2e8f0;
  border-radius: 0;
  background: #fff;
}

.summary-hero > div {
  width: 100%;
}

.summary-hero__eyebrow {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #18b58a;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.summary-hero h2,
.summary-card h3,
.workbook-panel h3 {
  margin: 0;
  color: #1a1a1a;
}

.summary-hero h2 {
  margin-bottom: 6px;
  font-size: 20px;
}

.summary-hero p {
  margin: 0;
  color: #606266;
  font-size: 13px;
}

.summary-search-bar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 10px 14px;
  padding: 12px 14px;
  border: 1px solid #e8edf2;
  border-top: 0;
  border-radius: 0 0 8px 8px;
  background: #fff;
}

.summary-search-bar--with-advanced {
  border-radius: 0;
}

.summary-tabs {
  padding: 0 12px;
  border: 1px solid #e8edf2;
  border-radius: 8px 8px 0 0;
  background: #fff;
}

.summary-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.summary-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.summary-tabs :deep(.el-tabs__item) {
  height: 34px;
  font-weight: 700;
}

.summary-search-bar__main {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
  min-width: 0;
}

.summary-search-bar__main :deep(.el-input) {
  width: 320px;
  max-width: 100%;
}

.summary-search-bar__search {
  min-width: 88px;
}

.summary-search-bar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.summary-search-bar__filters,
.summary-search-bar__reset {
  min-width: 72px;
}

.summary-search-bar__filter-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 2px;
  padding: 0 5px;
  border-radius: 9px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 11px;
}

.summary-advanced-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-top: 0;
  border-radius: 0 0 8px 8px;
  background: #fafcff;
}

.summary-advanced-filters :deep(.el-select) {
  width: 190px;
}

.summary-advanced-filters :deep(.el-date-editor) {
  width: 280px;
}

.report-summary-strip {
  display: flex;
  align-items: stretch;
  gap: 1px;
  overflow-x: auto;
  border: 1px solid #e8edf2;
  border-top: 0;
  border-radius: 0;
  background: #e8edf2;
}

.report-summary-strip__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  flex: 1 0 180px;
  min-width: 0;
  background: #fff;
  padding: 12px 14px;
}

.report-summary-strip__item span,
.report-summary-strip__item small {
  color: #909399;
  font-size: 12px;
}

.report-summary-strip__item strong {
  color: #1a1a1a;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.report-summary-strip__item.metric-card--danger strong {
  color: #d93026;
}

.report-summary-strip__item.metric-card--warning strong {
  color: #b7791f;
}

.metric-privacy-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  border: 0;
  border-left: 1px solid #e8edf2;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  font-size: 18px;
}

.metric-privacy-toggle:hover {
  background: #eff6ff;
  color: #2563eb;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  padding: 12px;
}

.summary-card__header,
.workbook-panel__header,
.summary-group {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary-card__header {
  margin-bottom: 12px;
}

.summary-card__header span,
.workbook-panel__header span,
.summary-group span,
.summary-group small,
.check-table small {
  color: #909399;
  font-size: 12px;
}

.summary-card__list {
  display: grid;
  gap: 8px;
  max-height: 190px;
  overflow: auto;
}

.check-table small {
  display: block;
}

.summary-group {
  border: 1px solid #edf0f3;
  border-radius: 6px;
  padding: 10px 12px;
}

.summary-group div:first-child {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.review-link {
  height: auto;
  padding: 0;
  font-size: 12px;
}

.review-empty {
  display: block;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.review-section {
  margin-bottom: 22px;
}

.review-section h4 {
  margin: 0 0 10px;
  color: #303133;
  font-size: 14px;
}

.review-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.review-section__header h4 {
  margin-bottom: 3px;
}

.review-section__header span {
  color: #909399;
  font-size: 12px;
}

.review-grid {
  display: grid;
  grid-template-columns: minmax(120px, 0.7fr) minmax(0, 1.3fr);
  gap: 8px 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  font-size: 13px;
}

.review-grid > span {
  color: #909399;
}

.review-grid > strong {
  font-variant-numeric: tabular-nums;
}

.review-package-list {
  display: grid;
  gap: 8px;
}

.review-package {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.review-package > div:first-child {
  display: grid;
  gap: 3px;
}

.review-package span {
  color: #909399;
  font-size: 12px;
}

.review-package__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.summary-group__amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}

.workbook-panel {
  margin-top: 16px;
  padding: 16px;
}

.workbook-panel.table-panel {
  padding: 12px;
  overflow-x: auto;
}

.workbook-panel__header {
  margin-bottom: 12px;
}

.summary-detail-collapse {
  overflow: hidden;
}

:deep(.summary-detail-collapse .el-collapse-item__header) {
  height: 42px;
  border-bottom: none;
  padding: 0 12px;
}

:deep(.summary-detail-collapse .el-collapse-item__wrap) {
  border-bottom: none;
}

:deep(.summary-detail-collapse .el-collapse-item__content) {
  padding: 0 12px 12px;
}

.summary-detail-collapse__title {
  color: #303133;
  font-size: 13px;
  font-weight: 700;
}

.summary-detail-collapse__meta {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.check-table-hint,
.check-muted {
  color: #909399;
  font-size: 12px;
}

.check-table-wrap {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.check-table {
  min-width: 1080px;
  background: #fff;
}

.check-table__row {
  display: grid;
  grid-template-columns:
    minmax(240px, 1.6fr)
    minmax(170px, 1.1fr)
    minmax(190px, 1.25fr)
    minmax(150px, 0.9fr)
    minmax(140px, 0.85fr)
    120px;
  gap: 0;
  border-bottom: 1px solid #edf2f7;
}

.check-table__row:last-child {
  border-bottom: 0;
}

.check-empty-row {
  display: grid;
  place-items: center;
  gap: 4px;
  min-height: 120px;
  border-bottom: 0;
  color: #606266;
  font-size: 13px;
  text-align: center;
}

.check-empty-row strong {
  color: #303133;
}

.check-empty-row span {
  color: #909399;
  font-size: 12px;
}

.check-table__row > * {
  min-width: 0;
  padding: 10px 12px;
}

.check-table__head {
  align-items: center;
  background: #f8fafc;
  color: #102a43;
  font-size: 12px;
  font-weight: 700;
}

.check-header-filter {
  display: grid;
  gap: 3px;
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  padding: 0;
  text-align: left;
}

.check-header-filter small {
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
}

.check-header-filter--active small {
  color: #2563eb;
}

.check-filter-menu {
  display: grid;
  gap: 4px;
}

.check-filter-menu button {
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #303133;
  cursor: pointer;
  font-size: 12px;
  padding: 7px 8px;
  text-align: left;
}

.check-filter-menu button:hover,
.check-filter-menu button.active {
  background: #ecf5ff;
  color: #2563eb;
}

.check-row--danger > :first-child {
  box-shadow: inset 3px 0 0 #f56c6c;
}

.check-row--warning > :first-child {
  box-shadow: inset 3px 0 0 #e6a23c;
}

.check-primary,
.check-stack {
  display: grid;
  gap: 4px;
  min-width: 0;
  font-size: 12px;
  line-height: 1.4;
}

.check-primary strong,
.check-stack strong {
  color: #303133;
}

.check-primary span,
.check-primary small,
.check-stack small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check-issue {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  font-size: 12px;
  line-height: 1.4;
}

.check-issue__dot {
  flex: 0 0 auto;
  width: 9px;
  height: 9px;
  margin-top: 4px;
  border-radius: 50%;
  background: #67c23a;
}

.check-issue div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.check-issue strong {
  color: #303133;
}

.check-issue small {
  overflow: hidden;
  color: #606266;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check-issue--danger .check-issue__dot {
  background: #f56c6c;
}

.check-issue--warning .check-issue__dot {
  background: #e6a23c;
}

.check-issue--success .check-issue__dot {
  background: #67c23a;
}

.check-warning {
  color: #a16207 !important;
}

.check-action {
  text-align: center;
}

.check-action .review-link {
  white-space: nowrap;
}

.empty-line {
  padding: 16px 0;
  color: #909399;
  text-align: center;
}

@media (max-width: 1280px) {
  .report-summary-strip,
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-advanced-filters {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-advanced-filters :deep(.el-select),
  .summary-advanced-filters :deep(.el-date-editor) {
    width: 100%;
  }

  .summary-search-bar__actions {
    justify-content: flex-end;
  }
}

@media (max-width: 720px) {
  .summary-search-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-search-bar__main {
    width: 100%;
  }

  .summary-search-bar__main :deep(.el-input) {
    width: 100%;
  }

  .summary-search-bar__actions {
    justify-content: flex-start;
  }
}
</style>
