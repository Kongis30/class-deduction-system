package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.impl.ClassGroupServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 班课确认测试：确保老师初确认后的管理员终确认会持久化每个学生的确认阶段。
 */
@ExtendWith(MockitoExtension.class)
class ClassGroupServiceImplTest {

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;

    @Mock
    private ClassHolidayService classHolidayService;

    @Captor
    private ArgumentCaptor<LessonRecordEntity> recordCaptor;

    @Captor
    private ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor;

    private ClassGroupServiceImpl classGroupService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonOccurrenceEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonRecordEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassGroupEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassEnrollmentEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassGroupScheduleSlotEntity.class);
    }

    @BeforeEach
    void setUp() {
        classGroupService = new ClassGroupServiceImpl(
                classGroupMapper,
                classEnrollmentMapper,
                lessonOccurrenceMapper,
                lessonRecordMapper,
                classGroupScheduleSlotMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                courseProductMapper,
                coursePackageMapper,
                privateLessonGroupMemberMapper,
                classHolidayService
        );
    }

    @Test
    void shouldReturnSessionBalanceForClassStudentList() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));
        enrollment.setBillingType("PER_SESSION");
        enrollment.setPurchasedQuantity(4);
        StudentEntity student = new StudentEntity();
        student.setId(601L);
        student.setStudentName("Alice Chen");

        LessonOccurrenceEntity usedOccurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 5, 10, 0));
        usedOccurrence.setId(9001L);
        LessonRecordEntity usedRecord = new LessonRecordEntity();
        usedRecord.setOccurrenceId(9001L);
        usedRecord.setStudentId(601L);
        usedRecord.setConfirmStage("CONFIRMED");
        usedRecord.setResultStatus("COMPLETED");

        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(studentMapper.selectBatchIds(List.of(601L))).thenReturn(List.of(student));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(usedOccurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(usedRecord));

        List<Map<String, Object>> result = classGroupService.listEnrolledStudents(501L, adminUser());

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("studentName", "Alice Chen")
                .containsEntry("billingType", "PER_SESSION")
                .containsEntry("purchasedQuantity", 4)
                .containsEntry("usedSessions", 1L)
                .containsEntry("remainingSessions", 3);
    }

    @Test
    void shouldHideWithdrawnStudentsFromScheduleEnrollmentList() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        ClassEnrollmentEntity withdrawn = activeEnrollment(601L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));
        withdrawn.setStatus("WITHDRAWN");

        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(withdrawn));

        List<Map<String, Object>> result = classGroupService.listEnrolledStudents(501L, adminUser());

        assertThat(result).isEmpty();
        verify(studentMapper, never()).selectBatchIds(any());
    }

    @Test
    void shouldRetireClassWithoutDeletingStudentOrHistoricalData() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);

        classGroupService.deleteClassGroup(501L);

        verify(lessonOccurrenceMapper).update(any(LessonOccurrenceEntity.class), any());
        verify(classEnrollmentMapper).update(any(ClassEnrollmentEntity.class), any());
        verify(classGroupScheduleSlotMapper).delete(any());
        verify(classGroupMapper).deleteById(501L);
    }

    @Test
    void shouldCountMultipleConfirmedSlotsOnSameDayAsOneSession() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));
        enrollment.setBillingType("PER_SESSION");
        enrollment.setPurchasedQuantity(4);
        StudentEntity student = new StudentEntity();
        student.setId(601L);
        student.setStudentName("Alice Chen");

        LessonOccurrenceEntity morning = scheduledOccurrence(LocalDateTime.of(2026, 4, 5, 9, 0));
        morning.setId(9001L);
        LessonOccurrenceEntity afternoon = scheduledOccurrence(LocalDateTime.of(2026, 4, 5, 13, 0));
        afternoon.setId(9002L);
        LessonRecordEntity morningRecord = confirmedClassRecord(9001L, 601L);
        LessonRecordEntity afternoonRecord = confirmedClassRecord(9002L, 601L);

        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(studentMapper.selectBatchIds(List.of(601L))).thenReturn(List.of(student));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(morning, afternoon));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(morningRecord, afternoonRecord));

        List<Map<String, Object>> result = classGroupService.listEnrolledStudents(501L, adminUser());

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("usedSessions", 1L)
                .containsEntry("remainingSessions", 3);
    }

    @Test
    void shouldPersistConfirmedStageForAllPendingClassRecordsWhenAdminFinalConfirms() {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(1001L);
        occurrence.setClassGroupId(501L);
        occurrence.setPlannedTeacherId(701L);
        occurrence.setPlannedClassroomId(801L);
        occurrence.setPlannedStartAt(LocalDateTime.now().minusHours(1));
        occurrence.setPlannedEndAt(LocalDateTime.now());
        occurrence.setPlannedUnits(2);
        occurrence.setPlanningStatus("SCHEDULED");

        ClassEnrollmentEntity firstEnrollment = new ClassEnrollmentEntity();
        firstEnrollment.setStudentId(601L);
        ClassEnrollmentEntity secondEnrollment = new ClassEnrollmentEntity();
        secondEnrollment.setStudentId(602L);

        LessonRecordEntity firstPending = pendingClassRecord(2001L, 601L);
        LessonRecordEntity secondPending = pendingClassRecord(2002L, 602L);

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(firstEnrollment, secondEnrollment));
        when(lessonRecordMapper.selectOne(any())).thenReturn(firstPending, secondPending);

        Map<String, Object> result = classGroupService.confirmLesson(501L, 1001L, Map.of(
                "actualTeacherId", 702L,
                "actualClassroomId", 802L,
                "actualStartAt", LocalDateTime.of(2026, 4, 12, 10, 0).toString(),
                "actualEndAt", LocalDateTime.of(2026, 4, 12, 11, 30).toString(),
                "actualUnits", 3,
                "notes", "Admin adjusted actual class details."
        ), adminUser());

        verify(lessonRecordMapper, times(2)).updateById(recordCaptor.capture());
        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(recordCaptor.getAllValues())
                .extracting(LessonRecordEntity::getConfirmStage)
                .containsExactly("CONFIRMED", "CONFIRMED");
        assertThat(recordCaptor.getAllValues())
                .extracting(LessonRecordEntity::getAdminConfirmedByUserId)
                .containsExactly(999L, 999L);
        assertThat(recordCaptor.getAllValues())
                .extracting(LessonRecordEntity::getActualTeacherId)
                .containsExactly(702L, 702L);
        assertThat(recordCaptor.getAllValues())
                .extracting(LessonRecordEntity::getActualClassroomId)
                .containsExactly(802L, 802L);
        assertThat(recordCaptor.getAllValues())
                .extracting(LessonRecordEntity::getActualUnits)
                .containsExactly(3, 3);
        assertThat(recordCaptor.getAllValues())
                .extracting(LessonRecordEntity::getNotes)
                .containsExactly("Admin adjusted actual class details.", "Admin adjusted actual class details.");
        assertThat(occurrenceCaptor.getValue().getPlanningStatus()).isEqualTo("MERGED");
        assertThat(occurrenceCaptor.getValue().getPlannedTeacherId()).isEqualTo(702L);
        assertThat(occurrenceCaptor.getValue().getPlannedClassroomId()).isEqualTo(802L);
        assertThat(occurrenceCaptor.getValue().getPlannedUnits()).isEqualTo(3);
        assertThat(result.get("confirmedCount")).isEqualTo(2);
        assertThat(result.get("confirmStage")).isEqualTo("CONFIRMED");
    }

    @Test
    void shouldPreserveTeacherActualValuesWhenAdminConfirmsWithoutOverrides() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 9, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));
        LessonRecordEntity pending = pendingClassRecord(2001L, 601L);
        pending.setActualTeacherId(702L);
        pending.setActualClassroomId(802L);
        pending.setActualUnits(2);
        pending.setActualStartAt(LocalDateTime.of(2026, 4, 12, 10, 0));
        pending.setActualEndAt(LocalDateTime.of(2026, 4, 12, 11, 0));
        pending.setNotes("Teacher submitted actual values.");

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(lessonRecordMapper.selectOne(any())).thenReturn(pending);

        classGroupService.confirmLesson(501L, 1001L, Map.of(), adminUser());

        verify(lessonRecordMapper).updateById(recordCaptor.capture());
        LessonRecordEntity confirmed = recordCaptor.getValue();
        assertThat(confirmed.getActualTeacherId()).isEqualTo(702L);
        assertThat(confirmed.getActualClassroomId()).isEqualTo(802L);
        assertThat(confirmed.getActualUnits()).isEqualTo(2);
        assertThat(confirmed.getActualStartAt()).isEqualTo(LocalDateTime.of(2026, 4, 12, 10, 0));
        assertThat(confirmed.getActualEndAt()).isEqualTo(LocalDateTime.of(2026, 4, 12, 11, 0));
        assertThat(confirmed.getNotes()).isEqualTo("Teacher submitted actual values.");
    }

    @Test
    void shouldApplyAdminFinalResultStatusWhenConfirmingPendingClassRecord() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 9, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));
        LessonRecordEntity pending = pendingClassRecord(2001L, 601L);
        pending.setResultStatus("COMPLETED");

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(lessonRecordMapper.selectOne(any())).thenReturn(pending);

        classGroupService.confirmLesson(501L, 1001L,
                Map.of("resultStatus", "CANCELLED"), adminUser());

        verify(lessonRecordMapper).updateById(recordCaptor.capture());
        LessonRecordEntity confirmed = recordCaptor.getValue();
        assertThat(confirmed.getResultStatus()).isEqualTo("CANCELLED");
        assertThat(confirmed.getConfirmStage()).isEqualTo("CONFIRMED");
    }

    @Test
    void shouldRejectClassConfirmationWhenActualTeacherConflicts() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(lessonOccurrenceMapper.selectCount(any())).thenAnswer(invocation ->
                invocation.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LessonOccurrenceEntity>>
                        getArgument(0).getSqlSegment().contains("plannedTeacherId") ? 1L : 0L);

        assertThatThrownBy(() -> classGroupService.confirmLesson(501L, 1001L, Map.of(
                "actualTeacherId", 702L,
                "actualStartAt", "2026-04-12T10:00:00",
                "actualEndAt", "2026-04-12T11:30:00",
                "actualUnits", 3
        ), adminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SCHEDULE_CONFLICT");

        verify(lessonRecordMapper, never()).insert(any(LessonRecordEntity.class));
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldAllowFinalConfirmationOfPreviouslyAcceptedStudentOverlap() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        Map<String, Object> result = classGroupService.confirmLesson(501L, 1001L, Map.of(), adminUser());

        assertThat(result.get("confirmStage")).isEqualTo("CONFIRMED");
        verify(lessonOccurrenceMapper, never()).selectCount(any());
        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getPlanningStatus()).isEqualTo("MERGED");
    }

    @Test
    void shouldCheckStudentConflictOnlyInTheNewlyAddedActualTime() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(lessonOccurrenceMapper.selectCount(any())).thenAnswer(invocation ->
                invocation.<LambdaQueryWrapper<LessonOccurrenceEntity>>getArgument(0)
                        .getSqlSegment().contains("studentId") ? 1L : 0L);

        assertThatThrownBy(() -> classGroupService.confirmLesson(501L, 1001L, Map.of(
                "actualStartAt", "2026-04-12T09:30:00",
                "actualEndAt", "2026-04-12T11:30:00",
                "actualUnits", 4), adminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SCHEDULE_CONFLICT");
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldSkipExpiredClassEnrollmentWhenConfirmingClassLesson() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity expired = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 11));
        ClassEnrollmentEntity eligible = activeEnrollment(602L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(expired, eligible));
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = classGroupService.confirmLesson(501L, 1001L, Map.of(), adminUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStudentId()).isEqualTo(602L);
        assertThat(recordCaptor.getValue().getConfirmStage()).isEqualTo("CONFIRMED");
        assertThat(result.get("confirmedCount")).isEqualTo(1);
    }

    @Test
    void shouldCountCancelledLessonAgainstPurchasedSessionQuantity() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity usedUp = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));
        usedUp.setBillingType("PER_SESSION");
        usedUp.setPurchasedQuantity(1);
        LessonRecordEntity confirmed = new LessonRecordEntity();
        confirmed.setOccurrenceId(9001L);
        confirmed.setStudentId(601L);
        confirmed.setConfirmStage("CONFIRMED");
        confirmed.setResultStatus("CANCELLED");
        LessonOccurrenceEntity previousOccurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 5, 10, 0));
        previousOccurrence.setId(9001L);

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(usedUp));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(previousOccurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(confirmed));

        assertThatThrownBy(() -> classGroupService.confirmLesson(501L, 1001L, Map.of(), adminUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No active eligible students in this class lesson.");
        verify(lessonRecordMapper, never()).insert(any(LessonRecordEntity.class));
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldAllowSecondSlotOnSameClassDayForPerSessionEnrollment() {
        LessonOccurrenceEntity afternoon = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 13, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));
        enrollment.setBillingType("PER_SESSION");
        enrollment.setPurchasedQuantity(1);
        LessonOccurrenceEntity morning = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 9, 0));
        morning.setId(9001L);
        LessonRecordEntity confirmedMorning = confirmedClassRecord(9001L, 601L);

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(afternoon);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(morning));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(confirmedMorning));
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = classGroupService.confirmLesson(501L, 1001L, Map.of(), adminUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStudentId()).isEqualTo(601L);
        assertThat(result.get("confirmedCount")).isEqualTo(1);
    }

    @Test
    void shouldRejectFormalLeaveAndNoShowForClassLessons() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        assertThatThrownBy(() -> classGroupService.confirmLesson(501L, 1001L,
                Map.of("resultStatus", "FORMAL_LEAVE"), adminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CLASS_RESULT_STATUS_NOT_ALLOWED");
        assertThatThrownBy(() -> classGroupService.confirmLesson(501L, 1001L,
                Map.of("resultStatus", "NO_SHOW"), adminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CLASS_RESULT_STATUS_NOT_ALLOWED");

        verify(lessonRecordMapper, never()).insert(any(LessonRecordEntity.class));
    }

    @Test
    void shouldAllowAdminToConfirmCancelledClassLesson() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);

        classGroupService.confirmLesson(501L, 1001L,
                Map.of("resultStatus", "CANCELLED"), adminUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getResultStatus()).isEqualTo("CANCELLED");
        assertThat(recordCaptor.getValue().getConfirmStage()).isEqualTo("CONFIRMED");
    }

    @Test
    void shouldIgnoreConfirmedLessonsBeforeTheCurrentEnrollmentWindow() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity renewedEnrollment = activeEnrollment(601L,
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 5, 31));
        renewedEnrollment.setBillingType("PER_SESSION");
        renewedEnrollment.setPurchasedQuantity(1);

        LessonOccurrenceEntity oldOccurrence = scheduledOccurrence(LocalDateTime.of(2026, 3, 5, 10, 0));
        oldOccurrence.setId(9001L);
        LessonRecordEntity oldConfirmed = new LessonRecordEntity();
        oldConfirmed.setOccurrenceId(9001L);
        oldConfirmed.setStudentId(601L);
        oldConfirmed.setConfirmStage("CONFIRMED");
        oldConfirmed.setResultStatus("COMPLETED");

        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(renewedEnrollment));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(oldOccurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(oldConfirmed));
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = classGroupService.confirmLesson(501L, 1001L, Map.of(), adminUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStudentId()).isEqualTo(601L);
        assertThat(result.get("confirmedCount")).isEqualTo(1);
    }

    @Test
    void shouldRejectUnsupportedClassResultStatusBeforeWritingRecords() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        assertThatThrownBy(() -> classGroupService.confirmLesson(501L, 1001L,
                Map.of("resultStatus", "UNKNOWN"), adminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CLASS_RESULT_STATUS_NOT_ALLOWED");
        verify(lessonRecordMapper, never()).insert(any(LessonRecordEntity.class));
    }

    @Test
    void shouldRequireTeacherButAllowMissingClassroomWhenConfirmingClassLesson() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        occurrence.setPlannedTeacherId(null);
        occurrence.setPlannedClassroomId(null);
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        assertThatThrownBy(() -> classGroupService.confirmLesson(501L, 1001L, Map.of(), adminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CLASS_TEACHER_REQUIRED");

        verify(lessonRecordMapper, never()).insert(any(LessonRecordEntity.class));
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldRejectClassActualHoursThatDoNotMatchTimeRange() {
        LessonOccurrenceEntity occurrence = scheduledOccurrence(LocalDateTime.of(2026, 4, 12, 10, 0));
        ClassEnrollmentEntity enrollment = activeEnrollment(601L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30));
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        assertThatThrownBy(() -> classGroupService.confirmLesson(501L, 1001L, Map.of(
                "actualStartAt", "2026-04-12T10:00:00",
                "actualEndAt", "2026-04-12T11:30:00",
                "actualUnits", 4
        ), adminUser()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ACTUAL_TIME_UNITS_MISMATCH");
        verify(lessonRecordMapper, never()).insert(any(LessonRecordEntity.class));
    }

    @Test
    void shouldRejectTeacherReadingAnotherClass() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setTeacherId(702L);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);

        assertThatThrownBy(() -> classGroupService.getClassDetail(501L, teacherUser(701L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void shouldRejectUnlinkedTeacherBeforeListingClasses() {
        CurrentUserResponse unlinkedTeacher = new CurrentUserResponse(
                1002L, "teacher", "Teacher", "TEACHER", null, null, List.of("TEACHER"));

        assertThatThrownBy(() -> classGroupService.listClasses(unlinkedTeacher))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("TEACHER_BINDING_REQUIRED");
        verify(classGroupMapper, never()).selectList(any());
    }

    @Test
    void shouldUpdateClassWeeklyScheduleAndGenerateFutureOccurrences() {
        LocalDate futureDate = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setTeacherId(701L);
        classGroup.setStatus("ACTIVE");
        classGroup.setStartDate(futureDate);
        classGroup.setEndDate(futureDate);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        TeacherEntity slotTeacher = new TeacherEntity();
        slotTeacher.setId(702L);
        slotTeacher.setStatus("ACTIVE");
        when(teacherMapper.selectById(702L)).thenReturn(slotTeacher);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(
                activeEnrollment(601L, futureDate, futureDate)));

        int weekday = futureDate.getDayOfWeek().getValue();
        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of(
                        "teacherId", 702L,
                        "weekday", weekday,
                        "startTime", "10:00",
                        "endTime", "11:30"))
        ));

        ArgumentCaptor<ClassGroupScheduleSlotEntity> slotCaptor = ArgumentCaptor.forClass(ClassGroupScheduleSlotEntity.class);
        verify(classGroupMapper).updateById(classGroup);
        verify(classGroupScheduleSlotMapper).insert(slotCaptor.capture());
        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        assertThat(classGroup.getScheduleDesc()).isEqualTo(weekdayShortLabel(weekday) + " 10:00-11:30");
        assertThat(slotCaptor.getValue().getClassGroupId()).isEqualTo(501L);
        assertThat(slotCaptor.getValue().getTeacherId()).isEqualTo(702L);
        assertThat(slotCaptor.getValue().getWeekday()).isEqualTo(weekday);
        assertThat(occurrenceCaptor.getValue().getClassGroupId()).isEqualTo(501L);
        assertThat(occurrenceCaptor.getValue().getPlannedTeacherId()).isEqualTo(702L);
        assertThat(occurrenceCaptor.getValue().getDeliveryMode()).isEqualTo("IN_PERSON");
        assertThat(occurrenceCaptor.getValue().getPlannedClassroomId()).isNull();
        assertThat(occurrenceCaptor.getValue().getPlannedUnits()).isEqualTo(3);
        assertThat(result.get("generatedOccurrences")).isEqualTo(1);
    }

    @Test
    void shouldPersistTeacherAndClassroomIndependentlyForEachWeeklySlot() {
        LocalDate futureDate = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        classGroup.setStartDate(futureDate);
        classGroup.setEndDate(futureDate);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);

        TeacherEntity firstTeacher = activeTeacher(702L);
        TeacherEntity secondTeacher = activeTeacher(703L);
        when(teacherMapper.selectById(702L)).thenReturn(firstTeacher);
        when(teacherMapper.selectById(703L)).thenReturn(secondTeacher);
        when(classroomMapper.selectById(801L)).thenReturn(activeClassroom(801L));
        when(classroomMapper.selectById(802L)).thenReturn(activeClassroom(802L));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(
                activeEnrollment(601L, futureDate, futureDate)));

        int weekday = futureDate.getDayOfWeek().getValue();
        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(
                        Map.of("teacherId", 702L, "classroomId", 801L,
                                "weekday", weekday, "startTime", "10:00", "endTime", "11:00"),
                        Map.of("teacherId", 703L, "classroomId", 802L,
                                "weekday", weekday, "startTime", "13:00", "endTime", "14:00"))
        ));

        ArgumentCaptor<ClassGroupScheduleSlotEntity> slotCaptor = ArgumentCaptor.forClass(ClassGroupScheduleSlotEntity.class);
        verify(classGroupScheduleSlotMapper, times(2)).insert(slotCaptor.capture());
        verify(lessonOccurrenceMapper, times(2)).insert(occurrenceCaptor.capture());
        assertThat(slotCaptor.getAllValues())
                .extracting(ClassGroupScheduleSlotEntity::getTeacherId)
                .containsExactly(702L, 703L);
        assertThat(slotCaptor.getAllValues())
                .extracting(ClassGroupScheduleSlotEntity::getClassroomId)
                .containsExactly(801L, 802L);
        assertThat(occurrenceCaptor.getAllValues())
                .extracting(LessonOccurrenceEntity::getPlannedTeacherId)
                .containsExactly(702L, 703L);
        assertThat(occurrenceCaptor.getAllValues())
                .extracting(LessonOccurrenceEntity::getPlannedClassroomId)
                .containsExactly(801L, 802L);
        assertThat(result.get("generatedOccurrences")).isEqualTo(2);
    }

    @Test
    void shouldNameTeacherAndExistingGroupCourseWhenClassScheduleConflicts() {
        LocalDate lessonDate = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        ClassGroupEntity editingClass = new ClassGroupEntity();
        editingClass.setId(501L);
        editingClass.setClassName("CRASH A");
        editingClass.setStatus("ACTIVE");
        editingClass.setStartDate(lessonDate);
        editingClass.setEndDate(lessonDate);
        ClassGroupEntity existingClass = new ClassGroupEntity();
        existingClass.setId(900L);
        existingClass.setProductId(300L);
        existingClass.setGradeBand("G8 SHSAT CRASH");
        existingClass.setActualClass("CRASH B");
        CourseProductEntity product = new CourseProductEntity();
        product.setProductName("2026 SHSAT");
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(702L);
        teacher.setTeacherName("Derek Wong");
        teacher.setStatus("ACTIVE");
        LessonOccurrenceEntity conflict = scheduledOccurrence(lessonDate.atTime(10, 30));
        conflict.setClassGroupId(900L);
        conflict.setPlannedTeacherId(702L);

        when(classGroupMapper.selectById(501L)).thenReturn(editingClass);
        when(classGroupMapper.selectById(900L)).thenReturn(existingClass);
        when(courseProductMapper.selectById(300L)).thenReturn(product);
        when(teacherMapper.selectById(702L)).thenReturn(teacher);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(
                List.of(), List.of(), List.of(), List.of(conflict));

        assertThatThrownBy(() -> classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of("teacherId", 702L,
                        "weekday", lessonDate.getDayOfWeek().getValue(),
                        "startTime", "10:00", "endTime", "11:30")))))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .contains("Derek Wong", "2026 SHSAT", "G8 SHSAT CRASH", "CRASH B", "CRASH A")
                        .contains(lessonDate.toString(), "10:30–12:00", "10:00–11:30"));
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldNamePrivateLessonPackageWhenTeacherConflictsWithClassSchedule() {
        LocalDate lessonDate = LocalDate.of(2026, 10, 3);
        ClassGroupEntity editingClass = new ClassGroupEntity();
        editingClass.setClassName("CRASH A");
        LessonOccurrenceEntity candidate = scheduledOccurrence(lessonDate.atTime(10, 0));
        candidate.setPlannedTeacherId(702L);
        LessonOccurrenceEntity conflict = scheduledOccurrence(lessonDate.atTime(10, 30));
        conflict.setClassGroupId(null);
        conflict.setPackageId(800L);
        conflict.setStudentId(601L);
        TeacherEntity teacher = new TeacherEntity();
        teacher.setTeacherName("Derek Wong");
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setPackageName("G1-G5 | 40 hours");
        StudentEntity student = new StudentEntity();
        student.setStudentName("Alice Chen");

        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(conflict));
        when(teacherMapper.selectById(702L)).thenReturn(teacher);
        when(coursePackageMapper.selectById(800L)).thenReturn(coursePackage);
        when(studentMapper.selectById(601L)).thenReturn(student);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(classGroupService,
                "ensureNoGeneratedClassOccurrenceConflicts", candidate, editingClass, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Derek Wong")
                .hasMessageContaining("G1-G5 | 40 hours")
                .hasMessageContaining("Alice Chen");
    }

    @Test
    void shouldKeepStartedClassLessonWhenAddingFutureWeeklySlot() {
        LocalDate today = AppTime.nowLocalDateTime().toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        classGroup.setStartDate(today);
        classGroup.setEndDate(tomorrow);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(
                activeEnrollment(601L, today, tomorrow)));
        // 当天已开始的旧课不在“未来可删除课次”查询中，但仍留在数据库参与冲突检查。
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(
                        Map.of("weekday", today.getDayOfWeek().getValue(),
                                "startTime", "00:00", "endTime", "00:30"),
                        Map.of("weekday", tomorrow.getDayOfWeek().getValue(),
                                "startTime", "10:00", "endTime", "11:00"))));

        verify(lessonOccurrenceMapper, times(1)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getPlannedStartAt()).isEqualTo(tomorrow.atTime(10, 0));
        assertThat(result.get("generatedOccurrences")).isEqualTo(1);
        assertThat(result.get("cancelledFutureOccurrences")).isEqualTo(0);
    }

    @Test
    void shouldRejectOverlappingClassWeeklyScheduleSlots() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);

        assertThatThrownBy(() -> classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(
                        Map.of("weekday", 1, "startTime", "10:00", "endTime", "11:00"),
                        Map.of("weekday", 1, "startTime", "10:30", "endTime", "11:30"))
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_CLASS_SCHEDULE");
        verify(classGroupMapper, never()).updateById(any(ClassGroupEntity.class));
        verify(classGroupScheduleSlotMapper, never()).insert(any(ClassGroupScheduleSlotEntity.class));
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldRequireFinalConfirmationBeforeEditingAnAlreadyStartedClassLesson() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(
                activeEnrollment(601L, now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(1))));
        LessonOccurrenceEntity inProgress = scheduledOccurrence(now.minusMinutes(1));
        inProgress.setPlannedEndAt(now.plusHours(1));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(inProgress));

        assertThatThrownBy(() -> classGroupService.checkClassScheduleEditable(501L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CLASS_LESSONS_NOT_CONFIRMED");
        assertThatThrownBy(() -> classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of("weekday", now.getDayOfWeek().getValue(),
                        "startTime", "15:00", "endTime", "18:00")))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CLASS_LESSONS_NOT_CONFIRMED");
        verify(classGroupMapper, never()).updateById(any(ClassGroupEntity.class));
        verify(classGroupScheduleSlotMapper, never()).insert(any(ClassGroupScheduleSlotEntity.class));
        verify(lessonOccurrenceMapper, never()).delete(any());
    }

    @Test
    void shouldAllowExplicitlyPreservingStartedLessonsWhenUpdatingFutureSchedule() {
        LocalDate today = AppTime.nowLocalDateTime().toLocalDate();
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        classGroup.setStartDate(today.minusDays(7));
        classGroup.setEndDate(today.minusDays(1));
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(
                activeEnrollment(601L, today.minusDays(7), today.plusDays(7))));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "preserveStartedLessons", true,
                "slots", List.of(Map.of("weekday", today.plusDays(1).getDayOfWeek().getValue(),
                        "startTime", "15:00", "endTime", "17:00"))));

        verify(classGroupMapper).updateById(classGroup);
        verify(classGroupScheduleSlotMapper).insert(any(ClassGroupScheduleSlotEntity.class));
        assertThat(result.get("classId")).isEqualTo(501L);
    }

    @Test
    void shouldIgnoreGeneratedLessonsFromBeforeTheStudentJoined() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        classGroup.setStartDate(tomorrow);
        classGroup.setEndDate(tomorrow);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        ClassEnrollmentEntity enrollment = activeEnrollment(601L, tomorrow, tomorrow);
        enrollment.setCreatedAt(now);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        LessonOccurrenceEntity historical = scheduledOccurrence(now.minusDays(1));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(
                List.of(historical), List.of(), List.of(), List.of());

        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of("weekday", tomorrow.getDayOfWeek().getValue(),
                        "startTime", "10:00", "endTime", "11:00"))));

        assertThat(result.get("generatedOccurrences")).isEqualTo(1);
    }

    @Test
    void shouldPreserveRecordedFutureClassOccurrenceWithoutDuplicatingTheSameTime() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);

        LocalDate preservedDate = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        classGroup.setStartDate(preservedDate);
        classGroup.setEndDate(preservedDate);
        LessonOccurrenceEntity preserved = scheduledOccurrence(preservedDate.atTime(10, 0));
        preserved.setId(2001L);
        LessonRecordEntity record = new LessonRecordEntity();
        record.setOccurrenceId(2001L);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(
                activeEnrollment(601L, preservedDate, preservedDate)));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(
                List.of(), List.of(preserved), List.of(preserved));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));

        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of(
                        "weekday", preservedDate.getDayOfWeek().getValue(),
                        "startTime", "10:00",
                        "endTime", "11:30"))
        ));

        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
        assertThat(result.get("cancelledFutureOccurrences")).isEqualTo(0);
        assertThat(result.get("generatedOccurrences")).isEqualTo(0);
    }

    @Test
    void shouldAddClassSlotOnDateWithRecordedFutureLesson() {
        LocalDate lessonDate = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        classGroup.setStartDate(lessonDate);
        classGroup.setEndDate(lessonDate);
        LessonOccurrenceEntity recorded = scheduledOccurrence(lessonDate.atTime(10, 0));
        LessonRecordEntity record = new LessonRecordEntity();
        record.setOccurrenceId(recorded.getId());
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(
                activeEnrollment(601L, lessonDate, lessonDate)));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(
                List.of(), List.of(recorded), List.of(recorded), List.of());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));

        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(
                        Map.of("weekday", lessonDate.getDayOfWeek().getValue(),
                                "startTime", "10:00", "endTime", "11:30"),
                        Map.of("weekday", lessonDate.getDayOfWeek().getValue(),
                                "startTime", "13:00", "endTime", "14:00"))));

        verify(lessonOccurrenceMapper, times(1)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getPlannedStartAt()).isEqualTo(lessonDate.atTime(13, 0));
        assertThat(result.get("generatedOccurrences")).isEqualTo(1);
    }

    @Test
    void shouldKeepOngoingClassLessonAndApplySplitSlotsNextWeek() {
        LocalDate sunday = LocalDate.of(2026, 9, 27);
        LocalDateTime rebuildFrom = sunday.atTime(13, 30);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStartDate(sunday);
        classGroup.setEndDate(sunday.plusWeeks(1));

        LessonOccurrenceEntity ongoing = scheduledOccurrence(sunday.atTime(13, 0));
        ongoing.setPlannedEndAt(sunday.atTime(16, 0));
        ClassGroupScheduleSlotEntity firstHalf = new ClassGroupScheduleSlotEntity();
        firstHalf.setWeekday(7);
        firstHalf.setStartTime(LocalTime.of(13, 0));
        firstHalf.setEndTime(LocalTime.of(14, 30));
        ClassGroupScheduleSlotEntity secondHalf = new ClassGroupScheduleSlotEntity();
        secondHalf.setWeekday(7);
        secondHalf.setStartTime(LocalTime.of(14, 30));
        secondHalf.setEndTime(LocalTime.of(16, 0));

        Integer generated = ReflectionTestUtils.invokeMethod(classGroupService,
                "generateFutureClassOccurrences", classGroup, List.of(firstHalf, secondHalf),
                List.of(ongoing), List.of(ongoing), rebuildFrom);

        verify(lessonOccurrenceMapper, times(2)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues())
                .extracting(LessonOccurrenceEntity::getPlannedStartAt)
                .containsExactly(sunday.plusWeeks(1).atTime(13, 0), sunday.plusWeeks(1).atTime(14, 30));
        assertThat(generated).isEqualTo(2);
    }

    @Test
    void shouldRejectClassSlotOverlappingRecordedFutureLesson() {
        LocalDate lessonDate = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        classGroup.setStartDate(lessonDate);
        classGroup.setEndDate(lessonDate);
        LessonOccurrenceEntity recorded = scheduledOccurrence(lessonDate.atTime(10, 0));
        LessonRecordEntity record = new LessonRecordEntity();
        record.setOccurrenceId(recorded.getId());
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(
                activeEnrollment(601L, lessonDate, lessonDate)));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(
                List.of(), List.of(recorded), List.of(recorded));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));

        assertThatThrownBy(() -> classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of("weekday", lessonDate.getDayOfWeek().getValue(),
                        "startTime", "11:00", "endTime", "12:00")))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SCHEDULE_CONFLICT");
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldKeepAnExistingStudentOverlapWhenRebuildingTheSameClassTime() {
        LocalDate date = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        stubPreviouslyOverlappingClassSchedule(date);

        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of("weekday", date.getDayOfWeek().getValue(),
                        "startTime", "15:00", "endTime", "18:00"))));

        assertThat(result.get("generatedOccurrences")).isEqualTo(1);
        verify(lessonOccurrenceMapper, times(1)).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldAllowScheduleChangeOutsideTheExistingStudentOverlap() {
        LocalDate date = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        stubPreviouslyOverlappingClassSchedule(date);

        Map<String, Object> result = classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of("weekday", date.getDayOfWeek().getValue(),
                        "startTime", "15:00", "endTime", "19:00"))));

        assertThat(result.get("generatedOccurrences")).isEqualTo(1);
        verify(lessonOccurrenceMapper, times(1)).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldRejectOnlyTheNewPartOfAStudentOverlap() {
        LocalDate date = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        stubPreviouslyOverlappingClassSchedule(date);
        StudentEntity student = new StudentEntity();
        student.setStudentName("Osborn Wang");
        when(studentMapper.selectById(601L)).thenReturn(student);

        assertThatThrownBy(() -> classGroupService.updateClassSchedule(501L, Map.of(
                "slots", List.of(Map.of("weekday", date.getDayOfWeek().getValue(),
                        "startTime", "14:00", "endTime", "18:00")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Osborn Wang")
                .hasMessageContaining("13:00–16:00");
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldCheckSharedPrivateLessonsForEveryEnrolledStudent() {
        LocalDate date = AppTime.nowLocalDateTime().toLocalDate().plusDays(1);
        LessonOccurrenceEntity candidate = scheduledOccurrence(date.atTime(10, 30));
        LessonOccurrenceEntity sharedLesson = scheduledOccurrence(date.atTime(10, 0));
        sharedLesson.setId(2002L);
        sharedLesson.setClassGroupId(null);
        sharedLesson.setStudentId(700L);
        sharedLesson.setPrivateLessonGroupId(900L);
        PrivateLessonGroupMemberEntity member = new PrivateLessonGroupMemberEntity();
        member.setStudentId(601L);
        member.setPrivateLessonGroupId(900L);
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(List.of(member));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(activeEnrollment(601L, date, date)));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(), List.of(sharedLesson));
        StudentEntity student = new StudentEntity();
        student.setStudentName("Alice Chen");
        when(studentMapper.selectById(601L)).thenReturn(student);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(classGroupService,
                "ensureNoGeneratedClassOccurrenceConflicts", candidate, new ClassGroupEntity(), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Alice Chen")
                .hasMessageContaining("1-on-1 lesson");
        ArgumentCaptor<LambdaQueryWrapper<LessonOccurrenceEntity>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonOccurrenceMapper, times(2)).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(1).getSqlSegment()).contains("privateLessonGroupId");
    }

    /** 模拟线上学生在原 15–18 点班课之外，还报名了 13–16 点班课。 */
    private void stubPreviouslyOverlappingClassSchedule(LocalDate date) {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setStatus("ACTIVE");
        classGroup.setStartDate(date);
        classGroup.setEndDate(date);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);

        ClassEnrollmentEntity currentEnrollment = activeEnrollment(601L, date, date);
        ClassEnrollmentEntity otherEnrollment = activeEnrollment(601L, date, date);
        otherEnrollment.setClassId(900L);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(currentEnrollment, otherEnrollment));

        LessonOccurrenceEntity previous = scheduledOccurrence(date.atTime(15, 0));
        previous.setPlannedEndAt(date.atTime(18, 0));
        LessonOccurrenceEntity other = scheduledOccurrence(date.atTime(13, 0));
        other.setId(2002L);
        other.setClassGroupId(900L);
        other.setPlannedEndAt(date.atTime(16, 0));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(
                List.of(), List.of(previous), List.of(), List.of(other));
    }

    private LessonRecordEntity pendingClassRecord(Long id, Long studentId) {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setId(id);
        record.setOccurrenceId(1001L);
        record.setStudentId(studentId);
        record.setPackageId(null);
        record.setResultStatus("COMPLETED");
        record.setActualTeacherId(701L);
        record.setActualClassroomId(801L);
        record.setActualUnits(2);
        record.setShouldDeduct(0);
        record.setDeductedUnits(0);
        record.setShouldExtendEndDate(0);
        record.setConfirmStage("PENDING_ADMIN");
        record.setTeacherConfirmedByUserId(1002L);
        record.setTeacherConfirmedAt(LocalDateTime.now().minusMinutes(10));
        return record;
    }

    private LessonRecordEntity confirmedClassRecord(Long occurrenceId, Long studentId) {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setOccurrenceId(occurrenceId);
        record.setStudentId(studentId);
        record.setConfirmStage("CONFIRMED");
        record.setResultStatus("COMPLETED");
        return record;
    }

    private TeacherEntity activeTeacher(Long id) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(id);
        teacher.setStatus("ACTIVE");
        return teacher;
    }

    private ClassroomEntity activeClassroom(Long id) {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(id);
        classroom.setStatus("ACTIVE");
        return classroom;
    }

    private LessonOccurrenceEntity scheduledOccurrence(LocalDateTime startAt) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(1001L);
        occurrence.setClassGroupId(501L);
        occurrence.setPlannedTeacherId(701L);
        occurrence.setPlannedClassroomId(801L);
        occurrence.setPlannedStartAt(startAt);
        occurrence.setPlannedEndAt(startAt.plusMinutes(90));
        occurrence.setPlannedUnits(3);
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private ClassEnrollmentEntity activeEnrollment(Long studentId, LocalDate enrollmentDate, LocalDate expiryDate) {
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setClassId(501L);
        enrollment.setStudentId(studentId);
        enrollment.setEnrollmentDate(enrollmentDate);
        enrollment.setExpiryDate(expiryDate);
        enrollment.setStatus("ACTIVE");
        return enrollment;
    }

    private CurrentUserResponse adminUser() {
        return new CurrentUserResponse(999L, "admin", "Admin", "ADMIN", null, null, List.of("ADMIN"));
    }

    private CurrentUserResponse teacherUser(Long teacherId) {
        return new CurrentUserResponse(1002L, "teacher", "Teacher", "TEACHER", teacherId, null, List.of("TEACHER"));
    }

    private String weekdayShortLabel(int weekday) {
        return switch (weekday) {
            case 1 -> "Mon";
            case 2 -> "Tue";
            case 3 -> "Wed";
            case 4 -> "Thu";
            case 5 -> "Fri";
            case 6 -> "Sat";
            case 7 -> "Sun";
            default -> "?";
        };
    }
}
