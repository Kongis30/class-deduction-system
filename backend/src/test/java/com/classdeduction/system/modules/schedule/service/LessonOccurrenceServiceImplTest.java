package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.schedule.dto.OneOnOneDeductionQuery;
import com.classdeduction.system.modules.schedule.dto.PendingClassroomBackfillRequest;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.impl.LessonOccurrenceServiceImpl;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneTeacherVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 课次查询服务测试：覆盖查询窗口保护，避免生产环境出现超大时间范围扫描。
 */
@ExtendWith(MockitoExtension.class)
class LessonOccurrenceServiceImplTest {

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private LessonStatusRuleMapper lessonStatusRuleMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;

    @Mock
    private RecurringScheduleSlotMapper recurringScheduleSlotMapper;

    private LessonOccurrenceServiceImpl lessonOccurrenceService;

    @BeforeEach
    void setUp() {
        lessonOccurrenceService = new LessonOccurrenceServiceImpl(
                lessonOccurrenceMapper,
                lessonRecordMapper,
                lessonStatusRuleMapper,
                coursePackageMapper,
                enrollmentOrderItemMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                courseProductMapper,
                classGroupMapper,
                classEnrollmentMapper,
                classGroupScheduleSlotMapper,
                recurringScheduleSlotMapper
        );
    }

    @Test
    void shouldRejectTooLargeOccurrenceQueryWindow() {
        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setStartAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        query.setEndAt(LocalDateTime.of(2026, 8, 1, 0, 0));

        assertThatThrownBy(() -> lessonOccurrenceService.pageOccurrences(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Query window cannot exceed 180 days.");
    }

    @Test
    void shouldRejectTeacherCalendarQueryForOtherTeacher() {
        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setTeacherId(302L);

        assertThatThrownBy(() -> lessonOccurrenceService.pageOccurrences(query, teacherUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Teachers can only view their own calendar.");
    }

    @Test
    void shouldScopeTeacherCalendarQueryToCurrentTeacher() {
        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        when(lessonOccurrenceMapper.selectPage(any(), any())).thenReturn(new Page<LessonOccurrenceEntity>(1, 10));

        lessonOccurrenceService.pageOccurrences(query, teacherUser());

        assertThat(query.getTeacherId()).isEqualTo(301L);
    }

    @Test
    void shouldRejectStudentOccurrenceDetailForOtherStudent() {
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        occurrence.setStudentId(102L);
        when(lessonOccurrenceMapper.selectById(4001L)).thenReturn(occurrence);

        assertThatThrownBy(() -> lessonOccurrenceService.getOccurrence(4001L, studentUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Students can only view their own calendar.");
    }

    @Test
    void shouldAllowStudentOccurrenceDetailForOwnClassEnrollment() {
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        occurrence.setStudentId(null);
        occurrence.setClassGroupId(7001L);
        when(lessonOccurrenceMapper.selectById(4001L)).thenReturn(occurrence);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(buildClassEnrollment(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31),
                LocalDateTime.of(2026, 9, 1, 9, 0))));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        assertThat(lessonOccurrenceService.getOccurrence(4001L, studentUser()).id()).isEqualTo(4001L);
    }

    @Test
    void shouldRejectStudentClassOccurrenceDetailBeforeEnrollmentStartDate() {
        LessonOccurrenceEntity occurrence = buildClassOccurrence(LocalDateTime.of(2026, 9, 12, 9, 0));
        when(lessonOccurrenceMapper.selectById(4001L)).thenReturn(occurrence);

        assertThatThrownBy(() -> lessonOccurrenceService.getOccurrence(4001L, studentUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Students can only view their own calendar.");
    }

    @Test
    void shouldHideClassOccurrencesBeforeStudentEnrollmentStartDate() {
        LessonOccurrenceEntity beforeEnrollment = buildClassOccurrence(LocalDateTime.of(2026, 9, 12, 9, 0));
        LessonOccurrenceEntity afterEnrollment = buildClassOccurrence(LocalDateTime.of(2026, 9, 19, 9, 0));
        afterEnrollment.setId(4002L);
        afterEnrollment.setLessonNo("CLS202609190001");
        ClassEnrollmentEntity enrollment = buildClassEnrollment(
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 10, 31),
                LocalDateTime.of(2026, 9, 15, 10, 0));

        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(beforeEnrollment, afterEnrollment));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setStudentId(101L);
        query.setStartAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        query.setEndAt(LocalDateTime.of(2026, 9, 30, 23, 59));
        query.setPageNum(1);
        query.setPageSize(10);

        List<LessonOccurrenceVO> records = lessonOccurrenceService.pageOccurrences(query).records();

        assertThat(records).extracting(LessonOccurrenceVO::id).containsExactly(4002L);
    }

    @Test
    void shouldHideClassOccurrencesBeforeEnrollmentWasCreatedWhenStartDateIsBackdated() {
        LessonOccurrenceEntity beforeSignup = buildClassOccurrence(LocalDateTime.of(2026, 9, 10, 15, 0));
        LessonOccurrenceEntity afterSignupPast = buildClassOccurrence(LocalDateTime.of(2026, 9, 18, 15, 0));
        afterSignupPast.setId(4002L);
        afterSignupPast.setLessonNo("CLS202609180001");
        ClassEnrollmentEntity enrollment = buildClassEnrollment(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31),
                LocalDateTime.of(2026, 9, 15, 10, 0));

        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(beforeSignup, afterSignupPast));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setStudentId(101L);
        query.setStartAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        query.setEndAt(LocalDateTime.of(2026, 9, 30, 23, 59));
        query.setPageNum(1);
        query.setPageSize(10);

        List<LessonOccurrenceVO> records = lessonOccurrenceService.pageOccurrences(query).records();

        assertThat(records).extracting(LessonOccurrenceVO::id).containsExactly(4002L);
    }

    @Test
    void shouldExposeClassSlotTaughtSubjectInStudentSchedule() {
        LessonOccurrenceEntity occurrence = buildClassOccurrence(LocalDateTime.of(2026, 9, 19, 9, 0));
        ClassEnrollmentEntity enrollment = buildClassEnrollment(
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 10, 31),
                LocalDateTime.of(2026, 9, 15, 10, 0));
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setId(8001L);
        slot.setClassGroupId(7001L);
        slot.setSubjectName("SHSAT Reading");

        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(classGroupScheduleSlotMapper.selectBatchIds(any())).thenReturn(List.of(slot));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(buildClassGroup()));
        when(courseProductMapper.selectBatchIds(any())).thenReturn(List.of(buildCourseProduct()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setStudentId(101L);
        query.setStartAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        query.setEndAt(LocalDateTime.of(2026, 9, 30, 23, 59));
        query.setPageNum(1);
        query.setPageSize(10);

        List<LessonOccurrenceVO> records = lessonOccurrenceService.pageOccurrences(query).records();

        assertThat(records).singleElement()
                .satisfies(record -> {
                    assertThat(record.className()).isEqualTo("SHSAT Sprint");
                    assertThat(record.productName()).isEqualTo("SHSAT Prep");
                    assertThat(record.subjectName()).isEqualTo("SHSAT Reading");
                });
    }

    @Test
    void shouldExposeOneOnOneOccurrencesInDeductionQuery() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence), List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneDeductionQuery query = new OneOnOneDeductionQuery();
        query.setTeacherId(301L);
        query.setStartAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        query.setEndAt(LocalDateTime.of(2026, 9, 2, 0, 0));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(query, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).teacherId()).isEqualTo(301L);
        assertThat(result.get(0).students()).hasSize(1);
        assertThat(result.get(0).students().get(0).packages()).hasSize(1);
        assertThat(result.get(0).students().get(0).packages().get(0).lessons())
                .singleElement()
                .satisfies(lesson -> {
                    assertThat(lesson.occurrenceId()).isEqualTo(4001L);
                    assertThat(lesson.packageId()).isEqualTo(3001L);
                    assertThat(lesson.planningStatus()).isEqualTo("SCHEDULED");
                    assertThat(lesson.plannedTeacherName()).isEqualTo("Ms Lee");
                });
    }

    @Test
    void shouldRemindRenewalWhenTwoLessonsRemain() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setRemainingUnits(6);
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence), Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));

        OneOnOneDeductionQuery query = new OneOnOneDeductionQuery();
        query.setTeacherId(301L);

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(query, null).get(0);

        assertThat(teacher.students().get(0).renewalReminder()).isTrue();
        assertThat(teacher.students().get(0).packages().get(0).renewalReminder()).isTrue();
    }

    @Test
    void shouldReturnStructuredClassroomConflictWhenBackfillOverlapsExistingLesson() {
        LessonOccurrenceEntity pending = buildOneOnOneOccurrence();
        pending.setPlannedClassroomId(null);
        LessonOccurrenceEntity existing = buildOneOnOneOccurrence();
        existing.setId(5001L);
        existing.setLessonNo("CLS-CONFLICT");
        existing.setPlannedClassroomId(601L);
        ClassroomEntity classroom = buildClassroom();
        when(classroomMapper.selectByIdForUpdate(601L)).thenReturn(classroom);
        when(classroomMapper.selectById(601L)).thenReturn(classroom);
        when(lessonOccurrenceMapper.selectById(4001L)).thenReturn(pending);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(existing));

        PendingClassroomBackfillRequest request = new PendingClassroomBackfillRequest();
        request.setTargetType("OCCURRENCE");
        request.setTargetId(4001L);
        request.setClassroomId(601L);

        assertThatThrownBy(() -> lessonOccurrenceService.backfillPendingClassroom(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessError = (BusinessException) error;
                    assertThat(businessError.getCode()).isEqualTo("SCHEDULE_CONFLICT");
                    assertThat((List<?>) businessError.getData())
                            .singleElement()
                            .satisfies(detail -> {
                                ScheduleConflictDetailVO conflict = (ScheduleConflictDetailVO) detail;
                                assertThat(conflict.resourceType()).isEqualTo("CLASSROOM");
                                assertThat(conflict.resourceId()).isEqualTo(601L);
                                assertThat(conflict.resourceName()).isEqualTo("Room 601");
                                assertThat(conflict.existingOccurrenceId()).isEqualTo(5001L);
                            });
                });
    }

    @Test
    void shouldRejectClassroomBackfillWhenOccurrenceWasConcurrentlyUpdated() {
        LessonOccurrenceEntity pending = buildOneOnOneOccurrence();
        pending.setPlannedClassroomId(null);
        pending.setVersion(1);
        when(classroomMapper.selectByIdForUpdate(601L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectById(4001L)).thenReturn(pending);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.updateById(any(LessonOccurrenceEntity.class))).thenReturn(0);

        PendingClassroomBackfillRequest request = new PendingClassroomBackfillRequest();
        request.setTargetType("OCCURRENCE");
        request.setTargetId(4001L);
        request.setClassroomId(601L);

        assertThatThrownBy(() -> lessonOccurrenceService.backfillPendingClassroom(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("OCCURRENCE_CONCURRENT_UPDATE");
    }

    private CoursePackageEntity buildOneOnOnePackage() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setPackageNo("PK3001");
        coursePackage.setPackageName("1:1 Algebra");
        coursePackage.setSubjectName("Math");
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setStudentId(101L);
        coursePackage.setTotalUnits(20);
        coursePackage.setConsumedUnits(0);
        coursePackage.setRemainingUnits(20);
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setStartDate(LocalDate.of(2026, 9, 1));
        coursePackage.setEstimatedEndDate(LocalDate.of(2026, 12, 31));
        return coursePackage;
    }

    private LessonOccurrenceEntity buildOneOnOneOccurrence() {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(4001L);
        occurrence.setLessonNo("O2O202609010001");
        occurrence.setPackageId(3001L);
        occurrence.setStudentId(101L);
        occurrence.setPlannedTeacherId(301L);
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 9, 1, 15, 30));
        occurrence.setPlannedEndAt(LocalDateTime.of(2026, 9, 1, 17, 0));
        occurrence.setPlannedUnits(3);
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private LessonOccurrenceEntity buildClassOccurrence(LocalDateTime startAt) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(4001L);
        occurrence.setLessonNo("CLS202609120001");
        occurrence.setClassGroupId(7001L);
        occurrence.setScheduleSlotId(8001L);
        occurrence.setPlannedTeacherId(301L);
        occurrence.setPlannedStartAt(startAt);
        occurrence.setPlannedEndAt(startAt.plusHours(3));
        occurrence.setPlannedUnits(6);
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private ClassGroupEntity buildClassGroup() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(7001L);
        classGroup.setClassName("SHSAT Sprint");
        classGroup.setProductId(9001L);
        classGroup.setStatus("ACTIVE");
        return classGroup;
    }

    private CourseProductEntity buildCourseProduct() {
        CourseProductEntity product = new CourseProductEntity();
        product.setId(9001L);
        product.setProductName("SHSAT Prep");
        return product;
    }

    private ClassEnrollmentEntity buildClassEnrollment(LocalDate enrollmentDate,
                                                       LocalDate expiryDate,
                                                       LocalDateTime createdAt) {
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setClassId(7001L);
        enrollment.setStudentId(101L);
        enrollment.setEnrollmentDate(enrollmentDate);
        enrollment.setExpiryDate(expiryDate);
        enrollment.setStatus("ACTIVE");
        enrollment.setCreatedAt(createdAt);
        return enrollment;
    }

    private StudentEntity buildStudent() {
        StudentEntity student = new StudentEntity();
        student.setId(101L);
        student.setStudentNo("STU101");
        student.setStudentName("Jin Tang");
        return student;
    }

    private TeacherEntity buildTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(301L);
        teacher.setTeacherNo("T301");
        teacher.setTeacherName("Ms Lee");
        return teacher;
    }

    private ClassroomEntity buildClassroom() {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(601L);
        classroom.setRoomName("Room 601");
        classroom.setStatus("ACTIVE");
        return classroom;
    }

    private CurrentUserResponse teacherUser() {
        return new CurrentUserResponse(9001L, "teacher", "Ms Lee", "TEACHER", 301L, null, List.of("TEACHER"));
    }

    private CurrentUserResponse studentUser() {
        return new CurrentUserResponse(9002L, "student", "Jin Tang", "STUDENT", null, 101L, List.of("STUDENT"));
    }
}
