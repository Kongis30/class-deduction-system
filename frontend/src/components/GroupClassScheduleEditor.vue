<template>
  <el-dialog
    :model-value="visible"
    :title="`${isCreate ? 'Create' : 'Edit'} schedule${classItem ? ` · ${classItem.className}` : ''}`"
    width="min(960px, calc(100vw - 24px))"
    class="class-period-dialog"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="class-period-dialog__body">
      <p v-if="classItem" class="class-period-dialog__intro">
        Class period {{ classItem.startDate }}–{{ classItem.scheduleBaseEndDate || classItem.endDate }}.
        Add periods for different dates and times. Gaps between periods have no classes.
      </p>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
      <template v-if="classItem && !loadError">
        <section v-for="(period, index) in segments" :key="period.key" class="class-period">
          <header class="class-period__heading">
            <button type="button" class="class-period__toggle" @click="expandedKey = expandedKey === period.key ? null : period.key">
              <strong>Period {{ index + 1 }}</strong>
              <span>{{ period.startDate || 'Start date' }}–{{ period.endDate || 'End date' }} · {{ period.slots.length }} weekly time(s)</span>
            </button>
            <el-button size="small" text @click="copyPeriod(period)">Copy</el-button>
            <el-button v-if="segments.length > 1 && !isPastPeriod(period)" size="small" text type="danger" @click="removePeriod(index)">Remove</el-button>
          </header>
          <div v-if="expandedKey === period.key" class="class-period__content">
            <div class="class-period__dates">
              <label><span>From</span><el-date-picker v-model="period.startDate" type="date" value-format="YYYY-MM-DD" :disabled="isPastPeriod(period)" /></label>
              <label><span>Through</span><el-date-picker v-model="period.endDate" type="date" value-format="YYYY-MM-DD" :disabled="isPastPeriod(period)" /></label>
            </div>
            <el-alert v-if="isPastPeriod(period)" title="Historical period: existing lessons are kept and this period is read-only." type="info" :closable="false" />
            <template v-else>
              <div class="class-period__calendar"><WeeklyTimeSelector v-model="period.slots" hide-hint /></div>
              <article v-for="(slot, slotIndex) in period.slots" :key="`${slot.weekday}-${slot.startTime}-${slotIndex}`" class="class-period__slot">
                <div class="class-period__slot-head">
                  <strong>{{ slotLabel(slot) }}</strong>
                  <span>{{ (slot.plannedUnits * 0.5).toFixed(1) }}h</span>
                  <label class="class-period__rotation">A/B rotation <el-switch v-model="slot.rotationEnabled" /></label>
                </div>
                <div class="class-period__fields">
                  <label><span>Teacher</span><el-select v-model="slot.teacherId" clearable filterable placeholder="No teacher"><el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" /></el-select></label>
                  <label><span>Course / subject</span><el-input v-model="slot.subjectName" maxlength="128" placeholder="e.g. English" /></label>
                  <label><span>Classroom</span><el-select v-model="slot.classroomSelection" clearable filterable placeholder="No classroom"><el-option label="Online" value="ONLINE" /><el-option-group v-for="group in classroomGroups" :key="group.name" :label="group.name"><el-option v-for="room in group.rooms" :key="room.id" :label="room.roomName" :value="`ROOM:${room.id}`" /></el-option-group></el-select></label>
                </div>
                <div v-if="slot.rotationEnabled" class="class-period__fields class-period__fields--alternate">
                  <label><span>Alternate teacher</span><el-select v-model="slot.alternateTeacherId" clearable filterable placeholder="Select teacher"><el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" /></el-select></label>
                  <label><span>Alternate course / subject</span><el-input v-model="slot.alternateSubjectName" maxlength="128" /></label>
                  <label><span>Alternate classroom</span><el-select v-model="slot.alternateClassroomSelection" clearable filterable placeholder="No classroom"><el-option label="Online" value="ONLINE" /><el-option-group v-for="group in classroomGroups" :key="group.name" :label="group.name"><el-option v-for="room in group.rooms" :key="room.id" :label="room.roomName" :value="`ROOM:${room.id}`" /></el-option-group></el-select></label>
                  <label><span>Start rotation on or after</span><el-date-picker v-model="slot.rotationAnchorDate" type="date" value-format="YYYY-MM-DD" placeholder="Use period start" /></label>
                </div>
              </article>
            </template>
          </div>
        </section>
        <el-button class="class-period-dialog__add" plain @click="addPeriod">Add period</el-button>
        <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" show-icon />
        <section v-if="preview" class="class-period-preview">
          <strong>Preview · {{ preview.futureLessonCount }} future lessons · {{ preview.futureHours.toFixed(1) }}h</strong>
          <p>{{ preview.cancelledFutureOccurrences }} ordinary future lesson(s) replaced; {{ preview.generatedOccurrences }} generated; {{ preview.retainedFutureOccurrences }} kept. {{ preview.sessionEntitlementChanges }} session enrollment end date(s) change.</p>
          <div class="class-period-preview__lessons">
            <span v-for="(lesson, index) in preview.firstLessons" :key="index">
              {{ lesson.startAt.replace('T', ' ').slice(0, 16) }} · {{ lesson.subjectName || 'Class' }} · {{ lesson.hours.toFixed(1) }}h{{ lesson.preserved ? ' · kept' : '' }}
            </span>
          </div>
          <small v-if="preview.futureLessonCount > preview.firstLessons.length">Showing the first {{ preview.firstLessons.length }} future lessons.</small>
        </section>
      </template>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">Cancel</el-button>
      <el-button :loading="previewing" :disabled="loading || saving || !isDirty || Boolean(loadError)" @click="previewChanges">Preview lessons</el-button>
      <el-button type="primary" :loading="saving" :disabled="!preview || !isDirty || loading || previewing" @click="saveChanges">{{ isCreate ? 'Create schedule' : 'Save changes' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';
import { fetchAllClassrooms, fetchAllTeachers } from '@/api/base-data';
import { extractErrorMessage } from '@/api/http';
import { fetchClassGroups, previewClassSchedule, updateClassSchedule } from '@/api/schedule';
import type { ClassSchedulePreview, ClassScheduleSlotPayload, ClassScheduleUpdatePayload } from '@/api/schedule';
import type { ClassGroupItem, ClassroomItem, EntityId, TeacherItem } from '@/types/domain';
import { scheduleEndTimeForApi, scheduleEndTimeForDisplay } from '@/utils/schedule-time';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { SlotDef } from '@/components/WeeklyTimeSelector.vue';

interface EditableSlot extends SlotDef {
  slotLineageId?: EntityId;
  teacherId?: EntityId;
  subjectName: string;
  classroomSelection: string;
  alternateTeacherId?: EntityId;
  alternateSubjectName: string;
  alternateClassroomSelection: string;
  rotationAnchorDate: string;
  rotationEnabled: boolean;
}
interface EditablePeriod { key: number; startDate: string; endDate: string; slots: EditableSlot[] }

const props = defineProps<{ visible: boolean; classId?: EntityId | null }>();
const emit = defineEmits<{ 'update:visible': [value: boolean]; saved: [] }>();
const classItem = ref<ClassGroupItem | null>(null);
const segments = ref<EditablePeriod[]>([]);
const teachers = ref<TeacherItem[]>([]);
const classrooms = ref<ClassroomItem[]>([]);
const preview = ref<ClassSchedulePreview | null>(null);
const previewError = ref('');
const loadError = ref('');
const loading = ref(false);
const saving = ref(false);
const previewing = ref(false);
const isCreate = ref(false);
const expandedKey = ref<number | null>(null);
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/New_York', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const initialFingerprint = ref('');
const isDirty = computed(() => JSON.stringify(segments.value) !== initialFingerprint.value);
let nextKey = 1;
let requestId = 0;

const classroomGroups = computed(() => {
  const byCampus = new Map<string, ClassroomItem[]>();
  for (const room of classrooms.value) {
    const name = room.campus?.trim() || 'Unassigned campus';
    byCampus.set(name, [...(byCampus.get(name) || []), room]);
  }
  return [...byCampus].map(([name, rooms]) => ({ name, rooms: rooms.sort((a, b) => a.roomName.localeCompare(b.roomName, undefined, { numeric: true })) }))
    .sort((a, b) => a.name.localeCompare(b.name));
});

watch(() => [props.visible, props.classId] as const, async ([visible, classId]) => {
  if (!visible || classId == null) return;
  const currentRequest = ++requestId;
  loading.value = true;
  loadError.value = '';
  preview.value = null;
  classItem.value = null;
  try {
    const [classResponse, teacherResponse, roomResponse] = await Promise.all([
      fetchClassGroups({ includeInactiveProducts: true }), fetchAllTeachers(), fetchAllClassrooms(),
    ]);
    if (currentRequest !== requestId) return;
    const item = (classResponse.data.data || []).find(value => String(value.id) === String(classId));
    if (!item) throw new Error('Class schedule not found. Refresh this page and try again.');
    classItem.value = item;
    teachers.value = (teacherResponse.data.data || []).sort((a, b) => a.teacherName.localeCompare(b.teacherName, undefined, { sensitivity: 'base' }));
    classrooms.value = roomResponse.data.data || [];
    isCreate.value = !item.scheduleSlots?.length;
    segments.value = periodsFromClass(item);
    initialFingerprint.value = JSON.stringify(segments.value);
    expandedKey.value = segments.value.find(period => !isPastPeriod(period))?.key || segments.value[0]?.key || null;
  } catch (error) {
    if (currentRequest === requestId) loadError.value = extractErrorMessage(error);
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}, { immediate: true });

watch(segments, () => { preview.value = null; previewError.value = ''; }, { deep: true });

// 旧周课表包装成单段；跨越今天的段拆出只读历史，避免编辑未来时重算过去的报名日期。
function periodsFromClass(item: ClassGroupItem): EditablePeriod[] {
  const baseEnd = item.scheduleBaseEndDate || item.endDate || '';
  const grouped = new Map<string, EditablePeriod>();
  for (const slot of item.scheduleSlots || []) {
    const startDate = slot.effectiveStartDate || item.startDate || '';
    const endDate = slot.effectiveEndDate || baseEnd;
    const key = `${startDate}|${endDate}`;
    let period = grouped.get(key);
    if (!period) { period = { key: nextKey++, startDate, endDate, slots: [] }; grouped.set(key, period); }
    period.slots.push({
      slotLineageId: slot.slotLineageId,
      weekday: slot.weekday, startTime: slot.startTime.slice(0, 5),
      endTime: scheduleEndTimeForDisplay(slot.startTime, slot.endTime),
      plannedUnits: slot.plannedUnits || 1,
      teacherId: slot.teacherId ?? item.teacherId,
      subjectName: slot.subjectName || '',
      classroomSelection: slot.deliveryMode === 'ONLINE' ? 'ONLINE'
        : slot.classroomId != null || item.classroomId != null ? `ROOM:${slot.classroomId ?? item.classroomId}` : '',
      alternateTeacherId: slot.alternateTeacherId,
      alternateSubjectName: slot.alternateSubjectName || '',
      alternateClassroomSelection: slot.alternateDeliveryMode === 'ONLINE' ? 'ONLINE'
        : slot.alternateClassroomId != null ? `ROOM:${slot.alternateClassroomId}` : '',
      rotationAnchorDate: slot.rotationAnchorDate || '',
      rotationEnabled: Boolean(slot.alternateTeacherId || slot.alternateSubjectName || slot.alternateClassroomId || slot.alternateDeliveryMode),
    });
  }
  if (!grouped.size) return [{ key: nextKey++, startDate: item.startDate || '', endDate: baseEnd, slots: [] }];
  const result = [...grouped.values()].sort((a, b) => a.startDate.localeCompare(b.startDate));
  return result.flatMap(period => {
    if (period.startDate >= today || period.endDate < today) return [period];
    const yesterday = dayjs(today).subtract(1, 'day').format('YYYY-MM-DD');
    return [
      { ...period, endDate: yesterday },
      { key: nextKey++, startDate: today, endDate: period.endDate, slots: period.slots.map(slot => ({ ...slot })) },
    ];
  });
}

function isPastPeriod(period: EditablePeriod) { return Boolean(period.endDate && period.endDate < today); }
function slotLabel(slot: SlotDef) {
  return `${['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][slot.weekday] || '?'} ${slot.startTime}–${slot.endTime}`;
}
function addPeriod() {
  const latest = [...segments.value].sort((a, b) => b.endDate.localeCompare(a.endDate))[0];
  const startDate = latest?.endDate && latest.endDate < (classItem.value?.scheduleBaseEndDate || classItem.value?.endDate || '')
    ? dayjs(latest.endDate).add(1, 'day').format('YYYY-MM-DD') : '';
  const period = { key: nextKey++, startDate, endDate: '', slots: [] };
  segments.value.push(period);
  expandedKey.value = period.key;
}
function copyPeriod(source: EditablePeriod) {
  const period = { key: nextKey++, startDate: '', endDate: '', slots: source.slots.map(slot => ({
    ...slot, slotLineageId: undefined, rotationAnchorDate: '',
  })) };
  segments.value.push(period);
  expandedKey.value = period.key;
}
function removePeriod(index: number) { segments.value.splice(index, 1); }

function payload(): ClassScheduleUpdatePayload | null {
  const item = classItem.value;
  if (!item || !segments.value.length) return null;
  const start = item.startDate || '';
  const end = item.scheduleBaseEndDate || item.endDate || '';
  const ordered = [...segments.value].sort((a, b) => a.startDate.localeCompare(b.startDate));
  for (const [index, period] of ordered.entries()) {
    if (!period.startDate || !period.endDate || period.startDate > period.endDate || period.startDate < start || period.endDate > end) {
      ElMessage.warning(`Period ${index + 1}: choose dates inside the original class period.`); return null;
    }
    if (!period.slots.length) { ElMessage.warning(`Period ${index + 1}: add a weekly class time.`); return null; }
    if (index > 0 && ordered[index - 1].endDate >= period.startDate) {
      ElMessage.warning('Schedule periods cannot overlap.'); return null;
    }
  }
  return { segments: ordered.map(period => ({
    startDate: period.startDate, endDate: period.endDate,
    slots: period.slots.map(slot => slotPayload(slot, period)),
  })) };
}

function slotPayload(slot: EditableSlot, period: EditablePeriod): ClassScheduleSlotPayload {
  return {
    slotLineageId: slot.slotLineageId,
    teacherId: slot.teacherId,
    subjectName: slot.subjectName?.trim() || undefined,
    classroomId: slot.classroomSelection?.startsWith('ROOM:') ? slot.classroomSelection.slice(5) : undefined,
    deliveryMode: slot.classroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON',
    alternateTeacherId: slot.rotationEnabled ? slot.alternateTeacherId : undefined,
    alternateSubjectName: slot.rotationEnabled ? slot.alternateSubjectName?.trim() || undefined : undefined,
    alternateClassroomId: slot.rotationEnabled && slot.alternateClassroomSelection?.startsWith('ROOM:')
      ? slot.alternateClassroomSelection.slice(5) : undefined,
    alternateDeliveryMode: slot.rotationEnabled ? slot.alternateClassroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON' : undefined,
    rotationAnchorDate: slot.rotationEnabled
      ? slot.rotationAnchorDate || (slot.slotLineageId ? classItem.value?.startDate : period.startDate)
      : undefined,
    weekday: slot.weekday, startTime: slot.startTime,
    endTime: scheduleEndTimeForApi(slot.endTime), plannedUnits: slot.plannedUnits,
  };
}

async function previewChanges() {
  const request = payload();
  if (!request || props.classId == null) return;
  previewing.value = true;
  preview.value = null;
  previewError.value = '';
  try {
    const response = await previewClassSchedule(props.classId, request);
    preview.value = response.data.data;
  } catch (error) { previewError.value = extractErrorMessage(error); }
  finally { previewing.value = false; }
}

async function saveChanges() {
  const request = payload();
  if (!request || props.classId == null || !preview.value) return;
  saving.value = true;
  try {
    await updateClassSchedule(props.classId, request);
    ElMessage.success(isCreate.value ? 'Schedule created.' : 'Schedule updated.');
    emit('update:visible', false);
    emit('saved');
  } catch (error) { previewError.value = extractErrorMessage(error); preview.value = null; }
  finally { saving.value = false; }
}
</script>

<style scoped>
.class-period-dialog__body { max-height: min(72vh, 820px); overflow: auto; padding-right: 4px; }
.class-period-dialog__intro { margin: 0 0 14px; color: #66758b; font-size: 13px; }
.class-period { border: 1px solid #d7e1ec; border-radius: 10px; margin-bottom: 10px; overflow: hidden; background: #fff; }
.class-period__heading { display: flex; align-items: center; gap: 4px; padding: 6px 12px; background: #f6f9fc; }
.class-period__toggle { display: flex; flex: 1; align-items: center; gap: 12px; text-align: left; border: 0; background: none; color: #233950; min-width: 0; cursor: pointer; }
.class-period__toggle span { color: #738397; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.class-period__content { padding: 12px; }
.class-period__dates, .class-period__fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.class-period__dates { grid-template-columns: repeat(2, minmax(0, 1fr)); max-width: 540px; }
.class-period__dates label, .class-period__fields label { display: grid; gap: 5px; min-width: 0; font-size: 12px; color: #66758b; }
.class-period__dates :deep(.el-date-editor) { width: 100%; }
.class-period__calendar { margin: 12px 0; }
.class-period__slot { border: 1px solid #e2e9f0; border-radius: 8px; padding: 12px; margin-top: 8px; }
.class-period__slot-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; font-size: 13px; }
.class-period__slot-head > span { color: #738397; }
.class-period__rotation { margin-left: auto; display: flex !important; align-items: center; gap: 7px !important; }
.class-period__fields--alternate { border-top: 1px solid #e6ebf0; padding-top: 10px; margin-top: 10px; }
.class-period-dialog__add { width: 100%; margin: 2px 0 12px; }
.class-period-preview { padding: 12px; border: 1px solid #cddff3; border-radius: 8px; background: #f7fbff; font-size: 13px; }
.class-period-preview p { margin: 7px 0; color: #657a90; }
.class-period-preview__lessons { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 5px 14px; max-height: 160px; overflow: auto; }
.class-period-preview__lessons span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
@media (max-width: 700px) { .class-period__fields { grid-template-columns: 1fr; } .class-period-preview__lessons { grid-template-columns: 1fr; } .class-period__toggle { display: grid; gap: 1px; } }
</style>
