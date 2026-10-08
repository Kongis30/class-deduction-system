<template>
  <div class="weekly-time-selector">
    <div v-if="pinchZoomEnabled" class="wts-mobile-zoom-controls" aria-label="Calendar zoom controls">
      <span>Pinch to zoom</span>
      <button type="button" aria-label="Zoom out" :disabled="mobileZoom <= MIN_MOBILE_ZOOM" @click="adjustMobileZoom(-ZOOM_STEP)">−</button>
      <output>{{ Math.round(mobileZoom * 100) }}%</output>
      <button type="button" aria-label="Zoom in" :disabled="mobileZoom >= MAX_MOBILE_ZOOM" @click="adjustMobileZoom(ZOOM_STEP)">+</button>
      <button v-if="mobileZoom !== 1" type="button" class="wts-mobile-zoom-controls__reset" @click="mobileZoom = 1">Reset</button>
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
              <span>24:00</span>
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
            @mousedown.prevent="onMouseDown(day, time.hour, time.minute)"
            @mouseenter="onMouseEnter(day, time.hour, time.minute)"
            @click="onCellClick(day, time.hour, time.minute)"
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
                  'wts-event--conflict': occupiedEvent(day, time.hour, time.minute)?.isConflict,
                  'wts-event--online': occupiedEvent(day, time.hour, time.minute)?.isOnline && !occupiedEvent(day, time.hour, time.minute)?.isConflict,
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
                  'wts-event--conflict': occupiedEvent(day, time.hour, time.minute)?.isConflict,
                  'wts-event--online': occupiedEvent(day, time.hour, time.minute)?.isOnline && !occupiedEvent(day, time.hour, time.minute)?.isConflict,
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
import { computed, nextTick, onUnmounted, ref } from 'vue';
import { scheduleEndTimeForDisplay, scheduleTimeMinutes } from '@/utils/schedule-time';

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

const MIN_MOBILE_ZOOM = 0.8;
const MAX_MOBILE_ZOOM = 2.5;
const ZOOM_STEP = 0.15;
const mobileZoom = ref(1);
const gridScroll = ref<HTMLElement | null>(null);
let pinchStartDistance = 0;
let pinchStartZoom = 1;
let pinchAnchorX = 0;
let pinchAnchorY = 0;

const mobileZoomStyle = computed(() => ({
  '--wts-mobile-grid-width': `${(38 + 7 * 31) * mobileZoom.value}px`,
  '--wts-mobile-time-width': `${38 * mobileZoom.value}px`,
  '--wts-mobile-day-width': `${31 * mobileZoom.value}px`,
  '--wts-mobile-row-height': `${26 * mobileZoom.value}px`,
  '--wts-mobile-font-small': `${10 * mobileZoom.value}px`,
  '--wts-mobile-font-medium': `${11 * mobileZoom.value}px`,
  '--wts-mobile-font-large': `${12 * mobileZoom.value}px`,
  '--wts-mobile-padding': `${4 * mobileZoom.value}px`,
}) as Record<string, string>);

function adjustMobileZoom(delta: number) {
  mobileZoom.value = clampMobileZoom(mobileZoom.value + delta);
}

function clampMobileZoom(value: number) {
  return Math.min(MAX_MOBILE_ZOOM, Math.max(MIN_MOBILE_ZOOM, value));
}

function touchDistance(first: Touch, second: Touch) {
  return Math.hypot(first.clientX - second.clientX, first.clientY - second.clientY);
}

// 双指缩放只改变手机日历网格密度，并围绕手势中心保留当前查看位置。
function onTouchStart(event: TouchEvent) {
  if (!props.pinchZoomEnabled || event.touches.length !== 2) return;
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

function onTouchMove(event: TouchEvent) {
  if (!props.pinchZoomEnabled || !pinchStartDistance || event.touches.length !== 2) return;
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
}

function onTouchEnd(event: TouchEvent) {
  if (event.touches.length < 2) pinchStartDistance = 0;
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
const GRID_START_MINUTES = 7 * 60 + 30;
const GRID_END_MINUTES = 24 * 60;

const timeSlots = computed(() => {
  const rows: { label: string; hour: number; minute: number; isLastSlot: boolean }[] = [];
  for (let minutes = GRID_START_MINUTES; minutes < GRID_END_MINUTES; minutes += 30) {
    const hour = Math.floor(minutes / 60);
    const minute = minutes % 60;
    rows.push({ label: formatTime(minutes), hour, minute, isLastSlot: minutes === GRID_END_MINUTES - 30 });
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
    const firstCellMin = Math.floor(startMin / 30) * 30;
    for (let m = firstCellMin; m < endMin; m += 30) {
      if (m + 30 <= startMin) continue;
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
    `<b>Conflict: ${slots.length} lessons overlap</b>`,
    ...slots.map((slot, index) => `${index + 1}. ${occupiedSlotTooltip(slot)}`),
  ].join('<br><br>');
}

interface OccupiedEvent {
  title: string;
  context?: string;
  timeLabel?: string;
  coveredCells: number;
  isConflict?: boolean;
  isOnline?: boolean;
  showTime: boolean;
}

function occupiedEvent(day: number, hour: number, minute: number): OccupiedEvent | null {
  const minuteVal = timeToMinutes(hour, minute);
  const slots = occupiedMap.value.get(`${day}:${minuteVal}`);
  if (!slots?.length) return null;
  if (slots.length > 1 && isConflictStart(day, minuteVal)) {
    return {
      title: `${slots.length} lessons conflict`,
      coveredCells: 1,
      isConflict: true,
      showTime: false,
    };
  }
  const slot = slots[0];
  if (minuteVal === Math.floor(parseTimeToMin(slot.startTime) / 30) * 30) {
    const endMin = parseEndTimeToMin(slot.startTime, slot.endTime);
    const durationMinutes = endMin - parseTimeToMin(slot.startTime);
    const coveredCells = Math.max(
      1,
      Math.ceil(endMin / 30) - Math.floor(parseTimeToMin(slot.startTime) / 30),
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
  if (timeToMinutes(hour, minute) >= GRID_END_MINUTES) return;
  if (isOccupied(weekday, hour, minute)) return;
  anchor.value = { weekday, hour, minute };
  selecting.value = true;
}

function onMouseEnter(weekday: number, hour: number, minute: number) {
  if (props.readonly) return;
  if (!selecting.value) return;
  currentHover.value = { weekday, hour, minute };
}

function onMouseUp() {
  if (props.readonly) return;
  if (!selecting.value || !anchor.value) {
    selecting.value = false;
    return;
  }
  const hover = currentHover.value || anchor.value;
  if (hover.weekday !== anchor.value.weekday) {
    selecting.value = false;
    return;
  }

  const anchorMin = timeToMinutes(anchor.value.hour, anchor.value.minute);
  const hoverMin = timeToMinutes(hover.hour, hover.minute);
  if (hoverMin !== anchorMin && Math.abs(hoverMin - anchorMin) < 30) {
    selecting.value = false;
    return;
  }

  const startMin = Math.min(anchorMin, hoverMin);
  const endMin = hoverMin === anchorMin
    ? anchorMin + 30
    : Math.max(anchorMin, hoverMin);
  const units = Math.round((endMin - startMin) / 30);

  // Check no overlap with occupied slots
  const overlapsOccupied = (props.occupiedSlots || []).some(s => {
    if (s.weekday !== anchor.value!.weekday) return false;
    const sStart = parseTimeToMin(s.startTime);
    const sEnd = parseEndTimeToMin(s.startTime, s.endTime);
    return startMin < sEnd && endMin > sStart;
  });
  if (overlapsOccupied) {
    selecting.value = false;
    anchor.value = null;
    currentHover.value = null;
    return;
  }

  const newSlot: SlotDef = {
    weekday: anchor.value.weekday,
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

  selecting.value = false;
  anchor.value = null;
  currentHover.value = null;
}

document.addEventListener('mouseup', onMouseUp);
onUnmounted(() => {
  document.removeEventListener('mouseup', onMouseUp);
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

  // Occupied cells
  if (isOccupied(day, hour, minute)) {
    const slots = occupiedMap.value.get(`${day}:${minuteVal}`) || [];
    if (slots.length > 1) return 'wts-cell--conflict';
    const slot = slots[0];
    return slot?.isCurrent ? 'wts-cell--current' : 'wts-cell--occupied';
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

    // Drag preview
    if (selecting.value && anchor.value && currentHover.value) {
      if (anchor.value.weekday === day && currentHover.value.weekday === day) {
        const aMin = timeToMinutes(anchor.value.hour, anchor.value.minute);
        const hMin = timeToMinutes(currentHover.value.hour, currentHover.value.minute);
        const lo = Math.min(aMin, hMin);
        const hi = Math.max(aMin, hMin);
        if (minuteVal >= lo && minuteVal < hi) {
          return 'wts-cell--preview';
        }
      }
    }
  }
  return '';
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

.wts-mobile-zoom-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
  min-height: 34px;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 11px;
}

.wts-mobile-zoom-controls button {
  display: grid;
  place-items: center;
  min-width: 30px;
  height: 28px;
  padding: 0 7px;
  border: 1px solid #d7e0ea;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  font: inherit;
  font-size: 15px;
}

.wts-mobile-zoom-controls button:disabled { color: #b7c0cb; }
.wts-mobile-zoom-controls output { min-width: 38px; color: #334155; font-variant-numeric: tabular-nums; text-align: center; }
.wts-mobile-zoom-controls .wts-mobile-zoom-controls__reset { min-width: 46px; font-size: 11px; }

.wts-grid-scroll {
  max-height: min(85vh, 900px);
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.wts-grid-scroll--pinch {
  max-height: min(62vh, 620px);
  overflow-x: auto;
  overflow-y: auto;
  overscroll-behavior: contain;
  touch-action: pan-x pan-y;
  -webkit-overflow-scrolling: touch;
}

.wts-grid-scroll--pinch .wts-header,
.wts-grid-scroll--pinch .wts-row {
  grid-template-columns: var(--wts-mobile-time-width) repeat(7, minmax(var(--wts-mobile-day-width), 1fr));
  min-width: var(--wts-mobile-grid-width);
}

.wts-grid-scroll--pinch .wts-header { z-index: 20; }
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

/* ─── Occupied cells (conflicting) ─────────── */
.wts-cell--occupied {
  background: repeating-linear-gradient(
    -45deg,
    #fff1f0,
    #fff1f0 4px,
    #ffe8e6 4px,
    #ffe8e6 8px
  );
  cursor: not-allowed;
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

/* ─── Hard conflicts: multiple lessons cover the same 30-minute cell ─────────── */
.wts-cell--conflict {
  background: #fff1f0;
  box-shadow:
    inset 4px 0 0 #f56c6c,
    inset 0 0 0 1px #f56c6c;
  cursor: not-allowed;
  z-index: 2;
}

.wts-cell--conflict:hover {
  background: #ffe4e1;
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
.wts-event--conflict {
  border-left-color: #f56c6c;
  background: #fff1f0;
  color: #b42318;
}
.wts-event--conflict .wts-event__title {
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
