<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Students</span>
        <div class="panel-header__actions">
        </div>
      </div>

      <el-form class="filters" :model="filters" inline>
        <el-form-item label="Name" class="filters__item">
          <el-input
            v-model="filters.keyword"
            placeholder="Search by name"
            clearable
            style="width: 180px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Guardian" class="filters__item">
          <el-input
            v-model="filters.guardian"
            placeholder="Search by guardian"
            clearable
            style="width: 140px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Status" class="filters__item">
          <el-select
            v-model="filters.status"
            placeholder="All"
            clearable
            style="width: 140px;"
            @change="handleFilterChange"
          >
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="product-table"
        :show-empty="true"
      >
        <el-table-column label="Student name" min-width="140">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.studentName }}</strong>
              <span>{{ row.gender }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Guardian" min-width="140">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.guardianName }}</strong>
              <span>{{ row.phone }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Guardian Email" min-width="160">
          <template #default="{ row }">
            {{ row.guardianEmail || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="Campus" min-width="120">
          <template #default="{ row }">
            {{ row.campusName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="Status" min-width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" min-width="140" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" @click="openStudentDialog(row)">Edit</el-button>
              <el-button text type="danger" @click="handleDelete(row)">Delete</el-button>
            </div>
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
    </section>

    <!-- Student Form Dialog -->
    <el-dialog
      v-model="studentDialogVisible"
      width="580px"
      :close-on-click-modal="false"
      class="student-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <h3 class="dialog-header__title">Edit student</h3>
          <el-button
            text
            size="small"
            class="dialog-header__settings"
            @click="openFieldConfig"
            title="Customize form fields"
          >
            <el-icon><Setting /></el-icon>
          </el-button>
        </div>
      </template>

      <el-form ref="studentFormRef" label-position="top" :model="studentForm" :rules="studentFormRules" :validate-on-rule-change="false" class="student-form">
        <!-- Required: Student name -->
        <div class="student-form__row">
          <el-form-item label="Student name" prop="studentName" class="student-form__full">
            <el-input v-model="studentForm.studentName" placeholder="e.g. Sophia Lin" />
          </el-form-item>
        </div>

        <!-- Gender - configurable -->
        <div v-if="fieldConfig.gender.visible" class="student-form__row">
          <el-form-item :label="fieldConfig.gender.customLabel" prop="gender" class="student-form__half">
            <el-select v-model="studentForm.gender" placeholder="Select gender" style="width: 100%;">
              <el-option label="Male" value="Male" />
              <el-option label="Female" value="Female" />
            </el-select>
          </el-form-item>
        </div>

        <!-- Guardian info - required -->
        <div class="student-form__row">
          <el-form-item label="Guardian name" prop="guardianName" class="student-form__half">
            <el-input v-model="studentForm.guardianName" placeholder="Primary guardian" />
          </el-form-item>
          <el-form-item label="Phone" prop="guardianPhone" class="student-form__half">
            <el-input v-model="studentForm.guardianPhone" placeholder="(626) 555-0101" />
          </el-form-item>
        </div>

        <!-- Guardian email - configurable -->
        <div v-if="fieldConfig.email.visible" class="student-form__row">
          <el-form-item :label="fieldConfig.email.customLabel" class="student-form__half">
            <el-input v-model="studentForm.guardianEmail" placeholder="Optional email" />
          </el-form-item>
        </div>

        <!-- Status - configurable -->
        <div v-if="fieldConfig.status.visible" class="student-form__row">
          <el-form-item :label="fieldConfig.status.customLabel" prop="status" class="student-form__half">
            <el-select v-model="studentForm.status" placeholder="Select status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>

        <!-- Notes - configurable -->
        <div v-if="fieldConfig.notes.visible" class="student-form__row">
          <el-form-item :label="fieldConfig.notes.customLabel" class="student-form__full">
            <el-input v-model="studentForm.notes" type="textarea" :rows="2" placeholder="Optional notes" />
          </el-form-item>
        </div>

        <!-- Campus - configurable -->
        <div v-if="fieldConfig.campusId.visible" class="student-form__row">
          <el-form-item :label="fieldConfig.campusId.customLabel" class="student-form__half">
            <el-select v-model="studentForm.campusId" placeholder="Select campus" clearable style="width: 100%;">
              <el-option v-for="c in campusOptions" :key="c.id" :label="c.campusName" :value="c.id" />
            </el-select>
          </el-form-item>
        </div>

        <!-- Guardian Relation - configurable -->
        <div v-if="fieldConfig.guardianRelationType.visible" class="student-form__row">
          <el-form-item :label="fieldConfig.guardianRelationType.customLabel" class="student-form__half">
            <el-select v-model="studentForm.guardianRelationType" placeholder="Select relation" clearable style="width: 100%;">
              <el-option label="Mother" value="MOTHER" />
              <el-option label="Father" value="FATHER" />
              <el-option label="Grandparent" value="GRANDPARENT" />
              <el-option label="Other" value="OTHER" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="studentDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleStudentSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Field Config Dialog -->
    <FieldConfigDialog
      v-model="fieldConfigVisible"
      config-key="studentForm"
      :fields="configurableFields"
      :default-fields="defaultFields"
      @change="handleFieldConfigChange"
    />
  </div>
</template>

<script setup lang="ts">
import { Setting } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reactive, ref, watch } from 'vue';

import FieldConfigDialog, { type FieldConfig } from '@/components/shared/FieldConfigDialog.vue';
import {
  deleteStudent,
  fetchCampuses,
  fetchStudents,
  updateStudent,
  type StudentQuery,
  type StudentSavePayload,
} from '@/api/base-data';
import type { CampusItem, StudentItem } from '@/types/domain';

const loading = ref(false);
const submitting = ref(false);
const studentDialogVisible = ref(false);
const fieldConfigVisible = ref(false);
const editingId = ref<string | null>(null);
const studentFormRef = ref();

// ─── Field Configuration ──────────────────────────────
interface FieldConfigMap {
  [key: string]: FieldConfig;
}

const defaultFieldConfig: FieldConfigMap = {
  gender: { key: 'gender', defaultLabel: 'Gender', visible: true, customLabel: 'Gender' },
  email: { key: 'email', defaultLabel: 'Guardian email', visible: true, customLabel: 'Guardian email' },
  status: { key: 'status', defaultLabel: 'Status', visible: true, customLabel: 'Status' },
  notes: { key: 'notes', defaultLabel: 'Notes', visible: false, customLabel: 'Notes' },
  campusId: { key: 'campusId', defaultLabel: 'Campus', visible: true, customLabel: 'Campus' },
  guardianRelationType: { key: 'guardianRelationType', defaultLabel: 'Relation', visible: false, customLabel: 'Relation' },
};

function loadFieldConfig(): FieldConfigMap {
  try {
    const saved = localStorage.getItem('fieldConfig:studentForm');
    if (saved) {
      const parsed = JSON.parse(saved) as FieldConfig[];
      // Filter out stale keys that no longer exist in defaultFieldConfig (e.g. "grade")
      const validKeys = new Set(Object.keys(defaultFieldConfig));
      const map: FieldConfigMap = {};
      parsed.forEach((f) => {
        if (validKeys.has(f.key)) {
          map[f.key] = f;
        }
      });
      // Merge with defaults so any new default fields not yet saved are included
      for (const key of Object.keys(defaultFieldConfig)) {
        if (!map[key]) {
          map[key] = { ...defaultFieldConfig[key] };
        }
      }
      return map;
    }
  } catch {
    // ignore
  }
  // Deep clone to avoid mutating the original
  const cloned: FieldConfigMap = {};
  for (const key of Object.keys(defaultFieldConfig)) {
    cloned[key] = { ...defaultFieldConfig[key] };
  }
  return cloned;
}

const fieldConfig = reactive<FieldConfigMap>(loadFieldConfig());

const configurableFields = ref<FieldConfig[]>(Object.values(fieldConfig));
const defaultFields = ref<FieldConfig[]>(Object.values(defaultFieldConfig));

watch(
  () => fieldConfigVisible.value,
  (val) => {
    if (val) {
      configurableFields.value = Object.values(fieldConfig);
    }
  }
);

function openFieldConfig() {
  fieldConfigVisible.value = true;
}

function handleFieldConfigChange(fields: FieldConfig[]) {
  fields.forEach((f) => {
    fieldConfig[f.key].visible = f.visible;
    fieldConfig[f.key].customLabel = f.customLabel;
  });
}

// ─── Campus options for dropdown ────────────────────
const campusOptions = ref<CampusItem[]>([]);

async function loadCampuses() {
  try {
    const res = await fetchCampuses();
    campusOptions.value = res.data.data ?? [];
  } catch {
    campusOptions.value = [];
  }
}
loadCampuses();

// ─── Filters & Pagination ───────────────────────────
const filters = reactive({
  keyword: '',
  guardian: '',
  status: '',
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

const tableData = ref<StudentItem[]>([]);

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

// ─── Load Data ────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const params: StudentQuery = {
      keyword: filters.keyword || undefined,
      guardian: filters.guardian || undefined,
      status: filters.status || undefined,
      pageNum: pagination.page,
      pageSize: pagination.size,
    };
    const res = await fetchStudents(params);
    const data = res.data.data;
    tableData.value = data.records;
    pagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load students.');
  } finally {
    loading.value = false;
  }
}

// ─── Form ──────────────────────────────────────────
const studentForm = reactive<StudentSavePayload>({
  studentName: '',
  gender: 'Female',
  guardianName: '',
  guardianPhone: '',
  guardianEmail: '',
  guardianRelationType: '',
  status: 'ACTIVE',
  notes: '',
  englishName: '',
  birthDate: '',
  sourceChannel: '',
  campusId: undefined,
});

const studentFormRules = {
  studentName: [{ required: true, message: 'Name is required' }],
  guardianName: [{ required: true, message: 'Guardian name is required' }],
  guardianPhone: [{ required: true, message: 'Phone is required' }],
};

function openStudentDialog(row: StudentItem) {
  editingId.value = row.id;
  Object.assign(studentForm, {
    studentName: row.studentName,
    gender: row.gender,
    guardianName: row.guardianName,
    guardianPhone: row.phone ?? '',
    guardianEmail: row.guardianEmail ?? '',
    guardianRelationType: row.guardianRelationType ?? '',
    status: row.status,
    notes: row.notes ?? '',
    englishName: row.englishName ?? '',
    birthDate: row.birthDate ?? '',
    sourceChannel: row.sourceChannel ?? '',
    campusId: row.campusId ?? undefined,
  });
  // Clear any previous validation errors when opening dialog
  studentFormRef.value?.clearValidate();
  studentDialogVisible.value = true;
}

async function handleStudentSubmit() {
  const valid = await studentFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    await updateStudent(editingId.value!, { ...studentForm });
    ElMessage.success('Student updated.');
    studentDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

// ─── Delete ────────────────────────────────────────
async function handleDelete(row: StudentItem) {
  try {
    await ElMessageBox.confirm(
      `Delete "${row.studentName}"? This action cannot be undone.`,
      'Confirm delete',
      { type: 'warning' },
    );
  } catch {
    return; // User cancelled
  }
  try {
    await deleteStudent(String(row.id));
    ElMessage.success('Deleted.');
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
  }
}

// ─── Init ──────────────────────────────────────────
loadData();
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

.panel-header__actions {
  display: flex;
  gap: 8px;
}

.filters {
  margin-bottom: 12px;
}

.filters__item {
  margin-right: 16px;
  margin-bottom: 0;
}

.filters__item :deep(.el-form-item__label) {
  color: #606266;
  font-size: 13px;
}

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cell-stack strong {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.cell-stack span {
  color: #909399;
  font-size: 12px;
}

.table-actions {
  display: flex;
  gap: 4px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* Student dialog */
.student-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.student-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 14px 20px;
  margin-right: 0;
}

.student-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.dialog-header__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.dialog-header__settings {
  color: #909399;
}

.dialog-header__settings:hover {
  color: #409eff;
}

.student-form__row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 14px;
}

.student-form__full {
  grid-column: 1 / -1;
}

.student-form__half {
  /* each item takes one column */
}

/* Field Config dialog */
.field-config-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 14px 20px;
}

.field-config-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}
</style>
