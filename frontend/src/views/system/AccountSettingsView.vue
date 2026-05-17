<template>
  <div class="page-stack">
    <section class="settings-panel">
      <div class="settings-panel__header">
        <span class="settings-panel__title">Teacher Accounts</span>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          Add account
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" row-key="userId" class="product-table">
        <el-table-column label="Username" min-width="120" prop="username" />
        <el-table-column label="Display name" min-width="140" prop="displayName" />
        <el-table-column label="Teacher" min-width="120" prop="teacherName" />
        <el-table-column label="Email" min-width="180" prop="email" />
        <el-table-column label="Status" min-width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Last login" min-width="160" prop="lastLoginAt" />
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" width="500px" :close-on-click-modal="false" class="account-dialog">
      <template #header><h3>New Teacher Account</h3></template>
      <el-form ref="accountFormRef" label-position="top" :model="createForm" :rules="accountRules">
        <el-form-item label="Teacher" prop="teacherId">
          <el-select v-model="createForm.teacherId" placeholder="Select teacher" filterable style="width: 100%;">
            <el-option v-for="t in teachers" :key="t.id" :label="`${t.teacherName} (${t.teacherNo})`" :value="t.id" />
          </el-select>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="Username" prop="username">
            <el-input v-model="createForm.username" placeholder="e.g. emma.wang" />
          </el-form-item>
          <el-form-item label="Password" prop="password">
            <el-input v-model="createForm.password" type="password" placeholder="Min 8 characters" />
          </el-form-item>
        </div>
        <el-form-item label="Email">
          <el-input v-model="createForm.email" placeholder="teacher@example.com" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">Create</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import { createTeacherAccount, fetchTeacherAccounts } from '@/api/auth';
import { fetchAllTeachers } from '@/api/base-data';
import type { TeacherAccountItem } from '@/types/domain';

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const accountFormRef = ref();
const tableData = ref<TeacherAccountItem[]>([]);
const teachers = ref<{ id: number; teacherName: string; teacherNo: string }[]>([]);

const createForm = reactive({
  teacherId: null as number | null,
  username: '',
  password: '',
  email: '',
});

const accountRules = {
  teacherId: [{ required: true, message: 'Teacher is required' }],
  username: [{ required: true, message: 'Username is required' }],
  password: [{ required: true, message: 'Password is required', min: 8 }],
};

onMounted(async () => {
  await Promise.all([loadAccounts(), loadTeachers()]);
});

async function loadAccounts() {
  loading.value = true;
  try {
    const res = await fetchTeacherAccounts({ pageSize: 100 });
    tableData.value = res.data.data.records;
  } catch {
    ElMessage.error('Failed to load accounts.');
  } finally {
    loading.value = false;
  }
}

async function loadTeachers() {
  try {
    const res = await fetchAllTeachers();
    teachers.value = res.data.data.map(t => ({ id: t.id, teacherName: t.teacherName, teacherNo: t.teacherNo }));
  } catch { /* silent */ }
}

function openCreateDialog() {
  Object.assign(createForm, { teacherId: null, username: '', password: '', email: '' });
  accountFormRef.value?.clearValidate();
  dialogVisible.value = true;
}

async function handleCreate() {
  const valid = await accountFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    await createTeacherAccount({ ...createForm, teacherId: createForm.teacherId! });
    ElMessage.success('Account created.');
    dialogVisible.value = false;
    loadAccounts();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Create failed.');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.settings-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}
.settings-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.settings-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}
.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 14px;
}
.account-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
}
.account-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}
.account-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.account-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}
</style>
