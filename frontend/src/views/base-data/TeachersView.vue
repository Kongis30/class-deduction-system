<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Instructors</span>
        <div class="panel-header__actions">
          <el-button type="primary" @click="openTeacherDialog()">
            <el-icon><Plus /></el-icon>
            Add instructor
          </el-button>
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
        <el-form-item label="Subject" class="filters__item">
          <el-input
            v-model="filters.subject"
            placeholder="Search by subject"
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
        <el-table-column label="Name" min-width="140">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.teacherName }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Phone" min-width="140" prop="mobile" />
        <el-table-column label="Email" min-width="200" prop="email" />
        <el-table-column label="Subjects" min-width="160">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag v-for="subj in row.subjectTags" :key="subj" size="small" type="info">{{ subj }}</el-tag>
            </div>
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
              <el-button text type="primary" @click="openTeacherDialog(row)">Edit</el-button>
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

    <el-dialog
      v-model="teacherDialogVisible"
      width="500px"
      :close-on-click-modal="false"
      class="teacher-dialog"
    >
      <template #header>
        <h3>{{ editingId ? 'Edit instructor' : 'New instructor' }}</h3>
      </template>

      <el-form ref="teacherFormRef" label-position="top" :model="teacherForm" :rules="teacherFormRules" class="teacher-form">
        <div class="teacher-form__row">
          <el-form-item label="Name" prop="teacherName" class="teacher-form__full">
            <el-input v-model="teacherForm.teacherName" placeholder="e.g. Emily Chen" />
          </el-form-item>
        </div>

        <div class="teacher-form__row">
          <el-form-item label="Phone" prop="mobile">
            <el-input v-model="teacherForm.mobile" placeholder="(626) 555-0101" />
          </el-form-item>
          <el-form-item label="Email" prop="email">
            <el-input v-model="teacherForm.email" placeholder="emily@example.com" />
          </el-form-item>
        </div>

        <div class="teacher-form__row">
          <el-form-item label="Subjects" class="teacher-form__full">
            <el-select v-model="teacherForm.subjectTags" multiple placeholder="Select subjects" style="width: 100%;">
              <el-option v-for="subj in subjectOptions" :key="subj" :label="subj" :value="subj" />
            </el-select>
          </el-form-item>
        </div>

        <div class="teacher-form__row">
          <el-form-item label="Status" prop="status">
            <el-select v-model="teacherForm.status" placeholder="Select status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>

        <div class="teacher-form__row">
          <el-form-item label="Notes" class="teacher-form__full">
            <el-input v-model="teacherForm.notes" type="textarea" :rows="2" placeholder="Optional notes" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="teacherDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleTeacherSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reactive, ref, watch } from 'vue';

import {
  createTeacher,
  deleteTeacher,
  fetchAllTeachers,
  fetchTeachers,
  type TeacherQuery,
  type TeacherSavePayload,
} from '@/api/base-data';
import type { TeacherItem } from '@/types/domain';

const loading = ref(false);
const submitting = ref(false);
const teacherDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const teacherFormRef = ref();

// ─── 筛选 & 分页 ───────────────────────────────────────
const filters = reactive({
  keyword: '',
  subject: '',
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

const tableData = ref<TeacherItem[]>([]);

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

// ─── 加载数据 ──────────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const params: TeacherQuery = {
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      pageNum: pagination.page,
      pageSize: pagination.size,
    };
    const res = await fetchTeachers(params);
    const data = res.data.data;
    tableData.value = data.records;
    pagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load teachers.');
  } finally {
    loading.value = false;
  }
}

watch(() => filters.status, handleFilterChange);

// ─── Subjects 列表 ─────────────────────────────────────
const subjectOptions = ['Math', 'English', 'Science', 'SAT', 'Music', 'Reading'];

// ─── 新建 / 编辑 ───────────────────────────────────────
const teacherForm = reactive<TeacherSavePayload>({
  teacherName: '',
  mobile: '',
  email: '',
  subjectTags: [],
  status: 'ACTIVE',
  notes: '',
});

const teacherFormRules = {
  teacherName: [{ required: true, message: 'Name is required' }],
  mobile: [{ required: true, message: 'Phone is required' }],
  email: [{ required: true, message: 'Email is required', type: 'email' }],
};

async function openTeacherDialog(row?: TeacherItem) {
  editingId.value = row?.id ?? null;
  if (row) {
    Object.assign(teacherForm, {
      teacherName: row.teacherName,
      mobile: row.mobile,
      email: row.email,
      subjectTags: [...row.subjectTags],
      status: row.status,
      notes: '',
    });
  } else {
    Object.assign(teacherForm, {
      teacherName: '',
      mobile: '',
      email: '',
      subjectTags: [],
      status: 'ACTIVE',
      notes: '',
    });
  }
  teacherDialogVisible.value = true;
}

async function handleTeacherSubmit() {
  const valid = await teacherFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateTeacher(editingId.value, { ...teacherForm });
      ElMessage.success('Instructor updated.');
    } else {
      await createTeacher({ ...teacherForm });
      ElMessage.success('Instructor created.');
    }
    teacherDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

// ─── 删除 ──────────────────────────────────────────────
async function handleDelete(row: TeacherItem) {
  try {
    await ElMessageBox.confirm(
      `Delete "${row.teacherName}"? This action cannot be undone.`,
      'Confirm delete',
      { type: 'warning' },
    );
    await deleteTeacher(row.id);
    ElMessage.success('Deleted.');
    loadData();
  } catch {
    // User cancelled
  }
}

// ─── 初始化 ────────────────────────────────────────────
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

.tag-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
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

/* Teacher dialog */
.teacher-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.teacher-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.teacher-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.teacher-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.teacher-form__row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 14px;
}

.teacher-form__full {
  grid-column: 1 / -1;
}
</style>
