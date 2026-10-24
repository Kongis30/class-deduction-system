import { onBeforeUnmount, ref, watch, type Ref } from 'vue';

const DEFAULT_SCALE = 0.8;
const MIN_SCALE = 0.3;
const MAX_SCALE = 1.6;

interface PinchGesture {
  distance: number;
  scale: number;
  contentX: number;
  contentY: number;
}

/** 让可横向滚动的表格区域支持双指缩放，并尽量固定缩放中心位置。 */
export function useMobileTablePinchZoom(
  container: Ref<HTMLElement | null>,
  isEnabled: Ref<boolean>,
  initialScale = DEFAULT_SCALE,
) {
  const scale = ref(Math.min(MAX_SCALE, Math.max(MIN_SCALE, initialScale)));
  let gesture: PinchGesture | null = null;
  let frame = 0;
  let pendingPosition: { scale: number; contentX: number; contentY: number; viewportX: number; viewportY: number } | null = null;

  function distance(touches: TouchList) {
    return Math.hypot(
      touches[0].clientX - touches[1].clientX,
      touches[0].clientY - touches[1].clientY,
    );
  }

  function scheduleScroll() {
    if (frame) return;
    frame = requestAnimationFrame(() => {
      const element = container.value;
      const position = pendingPosition;
      if (element && position) {
        element.scrollLeft = position.contentX * position.scale - position.viewportX;
        element.scrollTop = position.contentY * position.scale - position.viewportY;
      }
      pendingPosition = null;
      frame = 0;
    });
  }

  function onTouchStart(event: TouchEvent) {
    const element = container.value;
    if (!isEnabled.value || !element || event.touches.length !== 2) {
      gesture = null;
      return;
    }
    const startDistance = distance(event.touches);
    if (startDistance < 1) return;
    const bounds = element.getBoundingClientRect();
    const viewportX = (event.touches[0].clientX + event.touches[1].clientX) / 2 - bounds.left;
    const viewportY = (event.touches[0].clientY + event.touches[1].clientY) / 2 - bounds.top;
    gesture = {
      distance: startDistance,
      scale: scale.value,
      contentX: (element.scrollLeft + viewportX) / scale.value,
      contentY: (element.scrollTop + viewportY) / scale.value,
    };
  }

  function onTouchMove(event: TouchEvent) {
    const element = container.value;
    if (!isEnabled.value || !element || !gesture || event.touches.length !== 2) return;
    event.preventDefault();
    const bounds = element.getBoundingClientRect();
    const nextScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, gesture.scale * distance(event.touches) / gesture.distance));
    pendingPosition = {
      scale: nextScale,
      contentX: gesture.contentX,
      contentY: gesture.contentY,
      viewportX: (event.touches[0].clientX + event.touches[1].clientX) / 2 - bounds.left,
      viewportY: (event.touches[0].clientY + event.touches[1].clientY) / 2 - bounds.top,
    };
    scale.value = nextScale;
    scheduleScroll();
  }

  function onTouchEnd(event: TouchEvent) {
    if (event.touches.length < 2) gesture = null;
  }

  function detach(element: HTMLElement | null) {
    element?.removeEventListener('touchstart', onTouchStart);
    element?.removeEventListener('touchmove', onTouchMove);
    element?.removeEventListener('touchend', onTouchEnd);
    element?.removeEventListener('touchcancel', onTouchEnd);
  }

  function attach(element: HTMLElement | null) {
    if (!element) return;
    element.addEventListener('touchstart', onTouchStart, { passive: true });
    element.addEventListener('touchmove', onTouchMove, { passive: false });
    element.addEventListener('touchend', onTouchEnd, { passive: true });
    element.addEventListener('touchcancel', onTouchEnd, { passive: true });
  }

  watch(container, (next, previous) => {
    detach(previous);
    attach(next);
  }, { immediate: true, flush: 'post' });

  onBeforeUnmount(() => {
    detach(container.value);
    if (frame) cancelAnimationFrame(frame);
  });

  return { scale };
}
