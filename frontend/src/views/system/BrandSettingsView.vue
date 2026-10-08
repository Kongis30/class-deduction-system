<template>
  <div class="page-stack">
    <section class="settings-panel">
      <div class="settings-panel__header">
        <span class="settings-panel__title">Brand & receipts</span>
        <el-button type="primary" :loading="saving" @click="handleSave">Save</el-button>
      </div>

      <div v-loading="loading" class="brand-form">
        <el-alert
          class="receipt-rule-alert"
          type="info"
          :closable="false"
          show-icon
          title="Receipt configuration scope"
          description="The current backend stores the receipt footer here. EIN is resolved from the selected campus in the registration receipt snapshot and can be shown or hidden by the Show EIN switch on the same receipt. Policy text and print layout are currently provided by the receipt preview template."
        />
        <el-form ref="formRef" label-position="top" :model="form">
          <div class="form-row">
            <el-form-item label="Business name" prop="brandName" :rules="[{ required: true, message: 'Required' }]">
              <el-input v-model="form.brandName" placeholder="JIN BEST TUTORING" />
            </el-form-item>
            <el-form-item label="Theme color">
              <el-input v-model="form.themeColor" placeholder="#18b58a" />
            </el-form-item>
          </div>
          <div class="form-row">
            <el-form-item label="Support email">
              <el-input v-model="form.supportEmail" placeholder="support@example.com" />
            </el-form-item>
            <el-form-item label="Logo URL">
              <el-input v-model="form.logoUrl" placeholder="https://..." />
            </el-form-item>
          </div>
          <el-form-item label="Receipt footer text">
            <el-input v-model="form.receiptFooterText" type="textarea" :rows="2" placeholder="Footer text on receipts" />
          </el-form-item>
          <el-form-item label="Status">
            <el-select v-model="form.status" style="width: 140px;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import { fetchBrandConfig, updateBrandConfig } from '@/api/system';

const loading = ref(false);
const saving = ref(false);
const formRef = ref();

const form = reactive({
  brandName: '',
  logoUrl: '',
  themeColor: '',
  supportEmail: '',
  receiptFooterText: '',
  status: 'ACTIVE',
});

onMounted(async () => {
  loading.value = true;
  try {
    const res = await fetchBrandConfig();
    const data = res.data.data;
    Object.assign(form, {
      brandName: data.brandName,
      logoUrl: data.logoUrl ?? '',
      themeColor: data.themeColor ?? '',
      supportEmail: data.supportEmail ?? '',
      receiptFooterText: data.receiptFooterText ?? '',
      status: data.status,
    });
  } catch {
    ElMessage.error('Failed to load brand config.');
  } finally {
    loading.value = false;
  }
});

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    await updateBrandConfig({ ...form });
    ElMessage.success('Brand settings saved.');
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
.brand-form {
  max-width: 720px;
}
.receipt-rule-alert {
  margin-bottom: 14px;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 20px;
}

@media (max-width: 640px) {
  .settings-panel { padding: 12px; }
  .form-row { grid-template-columns: 1fr; gap: 0; }
  .brand-form { max-width: none; }
}
</style>
