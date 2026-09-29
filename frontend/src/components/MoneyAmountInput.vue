<template>
  <el-input
    class="money-amount-input"
    :model-value="displayValue"
    :disabled="disabled"
    :inputmode="wholeDollars ? 'numeric' : 'decimal'"
    @focus="handleFocus"
    @input="handleInput"
    @blur="handleBlur"
    @change="emit('change', modelValue)"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { formatRoundedAmount } from '@/utils/money-display';

const props = withDefaults(defineProps<{
  modelValue?: number;
  min?: number;
  max?: number;
  disabled?: boolean;
  wholeDollars?: boolean;
}>(), { min: 0, disabled: false });

const emit = defineEmits<{
  'update:modelValue': [value: number];
  input: [value: number];
  change: [value: number | undefined];
}>();

const focused = ref(false);
const draft = ref('');
const displayValue = computed(() => focused.value
  ? draft.value
  : Number.isInteger(Math.round(Number(props.modelValue ?? 0) * 100) / 100)
    ? formatRoundedAmount(props.modelValue)
    : Number(props.modelValue ?? 0).toFixed(2));

// 用户编辑时显示精确分值；父级限制付款上限后，同步回被限制的真实金额。
watch(() => props.modelValue, (value) => {
  if (focused.value && Number(draft.value || 0) !== Number(value ?? 0)) {
    draft.value = String(value ?? 0);
  }
});

function handleFocus() {
  focused.value = true;
  draft.value = String(props.modelValue ?? 0);
}

function handleInput(value: string) {
  const cleaned = value.replace(/[^\d.]/g, '');
  if (props.wholeDollars && cleaned.includes('.')) return;
  if (!props.wholeDollars && !/^\d*(?:\.\d{0,2})?$/.test(cleaned)) return;
  draft.value = cleaned;
  const amount = cleaned === '.' ? 0 : Number(cleaned || 0);
  emit('input', amount);
  const limited = Math.min(props.max ?? Infinity, Math.max(props.min, amount));
  if (limited !== amount) draft.value = String(limited);
  emit('update:modelValue', props.wholeDollars ? Math.floor(limited) : Math.round((limited + Number.EPSILON) * 100) / 100);
}

function handleBlur() {
  focused.value = false;
  draft.value = '';
}
</script>

<style scoped>
.money-amount-input {
  width: 100%;
  min-width: 0;
}

.money-amount-input :deep(input) {
  text-align: right;
}
</style>
