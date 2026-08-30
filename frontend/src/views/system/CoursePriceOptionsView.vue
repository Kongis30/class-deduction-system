<template>
  <div class="page-stack">
    <section class="config-panel">
      <div class="config-panel__header">
        <div>
          <span class="config-panel__eyebrow">Course setup</span>
          <h2>Set Grade Classes & Registration Options</h2>
          <p>Choose a course, create one class for each grade, then add the monthly, weekly, or session registration options for that class.</p>
        </div>
        <div class="header-actions">
          <el-button @click="openCategoryDialog">Edit categories</el-button>
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
          <span class="navigation-sidebar__label">{{ selectedModeLabel }} courses</span>
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
            description="No courses"
          />
        </aside>

        <div class="matrix-content">
          <section v-if="!query.courseProductId" class="matrix-empty-guide">
            <span>Step 2</span>
            <strong>Choose a course on the left to set grades, prices, and schedule.</strong>
            <p>{{ emptyGuideDescription }}</p>
            <el-button v-if="fromProductSetup" @click="goToCourseProducts">Back to Courses</el-button>
          </section>

          <template v-else>
            <div class="matrix-toolbar">
              <el-button v-if="fromProductSetup" text type="primary" @click="handleContextBack">
                <el-icon><ArrowLeft /></el-icon>
                Back to Courses
              </el-button>
              <el-button v-if="selectedMode === 'GROUP'" @click="openGradeDialog">Manage grades</el-button>
              <el-button type="primary" @click="openDialog()">
                {{ matrixPrimaryActionLabel }}
              </el-button>
            </div>

            <el-empty v-if="!loading && groupedOptions.length === 0" description="No grade classes for this course yet." />

            <section
              v-for="group in groupedOptions"
              :key="group.key"
              class="product-price-group"
            >
              <header class="product-price-group__header">
                <div>
                  <strong>{{ group.gradeLabel }} class</strong>
                  <span v-if="selectedMode === 'GROUP'">{{ formatGroupSchedule(group.rows[0]) }}</span>
                  <small>{{ group.rows.length }} registration option{{ group.rows.length === 1 ? '' : 's' }} for this grade</small>
                </div>
                <div class="product-price-group__actions">
                  <small v-if="selectedMode === 'GROUP' && groupHalfDayPriceSummary(group)" class="group-halfday-summary">
                    {{ groupHalfDayPriceSummary(group) }}
                  </small>
                  <small v-if="selectedMode === 'GROUP'">{{ formatGroupClassroom(group.rows[0]) }}</small>
                  <el-button size="small" type="primary" plain @click="openDialog(undefined, group.gradeLabel)">
                    Add registration option
                  </el-button>
                </div>
              </header>

              <div v-loading="loading || loadingProducts" class="grade-package-card">
                <div v-if="selectedMode === 'ONE_ON_ONE'" class="grade-package-card__summary">
                  <div>
                    <strong>{{ groupPackageTitle(group) }}</strong>
                    <span>Private lesson package</span>
                  </div>
                </div>

                <el-table
                  :data="group.rows"
                  row-key="id"
                  class="config-table price-matrix-table payment-option-table"
                  size="small"
                  border
                >
                  <el-table-column label="Registration option" min-width="180">
                    <template #header>
                      <header-tip label="Registration option" tip="What the student is buying, such as 2 months, 6 months, or Fall 13 sessions." />
                    </template>
                    <template #default="{ row }">
                      <div class="matrix-class-cell">
                        <div class="matrix-class-cell__title">
                          <strong>{{ paymentOptionName(row) }}</strong>
                        </div>
                        <span>{{ packageLengthLabel(row) || '-' }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column :label="selectedMode === 'GROUP' ? 'Full-day price' : 'Price'" min-width="150" align="right">
                    <template #header>
                      <header-tip :label="selectedMode === 'GROUP' ? 'Full-day price' : 'Price'" :tip="priceColumnTip" />
                    </template>
                    <template #default="{ row }">
                      <div class="price-cell">
                        <strong>{{ formatMoney(row.totalPrice ?? row.priceAmount) }}</strong>
                        <span>{{ priceUnitLabel(row) }}</span>
                        <small v-if="row.derivedUnitPrice">
                          {{ formatMoney(row.derivedUnitPrice) }} / {{ packageUnitText(row.packageUnit || row.groupBillingType || 'package').toLowerCase() }}
                        </small>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column v-if="selectedMode === 'GROUP'" label="Half-day price" min-width="140" align="right">
                    <template #header>
                      <header-tip label="Half-day price" tip="Optional price used when a student signs up for morning or afternoon only." />
                    </template>
                    <template #default="{ row }">
                      <div class="price-cell">
                        <strong>{{ row.halfDayPrice ? formatMoney(row.halfDayPrice) : '-' }}</strong>
                        <span v-if="row.halfDayPrice">USD / package</span>
                        <small v-if="row.halfDayPrice && halfDayUnitPrice(row)">
                          {{ formatMoney(halfDayUnitPrice(row)) }} / {{ packageUnitText(row.packageUnit || row.groupBillingType || 'package').toLowerCase() }}
                        </small>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="Usage" width="120">
                    <template #header>
                      <header-tip label="Usage" tip="Whether this registration option has already been used by registrations." />
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
                      <header-tip label="Status" tip="Only active registration options are available during registration." />
                    </template>
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                        {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="Actions" width="124" align="center">
                    <template #header>
                      <header-tip label="Actions" tip="Edit, deactivate, or delete this registration option." />
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
              </div>
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
      width="min(1080px, calc(100vw - 48px))"
      :close-on-click-modal="false"
      :title="priceDialogTitle"
      class="registration-option-dialog"
    >
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules" class="registration-option-form">
        <div class="pricing-context">
          <span>{{ formTeachingMode === 'GROUP' ? 'Add a registration option for this grade class' : 'Add an hour package for' }}</span>
          <strong>{{ selectedFormProductName }} · {{ form.gradeBand || 'selected grade' }}</strong>
          <small>{{ formTeachingMode === 'GROUP'
            ? 'Example: Grade 3 can have choices like 2 months, 6 months, Fall 13 sessions, or Full year.'
            : 'Example: Grade 6 can have packages like 40 hours, 60 hours, or 120 hours.' }}</small>
        </div>

        <section class="price-dialog-section">
          <div class="price-dialog-section__head">
            <span class="section-step">1</span>
            <div>
              <strong>Choose grade class</strong>
              <small>One Product + Grade is one class. All monthly, weekly, or session choices for this grade belong to this class.</small>
            </div>
          </div>
          <div class="form-grid">
            <el-form-item v-if="!query.courseProductId" label="Course" prop="courseProductId">
              <el-select
                v-model="form.courseProductId"
                filterable
                placeholder="Select course"
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
            <el-form-item label="Grade" prop="gradeBand">
              <el-select
                v-model="form.gradeBand"
                filterable
                allow-create
                clearable
                default-first-option
                reserve-keyword
                placeholder="Select or type grade"
                no-match-text="Press Enter to use this grade"
                no-data-text="Type a grade and press Enter"
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
          </div>
        </section>

        <section v-if="formTeachingMode === 'GROUP'" class="grade-class-settings">
          <div class="grade-class-settings__head">
            <span class="section-step">2</span>
            <div>
              <strong>Set registration option</strong>
              <small>Each row is one way a student can register for this grade class.</small>
            </div>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Count by">
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
          <div class="registration-choice-preview">
            <span>Registration label</span>
            <strong>{{ registrationChoiceLabel }}</strong>
          </div>
        </section>

        <section v-if="formTeachingMode === 'GROUP'" class="grade-class-settings">
          <div class="grade-class-settings__head">
            <span class="section-step">3</span>
            <div>
              <strong>Set registration price</strong>
              <small>Enter the total amount paid for this choice. The per-unit price is calculated for reference.</small>
            </div>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Full-day price">
              <el-input-number
                v-model="form.totalPrice"
                :min="0"
                :precision="2"
                :controls="false"
                style="width: 100%;"
              />
            </el-form-item>
            <el-form-item label="Half-day price (optional)">
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
          <div class="calculated-price">
            <span>Calculated {{ derivedUnitLabel.toLowerCase() }}</span>
            <strong>{{ formatMoney(derivedUnitPrice) }}</strong>
            <small v-if="form.halfDayPrice && form.halfDayPrice > 0">
              Half-day registration uses {{ formatMoney(form.halfDayPrice) }}.
            </small>
          </div>
        </section>

        <section v-if="formTeachingMode === 'GROUP'" class="grade-class-settings">
          <div class="grade-class-settings__head">
            <span class="section-step">4</span>
            <div>
              <strong>Set grade class schedule</strong>
              <small>This calendar belongs to the grade class and is shared by every registration option in it.</small>
            </div>
          </div>
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            class="grade-class-settings__alert"
            title="Changing these dates updates every registration option in this grade class."
            description="Need different dates for terms like Fall and Spring? Create them as separate grade classes (for example grade 'G3-6 Fall' and 'G3-6 Spring') so each term keeps its own start and end date."
          />
          <div class="form-grid form-grid--two">
            <el-form-item :label="fixedDateGroup ? 'Class starts' : 'Class calendar starts'">
              <el-date-picker
                v-if="fixedDateGroup"
                v-model="form.groupStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%;"
              />
              <el-date-picker v-else v-model="form.groupStartMonth" type="month" value-format="YYYY-MM" style="width: 100%;" />
            </el-form-item>
            <el-form-item :label="fixedDateGroup ? 'Class ends' : 'Class calendar ends'">
              <el-date-picker
                v-if="fixedDateGroup"
                v-model="form.groupEndDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%;"
              />
              <el-date-picker v-else v-model="form.groupEndMonth" type="month" value-format="YYYY-MM" style="width: 100%;" />
            </el-form-item>
          </div>
          <div class="form-grid form-grid--one">
            <el-form-item label="Classroom">
              <el-select v-model="form.groupClassroomId" clearable placeholder="Assign later" style="width: 100%;">
                <el-option v-for="room in classrooms" :key="room.id" :label="room.roomName" :value="room.id" />
              </el-select>
            </el-form-item>
          </div>
          <div class="weekly-schedule-panel">
            <WeeklyTimeSelector v-model="form.groupScheduleSlots" />
          </div>
          <div v-if="form.groupScheduleSlots.length > 0" class="slot-teacher-list">
            <div
              v-for="(slot, index) in form.groupScheduleSlots"
              :key="`${slot.weekday}-${slot.startTime}-${index}`"
              class="slot-teacher-row"
            >
              <div class="slot-time-cell">
                <small>Time</small>
                <strong>{{ groupSlotLabel(slot) }}</strong>
              </div>
              <label class="slot-field">
                <span class="slot-field__label">
                  Applies to
                  <el-tooltip
                    content="Which half-day session this time slot serves. Pick 'All day' when the class runs the whole day; use Morning only / Afternoon only for half-day classes."
                    placement="top"
                    :show-after="300"
                  >
                    <el-icon class="slot-field__tip"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
                <el-select
                  v-model="slot.attendanceOptions"
                  placeholder="Select"
                >
                  <el-option label="All day (full day)" value="ALL" />
                  <el-option label="Morning only" value="MORNING" />
                  <el-option label="Afternoon only" value="AFTERNOON" />
                </el-select>
              </label>
              <label class="slot-field">
                <span>Teacher</span>
                <el-select
                  v-model="slot.teacherId"
                  clearable
                  filterable
                  placeholder="Select teacher"
                >
                  <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
                </el-select>
              </label>
              <label class="slot-field">
                <span>Taught subject</span>
                <el-input
                  v-model="slot.subjectName"
                  maxlength="128"
                  placeholder="e.g. Chemistry"
                  clearable
                />
              </label>
            </div>
          </div>
        </section>

        <section v-else class="grade-class-settings">
          <div class="grade-class-settings__head">
            <span class="section-step">2</span>
            <div>
              <strong>Set hour registration option</strong>
              <small>Each 1-on-1 hour package is a separate registration option for the selected grade.</small>
            </div>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Package name">
              <el-input v-model="form.periodLabel" placeholder="e.g. 40 hours, 60 hours, 120 hours" />
            </el-form-item>
            <el-form-item label="Package hours" prop="oneOnOneHours">
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
            </el-form-item>
            <el-form-item label="Package price" prop="priceAmount">
              <el-input-number v-model="form.priceAmount" :min="0" :precision="2" :controls="false" style="width: 100%;" />
            </el-form-item>
          </div>
        </section>

        <el-collapse class="advanced-settings">
          <el-collapse-item title="Advanced settings" name="advanced">
            <el-form-item v-if="formTeachingMode === 'GROUP'" label="Custom registration label">
              <el-input v-model="form.periodLabel" :placeholder="paymentOptionPlaceholder" />
              <span class="field-help">Leave blank to use “{{ registrationChoiceLabel }}”.</span>
            </el-form-item>
            <el-form-item label="Notes">
              <el-input v-model="form.detailText" type="textarea" :rows="2" placeholder="Optional" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>

        <div class="dialog-hint">
          <strong>Registration result:</strong>
          {{ formTeachingMode === 'ONE_ON_ONE'
            ? 'Registration will show this grade and hour package under the selected course.'
            : 'Registration will show this grade and registration option.' }}
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveOption">{{ priceSaveButtonLabel }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="categoryDialogVisible"
      title="Edit course categories"
      width="560px"
      :close-on-click-modal="false"
    >
      <p class="category-dialog__hint">
        Rename the top-level course categories and set their display order. The set of
        categories is fixed; smaller order numbers appear first.
      </p>
      <el-table :data="categoryRows" size="small" class="category-table">
        <el-table-column label="Display name" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.displayName" maxlength="64" />
          </template>
        </el-table-column>
        <el-table-column label="Order" width="120" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.displayOrder" :min="1" :max="99" :controls="false" style="width: 90px;" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingCategories" @click="saveCategories">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="gradeDialogVisible"
      title="Manage grades"
      width="520px"
      :close-on-click-modal="false"
    >
      <p class="category-dialog__hint">
        Rename a grade or change its display order. Renaming updates this course's
        price rows, classes, and teacher pay rate scopes together. Grades without
        a schedule stay in place; use the arrows to reorder.
      </p>
      <el-table :data="gradeRows" size="small" class="category-table">
        <el-table-column label="Grade name" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.newLabel" maxlength="64" />
          </template>
        </el-table-column>
        <el-table-column label="Order" width="130" align="center">
          <template #default="{ $index }">
            <div class="grade-order-actions">
              <el-button size="small" text :disabled="$index === 0" @click="moveGradeRow($index, -1)">Up</el-button>
              <el-button size="small" text :disabled="$index === gradeRows.length - 1" @click="moveGradeRow($index, 1)">Down</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="gradeDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingGrades" @click="saveGrades">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, InfoFilled, QuestionFilled } from '@element-plus/icons-vue';
import { ElIcon, ElMessage, ElMessageBox, ElTooltip } from 'element-plus';
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createCoursePriceOption,
  deleteCoursePriceOption,
  fetchAllClassrooms,
  fetchAllTeachers,
  fetchCoursePriceOptions,
  fetchCourseCategories,
  fetchCourseProducts,
  fetchProductGradeConfigs,
  saveProductGradeConfigs,
  updateCourseCategory,
  updateCoursePriceOption,
  type CourseCategoryItem,
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
  key: string;
  gradeLabel: string;
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

const courseTypeDefs: CourseTypeDef[] = [
  { value: 'AFTER_SCHOOL', label: 'After School', mode: 'GROUP' },
  { value: 'SATURDAY', label: 'Saturday Class', mode: 'GROUP' },
  { value: 'SUMMER', label: 'Summer Class', mode: 'GROUP' },
  { value: 'SPRINT', label: 'Sprint Class', mode: 'GROUP' },
  { value: 'ONE_ON_ONE', label: '1 on 1', mode: 'ONE_ON_ONE' },
];

// Category display name/order are configurable; codes and modes stay fixed.
const categoryConfig = ref<Record<string, { label: string; order: number }>>({});
const categoryDialogVisible = ref(false);
const savingCategories = ref(false);
const categoryRows = ref<CourseCategoryItem[]>([]);

// grade 名称/顺序管理：orderMap 驱动分组排序，rows 驱动管理弹窗。
const DEFAULT_GRADE_ORDER = 999;
const gradeDialogVisible = ref(false);
const savingGrades = ref(false);
const gradeRows = ref<Array<{ currentLabel: string; newLabel: string; displayOrder: number }>>([]);
const gradeOrderMap = ref<Map<string, number>>(new Map());

const courseTypes = computed<CourseTypeDef[]>(() =>
  [...courseTypeDefs]
    .map(def => ({
      ...def,
      label: categoryConfig.value[def.value]?.label ?? def.label,
    }))
    .sort((left, right) =>
      (categoryConfig.value[left.value]?.order ?? 99) - (categoryConfig.value[right.value]?.order ?? 99)));

const courseModes: CourseModeDef[] = [
  { value: 'ONE_ON_ONE', label: '1-on-1 Private' },
  { value: 'GROUP', label: 'Group Class' },
];

const route = useRoute();
const router = useRouter();
const oneOnOneHourChoices = [20, 40, 60, 80, 100, 120];
const groupPackageUnits: Array<{ value: Exclude<PackageUnit, 'HOUR'>; label: string }> = [
  { value: 'MONTH', label: 'By month' },
  { value: 'WEEK', label: 'By week' },
  { value: 'SESSION', label: 'By session' },
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
  groupClassroomId: undefined as EntityId | undefined,
  groupScheduleSlots: [] as Array<{
    teacherId?: EntityId;
    subjectName?: string;
    weekday: number;
    startTime: string;
    endTime: string;
    plannedUnits: number;
    attendanceOptions?: string;
  }>,
  groupStartDate: '',
  groupEndDate: '',
  groupTotalSessions: 13 as number | undefined,
  groupTotalPrice: undefined as number | undefined,
  groupStartMonth: '',
  groupEndMonth: '',
  sortOrder: 0,
  status: 'ACTIVE',
});

const rules = {
  courseProductId: [{ required: true, message: 'Course is required' }],
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

const gradeOptionsForForm = computed(() => {
  // 该产品已有的 grade（含改名后的）排在硬编码建议之前，避免改名后下拉仍引导创建旧名。
  const existing = new Map<string, string>();
  for (const option of options.value) {
    const label = (option.gradeBand ?? '').trim();
    if (label && String(option.courseProductId ?? '') === String(form.courseProductId ?? '')) {
      existing.set(label.toLowerCase(), label);
    }
  }
  const merged = [...existing.values()];
  for (const grade of pptGradeOptions[form.courseType] ?? []) {
    if (!merged.some(item => item.toLowerCase() === grade.toLowerCase())) {
      merged.push(grade);
    }
  }
  return merged;
});
const derivedUnitPrice = computed(() => {
  const totalPrice = Number(form.totalPrice ?? 0);
  const quantity = Number(form.packageQuantity ?? 0);
  return totalPrice > 0 && quantity > 0
    ? Math.round((totalPrice / quantity) * 100) / 100
    : 0;
});
const derivedUnitLabel = computed(() => `${packageUnitText(form.packageUnit)} price`);
const packageQuantityLabel = computed(() => {
  if (form.packageUnit === 'MONTH') {
    return 'Number of months';
  }
  if (form.packageUnit === 'WEEK') {
    return 'Number of weeks';
  }
  return 'Number of sessions';
});
const paymentOptionPlaceholder = computed(() => {
  if (form.packageUnit === 'MONTH') {
    return 'e.g. 2 months, 6 months, Full year';
  }
  if (form.packageUnit === 'WEEK') {
    return 'e.g. 7 weeks, Summer 7 weeks';
  }
  return 'e.g. Fall 13 sessions, Spring 20 sessions';
});
const registrationChoiceLabel = computed(() => {
  const quantity = Number(form.packageQuantity ?? 0);
  return quantity > 0
    ? `${formatQuantity(quantity)} ${packageUnitPlural(form.packageUnit, quantity)}`
    : `Choose a ${packageUnitText(form.packageUnit).toLowerCase()} count`;
});

const selectedMode = ref<CourseMode>('ONE_ON_ONE');
const selectedModeLabel = computed(() =>
  courseModes.find(item => item.value === selectedMode.value)?.label ?? ''
);
const productMap = computed(() => new Map(courseProducts.value.map(item => [String(item.id), item])));
const selectedProduct = computed(() => query.courseProductId ? productMap.value.get(String(query.courseProductId)) : undefined);
const fromProductSetup = computed(() => queryValue(route.query.from) === 'course-products');
const productChoices = computed(() =>
  sortCourseProducts(courseProducts.value.filter(product => product.teachingMode === selectedMode.value))
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

const emptyGuideDescription = computed(() =>
  selectedMode.value === 'GROUP'
    ? 'Pick a course, then add a grade. After that, enter the registration choices, prices, and schedule for that grade.'
    : 'Pick a 1-on-1 course, then add the grade and hour package students can buy.'
);

const matrixPrimaryActionLabel = computed(() =>
  selectedMode.value === 'ONE_ON_ONE'
    ? 'Add hour package'
    : groupedOptions.value.length === 0 ? 'Add grade' : 'Add registration option'
);

const priceDialogTitle = computed(() =>
  selectedMode.value === 'ONE_ON_ONE'
    ? 'Add 1-on-1 package'
    : editingId.value ? 'Edit registration option' : 'Add registration option'
);

const priceSaveButtonLabel = computed(() => {
  if (editingId.value) {
    return 'Save changes';
  }
  return selectedMode.value === 'ONE_ON_ONE'
    ? 'Save hour package'
    : 'Save registration option';
});

const groupedOptions = computed<ProductPriceGroup[]>(() => {
  const groups = new Map<string, ProductPriceGroup>();
  visibleOptions.value.forEach((option) => {
    const gradeLabel = option.gradeBand || 'Grade not set';
    const key = gradeLabel.toLowerCase();
    const existing = groups.get(key);
    if (existing) {
      existing.rows.push(option);
      return;
    }
    groups.set(key, {
      key,
      gradeLabel,
      rows: [option],
    });
  });

  return Array.from(groups.values())
    .map((group) => ({
      ...group,
      rows: [...group.rows].sort((left, right) =>
        left.sortOrder - right.sortOrder
        || String(left.periodLabel ?? '').localeCompare(String(right.periodLabel ?? ''))
        || String(left.durationLabel ?? '').localeCompare(String(right.durationLabel ?? ''))
      ),
    }))
    .sort((left, right) => {
      // 已配置顺序的 grade 优先按 displayOrder 排；未配置的按字典序排在其后。
      const leftOrder = gradeOrderMap.value.get(left.gradeLabel.toLowerCase());
      const rightOrder = gradeOrderMap.value.get(right.gradeLabel.toLowerCase());
      if (leftOrder !== undefined || rightOrder !== undefined) {
        return (leftOrder ?? DEFAULT_GRADE_ORDER) - (rightOrder ?? DEFAULT_GRADE_ORDER);
      }
      return left.gradeLabel.localeCompare(right.gradeLabel);
    });
});

const existingGradeClass = computed(() => {
  if (selectedMode.value !== 'GROUP' || editingId.value || !form.courseProductId || !form.gradeBand.trim()) {
    return false;
  }
  return options.value.some(option =>
    String(option.courseProductId) === String(form.courseProductId)
      && String(option.gradeBand || '').trim().toLowerCase() === form.gradeBand.trim().toLowerCase()
      && option.classId != null
  );
});

const formTeachingMode = computed<CourseMode>(() => courseTypes.value.find(item => item.value === form.courseType)?.mode ?? 'GROUP');
const fixedDateGroup = computed(() => form.packageUnit === 'WEEK' || form.packageUnit === 'SESSION');
const priceColumnTip = computed(() => selectedMode.value === 'ONE_ON_ONE'
  ? 'Total package price for the selected private lesson hours.'
  : 'Total price for this registration option. Registration uses this configured price directly.');
const formProductChoices = computed(() =>
  sortCourseProducts(courseProducts.value.filter(product => product.teachingMode === formTeachingMode.value))
);
const selectedFormProductName = computed(() =>
  form.courseProductId ? productName(form.courseProductId) : 'Select course'
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
  await loadCategoryConfig();
  const requestedType = queryValue(route.query.courseType);
  if (courseTypes.value.some(item => item.value === requestedType)) {
    query.courseType = requestedType as CourseTypeValue;
    selectedMode.value = modeForCourseType(query.courseType);
  }
  await loadProductsAndClasses();

  const requestedProductId = queryValue(route.query.courseProductId);
  const requestedProduct = requestedProductId ? productMap.value.get(requestedProductId) : undefined;
  if (requestedProduct) {
    selectedMode.value = requestedProduct.teachingMode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'GROUP';
    query.courseType = requestedProduct.teachingMode === 'ONE_ON_ONE'
      ? 'ONE_ON_ONE'
      : normalizeGroupCourseType(requestedProduct.courseType);
    query.courseProductId = requestedProduct.id;
  }
  await loadOptions();
}

async function loadAll() {
  await Promise.all([loadProductsAndClasses(), loadOptions()]);
}

async function loadCategoryConfig() {
  try {
    const res = await fetchCourseCategories();
    const map: Record<string, { label: string; order: number }> = {};
    for (const item of res.data.data ?? []) {
      map[item.typeCode] = { label: item.displayName, order: item.displayOrder ?? 99 };
    }
    categoryConfig.value = map;
  } catch {
    // Fall back to built-in labels/order if the config cannot be loaded.
    categoryConfig.value = {};
  }
}

async function openCategoryDialog() {
  try {
    const res = await fetchCourseCategories();
    categoryRows.value = (res.data.data ?? [])
      .slice()
      .sort((left, right) => (left.displayOrder ?? 99) - (right.displayOrder ?? 99))
      .map(item => ({ ...item }));
    categoryDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load course categories.');
  }
}

async function saveCategories() {
  for (const row of categoryRows.value) {
    if (!row.displayName || !row.displayName.trim()) {
      ElMessage.warning('Display name cannot be empty.');
      return;
    }
  }
  savingCategories.value = true;
  try {
    await Promise.all(categoryRows.value.map(row =>
      updateCourseCategory(row.id, {
        displayName: row.displayName.trim(),
        displayOrder: row.displayOrder,
      })));
    await loadCategoryConfig();
    categoryDialogVisible.value = false;
    ElMessage.success('Course categories updated.');
  } catch {
    ElMessage.error('Failed to save course categories.');
  } finally {
    savingCategories.value = false;
  }
}

async function loadGradeConfigs() {
  if (!query.courseProductId) {
    gradeOrderMap.value = new Map();
    return;
  }
  try {
    const res = await fetchProductGradeConfigs(query.courseProductId);
    const map = new Map<string, number>();
    for (const item of res.data.data ?? []) {
      map.set(item.gradeLabel.toLowerCase(), item.displayOrder ?? 99);
    }
    gradeOrderMap.value = map;
  } catch {
    // 加载失败时退回字典序排序，不阻塞页面。
    gradeOrderMap.value = new Map();
  }
}

watch(() => query.courseProductId, () => {
  void loadGradeConfigs();
}, { immediate: true });

async function openGradeDialog() {
  if (!query.courseProductId) {
    return;
  }
  try {
    const res = await fetchProductGradeConfigs(query.courseProductId);
    gradeRows.value = (res.data.data ?? []).map(item => ({
      currentLabel: item.gradeLabel,
      newLabel: item.gradeLabel,
      displayOrder: item.displayOrder ?? 99,
    }));
    gradeDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load grades.');
  }
}

function moveGradeRow(index: number, direction: -1 | 1) {
  const target = index + direction;
  if (target < 0 || target >= gradeRows.value.length) {
    return;
  }
  const rows = [...gradeRows.value];
  [rows[index], rows[target]] = [rows[target], rows[index]];
  gradeRows.value = rows;
}

async function saveGrades() {
  if (!query.courseProductId) {
    return;
  }
  const seen = new Set<string>();
  for (const row of gradeRows.value) {
    const label = row.newLabel.trim();
    if (!label) {
      ElMessage.warning('Grade name cannot be empty.');
      return;
    }
    const key = label.toLowerCase();
    if (seen.has(key)) {
      ElMessage.warning(`Duplicate grade name: ${label}`);
      return;
    }
    seen.add(key);
  }
  savingGrades.value = true;
  try {
    await saveProductGradeConfigs(query.courseProductId, gradeRows.value.map((row, index) => ({
      currentLabel: row.currentLabel,
      newLabel: row.newLabel.trim() === row.currentLabel ? undefined : row.newLabel.trim(),
      displayOrder: index + 1,
    })));
    gradeDialogVisible.value = false;
    ElMessage.success('Grades updated.');
    await Promise.all([loadOptions(), loadGradeConfigs()]);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to save grades.');
  } finally {
    savingGrades.value = false;
  }
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
    ElMessage.error(err?.response?.data?.message ?? 'Failed to load courses.');
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
  query.courseType = product.teachingMode === 'ONE_ON_ONE'
    ? 'ONE_ON_ONE'
    : normalizeGroupCourseType(product.courseType);
  reloadFirstPage();
}

function openDialog(option?: CoursePriceOptionItem, gradeLabel = '') {
  const presetGrade = gradeLabel === 'Grade not set' ? '' : gradeLabel;
  editingId.value = option?.id ?? null;
  form.optionCode = option?.optionCode ?? '';
  form.courseType = (option?.courseType as CourseTypeValue) ?? query.courseType;
  form.registerClass = option?.registerClass ?? selectedProduct.value?.registerClass ?? selectedProduct.value?.productName ?? selectedTypeLabelFor(form.courseType);
  form.gradeBand = option?.gradeBand ?? presetGrade;
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
  form.groupClassroomId = option?.groupClassroomId;
  form.groupScheduleSlots = (option?.groupScheduleSlots ?? []).map(slot => ({
    ...slot,
    plannedUnits: Math.round((timeToMinutes(slot.endTime) - timeToMinutes(slot.startTime)) / 30),
    attendanceOptions: normalizeSlotAttendance(slot.attendanceOptions),
  }));
  form.groupStartDate = option?.groupStartDate ?? '';
  form.groupEndDate = option?.groupEndDate ?? '';
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
    form.courseType = product.teachingMode === 'ONE_ON_ONE'
      ? 'ONE_ON_ONE'
      : normalizeGroupCourseType(product.courseType);
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

function normalizeGroupCourseType(courseType?: string): CourseTypeValue {
  const normalized = String(courseType || '').toUpperCase();
  return courseTypes.value.some(item => item.value === normalized && item.mode === 'GROUP')
    ? normalized as CourseTypeValue
    : 'AFTER_SCHOOL';
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
    periodLabel: form.periodLabel.trim() || undefined,
    durationLabel: formTeachingMode.value === 'ONE_ON_ONE' ? form.durationLabel.trim() || undefined : undefined,
    timeSlotLabel: undefined,
    detailText: form.detailText.trim(),
    packageUnit,
    packageQuantity,
    totalPrice: packageTotalPrice,
    fullDayPrice: formTeachingMode.value === 'GROUP' ? packageTotalPrice : undefined,
    halfDayPrice: formTeachingMode.value === 'GROUP' ? optionalPositive(form.halfDayPrice) : undefined,
    oneOnOneHours: formTeachingMode.value === 'ONE_ON_ONE' ? form.oneOnOneHours : undefined,
    priceAmount: packageTotalPrice ?? 0,
    currencyCode: 'USD',
    courseProductId: form.courseProductId,
    groupTeacherId: undefined,
    groupClassroomId: formTeachingMode.value === 'GROUP' ? form.groupClassroomId : undefined,
    groupScheduleSlots: formTeachingMode.value === 'GROUP'
      ? form.groupScheduleSlots.map(({ teacherId, subjectName, weekday, startTime, endTime, attendanceOptions }) => ({
          teacherId,
          subjectName: subjectName?.trim() || undefined,
          weekday,
          startTime,
          endTime,
          attendanceOptions: normalizeSlotAttendance(attendanceOptions),
        }))
      : undefined,
    groupBillingType: formTeachingMode.value === 'GROUP' ? packageUnit : undefined,
    groupStartDate: formTeachingMode.value === 'GROUP' && fixedDateGroup.value ? form.groupStartDate || undefined : undefined,
    groupEndDate: formTeachingMode.value === 'GROUP' && fixedDateGroup.value ? form.groupEndDate || undefined : undefined,
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
    groupTeacherId: undefined,
    groupClassroomId: row.groupClassroomId,
    groupScheduleSlots: row.groupScheduleSlots,
    groupBillingType: row.groupBillingType,
    groupStartDate: row.groupStartDate,
    groupEndDate: row.groupEndDate,
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
  if (formTeachingMode.value === 'GROUP' && !existingGradeClass.value && form.groupScheduleSlots.length === 0) {
    ElMessage.warning('Add at least one weekly schedule slot for this grade class.');
    return;
  }
  if (formTeachingMode.value === 'GROUP'
      && (!form.totalPrice || form.totalPrice <= 0
        || !form.packageQuantity || form.packageQuantity <= 0)) {
    ElMessage.warning('Add a package total price and package count.');
    return;
  }
  if (formTeachingMode.value === 'GROUP' && !existingGradeClass.value
      && (fixedDateGroup.value
        ? (!form.groupStartDate || !form.groupEndDate)
        : (!form.groupStartMonth || !form.groupEndMonth))) {
    ElMessage.warning(fixedDateGroup.value
      ? 'Add the first and last date for this class.'
      : 'Add the first and last month for this grade class.');
    return;
  }
  if (formTeachingMode.value === 'GROUP' && fixedDateGroup.value
      && form.groupEndDate < form.groupStartDate) {
    ElMessage.warning('Class end date cannot be earlier than the start date.');
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
    ElMessage.success(editingId.value ? 'Registration option updated.' : 'Registration option created.');
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
    await ElMessageBox.confirm('Deactivate this registration option?', 'Confirm', { type: 'warning' });
    await updateCoursePriceOption(row.id, payloadFromRow(row, 'INACTIVE'));
    ElMessage.success('Registration option deactivated.');
    await loadOptions();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
    }
  }
}

async function removeOption(id: EntityId) {
  try {
    await ElMessageBox.confirm('Delete this registration option?', 'Confirm', { type: 'warning' });
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

function sortCourseProducts(products: CourseProductItem[]) {
  return [...products].sort((left, right) =>
    (left.sortOrder ?? 99) - (right.sortOrder ?? 99)
    || left.productName.localeCompare(right.productName)
  );
}

function selectedTypeLabelFor(value: string) {
  return courseTypes.value.find(item => item.value === value)?.label ?? formatCourseType(value);
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
  return packageLengthLabel(row)
    || [row.groupStartDate, row.groupEndDate].filter(Boolean).join(' to ')
    || [row.groupStartMonth, row.groupEndMonth].filter(Boolean).join(' to ')
    || 'Package';
}

function groupPackageTitle(group: ProductPriceGroup) {
  if (selectedMode.value === 'ONE_ON_ONE') {
    return 'Hour packages';
  }
  const first = group.rows[0];
  const unit = packageUnitText(first?.packageUnit || first?.groupBillingType || 'MONTH');
  return `${group.gradeLabel} package (${unit.toLowerCase()} payment)`;
}

function packageDisplayName(row: CoursePriceOptionItem) {
  return row.periodLabel || row.durationLabel || packageLengthLabel(row) || 'Standard package';
}

function paymentOptionName(row: CoursePriceOptionItem) {
  return row.periodLabel || packageLengthLabel(row) || 'Standard payment';
}

function isGroupRow(row: CoursePriceOptionItem) {
  return modeForCourseType(row.courseType) === 'GROUP';
}

function billingSubtitle(row: CoursePriceOptionItem) {
  if (isGroupRow(row)) {
    const window = [row.groupStartDate, row.groupEndDate].filter(Boolean).join(' to ')
      || [row.groupStartMonth, row.groupEndMonth].filter(Boolean).join(' to ');
    return window || '';
  }
  return row.notes || '';
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

function formatGroupClassroom(row: CoursePriceOptionItem) {
  const room = classroomName(row.groupClassroomId);
  return `Room: ${room}`;
}

function groupHalfDayPriceSummary(group: ProductPriceGroup) {
  const prices = group.rows
    .map(row => Number(row.halfDayPrice ?? 0))
    .filter(price => price > 0);
  if (prices.length === 0) {
    return '';
  }
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  return min === max
    ? `Half-day: ${formatMoney(min)}`
    : `Half-day: ${formatMoney(min)}–${formatMoney(max)}`;
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

function halfDayUnitPrice(row: CoursePriceOptionItem) {
  const totalPrice = Number(row.halfDayPrice ?? 0);
  const quantity = Number(row.packageQuantity ?? row.groupTotalSessions ?? 0);
  return totalPrice > 0 && quantity > 0
    ? Math.round((totalPrice / quantity) * 100) / 100
    : 0;
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

function normalizeSlotAttendance(value?: string) {
  const normalized = String(value || '').trim().toUpperCase();
  return normalized === 'MORNING' || normalized === 'AFTERNOON' ? normalized : 'ALL';
}
</script>

<style scoped>
.category-dialog__hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6b7684;
}

.category-table {
  width: 100%;
}

.grade-order-actions {
  display: inline-flex;
  gap: 4px;
}

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

.matrix-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 10px;
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

.product-price-group__header small {
  color: #64748b;
  font-size: 12px;
}

.product-price-group__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.grade-package-card {
  padding: 12px;
  background: #fff;
}

.grade-package-card__summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fbfdff;
}

.grade-package-card__summary div {
  display: grid;
  gap: 3px;
}

.grade-package-card__summary strong {
  color: #0f172a;
  font-size: 14px;
}

.grade-package-card__summary span,
.grade-package-card__summary small {
  color: #64748b;
  font-size: 12px;
}

.payment-option-table {
  border-radius: 6px;
  overflow: hidden;
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

.registration-option-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow: auto;
  padding: 16px 20px;
}

.registration-option-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #e5e7eb;
  padding: 12px 20px;
}

.registration-option-form {
  min-width: 0;
}

.form-grid {
  display: grid;
  gap: 14px;
}

.form-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.calculated-price {
  display: grid;
  grid-template-columns: auto auto 1fr;
  align-items: baseline;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
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

.calculated-price small {
  text-align: right;
}

.grade-class-settings {
  display: grid;
  gap: 12px;
  margin: 16px 0;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.price-dialog-section {
  display: grid;
  gap: 12px;
  margin: 14px 0;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.price-dialog-section__head,
.grade-class-settings__head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.price-dialog-section__head div,
.grade-class-settings__head div {
  display: grid;
  gap: 3px;
}

.section-step {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.price-dialog-section__head strong,
.grade-class-settings__head strong {
  color: #1f2937;
  font-size: 13px;
}

.price-dialog-section__head small,
.grade-class-settings__head small {
  color: #64748b;
  font-size: 12px;
}

.slot-teacher-list {
  display: grid;
  gap: 8px;
}

.weekly-schedule-panel {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 4px;
}

.weekly-schedule-panel :deep(.weekly-time-selector) {
  min-width: 760px;
}

.slot-teacher-row {
  display: grid;
  grid-template-columns: minmax(130px, 0.8fr) minmax(130px, 0.75fr) minmax(160px, 1fr) minmax(160px, 1fr) minmax(112px, 0.65fr);
  align-items: end;
  gap: 10px;
  min-width: 0;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #fbfdff;
}

.slot-time-cell,
.slot-field {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.slot-time-cell small,
.slot-field span {
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.slot-field__label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.slot-field__tip {
  color: #909399;
  cursor: help;
  font-size: 13px;
  line-height: 1;
}

.slot-field__tip:hover {
  color: #409eff;
}

.slot-time-cell strong {
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slot-field :deep(.el-select),
.slot-field :deep(.el-input),
.slot-field :deep(.el-input-number) {
  width: 100%;
}

.slot-field :deep(.el-input-number .el-input__inner) {
  text-align: left;
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

.registration-choice-preview {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 6px;
  background: #f8fafc;
}

.registration-choice-preview span {
  color: #64748b;
  font-size: 12px;
}

.registration-choice-preview strong {
  color: #1e293b;
  font-size: 13px;
}

.pricing-context {
  display: grid;
  gap: 3px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.pricing-context span,
.pricing-context small {
  color: #6b7280;
  font-size: 12px;
}

.pricing-context strong {
  color: #111827;
  font-size: 15px;
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

  .slot-teacher-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .slot-time-cell {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .registration-option-dialog :deep(.el-dialog__body) {
    padding: 12px;
  }

  .slot-teacher-row {
    grid-template-columns: 1fr;
  }
}
</style>
