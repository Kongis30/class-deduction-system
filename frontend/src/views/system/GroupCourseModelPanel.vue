<template>
  <div class="group-model-layout">
    <div v-if="appStore.isMobileViewport" class="mobile-product-switcher">
      <label for="mobile-group-product">Course</label>
      <el-select
        id="mobile-group-product"
        :model-value="selectedProductId"
        filterable
        aria-label="Choose group class course"
        placeholder="Choose a course"
        @change="selectProduct"
      >
        <el-option
          v-for="product in products"
          :key="product.id"
          :label="product.productName"
          :value="product.id"
        />
      </el-select>
    </div>

    <aside v-if="!appStore.isMobileViewport" class="group-model-products">
      <span class="group-model-products__label">Group class products</span>
      <button
        v-for="product in products"
        :key="product.id"
        type="button"
        class="group-model-product"
        :class="{ 'group-model-product--active': String(product.id) === String(selectedProductId) }"
        @click="selectProduct(product.id)"
      >
        <strong>{{ product.productName }}</strong>
        <small v-if="product.status !== 'ACTIVE'">Inactive</small>
      </button>
      <el-empty v-if="products.length === 0" :image-size="48" description="No group class products" />
    </aside>

    <main v-loading="loading" class="group-model-content">
      <template v-if="setup">
        <header class="group-model-header">
          <div>
            <div class="group-model-eyebrow group-model-eyebrow--with-help">
              <span>Course structure</span>
              <el-tooltip
                content="Set one schedule per campus for each project. Fall, spring, and full-year plans choose which class dates a student attends."
                placement="top"
              >
                <button class="group-model-help" type="button" aria-label="About this course structure">i</button>
              </el-tooltip>
            </div>
            <h2>{{ setup.productName }}</h2>
            <p class="group-model-header__description">Set up projects, registration plans, and class schedules.</p>
          </div>
          <el-button type="primary" @click="openProjectDialog()"><span class="desktop-project-action">Add teaching project</span><span class="mobile-project-action">Add project</span></el-button>
        </header>

        <el-empty v-if="setup.projects.length === 0" description="Create a teaching project to start setting up this course." />
        <section v-for="project in setup.projects" :key="project.id" class="group-project-card">
          <header class="group-project-card__header">
            <div>
              <div class="group-project-card__title">
                <h3>{{ project.projectName }}</h3>
                <el-tag v-if="project.status !== 'ACTIVE'" type="info" effect="plain">Inactive</el-tag>
                <button
                  v-if="appStore.isMobileViewport"
                  class="mobile-project-toggle"
                  type="button"
                  :aria-expanded="isProjectExpanded(project.id)"
                  :aria-label="`${isProjectExpanded(project.id) ? 'Collapse' : 'Expand'} ${project.projectName}`"
                  @click="toggleProject(project.id)"
                >
                  <el-icon :class="{ 'mobile-project-toggle__icon--expanded': isProjectExpanded(project.id) }"><ArrowDown /></el-icon>
                </button>
              </div>
              <p v-if="project.description">{{ project.description }}</p>
              <small>
                <span class="desktop-project-summary">{{ project.classes.length }} actual classes · {{ project.plans.length }} registration plans</span>
                <span class="mobile-project-summary">{{ project.plans.length }} plans · {{ project.classes.length }} classes</span>
              </small>
            </div>
            <div v-if="!appStore.isMobileViewport" class="group-project-card__actions">
              <el-button size="small" @click="openProjectDialog(project)">Edit project</el-button>
              <el-button size="small" @click="openHolidayDialog(project)">Manage holidays</el-button>
              <el-button size="small" type="primary" plain @click="openPlanDialog(project)">Add plan</el-button>
              <el-button size="small" type="primary" plain @click="openClassDialog(project)">Add actual class</el-button>
              <el-button
                size="small"
                type="danger"
                text
                :disabled="!projectDeleteReady(project) || deletingProjectId === String(project.id)"
                :loading="deletingProjectId === String(project.id)"
                @click="removeProject(project)"
              >{{ projectDeleteButtonText(project) }}</el-button>
            </div>
            <div v-else class="mobile-project-actions">
              <el-button size="small" type="primary" plain @click="openPlanDialog(project)">Add plan</el-button>
              <el-button size="small" @click="openClassDialog(project)">Add class</el-button>
              <el-dropdown trigger="click">
                <el-button size="small" class="mobile-project-actions__more" aria-label="More project actions">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openProjectDialog(project)">Edit project</el-dropdown-item>
                    <el-dropdown-item @click="openHolidayDialog(project)">Manage holidays</el-dropdown-item>
                    <el-dropdown-item
                      divided
                      class="danger-menu-item"
                      :disabled="!projectDeleteReady(project) || deletingProjectId === String(project.id)"
                      @click="removeProject(project)"
                    >{{ projectDeleteButtonText(project) }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </header>

          <div v-show="!appStore.isMobileViewport || isProjectExpanded(project.id)" class="group-project-details">
          <section class="group-project-section">
            <div class="group-project-section__heading">
              <h4>Registration plans</h4>
              <span class="group-project-section__description">Plans control the dates and price a student receives.</span>
            </div>
            <el-table v-if="!appStore.isMobileViewport && project.plans.length" :data="project.plans" row-key="id" border size="small">
              <el-table-column prop="planName" label="Plan" min-width="190" />
              <el-table-column label="Coverage" min-width="200">
                <template #default="{ row }">
                  <span>{{ coverageLabel(row) }}</span>
                  <small v-if="row.coverageStartDate || row.coverageEndDate" class="group-cell-subtitle">
                    {{ row.coverageStartDate || '—' }} to {{ row.coverageEndDate || '—' }}
                  </small>
                </template>
              </el-table-column>
              <el-table-column label="Quantity" width="130" align="center">
                <template #default="{ row }">{{ row.packageQuantity }} {{ row.packageUnit === 'MONTH' ? 'months' : 'sessions' }}</template>
              </el-table-column>
              <el-table-column label="Full day" width="120" align="right">
                <template #default="{ row }">{{ money(row.fullDayPrice) }}</template>
              </el-table-column>
              <el-table-column label="Half day" width="120" align="right">
                <template #default="{ row }">{{ row.halfDayPrice == null ? '—' : money(row.halfDayPrice) }}</template>
              </el-table-column>
              <el-table-column label="Status" width="110" align="center">
                <template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column label="Actions" width="150" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openPlanDialog(project, row)">Edit</el-button>
                  <el-button link type="danger" @click="removePlan(row)">Delete</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="appStore.isMobileViewport && project.plans.length" class="mobile-plan-list">
              <article v-for="plan in project.plans" :key="plan.id" class="mobile-model-card">
                <header><strong>{{ plan.planName }}</strong><el-tag size="small" :type="plan.status === 'ACTIVE' ? 'success' : 'info'">{{ plan.status }}</el-tag></header>
                <span>{{ coverageLabel(plan) }}</span>
                <small v-if="plan.coverageStartDate || plan.coverageEndDate">{{ plan.coverageStartDate || '—' }} to {{ plan.coverageEndDate || '—' }}</small>
                <dl><div><dt>Quantity</dt><dd>{{ plan.packageQuantity }} {{ plan.packageUnit === 'MONTH' ? 'months' : 'sessions' }}</dd></div><div><dt>Full day</dt><dd>{{ money(plan.fullDayPrice) }}</dd></div><div><dt>Half day</dt><dd>{{ plan.halfDayPrice == null ? '—' : money(plan.halfDayPrice) }}</dd></div></dl>
                <div class="mobile-model-card__actions"><el-button type="primary" plain @click="openPlanDialog(project, plan)">Edit</el-button><el-dropdown trigger="click"><el-button text aria-label="More plan actions"><el-icon><MoreFilled /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item class="danger-menu-item" @click="removePlan(plan)">Delete plan</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
              </article>
            </div>
            <el-empty v-if="!project.plans.length" :image-size="38" description="No registration plans yet" />
          </section>

          <section class="group-project-section">
            <div class="group-project-section__heading">
              <h4>Actual class schedules</h4>
            </div>
            <el-table v-if="!appStore.isMobileViewport && project.classes.length" :data="project.classes" row-key="id" border size="small">
              <el-table-column label="Campus" width="170">
                <template #default="{ row }"><el-tag v-if="row.campusName" effect="plain">{{ row.campusName }}</el-tag><span v-else class="muted">—</span></template>
              </el-table-column>
              <el-table-column prop="actualClass" label="Class" min-width="150" />
              <el-table-column label="Class period" min-width="210">
                <template #default="{ row }">{{ row.startDate || '—' }} to {{ row.endDate || '—' }}</template>
              </el-table-column>
              <el-table-column label="Status" width="120" align="center">
                <template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column label="Actions" width="210" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openClassDialog(project, row)">Edit info</el-button>
                  <el-button link type="primary" :disabled="row.status !== 'ACTIVE'" @click="openSchedule(row)">Edit schedule</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="appStore.isMobileViewport && project.classes.length" class="mobile-class-list">
              <article v-for="actualClass in project.classes" :key="actualClass.id" class="mobile-model-card">
                <header><strong>{{ actualClass.actualClass }}</strong><el-tag size="small" :type="actualClass.status === 'ACTIVE' ? 'success' : 'info'">{{ actualClass.status }}</el-tag></header>
                <span>{{ actualClass.campusName || 'Campus not set' }}</span>
                <small>Class period: {{ actualClass.startDate || '—' }} to {{ actualClass.endDate || '—' }}</small>
                <div class="mobile-model-card__actions"><el-button @click="openClassDialog(project, actualClass)">Edit info</el-button><el-button type="primary" plain :disabled="actualClass.status !== 'ACTIVE'" @click="openSchedule(actualClass)">Edit schedule</el-button></div>
              </article>
            </div>
            <el-empty v-if="!project.classes.length" :image-size="38" description="No actual classes yet" />
          </section>
          </div>
        </section>
      </template>
      <el-empty v-else-if="!loading" description="Select a group class product." />
    </main>

    <el-dialog v-model="projectDialogVisible" :title="editingProject ? 'Edit teaching project' : 'Add teaching project'" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="Project name" required><el-input v-model="projectForm.projectName" maxlength="128" placeholder="e.g. SHSAT / PSAT / SAT" /></el-form-item>
        <el-form-item label="Description"><el-input v-model="projectForm.description" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="Status"><el-select v-model="projectForm.status" style="width: 100%"><el-option label="Active" value="ACTIVE" /><el-option label="Inactive" value="INACTIVE" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="projectDialogVisible = false">Cancel</el-button><el-button type="primary" :loading="saving" @click="saveProject">Save project</el-button></template>
    </el-dialog>

    <el-dialog v-model="planDialogVisible" :title="editingPlan ? 'Edit registration plan' : 'Add registration plan'" width="min(640px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-form label-position="top" class="plan-form">
        <el-form-item label="Plan name" required><el-input v-model="planForm.planName" maxlength="128" placeholder="e.g. Fall, Spring, Full year, 6 months" /></el-form-item>
        <div class="plan-form__row">
          <el-form-item label="Coverage type" required>
            <el-select v-model="planForm.coverageType" style="width: 100%" @change="normalizePlanFields">
              <el-option label="Fixed date range" value="FIXED_DATES" />
              <el-option label="Rolling months" value="ROLLING_MONTHS" />
              <el-option label="Number of sessions" value="SESSION_COUNT" />
            </el-select>
          </el-form-item>
          <el-form-item :label="planForm.packageUnit === 'MONTH' ? 'Months' : 'Sessions'" required>
            <el-input-number v-model="planForm.packageQuantity" :min="1" :precision="0" :controls="false" style="width: 100%" />
          </el-form-item>
        </div>
        <div v-if="planForm.coverageType === 'FIXED_DATES'" class="plan-form__row">
          <el-form-item label="First scheduled date" required><el-date-picker v-model="planForm.coverageStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          <el-form-item label="Last scheduled date" required><el-date-picker v-model="planForm.coverageEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        </div>
        <div class="plan-form__row">
          <el-form-item label="Full-day price" required><el-input-number v-model="planForm.fullDayPrice" :min="0" :precision="0" :controls="false" style="width: 100%" /></el-form-item>
          <el-form-item label="Half-day price"><el-input-number v-model="planForm.halfDayPrice" :min="0" :precision="0" :controls="false" style="width: 100%" /></el-form-item>
        </div>
        <el-form-item label="Status"><el-select v-model="planForm.status" style="width: 100%"><el-option label="Active" value="ACTIVE" /><el-option label="Inactive" value="INACTIVE" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="planDialogVisible = false">Cancel</el-button><el-button type="primary" :loading="saving" @click="savePlan">Save plan</el-button></template>
    </el-dialog>

    <el-dialog v-model="classDialogVisible" :title="editingClass ? 'Edit actual class' : 'Add actual class'" width="min(540px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="Class name" required><el-input v-model="classForm.actualClass" maxlength="64" placeholder="e.g. G3, SHSAT" /></el-form-item>
        <el-form-item label="Campus (optional)"><el-select v-model="classForm.campusId" filterable clearable placeholder="Shared across campuses" style="width: 100%"><el-option v-for="campus in campuses" :key="campus.id" :label="campus.campusName" :value="campus.id" /></el-select></el-form-item>
        <div class="plan-form__row">
          <el-form-item label="Class start date" required><el-date-picker v-model="classForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          <el-form-item label="Class end date" required><el-date-picker v-model="classForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        </div>
        <el-alert title="After creating the class, use Edit schedule here to set its weekly times, teachers, and rooms." type="info" :closable="false" />
      </el-form>
      <template #footer><el-button @click="classDialogVisible = false">Cancel</el-button><el-button type="primary" :loading="saving" @click="saveClass">Save class</el-button></template>
    </el-dialog>

    <el-dialog
      v-model="scheduleDialogVisible"
      :title="scheduleDialogTitle"
      width="min(960px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      class="group-class-schedule-dialog"
    >
      <div v-loading="loadingSchedule" class="group-class-schedule-dialog__body">
        <el-alert
          title="Saving updates this class's weekly schedule. Future unconfirmed lessons are rebuilt; confirmed lessons stay unchanged."
          type="info"
          :closable="false"
          show-icon
        />
        <div class="group-class-schedule-dialog__calendar">
          <WeeklyTimeSelector v-model="scheduleForm.slots" hide-hint />
        </div>
        <div v-if="scheduleForm.slots.length" class="group-class-schedule-dialog__slots">
          <section
            v-for="(slot, index) in scheduleForm.slots"
            :key="`${slot.weekday}-${slot.startTime}-${index}`"
            class="group-class-schedule-slot"
          >
            <header class="group-class-schedule-slot__header">
              <strong>{{ scheduleSlotLabel(slot) }}</strong>
              <span>{{ (slot.plannedUnits * 0.5).toFixed(1) }} hours</span>
              <el-tooltip content="Teacher, subject, and classroom alternate each class. Project holidays are skipped." placement="top">
                <div class="group-class-schedule-slot__rotation">
                  <span>A/B rotation</span>
                  <el-switch v-model="slot.rotationEnabled" />
                </div>
              </el-tooltip>
            </header>
            <div class="group-class-schedule-slot__fields">
              <label>
                <span>Teacher</span>
                <el-select v-model="slot.teacherId" clearable filterable placeholder="No teacher for this slot">
                  <el-option v-for="teacher in sortedTeachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
                </el-select>
              </label>
              <label>
                <span>Course / subject</span>
                <el-input v-model="slot.subjectName" maxlength="128" placeholder="e.g. English" />
              </label>
              <label>
                <span>Classroom</span>
                <el-select v-model="slot.classroomSelection" clearable filterable placeholder="No classroom (optional)">
                  <el-option label="Online" value="ONLINE" />
                  <el-option-group v-for="group in classroomGroups" :key="group.key" :label="group.label">
                    <el-option v-for="room in group.classrooms" :key="room.id" :label="room.roomName" :value="`ROOM:${room.id}`" />
                  </el-option-group>
                </el-select>
              </label>
            </div>
            <div v-if="slot.rotationEnabled" class="group-class-schedule-slot__fields group-class-schedule-slot__fields--alternate">
              <label>
                <span>Alternate teacher</span>
                <el-select v-model="slot.alternateTeacherId" clearable filterable placeholder="Select teacher">
                  <el-option v-for="teacher in sortedTeachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
                </el-select>
              </label>
              <label>
                <span>Alternate course / subject</span>
                <el-input v-model="slot.alternateSubjectName" maxlength="128" placeholder="e.g. Math" />
              </label>
              <label>
                <span>Alternate classroom</span>
                <el-select v-model="slot.alternateClassroomSelection" clearable filterable placeholder="No classroom (optional)">
                  <el-option label="Online" value="ONLINE" />
                  <el-option-group v-for="group in classroomGroups" :key="group.key" :label="group.label">
                    <el-option v-for="room in group.classrooms" :key="room.id" :label="room.roomName" :value="`ROOM:${room.id}`" />
                  </el-option-group>
                </el-select>
              </label>
              <label class="group-class-schedule-slot__anchor">
                <span>Start rotation on or after</span>
                <el-date-picker v-model="slot.rotationAnchorDate" type="date" value-format="YYYY-MM-DD" placeholder="Use class start date" />
              </label>
            </div>
          </section>
        </div>
        <el-empty v-else-if="!loadingSchedule" :image-size="48" description="Select weekly time slots in the calendar above." />
      </div>
      <template #footer>
        <el-button @click="scheduleDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingSchedule" :disabled="loadingSchedule" @click="saveSchedule">Save schedule</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="holidayDialogVisible" :title="`${holidayProject?.projectName ?? 'Project'} holidays`" width="min(760px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-alert
        title="A project holiday applies to every actual class and campus in this project. Classes skip these dates and continue on their normal weekly schedule afterward."
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="holiday-form">
        <el-form-item label="Holiday name" required><el-input v-model="holidayForm.holidayName" maxlength="100" placeholder="e.g. Thanksgiving break" /></el-form-item>
        <div class="plan-form__row">
          <el-form-item label="Start date" required><el-date-picker v-model="holidayForm.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="End date" required><el-date-picker v-model="holidayForm.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        </div>
        <div class="holiday-form__actions">
          <el-button v-if="editingHoliday" @click="resetHolidayForm">Cancel edit</el-button>
          <el-button type="primary" :loading="savingHoliday" @click="saveHoliday">{{ editingHoliday ? 'Save changes' : 'Add holiday' }}</el-button>
        </div>
      </el-form>
      <el-table v-loading="loadingHolidays" :data="holidays" border>
        <el-table-column prop="holidayName" label="Holiday" min-width="180" />
        <el-table-column label="Dates" min-width="220"><template #default="{ row }">{{ row.startDate }} – {{ row.endDate }}</template></el-table-column>
        <el-table-column label="Actions" width="140" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="editHoliday(row)">Edit</el-button>
            <el-button link type="danger" @click="removeHoliday(row)">Delete</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :image-size="44" description="No holidays configured for this project." /></template>
      </el-table>
      <template #footer><el-button @click="holidayDialogVisible = false">Close</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { ArrowDown, MoreFilled } from '@element-plus/icons-vue';
import { useAppStore } from '@/stores/app';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fetchAllClassrooms, fetchAllTeachers, fetchCampuses } from '@/api/base-data';
import { createActualClass, fetchClassGroups, updateClassInfo, updateClassSchedule, type ClassScheduleUpdatePayload } from '@/api/schedule';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { SlotDef } from '@/components/WeeklyTimeSelector.vue';
import { scheduleDurationMinutes, scheduleEndTimeForApi, scheduleEndTimeForDisplay } from '@/utils/schedule-time';
import {
  createGroupProjectHoliday,
  createGroupCourseProject,
  createGroupRegistrationPlan,
  deleteGroupProjectHoliday,
  deleteGroupCourseProject,
  deleteGroupRegistrationPlan,
  fetchGroupCourseProductSetup,
  fetchGroupProjectHolidays,
  previewGroupProjectHoliday,
  updateGroupCourseProject,
  updateGroupProjectHoliday,
  updateGroupRegistrationPlan,
  type GroupCourseProductSetup,
  type GroupCourseProject,
  type GroupProjectClass,
  type GroupProjectHoliday,
  type GroupProjectHolidayPayload,
  type GroupRegistrationPlan,
  type GroupRegistrationPlanPayload,
} from '@/api/group-course-model';
import type { CampusItem, ClassGroupItem, ClassroomItem, CourseProductItem, EntityId, TeacherItem } from '@/types/domain';

interface EditableClassScheduleSlot extends SlotDef {
  teacherId?: EntityId;
  subjectName: string;
  alternateTeacherId?: EntityId;
  alternateSubjectName: string;
  classroomSelection: string;
  alternateClassroomSelection: string;
  rotationAnchorDate: string;
  rotationEnabled: boolean;
}

const PROJECT_DELETE_COOLDOWN_SECONDS = 10;
const PROJECT_DELETE_COOLDOWN_MS = PROJECT_DELETE_COOLDOWN_SECONDS * 1000;
const props = defineProps<{ products: CourseProductItem[] }>();
const appStore = useAppStore();
const emit = defineEmits<{ reload: [] }>();
const products = computed(() => props.products
  .filter((item) => item.teachingMode === 'GROUP')
  .sort((left, right) => left.productName.localeCompare(right.productName, undefined, {
    sensitivity: 'base',
    numeric: true,
  })));
const selectedProductId = ref<EntityId>();
const expandedProjectIds = ref(new Set<string>());
const setup = ref<GroupCourseProductSetup>();
const campuses = ref<CampusItem[]>([]);
const loading = ref(false);
const saving = ref(false);
const projectDialogVisible = ref(false);
const planDialogVisible = ref(false);
const classDialogVisible = ref(false);
const scheduleDialogVisible = ref(false);
const loadingSchedule = ref(false);
const savingSchedule = ref(false);
const scheduleClass = ref<GroupProjectClass>();
const scheduleForm = reactive<{ slots: EditableClassScheduleSlot[] }>({ slots: [] });
const teachers = ref<TeacherItem[]>([]);
const classrooms = ref<ClassroomItem[]>([]);
const holidayDialogVisible = ref(false);
const loadingHolidays = ref(false);
const savingHoliday = ref(false);
const deletingProjectId = ref<string>();
const deleteCooldownNow = ref(Date.now());
const projectDeleteReadyAt = reactive<Record<string, number>>({});
const holidayProject = ref<GroupCourseProject>();
const holidays = ref<GroupProjectHoliday[]>([]);
const editingHoliday = ref<GroupProjectHoliday>();
const holidayForm = reactive({ holidayName: '', startDate: '', endDate: '' });
const editingProject = ref<GroupCourseProject>();
const editingPlan = ref<GroupRegistrationPlan>();
const editingClass = ref<GroupProjectClass>();
const selectedProject = ref<GroupCourseProject>();
const projectForm = reactive({ projectName: '', description: '', status: 'ACTIVE' });
const planForm = reactive<GroupRegistrationPlanPayload>({
  planCode: '', planName: '', coverageType: 'SESSION_COUNT', packageUnit: 'SESSION',
  packageQuantity: 1, fullDayPrice: 0, halfDayPrice: undefined, currencyCode: 'USD', status: 'ACTIVE',
});
const classForm = reactive({ actualClass: '', campusId: undefined as EntityId | undefined, startDate: '', endDate: '' });
let requestSequence = 0;
let scheduleRequestSequence = 0;
let deleteCooldownTimer: ReturnType<typeof setInterval> | undefined;

const scheduleDialogTitle = computed(() => {
  const name = scheduleClass.value?.className || scheduleClass.value?.actualClass;
  return name ? `Edit schedule · ${name}` : 'Edit class schedule';
});
const sortedTeachers = computed(() => [...teachers.value].sort((left, right) =>
  left.teacherName.localeCompare(right.teacherName, undefined, { sensitivity: 'base' })));
const classroomGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; classrooms: ClassroomItem[] }>();
  for (const room of classrooms.value) {
    const label = room.campus?.trim() || 'Unassigned campus';
    const key = room.campusId != null ? `campus:${room.campusId}` : `campus-name:${label.toLocaleLowerCase()}`;
    const group = groups.get(key) ?? { key, label, classrooms: [] };
    group.classrooms.push(room);
    groups.set(key, group);
  }
  return [...groups.values()]
    .map((group) => ({ ...group, classrooms: [...group.classrooms].sort((left, right) =>
      left.roomName.localeCompare(right.roomName, undefined, { numeric: true, sensitivity: 'base' })) }))
    .sort((left, right) => left.label.localeCompare(right.label, undefined, { numeric: true, sensitivity: 'base' }));
});

watch(products, (items) => {
  if (items.length === 0) {
    selectedProductId.value = undefined;
    setup.value = undefined;
  } else if (!items.some((item) => String(item.id) === String(selectedProductId.value))) {
    selectedProductId.value = items[0].id;
    void loadSetup();
  }
}, { immediate: true });

watch(() => setup.value?.projects.map((project) => String(project.id)) ?? [], (projectIds) => {
  const visibleIds = new Set(projectIds);
  for (const id of Object.keys(projectDeleteReadyAt)) {
    if (!visibleIds.has(id)) delete projectDeleteReadyAt[id];
  }
  for (const id of projectIds) {
    if (projectDeleteReadyAt[id] == null) projectDeleteReadyAt[id] = Date.now() + PROJECT_DELETE_COOLDOWN_MS;
  }
}, { immediate: true });

onMounted(async () => {
  deleteCooldownTimer = setInterval(() => {
    deleteCooldownNow.value = Date.now();
  }, 1000);
  try {
    const response = await fetchCampuses();
    campuses.value = response.data.data ?? [];
  } catch {
    campuses.value = [];
  }
});

onUnmounted(() => {
  if (deleteCooldownTimer) clearInterval(deleteCooldownTimer);
});

async function selectProduct(id: EntityId) {
  if (String(id) === String(selectedProductId.value)) return;
  selectedProductId.value = id;
  expandedProjectIds.value = new Set();
  await loadSetup();
}

// 移动端项目默认收起，点击项目标题后再展开报名方案和实际班级。
function isProjectExpanded(projectId: EntityId) {
  return expandedProjectIds.value.has(String(projectId));
}

function toggleProject(projectId: EntityId) {
  const next = new Set(expandedProjectIds.value);
  const id = String(projectId);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  expandedProjectIds.value = next;
}

async function loadSetup() {
  const productId = selectedProductId.value;
  if (!productId) return;
  const sequence = ++requestSequence;
  loading.value = true;
  try {
    const response = await fetchGroupCourseProductSetup(productId);
    if (sequence === requestSequence) setup.value = response.data.data;
  } catch (error) {
    if (sequence === requestSequence) ElMessage.error(errorMessage(error));
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

function openProjectDialog(project?: GroupCourseProject) {
  editingProject.value = project;
  projectForm.projectName = project?.projectName ?? '';
  projectForm.description = project?.description ?? '';
  projectForm.status = project?.status ?? 'ACTIVE';
  projectDialogVisible.value = true;
}

async function saveProject() {
  const productId = selectedProductId.value;
  const projectName = projectForm.projectName.trim();
  if (!productId || !projectName) return ElMessage.warning('Enter a project name.');
  saving.value = true;
  try {
    const payload = {
      projectCode: editingProject.value?.projectCode ?? createProjectCode(projectName),
      projectName,
      description: projectForm.description.trim() || undefined,
      status: projectForm.status,
    };
    if (editingProject.value) await updateGroupCourseProject(editingProject.value.id, payload);
    else await createGroupCourseProject(productId, payload);
    projectDialogVisible.value = false;
    ElMessage.success(editingProject.value ? 'Project updated.' : 'Project created.');
    await loadSetup();
  } catch (error) { ElMessage.error(errorMessage(error)); }
  finally { saving.value = false; }
}

function openPlanDialog(project: GroupCourseProject, plan?: GroupRegistrationPlan) {
  selectedProject.value = project;
  editingPlan.value = plan;
  planForm.planCode = plan?.planCode ?? '';
  planForm.planName = plan?.planName ?? '';
  planForm.coverageType = plan?.coverageType ?? 'SESSION_COUNT';
  planForm.termCode = plan?.termCode;
  planForm.coverageStartDate = plan?.coverageStartDate;
  planForm.coverageEndDate = plan?.coverageEndDate;
  planForm.packageUnit = plan?.packageUnit ?? (planForm.coverageType === 'ROLLING_MONTHS' ? 'MONTH' : 'SESSION');
  planForm.packageQuantity = plan?.packageQuantity ?? 1;
  planForm.fullDayPrice = plan?.fullDayPrice ?? 0;
  planForm.halfDayPrice = plan?.halfDayPrice;
  planForm.currencyCode = plan?.currencyCode ?? 'USD';
  planForm.status = plan?.status ?? 'ACTIVE';
  planDialogVisible.value = true;
}

function normalizePlanFields() {
  if (planForm.coverageType === 'ROLLING_MONTHS') {
    planForm.packageUnit = 'MONTH';
    planForm.coverageStartDate = undefined;
    planForm.coverageEndDate = undefined;
  } else {
    planForm.packageUnit = 'SESSION';
    if (planForm.coverageType !== 'FIXED_DATES') {
      planForm.coverageStartDate = undefined;
      planForm.coverageEndDate = undefined;
    }
  }
}

async function savePlan() {
  const projectId = selectedProject.value?.id;
  if (!projectId || !planForm.planName.trim() || planForm.packageQuantity < 1 || planForm.fullDayPrice < 0) {
    return ElMessage.warning('Complete the required plan fields.');
  }
  if (planForm.coverageType === 'FIXED_DATES'
      && (!planForm.coverageStartDate || !planForm.coverageEndDate || planForm.coverageEndDate < planForm.coverageStartDate)) {
    return ElMessage.warning('Enter a valid plan date range.');
  }
  saving.value = true;
  try {
    const payload: GroupRegistrationPlanPayload = {
      ...planForm,
      planCode: editingPlan.value?.planCode ?? createPlanCode(planForm.planName),
      planName: planForm.planName.trim(),
      halfDayPrice: planForm.halfDayPrice == null ? undefined : planForm.halfDayPrice,
    };
    if (editingPlan.value) await updateGroupRegistrationPlan(editingPlan.value.id, payload);
    else await createGroupRegistrationPlan(projectId, payload);
    planDialogVisible.value = false;
    ElMessage.success(editingPlan.value ? 'Plan updated.' : 'Plan created.');
    await loadSetup();
  } catch (error) { ElMessage.error(errorMessage(error)); }
  finally { saving.value = false; }
}

async function removeProject(project: GroupCourseProject) {
  if (!projectDeleteReady(project)) {
    ElMessage.info(`Wait ${projectDeleteCountdown(project)} seconds before deleting this project.`);
    return;
  }
  try {
    await ElMessageBox.confirm(
      'If related data exists, this only deactivates the project. Plans, classes, schedules, holidays, enrollments, receipts, and lesson records are kept; existing classes are not cancelled. New registrations will be blocked. A project with no related data is soft-deleted from the list.',
      'Delete teaching project',
      { type: 'warning', confirmButtonText: 'Delete project', cancelButtonText: 'Cancel' },
    );
    deletingProjectId.value = String(project.id);
    await deleteGroupCourseProject(project.id);
    ElMessage.success('Project deleted or deactivated. Related records, if any, were retained.');
    await loadSetup();
  } catch (error) { if (!isCancel(error)) ElMessage.error(errorMessage(error)); }
  finally {
    if (deletingProjectId.value === String(project.id)) deletingProjectId.value = undefined;
  }
}

function projectDeleteCountdown(project: GroupCourseProject) {
  const readyAt = projectDeleteReadyAt[String(project.id)];
  return readyAt == null
    ? PROJECT_DELETE_COOLDOWN_SECONDS
    : Math.max(0, Math.ceil((readyAt - deleteCooldownNow.value) / 1000));
}

function projectDeleteReady(project: GroupCourseProject) {
  return projectDeleteCountdown(project) === 0;
}

function projectDeleteButtonText(project: GroupCourseProject) {
  const seconds = projectDeleteCountdown(project);
  return seconds > 0 ? `Delete (${seconds}s)` : 'Delete';
}

async function removePlan(plan: GroupRegistrationPlan) {
  try {
    await ElMessageBox.confirm('Plans already used by students are archived; their receipts and course access remain unchanged.', 'Delete registration plan', { type: 'warning', confirmButtonText: 'Delete', cancelButtonText: 'Cancel' });
    await deleteGroupRegistrationPlan(plan.id);
    ElMessage.success('Plan removed or archived.');
    await loadSetup();
  } catch (error) { if (!isCancel(error)) ElMessage.error(errorMessage(error)); }
}

function openClassDialog(project: GroupCourseProject, classRow?: GroupProjectClass) {
  selectedProject.value = project;
  editingClass.value = classRow;
  classForm.actualClass = classRow?.actualClass ?? '';
  classForm.campusId = classRow?.campusId;
  classForm.startDate = classRow?.startDate ?? '';
  classForm.endDate = classRow?.endDate ?? '';
  classDialogVisible.value = true;
}

async function saveClass() {
  const project = selectedProject.value;
  if (!setup.value || !project || !classForm.actualClass.trim()
      || !classForm.startDate || !classForm.endDate || classForm.endDate < classForm.startDate) {
    return ElMessage.warning('Enter the class and a valid class period.');
  }
  saving.value = true;
  try {
    const payload = {
      productId: setup.value.productId,
      groupProjectId: project.id,
      gradeBand: classForm.actualClass.trim(),
      actualClass: classForm.actualClass.trim(),
      className: classForm.actualClass.trim(),
      campusId: classForm.campusId || undefined,
      startDate: classForm.startDate,
      endDate: classForm.endDate,
    };
    if (editingClass.value) await updateClassInfo(editingClass.value.id, payload);
    else await createActualClass(payload);
    classDialogVisible.value = false;
    ElMessage.success(editingClass.value ? 'Class updated.' : 'Class created.');
    await loadSetup();
    emit('reload');
  } catch (error) { ElMessage.error(errorMessage(error)); }
  finally { saving.value = false; }
}

async function openHolidayDialog(project: GroupCourseProject) {
  holidayProject.value = project;
  holidayDialogVisible.value = true;
  resetHolidayForm();
  await loadHolidays();
}

async function loadHolidays() {
  const projectId = holidayProject.value?.id;
  if (!projectId) return;
  loadingHolidays.value = true;
  try {
    const response = await fetchGroupProjectHolidays(projectId);
    holidays.value = response.data.data ?? [];
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loadingHolidays.value = false;
  }
}

function resetHolidayForm() {
  editingHoliday.value = undefined;
  holidayForm.holidayName = '';
  holidayForm.startDate = '';
  holidayForm.endDate = '';
}

function editHoliday(holiday: GroupProjectHoliday) {
  editingHoliday.value = holiday;
  holidayForm.holidayName = holiday.holidayName;
  holidayForm.startDate = holiday.startDate;
  holidayForm.endDate = holiday.endDate;
}

async function saveHoliday() {
  const project = holidayProject.value;
  const holidayName = holidayForm.holidayName.trim();
  if (!project || !holidayName || !holidayForm.startDate || !holidayForm.endDate) {
    return ElMessage.warning('Enter a holiday name and both dates.');
  }
  if (holidayForm.endDate < holidayForm.startDate) return ElMessage.warning('End date cannot be earlier than start date.');
  const payload: GroupProjectHolidayPayload = {
    holidayName,
    startDate: holidayForm.startDate,
    endDate: holidayForm.endDate,
  };
  savingHoliday.value = true;
  try {
    const previewResponse = await previewGroupProjectHoliday(project.id, payload, editingHoliday.value?.id);
    const preview = previewResponse.data.data;
    if (preview.conflicts.length) {
      await ElMessageBox.alert(preview.conflicts.join('\n'), 'Cannot apply project holiday', {
        type: 'warning',
        confirmButtonText: 'Close',
      });
      return;
    }
    const lessonWord = preview.affectedOccurrenceCount === 1 ? 'lesson' : 'lessons';
    const enrollmentWord = preview.affectedSessionEnrollmentCount === 1 ? 'enrollment' : 'enrollments';
    const summary = `${project.projectName}: ${preview.affectedOccurrenceCount} ${lessonWord} will be skipped. ${preview.affectedSessionEnrollmentCount} session-based ${enrollmentWord} will have their end dates recalculated.`;
    await ElMessageBox.confirm(summary, 'Confirm project holiday', {
      type: 'warning',
      confirmButtonText: editingHoliday.value ? 'Save changes' : 'Add holiday',
      cancelButtonText: 'Cancel',
    });
    if (editingHoliday.value) await updateGroupProjectHoliday(project.id, editingHoliday.value.id, payload);
    else await createGroupProjectHoliday(project.id, payload);
    resetHolidayForm();
    ElMessage.success('Project holiday saved. Schedules and eligible enrollment dates were updated.');
    await loadHolidays();
    await loadSetup();
  } catch (error) {
    if (!isCancel(error)) ElMessage.error(errorMessage(error));
  } finally {
    savingHoliday.value = false;
  }
}

async function removeHoliday(holiday: GroupProjectHoliday) {
  const projectId = holidayProject.value?.id;
  if (!projectId) return;
  try {
    await ElMessageBox.confirm(
      `Delete “${holiday.holidayName}”? Future class dates that are no longer covered by another holiday will be restored.`,
      'Delete project holiday',
      { type: 'warning', confirmButtonText: 'Delete', cancelButtonText: 'Cancel' },
    );
    await deleteGroupProjectHoliday(projectId, holiday.id);
    if (editingHoliday.value?.id === holiday.id) resetHolidayForm();
    ElMessage.success('Project holiday deleted.');
    await loadHolidays();
    await loadSetup();
  } catch (error) {
    if (!isCancel(error)) ElMessage.error(errorMessage(error));
  }
}

async function openSchedule(classRow: GroupProjectClass) {
  const sequence = ++scheduleRequestSequence;
  scheduleClass.value = classRow;
  scheduleDialogVisible.value = true;
  loadingSchedule.value = true;
  scheduleForm.slots = [];
  try {
    const [classResponse, teachersResponse, roomsResponse] = await Promise.all([
      fetchClassGroups({ includeInactiveProducts: true }),
      fetchAllTeachers(),
      fetchAllClassrooms(),
    ]);
    if (sequence !== scheduleRequestSequence) return;
    const classDetail = (classResponse.data.data ?? []).find((item) => String(item.id) === String(classRow.id));
    if (!classDetail) throw new Error('Could not load this class schedule. Refresh Prices and try again.');
    teachers.value = teachersResponse.data.data ?? [];
    classrooms.value = roomsResponse.data.data ?? [];
    scheduleForm.slots = normalizeScheduleSlots(classDetail);
  } catch (error) {
    if (sequence === scheduleRequestSequence) {
      scheduleDialogVisible.value = false;
      ElMessage.error(errorMessage(error));
    }
  } finally {
    if (sequence === scheduleRequestSequence) loadingSchedule.value = false;
  }
}

function normalizeScheduleSlots(classDetail: ClassGroupItem): EditableClassScheduleSlot[] {
  const slots = classDetail.scheduleSlots ?? [];
  const hasSlotTeacher = slots.some((slot) => slot.teacherId != null);
  const hasSlotClassroom = slots.some((slot) => slot.classroomId != null || slot.deliveryMode === 'ONLINE');
  return slots.map((slot) => {
    const roomId = hasSlotClassroom ? slot.classroomId : slot.classroomId ?? classDetail.classroomId;
    const alternateRoomId = slot.alternateClassroomId;
    return {
      weekday: slot.weekday,
      startTime: slot.startTime.slice(0, 5),
      endTime: scheduleEndTimeForDisplay(slot.startTime, slot.endTime),
      plannedUnits: slot.plannedUnits ?? scheduleDurationMinutes(slot.startTime, slot.endTime) / 30,
      teacherId: hasSlotTeacher ? slot.teacherId : slot.teacherId ?? classDetail.teacherId,
      subjectName: slot.subjectName ?? '',
      alternateTeacherId: slot.alternateTeacherId,
      alternateSubjectName: slot.alternateSubjectName ?? '',
      classroomSelection: slot.deliveryMode === 'ONLINE' ? 'ONLINE' : roomId != null ? `ROOM:${roomId}` : '',
      alternateClassroomSelection: slot.alternateDeliveryMode === 'ONLINE'
        ? 'ONLINE' : alternateRoomId != null ? `ROOM:${alternateRoomId}` : '',
      rotationAnchorDate: slot.rotationAnchorDate || classDetail.startDate || '',
      rotationEnabled: Boolean(slot.alternateTeacherId || slot.alternateSubjectName || alternateRoomId || slot.alternateDeliveryMode),
    };
  });
}

function scheduleSlotLabel(slot: SlotDef) {
  const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  return `${days[slot.weekday - 1] ?? ''} ${slot.startTime}–${scheduleEndTimeForDisplay(slot.startTime, slot.endTime)}`;
}

async function saveSchedule() {
  const classId = scheduleClass.value?.id;
  if (!classId || savingSchedule.value || loadingSchedule.value) return;
  if (!scheduleForm.slots.length) return ElMessage.warning('Add at least one weekly class time.');
  savingSchedule.value = true;
  try {
    const payload: ClassScheduleUpdatePayload = {
      slots: scheduleForm.slots.map((slot) => ({
        teacherId: slot.teacherId,
        subjectName: slot.subjectName.trim() || undefined,
        alternateTeacherId: slot.rotationEnabled ? slot.alternateTeacherId : undefined,
        alternateSubjectName: slot.rotationEnabled ? slot.alternateSubjectName.trim() || undefined : undefined,
        alternateDeliveryMode: slot.rotationEnabled
          ? slot.alternateClassroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON'
          : undefined,
        alternateClassroomId: slot.rotationEnabled && slot.alternateClassroomSelection.startsWith('ROOM:')
          ? slot.alternateClassroomSelection.slice(5) : undefined,
        rotationAnchorDate: slot.rotationEnabled
          ? slot.rotationAnchorDate || scheduleClass.value?.startDate || undefined
          : undefined,
        deliveryMode: slot.classroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON',
        classroomId: slot.classroomSelection.startsWith('ROOM:') ? slot.classroomSelection.slice(5) : undefined,
        weekday: slot.weekday,
        startTime: slot.startTime,
        endTime: scheduleEndTimeForApi(slot.endTime),
        plannedUnits: slot.plannedUnits,
      })),
    };
    await updateClassSchedule(classId, payload);
    ElMessage.success('Class schedule updated.');
    scheduleDialogVisible.value = false;
    await loadSetup();
    emit('reload');
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    savingSchedule.value = false;
  }
}

function coverageLabel(plan: GroupRegistrationPlan) {
  if (plan.coverageType === 'FIXED_DATES') return plan.termCode || 'Fixed date range';
  if (plan.coverageType === 'ROLLING_MONTHS') return 'Starts on selected date';
  return 'Selected number of scheduled class dates';
}

function money(value: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(value || 0);
}

function createProjectCode(name: string) {
  return `PROJECT-${slug(name)}-${Date.now().toString(36).toUpperCase()}`.slice(0, 64);
}

function createPlanCode(name: string) {
  return `PLAN-${slug(name)}-${Date.now().toString(36).toUpperCase()}`.slice(0, 64);
}

function slug(value: string) {
  return value.normalize('NFKD').replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '').toUpperCase().slice(0, 24) || 'NEW';
}

function isCancel(error: unknown) {
  return error === 'cancel' || error === 'close';
}

function errorMessage(error: any) {
  return error?.response?.data?.message ?? error?.message ?? 'Operation failed.';
}
</script>

<style scoped>
.mobile-plan-list,.mobile-class-list { display:grid; gap:8px; }
.mobile-model-card { min-width:0; padding:12px; border:1px solid var(--el-border-color); border-radius:10px; background:#fff; }
.mobile-model-card header { display:flex; align-items:flex-start; justify-content:space-between; gap:8px; }
.mobile-model-card header strong { color:var(--el-text-color-primary); font-size:14px; overflow-wrap:anywhere; }
.mobile-model-card > span,.mobile-model-card > small { display:block; margin-top:5px; color:var(--el-text-color-secondary); font-size:12px; }
.mobile-model-card dl { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; margin:10px 0 0; }
.mobile-model-card dl > div { display:grid; min-width:0; gap:3px; }
.mobile-model-card dt { color:var(--el-text-color-secondary); font-size:11px; }
.mobile-model-card dd { margin:0; color:var(--el-text-color-primary); font-size:13px; font-weight:600; overflow-wrap:anywhere; }
.mobile-model-card__actions { display:flex; flex-wrap:wrap; gap:6px; margin-top:11px; }
.mobile-model-card__actions .el-button { margin:0; }

.group-model-layout { display:grid; grid-template-columns:240px minmax(0,1fr); gap:20px; min-height:460px; }
.group-model-products { align-self:start; display:flex; flex-direction:column; gap:8px; padding:14px; border:1px solid var(--el-border-color); border-radius:12px; background:#fbfcfe; }
.group-model-products__label,.group-model-eyebrow { color:var(--el-text-color-secondary); font-size:12px; font-weight:700; letter-spacing:.08em; text-transform:uppercase; }
.group-model-eyebrow--with-help { display:flex; align-items:center; gap:6px; }
.group-model-help { display:inline-flex; align-items:center; justify-content:center; width:16px; height:16px; padding:0; border:1px solid var(--el-border-color); border-radius:50%; color:var(--el-text-color-secondary); background:#fff; font:inherit; font-size:10px; font-weight:700; line-height:1; letter-spacing:0; cursor:help; }
.group-model-help:hover { border-color:var(--el-color-primary); color:var(--el-color-primary); }
.group-model-help:focus-visible { border-color:var(--el-color-primary); color:var(--el-color-primary); outline:2px solid var(--el-color-primary-light-7); outline-offset:2px; }
.group-model-product { display:flex; flex-direction:column; gap:4px; padding:12px; border:1px solid transparent; border-radius:8px; color:var(--el-text-color-primary); background:transparent; text-align:left; cursor:pointer; }
.group-model-product:hover { background:#f2f6fc; }
.group-model-product--active { border-color:var(--el-color-primary-light-5); background:var(--el-color-primary-light-9); }
.group-model-product small,.group-project-card__header small,.group-project-section__heading span,.group-cell-subtitle { color:var(--el-text-color-secondary); font-size:12px; }
.group-model-content { min-width:0; }
.mobile-project-action,.mobile-project-summary { display:none; }
.group-model-header { display:flex; justify-content:space-between; align-items:center; gap:20px; margin:2px 0 16px; }
.group-model-header h2 { margin:4px 0; font-size:22px; }
.group-model-header p,.group-project-card__header p { margin:4px 0; color:var(--el-text-color-secondary); }
.group-project-card { margin-top:16px; padding:18px; border:1px solid var(--el-border-color); border-radius:12px; background:#fff; }
.group-project-card__header { display:flex; align-items:center; justify-content:space-between; gap:16px; padding-bottom:14px; border-bottom:1px solid var(--el-border-color-lighter); }
.group-project-card__title { display:flex; align-items:center; gap:10px; }
.group-project-card__title h3 { margin:0; font-size:18px; }
.group-project-card__actions { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:4px; }
.group-project-section { padding-top:16px; }
.group-project-section__heading { display:flex; flex-wrap:wrap; align-items:baseline; gap:10px; margin-bottom:10px; }
.group-project-section__heading h4 { margin:0; font-size:15px; }
.group-cell-subtitle { display:block; margin-top:3px; }
.muted { color:var(--el-text-color-placeholder); }
.plan-form__row { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
.holiday-form { margin-top:16px; }
.holiday-form__actions { display:flex; justify-content:flex-end; gap:8px; margin-bottom:16px; }
.group-class-schedule-dialog__body { max-height:min(72vh, 920px); overflow:auto; padding-right:4px; }
.group-class-schedule-dialog__calendar { margin:14px 0; }
.group-class-schedule-dialog__slots { display:grid; gap:10px; }
.group-class-schedule-slot { padding:14px; border:1px solid var(--el-border-color); border-radius:10px; background:#fbfcfe; }
.group-class-schedule-slot__header { display:flex; align-items:center; gap:10px; margin-bottom:12px; }
.group-class-schedule-slot__header > span { color:var(--el-text-color-secondary); font-size:12px; }
.group-class-schedule-slot__rotation { display:flex; align-items:center; gap:8px; margin-left:auto; color:var(--el-text-color-regular); font-size:13px; }
.group-class-schedule-slot__fields { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }
.group-class-schedule-slot__fields label { display:grid; gap:6px; min-width:0; }
.group-class-schedule-slot__fields label > span { color:var(--el-text-color-secondary); font-size:12px; }
.group-class-schedule-slot__fields--alternate { margin-top:12px; padding-top:12px; border-top:1px solid var(--el-border-color-lighter); }
.group-class-schedule-slot__anchor { grid-column:1 / -1; max-width:320px; }
.group-class-schedule-slot__anchor :deep(.el-date-editor) { width:100%; }
@media (max-width:900px) { .group-model-layout { grid-template-columns:1fr; } .group-model-products { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); } .group-model-products__label { grid-column:1/-1; } .group-model-header,.group-project-card__header { align-items:flex-start; flex-direction:column; } .group-project-card__actions { justify-content:flex-start; } }
@media (max-width:700px) { .plan-form__row,.group-class-schedule-slot__fields { grid-template-columns:1fr; } .group-class-schedule-slot__anchor { grid-column:auto; max-width:none; } }

@media (max-width:640px) {
  .group-model-layout { display:flex; flex-direction:column; gap:10px; min-height:0; }
  .mobile-product-switcher { display:grid; grid-template-columns:auto minmax(0,1fr); align-items:center; gap:10px; }
  .mobile-product-switcher > label { color:var(--el-text-color-secondary); font-size:12px; font-weight:600; }
  .mobile-product-switcher :deep(.el-select__wrapper) { min-height:38px; }
  .group-model-header { display:flex; flex-direction:row; align-items:center; gap:8px; margin:2px 0 8px; }
  .group-model-header h2 { margin:0; font-size:16px; line-height:1.3; }
  .group-model-header__description,.group-model-eyebrow { display:none; }
  .group-model-header > .el-button { flex:none; min-height:34px; padding:0 10px; }
  .desktop-project-action,.desktop-project-summary { display:none; }
  .mobile-project-action,.mobile-project-summary { display:inline; }
  .group-project-card { margin-top:8px; padding:12px; border-color:#e5eaf1; border-radius:10px; box-shadow:0 1px 2px rgb(28 55 85 / 4%); }
  .group-project-card__header { display:grid; grid-template-columns:minmax(0,1fr); gap:9px; padding-bottom:0; border-bottom:0; }
  .group-project-card__title { min-width:0; gap:7px; }
  .group-project-card__title h3 { overflow:hidden; font-size:14px; text-overflow:ellipsis; white-space:nowrap; }
  .group-project-card__header p { display:none; }
  .group-project-card__header small { font-size:11px; }
  .mobile-project-toggle { display:inline-flex; flex:none; align-items:center; justify-content:center; width:28px; height:28px; margin-left:auto; padding:0; border:1px solid #dbe3ed; border-radius:7px; background:#fff; color:#53657a; cursor:pointer; }
  .mobile-project-toggle__icon--expanded { transform:rotate(180deg); }
  .mobile-project-actions { display:flex; align-items:center; gap:6px; }
  .mobile-project-actions .el-button { min-height:32px; margin:0; padding:0 10px; font-size:12px; }
  .mobile-project-actions .mobile-project-actions__more { width:34px; padding:0; }
  .group-project-details { margin-top:12px; padding-top:2px; border-top:1px solid #edf1f5; }
  .group-project-section { padding-top:12px; }
  .group-project-section + .group-project-section { margin-top:12px; border-top:1px solid #edf1f5; }
  .group-project-section__heading { gap:4px; margin-bottom:8px; }
  .group-project-section__heading h4 { font-size:13px; }
  .group-project-section__description { display:none; }
  .mobile-model-card { padding:10px; border-color:#e6ebf2; border-radius:8px; }
  .mobile-model-card + .mobile-model-card { margin-top:7px; }
  .mobile-model-card > header { gap:8px; }
  .mobile-model-card > header strong { min-width:0; font-size:13px; }
  .mobile-model-card > span { font-size:12px; }
  .mobile-model-card > small { font-size:11px; }
  .mobile-model-card dl { gap:6px; margin:8px 0 0; }
  .mobile-model-card dl dt { font-size:10px; }
  .mobile-model-card dl dd { font-size:12px; }
  .mobile-model-card__actions { align-items:center; gap:4px; margin-top:7px; }
  .mobile-model-card__actions .el-button { min-height:30px; padding:0 9px; font-size:12px; }
}
</style>
