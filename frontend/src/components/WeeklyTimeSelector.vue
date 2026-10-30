<template>
  <div class="weekly-time-selector">
    <div v-if="pinchZoomEnabled" class="wts-mobile-zoom-bar">
      <span class="wts-mobile-zoom-bar__label">Zoom</span>
      <div class="wts-mobile-zoom-controls" role="group" aria-label="Calendar zoom">
        <button
          type="button"
          class="wts-zoom-btn"
          :disabled="mobileZoom <= MIN_MOBILE_ZOOM"
          aria-label="Zoom out"
          @click="zoomStep(-0.25)"
        >−</button>
        <button
          type="button"
          class="wts-zoom-btn wts-zoom-btn--value"
          :aria-label="`Reset zoom to fit the week (currently ${Math.round(mobileZoom * 100)}%)`"
          @click="resetZoom"
        >{{ Math.round(mobileZoom * 100) }}%</button>
        <button
          type="button"
          class="wts-zoom-btn"
          :disabled="mobileZoom >= MAX_MOBILE_ZOOM"
          aria-label="Zoom in"
          @click="zoomStep(0.25)"
        >+</button>
      </div>
    </div>
    <div
      ref="gridScroll"
      class="wts-grid-scroll"
      :class="{ 'wts-grid-scroll--pinch': pinchZoomEnabled }"
      :style="pinchZoomEnabled ? mobileZoomStyle : undefined"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <div class="wts-header">
        <span class="wts-header__spacer"></span>
        <span class="wts-header__cell" v-for="(label, idx) in dayLabelsWithDates" :key="idx">
          <span class="wts-header__day">{{ label.day }}</span>
          <span v-if="label.date" class="wts-header__date">{{ label.date }}</span>
        </span>
      </div>
      <div v-if="dragPreview" class="wts-drag-feedback" role="status" aria-live="polite">
        <span class="wts-drag-feedback__content">
          {{ weekdayName(dragPreview.weekday) }} {{ formatTime(dragPreview.startMin) }}–{{ formatTime(dragPreview.endMin) }}
          <strong>{{ dragPreview.units }} × 30 min · {{ (dragPreview.units * 0.5).toFixed(1) }}h</strong>
        </span>
      </div>
      <div class="wts-body">
        <div
          class="wts-row"
          v-for="time in timeSlots"
          :key="time.label"
          :class="{ 'wts-row--last-slot': time.isLastSlot }"
        >
          <span class="wts-row__label">
            <template v-if="time.isLastSlot">
              <span>{{ time.label }}</span>
              <span>{{ SCHEDULE_DAY_END_TIME }}</span>
            </template>
            <template v-else>{{ time.label }}</template>
          </span>
          <div
            v-for="day in 7"
            :key="day"
            class="wts-cell"
            :class="[
              cellClass(day, time.hour, time.minute),
              { 'wts-cell--event-start': occupiedEvent(day, time.hour, time.minute) },
            ]"
            :data-weekday="day"
            :data-hour="time.hour"
            :data-minute="time.minute"
            @mousedown.prevent="onMouseDown(day, time.hour, time.minute)"
            @mouseenter="onMouseEnter(day, time.hour, time.minute)"
            @click="onCellClick(day, time.hour, time.minute)"
            @touchstart="onCellTouchStart(day, time.hour, time.minute, $event)"
          >
            <el-tooltip
              v-if="isOccupied(day, time.hour, time.minute)"
              :content="occupiedTooltipContent(day, time.hour, time.minute)"
              placement="top"
              :show-after="200"
              raw-content
            >
              <div
                v-if="occupiedEvent(day, time.hour, time.minute)"
                class="wts-event"
                :class="{
                  'wts-event--multiple': occupiedEvent(day, time.hour, time.minute)?.isMultiple,
                  'wts-event--online': occupiedEvent(day, time.hour, time.minute)?.isOnline && !occupiedEvent(day, time.hour, time.minute)?.isMultiple,
                }"
                :style="occupiedEventStyle(occupiedEvent(day, time.hour, time.minute)!)"
              >
                <strong class="wts-event__title">{{ occupiedEvent(day, time.hour, time.minute)?.title }}</strong>
                <span
                  v-if="occupiedEvent(day, time.hour, time.minute)?.context"
                  class="wts-event__context"
                >
                  {{ occupiedEvent(day, time.hour, time.minute)?.context }}
                </span>
                <span
                  v-if="occupiedEvent(day, time.hour, time.minute)?.showTime"
                  class="wts-event__time"
                >
                  {{ occupiedEvent(day, time.hour, time.minute)?.timeLabel }}
                </span>
              </div>
              <span v-else class="wts-cell__fill" style="display:block;height:100%;width:100%"></span>
            </el-tooltip>
            <template v-else>
              <div
                v-if="occupiedEvent(day, time.hour, time.minute)"
                class="wts-event"
                :class="{
                  'wts-event--multiple': occupiedEvent(day, time.hour, time.minute)?.isMultiple,
                  'wts-event--online': occupiedEvent(day, time.hour, time.minute)?.isOnline && !occupiedEvent(day, time.hour, time.minute)?.isMultiple,
                }"
                :style="occupiedEventStyle(occupiedEvent(day, time.hour, time.minute)!)"
              >
                <strong class="wts-event__title">{{ occupiedEvent(day, time.hour, time.minute)?.title }}</strong>
                <span
                  v-if="occupiedEvent(day, time.hour, time.minute)?.context"
                  class="wts-event__context"
                >
                  {{ occupiedEvent(day, time.hour, time.minute)?.context }}
                </span>
                <span
                  v-if="occupiedEvent(day, time.hour, time.minute)?.showTime"
                  class="wts-event__time"
                >
                  {{ occupiedEvent(day, time.hour, time.minute)?.timeLabel }}
                </span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Selected slots summary -->
    <div v-if="modelValue.length > 0 && !readonly" class="wts-summary">
      <div class="wts-summary__title">Selected class slots ({{ modelValue.length }}):</div>
      <el-tag
        v-for="(slot, idx) in modelValue"
        :key="idx"
        closable
        size="small"
        type="primary"
        class="wts-tag"
        @close="removeSlot(idx)"
      >
        {{ weekdayName(slot.weekday) }} {{ slot.startTime }}–{{ scheduleEndTimeForDisplay(slot.startTime, slot.endTime) }}
        ({{ (slot.plannedUnits * 0.5).toFixed(1) }}h)
      </el-tag>
    </div>
    <div v-else-if="!readonly && modelValue.length === 0 && !hideHint" class="wts-empty">
      Click cells to select class time slots. Drag from start to end time.
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  SCHEDULE_DAY_END_MINUTES,
  SCHEDULE_DAY_END_TIME,
  SCHEDULE_DAY_START_MINUTES,
  SCHEDULE_SLOT_MINUTES,
  scheduleEndTimeForDisplay,
  scheduleTimeMinutes,
} from '@/utils/schedule-time';

export interface SlotDef {
  weekday: number;   // 1=Mon..7=Sun
  startTime: string; // HH:mm
  endTime: string;
  plannedUnits: number;
}

export interface OccupiedSlotDef {
  weekday: number;
  startTime: string;
  endTime: string;
  occurrenceDate?: string;
  courseLabel: string;
  displayTitle?: string;
  subjectName?: string;
  contextLabel?: string;
  studentName?: string;
  teacherName?: string;
  classroomName?: string;
  productName?: string;
  showTimeInEvent?: boolean;
  isOnline?: boolean;
  /** When true, renders as a blue highlight instead of gray striped — used to mark the "current" package's own slots. */
  isCurrent?: boolean;
}

const props = withDefaults(defineProps<{
  modelValue: SlotDef[];
  occupiedSlots?: OccupiedSlotDef[];
  weekStartDate?: string;  // ISO date of the Monday for the displayed week
  readonly?: boolean;
  hideHint?: boolean;
  pinchZoomEnabled?: boolean;
}>(), {
  readonly: false,
  hideHint: false,
  pinchZoomEnabled: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', val: SlotDef[]): void;
  (e: 'cell-click', weekday: number, hour: number, minute: number): void;
}>();

const MIN_MOBILE_ZOOM = 0.6;
const MAX_MOBILE_ZOOM = 2.5;
const MOBILE_BASE_GRID_WIDTH = 42 + 7 * 58; // 448px at 100% zoom (time column + 7 days)
const mobileZoom = ref(1);
const hasManualZoom = ref(false);
const gridScroll = ref<HTMLElement | null>(null);
let pinchStartDistance = 0;
let pinchStartZoom = 1;
let pinchAnchorX = 0;
let pinchAnchorY = 0;
let resizeObserver: ResizeObserver | null = null;

// 触摸拖动选择（移动端编辑排课）：单指在日历上拖动可形成连续时段。
let touchSelecting = false;
let touchStartX = 0;
let touchStartY = 0;
let touchMoved = false;
let suppressMouseUntil = 0;

function applyZoom(value: number) {
  mobileZoom.value = clampMobileZoom(value);
}

function zoomStep(delta: number) {
  hasManualZoom.value = true;
  applyZoom(mobileZoom.value + delta);
}

// 初始按可视宽度缩放，让一整周都能看到；用户手动缩放/双指缩放后不再自动重排。
function fitToWidth() {
  if (hasManualZoom.value) return;
  const scroller = gridScroll.value;
  if (!scroller) return;
  const available = scroller.clientWidth;
  if (!available) return;
  applyZoom(available / MOBILE_BASE_GRID_WIDTH);
}

function resetZoom() {
  hasManualZoom.value = false;
  fitToWidth();
}

const mobileZoomStyle = computed(() => ({
  '--wts-mobile-grid-width': `${(42 + 7 * 58) * mobileZoom.value}px`,
  '--wts-mobile-time-width': `${42 * mobileZoom.value}px`,
  '--wts-mobile-day-width': `${58 * mobileZoom.value}px`,
  '--wts-mobile-row-height': `${26 * mobileZoom.value}px`,
  '--wts-mobile-font-small': `${11 * mobileZoom.value}px`,
  '--wts-mobile-font-medium': `${12 * mobileZoom.value}px`,
  '--wts-mobile-font-large': `${13 * mobileZoom.value}px`,
  '--wts-mobile-padding': `${4 * mobileZoom.value}px`,
}) as Record<string, string>);

function clampMobileZoom(value: number) {
  return Math.min(MAX_MOBILE_ZOOM, Math.max(MIN_MOBILE_ZOOM, value));
}

function touchDistance(first: Touch, second: Touch) {
  return Math.hypot(first.clientX - second.clientX, first.clientY - second.clientY);
}

// 双指缩放只改变手机日历网格密度，并围绕手势中心保留当前查看位置。
function onTouchStart(event: TouchEvent) {
  if (!props.pinchZoomEnabled || event.touches.length !== 2) return;
  // 进入双指缩放时取消单指拖动选择，避免两个手势互相干扰。
  if (touchSelecting) {
    touchSelecting = false;
    clearDrag();
  }
  hasManualZoom.value = true;
  const scroller = gridScroll.value;
  if (!scroller) return;

  const [first, second] = Array.from(event.touches);
  const rect = scroller.getBoundingClientRect();
  const centerX = (first.clientX + second.clientX) / 2 - rect.left;
  const centerY = (first.clientY + second.clientY) / 2 - rect.top;
  pinchStartDistance = touchDistance(first, second);
  pinchStartZoom = mobileZoom.value;
  pinchAnchorX = (scroller.scrollLeft + centerX) / pinchStartZoom;
  pinchAnchorY = (scroller.scrollTop + centerY) / pinchStartZoom;
}

// 单指按住日历单元格开始选择；移动端用触摸拖动形成连续时段。
function onCellTouchStart(weekday: number, hour: number, minute: number, event: TouchEvent) {
  if (props.readonly || !props.pinchZoomEnabled) return;
  if (event.touches.length !== 1) return;
  if (timeToMinutes(hour, minute) >= SCHEDULE_DAY_END_MINUTES) return;
  const touch = event.touches[0];
  touchStartX = touch.clientX;
  touchStartY = touch.clientY;
  touchMoved = false;
  touchSelecting = true;
  anchor.value = { weekday, hour, minute };
  currentHover.value = anchor.value;
  selecting.value = true;
  // 阻止随后的合成鼠标事件与页面滚动，让拖动专心用于选择。
  if (event.cancelable) event.preventDefault();
}

function touchCellAt(clientX: number, clientY: number) {
  const element = document.elementFromPoint(clientX, clientY) as HTMLElement | null;
  const cell = element?.closest?.('.wts-cell') as HTMLElement | null;
  if (!cell || cell.dataset.weekday == null) return null;
  return {
    weekday: Number(cell.dataset.weekday),
    hour: Number(cell.dataset.hour),
    minute: Number(cell.dataset.minute),
  };
}

function onTouchMove(event: TouchEvent) {
  // 双指缩放优先。
  if (props.pinchZoomEnabled && pinchStartDistance && event.touches.length === 2) {
    if (event.cancelable) event.preventDefault();

    const scroller = gridScroll.value;
    if (!scroller) return;
    const [first, second] = Array.from(event.touches);
    const rect = scroller.getBoundingClientRect();
    const centerX = (first.clientX + second.clientX) / 2 - rect.left;
    const centerY = (first.clientY + second.clientY) / 2 - rect.top;
    const nextZoom = clampMobileZoom(pinchStartZoom * touchDistance(first, second) / pinchStartDistance);
    mobileZoom.value = nextZoom;

    void nextTick(() => {
      if (!gridScroll.value) return;
      gridScroll.value.scrollLeft = Math.max(0, pinchAnchorX * nextZoom - centerX);
      gridScroll.value.scrollTop = Math.max(0, pinchAnchorY * nextZoom - centerY);
    });
    return;
  }

  // 单指拖动选择连续时段。
  if (!touchSelecting || props.readonly || event.touches.length !== 1) return;
  if (event.cancelable) event.preventDefault();
  const touch = event.touches[0];
  if (Math.abs(touch.clientX - touchStartX) > 3 || Math.abs(touch.clientY - touchStartY) > 3) {
    touchMoved = true;
  }
  const cell = touchCellAt(touch.clientX, touch.clientY);
  if (!cell || !anchor.value) return;
  // 只允许在同一天内纵向拖动，避免滑到相邻列时选中错误时段。
  if (cell.weekday !== anchor.value.weekday) return;
  currentHover.value = cell;
}

function onTouchEnd(event: TouchEvent) {
  if (event.touches.length < 2) pinchStartDistance = 0;

  if (!touchSelecting) return;
  touchSelecting = false;
  // 拖动结束后提交连续时段；轻点则形成单个 30 分钟时段。
  const preview = dragPreview.value;
  if (preview) commitPreviewSlot(preview);
  clearDrag();
  // 阻止浏览器在触摸结束后补发 click，避免重复处理。
  suppressMouseUntil = Date.now() + 500;
  if (event.cancelable) event.preventDefault();
}

// ─── Day labels with optional dates ─────────
const dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

const dayLabelsWithDates = computed(() => {
  if (!props.weekStartDate) {
    return dayNames.map(day => ({ day, date: '' }));
  }
  const monday = new Date(props.weekStartDate + 'T00:00:00');
  return dayNames.map((day, idx) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() + idx);
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return { day, date: `${mm}/${dd}` };
  });
});

// ─── Grid config ─────────────────────────────
const timeSlots = computed(() => {
  const rows: { label: string; hour: number; minute: number; isLastSlot: boolean }[] = [];
  for (let minutes = SCHEDULE_DAY_START_MINUTES; minutes < SCHEDULE_DAY_END_MINUTES; minutes += SCHEDULE_SLOT_MINUTES) {
    const hour = Math.floor(minutes / 60);
    const minute = minutes % 60;
    rows.push({
      label: formatTime(minutes),
      hour,
      minute,
      isLastSlot: minutes === SCHEDULE_DAY_END_MINUTES - SCHEDULE_SLOT_MINUTES,
    });
  }
  return rows;
});

// ─── Time helpers ───────────────────────────
function timeToMinutes(h: number, m: number) {
  return h * 60 + m;
}

function parseTimeToMin(t: string) {
  return scheduleTimeMinutes(t);
}

function parseEndTimeToMin(startTime: string, endTime: string) {
  return parseTimeToMin(scheduleEndTimeForDisplay(startTime, endTime));
}

function formatTime(minutes: number) {
  const hh = String(Math.floor(minutes / 60)).padStart(2, '0');
  const mm = String(minutes % 60).padStart(2, '0');
  return `${hh}:${mm}`;
}

// ─── Occupied slot lookup ───────────────────
const occupiedMap = computed(() => {
  const map = new Map<string, OccupiedSlotDef[]>();
  for (const slot of (props.occupiedSlots || [])) {
    const startMin = parseTimeToMin(slot.startTime);
    const endMin = parseEndTimeToMin(slot.startTime, slot.endTime);
    // A lesson may start off-grid (for example 17:15). Mark every 30-minute
    // calendar cell touched by the lesson so overlaps are not silently missed.
    // 只映射日历可见范围；早于 06:00 的课程若延续到可见时段，仍从首行显示。
    const firstCellMin = Math.max(Math.floor(startMin / SCHEDULE_SLOT_MINUTES) * SCHEDULE_SLOT_MINUTES, SCHEDULE_DAY_START_MINUTES);
    const lastVisibleMin = Math.min(endMin, SCHEDULE_DAY_END_MINUTES);
    for (let m = firstCellMin; m < lastVisibleMin; m += SCHEDULE_SLOT_MINUTES) {
      if (m + SCHEDULE_SLOT_MINUTES <= startMin) continue;
      const key = `${slot.weekday}:${m}`;
      map.set(key, [...(map.get(key) || []), slot]);
    }
  }
  return map;
});

function isOccupied(day: number, hour: number, minute: number): boolean {
  return occupiedMap.value.has(`${day}:${timeToMinutes(hour, minute)}`);
}

function occupiedTooltipContent(day: number, hour: number, minute: number): string {
  const slots = occupiedMap.value.get(`${day}:${timeToMinutes(hour, minute)}`);
  if (!slots?.length) return '';
  if (slots.length === 1) {
    return occupiedSlotTooltip(slots[0]);
  }
  return [
    `<b>${slots.length} lessons in this time slot</b>`,
    ...slots.map((slot, index) => `${index + 1}. ${occupiedSlotTooltip(slot)}`),
  ].join('<br><br>');
}

interface OccupiedEvent {
  title: string;
  context?: string;
  timeLabel?: string;
  coveredCells: number;
  isMultiple?: boolean;
  isOnline?: boolean;
  showTime: boolean;
}

function occupiedEvent(day: number, hour: number, minute: number): OccupiedEvent | null {
  const minuteVal = timeToMinutes(hour, minute);
  const slots = occupiedMap.value.get(`${day}:${minuteVal}`);
  if (!slots?.length) return null;
  if (slots.length > 1 && isConflictStart(day, minuteVal)) {
    return {
      title: `${slots.length} lessons`,
      coveredCells: 1,
      isMultiple: true,
      showTime: false,
    };
  }
  const slot = slots[0];
  const visibleStartMin = Math.max(
    Math.floor(parseTimeToMin(slot.startTime) / SCHEDULE_SLOT_MINUTES) * SCHEDULE_SLOT_MINUTES,
    SCHEDULE_DAY_START_MINUTES,
  );
  if (minuteVal === visibleStartMin) {
    const endMin = parseEndTimeToMin(slot.startTime, slot.endTime);
    const durationMinutes = endMin - parseTimeToMin(slot.startTime);
    const coveredCells = Math.max(
      1,
      Math.ceil(Math.min(endMin, SCHEDULE_DAY_END_MINUTES) / SCHEDULE_SLOT_MINUTES)
        - Math.floor(visibleStartMin / SCHEDULE_SLOT_MINUTES),
    );
    return {
      title: occupiedSlotTitle(slot),
      context: slot.contextLabel,
      timeLabel: `${slot.startTime}–${scheduleEndTimeForDisplay(slot.startTime, slot.endTime)}`,
      coveredCells,
      isOnline: slot.isOnline,
      showTime: slot.showTimeInEvent ?? durationMinutes >= 60,
    };
  }
  return null;
}

function occupiedEventHeight(event: OccupiedEvent): number {
  return event.coveredCells * 27 - 1;
}

function occupiedEventStyle(event: OccupiedEvent) {
  const zoom = props.pinchZoomEnabled ? mobileZoom.value : 1;
  return { height: `${occupiedEventHeight(event) * zoom}px` };
}

function occupiedSlotTooltip(slot: OccupiedSlotDef): string {
  const lines = [
    `<b>${escapeHtml(occupiedSlotTitle(slot))}</b>`,
    ...(slot.occurrenceDate ? [`Date: ${escapeHtml(slot.occurrenceDate)}`] : []),
    `Time: ${escapeHtml(slot.startTime)}–${escapeHtml(scheduleEndTimeForDisplay(slot.startTime, slot.endTime))}`,
  ];
  if (slot.courseLabel) lines.push(`Course: ${escapeHtml(slot.courseLabel)}`);
  if (slot.studentName) lines.push(`Student: ${escapeHtml(slot.studentName)}`);
  lines.push(`Teacher: ${escapeHtml(slot.teacherName || '—')}`);
  lines.push(`Classroom: ${escapeHtml(slot.classroomName || '—')}`);
  if (slot.productName) lines.push(`Product: ${escapeHtml(slot.productName)}`);
  return lines.join('<br>');
}

function occupiedSlotTitle(slot: OccupiedSlotDef): string {
  return slot.displayTitle || slot.subjectName || slot.courseLabel || 'Lesson';
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function isConflictStart(day: number, minuteVal: number) {
  const current = occupiedMap.value.get(`${day}:${minuteVal}`) || [];
  const previous = occupiedMap.value.get(`${day}:${minuteVal - 30}`) || [];
  return current.length > 1 && previous.length <= 1;
}

// ─── Selection state ─────────────────────────
const selecting = ref(false);
const anchor = ref<{ weekday: number; hour: number; minute: number } | null>(null);
const currentHover = ref<{ weekday: number; hour: number; minute: number } | null>(null);

// 拖动预览与松开鼠标时使用相同的范围，保证看到的格数就是最终选中的课时。
const dragPreview = computed(() => {
  if (!selecting.value || !anchor.value) return null;
  const hover = currentHover.value || anchor.value;
  if (hover.weekday !== anchor.value.weekday) return null;
  const anchorMin = timeToMinutes(anchor.value.hour, anchor.value.minute);
  const hoverMin = timeToMinutes(hover.hour, hover.minute);
  const startMin = Math.min(anchorMin, hoverMin);
  const endMin = Math.max(anchorMin, hoverMin) + 30;
  return {
    weekday: anchor.value.weekday,
    startMin,
    endMin,
    units: (endMin - startMin) / 30,
  };
});

function clearDrag() {
  selecting.value = false;
  anchor.value = null;
  currentHover.value = null;
}

function onCellClick(weekday: number, hour: number, minute: number) {
  if (props.readonly) {
    if (isOccupied(weekday, hour, minute)) {
      emit('cell-click', weekday, hour, minute);
    }
    return;
  }
  if (isOccupied(weekday, hour, minute)) {
    emit('cell-click', weekday, hour, minute);
  }
}

function onMouseDown(weekday: number, hour: number, minute: number) {
  if (props.readonly) return;
  if (Date.now() < suppressMouseUntil) return;
  if (timeToMinutes(hour, minute) >= SCHEDULE_DAY_END_MINUTES) return;
  anchor.value = { weekday, hour, minute };
  currentHover.value = anchor.value;
  selecting.value = true;
}

function onMouseEnter(weekday: number, hour: number, minute: number) {
  if (props.readonly) return;
  if (!selecting.value) return;
  currentHover.value = { weekday, hour, minute };
}

// 鼠标拖动和触摸拖动共用同一套提交逻辑，保证两种输入得到一致的连续时段。
function commitPreviewSlot(preview: NonNullable<typeof dragPreview.value>) {
  const { startMin, endMin, units } = preview;

  const newSlot: SlotDef = {
    weekday: preview.weekday,
    startTime: formatTime(startMin),
    endTime: formatTime(endMin),
    plannedUnits: units,
  };

  // Check no overlap with existing selected slots on same weekday
  const overlaps = props.modelValue.some(s => {
    if (s.weekday !== newSlot.weekday) return false;
    const sStart = parseTimeToMin(s.startTime);
    const sEnd = parseEndTimeToMin(s.startTime, s.endTime);
    return startMin < sEnd && endMin > sStart;
  });
  if (!overlaps) {
    emit('update:modelValue', [...props.modelValue, newSlot]);
  }
}

function onMouseUp() {
  if (props.readonly) return;
  if (Date.now() < suppressMouseUntil) return;
  const preview = dragPreview.value;
  if (!preview) {
    clearDrag();
    return;
  }

  commitPreviewSlot(preview);
  clearDrag();
}

document.addEventListener('mouseup', onMouseUp);
onUnmounted(() => {
  document.removeEventListener('mouseup', onMouseUp);
});

// 手机端：初始自动缩放让整周可见，宽度变化时（旋转/切 tab）在未手动缩放前保持适配。
watch(() => props.pinchZoomEnabled, (enabled) => {
  if (!enabled) return;
  hasManualZoom.value = false;
  void nextTick(fitToWidth);
});

onMounted(() => {
  if (props.pinchZoomEnabled) void nextTick(fitToWidth);
  if (gridScroll.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => fitToWidth());
    resizeObserver.observe(gridScroll.value);
  }
});

onUnmounted(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
});

function removeSlot(idx: number) {
  const copy = [...props.modelValue];
  copy.splice(idx, 1);
  emit('update:modelValue', copy);
}

// ─── Cell class ──────────────────────────────
function cellClass(day: number, hour: number, minute: number) {
  if (props.readonly && !isOccupied(day, hour, minute)) return '';

  const minuteVal = timeToMinutes(hour, minute);
  const selectedClass = selectedOverlapClass(day, minuteVal);
  const previewClass = dragPreviewClass(day, minuteVal);

  // Occupied cells
  if (isOccupied(day, hour, minute)) {
    const slots = occupiedMap.value.get(`${day}:${minuteVal}`) || [];
    if (slots.length > 1) return `wts-cell--multiple${selectedClass}${previewClass}`;
    const slot = slots[0];
    return `${slot?.isCurrent ? 'wts-cell--current' : 'wts-cell--occupied'}${selectedClass}${previewClass}`;
  }

  // Selected slots in edit mode
  if (!props.readonly) {
    // Check which selected slots cover this cell
    const coveringSlot = props.modelValue.find(s => {
      if (s.weekday !== day) return false;
      return minuteVal >= parseTimeToMin(s.startTime)
        && minuteVal < parseEndTimeToMin(s.startTime, s.endTime);
    });
    if (coveringSlot) {
      const sMin = parseTimeToMin(coveringSlot.startTime);
      const eMin = parseEndTimeToMin(coveringSlot.startTime, coveringSlot.endTime);
      const isStart = minuteVal === sMin;
      const isEnd = minuteVal + 30 >= eMin;
      // Check if single cell
      if (eMin - sMin <= 30) return 'wts-cell--selected wts-cell--selected-single';
      if (isStart) return 'wts-cell--selected wts-cell--selected-start';
      if (isEnd) return 'wts-cell--selected wts-cell--selected-end';
      return 'wts-cell--selected';
    }

    if (previewClass) return 'wts-cell--preview';
  }
  return '';
}

function dragPreviewClass(day: number, minuteVal: number): string {
  const preview = dragPreview.value;
  if (!preview || preview.weekday !== day
    || minuteVal < preview.startMin || minuteVal >= preview.endMin) return '';
  if (!isOccupied(day, Math.floor(minuteVal / 60), minuteVal % 60)) return ' wts-cell--preview';
  return ' wts-cell--preview-overlap';
}

function selectedOverlapClass(day: number, minuteVal: number): string {
  if (props.readonly) return '';
  const slot = props.modelValue.find(item => item.weekday === day
    && minuteVal >= parseTimeToMin(item.startTime)
    && minuteVal < parseEndTimeToMin(item.startTime, item.endTime));
  if (!slot) return '';

  const start = parseTimeToMin(slot.startTime);
  const end = parseEndTimeToMin(slot.startTime, slot.endTime);
  const classes = ['wts-cell--selected-overlap'];
  if (minuteVal === Math.floor(start / 30) * 30) classes.push('wts-cell--selected-overlap-start');
  if (minuteVal + 30 >= end) classes.push('wts-cell--selected-overlap-end');
  return ` ${classes.join(' ')}`;
}

function weekdayName(d: number) {
  return dayNames[d - 1] || '?';
}
</script>

<style scoped>
.weekly-time-selector {
  user-select: none;
  font-size: 12px;
  width: 100%;
}

.wts-mobile-zoom-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  padding: 0 2px;
}

.wts-mobile-zoom-bar__label {
  color: #52647c;
  font-size: 12px;
  font-weight: 600;
}

.wts-mobile-zoom-controls {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 2px 8px rgb(15 23 42 / 8%);
}

.wts-zoom-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 30px;
  padding: 0 8px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #2563eb;
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
}

.wts-zoom-btn:active { background: #eef4ff; }
.wts-zoom-btn:disabled { color: #c3ccd9; cursor: default; }

.wts-zoom-btn--value {
  min-width: 46px;
  color: #52647c;
  font-size: 11px;
  font-weight: 600;
}

.wts-grid-scroll {
  max-height: min(85vh, 900px);
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.wts-grid-scroll--pinch {
  max-height: none;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  touch-action: pan-x pan-y;
  -webkit-overflow-scrolling: touch;
}

.wts-grid-scroll--pinch .wts-header,
.wts-grid-scroll--pinch .wts-row {
  grid-template-columns: var(--wts-mobile-time-width) repeat(7, minmax(var(--wts-mobile-day-width), 1fr));
  min-width: var(--wts-mobile-grid-width);
}

.wts-grid-scroll--pinch .wts-header { z-index: 20; }
.wts-grid-scroll--pinch .wts-header__spacer,
.wts-grid-scroll--pinch .wts-row__label {
  position: sticky;
  left: 0;
  z-index: 4;
  background: #f8fafc;
}
.wts-grid-scroll--pinch .wts-header__spacer { z-index: 5; }
.wts-grid-scroll--pinch .wts-body { min-width: var(--wts-mobile-grid-width); }
.wts-grid-scroll--pinch .wts-row,
.wts-grid-scroll--pinch .wts-row__label,
.wts-grid-scroll--pinch .wts-cell { height: var(--wts-mobile-row-height); }
.wts-grid-scroll--pinch .wts-row__label { font-size: var(--wts-mobile-font-small); line-height: var(--wts-mobile-row-height); }
.wts-grid-scroll--pinch .wts-row--last-slot .wts-row__label { line-height: var(--wts-mobile-font-small); }
.wts-grid-scroll--pinch .wts-header__day { font-size: var(--wts-mobile-font-large); }
.wts-grid-scroll--pinch .wts-header__date { font-size: var(--wts-mobile-font-small); }
.wts-grid-scroll--pinch .wts-event { padding: var(--wts-mobile-padding); }
.wts-grid-scroll--pinch .wts-event__title { font-size: var(--wts-mobile-font-medium); }
.wts-grid-scroll--pinch .wts-event__context,
.wts-grid-scroll--pinch .wts-event__time { font-size: var(--wts-mobile-font-small); }

/* ─── Header ─────────── */
.wts-header {
  position: sticky;
  top: 0;
  z-index: 3;
  display: grid;
  grid-template-columns: 56px repeat(7, minmax(0, 1fr));
  margin-bottom: 0;
  border-left: 1px solid transparent;
  border-right: 1px solid transparent;
  box-sizing: border-box;
  background: #fff;
}
.wts-header__spacer {
  /* occupies the 56px time-label column */
}
.wts-header__cell {
  text-align: center;
  padding: 8px 2px 6px;
  border-bottom: 2px solid #e8e8e8;
}
.wts-header__day {
  font-weight: 600;
  color: #1a1a1a;
  font-size: 12px;
}
.wts-header__date {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
  display: block;
}

.wts-drag-feedback {
  position: sticky;
  top: 42px;
  height: 0;
  z-index: 20;
  pointer-events: none;
}
.wts-drag-feedback__content {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border: 1px solid #93bbff;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 3px 12px rgb(25 79 159 / 18%);
  color: #1d4f99;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.wts-drag-feedback__content strong { color: #17427f; }

/* ─── Body (grid container) ─────────── */
.wts-body {
  border: 1px solid #e8e8e8;
  border-top: none;
  border-radius: 0 0 6px 6px;
  overflow: hidden;
}

.wts-row {
  display: grid;
  grid-template-columns: 56px repeat(7, minmax(0, 1fr));
  height: 26px;
}

.wts-row--last-slot .wts-row__label,
.wts-row--last-slot .wts-cell {
  border-bottom: 1px solid #d7dee8;
}

.wts-row--last-slot .wts-row__label {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  padding: 1px 0;
  line-height: 11px;
}

/* ─── Time label column ─────────── */
.wts-row__label {
  box-sizing: border-box;
  height: 26px;
  font-size: 11px;
  color: #999;
  line-height: 26px;
  text-align: center;
  background: #fafafa;
  border-right: 1px solid #e8e8e8;
  border-bottom: 1px solid #f0f0f0;
  font-variant-numeric: tabular-nums;
}
.wts-row:last-child .wts-row__label {
  border-bottom: none;
}

/* ─── Cells ─────────── */
.wts-cell {
  box-sizing: border-box;
  height: 26px;
  cursor: pointer;
  transition: background 0.1s;
  border-bottom: 1px solid #f0f0f0;
  border-right: 1px solid #f5f5f5;
  position: relative;
  overflow: hidden;
}
.wts-row:last-child .wts-cell {
  border-bottom: none;
}
.wts-cell:last-child {
  border-right: none;
}
.wts-cell:hover {
  background: #f5f7ff;
}

/* ─── Selected cells ─────────── */
.wts-cell--selected {
  background: #e6f0ff;
  border-color: #b3d4ff;
  z-index: 1;
}
.wts-cell--selected:hover {
  background: #d6e5ff;
}
/* Left accent bar for selected blocks */
.wts-cell--selected-start {
  box-shadow: inset 3px 0 0 #409eff;
}
.wts-cell--selected-single {
  box-shadow: inset 3px 0 0 #409eff, inset -3px 0 0 #409eff;
}

/* ─── Drag preview ─────────── */
.wts-cell--preview {
  background: #d6e5ff;
  border-color: #b3d4ff;
}
.wts-cell--preview-overlap,
.wts-cell--preview-overlap:hover {
  background: transparent;
  z-index: 4;
}
.wts-cell--preview-overlap::before {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 6px;
  background: repeating-linear-gradient(to bottom, #1d65db 0 6px, transparent 6px 9px);
  pointer-events: none;
  z-index: 5;
  content: '';
}

/* ─── Occupied cells remain visible and selectable in edit mode ─────────── */
.wts-cell--occupied {
  background: repeating-linear-gradient(
    -45deg,
    #fff1f0,
    #fff1f0 4px,
    #ffe8e6 4px,
    #ffe8e6 8px
  );
  cursor: pointer;
}
.wts-cell--occupied:hover {
  background: repeating-linear-gradient(
    -45deg,
    #fff1f0,
    #fff1f0 4px,
    #ffe8e6 4px,
    #ffe8e6 8px
  );
}

/* Multiple existing lessons stay visible; save validation checks actual dates. */
.wts-cell--multiple {
  background: #fff8eb;
  box-shadow: inset 0 0 0 1px #e6a23c;
  cursor: pointer;
  z-index: 2;
}

.wts-cell--multiple:hover {
  background: #fff0d1;
}

/* Keep the existing lesson readable; the narrow rail marks the new weekly selection. */
.wts-cell--selected-overlap {
  background: transparent;
  box-shadow: none;
  z-index: 4;
}
.wts-cell--selected-overlap:hover,
.wts-cell--occupied.wts-cell--selected-overlap:hover,
.wts-cell--multiple.wts-cell--selected-overlap:hover {
  background: transparent;
}
.wts-cell--selected-overlap::after {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 5px;
  background: #409eff;
  pointer-events: none;
  z-index: 5;
  content: '';
}
.wts-cell--selected-overlap-start::after {
  top: 2px;
  border-radius: 3px 3px 0 0;
}
.wts-cell--selected-overlap-end::after {
  bottom: 2px;
  border-radius: 0 0 3px 3px;
}
.wts-cell--selected-overlap-start.wts-cell--selected-overlap-end::after {
  border-radius: 3px;
}

/* ─── Current package's own slots ─────────── */
.wts-cell--current {
  background: #e6f7ff;
  border-color: #91d5ff;
  cursor: default;
}

/* ─── Occupied event content ─────────── */
.wts-cell--event-start {
  overflow: visible;
  z-index: 3;
}
.wts-event {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 5px;
  overflow: hidden;
  color: #6b3a3a;
  line-height: 1.2;
  pointer-events: none;
  border-left: 3px solid #e67a73;
  background: rgba(255, 248, 247, 0.94);
  box-sizing: border-box;
}
.wts-event__title,
.wts-event__context,
.wts-event__time {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wts-event__title {
  font-size: 11px;
  font-weight: 700;
}
.wts-event__context {
  font-size: 10px;
  color: #8a5b5b;
}
.wts-event__time {
  margin-top: auto;
  font-size: 10px;
  color: #9a6b6b;
  font-variant-numeric: tabular-nums;
}
.wts-event--online {
  border-left-color: #7db7f7;
  background: rgba(232, 243, 255, 0.96);
  color: #285b8f;
}
.wts-event--online .wts-event__context,
.wts-event--online .wts-event__time {
  color: #52769b;
}
.wts-event--multiple {
  border-left-color: #e6a23c;
  background: rgba(255, 248, 235, 0.96);
  color: #6b4a16;
}
.wts-event--multiple .wts-event__title {
  font-size: 10px;
}

/* ─── Summary ─────────── */
.wts-summary {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.wts-summary__title {
  font-weight: 600;
  color: #303133;
  font-size: 12px;
  margin-right: 4px;
}
.wts-tag {
  margin: 0;
}

/* ─── Empty state ─────────── */
.wts-empty {
  margin-top: 10px;
  color: #999;
  font-size: 12px;
  text-align: center;
  padding: 16px 0;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  background: #fafafa;
}
</style>
