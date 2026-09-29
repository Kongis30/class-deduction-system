<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Teachers</span>
        <div class="panel-header__actions">
          <el-button text size="small" @click="openDeletedDialog">
            <el-icon><Delete /></el-icon>
            Deleted
          </el-button>
        </div>
      </div>

      <el-form class="filters" :model="filters" inline>
        <el-form-item label="Name" class="filters__item">
          <el-input
            v-model="filters.keyword"
            placeholder="Search by name"
            clearable
            style="width: 180px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Subject" class="filters__item">
          <el-input
            v-model="filters.subject"
            placeholder="Search by subject"
            clearable
            style="width: 140px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Status" class="filters__item">
          <el-select
            v-model="filters.status"
            placeholder="All"
            clearable
            style="width: 140px;"
            @change="handleFilterChange"
          >
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="product-table"
        :show-empty="true"
      >
        <el-table-column label="Name" min-width="140">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.teacherName }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Phone" min-width="140">
          <template #default="{ row }">{{ formatUsPhoneDisplay(row.mobile) || '-' }}</template>
        </el-table-column>
        <el-table-column label="Email" min-width="200" prop="email" />
        <el-table-column label="Subjects" min-width="160">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag v-for="subj in row.subjectTags" :key="subj" size="small" type="info">{{ subj }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Status" min-width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Login access" min-width="170">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ accountForTeacher(row)?.username || 'No login' }}</strong>
              <el-tag size="small" :type="accountForTeacher(row)?.status === 'ACTIVE' ? 'success' : 'info'">
                {{ accountForTeacher(row) ? accountStatusLabel(accountForTeacher(row)!.status) : 'Not enabled' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Actions" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" @click="openTeacherAccountDialog(row)">
                {{ accountForTeacher(row) ? 'Edit login' : 'Create login' }}
              </el-button>
              <el-button text type="primary" @click="openTeacherDialog(row)">Edit</el-button>
              <el-button text type="danger" @click="handleDeleteTeacher(row)">Delete</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span>Showing {{ pagination.start }}–{{ pagination.end }} of {{ pagination.total }}</span>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          layout="prev, pager, next, sizes"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </section>

    <el-dialog
      v-model="teacherDialogVisible"
      width="500px"
      :close-on-click-modal="false"
      class="teacher-dialog"
    >
      <template #header>
        <h3>{{ editingId ? 'Edit teacher' : 'Add teacher' }}</h3>
      </template>

      <el-form ref="teacherFormRef" label-position="top" :model="teacherForm" :rules="teacherFormRules" class="teacher-form">
        <div class="teacher-form__row">
          <el-form-item label="Name" prop="teacherName" class="teacher-form__full">
            <el-input v-model="teacherForm.teacherName" placeholder="e.g. Emily Chen" />
          </el-form-item>
        </div>

        <div class="teacher-form__row">
          <el-form-item label="Phone" prop="mobile">
            <el-input v-model="teacherForm.mobile" placeholder="(626) 555-0101" />
          </el-form-item>
          <el-form-item label="Email" prop="email">
            <el-input v-model="teacherForm.email" placeholder="emily@example.com" />
          </el-form-item>
        </div>

        <div class="teacher-form__row">
          <el-form-item label="Subjects" class="teacher-form__full">
            <el-select v-model="teacherForm.subjectTags" multiple placeholder="Select subjects" style="width: 100%;">
              <el-option v-for="subj in subjectOptions" :key="subj" :label="subj" :value="subj" />
            </el-select>
          </el-form-item>
        </div>

        <div class="teacher-form__row">
          <el-form-item label="Status" prop="status">
            <el-select v-model="teacherForm.status" placeholder="Select status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>

        <div class="teacher-form__row">
          <el-form-item label="Notes" class="teacher-form__full">
            <el-input v-model="teacherForm.notes" type="textarea" :rows="2" placeholder="Optional notes" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="teacherDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleTeacherSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="teacherAccountDialogVisible"
      width="480px"
      :close-on-click-modal="false"
      class="teacher-dialog"
    >
      <template #header>
        <h3>{{ teacherAccountMode === 'create' ? 'Create teacher login' : 'Edit teacher login' }}</h3>
      </template>

      <div v-if="teacherAccountTarget" class="payment-detail">
        <div class="metric-grid">
          <div>
            <label>Teacher</label>
            <strong>{{ teacherAccountTarget.teacherName }}</strong>
          </div>
          <div>
            <label>Teacher no.</label>
            <strong>{{ teacherAccountTarget.teacherNo }}</strong>
          </div>
        </div>
        <el-form label-position="top" class="teacher-form">
          <el-form-item label="Username">
            <el-input v-model="teacherAccountForm.username" :readonly="teacherAccountMode === 'create'" placeholder="Login username" />
          </el-form-item>
          <el-form-item label="Display name">
            <el-input v-model="teacherAccountForm.displayName" placeholder="Defaults to teacher name" />
          </el-form-item>
          <el-form-item label="Email">
            <el-input v-model="teacherAccountForm.email" placeholder="Login email" />
          </el-form-item>
          <el-form-item label="Status">
            <el-select v-model="teacherAccountForm.status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item :label="teacherAccountMode === 'create' ? 'Initial password' : 'New password'">
            <el-input
              v-model="teacherAccountForm.password"
              type="password"
              :readonly="teacherAccountMode === 'create'"
              :placeholder="teacherAccountMode === 'create' ? 'Min 8 characters' : 'Leave blank to keep current'"
              show-password
            />
          </el-form-item>
          <el-form-item label="Confirm password">
            <el-input v-model="teacherAccountForm.confirmPassword" type="password" placeholder="Re-enter password" show-password />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="teacherAccountDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="teacherAccountSaving" @click="submitTeacherAccount">Save</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-if="deleteDialogVisible"
      v-model="deleteDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      class="teacher-dialog"
      @close="resetDeleteDialog"
    >
      <template #header>
        <h3>Delete teacher</h3>
      </template>

      <div v-if="deleteTarget" class="delete-confirm">
        <p class="delete-warning-text">
          This action is only for mistaken teacher profiles. The system will remove login access, unassign active
          teaching links, and cancel future schedules. Use Inactive when you need to preserve operations.
        </p>
        <div class="metric-grid">
          <div>
            <label>Teacher</label>
            <strong>{{ deleteTarget.teacherName }}</strong>
          </div>
          <div>
            <label>Managed courses</label>
            <strong>{{ deleteManagedCourseCountLabel }}</strong>
          </div>
          <div>
            <label>Login access</label>
            <strong>{{ accountForTeacher(deleteTarget) ? 'Enabled' : 'No login' }}</strong>
          </div>
        </div>
        <p v-if="deleteConfirmRemaining > 0" class="delete-cooldown">
          Please wait {{ deleteConfirmRemaining }} second(s) before confirming.
        </p>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeDeleteDialog">Cancel</el-button>
          <el-button
            type="danger"
            :disabled="deleteConfirmRemaining > 0"
            :loading="deletingTeacher"
            @click="confirmDeleteTeacher"
          >
            {{ deleteConfirmRemaining > 0 ? `Delete (${deleteConfirmRemaining}s)` : 'Delete teacher' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="subjectManagerVisible"
      title="Manage subject options"
      width="620px"
      :close-on-click-modal="false"
      class="teacher-dialog"
      @closed="resetSubjectForm"
    >
      <p class="subject-manager__hint">These subjects are available when setting a teacher’s subjects. Existing teacher profiles keep their saved labels when an option is renamed or removed.</p>
      <el-form class="subject-manager__form" inline @submit.prevent="saveSubjectOption">
        <el-form-item label="Subject name">
          <el-input
            v-model="subjectForm.subjectName"
            maxlength="64"
            placeholder="e.g. Mathematics"
            @keyup.enter="saveSubjectOption"
          />
        </el-form-item>
        <el-form-item label="Order">
          <el-input-number v-model="subjectForm.displayOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="subjectSaving" @click="saveSubjectOption">
            {{ editingSubjectId == null ? 'Add subject' : 'Save changes' }}
          </el-button>
          <el-button v-if="editingSubjectId != null" @click="resetSubjectForm">Cancel edit</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="subjectsLoading" :data="subjectItems" row-key="id" class="product-table" max-height="360">
        <el-table-column label="Subject" min-width="220" prop="subjectName" />
        <el-table-column label="Order" width="100" align="center" prop="displayOrder" />
        <el-table-column label="Actions" width="150" align="right" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" @click="editSubjectOption(row)">Edit</el-button>
              <el-button text type="danger" :loading="deletingSubjectId === row.id" @click="removeSubjectOption(row)">Remove</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!subjectsLoading && subjectItems.length === 0" description="No subject options yet." />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="subjectManagerVisible = false">Close</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Deleted Teachers Dialog -->
    <el-dialog
      v-model="deletedDialogVisible"
      title="Deleted Teachers"
      width="720px"
    >
      <p class="deleted-hint">These instructor profiles have been deleted. This view is read-only.</p>
      <el-table v-loading="deletedLoading" :data="deletedRows" row-key="id" class="product-table" max-height="420">
        <el-table-column label="Name" min-width="140" prop="teacherName" />
        <el-table-column label="Phone" min-width="140">
          <template #default="{ row }">{{ formatUsPhoneDisplay(row.mobile) || '-' }}</template>
        </el-table-column>
        <el-table-column label="Email" min-width="200">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="Subjects" min-width="160">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag v-for="subj in row.subjectTags" :key="subj" size="small" type="info">{{ subj }}</el-tag>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!deletedLoading && deletedRows.length === 0" description="No deleted instructors." />
      <template #footer>
        <el-button @click="deletedDialogVisible = false">Close</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Delete } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';

import {
  createTeacherAccount,
  fetchTeacherAccounts,
  updateTeacherAccount,
  type AccountUpdatePayload,
  type TeacherAccountCreatePayload,
} from '@/api/auth';
import {
  createTeacher,
  createSubject,
  deleteSubject,
  fetchDeletedTeachers,
  fetchSubjects,
  fetchTeachers,
  deleteTeacher,
  updateSubject,
  updateTeacher,
  type SubjectSavePayload,
  type TeacherQuery,
  type TeacherSavePayload,
} from '@/api/base-data';
import { extractErrorMessage } from '@/api/http';
import { fetchClassGroupsByTeacher } from '@/api/schedule';
import type { SubjectItem, TeacherAccountItem, TeacherItem } from '@/types/domain';
import { DEFAULT_INITIAL_PASSWORD, usernameFromName } from '@/utils/account-credentials';
import { formatUsPhoneDisplay } from '@/utils/phone-format';

const loading = ref(false);
const submitting = ref(false);
const teacherDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const teacherFormRef = ref();


const filters = reactive({
  keyword: '',
  subject: '',
  status: '',
});

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
  get start() {
    return this.total === 0 ? 0 : (this.page - 1) * this.size + 1;
  },
  get end() {
    return Math.min(this.page * this.size, this.total);
  },
});

const tableData = ref<TeacherItem[]>([]);
const teacherAccounts = ref<TeacherAccountItem[]>([]);
const teacherAccountDialogVisible = ref(false);
const teacherAccountSaving = ref(false);
const teacherAccountMode = ref<'create' | 'edit'>('create');
const teacherAccountTarget = ref<TeacherItem | null>(null);
const editingTeacherAccount = ref<TeacherAccountItem | null>(null);
const teacherAccountForm = reactive({
  username: '',
  displayName: '',
  email: '',
  status: 'ACTIVE',
  password: '',
  confirmPassword: '',
});

const DELETE_COOLDOWN_SECONDS = 10;
const deleteDialogVisible = ref(false);
const deleteTarget = ref<TeacherItem | null>(null);
const deleteConfirmRemaining = ref(DELETE_COOLDOWN_SECONDS);
const deletingTeacher = ref(false);
const deleteManagedCourseCount = ref<number | null>(null);
let deleteCooldownTimer: number | undefined;
let deleteCooldownReleaseTimer: number | undefined;
let deleteCooldownDeadline = 0;

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}


async function loadData() {
  loading.value = true;
  try {
    const params: TeacherQuery = {
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      pageNum: pagination.page,
      pageSize: pagination.size,
    };
    const res = await fetchTeachers(params);
    const data = res.data.data;
    tableData.value = data.records;
    pagination.total = data.total;
    await loadTeacherAccounts();
  } catch {
    ElMessage.error('Failed to load teachers.');
  } finally {
    loading.value = false;
  }
}

async function loadTeacherAccounts() {
  try {
    const res = await fetchTeacherAccounts({ pageSize: 500 });
    teacherAccounts.value = res.data.data.records ?? [];
  } catch {
    teacherAccounts.value = [];
  }
}

function accountForTeacher(row: TeacherItem) {
  return teacherAccounts.value.find((account) => String(account.teacherId) === String(row.id));
}

function accountStatusLabel(status?: string) {
  return status === 'ACTIVE' ? 'Active' : 'Inactive';
}

function openTeacherAccountDialog(row: TeacherItem) {
  const account = accountForTeacher(row);
  teacherAccountTarget.value = row;
  editingTeacherAccount.value = account ?? null;
  teacherAccountMode.value = account ? 'edit' : 'create';
  Object.assign(teacherAccountForm, {
    username: account?.username ?? usernameFromName(row.teacherName),
    displayName: account?.displayName ?? row.teacherName,
    email: account?.email ?? row.email ?? '',
    status: account?.status ?? 'ACTIVE',
    password: account ? '' : DEFAULT_INITIAL_PASSWORD,
    confirmPassword: account ? '' : DEFAULT_INITIAL_PASSWORD,
  });
  teacherAccountDialogVisible.value = true;
}

function validateTeacherAccountForm() {
  if (!teacherAccountForm.username.trim()) {
    ElMessage.warning('Username is required.');
    return false;
  }
  if (teacherAccountMode.value === 'create' && teacherAccountForm.password.length < 8) {
    ElMessage.warning('Password must be at least 8 characters.');
    return false;
  }
  if (teacherAccountForm.password && teacherAccountForm.password.length < 8) {
    ElMessage.warning('Password must be at least 8 characters.');
    return false;
  }
  if (teacherAccountForm.password !== teacherAccountForm.confirmPassword) {
    ElMessage.warning('Passwords do not match.');
    return false;
  }
  return true;
}

async function submitTeacherAccount() {
  if (!teacherAccountTarget.value || !validateTeacherAccountForm()) {
    return;
  }
  teacherAccountSaving.value = true;
  try {
    if (teacherAccountMode.value === 'create') {
      const payload: TeacherAccountCreatePayload = {
        teacherId: teacherAccountTarget.value.id,
        teacherName: teacherAccountTarget.value.teacherName,
        username: teacherAccountForm.username.trim(),
        password: teacherAccountForm.password,
        displayName: teacherAccountForm.displayName.trim() || undefined,
        mobile: teacherAccountTarget.value.mobile ?? undefined,
        email: teacherAccountForm.email.trim() || undefined,
        subjectTags: teacherAccountTarget.value.subjectTags ?? undefined,
        status: teacherAccountForm.status,
      };
      await createTeacherAccount(payload);
      ElMessage.success('Teacher login created.');
    } else if (editingTeacherAccount.value) {
      const payload: AccountUpdatePayload = {
        username: teacherAccountForm.username.trim(),
        displayName: teacherAccountForm.displayName.trim() || undefined,
        email: teacherAccountForm.email.trim() || undefined,
        status: teacherAccountForm.status,
        password: teacherAccountForm.password || undefined,
      };
      await updateTeacherAccount(editingTeacherAccount.value.userId, payload);
      ElMessage.success('Teacher login updated.');
    }
    teacherAccountDialogVisible.value = false;
    await loadTeacherAccounts();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    teacherAccountSaving.value = false;
  }
}

watch(() => filters.status, handleFilterChange);


const subjectItems = ref<SubjectItem[]>([]);
const subjectOptions = computed(() => subjectItems.value.map(subject => subject.subjectName));
const subjectManagerVisible = ref(false);
const subjectsLoading = ref(false);
const subjectSaving = ref(false);
const deletingSubjectId = ref<SubjectItem['id'] | null>(null);
const editingSubjectId = ref<SubjectItem['id'] | null>(null);
const editingSubjectNotes = ref<string | undefined>();
const subjectForm = reactive({ subjectName: '', displayOrder: 1 });

// 老师资料与管理窗口共用服务端科目选项，避免静态列表和实际配置不一致。
async function loadSubjectOptions() {
  subjectsLoading.value = true;
  try {
    const response = await fetchSubjects();
    subjectItems.value = response.data.data ?? [];
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    subjectsLoading.value = false;
  }
}

async function openSubjectManager() {
  resetSubjectForm();
  subjectManagerVisible.value = true;
  await loadSubjectOptions();
  subjectForm.displayOrder = subjectItems.value.length + 1;
}

function editSubjectOption(subject: SubjectItem) {
  editingSubjectId.value = subject.id;
  editingSubjectNotes.value = subject.notes;
  subjectForm.subjectName = subject.subjectName;
  subjectForm.displayOrder = subject.displayOrder;
}

function resetSubjectForm() {
  editingSubjectId.value = null;
  editingSubjectNotes.value = undefined;
  subjectForm.subjectName = '';
  subjectForm.displayOrder = subjectItems.value.length + 1;
}

async function saveSubjectOption() {
  const subjectName = subjectForm.subjectName.trim();
  if (!subjectName) {
    ElMessage.warning('Enter a subject name.');
    return;
  }
  const payload: SubjectSavePayload = {
    subjectName,
    displayOrder: subjectForm.displayOrder,
    status: 'ACTIVE',
    notes: editingSubjectNotes.value,
  };
  subjectSaving.value = true;
  try {
    if (editingSubjectId.value == null) {
      await createSubject(payload);
      ElMessage.success('Subject added.');
    } else {
      await updateSubject(editingSubjectId.value, payload);
      ElMessage.success('Subject updated.');
    }
    await loadSubjectOptions();
    resetSubjectForm();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    subjectSaving.value = false;
  }
}

async function removeSubjectOption(subject: SubjectItem) {
  try {
    await ElMessageBox.confirm(
      `Remove “${subject.subjectName}” from the available subject options? Existing teacher records will keep their saved subject labels.`,
      'Remove subject option',
      { type: 'warning', confirmButtonText: 'Remove', cancelButtonText: 'Cancel' },
    );
  } catch {
    return;
  }
  deletingSubjectId.value = subject.id;
  try {
    await deleteSubject(subject.id);
    ElMessage.success('Subject removed.');
    if (editingSubjectId.value === subject.id) resetSubjectForm();
    await loadSubjectOptions();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    deletingSubjectId.value = null;
  }
}


const teacherForm = reactive<TeacherSavePayload>({
  teacherName: '',
  mobile: '',
  email: '',
  subjectTags: [],
  status: 'ACTIVE',
  notes: '',
});

const teacherFormRules = {
  teacherName: [{ required: true, message: 'Name is required' }],
  mobile: [],
  email: [{ type: 'email', message: 'Email format is invalid' }],
};

async function openTeacherDialog(row?: TeacherItem) {
  editingId.value = row?.id ?? null;
  Object.assign(teacherForm, {
    teacherName: row?.teacherName ?? '',
    mobile: row?.mobile ?? '',
    email: row?.email ?? '',
    subjectTags: [...(row?.subjectTags || [])],
    status: row?.status ?? 'ACTIVE',
    notes: row?.notes ?? '',
  });
  teacherFormRef.value?.clearValidate();
  teacherDialogVisible.value = true;
}

async function handleTeacherSubmit() {
  const valid = await teacherFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  // Convert empty strings to null so backend @Email/@Pattern validation passes
  // (@Email rejects "" but accepts null; @Pattern(regexp="^$|...") accepts "")
  const payload: TeacherSavePayload = {
    teacherName: teacherForm.teacherName,
    mobile: teacherForm.mobile || null,
    email: teacherForm.email || null,
    subjectTags: teacherForm.subjectTags?.length ? teacherForm.subjectTags : [],
    status: teacherForm.status,
    notes: teacherForm.notes || null,
  };

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateTeacher(editingId.value, payload);
      ElMessage.success('Teacher updated.');
    } else {
      await createTeacher(payload);
      ElMessage.success('Teacher added.');
    }
    teacherDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

defineExpose({
  openCreateDialog: () => openTeacherDialog(),
  openSubjectManager,
});

async function handleDeleteTeacher(row: TeacherItem) {
  deleteTarget.value = row;
  deleteManagedCourseCount.value = null;
  deleteDialogVisible.value = true;
  startDeleteCooldown();
  try {
    const response = await fetchClassGroupsByTeacher(row.id);
    deleteManagedCourseCount.value = response.data.data?.length ?? 0;
  } catch {
    deleteManagedCourseCount.value = null;
  }
}

const deleteManagedCourseCountLabel = computed(() =>
  deleteManagedCourseCount.value == null ? 'Loading...' : deleteManagedCourseCount.value
);

function startDeleteCooldown() {
  clearDeleteCooldown();
  deleteCooldownDeadline = Date.now() + DELETE_COOLDOWN_SECONDS * 1000;
  deleteCooldownTimer = window.setInterval(() => {
    deleteConfirmRemaining.value = Math.max(0, Math.ceil((deleteCooldownDeadline - Date.now()) / 1000));
    if (deleteConfirmRemaining.value === 0) {
      clearDeleteCooldown();
    }
  }, 250);
  deleteCooldownReleaseTimer = window.setTimeout(() => {
    deleteConfirmRemaining.value = 0;
    clearDeleteCooldown();
  }, DELETE_COOLDOWN_SECONDS * 1000);
  deleteConfirmRemaining.value = DELETE_COOLDOWN_SECONDS;
}

function clearDeleteCooldown() {
  if (deleteCooldownTimer != null) {
    window.clearInterval(deleteCooldownTimer);
    deleteCooldownTimer = undefined;
  }
  if (deleteCooldownReleaseTimer != null) {
    window.clearTimeout(deleteCooldownReleaseTimer);
    deleteCooldownReleaseTimer = undefined;
  }
  deleteCooldownDeadline = 0;
}

function resetDeleteDialog() {
  clearDeleteCooldown();
  deleteTarget.value = null;
  deleteManagedCourseCount.value = null;
  deleteConfirmRemaining.value = DELETE_COOLDOWN_SECONDS;
  deletingTeacher.value = false;
}

function closeDeleteDialog() {
  deleteDialogVisible.value = false;
  resetDeleteDialog();
}

async function confirmDeleteTeacher() {
  if (!deleteTarget.value || deleteConfirmRemaining.value > 0) {
    return;
  }
  deletingTeacher.value = true;
  try {
    await deleteTeacher(deleteTarget.value.id);
    ElMessage.success('Teacher deleted.');
    closeDeleteDialog();
    await loadData();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    deletingTeacher.value = false;
  }
}

// ─── Deleted Teachers Dialog ───────────────────────────
const deletedDialogVisible = ref(false);
const deletedLoading = ref(false);
const deletedRows = ref<TeacherItem[]>([]);

async function openDeletedDialog() {
  deletedDialogVisible.value = true;
  deletedLoading.value = true;
  deletedRows.value = [];
  try {
    const res = await fetchDeletedTeachers();
    deletedRows.value = res.data.data ?? [];
  } catch {
    ElMessage.error('Failed to load deleted instructors.');
  } finally {
    deletedLoading.value = false;
  }
}


loadData();
void loadSubjectOptions();

onBeforeUnmount(() => {
  clearDeleteCooldown();
});
</script>

<style scoped>
.course-product-panel {
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

.filters {
  margin-bottom: 12px;
}

.filters__item {
  margin-right: 16px;
  margin-bottom: 0;
}

.filters__item :deep(.el-form-item__label) {
  color: #606266;
  font-size: 13px;
}

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cell-stack strong {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.cell-stack span {
  color: #909399;
  font-size: 12px;
}

.tag-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.table-actions {
  display: flex;
  gap: 4px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.deleted-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
}

.subject-manager__hint {
  margin: 0 0 14px;
  color: #64748b;
  font-size: 13px;
}

.subject-manager__form {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 0 10px;
  margin-bottom: 8px;
}

.subject-manager__form :deep(.el-form-item) {
  margin-right: 0;
}

.delete-confirm {
  display: grid;
  gap: 14px;
}

.delete-warning-text {
  margin: 0;
  border: 1px solid #f5c2c7;
  border-radius: 6px;
  background: #fff5f5;
  padding: 10px 12px;
  color: #b42318;
  font-size: 13px;
  line-height: 1.5;
}

.delete-cooldown {
  margin: 0;
  color: #b42318;
  font-size: 12px;
  font-weight: 600;
}

/* Teacher dialog */
.teacher-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.teacher-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.teacher-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.teacher-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.teacher-form__row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 14px;
}

.teacher-form__full {
  grid-column: 1 / -1;
}
</style>
