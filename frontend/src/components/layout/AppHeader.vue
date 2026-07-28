<template>
  <header class="app-header">
    <div class="app-header__left">
      <el-button text class="app-header__toggle" aria-label="Toggle navigation" @click="appStore.toggleSidebar">
        <el-icon><Fold /></el-icon>
      </el-button>
      <nav v-if="breadcrumbs.length" class="app-header__breadcrumbs" aria-label="Current page path">
        <span v-for="(crumb, index) in breadcrumbs" :key="`${crumb.label}-${index}`">
          <span v-if="index > 0" class="app-header__breadcrumb-separator">/</span>
          <strong v-if="index === breadcrumbs.length - 1">{{ crumb.label }}</strong>
          <router-link v-else-if="crumb.to" :to="crumb.to">{{ crumb.label }}</router-link>
          <span v-else>{{ crumb.label }}</span>
        </span>
      </nav>
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

    <div class="app-header__right">
      <el-dropdown
        v-if="adminToolGroups.length"
        trigger="click"
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
import { ArrowDown, ArrowLeft, DataAnalysis, Files, Fold, Money, Setting, Tickets, User } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { changePassword } from '@/api/auth';
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
const breadcrumbs = computed(() => buildBreadcrumbs());

function resolveAdminIcon(icon?: string) {
  return adminIconMap[icon as keyof typeof adminIconMap] ?? Setting;
}

function buildBreadcrumbs() {
  const section = String(route.meta.section ?? '').trim();
  const title = String(route.meta.title ?? '').trim();
  if (!title || route.path === '/login') {
    return [];
  }
  if (route.path === '/registration' || route.path === '/calendar' || route.path === '/one-on-one-deduction' || route.path === '/data-summary') {
    return [
      { label: 'Daily work', to: '/dashboard' },
      { label: title }
    ];
  }
  if (route.path.startsWith('/admin') || route.path.startsWith('/base-data') || route.path.startsWith('/schedule') || route.path.startsWith('/enrollment')) {
    const resolvedSection = resolveBackOfficeSection(section, title);
    return [
      { label: 'Back office', to: '/base-data/course-products' },
      ...(resolvedSection === 'Back office'
        ? []
        : [{
            label: resolvedSection,
            to: resolvedSection === 'Course setup' ? '/base-data/course-products' : '/dashboard'
          }]),
      { label: title }
    ];
  }
  return section && section !== title
    ? [{ label: section, to: '/dashboard' }, { label: title }]
    : [{ label: title }];
}

function resolveBackOfficeSection(section: string, title: string) {
  if (['Course Products', 'Course Pricing', 'Class Schedules', 'Schedule 1-on-1 Lessons'].includes(title)) {
    return 'Course setup';
  }
  if (['Enrollment History', 'Course Packages', 'Registration Receipts'].includes(title)) {
    return 'Records';
  }
  if (['Teacher Pay', 'Payment Methods'].includes(title)) {
    return 'Payments & payroll';
  }
  if (['Students', 'Teachers', 'Accounts'].includes(title)) {
    return 'People & accounts';
  }
  if (['Campuses', 'Notifications', 'Brand & receipts', 'User Guide'].includes(title)) {
    return 'School settings';
  }
  return section || 'Back office';
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

.app-header__breadcrumbs {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #64748b;
  font-size: 13px;
  white-space: nowrap;
}

.app-header__breadcrumbs strong {
  color: #102a43;
  font-weight: 700;
}

.app-header__breadcrumb-separator {
  margin-right: 6px;
  color: #94a3b8;
}

.app-header__back {
  color: #334155;
  font-weight: 600;
}

:global(.app-header__admin-tools-menu) {
  min-width: 248px;
}

:global(.app-header__tool-group) {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 6px 8px 2px;
  padding: 4px 8px;
  border-top: 1px solid #eef2f7;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

:global(.app-header__tool-group:first-child) {
  border-top: 0;
  margin-top: 0;
}

:global(.app-header__tool-group .el-icon) {
  color: var(--accent-deep);
}

:global(.app-header__tool-item) {
  display: flex;
  min-width: 248px !important;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  padding: 7px 14px !important;
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
