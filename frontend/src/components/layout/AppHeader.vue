<template>
  <header class="app-header">
    <div class="app-header__left">
      <el-button text class="app-header__toggle" aria-label="Toggle navigation" @click="appStore.toggleSidebar">
        <el-icon><Fold /></el-icon>
      </el-button>
      <el-button
        v-if="showBackButton"
        text
        class="app-header__back"
        aria-label="Go back"
        @click="goBack"
      >
        <el-icon><ArrowLeft /></el-icon>
        Back
      </el-button>
    </div>

    <div class="app-header__page">
      <span v-if="currentPageSection">{{ currentPageSection }}</span>
      <strong>{{ currentPageTitle }}</strong>
    </div>

    <div class="app-header__right">
      <PageUsageGuide />

      <el-dropdown
        v-if="adminToolGroups.length"
        trigger="click"
        :teleported="true"
        :popper-options="{ strategy: 'fixed' }"
        popper-class="app-header__admin-tools-menu"
        @command="handleAdminToolCommand"
      >
        <el-button class="app-header__admin-tools">
          <el-icon><Setting /></el-icon>
          Back office
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <template v-for="group in adminToolGroups" :key="group.key">
              <div class="app-header__tool-group">
                <el-icon v-if="group.icon"><component :is="resolveAdminIcon(group.icon)" /></el-icon>
                <span>{{ group.title }}</span>
              </div>
              <el-dropdown-item
                v-for="item in group.children"
                :key="item.key"
                :command="item.path"
                class="app-header__tool-item"
              >
                <span class="app-header__tool-item-title">{{ item.title }}</span>
                <span v-if="item.description" class="app-header__tool-item-description">{{ item.description }}</span>
              </el-dropdown-item>
            </template>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

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
  </header>

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
</template>

<script setup lang="ts">
import { ArrowDown, ArrowLeft, DataAnalysis, Files, Fold, Key, Money, Setting, Tickets, User } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { changePassword } from '@/api/auth';
import PageUsageGuide from '@/components/shared/PageUsageGuide.vue';
import { APP_TIMEZONE_LABEL } from '@/config/app-config';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { getSecondaryMenusForUser } from '@/utils/menu';

const router = useRouter();
const route = useRoute();
const appStore = useAppStore();
const authStore = useAuthStore();
const adminToolGroups = computed(() => getSecondaryMenusForUser(authStore.currentUser));
const adminIconMap = { DataAnalysis, Files, Money, Setting, Tickets, User };
const showBackButton = computed(() => route.path !== '/dashboard');
const currentPageTitle = computed(() => String(route.meta.title || 'Dashboard'));
const currentPageSection = computed(() => String(route.meta.section || ''));

function resolveAdminIcon(icon?: string) {
  return adminIconMap[icon as keyof typeof adminIconMap] ?? Setting;
}

async function handleSignOut() {
  await authStore.signOut();
  await router.push('/login');
}

function goBack() {
  const previousPath = (window.history.state as { back?: string } | null)?.back;
  if (previousPath?.startsWith('/') && previousPath !== '/login') {
    router.back();
    return;
  }
  router.push('/dashboard');
}

// ─── Dropdown ───────────────────────────────────────

function handleDropdownCommand(command: string) {
  if (command === 'changePassword') {
    passwordDialogVisible.value = true;
  } else if (command === 'signOut') {
    handleSignOut();
  }
}

function handleAdminToolCommand(path: string) {
  if (path) {
    router.push(path);
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

.app-header__admin-tools {
  border-color: #d8e1e8;
  color: #334155;
}

.app-header__back {
  color: #334155;
  font-weight: 600;
}

:global(.app-header__admin-tools-menu) {
  min-width: 264px;
  max-height: min(680px, calc(100vh - 88px));
  overflow: hidden;
}

:global(.app-header__admin-tools-menu .el-dropdown-menu) {
  max-height: min(680px, calc(100vh - 88px));
  overflow-y: auto;
  overscroll-behavior: contain;
}

:global(.app-header__tool-group) {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 8px 4px;
  padding: 7px 10px;
  border-left: 3px solid #2563eb;
  border-radius: 6px;
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

:global(.app-header__tool-group:first-child) {
  margin-top: 6px;
}

:global(.app-header__tool-group .el-icon) {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
}

:global(.app-header__tool-item) {
  display: flex;
  min-width: 264px !important;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  padding: 8px 14px 8px 18px !important;
  line-height: 1.2;
}

:global(.app-header__tool-item-title) {
  color: #1e293b;
  font-size: 13px;
  font-weight: 600;
}

:global(.app-header__tool-item-description) {
  color: #94a3b8;
  font-size: 11px;
}

.app-header__signout-item {
  color: var(--el-color-danger);
}
</style>
