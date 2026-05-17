<template>
  <div class="page-stack">
    <section class="subjects-panel">
      <!-- 工具栏 -->
      <div class="panel-header">
        <span class="panel-header__title">Subjects</span>
        <div class="panel-header__actions">
          <el-button type="primary" @click="openDialog()">
            <el-icon><Plus /></el-icon>
            Add subject
          </el-button>
        </div>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" row-key="id" class="subjects-table" :show-empty="true">
        <el-table-column label="Display order" min-width="120" align="center">
          <template #default="{ row }">{{ row.displayOrder }}</template>
        </el-table-column>
        <el-table-column label="Subject name" min-width="200">
          <template #default="{ row }">
            <strong>{{ row.subjectName }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="Status" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Notes" min-width="300">
          <template #default="{ row }">{{ row.notes ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="Actions" min-width="140" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" @click="openDialog(row)">Edit</el-button>
              <el-button text type="danger" @click="handleDelete(row)">Delete</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部统计 -->
      <div class="table-footer">
        <span>{{ tableData.length }} subject(s)</span>
      </div>
    </section>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" width="480px" :close-on-click-modal="false" destroy-on-close>
      <template #header>
        <h3>{{ editingId ? 'Edit subject' : 'New subject' }}</h3>
      </template>

      <el-form ref="formRef" label-position="top" :model="form" :rules="formRules">
        <el-form-item label="Subject name" prop="subjectName">
          <el-input v-model="form.subjectName" placeholder="e.g. Math, English, SAT" />
        </el-form-item>
        <el-form-item label="Display order" prop="displayOrder">
          <el-input-number v-model="form.displayOrder" :min="1" :max="999" />
          <small class="field-hint">Lower numbers appear first in dropdowns</small>
        </el-form-item>
        <el-form-item label="Status" prop="status">
          <el-select v-model="form.status" placeholder="Select status" style="width: 100%;">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Notes">
          <el-input v-model="form.notes" type="textarea" :rows="3" placeholder="Optional description" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reactive, ref } from 'vue';

import {
  createSubject,
  deleteSubject,
  fetchSubjects,
  updateSubject,
  type SubjectSavePayload,
} from '@/api/base-data';
import type { SubjectItem } from '@/types/domain';

const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const formRef = ref();

const tableData = ref<SubjectItem[]>([]);

async function loadData() {
  loading.value = true;
  try {
    const res = await fetchSubjects();
    tableData.value = res.data.data;
  } catch {
    ElMessage.error('Failed to load subjects.');
  } finally {
    loading.value = false;
  }
}

const form = reactive<SubjectSavePayload>({
  subjectName: '',
  displayOrder: 99,
  status: 'ACTIVE',
  notes: '',
});

const formRules = {
  subjectName: [{ required: true, message: 'Subject name is required' }],
  displayOrder: [{ required: true, message: 'Display order is required' }],
};

function openDialog(row?: SubjectItem) {
  editingId.value = row?.id ?? null;
  if (row) {
    Object.assign(form, {
      subjectName: row.subjectName,
      displayOrder: row.displayOrder,
      status: row.status,
      notes: row.notes ?? '',
    });
  } else {
    Object.assign(form, {
      subjectName: '',
      displayOrder: tableData.value.length + 1,
      status: 'ACTIVE',
      notes: '',
    });
  }
  dialogVisible.value = true;
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateSubject(editingId.value, { ...form });
      ElMessage.success('Subject updated.');
    } else {
      await createSubject({ ...form });
      ElMessage.success('Subject created.');
    }
    dialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: SubjectItem) {
  try {
    await ElMessageBox.confirm(
      `Delete "${row.subjectName}"? This action cannot be undone.`,
      'Confirm delete',
      { type: 'warning' },
    );
    await deleteSubject(row.id);
    ElMessage.success('Deleted.');
    loadData();
  } catch {
    // 用户取消
  }
}

loadData();
</script>

<style scoped>
.subjects-panel {
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

.subjects-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.table-actions {
  display: flex;
  gap: 4px;
}

.table-footer {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.field-hint {
  display: block;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
