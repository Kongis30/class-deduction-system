<template>
  <div class="data-summary-page" v-loading="loading">
    <div class="workbook-toolbar">
      <div class="workbook-toolbar__month">
        <span class="workbook-toolbar__label">Student Credit</span>
        <el-input v-model="keyword" class="workbook-toolbar__select" clearable placeholder="Search student or phone" @keyup.enter="loadBalances" />
        <el-button type="primary" size="small" @click="loadBalances">Search</el-button>
      </div>
      <div class="workbook-toolbar__stats">
        <span class="workbook-toolbar__stat"><em>Students</em><strong>{{ studentGroups.length }}</strong></span>
        <span class="workbook-toolbar__stat"><em>Available Credit</em><strong>{{ formatMoney(totalCredit) }}</strong></span>
      </div>
      <div class="workbook-toolbar__export">
        <el-button type="primary" size="small" :disabled="studentGroups.length === 0" @click="exportExcel">Export Excel</el-button>
      </div>
    </div>

    <div class="workbook-scroll">
      <table class="workbook-table student-credit-table">
        <colgroup>
          <col style="width: 260px" />
          <col style="width: 220px" />
          <col style="width: 220px" />
          <col style="width: 200px" />
        </colgroup>
        <thead>
          <tr>
            <th>Student</th><th>Guardian phone</th><th>Credit balance</th><th>Last activity</th>
          </tr>
        </thead>
        <tbody v-if="studentGroups.length">
          <tr v-for="group in studentGroups" :key="String(group.studentId)" class="workbook-data-row">
            <td>{{ group.studentName || '—' }}</td>
            <td>{{ group.guardianPhone || '—' }}</td>
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
        <tbody v-else-if="!loading"><tr class="workbook-empty-row"><td colspan="4">No available student Credit.</td></tr></tbody>
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
          :precision="2"
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
import { EditPen } from '@element-plus/icons-vue';
import { adjustStudentCredit, fetchStudentCreditBalances, type StudentCreditBalanceItem } from '@/api/student-credit';
import { extractErrorMessage } from '@/api/http';
import { ElMessage } from 'element-plus';

type StudentCreditGroup = Omit<StudentCreditBalanceItem, 'studentNo' | 'grade' | 'studentClass'>;

const studentGroups = ref<StudentCreditGroup[]>([]);
const keyword = ref('');
const loading = ref(false);
const editDialogVisible = ref(false);
const editingGroup = ref<StudentCreditGroup | null>(null);
const saving = ref(false);
const editForm = reactive({
  creditBalance: 0,
});
const totalCredit = computed(() => studentGroups.value.reduce((sum, row) => sum + Number(row.creditBalance || 0), 0));

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
  const targetBalance = Number(Number(editForm.creditBalance || 0).toFixed(2));
  if (!Number.isFinite(targetBalance) || targetBalance < 0) {
    ElMessage.warning('Credit balance cannot be negative.');
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
  const sheet = XLSX.utils.json_to_sheet(studentGroups.value.map((group) => ({
    Student: group.studentName,
    'Guardian phone': group.guardianPhone || '',
    'Credit balance': Number(group.creditBalance || 0),
    'Last activity': formatDate(group.lastOccurredAt),
  })));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, 'Student Credit');
  XLSX.writeFile(workbook, 'student-credit.xlsx');
}

onMounted(loadBalances);
</script>

<style scoped>
.student-credit-table th,
.student-credit-table td,
.student-credit-table .workbook-cell--money {
  text-align: center;
}

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

.credit-edit-amount {
  width: 100%;
}

</style>
