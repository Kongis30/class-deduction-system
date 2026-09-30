<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="460px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="pause-confirmation">
      <el-alert :title="message" type="warning" :closable="false" show-icon />
      <p class="pause-confirmation__countdown">
        {{ remainingSeconds > 0 ? `Please review this change. You can confirm in ${remainingSeconds} seconds.` : 'You can now confirm the pause.' }}
      </p>
    </div>

    <template #footer>
      <el-button v-if="alternateActionText" type="primary" plain @click="emit('alternate-action')">
        {{ alternateActionText }}
      </el-button>
      <el-button v-else @click="emit('update:modelValue', false)">Cancel</el-button>
      <el-button type="danger" :disabled="remainingSeconds > 0 || loading" :loading="loading" @click="emit('confirm')">
        {{ remainingSeconds > 0 ? `${confirmText} (${remainingSeconds}s)` : confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue';

const DEFAULT_COOLDOWN_SECONDS = 10;

const props = withDefaults(defineProps<{
  modelValue: boolean;
  title: string;
  message: string;
  confirmText: string;
  confirmCooldownSeconds?: number;
  alternateActionText?: string;
  loading?: boolean;
}>(), {
  confirmCooldownSeconds: DEFAULT_COOLDOWN_SECONDS,
  loading: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  confirm: [];
  'alternate-action': [];
}>();

const remainingSeconds = ref(props.confirmCooldownSeconds);
let countdownTimer: ReturnType<typeof setInterval> | undefined;

function clearCountdown() {
  if (countdownTimer) clearInterval(countdownTimer);
  countdownTimer = undefined;
}

watch(() => [props.modelValue, props.confirmCooldownSeconds] as const, ([visible, cooldownSeconds]) => {
  clearCountdown();
  remainingSeconds.value = Math.max(0, Math.ceil(cooldownSeconds));
  if (!visible || remainingSeconds.value === 0) return;

  countdownTimer = setInterval(() => {
    remainingSeconds.value = Math.max(0, remainingSeconds.value - 1);
    if (remainingSeconds.value === 0) clearCountdown();
  }, 1000);
}, { immediate: true });

onUnmounted(clearCountdown);
</script>

<style scoped>
.pause-confirmation__countdown {
  margin: 14px 0 0;
  color: #606266;
  font-size: 13px;
}
</style>
