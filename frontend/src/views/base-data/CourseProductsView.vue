<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <el-tabs v-model="activeTab" class="product-tabs product-tabs--top">
        <el-tab-pane label="Group Class" name="GROUP" />
        <el-tab-pane label="1:1 Private" name="ONE_ON_ONE" />
      </el-tabs>

      <div class="panel-header">
        <div>
          <span class="panel-header__eyebrow">Course setup</span>
          <h2 class="panel-header__title">Courses</h2>
          <p class="panel-header__desc">Create the course product first. Set packages and prices before registration.</p>
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
          <span>What to do next</span>
          <strong>{{ setupPathTitle }}</strong>
          <p>{{ setupPathDescription }}</p>
        </div>
      </section>

      <div class="product-toolbar">
        <div class="product-toolbar__filters">
          <el-input
            v-model="filters.productName"
            placeholder="Search course"
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
                    <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                      {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                    </el-tag>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="250" align="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button type="primary" link @click="openProductPrices(row)">Set packages & prices</el-button>
                  <el-dropdown trigger="click" @command="command => handleProductRowAction(String(command), row)">
                    <el-button text class="table-actions__more">
                      More
                      <el-icon><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="copy">Copy</el-dropdown-item>
                        <el-dropdown-item command="edit">Edit</el-dropdown-item>
                        <el-dropdown-item command="delete" divided class="table-actions__delete">Delete</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
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
                  <span>{{ groupClassCount(row) > 0 ? 'Ready for registration' : 'Add grades, prices, and schedule first' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="250" align="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button type="primary" link @click="openProductPrices(row)">Set grades & schedule</el-button>
                  <el-dropdown trigger="click" @command="command => handleProductRowAction(String(command), row)">
                    <el-button text class="table-actions__more">
                      More
                      <el-icon><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="copy">Copy</el-dropdown-item>
                        <el-dropdown-item command="edit">Edit</el-dropdown-item>
                        <el-dropdown-item command="delete" divided class="table-actions__delete">Delete</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
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
            <small>
            Save the course product first. Package hours and prices are configured next; teachers and lesson names are assigned when you schedule lessons.
            </small>
          </div>
          <div class="product-form__row">
            <el-form-item label="Course name" prop="productName" class="product-form__full">
              <el-input v-model="form.productName" :placeholder="courseNamePlaceholder" />
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
          title="After saving, add grades, package sizes, and prices. Assign the teacher, lesson name, and weekly times after enrollment."
        />

        <el-alert
          v-if="form.teachingMode === 'GROUP'"
          type="info"
          :closable="false"
          show-icon
          title="After saving, add grades, registration options, prices, and weekly schedule."
        />

        <section class="product-step product-step--settings">
          <div class="product-form__row">
            <el-form-item v-if="form.teachingMode === 'GROUP'" label="Course type" prop="courseType">
              <el-select v-model="form.courseType" style="width: 100%;">
                <el-option label="After-school" value="AFTER_SCHOOL" />
                <el-option label="Saturday class" value="SATURDAY" />
                <el-option label="Summer class" value="SUMMER" />
                <el-option label="Sprint class" value="SPRINT" />
              </el-select>
            </el-form-item>
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

    <el-dialog v-model="copyDialogVisible" title="Copy course product" width="520px" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="New course name" required>
          <el-input v-model="copyProductName" maxlength="128" show-word-limit @keyup.enter="submitCopy" />
        </el-form-item>
        <el-form-item label="Shift course dates by years">
          <el-input-number v-model="copyDateShiftYears" :min="0" :max="10" />
          <span class="copy-date-hint">Weekly weekdays and times stay the same.</span>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="Prices, grade classes, and weekly time slots will be copied into independent records. The new course will stay inactive until you review and activate it. Student enrollments, receipts, and lesson records are not copied."
      />
      <template #footer>
        <el-button @click="copyDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="copySubmitting" :disabled="!copyProductName.trim()" @click="submitCopy">
          Copy and review setup
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ArrowDown, Plus, QuestionFilled } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createCourseProduct,
  copyCourseProduct,
  deleteCourseProduct,
  forceDeleteCourseProduct,
  fetchCourseProducts,
  updateCourseProduct,
  type CourseProductQuery,
  type CourseProductSavePayload,
} from '@/api/base-data';
import { fetchClassGroups } from '@/api/schedule';
import { useAuthStore } from '@/stores/auth';
import { isAdminUser } from '@/types/auth';
import type { ClassGroupItem, CourseProductItem, EntityId } from '@/types/domain';

const authStore = useAuthStore();
const isAdmin = computed(() => isAdminUser(authStore.currentUser));

const router = useRouter();
const route = useRoute();
const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const copyDialogVisible = ref(false);
const copySubmitting = ref(false);
const copyProductName = ref('');
const copyDateShiftYears = ref(1);
const copySource = ref<CourseProductItem | null>(null);
const editingId = ref<EntityId | null>(null);
const formRef = ref();

const classGroups = ref<ClassGroupItem[]>([]);
const createdNextStep = ref<{
  productId: EntityId;
  productName: string;
  courseType?: string;
  description: string;
} | null>(null);

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
type CourseProductTab = 'GROUP' | 'ONE_ON_ONE';
const activeTabStorageKey = 'jin.course-products.active-tab';

function parseCourseProductTab(value: unknown): CourseProductTab | null {
  const tab = Array.isArray(value) ? value[0] : value;
  if (tab === 'group' || tab === 'GROUP') return 'GROUP';
  if (tab === 'one-on-one' || tab === 'ONE_ON_ONE') return 'ONE_ON_ONE';
  return null;
}

const activeTab = ref<CourseProductTab>(
  parseCourseProductTab(route.query.tab)
    ?? parseCourseProductTab(localStorage.getItem(activeTabStorageKey))
    ?? 'GROUP'
);

watch(activeTab, (tab) => {
  localStorage.setItem(activeTabStorageKey, tab);
  const routeTab = tab === 'GROUP' ? 'group' : 'one-on-one';
  if (route.query.tab !== routeTab) {
    void router.replace({ query: { ...route.query, tab: routeTab } });
  }
});

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
    ? 'Create one group class course, then set each grade, price, and weekly schedule.'
    : 'Create one private course, then set each grade, hour package, and price.'
);

const setupPathDescription = computed(() =>
  activeTab.value === 'GROUP'
    ? 'Use the row action after saving the course. Students can register after a grade has a price and schedule.'
    : 'Use the row action after saving the course. Students can register after a grade has at least one hour package.'
);

const productDialogTitle = computed(() => {
  if (editingId.value) return 'Edit course';
  return form.teachingMode === 'ONE_ON_ONE' ? 'Create 1-on-1 course' : 'Create group class course';
});

const productDialogSubtitle = computed(() =>
  form.teachingMode === 'ONE_ON_ONE'
    ? 'Step 1: name the course. Step 2: add grades, hour packages, and prices.'
    : 'Step 1: name the group course. Step 2: add grades, registration options, prices, and schedule.'
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
  teachingMode: 'GROUP',
  sortOrder: 99,
  status: 'ACTIVE',
  notes: '',
});

const formRules = {
  productName: [{ required: true, message: 'Course name is required' }],
  teachingMode: [{ required: true, message: 'Teaching format is required' }],
};

function openProductDialog(row?: CourseProductItem) {
  editingId.value = row?.id ?? null;
  if (row) {
    Object.assign(form, {
      productCode: row.productCode,
      productName: row.productName,
      courseType: row.courseType ?? (row.teachingMode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'AFTER_SCHOOL'),
      registerClass: row.registerClass ?? row.productName,
      teachingMode: row.teachingMode,
      sortOrder: row.sortOrder ?? 99,
      status: row.status,
      notes: row.notes ?? '',
    });

  } else {
    const mode = activeTab.value === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'GROUP';
    Object.assign(form, {
      productCode: '',
      productName: '',
      courseType: mode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'AFTER_SCHOOL',
      registerClass: '',
      teachingMode: mode,
      sortOrder: 99,
      status: 'ACTIVE',
      notes: '',
    });
  }
  dialogVisible.value = true;
}

function openCopyDialog(row: CourseProductItem) {
  copySource.value = row;
  copyProductName.value = `${row.productName} Copy`;
  copyDateShiftYears.value = 1;
  copyDialogVisible.value = true;
}

function handleProductRowAction(command: string, row: CourseProductItem) {
  if (command === 'copy') {
    openCopyDialog(row);
  } else if (command === 'edit') {
    openProductDialog(row);
  } else if (command === 'delete') {
    void handleDelete(row);
  }
}

async function submitCopy() {
  const source = copySource.value;
  const productName = copyProductName.value.trim();
  if (!source || !productName || copySubmitting.value) return;
  copySubmitting.value = true;
  try {
    const response = await copyCourseProduct(source.id, productName, copyDateShiftYears.value);
    const productId = response.data.data;
    copyDialogVisible.value = false;
    filters.status = '';
    pagination.page = 1;
    ElMessage.success('Course copied. Review its prices and schedule before activating it.');
    await loadData();
    router.push({
      name: 'admin-config-course-prices',
      query: {
        from: 'course-products',
        create: '1',
        ...(source.courseType ? { courseType: source.courseType } : {}),
        courseProductId: productId,
      },
    });
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Course copy failed.');
  } finally {
    copySubmitting.value = false;
  }
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
      teachingMode: form.teachingMode,
      sortOrder: form.sortOrder ?? 99,
      status: form.status,
      notes: form.notes,
    };
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
          ? 'Next, add grades, hour packages, and prices. You can do it now or come back later.'
          : 'Next, add grades, registration options, prices, and schedule. You can do it now or come back later.',
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
    // 常规删除被引用保护挡下时，管理员可走强制删除清理历史脏数据。
    if (isAdmin.value) {
      await offerForceDelete(row, err?.response?.data?.message);
      return;
    }
    ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
  }
}

async function offerForceDelete(row: CourseProductItem, blockedMessage?: string) {
  let reason: string;
  try {
    const result = await ElMessageBox.prompt(
      `${blockedMessage ?? 'This product cannot be deleted normally.'}\n\n`
        + `Force delete also removes its classes and cancels future lessons. `
        + `Payment and lesson history are kept for audit. This cannot be undone. Enter a reason:`,
      'Force delete (admin)',
      {
        confirmButtonText: 'Force delete',
        cancelButtonText: 'Cancel',
        inputPlaceholder: 'Reason (e.g. legacy record before system update)',
        confirmButtonClass: 'el-button--danger',
      },
    );
    reason = result.value?.trim() ?? '';
  } catch {
    return;
  }
  try {
    await forceDeleteCourseProduct(row.id, reason || undefined);
    ElMessage.success('Deleted.');
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Force delete failed.');
  }
}


loadData();
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
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  white-space: nowrap;
}

.table-actions :deep(.el-button) {
  margin-left: 0;
}

.table-actions__more {
  color: #606266;
}

.table-actions__more:hover {
  color: var(--el-color-primary);
}

.table-actions__more :deep(.el-icon) {
  margin-left: 3px;
}

:global(.el-dropdown-menu__item.table-actions__delete) {
  color: var(--el-color-danger);
}

.copy-date-hint {
  margin-left: 10px;
  color: #7b8494;
  font-size: 12px;
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

</style>
