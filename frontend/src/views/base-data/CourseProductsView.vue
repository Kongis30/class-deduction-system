<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <el-tabs v-model="activeTab" class="product-tabs product-tabs--top">
        <el-tab-pane label="1:1 Private" name="ONE_ON_ONE" />
        <el-tab-pane label="Group Class" name="GROUP" />
      </el-tabs>

      <div class="panel-header">
        <div>
          <span class="panel-header__eyebrow">Course setup</span>
          <h2 class="panel-header__title">Course Products</h2>
          <p class="panel-header__desc">Create the parent course first. Grade, duration, time slot, and price are maintained in the price matrix.</p>
        </div>
        <div class="panel-header__actions">
          <el-button @click="openSubjectDialog()">Manage subjects</el-button>
          <el-button @click="$router.push('/admin/config/course-prices')">Open price matrix</el-button>
          <el-button type="primary" @click="openProductDialog()">
            <el-icon><Plus /></el-icon>
            {{ createCourseButtonLabel }}
          </el-button>
        </div>
      </div>

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

      <div class="product-tab-content">
        <template v-if="activeTab === 'ONE_ON_ONE'">
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
                  <strong class="product-title">
                    {{ row.productName }}
                    <el-tooltip
                      :content="billingTypeDescription(row.billingType)"
                      placement="top"
                    >
                      <span :class="['class-type-badge', classTypeClass(row.billingType)]">
                        <span class="class-type-badge__icon">{{ classTypeInitial(row.billingType) }}</span>
                        {{ classTypeLabel(row.billingType) }}
                      </span>
                    </el-tooltip>
                  </strong>
                  <span>{{ formatCourseType(row.courseType) }} · {{ row.registerClass || row.subjectName || 'No display label' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Total hours" min-width="100" align="center">
              <template #default="{ row }">{{ row.defaultTotalUnits }}</template>
            </el-table-column>
            <el-table-column label="Status" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" min-width="220" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button text type="primary" @click="openProductPrices(row)">Prices</el-button>
                  <el-button text type="primary" @click="openProductDialog(row)">Edit</el-button>
                  <el-button text type="danger" @click="handleDelete(row)">Delete</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <template v-else>
          <el-table
            v-loading="loading"
            :data="groupData"
            row-key="id"
            class="product-table"
            :show-empty="true"
          >
            <el-table-column label="Course product" min-width="260">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong>{{ row.productName }}</strong>
                  <span>{{ formatCourseType(row.courseType) }} · {{ row.registerClass || row.subjectName || 'No display label' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Product role" min-width="170">
              <template #default>
                <div class="cell-stack">
                  <strong>Parent course</strong>
                  <span>Grade classes and prices are configured next</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Setup" min-width="210">
              <template #default="{ row }">
                <div class="setup-cell">
                  <el-tag
                    size="small"
                    :type="groupClassCount(row) > 0 ? 'success' : 'warning'"
                    effect="plain"
                  >
                    {{ groupClassCount(row) > 0 ? `${groupClassCount(row)} grade class${groupClassCount(row) === 1 ? '' : 'es'}` : 'Needs grade class' }}
                  </el-tag>
                  <span>{{ groupClassCount(row) > 0 ? 'Ready for schedules and enrollment' : 'Add grade classes and prices first' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Active students" min-width="120" align="center">
              <template #default="{ row }">
                {{ groupStudentCount(row) }}
              </template>
            </el-table-column>
            <el-table-column label="Status" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" min-width="250" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button text type="primary" @click="openProductPrices(row)">Configure grade classes</el-button>
                  <el-tooltip
                    :content="hasGradeClasses(row) ? 'Open grade class schedules' : 'Create grade classes in Price Matrix first'"
                    placement="top"
                  >
                    <span>
                      <el-button
                        text
                        type="success"
                        :disabled="!hasGradeClasses(row)"
                        @click="openClassSchedules(row)"
                      >Class schedules</el-button>
                    </span>
                  </el-tooltip>
                  <el-button text type="primary" @click="openProductDialog(row)">Edit</el-button>
                  <el-button text type="danger" @click="handleDelete(row)">Delete</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </section>


    <el-dialog
      v-model="dialogVisible"
      width="720px"
      :close-on-click-modal="false"
      class="product-dialog"
    >
      <template #header>
        <div class="product-dialog__title">
          <h3>{{ productDialogTitle }}</h3>
          <span>{{ productDialogSubtitle }}</span>
        </div>
      </template>

      <el-form ref="formRef" label-position="top" :model="form" :rules="formRules" class="product-form">
        <section class="product-step">
          <div class="product-step__head">
            <strong>Course details</strong>
          </div>
          <div class="product-form__row">
            <el-form-item label="Product name" prop="productName" class="product-form__full">
              <el-input v-model="form.productName" placeholder="e.g. Saturday Class / 1-on-1 Private" />
            </el-form-item>
            <el-form-item label="Registration label" prop="registerClass">
              <el-input v-model="form.registerClass" placeholder="Shown on registration" />
            </el-form-item>
            <el-form-item label="Subject" prop="subjectName">
              <el-select v-model="form.subjectName" placeholder="Select subject" filterable style="width: 100%;">
                <el-option v-for="subj in subjects" :key="subj.subjectName" :label="subj.subjectName" :value="subj.subjectName" />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section class="product-step">
          <div class="product-step__head">
            <strong>Private lesson defaults</strong>
          </div>
          <div v-if="form.teachingMode === 'ONE_ON_ONE'" class="product-form__row">
            <el-form-item label="Total hours" prop="defaultTotalUnits">
              <el-input-number v-model="form.defaultTotalUnits" :min="1" :step="1" :precision="0" :controls="false" style="width: 100%;" />
            </el-form-item>
            <el-form-item label="Default lesson hours">
              <el-input-number v-model="defaultLessonHours" :min="0.5" :step="0.5" :precision="1" :controls="false" style="width: 100%;" />
            </el-form-item>
          </div>
        </section>

        <el-alert
          v-if="form.teachingMode === 'GROUP'"
          type="info"
          :closable="false"
          show-icon
          title="Set the grade, class run, teacher, classroom, and weekly schedule in Price Matrix after creating this product."
        />

        <section class="product-step product-step--settings">
          <div class="product-form__row">
            <el-form-item label="Status" prop="status">
              <el-select v-model="form.status" style="width: 100%;">
                <el-option label="Active" value="ACTIVE" />
                <el-option label="Inactive" value="INACTIVE" />
              </el-select>
            </el-form-item>
            <el-form-item label="Notes">
              <el-input v-model="form.notes" placeholder="Optional notes" />
            </el-form-item>
          </div>
        </section>

      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>


    <el-dialog
      v-model="subjectDialogVisible"
      width="720px"
      :close-on-click-modal="false"
      class="subject-dialog"
    >
      <template #header>
        <h3>Manage subjects</h3>
      </template>


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
import { useRouter } from 'vue-router';

import {
  createCourseProduct,
  createSubject,
  deleteCourseProduct,
  deleteSubject,
  fetchAllClassrooms,
  fetchAllTeachers,
  fetchCourseProducts,
  fetchSubjects,
  updateCourseProduct,
  updateSubject,
  type CourseProductQuery,
  type CourseProductSavePayload,
} from '@/api/base-data';
import { fetchClassGroups, fetchClassroomOccupancy } from '@/api/schedule';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { OccupiedSlotDef, SlotDef } from '@/components/WeeklyTimeSelector.vue';
import type { ClassGroupItem, CourseProductItem, EntityId, SubjectItem } from '@/types/domain';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const editingId = ref<EntityId | null>(null);
const formRef = ref();

const classrooms = ref<{ id: EntityId; roomName: string }[]>([]);
const teachers = ref<{ id: EntityId; teacherName: string }[]>([]);
const classGroups = ref<ClassGroupItem[]>([]);
const formSlots = ref<SlotDef[]>([]);
const occupiedSlots = ref<OccupiedSlotDef[]>([]);
const planningDefaultsOpen = ref<string[]>([]);


const subjectDialogVisible = ref(false);
const subjectTableData = ref<SubjectItem[]>([]);
const subjectFormRef = ref();
const subjectEditingId = ref<EntityId | null>(null);

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

    if (subjectEditingId.value === row.id) {
      subjectEditingId.value = null;
      Object.assign(subjectForm, { subjectName: '', displayOrder: 99, status: 'ACTIVE', notes: '' });
    }
  } catch {

  }
}


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

const courseTypeOptions = [
  { value: 'AFTER_SCHOOL', label: 'After School', hint: 'Group course. Add grade/month price rows later.', mode: 'GROUP', registerClass: 'After School' },
  { value: 'SATURDAY', label: 'Saturday Class', hint: 'Group course. Add season and half/full day rows later.', mode: 'GROUP', registerClass: 'Saturday Class' },
  { value: 'SUMMER', label: 'Summer Class', hint: 'Group course. Add 7-week and time-slot rows later.', mode: 'GROUP', registerClass: 'Summer Class' },
  { value: 'SPRINT', label: 'Sprint Class', hint: 'Group course. Matrix still needs product confirmation.', mode: 'GROUP', registerClass: 'Sprint Class' },
  { value: 'ONE_ON_ONE', label: '1-on-1', hint: 'Private course. Add 40/60/120 hour price rows later.', mode: 'ONE_ON_ONE', registerClass: '1 on 1' },
] as const;

const oneOnOneData = computed(() =>
  tableData.value.filter(p => p.teachingMode === 'ONE_ON_ONE')
);

const groupData = computed(() =>
  tableData.value.filter(p => p.teachingMode === 'GROUP')
);

const createCourseButtonLabel = computed(() =>
  activeTab.value === 'ONE_ON_ONE' ? 'Create 1-on-1 product' : 'Create group class product'
);

const productDialogTitle = computed(() => {
  if (editingId.value) return 'Edit course product';
  return form.teachingMode === 'ONE_ON_ONE' ? 'New 1-on-1 product' : 'New group class product';
});

const productDialogSubtitle = computed(() =>
  form.teachingMode === 'ONE_ON_ONE'
    ? 'Private tutoring products define the saleable package family. Prices are configured by grade and package hours.'
    : 'Group class products define course families. Grade classes, prices, rooms, teachers, and schedules are configured next.'
);

function openProductPrices(product: CourseProductItem) {
  router.push({
    name: 'admin-config-course-prices',
    query: {
      ...(product.courseType ? { courseType: product.courseType } : {}),
      courseProductId: product.id,
    },
  });
}

function openClassSchedules(product: CourseProductItem) {
  if (!hasGradeClasses(product)) return;
  router.push({
    name: 'class-schedule',
    query: { productId: product.id },
  });
}

function hasGradeClasses(product: CourseProductItem) {
  return classGroups.value.some(item => String(item.productId) === String(product.id));
}

function groupClassesFor(product: CourseProductItem) {
  return classGroups.value.filter(item => String(item.productId) === String(product.id));
}

function groupClassCount(product: CourseProductItem) {
  return groupClassesFor(product).length;
}

function groupStudentCount(product: CourseProductItem) {
  return groupClassesFor(product).reduce((sum, item) => sum + (item.activeStudentCount ?? 0), 0);
}

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}


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
    await loadClassGroups();
  } catch {
    ElMessage.error('Failed to load course products.');
  } finally {
    loading.value = false;
  }
}


const subjects = ref<SubjectItem[]>([]);

async function loadSubjects() {
  try {
    const res = await fetchSubjects();
    subjects.value = res.data.data;
  } catch {
    ElMessage.error('Failed to load subjects.');
  }
}

async function loadClassGroups() {
  try {
    const res = await fetchClassGroups();
    classGroups.value = res.data.data ?? [];
  } catch {
    classGroups.value = [];
  }
}


const form = reactive<CourseProductSavePayload>({
  productCode: '',
  productName: '',
  courseType: undefined,
  registerClass: 'Group Class',
  subjectName: '',
  teachingMode: 'GROUP',
  defaultTotalUnits: 40,
  defaultLessonUnits: 4,
  billingType: 'PER_SESSION',
  startDate: undefined,
  totalSessions: undefined,
  startMonth: undefined,
  endMonth: undefined,
  defaultClassroomId: undefined,
  defaultTeacherId: undefined,
  status: 'ACTIVE',
  notes: '',
  slots: [],
});

const defaultLessonHours = computed({
  get: () => (form.defaultLessonUnits ?? 4) / 2,
  set: (hours: number) => {
    form.defaultLessonUnits = Math.round(hours * 2);
  },
});

const formRules = {
  productName: [{ required: true, message: 'Product name is required' }],
  registerClass: [{ required: true, message: 'Registration label is required' }],
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
};

function openProductDialog(row?: CourseProductItem) {
  editingId.value = row?.id ?? null;
  if (row) {
    Object.assign(form, {
      productCode: row.productCode,
      productName: row.productName,
      courseType: row.courseType ?? (row.teachingMode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'AFTER_SCHOOL'),
      registerClass: row.registerClass ?? row.productName,
      subjectName: row.subjectName ?? '',
      teachingMode: row.teachingMode,
      defaultTotalUnits: row.defaultTotalUnits,
      defaultLessonUnits: row.defaultLessonUnits,
      billingType: row.billingType ?? 'PER_SESSION',
      startDate: row.startDate ?? undefined,
      totalSessions: row.totalSessions ?? undefined,
      startMonth: row.startMonth ?? undefined,
      endMonth: row.endMonth ?? undefined,
      defaultClassroomId: row.defaultClassroomId ?? undefined,
      defaultTeacherId: row.defaultTeacherId ?? undefined,
      status: row.status,
      notes: row.notes ?? '',
      slots: row.slots ? row.slots.map(s => ({ weekday: s.weekday, startTime: s.startTime, endTime: s.endTime })) : [],
    });

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

    occupiedSlots.value = [];
  } else {
    const mode = activeTab.value === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'GROUP';
    Object.assign(form, {
      productCode: '',
      productName: '',
      courseType: mode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : undefined,
      registerClass: mode === 'ONE_ON_ONE' ? '1-on-1' : 'Group Class',
      subjectName: '',
      teachingMode: mode,
      defaultTotalUnits: 40,
      defaultLessonUnits: 4,
      billingType: 'PER_SESSION',
      startDate: undefined,
      totalSessions: undefined,
      startMonth: undefined,
      endMonth: undefined,
      defaultClassroomId: undefined,
      defaultTeacherId: undefined,
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


  submitting.value = true;
  try {

    const payload: any = {
      productCode: form.productCode,
      productName: form.productName,
      courseType: form.courseType,
      registerClass: form.registerClass,
      subjectName: form.subjectName,
      teachingMode: form.teachingMode,
      status: form.status,
      notes: form.notes,
      // Older local servers still validate this legacy field. Pricing remains
      // exclusively controlled by the price matrix, so send its neutral value.
      listPrice: 0,
    };
    if (form.teachingMode === 'ONE_ON_ONE') {
      payload.defaultTotalUnits = form.defaultTotalUnits;
      payload.defaultLessonUnits = form.defaultLessonUnits;
    }

    if (editingId.value) {
      await updateCourseProduct(editingId.value, payload);
      ElMessage.success('Course product updated.');
      dialogVisible.value = false;
      await loadData();
    } else {
      const response = await createCourseProduct(payload);
      ElMessage.success('Course created. Add its registration prices next.');
      dialogVisible.value = false;
      await router.push({
        name: 'admin-config-course-prices',
        query: {
          ...(form.courseType ? { courseType: form.courseType } : {}),
          courseProductId: String(response.data.data),
          create: '1',
        },
      });
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}


async function handleDelete(row: CourseProductItem) {
  try {
    await ElMessageBox.confirm(
      `Delete "${row.productName}"? This action cannot be undone.`,
      'Confirm delete',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await deleteCourseProduct(row.id);
    ElMessage.success('Deleted.');
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
  }
}


function formatCourseType(type?: string): string {
  return type ? courseTypeOptions.find(item => item.value === type)?.label ?? 'Unclassified' : 'Any group type';
}

function classTypeInitial(billingType?: string): string {
  return billingType === 'MONTHLY' ? 'M' : 'S';
}

function classTypeLabel(billingType?: string): string {
  return billingType === 'MONTHLY' ? 'Monthly' : 'Per session';
}

function classTypeClass(billingType?: string): string {
  return billingType === 'MONTHLY' ? 'class-type-badge--monthly' : 'class-type-badge--session';
}

function billingTypeDescription(billingType?: string): string {
  if (billingType === 'MONTHLY') {
    return 'Monthly class: students enroll for a selected month range.';
  }
  return 'Per-session class: students buy a fixed number of class sessions.';
}


async function loadClassrooms() {
  try {
    const res = await fetchAllClassrooms();
    classrooms.value = (res.data.data || []).map(r => ({ id: r.id, roomName: r.roomName }));
  } catch {
    // ignore
  }
}

async function loadTeachers() {
  try {
    const res = await fetchAllTeachers();
    teachers.value = res.data.data.map(t => ({ id: t.id, teacherName: t.teacherName }));
  } catch {
    // ignore
  }
}


loadData();
loadSubjects();
loadClassrooms();
loadTeachers();
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
  margin: 2px 0 0;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.panel-header__eyebrow {
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.panel-header__desc {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.panel-header__actions {
  display: flex;
  gap: 8px;
}

.product-tabs--top {
  margin-bottom: 12px;
}

.product-tabs--top :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.product-tabs--top :deep(.el-tabs__content) {
  display: none;
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

.setup-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.setup-cell span {
  color: #64748b;
  font-size: 12px;
}

.product-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.class-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px 1px 3px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  line-height: 16px;
  cursor: help;
}

.class-type-badge__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  color: #fff !important;
  font-size: 10px !important;
  font-weight: 600;
}

.class-type-badge--session {
  color: #409eff;
  background: #ecf5ff;
}

.class-type-badge--session .class-type-badge__icon {
  background: #409eff;
}

.class-type-badge--monthly {
  color: #67c23a;
  background: #f0f9eb;
}

.class-type-badge--monthly .class-type-badge__icon {
  background: #67c23a;
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

.product-dialog {
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  margin: 5vh auto;
}

.product-dialog :deep(.el-dialog__body) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 18px;
}

.product-dialog__title h3,
.product-dialog__title span {
  display: block;
}

.product-dialog__title h3 {
  margin: 0;
}

.product-dialog__title span {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.product-step {
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
}

.product-step__head {
  margin-bottom: 8px;
}

.product-step__head strong {
  color: #102a43;
  font-size: 14px;
}

.planning-defaults {
  margin: 0 2px 10px;
}

.planning-defaults :deep(.el-collapse) {
  border-top: 1px solid #e8edf2;
  border-bottom: 1px solid #e8edf2;
}

.planning-defaults :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 42px;
  padding: 7px 0;
  line-height: 1.25;
}

.planning-defaults :deep(.el-collapse-item__header strong),
.planning-defaults :deep(.el-collapse-item__header span) {
  display: block;
}

.planning-defaults :deep(.el-collapse-item__header strong) {
  color: #334155;
  font-size: 13px;
}

.planning-defaults :deep(.el-collapse-item__header span) {
  margin-top: 2px;
  color: #64748b;
  font-size: 11px;
}

.planning-defaults :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}

.product-step--settings {
  margin-bottom: 0;
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
  flex: none;
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
  flex: none;
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
