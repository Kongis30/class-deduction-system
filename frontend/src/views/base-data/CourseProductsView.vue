<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <el-tabs v-model="activeTab" class="product-tabs product-tabs--top">
        <el-tab-pane label="Group Class" name="GROUP" />
        <el-tab-pane label="1:1 Private" name="ONE_ON_ONE" />
      </el-tabs>

      <div class="panel-header">
        <div v-if="!appStore.isMobileViewport">
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

      <section v-if="!appStore.isMobileViewport" class="setup-path-card">
        <div>
          <span>What to do next</span>
          <strong>{{ setupPathTitle }}</strong>
          <p>{{ setupPathDescription }}</p>
        </div>
      </section>

      <details v-if="appStore.isMobileViewport" class="mobile-setup-guide">
        <summary>Course setup guide</summary>
        <div>
          <strong>{{ setupPathTitle }}</strong>
          <p>{{ setupPathDescription }}</p>
        </div>
      </details>

      <div class="product-toolbar">
        <div v-if="appStore.isMobileViewport" class="mobile-search-toggle-row">
          <span class="mobile-product-count">{{ mobileProductCount }} {{ mobileProductCount === 1 ? 'course' : 'courses' }}</span>
          <button
            type="button"
            class="mobile-product-search-trigger"
            :aria-expanded="mobileSearchExpanded"
            aria-label="Search and filter courses"
            @click="mobileSearchExpanded = !mobileSearchExpanded"
          >
            <el-icon><Search /></el-icon>
            <span>Search &amp; filter</span>
          </button>
        </div>
        <div
          v-show="!appStore.isMobileViewport || mobileSearchExpanded"
          class="product-toolbar__filters"
          :class="{ 'mobile-search-panel': appStore.isMobileViewport, 'product-toolbar__filters--mobile': appStore.isMobileViewport }"
        >
          <div v-if="appStore.isMobileViewport" class="mobile-search-panel__header">
            <span>Search courses</span>
            <button type="button" aria-label="Close search" @click="mobileSearchExpanded = false">
              <el-icon><Close /></el-icon>
            </button>
          </div>
          <el-input v-model="filters.productName" placeholder="Search course" clearable @input="handleFilterChange" />
          <el-select v-model="filters.status" placeholder="All statuses" @change="handleFilterChange">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
            <el-option label="Archived" value="ARCHIVED" />
            <el-option label="All statuses" value="ALL" />
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
            v-if="!appStore.isMobileViewport"
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
                    <el-tag v-if="row.archivedAt" size="small" type="warning" effect="plain">Archived</el-tag>
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
                        <el-dropdown-item v-if="isAdmin && !row.archivedAt" command="archive" divided>Archive</el-dropdown-item>
                        <el-dropdown-item v-if="isAdmin && row.archivedAt" command="restore" divided>Restore from archive</el-dropdown-item>
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
            v-if="!appStore.isMobileViewport"
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
                    <el-tag v-if="row.archivedAt" size="small" type="warning" effect="plain">Archived</el-tag>
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
                  <span>{{ groupClassCount(row) > 0 ? 'Review prices and schedules before registration' : 'Add grade classes to begin setup' }}</span>
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
                        <el-dropdown-item v-if="isAdmin && !row.archivedAt" command="archive" divided>Archive</el-dropdown-item>
                        <el-dropdown-item v-if="isAdmin && row.archivedAt" command="restore" divided>Restore from archive</el-dropdown-item>
                        <el-dropdown-item command="delete" divided class="table-actions__delete">Delete</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <div v-if="appStore.isMobileViewport" v-loading="loading" class="mobile-product-list">
          <template v-if="activeTab === 'ONE_ON_ONE'">
            <article v-for="product in oneOnOneData" :key="product.id" class="mobile-product-row">
              <header class="mobile-product-row__heading">
                <strong>{{ product.productName }}</strong>
                <el-tag size="small" :type="product.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                  {{ product.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                </el-tag>
                <el-tag v-if="product.archivedAt" size="small" type="warning" effect="plain">Archived</el-tag>
                <el-dropdown trigger="click" @command="command => handleProductRowAction(String(command), product)">
                  <el-button text class="mobile-product-row__more">More <el-icon><ArrowDown /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="copy">Copy</el-dropdown-item>
                      <el-dropdown-item command="edit">Edit</el-dropdown-item>
                      <el-dropdown-item v-if="isAdmin && !product.archivedAt" command="archive" divided>Archive</el-dropdown-item>
                      <el-dropdown-item v-if="isAdmin && product.archivedAt" command="restore" divided>Restore from archive</el-dropdown-item>
                      <el-dropdown-item command="delete" divided class="table-actions__delete">Delete</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </header>
              <div class="mobile-product-row__footer">
                <span class="mobile-product-row__summary">1:1 private course</span>
                <el-button type="primary" plain @click="openProductPrices(product)">Set packages &amp; prices</el-button>
              </div>
            </article>
          </template>
          <template v-else>
            <article v-for="product in groupData" :key="product.id" class="mobile-product-row">
              <header class="mobile-product-row__heading">
                <strong>{{ product.productName }}</strong>
                <el-tag size="small" :type="product.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                  {{ product.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                </el-tag>
                <el-tag v-if="product.archivedAt" size="small" type="warning" effect="plain">Archived</el-tag>
                <el-dropdown trigger="click" @command="command => handleProductRowAction(String(command), product)">
                  <el-button text class="mobile-product-row__more">More <el-icon><ArrowDown /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="copy">Copy</el-dropdown-item>
                      <el-dropdown-item command="edit">Edit</el-dropdown-item>
                      <el-dropdown-item v-if="isAdmin && !product.archivedAt" command="archive" divided>Archive</el-dropdown-item>
                      <el-dropdown-item v-if="isAdmin && product.archivedAt" command="restore" divided>Restore from archive</el-dropdown-item>
                      <el-dropdown-item command="delete" divided class="table-actions__delete">Delete</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </header>
              <div class="mobile-product-row__footer">
                <div class="mobile-product-row__summary">
                  <template v-if="groupClassCount(product) > 0">
                    <span>{{ groupSetupLabel(product) }}</span>
                  </template>
                  <el-tag v-else size="small" type="warning" effect="plain">Needs grade classes</el-tag>
                </div>
                <el-button type="primary" plain @click="openProductPrices(product)">Set grades, prices &amp; schedule</el-button>
              </div>
            </article>
          </template>
          <el-empty v-if="!loading && (activeTab === 'ONE_ON_ONE' ? oneOnOneData.length === 0 : groupData.length === 0)" description="No courses found." />
        </div>
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
            <el-form-item v-if="!editingId && form.teachingMode === 'GROUP'" label="Course type" prop="courseType">
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
import { ArrowDown, Close, Plus, QuestionFilled, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, h, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createCourseProduct,
  copyCourseProduct,
  deleteCourseProduct,
  forceDeleteCourseProduct,
  fetchCourseProducts,
  fetchCourseProductArchivePreview,
  archiveCourseProductWithPreview,
  restoreCourseProduct,
  updateCourseProduct,
  type CourseProductQuery,
  type CourseProductSavePayload,
} from '@/api/base-data';
import { fetchClassGroups } from '@/api/schedule';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import { isAdminUser } from '@/types/auth';
import type { ClassGroupItem, CourseProductItem, EntityId } from '@/types/domain';

const authStore = useAuthStore();
const appStore = useAppStore();
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
const mobileSearchExpanded = ref(false);

const classGroups = ref<ClassGroupItem[]>([]);
const createdNextStep = ref<{
  productId: EntityId;
  productName: string;
  courseType?: string;
  description: string;
} | null>(null);

const filters = reactive({
  productName: '',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE' | 'ARCHIVED' | 'ALL',
});

const pagination = reactive({
  page: 1,
  size: appStore.isMobileViewport ? 200 : 10,
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

watch(() => appStore.isMobileViewport, (isMobile) => {
  const nextSize = isMobile ? 200 : 10;
  if (pagination.size === nextSize) return;
  pagination.page = 1;
  pagination.size = nextSize;
  loadData();
});

const oneOnOneData = computed(() =>
  sortCourseProducts(tableData.value.filter(p => p.teachingMode === 'ONE_ON_ONE'))
);

const groupData = computed(() =>
  sortCourseProducts(tableData.value.filter(p => p.teachingMode === 'GROUP'))
);

const mobileProductCount = computed(() =>
  activeTab.value === 'ONE_ON_ONE' ? oneOnOneData.value.length : groupData.value.length
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
      status: filters.status === 'ACTIVE' || filters.status === 'INACTIVE' ? filters.status : undefined,
      archiveScope: filters.status === 'ARCHIVED' ? 'ARCHIVED' : filters.status === 'ALL' ? 'ALL' : 'CURRENT',
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
  } else if (command === 'archive') {
    void changeProductArchive(row, true);
  } else if (command === 'restore') {
    void changeProductArchive(row, false);
  }
}

async function changeProductArchive(row: CourseProductItem, archive: boolean) {
  if (!isAdmin.value) return;
  let archivedSummary: Awaited<ReturnType<typeof fetchCourseProductArchivePreview>>['data']['data'] | null = null;
  try {
    if (!archive) {
      await ElMessageBox.confirm(
        `Restore "${row.productName}" to current course lists? It will remain inactive. Classes, enrollments, packages, schedules, and cancelled lessons will not be restored.`,
        'Restore course',
        { type: 'warning', confirmButtonText: 'Restore as inactive', cancelButtonText: 'Cancel' },
      );
    } else {
      let preview = (await fetchCourseProductArchivePreview(row.id)).data.data;
      while (true) {
        await ElMessageBox.confirm(archiveImpactMessage(preview), 'Archive course product', {
          type: 'warning',
          confirmButtonText: 'Archive and end courses',
          confirmButtonClass: 'el-button--danger',
          cancelButtonText: 'Cancel',
          distinguishCancelAndClose: true,
        });
        try {
          await archiveCourseProductWithPreview(row.id, preview.fingerprint);
          archivedSummary = preview;
          break;
        } catch (error: any) {
          const response = error?.response?.data;
          if (response?.code !== 'COURSE_PRODUCT_ARCHIVE_PREVIEW_CHANGED' || !response?.data) throw error;
          preview = response.data;
          ElMessage.warning('The impact changed while you were reviewing it. Please review and confirm the updated counts.');
        }
      }
    }
    if (!archive) await restoreCourseProduct(row.id);
    ElMessage.success(archive && archivedSummary
      ? `Archived. Ended ${archivedSummary.classCount} classes, cancelled ${archivedSummary.activeEnrollmentCount} enrollments, stopped ${archivedSummary.recurringRuleCount} rules, and cancelled ${archivedSummary.unfinishedLessonCount} lessons.`
      : 'Course restored as inactive.');
    await loadData();
  } catch (error: any) {
    // Element Plus confirmation rejects with these values when the user dismisses the dialog.
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(error?.response?.data?.message ?? `Course ${archive ? 'archive' : 'restore'} failed.`);
  }
}

function archiveImpactMessage(preview: Awaited<ReturnType<typeof fetchCourseProductArchivePreview>>['data']['data']) {
  const count = (value: number) => value.toLocaleString();
  return h('div', { class: 'archive-impact' }, [
    h('p', `Archiving “${preview.productName}” will stop new enrollment and terminate its active teaching activity.`),
    h('ul', [
      h('li', `${count(preview.classCount)} active classes; ${count(preview.activeEnrollmentCount)} active enrollments will be ended.`),
      h('li', `${count(preview.activePackageCount)} active or paused packages will be closed. Their current balances and ledgers will be retained.`),
      h('li', `${count(preview.recurringRuleCount)} recurring schedule rules will be stopped.`),
      h('li', `${count(preview.unfinishedLessonCount)} unfinished lessons and future occurrences will be cancelled.`),
      h('li', `Estimated total package deduction: ${Number(preview.estimatedDeductedHours).toFixed(1)} hours, including each active member of shared private lessons. Cancellation follows the existing administrator Cancel flow.`),
    ]),
    h('p', 'Completed lessons and historical orders, receipts, refunds, and financial records will remain. Restoring this product will not revive ended classes, enrollments, packages, schedules, or cancelled lessons.'),
  ]);
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
    filters.status = 'ACTIVE';
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
.mobile-product-list { display: grid; gap: 0; }
.mobile-product-row { min-width: 0; padding: 10px 2px; border-bottom: 1px solid #e8edf2; }
.mobile-product-row:first-child { border-top: 1px solid #e8edf2; }
.mobile-product-row__heading { display: flex; align-items: center; gap: 7px; min-width: 0; }
.mobile-product-row__heading > strong { flex: 1; min-width: 0; overflow-wrap: anywhere; color: #26384c; font-size: 14px; line-height: 1.35; }
.mobile-product-row__heading > .el-tag { flex: none; }
.mobile-product-row__more { flex: none; min-height: 32px; margin: 0; padding: 0 4px; color: #64748b; }
.mobile-product-row__footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 6px; }
.mobile-product-row__summary { display: flex; flex: 1; flex-wrap: wrap; align-items: center; gap: 4px 10px; min-width: 0; color: #64748b; font-size: 11px; line-height: 1.35; }
.mobile-product-row__footer > .el-button { flex: none; min-height: 36px; margin: 0; padding: 0 9px; font-size: 12px; }
.mobile-setup-guide { margin: 4px 0 8px; color: #64748b; font-size: 12px; }
.mobile-setup-guide summary { width: fit-content; color: #526783; cursor: pointer; }
.mobile-setup-guide > div { margin-top: 6px; padding: 9px 10px; border-radius: 8px; background: #f5f8fc; }
.mobile-setup-guide strong { color: #334155; font-size: 12px; }
.mobile-setup-guide p { margin: 3px 0 0; line-height: 1.4; }
.mobile-product-count { color: #718096; font-size: 12px; }
.mobile-product-search-trigger { display: inline-flex; min-height: 36px; align-items: center; gap: 6px; padding: 0 10px; border: 1px solid #d5deea; border-radius: 8px; background: #fff; color: #415574; font: inherit; font-size: 12px; cursor: pointer; }
.mobile-product-search-trigger .el-icon { font-size: 15px; }

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
  grid-template-columns: minmax(220px, 320px) minmax(140px, 160px);
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

:global(html.mobile-layout) & {
  .product-toolbar { align-items: stretch; flex-direction: column; }
  .product-toolbar__filters { display: grid; grid-template-columns: minmax(0, 1fr) minmax(132px, 160px); }
  .product-toolbar__filters > * { min-width: 0; width: 100% !important; }
  .product-toolbar__filters--mobile { grid-template-columns: minmax(0, 1fr) minmax(132px, 160px); }
  .mobile-search-toggle-row { display: flex; justify-content: flex-end; margin-bottom: 8px; }
}

@media (max-width: 640px) {
  .course-product-panel { padding: 12px; }
  .product-tabs--top { margin-bottom: 8px; }
  .panel-header { margin-bottom: 8px; }
  .panel-header__actions { width: 100%; }
  .panel-header__actions :deep(.el-button) { width: 100%; min-height: 42px; margin: 0; }
  .product-toolbar { margin-bottom: 6px; }
  .mobile-search-toggle-row { align-items: center; justify-content: space-between; margin-bottom: 6px; }
  .product-toolbar__filters { grid-template-columns: minmax(0, 1fr) minmax(120px, 145px); }
  .product-toolbar__filters :deep(.el-input__wrapper),
  .product-toolbar__filters :deep(.el-select__wrapper) { min-height: 42px; }
  .product-toolbar__filters :deep(.el-input__inner),
  .product-toolbar__filters :deep(.el-select__placeholder),
  .product-toolbar__filters :deep(.el-select__selected-item) { font-size: 13px; }
  .product-step__head { align-items: flex-start; }
  .product-form__row { grid-template-columns: 1fr; gap: 0; }
  .product-form__full { grid-column: auto; }
  .product-dialog :deep(.el-dialog__header), .product-dialog :deep(.el-dialog__footer) { padding-right: 14px; padding-left: 14px; }
}

</style>
