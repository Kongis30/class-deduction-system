import { onBeforeUnmount, onMounted, ref } from 'vue';

/** 追踪触屏设备的文本输入焦点，供键盘弹出时收起占空间的固定栏。 */
export function useMobileKeyboardState() {
  const isKeyboardOpen = ref(false);
  let restoreTimer: ReturnType<typeof setTimeout> | undefined;

  function isTextInput(target: EventTarget | null) {
    if (target instanceof HTMLTextAreaElement) return !target.readOnly && !target.disabled;
    if (target instanceof HTMLInputElement) {
      return !target.readOnly && !target.disabled
        && !['button', 'checkbox', 'file', 'hidden', 'radio', 'reset', 'submit'].includes(target.type);
    }
    return target instanceof HTMLElement && target.isContentEditable;
  }

  // 延迟恢复，等待手机键盘收起动画结束，避免固定栏短暂浮在键盘上方。
  function handleFocusIn(event: FocusEvent) {
    if (!window.matchMedia('(any-pointer: coarse)').matches || !isTextInput(event.target)) return;
    if (restoreTimer) clearTimeout(restoreTimer);
    isKeyboardOpen.value = true;
  }

  function handleFocusOut(event: FocusEvent) {
    if (!isTextInput(event.target)) return;
    if (restoreTimer) clearTimeout(restoreTimer);
    restoreTimer = setTimeout(() => {
      isKeyboardOpen.value = isTextInput(document.activeElement);
      restoreTimer = undefined;
    }, 350);
  }

  onMounted(() => {
    document.addEventListener('focusin', handleFocusIn);
    document.addEventListener('focusout', handleFocusOut);
  });
  onBeforeUnmount(() => {
    document.removeEventListener('focusin', handleFocusIn);
    document.removeEventListener('focusout', handleFocusOut);
    if (restoreTimer) clearTimeout(restoreTimer);
  });

  return { isKeyboardOpen };
}
