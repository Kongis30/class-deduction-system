<template>
  <div class="adj-page">
    <!-- Tab 切换 -->
    <div class="adj-tabs">
      <button
        class="adj-tab"
        :class="{ 'adj-tab--active': activeTab === 'one-on-one' }"
        @click="activeTab = 'one-on-one'"
      >1v1 Lesson Write-off</button>
      <button
        class="adj-tab"
        :class="{ 'adj-tab--active': activeTab === 'class' }"
        @click="activeTab = 'class'"
      >Class Management</button>
    </div>

    <!-- ====== 1v1 Tab ====== -->
    <div v-if="activeTab === 'one-on-one'" class="adj-workspace">
      <!-- 左侧学生列表 -->
      <aside class="adj-sidebar">
        <div class="adj-sidebar__search">
          <el-input v-model="studentKeyword" placeholder="Search student..." clearable size="small" />
        </div>
        <div class="adj-sidebar__list" v-loading="studentsLoading">
          <div
            v-for="s in filteredStudents"
            :key="s.id"
            class="adj-sidebar__item"
            :class="{ 'adj-sidebar__item--active': selectedStudentId === s.id }"
            @click="selectStudent(s.id)"
          >
            <div class="adj-sidebar__name">{{ s.studentName }}</div>
            <div v-if="s.id && studentLastDates[s.id]" class="adj-sidebar__meta">
              Last: {{ studentLastDates[s.id] }}
            </div>
          </div>
          <div v-if="!studentsLoading && filteredStudents.length === 0" class="adj-sidebar__empty">
            No students found.
          </div>
        </div>
      </aside>

      <!-- 右侧内容 -->
      <main class="adj-content">
        <div v-if="!selectedStudentId" class="adj-content__placeholder">
          Select a student from the left panel to view their packages.
        </div>

        <div v-else-if="packagesLoading" class="adj-content__loading" v-loading="packagesLoading" />

        <div v-else-if="packages.length === 0" class="adj-content__placeholder">
          No 1v1 course packages for this student.
        </div>

        <!-- 课包列表 -->
        <div v-else class="adj-packages">
          <div
            v-for="pkg in packages"
            :key="pkg.id"
            class="pkg-card"
            :class="{ 'pkg-card--expanded': selectedPackageId === String(pkg.id) }"
          >
            <!-- 课包头部 — 点击展开/收起 -->
            <div class="pkg-card__header" @click="selectPackage(pkg.id)">
              <div class="pkg-card__info">
                <span class="pkg-card__name">{{ pkg.packageName }}</span>
                <span class="pkg-card__badge">{{ pkg.remainingUnits }}h left</span>
                <span v-if="occurrenceCounts[String(pkg.id)]" class="pkg-card__count">{{ occurrenceCounts[String(pkg.id)] }} lessons</span>
              </div>
              <div class="pkg-card__meta">
                <span v-if="packageLastDates[String(pkg.id)]" class="pkg-card__last">
                  Last: {{ packageLastDates[String(pkg.id)] }}
                </span>
                <el-icon class="pkg-card__arrow" :class="{ 'pkg-card__arrow--open': selectedPackageId === String(pkg.id) }">
                  <ArrowDown />
                </el-icon>
              </div>
            </div>

            <!-- 课包排课记录 -->
            <div v-if="selectedPackageId === String(pkg.id)" class="pkg-card__body">
              <div class="pkg-card__toolbar">
                <div>
                  <div class="pkg-card__toolbar-title">This week lessons</div>
                  <div class="pkg-card__toolbar-hint">Temporary changes here only affect the current natural week.</div>
                </div>
              </div>
              <div v-loading="occurrencesLoading" class="pkg-occ-list">
                <div v-for="occ in occurrences" :key="occ.id" class="pkg-occ-row" :class="dayMarkerClass(occ.plannedStartAt)">
                  <!-- 日期标记 -->
                  <div class="pkg-occ__marker">
                    <el-tag v-if="isToday(occ.plannedStartAt)" size="small" type="success" effect="dark">today</el-tag>
                    <el-tag v-else-if="isPast(occ.plannedStartAt)" size="small" type="info">prev</el-tag>
                    <el-tag v-else size="small">next</el-tag>
                  </div>
                  <!-- 日期时间 -->
                  <div class="pkg-occ__time">
                    <div class="pkg-occ__date">{{ formatDate(occ.plannedStartAt) }}</div>
                    <div class="pkg-occ__range">{{ formatTimeRange(occ.plannedStartAt, occ.plannedEndAt) }}</div>
                  </div>
                  <!-- 教师 & 教室 -->
                  <div class="pkg-occ__people">
                    <div>{{ occ.plannedTeacherName || '—' }}</div>
                    <div class="pkg-occ__room">{{ occ.plannedClassroomName || '—' }}</div>
                  </div>
                  <!-- 时长 -->
                  <div class="pkg-occ__hours">{{ (occ.plannedUnits * 0.5).toFixed(1) }}h</div>
                  <!-- 状态 -->
                  <div class="pkg-occ__status">
                    <el-tag v-if="isPendingAdmin(occ)" size="small" type="warning">
                      Pending Admin Review
                    </el-tag>
                    <el-tooltip
                      v-else-if="isRejected(occ)"
                      :content="occ.rejectReason ? `Reason: ${occ.rejectReason}` : 'Admin rejected this confirmation. Teacher can submit again.'"
                      placement="top"
                    >
                      <el-tag size="small" type="danger">Rejected - Teacher Action Required</el-tag>
                    </el-tooltip>
                    <el-tag v-else-if="occ.resultStatus" size="small" :type="resultStatusTag(occ.resultStatus)">
                      {{ resultStatusLabel(occ.resultStatus) }}
                    </el-tag>
                    <el-tag v-else-if="occ.planningStatus === 'CANCELLED'" size="small" type="danger">Cancelled</el-tag>
                    <el-tag v-else size="small" type="warning">Pending</el-tag>
                  </div>
                  <!-- 操作 -->
                  <div class="pkg-occ__actions">
                    <el-tooltip
                      v-if="canAdminReviewOccurrence(occ)"
                      content="Teacher confirmed. Pending admin review."
                      placement="top"
                    >
                      <el-button
                        type="warning" size="small"
                        @click="openAdminConfirmDialog(occ)"
                      >Admin Confirm</el-button>
                    </el-tooltip>
                    <el-button
                      v-if="canAdminRejectOccurrence(occ)"
                      type="danger" size="small" plain
                      @click="openRejectDialog(occ)"
                    >Reject</el-button>
                    <el-button
                      v-if="isTeacherRole && isPendingAdmin(occ)"
                      type="warning" size="small" plain disabled
                    >Pending Admin Review</el-button>
                    <el-button
                      v-if="canOpenNormalConfirm(occ)"
                      type="success" size="small" plain
                      @click="openConfirmDialog(occ)"
                    >{{ isRejected(occ) ? 'Confirm Again' : (isTeacherRole ? 'Teacher Confirm' : 'Confirm') }}</el-button>
                    <el-button
                      v-if="occ.resultStatus && !isRejected(occ)"
                      size="small" plain
                      @click="openDetailDialog(occ)"
                    >Detail</el-button>
                    <el-button
                      v-if="occ.resultStatus === 'CANCELLED'"
                      size="small" plain
                      @click="openMakeupDialog(occ)"
                    >Makeup</el-button>
                  </div>
                </div>

                <div v-if="!occurrencesLoading && occurrences.length === 0" class="pkg-occ-empty">
                  No lesson records in this package.
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- ====== Class Tab ====== -->
    <div v-if="activeTab === 'class'" class="adj-workspace">
      <!-- 左侧班课列表 -->
      <aside class="adj-sidebar">
        <div class="adj-sidebar__search">
          <el-input v-model="classKeyword" placeholder="Search class..." clearable size="small" />
        </div>
        <div class="adj-sidebar__list" v-loading="classesLoading">
          <div
            v-for="clz in filteredClasses"
            :key="clz.id"
            class="adj-sidebar__item"
            :class="{ 'adj-sidebar__item--active': selectedClassId === String(clz.id) }"
            @click="selectClass(clz.id)"
          >
            <div class="adj-sidebar__name">{{ clz.className }}</div>
            <div class="adj-sidebar__meta">{{ clz.activeStudentCount }} students</div>
          </div>
          <div v-if="!classesLoading && filteredClasses.length === 0" class="adj-sidebar__empty">
            No classes found.
          </div>
        </div>
      </aside>

      <!-- 右侧内容 -->
      <main class="adj-content">
        <div v-if="!selectedClassId" class="adj-content__placeholder">
          Select a class from the left panel to manage.
        </div>

        <div v-else class="class-detail">
          <!-- 班课基本信息 -->
          <div class="class-detail__header">
            <div class="class-detail__title-row">
              <div class="class-detail__title">{{ selectedClass?.className }}</div>
            </div>
            <div class="class-detail__info-grid">
              <div class="class-detail__info-item" v-if="selectedClass?.teacherName">
                <span class="class-detail__info-label">Teacher</span>
                <span class="class-detail__info-value">{{ selectedClass.teacherName }}</span>
              </div>
              <div class="class-detail__info-item" v-if="selectedClass?.classroomName">
                <span class="class-detail__info-label">Classroom</span>
                <span class="class-detail__info-value">{{ selectedClass.classroomName }}</span>
              </div>
              <div class="class-detail__info-item" v-if="selectedClass?.scheduleDesc">
                <span class="class-detail__info-label">Schedule</span>
                <span class="class-detail__info-value">{{ selectedClass.scheduleDesc }}</span>
              </div>
            </div>
            <div v-if="selectedClass?.description" class="class-detail__desc">{{ selectedClass.description }}</div>
          </div>

          <!-- 在册学生 + 到期管理 -->
          <div class="class-section">
            <div class="class-section__header">
              <h3>Enrolled Students</h3>
              <el-button size="small" plain :disabled="!hasExpiringStudents" :loading="reminding" @click="sendReminders">
                Send Expiry Reminders
              </el-button>
            </div>
            <el-table :data="enrolledStudents" v-loading="classStudentsLoading" size="small" max-height="220" stripe>
              <el-table-column label="Student" prop="studentName" min-width="120" />
              <el-table-column label="Enrolled" prop="enrollmentDate" min-width="100" sortable>
                <template #default="{ row }">{{ formatDateShort(row.enrollmentDate) }}</template>
              </el-table-column>
              <el-table-column label="Expiry" prop="expiryDate" min-width="100" sortable>
                <template #default="{ row }">{{ formatDateShort(row.expiryDate) }}</template>
              </el-table-column>
              <el-table-column label="Expiry Status" width="130"
                :filters="expiryStatusFilters" :filter-method="filterExpiryStatus">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.expiryStatus === 'EXPIRED' ? 'danger' : row.expiryStatus === 'EXPIRING_SOON' ? 'warning' : 'success'"
                  >
                    {{ row.expiryStatus === 'EXPIRED' ? 'Expired' : row.expiryStatus === 'EXPIRING_SOON' ? 'Expiring Soon' : 'Normal' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 排课记录 + 统一核销 -->
          <div class="class-section">
            <div class="class-section__header">
              <h3>Lesson Occurrences</h3>
            </div>
            <div v-if="classOccurrences.length === 0 && !occurrencesLoading" class="class-empty">
              No occurrences for this class.
            </div>
            <div v-loading="occurrencesLoading" class="class-occ-list">
              <div v-for="occ in classOccurrences" :key="occ.id" class="pkg-occ-row" :class="dayMarkerClass(occ.plannedStartAt)">
                <div class="pkg-occ__marker">
                  <el-tag v-if="isToday(occ.plannedStartAt)" size="small" type="success" effect="dark">today</el-tag>
                  <el-tag v-else-if="isPast(occ.plannedStartAt)" size="small" type="info">prev</el-tag>
                  <el-tag v-else size="small">next</el-tag>
                </div>
                <div class="pkg-occ__time">
                  <div class="pkg-occ__date">{{ formatDate(occ.plannedStartAt) }}</div>
                  <div class="pkg-occ__range">{{ formatTimeRange(occ.plannedStartAt, occ.plannedEndAt) }}</div>
                </div>
                <div class="pkg-occ__people">
                  <div>{{ occ.plannedTeacherName || '—' }}</div>
                  <div class="pkg-occ__room">{{ occ.plannedClassroomName || '—' }}</div>
                </div>
                <div class="pkg-occ__hours">{{ (occ.plannedUnits * 0.5).toFixed(1) }}h</div>
                <div class="pkg-occ__status">
                  <el-tag v-if="isPendingAdmin(occ)" size="small" type="warning">
                    Pending Admin Review
                  </el-tag>
                  <el-tooltip
                    v-else-if="isRejected(occ)"
                    :content="occ.rejectReason ? `Reason: ${occ.rejectReason}` : 'Admin rejected this confirmation. Teacher can submit again.'"
                    placement="top"
                  >
                    <el-tag size="small" type="danger">Rejected - Teacher Action Required</el-tag>
                  </el-tooltip>
                  <el-tag v-else-if="occ.resultStatus" size="small" :type="resultStatusTag(occ.resultStatus)">
                    {{ resultStatusLabel(occ.resultStatus) }}
                  </el-tag>
                  <el-tag v-else-if="occ.planningStatus === 'CANCELLED'" size="small" type="danger">Cancelled</el-tag>
                  <el-tag v-else size="small" type="warning">Pending</el-tag>
                </div>
                <div class="pkg-occ__actions">
                  <el-tooltip
                    v-if="canAdminReviewOccurrence(occ)"
                    content="Teacher confirmed. Pending admin review."
                    placement="top"
                  >
                    <el-button
                      type="warning" size="small"
                      @click="openAdminConfirmDialog(occ)"
                    >Admin Confirm</el-button>
                  </el-tooltip>
                  <el-button
                    v-if="canAdminRejectOccurrence(occ)"
                    type="danger" size="small" plain
                    @click="openRejectDialog(occ)"
                  >Reject</el-button>
                  <el-button
                    v-if="isTeacherRole && isPendingAdmin(occ)"
                    type="warning" size="small" plain disabled
                  >Pending Admin Review</el-button>
                  <el-button
                    v-if="canOpenNormalConfirm(occ)"
                    type="success" size="small" plain
                    @click="openClassConfirmDialog(occ)"
                  >{{ isRejected(occ) ? 'Confirm Again' : (isTeacherRole ? 'Teacher Confirm' : 'Confirm') }}</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- ====== Confirm Dialog ====== -->
    <el-dialog
      v-model="confirmDialogVisible"
      :title="`Confirm Lesson — ${selectedOcc?.lessonNo}`"
      width="620px"
      :close-on-click-modal="false"
      class="adj-dialog"
      @close="classConfirmMode = false"
    >
      <el-form ref="confirmFormRef" :model="confirmForm" :rules="confirmFormRules" label-width="160px">
        <el-form-item label="Result Status" prop="resultStatus">
          <el-select v-model="confirmForm.resultStatus" placeholder="Select status" style="width: 100%;">
            <el-option label="Completed" value="COMPLETED" />
            <el-option label="Formal Leave" value="FORMAL_LEAVE" />
            <el-option label="No Show" value="NO_SHOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="Actual Teacher" prop="actualTeacherId">
          <el-select v-model="confirmForm.actualTeacherId" placeholder="Select teacher" filterable style="width: 100%;">
            <el-option v-for="t in teachers" :key="t.id" :label="t.teacherName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Actual Classroom">
          <el-select v-model="confirmForm.actualClassroomId" placeholder="Defaults to planned classroom" clearable filterable style="width: 100%;">
            <el-option v-for="c in classrooms" :key="c.id" :label="c.roomName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Actual Start Time">
          <el-date-picker
            v-model="confirmForm.actualStartAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="Defaults to planned start"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="Actual End Time">
          <div class="end-time-display">{{ computedEndTime || '—' }}</div>
        </el-form-item>
        <el-form-item label="Hours">
          <div class="units-input-row">
            <el-input-number
              v-model="confirmForm.actualHours"
              :min="0" :max="10" :precision="1" :step="0.5"
              :class="{ 'hours-input--changed': hoursChangedByTeacher }"
              style="width: 120px;"
            />
          </div>
          <div v-if="hoursChangedByTeacher" class="form-hint form-hint--warning">
            Teacher changed hours from {{ plannedHoursForReview?.toFixed(1) }}h to {{ (confirmForm.actualHours ?? 0).toFixed(1) }}h
          </div>
          <div v-else class="form-hint">0.5h increments</div>
        </el-form-item>
        <el-form-item v-if="confirmForm.resultStatus !== 'FORMAL_LEAVE'" label="Deduction Preview">
          <el-alert :title="deductionPreview" type="info" :closable="false" show-icon />
        </el-form-item>
        <el-form-item label="Notes">
          <el-input v-model="confirmForm.notes" type="textarea" :rows="2" placeholder="Optional notes" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConfirm">Confirm</el-button>
      </template>
    </el-dialog>

    <!-- ====== Reject Dialog ====== -->
    <el-dialog
      v-model="rejectDialogVisible"
      :title="`Reject Lesson — ${selectedOcc?.lessonNo}`"
      width="480px"
      :close-on-click-modal="false"
      class="adj-dialog"
    >
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectFormRules" label-width="110px">
        <el-form-item label="Reason" prop="rejectReason">
          <el-input
            v-model="rejectForm.rejectReason"
            type="textarea"
            :rows="3"
            placeholder="Enter reject reason"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">Cancel</el-button>
        <el-button type="danger" :loading="submitting" @click="submitReject">Reject</el-button>
      </template>
    </el-dialog>

    <!-- ====== Detail Dialog ====== -->
    <el-dialog
      v-model="detailDialogVisible"
      title="Lesson Detail"
      width="540px"
      :close-on-click-modal="false"
      class="adj-dialog"
    >
      <div v-if="detailData" class="lesson-detail" v-loading="detailLoading">
        <div class="detail-section">
          <h4 class="detail-section__title">Lesson info</h4>
          <div class="detail-grid">
            <div class="detail-field"><label>Lesson No.</label><span>{{ detailData.lessonNo }}</span></div>
            <div class="detail-field">
              <label>Type</label>
              <el-tag size="small" :type="detailData.occurrenceType === 'MAKEUP' ? 'warning' : ''">
                {{ detailData.occurrenceType === 'MAKEUP' ? 'Makeup' : detailData.occurrenceType === 'MANUAL' ? 'Manual' : 'Regular' }}
              </el-tag>
            </div>
            <div class="detail-field"><label>Student</label><span>{{ detailData.studentName }}</span></div>
            <div class="detail-field"><label>Package</label><span>{{ detailData.packageName || '—' }}</span></div>
            <div class="detail-field"><label>Teacher</label><span>{{ detailData.plannedTeacherName ?? '—' }}</span></div>
            <div class="detail-field"><label>Classroom</label><span>{{ detailData.plannedClassroomName ?? '—' }}</span></div>
            <div class="detail-field"><label>Start time</label><span>{{ detailData.plannedStartAt }}</span></div>
            <div class="detail-field"><label>End time</label><span>{{ detailData.plannedEndAt }}</span></div>
            <div class="detail-field"><label>Hours</label><span>{{ (detailData.plannedUnits * 0.5).toFixed(1) }}</span></div>
            <div class="detail-field">
              <label>Status</label>
              <el-tag size="small" :type="detailData.planningStatus === 'SCHEDULED' ? '' : 'warning'">
                {{ detailData.planningStatus === 'SCHEDULED' ? 'Scheduled' : detailData.planningStatus === 'ADJUSTED' ? 'Adjusted' : detailData.planningStatus }}
              </el-tag>
            </div>
          </div>
        </div>
        <div v-if="detailData.notes" class="detail-section">
          <h4 class="detail-section__title">Notes</h4>
          <p class="detail-notes">{{ detailData.notes }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">Close</el-button>
      </template>
    </el-dialog>

    <!-- ====== Makeup Dialog ====== -->
    <el-dialog
      v-model="makeupDialogVisible"
      :title="`Create Makeup — ${selectedOcc?.lessonNo}`"
      width="520px"
      :close-on-click-modal="false"
      class="adj-dialog"
    >
      <p class="makeup-hint">Create a makeup lesson to replace the cancelled lesson.</p>
      <el-form ref="makeupFormRef" :model="makeupForm" :rules="makeupFormRules" label-width="140px">
        <el-form-item label="New Teacher">
          <el-select v-model="makeupForm.plannedTeacherId" placeholder="Select teacher (optional)" clearable filterable style="width: 100%;">
            <el-option v-for="t in teachers" :key="t.id" :label="t.teacherName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="New Classroom">
          <el-select v-model="makeupForm.plannedClassroomId" placeholder="Select classroom (optional)" clearable filterable style="width: 100%;">
            <el-option v-for="c in classrooms" :key="c.id" :label="c.roomName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Start Time" prop="plannedStartAt">
          <el-date-picker v-model="makeupForm.plannedStartAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="Select start time" style="width: 100%;" :disabled-date="disabledPastDatetime" />
        </el-form-item>
        <el-form-item label="End Time" prop="plannedEndAt">
          <el-date-picker v-model="makeupForm.plannedEndAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="Select end time" style="width: 100%;" :disabled-date="disabledPastDatetime" />
        </el-form-item>
        <el-form-item label="Reason">
          <el-input v-model="makeupForm.reason" type="textarea" :rows="2" placeholder="Optional reason" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="makeupDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMakeup">Create Makeup</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import dayjs from 'dayjs';

import { fetchAllClassrooms, fetchAllStudents, fetchAllTeachers } from '@/api/base-data';
import { createScheduleAdjustment, fetchLessonOccurrences, fetchClassGroups, fetchClassStudents, fetchClassOccurrences, confirmClassLesson, rejectClassLesson, sendClassExpiryReminders, deleteClassGroup } from '@/api/schedule';
import { fetchCoursePackages } from '@/api/enrollment';
import { adminConfirmLessonRecord, confirmLesson, fetchLessonRecords, rejectLessonRecord, type LessonConfirmPayload } from '@/api/lesson';
import { useAuthStore } from '@/stores/auth';
import { isTeacherUser } from '@/types/auth';
import type { CoursePackageItem, EntityId, LessonOccurrenceItem, ClassGroupItem, ClassEnrolledStudentItem, ClassOccurrenceItem, LessonRecordItem } from '@/types/domain';
import { ElMessageBox } from 'element-plus';

// ─── State ──────────────────────────────────────────────
const activeTab = ref<'one-on-one' | 'class'>('one-on-one');

// 学生列表
const studentsLoading = ref(false);
const students = ref<{ id: EntityId; studentName: string }[]>([]);
const studentKeyword = ref('');
const selectedStudentId = ref<EntityId | null>(null);

// 课包
const packagesLoading = ref(false);
const packages = ref<CoursePackageItem[]>([]);
const selectedPackageId = ref<EntityId | null>(null);

// 排课记录
const occurrencesLoading = ref(false);
const occurrences = ref<LessonOccurrenceItem[]>([]);

// 缓存：学生/课包的最后课程日期
const studentLastDates = reactive<Record<string, string>>({});
const packageLastDates = reactive<Record<string, string>>({});
// 缓存：课包的排课记录数
const occurrenceCounts = reactive<Record<string, number>>({});

// 班课管理
const classKeyword = ref('');
const classesLoading = ref(false);
const classes = ref<ClassGroupItem[]>([]);
const selectedClassId = ref<EntityId | null>(null);
const selectedClass = computed(() => classes.value.find(c => String(c.id) === selectedClassId.value) ?? null);
const enrolledStudents = ref<ClassEnrolledStudentItem[]>([]);
const classOccurrences = ref<ClassOccurrenceItem[]>([]);
const classStudentsLoading = ref(false);
const reminding = ref(false);
const deleting = ref(false);

// 教师/教室（弹窗用）
const teachers = ref<{ id: EntityId; teacherName: string }[]>([]);
const classrooms = ref<{ id: EntityId; roomName: string }[]>([]);

// 弹窗
const authStore = useAuthStore();
const submitting = ref(false);
const selectedOcc = ref<ConfirmableOccurrence | null>(null);
const confirmSubmitMode = ref<'normal' | 'admin-final'>('normal');
const confirmDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const detailData = ref<LessonOccurrenceItem | null>(null);
const makeupDialogVisible = ref(false);
const rejectDialogVisible = ref(false);
const confirmFormRef = ref<FormInstance>();
const rejectFormRef = ref<FormInstance>();
const makeupFormRef = ref<FormInstance>();

const isTeacherRole = computed(() => isTeacherUser(authStore.currentUser));
const isAdminRole = computed(() => Boolean(authStore.currentUser?.userType === 'ADMIN' || (authStore.currentUser?.roleCodes ?? []).includes('ADMIN')));
const isNonTeacherRole = computed(() => !isTeacherRole.value);

// ─── 学生过滤 ─────────────────────────
const filteredStudents = computed(() => {
  if (!studentKeyword.value) return students.value;
  const kw = studentKeyword.value.toLowerCase();
  return students.value.filter(s => s.studentName.toLowerCase().includes(kw));
});

// ─── 班课过滤 ─────────────────────────
const filteredClasses = computed(() => {
  if (!classKeyword.value) return classes.value;
  const kw = classKeyword.value.toLowerCase();
  return classes.value.filter(c => c.className.toLowerCase().includes(kw));
});

type ConfirmableOccurrence = LessonOccurrenceItem | ClassOccurrenceItem;

function isPendingAdmin(occ: ConfirmableOccurrence): boolean {
  return occ.confirmStage === 'PENDING_ADMIN';
}

function isRejected(occ: ConfirmableOccurrence): boolean {
  return occ.confirmStage === 'REJECTED';
}

function getLessonRecordId(occ: ConfirmableOccurrence): EntityId | undefined {
  return occ.lessonRecordId ?? occ.recordId;
}

function canAdminReviewOccurrence(occ: ConfirmableOccurrence): boolean {
  return isAdminRole.value && isPendingAdmin(occ) && Boolean(getLessonRecordId(occ));
}

function canAdminRejectOccurrence(occ: ConfirmableOccurrence): boolean {
  return canAdminReviewOccurrence(occ);
}

function canOpenNormalConfirm(occ: ConfirmableOccurrence): boolean {
  if (occ.planningStatus === 'CANCELLED' || isPendingAdmin(occ)) return false;
  if (isRejected(occ)) return isTeacherRole.value;
  if ('confirmed' in occ && occ.confirmed) return false;
  return !occ.resultStatus && (isTeacherRole.value || isNonTeacherRole.value);
}

function mergeRecordIntoOccurrence<T extends ConfirmableOccurrence>(occ: T, record?: LessonRecordItem): T {
  if (!record) return occ;
  return {
    ...occ,
    lessonRecordId: record.id,
    recordId: record.id,
    resultStatus: record.resultStatus,
    confirmStage: record.confirmStage,
    confirmedAt: record.confirmedAt,
    teacherConfirmedAt: record.teacherConfirmedAt,
    teacherConfirmedByUserId: record.teacherConfirmedByUserId,
    adminConfirmedAt: record.adminConfirmedAt,
    adminConfirmedByUserId: record.adminConfirmedByUserId,
    rejectReason: record.rejectReason,
    rejectedAt: record.rejectedAt,
    rejectedByUserId: record.rejectedByUserId,
    actualTeacherId: record.actualTeacherId,
    actualTeacherName: record.actualTeacherName,
    actualClassroomId: record.actualClassroomId,
    actualClassroomName: record.actualClassroomName,
    actualStartAt: record.actualStartAt,
    actualEndAt: record.actualEndAt,
    actualUnits: record.actualUnits,
    notes: record.notes,
  };
}

function latestActiveRecordByOccurrence(records: LessonRecordItem[]) {
  const map = new Map<string, LessonRecordItem>();
  records.forEach(record => {
    const occurrenceKey = String(record.occurrenceId);
    if (!map.has(occurrenceKey)) {
      map.set(occurrenceKey, record);
    }
  });
  return map;
}

async function enrichPackageOccurrences(pkgId: EntityId, items: LessonOccurrenceItem[]) {
  try {
    const recordsRes = await fetchLessonRecords({ packageId: pkgId, pageSize: 500 });
    const recordMap = latestActiveRecordByOccurrence(recordsRes.data.data.records);
    return items.map(item => mergeRecordIntoOccurrence(item, recordMap.get(String(item.id))));
  } catch {
    return items;
  }
}

async function enrichClassOccurrences(items: ClassOccurrenceItem[]) {
  if (items.length === 0 || items.some(item => item.confirmStage || getLessonRecordId(item))) {
    return items;
  }

  try {
    const sorted = [...items].sort((a, b) => a.plannedStartAt.localeCompare(b.plannedStartAt));
    const recordsRes = await fetchLessonRecords({
      confirmedFrom: dayjs(sorted[0].plannedStartAt).subtract(1, 'day').format('YYYY-MM-DDT00:00:00'),
      confirmedTo: dayjs(sorted[sorted.length - 1].plannedStartAt).add(1, 'day').format('YYYY-MM-DDT23:59:59'),
      pageSize: 500,
    });
    const occurrenceIds = new Set(items.map(item => String(item.id)));
    const recordMap = latestActiveRecordByOccurrence(
      recordsRes.data.data.records.filter(record => occurrenceIds.has(String(record.occurrenceId)))
    );
    return items.map(item => mergeRecordIntoOccurrence(item, recordMap.get(String(item.id))));
  } catch {
    // 班课旧接口可能尚未携带 confirmStage，保留既有 confirmed/resultStatus 交互。
    return items;
  }
}

async function refreshCurrentOccurrences() {
  if (activeTab.value === 'class') {
    if (selectedClassId.value) await loadClassOccurrences(selectedClassId.value);
    return;
  }
  if (selectedPackageId.value) await loadPackageOccurrences(selectedPackageId.value);
}

// ─── 方法 ──────────────────────────────

// 选中学生 → 加载课包
async function selectStudent(id: EntityId) {
  selectedStudentId.value = id;
  selectedPackageId.value = null;
  occurrences.value = [];
  packagesLoading.value = true;
  try {
    const res = await fetchCoursePackages({
      studentId: id,
      packageStatus: 'ACTIVE',
      pageSize: 50,
    });
    packages.value = res.data.data.records.filter(pkg =>
      (!pkg.teachingMode || pkg.teachingMode === 'ONE_ON_ONE') && pkg.packageStatus === 'ACTIVE'
    );
    // 计算每个课包的最后课程日期
    await computeLastDates(id);
  } catch {
    packages.value = [];
  } finally {
    packagesLoading.value = false;
  }
}

// 选中课包 → 加载排课记录
async function selectPackage(pkgId: string | number) {
  const packageKey = String(pkgId);
  if (selectedPackageId.value === packageKey) {
    // 已展开则收起
    selectedPackageId.value = null;
    occurrences.value = [];
    return;
  }
  selectedPackageId.value = packageKey;
  await loadPackageOccurrences(packageKey);
}

async function loadPackageOccurrences(pkgId: EntityId) {
  const packageKey = String(pkgId);
  occurrencesLoading.value = true;
  try {
    // 以课包起止时间为查询范围，确保拉取所有排课记录
    const pkg = packages.value.find(p => String(p.id) === packageKey);
    const pkgStart = pkg?.startDate ? dayjs(pkg.startDate).format('YYYY-MM-DDT00:00:00') : null;
    const pkgEnd = pkg?.estimatedEndDate ? dayjs(pkg.estimatedEndDate).format('YYYY-MM-DDT23:59:59') : null;
    const res = await fetchLessonOccurrences({
      packageId: packageKey,
      startAt: pkgStart ?? dayjs().subtract(90, 'day').format('YYYY-MM-DDT00:00:00'),
      endAt: pkgEnd ?? dayjs().add(90, 'day').format('YYYY-MM-DDT23:59:59'),
      pageSize: 200,
    });
    occurrences.value = await enrichPackageOccurrences(packageKey, res.data.data.records);
    // 用实际返回的记录数更新课包排课计数（比 computeLastDates 的 ±89 天精确）
    occurrenceCounts[packageKey] = res.data.data.records.length;
  } catch {
    occurrences.value = [];
  } finally {
    occurrencesLoading.value = false;
  }
}

// 计算学生每个课包的最后课程日期和排课记录数
async function computeLastDates(studentId: EntityId) {
  try {
    // ±89 天（共 178 天），安全低于后端 180 天限制；23:59:59 已被后端忽略时分秒
    const startAt = dayjs().subtract(89, 'day').format('YYYY-MM-DDT00:00:00');
    const endAt = dayjs().add(89, 'day').format('YYYY-MM-DDT23:59:59');
    const res = await fetchLessonOccurrences({
      studentId,
      startAt,
      endAt,
      pageSize: 200,
    });
    const allOccs = res.data.data.records;
    // 按 packageId 分组：记录最后日期和记录数
    const byPkg = new Map<string, string>();
    const counts = new Map<string, number>();
    let studentLast = '';
    for (const occ of allOccs) {
      if (!occ.packageId) continue;
      const packageKey = String(occ.packageId);
      const date = dayjs(occ.plannedStartAt).format('MM/DD');
      if (!byPkg.has(packageKey) || occ.plannedStartAt > byPkg.get(packageKey)!) {
        byPkg.set(packageKey, date);
      }
      counts.set(packageKey, (counts.get(packageKey) ?? 0) + 1);
      if (!studentLast || occ.plannedStartAt > studentLast) {
        studentLast = occ.plannedStartAt;
      }
    }
    byPkg.forEach((v, k) => { packageLastDates[k] = v; });
    counts.forEach((v, k) => { occurrenceCounts[k] = v; });
    studentLastDates[studentId] = studentLast ? dayjs(studentLast).format('MM/DD') : '';
  } catch {
    // ignore
  }
}

// ─── 班课管理方法 ──────────────────────
const hasExpiringStudents = computed(() =>
  enrolledStudents.value.some(s => s.expiryStatus === 'EXPIRING_SOON' || s.expiryStatus === 'EXPIRED')
);

const expiryStatusFilters = [
  { text: 'Normal', value: 'NORMAL' },
  { text: 'Expiring Soon', value: 'EXPIRING_SOON' },
  { text: 'Expired', value: 'EXPIRED' },
];

function filterExpiryStatus(value: string, row: ClassEnrolledStudentItem): boolean {
  return row.expiryStatus === value;
}

async function selectClass(id: string | number) {
  const classKey = String(id);
  selectedClassId.value = classKey;
  classOccurrences.value = [];
  enrolledStudents.value = [];

  await Promise.all([loadClassStudents(classKey), loadClassOccurrences(classKey)]);
}

async function loadClassStudents(id: EntityId) {
  classStudentsLoading.value = true;
  try {
    const res = await fetchClassStudents(id);
    enrolledStudents.value = res.data.data;
  } catch {
    enrolledStudents.value = [];
  } finally {
    classStudentsLoading.value = false;
  }
}

async function loadClassOccurrences(id: EntityId) {
  occurrencesLoading.value = true;
  try {
    const res = await fetchClassOccurrences(id);
    classOccurrences.value = await enrichClassOccurrences(res.data.data);
  } catch {
    classOccurrences.value = [];
  } finally {
    occurrencesLoading.value = false;
  }
}

function openClassConfirmDialog(occ: ClassOccurrenceItem) {
  confirmSubmitMode.value = 'normal';
  selectedOcc.value = occ;
  confirmFormRef.value?.resetFields();
  Object.assign(confirmForm, {
    resultStatus: 'COMPLETED',
    actualTeacherId: occ.plannedTeacherId ?? selectedClass.value?.teacherId,
    actualClassroomId: occ.plannedClassroomId ?? selectedClass.value?.classroomId,
    actualStartAt: occ.plannedStartAt || '',
    actualHours: (occ.plannedUnits ?? 0) * 0.5,
    notes: '',
  });
  confirmDialogVisible.value = true;
  // 覆写 submitConfirm 判断 — 如果是班课模式走班课确认
  classConfirmMode.value = true;
}

const classConfirmMode = ref(false);

async function submitClassConfirm(occId: string | number) {
  if (!selectedClassId.value) return;
  if (!confirmFormRef.value) return;
  await confirmFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
        const payload = buildConfirmPayload(selectedOcc.value!);
        await confirmClassLesson(selectedClassId.value!, occId, payload);
      ElMessage.success('Class lesson confirmed successfully.');
      confirmDialogVisible.value = false;
      classConfirmMode.value = false;
        confirmSubmitMode.value = 'normal';
      await loadClassOccurrences(selectedClassId.value!);
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message ?? 'Confirmation failed.');
    } finally {
      submitting.value = false;
    }
  });
}

async function sendReminders() {
  if (!selectedClassId.value) return;
  reminding.value = true;
  try {
    const res = await sendClassExpiryReminders(selectedClassId.value);
    const count = res.data.data?.sentCount ?? 0;
    ElMessage.success(`${count} expiry reminder(s) sent.`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to send reminders.');
  } finally {
    reminding.value = false;
  }
}

async function handleDeleteClass() {
  if (!selectedClassId.value) return;
  try {
    await ElMessageBox.confirm(
      'Are you sure you want to delete this class? This will cancel all future occurrences and remove all enrollments.',
      'Delete Class',
      { confirmButtonText: 'Delete', cancelButtonText: 'Cancel', type: 'warning' }
    );
  } catch {
    return; // user cancelled
  }
  deleting.value = true;
  try {
    await deleteClassGroup(selectedClassId.value);
    ElMessage.success('Class group deleted.');
    selectedClassId.value = null;
    enrolledStudents.value = [];
    classOccurrences.value = [];
    await loadClasses();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
  } finally {
    deleting.value = false;
  }
}

async function loadClasses() {
  classesLoading.value = true;
  try {
    const res = await fetchClassGroups();
    classes.value = res.data.data;
  } catch {
    classes.value = [];
  } finally {
    classesLoading.value = false;
  }
}

function formatDateShort(dt: string): string {
  return dt ? dayjs(dt).format('MM/DD/YYYY') : '—';
}

// ─── 日期工具 ─────────────────────────
function isToday(dt: string): boolean {
  return dayjs(dt).format('YYYY-MM-DD') === dayjs().format('YYYY-MM-DD');
}
function isPast(dt: string): boolean {
  return dayjs(dt).isBefore(dayjs(), 'day');
}
function dayMarkerClass(dt: string): string {
  if (isToday(dt)) return 'pkg-occ-row--today';
  if (isPast(dt)) return 'pkg-occ-row--past';
  return 'pkg-occ-row--future';
}
function formatDate(dt: string): string {
  return dt ? dayjs(dt).format('MM/DD (ddd)') : '—';
}
function formatTimeRange(startAt: string, endAt: string): string {
  if (!startAt || !endAt) return '—';
  const s = startAt.includes('T') ? startAt.split('T')[1].slice(0, 5) : startAt.slice(0, 5);
  const e = endAt.includes('T') ? endAt.split('T')[1].slice(0, 5) : endAt.slice(0, 5);
  return `${s} – ${e}`;
}
// ─── 状态工具 ─────────────────────────
function resultStatusTag(status: string) {
  return { COMPLETED: 'success', FORMAL_LEAVE: 'warning', NO_SHOW: 'danger', CANCELLED: 'info' }[status] ?? '';
}
function resultStatusLabel(status: string) {
  return { COMPLETED: 'Completed', FORMAL_LEAVE: 'Leave', NO_SHOW: 'No Show', CANCELLED: 'Cancelled' }[status] ?? status;
}

// ─── Confirm 弹窗 ─────────────────────
const confirmForm = reactive({
  resultStatus: 'COMPLETED',
  actualTeacherId: undefined as string | number | undefined,
  actualClassroomId: undefined as string | number | undefined,
  actualStartAt: '',
  actualHours: undefined as number | undefined,
  notes: '',
});
const confirmFormRules: FormRules = {
  resultStatus: [{ required: true, message: 'Result status is required', trigger: 'change' }],
  actualTeacherId: [{ required: true, message: 'Teacher is required for payment', trigger: 'change' }],
};

// 管理员复核时记录课次原计划课时，用于对比老师是否改动了 hours。
const plannedHoursForReview = ref<number | null>(null);
// 仅在管理员终确认场景下，老师提交的 hours 与原计划不一致时高亮提示。
const hoursChangedByTeacher = computed(() => {
  if (confirmSubmitMode.value !== 'admin-final' || plannedHoursForReview.value == null) {
    return false;
  }
  return (confirmForm.actualHours ?? 0) !== plannedHoursForReview.value;
});

const deductionPreview = computed(() => {
  const status = confirmForm.resultStatus;
  if (status === 'FORMAL_LEAVE') return 'No hours will be deducted for this status.';
  const hours = confirmForm.actualHours;
  if (hours == null || hours <= 0) return 'No hours will be deducted.';
  return `This lesson will deduct ${hours.toFixed(1)}h.`;
});

const computedEndTime = computed(() => {
  if (!confirmForm.actualStartAt || !confirmForm.actualHours) return '';
  return dayjs(confirmForm.actualStartAt).add(confirmForm.actualHours, 'hour').format('YYYY-MM-DD HH:mm');
});

function openConfirmDialog(occ: LessonOccurrenceItem) {
  classConfirmMode.value = false;
  confirmSubmitMode.value = 'normal';
  selectedOcc.value = occ;
  plannedHoursForReview.value = null;
  confirmFormRef.value?.resetFields();
  Object.assign(confirmForm, {
    resultStatus: 'COMPLETED',
    actualTeacherId: occ.plannedTeacherId,
    actualClassroomId: occ.plannedClassroomId,
    actualStartAt: occ.plannedStartAt || '',
    actualHours: (occ.plannedUnits ?? 0) * 0.5,
    notes: '',
  });

  // 老师代其他老师确认 — 提示
  if (authStore.currentUser?.userType === 'TEACHER' && occ.plannedTeacherId && authStore.currentUser.teacherId != null && occ.plannedTeacherId !== authStore.currentUser.teacherId) {
    ElMessageBox.confirm(
      'This lesson is assigned to another teacher. Are you sure you want to confirm it?',
      'Confirm as Another Teacher',
      { confirmButtonText: 'Confirm', cancelButtonText: 'Cancel', type: 'warning' },
    ).then(() => { confirmDialogVisible.value = true; }).catch(() => {});
    return;
  }
  confirmDialogVisible.value = true;
}

function openAdminConfirmDialog(occ: ConfirmableOccurrence) {
  const recordId = getLessonRecordId(occ);
  if (!recordId) {
    ElMessage.warning('Missing pending lesson record id.');
    return;
  }
  selectedOcc.value = occ;
  confirmSubmitMode.value = 'admin-final';
  classConfirmMode.value = activeTab.value === 'class';
  confirmFormRef.value?.resetFields();
  // 记录原计划课时，便于对比老师确认时是否改动了 hours。
  plannedHoursForReview.value = (occ.plannedUnits ?? 0) * 0.5;
  Object.assign(confirmForm, {
    resultStatus: occ.resultStatus || 'COMPLETED',
    actualTeacherId: occ.actualTeacherId ?? occ.plannedTeacherId,
    actualClassroomId: occ.actualClassroomId ?? occ.plannedClassroomId,
    actualStartAt: occ.actualStartAt || occ.plannedStartAt || '',
    actualHours: resolveActualHours(occ),
    notes: occ.notes || '',
  });
  confirmDialogVisible.value = true;
}

function resolveActualHours(occ: ConfirmableOccurrence): number {
  if (occ.actualStartAt && occ.actualEndAt) {
    const minutes = dayjs(occ.actualEndAt).diff(dayjs(occ.actualStartAt), 'minute');
    if (minutes > 0) return minutes / 60;
  }
  if (occ.actualUnits != null) {
    // Class occurrence API returns raw half-hour units, while lesson record API returns hours.
    // After enrichment from lesson records, recordId marks the value as already converted.
    if (isClassOccurrence(occ)) {
      return getLessonRecordId(occ) ? occ.actualUnits : occ.actualUnits * 0.5;
    }
    return occ.actualUnits;
  }
  return ((occ.plannedUnits ?? 0) * 0.5);
}

function isClassOccurrence(occ: ConfirmableOccurrence): occ is ClassOccurrenceItem {
  return !('packageId' in occ);
}

function buildConfirmPayload(occ: ConfirmableOccurrence): LessonConfirmPayload {
  const hours = confirmForm.actualHours ?? 0;
  const startAt = confirmForm.actualStartAt || undefined;
  const endAt = startAt && hours > 0
    ? dayjs(startAt).add(hours, 'hour').format('YYYY-MM-DDTHH:mm:ss')
    : undefined;
  return {
    occurrenceId: occ.id,
    resultStatus: confirmForm.resultStatus,
    actualTeacherId: confirmForm.actualTeacherId,
    actualClassroomId: confirmForm.actualClassroomId,
    actualStartAt: startAt,
    actualEndAt: endAt,
    actualUnits: hours * 2,
    notes: confirmForm.notes || undefined,
  };
}

async function submitConfirm() {
  // 班课模式 → 走批量确认
  if (classConfirmMode.value && selectedClassId.value) {
    await submitClassConfirm(selectedOcc.value!.id);
    return;
  }
  if (!confirmFormRef.value) return;
  await confirmFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      if (confirmSubmitMode.value === 'admin-final') {
        const recordId = getLessonRecordId(selectedOcc.value!);
        if (!recordId) throw new Error('Missing pending lesson record id.');
          const result = await adminConfirmLessonRecord(recordId, buildConfirmPayload(selectedOcc.value!));
        const data = result.data.data;
        ElMessage.success(`Lesson finally confirmed. Deducted ${data.deductedUnits.toFixed(1)}h, remaining ${data.remainingUnits.toFixed(1)}h.`);
        confirmDialogVisible.value = false;
        confirmSubmitMode.value = 'normal';
        await refreshCurrentOccurrences();
        return;
      }
        const result = await confirmLesson(buildConfirmPayload(selectedOcc.value!));
      const data = result.data.data;
      ElMessage.success(`Lesson confirmed. Deducted ${data.deductedUnits.toFixed(1)}h, remaining ${data.remainingUnits.toFixed(1)}h.`);
      confirmDialogVisible.value = false;
      confirmSubmitMode.value = 'normal';
      // 刷新当前课包的排课记录
      if (selectedPackageId.value) await loadPackageOccurrences(selectedPackageId.value);
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message ?? 'Confirmation failed.');
    } finally {
      submitting.value = false;
    }
  });
}

const rejectForm = reactive({
  rejectReason: '',
});
const rejectFormRules: FormRules = {
  rejectReason: [{ required: true, message: 'Reject reason is required', trigger: 'blur' }],
};

function openRejectDialog(occ: ConfirmableOccurrence) {
  const recordId = getLessonRecordId(occ);
  if (!recordId) {
    ElMessage.warning('Missing pending lesson record id.');
    return;
  }
  selectedOcc.value = occ;
  rejectFormRef.value?.resetFields();
  rejectForm.rejectReason = '';
  rejectDialogVisible.value = true;
}

async function submitReject() {
  if (!rejectFormRef.value || !selectedOcc.value) return;
  await rejectFormRef.value.validate(async (valid) => {
    if (!valid) return;
    const recordId = getLessonRecordId(selectedOcc.value!);
    if (!recordId) {
      ElMessage.warning('Missing pending lesson record id.');
      return;
    }
    submitting.value = true;
    try {
      if (activeTab.value === 'class' && selectedClassId.value) {
        await rejectClassLesson(selectedClassId.value, selectedOcc.value!.id, { rejectReason: rejectForm.rejectReason.trim() });
      } else {
        await rejectLessonRecord(recordId, { rejectReason: rejectForm.rejectReason.trim() });
      }
      ElMessage.success('Lesson confirmation rejected.');
      rejectDialogVisible.value = false;
      await refreshCurrentOccurrences();
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message ?? 'Reject failed.');
    } finally {
      submitting.value = false;
    }
  });
}

// ─── Detail 弹窗 ──────────────────────
async function openDetailDialog(occ: LessonOccurrenceItem) {
  detailData.value = occ;
  detailDialogVisible.value = true;
}

// ─── Makeup 弹窗 ──────────────────────
const makeupForm = reactive({
  plannedTeacherId: undefined as string | number | undefined,
  plannedClassroomId: undefined as string | number | undefined,
  plannedStartAt: '',
  plannedEndAt: '',
  reason: '',
});
const makeupFormRules: FormRules = {
  plannedStartAt: [{ required: true, message: 'Start time is required', trigger: 'change' }],
  plannedEndAt: [{ required: true, message: 'End time is required', trigger: 'change' }],
};

function openMakeupDialog(occ: LessonOccurrenceItem) {
  selectedOcc.value = occ;
  makeupFormRef.value?.resetFields();
  Object.assign(makeupForm, {
    plannedTeacherId: undefined,
    plannedClassroomId: undefined,
    plannedStartAt: '',
    plannedEndAt: '',
    reason: '',
  });
  makeupDialogVisible.value = true;
}

async function submitMakeup() {
  if (!makeupFormRef.value) return;
  await makeupFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      await createScheduleAdjustment({
        occurrenceId: selectedOcc.value!.id,
        adjustmentType: 'MAKEUP_CREATE',
        plannedTeacherId: makeupForm.plannedTeacherId,
        plannedClassroomId: makeupForm.plannedClassroomId,
        plannedStartAt: makeupForm.plannedStartAt,
        plannedEndAt: makeupForm.plannedEndAt,
        reason: makeupForm.reason || undefined,
      });
      ElMessage.success('Makeup lesson created.');
      makeupDialogVisible.value = false;
      // 刷新排课记录
      if (selectedPackageId.value) await loadPackageOccurrences(selectedPackageId.value);
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message ?? 'Failed to create makeup.');
    } finally {
      submitting.value = false;
    }
  });
}

// ─── Init ──────────────────────────────
onMounted(async () => {
  studentsLoading.value = true;
  classesLoading.value = true;
  try {
    const [sRes, tRes, cRes, clzRes] = await Promise.all([
      fetchAllStudents(),
      fetchAllTeachers(),
      fetchAllClassrooms(),
      fetchClassGroups(),
    ]);
    students.value = sRes.data.data;
    teachers.value = tRes.data.data;
    classrooms.value = cRes.data.data;
    classes.value = clzRes.data.data;
  } catch {
    ElMessage.error('Failed to load initial data.');
  } finally {
    studentsLoading.value = false;
    classesLoading.value = false;
  }
});

// ─── Date helpers ──────────────────────────────────────────
function disabledPastDatetime(date: Date) {
  return dayjs(date).isBefore(dayjs().startOf('day'));
}
</script>

<style scoped>
/* ── Page Layout ─────────────────────── */
.adj-page {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: calc(100vh - 120px);
}

/* ── Tab bar ──────────────────────────── */
.adj-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid #e8edf2;
  margin-bottom: 0;
  flex-shrink: 0;
}
.adj-tab {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 500;
  color: #909399;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.adj-tab:hover { color: #18b58a; }
.adj-tab--active {
  color: #18b58a;
  border-bottom-color: #18b58a;
}

/* ── Workspace (left + right) ─────────── */
.adj-workspace {
  display: flex;
  flex: 1;
  min-height: 0;
  border: 1px solid #e8edf2;
  border-top: none;
  border-radius: 0 0 8px 8px;
  background: #fff;
  overflow: hidden;
}

/* ── Left sidebar ─────────────────────── */
.adj-sidebar {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #e8edf2;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
}
.adj-sidebar__search {
  padding: 12px;
  border-bottom: 1px solid #e8edf2;
}
.adj-sidebar__list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
}
.adj-sidebar__item {
  padding: 10px 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background 0.1s;
}
.adj-sidebar__item:hover { background: #f0faf6; }
.adj-sidebar__item--active {
  background: #eefaf5;
  border-left-color: #18b58a;
}
.adj-sidebar__name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
.adj-sidebar__meta {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.adj-sidebar__empty {
  padding: 24px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* ── Right content ──────────────────── */
.adj-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.adj-content__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  font-size: 14px;
}
.adj-content__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

/* ── Package cards ───────────────────── */
.adj-packages {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pkg-card {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.15s;
}
.pkg-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.pkg-card--expanded {
  border-color: #c0e6d8;
  box-shadow: 0 2px 12px rgba(24, 181, 138, 0.08);
}
.pkg-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  cursor: pointer;
  background: #fafbfc;
  transition: background 0.1s;
}
.pkg-card__header:hover { background: #f0faf6; }
.pkg-card__info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pkg-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.pkg-card__badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(24, 181, 138, 0.12);
  color: #0d8f6c;
  font-weight: 600;
}
.pkg-card__count {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
  font-weight: 500;
}
.pkg-card__meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pkg-card__last {
  font-size: 12px;
  color: #909399;
}
.pkg-card__arrow {
  font-size: 14px;
  color: #909399;
  transition: transform 0.2s;
}
.pkg-card__arrow--open { transform: rotate(180deg); }

/* ── Occurrence list ─────────────────── */
.pkg-card__body {
  border-top: 1px solid #e8edf2;
}
.pkg-card__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e8edf2;
}
.pkg-card__toolbar-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.pkg-card__toolbar-hint {
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
}
.pkg-occ-list {
  display: flex;
  flex-direction: column;
}
.pkg-occ-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 13px;
  transition: background 0.1s;
}
.pkg-occ-row:last-child { border-bottom: none; }
.pkg-occ-row--today { background: #f0faf6; }
.pkg-occ-row--past { color: #909399; }
.pkg-occ-row--future { background: #fafbfc; }
.pkg-occ-row:hover { background: #f8f9fc; }

.pkg-occ__marker { width: 52px; flex-shrink: 0; }
.pkg-occ__marker .el-tag { width: 100%; text-align: center; }
.pkg-occ__time { width: 130px; flex-shrink: 0; }
.pkg-occ__date { font-weight: 500; color: #303133; }
.pkg-occ__range { font-size: 12px; color: #909399; }
.pkg-occ__people { flex: 1; min-width: 0; }
.pkg-occ__room { font-size: 11px; color: #909399; }
.pkg-occ__hours { width: 50px; text-align: center; color: #606266; }
.pkg-occ__status {
  width: 180px;
  flex-shrink: 0;
  text-align: center;
}
.pkg-occ__status :deep(.el-tag) {
  max-width: 100%;
  justify-content: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pkg-occ__actions {
  width: 230px;
  text-align: right;
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}
.pkg-occ__actions :deep(.el-button) {
  margin-left: 0;
}

.pkg-occ-empty {
  padding: 24px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* ── Class Detail ─────────────────────── */
.class-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.class-detail__header {
  padding: 14px 16px;
  background: linear-gradient(135deg, #f0faf6, #f6f9fc);
  border-radius: 8px;
  border: 1px solid #e8edf2;
}
.class-detail__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.class-detail__title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}
.class-detail__info-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 32px;
  margin-top: 8px;
}
.class-detail__info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.class-detail__info-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: #909399;
  letter-spacing: 0.3px;
}
.class-detail__info-value {
  font-size: 14px;
  color: #303133;
}
.class-detail__desc {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}
.class-section {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
}
.class-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fafbfc;
  border-bottom: 1px solid #e8edf2;
}
.class-section__header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.class-empty {
  padding: 32px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* ── Dialog ──────────────────────────── */
.adj-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}
.adj-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}
.adj-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}
.makeup-hint {
  margin: 0 0 12px;
  color: #606266;
  font-size: 13px;
}
.units-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.form-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
/* 老师改动课时时的警示提示 */
.form-hint--warning {
  color: #f56c6c;
  font-weight: 500;
}
/* 老师改动课时时高亮 Hours 输入框 */
.hours-input--changed :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
.end-time-display {
  padding: 6px 0;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
}
.lesson-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.detail-section__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 20px;
}
.detail-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.detail-field label { color: #909399; font-size: 12px; }
.detail-field span { color: #303133; font-size: 13px; }
.detail-notes {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
</style>
