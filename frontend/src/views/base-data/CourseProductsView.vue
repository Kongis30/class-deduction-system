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
          <h2 class="panel-header__title">Courses</h2>
          <p class="panel-header__desc">Create the course name first, then add grades and package prices.</p>
        </div>
        <div class="panel-header__actions">
          <el-button type="primary" @click="openProductDialog()">
            <el-icon><Plus /></el-icon>
            {{ createCourseButtonLabel }}
          </el-button>
        </div>
      </div>

      <section class="setup-path-card">
        <div>
          <span>{{ activeTab === 'GROUP' ? 'Group class setup path' : '1-on-1 setup path' }}</span>
          <strong>{{ setupPathTitle }}</strong>
          <p>{{ setupPathDescription }}</p>
        </div>
        <div class="setup-path-card__actions">
          <el-button type="primary" @click="openPriceMatrixForCurrentTab">
            Set package prices
          </el-button>
        </div>
      </section>

      <div class="product-toolbar">
        <div class="product-toolbar__filters">
          <el-input
            v-model="filters.productName"
            placeholder="Search course or subject"
            clearable
            @input="handleFilterChange"
          />
          <el-select
            v-model="filters.status"
            placeholder="All status"
            clearable
            @change="handleFilterChange"
          >
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </div>
        <div class="product-toolbar__links">
          <el-button text type="primary" @click="openSubjectDialog()">Manage subjects</el-button>
          <el-button text type="primary" @click="$router.push('/admin/config/course-prices')">Package prices</el-button>
        </div>
      </div>

      <section v-if="createdNextStep" class="next-step-card">
        <div>
          <span>Course created</span>
          <strong>{{ createdNextStep.productName }}</strong>
          <p>{{ createdNextStep.description }}</p>
        </div>
        <div class="next-step-card__actions">
          <el-button @click="createdNextStep = null">Later</el-button>
          <el-button type="primary" @click="openCreatedProductNextStep">Continue setup</el-button>
        </div>
      </section>

      <div class="product-tab-content">
        <template v-if="activeTab === 'ONE_ON_ONE'">
          <el-table
            v-loading="loading"
            :data="oneOnOneData"
            row-key="id"
            class="product-table"
            :show-empty="true"
          >
            <el-table-column label="Course name" min-width="220">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong class="product-title">
                    {{ row.productName }}
                  </strong>
                  <span>
                    {{ row.subjectName || 'No subject' }}
                    <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                      {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                    </el-tag>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Next step" min-width="150">
              <template #default>
                <span class="next-step-text">Add hour packages</span>
              </template>
            </el-table-column>
            <el-table-column label="Sort" width="80" align="center">
              <template #default="{ row }">{{ row.sortOrder ?? 99 }}</template>
            </el-table-column>
            <el-table-column label="Actions" width="190">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button type="primary" link @click="openProductPrices(row)">Set packages</el-button>
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
            <el-table-column label="Course name" min-width="260">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong>
                    {{ row.productName }}
                    <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                      {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                    </el-tag>
                  </strong>
                  <span>{{ row.subjectName || 'No subject' }}</span>
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
                    {{ groupSetupLabel(row) }}
                  </el-tag>
                  <span>{{ groupClassCount(row) > 0 ? 'Ready for registration' : 'Add package prices and schedule first' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Sort" width="80" align="center">
              <template #default="{ row }">{{ row.sortOrder ?? 99 }}</template>
            </el-table-column>
            <el-table-column label="Actions" width="210">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button type="primary" link @click="openProductPrices(row)">Set packages</el-button>
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
            <small v-if="form.teachingMode === 'ONE_ON_ONE'">
              Only create the 1-on-1 course here. Hour packages and prices are added after saving.
            </small>
          </div>
          <div class="product-form__row">
            <el-form-item label="Course name" prop="productName" class="product-form__full">
              <el-input v-model="form.productName" :placeholder="courseNamePlaceholder" />
            </el-form-item>
            <el-form-item label="Subject" prop="subjectName">
              <el-select v-model="form.subjectName" placeholder="Select subject" filterable style="width: 100%;">
                <el-option v-for="subj in subjects" :key="subj.subjectName" :label="subj.subjectName" :value="subj.subjectName" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <template #label>
                <span class="form-label-with-tip">
                  Sort
                  <el-tooltip
                    content="Controls course display order in lists. Smaller numbers appear first. Default is 99."
                    placement="top"
                    :show-after="300"
                  >
                    <el-icon class="form-label-with-tip__icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
              </template>
              <el-input-number v-model="form.sortOrder" :min="0" :max="9999" :controls="false" style="width: 100%;" />
            </el-form-item>
          </div>
        </section>

        <el-alert
          v-if="form.teachingMode === 'ONE_ON_ONE'"
          type="info"
          :closable="false"
          show-icon
          title="After saving, add packages such as 40 hours, 60 hours, and 120 hours with their prices."
        />

        <el-alert
          v-if="form.teachingMode === 'GROUP'"
          type="info"
          :closable="false"
          show-icon
          title="After creating the course, add grades, package prices, and weekly schedule."
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
          <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ productSaveButtonLabel }}</el-button>
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
import { Plus, QuestionFilled } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createCourseProduct,
  createSubject,
  deleteCourseProduct,
  deleteSubject,
  fetchCourseProducts,
  fetchSubjects,
  updateCourseProduct,
  updateSubject,
  type CourseProductQuery,
  type CourseProductSavePayload,
} from '@/api/base-data';
import { fetchClassGroups } from '@/api/schedule';
import type { ClassGroupItem, CourseProductItem, EntityId, SubjectItem } from '@/types/domain';

const router = useRouter();
const route = useRoute();
const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const editingId = ref<EntityId | null>(null);
const formRef = ref();

const classGroups = ref<ClassGroupItem[]>([]);
const createdNextStep = ref<{
  productId: EntityId;
  productName: string;
  courseType?: string;
  description: string;
} | null>(null);


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
const activeTab = ref(route.query.tab === 'group' ? 'GROUP' : 'ONE_ON_ONE');

const oneOnOneData = computed(() =>
  sortCourseProducts(tableData.value.filter(p => p.teachingMode === 'ONE_ON_ONE'))
);

const groupData = computed(() =>
  sortCourseProducts(tableData.value.filter(p => p.teachingMode === 'GROUP'))
);

const createCourseButtonLabel = computed(() =>
  activeTab.value === 'ONE_ON_ONE' ? 'Create 1-on-1 course' : 'Create group class course'
);

const courseNamePlaceholder = computed(() =>
  form.teachingMode === 'ONE_ON_ONE'
    ? 'e.g. 1-on-1 Math / Private Tutoring'
    : 'e.g. Saturday Class / Summer Class'
);

const setupPathTitle = computed(() =>
  activeTab.value === 'GROUP'
    ? 'Create the course first, then add package prices and schedule.'
    : 'Create the course first, then add hour packages.'
);

const setupPathDescription = computed(() =>
  activeTab.value === 'GROUP'
    ? 'A group course becomes available for registration after at least one grade has a package price and weekly schedule.'
    : 'A 1-on-1 course becomes available for registration after its grade and hour packages are added.'
);

const productDialogTitle = computed(() => {
  if (editingId.value) return 'Edit course';
  return form.teachingMode === 'ONE_ON_ONE' ? 'Create 1-on-1 course' : 'Create group class course';
});

const productDialogSubtitle = computed(() =>
  form.teachingMode === 'ONE_ON_ONE'
    ? 'Step 1: name the course. Step 2: add 40/60/120-hour packages and prices.'
    : 'Step 1 of 3: create the group course. After saving, add package prices and schedules.'
);

const productSaveButtonLabel = computed(() => {
  if (editingId.value) {
    return 'Save changes';
  }
  return 'Create course';
});

function openProductPrices(product: CourseProductItem) {
  router.push({
    name: 'admin-config-course-prices',
    query: {
      from: 'course-products',
      ...(product.courseType ? { courseType: product.courseType } : {}),
      courseProductId: product.id,
    },
  });
}

function openPriceMatrixForCurrentTab() {
  router.push({
    name: 'admin-config-course-prices',
    query: {
      from: 'course-products',
      courseType: activeTab.value === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'AFTER_SCHOOL',
    },
  });
}

function openCreatedProductNextStep() {
  if (!createdNextStep.value) {
    return;
  }
  router.push({
    name: 'admin-config-course-prices',
    query: {
      from: 'course-products',
      create: '1',
      ...(createdNextStep.value.courseType ? { courseType: createdNextStep.value.courseType } : {}),
      courseProductId: createdNextStep.value.productId,
    },
  });
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

function groupSetupLabel(product: CourseProductItem) {
  const classCount = groupClassCount(product);
  if (classCount === 0) {
    return 'Needs grade class';
  }
  const classLabel = `${classCount} grade class${classCount === 1 ? '' : 'es'}`;
  const studentCount = groupStudentCount(product);
  return `${classLabel} · ${studentCount} student${studentCount === 1 ? '' : 's'}`;
}

function sortCourseProducts(products: CourseProductItem[]) {
  return [...products].sort((left, right) =>
    (left.sortOrder ?? 99) - (right.sortOrder ?? 99)
    || left.productName.localeCompare(right.productName)
  );
}

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}


async function loadData() {
  loading.value = true;
  try {
    const params: CourseProductQuery = {
      keyword: filters.productName || undefined,
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
    ElMessage.error('Failed to load courses.');
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
  registerClass: '',
  subjectName: '',
  teachingMode: 'GROUP',
  sortOrder: 99,
  defaultTotalUnits: 40,
  defaultLessonUnits: 4,
  status: 'ACTIVE',
  notes: '',
});

const formRules = {
  productName: [{ required: true, message: 'Course name is required' }],
  teachingMode: [{ required: true, message: 'Teaching format is required' }],
  defaultTotalUnits: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (form.teachingMode === 'ONE_ON_ONE' && (value === undefined || value === null)) {
          callback(new Error('Default package size is required'));
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
      sortOrder: row.sortOrder ?? 99,
      defaultTotalUnits: row.defaultTotalUnits,
      defaultLessonUnits: row.defaultLessonUnits,
      status: row.status,
      notes: row.notes ?? '',
    });

  } else {
    const mode = activeTab.value === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'GROUP';
    Object.assign(form, {
      productCode: '',
      productName: '',
      courseType: mode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : undefined,
      registerClass: '',
      subjectName: '',
      teachingMode: mode,
      sortOrder: 99,
      defaultTotalUnits: 40,
      defaultLessonUnits: 4,
      status: 'ACTIVE',
      notes: '',
    });
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
      registerClass: editingId.value ? (form.registerClass || form.productName) : form.productName,
      subjectName: form.subjectName,
      teachingMode: form.teachingMode,
      sortOrder: form.sortOrder ?? 99,
      status: form.status,
      notes: form.notes,
    };
    if (form.teachingMode === 'ONE_ON_ONE') {
      payload.defaultTotalUnits = form.defaultTotalUnits || 40;
      payload.defaultLessonUnits = form.defaultLessonUnits || 4;
    }

    if (editingId.value) {
      await updateCourseProduct(editingId.value, payload);
      ElMessage.success('Course updated.');
      dialogVisible.value = false;
      await loadData();
    } else {
      const response = await createCourseProduct(payload);
      const productId = response.data.data;
      createdNextStep.value = {
        productId,
        productName: form.productName,
        courseType: form.courseType,
        description: form.teachingMode === 'ONE_ON_ONE'
          ? 'Next, add grade, package hours, and price. You can do it now or come back later.'
          : 'Next, add grade, package price, and schedule. You can do it now or come back later.',
      };
      ElMessage.success('Course created.');
      dialogVisible.value = false;
      await loadData();
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


loadData();
loadSubjects();
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

.setup-path-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  background: #eff6ff;
}

.setup-path-card span {
  display: block;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.setup-path-card strong {
  display: block;
  margin-top: 3px;
  color: #1f2937;
  font-size: 14px;
}

.setup-path-card p {
  margin: 3px 0 0;
  color: #606266;
  font-size: 12px;
  line-height: 1.45;
}

.setup-path-card__actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.product-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.product-toolbar__filters {
  display: grid;
  grid-template-columns: minmax(220px, 320px) 140px;
  gap: 8px;
}

.product-toolbar__links {
  display: flex;
  flex: none;
  gap: 6px;
}

.next-step-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid #c9ead5;
  border-radius: 10px;
  background: #effaf3;
}

.next-step-card span {
  display: block;
  color: #18a058;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.next-step-card strong {
  display: block;
  margin-top: 3px;
  color: #1f2937;
  font-size: 14px;
}

.next-step-card p {
  margin: 3px 0 0;
  color: #606266;
  font-size: 12px;
  line-height: 1.45;
}

.next-step-card__actions {
  display: flex;
  flex: none;
  gap: 8px;
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

.cell-stack span :deep(.el-tag) {
  margin-left: 6px;
  vertical-align: 1px;
}

.next-step-text {
  color: #64748b;
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

.status-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.status-cell span {
  max-width: 140px;
  color: #909399;
  font-size: 11px;
  line-height: 1.25;
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
  flex-wrap: wrap;
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

.form-label-with-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.form-label-with-tip__icon {
  color: #909399;
  cursor: help;
  font-size: 14px;
  line-height: 1;
}

.form-label-with-tip__icon:hover {
  color: #409eff;
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
