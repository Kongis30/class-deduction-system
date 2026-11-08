<template>
  <div class="tp-view">
    <section class="table-page-header">
      <div>
        <span class="table-page-header__eyebrow">Finance</span>
        <h2>Teacher payroll</h2>
        <p class="tp-page-description">Choose a default period for unpaid lessons. Set a separate period for an individual teacher when needed.</p>
      </div>
    </section>

    <section class="tp-section tp-section--filters">
      <div class="tp-filters">
        <div class="tp-filters__group">
          <label class="tp-filters__label">Period</label>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="–"
            start-placeholder="Start"
            end-placeholder="End"
            value-format="YYYY-MM-DD"
            class="tp-period-picker"
            style="width: 260px;"
          />
        </div>
        <div class="tp-filters__actions">
          <el-button type="primary" :loading="unpaidLoading" @click="loadUnpaidLessons">
            Load Unpaid Lessons
          </el-button>
        </div>
      </div>
    </section>

    <div class="tp-page">
      <aside class="tp-sidebar" :class="{ 'tp-sidebar--mobile-hidden': appStore.isMobileViewport && !mobileTeacherListVisible }">
        <div class="tp-mobile-list-header">
          <div>
            <strong>Teachers by product</strong>
            <span v-if="teachersLoading">Loading…</span>
            <span v-else>{{ allTeachers.length }} teachers</span>
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
        <div v-show="!appStore.isMobileViewport || mobileSearchExpanded" class="tp-sidebar__search">
          <el-input v-model="teacherKeyword" placeholder="Search teachers or products…" clearable size="small" />
        </div>
        <nav class="tp-sidebar__list" v-loading="teachersLoading">
          <section v-for="group in filteredTeacherProductGroups" :key="group.key" class="tp-sidebar__group">
            <button type="button" class="tp-sidebar__group-toggle" :aria-expanded="isGroupOpen(group.key)" @click="toggleGroup(group.key)">
              <span>{{ group.productName }}</span>
              <span class="tp-sidebar__chevron" :class="{ 'tp-sidebar__chevron--open': isGroupOpen(group.key) }" aria-hidden="true">›</span>
            </button>
            <div v-show="isGroupOpen(group.key)">
            <button
              v-for="teacher in group.teachers"
              :key="`${group.key}-${teacher.id}`"
              type="button"
              class="tp-sidebar__item"
              :class="{ 'tp-sidebar__item--active': isTeacherSelected(teacher.id) }"
              @click="selectTeacher(teacher.id)"
            >
              <span class="tp-sidebar__name">{{ teacher.teacherName }}</span>
            </button>
            </div>
          </section>
          <div v-if="!teachersLoading && filteredTeacherProductGroups.length === 0" class="tp-sidebar__empty">
            No teachers found.
          </div>
        </nav>
      </aside>

      <main v-loading="teacherSwitchLoading" class="tp-content" :class="{ 'tp-content--mobile-hidden': appStore.isMobileViewport && mobileTeacherListVisible }">
        <div v-if="appStore.isMobileViewport && !mobileTeacherListVisible" class="tp-mobile-selection">
          <el-button text @click="mobileTeacherListVisible = true">← Teacher list</el-button>
          <strong>{{ selectedTeacher?.teacherName || 'All teachers' }}</strong>
          <small v-if="selectedTeacher">{{ selectedTeacher.teacherNo }}</small>
        </div>
        <div class="tp-content__header">
          <div class="tp-content__title">
            <strong>{{ selectedTeacher?.teacherName || 'All teachers' }}</strong>
            <span v-if="selectedTeacher" class="tp-content__no">{{ selectedTeacher.teacherNo }}</span>
          </div>
        </div>

        <section v-if="selectedTeacher" class="tp-section tp-section--teacher-period">
          <div class="tp-teacher-period">
            <div class="tp-filters__group">
              <label class="tp-filters__label">Teacher period</label>
              <el-date-picker
                v-model="selectedTeacherPeriodRange"
                type="daterange"
                range-separator="–"
                start-placeholder="Start"
                end-placeholder="End"
                value-format="YYYY-MM-DD"
                class="tp-period-picker"
                style="width: 260px;"
                @change="handleSelectedTeacherPeriodChange"
              />
            </div>
            <el-button
              type="primary"
              :loading="teacherPeriodLoading"
              @click="loadSelectedTeacherUnpaidLessons"
            >
              Load this teacher's lessons
            </el-button>
          </div>
        </section>

        <el-alert
          v-if="missingRateCount > 0"
          type="warning"
          :closable="false"
          show-icon
          class="tp-rate-alert"
        >
          <template #title>
            {{ missingRateCount }} lesson(s) cannot be paid because the teacher pay rate is not configured.
          </template>
          <el-button text type="primary" @click="router.push('/admin/config/teacher-pay-rates')">
            Configure rates
          </el-button>
        </el-alert>

        <el-alert
          v-if="searched && unpaidLessons.length > 0 && !selectedTeacherId"
          type="info"
          :closable="false"
          class="tp-rate-alert"
        >
          All teachers' lessons are loaded. Choose one teacher from the left list to select lessons and create that teacher's payment.
        </el-alert>

        <!-- ─── 2. Unpaid 1:1 Lessons ────────────────── -->
        <section v-if="individualLessons.length > 0" class="tp-section tp-section--card">
          <div class="tp-section__header">
            <span class="tp-section__title">1:1 Lessons</span>
            <span v-if="individualTotal" class="tp-section__subtitle">
              {{ individualLessons.length }} lesson(s) · {{ individualTotal }}
            </span>
          </div>

          <el-table
            v-if="!appStore.isMobileViewport"
            ref="individualLessonTable"
            v-loading="unpaidLoading"
            :data="individualLessons"
            row-key="lessonRecordId"
            class="tp-table"
            @selection-change="individualSelections = $event; updateMergedSelection()"
          >
            <el-table-column type="selection" width="36" :selectable="canSelectLesson" />
            <el-table-column label="Date" min-width="200" class-name="tp-table__nowrap">
              <template #default="{ row }">{{ formatLessonSchedule(row) }}</template>
            </el-table-column>
            <el-table-column v-if="!selectedTeacherId" label="Teacher" min-width="140" prop="teacherName" />
            <el-table-column label="Type" width="80" align="center" header-class-name="tp-table__nowrap">
              <template #default="{ row }">
                <el-tag size="small" type="primary">1:1</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Student" min-width="150">
              <template #default="{ row }">
                <span>{{ row.participantNames?.length ? row.participantNames.join(' / ') : row.studentName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Product" min-width="120" prop="productName" show-overflow-tooltip />
            <el-table-column label="Hours" width="80" align="center" prop="hours" header-class-name="tp-table__nowrap" />
            <el-table-column label="Rate" width="90" align="right">
              <template #default="{ row }">
                <span v-if="hasPayRate(row)">${{ row.hourlyRate.toFixed(2) }}</span>
                <el-tag v-else type="danger" size="small">Not set</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Amount" width="100" align="right">
              <template #default="{ row }">
                <strong>${{ row.amount?.toFixed(2) }}</strong>
              </template>
            </el-table-column>
          </el-table>
          <div class="tp-mobile-lesson-list">
            <article v-for="row in individualLessons" :key="`mobile-${row.lessonRecordId}`" class="tp-mobile-lesson-card">
              <div class="tp-mobile-lesson-card__header">
                <el-checkbox :model-value="isLessonSelected(row)" :disabled="!canSelectLesson(row)" :aria-label="`Select ${row.studentName}`" @change="toggleLessonSelection(row, Boolean($event))" />
                <div class="tp-mobile-lesson-card__identity">
                  <strong>{{ row.participantNames?.length ? row.participantNames.join(' / ') : row.studentName }}</strong>
                  <span>{{ formatLessonSchedule(row) }} · 1:1 · {{ row.teacherName }}</span>
                </div>
              </div>
              <dl class="tp-mobile-lesson-card__details">
                <div><dt>Product</dt><dd>{{ row.productName || '—' }}</dd></div>
                <div><dt>Hours</dt><dd>{{ row.hours }}</dd></div>
                <div><dt>Rate</dt><dd v-if="hasPayRate(row)">${{ row.hourlyRate.toFixed(2) }}/h</dd><dd v-else class="tp-mobile-lesson-card__missing">Not set</dd></div>
                <div><dt>Amount</dt><dd>${{ row.amount?.toFixed(2) }}</dd></div>
              </dl>
            </article>
          </div>
        </section>

        <!-- ─── 3. Unpaid Group Class Lessons ─────────── -->
        <section v-if="groupLessons.length > 0" class="tp-section tp-section--card">
          <div class="tp-section__header">
            <span class="tp-section__title">Group Class Lessons</span>
            <span v-if="groupTotal" class="tp-section__subtitle">
              {{ groupLessons.length }} lesson(s) · {{ groupTotal }}
            </span>
          </div>

          <el-table
            v-if="!appStore.isMobileViewport"
            ref="groupLessonTable"
            v-loading="unpaidLoading"
            :data="groupLessons"
            row-key="lessonRecordId"
            class="tp-table"
            @selection-change="groupSelections = $event; updateMergedSelection()"
          >
            <el-table-column type="selection" width="36" :selectable="canSelectLesson" />
            <el-table-column label="Date" min-width="200" class-name="tp-table__nowrap">
              <template #default="{ row }">{{ formatLessonSchedule(row) }}</template>
            </el-table-column>
            <el-table-column v-if="!selectedTeacherId" label="Teacher" min-width="140" prop="teacherName" />
            <el-table-column label="Type" width="80" align="center" header-class-name="tp-table__nowrap">
              <template #default="{ row }">
                <el-tag size="small" type="warning">G</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Class" min-width="120" prop="className" show-overflow-tooltip />
            <el-table-column label="Product" min-width="120" prop="productName" show-overflow-tooltip />
            <el-table-column label="Hours" width="80" align="center" prop="hours" header-class-name="tp-table__nowrap" />
            <el-table-column label="Rate" width="90" align="right">
              <template #default="{ row }">
                <span v-if="hasPayRate(row)">${{ row.hourlyRate.toFixed(2) }}</span>
                <el-tag v-else type="danger" size="small">Not set</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Amount" width="100" align="right">
              <template #default="{ row }">
                <strong>${{ row.amount?.toFixed(2) }}</strong>
              </template>
            </el-table-column>
          </el-table>
          <div class="tp-mobile-lesson-list">
            <article v-for="row in groupLessons" :key="`mobile-${row.lessonRecordId}`" class="tp-mobile-lesson-card">
              <div class="tp-mobile-lesson-card__header">
                <el-checkbox :model-value="isLessonSelected(row)" :disabled="!canSelectLesson(row)" :aria-label="`Select ${row.className || row.productName}`" @change="toggleLessonSelection(row, Boolean($event))" />
                <div class="tp-mobile-lesson-card__identity">
                  <strong>{{ row.className || row.productName || 'Group class' }}</strong>
                  <span>{{ formatLessonSchedule(row) }} · Group · {{ row.teacherName }}</span>
                </div>
              </div>
              <dl class="tp-mobile-lesson-card__details">
                <div><dt>Product</dt><dd>{{ row.productName || '—' }}</dd></div>
                <div><dt>Hours</dt><dd>{{ row.hours }}</dd></div>
                <div><dt>Rate</dt><dd v-if="hasPayRate(row)">${{ row.hourlyRate.toFixed(2) }}/h</dd><dd v-else class="tp-mobile-lesson-card__missing">Not set</dd></div>
                <div><dt>Amount</dt><dd>${{ row.amount?.toFixed(2) }}</dd></div>
              </dl>
            </article>
          </div>
        </section>

        <!-- ─── 4. Summary & Create Payment ────────────── -->
        <section v-if="selectedIds.length > 0" class="tp-section tp-section--card">
          <div class="tp-summary">
            <span>
              Selected <strong>{{ selectedIds.length }}</strong> lessons ·
              Hours: <strong>{{ selectedTotalHours }}</strong> ·
              Amount: <strong>${{ selectedTotalAmount }}</strong>
            </span>
            <el-button
              type="primary"
              :loading="creatingPayment"
              @click="handleCreatePayment"
            >
              {{ appStore.isMobileViewport ? 'Create payment' : 'Pay' }}
            </el-button>
          </div>
        </section>

        <el-empty
          v-else-if="searched && !unpaidLoading && individualLessons.length === 0 && groupLessons.length === 0"
          :description="'No unpaid lessons found for this period.'"
          class="tp-empty"
        />

        <!-- ─── 6. Payment History ─────────────────────── -->
        <section class="tp-section tp-section--card" style="margin-top: 4px;">
          <div class="tp-section__header">
            <div class="tp-section__header-left">
              <span class="tp-section__title">Payment History</span>
              <span class="tp-section__subtitle">{{ pagination.total }} record(s)</span>
            </div>
            <div class="tp-history-filters">
              <el-select
                v-model="historyFilter.status"
                placeholder="Status"
                clearable
                style="width: 120px;"
                @change="loadHistory(1)"
              >
                <el-option label="Pending" value="PENDING" />
                <el-option label="Paid" value="PAID" />
              </el-select>
            </div>
          </div>

          <el-table
            v-if="!appStore.isMobileViewport"
            v-loading="historyLoading"
            :data="historyData"
            row-key="id"
            class="tp-table"
          >
            <el-table-column label="Payment No" min-width="140" prop="paymentNo" />
            <el-table-column v-if="!selectedTeacherId" label="Teacher" min-width="140" prop="teacherName" />
            <el-table-column label="Period" min-width="155">
              <template #default="{ row }">
                {{ row.periodStart }} – {{ row.periodEnd }}
              </template>
            </el-table-column>
            <el-table-column label="Hours" width="80" align="center" prop="totalHours" header-class-name="tp-table__nowrap" />
            <el-table-column label="Amount" width="110" align="right">
              <template #default="{ row }">${{ row.totalAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Status" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PAID' ? 'success' : 'warning'" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Paid At" min-width="140">
              <template #default="{ row }">
                {{ row.paidAt ? formatDateTime(row.paidAt) : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="155" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="viewDetail(row)">Detail</el-button>
                <el-button
                  v-if="row.status === 'PENDING'"
                  text
                  type="success"
                  size="small"
                  @click="handleConfirm(row)"
                >
                  Confirm
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="tp-mobile-history-list">
            <article v-for="row in historyData" :key="`mobile-history-${row.id}`" class="tp-mobile-history-card">
              <div class="tp-mobile-history-card__header">
                <div><strong>{{ row.paymentNo }}</strong><span>{{ row.teacherName }} · {{ row.periodStart }} – {{ row.periodEnd }}</span></div>
                <el-tag :type="row.status === 'PAID' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
              </div>
              <div class="tp-mobile-history-card__summary">
                <span>{{ row.totalHours }}h</span>
                <strong>${{ row.totalAmount?.toFixed(2) }}</strong>
              </div>
              <div class="tp-mobile-history-card__actions">
                <el-button text type="primary" class="tp-history-detail-button" @click="viewDetail(row)">Detail</el-button>
                <el-button v-if="row.status === 'PENDING'" text type="success" size="small" @click="handleConfirm(row)">Confirm</el-button>
              </div>
            </article>
          </div>

          <div v-if="pagination.total > pagination.size" class="tp-pagination">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              background
              :layout="appStore.isMobileViewport ? 'prev, pager, next' : 'prev, pager, next, sizes'"
              :page-sizes="[10, 20, 50]"
              :total="pagination.total"
              @current-change="loadHistory"
              @size-change="loadHistory"
            />
          </div>
        </section>

        <!-- ─── 7. Pending payment banner ────────────── -->
      </main>
    </div>


    <el-dialog
      v-model="detailVisible"
      :width="appStore.isMobileViewport ? 'calc(100vw - 24px)' : '750px'"
      :close-on-click-modal="false"
      class="tp-dialog"
    >
      <template #header>
        <h3>Payment Detail — {{ detailData?.paymentNo }}</h3>
      </template>

      <template v-if="detailData">
        <div class="tp-detail-summary">
          <div class="tp-detail-summary__item">
            <span class="label">Teacher</span>
            <span class="value">{{ detailData.teacherName }}</span>
          </div>
          <div class="tp-detail-summary__item">
            <span class="label">Period</span>
            <span class="value">{{ detailData.periodStart }} – {{ detailData.periodEnd }}</span>
          </div>
          <div class="tp-detail-summary__item">
            <span class="label">Total Hours</span>
            <span class="value">{{ detailData.totalHours }}</span>
          </div>
          <div class="tp-detail-summary__item">
            <span class="label">Total Amount</span>
            <span class="value">${{ detailData.totalAmount?.toFixed(2) }}</span>
          </div>
          <div class="tp-detail-summary__item">
            <span class="label">Status</span>
            <span class="value">
              <el-tag :type="detailData.status === 'PAID' ? 'success' : 'warning'" size="small">
                {{ detailData.status }}
              </el-tag>
            </span>
          </div>
          <div class="tp-detail-summary__item" v-if="detailData.paidAt">
            <span class="label">Paid At</span>
            <span class="value">{{ formatDateTime(detailData.paidAt) }}</span>
          </div>
        </div>

        <div v-if="detailData.items?.length" class="tp-detail-list-scroll">
          <section
            v-for="studentGroup in detailStudentGroups"
            :key="studentGroup.key"
            class="tp-detail-student-group"
          >
            <header class="tp-detail-student-group__header">
              <strong>{{ studentGroup.studentName }}</strong>
              <span>{{ studentGroup.items.length }} lesson(s) · {{ studentGroup.totalHours }}h · ${{ studentGroup.totalAmount.toFixed(2) }}</span>
            </header>
            <el-table
              v-if="!appStore.isMobileViewport"
              :data="studentGroup.items"
              row-key="id"
              class="tp-table tp-detail-table"
            >
              <el-table-column label="Type" width="72" align="center" header-class-name="tp-table__nowrap">
                <template #default="{ row }">
                  <el-tag v-if="row.className" size="small" type="warning">G</el-tag>
                  <el-tag v-else size="small" type="primary">1</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Package" width="145" prop="packageName" header-class-name="tp-table__nowrap" />
              <el-table-column label="Product" width="120" prop="productName" header-class-name="tp-table__nowrap" />
              <el-table-column label="Class" width="105" prop="className" header-class-name="tp-table__nowrap">
                <template #default="{ row }">{{ row.className || '—' }}</template>
              </el-table-column>
              <el-table-column label="Hours" width="75" align="center" prop="hours" header-class-name="tp-table__nowrap" />
              <el-table-column label="Rate" width="95" align="right" header-class-name="tp-table__nowrap">
                <template #default="{ row }">${{ row.hourlyRate?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="Amount" width="110" align="right" header-class-name="tp-table__nowrap">
                <template #default="{ row }">${{ row.amount?.toFixed(2) }}</template>
              </el-table-column>
            </el-table>
            <div v-else class="tp-mobile-detail-list">
              <article v-for="item in studentGroup.items" :key="item.id" class="tp-mobile-detail-card">
                <header>
                  <el-tag v-if="item.className" size="small" type="warning">Group</el-tag>
                  <el-tag v-else size="small" type="primary">1:1</el-tag>
                  <strong>{{ item.packageName || item.productName }}</strong>
                </header>
                <span v-if="item.className || item.productName" class="tp-mobile-detail-card__context">
                  {{ item.productName }}<template v-if="item.className"> · {{ item.className }}</template>
                </span>
                <div class="tp-mobile-lesson-card__metrics">
                  <div><small>Hours</small><strong>{{ item.hours }}</strong></div>
                  <div><small>Rate</small><strong>${{ item.hourlyRate?.toFixed(2) }}</strong></div>
                  <div><small>Amount</small><strong>${{ item.amount?.toFixed(2) }}</strong></div>
                </div>
              </article>
            </div>
          </section>
        </div>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">Close</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAppStore } from '@/stores/app';

import {
  confirmPayment,
  createPayment,
  fetchPaymentDetail,
  fetchPayments,
  fetchUnpaidLessons,
} from '@/api/finance';
import { fetchAllTeachers } from '@/api/base-data';
import { fetchTeacherCalendarCategories, type TeacherCalendarCategoryItem } from '@/api/schedule';
import type { EntityId, TeacherPaymentItem, UnpaidLessonItem } from '@/types/domain';

const router = useRouter();
const appStore = useAppStore();
const teachersLoading = ref(false);
const allTeachers = ref<{ id: EntityId; teacherName: string; teacherNo: string }[]>([]);
const teacherProductCategories = ref<TeacherCalendarCategoryItem[]>([]);
const teacherKeyword = ref('');
const productExpansionStorageKey = 'teacher-payroll-expanded-products';
function loadExpandedProducts(): Set<string> {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(productExpansionStorageKey) || '[]');
    return new Set(Array.isArray(saved) ? saved.filter((key): key is string => typeof key === 'string') : []);
  } catch {
    return new Set();
  }
}
const expandedProducts = ref(loadExpandedProducts());
const searchCollapsedProducts = ref(new Set<string>());
watch(() => Boolean(teacherKeyword.value.trim()), () => { searchCollapsedProducts.value = new Set(); });

function isGroupOpen(key: string) {
  return teacherKeyword.value.trim() ? !searchCollapsedProducts.value.has(key) : expandedProducts.value.has(key);
}

function toggleGroup(key: string) {
  if (teacherKeyword.value.trim()) {
    const collapsed = new Set(searchCollapsedProducts.value);
    collapsed.has(key) ? collapsed.delete(key) : collapsed.add(key);
    searchCollapsedProducts.value = collapsed;
    return;
  }
  const expanded = new Set(expandedProducts.value);
  expanded.has(key) ? expanded.delete(key) : expanded.add(key);
  expandedProducts.value = expanded;
  try {
    localStorage.setItem(productExpansionStorageKey, JSON.stringify([...expanded]));
  } catch {
    // Keep groups usable in the current page if browser storage is unavailable.
  }
}
const mobileSearchExpanded = ref(false);
const mobileTeacherListVisible = ref(true);
const selectedTeacherId = ref<EntityId | null>(null);
const teacherSwitchLoading = ref(false);
const teacherPeriodLoading = ref(false);
let teacherSwitchRequest = 0;
let teacherPeriodRequest = 0;

const selectedTeacher = computed(() =>
  allTeachers.value.find(t => String(t.id) === String(selectedTeacherId.value)),
);

interface TeacherProductGroup {
  key: string;
  productName: string;
  teachers: typeof allTeachers.value;
}

const teacherProductGroups = computed<TeacherProductGroup[]>(() => {
  const teachersById = new Map(allTeachers.value.map(teacher => [String(teacher.id), teacher]));
  const products = new Map<string, { productName: string; teacherIds: Set<string> }>();

  for (const category of teacherProductCategories.value) {
    if (category.productId == null) continue;
    const key = String(category.productId);
    const product = products.get(key) ?? {
      productName: category.productName?.trim() || category.categoryName,
      teacherIds: new Set<string>(),
    };
    product.teacherIds.add(String(category.teacherId));
    products.set(key, product);
  }

  const associatedTeacherIds = new Set<string>();
  const groups = [...products.entries()].map(([key, product]) => {
    const teachers = [...product.teacherIds]
      .map(id => teachersById.get(id))
      .filter((teacher): teacher is (typeof allTeachers.value)[number] => Boolean(teacher))
      .sort((left, right) => left.teacherName.localeCompare(right.teacherName));
    teachers.forEach(teacher => associatedTeacherIds.add(String(teacher.id)));
    return { key, productName: product.productName, teachers };
  });

  const teachersWithoutProduct = allTeachers.value
    .filter(teacher => !associatedTeacherIds.has(String(teacher.id)))
    .sort((left, right) => left.teacherName.localeCompare(right.teacherName));
  if (teachersWithoutProduct.length > 0) {
    groups.push({ key: 'no-active-product', productName: 'No active product', teachers: teachersWithoutProduct });
  }

  return groups.sort((left, right) => left.productName.localeCompare(right.productName));
});

const filteredTeacherProductGroups = computed(() => {
  const keyword = teacherKeyword.value.trim().toLocaleLowerCase();
  if (!keyword) return teacherProductGroups.value;
  return teacherProductGroups.value
    .map(group => ({
      ...group,
      teachers: group.teachers.filter(teacher =>
        teacher.teacherName.toLocaleLowerCase().includes(keyword)
        || teacher.teacherNo.toLocaleLowerCase().includes(keyword)
        || group.productName.toLocaleLowerCase().includes(keyword)),
    }))
    .filter(group => group.teachers.length > 0);
});

function isTeacherSelected(teacherId: EntityId) {
  return selectedTeacherId.value != null && String(selectedTeacherId.value) === String(teacherId);
}

async function selectTeacher(teacherId: EntityId) {
  const request = ++teacherSwitchRequest;
  teacherPeriodRequest += 1;
  teacherPeriodLoading.value = false;
  unpaidLoading.value = false;
  teacherSwitchLoading.value = true;
  const period = ensureTeacherPeriod(teacherId);
  try {
    const [history, unpaid] = await Promise.all([
      fetchPayments({
        teacherId,
        status: historyFilter.status,
        pageNum: 1,
        pageSize: pagination.size,
      }),
      searched.value
        ? fetchUnpaidLessons({ teacherId, startDate: period[0], endDate: period[1] })
        : Promise.resolve(null),
    ]);
    if (request !== teacherSwitchRequest) return;
    clearUnpaidSelections();
    selectedTeacherId.value = teacherId;
    historyData.value = history.data.data.records;
    pagination.page = 1;
    pagination.total = history.data.data.total;
    if (unpaid) {
      unpaidLessons.value = unpaid.data.data;
      selectedTeacherLoadedPeriod.value = { teacherId: String(teacherId), range: [...period] };
    } else {
      unpaidLessons.value = [];
      selectedTeacherLoadedPeriod.value = null;
    }
    mobileTeacherListVisible.value = false;
    mobileSearchExpanded.value = false;
  } catch {
    if (request === teacherSwitchRequest) ElMessage.error('Failed to load teacher payroll. Please try again.');
  } finally {
    if (request === teacherSwitchRequest) teacherSwitchLoading.value = false;
  }
}



const dateRange = ref<[string, string] | null>(null);
const teacherPeriodRanges = reactive<Record<string, [string, string]>>({});
const selectedTeacherPeriodRange = computed<[string, string] | null>({
  get() {
    if (selectedTeacherId.value == null) return dateRange.value;
    return teacherPeriodRanges[String(selectedTeacherId.value)] ?? dateRange.value;
  },
  set(range) {
    if (selectedTeacherId.value == null || !range) return;
    teacherPeriodRanges[String(selectedTeacherId.value)] = [...range];
  },
});

function ensureTeacherPeriod(teacherId: EntityId): [string, string] {
  const key = String(teacherId);
  if (!teacherPeriodRanges[key]) {
    teacherPeriodRanges[key] = dateRange.value ? [...dateRange.value] : currentMonthRange();
  }
  return teacherPeriodRanges[key];
}

function currentMonthRange(): [string, string] {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  return [`${y}-${m}-01`, `${y}-${m}-${String(lastDay).padStart(2, '0')}`];
}



const unpaidLoading = ref(false);
const searched = ref(false);
const unpaidLessons = ref<UnpaidLessonItem[]>([]);
const selectedTeacherLoadedPeriod = ref<{ teacherId: string; range: [string, string] } | null>(null);
const individualLessonTable = ref<{ clearSelection: () => void }>();
const groupLessonTable = ref<{ clearSelection: () => void }>();
const individualSelections = ref<UnpaidLessonItem[]>([]);
const groupSelections = ref<UnpaidLessonItem[]>([]);
const selectedIds = ref<EntityId[]>([]);

const visibleUnpaidLessons = computed(() => selectedTeacherId.value == null
  ? unpaidLessons.value
  : unpaidLessons.value.filter(row => String(row.teacherId) === String(selectedTeacherId.value)));

const individualLessons = computed(() =>
  visibleUnpaidLessons.value.filter(r => !r.className),
);

const groupLessons = computed(() =>
  visibleUnpaidLessons.value.filter(r => !!r.className),
);

const missingRateCount = computed(() =>
  visibleUnpaidLessons.value.filter(row => !hasPayRate(row)).length,
);

function hasPayRate(row: UnpaidLessonItem) {
  return typeof row.hourlyRate === 'number' && row.hourlyRate > 0;
}

function canSelectLesson(row: UnpaidLessonItem) {
  return hasPayRate(row)
    && selectedTeacherId.value != null
    && String(row.teacherId) === String(selectedTeacherId.value);
}

// Mobile cards and desktop tables share selections so payment submits the same lesson set.
function isLessonSelected(row: UnpaidLessonItem) {
  return selectedIds.value.some((id) => String(id) === String(row.lessonRecordId));
}

function toggleLessonSelection(row: UnpaidLessonItem, checked: boolean) {
  if (!canSelectLesson(row)) return;
  const source = row.className ? groupSelections : individualSelections;
  const next = source.value.filter((item) => String(item.lessonRecordId) !== String(row.lessonRecordId));
  if (checked) next.push(row);
  source.value = next;
  updateMergedSelection();
}

const individualTotal = computed(() => {
  if (!individualLessons.value.length) return '';
  const h = individualLessons.value.reduce((s, r) => s + r.hours, 0);
  const a = individualLessons.value.reduce((s, r) => s + r.amount, 0);
  return `${h.toFixed(1)}h · $${a.toFixed(2)}`;
});

const groupTotal = computed(() => {
  if (!groupLessons.value.length) return '';
  const h = groupLessons.value.reduce((s, r) => s + r.hours, 0);
  const a = groupLessons.value.reduce((s, r) => s + r.amount, 0);
  return `${h.toFixed(1)}h · $${a.toFixed(2)}`;
});

function updateMergedSelection() {
  const allRows = [...individualSelections.value, ...groupSelections.value];
  selectedIds.value = allRows.map(r => r.lessonRecordId);
}

function clearUnpaidSelections() {
  individualLessonTable.value?.clearSelection();
  groupLessonTable.value?.clearSelection();
  individualSelections.value = [];
  groupSelections.value = [];
  selectedIds.value = [];
}

const selectedTotalHours = computed(() => {
  const allRows = [...individualSelections.value, ...groupSelections.value];
  return allRows.reduce((s, r) => s + r.hours, 0).toFixed(1);
});

const selectedTotalAmount = computed(() => {
  const allRows = [...individualSelections.value, ...groupSelections.value];
  return allRows.reduce((s, r) => s + r.amount, 0).toFixed(2);
});

async function loadUnpaidLessons() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('Select a period before loading unpaid lessons.');
    return;
  }
  unpaidLoading.value = true;
  teacherPeriodRequest += 1;
  teacherPeriodLoading.value = false;
  searched.value = true;
  const requestedRange: [string, string] = [...dateRange.value];
  if (selectedTeacherId.value != null) {
    teacherPeriodRanges[String(selectedTeacherId.value)] = [...requestedRange];
  }
  clearUnpaidSelections();
  unpaidLessons.value = [];
  selectedTeacherLoadedPeriod.value = null;
  try {
    const res = await fetchUnpaidLessons({
      teacherId: selectedTeacherId.value ?? undefined,
      startDate: requestedRange[0],
      endDate: requestedRange[1],
    });
    unpaidLessons.value = res.data.data;
    selectedTeacherLoadedPeriod.value = selectedTeacherId.value == null
      ? null
      : { teacherId: String(selectedTeacherId.value), range: requestedRange };
  } catch {
    ElMessage.error('Failed to load unpaid lessons.');
  } finally {
    unpaidLoading.value = false;
  }
}

function handleSelectedTeacherPeriodChange() {
  teacherPeriodRequest += 1;
  teacherPeriodLoading.value = false;
  unpaidLoading.value = false;
  clearUnpaidSelections();
  unpaidLessons.value = [];
  selectedTeacherLoadedPeriod.value = null;
}

async function loadSelectedTeacherUnpaidLessons() {
  const teacherId = selectedTeacherId.value;
  const period = selectedTeacherPeriodRange.value;
  if (teacherId == null) return;
  if (!period?.[0] || !period[1]) {
    ElMessage.warning('Select a teacher period before loading lessons.');
    return;
  }

  const request = ++teacherPeriodRequest;
  const requestedRange: [string, string] = [...period];
  teacherPeriodLoading.value = true;
  unpaidLoading.value = true;
  searched.value = true;
  clearUnpaidSelections();
  unpaidLessons.value = [];
  selectedTeacherLoadedPeriod.value = null;
  try {
    const res = await fetchUnpaidLessons({
      teacherId,
      startDate: requestedRange[0],
      endDate: requestedRange[1],
    });
    if (request !== teacherPeriodRequest || String(selectedTeacherId.value) !== String(teacherId)) return;
    unpaidLessons.value = res.data.data;
    selectedTeacherLoadedPeriod.value = { teacherId: String(teacherId), range: requestedRange };
  } catch {
    if (request === teacherPeriodRequest) {
      unpaidLessons.value = [];
      selectedTeacherLoadedPeriod.value = null;
    ElMessage.error("Failed to load this teacher's unpaid lessons.");
    }
  } finally {
    if (request === teacherPeriodRequest) {
      teacherPeriodLoading.value = false;
      unpaidLoading.value = false;
    }
  }
}



const creatingPayment = ref(false);

async function handleCreatePayment() {
  if (!selectedTeacherId.value || selectedIds.value.length === 0) return;

  const allSelected = [...individualSelections.value, ...groupSelections.value];
  if (allSelected.some(row => !hasPayRate(row))) return;
  const paymentPeriod = selectedTeacherLoadedPeriod.value?.teacherId === String(selectedTeacherId.value)
    ? selectedTeacherLoadedPeriod.value.range : selectedTeacherPeriodRange.value;
  if (!paymentPeriod?.[0] || !paymentPeriod[1]) {
    ElMessage.warning('Load lessons for a teacher period before creating a payment.');
    return;
  }

  try {
    await ElMessageBox.confirm(
      `Pay for ${selectedIds.value.length} lesson(s) — total $${selectedTotalAmount.value}?`,
      'Confirm payment',
      { type: 'info' },
    );
  } catch {
    return;
  }

  creatingPayment.value = true;
  try {
    await createPayment({
      teacherId: selectedTeacherId.value,
      periodStart: paymentPeriod[0],
      periodEnd: paymentPeriod[1],
      lessonRecordIds: selectedIds.value,
    });
    ElMessage.success('Payment created.');
    await Promise.all([loadSelectedTeacherUnpaidLessons(), loadHistory(1)]);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to create payment.');
  } finally {
    creatingPayment.value = false;
  }
}



const historyFilter = reactive({ status: undefined as string | undefined });

const historyLoading = ref(false);
const historyData = ref<TeacherPaymentItem[]>([]);
const pagination = reactive({ page: 1, size: 10, total: 0 });

async function loadHistory(pageNum?: number) {
  if (pageNum) pagination.page = pageNum;
  historyLoading.value = true;
  try {
    const res = await fetchPayments({
      teacherId: selectedTeacherId.value ?? undefined,
      status: historyFilter.status,
      pageNum: pagination.page,
      pageSize: pagination.size,
    });
    const data = res.data.data;
    historyData.value = data.records;
    pagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load payment history.');
  } finally {
    historyLoading.value = false;
  }
}



const detailVisible = ref(false);
const detailData = ref<TeacherPaymentItem | null>(null);
const detailStudentGroups = computed(() => {
  const groups = new Map<string, {
    key: string;
    studentName: string;
    items: NonNullable<TeacherPaymentItem['items']>;
    totalHours: number;
    totalAmount: number;
  }>();
  for (const item of detailData.value?.items ?? []) {
    const studentName = item.studentName?.trim() || 'Student not available';
    const key = item.studentId != null ? `student:${item.studentId}` : `name:${studentName}`;
    let group = groups.get(key);
    if (!group) {
      group = { key, studentName, items: [], totalHours: 0, totalAmount: 0 };
      groups.set(key, group);
    }
    group.items.push(item);
    group.totalHours += item.hours || 0;
    group.totalAmount += item.amount || 0;
  }
  return [...groups.values()].sort((left, right) => left.studentName.localeCompare(right.studentName));
});

async function viewDetail(row: TeacherPaymentItem) {
  try {
    const res = await fetchPaymentDetail(row.id);
    detailData.value = res.data.data;
    detailVisible.value = true;
  } catch {
    ElMessage.error('Failed to load payment detail.');
  }
}



async function handleConfirm(row: TeacherPaymentItem) {
  try {
    await ElMessageBox.confirm(
      `Confirm payment "${row.paymentNo}" for $${row.totalAmount?.toFixed(2)}?`,
      'Confirm payment',
      { type: 'info' },
    );
    await confirmPayment(row.id);
    ElMessage.success('Payment confirmed.');
    loadHistory();
  } catch { /* cancelled */ }
}



function formatDateTime(dateStr: string) {
  if (!dateStr) return '—';
  return dateStr.substring(0, 19).replace('T', ' ');
}

type LessonTime = { year: number; month: number; day: number; hour: number; minute: number };

// Parse local lesson times without a timezone suffix to avoid shifting them in the browser.
function parseLessonTime(value: string | null): LessonTime | null {
  const parts = value?.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (!parts) return null;
  return {
    year: Number(parts[1]), month: Number(parts[2]), day: Number(parts[3]),
    hour: Number(parts[4]), minute: Number(parts[5]),
  };
}

function formatLessonClock(time: LessonTime, showPeriod: boolean) {
  const hour = time.hour % 12 || 12;
  const minute = time.minute ? `:${String(time.minute).padStart(2, '0')}` : '';
  return `${hour}${minute}${showPeriod ? (time.hour < 12 ? 'am' : 'pm') : ''}`;
}

function formatLessonSchedule(row: UnpaidLessonItem) {
  const start = parseLessonTime(row.lessonStartAt);
  if (!start) return '—';

  const date = `${start.year}/${start.month}/${start.day}`;
  const end = parseLessonTime(row.lessonEndAt);
  if (!end) return `${date} ${formatLessonClock(start, true)}`;

  const samePeriod = (start.hour < 12) === (end.hour < 12);
  const endDate = start.year === end.year && start.month === end.month && start.day === end.day
    ? '' : `${end.month}/${end.day} `;
  return `${date} ${formatLessonClock(start, !samePeriod)}–${endDate}${formatLessonClock(end, true)}`;
}



onMounted(async () => {
  dateRange.value = currentMonthRange();
  teachersLoading.value = true;
  try {
    const [teachersRes, categoriesRes] = await Promise.all([
      fetchAllTeachers(),
      fetchTeacherCalendarCategories().catch(() => null),
    ]);
    allTeachers.value = teachersRes.data.data;
    teacherProductCategories.value = categoriesRes?.data.data ?? [];
  } catch { /* silent */ }
  teachersLoading.value = false;
  loadHistory(1);
});
</script>

<style scoped>
.tp-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tp-page {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-width: 0;
}

/* Group teachers by their active schedules; a teacher may appear under multiple products. */
.tp-sidebar {
  display: flex;
  flex: 0 0 240px;
  flex-direction: column;
  height: clamp(450px, calc(100dvh - 80px), 850px);
  overflow: hidden;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
}

.tp-mobile-list-header {
  display: none;
}

.tp-sidebar__search {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
}

.tp-sidebar__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px;
}

.tp-sidebar__item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #334155;
  cursor: pointer;
  text-align: left;
}

.tp-sidebar__group {
  margin-top: 8px;
}

.tp-sidebar__group-toggle {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  border: 0;
  border-radius: 6px;
  background: #f5f8fc;
  color: #475569;
  cursor: pointer;
  text-align: left;
  font-size: 14px;
  font-weight: 600;
}

.tp-sidebar__chevron { flex: none; font-size: 20px; line-height: 1; transition: transform 0.15s; }
.tp-sidebar__chevron--open { transform: rotate(90deg); }

.tp-sidebar__item {
  padding: 9px 10px 9px 24px;
  font-size: 14px;
}

.tp-sidebar__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tp-sidebar__item:hover {
  background: #f2f6fb;
}

.tp-sidebar__item--active {
  background: #eaf3ff;
  color: #2468b4;
}

.tp-sidebar__empty {
  padding: 16px 10px;
  color: #909399;
  font-size: 12px;
  text-align: center;
}

.mobile-search-trigger {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border: 1px solid #dce5ef;
  border-radius: 6px;
  background: #fff;
  color: #64748b;
}

/* ─── Content ────────────────────────────────────────── */

.tp-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: visible;
}

.tp-content__header {
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  padding: 14px 20px;
}

.tp-content__title {
  font-size: 16px;
}

.tp-content__no {
  color: #909399;
  font-size: 13px;
  margin-left: 8px;
  font-weight: normal;
}

/* ─── Section ─────────────────────────────────────────── */

.tp-section {
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  padding: 14px 16px;
}

.tp-section--filters {
  padding: 12px 16px;
}

.tp-section--teacher-period {
  padding: 10px 14px;
}

.tp-teacher-period {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.tp-section--card {
  padding: 14px 16px;
}

.tp-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.tp-section__header-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.tp-section__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.tp-section__subtitle {
  font-size: 12px;
  color: #909399;
}

/* ─── Filters ─────────────────────────────────────────── */

.tp-filters {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}

.tp-filters__group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tp-filters__label {
  font-size: 12px;
  color: #909399;
}

.tp-filters__actions {
  display: flex;
  gap: 8px;
}

/* ─── History status filter ────────────────────────────── */

.tp-history-filters {
  display: flex;
  gap: 8px;
}

/* ─── Table ───────────────────────────────────────────── */

.tp-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.tp-table :deep(.tp-table__nowrap .cell) {
  white-space: nowrap;
}

/* ─── Summary bar ─────────────────────────────────────── */

.tp-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding: 8px 14px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}

/* ─── Pagination ──────────────────────────────────────── */

.tp-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

/* ─── Empty ───────────────────────────────────────────── */

.tp-empty {
  border: 1px dashed #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 30px 0;
}

.tp-mobile-lesson-list,
.tp-mobile-history-list {
  display: none;
}


/* ─── Detail Dialog ───────────────────────────────────── */

.tp-dialog :deep(.el-dialog__body) {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  max-height: calc(100dvh - 160px);
  overflow: hidden;
  padding: 16px 20px;
}

.tp-dialog :deep(.el-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 32px);
  margin: 16px auto !important;
  overflow: hidden;
}

.tp-dialog :deep(.el-dialog__header) {
  flex: 0 0 auto;
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.tp-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.tp-dialog :deep(.el-dialog__footer) {
  flex: 0 0 auto;
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.tp-detail-list-scroll {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  margin-top: 14px;
}

.tp-detail-table {
  min-width: 722px;
}

.tp-detail-student-group + .tp-detail-student-group {
  margin-top: 14px;
}

.tp-detail-student-group__header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border: 1px solid #dce5ef;
  border-bottom: 0;
  border-radius: 6px 6px 0 0;
  background: #f5f8fc;
}

.tp-detail-student-group__header strong {
  color: #334155;
  font-size: 13px;
}

.tp-detail-student-group__header span {
  color: #718096;
  font-size: 12px;
  white-space: nowrap;
}

.tp-detail-student-group .tp-detail-table {
  border-radius: 0 0 6px 6px;
}

.tp-detail-summary {
  display: grid;
  flex: 0 0 auto;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px 14px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 6px;
}

.tp-detail-summary__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tp-detail-summary__item .label {
  font-size: 12px;
  color: #909399;
}

.tp-detail-summary__item .value {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

@media (max-width: 640px) {
  .tp-view { gap: 8px; }
  .tp-view > .table-page-header { padding: 8px 12px; border: 0; border-bottom: 1px solid #e7edf3; border-radius: 0; }
  .tp-view > .table-page-header .table-page-header__eyebrow,
  .tp-page-description { display: none; }
  .tp-view > .table-page-header h2 { font-size: 16px; }
  .tp-page { display: block; min-height: 0; }
  .tp-sidebar { width: 100%; height: clamp(390px, calc(100dvh - 80px), 850px); }
  .tp-sidebar--mobile-hidden,
  .tp-content--mobile-hidden { display: none; }
  .tp-mobile-list-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 12px; border-bottom: 1px solid #ebeef5; }
  .tp-mobile-list-header > div { display: grid; gap: 2px; }
  .tp-mobile-list-header strong { color: #334155; font-size: 13px; }
  .tp-mobile-list-header span { color: #98a2b3; font-size: 11px; }
  .tp-sidebar__search { padding: 8px 10px; }
  .tp-mobile-selection { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
  .tp-mobile-selection strong { font-size: 13px; }
  .tp-mobile-selection small { color: #8b95a5; }
  .tp-content__header { display:none; }
  .tp-content { gap: 8px; padding-bottom: 72px; }
  .tp-section, .tp-section--card, .tp-section--filters, .tp-section--teacher-period { padding: 10px; border-radius: 9px; }
  .tp-section--filters { padding: 10px; }
  .tp-filters { display: grid; grid-template-columns: minmax(0, 1fr); gap: 8px; }
  .tp-filters__group { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 8px; }
  .tp-filters__label { margin: 0; font-size: 12px; }
  .tp-filters__group .tp-period-picker { width: 100% !important; }
  .tp-filters__group .tp-period-picker :deep(.el-range-editor) { width: 100%; }
  .tp-filters__actions { display: block; }
  .tp-filters__actions .el-button { width: 100%; min-height: 36px; margin: 0; }
  .tp-teacher-period { display: grid; grid-template-columns: minmax(0, 1fr); gap: 8px; }
  .tp-teacher-period .tp-filters__group { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 8px; }
  .tp-teacher-period .tp-filters__group .tp-period-picker { width: 100% !important; }
  .tp-teacher-period .tp-filters__group .tp-period-picker :deep(.el-range-editor) { width: 100%; }
  .tp-teacher-period > .el-button { width: 100%; min-height: 36px; margin: 0; }
  .tp-section__header { align-items: center; flex-direction: row; justify-content: space-between; gap: 8px; }
  .tp-section__title { font-size: 14px; }
  .tp-section__subtitle { color: #748397; font-size: 11px; text-align: right; }
  .tp-section__header-left { align-items: center; flex-direction: row; gap: 7px; }
  .tp-history-filters .el-select { width: 104px !important; }
  .tp-rate-alert { padding: 8px; }
  .tp-mobile-lesson-list,
  .tp-mobile-history-list,
  .tp-mobile-detail-list { display: grid; gap: 7px; min-width: 0; }
  .tp-mobile-lesson-card,
  .tp-mobile-history-card,
  .tp-mobile-detail-card { min-width: 0; padding: 9px; border: 1px solid #e0e7ef; border-radius: 8px; background: #fff; }
  .tp-mobile-lesson-card > header,
  .tp-mobile-history-card > header,
  .tp-mobile-detail-card > header { display: flex; align-items: center; gap: 7px; min-width: 0; }
  .tp-mobile-lesson-card > header > strong { flex: 1; min-width: 0; color: #334155; font-size: 12px; line-height: 1.35; }
  .tp-mobile-lesson-card > header .el-tag { flex:none; }
  .tp-mobile-lesson-card__identity { display: grid; gap: 2px; min-width: 0; margin: 7px 0; }
  .tp-mobile-lesson-card__identity > strong { overflow-wrap: anywhere; color: #25384d; font-size: 13px; }
  .tp-mobile-lesson-card__identity > span { overflow-wrap: anywhere; color: #768498; font-size: 11px; line-height: 1.4; }
  .tp-mobile-lesson-card__metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 5px; padding: 7px 8px; border-radius: 6px; background: #f5f7fa; }
  .tp-mobile-lesson-card__metrics > div { display: grid; align-content: start; gap: 3px; min-width: 0; }
  .tp-mobile-lesson-card__metrics small { color: #8491a2; font-size: 10px; }
  .tp-mobile-lesson-card__metrics strong { overflow-wrap: anywhere; color: #35485f; font-size: 12px; }
  .tp-mobile-lesson-card__metrics .el-tag { justify-self: start; }
  .tp-summary { position: sticky; bottom: 72px; z-index: 5; align-items: center; flex-direction: row; gap: 8px; margin: 0; padding: 9px 10px; border: 1px solid #dce5ef; background: #f8fafc; box-shadow: 0 3px 12px rgb(32 51 74 / 10%); font-size: 11px; }
  .tp-summary > span { min-width: 0; line-height: 1.45; }
  .tp-summary .el-button { flex:none; min-height: 34px; margin: 0; padding: 0 11px; }
  .tp-mobile-history-card > header { align-items: flex-start; justify-content: space-between; }
  .tp-mobile-history-card > header > div { display: grid; gap: 3px; min-width: 0; }
  .tp-mobile-history-card > header strong { overflow-wrap: anywhere; color: #334155; font-size: 12px; }
  .tp-mobile-history-card > header small { color: #7b8797; font-size: 10px; }
  .tp-mobile-history-card__totals { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 8px; }
  .tp-mobile-history-card__totals > span { display: grid; gap: 2px; }
  .tp-mobile-history-card__totals small { color: #8491a2; font-size: 10px; }
  .tp-mobile-history-card__totals strong { color: #35485f; font-size: 12px; }
  .tp-mobile-history-card__actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
  .tp-mobile-history-card__actions .el-button { min-height: 30px; margin: 0; padding: 0 9px; font-size: 11px; }
  .tp-mobile-history-card__actions .tp-history-detail-button { min-height: 36px; font-size: 13px; }
  .tp-detail-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; padding: 9px; }
  .tp-detail-summary__item .value { overflow-wrap: anywhere; font-size: 12px; }
  .tp-mobile-detail-card__context { display: block; margin: 5px 0 8px; color: #78869a; font-size: 11px; line-height: 1.4; }
  .tp-pagination { justify-content: center; overflow-x: auto; }
  .tp-dialog :deep(.el-dialog__body) { padding: 12px; }
  .tp-section .tp-table { display: none; }
  .tp-mobile-lesson-list,
  .tp-mobile-history-list { display: grid; gap: 8px; }
  .tp-mobile-lesson-card,
  .tp-mobile-history-card { display: grid; gap: 10px; padding: 11px; border: 1px solid #dbe4ea; border-radius: 10px; background: #fff; }
  .tp-mobile-lesson-card__header,
  .tp-mobile-history-card__header { display: flex; align-items: flex-start; gap: 8px; }
  .tp-mobile-lesson-card__identity,
  .tp-mobile-history-card__header > div { display: grid; min-width: 0; gap: 3px; }
  .tp-mobile-lesson-card__identity strong,
  .tp-mobile-history-card__header strong { overflow: hidden; color: #263b44; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
  .tp-mobile-lesson-card__identity span,
  .tp-mobile-history-card__header span { color: #7b8794; font-size: 11px; }
  .tp-mobile-lesson-card__details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; margin: 0; padding-top: 9px; border-top: 1px solid #edf1f3; }
  .tp-mobile-lesson-card__details dt { margin-bottom: 2px; color: #8a969e; font-size: 10px; }
  .tp-mobile-lesson-card__details dd { margin: 0; color: #40535d; font-size: 12px; font-variant-numeric: tabular-nums; }
  .tp-mobile-lesson-card__missing { color: #d14d4d !important; font-weight: 600; }
  .tp-mobile-history-card__header { justify-content: space-between; }
  .tp-mobile-history-card__summary { display: flex; align-items: baseline; justify-content: space-between; padding-top: 8px; border-top: 1px solid #edf1f3; color: #52636c; font-size: 12px; }
  .tp-mobile-history-card__summary strong { color: #263b44; font-size: 15px; }
  .tp-mobile-history-card__actions { display: flex; justify-content: flex-end; gap: 4px; }
}
</style>
