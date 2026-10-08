<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Campuses</span>
        <div class="panel-header__actions">
          <el-button type="primary" @click="openCampusDialog()">
            <el-icon><Plus /></el-icon>
            Add campus
          </el-button>
        </div>
      </div>

      <el-table
        v-if="!appStore.isMobileViewport"
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="product-table"
        :show-empty="true"
      >
        <el-table-column label="Campus name" min-width="200">
          <template #default="{ row }">
            <strong>{{ row.campusName }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="Status" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Pickup fee" min-width="110" align="right">
          <template #default="{ row }">${{ (row.defaultPickupFeeAmount ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="Drop-off fee" min-width="110" align="right">
          <template #default="{ row }">${{ (row.defaultDropoffFeeAmount ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="Notes" min-width="200" prop="notes" show-overflow-tooltip />
        <el-table-column label="Actions" min-width="140" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" @click="openCampusDialog(row)">Edit</el-button>
              <el-button text type="danger" @click="handleDelete(row)">Delete</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-else v-loading="loading" class="mobile-campus-list">
        <article v-for="campus in tableData" :key="campus.id" class="mobile-campus-card">
          <header class="mobile-campus-card__heading">
            <strong>{{ campus.campusName }}</strong>
            <el-tag size="small" :type="campus.status === 'ACTIVE' ? 'success' : 'info'">
              {{ campus.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
            </el-tag>
          </header>
          <dl class="mobile-campus-card__details">
            <div><dt>Pickup fee</dt><dd>${{ (campus.defaultPickupFeeAmount ?? 0).toFixed(2) }}</dd></div>
            <div><dt>Drop-off fee</dt><dd>${{ (campus.defaultDropoffFeeAmount ?? 0).toFixed(2) }}</dd></div>
          </dl>
          <p v-if="campus.notes" class="mobile-campus-card__notes">{{ campus.notes }}</p>
          <div class="mobile-campus-card__actions">
            <el-button type="primary" plain @click="openCampusDialog(campus)">Edit campus</el-button>
            <el-button text type="danger" @click="handleDelete(campus)">Delete</el-button>
          </div>
        </article>
        <el-empty v-if="!loading && tableData.length === 0" description="No campuses configured." />
      </div>
    </section>

    <!-- Campus dialog -->
    <el-dialog
      v-model="campusDialogVisible"
      width="540px"
      :close-on-click-modal="false"
      class="campus-dialog"
    >
      <template #header>
        <h3>{{ editingId ? 'Edit campus' : 'New campus' }}</h3>
      </template>

      <el-form ref="campusFormRef" :model="campusForm" :rules="campusFormRules" label-position="top">
        <el-form-item label="Campus name" prop="campusName">
          <el-input v-model="campusForm.campusName" placeholder="e.g. Main Building" />
        </el-form-item>
        <el-form-item label="Display order">
          <el-input-number v-model="campusForm.displayOrder" :min="1" :max="999" :controls="false" />
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="campusForm.status" style="width: 100%;">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Default pickup fee">
          <el-input-number v-model="campusForm.defaultPickupFeeAmount" :min="0" :precision="2" :controls="false" />
        </el-form-item>
        <el-form-item label="Default drop-off fee">
          <el-input-number v-model="campusForm.defaultDropoffFeeAmount" :min="0" :precision="2" :controls="false" />
        </el-form-item>
        <el-form-item label="Notes">
          <el-input v-model="campusForm.notes" type="textarea" :rows="2" placeholder="Optional notes" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="campusDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reactive, ref } from 'vue';
import { useAppStore } from '@/stores/app';

import {
  createCampus,
  deleteCampus,
  fetchCampuses,
  updateCampus,
  type CampusSavePayload,
} from '@/api/base-data';
import type { CampusItem } from '@/types/domain';

const loading = ref(false);
const appStore = useAppStore();
const submitting = ref(false);
const campusDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const campusFormRef = ref();

const tableData = ref<CampusItem[]>([]);

const campusForm = reactive<CampusSavePayload>({
  campusName: '',
  displayOrder: 99,
  status: 'ACTIVE',
  defaultPickupFeeAmount: 0,
  defaultDropoffFeeAmount: 0,
  notes: '',
});

const campusFormRules = {
  campusName: [{ required: true, message: 'Campus name is required' }],
};

// ─── Load Data ──────────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const res = await fetchCampuses();
    tableData.value = res.data.data;
  } catch {
    ElMessage.error('Failed to load campuses.');
  } finally {
    loading.value = false;
  }
}

// ─── Form ──────────────────────────────────────────────
function openCampusDialog(row?: CampusItem) {
  editingId.value = row?.id ?? null;
  if (row) {
    Object.assign(campusForm, {
      campusName: row.campusName,
      displayOrder: row.displayOrder,
      status: row.status,
      defaultPickupFeeAmount: row.defaultPickupFeeAmount ?? 0,
      defaultDropoffFeeAmount: row.defaultDropoffFeeAmount ?? 0,
      notes: row.notes ?? '',
    });
  } else {
    Object.assign(campusForm, {
      campusName: '',
      displayOrder: 99,
      status: 'ACTIVE',
      defaultPickupFeeAmount: 0,
      defaultDropoffFeeAmount: 0,
      notes: '',
    });
  }
  campusFormRef.value?.clearValidate();
  campusDialogVisible.value = true;
}

async function handleSubmit() {
  const valid = await campusFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateCampus(editingId.value, { ...campusForm });
      ElMessage.success('Campus updated.');
    } else {
      await createCampus({ ...campusForm });
      ElMessage.success('Campus created.');
    }
    campusDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

// ─── Delete ─────────────────────────────────────────────
async function handleDelete(row: CampusItem) {
  try {
    await ElMessageBox.confirm(
      `Delete campus "${row.campusName}"?`,
      'Confirm delete',
      { type: 'warning' },
    );
    await deleteCampus(row.id);
    ElMessage.success('Deleted.');
    loadData();
  } catch {
    // User cancelled
  }
}

// ─── Init ────────────────────────────────────────────────
loadData();
</script>

<style scoped>
.mobile-campus-list { display: grid; gap: 10px; }
.mobile-campus-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}
.mobile-campus-card__heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.mobile-campus-card__heading strong { overflow-wrap: anywhere; color: #26384c; font-size: 15px; }
.mobile-campus-card__details { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 12px 0 0; }
.mobile-campus-card__details > div { display: grid; gap: 4px; }
.mobile-campus-card__details dt { color: #718096; font-size: 12px; }
.mobile-campus-card__details dd { margin: 0; color: #334155; font-size: 14px; }
.mobile-campus-card__notes { margin: 12px 0 0; color: #64748b; font-size: 13px; overflow-wrap: anywhere; }
.mobile-campus-card__actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.mobile-campus-card__actions .el-button { margin: 0; }

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

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.table-actions {
  display: flex;
  gap: 4px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.campus-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 14px 20px;
  margin-right: 0;
}

.campus-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
}

.campus-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

@media (max-width: 640px) {
  .course-product-panel { padding: 12px; }
  .campus-dialog :deep(.el-dialog__header),
  .campus-dialog :deep(.el-dialog__body),
  .campus-dialog :deep(.el-dialog__footer) { padding-right: 14px; padding-left: 14px; }
  .dialog-footer { width: 100%; }
}
</style>
