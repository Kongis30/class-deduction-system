<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Classrooms</span>
        <div class="panel-header__actions">
          <button
            v-if="appStore.isMobileViewport"
            type="button"
            class="mobile-search-trigger"
            :aria-expanded="mobileSearchExpanded"
            aria-label="Open classroom search and filters"
            @click="mobileSearchExpanded = !mobileSearchExpanded"
          >
            <el-icon><Search /></el-icon>
          </button>
          <el-dropdown v-if="appStore.isMobileViewport" trigger="click" placement="bottom-end">
            <el-button aria-label="More classroom actions"><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openBackfillSchedule()">Assign rooms</el-dropdown-item>
                <el-dropdown-item @click="openRoomCalendar()">Room calendar</el-dropdown-item>
                <el-dropdown-item divided @click="openClassroomDialog()">Add classroom</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <template v-else>
            <el-button @click="openBackfillSchedule()">Assign rooms</el-button>
            <el-button @click="openRoomCalendar()">Room calendar</el-button>
            <el-button type="primary" @click="openClassroomDialog()">
              <el-icon><Plus /></el-icon>
              Add classroom
            </el-button>
          </template>
        </div>
      </div>

      <el-alert
        class="workflow-note"
        type="info"
        :closable="false"
        show-icon
        title="Registration does not require room assignment; rooms can be backfilled later."
        description="Use Room calendar to assign pending rooms and verify occupancy in one place."
      />

      <section v-show="!appStore.isMobileViewport || mobileSearchExpanded" class="mobile-search-panel">
      <div v-if="appStore.isMobileViewport" class="mobile-search-panel__header">
        <span>Search classrooms</span>
        <button type="button" aria-label="Close search" @click="mobileSearchExpanded = false">
          <el-icon><Close /></el-icon>
        </button>
      </div>
      <el-form class="filters" :model="filters" inline>
        <el-form-item label="Name" class="filters__item">
          <el-input
            v-model="filters.keyword"
            placeholder="Search by name"
            clearable
            style="width: 180px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Campus" class="filters__item">
          <el-select
            v-model="filters.campus"
            placeholder="All"
            clearable
            style="width: 140px;"
            @change="handleFilterChange"
          >
            <el-option v-for="campus in campuses" :key="campus.campusName" :label="campus.campusName" :value="campus.campusName" />
          </el-select>
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
      </section>

      <el-table
        v-if="!appStore.isMobileViewport"
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="product-table"
        :show-empty="true"
      >
        <template #empty>
          <div class="classroom-empty empty-state">
            <strong>{{ filters.keyword || filters.campus || filters.status ? 'No classrooms match the current filters.' : 'No classrooms yet.' }}</strong>
            <span>{{ filters.keyword || filters.campus || filters.status ? 'Clear filters or add a classroom for future schedule backfill.' : 'Registration can continue without rooms. Add rooms when schedules are ready to backfill.' }}</span>
          </div>
        </template>
        <el-table-column label="Room name" min-width="180">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.roomName }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Campus" min-width="120" prop="campus" />
        <el-table-column label="Capacity" min-width="90" align="center" prop="capacity" />
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
              <el-button text type="primary" @click="openRoomCalendar(row)">Calendar</el-button>
              <el-button text type="primary" @click="openBackfillSchedule(row)">Assign room</el-button>
              <el-button text type="primary" @click="openClassroomDialog(row)">Edit</el-button>
              <el-button text type="danger" @click="handleDelete(row)">Delete</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-else v-loading="loading" class="mobile-classroom-list">
        <article v-for="room in tableData" :key="room.id" class="mobile-classroom-card">
          <header class="mobile-classroom-card__heading">
            <strong>{{ room.roomName }}</strong>
            <el-tag size="small" :type="room.status === 'ACTIVE' ? 'success' : 'info'">
              {{ room.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </header>
          <dl class="mobile-classroom-card__details">
            <div><dt>Campus</dt><dd>{{ room.campus }}</dd></div>
            <div><dt>Capacity</dt><dd>{{ room.capacity }} students</dd></div>
          </dl>
          <div class="mobile-classroom-card__actions">
            <el-button @click="openRoomCalendar(room)">Calendar</el-button>
            <el-button type="primary" plain @click="openBackfillSchedule(room)">Assign room</el-button>
            <el-button @click="openClassroomDialog(room)">Edit</el-button>
            <el-button text type="danger" @click="handleDelete(room)">Delete</el-button>
          </div>
        </article>
        <el-empty v-if="!loading && tableData.length === 0" :description="filters.keyword || filters.campus || filters.status ? 'No classrooms match the current filters.' : 'No classrooms yet.'" />
      </div>

      <div class="table-footer">
        <span>Showing {{ pagination.start }}–{{ pagination.end }} of {{ pagination.total }}</span>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          :layout="appStore.isMobileViewport ? 'prev, pager, next' : 'prev, pager, next, sizes'"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </section>

    <!-- Classroom dialog -->
    <el-dialog
      v-model="classroomDialogVisible"
      width="540px"
      :close-on-click-modal="false"
      class="classroom-dialog"
    >
      <template #header>
        <h3>{{ editingId ? 'Edit classroom' : 'New classroom' }}</h3>
      </template>

      <el-form ref="classroomFormRef" :model="classroomForm" :rules="classroomFormRules" inline>
        <el-form-item label="Room name" prop="roomName" style="flex: 1;">
          <el-input v-model="classroomForm.roomName" placeholder="e.g. Room 101" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="Campus" prop="campus">
          <el-select v-model="classroomForm.campus" placeholder="Select campus" style="width: 140px;">
            <el-option v-for="campus in campuses" :key="campus.campusName" :label="campus.campusName" :value="campus.campusName" />
          </el-select>
        </el-form-item>
        <el-form-item label="Capacity">
          <el-input-number v-model="classroomForm.capacity" :min="1" :max="9999" :controls="false" style="width: 100px;" />
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="classroomForm.status" style="width: 110px;">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Notes" style="flex: 1;">
          <el-input v-model="classroomForm.notes" placeholder="Optional notes" style="width: 100%;" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="classroomDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleClassroomSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import axios from 'axios';
import { Close, MoreFilled, Plus, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAppStore } from '@/stores/app';

import { extractErrorMessage } from '@/api/http';
import {
  createClassroom,
  deleteClassroom,
  fetchCampuses,
  fetchClassrooms,
  updateClassroom,
  type ClassroomQuery,
  type ClassroomSavePayload,
} from '@/api/base-data';
import type { CampusItem, ClassroomItem, EntityId } from '@/types/domain';

const loading = ref(false);
const submitting = ref(false);
const classroomDialogVisible = ref(false);
const editingId = ref<EntityId | null>(null);
const classroomFormRef = ref();
const campuses = ref<CampusItem[]>([]);
const CLASSROOM_IN_USE_MESSAGE = 'This classroom is still in use. Please reassign related classes or schedules first.';
const router = useRouter();
const appStore = useAppStore();
const mobileSearchExpanded = ref(false);

// ─── Filters & Pagination ───────────────────────────────
const filters = reactive({
  keyword: '',
  campus: '',
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

const tableData = ref<ClassroomItem[]>([]);

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

function openRoomCalendar(row?: ClassroomItem) {
  router.push({
    path: '/classrooms/occupancy',
    query: row ? { classroomId: row.id } : undefined,
  });
}

function openBackfillSchedule(row?: ClassroomItem) {
  router.push({
    path: '/classrooms/occupancy',
    query: row ? { classroomId: row.id } : undefined,
  });
}

// ─── Load Data ──────────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const params: ClassroomQuery = {
      keyword: filters.keyword || undefined,
      campus: filters.campus || undefined,
      status: filters.status || undefined,
      pageNum: pagination.page,
      pageSize: pagination.size,
    };
    const res = await fetchClassrooms(params);
    const data = res.data.data;
    tableData.value = data.records;
    pagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load classrooms.');
  } finally {
    loading.value = false;
  }
}

async function loadCampuses() {
  try {
    const res = await fetchCampuses();
    campuses.value = res.data.data;
  } catch {
    ElMessage.error('Failed to load campuses.');
  }
}

// ─── Form ──────────────────────────────────────────────
const classroomForm = reactive<ClassroomSavePayload>({
  roomName: '',
  campus: '',
  capacity: 10,
  status: 'ACTIVE',
  notes: '',
});

const classroomFormRules = {
  roomName: [{ required: true, message: 'Room name is required' }],
  campus: [{ required: true, message: 'Campus is required' }],
};

function openClassroomDialog(row?: ClassroomItem) {
  editingId.value = row?.id ?? null;
  if (row) {
    Object.assign(classroomForm, {
      roomName: row.roomName,
      campus: row.campus,
      capacity: row.capacity,
      status: row.status,
      notes: row.notes ?? '',
    });
  } else {
    Object.assign(classroomForm, {
      roomName: '',
      campus: campuses.value[0]?.campusName || '',
      capacity: 10,
      status: 'ACTIVE',
      notes: '',
    });
  }
  classroomFormRef.value?.clearValidate();
  classroomDialogVisible.value = true;
}

async function handleClassroomSubmit() {
  const valid = await classroomFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateClassroom(editingId.value, { ...classroomForm });
      ElMessage.success('Classroom updated.');
    } else {
      await createClassroom({ ...classroomForm });
      ElMessage.success('Classroom created.');
    }
    classroomDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

// ─── Delete ─────────────────────────────────────────────
async function handleDelete(row: ClassroomItem) {
  try {
    await ElMessageBox.confirm(
      `Delete classroom "${row.roomName}"?`,
      'Confirm delete',
      { type: 'warning' },
    );
    await deleteClassroom(row.id);
    ElMessage.success('Deleted.');
    await loadData();
  } catch (error) {
    if (isDeleteCancel(error)) {
      return;
    }
    ElMessage.error(resolveDeleteErrorMessage(error));
  }
}

function isDeleteCancel(error: unknown): boolean {
  return error === 'cancel' || error === 'close';
}

function resolveDeleteErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { code?: string; message?: string } | undefined;
    if (data?.code === 'CLASSROOM_HAS_REFERENCES') {
      return CLASSROOM_IN_USE_MESSAGE;
    }
  }
  return extractErrorMessage(error);
}

// ─── Init ────────────────────────────────────────────────
loadCampuses();
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
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.panel-header__actions {
  display: flex;
  gap: 8px;
}

.filters {
  margin-bottom: 12px;
}

.workflow-note {
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

.classroom-empty {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  min-width: min(520px, 100%);
}

.classroom-empty strong {
  color: #303133;
  font-weight: 600;
}

.classroom-empty span {
  color: #606266;
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

.mobile-classroom-list { display: grid; gap: 10px; }
.mobile-classroom-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}
.mobile-classroom-card__heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.mobile-classroom-card__heading strong { overflow-wrap: anywhere; color: #26384c; font-size: 15px; }
.mobile-classroom-card__details { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 12px 0 0; }
.mobile-classroom-card__details > div { display: grid; gap: 4px; }
.mobile-classroom-card__details dt { color: #718096; font-size: 12px; }
.mobile-classroom-card__details dd { margin: 0; color: #334155; font-size: 14px; }
.mobile-classroom-card__actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.mobile-classroom-card__actions .el-button { margin: 0; }

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* Classroom dialog */
.classroom-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.classroom-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 14px 20px;
  margin-right: 0;
}

.classroom-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

@media (max-width: 640px) {
  .course-product-panel { padding: 12px; }
  .panel-header { align-items: center; margin-bottom: 10px; }
  .panel-header__title { display: none; }
  .panel-header__actions { margin-left: auto; gap: 6px; }
  .panel-header__actions > .el-dropdown > .el-button { width: 36px; height: 36px; min-height: 36px; padding: 0; }
  .workflow-note :deep(.el-alert__description) { display: none; }
  .filters { display: grid; grid-template-columns: 1fr; gap: 4px; }
  .filters__item, .filters__item :deep(.el-input), .filters__item :deep(.el-select) { width: 100% !important; }
  .table-footer { align-items: stretch; flex-direction: column; gap: 8px; }
  .table-footer :deep(.el-pagination) { width: 100%; min-width: 0; }
  .classroom-dialog :deep(.el-dialog__header),
  .classroom-dialog :deep(.el-dialog__body),
  .classroom-dialog :deep(.el-dialog__footer) { padding-right: 14px; padding-left: 14px; }
}
</style>
