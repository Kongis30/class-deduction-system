<template>
  <div class="my-lessons-page">
    <section class="my-lessons-page__header">
      <div>
        <h2>My Lessons</h2>
        <p>Review your lessons and submit each lesson result for admin deduction review.</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadLessons">Refresh</el-button>
    </section>

    <section v-if="!teacherId" class="my-lessons-empty">
      This account is not linked to a teacher profile.
    </section>

    <template v-else>
      <el-tabs v-model="lessonType" class="my-lessons-type-tabs">
        <el-tab-pane label="1-on-1" name="one-on-one" />
        <el-tab-pane label="Group class" name="group" />
      </el-tabs>

      <el-table
        v-loading="loading"
        :data="visibleLessons"
        border
        stripe
        size="small"
        max-height="520"
        class="my-lessons-table"
        :empty-text="emptyText"
      >
        <el-table-column label="Course / Class" min-width="180" align="center" header-align="center">
          <template #default="{ row }">
            {{ row.className || row.packageName || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="Student" min-width="160" align="center" header-align="center">
          <template #default="{ row }">
            {{ row.participantNames?.length ? row.participantNames.join(' / ') : row.studentName || 'Group lesson' }}
          </template>
        </el-table-column>
        <el-table-column label="Time" min-width="180" align="center" header-align="center">
          <template #default="{ row }">
            <div class="my-lessons-time">
              <strong>{{ formatDate(row.plannedStartAt) }}</strong>
              <span>{{ formatTimeRange(row.plannedStartAt, row.plannedEndAt) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Classroom" min-width="130" align="center" header-align="center">
          <template #default="{ row }">
            {{ row.actualClassroomName || row.plannedClassroomName || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="Status" min-width="165" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row)">
              {{ statusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Action" width="280" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="my-lessons-action">
              <el-button
                v-if="canAdjustTime(row)"
                size="small"
                @click="openAdjustDialog(row)"
              >
                Adjust
              </el-button>
              <el-button
                v-if="canSubmitAttendance(row)"
                type="primary"
                size="small"
                @click="openConfirmDialog(row)"
              >
                Confirm
              </el-button>
              <span v-if="!canSubmitAttendance(row)" class="my-lessons-no-action">
                {{ actionLabel(row) }}
              </span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog
      v-model="confirmDialogVisible"
      title="Confirm Lesson"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-alert type="info" :closable="false" show-icon class="my-lessons-confirm-alert">
        <template #title>Submit this lesson for review</template>
        <div class="my-lessons-alert-body">
          <p>Record what happened in the lesson and send it to the administrator.</p>
          <p>Hours are deducted only after the administrator confirms the result.</p>
        </div>
      </el-alert>
      <el-form label-width="130px">
        <el-form-item label="Result status">
          <el-select v-model="confirmForm.resultStatus" style="width: 100%">
            <el-option label="Completed" value="COMPLETED" />
            <el-option label="Formal Leave" value="FORMAL_LEAVE" />
            <el-option label="Last minute cancel or no show" value="NO_SHOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="Actual start">
          <el-date-picker
            v-model="confirmForm.actualStartAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="Actual duration">
          <el-input-number
            v-model="confirmForm.actualHours"
            :min="0"
            :max="10"
            :precision="1"
            :step="0.5"
          />
          <span class="my-lessons-form-hint">Use 0.5-hour increments.</span>
        </el-form-item>
        <el-form-item label="Notes">
          <el-input v-model="confirmForm.notes" type="textarea" :rows="3" placeholder="Optional notes" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAttendance">Confirm</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="adjustDialogVisible"
      title="Adjust Lesson Time"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-alert type="info" :closable="false" show-icon class="my-lessons-confirm-alert">
        <template #title>Adjust an unconfirmed lesson</template>
        <div class="my-lessons-alert-body">
          <p>You can adjust the time even after the original lesson time has passed.</p>
          <p>The lesson duration stays the same. Save the time that should be recorded for this lesson.</p>
        </div>
      </el-alert>
      <el-form label-width="130px">
        <el-form-item label="New start time">
          <el-date-picker
            v-model="adjustForm.plannedStartAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="New end time">
          <span>{{ adjustedEndAt ? dayjs(adjustedEndAt).format('YYYY-MM-DD HH:mm') : '—' }}</span>
        </el-form-item>
        <el-form-item label="Reason">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="2" placeholder="Optional" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitTimeAdjustment">Save time</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import dayjs from 'dayjs';

import { extractErrorMessage } from '@/api/http';
import { confirmLesson } from '@/api/lesson';
import { confirmClassLesson, createScheduleAdjustment, fetchLessonOccurrences } from '@/api/schedule';
import { useAuthStore } from '@/stores/auth';
import type { LessonOccurrenceItem } from '@/types/domain';

type LessonType = 'one-on-one' | 'group';

const authStore = useAuthStore();
const teacherId = computed(() => authStore.currentUser?.teacherId);
const lessonType = ref<LessonType>('one-on-one');
const loading = ref(false);
const submitting = ref(false);
const adjusting = ref(false);
const lessons = ref<LessonOccurrenceItem[]>([]);
const selectedLesson = ref<LessonOccurrenceItem | null>(null);
const confirmDialogVisible = ref(false);
const adjustDialogVisible = ref(false);
const adjustForm = reactive({ plannedStartAt: '', reason: '' });

const confirmForm = reactive({
  resultStatus: 'COMPLETED',
  actualStartAt: '',
  actualHours: 0,
  notes: '',
});

const visibleLessons = computed(() => lessons.value.filter(lesson =>
  lessonType.value === 'group' ? Boolean(lesson.classGroupId) : !lesson.classGroupId
));

const emptyText = computed(() =>
  `No ${lessonType.value === 'group' ? 'group class' : '1-on-1'} lessons found in this date range.`
);

const adjustedEndAt = computed(() => {
  if (!selectedLesson.value || !adjustForm.plannedStartAt) return '';
  const durationMinutes = dayjs(selectedLesson.value.plannedEndAt).diff(dayjs(selectedLesson.value.plannedStartAt), 'minute');
  return dayjs(adjustForm.plannedStartAt).add(durationMinutes, 'minute').format('YYYY-MM-DDTHH:mm:ss');
});

onMounted(loadLessons);

async function loadLessons() {
  if (!teacherId.value) {
    lessons.value = [];
    return;
  }

  // 合并展示最近 30 天和未来 30 天课次，避免“今天”的记录在两个筛选页重复出现。
  const now = dayjs();

  loading.value = true;
  try {
    const response = await fetchLessonOccurrences({
      startAt: now.subtract(30, 'day').startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
      endAt: now.add(30, 'day').endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
      pageSize: 200,
    });
    lessons.value = [...response.data.data.records]
      .sort((left, right) => left.plannedStartAt.localeCompare(right.plannedStartAt));
  } catch (error) {
    lessons.value = [];
    ElMessage.error(extractErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

function openConfirmDialog(lesson: LessonOccurrenceItem) {
  selectedLesson.value = lesson;
  Object.assign(confirmForm, {
    resultStatus: lesson.resultStatus || 'COMPLETED',
    actualStartAt: lesson.actualStartAt || lesson.plannedStartAt,
    actualHours: lesson.actualUnits != null ? lesson.actualUnits * 0.5 : lesson.plannedUnits * 0.5,
    notes: lesson.notes || '',
  });
  confirmDialogVisible.value = true;
}

function canAdjustTime(lesson: LessonOccurrenceItem) {
  return Boolean(teacherId.value)
    && String(lesson.plannedTeacherId) === String(teacherId.value)
    && lesson.planningStatus !== 'CANCELLED'
    && lesson.confirmStage !== 'PENDING_ADMIN'
    && lesson.confirmStage !== 'CONFIRMED'
    && !lesson.resultStatus;
}

function openAdjustDialog(lesson: LessonOccurrenceItem) {
  selectedLesson.value = lesson;
  Object.assign(adjustForm, { plannedStartAt: lesson.plannedStartAt, reason: '' });
  adjustDialogVisible.value = true;
}

async function submitTimeAdjustment() {
  const lesson = selectedLesson.value;
  if (!lesson || !adjustForm.plannedStartAt || !adjustedEndAt.value) {
    ElMessage.error('Choose a new lesson start time.');
    return;
  }
  adjusting.value = true;
  try {
    await createScheduleAdjustment({
      occurrenceId: lesson.id,
      adjustmentType: 'RESCHEDULE',
      plannedStartAt: adjustForm.plannedStartAt,
      plannedEndAt: adjustedEndAt.value,
      plannedUnits: lesson.plannedUnits,
      reason: adjustForm.reason.trim() || 'Adjusted by teacher from My Lessons',
    });
    ElMessage.success('Lesson time adjusted.');
    adjustDialogVisible.value = false;
    await loadLessons();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    adjusting.value = false;
  }
}

async function submitAttendance() {
  const lesson = selectedLesson.value;
  if (!lesson) return;

  const isNonAttendance = confirmForm.resultStatus === 'FORMAL_LEAVE' || confirmForm.resultStatus === 'NO_SHOW';
  if (!isNonAttendance && !confirmForm.actualStartAt) {
    ElMessage.error('Actual start time is required for a completed lesson.');
    return;
  }
  if (!isNonAttendance && (confirmForm.actualHours <= 0 || !Number.isInteger(confirmForm.actualHours * 2))) {
    ElMessage.error('Actual duration must be greater than zero and use 0.5-hour increments.');
    return;
  }

  const actualEndAt = !isNonAttendance && confirmForm.actualStartAt
    ? dayjs(confirmForm.actualStartAt).add(confirmForm.actualHours, 'hour').format('YYYY-MM-DDTHH:mm:ss')
    : undefined;
  const payload = {
    resultStatus: confirmForm.resultStatus,
    actualTeacherId: isNonAttendance ? undefined : lesson.actualTeacherId || lesson.plannedTeacherId,
    actualClassroomId: isNonAttendance ? undefined : lesson.actualClassroomId || lesson.plannedClassroomId,
    actualStartAt: isNonAttendance ? undefined : confirmForm.actualStartAt,
    actualEndAt,
    actualUnits: isNonAttendance ? 0 : Math.round(confirmForm.actualHours * 2),
    notes: confirmForm.notes || undefined,
  };

  submitting.value = true;
  try {
    if (lesson.classGroupId) {
      await confirmClassLesson(lesson.classGroupId, lesson.id, payload);
    } else {
      await confirmLesson({ occurrenceId: lesson.id, ...payload });
    }
    ElMessage.success('Lesson result submitted for admin review.');
    confirmDialogVisible.value = false;
    await loadLessons();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    submitting.value = false;
  }
}

function canSubmitAttendance(lesson: LessonOccurrenceItem) {
  const now = dayjs();
  return lesson.planningStatus !== 'CANCELLED'
    && lesson.confirmStage !== 'PENDING_ADMIN'
    && lesson.confirmStage !== 'CONFIRMED'
    && !(lesson.resultStatus && lesson.confirmStage !== 'REJECTED')
    && !dayjs(lesson.plannedStartAt).isAfter(now.add(14, 'day'));
}

function actionLabel(lesson: LessonOccurrenceItem) {
  if (lesson.confirmStage === 'PENDING_ADMIN') return 'Awaiting admin review';
  if (lesson.confirmStage === 'CONFIRMED') return 'Confirmed';
  if (lesson.planningStatus === 'CANCELLED') return 'Cancelled';
  if (dayjs(lesson.plannedStartAt).isAfter(dayjs().add(14, 'day'))) {
    return `Confirmation available from ${dayjs(lesson.plannedStartAt).subtract(14, 'day').format('MMM D, HH:mm')}`;
  }
  return 'No action';
}

function statusLabel(lesson: LessonOccurrenceItem) {
  if (lesson.confirmStage === 'PENDING_ADMIN') return 'Awaiting admin review';
  if (lesson.confirmStage === 'REJECTED') {
    return lesson.rejectReason ? `Rejected: ${lesson.rejectReason}` : 'Rejected - resubmit';
  }
  if (lesson.confirmStage === 'CONFIRMED') return lesson.resultStatusName || resultStatusLabel(lesson.resultStatus);
  if (lesson.resultStatus) return lesson.resultStatusName || resultStatusLabel(lesson.resultStatus);
  if (lesson.planningStatus === 'CANCELLED') return 'Cancelled';
  return 'Scheduled';
}

function statusTag(lesson: LessonOccurrenceItem) {
  if (lesson.confirmStage === 'PENDING_ADMIN') return 'warning';
  if (lesson.confirmStage === 'REJECTED' || lesson.planningStatus === 'CANCELLED') return 'danger';
  if (lesson.confirmStage === 'CONFIRMED' || lesson.resultStatus === 'COMPLETED') return 'success';
  return 'info';
}

function resultStatusLabel(status?: string) {
  return {
    COMPLETED: 'Completed',
    FORMAL_LEAVE: 'Formal Leave',
    NO_SHOW: 'Last minute cancel or no show',
    CANCELLED: 'Last minute cancel or no show',
  }[status || ''] || status || 'Confirmed';
}

function formatDate(value: string) {
  return dayjs(value).format('MMM D, YYYY');
}

function formatTimeRange(startAt: string, endAt: string) {
  return `${dayjs(startAt).format('HH:mm')} - ${dayjs(endAt).format('HH:mm')}`;
}
</script>

<style scoped>
.my-lessons-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.my-lessons-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.my-lessons-page__header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 22px;
}

.my-lessons-page__header p,
.my-lessons-form-hint,
.my-lessons-no-action {
  color: var(--text-secondary);
  font-size: 13px;
}

.my-lessons-page__header p {
  margin: 4px 0 0;
}

.my-lessons-type-tabs :deep(.el-tabs__header) {
  margin: 0;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface);
  padding: 0 14px;
}

.my-lessons-type-tabs :deep(.el-tabs__content) {
  display: none;
}

.my-lessons-empty {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text-secondary);
  padding: 28px;
  text-align: center;
}

.my-lessons-time {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.my-lessons-time span {
  color: var(--text-secondary);
}

.my-lessons-confirm-alert {
  margin-bottom: 16px;
}

.my-lessons-alert-body {
  display: grid;
  gap: 4px;
  margin-top: 4px;
  line-height: 1.45;
}

.my-lessons-alert-body p {
  margin: 0;
}

.my-lessons-form-hint {
  margin-left: 10px;
}

.my-lessons-action {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
}

.my-lessons-no-action {
  line-height: 1.35;
  max-width: 190px;
}

@media (max-width: 720px) {
  .my-lessons-page__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
