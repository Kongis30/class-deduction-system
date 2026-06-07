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
        <el-table-column label="Actions" min-width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEditDialog(row, 'TEACHER')">Edit</el-button>
            <el-button text type="danger" @click="openDeleteDialog(row, 'TEACHER')">Disable login</el-button>
          </template>
        </el-table-column>
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
        <el-table-column label="Actions" min-width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEditDialog(row, 'STUDENT')">Edit</el-button>
            <el-button text type="danger" @click="openDeleteDialog(row, 'STUDENT')">Disable login</el-button>
          </template>
        </el-table-column>
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

    <!-- Edit Dialog -->
    <el-dialog v-model="editDialogVisible" width="480px" :close-on-click-modal="false" class="account-dialog">
      <template #header><h3>Edit Account</h3></template>
      <el-form ref="editFormRef" label-position="top" :model="editForm" class="account-form">
        <el-form-item label="Username">
          <el-input v-model="editForm.username" placeholder="Login username" />
        </el-form-item>
        <el-form-item label="Display name">
          <el-input v-model="editForm.displayName" placeholder="Display name" />
        </el-form-item>
        <template v-if="editForm.userType === 'TEACHER'">
          <el-form-item label="Email">
            <el-input v-model="editForm.email" placeholder="Email address" />
          </el-form-item>
          <el-form-item label="Phone">
            <el-input v-model="editForm.mobile" placeholder="Phone number" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="Email">
            <el-input v-model="editForm.email" placeholder="Email address" />
          </el-form-item>
        </template>
        <el-form-item label="Status">
          <el-select v-model="editForm.status" style="width: 100%;">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <div class="form-divider">Reset Password</div>
        <el-form-item label="New password" prop="editPassword">
          <el-input v-model="editForm.password" type="password" placeholder="Leave blank to keep current" show-password />
        </el-form-item>
        <el-form-item label="Confirm password" prop="editConfirmPassword">
          <el-input v-model="editForm.confirmPassword" type="password" placeholder="Re-enter new password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEditSubmit">Save</el-button>
      </template>
    </el-dialog>

    <!-- Login Disable Confirmation Dialog -->
    <el-dialog v-model="deleteDialogVisible" width="460px" :close-on-click-modal="false" class="account-dialog" :close-on-press-escape="false">
      <template #header><h3>Disable Login Account</h3></template>
      <div class="delete-warning">
        <div class="delete-warning__title">
          Disable login account "{{ deleteTargetName }}"?
        </div>
        <div class="delete-warning__body">
          <p>After confirmation, this login account will be removed. Existing sessions will expire, and the user will no longer be able to sign in.</p>
          <p>The linked {{ deleteProfileTypeLabel }} profile and business records will be kept.</p>
          <p>This action will not cancel future lessons, close packages, stop scheduling, or change existing deduction records.</p>
          <p class="delete-warning__caution">For business deactivation, set the {{ deleteProfileTypeLabel }} profile status to Inactive on the profile page.</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="closeDeleteDialog">Cancel</el-button>
        <el-button
          type="danger"
          :disabled="deleteCountdown > 0"
          :loading="deleteSubmitting"
          @click="handleConfirmDelete"
        >
          {{ deleteCountdown > 0 ? `Disable login (${deleteCountdown}s)` : 'Disable login' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { createStudentAccount, createTeacherAccount, deleteStudentAccount, deleteTeacherAccount, fetchStudentAccounts, fetchTeacherAccounts, updateTeacherAccount, updateStudentAccount } from '@/api/auth';
import type { StudentAccountItem, TeacherAccountItem } from '@/types/domain';

const teacherLoading = ref(false);
const studentLoading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const formRef = ref();
const teacherAccounts = ref<TeacherAccountItem[]>([]);
const studentAccounts = ref<StudentAccountItem[]>([]);

// ─── Delete Confirmation Dialog ──────────────────────
const deleteDialogVisible = ref(false);
const deleteType = ref<'TEACHER' | 'STUDENT'>('TEACHER');
const deleteTargetId = ref<number | null>(null);
const deleteTargetName = ref('');
const deleteCountdown = ref(0);
const deleteSubmitting = ref(false);
let deleteTimer: ReturnType<typeof setInterval> | null = null;
const deleteProfileTypeLabel = computed(() => (deleteType.value === 'TEACHER' ? 'teacher' : 'student'));

function openDeleteDialog(row: any, type: 'TEACHER' | 'STUDENT') {
  deleteType.value = type;
  deleteTargetId.value = row.userId;
  deleteTargetName.value = row.displayName || row.username || `${type === 'TEACHER' ? 'Teacher' : 'Student'} #${row.userId}`;
  deleteCountdown.value = 10;
  deleteSubmitting.value = false;
  deleteDialogVisible.value = true;
  deleteTimer = setInterval(() => {
    deleteCountdown.value--;
    if (deleteCountdown.value <= 0 && deleteTimer) {
      clearInterval(deleteTimer);
      deleteTimer = null;
    }
  }, 1000);
}

function closeDeleteDialog() {
  if (deleteTimer) {
    clearInterval(deleteTimer);
    deleteTimer = null;
  }
  deleteDialogVisible.value = false;
}

async function handleConfirmDelete() {
  if (deleteCountdown.value > 0) return;
  deleteSubmitting.value = true;
  try {
    if (deleteType.value === 'TEACHER') {
      await deleteTeacherAccount(deleteTargetId.value!);
    } else {
      await deleteStudentAccount(deleteTargetId.value!);
    }
    ElMessage.success(`Login account disabled.`);
    closeDeleteDialog();
    await Promise.all([loadTeacherAccounts(), loadStudentAccounts()]);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
  } finally {
    deleteSubmitting.value = false;
  }
}

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

// ─── Edit Account ────────────────────────────────────
const editDialogVisible = ref(false);
const editSaving = ref(false);
const editFormRef = ref();
const editingUserType = ref<'TEACHER' | 'STUDENT'>('TEACHER');
const editingUserId = ref<number | null>(null);

const editForm = reactive({
  userType: 'TEACHER' as 'TEACHER' | 'STUDENT',
  username: '',
  displayName: '',
  email: '',
  mobile: '',
  status: 'ACTIVE',
  password: '',
  confirmPassword: '',
});

function openEditDialog(row: any, type: 'TEACHER' | 'STUDENT') {
  editingUserType.value = type;
  editingUserId.value = row.userId;
  editForm.userType = type;
  editForm.username = row.username ?? '';
  editForm.displayName = row.displayName ?? '';
  editForm.email = row.email ?? '';
  editForm.mobile = row.mobile ?? '';
  editForm.status = row.status ?? 'ACTIVE';
  editForm.password = '';
  editForm.confirmPassword = '';
  editFormRef.value?.clearValidate();
  editDialogVisible.value = true;
}

async function handleEditSubmit() {
  if (editForm.password && editForm.password !== editForm.confirmPassword) {
    ElMessage.error('Passwords do not match.');
    return;
  }
  editSaving.value = true;
  try {
    const payload: Record<string, any> = {};
    if (editForm.username) payload.username = editForm.username;
    if (editForm.displayName) payload.displayName = editForm.displayName;
    if (editForm.email) payload.email = editForm.email;
    if (editForm.password) payload.password = editForm.password;
    if (editForm.status) payload.status = editForm.status;
    if (editForm.userType === 'TEACHER' && editForm.mobile) {
      payload.mobile = editForm.mobile;
    }

    if (editingUserType.value === 'TEACHER') {
      await updateTeacherAccount(editingUserId.value!, payload);
    } else {
      await updateStudentAccount(editingUserId.value!, payload);
    }
    ElMessage.success('Account updated.');
    editDialogVisible.value = false;
    await Promise.all([loadTeacherAccounts(), loadStudentAccounts()]);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Update failed.');
  } finally {
    editSaving.value = false;
  }
}

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

.delete-warning {
  padding: 8px 0;
}

.delete-warning__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.delete-warning__body {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}

.delete-warning__body p {
  margin: 0 0 6px;
}

.delete-warning__caution {
  color: #e6a23c;
  font-weight: 500;
}
</style>
