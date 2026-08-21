<template>
  <div class="page-stack">
    <section class="config-panel">
      <div class="config-panel__header">
        <div>
          <span class="config-panel__eyebrow">Settings</span>
          <h2>Payment methods</h2>
          <p>Set the payment methods staff can choose during registration.</p>
        </div>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>
          Add method
        </el-button>
      </div>

      <el-table v-loading="loading" :data="methods" row-key="id" class="config-table">
        <el-table-column prop="methodCode" label="Code" min-width="150" />
        <el-table-column prop="methodName" label="Name" min-width="180" />
        <el-table-column prop="sortOrder" label="Sort" width="90" align="center" />
        <el-table-column label="Status" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="230" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDialog(row)">Edit</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              text
              type="warning"
              @click="deactivateMethod(row)"
            >
              Deactivate
            </el-button>
            <el-button text type="danger" @click="deleteMethod(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :title="editingId ? 'Edit payment method' : 'Add payment method'"
    >
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-form-item label="Method name" prop="methodName">
          <el-input v-model="form.methodName" placeholder="e.g. Zelle" @blur="fillCodeFromName" />
        </el-form-item>
        <el-form-item label="Method code" prop="methodCode">
          <el-input v-model="form.methodCode" placeholder="e.g. ZELLE" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="Sort order">
            <el-input-number v-model="form.sortOrder" :min="0" :controls="false" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="Status">
            <el-select v-model="form.status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveMethod">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';

import {
  createPaymentMethod,
  deletePaymentMethod,
  fetchPaymentMethods,
  updatePaymentMethod,
  type PaymentMethodSavePayload,
} from '@/api/enrollment';
import type { EntityId, PaymentMethodItem } from '@/types/domain';

const loading = ref(false);
const saving = ref(false);
const methods = ref<PaymentMethodItem[]>([]);
const dialogVisible = ref(false);
const editingId = ref<EntityId | null>(null);
const formRef = ref();

const form = reactive({
  methodCode: '',
  methodName: '',
  sortOrder: 0,
  status: 'ACTIVE',
});

const rules = {
  methodName: [{ required: true, message: 'Method name is required' }],
  methodCode: [{ required: true, message: 'Method code is required' }],
};

onMounted(loadMethods);

async function loadMethods() {
  loading.value = true;
  try {
    const res = await fetchPaymentMethods();
    methods.value = res.data.data ?? [];
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to load payment methods.');
  } finally {
    loading.value = false;
  }
}

function openDialog(method?: PaymentMethodItem) {
  editingId.value = method?.id ?? null;
  form.methodCode = method?.methodCode ?? '';
  form.methodName = method?.methodName ?? '';
  form.sortOrder = method?.sortOrder ?? 0;
  form.status = method?.status ?? 'ACTIVE';
  dialogVisible.value = true;
}

function fillCodeFromName() {
  if (form.methodCode || !form.methodName) {
    return;
  }
  form.methodCode = normalizeCode(form.methodName);
}

function normalizeCode(value: string) {
  return value
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[\s-]+/g, '_')
    .replace(/[^a-zA-Z0-9_]/g, '')
    .toUpperCase();
}

function buildPayload(status = form.status): PaymentMethodSavePayload {
  return {
    methodCode: form.methodCode.trim(),
    methodName: form.methodName.trim(),
    sortOrder: form.sortOrder,
    status,
  };
}

function payloadFromRow(row: PaymentMethodItem, status: string): PaymentMethodSavePayload {
  return {
    methodCode: row.methodCode,
    methodName: row.methodName,
    sortOrder: row.sortOrder,
    status,
  };
}

async function saveMethod() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  saving.value = true;
  try {
    if (editingId.value) {
      await updatePaymentMethod(editingId.value, buildPayload());
    } else {
      await createPaymentMethod(buildPayload());
    }
    ElMessage.success(editingId.value ? 'Payment method updated.' : 'Payment method created.');
    dialogVisible.value = false;
    await loadMethods();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    saving.value = false;
  }
}

async function deactivateMethod(row: PaymentMethodItem) {
  try {
    await ElMessageBox.confirm('Deactivate this payment method?', 'Confirm', { type: 'warning' });
    await updatePaymentMethod(row.id, payloadFromRow(row, 'INACTIVE'));
    ElMessage.success('Payment method deactivated.');
    await loadMethods();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
    }
  }
}

async function deleteMethod(id: EntityId) {
  try {
    await ElMessageBox.confirm('Delete this payment method?', 'Confirm', { type: 'warning' });
    await deletePaymentMethod(id);
    ElMessage.success('Payment method deleted.');
    await loadMethods();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
    }
  }
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

.config-panel__header p {
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

.config-table {
  width: 100%;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
</style>
