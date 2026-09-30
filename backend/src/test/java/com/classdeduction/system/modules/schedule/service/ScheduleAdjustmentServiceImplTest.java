package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.dto.WeeklyScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.impl.ScheduleAdjustmentServiceImpl;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
import com.classdeduction.system.modules.schedule.vo.WeeklyScheduleAdjustmentResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调课服务测试：覆盖补课创建时的新课次生成与日志落库。
 */
@ExtendWith(MockitoExtension.class)
class ScheduleAdjustmentServiceImplTest {

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonAdjustmentLogMapper lessonAdjustmentLogMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private JsonHelper jsonHelper;

    @Mock
    private ClassHolidayService classHolidayService;

    @Captor
    private ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor;

    @Captor
    private ArgumentCaptor<LessonAdjustmentLogEntity> logCaptor;

    private ScheduleAdjustmentServiceImpl scheduleAdjustmentService;

    @BeforeEach
    void setUp() {
        scheduleAdjustmentService = new ScheduleAdjustmentServiceImpl(
                lessonOccurrenceMapper,
                lessonAdjustmentLogMapper,
                lessonRecordMapper,
                classEnrollmentMapper,
                classGroupMapper,
                coursePackageMapper,
                privateLessonGroupMemberMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                jsonHelper,
                classHolidayService
        );
        lenient().when(coursePackageMapper.selectById(501L)).thenReturn(activeOneOnOnePackage());
        // 调课写入现在会校验受影响行数，默认让 updateById 返回成功，避免误报并发冲突。
        lenient().when(lessonOccurrenceMapper.updateById(any(LessonOccurrenceEntity.class))).thenReturn(1);
    }

    @Test
    void shouldRejectAdjustmentWhileOneOnOnePackageIsPaused() {
        CoursePackageEntity pausedPackage = new CoursePackageEntity();
        pausedPackage.setId(101L);
        pausedPackage.setTeachingMode("ONE_ON_ONE");
        pausedPackage.setPackageStatus("PAUSED");
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildSourceOccurrence());
        when(coursePackageMapper.selectById(501L)).thenReturn(pausedPackage);

        ScheduleAdjustmentRequest request = new ScheduleAdjustmentRequest();
        request.setOccurrenceId(1001L);
        request.setAdjustmentType("TIME_ONLY");
        request.setPlannedStartAt(LocalDateTime.of(2026, 4, 20, 18, 0));
        request.setPlannedEndAt(LocalDateTime.of(2026, 4, 20, 20, 0));
        request.setPlannedUnits(4);

        assertThatThrownBy(() -> scheduleAdjustmentService.adjust(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("paused or inactive");
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).updateById(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldCreateMakeupOccurrenceAndAdjustmentLog() {
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildSourceOccurrence());
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");
        doAnswer(invocation -> {
            LessonOccurrenceEntity entity = invocation.getArgument(0);
            entity.setId(2002L);
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));
        doAnswer(invocation -> {
            LessonAdjustmentLogEntity entity = invocation.getArgument(0);
            entity.setId(3003L);
            return 1;
        }).when(lessonAdjustmentLogMapper).insert(any(LessonAdjustmentLogEntity.class));

        ScheduleAdjustmentRequest request = new ScheduleAdjustmentRequest();
        request.setOccurrenceId(1001L);
        request.setAdjustmentType("MAKEUP_CREATE");
        request.setPlannedTeacherId(3001L);
        request.setPlannedClassroomId(4001L);
        request.setPlannedStartAt(LocalDateTime.of(2026, 4, 20, 18, 0));
        request.setPlannedEndAt(LocalDateTime.of(2026, 4, 20, 20, 0));
        request.setPlannedUnits(4);
        request.setAdjustedBy(999L);
        request.setReason("makeup lesson");

        ScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjust(request);

        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        verify(lessonAdjustmentLogMapper).insert(logCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getOccurrenceType()).isEqualTo("MAKEUP");
        assertThat(occurrenceCaptor.getValue().getSourceOccurrenceId()).isEqualTo(1001L);
        assertThat(logCaptor.getValue().getAdjustmentType()).isEqualTo("MAKEUP_CREATE");
        assertThat(result.adjustmentLogId()).isEqualTo(3003L);
        assertThat(result.createdOccurrenceId()).isEqualTo(2002L);
    }

    @Test
    void shouldAssignTeacherWithoutRequiringClassroom() {
        LessonOccurrenceEntity source = buildSourceOccurrence();
        source.setPlannedTeacherId(null);
        source.setPlannedClassroomId(null);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(source);
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");
        doAnswer(invocation -> {
            LessonAdjustmentLogEntity entity = invocation.getArgument(0);
            entity.setId(3003L);
            return 1;
        }).when(lessonAdjustmentLogMapper).insert(any(LessonAdjustmentLogEntity.class));

        ScheduleAdjustmentRequest request = new ScheduleAdjustmentRequest();
        request.setOccurrenceId(1001L);
        request.setAdjustmentType("ASSIGN_RESOURCES");
        request.setPlannedTeacherId(3001L);
        request.setReason("Assign before class");

        scheduleAdjustmentService.adjust(request);

        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getPlannedTeacherId()).isEqualTo(3001L);
        assertThat(occurrenceCaptor.getValue().getPlannedClassroomId()).isNull();
        assertThat(occurrenceCaptor.getValue().getPlanningStatus()).isEqualTo("ADJUSTED");
    }

    @Test
    void teacherCanRescheduleOwnUpcomingLessonAndIsRecordedAsOperator() {
        LocalDateTime originalStartAt = AppTime.nowLocalDateTime().plusDays(2).withSecond(0).withNano(0);
        LessonOccurrenceEntity source = buildSourceOccurrence();
        source.setPlannedStartAt(originalStartAt);
        source.setPlannedEndAt(originalStartAt.plusHours(2));
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(source);
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        ScheduleAdjustmentRequest request = new ScheduleAdjustmentRequest();
        request.setOccurrenceId(1001L);
        request.setAdjustmentType("RESCHEDULE");
        request.setPlannedStartAt(originalStartAt.plusDays(1));
        request.setPlannedEndAt(originalStartAt.plusDays(1).plusHours(2));
        request.setPlannedUnits(4);
        request.setAdjustedBy(999L);
        CurrentUserResponse teacher = new CurrentUserResponse(123L, "teacher", "Teacher", "TEACHER", 3001L, null, List.of("TEACHER"));

        scheduleAdjustmentService.adjustByTeacher(request, teacher);

        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        verify(lessonAdjustmentLogMapper).insert(logCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getPlannedStartAt()).isEqualTo(request.getPlannedStartAt());
        assertThat(logCaptor.getValue().getAdjustedBy()).isEqualTo(123L);
    }

    @Test
    void teacherCanRescheduleOwnPastUnconfirmedLesson() {
        LessonOccurrenceEntity source = buildSourceOccurrence();
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(source);
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        ScheduleAdjustmentRequest request = new ScheduleAdjustmentRequest();
        request.setOccurrenceId(1001L);
        request.setAdjustmentType("RESCHEDULE");
        request.setPlannedStartAt(LocalDateTime.of(2026, 4, 14, 18, 0));
        request.setPlannedEndAt(LocalDateTime.of(2026, 4, 14, 20, 0));
        request.setPlannedUnits(4);
        CurrentUserResponse teacher = new CurrentUserResponse(123L, "teacher", "Teacher", "TEACHER", 3001L, null, List.of("TEACHER"));

        scheduleAdjustmentService.adjustByTeacher(request, teacher);

        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getPlannedStartAt()).isEqualTo(request.getPlannedStartAt());
    }

    @Test
    void teacherCannotRescheduleAnotherTeachersLesson() {
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildSourceOccurrence());
        ScheduleAdjustmentRequest request = new ScheduleAdjustmentRequest();
        request.setOccurrenceId(1001L);
        request.setAdjustmentType("RESCHEDULE");
        CurrentUserResponse teacher = new CurrentUserResponse(123L, "teacher", "Teacher", "TEACHER", 3999L, null, List.of("TEACHER"));

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustByTeacher(request, teacher))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assigned to you");
    }

    @Test
    void shouldKeepClassGroupWhenAdjustingClassOccurrence() {
        LessonOccurrenceEntity source = buildSourceOccurrence();
        source.setPackageId(null);
        source.setStudentId(null);
        source.setClassGroupId(7001L);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(source);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");
        doAnswer(invocation -> {
            LessonAdjustmentLogEntity entity = invocation.getArgument(0);
            entity.setId(3003L);
            return 1;
        }).when(lessonAdjustmentLogMapper).insert(any(LessonAdjustmentLogEntity.class));

        ScheduleAdjustmentRequest request = new ScheduleAdjustmentRequest();
        request.setOccurrenceId(1001L);
        request.setAdjustmentType("RESCHEDULE");
        request.setPlannedTeacherId(3001L);
        request.setPlannedClassroomId(4001L);
        request.setPlannedStartAt(LocalDateTime.of(2026, 4, 14, 18, 0));
        request.setPlannedEndAt(LocalDateTime.of(2026, 4, 14, 20, 0));
        request.setPlannedUnits(4);
        request.setReason("class schedule change");

        scheduleAdjustmentService.adjust(request);

        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getClassGroupId()).isEqualTo(7001L);
        assertThat(occurrenceCaptor.getValue().getPackageId()).isNull();
        assertThat(occurrenceCaptor.getValue().getStudentId()).isNull();
    }

    @Test
    void shouldNotRebalancePackageWhenClassOccurrenceDurationChanges() {
        LessonOccurrenceEntity source = buildSourceOccurrence();
        source.setPackageId(null);
        source.setStudentId(null);
        source.setClassGroupId(7001L);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(source);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of());
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");
        doAnswer(invocation -> {
            LessonAdjustmentLogEntity entity = invocation.getArgument(0);
            entity.setId(3003L);
            return 1;
        }).when(lessonAdjustmentLogMapper).insert(any(LessonAdjustmentLogEntity.class));

        ScheduleAdjustmentRequest request = new ScheduleAdjustmentRequest();
        request.setOccurrenceId(1001L);
        request.setAdjustmentType("RESCHEDULE");
        request.setPlannedTeacherId(3001L);
        request.setPlannedClassroomId(4001L);
        request.setPlannedStartAt(LocalDateTime.of(2026, 4, 14, 18, 0));
        request.setPlannedEndAt(LocalDateTime.of(2026, 4, 14, 20, 30));
        request.setPlannedUnits(5);
        request.setReason("class duration change");

        scheduleAdjustmentService.adjust(request);

        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getClassGroupId()).isEqualTo(7001L);
        assertThat(occurrenceCaptor.getValue().getPackageId()).isNull();
        assertThat(occurrenceCaptor.getValue().getPlannedUnits()).isEqualTo(5);
        assertThat(occurrenceCaptor.getValue().getPlannedEndAt())
                .isEqualTo(LocalDateTime.of(2026, 4, 14, 20, 30));
    }

    @Test
    void shouldAdjustWeeklyOccurrenceAndReturnPositiveDelta() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime firstStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime secondStartAt = weekStartDate.plusDays(2).atTime(18, 0);
        LocalDateTime futureStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LessonOccurrenceEntity first = buildWeeklyOccurrence(1001L, firstStartAt, 4);
        LessonOccurrenceEntity second = buildWeeklyOccurrence(1002L, secondStartAt, 4);
        LessonOccurrenceEntity future = buildWeeklyOccurrence(2001L, futureStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(first));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(first, second), List.of(future));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, firstStartAt, 6))
        );

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        verify(lessonAdjustmentLogMapper).insert(logCaptor.capture());
        LessonOccurrenceEntity adjustedCurrent = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 1001L))
                .findFirst()
                .orElseThrow();
        LessonOccurrenceEntity balancedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        assertThat(adjustedCurrent.getPlannedUnits()).isEqualTo(6);
        assertThat(adjustedCurrent.getPlanningStatus()).isEqualTo("ADJUSTED");
        assertThat(balancedFuture.getPlannedUnits()).isEqualTo(2);
        assertThat(balancedFuture.getPlannedEndAt()).isEqualTo(futureStartAt.plusHours(1));
        assertThat(logCaptor.getValue().getAdjustmentType()).isEqualTo("WEEKLY_TEMP_UPDATE");
        assertThat(result.originalWeeklyUnits()).isEqualTo(8);
        assertThat(result.adjustedWeeklyUnits()).isEqualTo(10);
        assertThat(result.unitDelta()).isEqualTo(2);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.adjustedOccurrences()).hasSize(1);
    }

    @Test
    void shouldReturnNegativeWeeklyDeltaWhenUnitsDecrease() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime firstStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime secondStartAt = weekStartDate.plusDays(2).atTime(18, 0);
        LocalDateTime futureStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LessonOccurrenceEntity first = buildWeeklyOccurrence(1001L, firstStartAt, 4);
        LessonOccurrenceEntity second = buildWeeklyOccurrence(1002L, secondStartAt, 4);
        LessonOccurrenceEntity future = buildWeeklyOccurrence(2001L, futureStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(first));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(first, second), List.of(future));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, firstStartAt, 2))
        );

        assertThat(result.originalWeeklyUnits()).isEqualTo(8);
        assertThat(result.adjustedWeeklyUnits()).isEqualTo(6);
        assertThat(result.unitDelta()).isEqualTo(-2);
        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonOccurrenceEntity balancedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        assertThat(balancedFuture.getPlannedUnits()).isEqualTo(6);
        assertThat(balancedFuture.getPlannedEndAt()).isEqualTo(futureStartAt.plusHours(3));
    }

    @Test
    void shouldIncreaseEarlierFutureOccurrenceWhenTailWouldConflictAfterWeeklyUnitsDecrease() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime firstStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime secondStartAt = weekStartDate.plusDays(2).atTime(18, 0);
        LocalDateTime availableStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LocalDateTime conflictTailStartAt = weekStartDate.plusDays(15).atTime(18, 0);
        LessonOccurrenceEntity first = buildWeeklyOccurrence(1001L, firstStartAt, 4);
        LessonOccurrenceEntity second = buildWeeklyOccurrence(1002L, secondStartAt, 4);
        LessonOccurrenceEntity availableFuture = buildWeeklyOccurrence(2001L, availableStartAt, 4);
        LessonOccurrenceEntity conflictTail = buildWeeklyOccurrence(2002L, conflictTailStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(first));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(first, second), List.of(availableFuture, conflictTail));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 1L, 0L, 0L, 0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, firstStartAt, 2))
        );

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonOccurrenceEntity balancedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        boolean conflictTailTouched = occurrenceCaptor.getAllValues().stream()
                .anyMatch(item -> Objects.equals(item.getId(), 2002L));
        assertThat(result.unitDelta()).isEqualTo(-2);
        assertThat(balancedFuture.getPlannedUnits()).isEqualTo(6);
        assertThat(balancedFuture.getPlannedEndAt()).isEqualTo(availableStartAt.plusHours(3));
        assertThat(conflictTailTouched).isFalse();
    }

    @Test
    void shouldCancelTailFutureAndShortenPreviousWhenWeeklyUsesMuchMoreUnits() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime currentStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime futureStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LocalDateTime tailStartAt = weekStartDate.plusDays(15).atTime(18, 0);
        LessonOccurrenceEntity current = buildWeeklyOccurrence(1001L, currentStartAt, 4);
        LessonOccurrenceEntity future = buildWeeklyOccurrence(2001L, futureStartAt, 4);
        LessonOccurrenceEntity tail = buildWeeklyOccurrence(2002L, tailStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(current));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(current), List.of(future, tail));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, currentStartAt, 10))
        );

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonOccurrenceEntity shortenedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        LessonOccurrenceEntity cancelledTail = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2002L))
                .findFirst()
                .orElseThrow();
        assertThat(result.unitDelta()).isEqualTo(6);
        assertThat(shortenedFuture.getPlannedUnits()).isEqualTo(2);
        assertThat(shortenedFuture.getPlannedEndAt()).isEqualTo(futureStartAt.plusHours(1));
        assertThat(cancelledTail.getPlanningStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldSkipFutureOccurrenceWithTeacherConfirmedRecordWhenBalancingWeeklyChange() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime currentStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime adjustableStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LocalDateTime recordedStartAt = weekStartDate.plusDays(15).atTime(18, 0);
        LessonOccurrenceEntity current = buildWeeklyOccurrence(1001L, currentStartAt, 4);
        LessonOccurrenceEntity adjustableFuture = buildWeeklyOccurrence(2001L, adjustableStartAt, 4);
        LessonOccurrenceEntity recordedFuture = buildWeeklyOccurrence(2002L, recordedStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(current));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(current), List.of(adjustableFuture, recordedFuture));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(buildPendingRecord(2002L)));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, currentStartAt, 6))
        );

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        LessonOccurrenceEntity shortenedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        boolean recordedFutureTouched = occurrenceCaptor.getAllValues().stream()
                .anyMatch(item -> Objects.equals(item.getId(), 2002L));
        assertThat(result.unitDelta()).isEqualTo(2);
        assertThat(shortenedFuture.getPlannedUnits()).isEqualTo(2);
        assertThat(recordedFutureTouched).isFalse();
    }

    @Test
    void shouldRejectWeeklyHoursChangeWhenNoFutureOccurrenceCanBalancePackage() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime currentStartAt = weekStartDate.atTime(18, 0);
        LessonOccurrenceEntity current = buildWeeklyOccurrence(1001L, currentStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(current));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(current), List.of());
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, currentStartAt, 6))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("FUTURE_SCHEDULE_NOT_ENOUGH");
    }

    @Test
    void shouldRejectWeeklyAdjustmentOutsideRequestedWeek() {
        LocalDateTime nextWeekStartAt = currentWeekStartDate().plusDays(7).atTime(18, 0);
        LessonOccurrenceEntity occurrence = buildWeeklyOccurrence(1001L, nextWeekStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(occurrence));

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, nextWeekStartAt, 4))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("OCCURRENCE_OUT_OF_WEEK");
    }

    @Test
    void shouldRejectWeeklyAdjustmentWhenRequestedWeekIsNotCurrentWeek() {
        LocalDateTime lastWeekStartAt = currentWeekStartDate().minusDays(7).atTime(18, 0);
        WeeklyScheduleAdjustmentRequest request = buildWeeklyRequest(weeklyItem(1001L, lastWeekStartAt, 4));
        request.setWeekStartDate(currentWeekStartDate().minusDays(7));

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WEEKLY_ADJUSTMENT_NOT_CURRENT_WEEK");
    }

    @Test
    void shouldRejectCancelledWeeklyOccurrence() {
        LocalDateTime startAt = currentWeekStartDate().atTime(18, 0);
        LessonOccurrenceEntity occurrence = buildWeeklyOccurrence(1001L, startAt, 4);
        occurrence.setPlanningStatus("CANCELLED");
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(occurrence));

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, startAt, 4))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LESSON_OCCURRENCE_CANCELLED");
    }

    @Test
    void shouldRejectRedeemedWeeklyOccurrence() {
        LocalDateTime startAt = currentWeekStartDate().atTime(18, 0);
        LessonOccurrenceEntity occurrence = buildWeeklyOccurrence(1001L, startAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(occurrence));
        when(lessonRecordMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(weeklyItem(1001L, startAt, 4))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LESSON_OCCURRENCE_REDEEMED");
    }

    @Test
    void shouldRejectInternalConflictInWeeklyAdjustment() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime firstStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime secondStartAt = weekStartDate.plusDays(2).atTime(18, 0);
        LessonOccurrenceEntity first = buildWeeklyOccurrence(1001L, firstStartAt, 4);
        LessonOccurrenceEntity second = buildWeeklyOccurrence(1002L, secondStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(first, second));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(first, second));
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(buildWeeklyRequest(
                weeklyItem(1001L, firstStartAt, 4),
                weeklyItem(1002L, firstStartAt.plusHours(1), 4)
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SCHEDULE_CONFLICT");
    }

    @Test
    void shouldDeserializeWeeklyPayloadWithItemsContract() throws Exception {
        LocalDate weekStartDate = currentWeekStartDate();
        String payload = """
                {
                  "packageId": 501,
                  "weekStartDate": "%s",
                  "reason": "temporary weekly adjustment",
                  "items": [
                    {
                      "occurrenceId": 1001,
                      "plannedTeacherId": 3001,
                      "plannedClassroomId": 4001,
                      "plannedStartAt": "%s",
                      "plannedEndAt": "%s",
                      "plannedUnits": 4
                    }
                  ]
                }
                """.formatted(weekStartDate, weekStartDate.atTime(18, 0), weekStartDate.atTime(20, 0));

        WeeklyScheduleAdjustmentRequest request = new ObjectMapper()
                .findAndRegisterModules()
                .readValue(payload, WeeklyScheduleAdjustmentRequest.class);

        assertThat(request.getItems()).hasSize(1);
        assertThat(request.getItems().get(0).getOccurrenceId()).isEqualTo(1001L);
    }

    @Test
    void shouldCreateWeeklyOccurrenceAndReturnPositiveDelta() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime existingStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime createStartAt = weekStartDate.plusDays(3).atTime(18, 0);
        LocalDateTime futureStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LessonOccurrenceEntity existing = buildWeeklyOccurrence(1001L, existingStartAt, 4);
        LessonOccurrenceEntity future = buildWeeklyOccurrence(2001L, futureStartAt, 4);
        // 纯新增场景：无源课次，学生从课包既有课次推导（resolveStudentId 与周课次查询都走 selectList）。
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(existing), List.of(existing), List.of(future));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");
        doAnswer(invocation -> {
            LessonOccurrenceEntity entity = invocation.getArgument(0);
            entity.setId(2002L);
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(createItem(createStartAt, 4))
        );

        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        verify(lessonAdjustmentLogMapper).insert(logCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getOccurrenceType()).isEqualTo("ADHOC");
        assertThat(occurrenceCaptor.getValue().getPlanningStatus()).isEqualTo("SCHEDULED");
        assertThat(occurrenceCaptor.getValue().getStudentId()).isEqualTo(601L);
        assertThat(logCaptor.getValue().getAdjustmentType()).isEqualTo("WEEKLY_TEMP_CREATE");
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.originalWeeklyUnits()).isEqualTo(4);
        assertThat(result.adjustedWeeklyUnits()).isEqualTo(8);
        assertThat(result.unitDelta()).isEqualTo(4);
    }

    @Test
    void shouldDeleteWeeklyOccurrenceAndReturnNegativeDelta() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime firstStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime secondStartAt = weekStartDate.plusDays(2).atTime(18, 0);
        LocalDateTime futureStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LessonOccurrenceEntity toDelete = buildWeeklyOccurrence(1001L, firstStartAt, 4);
        LessonOccurrenceEntity other = buildWeeklyOccurrence(1002L, secondStartAt, 4);
        LessonOccurrenceEntity future = buildWeeklyOccurrence(2001L, futureStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(toDelete));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(toDelete, other), List.of(future));
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(deleteItem(1001L))
        );

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).updateById(occurrenceCaptor.capture());
        verify(lessonAdjustmentLogMapper).insert(logCaptor.capture());
        LessonOccurrenceEntity cancelledCurrent = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 1001L))
                .findFirst()
                .orElseThrow();
        LessonOccurrenceEntity balancedFuture = occurrenceCaptor.getAllValues().stream()
                .filter(item -> Objects.equals(item.getId(), 2001L))
                .findFirst()
                .orElseThrow();
        assertThat(cancelledCurrent.getPlanningStatus()).isEqualTo("CANCELLED");
        assertThat(balancedFuture.getPlannedUnits()).isEqualTo(8);
        assertThat(balancedFuture.getPlannedEndAt()).isEqualTo(futureStartAt.plusHours(4));
        assertThat(logCaptor.getValue().getAdjustmentType()).isEqualTo("WEEKLY_TEMP_DELETE");
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.originalWeeklyUnits()).isEqualTo(8);
        assertThat(result.adjustedWeeklyUnits()).isEqualTo(4);
        assertThat(result.unitDelta()).isEqualTo(-4);
        assertThat(result.adjustedOccurrences()).isEmpty();
    }

    @Test
    void shouldApplyMixedUpdateCreateAndDeleteInOneRequest() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime updateStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime deleteStartAt = weekStartDate.plusDays(2).atTime(18, 0);
        LocalDateTime createStartAt = weekStartDate.plusDays(4).atTime(18, 0);
        LocalDateTime futureStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LessonOccurrenceEntity toUpdate = buildWeeklyOccurrence(1001L, updateStartAt, 4);
        LessonOccurrenceEntity toDelete = buildWeeklyOccurrence(1002L, deleteStartAt, 4);
        LessonOccurrenceEntity future = buildWeeklyOccurrence(2001L, futureStartAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(toUpdate, toDelete));
        when(lessonRecordMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(toUpdate, toDelete), List.of(future));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");
        doAnswer(invocation -> {
            LessonOccurrenceEntity entity = invocation.getArgument(0);
            entity.setId(2002L);
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(buildWeeklyRequest(
                weeklyItem(1001L, updateStartAt, 6),
                deleteItem(1002L),
                createItem(createStartAt, 4)
        ));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.originalWeeklyUnits()).isEqualTo(8);
        assertThat(result.adjustedWeeklyUnits()).isEqualTo(10);
        assertThat(result.unitDelta()).isEqualTo(2);
        assertThat(result.adjustedOccurrences()).hasSize(2);
    }

    @Test
    void shouldAllowCreateWithoutItemReason() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime existingStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime createStartAt = weekStartDate.plusDays(3).atTime(18, 0);
        LocalDateTime futureStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LessonOccurrenceEntity existing = buildWeeklyOccurrence(1001L, existingStartAt, 4);
        LessonOccurrenceEntity future = buildWeeklyOccurrence(2001L, futureStartAt, 4);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(existing), List.of(existing), List.of(future));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");
        doAnswer(invocation -> {
            LessonOccurrenceEntity entity = invocation.getArgument(0);
            entity.setId(2002L);
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));

        WeeklyScheduleAdjustmentRequest.Item item = createItem(createStartAt, 4);
        item.setItemReason(null);

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(buildWeeklyRequest(item));

        assertThat(result.createdCount()).isEqualTo(1);
    }

    @Test
    void shouldCreateOnlineWeeklyAdjustmentWithoutClassroom() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime existingStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime createStartAt = weekStartDate.plusDays(3).atTime(18, 0);
        LocalDateTime futureStartAt = weekStartDate.plusDays(8).atTime(18, 0);
        LessonOccurrenceEntity existing = buildWeeklyOccurrence(1001L, existingStartAt, 4);
        LessonOccurrenceEntity future = buildWeeklyOccurrence(2001L, futureStartAt, 4);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(existing), List.of(existing), List.of(future));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(jsonHelper.toJson(any())).thenReturn("{\"ok\":true}");
        doAnswer(invocation -> {
            LessonOccurrenceEntity entity = invocation.getArgument(0);
            entity.setId(2002L);
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));

        WeeklyScheduleAdjustmentRequest.Item onlineItem = createItem(createStartAt, 4);
        onlineItem.setPlannedClassroomId(null);
        onlineItem.setDeliveryMode("ONLINE");

        WeeklyScheduleAdjustmentResultVO result = scheduleAdjustmentService.adjustWeekly(buildWeeklyRequest(onlineItem));

        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(occurrenceCaptor.getValue().getDeliveryMode()).isEqualTo("ONLINE");
        assertThat(occurrenceCaptor.getValue().getPlannedClassroomId()).isNull();
    }

    @Test
    void shouldRejectCreateWhenConflictDetected() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime existingStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime createStartAt = weekStartDate.plusDays(3).atTime(18, 0);
        LessonOccurrenceEntity existing = buildWeeklyOccurrence(1001L, existingStartAt, 4);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(existing));
        when(lessonOccurrenceMapper.selectCount(any())).thenReturn(1L);
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(createItem(createStartAt, 4))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SCHEDULE_CONFLICT");
    }

    @Test
    void shouldRejectDeleteOfRedeemedOccurrence() {
        LocalDateTime startAt = currentWeekStartDate().atTime(18, 0);
        LessonOccurrenceEntity occurrence = buildWeeklyOccurrence(1001L, startAt, 4);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(occurrence));
        when(lessonRecordMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(deleteItem(1001L))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LESSON_OCCURRENCE_REDEEMED");
    }

    @Test
    void shouldRejectCreateOutsideRequestedWeek() {
        LocalDate weekStartDate = currentWeekStartDate();
        LocalDateTime existingStartAt = weekStartDate.atTime(18, 0);
        LocalDateTime outsideStartAt = weekStartDate.plusDays(7).atTime(18, 0);
        LessonOccurrenceEntity existing = buildWeeklyOccurrence(1001L, existingStartAt, 4);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> scheduleAdjustmentService.adjustWeekly(
                buildWeeklyRequest(createItem(outsideStartAt, 4))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ADJUSTED_OCCURRENCE_OUT_OF_WEEK");
    }

    private LessonOccurrenceEntity buildSourceOccurrence() {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(1001L);
        occurrence.setLessonNo("LS-1001");
        occurrence.setPackageId(501L);
        occurrence.setStudentId(601L);
        occurrence.setScheduleRuleId(701L);
        occurrence.setScheduleSlotId(801L);
        occurrence.setOccurrenceType("REGULAR");
        occurrence.setPlannedTeacherId(3001L);
        occurrence.setPlannedClassroomId(4001L);
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 4, 13, 18, 0));
        occurrence.setPlannedEndAt(LocalDateTime.of(2026, 4, 13, 20, 0));
        occurrence.setPlannedUnits(4);
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private LessonOccurrenceEntity buildWeeklyOccurrence(Long id, LocalDateTime startAt, Integer plannedUnits) {
        LessonOccurrenceEntity occurrence = buildSourceOccurrence();
        occurrence.setId(id);
        occurrence.setLessonNo("LS-" + id);
        occurrence.setPlannedStartAt(startAt);
        occurrence.setPlannedEndAt(startAt.plusMinutes(plannedUnits * 30L));
        occurrence.setPlannedUnits(plannedUnits);
        return occurrence;
    }

    private LessonRecordEntity buildPendingRecord(Long occurrenceId) {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setId(9000L + occurrenceId);
        record.setOccurrenceId(occurrenceId);
        record.setConfirmStage("PENDING_ADMIN");
        record.setActualUnits(2);
        record.setDeductedUnits(2);
        record.setShouldDeduct(1);
        return record;
    }

    private WeeklyScheduleAdjustmentRequest buildWeeklyRequest(WeeklyScheduleAdjustmentRequest.Item... items) {
        WeeklyScheduleAdjustmentRequest request = new WeeklyScheduleAdjustmentRequest();
        request.setPackageId(501L);
        request.setWeekStartDate(currentWeekStartDate());
        request.setItems(List.of(items));
        request.setAdjustedBy(999L);
        request.setReason("temporary weekly adjustment");
        return request;
    }

    private LocalDate currentWeekStartDate() {
        return AppTime.nowLocalDateTime()
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    }

    private WeeklyScheduleAdjustmentRequest.Item weeklyItem(Long occurrenceId, LocalDateTime startAt, Integer plannedUnits) {
        WeeklyScheduleAdjustmentRequest.Item item = new WeeklyScheduleAdjustmentRequest.Item();
        item.setOccurrenceId(occurrenceId);
        item.setPlannedTeacherId(3001L);
        item.setPlannedClassroomId(4001L);
        item.setPlannedStartAt(startAt);
        item.setPlannedEndAt(startAt.plusMinutes(plannedUnits * 30L));
        item.setPlannedUnits(plannedUnits);
        return item;
    }

    private WeeklyScheduleAdjustmentRequest.Item createItem(LocalDateTime startAt, Integer plannedUnits) {
        WeeklyScheduleAdjustmentRequest.Item item = new WeeklyScheduleAdjustmentRequest.Item();
        item.setAction("CREATE");
        item.setPlannedTeacherId(3001L);
        item.setPlannedClassroomId(4001L);
        item.setPlannedStartAt(startAt);
        item.setPlannedEndAt(startAt.plusMinutes(plannedUnits * 30L));
        item.setPlannedUnits(plannedUnits);
        item.setItemReason("temporary extra lesson");
        return item;
    }

    private WeeklyScheduleAdjustmentRequest.Item deleteItem(Long occurrenceId) {
        WeeklyScheduleAdjustmentRequest.Item item = new WeeklyScheduleAdjustmentRequest.Item();
        item.setAction("DELETE");
        item.setOccurrenceId(occurrenceId);
        item.setItemReason("cancel this lesson");
        return item;
    }

    private TeacherEntity buildTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(3001L);
        teacher.setStatus("ACTIVE");
        return teacher;
    }

    private ClassroomEntity buildClassroom() {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(4001L);
        classroom.setStatus("ACTIVE");
        return classroom;
    }

    private CoursePackageEntity activeOneOnOnePackage() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(501L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setPackageStatus("ACTIVE");
        return coursePackage;
    }
}
