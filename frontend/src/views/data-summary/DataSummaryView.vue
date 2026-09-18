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
      </div>
      <div class="workbook-toolbar__stats">
        <span class="workbook-toolbar__stat">
          <em>Registrations</em>
          <strong>{{ monthStats.count }}</strong>
        </span>
        <span class="workbook-toolbar__stat">
          <em>Paid</em>
          <strong>{{ formatMoney(monthStats.paid) }}</strong>
        </span>
        <span class="workbook-toolbar__stat">
          <em>Balance</em>
          <strong>{{ formatMoney(monthStats.balance) }}</strong>
        </span>
      </div>
      <div class="workbook-toolbar__export">
        <el-button
          size="small"
          type="primary"
          :disabled="sortedRows.length === 0"
          @click="exportExcel"
        >
          Export Excel
        </el-button>
      </div>
    </div>
    <div class="workbook-scroll" data-testid="data-summary-workbook">
      <table class="workbook-table">
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

                  <div v-else class="workbook-number-tools">
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

                    <div class="workbook-filter-section">
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
                <td>{{ displayText(row.phone) }}</td>
                <td>{{ displayText(row.phone2) }}</td>
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
                <td class="workbook-cell--money">{{ formatMoney(row.linePriceAmount) }}</td>
                <td class="workbook-cell--money">{{ formatMoney(row.pickupFeeAmount) }}</td>
                <td class="workbook-cell--money">{{ formatMoney(row.discountAmount) }}</td>
                <td class="workbook-cell--money">{{ formatMoney(row.paidAmount) }}</td>
                <td
                  class="workbook-cell--money"
                  :class="{ 'workbook-cell--balance-due': Number(row.balanceAmount ?? 0) < 0 }"
                >
                  {{ formatMoney(row.balanceAmount) }}
                </td>
              </tr>
            </template>
          </template>
        </tbody>

        <tbody v-else-if="!loading">
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
        v-if="canRefundFromDataSummary(studentContextMenu.row)"
        type="button"
        role="menuitem"
        @click="openOrderAction('refund')"
      >
        Refund
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
import { ElMessage } from 'element-plus';
import * as XLSX from 'xlsx';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import { fetchDataSummary, type DataSummaryRow } from '@/api/data-summary';
import { fetchAllCourseProducts, saveCourseProductTabOrder } from '@/api/base-data';
import { extractErrorMessage } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import { isAdminUser } from '@/types/auth';
import type { CourseProductItem } from '@/types/domain';
import { useRouter } from 'vue-router';

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
  | 'transportFee'
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

/** 每个 Product Tab 独立保留的视图状态（月份、筛选、排序）。 */
interface ProductViewState {
  selectedMonth: string;
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

type SaturdayRosterSeason = 'FALL' | 'SPRING';

interface DataSummaryDisplayRow extends DataSummaryRow {
  /** Data Summary 专用展示分组，不写回报名记录。 */
  displayStudentClass?: string;
  displaySourceKey?: string;
}

/** 全局工作表页签固定在最左，展示全部报名数据。 */
const ALL_TAB_ID = '__ALL__';
/** 未关联到当前有效产品的历史行兜底页签，固定在末尾且不可拖拽。 */
const UNASSIGNED_TAB_ID = '__UNASSIGNED__';
const ORDER_SAVE_DEBOUNCE_MS = 300;
const CLASS_SORT_ORDER = ['G1', 'G2', 'G3', 'G4', 'G5', 'G6', 'G7', 'G8', 'ELT', 'SHSAT', 'PSAT', 'SAT', 'ESL', 'TBD'];

const columns: WorkbookColumn[] = [
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
  { key: 'transportFee', label: 'Pickup/Dropoff', width: 128, filterType: 'number' },
  { key: 'discount', label: 'Discount', width: 100, filterType: 'number' },
  { key: 'paid', label: 'Paid', width: 100, filterType: 'number' },
  { key: 'balance', label: 'Balance', width: 104, filterType: 'number' },
];

const authStore = useAuthStore();
const router = useRouter();
const isAdmin = computed(() => isAdminUser(authStore.currentUser));
const canDragTabs = computed(() => isAdmin.value && !savingOrder.value);

const loading = ref(false);
const savingOrder = ref(false);
const rows = ref<DataSummaryRow[]>([]);
const products = ref<CourseProductItem[]>([]);
const activeTabId = ref<string>('');
const selectedMonth = ref<string>('ALL');
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
    return productRows.value;
  }
  return expandSaturdayRosterRows(productRows.value);
});

const selectOptions = computed<Record<ColumnKey, string[]>>(() => {
  return Object.fromEntries(columns.map((column) => {
    if (column.filterType !== 'select') {
      return [column.key, []];
    }
    const values = displayProductRows.value.map((row) => selectValue(row, column.key));
    return [column.key, [...new Set(values)].sort(naturalCompare)];
  })) as Record<ColumnKey, string[]>;
});

// Month is derived from the sign-up date so staff can review one month at a time.
function monthKey(row: DataSummaryRow) {
  return row.signupDate ? dayjs(row.signupDate).format('YYYY-MM') : '';
}

const availableMonths = computed(() => {
  const keys = new Set<string>();
  displayProductRows.value.forEach((row) => {
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

const monthStats = computed(() => {
  return uniqueSourceRows(monthFilteredRows.value).reduce(
    (acc, row) => {
      acc.count += 1;
      acc.paid += Number(row.paidAmount ?? 0);
      acc.balance += Number(row.balanceAmount ?? 0);
      return acc;
    },
    { count: 0, paid: 0, balance: 0 },
  );
});

const filteredRows = computed<DataSummaryDisplayRow[]>(() => monthFilteredRows.value.filter((row) => {
  return columns.every((column) => matchesColumnFilter(row, column));
}));

const sortedRows = computed<DataSummaryDisplayRow[]>(() => {
  const source = [...filteredRows.value];
  if (!sortState.key) {
    return source.sort(compareRowsByBusinessGroup);
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
});

/** 不再分页：每个 Tab 一次性渲染全部数据行。 */
const pagedRows = computed(() => sortedRows.value);

const groupedCampusRows = computed<WorkbookCampusGroup[]>(() => {
  const groups = new Map<string, WorkbookCampusGroup>();
  pagedRows.value.forEach((row) => {
    const campusName = displayText(row.campusName);
    const className = displayText(row.displayStudentClass ?? displayClassName(row));
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
    columnFilters: Object.fromEntries(
      columns.map((column) => [column.key, createEmptyFilter()]),
    ) as Record<ColumnKey, ColumnFilter>,
    sortKey: null,
    sortDirection: 'asc',
  };
}

function restoreState(state: ProductViewState) {
  selectedMonth.value = state.selectedMonth;
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

function openStudentContextMenu(event: MouseEvent, row: DataSummaryDisplayRow) {
  const menuWidth = 150;
  const menuHeight = 140;
  studentContextMenu.x = Math.max(8, Math.min(event.clientX, window.innerWidth - menuWidth - 8));
  studentContextMenu.y = Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8));
  studentContextMenu.row = row;
  studentContextMenu.visible = true;
}

function closeStudentContextMenu() {
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
  return Boolean(
    row
      && row.orderStatus === 'CONFIRMED'
      // Data Summary 将待收余额按负数展示；订单页内部仍使用正数做支付计算。
      && Number(row.balanceAmount ?? 0) < 0,
  );
}

function canRefundFromDataSummary(row: DataSummaryDisplayRow | null) {
  return Boolean(
    row
      && row.paymentStatus !== 'REFUNDED'
      && Number(row.paidAmount ?? 0) > 0,
  );
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
      orderItemId: action === 'pay' && row.orderItemId != null
        ? String(row.orderItemId)
        : undefined,
      participantIndex: (action === 'pay' || action === 'refund') && row.participantIndex != null
        ? String(row.participantIndex)
        : undefined,
    },
  });
}

function handleStudentContextMenuKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeStudentContextMenu();
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
});

function matchesColumnFilter(row: DataSummaryDisplayRow, column: WorkbookColumn) {
  const filter = columnFilters[column.key];
  if (column.filterType === 'text') {
    const keyword = filter.text.trim().toLowerCase();
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
      return row.displayStudentClass ?? displayClassName(row);
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
      return row.periodStartDate || row.periodLabel;
    case 'tuition':
      return Number(row.linePriceAmount ?? 0);
    case 'transportFee':
      return Number(row.pickupFeeAmount ?? 0);
    case 'discount':
      return Number(row.discountAmount ?? 0);
    case 'paid':
      return Number(row.paidAmount ?? 0);
    case 'balance':
      return Number(row.balanceAmount ?? 0);
  }
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

function compareRowsByBusinessGroup(left: DataSummaryDisplayRow, right: DataSummaryDisplayRow) {
  return compareCampus(displayText(left.campusName), displayText(right.campusName))
    || compareClassName(displayText(left.displayStudentClass ?? displayClassName(left)), displayText(right.displayStudentClass ?? displayClassName(right)))
    || comparePrivateLessonGroup(left, right)
    || naturalCompare(displayText(left.studentName), displayText(right.studentName))
    || compareClassName(displayText(left.grade), displayText(right.grade))
    || naturalCompare(left.signupDate ?? '', right.signupDate ?? '')
    || naturalCompare(rowKey(left), rowKey(right));
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
    row.orderItemId ?? row.classId ?? row.courseProductId ?? row.enrollmentNo,
    row.studentId ?? row.studentName,
    row.displayStudentClass ?? displayClassName(row),
  ].join('-');
}

function sourceRowKey(row: DataSummaryDisplayRow) {
  return row.displaySourceKey ?? rowKey(row);
}

function uniqueSourceRows(sourceRows: DataSummaryDisplayRow[]) {
  const rowsBySource = new Map<string, DataSummaryDisplayRow>();
  sourceRows.forEach((row) => rowsBySource.set(sourceRowKey(row), row));
  return [...rowsBySource.values()];
}

function expandSaturdayRosterRows(sourceRows: DataSummaryRow[]): DataSummaryDisplayRow[] {
  return sourceRows.flatMap((row) => {
    const baseClass = saturdayBaseClass(row);
    const sourceKey = rowKey(row);
    const season = saturdayRosterSeason(row);
    if (season === 'FULL_YEAR') {
      return (['FALL', 'SPRING'] as const).map((targetSeason) => ({
        ...row,
        displayStudentClass: `${baseClass} · ${targetSeason === 'FALL' ? 'Fall' : 'Spring'}`,
        displaySourceKey: sourceKey,
      }));
    }
    if (season === 'FALL' || season === 'SPRING') {
      return [{
        ...row,
        displayStudentClass: `${baseClass} · ${season === 'FALL' ? 'Fall' : 'Spring'}`,
        displaySourceKey: sourceKey,
      }];
    }
    return [{
      ...row,
      displayStudentClass: baseClass,
      displaySourceKey: sourceKey,
    }];
  });
}

function saturdayRosterSeason(row: DataSummaryRow): 'FULL_YEAR' | SaturdayRosterSeason | undefined {
  const labels = [row.periodLabel, row.className, row.registerClass, row.detail]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  if (/全年|full[\s-]*year|annual/.test(labels)) {
    return 'FULL_YEAR';
  }
  if (/秋季|fall|autumn/.test(labels)) {
    return 'FALL';
  }
  if (/春季|spring/.test(labels)) {
    return 'SPRING';
  }
  return undefined;
}

function saturdayBaseClass(row: DataSummaryRow) {
  const className = displayClassName(row);
  if (className && !/(全年班|秋季班|春季班|full[\s-]*year|fall|spring)/i.test(className)) {
    return className;
  }

  const classSuffix = className.match(/(?:全年班|秋季班|春季班|full[\s-]*year|fall|spring)\s*[-·:]?\s*(.+)$/i)?.[1]?.trim();
  if (classSuffix) {
    return classSuffix;
  }

  return className || row.grade?.trim() || 'Class';
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
  if (!row.periodStartDate && !row.periodEndDate) {
    return displayText(row.periodLabel);
  }
  const start = row.periodStartDate ? dayjs(row.periodStartDate).format('M/D/YYYY') : '—';
  const end = row.periodEndDate ? dayjs(row.periodEndDate).format('M/D/YYYY') : '—';
  return `${start} - ${end}`;
}

function isPeriodRenewalDue(row: DataSummaryRow) {
  if (!row.periodEndDate) {
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
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));
}

// ─── Excel 导出 ─────────────────────────────────
// 金额列导出为数值，便于在 Excel 中直接求和/计算；其余列导出为展示文本。
const EXPORT_NUMERIC_COLUMNS: ReadonlySet<ColumnKey> = new Set([
  'tuition',
  'transportFee',
  'discount',
  'paid',
  'balance',
]);

function exportExcel() {
  const dataRows = sortedRows.value;
  if (dataRows.length === 0) {
    ElMessage.warning('No rows to export.');
    return;
  }
  const aoa: (string | number)[][] = [columns.map((column) => column.label)];
  dataRows.forEach((row) => {
    aoa.push(columns.map((column) => exportCell(row, column.key)));
  });
  const sheet = XLSX.utils.aoa_to_sheet(aoa);
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
});
</script>

<style scoped>
.data-summary-page {
  min-width: 0;
  height: calc(100vh - 88px);
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

.workbook-toolbar__month {
  display: flex;
  align-items: center;
  gap: 8px;
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
  height: 100%;
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid #9eafa7;
  background: #fff;
}

.workbook-table {
  width: max-content;
  min-width: 100%;
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
  text-overflow: ellipsis;
  white-space: nowrap;
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

.workbook-header-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  width: 100%;
  height: 100%;
  padding: 5px 7px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.workbook-header-button:hover,
.workbook-header--active .workbook-header-button {
  background: #2d5544;
}

.workbook-header-icons {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
}

.workbook-sort-indicator {
  color: #ffd95a;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.workbook-header--active .workbook-filter-arrow {
  border-top-color: #ffd95a;
}

.workbook-filter-arrow {
  flex: 0 0 auto;
  width: 0;
  height: 0;
  border-top: 5px solid #d9ebe3;
  border-right: 4px solid transparent;
  border-left: 4px solid transparent;
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

.workbook-number-tools {
  display: grid;
  gap: 12px;
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

/* ── Product Tab 工作表页签（位于表格底部） ── */
.product-tab-bar {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  padding: 4px 8px 0;
  border: 1px solid #9eafa7;
  border-top: 0;
  border-bottom: 0;
  background: #dce8e2;
  overflow-x: auto;
  scrollbar-width: none; /* Firefox: 隐藏横向滚动条，保留滚轮/触控板滚动 */
}

/* Chrome / Safari (Chromium)：隐藏横向滚动条，但保留滚动能力 */
.product-tab-bar::-webkit-scrollbar {
  display: none;
}

.product-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  max-width: 220px;
  height: 30px;
  padding: 0 14px;
  border: 1px solid #b7cec5;
  border-bottom: 0;
  border-radius: 7px 7px 0 0;
  background: #eef5f1;
  color: #376451;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}

.product-tab:hover {
  background: #f7fbf9;
  color: #224a3b;
}

.product-tab__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-tab--active {
  background: #fff;
  border-color: #9eafa7;
  color: #1f3d32;
  font-weight: 700;
  box-shadow: inset 0 3px 0 #2f8f6f;
}

.product-tab--private {
  border-color: #b5cfee;
  background: #edf5ff;
  color: #245c98;
}

.product-tab--private:hover {
  border-color: #94b9e8;
  background: #f6faff;
  color: #194b83;
}

.product-tab--private.product-tab--active {
  border-color: #94b9e8;
  background: #fff;
  color: #194b83;
  box-shadow: inset 0 3px 0 #4a90df;
}

.product-tab--global {
  min-width: 96px;
  border-color: #2f6b55;
  background: #376451;
  color: #fff;
  font-weight: 800;
}

.product-tab--global:hover {
  background: #2f5b49;
  color: #fff;
}

.product-tab--global.product-tab--active {
  background: #fff;
  border-color: #9eafa7;
  color: #214d3b;
}

.product-tab--unassigned {
  background: #f4f1e8;
  border-style: dashed;
  border-color: #c9bfa5;
  color: #7a6a3f;
}

.product-tab--unassigned.product-tab--active {
  background: #fffdf5;
  box-shadow: inset 0 3px 0 #b09a55;
  color: #5c4d24;
}

.product-tab[draggable='true'] {
  cursor: grab;
}

.product-tab--dragging {
  opacity: 0.45;
  transform: translateY(2px) rotate(-1deg);
}

.product-tab--drop-target {
  border-color: #2f8f6f;
  box-shadow: inset 0 0 0 2px rgb(47 143 111 / 35%);
}

@media (max-width: 720px) {
  .data-summary-page {
    height: calc(100vh - 76px);
  }
}
</style>
