<template>
  <div class="page-stack">
    <section class="settings-panel">
      <div class="settings-panel__header">
        <span class="settings-panel__title">Lesson Status Rules</span>
      </div>

      <el-table v-loading="loading" :data="tableData" row-key="id" class="product-table">
        <el-table-column label="Status code" min-width="120" prop="statusCode" />
        <el-table-column label="Status name" min-width="140" prop="statusName" />
        <el-table-column label="Deduct units" min-width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.defaultShouldDeduct ? 'success' : 'info'">
              {{ row.defaultShouldDeduct ? 'Deduct' : 'No deduct' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Deduction mode" min-width="120" align="center" prop="deductMode" />
        <el-table-column label="Extend schedule" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.defaultShouldExtend ? 'warning' : 'info'">
              {{ row.defaultShouldExtend ? 'Extend' : 'No extend' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Manual override" min-width="120" align="center">
          <template #default="{ row }">
            {{ row.allowManualOverride ? 'Yes' : 'No' }}
          </template>
        </el-table-column>
        <el-table-column label="Rule status" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" min-width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEditDialog(row)">Edit</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" width="520px" :close-on-click-modal="false" class="rule-dialog">
      <template #header><h3>Edit rule: {{ editForm.statusCode }}</h3></template>
      <el-form ref="ruleFormRef" label-position="top" :model="editForm">
        <el-form-item label="Status name" prop="statusName" :rules="[{ required: true, message: 'Required' }]">
          <el-input v-model="editForm.statusName" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="Default deduction">
            <el-switch v-model="editForm.defaultShouldDeduct" active-text="Deduct" inactive-text="No deduct" />
          </el-form-item>
          <el-form-item label="Deduction mode">
            <el-select v-model="editForm.deductMode" style="width: 100%;">
              <el-option label="None" value="NONE" />
              <el-option label="Actual units" value="ACTUAL_UNITS" />
              <el-option label="Planned units" value="PLANNED_UNITS" />
              <el-option label="Fixed units" value="FIXED_UNITS" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item v-if="editForm.deductMode === 'FIXED_UNITS'" label="Fixed units">
          <el-input-number v-model="editForm.fixedUnits" :min="0" style="width: 140px;" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="Extend end date">
            <el-switch v-model="editForm.defaultShouldExtend" active-text="Yes" inactive-text="No" />
          </el-form-item>
          <el-form-item label="Manual override">
            <el-switch v-model="editForm.allowManualOverride" active-text="Yes" inactive-text="No" />
          </el-form-item>
        </div>
        <el-form-item label="Rule status">
          <el-select v-model="editForm.status" style="width: 140px;">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Disabled" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="Remark">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import { fetchLessonStatusRules, updateLessonStatusRule } from '@/api/system';
import type { LessonStatusRuleItem } from '@/types/domain';

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const ruleFormRef = ref();
const tableData = ref<LessonStatusRuleItem[]>([]);
const editingId = ref<number | null>(null);

const editForm = reactive({
  statusCode: '',
  statusName: '',
  defaultShouldDeduct: true,
  deductMode: 'ACTUAL_UNITS',
  fixedUnits: undefined as number | undefined,
  defaultShouldExtend: false,
  allowManualOverride: false,
  status: 'ACTIVE',
  remark: '',
});

onMounted(async () => {
  loading.value = true;
  try {
    const res = await fetchLessonStatusRules({ pageSize: 100 });
    tableData.value = res.data.data.records;
  } catch {
    ElMessage.error('Failed to load rules.');
  } finally {
    loading.value = false;
  }
});

function openEditDialog(row: LessonStatusRuleItem) {
  editingId.value = row.id;
  Object.assign(editForm, {
    statusCode: row.statusCode,
    statusName: row.statusName,
    defaultShouldDeduct: row.defaultShouldDeduct,
    deductMode: row.deductMode,
    fixedUnits: row.fixedUnits,
    defaultShouldExtend: row.defaultShouldExtend,
    allowManualOverride: row.allowManualOverride,
    status: row.status,
    remark: '',
  });
  dialogVisible.value = true;
}

async function handleSave() {
  const valid = await ruleFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    await updateLessonStatusRule(editingId.value!, {
      statusName: editForm.statusName,
      defaultShouldDeduct: editForm.defaultShouldDeduct,
      deductMode: editForm.deductMode,
      fixedUnits: editForm.fixedUnits,
      defaultShouldExtend: editForm.defaultShouldExtend,
      allowManualOverride: editForm.allowManualOverride,
      status: editForm.status,
      remark: editForm.remark || undefined,
    });
    ElMessage.success('Rule updated.');
    dialogVisible.value = false;
    const res = await fetchLessonStatusRules({ pageSize: 100 });
    tableData.value = res.data.data.records;
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.settings-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}
.settings-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.settings-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}
.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 14px;
}
.rule-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
}
.rule-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}
.rule-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.rule-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}
</style>
