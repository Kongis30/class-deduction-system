package com.classdeduction.system.modules.lesson.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.lesson.service.impl.LessonRecordServiceImpl;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.systemconfig.service.NotificationOutboxService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课次确认服务测试：覆盖正常扣课和余额不足阻断这两个最关键的消课场景。
 */
@ExtendWith(MockitoExtension.class)
class LessonRecordServiceImplTest {

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private HourLedgerMapper hourLedgerMapper;

    @Mock
    private PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;

    @Mock
    private LessonStatusRuleMapper lessonStatusRuleMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private NotificationOutboxService notificationOutboxService;

    @Mock
    private com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper notificationTriggerRuleMapper;

    @Captor
    private ArgumentCaptor<LessonRecordEntity> recordCaptor;

    @Captor
    private ArgumentCaptor<HourLedgerEntity> ledgerCaptor;

    @Captor
    private ArgumentCaptor<CoursePackageEntity> packageCaptor;

    @Captor
    private ArgumentCaptor<PackageBalanceSnapshotEntity> snapshotCaptor;

    @Captor
    private ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor;

    private LessonRecordServiceImpl lessonRecordService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单元测试不启动 MyBatis 上下文，手动初始化 LambdaQueryWrapper 字段缓存。
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonOccurrenceEntity.class);
    }

    @BeforeEach
    void setUp() {
        lessonRecordService = new LessonRecordServiceImpl(
                lessonRecordMapper,
                hourLedgerMapper,
                packageBalanceSnapshotMapper,
                lessonStatusRuleMapper,
                lessonOccurrenceMapper,
                coursePackageMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                notificationOutboxService,
                notificationTriggerRuleMapper
        );
    }

    @Test
    void shouldConfirmCompletedLessonAndWriteLedger() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(701L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(801L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(buildFutureOccurrence(2001L, LocalDate.of(2026, 4, 20), 4), buildFutureOccurrence(2002L, LocalDate.of(2026, 4, 27), 4)));
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        stubInsertedIds();

        LessonRecordConfirmResultVO result = lessonRecordService.confirmLesson(request, currentStaffUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        verify(coursePackageMapper, org.mockito.Mockito.atLeastOnce()).updateById(packageCaptor.capture());
        verify(packageBalanceSnapshotMapper).insert(snapshotCaptor.capture());
        verify(notificationOutboxService).queueLessonBalanceEmail(any(LessonRecordEntity.class), any(CoursePackageEntity.class), any(StudentEntity.class), eq("Completed"));

        assertThat(recordCaptor.getValue().getDeductedUnits()).isEqualTo(4);
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(-4);
        assertThat(packageCaptor.getValue().getRemainingUnits()).isEqualTo(6);
        assertThat(snapshotCaptor.getValue().getRemainingUnits()).isEqualTo(6);
        assertThat(result.ledgerId()).isEqualTo(3001L);
        assertThat(result.remainingUnits()).isEqualTo(3.0);
        assertThat(result.packageCompleted()).isFalse();
    }

    @Test
    void shouldCreatePendingAdminRecordWithoutDeductionWhenTeacherInitialConfirms() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        request.setNotes("Teacher submitted actual lesson.");
        LessonOccurrenceEntity occurrence = buildRecentOccurrence();
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(701L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(801L)).thenReturn(buildClassroom());
        stubRecordInsertedId(2001L);

        LessonRecordConfirmResultVO result = lessonRecordService.confirmLesson(request, currentTeacherUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        LessonRecordEntity pendingRecord = recordCaptor.getValue();
        assertThat(pendingRecord.getConfirmStage()).isEqualTo("PENDING_ADMIN");
        assertThat(pendingRecord.getRecordSource()).isEqualTo("TEACHER");
        assertThat(pendingRecord.getTeacherConfirmedByUserId()).isEqualTo(1002L);
        assertThat(pendingRecord.getConfirmedAt()).isNull();
        assertThat(pendingRecord.getAdminConfirmedAt()).isNull();
        assertThat(result.ledgerId()).isNull();
        assertThat(result.remainingUnits()).isEqualTo(5.0);

        verify(hourLedgerMapper, never()).insert(any(HourLedgerEntity.class));
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldFinalizePendingRecordAndSettleDeductionWhenAdminConfirms() {
        LessonRecordEntity pendingRecord = buildPendingRecord();
        LessonOccurrenceEntity occurrence = buildOccurrence();
        when(lessonRecordMapper.selectById(2001L)).thenReturn(pendingRecord);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(lessonRecordMapper.update(any(LessonRecordEntity.class), any())).thenReturn(1);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubLedgerInsertedId(3001L);

        LessonRecordConfirmResultVO result = lessonRecordService.adminConfirmLesson(2001L, currentAdminUser());

        verify(lessonRecordMapper).update(recordCaptor.capture(), any());
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        verify(coursePackageMapper).updateById(packageCaptor.capture());
        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(recordCaptor.getValue().getConfirmStage()).isEqualTo("CONFIRMED");
        assertThat(recordCaptor.getValue().getAdminConfirmedByUserId()).isEqualTo(999L);
        assertThat(recordCaptor.getValue().getAdminConfirmedAt()).isNotNull();
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(-4);
        assertThat(packageCaptor.getValue().getRemainingUnits()).isEqualTo(6);
        assertThat(occurrenceCaptor.getValue().getPlanningStatus()).isEqualTo("MERGED");
        assertThat(result.ledgerId()).isEqualTo(3001L);
    }

    @Test
    void shouldRejectStaleAdminFinalConfirmBeforeWritingLedger() {
        LessonRecordEntity pendingRecord = buildPendingRecord();
        when(lessonRecordMapper.selectById(2001L)).thenReturn(pendingRecord);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(lessonRecordMapper.update(any(LessonRecordEntity.class), any())).thenReturn(0);

        assertThatThrownBy(() -> lessonRecordService.adminConfirmLesson(2001L, currentAdminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_CONFIRM_STAGE");

        verify(hourLedgerMapper, never()).insert(any(HourLedgerEntity.class));
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldApplyAdminReviewChangesWhenFinalConfirmingPendingRecord() {
        LessonRecordEntity pendingRecord = buildPendingRecord();
        LessonOccurrenceEntity occurrence = buildOccurrence();
        LessonRecordConfirmRequest reviewRequest = new LessonRecordConfirmRequest();
        reviewRequest.setOccurrenceId(1001L);
        reviewRequest.setResultStatus("COMPLETED");
        reviewRequest.setActualTeacherId(702L);
        reviewRequest.setActualClassroomId(802L);
        reviewRequest.setActualStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        reviewRequest.setActualEndAt(LocalDateTime.of(2026, 4, 12, 19, 30));
        reviewRequest.setActualUnits(3);
        reviewRequest.setNotes("Admin corrected actual duration and teacher.");

        when(lessonRecordMapper.selectById(2001L)).thenReturn(pendingRecord);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(702L)).thenReturn(buildTeacher(702L));
        when(classroomMapper.selectById(802L)).thenReturn(buildClassroom(802L));
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(buildFutureOccurrence(2001L, LocalDate.of(2026, 4, 20), 4)));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(lessonRecordMapper.update(any(LessonRecordEntity.class), any())).thenReturn(1);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubLedgerInsertedId(3001L);

        LessonRecordConfirmResultVO result = lessonRecordService.adminConfirmLesson(2001L, reviewRequest, currentAdminUser());

        verify(lessonRecordMapper).update(recordCaptor.capture(), any());
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        verify(coursePackageMapper, org.mockito.Mockito.atLeastOnce()).updateById(packageCaptor.capture());
        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonRecordEntity updatedRecord = recordCaptor.getAllValues().get(recordCaptor.getAllValues().size() - 1);
        LessonOccurrenceEntity balancedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        assertThat(updatedRecord.getActualTeacherId()).isEqualTo(702L);
        assertThat(updatedRecord.getActualClassroomId()).isEqualTo(802L);
        assertThat(updatedRecord.getActualUnits()).isEqualTo(3);
        assertThat(updatedRecord.getDeductedUnits()).isEqualTo(3);
        assertThat(updatedRecord.getNotes()).isEqualTo("Admin corrected actual duration and teacher.");
        assertThat(balancedFuture.getPlannedUnits()).isEqualTo(5);
        assertThat(balancedFuture.getPlannedEndAt()).isEqualTo(LocalDate.of(2026, 4, 20).atTime(20, 30));
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(-3);
        assertThat(packageCaptor.getValue().getRemainingUnits()).isEqualTo(7);
        assertThat(packageCaptor.getValue().getEstimatedEndDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(result.deductedUnits()).isEqualTo(1.5);
    }

    @Test
    void shouldRejectAdminConfirmationWhenActualLessonConflicts() {
        LessonRecordEntity pendingRecord = buildPendingRecord();
        LessonOccurrenceEntity occurrence = buildOccurrence();
        LessonRecordConfirmRequest reviewRequest = new LessonRecordConfirmRequest();
        reviewRequest.setOccurrenceId(1001L);
        reviewRequest.setResultStatus("COMPLETED");
        reviewRequest.setActualStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        reviewRequest.setActualEndAt(LocalDateTime.of(2026, 4, 12, 19, 30));
        reviewRequest.setActualUnits(3);

        when(lessonRecordMapper.selectById(2001L)).thenReturn(pendingRecord);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(any())).thenReturn(buildTeacher(701L));
        when(classroomMapper.selectById(any())).thenReturn(buildClassroom(801L));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> lessonRecordService.adminConfirmLesson(
                2001L, reviewRequest, currentAdminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SCHEDULE_CONFLICT");

        verify(hourLedgerMapper, never()).insert(any(HourLedgerEntity.class));
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
    }

    @Test
    void shouldBalanceFutureOccurrencesWhenTeacherChangesHoursThenAdminFinalConfirms() {
        LessonRecordEntity pendingRecord = buildPendingRecord();
        pendingRecord.setActualEndAt(LocalDateTime.of(2026, 4, 12, 19, 30));
        pendingRecord.setActualUnits(3);
        pendingRecord.setDeductedUnits(3);
        LessonOccurrenceEntity occurrence = buildOccurrence();
        LessonOccurrenceEntity future = buildFutureOccurrence(2001L, LocalDate.of(2026, 4, 20), 4);
        when(lessonRecordMapper.selectById(2001L)).thenReturn(pendingRecord);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(future));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(lessonRecordMapper.update(any(LessonRecordEntity.class), any())).thenReturn(1);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubLedgerInsertedId(3001L);

        lessonRecordService.adminConfirmLesson(2001L, currentAdminUser());

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonOccurrenceEntity mergedOccurrence = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 1001L))
                .findFirst()
                .orElseThrow();
        LessonOccurrenceEntity balancedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        assertThat(mergedOccurrence.getPlanningStatus()).isEqualTo("MERGED");
        assertThat(mergedOccurrence.getPlannedUnits()).isEqualTo(3);
        assertThat(mergedOccurrence.getPlannedEndAt()).isEqualTo(LocalDateTime.of(2026, 4, 12, 19, 30));
        assertThat(balancedFuture.getPlannedUnits()).isEqualTo(5);
        assertThat(balancedFuture.getPlannedEndAt()).isEqualTo(LocalDate.of(2026, 4, 20).atTime(20, 30));
    }

    @Test
    void shouldShortenFutureOccurrenceWhenAdminDirectConfirmUsesMoreHours() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        request.setActualStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        request.setActualEndAt(LocalDateTime.of(2026, 4, 12, 21, 0));
        request.setActualUnits(6);
        LessonOccurrenceEntity future = buildFutureOccurrence(2001L, LocalDate.of(2026, 4, 20), 4);
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(701L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(801L)).thenReturn(buildClassroom());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(future));
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubInsertedIds();

        lessonRecordService.confirmLesson(request, currentAdminUser());

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonOccurrenceEntity shortenedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        assertThat(shortenedFuture.getPlannedUnits()).isEqualTo(2);
        assertThat(shortenedFuture.getPlannedEndAt()).isEqualTo(LocalDate.of(2026, 4, 20).atTime(19, 0));
    }

    @Test
    void shouldRequireScheduleReconciliationWhenLongerLessonHasNoFutureOccurrence() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        request.setActualStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        request.setActualEndAt(LocalDateTime.of(2026, 4, 12, 21, 0));
        request.setActualUnits(6);
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(701L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(801L)).thenReturn(buildClassroom());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubInsertedIds();

        assertThatThrownBy(() -> lessonRecordService.confirmLesson(request, currentAdminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("FUTURE_SCHEDULE_RECONCILIATION_REQUIRED");
    }

    @Test
    void shouldCreateMakeupWithoutDeductionForFormalLeave() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("FORMAL_LEAVE");
        LessonOccurrenceEntity future = buildFutureOccurrence(2001L, LocalDate.of(2026, 4, 20), 4);
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildFormalLeaveRule());
        when(lessonOccurrenceMapper.selectOne(any())).thenReturn(future);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(), List.of(future));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubInsertedIds();

        LessonRecordConfirmResultVO result = lessonRecordService.confirmLesson(request, currentAdminUser());

        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        assertThat(ledgerCaptor.getValue().getLedgerType()).isEqualTo("NO_DEDUCT_AUDIT");
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isZero();
        assertThat(occurrenceCaptor.getValue().getOccurrenceType()).isEqualTo("MAKEUP");
        assertThat(occurrenceCaptor.getValue().getPlannedUnits()).isEqualTo(4);
        assertThat(result.deductedUnits()).isZero();
    }

    @Test
    void shouldRejectFormalLeaveMakeupWhenStudentScheduleConflicts() {
        assertFormalLeaveMakeupConflictRejected(List.of(1L), "Student conflict");
    }

    @Test
    void shouldRejectFormalLeaveMakeupWhenTeacherScheduleConflicts() {
        assertFormalLeaveMakeupConflictRejected(List.of(0L, 1L), "Teacher conflict");
    }

    @Test
    void shouldRejectFormalLeaveMakeupWhenClassroomScheduleConflicts() {
        assertFormalLeaveMakeupConflictRejected(List.of(0L, 0L, 1L), "Classroom conflict");
    }

    @Test
    void shouldDeductPlannedHoursForNoShowWithoutChangingFutureSchedule() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("NO_SHOW");
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildNoShowRule());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubInsertedIds();

        LessonRecordConfirmResultVO result = lessonRecordService.confirmLesson(request, currentAdminUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        assertThat(recordCaptor.getValue().getActualUnits()).isZero();
        assertThat(recordCaptor.getValue().getDeductedUnits()).isEqualTo(4);
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(-4);
        assertThat(result.remainingUnits()).isEqualTo(3.0);
    }

    @Test
    void shouldDeductPlannedHoursForCancellationWithoutCreatingMakeup() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("CANCELLED");
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCancelledRule());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubInsertedIds();

        LessonRecordConfirmResultVO result = lessonRecordService.confirmLesson(request, currentAdminUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
        assertThat(recordCaptor.getValue().getActualUnits()).isZero();
        assertThat(recordCaptor.getValue().getDeductedUnits()).isEqualTo(4);
        assertThat(recordCaptor.getValue().getShouldExtendEndDate()).isZero();
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(-4);
        assertThat(result.remainingUnits()).isEqualTo(3.0);
    }

    @Test
    void shouldSkipFutureOccurrenceWithPendingRecordWhenAdminDirectConfirmBalancesHours() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        request.setActualStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        request.setActualEndAt(LocalDateTime.of(2026, 4, 12, 21, 0));
        request.setActualUnits(6);
        LessonOccurrenceEntity adjustableFuture = buildFutureOccurrence(2001L, LocalDate.of(2026, 4, 20), 4);
        LessonOccurrenceEntity recordedFuture = buildFutureOccurrence(2002L, LocalDate.of(2026, 4, 27), 4);
        LessonRecordEntity pendingFutureRecord = new LessonRecordEntity();
        pendingFutureRecord.setId(3002L);
        pendingFutureRecord.setOccurrenceId(2002L);
        pendingFutureRecord.setConfirmStage("PENDING_ADMIN");
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(701L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(801L)).thenReturn(buildClassroom());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(pendingFutureRecord));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(adjustableFuture, recordedFuture));
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubInsertedIds();

        lessonRecordService.confirmLesson(request, currentAdminUser());

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonOccurrenceEntity shortenedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        boolean recordedFutureTouched = occurrenceCaptor.getAllValues().stream()
                .anyMatch(item -> Objects.equals(item.getId(), 2002L));
        assertThat(shortenedFuture.getPlannedUnits()).isEqualTo(2);
        assertThat(recordedFutureTouched).isFalse();
    }

    @Test
    void shouldDirectlyConfirmAndSettleWhenAdminConfirmsWithoutPendingRecord() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(701L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(801L)).thenReturn(buildClassroom());
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubInsertedIds();

        LessonRecordConfirmResultVO result = lessonRecordService.confirmLesson(request, currentAdminUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        verify(coursePackageMapper).updateById(packageCaptor.capture());
        assertThat(recordCaptor.getValue().getConfirmStage()).isEqualTo("CONFIRMED");
        assertThat(recordCaptor.getValue().getRecordSource()).isEqualTo("STAFF");
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(-4);
        assertThat(packageCaptor.getValue().getRemainingUnits()).isEqualTo(6);
        assertThat(result.ledgerId()).isEqualTo(3001L);
    }

    @Test
    void shouldRejectPendingRecordWithoutWritingLedger() {
        LessonRecordEntity pendingRecord = buildPendingRecord();
        LessonOccurrenceEntity occurrence = buildOccurrence();
        occurrence.setPlanningStatus("MERGED");
        when(lessonRecordMapper.selectById(2001L)).thenReturn(pendingRecord);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);

        lessonRecordService.rejectLesson(2001L, "Actual time needs correction.", currentAdminUser());

        verify(lessonRecordMapper).updateById(recordCaptor.capture());
        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(recordCaptor.getValue().getConfirmStage()).isEqualTo("REJECTED");
        assertThat(recordCaptor.getValue().getRejectReason()).isEqualTo("Actual time needs correction.");
        assertThat(recordCaptor.getValue().getRejectedByUserId()).isEqualTo(999L);
        assertThat(occurrenceCaptor.getValue().getPlanningStatus()).isEqualTo("SCHEDULED");
        verify(hourLedgerMapper, never()).insert(any(HourLedgerEntity.class));
    }

    @Test
    void shouldAllowTeacherToConfirmAgainAfterAdminRejects() {
        LessonRecordEntity rejectedRecord = buildPendingRecord();
        rejectedRecord.setConfirmStage("REJECTED");
        rejectedRecord.setRejectReason("Actual time needs correction.");
        rejectedRecord.setRejectedByUserId(999L);
        rejectedRecord.setRejectedAt(LocalDateTime.of(2026, 4, 12, 20, 10));

        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        request.setNotes("Teacher corrected and confirmed again.");

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildRecentOccurrence());
        when(lessonRecordMapper.selectOne(any())).thenReturn(rejectedRecord);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(701L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(801L)).thenReturn(buildClassroom());

        lessonRecordService.initialConfirmLesson(request, currentTeacherUser());

        verify(lessonRecordMapper).updateById(recordCaptor.capture());
        LessonRecordEntity updated = recordCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(2001L);
        assertThat(updated.getConfirmStage()).isEqualTo("PENDING_ADMIN");
        assertThat(updated.getTeacherConfirmedByUserId()).isEqualTo(1002L);
        assertThat(updated.getRejectReason()).isNull();
        assertThat(updated.getRejectedByUserId()).isNull();
        assertThat(updated.getRejectedAt()).isNull();
        verify(hourLedgerMapper, never()).insert(any(HourLedgerEntity.class));
    }

    @Test
    void shouldReverseFutureBalanceAndRestorePlannedSnapshotWhenRevokingConfirmedRecord() {
        LessonRecordEntity confirmedRecord = buildPendingRecord();
        confirmedRecord.setConfirmStage("CONFIRMED");
        confirmedRecord.setActualEndAt(LocalDateTime.of(2026, 4, 12, 19, 30));
        confirmedRecord.setActualUnits(3);
        confirmedRecord.setDeductedUnits(3);
        confirmedRecord.setPlannedStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        confirmedRecord.setPlannedEndAt(LocalDateTime.of(2026, 4, 12, 20, 0));
        confirmedRecord.setPlannedUnits(4);
        LessonOccurrenceEntity mergedOccurrence = buildOccurrence();
        mergedOccurrence.setPlanningStatus("MERGED");
        mergedOccurrence.setPlannedEndAt(LocalDateTime.of(2026, 4, 12, 19, 30));
        mergedOccurrence.setPlannedUnits(3);
        // 确认阶段已把课次改成实际老师/教室（与原计划 701/801 不同），撤销后应恢复原计划。
        mergedOccurrence.setPlannedTeacherId(702L);
        mergedOccurrence.setPlannedClassroomId(802L);
        LessonOccurrenceEntity balancedFuture = buildFutureOccurrence(2002L, LocalDate.of(2026, 4, 20), 5);
        when(lessonRecordMapper.selectById(2001L)).thenReturn(confirmedRecord);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 3));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(balancedFuture), List.of());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(mergedOccurrence);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        stubLedgerInsertedId(3001L);

        lessonRecordService.revokeLesson(2001L, currentAdminUser());

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonOccurrenceEntity shortenedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2002L))
                .findFirst()
                .orElseThrow();
        LessonOccurrenceEntity restoredOccurrence = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 1001L))
                .findFirst()
                .orElseThrow();
        assertThat(shortenedFuture.getPlannedUnits()).isEqualTo(4);
        assertThat(shortenedFuture.getPlannedEndAt()).isEqualTo(LocalDate.of(2026, 4, 20).atTime(20, 0));
        assertThat(restoredOccurrence.getPlanningStatus()).isEqualTo("SCHEDULED");
        assertThat(restoredOccurrence.getPlannedUnits()).isEqualTo(4);
        assertThat(restoredOccurrence.getPlannedEndAt()).isEqualTo(LocalDateTime.of(2026, 4, 12, 20, 0));
        // 撤销确认必须把课次的老师/教室恢复为确认前的原计划，避免日历与后续冲突检测错误占用。
        assertThat(restoredOccurrence.getPlannedTeacherId()).isEqualTo(701L);
        assertThat(restoredOccurrence.getPlannedClassroomId()).isEqualTo(801L);
    }

    @Test
    void shouldRejectWhenDeductionWouldMakeBalanceNegative() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("NO_SHOW");
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(2, 10));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildNoShowRule());

        assertThatThrownBy(() -> lessonRecordService.confirmLesson(request, currentStaffUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The package does not have enough remaining units for this confirmation.");
    }

    private void assertFormalLeaveMakeupConflictRejected(List<Long> conflictCounts, String expectedMessage) {
        LessonRecordEntity pendingRecord = buildPendingRecord();
        pendingRecord.setResultStatus("FORMAL_LEAVE");
        pendingRecord.setShouldDeduct(0);
        pendingRecord.setDeductedUnits(0);
        pendingRecord.setShouldExtendEndDate(1);
        LessonOccurrenceEntity occurrence = buildOccurrence();
        LessonOccurrenceEntity lastOccurrence = buildFutureOccurrence(2001L, LocalDate.of(2026, 4, 20), 4);
        lastOccurrence.setStudentId(601L);
        lastOccurrence.setPlannedTeacherId(701L);
        lastOccurrence.setPlannedClassroomId(801L);
        CoursePackageEntity coursePackage = buildPackage(10, 2);
        AtomicInteger conflictCheckIndex = new AtomicInteger();
        List<Long> allConflictCounts = new ArrayList<>(List.of(0L, 0L, 0L));
        allConflictCounts.addAll(conflictCounts);

        when(lessonRecordMapper.selectById(2001L)).thenReturn(pendingRecord);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(coursePackageMapper.selectById(501L)).thenReturn(coursePackage);
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildFormalLeaveRule());
        when(lessonOccurrenceMapper.selectOne(any())).thenReturn(lastOccurrence);
        when(lessonOccurrenceMapper.selectCount(any())).thenAnswer(invocation ->
                allConflictCounts.get(conflictCheckIndex.getAndIncrement()));

        assertThatThrownBy(() -> lessonRecordService.adminConfirmLesson(2001L, currentAdminUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(expectedMessage);

        verify(lessonRecordMapper, never()).updateById(any(LessonRecordEntity.class));
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
        verify(hourLedgerMapper, never()).insert(any(HourLedgerEntity.class));
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
        assertThat(pendingRecord.getConfirmStage()).isEqualTo("PENDING_ADMIN");
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(10);
    }

    private void stubInsertedIds() {
        stubRecordInsertedId(2001L);
        stubLedgerInsertedId(3001L);
    }

    private void stubRecordInsertedId(Long recordId) {
        doAnswer(invocation -> {
            LessonRecordEntity entity = invocation.getArgument(0);
            entity.setId(recordId);
            return 1;
        }).when(lessonRecordMapper).insert(any(LessonRecordEntity.class));
    }

    private void stubLedgerInsertedId(Long ledgerId) {
        doAnswer(invocation -> {
            HourLedgerEntity entity = invocation.getArgument(0);
            entity.setId(ledgerId);
            return 1;
        }).when(hourLedgerMapper).insert(any(HourLedgerEntity.class));
    }

    private CurrentUserResponse currentStaffUser() {
        return new CurrentUserResponse(999L, "staff", "Front Desk", "STAFF", null, null, List.of("STAFF"));
    }

    private CurrentUserResponse currentAdminUser() {
        return new CurrentUserResponse(999L, "admin", "System Administrator", "ADMIN", null, null, List.of("ADMIN"));
    }

    private CurrentUserResponse currentTeacherUser() {
        return new CurrentUserResponse(1002L, "teacher", "Emma Wang", "TEACHER", 701L, null, List.of("TEACHER"));
    }

    private LessonOccurrenceEntity buildOccurrence() {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(1001L);
        occurrence.setLessonNo("LS-1001");
        occurrence.setPackageId(501L);
        occurrence.setStudentId(601L);
        occurrence.setPlannedTeacherId(701L);
        occurrence.setPlannedClassroomId(801L);
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        occurrence.setPlannedEndAt(LocalDateTime.of(2026, 4, 12, 20, 0));
        occurrence.setPlannedUnits(4);
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private LessonOccurrenceEntity buildRecentOccurrence() {
        LessonOccurrenceEntity occurrence = buildOccurrence();
        LocalDateTime startAt = LocalDateTime.now(com.classdeduction.system.common.time.AppTime.APP_ZONE_ID).minusHours(2);
        occurrence.setPlannedStartAt(startAt);
        occurrence.setPlannedEndAt(startAt.plusHours(2));
        return occurrence;
    }

    private LessonRecordEntity buildPendingRecord() {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setId(2001L);
        record.setOccurrenceId(1001L);
        record.setPackageId(501L);
        record.setStudentId(601L);
        record.setPlannedTeacherId(701L);
        record.setActualTeacherId(701L);
        record.setPlannedClassroomId(801L);
        record.setActualClassroomId(801L);
        record.setPlannedStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        record.setPlannedEndAt(LocalDateTime.of(2026, 4, 12, 20, 0));
        record.setPlannedUnits(4);
        record.setResultStatus("COMPLETED");
        record.setActualStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        record.setActualEndAt(LocalDateTime.of(2026, 4, 12, 20, 0));
        record.setActualUnits(4);
        record.setShouldDeduct(1);
        record.setDeductedUnits(4);
        record.setShouldExtendEndDate(0);
        record.setConfirmStage("PENDING_ADMIN");
        record.setTeacherConfirmedByUserId(1002L);
        record.setTeacherConfirmedAt(LocalDateTime.of(2026, 4, 12, 20, 5));
        record.setRecordSource("TEACHER");
        return record;
    }

    private LessonOccurrenceEntity buildFutureOccurrence(Long id, LocalDate date, int units) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(id);
        occurrence.setPackageId(501L);
        occurrence.setPlannedStartAt(date.atTime(18, 0));
        occurrence.setPlannedEndAt(date.atTime(18, 0).plusMinutes(units * 30L));
        occurrence.setPlannedUnits(units);
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private CoursePackageEntity buildPackage(int remainingUnits, int consumedUnits) {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(501L);
        coursePackage.setPackageNo("PK-501");
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setTotalUnits(12);
        coursePackage.setConsumedUnits(consumedUnits);
        coursePackage.setRemainingUnits(remainingUnits);
        return coursePackage;
    }

    private StudentEntity buildStudent() {
        StudentEntity student = new StudentEntity();
        student.setId(601L);
        student.setStudentName("Olivia Lin");
        return student;
    }

    private TeacherEntity buildTeacher() {
        return buildTeacher(701L);
    }

    private TeacherEntity buildTeacher(Long id) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setStatus("ACTIVE");
        return teacher;
    }

    private ClassroomEntity buildClassroom() {
        return buildClassroom(801L);
    }

    private ClassroomEntity buildClassroom(Long id) {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(id);
        classroom.setStatus("ACTIVE");
        return classroom;
    }

    private LessonStatusRuleEntity buildCompletedRule() {
        LessonStatusRuleEntity rule = new LessonStatusRuleEntity();
        rule.setId(1L);
        rule.setStatusCode("COMPLETED");
        rule.setStatusName("Completed");
        rule.setDefaultShouldDeduct(1);
        rule.setDeductMode("ACTUAL_UNITS");
        rule.setDefaultShouldExtend(0);
        rule.setAllowManualOverride(1);
        rule.setStatus("ACTIVE");
        return rule;
    }

    private LessonStatusRuleEntity buildNoShowRule() {
        LessonStatusRuleEntity rule = new LessonStatusRuleEntity();
        rule.setId(2L);
        rule.setStatusCode("NO_SHOW");
        rule.setStatusName("No-show");
        rule.setDefaultShouldDeduct(1);
        rule.setDeductMode("PLANNED_UNITS");
        rule.setDefaultShouldExtend(0);
        rule.setAllowManualOverride(1);
        rule.setStatus("ACTIVE");
        return rule;
    }

    private LessonStatusRuleEntity buildFormalLeaveRule() {
        LessonStatusRuleEntity rule = new LessonStatusRuleEntity();
        rule.setId(3L);
        rule.setStatusCode("FORMAL_LEAVE");
        rule.setStatusName("Formal Leave");
        rule.setDefaultShouldDeduct(0);
        rule.setDeductMode("NONE");
        rule.setDefaultShouldExtend(1);
        rule.setAllowManualOverride(0);
        rule.setStatus("ACTIVE");
        return rule;
    }

    private LessonStatusRuleEntity buildCancelledRule() {
        LessonStatusRuleEntity rule = new LessonStatusRuleEntity();
        rule.setId(4L);
        rule.setStatusCode("CANCELLED");
        rule.setStatusName("Cancelled");
        rule.setDefaultShouldDeduct(1);
        rule.setDeductMode("PLANNED_UNITS");
        rule.setDefaultShouldExtend(0);
        rule.setAllowManualOverride(0);
        rule.setStatus("ACTIVE");
        return rule;
    }
}
