<template>
  <header class="app-header">
    <div class="app-header__left">
      <el-button text class="app-header__toggle" @click="appStore.toggleSidebar">
        <el-icon><Fold /></el-icon>
      </el-button>

      <div>
        <h1 class="app-header__title">{{ pageTitle }}</h1>
        <p class="app-header__breadcrumb">{{ sectionTitle }}</p>
      </div>
    </div>

    <div class="app-header__right">
      <el-input placeholder="Search students, instructors, classrooms, or packages" class="app-header__search" />
      <el-tag type="success" effect="light">{{ APP_TIMEZONE_LABEL }} timezone</el-tag>

      <el-dropdown trigger="click" @command="handleDropdownCommand">
        <span class="app-header__user-trigger">
          <span class="app-header__user-name">{{ authStore.displayName }}</span>
          <el-avatar :size="34">{{ authStore.userInitials }}</el-avatar>
          <el-icon class="app-header__dropdown-icon"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="changePassword">
              <el-icon><Key /></el-icon>
              Change Password
            </el-dropdown-item>
            <el-dropdown-item command="signOut" divided class="app-header__signout-item">
              Sign out
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- Change Password Dialog -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="Change Password"
      width="480px"
      :close-on-click-modal="false"
      @closed="resetPasswordForm"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
        @submit.prevent="handleChangePassword"
      >
        <el-form-item label="Current Password" prop="currentPassword">
          <el-input
            v-model="passwordForm.currentPassword"
            type="password"
            show-password
            placeholder="Enter current password"
          />
        </el-form-item>
        <el-form-item label="New Password" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            placeholder="At least 8 characters"
          />
        </el-form-item>
        <el-form-item label="Confirm New Password" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            placeholder="Re-enter new password"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="handleChangePassword">
          Confirm
        </el-button>
      </template>
    </el-dialog>
  </header>
</template>

<script setup lang="ts">
import { ArrowDown, Fold, Key } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { changePassword } from '@/api/auth';
import { APP_TIMEZONE_LABEL } from '@/config/app-config';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();
const authStore = useAuthStore();

const pageTitle = computed(() => String(route.meta.title ?? 'Dashboard'));
const sectionTitle = computed(() => String(route.meta.section ?? 'Admin'));

async function handleSignOut() {
  await authStore.signOut();
  await router.push('/login');
}

// ─── Dropdown ───────────────────────────────────────

function handleDropdownCommand(command: string) {
  if (command === 'changePassword') {
    passwordDialogVisible.value = true;
  } else if (command === 'signOut') {
    handleSignOut();
  }
}

// ─── Change Password Dialog ─────────────────────────

const passwordDialogVisible = ref(false);
const passwordSubmitting = ref(false);
const passwordFormRef = ref<FormInstance>();

const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const validateConfirmPassword = (_rule: any, value: string, callback: (error?: Error) => void) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('Passwords do not match'));
  } else {
    callback();
  }
};

const passwordRules: FormRules = {
  currentPassword: [{ required: true, message: 'Current password is required', trigger: 'blur' }],
  newPassword: [
    { required: true, message: 'New password is required', trigger: 'blur' },
    { min: 8, message: 'New password must be at least 8 characters', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: 'Please confirm your new password', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
};

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  passwordSubmitting.value = true;
  try {
    await changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    });
    ElMessage.success('Password changed successfully.');
    passwordDialogVisible.value = false;
  } catch (e: any) {
    const msg = e?.response?.data?.message || 'Failed to change password.';
    ElMessage.error(msg);
  } finally {
    passwordSubmitting.value = false;
  }
}

function resetPasswordForm() {
  passwordFormRef.value?.resetFields();
}
</script>

<style scoped>
.app-header__user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.app-header__user-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-header__dropdown-icon {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.app-header__signout-item {
  color: var(--el-color-danger);
}
</style>
