<template>
  <div class="weekly-time-selector">
    <div class="wts-header">
      <span class="wts-header__spacer"></span>
      <span class="wts-header__cell" v-for="(label, idx) in dayLabelsWithDates" :key="idx">
        <span class="wts-header__day">{{ label.day }}</span>
        <span v-if="label.date" class="wts-header__date">{{ label.date }}</span>
      </span>
    </div>
    <div class="wts-body">
      <div class="wts-row" v-for="time in timeSlots" :key="time.label">
        <span class="wts-row__label">{{ time.label }}</span>
        <div
          v-for="day in 7"
          :key="day"
          class="wts-cell"
          :class="cellClass(day, time.hour, time.minute)"
          :title="occupiedTitle(day, time.hour, time.minute)"
          @mousedown.prevent="onMouseDown(day, time.hour, time.minute)"
          @mouseenter="onMouseEnter(day, time.hour, time.minute)"
          @click="onCellClick(day, time.hour, time.minute)"
        >
          <span v-if="occupiedLabel(day, time.hour, time.minute)" class="wts-cell__label">
            {{ occupiedLabel(day, time.hour, time.minute) }}
          </span>
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
        {{ weekdayName(slot.weekday) }} {{ slot.startTime }}–{{ slot.endTime }}
        ({{ (slot.plannedUnits * 0.5).toFixed(1) }}h)
      </el-tag>
    </div>
    <div v-else-if="!readonly && modelValue.length === 0 && !hideHint" class="wts-empty">
      Click cells to select class time slots. Drag from start to end time.
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue';

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
  studentName?: string;
  teacherName?: string;
  classroomName?: string;
  /** When true, renders as a blue highlight instead of gray striped — used to mark the "current" package's own slots. */
  isCurrent?: boolean;
}

const props = withDefaults(defineProps<{
  modelValue: SlotDef[];
  occupiedSlots?: OccupiedSlotDef[];
  weekStartDate?: string;  // ISO date of the Monday for the displayed week
  readonly?: boolean;
  hideHint?: boolean;
}>(), {
  readonly: false,
  hideHint: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', val: SlotDef[]): void;
  (e: 'cell-click', weekday: number, hour: number, minute: number): void;
}>();

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
  const rows: { label: string; hour: number; minute: number }[] = [];
  for (let h = 8; h <= 20; h++) {
    rows.push({ label: `${String(h).padStart(2, '0')}:00`, hour: h, minute: 0 });
    rows.push({ label: `${String(h).padStart(2, '0')}:30`, hour: h, minute: 30 });
  }
  return rows;
});

// ─── Time helpers ───────────────────────────
function timeToMinutes(h: number, m: number) {
  return h * 60 + m;
}

function parseTimeToMin(t: string) {
  const [hh, mm] = t.split(':').map(Number);
  return timeToMinutes(hh, mm);
}

function formatTime(minutes: number) {
  const hh = String(Math.floor(minutes / 60)).padStart(2, '0');
  const mm = String(minutes % 60).padStart(2, '0');
  return `${hh}:${mm}`;
}

// ─── Occupied slot lookup ───────────────────
const occupiedMap = computed(() => {
  const map = new Map<string, OccupiedSlotDef>();
  for (const slot of (props.occupiedSlots || [])) {
    const startMin = parseTimeToMin(slot.startTime);
    const endMin = parseTimeToMin(slot.endTime);
    for (let m = startMin; m < endMin; m += 30) {
      map.set(`${slot.weekday}:${m}`, slot);
    }
  }
  return map;
});

function isOccupied(day: number, hour: number, minute: number): boolean {
  return occupiedMap.value.has(`${day}:${timeToMinutes(hour, minute)}`);
}

function occupiedTitle(day: number, hour: number, minute: number): string {
  const slot = occupiedMap.value.get(`${day}:${timeToMinutes(hour, minute)}`);
  if (!slot) return '';
  const parts = [slot.courseLabel, `${slot.startTime}–${slot.endTime}`];
  if (slot.studentName) parts.push(`Student: ${slot.studentName}`);
  if (slot.teacherName) parts.push(`Teacher: ${slot.teacherName}`);
  if (slot.classroomName) parts.push(`Room: ${slot.classroomName}`);
  return parts.join('\n');
}

function occupiedLabel(day: number, hour: number, minute: number): string {
  const slot = occupiedMap.value.get(`${day}:${timeToMinutes(hour, minute)}`);
  if (!slot) return '';
  // Only show label on first cell of the slot block
  if (timeToMinutes(hour, minute) === parseTimeToMin(slot.startTime)) {
    return slot.courseLabel;
  }
  return '';
}

// ─── Selection state ─────────────────────────
const selecting = ref(false);
const anchor = ref<{ weekday: number; hour: number; minute: number } | null>(null);
const currentHover = ref<{ weekday: number; hour: number; minute: number } | null>(null);

function onCellClick(weekday: number, hour: number, minute: number) {
  if (props.readonly) return;
  if (isOccupied(weekday, hour, minute)) {
    emit('cell-click', weekday, hour, minute);
  }
}

function onMouseDown(weekday: number, hour: number, minute: number) {
  if (props.readonly) return;
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
  if (Math.abs(hoverMin - anchorMin) < 30) {
    selecting.value = false;
    return;
  }

  const startMin = Math.min(anchorMin, hoverMin);
  const endMin = Math.max(anchorMin, hoverMin);
  const units = Math.round((endMin - startMin) / 30);

  // Check no overlap with occupied slots
  const overlapsOccupied = (props.occupiedSlots || []).some(s => {
    if (s.weekday !== anchor.value!.weekday) return false;
    const sStart = parseTimeToMin(s.startTime);
    const sEnd = parseTimeToMin(s.endTime);
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
    const sEnd = parseTimeToMin(s.endTime);
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
    const slot = occupiedMap.value.get(`${day}:${minuteVal}`);
    return slot?.isCurrent ? 'wts-cell--current' : 'wts-cell--occupied';
  }

  // Selected slots in edit mode
  if (!props.readonly) {
    // Check which selected slots cover this cell
    const coveringSlot = props.modelValue.find(s => {
      if (s.weekday !== day) return false;
      return minuteVal >= parseTimeToMin(s.startTime) && minuteVal < parseTimeToMin(s.endTime);
    });
    if (coveringSlot) {
      const sMin = parseTimeToMin(coveringSlot.startTime);
      const eMin = parseTimeToMin(coveringSlot.endTime);
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

/* ─── Header ─────────── */
.wts-header {
  display: grid;
  grid-template-columns: 56px repeat(7, 1fr);
  margin-bottom: 0;
  border-left: 1px solid transparent;
  border-right: 1px solid transparent;
  box-sizing: border-box;
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
  grid-template-columns: 56px repeat(7, 1fr);
}

/* ─── Time label column ─────────── */
.wts-row__label {
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
  height: 26px;
  cursor: pointer;
  transition: background 0.1s;
  border-bottom: 1px solid #f0f0f0;
  border-right: 1px solid #f5f5f5;
  position: relative;
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

/* ─── Current package's own slots ─────────── */
.wts-cell--current {
  background: #e6f7ff;
  border-color: #91d5ff;
  cursor: default;
}

/* ─── Occupied cell label ─────────── */
.wts-cell__label {
  display: block;
  font-size: 8px;
  color: #666;
  line-height: 26px;
  padding: 0 0 0 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
  font-weight: 600;
}
.wts-cell--occupied .wts-cell__label {
  color: #999;
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
