<template>
  <div class="manager-accounts-page" v-loading="loading">
    <header class="page-heading">
      <div>
        <h2>Manager Accounts</h2>
        <p>Managers can register students and collect payments, but cannot issue refunds or payouts.</p>
      </div>
      <el-button type="primary" @click="openCreate">Add manager</el-button>
    </header>

    <el-table :data="accounts" row-key="userId" empty-text="No manager accounts">
      <el-table-column prop="displayName" label="Name" min-width="180" />
      <el-table-column prop="username" label="Username" min-width="170" />
      <el-table-column prop="mobile" label="Phone" min-width="140" />
      <el-table-column prop="email" label="Email" min-width="190" />
      <el-table-column label="Status" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Last login" min-width="180">
        <template #default="{ row }">{{ dateTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="110" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">Edit</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? 'Edit manager account' : 'Add manager account'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="Display name" prop="displayName"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="Username" prop="username"><el-input v-model="form.username" :disabled="editing" /></el-form-item>
        <el-form-item :label="editing ? 'New password (optional)' : 'Initial password'" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
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
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';

import {
  createManagerAccount,
  fetchManagerAccounts,
  updateManagerAccount,
  type ManagerAccount,
} from '@/api/manager-accounts';
import { extractErrorMessage } from '@/api/http';
import type { EntityId } from '@/types/domain';

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
const accounts = ref<ManagerAccount[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editing = ref(false);
const editingId = ref<EntityId>();
const form = reactive<AccountForm>(emptyForm());
const rules: FormRules<AccountForm> = {
  displayName: [{ required: true, message: 'Enter a display name.', trigger: 'blur' }],
  username: [{ required: true, message: 'Enter a username.', trigger: 'blur' }],
  password: [{ validator: (_, value: string, done) => {
    if ((!editing.value && !value) || (value && value.length < 8)) return done(new Error('Password must contain at least 8 characters.'));
    done();
  }, trigger: 'blur' }],
};

onMounted(loadAccounts);

async function loadAccounts() {
  loading.value = true;
  try {
    const response = await fetchManagerAccounts({ pageNum: 1, pageSize: 200 });
    accounts.value = response.data.data.records;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, 'Failed to load manager accounts.'));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = false;
  editingId.value = undefined;
  Object.assign(form, emptyForm());
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

function emptyForm(): AccountForm {
  return { username: '', password: '', displayName: '', mobile: '', email: '', status: 'ACTIVE', remark: '' };
}

function dateTime(value?: string) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
</script>

<style scoped>
.manager-accounts-page { display: grid; gap: 18px; padding: 20px; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 0; color: #17324d; }
.page-heading p { margin: 6px 0 0; color: #718096; font-size: 13px; }
@media (max-width: 700px) { .page-heading { align-items: flex-start; flex-direction: column; } }
</style>
