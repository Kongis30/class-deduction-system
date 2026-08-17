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
        </div>
      </div>

      <div class="pricing-mode-tabs">
        <button
          v-for="mode in courseModes"
          :key="mode.value"
          class="pricing-mode-tab"
          :class="{ 'pricing-mode-tab--active': selectedMode === mode.value }"
          type="button"
          @click="switchCourseMode(mode.value)"
        >
          {{ mode.label }}
        </button>
      </div>

      <div class="matrix-layout">
        <aside class="navigation-sidebar">
          <span class="navigation-sidebar__label">{{ selectedModeLabel }} products</span>
          <el-tooltip
            v-for="product in productChoices"
            :key="product.id"
            :content="product.productName"
            placement="right"
            :show-after="300"
          >
            <button
              :class="['product-nav-item', { 'product-nav-item--active': String(query.courseProductId) === String(product.id) }]"
              type="button"
              @click="selectCourseProduct(product)"
            >
              <strong>{{ product.productName }}</strong>
            </button>
          </el-tooltip>
          <el-empty
            v-if="!loadingProducts && productChoices.length === 0"
            :image-size="44"
            description="No products"
          />
        </aside>

        <div class="matrix-content">
          <section v-if="!query.courseProductId" class="matrix-empty-guide">
            <span>Step 2</span>
            <strong>Choose a course product on the left to set grade and price.</strong>
            <p>{{ emptyGuideDescription }}</p>
            <el-button v-if="fromProductSetup" @click="goToCourseProducts">Back to Course Products</el-button>
          </section>

          <template v-else>
            <div class="matrix-content__context">
              <el-button text type="primary" @click="handleContextBack">
                <el-icon><ArrowLeft /></el-icon>
                {{ contextBackLabel }}
              </el-button>
              <strong>{{ selectedProduct?.productName }}</strong>
            </div>

            <section class="matrix-flow-card">
              <div>
                <span>{{ matrixFlowEyebrow }}</span>
                <strong>{{ matrixFlowTitle }}</strong>
                <p>{{ matrixFlowDescription }}</p>
              </div>
              <el-button type="primary" @click="openDialog()">
                {{ matrixPrimaryActionLabel }}
              </el-button>
            </section>

            <section class="matrix-step-strip">
              <div class="matrix-step-strip__item matrix-step-strip__item--done">
                <span>1</span>
                <strong>Product created</strong>
                <small>{{ selectedProduct?.productName }}</small>
              </div>
              <div class="matrix-step-strip__item matrix-step-strip__item--active">
                <span>2</span>
                <strong>{{ selectedMode === 'GROUP' ? 'Add grade class & price' : 'Add grade/hour price' }}</strong>
                <small>{{ visibleOptions.length }} row{{ visibleOptions.length === 1 ? '' : 's' }} configured</small>
              </div>
              <div class="matrix-step-strip__item">
                <span>3</span>
                <strong>Use in registration</strong>
                <small>Only active rows appear in registration.</small>
              </div>
            </section>

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
              </header>

              <el-table
                v-loading="loading || loadingProducts"
                :data="group.rows"
                row-key="id"
                class="config-table price-matrix-table"
                size="small"
                border
              >
                <el-table-column :label="primaryColumnLabel" min-width="220">
                  <template #header>
                    <header-tip :label="primaryColumnLabel" :tip="primaryColumnTip" />
                  </template>
                  <template #default="{ row }">
                    <div class="matrix-class-cell">
                      <div class="matrix-class-cell__title">
                        <strong>{{ row.gradeBand || 'Grade not set' }}</strong>
                        <el-tag v-if="isGroupRow(row)" size="small" type="info" effect="plain">
                          {{ packageUnitText(row.packageUnit || row.groupBillingType || 'MONTH') }}
                        </el-tag>
                      </div>
                      <template v-if="isGroupRow(row)">
                        <span>{{ formatGroupSchedule(row) }}</span>
                        <small>{{ formatGroupResources(row) }}</small>
                      </template>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column :label="billingColumnLabel" min-width="150">
                  <template #header>
                    <header-tip :label="billingColumnLabel" :tip="billingColumnTip" />
                  </template>
                  <template #default="{ row }">
                    <div class="cell-stack">
                      <strong>{{ formatGroupBilling(row) }}</strong>
                      <span v-if="billingSubtitle(row)">{{ billingSubtitle(row) }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="Price" min-width="150" align="right">
                  <template #header>
                    <header-tip label="Price" :tip="priceColumnTip" />
                  </template>
                  <template #default="{ row }">
                    <div class="price-cell">
                      <strong>{{ formatMoney(row.totalPrice ?? row.priceAmount) }}</strong>
                      <span>{{ priceUnitLabel(row) }}</span>
                      <small v-if="row.derivedUnitPrice">
                        {{ formatMoney(row.derivedUnitPrice) }} / {{ packageUnitText(row.packageUnit || row.groupBillingType || 'package').toLowerCase() }}
                      </small>
                      <small v-if="row.halfDayPrice">
                        Half day {{ formatMoney(row.halfDayPrice) }}
                      </small>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="Usage" width="120">
                  <template #header>
                    <header-tip label="Usage" tip="Whether this price row has already been used by registrations or packages." />
                  </template>
                  <template #default="{ row }">
                    <div class="usage-cell">
                      <el-tag size="small" :type="row.referenced ? 'warning' : 'info'" effect="plain">
                        {{ row.referenced ? `Used ${row.referenceCount ?? 0}` : 'Not used' }}
                      </el-tag>
                      <el-tag
                        v-if="hasRowIssue(row)"
                        size="small"
                        type="danger"
                        effect="plain"
                      >
                        Needs setup
                      </el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="Status" width="96" align="center">
                  <template #header>
                    <header-tip label="Status" tip="Only active rows are available during registration." />
                  </template>
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                      {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="Actions" width="124" align="center">
                  <template #header>
                    <header-tip label="Actions" tip="Edit the row, deactivate it, or delete unused setup rows." />
                  </template>
                  <template #default="{ row }">
                    <div class="matrix-actions">
                      <el-button size="small" type="primary" plain @click="openDialog(row)">Edit</el-button>
                      <el-dropdown trigger="click">
                        <el-button size="small" text>More</el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item v-if="row.status === 'ACTIVE'" @click="deactivateOption(row)">Deactivate</el-dropdown-item>
                            <el-dropdown-item divided class="danger-menu-item" @click="removeOption(row.id)">Delete</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </section>

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
      :title="editingId ? 'Edit price matrix row' : priceDialogTitle"
    >
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <div class="pricing-context">
          <span>Product</span>
          <strong>{{ selectedFormProductName }}</strong>
          <small>Create one package row for each grade, period, or attendance price that students can choose at registration.</small>
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
            <el-select
              v-model="form.gradeBand"
              filterable
              allow-create
              clearable
              default-first-option
              reserve-keyword
              placeholder="Select or type grade / level"
              no-match-text="Press Enter to use this grade / level"
              no-data-text="Type a grade / level and press Enter"
              style="width: 100%;"
            >
              <el-option
                v-for="grade in gradeOptionsForForm"
                :key="grade"
                :label="grade"
                :value="grade"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="formTeachingMode === 'ONE_ON_ONE'" label="Package price" prop="priceAmount">
            <el-input-number v-model="form.priceAmount" :min="0" :precision="2" :controls="false" style="width: 100%;" />
          </el-form-item>
        </div>

        <section v-if="formTeachingMode === 'GROUP'" class="grade-class-settings">
          <div class="grade-class-settings__head">
            <strong>Package and class arrangement</strong>
            <small>Set the package students buy, then set the weekly class time used for attendance deduction.</small>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Package unit">
              <el-select v-model="form.packageUnit" style="width: 100%;">
                <el-option
                  v-for="unit in groupPackageUnits"
                  :key="unit.value"
                  :label="unit.label"
                  :value="unit.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="packageQuantityLabel">
              <el-input-number
                v-model="form.packageQuantity"
                :min="1"
                :precision="form.packageUnit === 'SESSION' ? 0 : 1"
                :step="1"
                :controls="false"
                style="width: 100%;"
              />
            </el-form-item>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Package total price">
              <el-input-number
                v-model="form.totalPrice"
                :min="0"
                :precision="2"
                :controls="false"
                style="width: 100%;"
              />
            </el-form-item>
            <el-form-item label="Half-day package price">
              <el-input-number
                v-model="form.halfDayPrice"
                :min="0"
                :precision="2"
                :controls="false"
                placeholder="Optional"
                style="width: 100%;"
              />
            </el-form-item>
          </div>
          <div class="form-grid form-grid--two calculated-price-grid">
            <div class="calculated-price">
              <span>{{ derivedUnitLabel }}</span>
              <strong>{{ formatMoney(derivedUnitPrice) }}</strong>
            </div>
            <div class="calculated-price">
              <span>Registration price</span>
              <strong>{{ formatMoney(defaultRegistrationPrice) }}</strong>
              <small>Half-day uses its own price when filled.</small>
            </div>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Class start month">
              <el-date-picker v-model="form.groupStartMonth" type="month" value-format="YYYY-MM" style="width: 100%;" />
            </el-form-item>
            <el-form-item label="Class end month">
              <el-date-picker v-model="form.groupEndMonth" type="month" value-format="YYYY-MM" style="width: 100%;" />
            </el-form-item>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Default teacher">
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
          <div v-if="form.groupScheduleSlots.length > 0" class="slot-teacher-list">
            <div
              v-for="(slot, index) in form.groupScheduleSlots"
              :key="`${slot.weekday}-${slot.startTime}-${index}`"
              class="slot-teacher-row"
            >
              <span>{{ groupSlotLabel(slot) }}</span>
              <el-select
                v-model="slot.teacherId"
                clearable
                filterable
                placeholder="Use default teacher"
              >
                <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
              </el-select>
            </div>
          </div>
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
            <el-form-item label="Registration detail">
              <el-input v-model="form.detailText" type="textarea" :rows="2" placeholder="Optional" />
            </el-form-item>
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
        <el-button type="primary" :loading="saving" @click="saveOption">{{ priceSaveButtonLabel }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, InfoFilled } from '@element-plus/icons-vue';
import { ElIcon, ElMessage, ElMessageBox, ElTooltip } from 'element-plus';
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue';
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
type PackageUnit = 'MONTH' | 'WEEK' | 'SESSION' | 'HOUR';

interface CourseTypeDef {
  value: CourseTypeValue;
  label: string;
  mode: CourseMode;
}

interface CourseModeDef {
  value: CourseMode;
  label: string;
}

interface ProductPriceGroup {
  productId: EntityId | undefined;
  productName: string;
  registerClass: string;
  rows: CoursePriceOptionItem[];
}

const HeaderTip = defineComponent({
  name: 'HeaderTip',
  props: {
    label: { type: String, required: true },
    tip: { type: String, required: true },
  },
  setup(props) {
    return () => h('span', { class: 'table-header-tip' }, [
      h('span', props.label),
      h(ElTooltip, { content: props.tip, placement: 'top', effect: 'dark' }, {
        default: () => h(ElIcon, { class: 'table-header-tip__icon' }, () => h(InfoFilled)),
      }),
    ]);
  },
});

const courseTypes: CourseTypeDef[] = [
  { value: 'AFTER_SCHOOL', label: 'After School', mode: 'GROUP' },
  { value: 'SATURDAY', label: 'Saturday Class', mode: 'GROUP' },
  { value: 'SUMMER', label: 'Summer Class', mode: 'GROUP' },
  { value: 'SPRINT', label: 'Sprint Class', mode: 'GROUP' },
  { value: 'ONE_ON_ONE', label: '1 on 1', mode: 'ONE_ON_ONE' },
];

const courseModes: CourseModeDef[] = [
  { value: 'ONE_ON_ONE', label: '1-on-1 Private' },
  { value: 'GROUP', label: 'Group Class' },
];

const route = useRoute();
const router = useRouter();
const oneOnOneHourChoices = [20, 40, 60, 80, 100, 120];
const groupPackageUnits: Array<{ value: Exclude<PackageUnit, 'HOUR'>; label: string }> = [
  { value: 'MONTH', label: 'Month package' },
  { value: 'WEEK', label: 'Week package' },
  { value: 'SESSION', label: 'Session package' },
];
const pptGradeOptions: Record<CourseTypeValue, string[]> = {
  AFTER_SCHOOL: ['G1-G5', 'G6-G8', 'ESL'],
  SATURDAY: ['G1-2', 'G3-G6', 'SHSAT', 'PSAT', 'SAT'],
  SUMMER: ['G1-G6', 'SHSAT', 'PSAT', 'SAT', 'ESL'],
  SPRINT: [],
  ONE_ON_ONE: ['G1-G5', 'G6-G8', 'G9-G12', 'AP'],
};

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
  courseType: 'ONE_ON_ONE' as CourseTypeValue,
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
  packageUnit: 'MONTH' as PackageUnit,
  packageQuantity: 2 as number | undefined,
  totalPrice: undefined as number | undefined,
  halfDayPrice: undefined as number | undefined,
  courseProductId: undefined as EntityId | undefined,
  groupTeacherId: undefined as EntityId | undefined,
  groupClassroomId: undefined as EntityId | undefined,
  groupScheduleSlots: [] as Array<{ teacherId?: EntityId; weekday: number; startTime: string; endTime: string; plannedUnits: number }>,
  groupStartDate: '',
  groupTotalSessions: 13 as number | undefined,
  groupTotalPrice: undefined as number | undefined,
  groupStartMonth: '',
  groupEndMonth: '',
  sortOrder: 0,
  status: 'ACTIVE',
});

const rules = {
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
  priceAmount: [{
    validator: (_rule: unknown, value: number, callback: (error?: Error) => void) => {
      callback(value > 0 ? undefined : new Error('Price must be greater than zero'));
    },
    trigger: 'change',
  }],
};

const gradeOptionsForForm = computed(() => pptGradeOptions[form.courseType] ?? []);
const derivedUnitPrice = computed(() => {
  const totalPrice = Number(form.totalPrice ?? 0);
  const quantity = Number(form.packageQuantity ?? 0);
  return totalPrice > 0 && quantity > 0
    ? Math.round((totalPrice / quantity) * 100) / 100
    : 0;
});
const derivedUnitLabel = computed(() => `${packageUnitText(form.packageUnit)} price`);
const defaultRegistrationPrice = computed(() => Number(form.totalPrice ?? 0));
const packageQuantityLabel = computed(() => `${packageUnitText(form.packageUnit)} count`);

const selectedMode = ref<CourseMode>('ONE_ON_ONE');
const selectedModeLabel = computed(() =>
  courseModes.find(item => item.value === selectedMode.value)?.label ?? ''
);
const productMap = computed(() => new Map(courseProducts.value.map(item => [String(item.id), item])));
const selectedProduct = computed(() => query.courseProductId ? productMap.value.get(String(query.courseProductId)) : undefined);
const startedFromProductCreate = computed(() => queryValue(route.query.create) === '1');
const fromProductSetup = computed(() => queryValue(route.query.from) === 'course-products');
const productChoices = computed(() =>
  courseProducts.value.filter(product => product.teachingMode === selectedMode.value)
);
const visibleOptions = computed(() => {
  return options.value.filter((option) => {
    if (modeForCourseType(option.courseType) !== selectedMode.value) {
      return false;
    }
    if (!query.courseProductId) {
      return false;
    }
    if (query.courseProductId && String(option.courseProductId) !== String(query.courseProductId)) {
      return false;
    }
    return true;
  });
});

const matrixFlowTitle = computed(() => {
  if (selectedMode.value === 'ONE_ON_ONE') {
    return visibleOptions.value.length === 0
      ? 'Step 2: add grade, package hours, and price'
      : 'Add more grade/hour price rows when needed';
  }
  return visibleOptions.value.length === 0
    ? 'Step 2: add grade class, billing, schedule, and price'
    : 'Review grade classes, schedules, and prices';
});

const matrixFlowEyebrow = computed(() => {
  if (startedFromProductCreate.value) {
    return 'You just created the course';
  }
  return fromProductSetup.value ? 'Continue from Course Products' : 'Course setup flow';
});

const matrixFlowDescription = computed(() => {
  if (selectedMode.value === 'ONE_ON_ONE') {
    return 'Each row becomes one registration option, such as G1-G5 / 40 hours / $price.';
  }
  return 'Each row creates one grade class for enrollment. Add grade, billing run, weekly time, teacher or room if known, and price.';
});

const emptyGuideDescription = computed(() =>
  selectedMode.value === 'GROUP'
    ? 'Products are only parent courses. Pick one here, then add the grade, billing period, weekly time, teacher, room, and price.'
    : 'Pick a 1-on-1 product here, then add grade, package hours, and price rows.'
);

const contextBackLabel = computed(() =>
  fromProductSetup.value ? 'Back to Course Products' : 'Choose another product'
);

const matrixPrimaryActionLabel = computed(() =>
  selectedMode.value === 'ONE_ON_ONE'
    ? 'Add grade and price'
    : 'Add grade class and price'
);

const priceDialogTitle = computed(() =>
  selectedMode.value === 'ONE_ON_ONE'
    ? 'Step 2: Add grade and package price'
    : 'Step 2: Add grade class and price'
);

const priceSaveButtonLabel = computed(() => {
  if (editingId.value) {
    return 'Save changes';
  }
  return selectedMode.value === 'ONE_ON_ONE'
    ? 'Save price row'
    : 'Save grade class';
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
const primaryColumnLabel = computed(() => selectedMode.value === 'ONE_ON_ONE' ? 'Grade' : 'Class schedule');
const primaryColumnTip = computed(() => selectedMode.value === 'ONE_ON_ONE'
  ? 'Grade range that can use this private lesson package.'
  : 'Grade class setup used for registration, including weekly schedule and assigned resources.');
const billingColumnLabel = computed(() => selectedMode.value === 'ONE_ON_ONE' ? 'Package' : 'Billing window');
const billingColumnTip = computed(() => selectedMode.value === 'ONE_ON_ONE'
  ? 'Package size sold to the student, measured in hours.'
  : 'Package length students buy, such as 2 months, 13 weeks, or 20 sessions.');
const priceColumnTip = computed(() => selectedMode.value === 'ONE_ON_ONE'
  ? 'Total package price for the selected private lesson hours.'
  : 'Total package price. Registration uses this configured package price directly.');
const formProductChoices = computed(() =>
  courseProducts.value.filter(product => product.teachingMode === formTeachingMode.value)
);
const selectedFormProductName = computed(() =>
  form.courseProductId ? productName(form.courseProductId) : 'Select course product'
);

watch(() => form.packageUnit, (unit) => {
  if (formTeachingMode.value !== 'GROUP') {
    return;
  }
  if (!form.packageQuantity || form.packageQuantity <= 0) {
    form.packageQuantity = defaultPackageQuantity(unit);
  }
  form.groupTotalSessions = Math.ceil(Number(form.packageQuantity ?? defaultPackageQuantity(unit)));
});

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
    total.value = query.courseProductId ? visibleOptions.value.length : (res.data.data.total ?? 0);
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

function switchCourseMode(mode: CourseMode) {
  if (selectedMode.value === mode) {
    return;
  }
  selectedMode.value = mode;
  query.courseType = mode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'AFTER_SCHOOL';
  query.courseProductId = '';
  reloadFirstPage();
}

function chooseAnotherProduct() {
  query.courseProductId = '';
}

function goToCourseProducts() {
  router.push({
    name: 'course-products',
    query: { tab: selectedMode.value === 'ONE_ON_ONE' ? 'one-on-one' : 'group' },
  });
}

function handleContextBack() {
  if (fromProductSetup.value) {
    goToCourseProducts();
    return;
  }
  chooseAnotherProduct();
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
  form.registerClass = option?.registerClass ?? selectedProduct.value?.registerClass ?? selectedProduct.value?.productName ?? selectedTypeLabelFor(form.courseType);
  form.gradeBand = option?.gradeBand ?? '';
  form.periodLabel = option?.periodLabel ?? '';
  form.durationLabel = option?.durationLabel ?? '';
  form.timeSlotLabel = option?.timeSlotLabel ?? '';
  form.detailText = option?.detailText ?? '';
  form.oneOnOneHours = option?.packageQuantity ?? option?.oneOnOneHours;
  form.customOneOnOneHours = undefined;
  form.oneOnOneHoursMode = oneOnOneHourChoices.includes(Number(option?.oneOnOneHours))
    ? String(option?.oneOnOneHours)
    : 'CUSTOM';
  if (form.oneOnOneHoursMode === 'CUSTOM') {
    form.customOneOnOneHours = option?.packageQuantity ?? option?.oneOnOneHours;
  }
  if (!option) {
    if (formTeachingMode.value === 'ONE_ON_ONE') {
      form.oneOnOneHours = 40;
      form.oneOnOneHoursMode = '40';
      syncOneOnOneDuration();
    }
  }
  form.priceAmount = option?.totalPrice ?? option?.priceAmount ?? 0;
  form.packageUnit = normalizePackageUnit(option?.packageUnit, formTeachingMode.value);
  form.packageQuantity = option?.packageQuantity
    ?? (formTeachingMode.value === 'GROUP' ? option?.groupTotalSessions : option?.oneOnOneHours)
    ?? defaultPackageQuantity(form.packageUnit);
  form.totalPrice = option?.totalPrice ?? option?.groupTotalPrice ?? option?.priceAmount;
  form.halfDayPrice = option?.halfDayPrice;
  form.courseProductId = option?.courseProductId ?? (query.courseProductId || undefined);
  form.groupTeacherId = option?.groupTeacherId;
  form.groupClassroomId = option?.groupClassroomId;
  form.groupScheduleSlots = (option?.groupScheduleSlots ?? []).map(slot => ({
    ...slot,
    plannedUnits: Math.round((timeToMinutes(slot.endTime) - timeToMinutes(slot.startTime)) / 30),
  }));
  form.groupStartDate = option?.groupStartDate ?? '';
  form.groupTotalSessions = Number(form.packageQuantity ?? option?.groupTotalSessions ?? 13);
  form.groupTotalPrice = option?.totalPrice ?? option?.groupTotalPrice;
  form.groupStartMonth = option?.groupStartMonth ?? '';
  form.groupEndMonth = option?.groupEndMonth ?? '';
  form.sortOrder = option?.sortOrder ?? 0;
  form.status = option?.status ?? 'ACTIVE';
  dialogVisible.value = true;
}

function handleFormProductChange() {
  const product = form.courseProductId ? productMap.value.get(String(form.courseProductId)) : undefined;
  if (product) {
    form.courseType = (product.courseType as CourseTypeValue) || form.courseType;
    form.registerClass = product.registerClass || product.productName;
    if (product.teachingMode === 'ONE_ON_ONE') {
      form.packageUnit = 'HOUR';
    } else if (form.packageUnit === 'HOUR') {
      form.packageUnit = 'MONTH';
      form.packageQuantity = defaultPackageQuantity(form.packageUnit);
    }
  }
}

function normalizePackageUnit(unit: string | undefined, mode: CourseMode): PackageUnit {
  if (mode === 'ONE_ON_ONE') {
    return 'HOUR';
  }
  const normalized = String(unit || '').toUpperCase();
  return normalized === 'WEEK' || normalized === 'SESSION' ? normalized : 'MONTH';
}

function defaultPackageQuantity(unit: string) {
  const normalized = String(unit || '').toUpperCase();
  if (normalized === 'WEEK') return 13;
  if (normalized === 'SESSION') return 13;
  if (normalized === 'HOUR') return 40;
  return 2;
}

function optionalPositive(value?: number) {
  return value != null && value > 0 ? value : undefined;
}

function selectOneOnOneHours(value: string) {
  if (value === 'CUSTOM') {
    form.oneOnOneHours = undefined;
    form.customOneOnOneHours = undefined;
    form.durationLabel = '';
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
}

function buildPayload(status = form.status): CoursePriceOptionSavePayload {
  const packageUnit = formTeachingMode.value === 'ONE_ON_ONE' ? 'HOUR' : form.packageUnit;
  const packageQuantity = formTeachingMode.value === 'ONE_ON_ONE'
    ? form.oneOnOneHours
    : form.packageQuantity;
  const packageTotalPrice = formTeachingMode.value === 'ONE_ON_ONE'
    ? form.priceAmount
    : form.totalPrice;
  return {
    optionCode: form.optionCode.trim() || undefined,
    courseType: form.courseType,
    registerClass: form.registerClass.trim(),
    gradeBand: form.gradeBand.trim() || undefined,
    periodLabel: formTeachingMode.value === 'ONE_ON_ONE' ? form.periodLabel.trim() || undefined : undefined,
    durationLabel: formTeachingMode.value === 'ONE_ON_ONE' ? form.durationLabel.trim() || undefined : undefined,
    timeSlotLabel: undefined,
    detailText: form.detailText.trim() || undefined,
    packageUnit,
    packageQuantity,
    totalPrice: packageTotalPrice,
    fullDayPrice: formTeachingMode.value === 'GROUP' ? packageTotalPrice : undefined,
    halfDayPrice: formTeachingMode.value === 'GROUP' ? optionalPositive(form.halfDayPrice) : undefined,
    oneOnOneHours: formTeachingMode.value === 'ONE_ON_ONE' ? form.oneOnOneHours : undefined,
    priceAmount: packageTotalPrice ?? 0,
    currencyCode: 'USD',
    courseProductId: form.courseProductId,
    groupTeacherId: formTeachingMode.value === 'GROUP' ? form.groupTeacherId : undefined,
    groupClassroomId: formTeachingMode.value === 'GROUP' ? form.groupClassroomId : undefined,
    groupScheduleSlots: formTeachingMode.value === 'GROUP'
      ? form.groupScheduleSlots.map(({ teacherId, weekday, startTime, endTime }) => ({
          teacherId, weekday, startTime, endTime,
        }))
      : undefined,
    groupBillingType: formTeachingMode.value === 'GROUP' ? packageUnit : undefined,
    groupStartDate: undefined,
    groupTotalSessions: formTeachingMode.value === 'GROUP' ? Math.ceil(Number(packageQuantity ?? 0)) : undefined,
    groupTotalPrice: formTeachingMode.value === 'GROUP' ? packageTotalPrice : undefined,
    groupStartMonth: formTeachingMode.value === 'GROUP' ? form.groupStartMonth || undefined : undefined,
    groupEndMonth: formTeachingMode.value === 'GROUP' ? form.groupEndMonth || undefined : undefined,
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
    packageUnit: row.packageUnit,
    packageQuantity: row.packageQuantity,
    totalPrice: row.totalPrice,
    fullDayPrice: row.fullDayPrice,
    halfDayPrice: row.halfDayPrice,
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
    groupTotalPrice: row.groupTotalPrice,
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
  if (formTeachingMode.value === 'GROUP'
      && (!form.totalPrice || form.totalPrice <= 0
        || !form.packageQuantity || form.packageQuantity <= 0)) {
    ElMessage.warning('Add a package total price and package count.');
    return;
  }
  if (formTeachingMode.value === 'GROUP' && (!form.groupStartMonth || !form.groupEndMonth)) {
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
  return product.productName;
}

function selectedTypeLabelFor(value: string) {
  return courseTypes.find(item => item.value === value)?.label ?? formatCourseType(value);
}

function modeForCourseType(value: string): CourseMode {
  return value === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'GROUP';
}

function productMatchesCourseType(product: CourseProductItem, courseType: CourseTypeValue) {
  return product.teachingMode === modeForCourseType(courseType);
}

function queryValue(value: unknown) {
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '');
}

function formatCourseType(value: string) {
  return value ? value.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char: string) => char.toUpperCase()) : '-';
}

function formatGroupBilling(row: CoursePriceOptionItem) {
  if (row.courseType === 'ONE_ON_ONE') {
    return packageLengthLabel(row) || '-';
  }
  return packageLengthLabel(row) || [row.groupStartMonth, row.groupEndMonth].filter(Boolean).join(' to ') || 'Package';
}

function isGroupRow(row: CoursePriceOptionItem) {
  return modeForCourseType(row.courseType) === 'GROUP';
}

function billingSubtitle(row: CoursePriceOptionItem) {
  if (isGroupRow(row)) {
    const window = [row.groupStartMonth, row.groupEndMonth].filter(Boolean).join(' to ');
    return window || '';
  }
  return row.notes || '';
}

function groupBillingTypeLabel(row: CoursePriceOptionItem) {
  return packageUnitText(row.packageUnit || row.groupBillingType || 'MONTH');
}

function packageLengthLabel(row: CoursePriceOptionItem) {
  const quantity = row.packageQuantity ?? row.oneOnOneHours ?? row.groupTotalSessions;
  const unit = row.packageUnit || row.groupBillingType || (row.courseType === 'ONE_ON_ONE' ? 'HOUR' : 'MONTH');
  if (!quantity || quantity <= 0) {
    return row.durationLabel || '';
  }
  return `${formatQuantity(quantity)} ${packageUnitPlural(unit, quantity)}`;
}

function packageUnitText(unit?: string) {
  const normalized = String(unit || '').toUpperCase();
  if (normalized === 'MONTH' || normalized === 'MONTHLY') return 'Month';
  if (normalized === 'WEEK') return 'Week';
  if (normalized === 'SESSION' || normalized === 'PER_SESSION') return 'Session';
  if (normalized === 'HOUR') return 'Hour';
  return 'Package';
}

function packageUnitPlural(unit: string, quantity: number) {
  const text = packageUnitText(unit).toLowerCase();
  return Number(quantity) === 1 ? text : `${text}s`;
}

function formatQuantity(value: number) {
  return Number.isInteger(Number(value)) ? String(Number(value)) : Number(value).toFixed(1);
}

function formatGroupSchedule(row: CoursePriceOptionItem) {
  if (row.courseType === 'ONE_ON_ONE') {
    return '-';
  }
  return (row.groupScheduleSlots ?? [])
    .map(slot => `${['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][slot.weekday]} ${slot.startTime}-${slot.endTime}`)
    .join(', ') || 'Schedule not set';
}

function groupSlotLabel(slot: { weekday: number; startTime: string; endTime: string }) {
  const weekday = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][slot.weekday] ?? '';
  return `${weekday} ${slot.startTime}-${slot.endTime}`;
}

function formatGroupResources(row: CoursePriceOptionItem) {
  const teacher = teacherName(row.groupTeacherId);
  const room = classroomName(row.groupClassroomId);
  return `Teacher: ${teacher} · Room: ${room}`;
}

function teacherName(id?: EntityId) {
  if (!id) return 'Assign later';
  return teachers.value.find(item => String(item.id) === String(id))?.teacherName ?? 'Assign later';
}

function classroomName(id?: EntityId) {
  if (!id) return 'Assign later';
  return classrooms.value.find(item => String(item.id) === String(id))?.roomName ?? 'Assign later';
}

function hasRowIssue(row: CoursePriceOptionItem) {
  const product = row.courseProductId ? productMap.value.get(String(row.courseProductId)) : undefined;
  if (!product) {
    return true;
  }
  return product.teachingMode !== modeForCourseType(row.courseType)
    || row.totalPrice === undefined
    || row.totalPrice === null
    || Number(row.totalPrice) === 0
    || (product.teachingMode === 'GROUP' && !hasClassGroup(product.id))
    || (modeForCourseType(row.courseType) === 'GROUP' && (row.groupScheduleSlots ?? []).length === 0);
}

function priceUnitLabel(row: CoursePriceOptionItem) {
  const currency = row.currencyCode || 'USD';
  if (modeForCourseType(row.courseType) === 'GROUP') {
    return `${currency} / package`;
  }
  return `${currency} / package`;
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
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.pricing-mode-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 14px;
  border-bottom: 2px solid #e8edf2;
}

.pricing-mode-tab {
  margin-bottom: -2px;
  padding: 10px 24px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #909399;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.pricing-mode-tab:hover {
  color: #18b58a;
}

.pricing-mode-tab--active {
  border-bottom-color: #18b58a;
  color: #18b58a;
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

.product-nav-item {
  display: block;
  width: 100%;
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

.product-nav-item strong {
  display: block;
  width: 100%;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.matrix-empty-guide {
  display: grid;
  gap: 7px;
  padding: 18px;
  border: 1px dashed #bfdbfe;
  border-radius: 10px;
  background: #eff6ff;
}

.matrix-empty-guide span {
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.matrix-empty-guide strong {
  color: #1f2937;
  font-size: 15px;
}

.matrix-empty-guide p {
  max-width: 640px;
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.matrix-flow-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid #d8e7ff;
  border-radius: 10px;
  background: #f7fbff;
}

.matrix-flow-card div {
  min-width: 0;
}

.matrix-flow-card span {
  display: block;
  color: #409eff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.matrix-flow-card strong {
  display: block;
  margin-top: 3px;
  color: #1f2937;
  font-size: 14px;
}

.matrix-flow-card p {
  margin: 3px 0 0;
  color: #606266;
  font-size: 12px;
  line-height: 1.45;
}

.matrix-step-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.matrix-step-strip__item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 3px 8px;
  align-items: start;
  padding: 10px 12px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #f8fafc;
}

.matrix-step-strip__item--done,
.matrix-step-strip__item--active {
  border-color: #c9ead5;
  background: #effaf3;
}

.matrix-step-strip__item span {
  grid-row: span 2;
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: #cbd5e1;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.matrix-step-strip__item--done span,
.matrix-step-strip__item--active span {
  background: #18a058;
}

.matrix-step-strip__item strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.matrix-step-strip__item small {
  overflow: hidden;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-table {
  width: 100%;
}

.price-matrix-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
}

.table-header-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 0;
}

.table-header-tip__icon {
  color: #94a3b8;
  font-size: 13px;
  cursor: help;
}

.table-header-tip__icon:hover {
  color: #409eff;
}

.price-matrix-table :deep(.el-table__cell) {
  vertical-align: top;
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

.cell-stack strong {
  color: #1e293b;
}

.matrix-class-cell {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.matrix-class-cell__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.matrix-class-cell__title strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.matrix-class-cell span,
.matrix-class-cell small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-cell,
.usage-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.price-cell {
  align-items: flex-end;
}

.price-cell strong {
  color: #111827;
  font-size: 14px;
}

.price-cell span {
  color: #94a3b8;
  font-size: 11px;
}

.price-cell small {
  color: #64748b;
  font-size: 10px;
}

.usage-cell {
  align-items: flex-start;
}

.matrix-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.matrix-actions :deep(.el-button) {
  margin-left: 0;
}

:deep(.danger-menu-item) {
  color: #f56c6c;
}

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

.form-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.calculated-price-grid {
  gap: 12px;
}

.calculated-price {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #fff;
}

.calculated-price span,
.calculated-price small {
  color: #64748b;
  font-size: 12px;
}

.calculated-price strong {
  color: #1d4ed8;
  font-size: 18px;
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

.slot-teacher-list {
  display: grid;
  gap: 8px;
}

.slot-teacher-row {
  display: grid;
  grid-template-columns: minmax(150px, 0.8fr) minmax(220px, 1.2fr);
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid #dbeafe;
  border-radius: 6px;
  background: #fff;
}

.slot-teacher-row > span {
  color: #334155;
  font-size: 13px;
  font-weight: 600;
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

  .form-grid--two {
    grid-template-columns: 1fr;
  }
}
</style>
