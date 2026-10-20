<template>
  <div class="data-summary-page transport-page" v-loading="loading">
    <div class="workbook-toolbar">
      <div class="workbook-toolbar__month">
        <template v-if="appStore.isMobileViewport">
          <div class="mobile-search-heading">
            <span class="workbook-toolbar__label">Transport roster</span>
            <div class="mobile-toolbar-actions">
              <button type="button" class="mobile-search-trigger" :aria-expanded="mobileSearchExpanded" aria-label="Search the transport roster" @click="mobileSearchExpanded = !mobileSearchExpanded"><el-icon><Search /></el-icon></button>
              <el-dropdown trigger="click" @command="handleMobileAction">
                <button type="button" class="mobile-search-trigger" aria-label="More transport actions"><el-icon><MoreFilled /></el-icon></button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="drivers">Manage drivers</el-dropdown-item>
                    <el-dropdown-item command="export" :disabled="!selectedCourse">Export Excel</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <section v-show="mobileSearchExpanded" class="mobile-search-panel">
            <div class="mobile-search-panel__header">
              <span>Find a class or student</span>
              <button type="button" aria-label="Close search" @click="mobileSearchExpanded = false">
                <el-icon><Close /></el-icon>
              </button>
            </div>
            <el-input v-model="keyword" clearable placeholder="Search course, class, or student" />
            <el-select v-model="classFilter" clearable filterable placeholder="All classes">
              <el-option v-for="className in classOptions" :key="className" :label="className" :value="className" />
            </el-select>
            <div class="workbook-toolbar__stats">
              <span class="workbook-toolbar__stat"><em>Courses</em><strong>{{ courseTabs.length }}</strong></span>
              <span class="workbook-toolbar__stat"><em>Pickup</em><strong>{{ selectedCourse?.pickupRows.length ?? 0 }}</strong></span>
              <span class="workbook-toolbar__stat"><em>Drop-off</em><strong>{{ selectedCourse?.dropoffRows.length ?? 0 }}</strong></span>
            </div>
          </section>
        </template>
        <template v-else>
          <span class="workbook-toolbar__label">Transport roster</span>
          <el-input v-model="keyword" class="workbook-toolbar__select" clearable placeholder="Search course, class, or student" />
          <el-select v-model="classFilter" class="workbook-toolbar__select" size="small" clearable filterable placeholder="Class">
            <el-option v-for="className in classOptions" :key="className" :label="className" :value="className" />
          </el-select>
        </template>
      </div>
      <div v-if="!appStore.isMobileViewport" class="workbook-toolbar__stats">
        <span class="workbook-toolbar__stat"><em>Courses</em><strong>{{ courseTabs.length }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Pickup</em><strong>{{ selectedCourse?.pickupRows.length ?? 0 }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Drop-off</em><strong>{{ selectedCourse?.dropoffRows.length ?? 0 }}</strong></span>
      </div>
      <div v-if="!appStore.isMobileViewport" class="workbook-toolbar__export">
        <el-button size="small" @click="openDriverDialog">Manage drivers</el-button>
        <el-button type="primary" size="small" :disabled="!selectedCourse" @click="exportExcel">Export Excel</el-button>
      </div>
    </div>

    <div v-if="selectedCourse" class="workbook-scroll transport-scroll">
      <section class="transport-roster-section">
        <div class="transport-roster-section__heading">
          <strong>Pickup</strong>
          <span>{{ selectedCourse.pickupRows.length }} students need pickup</span>
        </div>
        <div ref="pickupScrollRef" class="transport-table-scroll mobile-pinch-scroll" role="region" aria-label="Pickup table, scroll horizontally">
          <table class="workbook-table transport-detail-table" :style="{ zoom: appStore.isMobileViewport ? pickupTableScale : undefined }">
          <colgroup><col v-for="column in tableColumns('pickup')" :key="column.key" :style="{ width: `${column.width}px` }" /></colgroup>
          <thead>
            <tr>
              <th
                v-for="column in tableColumns('pickup')"
                :key="column.key"
                :class="{ 'workbook-header--active': hasTransportFilter(column.key) || isColumnSorted(column.key, 'pickup') }"
              >
                <el-popover placement="bottom-start" trigger="click" :width="300" popper-class="workbook-filter-popper">
                  <template #reference>
                    <button class="workbook-header-button" type="button" :aria-label="`Sort and filter ${column.label}`">
                      <span>{{ column.label }}</span>
                      <span class="workbook-header-icons">
                        <span v-if="isColumnSorted(column.key, 'pickup')" class="workbook-sort-indicator">
                          {{ pickupSortState.direction === 'asc' ? '↑' : '↓' }}
                        </span>
                        <span class="workbook-filter-arrow" aria-hidden="true" />
                      </span>
                    </button>
                  </template>
                  <div class="workbook-filter">
                    <strong>Filter {{ column.label }}</strong>
                    <el-input
                      v-if="column.filterType === 'text'"
                      v-model="transportFilters[column.key].text"
                      clearable
                      :placeholder="`Search ${column.label}`"
                    />
                    <el-select
                      v-else
                      v-model="transportFilters[column.key].selected"
                      multiple
                      clearable
                      collapse-tags
                      collapse-tags-tooltip
                      placeholder="All"
                    >
                      <el-option v-for="option in transportSelectOptions(column.key, 'pickup')" :key="option" :label="option" :value="option" />
                    </el-select>
                    <div class="workbook-filter-section">
                      <span class="workbook-filter-label">Sort</span>
                      <el-button-group class="workbook-sort-buttons">
                        <el-button size="small" :type="isColumnSorted(column.key, 'pickup', 'asc') ? 'primary' : 'default'" @click="setSort(column.key, 'pickup', 'asc')">Asc</el-button>
                        <el-button size="small" :type="isColumnSorted(column.key, 'pickup', 'desc') ? 'primary' : 'default'" @click="setSort(column.key, 'pickup', 'desc')">Desc</el-button>
                        <el-button size="small" @click="clearSort(column.key, 'pickup')">Clear</el-button>
                      </el-button-group>
                    </div>
                    <div class="workbook-filter-actions">
                      <el-button text size="small" @click="clearTransportFilter(column.key, 'pickup')">Clear column</el-button>
                      <el-button text size="small" @click="clearAllTransportFilters">Clear all</el-button>
                    </div>
                  </div>
                </el-popover>
              </th>
            </tr>
          </thead>
          <tbody v-if="selectedCourse.pickupRows.length">
            <tr v-for="row in filteredPickupRows" :key="`pickup-${rowKey(row)}`" class="workbook-data-row">
              <td v-for="column in tableColumns('pickup')" :key="column.key">
                <el-select
                  v-if="column.key === 'driver'"
                  :model-value="driverIdForRow(row, 'pickup')"
                  class="transport-driver-select"
                  size="small"
                  clearable
                  filterable
                  placeholder="Unassigned"
                  @change="handleDriverChange(row, 'pickup', $event)"
                >
                  <el-option v-for="driver in drivers" :key="String(driver.id)" :label="driver.driverName" :value="String(driver.id)" />
                </el-select>
                <span v-else>{{ transportCellValue(row, column.key, 'pickup') }}</span>
              </td>
            </tr>
          </tbody>
          <tbody v-else><tr class="workbook-empty-row"><td :colspan="tableColumns('pickup').length">No pickup students for this course.</td></tr></tbody>
          <tbody v-if="selectedCourse.pickupRows.length && !filteredPickupRows.length"><tr class="workbook-empty-row"><td :colspan="tableColumns('pickup').length">No pickup students match these filters.</td></tr></tbody>
          </table>
        </div>
      </section>

      <section class="transport-roster-section transport-roster-section--dropoff">
        <div class="transport-roster-section__heading">
          <strong>Drop-off</strong>
          <span>{{ selectedCourse.dropoffRows.length }} students need drop-off</span>
        </div>
        <div ref="dropoffScrollRef" class="transport-table-scroll mobile-pinch-scroll" role="region" aria-label="Drop-off table, scroll horizontally">
          <table class="workbook-table transport-detail-table" :style="{ zoom: appStore.isMobileViewport ? dropoffTableScale : undefined }">
          <colgroup><col v-for="column in tableColumns('dropoff')" :key="column.key" :style="{ width: `${column.width}px` }" /></colgroup>
          <thead>
            <tr>
              <th
                v-for="column in tableColumns('dropoff')"
                :key="column.key"
                :class="{ 'workbook-header--active': hasTransportFilter(column.key) || isColumnSorted(column.key, 'dropoff') }"
              >
                <el-popover placement="bottom-start" trigger="click" :width="300" popper-class="workbook-filter-popper">
                  <template #reference>
                    <button class="workbook-header-button" type="button" :aria-label="`Sort and filter ${column.label}`">
                      <span>{{ column.label }}</span>
                      <span class="workbook-header-icons">
                        <span v-if="isColumnSorted(column.key, 'dropoff')" class="workbook-sort-indicator">
                          {{ dropoffSortState.direction === 'asc' ? '↑' : '↓' }}
                        </span>
                        <span class="workbook-filter-arrow" aria-hidden="true" />
                      </span>
                    </button>
                  </template>
                  <div class="workbook-filter">
                    <strong>Filter {{ column.label }}</strong>
                    <el-input
                      v-if="column.filterType === 'text'"
                      v-model="transportFilters[column.key].text"
                      clearable
                      :placeholder="`Search ${column.label}`"
                    />
                    <el-select
                      v-else
                      v-model="transportFilters[column.key].selected"
                      multiple
                      clearable
                      collapse-tags
                      collapse-tags-tooltip
                      placeholder="All"
                    >
                      <el-option v-for="option in transportSelectOptions(column.key, 'dropoff')" :key="option" :label="option" :value="option" />
                    </el-select>
                    <div class="workbook-filter-section">
                      <span class="workbook-filter-label">Sort</span>
                      <el-button-group class="workbook-sort-buttons">
                        <el-button size="small" :type="isColumnSorted(column.key, 'dropoff', 'asc') ? 'primary' : 'default'" @click="setSort(column.key, 'dropoff', 'asc')">Asc</el-button>
                        <el-button size="small" :type="isColumnSorted(column.key, 'dropoff', 'desc') ? 'primary' : 'default'" @click="setSort(column.key, 'dropoff', 'desc')">Desc</el-button>
                        <el-button size="small" @click="clearSort(column.key, 'dropoff')">Clear</el-button>
                      </el-button-group>
                    </div>
                    <div class="workbook-filter-actions">
                      <el-button text size="small" @click="clearTransportFilter(column.key, 'dropoff')">Clear column</el-button>
                      <el-button text size="small" @click="clearAllTransportFilters">Clear all</el-button>
                    </div>
                  </div>
                </el-popover>
              </th>
            </tr>
          </thead>
          <tbody v-if="selectedCourse.dropoffRows.length">
            <tr v-for="row in filteredDropoffRows" :key="`dropoff-${rowKey(row)}`" class="workbook-data-row">
              <td v-for="column in tableColumns('dropoff')" :key="column.key">
                <el-select
                  v-if="column.key === 'driver'"
                  :model-value="driverIdForRow(row, 'dropoff')"
                  class="transport-driver-select"
                  size="small"
                  clearable
                  filterable
                  placeholder="Unassigned"
                  @change="handleDriverChange(row, 'dropoff', $event)"
                >
                  <el-option v-for="driver in drivers" :key="String(driver.id)" :label="driver.driverName" :value="String(driver.id)" />
                </el-select>
                <span v-else>{{ transportCellValue(row, column.key, 'dropoff') }}</span>
              </td>
            </tr>
          </tbody>
          <tbody v-else><tr class="workbook-empty-row"><td :colspan="tableColumns('dropoff').length">No drop-off students for this course.</td></tr></tbody>
          <tbody v-if="selectedCourse.dropoffRows.length && !filteredDropoffRows.length"><tr class="workbook-empty-row"><td :colspan="tableColumns('dropoff').length">No drop-off students match these filters.</td></tr></tbody>
          </table>
        </div>
      </section>
    </div>
    <div v-else-if="!loading" class="transport-empty">No pickup or drop-off records.</div>

    <nav v-if="courseTabs.length" class="product-tab-bar transport-tab-bar" role="tablist" aria-label="Course tabs">
      <button
        v-for="course in courseTabs"
        :key="course.key"
        type="button"
        class="product-tab"
        :class="{
          'product-tab--active': course.key === displayedCourseKey,
          'product-tab--dragging': dragState.fromId === course.key,
          'product-tab--drop-target': dragState.overId === course.key && dragState.fromId !== course.key,
        }"
        :draggable="canMoveCourseTab(course)"
        role="tab"
        :aria-selected="course.key === displayedCourseKey"
        :title="course.course"
        @click="selectCourseTab(course.key)"
        @dragstart="handleDragStart(course, $event)"
        @dragenter.prevent="handleDragEnter(course)"
        @dragover.prevent="handleDragOver(course)"
        @drop.prevent="handleDrop(course)"
        @dragend="handleDragEnd"
      >
        <span class="product-tab__label">{{ course.course }}</span>
      </button>
    </nav>
  </div>

  <el-dialog v-model="driverDialogVisible" title="Manage transport drivers" width="520px" :close-on-click-modal="false">
    <div class="driver-editor">
      <el-input v-model="driverForm.driverName" clearable placeholder="Driver name" @keyup.enter="saveDriver" />
      <el-button type="primary" :loading="driverSaving" @click="saveDriver">
        {{ editingDriverId ? 'Update' : 'Add driver' }}
      </el-button>
      <el-button v-if="editingDriverId" @click="cancelDriverEdit">Cancel</el-button>
    </div>
    <div v-if="drivers.length" class="driver-list">
      <div v-for="driver in drivers" :key="String(driver.id)" class="driver-list__row">
        <span>{{ driver.driverName }}</span>
        <span class="driver-list__actions">
          <el-button link type="primary" :icon="EditPen" aria-label="Edit driver" title="Edit driver" @click="editDriver(driver)" />
          <el-button link type="danger" :icon="Delete" aria-label="Delete driver" title="Delete driver" @click="removeDriver(driver)" />
        </span>
      </div>
    </div>
    <div v-else class="driver-list__empty">No drivers configured.</div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import * as XLSX from 'xlsx';
import { fetchDataSummary, type DataSummaryRow } from '@/api/data-summary';
import { fetchAllCourseProducts, saveCourseProductTabOrder } from '@/api/base-data';
import {
  createTransportDriver,
  deleteTransportDriver,
  fetchTransportDriverAssignments,
  fetchTransportDrivers,
  saveTransportDriverAssignment,
  updateTransportDriver,
  type TransportDriver,
} from '@/api/transport';
import { extractErrorMessage } from '@/api/http';
import { Close, Delete, EditPen, MoreFilled, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { isAdminUser } from '@/types/auth';
import type { CourseProductItem } from '@/types/domain';
import { formatUsPhoneDisplay, matchesPhoneSearch } from '@/utils/phone-format';
import { useAppStore } from '@/stores/app';
import { useMobileTablePinchZoom } from '@/composables/useMobileTablePinchZoom';

type TransportTable = 'pickup' | 'dropoff';
type TransportColumnKey = 'period' | 'campus' | 'student' | 'grade' | 'class' | 'phone' | 'driver' | 'pickupAddress' | 'dropoffAddress';
type TransportFilterType = 'text' | 'select';

interface TransportColumn {
  key: TransportColumnKey;
  label: string;
  width: number;
  filterType: TransportFilterType;
}

interface TransportFilter {
  text: string;
  selected: string[];
}

type TransportSortDirection = 'asc' | 'desc';

interface TransportSortState {
  key: TransportColumnKey | null;
  direction: TransportSortDirection;
}

interface TransportTabState {
  filters: Record<TransportColumnKey, TransportFilter>;
}

interface TransportCourseTab {
  key: string;
  course: string;
  showClass: boolean;
  classNames: string[];
  rows: DataSummaryRow[];
  pickupRows: DataSummaryRow[];
  dropoffRows: DataSummaryRow[];
}

const rows = ref<DataSummaryRow[]>([]);
const products = ref<CourseProductItem[]>([]);
const drivers = ref<TransportDriver[]>([]);
const driverAssignments = reactive<Record<string, string>>({});
const keyword = ref('');
const classFilter = ref('');
const appStore = useAppStore();
const mobileSearchExpanded = ref(false);
const pickupScrollRef = ref<HTMLElement | null>(null);
const dropoffScrollRef = ref<HTMLElement | null>(null);
const { scale: pickupTableScale } = useMobileTablePinchZoom(pickupScrollRef, computed(() => appStore.isMobileViewport));
const { scale: dropoffTableScale } = useMobileTablePinchZoom(dropoffScrollRef, computed(() => appStore.isMobileViewport));
const activeCourseKey = ref('');
const loading = ref(false);
const savingOrder = ref(false);
const driverDialogVisible = ref(false);
const driverSaving = ref(false);
const editingDriverId = ref<string | null>(null);
const driverForm = reactive({ driverName: '' });
const authStore = useAuthStore();
const isAdmin = computed(() => isAdminUser(authStore.currentUser));
const canDragTabs = computed(() => isAdmin.value && !savingOrder.value);

const pickupColumns: TransportColumn[] = [
  { key: 'driver', label: 'Driver', width: 180, filterType: 'select' },
  { key: 'period', label: 'Period', width: 190, filterType: 'text' },
  { key: 'campus', label: 'Campus', width: 180, filterType: 'select' },
  { key: 'student', label: 'Student', width: 220, filterType: 'text' },
  { key: 'grade', label: 'Grade', width: 100, filterType: 'select' },
  { key: 'class', label: 'Class', width: 150, filterType: 'select' },
  { key: 'phone', label: 'Phone', width: 160, filterType: 'text' },
  { key: 'pickupAddress', label: 'Pickup address', width: 360, filterType: 'text' },
];

const dropoffColumns: TransportColumn[] = [
  { key: 'driver', label: 'Driver', width: 180, filterType: 'select' },
  { key: 'period', label: 'Period', width: 190, filterType: 'text' },
  { key: 'campus', label: 'Campus', width: 180, filterType: 'select' },
  { key: 'student', label: 'Student', width: 220, filterType: 'text' },
  { key: 'grade', label: 'Grade', width: 100, filterType: 'select' },
  { key: 'class', label: 'Class', width: 150, filterType: 'select' },
  { key: 'phone', label: 'Phone', width: 160, filterType: 'text' },
  { key: 'dropoffAddress', label: 'Drop-off address', width: 360, filterType: 'text' },
];

function createEmptyTransportFilter(): TransportFilter {
  return { text: '', selected: [] };
}

function createDefaultTransportFilters(): Record<TransportColumnKey, TransportFilter> {
  return Object.fromEntries(
    (['period', 'campus', 'student', 'grade', 'class', 'phone', 'driver', 'pickupAddress', 'dropoffAddress'] as TransportColumnKey[])
      .map((key) => [key, createEmptyTransportFilter()]),
  ) as Record<TransportColumnKey, TransportFilter>;
}

const transportFilters = reactive<Record<TransportColumnKey, TransportFilter>>(createDefaultTransportFilters());
const tabStates = new Map<string, TransportTabState>();
const dragState = reactive<{ fromId: string | null; overId: string | null }>({ fromId: null, overId: null });
let orderSaveTimer: number | undefined;
let savedOrderProducts: CourseProductItem[] = [];

const driverNameById = computed(() => new Map(drivers.value.map((driver) => [String(driver.id), driver.driverName])));
const courseLabel = (row: DataSummaryRow) => row.courseProductName || row.detail || row.registerClass || '—';
const classLabel = (row: DataSummaryRow) => row.className || row.registerClass || '—';
const isOneOnOneRow = (row: DataSummaryRow) => row.courseType?.toUpperCase() === 'ONE_ON_ONE';
const transportRows = computed(() => rows.value.filter((row) => row.pickupRequired || row.dropoffRequired));
const classOptions = computed(() => Array.from(new Set(transportRows.value.map(classLabel))).sort());

const courseTabs = computed<TransportCourseTab[]>(() => {
  const search = keyword.value.trim().toLowerCase();
  const grouped = new Map<string, TransportCourseTab>();
  const rowKeysByCourse = new Map<string, Set<string>>();
  for (const row of transportRows.value) {
    const course = courseLabel(row);
    const className = classLabel(row);
    if (classFilter.value && className !== classFilter.value) continue;
    const text = [course, className, periodLabel(row), row.campusName, row.studentName, row.grade,
      driverNameForRow(row, 'pickup'), driverNameForRow(row, 'dropoff'), row.pickupAddress, row.dropoffAddress]
      .filter(Boolean).join(' ').toLowerCase();
    if (search && !text.includes(search)) continue;
    const key = String(row.courseProductId ?? course);
    const tab = grouped.get(key) ?? { key, course, showClass: false, classNames: [], rows: [], pickupRows: [], dropoffRows: [] };
    const seenRows = rowKeysByCourse.get(key) ?? new Set<string>();
    tab.showClass ||= !isOneOnOneRow(row);
    if (!tab.classNames.includes(className)) tab.classNames.push(className);
    if (!seenRows.has(rowKey(row))) {
      seenRows.add(rowKey(row));
      rowKeysByCourse.set(key, seenRows);
      tab.rows.push(row);
      if (row.pickupRequired) tab.pickupRows.push(row);
      if (row.dropoffRequired) tab.dropoffRows.push(row);
    }
    grouped.set(key, tab);
  }
  const orderIndex = new Map(products.value.map((product, index) => [String(product.id), index]));
  return Array.from(grouped.values()).sort((left, right) => {
    const leftIndex = orderIndex.get(left.key);
    const rightIndex = orderIndex.get(right.key);
    if (leftIndex != null && rightIndex != null) return leftIndex - rightIndex;
    if (leftIndex != null) return -1;
    if (rightIndex != null) return 1;
    return left.course.localeCompare(right.course);
  });
});

const selectedCourse = computed(() => courseTabs.value.find((tab) => tab.key === activeCourseKey.value) ?? courseTabs.value[0] ?? null);
const displayedCourseKey = computed(() => selectedCourse.value?.key ?? '');
const pickupSortState = reactive<TransportSortState>({ key: null, direction: 'asc' });
const dropoffSortState = reactive<TransportSortState>({ key: null, direction: 'asc' });
const filteredPickupRows = computed(() => sortTransportRows(
  selectedCourse.value?.pickupRows.filter((row) => matchesTransportFilters(row, 'pickup')) ?? [],
  pickupSortState,
  'pickup',
));
const filteredDropoffRows = computed(() => sortTransportRows(
  selectedCourse.value?.dropoffRows.filter((row) => matchesTransportFilters(row, 'dropoff')) ?? [],
  dropoffSortState,
  'dropoff',
));

function rowKey(row: DataSummaryRow) {
  return `${row.orderId}-${row.orderItemId ?? 'order'}-${row.studentId}-${row.participantIndex ?? 1}`;
}

async function loadRows() {
  loading.value = true;
  const [productsResult, rowsResult, driversResult, assignmentsResult] = await Promise.allSettled([
    fetchAllCourseProducts(),
    fetchDataSummary({ transportOnly: true }),
    fetchTransportDrivers(),
    fetchTransportDriverAssignments(),
  ]);
  if (productsResult.status === 'fulfilled') {
    products.value = productsResult.value.data.data ?? [];
    savedOrderProducts = [...products.value];
  } else {
    products.value = [];
    ElMessage.error(extractErrorMessage(productsResult.reason));
  }
  if (rowsResult.status === 'fulfilled') {
    rows.value = rowsResult.value.data.data?.rows ?? [];
  } else {
    rows.value = [];
    ElMessage.error(extractErrorMessage(rowsResult.reason));
  }
  if (driversResult.status === 'fulfilled') {
    drivers.value = driversResult.value.data.data ?? [];
  } else {
    drivers.value = [];
    ElMessage.error(extractErrorMessage(driversResult.reason));
  }
  if (assignmentsResult.status === 'fulfilled') {
    replaceDriverAssignments(assignmentsResult.value.data.data ?? []);
  } else {
    clearDriverAssignments();
    ElMessage.error(extractErrorMessage(assignmentsResult.reason));
  }
  loading.value = false;
  ensureActiveCourse();
}

function exportExcel() {
  const course = selectedCourse.value;
  if (!course) return;
  const workbook = XLSX.utils.book_new();
  const exportRows = (items: DataSummaryRow[], addressKey: 'pickupAddress' | 'dropoffAddress', addressLabel: string) => items.map((row) => {
    const exportRow: Record<string, string> = {
      Driver: driverNameForRow(row, addressKey === 'pickupAddress' ? 'pickup' : 'dropoff'),
      Course: course.course,
      Period: periodLabel(row),
      Campus: row.campusName || '',
      Student: row.studentName || '',
      Grade: row.grade || '',
    };
    if (course.showClass) exportRow.Class = classLabel(row);
    exportRow.Phone = formatUsPhoneDisplay(row.phone || row.phone2);
    exportRow[addressLabel] = row[addressKey] || '';
    return exportRow;
  });
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(exportRows(filteredPickupRows.value, 'pickupAddress', 'Pickup address')), 'Pickup');
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(exportRows(filteredDropoffRows.value, 'dropoffAddress', 'Drop-off address')), 'Drop-off');
  XLSX.writeFile(workbook, `transport-${course.course}.xlsx`);
}

function handleMobileAction(command: string) {
  if (command === 'drivers') openDriverDialog();
  if (command === 'export') exportExcel();
}

function ensureActiveCourse() {
  if (!courseTabs.value.length) {
    activeCourseKey.value = '';
    return;
  }
  if (!courseTabs.value.some((tab) => tab.key === activeCourseKey.value)) {
    activeCourseKey.value = courseTabs.value[0].key;
    restoreTabState(tabStates.get(activeCourseKey.value));
  }
}

function snapshotTransportFilters(): Record<TransportColumnKey, TransportFilter> {
  return Object.fromEntries(
    Object.entries(transportFilters).map(([key, filter]) => [key, {
      text: filter.text,
      selected: [...filter.selected],
    }]),
  ) as Record<TransportColumnKey, TransportFilter>;
}

function restoreTabState(state?: TransportTabState) {
  const next = state?.filters ?? createDefaultTransportFilters();
  (Object.keys(transportFilters) as TransportColumnKey[]).forEach((key) => {
    Object.assign(transportFilters[key], {
      text: next[key]?.text ?? '',
      selected: [...(next[key]?.selected ?? [])],
    });
  });
}

function selectCourseTab(tabKey: string) {
  if (tabKey === activeCourseKey.value) return;
  if (activeCourseKey.value) tabStates.set(activeCourseKey.value, { filters: snapshotTransportFilters() });
  activeCourseKey.value = tabKey;
  restoreTabState(tabStates.get(tabKey));
}

function tableColumns(table: TransportTable) {
  const columns = table === 'pickup' ? pickupColumns : dropoffColumns;
  return columns.filter((column) => column.key !== 'class' || selectedCourse.value?.showClass);
}

function transportCellValue(row: DataSummaryRow, key: TransportColumnKey, table: TransportTable = 'pickup'): string {
  switch (key) {
    case 'period': return periodLabel(row);
    case 'campus': return row.campusName || '—';
    case 'student': return row.studentName || '—';
    case 'grade': return row.grade || '—';
    case 'class': return row.className || row.registerClass || '—';
    case 'phone': return formatUsPhoneDisplay(row.phone || row.phone2) || '—';
    case 'driver': return driverNameForRow(row, table);
    case 'pickupAddress': return row.pickupAddress || '—';
    case 'dropoffAddress': return row.dropoffAddress || '—';
  }
}

function driverAssignmentKey(row: DataSummaryRow, table: TransportTable): string {
  return `${rowKey(row)}-${table}`;
}

function driverIdForRow(row: DataSummaryRow, table: TransportTable): string {
  return driverAssignments[driverAssignmentKey(row, table)] ?? '';
}

function driverNameForRow(row: DataSummaryRow, table: TransportTable): string {
  return driverNameById.value.get(driverIdForRow(row, table)) || '—';
}

function replaceDriverAssignments(assignments: Array<{
  assignmentKey: string;
  driverId?: string | number;
  transportType?: 'PICKUP' | 'DROPOFF' | 'BOTH';
}>) {
  clearDriverAssignments();
  assignments.forEach((assignment) => {
    if (assignment.assignmentKey && assignment.driverId != null) {
      if (assignment.transportType === 'BOTH' || !assignment.transportType) {
        driverAssignments[`${assignment.assignmentKey}-pickup`] = String(assignment.driverId);
        driverAssignments[`${assignment.assignmentKey}-dropoff`] = String(assignment.driverId);
      } else {
        driverAssignments[assignment.assignmentKey] = String(assignment.driverId);
      }
    }
  });
}

function clearDriverAssignments() {
  Object.keys(driverAssignments).forEach((key) => delete driverAssignments[key]);
}

async function handleDriverChange(row: DataSummaryRow, table: TransportTable, value: string | number | null) {
  const key = driverAssignmentKey(row, table);
  const previousDriverId = driverAssignments[key] ?? '';
  const nextDriverId = value == null || value === '' ? '' : String(value);
  if (nextDriverId) {
    driverAssignments[key] = nextDriverId;
  } else {
    delete driverAssignments[key];
  }
  try {
    await saveTransportDriverAssignment({
      orderId: row.orderId,
      orderItemId: row.orderItemId,
      studentId: row.studentId,
      participantIndex: row.participantIndex,
      transportType: table === 'pickup' ? 'PICKUP' : 'DROPOFF',
      driverId: nextDriverId || null,
    });
  } catch (error) {
    if (previousDriverId) {
      driverAssignments[key] = previousDriverId;
    } else {
      delete driverAssignments[key];
    }
    ElMessage.error(extractErrorMessage(error));
  }
}

function openDriverDialog() {
  editingDriverId.value = null;
  driverForm.driverName = '';
  driverDialogVisible.value = true;
}

function editDriver(driver: TransportDriver) {
  editingDriverId.value = String(driver.id);
  driverForm.driverName = driver.driverName;
}

function cancelDriverEdit() {
  editingDriverId.value = null;
  driverForm.driverName = '';
}

async function saveDriver() {
  const name = driverForm.driverName.trim();
  if (!name) {
    ElMessage.warning('Driver name is required.');
    return;
  }
  driverSaving.value = true;
  try {
    if (editingDriverId.value) {
      await updateTransportDriver(editingDriverId.value, { driverName: name });
    } else {
      await createTransportDriver({ driverName: name });
    }
    await loadDrivers();
    cancelDriverEdit();
    ElMessage.success('Driver list updated.');
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    driverSaving.value = false;
  }
}

async function removeDriver(driver: TransportDriver) {
  try {
    await ElMessageBox.confirm(`Delete driver “${driver.driverName}”? Existing assignments will be cleared.`, 'Delete driver', {
      type: 'warning',
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
    });
    await deleteTransportDriver(driver.id);
    await Promise.all([loadDrivers(), loadAssignments()]);
    if (editingDriverId.value === String(driver.id)) cancelDriverEdit();
    ElMessage.success('Driver deleted.');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(extractErrorMessage(error));
  }
}

async function loadDrivers() {
  const result = await fetchTransportDrivers();
  drivers.value = result.data.data ?? [];
}

async function loadAssignments() {
  const result = await fetchTransportDriverAssignments();
  replaceDriverAssignments(result.data.data ?? []);
}

function periodLabel(row: DataSummaryRow): string {
  const start = formatPeriodDate(row.periodStartDate);
  const end = formatPeriodDate(row.periodEndDate);
  if (start && end) return `${start} - ${end}`;
  return start || end || row.periodLabel || '—';
}

function formatPeriodDate(value?: string): string {
  const match = value?.slice(0, 10).match(/^(\d{4})-(\d{2})-(\d{2})$/);
  return match ? `${Number(match[2])}/${Number(match[3])}/${match[1]}` : '';
}

function transportSelectOptions(key: TransportColumnKey, table: TransportTable) {
  const values = (selectedCourse.value?.rows ?? [])
    .map((row) => transportCellValue(row, key, table))
    .filter((value) => value !== '—' || key === 'driver');
  return Array.from(new Set(values)).sort((left, right) => left.localeCompare(right));
}

function matchesTransportFilters(row: DataSummaryRow, table: TransportTable) {
  return (Object.keys(transportFilters) as TransportColumnKey[]).every((key) => {
    if ((table === 'pickup' && key === 'dropoffAddress') || (table === 'dropoff' && key === 'pickupAddress')) return true;
    const filter = transportFilters[key];
    const value = transportCellValue(row, key, table);
    if (filter.selected.length > 0 && !filter.selected.includes(value)) return false;
    const keywordValue = filter.text.trim().toLowerCase();
    if (key === 'phone') {
      return matchesPhoneSearch(row.phone || row.phone2, keywordValue);
    }
    return !keywordValue || value.toLowerCase().includes(keywordValue);
  });
}

function hasTransportFilter(key: TransportColumnKey) {
  const filter = transportFilters[key];
  return Boolean(filter.text.trim() || filter.selected.length);
}

function sortStateForTable(table: TransportTable) {
  return table === 'pickup' ? pickupSortState : dropoffSortState;
}

function isColumnSorted(key: TransportColumnKey, table: TransportTable, direction?: TransportSortDirection) {
  const state = sortStateForTable(table);
  return state.key === key && (!direction || state.direction === direction);
}

function setSort(key: TransportColumnKey, table: TransportTable, direction: TransportSortDirection) {
  const state = sortStateForTable(table);
  state.key = key;
  state.direction = direction;
}

function clearSort(key: TransportColumnKey, table: TransportTable) {
  const state = sortStateForTable(table);
  if (state.key === key) state.key = null;
}

function sortTransportRows(items: DataSummaryRow[], state: TransportSortState, table: TransportTable) {
  if (!state.key) return items;
  const key = state.key;
  const direction = state.direction === 'asc' ? 1 : -1;
  return items
    .map((row, index) => ({ row, index }))
    .sort((left, right) => {
      const leftValue = transportSortValue(left.row, key, table);
      const rightValue = transportSortValue(right.row, key, table);
      const leftEmpty = leftValue === '—';
      const rightEmpty = rightValue === '—';
      if (leftEmpty || rightEmpty) {
        if (leftEmpty && rightEmpty) return left.index - right.index;
        return leftEmpty ? 1 : -1;
      }
      return leftValue.localeCompare(rightValue, undefined, { numeric: true, sensitivity: 'base' }) * direction
        || left.index - right.index;
    })
    .map(({ row }) => row);
}

function transportSortValue(row: DataSummaryRow, key: TransportColumnKey, table: TransportTable): string {
  if (key === 'period') {
    return row.periodStartDate || row.periodEndDate || row.periodLabel || '—';
  }
  return transportCellValue(row, key, table);
}

function clearTransportFilter(key: TransportColumnKey, table: TransportTable) {
  Object.assign(transportFilters[key], createEmptyTransportFilter());
  clearSort(key, table);
}

function clearAllTransportFilters() {
  (Object.keys(transportFilters) as TransportColumnKey[]).forEach(clearTransportFilter);
  pickupSortState.key = null;
  dropoffSortState.key = null;
}

function canMoveCourseTab(tab: TransportCourseTab) {
  return canDragTabs.value && products.value.some((product) => String(product.id) === tab.key);
}

function handleDragStart(tab: TransportCourseTab, event: DragEvent) {
  if (!canMoveCourseTab(tab)) {
    event.preventDefault();
    return;
  }
  dragState.fromId = tab.key;
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', tab.key);
  }
}

function handleDragEnter(tab: TransportCourseTab) {
  if (!dragState.fromId || !canMoveCourseTab(tab) || tab.key === dragState.fromId) return;
  dragState.overId = tab.key;
}

function handleDragOver(tab: TransportCourseTab) {
  if (!dragState.fromId || !canMoveCourseTab(tab) || tab.key === dragState.fromId) return;
  dragState.overId = tab.key;
}

function handleDrop(tab: TransportCourseTab) {
  const fromId = dragState.fromId;
  const overId = canMoveCourseTab(tab) && tab.key !== fromId ? tab.key : null;
  handleDragEnd();
  if (!fromId || !overId) return;
  const fromIndex = products.value.findIndex((product) => String(product.id) === fromId);
  const toIndex = products.value.findIndex((product) => String(product.id) === overId);
  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return;
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

function scheduleOrderSave() {
  window.clearTimeout(orderSaveTimer);
  orderSaveTimer = window.setTimeout(() => { void persistTabOrder(); }, 300);
}

async function persistTabOrder() {
  if (!products.value.length) return;
  savingOrder.value = true;
  const requested = [...products.value];
  try {
    await saveCourseProductTabOrder(requested.map((product) => product.id));
    savedOrderProducts = requested;
    ElMessage.success('Tab order saved.');
  } catch (error) {
    products.value = [...savedOrderProducts];
    ElMessage.error(extractErrorMessage(error));
  } finally {
    savingOrder.value = false;
  }
}

watch(courseTabs, ensureActiveCourse);

onMounted(loadRows);
onBeforeUnmount(() => {
  window.clearTimeout(orderSaveTimer);
});
</script>

<style scoped>
.transport-tab-bar { margin: 0 0 12px; }
.transport-scroll { padding: 0 0 18px; }
.transport-roster-section__heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; color: #263b44; font-size: 13px; font-weight: 700; }
.transport-roster-section__heading span { color: #667780; font-size: 11px; font-weight: 500; }
.transport-roster-section { margin-top: 12px; overflow: hidden; border: 1px solid #b8c5cb; border-radius: 6px; background: #fff; }
.transport-roster-section--dropoff { margin-top: 40px; }
.transport-roster-section__heading { border-bottom: 1px solid #b8c5cb; background: #f3f5f6; }
.transport-table-scroll { width: 100%; max-width: 100%; overflow-x: auto; overflow-y: hidden; scrollbar-gutter: stable; scrollbar-color: #87949b #e7ecef; scrollbar-width: thin; }
.transport-table-scroll::-webkit-scrollbar { height: 12px; }
.transport-table-scroll::-webkit-scrollbar-track { background: #e7ecef; }
.transport-table-scroll::-webkit-scrollbar-thumb { border: 2px solid #e7ecef; border-radius: 8px; background: #87949b; }
.transport-table-scroll::-webkit-scrollbar-thumb:hover { background: #52636c; }
.transport-detail-table { border: 0; }
.transport-page .workbook-table th, .transport-page .workbook-table td { text-align: center; }
.transport-page .transport-detail-table th { border-color: #87949b; background: #e7e7e7; color: #1f2b31; }
.transport-page .transport-detail-table td { border-color: #c5cdd2; background: #fff; color: #1f2b31; }
.transport-page .transport-driver-select { width: 160px; }
.transport-page .transport-driver-select :deep(.el-input__wrapper) { box-shadow: none; background: transparent; }
.driver-editor { display: flex; align-items: center; gap: 8px; }
.driver-editor .el-input { flex: 1; }
.driver-list { margin-top: 16px; border: 1px solid #c5cdd2; border-radius: 5px; }
.driver-list__row { display: flex; align-items: center; justify-content: space-between; min-height: 42px; padding: 0 10px 0 12px; border-bottom: 1px solid #e1e7ea; color: #263b44; }
.driver-list__row:last-child { border-bottom: 0; }
.driver-list__actions { display: inline-flex; align-items: center; gap: 2px; }
.driver-list__empty { margin-top: 16px; padding: 24px; border: 1px dashed #c5cdd2; color: #667780; text-align: center; }
.transport-page .workbook-header-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 100%;
  padding: 0 9px;
  overflow: hidden;
  border: 0;
  border-radius: 0;
  appearance: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: center;
}
.transport-page .workbook-header-button:hover,
.transport-page .workbook-header--active .workbook-header-button {
  background: #d5dadd;
  color: #162126;
}
.transport-page .workbook-header-button > span:first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.transport-page .workbook-header-icons { display: inline-flex; flex: 0 0 auto; align-items: center; }
.transport-page .workbook-filter-arrow {
  width: 0;
  height: 0;
  border-top: 5px solid #65747b;
  border-right: 4px solid transparent;
  border-left: 4px solid transparent;
}
.transport-page .workbook-header--active .workbook-filter-arrow { border-top-color: #1f2b31; }
.transport-page .transport-detail-table tbody tr:last-child td { border-bottom: 0; }
.transport-page .transport-detail-table .workbook-data-row:hover td { background: #fff; }
.transport-page .transport-detail-table td:last-child { white-space: normal; line-height: 1.4; }
.transport-empty { margin-top: 12px; padding: 40px; border: 1px solid #b8c5cb; border-radius: 6px; color: #52636c; text-align: center; }

@media (max-width: 640px) {
  .transport-page { min-height: 0; }
  .transport-scroll { padding-bottom: 12px; }
  .transport-roster-section { min-width: 0; }
  .transport-roster-section__heading { align-items: flex-start; flex-direction: column; gap: 2px; }
  .transport-table-scroll { border-radius: 4px; }
  .mobile-pinch-scroll { touch-action: pan-x pan-y; -webkit-overflow-scrolling: touch; }
  .mobile-toolbar-actions { display: inline-flex; align-items: center; gap: 8px; }
  .mobile-search-panel .workbook-toolbar__stats { display: flex; flex-wrap: wrap; gap: 12px 18px; margin-top: 12px; }
  .transport-detail-table { min-width: 760px; }
  .transport-page .transport-driver-select { width: 150px; }
  .transport-empty { padding: 24px 14px; }
}
</style>
