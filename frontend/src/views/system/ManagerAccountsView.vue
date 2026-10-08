<template>
  <div class="manager-accounts-page" v-loading="loading">
    <div v-if="appStore.isMobileViewport" class="mobile-search-toggle-row">
      <button
        type="button"
        class="mobile-search-trigger"
        :aria-expanded="mobileSearchExpanded"
        aria-label="Search manager accounts"
        @click="mobileSearchExpanded = !mobileSearchExpanded"
      >
        <el-icon><Search /></el-icon>
      </button>
    </div>
    <section v-show="!appStore.isMobileViewport || mobileSearchExpanded" :class="{ 'mobile-search-panel': appStore.isMobileViewport }">
      <div v-if="appStore.isMobileViewport" class="mobile-search-panel__header">
        <span>Search manager accounts</span>
        <button type="button" aria-label="Close search" @click="mobileSearchExpanded = false">
          <el-icon><Close /></el-icon>
        </button>
      </div>
      <div class="manager-accounts-toolbar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="Search name or username"
          style="max-width: 320px"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <el-select v-model="filters.status" clearable placeholder="All statuses" style="width: 160px" @change="handleFilterChange">
          <el-option label="Active" value="ACTIVE" />
          <el-option label="Inactive" value="INACTIVE" />
        </el-select>
        <el-button @click="handleFilterChange">Search</el-button>
      </div>
    </section>
    <p v-if="appStore.isMobileViewport" class="mobile-table-hint">Swipe horizontally to see more columns</p>
    <el-table :data="accounts" row-key="userId" empty-text="No manager accounts" class="manager-accounts-table">
      <el-table-column prop="displayName" label="Name" min-width="180" />
      <el-table-column prop="username" label="Username" min-width="170" />
      <el-table-column prop="mobile" label="Phone" min-width="140" />
      <el-table-column prop="email" label="Email" min-width="190" />
      <el-table-column prop="uniqueStudentCount" label="Students registered" width="160" align="right" />
      <el-table-column prop="registrationCount" label="Registration orders" width="160" align="right" />
      <el-table-column label="Status" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Last login" min-width="180">
        <template #default="{ row }">{{ dateTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column :label="appStore.isMobileViewport ? 'More' : 'Actions'" :width="appStore.isMobileViewport ? 72 : 150" fixed="right" align="center">
        <template #default="{ row }">
          <el-dropdown v-if="appStore.isMobileViewport" class="mobile-table-actions" trigger="click" placement="bottom-end" @click.stop>
            <el-button text circle aria-label="More manager actions" @click.stop><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openEdit(row)">Edit manager</el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete(row)">Delete manager</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <template v-else>
            <el-button link type="primary" @click="openEdit(row)">Edit</el-button>
            <el-button link type="danger" @click="handleDelete(row)">Delete</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="deleteDialogVisible"
      title="Delete manager account"
      width="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="!deleting"
      @close="clearDeleteTimer"
    >
      <p v-if="deleteTarget">
        Delete manager account “{{ deleteTarget.displayName }}” ({{ deleteTarget.username }})?
        The account will lose login access; registration records will be kept.
      </p>
      <template #footer>
        <el-button :disabled="deleting" @click="closeDeleteDialog">Cancel</el-button>
        <el-button
          type="danger"
          :disabled="deleteCountdown > 0"
          :loading="deleting"
          @click="confirmDelete"
        >
          {{ deleteCountdown > 0 ? `Delete (${deleteCountdown}s)` : 'Delete account' }}
        </el-button>
      </template>
    </el-dialog>

    <div class="manager-accounts-footer">
      <span>Showing {{ rangeStart }}–{{ rangeEnd }} of {{ pagination.total }}</span>
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        background
        layout="prev, pager, next, sizes"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        @current-change="loadAccounts"
        @size-change="handlePageSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? 'Edit manager account' : 'Add manager account'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="Display name" prop="displayName"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="Username" prop="username">
          <el-input v-model="form.username" :disabled="editing" :readonly="!editing" placeholder="Enter a first and last name above" />
          <div v-if="!editing" class="form-hint">Generated from the name: first name + last initial + @jin.</div>
        </el-form-item>
        <el-form-item :label="editing ? 'New password (optional)' : 'Initial password'" prop="password">
          <el-input v-model="form.password" :readonly="!editing" type="password" show-password autocomplete="new-password" />
          <div v-if="!editing" class="form-hint">The user can change this after signing in.</div>
        </el-form-item>
        <el-form-item label="Phone"><el-input v-model="form.mobile" /></el-form-item>
        <el-form-item label="Email"><el-input v-model="form.email" /></el-form-item>
        <el-form-item v-if="editing" label="Account status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Remark"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="save">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Close, MoreFilled, Search } from '@element-plus/icons-vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import {
  createManagerAccount,
  deleteManagerAccount,
  fetchManagerAccounts,
  updateManagerAccount,
  type ManagerAccount,
} from '@/api/manager-accounts';
import { extractErrorMessage } from '@/api/http';
import type { EntityId } from '@/types/domain';
import { DEFAULT_INITIAL_PASSWORD, usernameFromName } from '@/utils/account-credentials';
import { useAppStore } from '@/stores/app';

interface AccountForm {
  username: string;
  password: string;
  displayName: string;
  mobile: string;
  email: string;
  status: string;
  remark: string;
}

const formRef = ref<FormInstance>();
const appStore = useAppStore();
const mobileSearchExpanded = ref(false);
const accounts = ref<ManagerAccount[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editing = ref(false);
const editingId = ref<EntityId>();
const deleteDialogVisible = ref(false);
const deleteTarget = ref<ManagerAccount | null>(null);
const deleteCountdown = ref(0);
const deleting = ref(false);
let deleteTimer: ReturnType<typeof setInterval> | null = null;
const form = reactive<AccountForm>(emptyForm());
const filters = reactive({ keyword: '', status: '' });
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 });
const rangeStart = computed(() => pagination.total === 0 ? 0 : (pagination.pageNum - 1) * pagination.pageSize + 1);
const rangeEnd = computed(() => Math.min(pagination.pageNum * pagination.pageSize, pagination.total));
const rules: FormRules<AccountForm> = {
  displayName: [{ required: true, message: 'Enter a display name.', trigger: 'blur' }],
  username: [{ required: true, message: 'Enter a username.', trigger: 'blur' }],
  password: [{ validator: (_, value: string, done) => {
    if ((!editing.value && !value) || (value && value.length < 8)) return done(new Error('Password must contain at least 8 characters.'));
    done();
  }, trigger: 'blur' }],
};

onMounted(loadAccounts);
onBeforeUnmount(clearDeleteTimer);

async function loadAccounts() {
  loading.value = true;
  try {
    const response = await fetchManagerAccounts({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    });
    const data = response.data.data;
    accounts.value = data.records;
    pagination.total = data.total;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, 'Failed to load manager accounts.'));
  } finally {
    loading.value = false;
  }
}

function handleFilterChange() {
  pagination.pageNum = 1;
  loadAccounts();
}

function handlePageSizeChange() {
  pagination.pageNum = 1;
  loadAccounts();
}

function openCreate() {
  editing.value = false;
  editingId.value = undefined;
  Object.assign(form, emptyForm());
  form.password = DEFAULT_INITIAL_PASSWORD;
  dialogVisible.value = true;
}

function openEdit(account: ManagerAccount) {
  editing.value = true;
  editingId.value = account.userId;
  Object.assign(form, {
    username: account.username,
    password: '',
    displayName: account.displayName,
    mobile: account.mobile ?? '',
    email: account.email ?? '',
    status: account.status,
    remark: account.remark ?? '',
  });
  dialogVisible.value = true;
}

async function save() {
  if (!formRef.value) return;
  await formRef.value.validate();
  saving.value = true;
  try {
    if (editing.value && editingId.value != null) {
      await updateManagerAccount(editingId.value, {
        displayName: form.displayName,
        mobile: form.mobile,
        email: form.email,
        status: form.status,
        remark: form.remark,
        ...(form.password ? { password: form.password } : {}),
      });
      ElMessage.success('Manager account updated.');
    } else {
      await createManagerAccount({
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        mobile: form.mobile,
        email: form.email,
        remark: form.remark,
      });
      ElMessage.success('Manager account created.');
    }
    dialogVisible.value = false;
    await loadAccounts();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, 'Failed to save manager account.'));
  } finally {
    saving.value = false;
  }
}

async function handleDelete(account: ManagerAccount) {
  clearDeleteTimer();
  deleteTarget.value = account;
  deleteCountdown.value = 10;
  deleteDialogVisible.value = true;
  deleteTimer = setInterval(() => {
    deleteCountdown.value = Math.max(0, deleteCountdown.value - 1);
    if (deleteCountdown.value === 0) clearDeleteTimer();
  }, 1000);
}

function closeDeleteDialog() {
  clearDeleteTimer();
  deleteDialogVisible.value = false;
  deleteTarget.value = null;
}

function clearDeleteTimer() {
  if (!deleteTimer) return;
  clearInterval(deleteTimer);
  deleteTimer = null;
}

async function confirmDelete() {
  if (!deleteTarget.value || deleteCountdown.value > 0 || deleting.value) return;
  deleting.value = true;
  try {
    await deleteManagerAccount(deleteTarget.value.userId);
    ElMessage.success('Manager account deleted.');
    if (accounts.value.length === 1 && pagination.pageNum > 1) pagination.pageNum -= 1;
    closeDeleteDialog();
    await loadAccounts();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, 'Failed to delete manager account.'));
  } finally {
    deleting.value = false;
  }
}

function emptyForm(): AccountForm {
  return { username: '', password: DEFAULT_INITIAL_PASSWORD, displayName: '', mobile: '', email: '', status: 'ACTIVE', remark: '' };
}

watch(() => form.displayName, (displayName) => {
  if (!editing.value) {
    form.username = usernameFromName(displayName);
  }
});

function dateTime(value?: string) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

defineExpose({ openCreateDialog: openCreate });
</script>

<style scoped>
.manager-accounts-page { display: grid; gap: 14px; }
.manager-accounts-toolbar { display: flex; align-items: center; gap: 10px; }
.manager-accounts-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; color: #64748b; font-size: 13px; }
.form-hint { margin-top: 4px; color: #909399; font-size: 12px; line-height: 1.4; }
@media (max-width: 920px) {
  .manager-accounts-toolbar,
  .manager-accounts-footer { flex-wrap: wrap; }
  .manager-accounts-footer :deep(.el-pagination) { margin-left: auto; }
  .manager-accounts-toolbar :deep(.el-input),
  .manager-accounts-toolbar :deep(.el-select) { width: 100% !important; }
  .manager-accounts-toolbar > .el-button { width: 100%; }
}
</style>
