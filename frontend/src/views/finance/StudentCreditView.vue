<template>
  <div class="data-summary-page" v-loading="loading">
    <div class="workbook-toolbar">
      <div class="workbook-toolbar__month">
        <template v-if="appStore.isMobileViewport">
          <div class="mobile-search-heading">
            <span class="workbook-toolbar__label">Student Credit</span>
            <div class="mobile-toolbar-actions">
              <button type="button" class="mobile-search-trigger" :aria-expanded="mobileSearchExpanded" aria-label="Search students" @click="mobileSearchExpanded = !mobileSearchExpanded">
                <el-icon><Search /></el-icon>
              </button>
              <el-dropdown trigger="click" @command="handleMobileAction">
                <button type="button" class="mobile-search-trigger" aria-label="More student credit actions"><el-icon><MoreFilled /></el-icon></button>
                <template #dropdown>
                  <el-dropdown-menu><el-dropdown-item command="export" :disabled="studentGroups.length === 0">Export Excel</el-dropdown-item></el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <section v-show="mobileSearchExpanded" class="mobile-search-panel">
            <div class="mobile-search-panel__header">
              <span>Search students</span>
              <button type="button" aria-label="Close search" @click="mobileSearchExpanded = false">
                <el-icon><Close /></el-icon>
              </button>
            </div>
            <div class="student-credit-search-form">
              <el-input v-model="keyword" clearable placeholder="Search student or phone" @keyup.enter="loadBalances" />
              <el-button type="primary" @click="loadBalances">Search</el-button>
            </div>
          </section>
          <div class="workbook-toolbar__stats">
            <span class="workbook-toolbar__stat"><em>Students</em><strong>{{ studentGroups.length }}</strong></span>
            <span class="workbook-toolbar__stat"><em>Available credit</em><strong>{{ formatMoney(totalCredit) }}</strong></span>
          </div>
        </template>
        <template v-else>
          <span class="workbook-toolbar__label">Student Credit</span>
          <el-input v-model="keyword" class="workbook-toolbar__select" clearable placeholder="Search student or phone" @keyup.enter="loadBalances" />
          <el-button type="primary" size="small" @click="loadBalances">Search</el-button>
        </template>
      </div>
      <div v-if="!appStore.isMobileViewport" class="workbook-toolbar__stats">
        <span class="workbook-toolbar__stat"><em>Students</em><strong>{{ studentGroups.length }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Available Credit</em><strong>{{ formatMoney(totalCredit) }}</strong></span>
      </div>
      <div v-if="!appStore.isMobileViewport" class="workbook-toolbar__export">
        <el-button type="primary" size="small" :disabled="studentGroups.length === 0" @click="exportExcel">Export Excel</el-button>
      </div>
    </div>

    <div ref="creditScrollRef" class="workbook-scroll mobile-pinch-scroll">
      <table class="workbook-table student-credit-table" :style="{ zoom: appStore.isMobileViewport ? mobileTableScale : undefined }">
        <colgroup>
          <col v-for="column in creditColumns" :key="column.key" :style="{ width: `${column.width}px` }" />
        </colgroup>
        <thead>
          <tr>
            <th
              v-for="column in creditColumns"
              :key="column.key"
              :class="{ 'workbook-header--active': creditSort.key === column.key }"
            >
              <el-popover placement="bottom-start" trigger="click" :width="260" popper-class="workbook-filter-popper">
                <template #reference>
                  <button class="workbook-header-button" type="button" :aria-label="`Sort ${column.label}`">
                    <span>{{ column.label }}</span>
                    <span class="workbook-header-icons">
                      <span v-if="creditSort.key === column.key" class="workbook-sort-indicator">
                        {{ creditSort.direction === 'asc' ? '↑' : '↓' }}
                      </span>
                      <span class="workbook-filter-arrow" aria-hidden="true" />
                    </span>
                  </button>
                </template>
                <div class="workbook-filter">
                  <strong>Sort {{ column.label }}</strong>
                  <div class="workbook-filter-section">
                    <el-button-group class="workbook-sort-buttons">
                      <el-button size="small" :type="isCreditColumnSorted(column.key, 'asc') ? 'primary' : 'default'" @click="setCreditSort(column.key, 'asc')">Asc</el-button>
                      <el-button size="small" :type="isCreditColumnSorted(column.key, 'desc') ? 'primary' : 'default'" @click="setCreditSort(column.key, 'desc')">Desc</el-button>
                      <el-button size="small" @click="clearCreditSort(column.key)">Clear</el-button>
                    </el-button-group>
                  </div>
                </div>
              </el-popover>
            </th>
          </tr>
        </thead>
        <tbody v-if="sortedStudentGroups.length">
          <tr v-for="group in sortedStudentGroups" :key="String(group.studentId)" class="workbook-data-row">
            <td>{{ group.studentName || '—' }}</td>
            <td>{{ formatUsPhoneDisplay(group.guardianPhone) || '—' }}</td>
            <td class="workbook-cell--money">
              <span class="credit-balance-cell">
                <span>{{ formatMoney(group.creditBalance) }}</span>
                <el-button
                  class="credit-edit-button"
                  type="primary"
                  link
                  :icon="EditPen"
                  aria-label="Edit student credit"
                  title="Edit student credit"
                  @click="openEditDialog(group)"
                />
              </span>
            </td>
            <td>{{ formatDate(group.lastOccurredAt) }}</td>
          </tr>
        </tbody>
        <tbody v-else-if="!loading"><tr class="workbook-empty-row"><td :colspan="creditColumns.length">No available student Credit.</td></tr></tbody>
      </table>
    </div>
  </div>

  <el-dialog v-model="editDialogVisible" title="Edit Student Credit" width="440px" :close-on-click-modal="false">
    <el-form v-if="editingGroup" label-position="top" @submit.prevent="saveCreditAdjustment">
      <div class="credit-edit-student">
        <strong>{{ editingGroup.studentName || '—' }}</strong>
      </div>
      <el-form-item label="Current Credit">
        <el-input :model-value="formatMoney(editingGroup.creditBalance)" disabled />
      </el-form-item>
      <el-form-item label="New Credit" required>
        <el-input-number
          v-model="editForm.creditBalance"
          :min="0"
          :precision="0"
          :controls="false"
          class="credit-edit-amount"
          placeholder="Enter new balance"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">Cancel</el-button>
      <el-button type="primary" :loading="saving" @click="saveCreditAdjustment">Save</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import * as XLSX from 'xlsx';
import { Close, EditPen, MoreFilled, Search } from '@element-plus/icons-vue';
import { adjustStudentCredit, fetchStudentCreditBalances, type StudentCreditBalanceItem } from '@/api/student-credit';
import { extractErrorMessage } from '@/api/http';
import { ElMessage } from 'element-plus';
import { formatUsPhoneDisplay } from '@/utils/phone-format';
import { useAppStore } from '@/stores/app';
import { useMobileTablePinchZoom } from '@/composables/useMobileTablePinchZoom';

type StudentCreditGroup = Omit<StudentCreditBalanceItem, 'studentNo' | 'grade' | 'studentClass'>;
type CreditColumnKey = 'student' | 'phone' | 'balance' | 'activity';
type CreditSortDirection = 'asc' | 'desc';

interface CreditColumn {
  key: CreditColumnKey;
  label: string;
  width: number;
}

const creditColumns: CreditColumn[] = [
  { key: 'student', label: 'Student', width: 260 },
  { key: 'phone', label: 'Guardian phone', width: 220 },
  { key: 'balance', label: 'Credit balance', width: 220 },
  { key: 'activity', label: 'Last activity', width: 200 },
];

const studentGroups = ref<StudentCreditGroup[]>([]);
const appStore = useAppStore();
const mobileSearchExpanded = ref(false);
const creditScrollRef = ref<HTMLElement | null>(null);
const { scale: mobileTableScale } = useMobileTablePinchZoom(creditScrollRef, computed(() => appStore.isMobileViewport));
const keyword = ref('');
const loading = ref(false);
const editDialogVisible = ref(false);
const editingGroup = ref<StudentCreditGroup | null>(null);
const saving = ref(false);
const creditSort = reactive<{ key: CreditColumnKey | null; direction: CreditSortDirection }>({
  key: null,
  direction: 'asc',
});
const editForm = reactive({
  creditBalance: 0,
});
const totalCredit = computed(() => studentGroups.value.reduce((sum, row) => sum + Number(row.creditBalance || 0), 0));
const sortedStudentGroups = computed(() => {
  const { key, direction } = creditSort;
  if (!key) return studentGroups.value;
  const multiplier = direction === 'asc' ? 1 : -1;
  return [...studentGroups.value].sort((left, right) => {
    const leftValue = creditSortValue(left, key);
    const rightValue = creditSortValue(right, key);
    if (leftValue == null && rightValue == null) return 0;
    if (leftValue == null) return 1;
    if (rightValue == null) return -1;
    if (typeof leftValue === 'number' && typeof rightValue === 'number') {
      return (leftValue - rightValue) * multiplier;
    }
    return String(leftValue).localeCompare(String(rightValue), undefined, { numeric: true, sensitivity: 'base' }) * multiplier;
  });
});

function creditSortValue(group: StudentCreditGroup, key: CreditColumnKey): number | string | null {
  switch (key) {
    case 'student': return group.studentName || null;
    case 'phone': return group.guardianPhone || null;
    case 'balance': return Number(group.creditBalance || 0);
    case 'activity': return group.lastOccurredAt || null;
  }
  return null;
}

function setCreditSort(key: CreditColumnKey, direction: CreditSortDirection) {
  creditSort.key = key;
  creditSort.direction = direction;
}

function clearCreditSort(key: CreditColumnKey) {
  if (creditSort.key === key) creditSort.key = null;
}

function isCreditColumnSorted(key: CreditColumnKey, direction: CreditSortDirection) {
  return creditSort.key === key && creditSort.direction === direction;
}

function mergeStudentRows(items: StudentCreditBalanceItem[]) {
  const grouped = new Map<string, StudentCreditGroup>();
  for (const item of items.filter((candidate) => Number(candidate.creditBalance || 0) > 0)) {
    const key = String(item.studentId);
    const current = grouped.get(key);
    if (!current) {
      grouped.set(key, {
        studentId: item.studentId,
        studentName: item.studentName,
        guardianPhone: item.guardianPhone,
        creditBalance: Number(item.creditBalance || 0),
        lastOccurredAt: item.lastOccurredAt,
      });
      continue;
    }
    current.creditBalance = Math.max(Number(current.creditBalance || 0), Number(item.creditBalance || 0));
    if (item.lastOccurredAt && (!current.lastOccurredAt || item.lastOccurredAt > current.lastOccurredAt)) {
      current.lastOccurredAt = item.lastOccurredAt;
    }
  }
  return Array.from(grouped.values());
}

function formatMoney(value?: number) {
  return `$${Number(value || 0).toFixed(2)}`;
}

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleDateString() : '—';
}

function openEditDialog(group: StudentCreditGroup) {
  editingGroup.value = group;
  editForm.creditBalance = Number(Number(group.creditBalance || 0).toFixed(2));
  editDialogVisible.value = true;
}

async function saveCreditAdjustment() {
  if (!editingGroup.value) return;
  const targetBalance = Number(editForm.creditBalance);
  if (!Number.isInteger(targetBalance) || targetBalance < 0) {
    ElMessage.warning('Credit balance must be a non-negative whole dollar amount.');
    return;
  }
  saving.value = true;
  try {
    await adjustStudentCredit(String(editingGroup.value.studentId), {
      creditBalance: targetBalance,
    });
    ElMessage.success('Student Credit updated.');
    editDialogVisible.value = false;
    await loadBalances();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function loadBalances() {
  loading.value = true;
  try {
    studentGroups.value = mergeStudentRows((await fetchStudentCreditBalances(keyword.value.trim())).data.data ?? []);
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

function exportExcel() {
  const sheet = XLSX.utils.json_to_sheet(sortedStudentGroups.value.map((group) => ({
    Student: group.studentName,
    'Guardian phone': formatUsPhoneDisplay(group.guardianPhone),
    'Credit balance': Number(group.creditBalance || 0),
    'Last activity': formatDate(group.lastOccurredAt),
  })));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, 'Student Credit');
  XLSX.writeFile(workbook, 'student-credit.xlsx');
}

function handleMobileAction(command: string) {
  if (command === 'export') exportExcel();
}

onMounted(loadBalances);
</script>

<style scoped>
.student-credit-table th,
.student-credit-table td,
.student-credit-table .workbook-cell--money {
  text-align: center;
}

.student-credit-table .workbook-header-button {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 8px;
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: center;
}

.student-credit-table .workbook-header-button > span:first-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.student-credit-table .workbook-header-icons { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 3px; }
.student-credit-table .workbook-filter-arrow { width: 0; height: 0; border-top: 5px solid currentColor; border-right: 4px solid transparent; border-left: 4px solid transparent; opacity: .75; }
.student-credit-table .workbook-sort-indicator { font-size: 10px; }

.credit-edit-student {
  margin-bottom: 18px;
  color: #64748b;
}

.credit-edit-student strong {
  color: #172033;
  font-size: 16px;
}

.credit-balance-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 96px;
  font-variant-numeric: tabular-nums;
}

.credit-edit-button {
  width: 24px;
  height: 24px;
  padding: 3px;
  color: #376451;
  vertical-align: middle;
}

.credit-edit-button:hover {
  color: #1f8a63;
  background: #e5f3eb;
  border-radius: 5px;
}

.student-credit-search-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px;
  gap: 8px;
}

.credit-edit-amount {
  width: 100%;
}

html.mobile-layout & {
  .data-summary-page { height: 100%; min-height: 0; flex: 1 1 0; overflow: hidden; }
  .data-summary-page .workbook-toolbar { flex: 0 0 auto; gap: 6px; padding: 6px 8px; }
  .data-summary-page .workbook-scroll { display: block; min-height: 0; flex: 1 1 0; overflow: auto; border: 1px solid #9eafa7; overscroll-behavior: contain; -webkit-overflow-scrolling: touch; }
  .student-credit-table { display: table; width: max-content; min-width: 100%; margin: 0; border-collapse: separate; border-spacing: 0; table-layout: fixed; color: #263b44; font-size: 11px; }
  .student-credit-table th,
  .student-credit-table td { height: 34px; padding: 6px 7px; overflow: hidden; border-right: 1px solid #c3d3df; border-bottom: 1px solid #c3d3df; background: #dcebf8; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
  .student-credit-table th { border-right: 1px solid #517768; border-bottom: 1px solid #517768; }
  .student-credit-table th { position: sticky; top: 0; z-index: 4; height: 38px; padding: 0; border-color: #517768; background: #376451; color: #fff; font-size: 11px; }
  .student-credit-table .workbook-header-button { justify-content: center; gap: 5px; padding: 0 6px; }
  .student-credit-table td:first-child { position: sticky; left: 0; z-index: 2; background: #dcebf8; box-shadow: 2px 0 0 rgb(81 119 104 / 25%); }
  .student-credit-table th:first-child { position: sticky; left: 0; z-index: 6; background: #376451; }
  .student-credit-table .workbook-data-row:hover td { background: #d1e5f6; }
  .student-credit-table .credit-balance-cell { min-width: 0; gap: 3px; }
  .credit-edit-button { width: 30px; height: 30px; }
  .credit-edit-student { padding: 12px; border-radius: 6px; background: #f8fafc; }
  .student-credit-search-form .el-button { min-height: 36px; margin: 0; }
  .mobile-toolbar-actions { display: inline-flex; align-items: center; gap: 8px; }
  .mobile-pinch-scroll { touch-action: pan-x pan-y; -webkit-overflow-scrolling: touch; }
  .mobile-search-panel .workbook-toolbar__stats { display: flex; flex-wrap: wrap; gap: 12px 18px; margin-top: 12px; }
}

</style>
