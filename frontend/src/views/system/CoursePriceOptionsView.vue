<template>
  <div class="page-stack">
    <section class="config-panel">
      <div class="config-panel__header">
        <div>
          <span class="config-panel__eyebrow">Course setup</span>
          <h2>Course Pricing Matrix</h2>
          <p>Set up each grade class first, then add its registration prices.</p>
        </div>
        <div class="header-actions">
          <el-button @click="loadAll">Reload</el-button>
          <el-button type="primary" :disabled="!query.courseProductId" @click="openDialog()">
            <el-icon><Plus /></el-icon>
            Add price row
          </el-button>
        </div>
      </div>

      <div class="matrix-layout">
        <aside class="navigation-sidebar">
          <template v-if="!selectedMode">
            <span class="navigation-sidebar__label">Teaching mode</span>
            <button
              v-for="mode in courseModes"
              :key="mode.value"
              class="type-tab"
              type="button"
              @click="selectCourseMode(mode.value)"
            >
              <strong>{{ mode.label }}</strong>
              <span>{{ mode.description }}</span>
            </button>
          </template>

          <template v-else>
            <button class="navigation-back" type="button" @click="backToModePicker">
              <el-icon><ArrowLeft /></el-icon>
              Teaching modes
            </button>
            <span class="navigation-sidebar__label">{{ selectedModeLabel }}</span>
            <button
              v-for="product in productChoices"
              :key="product.id"
              :class="['product-nav-item', { 'product-nav-item--active': String(query.courseProductId) === String(product.id) }]"
              type="button"
              @click="selectCourseProduct(product)"
            >
              <strong>{{ product.productName }}</strong>
              <span>{{ product.registerClass || product.subjectName || 'Course product' }}</span>
            </button>
            <el-empty
              v-if="!loadingProducts && productChoices.length === 0"
              :image-size="44"
              description="No products"
            />
          </template>
        </aside>

        <div class="matrix-content">
          <el-empty
            v-if="!query.courseProductId"
            description="Choose a course product to manage its grade and price rows."
          />

          <template v-else>
            <div class="matrix-content__context">
              <el-button text type="primary" @click="backToProductList">
                <el-icon><ArrowLeft /></el-icon>
                Course products
              </el-button>
              <strong>{{ selectedProduct?.productName }}</strong>
            </div>

            <el-form class="filters" :model="query" inline>
              <el-form-item label="Keyword">
                <el-input
                  v-model="query.keyword"
                  clearable
                  placeholder="Grade, duration, time"
                  style="width: 220px;"
                  @input="reloadFirstPage"
                />
              </el-form-item>
              <el-form-item label="Status">
                <el-select v-model="query.status" clearable placeholder="All" style="width: 140px;" @change="reloadFirstPage">
                  <el-option label="Active" value="ACTIVE" />
                  <el-option label="Inactive" value="INACTIVE" />
                </el-select>
              </el-form-item>
            </el-form>

            <div class="matrix-summary">
              <div class="summary-card">
                <span>Teaching mode</span>
                <strong>{{ selectedModeLabel }}</strong>
              </div>
              <div class="summary-card">
                <span>Product</span>
                <strong>{{ selectedProduct?.productName }}</strong>
              </div>
              <div class="summary-card">
                <span>Matrix rows</span>
                <strong>{{ visibleOptions.length }}</strong>
              </div>
            </div>

            <div class="template-strip">
              <span>Quick templates</span>
              <el-button
                v-for="preset in visiblePresets"
                :key="preset.label"
                size="small"
                @click="openTemplate(preset)"
              >
                {{ preset.label }}
              </el-button>
            </div>

            <el-empty v-if="!loading && groupedOptions.length === 0" description="No price rows for this course product." />

            <section
              v-for="group in groupedOptions"
              :key="group.productId"
              class="product-price-group"
            >
              <header class="product-price-group__header">
                <div>
                  <strong>{{ group.productName }}</strong>
                  <span>{{ group.registerClass }} · {{ group.rows.length }} price row{{ group.rows.length === 1 ? '' : 's' }}</span>
                </div>
                <el-button text type="primary" @click="openPriceForProduct(group.productId)">Add price row</el-button>
              </header>

              <el-table v-loading="loading || loadingProducts" :data="group.rows" row-key="id" class="config-table">
                <el-table-column prop="gradeBand" label="Grade / Level" min-width="120" />
                <el-table-column label="Grade class" min-width="200">
                  <template #default="{ row }">
                    <div class="cell-stack">
                      <strong>{{ formatGroupBilling(row) }}</strong>
                      <span>{{ formatGroupSchedule(row) }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="detailText" label="Registration detail" min-width="200" show-overflow-tooltip />
                <el-table-column label="Hours" width="90" align="right">
                  <template #default="{ row }">{{ row.oneOnOneHours ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="Price" width="110" align="right">
                  <template #default="{ row }">{{ formatMoney(row.priceAmount) }}</template>
                </el-table-column>
                <el-table-column label="Readiness" min-width="210">
                  <template #default="{ row }">
                    <div class="tag-stack">
                      <el-tag
                        v-for="issue in rowIssues(row)"
                        :key="issue.label"
                        size="small"
                        :type="issue.type"
                      >
                        {{ issue.label }}
                      </el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="sortOrder" label="Sort" width="80" align="center" />
                <el-table-column label="Status" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="Actions" width="220" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" @click="openDialog(row)">Edit</el-button>
                    <el-button v-if="row.status === 'ACTIVE'" text type="warning" @click="deactivateOption(row)">Deactivate</el-button>
                    <el-button text type="danger" @click="removeOption(row.id)">Delete</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <div class="matrix-note">
              <strong>Downstream impact:</strong>
              Product and price rows are copied into enrollment snapshots. Once used, prefer deactivating or adding a new price row instead of changing historical structure.
            </div>

            <div class="pagination-row">
              <el-pagination
                v-model:current-page="query.pageNum"
                v-model:page-size="query.pageSize"
                layout="total, sizes, prev, pager, next"
                :total="total"
                :page-sizes="[20, 50, 100, 200]"
                @current-change="loadOptions"
                @size-change="reloadFirstPage"
              />
            </div>
          </template>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      width="860px"
      :close-on-click-modal="false"
      :title="editingId ? 'Edit price matrix row' : 'Add price matrix row'"
    >
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <div class="pricing-context">
          <span>Course type</span>
          <strong>{{ selectedTypeLabelFor(form.courseType) }}</strong>
          <small>The price row will only be available for this course type.</small>
        </div>

        <el-form-item label="Course product" prop="courseProductId">
          <el-select
            v-model="form.courseProductId"
            filterable
            placeholder="Select the course to price"
            style="width: 100%;"
            @change="handleFormProductChange"
          >
            <el-option
              v-for="product in formProductChoices"
              :key="product.id"
              :label="productLabel(product)"
              :value="product.id"
            />
          </el-select>
        </el-form-item>

        <div class="form-grid form-grid--two">
          <el-form-item label="Grade / Level" prop="gradeBand">
            <el-input v-model="form.gradeBand" placeholder="e.g. G3-G6" @input="syncDetailText" />
          </el-form-item>
          <el-form-item :label="priceFieldLabel" prop="priceAmount">
            <el-input-number v-model="form.priceAmount" :min="0" :precision="2" :controls="false" style="width: 100%;" />
          </el-form-item>
        </div>

        <section v-if="formTeachingMode === 'GROUP'" class="grade-class-settings">
          <div class="grade-class-settings__head">
            <strong>Grade class arrangement</strong>
            <small>Students who choose this grade join this class. It owns the class run and schedule.</small>
          </div>
          <el-form-item label="Billing type">
            <el-radio-group v-model="form.groupBillingType" @change="syncDetailText">
              <el-radio-button value="PER_SESSION">Per session</el-radio-button>
              <el-radio-button value="MONTHLY">Monthly</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <div v-if="form.groupBillingType === 'PER_SESSION'" class="form-grid form-grid--two">
            <el-form-item label="First class date">
              <el-date-picker v-model="form.groupStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%;" @change="syncDetailText" />
            </el-form-item>
            <el-form-item label="Total sessions">
              <el-input-number v-model="form.groupTotalSessions" :min="1" :precision="0" :controls="false" style="width: 100%;" @change="syncDetailText" />
            </el-form-item>
          </div>
          <div v-else class="form-grid form-grid--two">
            <el-form-item label="First month">
              <el-date-picker v-model="form.groupStartMonth" type="month" value-format="YYYY-MM" style="width: 100%;" @change="syncDetailText" />
            </el-form-item>
            <el-form-item label="Last month">
              <el-date-picker v-model="form.groupEndMonth" type="month" value-format="YYYY-MM" style="width: 100%;" @change="syncDetailText" />
            </el-form-item>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Teacher">
              <el-select v-model="form.groupTeacherId" clearable placeholder="Assign later" style="width: 100%;">
                <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="Classroom">
              <el-select v-model="form.groupClassroomId" clearable placeholder="Assign later" style="width: 100%;">
                <el-option v-for="room in classrooms" :key="room.id" :label="room.roomName" :value="room.id" />
              </el-select>
            </el-form-item>
          </div>
          <WeeklyTimeSelector v-model="form.groupScheduleSlots" />
        </section>

        <el-form-item v-else label="1-on-1 package hours" prop="oneOnOneHours">
          <el-radio-group v-model="form.oneOnOneHoursMode" class="hours-presets" @change="selectOneOnOneHours">
            <el-radio-button v-for="hours in oneOnOneHourChoices" :key="hours" :value="String(hours)">
              {{ hours }}h
            </el-radio-button>
            <el-radio-button value="CUSTOM">Custom</el-radio-button>
          </el-radio-group>
          <div v-if="form.oneOnOneHoursMode === 'CUSTOM'" class="custom-hours">
            <el-input-number
              v-model="form.customOneOnOneHours"
              :min="0.5"
              :max="400"
              :step="0.5"
              :precision="1"
              :controls="true"
              @change="applyCustomOneOnOneHours"
            />
            <span>hours</span>
          </div>
          <small class="field-help">
            Choose a standard package when possible. Custom packages use 0.5-hour increments.
          </small>
        </el-form-item>

        <el-collapse class="advanced-settings">
          <el-collapse-item title="Advanced settings" name="advanced">
            <el-form-item label="Registration detail" prop="detailText">
              <el-input v-model="form.detailText" type="textarea" :rows="2" />
            </el-form-item>
            <div class="form-grid form-grid--three">
              <el-form-item label="Option code">
                <el-input v-model="form.optionCode" placeholder="Auto-generated when empty" />
              </el-form-item>
              <el-form-item label="Sort order">
                <el-input-number v-model="form.sortOrder" :min="0" :precision="0" :controls="false" style="width: 100%;" />
              </el-form-item>
              <el-form-item label="Status">
                <el-select v-model="form.status" style="width: 100%;">
                  <el-option label="Active" value="ACTIVE" />
                  <el-option label="Inactive" value="INACTIVE" />
                </el-select>
              </el-form-item>
            </div>
          </el-collapse-item>
        </el-collapse>

        <div class="dialog-hint">
          <strong>Usage note:</strong>
          {{ formTeachingMode === 'ONE_ON_ONE'
            ? 'This row will create a private package with hours converted to 0.5-hour units.'
            : 'This row controls billing. Students still need a concrete class group for schedule and deduction.' }}
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveOption">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createCoursePriceOption,
  deleteCoursePriceOption,
  fetchAllClassrooms,
  fetchAllTeachers,
  fetchCoursePriceOptions,
  fetchCourseProducts,
  updateCoursePriceOption,
  type CoursePriceOptionSavePayload,
} from '@/api/base-data';
import { fetchClassGroups } from '@/api/schedule';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { ClassGroupItem, ClassroomItem, CoursePriceOptionItem, CourseProductItem, EntityId, TeacherItem } from '@/types/domain';

type CourseMode = 'ONE_ON_ONE' | 'GROUP';
type CourseTypeValue = 'AFTER_SCHOOL' | 'SATURDAY' | 'SUMMER' | 'SPRINT' | 'ONE_ON_ONE';

interface CourseTypeDef {
  value: CourseTypeValue;
  label: string;
  mode: CourseMode;
}

interface CourseModeDef {
  value: CourseMode;
  label: string;
  description: string;
}

interface MatrixPreset {
  label: string;
  courseType: CourseTypeValue;
  gradeBand: string;
  periodLabel?: string;
  durationLabel: string;
  timeSlotLabel?: string;
  oneOnOneHours?: number;
}

interface ProductPriceGroup {
  productId: EntityId | undefined;
  productName: string;
  registerClass: string;
  rows: CoursePriceOptionItem[];
}

const courseTypes: CourseTypeDef[] = [
  { value: 'AFTER_SCHOOL', label: 'After School', mode: 'GROUP' },
  { value: 'SATURDAY', label: 'Saturday Class', mode: 'GROUP' },
  { value: 'SUMMER', label: 'Summer Class', mode: 'GROUP' },
  { value: 'SPRINT', label: 'Sprint Class', mode: 'GROUP' },
  { value: 'ONE_ON_ONE', label: '1 on 1', mode: 'ONE_ON_ONE' },
];

const courseModes: CourseModeDef[] = [
  { value: 'GROUP', label: 'Group Class', description: 'Classes and schedules' },
  { value: 'ONE_ON_ONE', label: '1-on-1 Private', description: 'Packages and prices' },
];

const route = useRoute();
const router = useRouter();
const presets: MatrixPreset[] = [
  { label: 'After School G1-G5 2 months', courseType: 'AFTER_SCHOOL', gradeBand: 'G1-G5', durationLabel: '2 months', timeSlotLabel: 'Mon-Fri 3-6pm' },
  { label: 'After School G6-G8 6 months', courseType: 'AFTER_SCHOOL', gradeBand: 'G6-G8', durationLabel: '6 months', timeSlotLabel: 'Mon-Fri 3-6pm' },
  { label: 'Saturday G1-2 Fall half day', courseType: 'SATURDAY', gradeBand: 'G1-2', periodLabel: 'Fall', durationLabel: '13 weeks half day', timeSlotLabel: 'Sat 9am-12pm' },
  { label: 'Saturday SAT full day', courseType: 'SATURDAY', gradeBand: 'SAT', periodLabel: 'Full year', durationLabel: '33 weeks full day', timeSlotLabel: 'Sat 9am-4pm' },
  { label: 'Summer G1-G6 half day', courseType: 'SUMMER', gradeBand: 'G1-G6', periodLabel: 'Summer', durationLabel: '7 weeks half day', timeSlotLabel: '9am-12pm' },
  { label: '1 on 1 G1-G5 40 hours', courseType: 'ONE_ON_ONE', gradeBand: 'G1-G5', durationLabel: '40 hours', oneOnOneHours: 40 },
  { label: '1 on 1 G6-G8 60 hours', courseType: 'ONE_ON_ONE', gradeBand: 'G6-G8', durationLabel: '60 hours', oneOnOneHours: 60 },
  { label: '1 on 1 AP 120 hours', courseType: 'ONE_ON_ONE', gradeBand: 'AP', durationLabel: '120 hours', oneOnOneHours: 120 },
];
const oneOnOneHourChoices = [20, 40, 60, 80, 100, 120];

const loading = ref(false);
const loadingProducts = ref(false);
const saving = ref(false);
const options = ref<CoursePriceOptionItem[]>([]);
const courseProducts = ref<CourseProductItem[]>([]);
const classGroups = ref<ClassGroupItem[]>([]);
const teachers = ref<TeacherItem[]>([]);
const classrooms = ref<ClassroomItem[]>([]);
const total = ref(0);
const dialogVisible = ref(false);
const editingId = ref<EntityId | null>(null);
const formRef = ref();

const query = reactive({
  keyword: '',
  courseType: 'AFTER_SCHOOL' as CourseTypeValue,
  courseProductId: '' as EntityId | '',
  status: 'ACTIVE',
  pageNum: 1,
  pageSize: 100,
});

const form = reactive({
  optionCode: '',
  courseType: 'AFTER_SCHOOL' as CourseTypeValue,
  registerClass: 'After School',
  gradeBand: '',
  periodLabel: '',
  durationLabel: '',
  timeSlotLabel: '',
  detailText: '',
  oneOnOneHours: undefined as number | undefined,
  oneOnOneHoursMode: '40' as string,
  customOneOnOneHours: undefined as number | undefined,
  priceAmount: 0,
  courseProductId: undefined as EntityId | undefined,
  groupTeacherId: undefined as EntityId | undefined,
  groupClassroomId: undefined as EntityId | undefined,
  groupScheduleSlots: [] as Array<{ weekday: number; startTime: string; endTime: string; plannedUnits?: number }>,
  groupBillingType: 'PER_SESSION' as 'PER_SESSION' | 'MONTHLY',
  groupStartDate: '',
  groupTotalSessions: undefined as number | undefined,
  groupStartMonth: '',
  groupEndMonth: '',
  sortOrder: 0,
  status: 'ACTIVE',
});

const rules = {
  courseType: [{ required: true, message: 'Course type is required' }],
  courseProductId: [{ required: true, message: 'Course product is required' }],
  registerClass: [{ required: true, message: 'Register Class is required' }],
  gradeBand: [{ required: true, message: 'Grade or level is required' }],
  oneOnOneHours: [{
    validator: (_rule: unknown, value: number | undefined, callback: (error?: Error) => void) => {
      callback(formTeachingMode.value === 'ONE_ON_ONE' && (!value || value <= 0)
        ? new Error('Purchased hours must be greater than zero')
        : undefined);
    },
    trigger: 'change',
  }],
  detailText: [{ required: true, message: 'Detail is required' }],
  priceAmount: [{
    validator: (_rule: unknown, value: number, callback: (error?: Error) => void) => {
      callback(value > 0 ? undefined : new Error('Price must be greater than zero'));
    },
    trigger: 'change',
  }],
};

const selectedMode = ref<CourseMode | null>(null);
const selectedModeLabel = computed(() =>
  courseModes.find(item => item.value === selectedMode.value)?.label ?? ''
);
const productMap = computed(() => new Map(courseProducts.value.map(item => [String(item.id), item])));
const selectedProduct = computed(() => query.courseProductId ? productMap.value.get(String(query.courseProductId)) : undefined);
const productChoices = computed(() =>
  selectedMode.value
    ? courseProducts.value.filter(product => product.teachingMode === selectedMode.value)
    : []
);
const visiblePresets = computed(() =>
  presets.filter(item => selectedMode.value && modeForCourseType(item.courseType) === selectedMode.value)
);

const visibleOptions = computed(() => {
  return options.value.filter((option) => {
    if (!selectedMode.value || modeForCourseType(option.courseType) !== selectedMode.value) {
      return false;
    }
    if (!query.courseProductId) {
      return false;
    }
    if (query.courseProductId && String(option.courseProductId) !== String(query.courseProductId)) {
      return false;
    }
    const keyword = query.keyword.trim().toLowerCase();
    if (!keyword) {
      return true;
    }
    return [
      option.registerClass,
      option.gradeBand,
      option.periodLabel,
      option.durationLabel,
      option.timeSlotLabel,
      option.detailText,
      productName(option.courseProductId),
    ].some(value => String(value ?? '').toLowerCase().includes(keyword));
  });
});

const groupedOptions = computed<ProductPriceGroup[]>(() => {
  const groups = new Map<string, ProductPriceGroup>();
  visibleOptions.value.forEach((option) => {
    const key = String(option.courseProductId ?? 'unlinked');
    const existing = groups.get(key);
    if (existing) {
      existing.rows.push(option);
      return;
    }
    groups.set(key, {
      productId: option.courseProductId,
      productName: productName(option.courseProductId),
      registerClass: option.registerClass || 'Course',
      rows: [option],
    });
  });

  return Array.from(groups.values())
    .map((group) => ({
      ...group,
      rows: [...group.rows].sort((left, right) =>
        left.sortOrder - right.sortOrder
        || String(left.gradeBand ?? '').localeCompare(String(right.gradeBand ?? ''))
        || String(left.durationLabel ?? '').localeCompare(String(right.durationLabel ?? ''))
      ),
    }))
    .sort((left, right) => left.productName.localeCompare(right.productName));
});

const formTeachingMode = computed<CourseMode>(() => courseTypes.find(item => item.value === form.courseType)?.mode ?? 'GROUP');
const priceFieldLabel = computed(() => {
  if (formTeachingMode.value !== 'GROUP') return 'Price';
  return form.groupBillingType === 'MONTHLY' ? 'Price per month' : 'Price per session';
});
const formProductChoices = computed(() =>
  courseProducts.value.filter(product => productMatchesCourseType(product, form.courseType))
);

onMounted(initializePage);

async function initializePage() {
  const requestedType = queryValue(route.query.courseType);
  if (courseTypes.some(item => item.value === requestedType)) {
    query.courseType = requestedType as CourseTypeValue;
    selectedMode.value = modeForCourseType(query.courseType);
  }
  await loadProductsAndClasses();

  const requestedProductId = queryValue(route.query.courseProductId);
  const requestedProduct = requestedProductId ? productMap.value.get(requestedProductId) : undefined;
  if (requestedProduct && productMatchesCourseType(requestedProduct, query.courseType)) {
    query.courseProductId = requestedProduct.id;
  }
  await loadOptions();

  if (queryValue(route.query.create) === '1') {
    openDialog();
  }
}

async function loadAll() {
  await Promise.all([loadProductsAndClasses(), loadOptions()]);
}

async function loadProductsAndClasses() {
  loadingProducts.value = true;
  try {
    const [productsRes, classRes, teachersRes, classroomsRes] = await Promise.all([
      fetchCourseProducts({ status: 'ACTIVE', pageNum: 1, pageSize: 200 }),
      fetchClassGroups().catch(() => ({ data: { data: [] as ClassGroupItem[] } })),
      fetchAllTeachers(),
      fetchAllClassrooms(),
    ]);
    courseProducts.value = productsRes.data.data.records ?? [];
    classGroups.value = classRes.data.data ?? [];
    teachers.value = teachersRes.data.data ?? [];
    classrooms.value = classroomsRes.data.data ?? [];
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to load course products.');
  } finally {
    loadingProducts.value = false;
  }
}

async function loadOptions() {
  loading.value = true;
  try {
    const res = await fetchCoursePriceOptions({
      courseProductId: query.courseProductId || undefined,
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    });
    options.value = res.data.data.records ?? [];
    total.value = query.courseProductId || query.keyword ? visibleOptions.value.length : (res.data.data.total ?? 0);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to load course price options.');
  } finally {
    loading.value = false;
  }
}

function reloadFirstPage() {
  query.pageNum = 1;
  loadOptions();
}

function selectCourseMode(mode: CourseMode) {
  selectedMode.value = mode;
  query.courseType = mode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'AFTER_SCHOOL';
  query.courseProductId = '';
  reloadFirstPage();
}

function backToModePicker() {
  selectedMode.value = null;
  query.courseProductId = '';
}

function backToProductList() {
  query.courseProductId = '';
}

function selectCourseProduct(product: CourseProductItem) {
  query.courseProductId = product.id;
  query.courseType = (product.courseType as CourseTypeValue) ?? query.courseType;
  reloadFirstPage();
}

function openDialog(option?: CoursePriceOptionItem) {
  editingId.value = option?.id ?? null;
  form.optionCode = option?.optionCode ?? '';
  form.courseType = (option?.courseType as CourseTypeValue) ?? query.courseType;
  form.registerClass = option?.registerClass ?? selectedTypeLabelFor(form.courseType);
  form.gradeBand = option?.gradeBand ?? '';
  form.periodLabel = option?.periodLabel ?? '';
  form.durationLabel = option?.durationLabel ?? '';
  form.timeSlotLabel = option?.timeSlotLabel ?? '';
  form.detailText = option?.detailText ?? '';
  form.oneOnOneHours = option?.oneOnOneHours;
  form.customOneOnOneHours = undefined;
  form.oneOnOneHoursMode = oneOnOneHourChoices.includes(Number(option?.oneOnOneHours))
    ? String(option?.oneOnOneHours)
    : 'CUSTOM';
  if (form.oneOnOneHoursMode === 'CUSTOM') {
    form.customOneOnOneHours = option?.oneOnOneHours;
  }
  if (!option) {
    if (formTeachingMode.value === 'ONE_ON_ONE') {
      form.oneOnOneHours = 40;
      form.oneOnOneHoursMode = '40';
      syncOneOnOneDuration();
    }
  }
  form.priceAmount = option?.priceAmount ?? 0;
  form.courseProductId = option?.courseProductId ?? (query.courseProductId || undefined);
  form.groupTeacherId = option?.groupTeacherId;
  form.groupClassroomId = option?.groupClassroomId;
  form.groupScheduleSlots = (option?.groupScheduleSlots ?? []).map(slot => ({
    ...slot,
    plannedUnits: Math.round((timeToMinutes(slot.endTime) - timeToMinutes(slot.startTime)) / 30),
  }));
  form.groupBillingType = option?.groupBillingType === 'MONTHLY' ? 'MONTHLY' : 'PER_SESSION';
  form.groupStartDate = option?.groupStartDate ?? '';
  form.groupTotalSessions = option?.groupTotalSessions;
  form.groupStartMonth = option?.groupStartMonth ?? '';
  form.groupEndMonth = option?.groupEndMonth ?? '';
  form.sortOrder = option?.sortOrder ?? 0;
  form.status = option?.status ?? 'ACTIVE';
  dialogVisible.value = true;
  if (!form.detailText) {
    syncDetailText();
  }
}

function openPriceForProduct(productId?: EntityId) {
  if (!productId) {
    ElMessage.warning('Link this price row to a course product before adding more prices.');
    return;
  }
  query.courseProductId = productId;
  openDialog();
}

function openTemplate(preset: MatrixPreset) {
  openDialog();
  form.courseType = preset.courseType;
  form.registerClass = selectedTypeLabelFor(preset.courseType);
  form.gradeBand = preset.gradeBand;
  form.periodLabel = preset.periodLabel ?? '';
  form.durationLabel = preset.durationLabel;
  form.timeSlotLabel = preset.timeSlotLabel ?? '';
  form.oneOnOneHours = preset.oneOnOneHours;
  form.customOneOnOneHours = undefined;
  form.oneOnOneHoursMode = preset.oneOnOneHours && oneOnOneHourChoices.includes(preset.oneOnOneHours)
    ? String(preset.oneOnOneHours)
    : 'CUSTOM';
  const currentProduct = query.courseProductId ? productMap.value.get(String(query.courseProductId)) : undefined;
  form.courseProductId = currentProduct && productMatchesCourseType(currentProduct, preset.courseType)
    ? currentProduct.id
    : undefined;
  syncDetailText();
}

function handleFormProductChange() {
  const product = form.courseProductId ? productMap.value.get(String(form.courseProductId)) : undefined;
  if (product) {
    form.courseType = (product.courseType as CourseTypeValue) || form.courseType;
    form.registerClass = product.registerClass || product.productName;
    syncDetailText();
  }
}

function selectOneOnOneHours(value: string) {
  if (value === 'CUSTOM') {
    form.oneOnOneHours = undefined;
    form.customOneOnOneHours = undefined;
    form.durationLabel = '';
    syncDetailText();
    return;
  }
  form.oneOnOneHours = Number(value);
  form.customOneOnOneHours = undefined;
  syncOneOnOneDuration();
}

function applyCustomOneOnOneHours() {
  const hours = form.customOneOnOneHours;
  if (hours == null || hours <= 0) {
    form.oneOnOneHours = undefined;
    form.durationLabel = '';
  } else {
    form.oneOnOneHours = hours;
    syncOneOnOneDuration();
  }
}

function syncOneOnOneDuration() {
  if (form.oneOnOneHours == null || form.oneOnOneHours <= 0) {
    form.durationLabel = '';
  } else {
    form.durationLabel = `${formatHours(form.oneOnOneHours)} hours`;
  }
  syncDetailText();
}

function syncDetailText() {
  const groupBillingDetail = form.groupBillingType === 'PER_SESSION'
    ? 'Per session'
    : 'Monthly';
  const detailParts = formTeachingMode.value === 'GROUP'
    ? [form.registerClass, form.gradeBand, groupBillingDetail]
    : [form.registerClass, form.gradeBand, form.durationLabel];
  const parts = detailParts
    .map(value => String(value ?? '').trim())
    .filter(Boolean);
  form.detailText = parts.join(' · ');
}

function buildPayload(status = form.status): CoursePriceOptionSavePayload {
  return {
    optionCode: form.optionCode.trim() || undefined,
    courseType: form.courseType,
    registerClass: form.registerClass.trim(),
    gradeBand: form.gradeBand.trim() || undefined,
    periodLabel: formTeachingMode.value === 'ONE_ON_ONE' ? form.periodLabel.trim() || undefined : undefined,
    durationLabel: formTeachingMode.value === 'ONE_ON_ONE' ? form.durationLabel.trim() || undefined : undefined,
    timeSlotLabel: undefined,
    detailText: form.detailText.trim(),
    oneOnOneHours: formTeachingMode.value === 'ONE_ON_ONE' ? form.oneOnOneHours : undefined,
    priceAmount: form.priceAmount,
    currencyCode: 'USD',
    courseProductId: form.courseProductId,
    groupTeacherId: formTeachingMode.value === 'GROUP' ? form.groupTeacherId : undefined,
    groupClassroomId: formTeachingMode.value === 'GROUP' ? form.groupClassroomId : undefined,
    groupScheduleSlots: formTeachingMode.value === 'GROUP'
      ? form.groupScheduleSlots.map(({ weekday, startTime, endTime }) => ({ weekday, startTime, endTime }))
      : undefined,
    groupBillingType: formTeachingMode.value === 'GROUP' ? form.groupBillingType : undefined,
    groupStartDate: formTeachingMode.value === 'GROUP' && form.groupBillingType === 'PER_SESSION'
      ? form.groupStartDate || undefined : undefined,
    groupTotalSessions: formTeachingMode.value === 'GROUP' && form.groupBillingType === 'PER_SESSION'
      ? form.groupTotalSessions : undefined,
    groupStartMonth: formTeachingMode.value === 'GROUP' && form.groupBillingType === 'MONTHLY'
      ? form.groupStartMonth || undefined : undefined,
    groupEndMonth: formTeachingMode.value === 'GROUP' && form.groupBillingType === 'MONTHLY'
      ? form.groupEndMonth || undefined : undefined,
    sortOrder: form.sortOrder,
    status,
  };
}

function payloadFromRow(row: CoursePriceOptionItem, status: string): CoursePriceOptionSavePayload {
  return {
    optionCode: row.optionCode,
    courseType: row.courseType,
    registerClass: row.registerClass,
    gradeBand: row.gradeBand,
    periodLabel: row.periodLabel,
    durationLabel: row.durationLabel,
    timeSlotLabel: row.timeSlotLabel,
    detailText: row.detailText,
    oneOnOneHours: row.oneOnOneHours,
    priceAmount: row.priceAmount,
    currencyCode: row.currencyCode || 'USD',
    courseProductId: row.courseProductId,
    classId: row.classId,
    groupTeacherId: row.groupTeacherId,
    groupClassroomId: row.groupClassroomId,
    groupScheduleSlots: row.groupScheduleSlots,
    groupBillingType: row.groupBillingType,
    groupStartDate: row.groupStartDate,
    groupTotalSessions: row.groupTotalSessions,
    groupStartMonth: row.groupStartMonth,
    groupEndMonth: row.groupEndMonth,
    sortOrder: row.sortOrder,
    status,
    notes: row.notes,
  };
}

async function saveOption() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (formTeachingMode.value === 'GROUP' && form.groupScheduleSlots.length === 0) {
    ElMessage.warning('Add at least one weekly schedule slot for this grade class.');
    return;
  }
  if (formTeachingMode.value === 'GROUP' && form.groupBillingType === 'PER_SESSION'
      && (!form.groupStartDate || !form.groupTotalSessions)) {
    ElMessage.warning('Add the first class date and total sessions for this grade class.');
    return;
  }
  if (formTeachingMode.value === 'GROUP' && form.groupBillingType === 'MONTHLY'
      && (!form.groupStartMonth || !form.groupEndMonth)) {
    ElMessage.warning('Add the first and last month for this grade class.');
    return;
  }
  if (formTeachingMode.value === 'ONE_ON_ONE' && (!form.oneOnOneHours || form.oneOnOneHours <= 0)) {
    ElMessage.error('1 on 1 hours must be greater than zero.');
    return;
  }
  saving.value = true;
  try {
    if (editingId.value) {
      await updateCoursePriceOption(editingId.value, buildPayload());
    } else {
      await createCoursePriceOption(buildPayload());
    }
    ElMessage.success(editingId.value ? 'Course price row updated.' : 'Course price row created.');
    dialogVisible.value = false;
    await loadOptions();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    saving.value = false;
  }
}

async function deactivateOption(row: CoursePriceOptionItem) {
  try {
    await ElMessageBox.confirm('Deactivate this course price row?', 'Confirm', { type: 'warning' });
    await updateCoursePriceOption(row.id, payloadFromRow(row, 'INACTIVE'));
    ElMessage.success('Course price row deactivated.');
    await loadOptions();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
    }
  }
}

async function removeOption(id: EntityId) {
  try {
    await ElMessageBox.confirm('Delete this course price row?', 'Confirm', { type: 'warning' });
    await deleteCoursePriceOption(id);
    ElMessage.success('Deleted.');
    await loadOptions();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
    }
  }
}

function rowIssues(row: CoursePriceOptionItem) {
  const issues: Array<{ label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = [];
  const product = row.courseProductId ? productMap.value.get(String(row.courseProductId)) : undefined;
  if (row.referenced) {
    const sourceText = row.referenceSources?.length ? ` · ${row.referenceSources.join(', ')}` : '';
    issues.push({ label: `Used ${row.referenceCount ?? 0}${sourceText}`, type: 'warning' });
  }
  if (!product) {
    issues.push({ label: 'Missing product', type: 'danger' });
  } else if (product.teachingMode !== modeForCourseType(row.courseType)) {
    issues.push({ label: 'Mode mismatch', type: 'danger' });
  } else if (product.referenced) {
    issues.push({ label: `Product used ${product.referenceCount ?? 0}`, type: 'info' });
  }
  if (row.priceAmount === undefined || row.priceAmount === null) {
    issues.push({ label: 'Missing price', type: 'danger' });
  } else if (Number(row.priceAmount) === 0) {
    issues.push({ label: 'Check $0 price', type: 'warning' });
  }
  if (product?.teachingMode === 'GROUP' && !hasClassGroup(product.id)) {
    issues.push({ label: 'No class yet', type: 'warning' });
  }
  if (issues.length === 0) {
    issues.push({ label: 'Ready', type: 'success' });
  }
  if (!row.referenced) {
    issues.push({ label: 'Not used yet', type: 'info' });
  }
  return issues;
}

function hasClassGroup(productId: EntityId) {
  return classGroups.value.some(item => String(item.productId) === String(productId));
}

function productName(id?: EntityId) {
  if (!id) {
    return 'No product linked';
  }
  return productMap.value.get(String(id))?.productName ?? `Product ${id}`;
}

function productLabel(product: CourseProductItem) {
  const mode = product.teachingMode === 'ONE_ON_ONE' ? '1 on 1' : 'Group';
  return `${product.productName} · ${mode}`;
}

function selectedTypeLabelFor(value: string) {
  return courseTypes.find(item => item.value === value)?.label ?? formatCourseType(value);
}

function modeForCourseType(value: string): CourseMode {
  return value === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'GROUP';
}

function productMatchesCourseType(product: CourseProductItem, courseType: CourseTypeValue) {
  return product.courseType
    ? product.courseType === courseType
    : product.teachingMode === modeForCourseType(courseType);
}

function queryValue(value: unknown) {
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '');
}

function formatCourseType(value: string) {
  return value ? value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase()) : '-';
}

function formatGroupBilling(row: CoursePriceOptionItem) {
  if (row.courseType === 'ONE_ON_ONE') {
    return row.oneOnOneHours ? `${row.oneOnOneHours} hours` : '-';
  }
  if (row.groupBillingType === 'MONTHLY') {
    return [row.groupStartMonth, row.groupEndMonth].filter(Boolean).join(' to ') || 'Monthly';
  }
  return row.groupTotalSessions ? `${row.groupTotalSessions} total sessions` : 'Per session';
}

function formatGroupSchedule(row: CoursePriceOptionItem) {
  if (row.courseType === 'ONE_ON_ONE') {
    return '-';
  }
  return (row.groupScheduleSlots ?? [])
    .map(slot => `${['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][slot.weekday]} ${slot.startTime}-${slot.endTime}`)
    .join(', ') || 'Schedule not set';
}

function formatMoney(value?: number) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}

function formatHours(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function timeToMinutes(value: string) {
  const [hours, minutes] = value.split(':').map(Number);
  return (hours * 60) + minutes;
}
</script>

<style scoped>
.config-panel {
  padding: 18px 20px;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  background: #fff;
}

.config-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.config-panel__header h2 {
  margin: 4px 0 6px;
  font-size: 20px;
}

.config-panel__header p,
.cell-stack span {
  margin: 0;
  color: #6b7280;
}

.config-panel__eyebrow {
  color: #18a058;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.matrix-layout {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 12px;
}

.navigation-sidebar {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding-right: 10px;
  border-right: 1px solid #edf0f3;
}

.navigation-sidebar__label {
  margin: 2px 3px 4px;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.type-tab {
  width: 100%;
  padding: 8px 9px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f9fafb;
  color: #374151;
  text-align: left;
  cursor: pointer;
}

.type-tab strong,
.type-tab span {
  display: block;
}

.type-tab span {
  margin-top: 2px;
  color: #6b7280;
  font-size: 11px;
  line-height: 1.25;
}

.type-tab--active {
  border-color: #18a058;
  background: #effaf3;
  color: #0f7a3a;
}

.navigation-back {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  width: fit-content;
  margin: 0 0 3px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}

.navigation-back:hover {
  color: #0f7a3a;
}

.product-nav-item {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.product-nav-item:hover,
.product-nav-item--active {
  border-color: #c9ead5;
  background: #effaf3;
  color: #0f7a3a;
}

.product-nav-item strong,
.product-nav-item span {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-nav-item strong {
  font-size: 12px;
}

.product-nav-item span {
  color: #64748b;
  font-size: 11px;
}

.matrix-content__context {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
}

.matrix-content__context strong {
  min-width: 0;
  overflow: hidden;
  color: #1e293b;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filters {
  margin-bottom: 12px;
}

.product-required-alert {
  margin-bottom: 12px;
}

.matrix-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.summary-card {
  padding: 10px 12px;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  background: #fbfcfe;
}

.summary-card span {
  display: block;
  color: #6b7280;
  font-size: 12px;
}

.summary-card strong {
  display: block;
  margin-top: 4px;
  color: #111827;
}

.template-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.template-strip > span {
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
}

.config-table {
  width: 100%;
}

.product-price-group {
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.product-price-group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.product-price-group__header div {
  display: grid;
  gap: 2px;
}

.product-price-group__header strong {
  color: #1e293b;
  font-size: 13px;
}

.product-price-group__header span {
  color: #64748b;
  font-size: 12px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.tag-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.matrix-note,
.dialog-hint {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  color: #4b5563;
  font-size: 13px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.form-grid {
  display: grid;
  gap: 14px;
}

.form-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.grade-class-settings {
  display: grid;
  gap: 12px;
  margin: 16px 0;
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.grade-class-settings__head {
  display: grid;
  gap: 3px;
}

.grade-class-settings__head strong {
  color: #1e3a5f;
  font-size: 13px;
}

.grade-class-settings__head small {
  color: #64748b;
  font-size: 12px;
}

.custom-hours {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.hours-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hours-presets :deep(.el-radio-button__inner) {
  border-left: 1px solid var(--el-border-color) !important;
  border-radius: 4px !important;
  box-shadow: none !important;
}

.custom-hours {
  margin-top: 10px;
}

.custom-hours :deep(.el-input-number) {
  width: 160px;
}

.custom-hours span,
.field-help {
  color: #6b7280;
  font-size: 12px;
}

.field-help {
  display: block;
  margin-top: 6px;
  line-height: 1.45;
}

.pricing-context {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 10px;
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f7fa;
}

.pricing-context span,
.pricing-context small {
  color: #6b7280;
  font-size: 12px;
}

.pricing-context small {
  grid-column: 1 / -1;
}

.advanced-settings {
  margin-top: 4px;
  border-top: 1px solid #e5e7eb;
  border-bottom: 1px solid #e5e7eb;
}

@media (max-width: 980px) {
  .matrix-layout {
    grid-template-columns: 1fr;
  }

  .navigation-sidebar {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: 0;
    border-right: 0;
  }

  .matrix-summary,
  .form-grid--three,
  .form-grid--two {
    grid-template-columns: 1fr;
  }
}
</style>
