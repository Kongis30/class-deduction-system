<template>
  <el-dialog
    v-model="visible"
    title="Form Field Settings"
    width="480px"
    :close-on-click-modal="false"
    class="field-config-dialog"
  >
    <div class="field-config-intro">
      Toggle and rename optional fields to customize the form layout.
    </div>

    <div class="field-config-list">
      <div v-for="field in editingFields" :key="field.key" class="field-config-item">
        <div class="field-config-item__toggle">
          <el-switch v-model="field.visible" size="small" />
          <span class="field-config-item__label" :class="{ 'field-config-item__label--disabled': !field.visible }">
            {{ field.defaultLabel }}
          </span>
        </div>
        <div v-if="field.visible" class="field-config-item__rename">
          <el-input
            v-model="field.customLabel"
            size="small"
            placeholder="Custom label"
            style="width: 180px;"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button size="small" @click="resetToDefault">Reset to Default</el-button>
        <el-button size="small" @click="visible = false">Cancel</el-button>
        <el-button type="primary" size="small" @click="handleSave">Save</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';

export interface FieldConfig {
  key: string;
  defaultLabel: string;
  visible: boolean;
  customLabel: string;
}

const props = defineProps<{
  modelValue: boolean;
  configKey: string;
  fields: FieldConfig[];
  defaultFields?: FieldConfig[];
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  'change': [fields: FieldConfig[]];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

// Clone fields for editing — use shallow copy so v-model bound to proxy elements
const editingFields = ref<FieldConfig[]>([]);

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      // Replace in-place with splice so Vue keeps reactive tracking on the new items
      editingFields.value.splice(0, editingFields.value.length);
      props.fields.forEach((f) => editingFields.value.push({ ...f }));
    }
  },
  { immediate: true }
);

function resetToDefault() {
  const defaults = props.defaultFields ?? props.fields;
  editingFields.value.splice(0, editingFields.value.length);
  defaults.forEach((f) => {
    editingFields.value.push({ ...f, customLabel: f.defaultLabel });
  });
}

function handleSave() {
  localStorage.setItem(`fieldConfig:${props.configKey}`, JSON.stringify(editingFields.value));
  emit('change', editingFields.value);
  visible.value = false;
}
</script>

<style scoped>
.field-config-intro {
  color: #606266;
  font-size: 13px;
  margin-bottom: 16px;
}

.field-config-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-config-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

.field-config-item__toggle {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-config-item__label {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

.field-config-item__label--disabled {
  color: #c0c4cc;
}

.field-config-item__rename {
  padding-left: 52px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
