import { onBeforeUnmount, onMounted, ref } from 'vue';

const KEYBOARD_VIEWPORT_THRESHOLD = 140;
const KEYBOARD_CLOSE_DELAY = 120;
const FOCUS_CHECK_DELAY = 450;

/**
 * 通过输入焦点和可视视口变化判断手机软键盘状态，供移动导航和表单操作栏同步布局。
 */
export function useMobileKeyboardState() {
  const isKeyboardOpen = ref(false);
  let restoreTimer: ReturnType<typeof setTimeout> | undefined;
  let focusCheckTimer: ReturnType<typeof setTimeout> | undefined;
  let viewportCloseTimer: ReturnType<typeof setTimeout> | undefined;
  let textInputFocused = false;
  let keyboardWasDetected = false;
  let baselineViewportHeight = 0;

  function isTextInput(target: EventTarget | null) {
    if (target instanceof HTMLTextAreaElement) return !target.readOnly && !target.disabled;
    if (target instanceof HTMLInputElement) {
      return !target.readOnly && !target.disabled
        && !['button', 'checkbox', 'file', 'hidden', 'radio', 'reset', 'submit'].includes(target.type);
    }
    return target instanceof HTMLElement && target.isContentEditable;
  }

  function isTouchDevice() {
    return window.matchMedia('(any-pointer: coarse)').matches;
  }

  function getViewportHeight() {
    return window.visualViewport?.height ?? window.innerHeight;
  }

  function updateBaselineViewportHeight() {
    baselineViewportHeight = Math.max(
      window.innerHeight,
      document.documentElement.clientHeight,
      getViewportHeight(),
    );
  }

  /** 软键盘收起不会必然触发 blur，因此以视口恢复作为主要关闭信号。 */
  function hasKeyboardViewportReduction() {
    const visualHeight = getViewportHeight();
    const layoutHeight = Math.max(document.documentElement.clientHeight, window.innerHeight);
    const reducedByLayoutViewport = layoutHeight - visualHeight;
    const reducedFromBaseline = baselineViewportHeight - visualHeight;
    return Math.max(reducedByLayoutViewport, reducedFromBaseline) > KEYBOARD_VIEWPORT_THRESHOLD;
  }

  function clearCloseTimers() {
    if (viewportCloseTimer) clearTimeout(viewportCloseTimer);
    if (focusCheckTimer) clearTimeout(focusCheckTimer);
    viewportCloseTimer = undefined;
    focusCheckTimer = undefined;
  }

  function updateFromViewport() {
    if (!isTouchDevice() || !textInputFocused) {
      isKeyboardOpen.value = false;
      if (!textInputFocused) updateBaselineViewportHeight();
      return;
    }

    if (hasKeyboardViewportReduction()) {
      keyboardWasDetected = true;
      clearCloseTimers();
      isKeyboardOpen.value = true;
      return;
    }

    // 等待键盘关闭动画结束，避免视口逐帧恢复时反复显示/隐藏底栏。
    if (keyboardWasDetected && isKeyboardOpen.value && !viewportCloseTimer) {
      viewportCloseTimer = setTimeout(() => {
        viewportCloseTimer = undefined;
        if (!hasKeyboardViewportReduction()) {
          keyboardWasDetected = false;
          isKeyboardOpen.value = false;
        }
      }, KEYBOARD_CLOSE_DELAY);
    }
  }

  function handleFocusIn(event: FocusEvent) {
    if (!isTouchDevice() || !isTextInput(event.target)) return;
    textInputFocused = true;
    keyboardWasDetected = false;
    if (restoreTimer) clearTimeout(restoreTimer);
    if (viewportCloseTimer) clearTimeout(viewportCloseTimer);
    viewportCloseTimer = undefined;
    isKeyboardOpen.value = true;

    // 外接键盘或浏览器阻止软键盘时，焦点本身不能代表键盘已弹出。
    if (focusCheckTimer) clearTimeout(focusCheckTimer);
    focusCheckTimer = setTimeout(() => {
      focusCheckTimer = undefined;
      if (hasKeyboardViewportReduction()) {
        keyboardWasDetected = true;
      } else {
        keyboardWasDetected = false;
        isKeyboardOpen.value = false;
      }
    }, FOCUS_CHECK_DELAY);
  }

  function handleFocusOut(event: FocusEvent) {
    if (!isTextInput(event.target)) return;
    if (restoreTimer) clearTimeout(restoreTimer);
    restoreTimer = setTimeout(() => {
      textInputFocused = isTextInput(document.activeElement);
      if (textInputFocused) {
        updateFromViewport();
      } else {
        clearCloseTimers();
        keyboardWasDetected = false;
        isKeyboardOpen.value = false;
        updateBaselineViewportHeight();
      }
      restoreTimer = undefined;
    }, 350);
  }

  function handleViewportChange() {
    updateFromViewport();
  }

  onMounted(() => {
    updateBaselineViewportHeight();
    document.addEventListener('focusin', handleFocusIn);
    document.addEventListener('focusout', handleFocusOut);
    window.addEventListener('resize', handleViewportChange);
    window.visualViewport?.addEventListener('resize', handleViewportChange);
  });
  onBeforeUnmount(() => {
    document.removeEventListener('focusin', handleFocusIn);
    document.removeEventListener('focusout', handleFocusOut);
    window.removeEventListener('resize', handleViewportChange);
    window.visualViewport?.removeEventListener('resize', handleViewportChange);
    if (restoreTimer) clearTimeout(restoreTimer);
    clearCloseTimers();
  });

  return { isKeyboardOpen };
}
