<template>
  <div class="pr-page">

    <aside class="pr-sidebar" :class="{ 'pr-sidebar--mobile-hidden': appStore.isMobileViewport && selectedTeacherId }">
      <div v-if="appStore.isMobileViewport" class="pr-mobile-list-header">
        <div>
          <strong>Teacher list</strong>
          <span v-if="teachersLoading">Loading teachers…</span>
          <span v-else>{{ filteredTeachers.length }} teachers</span>
        </div>
        <button
          type="button"
          class="mobile-search-trigger"
          :aria-expanded="mobileSearchExpanded"
          aria-label="Search teachers"
          @click="mobileSearchExpanded = !mobileSearchExpanded"
        >
          <el-icon><Search /></el-icon>
        </button>
      </div>
      <section v-show="!appStore.isMobileViewport || mobileSearchExpanded" :class="{ 'mobile-search-panel': appStore.isMobileViewport }">
        <div v-if="appStore.isMobileViewport" class="mobile-search-panel__header">
          <span>Find a teacher</span>
          <button type="button" aria-label="Close search" @click="mobileSearchExpanded = false">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <div class="pr-sidebar__search">
          <el-input v-model="teacherKeyword" placeholder="Search teacher..." clearable size="small" />
        </div>
      </section>
      <div class="pr-sidebar__list" v-loading="teachersLoading">
        <div
          v-for="t in filteredTeachers"
          :key="t.id"
          class="pr-sidebar__item"
          :class="{ 'pr-sidebar__item--active': selectedTeacherId === t.id }"
          @click="selectTeacher(t.id)"
        >
          <div class="pr-sidebar__name">{{ t.teacherName }}</div>
          <div class="pr-sidebar__meta">{{ t.teacherNo }}</div>
        </div>
        <div v-if="!teachersLoading && filteredTeachers.length === 0" class="pr-sidebar__empty">
          No teachers found.
        </div>
      </div>
    </aside>


    <main class="pr-content" :class="{ 'pr-content--mobile-hidden': appStore.isMobileViewport && !selectedTeacherId }">
      <div v-if="appStore.isMobileViewport && selectedTeacher" class="pr-mobile-selection">
        <el-button text @click="selectedTeacherId = null">← Teacher list</el-button>
        <strong>{{ selectedTeacher.teacherName }}</strong>
        <small>{{ selectedTeacher.teacherNo }}</small>
      </div>
      <div v-if="!selectedTeacherId" class="pr-content__placeholder">
        Select a teacher from the left panel to set their hourly rates.
      </div>

      <template v-else-if="selectedTeacher">
        <div class="pr-content__header">
          <div>
            <div class="pr-content__title">
              <strong>{{ selectedTeacher.teacherName }}</strong>
              <span class="pr-content__no">{{ selectedTeacher.teacherNo }}</span>
            </div>
            <div class="pr-content__hint">
              Most teachers need one standard hourly rate. Add an exception only when their pay varies by grade band, delivery, course, or student.
            </div>
          </div>
          <div class="pr-summary">
            <span><strong>{{ defaultRate ? formatMoney(defaultRate.hourlyRate) : 'Not set' }}</strong> standard rate</span>
            <span><strong>{{ rateExceptionRows.length }}</strong> exceptions</span>
          </div>
        </div>

        <div class="pr-rule-strip">
          <div class="pr-rule-step">
            <small>Default</small>
            <strong>Teacher standard rate</strong>
            <span>Used for every course unless an exception applies.</span>
          </div>
          <div class="pr-rule-step">
            <small>Product</small>
            <strong>Product default rate</strong>
            <span>Overrides the standard rate for one course product.</span>
          </div>
          <div class="pr-rule-step">
            <small>Special</small>
            <strong>Grade, delivery, or student</strong>
            <span>Used only when a matching scope exists.</span>
          </div>
        </div>
        <details v-if="appStore.isMobileViewport" class="pr-mobile-rule-guide">
          <summary>How rate rules work</summary>
          <p><strong>Teacher standard</strong> applies by default. A product rate overrides it for that course. A special rate applies when its grade, delivery, or student scope matches.</p>
        </details>

        <section class="pr-section">
              <div class="pr-section__heading">
                <div>
                  <strong>Teacher rates</strong>
                  <span>Each product shows its effective default rate. Expand a row to review or edit its special rates.</span>
                </div>
                <div class="pr-section__actions">
                  <el-button size="small" @click="openStandardRateDialog">
                    {{ defaultRate ? 'Edit standard rate' : 'Set standard rate' }}
                  </el-button>
                  <el-button size="small" type="primary" plain @click="openAddExceptionDialog">
                    Add special rate
                  </el-button>
                </div>
              </div>
              <el-table
                v-if="!appStore.isMobileViewport"
                v-loading="loading"
                :data="rateGroups"
                row-key="key"
                size="small"
                class="pr-table"
                empty-text="Set a standard hourly rate to begin."
              >
                <el-table-column type="expand" width="42">
                  <template #default="{ row }">
                    <div class="pr-nested-rate">
                      <div class="pr-nested-rate__heading">
                        <span>Special rates for {{ row.productName }}</span>
                        <el-button size="small" type="primary" plain @click="openAddExceptionDialog(row.productId)">
                          Add special rate
                        </el-button>
                      </div>
                      <el-table
                        :data="row.exceptions"
                        size="small"
                        class="pr-nested-rate__table"
                        empty-text="No special rates. The effective default rate above will be used."
                      >
                        <el-table-column label="Grade level" min-width="130">
                          <template #default="{ row: rate }">{{ rate.gradeBand || 'Any grade' }}</template>
                        </el-table-column>
                        <el-table-column label="Delivery" width="120">
                          <template #default="{ row: rate }">{{ formatDelivery(rate.deliveryMode) }}</template>
                        </el-table-column>
                        <el-table-column label="Student" min-width="150">
                          <template #default="{ row: rate }">{{ rate.studentName || 'All students' }}</template>
                        </el-table-column>
                        <el-table-column label="Hourly rate" width="130" align="right">
                          <template #default="{ row: rate }">{{ formatMoney(rate.hourlyRate) }}</template>
                        </el-table-column>
                        <el-table-column label="Action" width="120" fixed="right">
                          <template #default="{ row: rate }">
                            <el-button text type="primary" size="small" @click="openEditDialog(rate)">Edit</el-button>
                            <el-button
                              text
                              type="danger"
                              size="small"
                              :loading="removingRateId === rate.rateId"
                              :disabled="!rate.rateId"
                              @click="handleDeleteSpecialRate(rate)"
                            >Delete</el-button>
                          </template>
                        </el-table-column>
                      </el-table>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="Course product" min-width="240">
                  <template #default="{ row }">
                    <div class="pr-product-cell">
                      <span class="pr-product-cell__name">{{ row.productName }}</span>
                      <span v-if="row.subjectName" class="pr-product-cell__subject">{{ row.subjectName }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="Effective default rate" min-width="180" align="right">
                  <template #default="{ row }">
                    <span :class="{ 'pr-rate--set': row.effectiveRate > 0, 'pr-rate--unset': !row.effectiveRate }">
                      {{ formatMoney(row.effectiveRate) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="Special rates" width="130" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.exceptions.length ? 'warning' : 'info'">
                      {{ row.exceptions.length }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="Action" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.baseRate"
                      text
                      type="primary"
                      size="small"
                      @click="openEditDialog(row.baseRate)"
                    >
                      Edit default
                    </el-button>
                    <el-button
                      v-else-if="row.productId"
                      text
                      type="primary"
                      size="small"
                      @click="openProductRateDialog(row)"
                    >
                      Set product rate
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="appStore.isMobileViewport" class="pr-mobile-rate-list">
                <article v-for="row in rateGroups" :key="row.key" class="pr-mobile-rate-card">
                  <div class="pr-mobile-rate-card__header">
                    <div><strong>{{ row.productName }}</strong><span>{{ row.subjectName || 'Course product' }}</span></div>
                    <strong :class="{ 'pr-rate--set': row.effectiveRate > 0, 'pr-rate--unset': !row.effectiveRate }">{{ formatMoney(row.effectiveRate) }}</strong>
                  </div>
                  <div class="pr-mobile-rate-card__meta">Effective default rate · {{ row.exceptions.length }} special rate{{ row.exceptions.length === 1 ? '' : 's' }}</div>
                  <div class="pr-mobile-rate-card__actions">
                    <el-button v-if="row.baseRate" size="small" text type="primary" @click="openEditDialog(row.baseRate)">Edit default</el-button>
                    <el-button v-else-if="row.productId" size="small" text type="primary" @click="openProductRateDialog(row)">Set product rate</el-button>
                    <el-button size="small" type="primary" plain @click="openAddExceptionDialog(row.productId)">Add special rate</el-button>
                  </div>
                  <div v-if="row.exceptions.length" class="pr-mobile-exceptions">
                    <div v-for="rate in row.exceptions" :key="rate.rateId" class="pr-mobile-exception">
                      <div><strong>{{ scopeSummary(rate) }}</strong><span>{{ formatMoney(rate.hourlyRate) }}</span></div>
                      <el-button text type="primary" size="small" @click="openEditDialog(rate)">Edit</el-button>
                    </div>
                  </div>
                </article>
                <el-empty v-if="!loading && rateGroups.length === 0" description="Set a standard hourly rate to begin" />
              </div>
        </section>
      </template>
    </main>


    <el-dialog
      v-model="editDialogVisible"
      :width="appStore.isMobileViewport ? 'calc(100vw - 24px)' : '400px'"
      :close-on-click-modal="false"
      class="pr-dialog"
    >
      <template #header>
        <h3>Set Hourly Rate</h3>
      </template>

      <template v-if="editingRow">
        <div class="pr-dialog__product">
          <template v-if="addingScopedRate">
            <strong>{{ standardRateDialog ? 'Standard hourly rate' : productDefaultDialog ? 'Product default rate' : 'New special rate' }}</strong>
            <span>{{ standardRateDialog
              ? 'Applies to all courses, grades, delivery types, and students.'
              : productDefaultDialog
                ? 'Applies to this product unless a more specific rate applies.'
                : 'Add grade band and delivery first. Course and student are optional advanced scopes.' }}</span>
          </template>
          <template v-else>
            <strong>{{ editingRow.productName || 'Teacher-wide rate' }}</strong>
            <span>{{ scopeSummary(editingRow) }}</span>
          </template>
        </div>
        <el-form label-position="top" class="pr-dialog__form">
          <div v-if="!standardRateDialog && !productDefaultDialog" class="pr-scope-grid">
            <el-form-item label="Grade band">
              <el-select v-model="editingGradeBand" clearable placeholder="Any grade" style="width: 100%;">
                <el-option label="Any grade" :value="undefined" />
                <el-option v-for="grade in gradeScopeOptions" :key="grade" :label="grade" :value="grade" />
              </el-select>
            </el-form-item>
            <el-form-item label="Delivery">
              <el-select v-model="editingDeliveryMode" clearable placeholder="Any delivery" style="width: 100%;">
                <el-option label="Any delivery" :value="undefined" />
                <el-option label="Online" value="ONLINE" />
                <el-option label="In person" value="OFFLINE" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item v-if="!standardRateDialog && !productDefaultDialog" label="Course product (optional)">
            <el-select v-model="editingProductId" clearable filterable placeholder="All courses" style="width: 100%;">
              <el-option label="All courses" :value="undefined" />
              <el-option
                v-for="product in allProducts"
                :key="product.id"
                :label="product.productName"
                :value="product.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="!standardRateDialog && !productDefaultDialog" label="Student (optional)">
            <el-select v-model="editingStudentId" clearable placeholder="All students" style="width: 100%;">
              <el-option label="All students" :value="undefined" />
              <el-option
                v-for="student in allStudents"
                :key="student.id"
                :label="student.studentName"
                :value="student.id"
              />
            </el-select>
            <div class="pr-dialog__hint">Only use this for an exceptional one-on-one arrangement.</div>
          </el-form-item>
          <el-form-item label="Hourly Rate ($)">
            <el-input-number
              v-model="editingRate"
              :min="0"
              :precision="2"
              :step="1"
              :controls="true"
              style="width: 100%;"
            />
            <div class="pr-dialog__hint">
              Saved teacher payments keep their original amount. Unpaid lessons use the current matching rate.
            </div>
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="editDialogVisible = false">Cancel</el-button>
        <el-button
          type="primary"
          :loading="savingProductId !== null"
          @click="handleEditSave"
        >
          Save
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, Close, MoreFilled, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, ref } from 'vue';

import { fetchPayRates, savePayRate, deletePayRate, type TeacherPayRateSavePayload } from '@/api/finance';
import { fetchAllStudents, fetchAllTeachers, fetchCourseProducts } from '@/api/base-data';
import type { EntityId } from '@/types/domain';
import { useAppStore } from '@/stores/app';

const appStore = useAppStore();
const mobileSearchExpanded = ref(false);
const teacherKeyword = ref('');
const teachersLoading = ref(false);
const loading = ref(false);

const allTeachers = ref<{ id: EntityId; teacherName: string; teacherNo: string }[]>([]);
const allProducts = ref<{ id: EntityId; productCode: string; productName: string; subjectName: string }[]>([]);
const allStudents = ref<{ id: EntityId; studentName: string }[]>([]);

const selectedTeacherId = ref<EntityId | null>(null);

// Row type: a product with editable rate
interface RateRow {
  productId?: EntityId;
  productCode: string;
  productName: string;
  subjectName: string;
  rateId?: EntityId;
  hourlyRate: number;
  originalRate: number; // for change detection
  notes?: string;
  studentId?: EntityId;
  studentName?: string;
  gradeBand?: string;
  deliveryMode?: string;
}

interface RateGroup {
  key: string;
  productId?: EntityId;
  productName: string;
  subjectName?: string;
  effectiveRate?: number;
  baseRate?: RateRow;
  exceptions: RateRow[];
}

const defaultRate = ref<RateRow | null>(null);
const rateExceptionRows = ref<RateRow[]>([]);
const expandedExceptionGroups = ref(new Set<string>());
const savingProductId = ref<EntityId | null>(null);
const removingRateId = ref<EntityId | null>(null);



const editDialogVisible = ref(false);
const editingRow = ref<RateRow | null>(null);
const editingProductId = ref<EntityId | undefined>();
const editingRate = ref(0);
const editingStudentId = ref<EntityId | undefined>();
const editingGradeBand = ref<string | undefined>();
const editingDeliveryMode = ref<string | undefined>();
const addingScopedRate = ref(false);
const standardRateDialog = ref(false);
const productDefaultDialog = ref(false);

const gradeScopeOptions = ['K-5', '6-8', '9-12', 'AP Level'];

function openEditDialog(row: RateRow) {
  addingScopedRate.value = false;
  standardRateDialog.value = false;
  productDefaultDialog.value = false;
  editingRow.value = row;
  editingProductId.value = row.productId;
  editingRate.value = row.hourlyRate;
  editingStudentId.value = row.studentId;
  editingGradeBand.value = row.gradeBand || undefined;
  editingDeliveryMode.value = row.deliveryMode || undefined;
  editDialogVisible.value = true;
}

function openStandardRateDialog() {
  addingScopedRate.value = true;
  standardRateDialog.value = true;
  productDefaultDialog.value = false;
  editingRow.value = defaultRate.value ?? {
    productId: undefined,
    productCode: '',
    productName: '',
    subjectName: '',
    hourlyRate: 0,
    originalRate: 0,
  };
  editingProductId.value = undefined;
  editingRate.value = defaultRate.value?.hourlyRate ?? 0;
  editingStudentId.value = undefined;
  editingGradeBand.value = undefined;
  editingDeliveryMode.value = undefined;
  editDialogVisible.value = true;
}

function openAddExceptionDialog(productId?: EntityId) {
  addingScopedRate.value = true;
  standardRateDialog.value = false;
  productDefaultDialog.value = false;
  editingRow.value = {
    productId,
    productCode: '',
    productName: '',
    subjectName: '',
    hourlyRate: 0,
    originalRate: 0,
  };
  editingProductId.value = productId;
  editingRate.value = 0;
  editingStudentId.value = undefined;
  editingGradeBand.value = undefined;
  editingDeliveryMode.value = undefined;
  editDialogVisible.value = true;
}

function openProductRateDialog(group: RateGroup) {
  addingScopedRate.value = true;
  standardRateDialog.value = false;
  productDefaultDialog.value = true;
  editingRow.value = {
    productId: group.productId,
    productCode: '',
    productName: group.productName,
    subjectName: group.subjectName || '',
    hourlyRate: group.effectiveRate || 0,
    originalRate: group.effectiveRate || 0,
  };
  editingProductId.value = group.productId;
  editingRate.value = group.effectiveRate || 0;
  editingStudentId.value = undefined;
  editingGradeBand.value = undefined;
  editingDeliveryMode.value = undefined;
  editDialogVisible.value = true;
}

async function handleEditSave() {
  if (!editingRow.value || !selectedTeacherId.value) return;
  const row = editingRow.value;
  const newRate = editingRate.value;
  const productId = addingScopedRate.value ? editingProductId.value : row.productId;
  savingProductId.value = productId ?? 0;
  try {
    const payload: TeacherPayRateSavePayload = {
      teacherId: selectedTeacherId.value,
      courseProductId: productId,
      studentId: editingStudentId.value,
      gradeBand: editingGradeBand.value?.trim() || undefined,
      deliveryMode: editingDeliveryMode.value || undefined,
      hourlyRate: newRate,
      notes: undefined,
    };
    await savePayRate(payload);
    await loadRates();
    editDialogVisible.value = false;
    ElMessage.success(standardRateDialog.value ? 'Standard rate saved.' : 'Rate exception saved.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to save rate.');
  } finally {
    savingProductId.value = null;
  }
}

async function handleDeleteSpecialRate(rate: RateRow) {
  if (!rate.rateId) return;
  removingRateId.value = rate.rateId;
  try {
    await deletePayRate(rate.rateId);
    await loadRates();
    ElMessage.success('Special rate deleted.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to delete rate.');
  } finally {
    removingRateId.value = null;
  }
}

const filteredTeachers = computed(() => {
  const kw = teacherKeyword.value.toLowerCase().trim();
  if (!kw) return allTeachers.value;
  return allTeachers.value.filter(t =>
    t.teacherName.toLowerCase().includes(kw) ||
    t.teacherNo.toLowerCase().includes(kw),
  );
});

const selectedTeacher = computed(() =>
  allTeachers.value.find(t => t.id === selectedTeacherId.value),
);

const rateGroups = computed<RateGroup[]>(() => {
  const courseDefaults = new Map<EntityId, RateRow>();
  const exceptionsByProduct = new Map<string, RateRow[]>();

  rateExceptionRows.value.forEach((rate) => {
    const isProductDefault = rate.productId != null
      && rate.studentId == null
      && !rate.gradeBand
      && !rate.deliveryMode;
    if (isProductDefault) {
      courseDefaults.set(rate.productId, rate);
      return;
    }
    const groupKey = rate.productId == null ? 'all-courses' : String(rate.productId);
    const exceptions = exceptionsByProduct.get(groupKey) ?? [];
    exceptions.push(rate);
    exceptionsByProduct.set(groupKey, exceptions);
  });

  const productRateRows = allProducts.value.map((product): RateGroup => {
    const baseRate = courseDefaults.get(product.id);
    return {
      key: String(product.id),
      productId: product.id,
      productName: product.productName,
      subjectName: product.subjectName,
      effectiveRate: baseRate?.hourlyRate ?? defaultRate.value?.hourlyRate,
      baseRate,
      exceptions: exceptionsByProduct.get(String(product.id)) ?? [],
    };
  });

  const allCoursesGroup: RateGroup = {
    key: 'all-courses',
    productName: 'All courses',
    subjectName: 'Teacher-wide rate',
    effectiveRate: defaultRate.value?.hourlyRate,
    baseRate: defaultRate.value ?? undefined,
    exceptions: exceptionsByProduct.get('all-courses') ?? [],
  };

  return [allCoursesGroup, ...productRateRows];
});

async function selectTeacher(id: EntityId) {
  selectedTeacherId.value = id;
  await loadRates();
}

async function loadRates() {
  if (!selectedTeacherId.value) return;
  loading.value = true;
  try {

    const [ratesRes] = await Promise.all([
      fetchPayRates(selectedTeacherId.value),
    ]);
    const existingRates = ratesRes.data.data;


    const toRow = (rate: any): RateRow => ({
      productId: rate.courseProductId,
      productCode: rate.productCode || '',
      productName: rate.productName || '',
      subjectName: rate.subjectName || '',
      rateId: rate.id,
      hourlyRate: rate.hourlyRate,
      originalRate: rate.hourlyRate,
      notes: rate.notes,
      studentId: rate.studentId,
      studentName: rate.studentName,
      gradeBand: rate.gradeBand,
      deliveryMode: rate.deliveryMode,
    });
    const teacherWideDefault = existingRates.find(rate =>
      rate.courseProductId == null
        && rate.studentId == null
        && !rate.gradeBand
        && !rate.deliveryMode,
    );
    defaultRate.value = teacherWideDefault ? toRow(teacherWideDefault) : null;
    rateExceptionRows.value = existingRates
      .filter(rate => rate.id !== teacherWideDefault?.id)
      .map(toRow);
  } catch {
    ElMessage.error('Failed to load pay rates.');
  } finally {
    loading.value = false;
  }
}

function formatDelivery(value?: string) {
  if (value === 'ONLINE') return 'Online';
  if (value === 'OFFLINE') return 'In person';
  return 'Any delivery';
}

function effectiveRateSource(group: RateGroup) {
  if (group.key === 'all-courses') return defaultRate.value ? 'Teacher standard' : 'Not set';
  if (group.baseRate) return 'Course-specific rate';
  return defaultRate.value ? 'Uses teacher standard' : 'Rate not set';
}

function specialRateScope(rate: RateRow) {
  return [
    rate.gradeBand || 'Any grade',
    formatDelivery(rate.deliveryMode),
    rate.studentName || (rate.studentId ? 'Selected student' : 'All students'),
  ].join(' · ');
}

function isExceptionListExpanded(groupKey: string) {
  return expandedExceptionGroups.value.has(groupKey);
}

function toggleExceptionList(groupKey: string) {
  const next = new Set(expandedExceptionGroups.value);
  if (next.has(groupKey)) next.delete(groupKey);
  else next.add(groupKey);
  expandedExceptionGroups.value = next;
}

function scopeSummary(row: RateRow) {
  return [row.gradeBand || 'Any grade', formatDelivery(row.deliveryMode)].join(' · ');
}

function formatMoney(value?: number) {
  return value && value > 0 ? `$${Number(value).toFixed(2)}` : '—';
}



onMounted(async () => {
  teachersLoading.value = true;
  try {
    const [teachersRes, productsRes, studentsRes] = await Promise.all([
      fetchAllTeachers(),
      fetchCourseProducts({ pageSize: 200 }),
      fetchAllStudents(),
    ]);
    allTeachers.value = teachersRes.data.data;
    allProducts.value = productsRes.data.data.records.map((p: any) => ({
      id: p.id,
      productCode: p.productCode,
      productName: p.productName,
      subjectName: p.subjectName || '',
    }));
    allStudents.value = studentsRes.data.data.map(student => ({ id: student.id, studentName: student.studentName }));
  } catch {
    ElMessage.error('Failed to load initial data.');
  } finally {
    teachersLoading.value = false;
  }
});
</script>

<style scoped>
.pr-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - 92px);
  min-height: 0;
  overflow: hidden;
}

/* ─── Sidebar ────────────────────────────────────────── */

.pr-sidebar {
  flex-shrink: 0;
  width: 220px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pr-sidebar__search {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
}

.pr-sidebar__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 4px 0;
}

.pr-sidebar__item {
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  border-left: 3px solid transparent;
}

.pr-sidebar__item:hover {
  background: #f5f7fa;
}

.pr-sidebar__item--active {
  background: #ecf5ff;
  border-left-color: #409eff;
}

.pr-sidebar__name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.pr-sidebar__meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.pr-sidebar__empty {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* ─── Content ────────────────────────────────────────── */

.pr-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
  overflow: auto;
  overscroll-behavior: contain;
}

.pr-content__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #909399;
  font-size: 14px;
}

.pr-content__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.pr-content__title {
  font-size: 16px;
  margin-bottom: 4px;
}

.pr-content__no {
  color: #909399;
  font-size: 13px;
  margin-left: 8px;
  font-weight: normal;
}

.pr-content__hint {
  font-size: 12px;
  color: #909399;
}

.pr-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.pr-summary span {
  border: 1px solid #d8e0ea;
  border-radius: 6px;
  padding: 5px 8px;
  color: #606266;
  font-size: 12px;
  line-height: 1;
  background: #fafcff;
}

.pr-summary strong {
  color: #303133;
}

.pr-rule-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.pr-rule-step {
  border: 1px solid #dfe7f0;
  border-radius: 8px;
  padding: 10px;
  background: #fbfdff;
}

.pr-rule-step small,
.pr-rule-step span {
  display: block;
  color: #909399;
  font-size: 12px;
  line-height: 1.35;
}

.pr-rule-step strong {
  display: block;
  margin: 3px 0;
  color: #303133;
  font-size: 13px;
}

.pr-section {
  display: grid;
  gap: 10px;
}

.pr-section__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.pr-section__heading strong,
.pr-section__heading span {
  display: block;
}

.pr-section__heading strong {
  color: #303133;
  font-size: 13px;
}

.pr-section__heading span {
  margin-top: 2px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.pr-section__heading--spaced {
  margin-top: 8px;
}

.pr-section__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

/* ─── Table ──────────────────────────────────────────── */

.pr-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.pr-nested-rate {
  padding: 10px 12px;
  background: #f8fafc;
}

.pr-nested-rate__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #606266;
  font-size: 12px;
}

.pr-nested-rate__table {
  border: 1px solid #e6ebf1;
  border-radius: 4px;
  overflow: hidden;
}

.pr-product-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pr-product-cell__name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.pr-product-cell__subject {
  font-size: 12px;
  color: #909399;
}

/* ─── Info icon in header ─────────────────────────────── */

.pr-header-with-info {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.pr-info-icon {
  font-size: 14px;
  color: #909399;
  cursor: help;
}

/* ─── Rate display ────────────────────────────────────── */

.pr-rate--set {
  font-weight: 600;
  color: #303133;
}

.pr-rate--unset {
  color: #c0c4cc;
}

.pr-muted {
  color: #909399;
  font-size: 12px;
}

.pr-mobile-rate-list { display: none; }

/* ─── Edit dialog ─────────────────────────────────────── */

.pr-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
}

.pr-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.pr-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.pr-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.pr-dialog__product {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.pr-dialog__product strong {
  font-size: 14px;
  color: #303133;
}

.pr-dialog__product span {
  font-size: 12px;
  color: #909399;
}

.pr-dialog__form {
  margin-bottom: 0;
}

.pr-scope-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.pr-dialog__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

@media (max-width: 980px) {
  .pr-page,
  .pr-content__header {
    flex-direction: column;
  }

  .pr-sidebar {
    width: 100%;
    max-height: 240px;
  }

  .pr-summary {
    justify-content: flex-start;
  }

  .pr-section__actions {
    justify-content: flex-start;
  }

  .pr-rule-strip {
    grid-template-columns: 1fr;
  }

  .pr-scope-grid {
    grid-template-columns: 1fr;
  }
}

:global(html.mobile-layout) & {
  .pr-page { height: calc(100dvh - 176px); min-height: 0; align-items: stretch; gap: 10px; overflow: visible; }
  .pr-sidebar { height: 100%; min-height: 0; max-height: none; border-color: #d5deea; border-radius: 10px; box-shadow: 0 2px 8px rgb(33 53 77 / 5%); }
  .pr-sidebar--mobile-hidden,
  .pr-content--mobile-hidden { display: none; }
  .pr-sidebar__list { flex: 1; min-height: 0; max-height: none; overflow-y: auto; overscroll-behavior: contain; }
  .pr-mobile-list-header { display:flex; align-items:center; justify-content:space-between; gap:10px; min-height:58px; padding:9px 12px; border-bottom:1px solid #e1e8f0; background:#f8fafc; }
  .pr-mobile-list-header > div { display:grid; gap:2px; }
  .pr-mobile-list-header strong { color:#27384b; font-size:14px; }
  .pr-mobile-list-header span { color:#8190a2; font-size:11px; }
  .pr-mobile-list-header .mobile-search-trigger { flex:none; }
  .pr-sidebar__item { min-height:58px; padding:10px 14px; border-bottom:1px solid #e7edf3; border-left:3px solid transparent; }
  .pr-content { width: 100%; min-width: 0; overflow: visible; }
  .pr-table { display: none; }
  .pr-mobile-rate-list { display: grid; gap: 10px; }
  .pr-mobile-rate-card { padding: 12px; border: 1px solid #c9d9e2; border-radius: 10px; background: #fff; box-shadow: 0 2px 8px rgba(31, 63, 54, .05); }
  .pr-mobile-rate-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
  .pr-mobile-rate-card__header > div { min-width: 0; display: grid; gap: 3px; }
  .pr-mobile-rate-card__header strong:first-child { overflow: hidden; color: #1f3f36; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
  .pr-mobile-rate-card__header span,
  .pr-mobile-rate-card__meta { color: #71808a; font-size: 11px; }
  .pr-mobile-rate-card__meta { margin-top: 8px; }
  .pr-mobile-rate-card__actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; padding-top: 10px; border-top: 1px solid #edf1f4; }
  .pr-mobile-exceptions { display: grid; gap: 6px; margin-top: 10px; padding-top: 10px; border-top: 1px solid #edf1f4; }
  .pr-mobile-exception { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px; border-radius: 7px; background: #f5f8fa; }
  .pr-mobile-exception > div { min-width: 0; display: grid; gap: 2px; }
  .pr-mobile-exception strong { overflow: hidden; color: #40535d; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
  .pr-mobile-exception span { color: #1f6b4d; font-size: 12px; font-weight: 600; }
  .pr-mobile-selection { display: flex; align-items: center; gap: 10px; min-height: 44px; margin: 0 0 10px; padding: 4px 8px; border: 1px solid #e1e8f0; border-radius: 10px; background: #fff; }
  .pr-mobile-selection strong { overflow: hidden; color: #1f2937; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
  .pr-mobile-selection small { color: #8491a3; font-size: 11px; }
  .pr-content { padding: 12px; }
  .pr-content__header { gap: 8px; margin-bottom: 10px; }
  .pr-content__header > div:first-child { display: none; }
  .pr-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; justify-content: stretch; }
  .pr-summary span { min-width: 0; padding: 8px; line-height: 1.3; }
  .pr-rule-strip { display: none; }
  .pr-mobile-rule-guide { margin: 0 0 12px; border: 1px solid #e2e9f1; border-radius: 8px; background: #f8fafc; color: #536273; }
  .pr-mobile-rule-guide summary { padding: 9px 11px; color: #3f5267; font-size: 12px; font-weight: 600; cursor: pointer; }
  .pr-mobile-rule-guide p { margin: 0; padding: 0 11px 10px; font-size: 12px; line-height: 1.5; }
  .pr-section { gap: 8px; }
  .pr-section__heading { display: grid; gap: 8px; }
  .pr-section__heading strong { font-size: 15px; }
  .pr-section__heading > div:first-child > span { display: none; }
  .pr-section__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; }
  .pr-section__actions .el-button { min-width: 0; min-height: 36px; margin: 0; padding: 0 7px; font-size: 12px; }
  .mobile-rate-list { display: grid; gap: 8px; min-width: 0; }
  .mobile-rate-card { min-width: 0; padding: 11px; border: 1px solid #dce5ef; border-radius: 9px; background: #fff; }
  .mobile-rate-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
  .mobile-rate-card__product { display: grid; gap: 3px; min-width: 0; }
  .mobile-rate-card__product strong { overflow-wrap: anywhere; color: #27384b; font-size: 13px; line-height: 1.35; }
  .mobile-rate-card__product small { color: #8793a1; font-size: 11px; }
  .mobile-rate-card__header .el-tag { flex: none; }
  .mobile-rate-card__effective { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 9px; padding: 9px; border-radius: 7px; background: #f4f7fb; }
  .mobile-rate-card__effective > div { display: grid; gap: 2px; }
  .mobile-rate-card__effective small { color: #758397; font-size: 10px; }
  .mobile-rate-card__effective strong { color: #223b57; font-size: 17px; line-height: 1.2; }
  .mobile-rate-card__effective strong span { margin-left: 3px; color: #748397; font-size: 11px; font-weight: 500; }
  .mobile-rate-card__source { max-width: 45%; color: #55708d; font-size: 10px; text-align: right; }
  .mobile-rate-card__actions { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; margin-top: 7px; }
  .mobile-rate-card__actions .el-button { min-height: 30px; margin: 0; padding: 0 8px; font-size: 11px; }
  .mobile-rate-card__actions :deep(.el-icon) { margin-left: 3px; transition: transform .15s ease; }
  .mobile-rate-card__chevron--expanded { transform: rotate(180deg); }
  .mobile-special-rates { margin-top: 9px; padding-top: 9px; border-top: 1px solid #e9eef4; }
  .mobile-special-rates__heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; color: #42556b; font-size: 12px; }
  .mobile-special-rates__heading .el-button { min-height: 28px; margin: 0; }
  .mobile-special-rate-list { display: grid; gap: 6px; }
  .mobile-special-rate { padding: 8px; border-left: 3px solid #9bb8d8; border-radius: 5px; background: #f8fafc; }
  .mobile-special-rate__summary { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
  .mobile-special-rate__summary > div { display: grid; gap: 3px; min-width: 0; }
  .mobile-special-rate__summary strong { color: #27384b; font-size: 13px; }
  .mobile-special-rate__summary strong span { color: #7d8999; font-size: 10px; font-weight: 400; }
  .mobile-special-rate__summary small { overflow-wrap: anywhere; color: #758397; font-size: 10px; line-height: 1.4; }
  .mobile-special-rate__summary .el-button { min-height: 28px; margin: 0; }
  .mobile-special-rate__scopes { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
  .mobile-special-rate__scopes span { padding: 2px 6px; border-radius: 4px; background: #eaf0f7; color: #53677e; font-size: 10px; }
  .mobile-special-rates__empty { margin: 0; color: #8995a3; font-size: 11px; line-height: 1.45; }
  .pr-dialog { max-width: calc(100vw - 24px); }
  .pr-dialog :deep(.el-dialog__body) { padding: 12px; }
  .pr-dialog :deep(.el-dialog__header),
  .pr-dialog :deep(.el-dialog__footer) { padding-right: 14px; padding-left: 14px; }
}
</style>
