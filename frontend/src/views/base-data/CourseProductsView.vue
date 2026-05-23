<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <!-- 工具栏：标题 + 操作按钮 -->
      <div class="panel-header">
        <span class="panel-header__title">Course Products</span>
        <div class="panel-header__actions">
          <el-button @click="openSubjectDialog()">Manage subjects</el-button>
          <el-button type="primary" @click="openProductDialog()">
            <el-icon><Plus /></el-icon>
            Add course product
          </el-button>
        </div>
      </div>

      <!-- 筛选表单 -->
      <el-form class="filters" :model="filters" inline>
        <el-form-item label="Product name" class="filters__item">
          <el-input
            v-model="filters.productName"
            placeholder="Search by name"
            clearable
            style="width: 180px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Subject" class="filters__item">
          <el-input
            v-model="filters.subjectName"
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

      <!-- Tabs: 1v1 / Group -->
      <el-tabs v-model="activeTab" class="product-tabs">
        <el-tab-pane label="1:1 Private" name="ONE_ON_ONE">
          <el-table
            v-loading="loading"
            :data="oneOnOneData"
            row-key="id"
            class="product-table"
            :show-empty="true"
          >
            <el-table-column label="Product name" min-width="220">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong>{{ row.productName }}</strong>
                  <span v-if="row.subjectName">{{ row.subjectName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Total hours" min-width="100" align="center">
              <template #default="{ row }">{{ row.defaultTotalUnits }}</template>
            </el-table-column>
            <el-table-column label="Original price" min-width="110" align="right">
              <template #default="{ row }">{{ formatPrice(row.listPrice) }}</template>
            </el-table-column>
            <el-table-column label="Status" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" min-width="140" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button text type="primary" @click="openProductDialog(row)">Edit</el-button>
                  <el-button text type="danger" @click="handleDelete(row)">Delete</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="Group Class" name="GROUP">
          <el-table
            v-loading="loading"
            :data="groupData"
            row-key="id"
            class="product-table"
            :show-empty="true"
          >
            <el-table-column label="Product name" min-width="220">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong>{{ row.productName }}</strong>
                  <span v-if="row.subjectName">{{ row.subjectName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Expiry" min-width="100" align="center">
              <template #default="{ row }">{{ row.expiryDurationDays ? row.expiryDurationDays + 'd' : '—' }}</template>
            </el-table-column>
            <el-table-column label="Schedule" min-width="240">
              <template #default="{ row }">
                <span v-if="row.slots?.length">{{ formatSlots(row.slots) }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="Original price" min-width="110" align="right">
              <template #default="{ row }">{{ formatPrice(row.listPrice) }}</template>
            </el-table-column>
            <el-table-column label="Status" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" min-width="140" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button text type="primary" @click="openProductDialog(row)">Edit</el-button>
                  <el-button text type="danger" @click="handleDelete(row)">Delete</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      width="780px"
      :close-on-click-modal="false"
      class="product-dialog"
    >
      <template #header>
        <h3>{{ editingId ? 'Edit course product' : 'New course product' }}</h3>
      </template>

      <el-form ref="formRef" label-position="top" :model="form" :rules="formRules" class="product-form">
        <div class="product-form__row">
          <el-form-item label="Product name" prop="productName" class="product-form__full">
            <el-input v-model="form.productName" placeholder="e.g. Math Standard Package" />
          </el-form-item>
        </div>

        <div class="product-form__row">
          <el-form-item label="Subject" prop="subjectName">
            <el-select v-model="form.subjectName" placeholder="Select subject" filterable style="width: 100%;">
              <el-option v-for="subj in subjects" :key="subj.subjectName" :label="subj.subjectName" :value="subj.subjectName" />
            </el-select>
          </el-form-item>
          <el-form-item label="Teaching format" prop="teachingMode">
            <el-select v-model="form.teachingMode" placeholder="Select format" style="width: 100%;">
              <el-option label="1:1 private" value="ONE_ON_ONE" />
              <el-option label="Group" value="GROUP" />
            </el-select>
          </el-form-item>
        </div>

        <!-- 1v1: Total hours -->
        <div v-if="form.teachingMode === 'ONE_ON_ONE'" class="product-form__row">
          <el-form-item label="Total hours" prop="defaultTotalUnits">
            <el-input-number v-model="form.defaultTotalUnits" :min="1" :step="1" :precision="0" :controls="false" style="width: 100%;" />
          </el-form-item>
        </div>

        <!-- Group: Expiry date -->
        <div v-if="form.teachingMode === 'GROUP'" class="product-form__row">
          <el-form-item label="Expiry date" prop="expiryDate" class="product-form__full">
            <el-date-picker
              v-model="form.expiryDate"
              type="date"
              placeholder="Select expiry date (calculated from enrollment)"
              style="width: 100%;"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledPastDate"
            />
          </el-form-item>
        </div>

        <!-- Group: Default classroom -->
        <div v-if="form.teachingMode === 'GROUP'" class="product-form__row">
          <el-form-item label="Default classroom" class="product-form__full">
            <el-select v-model="form.defaultClassroomId" placeholder="Select classroom" filterable clearable style="width: 100%;">
              <el-option v-for="room in classrooms" :key="room.id" :label="room.roomName" :value="room.id" />
            </el-select>
          </el-form-item>
        </div>

        <!-- Group: Weekly schedule calendar -->
        <div v-if="form.teachingMode === 'GROUP'" class="product-form__row">
          <el-form-item label="Weekly schedule" class="product-form__full">
            <WeeklyTimeSelector
              v-model="formSlots"
              :occupied-slots="occupiedSlots"
            />
          </el-form-item>
        </div>

        <div class="product-form__row">
          <el-form-item label="Original price ($)" prop="listPrice">
            <el-input-number v-model="form.listPrice" :min="0" :precision="2" :controls="false" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="Status" prop="status">
            <el-select v-model="form.status" placeholder="Select status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>

        <div class="product-form__row">
          <el-form-item label="Product code" class="product-form__full">
            <el-input v-model="form.productCode" disabled placeholder="Auto-generated by system" />
          </el-form-item>
        </div>

        <div class="product-form__row">
          <el-form-item label="Notes" class="product-form__full">
            <el-input v-model="form.notes" type="textarea" :rows="2" placeholder="Optional notes" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Subject 管理弹窗 -->
    <el-dialog
      v-model="subjectDialogVisible"
      width="720px"
      :close-on-click-modal="false"
      class="subject-dialog"
    >
      <template #header>
        <h3>Manage subjects</h3>
      </template>

      <!-- Subject 列表 -->
      <el-table :data="subjectTableData" row-key="id" size="small" class="subject-table">
        <el-table-column label="Display order" width="110" align="center">
          <template #default="{ row }">{{ row.displayOrder }}</template>
        </el-table-column>
        <el-table-column label="Subject name" min-width="140">
          <template #default="{ row }">
            <strong>{{ row.subjectName }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Notes" min-width="160">
          <template #default="{ row }">
            <span class="notes-text">{{ row.notes ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openSubjectEditDialog(row)">Edit</el-button>
            <el-button text type="danger" size="small" @click="handleSubjectDelete(row)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 新建 / 编辑 Subject 表单 -->
      <div class="subject-form">
        <h4 class="subject-form__title">
          {{ subjectEditingId ? 'Edit subject' : 'Add new subject' }}
        </h4>
        <el-form ref="subjectFormRef" :model="subjectForm" :rules="subjectFormRules" inline>
          <el-form-item label="Subject name" prop="subjectName" style="flex: 1;">
            <el-input v-model="subjectForm.subjectName" placeholder="e.g. Art" style="width: 160px;" />
          </el-form-item>
          <el-form-item label="Display order">
            <el-input-number v-model="subjectForm.displayOrder" :min="1" :max="999" :controls="false" style="width: 80px;" />
          </el-form-item>
          <el-form-item label="Status">
            <el-select v-model="subjectForm.status" style="width: 110px;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="Notes">
            <el-input v-model="subjectForm.notes" placeholder="Optional" style="width: 160px;" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="small" @click="handleSubjectSave">
              {{ subjectEditingId ? 'Update' : 'Add' }}
            </el-button>
            <el-button v-if="subjectEditingId" size="small" @click="() => { subjectEditingId = null; Object.assign(subjectForm, { subjectName: '', displayOrder: 99, status: 'ACTIVE', notes: '' }); }">
              Cancel
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, reactive, ref, watch } from 'vue';
import dayjs from 'dayjs';

import {
  createCourseProduct,
  createSubject,
  deleteCourseProduct,
  deleteSubject,
  fetchAllClassrooms,
  fetchCourseProducts,
  fetchSubjects,
  updateCourseProduct,
  updateSubject,
  type CourseProductQuery,
  type CourseProductSavePayload,
} from '@/api/base-data';
import { fetchClassroomOccupancy } from '@/api/schedule';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import type { CourseProductItem, SubjectItem } from '@/types/domain';

const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const formRef = ref();

const classrooms = ref<{ id: number; roomName: string }[]>([]);
const formSlots = ref<SlotDef[]>([]);
const occupiedSlots = ref<OccupiedSlotDef[]>([]);

// ─── Subject 管理弹窗 ───────────────────────────────────
const subjectDialogVisible = ref(false);
const subjectTableData = ref<SubjectItem[]>([]);
const subjectFormRef = ref();
const subjectEditingId = ref<number | null>(null);

const subjectForm = reactive({
  subjectName: '',
  displayOrder: 99,
  status: 'ACTIVE',
  notes: '',
});

const subjectFormRules = {
  subjectName: [{ required: true, message: 'Subject name is required' }],
};

function openSubjectDialog() {
  subjectEditingId.value = null;
  Object.assign(subjectForm, { subjectName: '', displayOrder: 99, status: 'ACTIVE', notes: '' });
  loadSubjectsForDialog();
  subjectDialogVisible.value = true;
}

async function loadSubjectsForDialog() {
  try {
    const res = await fetchSubjects();
    subjectTableData.value = res.data.data;
  } catch (error) {
    console.error('Failed to load subjects:', error);
    ElMessage.error('Failed to load subjects.');
  }
}

function openSubjectEditDialog(row: SubjectItem) {
  subjectEditingId.value = row.id;
  Object.assign(subjectForm, {
    subjectName: row.subjectName,
    displayOrder: row.displayOrder,
    status: row.status,
    notes: row.notes ?? '',
  });
}

async function handleSubjectSave() {
  const valid = await subjectFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  try {
    if (subjectEditingId.value) {
      await updateSubject(subjectEditingId.value, { ...subjectForm });
      ElMessage.success('Subject updated.');
    } else {
      await createSubject({ ...subjectForm });
      ElMessage.success('Subject created.');
    }
    await loadSubjectsForDialog();
    subjectEditingId.value = null;
    Object.assign(subjectForm, { subjectName: '', displayOrder: 99, status: 'ACTIVE', notes: '' });
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  }
}

async function handleSubjectDelete(row: SubjectItem) {
  try {
    await ElMessageBox.confirm(
      `Delete subject "${row.subjectName}"?`,
      'Confirm delete',
      { type: 'warning' },
    );
    await deleteSubject(row.id);
    ElMessage.success('Deleted.');
    await loadSubjectsForDialog();
    // 如果当前正在编辑的被删了，重置表单
    if (subjectEditingId.value === row.id) {
      subjectEditingId.value = null;
      Object.assign(subjectForm, { subjectName: '', displayOrder: 99, status: 'ACTIVE', notes: '' });
    }
  } catch {
    // 用户取消
  }
}

// ─── 筛选 & 分页 ───────────────────────────────────────
const filters = reactive({
  productName: '',
  subjectName: '',
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

const tableData = ref<CourseProductItem[]>([]);
const activeTab = ref('ONE_ON_ONE');

const oneOnOneData = computed(() =>
  tableData.value.filter(p => p.teachingMode === 'ONE_ON_ONE')
);

const groupData = computed(() =>
  tableData.value.filter(p => p.teachingMode === 'GROUP')
);

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

// ─── 加载数据 ──────────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const params: CourseProductQuery = {
      keyword: [filters.productName, filters.subjectName].filter(Boolean).join(' ') || undefined,
      status: filters.status || undefined,
      pageNum: pagination.page,
      pageSize: pagination.size,
    };
    const res = await fetchCourseProducts(params);
    const data = res.data.data;
    tableData.value = data.records;
    pagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load course products.');
  } finally {
    loading.value = false;
  }
}

// ─── Subjects 列表 ─────────────────────────────────────
const subjects = ref<SubjectItem[]>([]);

async function loadSubjects() {
  try {
    const res = await fetchSubjects();
    subjects.value = res.data.data;
  } catch {
    ElMessage.error('Failed to load subjects.');
  }
}

// ─── 新建 / 编辑 ───────────────────────────────────────
const form = reactive<CourseProductSavePayload & { expiryDate: string | undefined }>({
  productCode: '',
  productName: '',
  subjectName: '',
  teachingMode: 'GROUP',
  defaultTotalUnits: 40,
  defaultLessonUnits: 2,
  expiryDurationDays: undefined,
  expiryDate: undefined,
  defaultClassroomId: undefined,
  listPrice: 0,
  status: 'ACTIVE',
  notes: '',
  slots: [],
});

const formRules = {
  productName: [{ required: true, message: 'Product name is required' }],
  teachingMode: [{ required: true, message: 'Teaching format is required' }],
  defaultTotalUnits: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (form.teachingMode === 'ONE_ON_ONE' && (value === undefined || value === null)) {
          callback(new Error('Total hours is required'));
        } else if (form.teachingMode === 'ONE_ON_ONE' && (value * 2) % 1 !== 0) {
          callback(new Error('Must be a multiple of 0.5'));
        } else {
          callback();
        }
      }, trigger: 'change',
    },
  ],
  listPrice: [{ required: true, message: 'List price is required' }],
  expiryDate: [{
    validator: (rule: any, value: any, callback: any) => {
      if (form.teachingMode === 'GROUP' && !value) {
        callback(new Error('Expiry date is required for group products'));
      } else {
        callback();
      }
    }, trigger: 'change',
  }],
};

// 监听教室选择，加载该教室的占用情况
watch(() => form.defaultClassroomId, async (classroomId) => {
  if (!classroomId || form.teachingMode !== 'GROUP') {
    occupiedSlots.value = [];
    return;
  }
  try {
    const now = new Date();
    const dayOfWeek = now.getDay();
    const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
    const monday = new Date(now);
    monday.setDate(now.getDate() + mondayOffset);
    monday.setHours(0, 0, 0, 0);
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);
    sunday.setHours(23, 59, 59, 999);

    const res = await fetchClassroomOccupancy({
      classroomId,
      startAt: monday.toISOString(),
      endAt: sunday.toISOString(),
      pageSize: 200,
    });
    occupiedSlots.value = res.data.data.records.map(item => {
      const start = new Date(item.plannedStartAt);
      const end = new Date(item.plannedEndAt);
      const weekday = start.getDay() === 0 ? 7 : start.getDay();
      const fmt = (d: Date) =>
        `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
      return {
        weekday,
        startTime: fmt(start),
        endTime: fmt(end),
        courseLabel: item.packageName || item.lessonNo,
        studentName: item.studentName,
        teacherName: item.teacherName,
        classroomName: item.classroomName,
      };
    });
  } catch {
    occupiedSlots.value = [];
  }
});

function calcExpiryDate(days: number | undefined): string | undefined {
  if (days == null) return undefined;
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function calcExpiryDays(dateStr: string | undefined): number | undefined {
  if (!dateStr) return undefined;
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  const target = new Date(dateStr + 'T00:00:00');
  return Math.round((target.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
}

function openProductDialog(row?: CourseProductItem) {
  editingId.value = row?.id ?? null;
  if (row) {
    Object.assign(form, {
      productCode: row.productCode,
      productName: row.productName,
      subjectName: row.subjectName ?? '',
      teachingMode: row.teachingMode,
      defaultTotalUnits: row.defaultTotalUnits,
      defaultLessonUnits: row.defaultLessonUnits,
      expiryDurationDays: row.expiryDurationDays ?? undefined,
      expiryDate: calcExpiryDate(row.expiryDurationDays),
      defaultClassroomId: row.defaultClassroomId ?? undefined,
      listPrice: row.listPrice,
      status: row.status,
      notes: row.notes ?? '',
      slots: row.slots ? row.slots.map(s => ({ weekday: s.weekday, startTime: s.startTime, endTime: s.endTime })) : [],
    });
    // 回显排课时段到日历组件
    formSlots.value = row.slots
      ? row.slots.map(s => {
          const startM = parseInt(s.startTime.split(':')[0]) * 60 + parseInt(s.startTime.split(':')[1]);
          const endM = parseInt(s.endTime.split(':')[0]) * 60 + parseInt(s.endTime.split(':')[1]);
          return {
            weekday: s.weekday,
            startTime: s.startTime,
            endTime: s.endTime,
            plannedUnits: Math.round((endM - startM) / 30),
          };
        })
      : [];
    // 编辑时如果已选了教室，触发占用加载
    occupiedSlots.value = [];
  } else {
    Object.assign(form, {
      productCode: '',
      productName: '',
      subjectName: '',
      teachingMode: 'GROUP',
      defaultTotalUnits: 40,
      defaultLessonUnits: 2,
      expiryDurationDays: undefined,
      expiryDate: undefined,
      defaultClassroomId: undefined,
      listPrice: 0,
      status: 'ACTIVE',
      notes: '',
      slots: [],
    });
    formSlots.value = [];
    occupiedSlots.value = [];
  }
  dialogVisible.value = true;
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  // GROUP 产品至少需要一个排课时段
  if (form.teachingMode === 'GROUP' && formSlots.value.length === 0) {
    ElMessage.warning('At least one schedule slot is required for group products.');
    return;
  }

  submitting.value = true;
  try {
    // 构建提交数据：不同类型传不同字段
    const payload: any = {
      productCode: form.productCode,
      productName: form.productName,
      subjectName: form.subjectName,
      teachingMode: form.teachingMode,
      listPrice: form.listPrice,
      status: form.status,
      notes: form.notes,
    };
    if (form.teachingMode === 'ONE_ON_ONE') {
      payload.defaultTotalUnits = form.defaultTotalUnits;
      payload.defaultLessonUnits = form.defaultLessonUnits;
    } else {
      payload.expiryDurationDays = editingId.value
        ? form.expiryDurationDays
        : calcExpiryDays(form.expiryDate);
      payload.defaultClassroomId = form.defaultClassroomId;
      payload.slots = formSlots.value.map(s => ({
        weekday: s.weekday,
        startTime: s.startTime,
        endTime: s.endTime,
      }));
    }

    if (editingId.value) {
      await updateCourseProduct(editingId.value, payload);
      ElMessage.success('Course product updated.');
    } else {
      await createCourseProduct(payload);
      ElMessage.success('Course product created.');
    }
    dialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

// ─── 删除 ──────────────────────────────────────────────
async function handleDelete(row: CourseProductItem) {
  try {
    await ElMessageBox.confirm(
      `Delete "${row.productName}"? This action cannot be undone.`,
      'Confirm delete',
      { type: 'warning' },
    );
    await deleteCourseProduct(row.id);
    ElMessage.success('Deleted.');
    loadData();
  } catch {
    // 用户取消
  }
}

// ─── 排课时段格式化（仅用于表格展示） ─────────────────────
function formatSlots(slots: { weekday: number; startTime: string; endTime: string }[]): string {
  const weekdays = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  return slots.map(s => `${weekdays[s.weekday]} ${s.startTime}-${s.endTime}`).join(', ');
}

// ─── 辅助函数 ──────────────────────────────────────────
function formatTeachingMode(mode: string): string {
  return mode === 'ONE_ON_ONE' ? '1:1' : mode === 'GROUP' ? 'Group' : mode;
}

function formatPrice(price: number): string {
  return `$${price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

// ─── 教室列表 ──────────────────────────────────────────
async function loadClassrooms() {
  try {
    const res = await fetchAllClassrooms();
    classrooms.value = (res.data.data || []).map(r => ({ id: r.id, roomName: r.roomName }));
  } catch {
    // ignore
  }
}

// ─── 初始化 ────────────────────────────────────────────
loadData();
loadSubjects();
loadClassrooms();

// ─── Date helpers ──────────────────────────────────────────
function disabledPastDate(date: Date) {
  return dayjs(date).startOf('day').isBefore(dayjs().startOf('day'));
}
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

.text-muted {
  color: #909399;
  font-size: 12px;
}

.field-hint {
  display: block;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 弹窗样式 */
.product-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.product-form__row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 14px;
}

.product-form__full {
  grid-column: 1 / -1;
}

.product-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.product-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.product-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

/* Subject dialog */
.subject-table {
  margin-bottom: 12px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.notes-text {
  color: #909399;
  font-size: 12px;
}

.subject-form {
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}

.subject-form__title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.subject-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.subject-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.subject-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.subject-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}
</style>
