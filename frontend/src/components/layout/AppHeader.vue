<template>
  <header class="app-header">
    <div class="app-header__left">
      <el-button text class="app-header__toggle" aria-label="Toggle navigation" @click="appStore.toggleSidebar">
        <el-icon><Fold /></el-icon>
      </el-button>
      <nav v-if="breadcrumbs.length" class="app-header__breadcrumbs" aria-label="Current page path">
        <span class="app-header__breadcrumb-label">You are in</span>
        <template v-for="(crumb, index) in breadcrumbs" :key="`${crumb.label}-${index}`">
          <span v-if="index > 0" class="app-header__breadcrumb-separator">›</span>
          <strong
            v-if="index === breadcrumbs.length - 1"
            class="app-header__breadcrumb-chip app-header__breadcrumb-chip--current"
          >
            {{ crumb.label }}
          </strong>
          <router-link
            v-else-if="crumb.to"
            :to="crumb.to"
            class="app-header__breadcrumb-chip app-header__breadcrumb-chip--link"
          >
            {{ crumb.label }}
          </router-link>
          <span v-else class="app-header__breadcrumb-chip">{{ crumb.label }}</span>
        </template>
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
  if (isDailyWorkRoute(route.path)) {
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
            to: backOfficeSectionPath(resolvedSection)
          }]),
      { label: title }
    ];
  }
  return section && section !== title
    ? [{ label: section, to: '/dashboard' }, { label: title }]
    : [{ label: title }];
}

function isDailyWorkRoute(path: string) {
  return [
    '/registration',
    '/calendar',
    '/one-on-one-deduction',
    '/group-class-lessons',
    '/data-summary',
    '/classrooms',
  ].some(dailyPath => path === dailyPath || path.startsWith(`${dailyPath}/`));
}

function resolveBackOfficeSection(section: string, title: string) {
  if (['Course Products', 'Course Pricing', 'Class Schedules', 'Schedule 1-on-1 Lessons'].includes(title)) {
    return 'Courses';
  }
  if (['Enrollment History', 'Course Packages', 'Registration Receipts'].includes(title)) {
    return 'Enrollment';
  }
  if (['Teacher Pay', 'Payment Methods'].includes(title)) {
    return 'Finance';
  }
  if (['People', 'Students', 'Teachers', 'Accounts'].includes(title)) {
    return 'People';
  }
  if (title === 'User Guide') {
    return 'Help';
  }
  if (['Campuses', 'Notifications', 'Brand & receipts'].includes(title)) {
    return 'System';
  }
  return section || 'Back office';
}

function backOfficeSectionPath(section: string) {
  const sectionPaths: Record<string, string> = {
    Help: '/admin/guide',
    People: '/admin/config/people',
    Enrollment: '/enrollment/orders',
    Courses: '/base-data/course-products',
    Finance: '/admin/config/teacher-pay-rates',
    System: '/admin/config/campuses',
  };
  return sectionPaths[section] ?? '/base-data/course-products';
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
  gap: 7px;
  min-width: 0;
  max-width: min(720px, 52vw);
  padding: 4px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  white-space: nowrap;
}

.app-header__breadcrumb-label {
  padding: 0 4px 0 6px;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.app-header__breadcrumb-separator {
  color: #94a3b8;
  font-size: 14px;
}

.app-header__breadcrumb-chip {
  display: inline-flex;
  max-width: 180px;
  min-width: 0;
  align-items: center;
  padding: 3px 9px;
  overflow: hidden;
  border-radius: 999px;
  color: #334155;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-header__breadcrumb-chip--link {
  color: #2563eb;
  text-decoration: none;
}

.app-header__breadcrumb-chip--link:hover {
  background: #dbeafe;
  color: #1d4ed8;
}

.app-header__breadcrumb-chip--current {
  background: #1e293b;
  color: #fff;
  font-weight: 800;
}

.app-header__back {
  color: #334155;
  font-weight: 600;
}

:global(.app-header__admin-tools-menu) {
  min-width: 264px;
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
