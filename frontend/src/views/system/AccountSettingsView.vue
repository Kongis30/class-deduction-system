<template>
  <div class="page-stack">
    <!-- Teacher Accounts Section -->
    <section class="settings-panel">
      <div class="settings-panel__header">
        <span class="settings-panel__title">Teacher Accounts</span>
        <el-button type="primary" @click="openCreateDialog('TEACHER')">
          <el-icon><Plus /></el-icon>
          Add account
        </el-button>
      </div>
      <el-table v-loading="teacherLoading" :data="teacherAccounts" row-key="userId" class="data-table">
        <el-table-column label="Username" min-width="120" prop="username" />
        <el-table-column label="Display name" min-width="140" prop="displayName" />
        <el-table-column label="Teacher" min-width="120" prop="teacherName" />
        <el-table-column label="Email" min-width="180" prop="email" />
        <el-table-column label="Status" min-width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Last login" min-width="160" prop="lastLoginAt" />
      </el-table>
    </section>

    <!-- Student Accounts Section -->
    <section class="settings-panel">
      <div class="settings-panel__header">
        <span class="settings-panel__title">Student Accounts</span>
        <el-button type="primary" @click="openCreateDialog('STUDENT')">
          <el-icon><Plus /></el-icon>
          Add account
        </el-button>
      </div>
      <el-table v-loading="studentLoading" :data="studentAccounts" row-key="userId" class="data-table">
        <el-table-column label="Username" min-width="120" prop="username" />
        <el-table-column label="Display name" min-width="140" prop="displayName" />
        <el-table-column label="Student" min-width="120" prop="studentName" />
        <el-table-column label="Email" min-width="180" prop="email" />
        <el-table-column label="Status" min-width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Last login" min-width="160" prop="lastLoginAt" />
      </el-table>
    </section>

    <!-- Unified Create Dialog -->
    <el-dialog v-model="dialogVisible" width="520px" :close-on-click-modal="false" class="account-dialog">
      <template #header><h3>New Account</h3></template>
      <el-form ref="formRef" label-position="top" :model="createForm" :rules="formRules" class="account-form">
        <!-- Account Type -->
        <el-form-item label="Account type" prop="userType">
          <el-select v-model="createForm.userType" placeholder="Select account type" style="width: 100%;">
            <el-option label="Teacher" value="TEACHER" />
            <el-option label="Student" value="STUDENT" />
          </el-select>
        </el-form-item>

        <!-- Teacher fields -->
        <template v-if="createForm.userType === 'TEACHER'">
          <el-form-item label="Teacher name" prop="teacherName">
            <el-input v-model="createForm.teacherName" placeholder="e.g. Emily Chen" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="Phone" prop="mobile">
              <el-input v-model="createForm.mobile" placeholder="(626) 555-0101" />
            </el-form-item>
            <el-form-item label="Email" prop="email">
              <el-input v-model="createForm.email" placeholder="teacher@example.com" />
            </el-form-item>
          </div>
          <el-form-item label="Subjects" prop="subjectTags">
            <el-select v-model="createForm.subjectTags" multiple placeholder="Select subjects" style="width: 100%;">
              <el-option v-for="s in subjectOptions" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
        </template>

        <!-- Student fields -->
        <template v-if="createForm.userType === 'STUDENT'">
          <el-form-item label="Student name" prop="studentName">
            <el-input v-model="createForm.studentName" placeholder="e.g. Sophia Lin" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="Guardian name" prop="guardianName">
              <el-input v-model="createForm.guardianName" placeholder="Primary guardian" />
            </el-form-item>
            <el-form-item label="Guardian phone" prop="guardianPhone">
              <el-input v-model="createForm.guardianPhone" placeholder="(626) 555-0102" />
            </el-form-item>
          </div>
          <el-form-item label="Guardian email" prop="guardianEmail">
            <el-input v-model="createForm.guardianEmail" placeholder="parent@example.com" />
          </el-form-item>
        </template>

        <!-- Account fields (common) -->
        <div class="form-divider">Account Info</div>
        <el-form-item label="Account" prop="username">
          <el-input v-model="createForm.username" placeholder="Login username" />
        </el-form-item>
        <el-form-item label="Display name" prop="displayName">
          <el-input v-model="createForm.displayName" placeholder="Display name (defaults to person name)" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="Password" prop="password">
            <el-input v-model="createForm.password" type="password" placeholder="Min 8 characters" show-password />
          </el-form-item>
          <el-form-item label="Confirm password" prop="confirmPassword">
            <el-input v-model="createForm.confirmPassword" type="password" placeholder="Re-enter password" show-password />
          </el-form-item>
        </div>
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
import { onMounted, reactive, ref, computed } from 'vue';
import { createStudentAccount, createTeacherAccount, fetchStudentAccounts, fetchTeacherAccounts } from '@/api/auth';
import type { StudentAccountItem, TeacherAccountItem } from '@/types/domain';

const teacherLoading = ref(false);
const studentLoading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const formRef = ref();
const teacherAccounts = ref<TeacherAccountItem[]>([]);
const studentAccounts = ref<StudentAccountItem[]>([]);

const subjectOptions = ['Math', 'English', 'Science', 'History', 'SAT', 'ACT', 'ESL', 'Music', 'Art', 'Other'];

const createForm = reactive({
  userType: 'TEACHER' as 'TEACHER' | 'STUDENT',
  teacherName: '',
  mobile: '',
  subjectTags: [] as string[],
  studentName: '',
  guardianName: '',
  guardianPhone: '',
  guardianEmail: '',
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
  email: '',
});

const formRules = computed(() => ({
  userType: [{ required: true, message: 'Account type is required' }],
  teacherName: [
    { required: createForm.userType === 'TEACHER', message: 'Teacher name is required' },
  ],
  studentName: [
    { required: createForm.userType === 'STUDENT', message: 'Student name is required' },
  ],
  username: [{ required: true, message: 'Username is required' }],
  password: [{ required: true, message: 'Password is required', min: 8 }],
  confirmPassword: [
    { required: true, message: 'Please confirm password' },
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (value !== createForm.password) {
          callback(new Error('Passwords do not match'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
}));

onMounted(async () => {
  await Promise.all([loadTeacherAccounts(), loadStudentAccounts()]);
});

async function loadTeacherAccounts() {
  teacherLoading.value = true;
  try {
    const res = await fetchTeacherAccounts({ pageSize: 100 });
    teacherAccounts.value = res.data.data.records;
  } catch {
    ElMessage.error('Failed to load teacher accounts.');
  } finally {
    teacherLoading.value = false;
  }
}

async function loadStudentAccounts() {
  studentLoading.value = true;
  try {
    const res = await fetchStudentAccounts({ pageSize: 100 });
    studentAccounts.value = res.data.data.records;
  } catch {
    ElMessage.error('Failed to load student accounts.');
  } finally {
    studentLoading.value = false;
  }
}

function openCreateDialog(type: 'TEACHER' | 'STUDENT') {
  Object.assign(createForm, {
    userType: type,
    teacherName: '',
    mobile: '',
    subjectTags: [],
    studentName: '',
    guardianName: '',
    guardianPhone: '',
    guardianEmail: '',
    username: '',
    displayName: '',
    password: '',
    confirmPassword: '',
    email: '',
  });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (createForm.userType === 'TEACHER') {
      await createTeacherAccount({
        teacherName: createForm.teacherName,
        username: createForm.username,
        password: createForm.password,
        displayName: createForm.displayName || undefined,
        mobile: createForm.mobile || undefined,
        email: createForm.email || undefined,
        subjectTags: createForm.subjectTags.length ? createForm.subjectTags : undefined,
      });
      ElMessage.success('Teacher account created.');
      dialogVisible.value = false;
      await loadTeacherAccounts();
    } else {
      await createStudentAccount({
        studentName: createForm.studentName,
        guardianName: createForm.guardianName || undefined,
        guardianPhone: createForm.guardianPhone || undefined,
        guardianEmail: createForm.guardianEmail || undefined,
        username: createForm.username,
        password: createForm.password,
        displayName: createForm.displayName || undefined,
        email: createForm.email || undefined,
      });
      ElMessage.success('Student account created.');
      dialogVisible.value = false;
      await loadStudentAccounts();
    }
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
.settings-panel + .settings-panel {
  margin-top: 16px;
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
.data-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 14px;
}
.form-divider {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 6px;
  margin-top: 8px;
  margin-bottom: 6px;
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
