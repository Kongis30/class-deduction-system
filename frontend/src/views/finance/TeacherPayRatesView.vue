<template>
  <div class="pr-page">

    <aside class="pr-sidebar">
      <div class="pr-sidebar__search">
        <el-input v-model="teacherKeyword" placeholder="Search teacher..." clearable size="small" />
      </div>
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


    <main class="pr-content">
      <div v-if="!selectedTeacherId" class="pr-content__placeholder">
        Select a teacher from the left panel to set their hourly rates.
      </div>

      <template v-else-if="selectedTeacher">
        <div class="pr-content__header">
          <div class="pr-content__title">
            <strong>{{ selectedTeacher.teacherName }}</strong>
            <span class="pr-content__no">{{ selectedTeacher.teacherNo }}</span>
          </div>
          <div class="pr-content__hint">
            Set a default hourly rate by course, then add a student override only when a one-on-one student has a different rate.
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="rateRows"
          row-key="productId"
          class="pr-table"
        >
          <el-table-column label="Course Product" min-width="180">
            <template #default="{ row }">
              <div class="pr-product-cell">
                <span class="pr-product-cell__name">{{ row.productName }}</span>
                <span class="pr-product-cell__subject">{{ row.subjectName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Hourly Rate ($)" width="150" align="center">
            <template #header>
              <span class="pr-header-with-info">
                Hourly Rate ($)
                <el-tooltip
                  content="The amount paid to the teacher per hour of class. Only products with a rate set will be included in payment calculations."
                  placement="top"
                >
                  <el-icon class="pr-info-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <template #default="{ row }">
              <span :class="{ 'pr-rate--set': row.hourlyRate > 0, 'pr-rate--unset': row.hourlyRate === 0 }">
                {{ row.hourlyRate > 0 ? '$' + row.hourlyRate.toFixed(2) : '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="Status" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.hourlyRate > 0" size="small" type="success">Set</el-tag>
              <el-tag v-else size="small" type="info">Not set</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-button
                text
                type="primary"
                size="small"
                @click="openEditDialog(row)"
              >
                Edit
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <section class="pr-overrides">
          <div class="pr-overrides__heading">
            <strong>Student-specific overrides</strong>
            <span>Overrides take precedence over the default course rate during 1-on-1 settlement.</span>
          </div>
          <el-table :data="studentOverrideRows" size="small" class="pr-table" empty-text="No student-specific overrides.">
            <el-table-column label="Student" min-width="160" prop="studentName" />
            <el-table-column label="Course product" min-width="180" prop="productName" />
            <el-table-column label="Hourly rate" width="130" align="right">
              <template #default="{ row }">${{ row.hourlyRate.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Action" width="90" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openOverrideDialog(row)">Edit</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </template>
    </main>


    <el-dialog
      v-model="editDialogVisible"
      width="400px"
      :close-on-click-modal="false"
      class="pr-dialog"
    >
      <template #header>
        <h3>Set Hourly Rate</h3>
      </template>

      <template v-if="editingRow">
        <div class="pr-dialog__product">
          <strong>{{ editingRow.productName }}</strong>
          <span>{{ editingRow.subjectName }}</span>
        </div>
        <el-form label-position="top" class="pr-dialog__form">
          <el-form-item label="Rate scope">
            <el-select v-model="editingStudentId" clearable placeholder="Default for all students" style="width: 100%;">
              <el-option label="Default for all students" :value="undefined" />
              <el-option
                v-for="student in allStudents"
                :key="student.id"
                :label="student.studentName"
                :value="student.id"
              />
            </el-select>
            <div class="pr-dialog__hint">Choose a student only to create a 1-on-1 override.</div>
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
              The amount paid to the teacher per hour of class for this course product.
              Set to $0 to exclude this product from payment calculation.
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
import { ElMessage } from 'element-plus';
import { InfoFilled } from '@element-plus/icons-vue';
import { computed, onMounted, ref } from 'vue';

import { fetchPayRates, savePayRate, type TeacherPayRateSavePayload } from '@/api/finance';
import { fetchAllStudents, fetchAllTeachers } from '@/api/base-data';
import { fetchCourseProducts } from '@/api/base-data';
import type { EntityId } from '@/types/domain';

const teacherKeyword = ref('');
const teachersLoading = ref(false);
const loading = ref(false);

const allTeachers = ref<{ id: EntityId; teacherName: string; teacherNo: string }[]>([]);
const allProducts = ref<{ id: EntityId; productCode: string; productName: string; subjectName: string }[]>([]);
const allStudents = ref<{ id: EntityId; studentName: string }[]>([]);

const selectedTeacherId = ref<EntityId | null>(null);

// Row type: a product with editable rate
interface RateRow {
  productId: EntityId;
  productCode: string;
  productName: string;
  subjectName: string;
  rateId?: EntityId;
  hourlyRate: number;
  originalRate: number; // for change detection
  notes?: string;
  studentId?: EntityId;
  studentName?: string;
}

const rateRows = ref<RateRow[]>([]);
const studentOverrideRows = ref<RateRow[]>([]);
const savingProductId = ref<EntityId | null>(null);



const editDialogVisible = ref(false);
const editingRow = ref<RateRow | null>(null);
const editingRate = ref(0);
const editingStudentId = ref<EntityId | undefined>();

function openEditDialog(row: RateRow) {
  editingRow.value = row;
  editingRate.value = row.hourlyRate;
  editingStudentId.value = undefined;
  editDialogVisible.value = true;
}

function openOverrideDialog(row: RateRow) {
  editingRow.value = row;
  editingRate.value = row.hourlyRate;
  editingStudentId.value = row.studentId;
  editDialogVisible.value = true;
}

async function handleEditSave() {
  if (!editingRow.value || !selectedTeacherId.value) return;
  const row = editingRow.value;
  const newRate = editingRate.value;
  savingProductId.value = row.productId;
  try {
    const payload: TeacherPayRateSavePayload = {
      teacherId: selectedTeacherId.value,
      courseProductId: row.productId,
      studentId: editingStudentId.value,
      hourlyRate: newRate,
      notes: undefined,
    };
    const res = await savePayRate(payload);
    await loadRates();
    editDialogVisible.value = false;
    ElMessage.success(`Rate saved for ${row.productName}.`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to save rate.');
  } finally {
    savingProductId.value = null;
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


    const rateMap = new Map<string, any>();
    for (const r of existingRates) {
      if (r.studentId == null) {
        rateMap.set(String(r.courseProductId), r);
      }
    }

    rateRows.value = allProducts.value.map(p => {
      const existing = rateMap.get(String(p.id));
      return {
        productId: p.id,
        productCode: p.productCode,
        productName: p.productName,
        subjectName: p.subjectName || '',
        rateId: existing?.id,
        hourlyRate: existing?.hourlyRate ?? 0,
        originalRate: existing?.hourlyRate ?? 0,
        notes: existing?.notes,
      };
    });
    studentOverrideRows.value = existingRates
      .filter(rate => rate.studentId != null)
      .map(rate => ({
        productId: rate.courseProductId,
        productCode: rate.productCode,
        productName: rate.productName,
        subjectName: rate.subjectName || '',
        rateId: rate.id,
        hourlyRate: rate.hourlyRate,
        originalRate: rate.hourlyRate,
        notes: rate.notes,
        studentId: rate.studentId,
        studentName: rate.studentName,
      }));
  } catch {
    ElMessage.error('Failed to load pay rates.');
  } finally {
    loading.value = false;
  }
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
  height: 100%;
  min-height: 500px;
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
  overflow-y: auto;
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
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
  overflow: auto;
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

/* ─── Table ──────────────────────────────────────────── */

.pr-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.pr-overrides {
  margin-top: 20px;
}

.pr-overrides__heading {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-bottom: 8px;
}

.pr-overrides__heading strong {
  color: #303133;
  font-size: 13px;
}

.pr-overrides__heading span {
  color: #909399;
  font-size: 12px;
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

.pr-dialog__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
