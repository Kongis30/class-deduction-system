package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.schedule.dto.OneOnOneDeductionQuery;
import com.classdeduction.system.modules.schedule.dto.PendingClassroomBackfillRequest;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.impl.LessonOccurrenceServiceImpl;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneTeacherVO;
import com.classdeduction.system.modules.schedule.vo.PendingClassroomAssignmentVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * 课次查询服务测试：覆盖查询窗口保护，避免生产环境出现超大时间范围扫描。
 */
@ExtendWith(MockitoExtension.class)
class LessonOccurrenceServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), LessonOccurrenceEntity.class);
    }

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
    private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;

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

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper recurringScheduleRuleMapper;

    private LessonOccurrenceServiceImpl lessonOccurrenceService;

    @BeforeEach
    void setUp() {
        lenient().when(lessonOccurrenceMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> lessonOccurrenceMapper.selectById(invocation.getArgument(0)));
        lenient().when(classGroupMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> classGroupMapper.selectById(invocation.getArgument(0)));
        lenient().when(recurringScheduleSlotMapper.selectList(any())).thenReturn(Collections.emptyList());
        lessonOccurrenceService = new LessonOccurrenceServiceImpl(
                lessonOccurrenceMapper,
                lessonRecordMapper,
                lessonStatusRuleMapper,
                coursePackageMapper,
                enrollmentOrderItemMapper,
                privateLessonGroupMemberMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                courseProductMapper,
                classGroupMapper,
                classEnrollmentMapper,
                classGroupScheduleSlotMapper,
                recurringScheduleSlotMapper,
                recurringScheduleRuleMapper
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
    void shouldAllowPackageOccurrenceQueryWindowLongerThanTwoYears() {
        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setPackageId(1001L);
        query.setPageNum(1);
        query.setPageSize(10);
        query.setStartAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        query.setEndAt(LocalDateTime.of(2029, 1, 1, 0, 0));
        when(lessonOccurrenceMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        assertThat(lessonOccurrenceService.pageOccurrences(query).records()).isEmpty();
    }

    @Test
    void shouldReturnAllPackageOccurrencesWhenNoDateRangeIsProvided() {
        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setPackageId(1001L);
        query.setPageNum(1);
        query.setPageSize(10);
        when(lessonOccurrenceMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        lessonOccurrenceService.pageOccurrences(query);

        ArgumentCaptor<Wrapper<LessonOccurrenceEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(lessonOccurrenceMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("package_id")
                .doesNotContain("planned_start_at");
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
    void shouldGiveCurrentSlotTeacherAccessToHandedOverLessonDetail() {
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        occurrence.setScheduleSlotId(6001L);
        RecurringScheduleSlotEntity oldSlot = new RecurringScheduleSlotEntity();
        oldSlot.setId(6001L);
        oldSlot.setSlotLineageId(7001L);
        oldSlot.setTeacherId(301L);
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6002L);
        currentSlot.setSlotLineageId(7001L);
        currentSlot.setRuleId(5002L);
        currentSlot.setTeacherId(302L);
        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(occurrence.getPackageId());
        currentRule.setRuleStatus("ACTIVE");
        when(lessonOccurrenceMapper.selectById(occurrence.getId())).thenReturn(occurrence);
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(oldSlot));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        CurrentUserResponse newTeacher = new CurrentUserResponse(
                9002L, "new-teacher", "New Teacher", "TEACHER", 302L, null, List.of("TEACHER"));
        assertThat(lessonOccurrenceService.getOccurrence(occurrence.getId(), newTeacher).id())
                .isEqualTo(occurrence.getId());
        assertThatThrownBy(() -> lessonOccurrenceService.getOccurrence(occurrence.getId(), teacherUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Teachers can only view their own calendar.");
    }

    @Test
    void shouldShowAllPrivateGroupStudentsInTeacherLessonList() {
        LessonOccurrenceEntity sharedLesson = buildOneOnOneOccurrence();
        sharedLesson.setPrivateLessonGroupId(7001L);
        LessonOccurrenceEntity soloLesson = buildOneOnOneOccurrence();
        soloLesson.setId(4002L);
        soloLesson.setLessonNo("O2O202609010002");
        Page<LessonOccurrenceEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(sharedLesson, soloLesson));
        when(lessonOccurrenceMapper.selectPage(any(), any())).thenReturn(page);
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(List.of(
                buildPrivateGroupMember(1, 101L),
                buildPrivateGroupMember(2, 102L),
                buildPrivateGroupMember(3, 103L)));
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(buildOneOnOnePackage()));
        StudentEntity secondStudent = buildStudent();
        secondStudent.setId(102L);
        secondStudent.setStudentName("Chenxi Lin");
        StudentEntity thirdStudent = buildStudent();
        thirdStudent.setId(103L);
        thirdStudent.setStudentName("Gloria Deng");
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent(), secondStudent, thirdStudent));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        List<LessonOccurrenceVO> records = lessonOccurrenceService.pageOccurrences(query, teacherUser()).records();

        assertThat(records).hasSize(2);
        assertThat(records.get(0).participantNames()).containsExactly("Jin Tang", "Chenxi Lin", "Gloria Deng");
        assertThat(records.get(0).studentName()).isEqualTo("Jin Tang");
        assertThat(records.get(1).participantNames()).isEmpty();
        assertThat(records.get(1).studentName()).isEqualTo("Jin Tang");
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
    void shouldExposeOnlyRealScheduleVersionChangesInLessonHistory() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        occurrence.setScheduleRuleId(5003L);
        RecurringScheduleRuleEntity initialRule = new RecurringScheduleRuleEntity();
        initialRule.setId(5001L);
        initialRule.setPackageId(coursePackage.getId());
        initialRule.setRuleStatus("ENDED");
        RecurringScheduleRuleEntity firstChange = new RecurringScheduleRuleEntity();
        firstChange.setId(5002L);
        firstChange.setPackageId(coursePackage.getId());
        firstChange.setSupersedesRuleId(5001L);
        firstChange.setCreatedAt(LocalDateTime.of(2026, 10, 8, 11, 30));
        firstChange.setRuleStatus("ENDED");
        RecurringScheduleRuleEntity secondChange = new RecurringScheduleRuleEntity();
        secondChange.setId(5003L);
        secondChange.setPackageId(coursePackage.getId());
        secondChange.setSupersedesRuleId(5002L);
        secondChange.setCreatedAt(LocalDateTime.of(2026, 10, 10, 9, 15));
        secondChange.setRuleStatus("ACTIVE");

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleRuleMapper.selectList(any()))
                .thenReturn(List.of(secondChange, initialRule, firstChange), List.of(secondChange));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);

        assertThat(teacher.students().get(0).packages()).singleElement().satisfies(pkg -> {
            assertThat(pkg.lessons()).hasSize(1);
            assertThat(pkg.scheduleChanges()).extracting(change -> change.ruleId())
                    .containsExactly(5002L, 5003L);
            assertThat(pkg.scheduleChanges()).extracting(change -> change.changedAt())
                    .containsExactly(LocalDateTime.of(2026, 10, 8, 11, 30),
                            LocalDateTime.of(2026, 10, 10, 9, 15));
        });
    }

    @Test
    void shouldShowOriginalSubjectForConfirmedLessonWithDeletedScheduleSlot() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity completedLesson = buildOneOnOneOccurrence();
        completedLesson.setPlanningStatus("MERGED");
        completedLesson.setScheduleSlotId(6001L);
        LessonRecordEntity record = new LessonRecordEntity();
        record.setId(8001L);
        record.setOccurrenceId(completedLesson.getId());
        record.setPackageId(coursePackage.getId());
        record.setConfirmStage("CONFIRMED");
        record.setResultStatus("COMPLETED");
        RecurringScheduleSlotEntity oldSlot = new RecurringScheduleSlotEntity();
        oldSlot.setId(6001L);
        oldSlot.setSubjectName("Vocabulary");
        oldSlot.setTeacherId(301L);
        oldSlot.setIsDeleted(1);

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(completedLesson), List.of(completedLesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(record)));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(oldSlot));

        OneOnOneDeductionQuery query = new OneOnOneDeductionQuery();
        query.setTeacherId(301L);
        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(query, null);

        assertThat(result.get(0).students().get(0).packages().get(0).lessons())
                .singleElement()
                .satisfies(lesson -> {
                    assertThat(lesson.confirmStage()).isEqualTo("CONFIRMED");
                    assertThat(lesson.subjectName()).isEqualTo("Vocabulary");
                });
    }

    @Test
    void shouldRecoverLegacyPastSubjectWhenSlotWasNotEditedSinceOccurrenceCreation() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        occurrence.setScheduleSlotId(6001L);
        occurrence.setCreatedAt(LocalDateTime.of(2026, 8, 31, 10, 0));
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setSubjectName("AMC/Algebra I");
        slot.setTeacherId(301L);
        slot.setIsDeleted(0);
        slot.setUpdatedAt(LocalDateTime.of(2026, 8, 31, 9, 0));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence), List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(slot));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);

        assertThat(teacher.students().get(0).packages().get(0).lessons())
                .singleElement()
                .satisfies(lesson -> assertThat(lesson.subjectName()).isEqualTo("AMC/Algebra I"));
    }

    @Test
    void shouldNotUseChangedCurrentSlotToNameLegacyPastOccurrence() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        occurrence.setScheduleSlotId(6001L);
        occurrence.setCreatedAt(LocalDateTime.of(2026, 8, 31, 10, 0));
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setSubjectName("New subject after schedule edit");
        slot.setTeacherId(301L);
        slot.setIsDeleted(0);
        slot.setUpdatedAt(LocalDateTime.of(2026, 9, 2, 9, 0));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence), List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(slot));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);

        assertThat(teacher.students().get(0).packages().get(0).lessons())
                .singleElement()
                .satisfies(lesson -> assertThat(lesson.subjectName()).isNull());
    }

    @Test
    void shouldKeepAllocatedHoursPackageWideWhenLessonListHasDateFilter() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity visibleLesson = buildOneOnOneOccurrence();
        visibleLesson.setScheduleRuleId(5001L);
        visibleLesson.setScheduleSlotId(6001L);
        LessonOccurrenceEntity laterLesson = buildOneOnOneOccurrence();
        laterLesson.setId(4002L);
        laterLesson.setLessonNo("O2O202609080001");
        laterLesson.setScheduleRuleId(5001L);
        laterLesson.setScheduleSlotId(6001L);
        laterLesson.setPlannedStartAt(LocalDateTime.of(2026, 9, 8, 15, 30));
        laterLesson.setPlannedEndAt(LocalDateTime.of(2026, 9, 8, 17, 0));
        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        RecurringScheduleSlotEntity activeSlot = new RecurringScheduleSlotEntity();
        activeSlot.setId(6001L);
        activeSlot.setRuleId(currentRule.getId());
        activeSlot.setTeacherId(301L);
        activeSlot.setAllocatedUnits(6); // 3h current allocation snapshot
        activeSlot.setSlotStatus("ACTIVE");
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(List.of(visibleLesson), List.of(visibleLesson, laterLesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(activeSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(activeSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneDeductionQuery query = new OneOnOneDeductionQuery();
        query.setStartAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        query.setEndAt(LocalDateTime.of(2026, 9, 2, 0, 0));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(query, null).get(0);
        assertThat(teacher.students().get(0).packages()).singleElement().satisfies(pkg -> {
            assertThat(pkg.lessons()).hasSize(1);
            assertThat(pkg.allocatedUnits()).isEqualTo(3.0);
        });
    }

    @Test
    void shouldListPackageUnderEachAssignedTeacherWithOnlyTheirLessons() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity firstTeacherLesson = buildOneOnOneOccurrence();
        firstTeacherLesson.setScheduleRuleId(5001L);
        firstTeacherLesson.setScheduleSlotId(6001L);
        LessonOccurrenceEntity secondTeacherLesson = buildOneOnOneOccurrence();
        secondTeacherLesson.setId(4002L);
        secondTeacherLesson.setLessonNo("O2O202609010002");
        secondTeacherLesson.setPlannedTeacherId(302L);
        secondTeacherLesson.setScheduleRuleId(5001L);
        secondTeacherLesson.setScheduleSlotId(6002L);

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        RecurringScheduleSlotEntity firstSlot = new RecurringScheduleSlotEntity();
        firstSlot.setId(6001L);
        firstSlot.setRuleId(currentRule.getId());
        firstSlot.setTeacherId(301L);
        firstSlot.setAllocatedUnits(6);
        firstSlot.setSlotStatus("ACTIVE");
        RecurringScheduleSlotEntity secondSlot = new RecurringScheduleSlotEntity();
        secondSlot.setId(6002L);
        secondSlot.setRuleId(currentRule.getId());
        secondSlot.setTeacherId(302L);
        secondSlot.setAllocatedUnits(6);
        secondSlot.setSlotStatus("ACTIVE");

        TeacherEntity secondTeacher = new TeacherEntity();
        secondTeacher.setId(302L);
        secondTeacher.setTeacherNo("T302");
        secondTeacher.setTeacherName("Ms Sophia");
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(firstTeacherLesson, secondTeacherLesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(firstSlot, secondSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(firstSlot, secondSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher(), secondTeacher));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OneOnOneTeacherVO::teacherId).containsExactly(301L, 302L);
        assertThat(result).allSatisfy(teacher -> {
            assertThat(teacher.students()).singleElement()
                    .satisfies(student -> assertThat(student.studentName()).isEqualTo("Jin Tang"));
            assertThat(teacher.students().get(0).packages()).singleElement()
                    .satisfies(pkg -> assertThat(pkg.lessons())
                            .singleElement()
                            .satisfies(lesson -> assertThat(lesson.plannedTeacherId()).isEqualTo(teacher.teacherId())));
            assertThat(teacher.students().get(0).packages().get(0).allocatedUnits()).isEqualTo(6.0);
            assertThat(teacher.students().get(0).packages().get(0).teacherHours())
                    .filteredOn(hours -> hours.teacherId().equals(teacher.teacherId()))
                    .singleElement()
                    .satisfies(hours -> {
                        assertThat(hours.allocatedUnits()).isEqualTo(3.0);
                        assertThat(hours.remainingUnits()).isEqualTo(3.0);
                    });
        });
    }

    @Test
    void shouldMoveHistoricalAndUnprocessedLessonsToCurrentTeacherTimeline() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LocalDateTime now = AppTime.nowLocalDateTime();

        RecurringScheduleRuleEntity oldRule = new RecurringScheduleRuleEntity();
        oldRule.setId(5001L);
        oldRule.setPackageId(coursePackage.getId());
        oldRule.setRuleStatus("PAUSED");
        oldRule.setCreatedAt(now.minusDays(30));
        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(1));
        currentRule.setAllocationPeriodStartedAt(now.minusDays(1));

        RecurringScheduleSlotEntity oldSlot = new RecurringScheduleSlotEntity();
        oldSlot.setId(6001L);
        oldSlot.setRuleId(oldRule.getId());
        oldSlot.setSlotLineageId(7001L);
        oldSlot.setTeacherId(301L);
        oldSlot.setSlotStatus("DELETED");
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6002L);
        currentSlot.setRuleId(currentRule.getId());
        currentSlot.setSlotLineageId(7001L);
        currentSlot.setTeacherId(302L);
        currentSlot.setAllocatedUnits(8);
        currentSlot.setSlotStatus("ACTIVE");

        LessonOccurrenceEntity historical = buildOneOnOneOccurrence();
        historical.setScheduleRuleId(oldRule.getId());
        historical.setScheduleSlotId(oldSlot.getId());
        historical.setPlannedTeacherId(301L);
        historical.setPlannedStartAt(now.minusDays(3));
        historical.setPlannedEndAt(now.minusDays(3).plusHours(2));
        historical.setPlannedUnits(4);
        historical.setPlanningStatus("MERGED");
        LessonRecordEntity historicalRecord = confirmedDeduction(5001L, historical, 4);
        historicalRecord.setConfirmedAt(now.minusDays(2));
        historicalRecord.setPlannedTeacherId(301L);
        historicalRecord.setActualTeacherId(301L);
        LessonOccurrenceEntity oldPending = buildOneOnOneOccurrence();
        oldPending.setId(4003L);
        oldPending.setLessonNo("O2O202610240003");
        oldPending.setScheduleRuleId(oldRule.getId());
        oldPending.setScheduleSlotId(oldSlot.getId());
        oldPending.setPlannedTeacherId(301L);
        oldPending.setAllocationOwnerTeacherId(301L);
        oldPending.setPlannedStartAt(now.minusDays(2));
        oldPending.setPlannedEndAt(now.minusDays(2).plusHours(2));
        oldPending.setPlannedUnits(4);
        LessonOccurrenceEntity future = buildOneOnOneOccurrence();
        future.setId(4002L);
        future.setLessonNo("O2O202610240002");
        future.setScheduleRuleId(currentRule.getId());
        future.setScheduleSlotId(currentSlot.getId());
        future.setPlannedTeacherId(302L);
        future.setAllocationOwnerTeacherId(302L);
        future.setPlannedStartAt(now.plusDays(1));
        future.setPlannedEndAt(now.plusDays(1).plusHours(2));
        future.setPlannedUnits(4);

        TeacherEntity newTeacher = new TeacherEntity();
        newTeacher.setId(302L);
        newTeacher.setTeacherNo("T302");
        newTeacher.setTeacherName("Ms Sophia");
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(historical, oldPending, future));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(historicalRecord));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(oldSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher(), newTeacher));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null);

        assertThat(result).extracting(OneOnOneTeacherVO::teacherId).containsExactly(302L);
        OneOnOneTeacherVO newTeacherView = result.stream()
                .filter(item -> item.teacherId().equals(302L)).findFirst().orElseThrow();
        assertThat(newTeacherView.students()).singleElement().satisfies(student ->
                assertThat(student.packages()).singleElement().satisfies(pkg -> {
                    assertThat(pkg.lessons()).hasSize(3);
                    assertThat(pkg.lessons()).anySatisfy(lesson -> {
                        assertThat(lesson.occurrenceId()).isEqualTo(historical.getId());
                        assertThat(lesson.plannedTeacherId()).isEqualTo(301L);
                    });
                    assertThat(pkg.lessons()).anySatisfy(lesson -> {
                        assertThat(lesson.occurrenceId()).isEqualTo(oldPending.getId());
                        assertThat(lesson.plannedTeacherId()).isEqualTo(301L);
                    });
                    assertThat(pkg.teacherHours()).singleElement().satisfies(hours -> {
                        assertThat(hours.teacherId()).isEqualTo(302L);
                        assertThat(hours.allocatedUnits()).isEqualTo(4.0);
                        assertThat(hours.deductedUnits()).isZero();
                        assertThat(hours.remainingUnits()).isEqualTo(4.0);
                    });
                }));
        assertThat(historicalRecord.getOccurrenceId()).isEqualTo(historical.getId());
        assertThat(historicalRecord.getPlannedTeacherId()).isEqualTo(301L);
        assertThat(historicalRecord.getDeductedUnits()).isEqualTo(4);
        assertThat(lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), teacherUser())).isEmpty();
    }

    @Test
    void shouldKeepUnresolvedPastLessonVisibleUnderItsOriginalTeacherAcrossSchedulePeriods() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setRemainingUnits(12);

        RecurringScheduleRuleEntity oldRule = new RecurringScheduleRuleEntity();
        oldRule.setId(5001L);
        oldRule.setPackageId(coursePackage.getId());
        oldRule.setRuleStatus("ENDED");
        oldRule.setCreatedAt(now.minusDays(10));
        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(1));
        currentRule.setAllocationPeriodStartedAt(now.minusDays(1));

        RecurringScheduleSlotEntity oldSlot = new RecurringScheduleSlotEntity();
        oldSlot.setId(6001L);
        oldSlot.setRuleId(oldRule.getId());
        oldSlot.setSlotLineageId(7001L);
        oldSlot.setTeacherId(301L);
        oldSlot.setSlotStatus("DELETED");
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6002L);
        currentSlot.setRuleId(currentRule.getId());
        currentSlot.setSlotLineageId(7002L);
        currentSlot.setTeacherId(302L);
        currentSlot.setAllocatedUnits(12);
        currentSlot.setSlotStatus("ACTIVE");

        LessonOccurrenceEntity oldPending = buildOneOnOneOccurrence();
        oldPending.setScheduleRuleId(oldRule.getId());
        oldPending.setScheduleSlotId(oldSlot.getId());
        oldPending.setAllocationOwnerTeacherId(301L);
        oldPending.setPlannedTeacherId(301L);
        oldPending.setPlannedStartAt(now.minusDays(2));
        oldPending.setPlannedEndAt(now.minusDays(2).plusHours(2));
        oldPending.setPlannedUnits(4);

        LessonOccurrenceEntity currentFirst = buildOneOnOneOccurrence();
        currentFirst.setId(4002L);
        currentFirst.setLessonNo("O2O202610240002");
        currentFirst.setScheduleRuleId(currentRule.getId());
        currentFirst.setScheduleSlotId(currentSlot.getId());
        currentFirst.setAllocationOwnerTeacherId(302L);
        currentFirst.setPlannedTeacherId(302L);
        currentFirst.setPlannedStartAt(now.plusDays(1));
        currentFirst.setPlannedEndAt(now.plusDays(1).plusHours(2));
        currentFirst.setPlannedUnits(4);
        LessonOccurrenceEntity currentSecond = buildOneOnOneOccurrence();
        currentSecond.setId(4003L);
        currentSecond.setLessonNo("O2O202610240003");
        currentSecond.setScheduleRuleId(currentRule.getId());
        currentSecond.setScheduleSlotId(currentSlot.getId());
        currentSecond.setAllocationOwnerTeacherId(302L);
        currentSecond.setPlannedTeacherId(302L);
        currentSecond.setPlannedStartAt(now.plusDays(8));
        currentSecond.setPlannedEndAt(now.plusDays(8).plusHours(2));
        currentSecond.setPlannedUnits(4);

        TeacherEntity currentTeacher = new TeacherEntity();
        currentTeacher.setId(302L);
        currentTeacher.setTeacherNo("T302");
        currentTeacher.setTeacherName("Mr Roland");
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(List.of(oldPending, currentFirst, currentSecond));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(oldSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher(), currentTeacher));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null);

        assertThat(result).extracting(OneOnOneTeacherVO::teacherId).containsExactlyInAnyOrder(301L, 302L);
        OneOnOneTeacherVO oldTeacherView = result.stream()
                .filter(item -> item.teacherId().equals(301L)).findFirst().orElseThrow();
        assertThat(oldTeacherView.students().get(0).packages().get(0).lessons())
                .singleElement()
                .satisfies(lesson -> {
                    assertThat(lesson.occurrenceId()).isEqualTo(oldPending.getId());
                    assertThat(lesson.plannedTeacherId()).isEqualTo(301L);
                    assertThat(lesson.plannedUnits()).isEqualTo(2.0);
                });
        OneOnOneTeacherVO currentTeacherView = result.stream()
                .filter(item -> item.teacherId().equals(302L)).findFirst().orElseThrow();
        assertThat(currentTeacherView.students().get(0).packages().get(0).lessons()).hasSize(2);
        assertThat(currentTeacherView.students().get(0).packages().get(0).allocatedUnits()).isEqualTo(6.0);
        assertThat(currentTeacherView.students().get(0).packages().get(0).scheduledReservedUnits()).isEqualTo(6.0);

        List<OneOnOneTeacherVO> originalTeacherResult = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), teacherUser());
        assertThat(originalTeacherResult).singleElement().satisfies(teacher -> {
            assertThat(teacher.teacherId()).isEqualTo(301L);
            assertThat(teacher.students().get(0).packages().get(0).lessons())
                    .singleElement()
                    .satisfies(lesson -> assertThat(lesson.occurrenceId()).isEqualTo(oldPending.getId()));
        });
    }

    @Test
    void shouldKeepAllocatedHoursPackageWideAndExcludeConfirmedLessons() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setConsumedUnits(4);
        coursePackage.setRemainingUnits(16);
        LessonOccurrenceEntity completed = buildOneOnOneOccurrence();
        completed.setScheduleRuleId(5001L);
        completed.setScheduleSlotId(6001L);
        completed.setPlannedUnits(4);
        completed.setPlanningStatus("MERGED");
        LessonOccurrenceEntity scheduled = buildOneOnOneOccurrence();
        scheduled.setId(4002L);
        scheduled.setLessonNo("O2O202609010002");
        scheduled.setPlannedUnits(4);
        scheduled.setPlannedTeacherId(302L);
        scheduled.setScheduleRuleId(5001L);
        scheduled.setScheduleSlotId(6001L);
        scheduled.setPlannedStartAt(AppTime.nowLocalDateTime().plusDays(1));
        scheduled.setPlannedEndAt(scheduled.getPlannedStartAt().plusHours(2));
        LessonOccurrenceEntity cancelled = buildOneOnOneOccurrence();
        cancelled.setId(4003L);
        cancelled.setLessonNo("O2O202609010003");
        cancelled.setPlannedUnits(4);
        cancelled.setPlanningStatus("CANCELLED");
        cancelled.setScheduleRuleId(5001L);
        cancelled.setScheduleSlotId(6001L);
        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        RecurringScheduleSlotEntity activeSlot = new RecurringScheduleSlotEntity();
        activeSlot.setId(6001L);
        activeSlot.setRuleId(currentRule.getId());
        activeSlot.setTeacherId(302L);
        activeSlot.setAllocatedUnits(4);
        activeSlot.setSlotStatus("ACTIVE");
        LessonRecordEntity completedRecord = new LessonRecordEntity();
        completedRecord.setId(5001L);
        completedRecord.setOccurrenceId(4001L);
        completedRecord.setConfirmStage("CONFIRMED");

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(completed, scheduled, cancelled));
        when(lessonRecordMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(completedRecord)));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(activeSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(activeSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null);

        assertThat(result).allSatisfy(teacher -> assertThat(teacher.students()).singleElement()
                .satisfies(student -> assertThat(student.packages()).singleElement()
                        .satisfies(pkg -> {
                            assertThat(pkg.remainingUnits()).isEqualTo(8.0);
                            assertThat(pkg.allocatedUnits()).isEqualTo(2.0);
                            assertThat(pkg.teacherHours()).singleElement().satisfies(hours -> {
                                assertThat(hours.teacherId()).isEqualTo(302L);
                                assertThat(hours.allocatedUnits()).isEqualTo(2.0);
                                assertThat(hours.deductedUnits()).isZero();
                                assertThat(hours.remainingUnits()).isEqualTo(2.0);
                            });
                        })));
    }

    @Test
    void shouldCombinePastUnconfirmedAndFutureLessonsInTeacherRemainingHours() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity pastUnconfirmed = buildOneOnOneOccurrence();
        pastUnconfirmed.setScheduleRuleId(5001L);
        pastUnconfirmed.setScheduleSlotId(6001L);
        pastUnconfirmed.setPlannedStartAt(now.minusDays(2));
        pastUnconfirmed.setPlannedEndAt(now.minusDays(2).plusHours(1));
        LessonOccurrenceEntity futureScheduled = buildOneOnOneOccurrence();
        futureScheduled.setId(4002L);
        futureScheduled.setLessonNo("O2O202610170001");
        futureScheduled.setScheduleRuleId(5001L);
        futureScheduled.setScheduleSlotId(6001L);
        futureScheduled.setPlannedUnits(2);
        futureScheduled.setPlannedStartAt(now.plusDays(1));
        futureScheduled.setPlannedEndAt(futureScheduled.getPlannedStartAt().plusHours(1));

        LessonOccurrenceEntity pastConfirmed = buildOneOnOneOccurrence();
        pastConfirmed.setId(4003L);
        pastConfirmed.setLessonNo("O2O202609010003");
        pastConfirmed.setScheduleRuleId(5001L);
        pastConfirmed.setScheduleSlotId(6001L);
        pastConfirmed.setPlannedStartAt(now.minusDays(4));
        pastConfirmed.setPlannedEndAt(now.minusDays(4).plusHours(1));
        pastConfirmed.setPlannedUnits(4);
        LessonRecordEntity confirmedRecord = new LessonRecordEntity();
        confirmedRecord.setId(5003L);
        confirmedRecord.setOccurrenceId(4003L);
        confirmedRecord.setPackageId(coursePackage.getId());
        confirmedRecord.setConfirmStage("CONFIRMED");

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(45));
        RecurringScheduleSlotEntity activeSlot = new RecurringScheduleSlotEntity();
        activeSlot.setId(6001L);
        activeSlot.setRuleId(currentRule.getId());
        activeSlot.setTeacherId(301L);
        activeSlot.setAllocatedUnits(5);
        activeSlot.setCreatedAt(now.minusDays(30));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(List.of(pastUnconfirmed, futureScheduled, pastConfirmed));
        when(lessonRecordMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(confirmedRecord)));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(activeSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);

        assertThat(teacher.students().get(0).packages().get(0).teacherHours())
                .singleElement()
                .satisfies(hours -> {
                    assertThat(hours.allocatedUnits()).isEqualTo(2.5);
                    assertThat(hours.deductedUnits()).isZero();
                    assertThat(hours.remainingUnits()).isEqualTo(2.5);
                });
    }

    @Test
    void shouldKeepPendingAdminInTeacherRemainingAndCountConfirmedChargedCancelAsDeducted() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();

        LessonOccurrenceEntity chargedCancel = buildOneOnOneOccurrence();
        chargedCancel.setScheduleRuleId(5001L);
        chargedCancel.setScheduleSlotId(6001L);
        chargedCancel.setPlannedStartAt(now.minusDays(4));
        chargedCancel.setPlannedEndAt(now.minusDays(4).plusHours(2));
        chargedCancel.setPlannedUnits(4);
        LessonRecordEntity chargedCancelRecord = confirmedDeduction(5001L, chargedCancel, 4);
        chargedCancelRecord.setResultStatus("CANCELLED");

        LessonOccurrenceEntity pendingAdmin = buildOneOnOneOccurrence();
        pendingAdmin.setId(4002L);
        pendingAdmin.setScheduleRuleId(5001L);
        pendingAdmin.setScheduleSlotId(6001L);
        pendingAdmin.setPlannedStartAt(now.minusDays(2));
        pendingAdmin.setPlannedEndAt(now.minusDays(2).plusHours(2));
        pendingAdmin.setPlannedUnits(4);
        LessonRecordEntity pendingRecord = confirmedDeduction(5002L, pendingAdmin, 4);
        pendingRecord.setConfirmStage("PENDING_ADMIN");

        LessonOccurrenceEntity future = buildOneOnOneOccurrence();
        future.setId(4003L);
        future.setScheduleRuleId(5001L);
        future.setScheduleSlotId(6001L);
        future.setPlannedStartAt(now.plusDays(2));
        future.setPlannedEndAt(now.plusDays(2).plusHours(2));
        future.setPlannedUnits(4);

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(30));
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setRuleId(currentRule.getId());
        slot.setTeacherId(301L);
        slot.setAllocatedUnits(12);
        slot.setCreatedAt(now.minusDays(29));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(chargedCancel, pendingAdmin, future));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(chargedCancelRecord, pendingRecord));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages().get(0).teacherHours())
                .singleElement().satisfies(hours -> {
                    assertThat(hours.allocatedUnits()).isEqualTo(6.0);
                    assertThat(hours.deductedUnits()).isEqualTo(2.0);
                    assertThat(hours.remainingUnits()).isEqualTo(4.0);
                });
    }

    @Test
    void shouldNotInferLegacyScheduleChangeFromSlotCreationTime() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity lesson = buildOneOnOneOccurrence();
        lesson.setScheduleRuleId(5001L);
        lesson.setScheduleSlotId(6001L);
        lesson.setPlannedStartAt(now.minusDays(2));
        lesson.setPlannedUnits(4);
        lesson.setPlanningStatus("MERGED");
        LessonRecordEntity record = confirmedDeduction(5001L, lesson, 4);
        record.setConfirmedAt(now.minusDays(2));

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(30));
        RecurringScheduleSlotEntity activeSlot = new RecurringScheduleSlotEntity();
        activeSlot.setId(6001L);
        activeSlot.setRuleId(currentRule.getId());
        activeSlot.setTeacherId(301L);
        activeSlot.setAllocatedUnits(4);
        activeSlot.setCreatedAt(now.minusDays(1));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(lesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(activeSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages()).singleElement().satisfies(pkg -> {
            assertThat(pkg.scheduleChanges()).isEmpty();
            assertThat(pkg.teacherHours()).singleElement().satisfies(hours -> {
                assertThat(hours.allocatedUnits()).isEqualTo(2.0);
                assertThat(hours.deductedUnits()).isEqualTo(2.0);
                assertThat(hours.remainingUnits()).isZero();
            });
        });
    }

    @Test
    void shouldUseCurrentAllocationForTeacherRemainingWhenOccurrenceRowsAreIncomplete() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity pendingPast = buildOneOnOneOccurrence();
        pendingPast.setScheduleRuleId(5001L);
        pendingPast.setScheduleSlotId(6001L);
        pendingPast.setPlannedUnits(2);
        pendingPast.setPlannedStartAt(now.minusDays(2));
        pendingPast.setPlannedEndAt(now.minusDays(2).plusHours(1));
        LessonOccurrenceEntity future = buildOneOnOneOccurrence();
        future.setId(4002L);
        future.setScheduleRuleId(5001L);
        future.setScheduleSlotId(6001L);
        future.setPlannedUnits(2);
        future.setPlannedStartAt(now.plusDays(1));
        future.setPlannedEndAt(future.getPlannedStartAt().plusHours(1));

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(45));
        RecurringScheduleSlotEntity activeSlot = new RecurringScheduleSlotEntity();
        activeSlot.setId(6001L);
        activeSlot.setRuleId(currentRule.getId());
        activeSlot.setTeacherId(301L);
        activeSlot.setAllocatedUnits(10);
        activeSlot.setCreatedAt(now.minusDays(30));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(pendingPast, future));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(activeSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);

        assertThat(teacher.students().get(0).packages().get(0).teacherHours())
                .singleElement()
                .satisfies(hours -> {
                    assertThat(hours.allocatedUnits()).isEqualTo(5.0);
                    assertThat(hours.deductedUnits()).isZero();
                    // 两条课次只覆盖 2 小时，但当前周期分配额为 5 小时，剩余应按额度账本核算。
                    assertThat(hours.remainingUnits()).isEqualTo(5.0);
                });
    }

    @Test
    void currentTeacherReceivesWholeSlotLedgerAndHistoricalLessons() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setTotalUnits(40);
        coursePackage.setConsumedUnits(12);
        coursePackage.setRemainingUnits(28);
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(5001L);
        rule.setPackageId(coursePackage.getId());
        rule.setRuleStatus("ACTIVE");
        rule.setAllocationPeriodStartedAt(now.minusDays(30));
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setRuleId(rule.getId());
        slot.setTeacherId(302L);
        slot.setAllocatedUnits(16);
        slot.setSlotStatus("ACTIVE");
        LessonOccurrenceEntity completed = buildOneOnOneOccurrence();
        completed.setScheduleRuleId(rule.getId());
        completed.setScheduleSlotId(slot.getId());
        completed.setAllocationOwnerTeacherId(301L);
        completed.setPlannedStartAt(now.minusDays(7));
        completed.setPlannedEndAt(now.minusDays(7).plusHours(6));
        completed.setPlannedUnits(12);
        LessonRecordEntity record = confirmedDeduction(5001L, completed, 12);
        record.setActualTeacherId(301L);
        record.setPlannedTeacherId(301L);
        record.setConfirmedAt(now.minusDays(6));
        LessonOccurrenceEntity future = buildOneOnOneOccurrence();
        future.setId(4002L);
        future.setScheduleRuleId(rule.getId());
        future.setScheduleSlotId(slot.getId());
        future.setPlannedTeacherId(302L);
        future.setAllocationOwnerTeacherId(302L);
        future.setPlannedStartAt(now.plusDays(1));
        future.setPlannedEndAt(now.plusDays(1).plusHours(2));
        future.setPlannedUnits(4);
        TeacherEntity newTeacher = buildTeacher();
        newTeacher.setId(302L);
        newTeacher.setTeacherName("Ms New");
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(completed, future));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(slot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher(), newTeacher));

        List<OneOnOneTeacherVO> teachers = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null);
        assertThat(teachers).singleElement().satisfies(teacher -> {
            assertThat(teacher.teacherId()).isEqualTo(302L);
            var pkg = teacher.students().get(0).packages().get(0);
            assertThat(pkg.totalUnits()).isEqualTo(20.0);
            assertThat(pkg.remainingUnits()).isEqualTo(14.0);
            assertThat(pkg.lessons()).extracting(lesson -> lesson.occurrenceId())
                    .containsExactlyInAnyOrder(4001L, 4002L);
            assertThat(pkg.lessons()).anySatisfy(lesson -> {
                assertThat(lesson.occurrenceId()).isEqualTo(4001L);
                assertThat(lesson.plannedTeacherId()).isEqualTo(301L);
            });
            assertThat(pkg.teacherHours()).singleElement().satisfies(hours -> {
                assertThat(hours.allocatedUnits()).isEqualTo(8.0);
                assertThat(hours.deductedUnits()).isEqualTo(6.0);
                assertThat(hours.remainingUnits()).isEqualTo(2.0);
            });
        });
        assertThat(record.getActualTeacherId()).isEqualTo(301L);
        assertThat(record.getDeductedUnits()).isEqualTo(12);
    }

    @Test
    void shouldMoveLegacyTeacherTimelineToCurrentTeacherWhenSlotOwnershipChanges() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity pastUnconfirmed = buildOneOnOneOccurrence();
        pastUnconfirmed.setScheduleRuleId(5001L);
        pastUnconfirmed.setScheduleSlotId(6001L);
        pastUnconfirmed.setPlannedStartAt(now.minusDays(2));
        pastUnconfirmed.setPlannedEndAt(now.minusDays(2).plusHours(2));
        pastUnconfirmed.setPlannedUnits(4);
        LessonOccurrenceEntity future = buildOneOnOneOccurrence();
        future.setId(4002L);
        future.setScheduleRuleId(5001L);
        future.setScheduleSlotId(6001L);
        future.setPlannedTeacherId(302L);
        future.setPlannedStartAt(now.plusDays(1));
        future.setPlannedEndAt(future.getPlannedStartAt().plusHours(2));
        future.setPlannedUnits(12);

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(30));
        RecurringScheduleSlotEntity activeSlot = new RecurringScheduleSlotEntity();
        activeSlot.setId(6001L);
        activeSlot.setRuleId(currentRule.getId());
        activeSlot.setTeacherId(302L);
        activeSlot.setAllocatedUnits(16);
        activeSlot.setCreatedAt(now.minusDays(20));
        TeacherEntity newTeacher = buildTeacher();
        newTeacher.setId(302L);
        newTeacher.setTeacherName("Ms New");

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(pastUnconfirmed, future));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(activeSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(activeSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher(), newTeacher));

        List<OneOnOneTeacherVO> teachers = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null);

        assertThat(teachers).singleElement().satisfies(teacher -> {
            assertThat(teacher.teacherId()).isEqualTo(302L);
            assertThat(teacher.teacherName()).isEqualTo("Ms New");
            assertThat(teacher.students().get(0).packages()).singleElement().satisfies(pkg -> {
                assertThat(pkg.lessons()).extracting(lesson -> lesson.occurrenceId())
                        .containsExactly(4001L, 4002L);
                assertThat(pkg.allocatedUnits()).isEqualTo(8.0);
                assertThat(pkg.scheduledReservedUnits()).isEqualTo(8.0);
                assertThat(pkg.teacherHours()).singleElement().satisfies(hours -> {
                    assertThat(hours.teacherId()).isEqualTo(302L);
                    assertThat(hours.allocatedUnits()).isEqualTo(8.0);
                    assertThat(hours.deductedUnits()).isZero();
                    assertThat(hours.remainingUnits()).isEqualTo(8.0);
                });
            });
        });
    }

    @Test
    void shouldResetTeacherDeductedToCurrentScheduleVersionWithoutChangingPackageLedger() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setConsumedUnits(6);
        coursePackage.setRemainingUnits(14);
        LessonOccurrenceEntity oldLesson = buildOneOnOneOccurrence();
        oldLesson.setScheduleRuleId(5001L);
        oldLesson.setScheduleSlotId(6001L);
        oldLesson.setPlannedStartAt(now.minusDays(10));
        LessonOccurrenceEntity currentLesson = buildOneOnOneOccurrence();
        currentLesson.setId(4002L);
        currentLesson.setLessonNo("O2O202609010002");
        currentLesson.setScheduleRuleId(5002L);
        currentLesson.setScheduleSlotId(6002L);
        currentLesson.setPlannedStartAt(now.minusDays(1));
        LessonRecordEntity oldRecord = confirmedDeduction(5001L, oldLesson, 4);
        LessonRecordEntity currentRecord = confirmedDeduction(5002L, currentLesson, 2);
        oldRecord.setConfirmedAt(now.minusDays(9));
        currentRecord.setConfirmedAt(now.minusHours(12));
        com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity currentRule =
                new com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(2));
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6002L);
        currentSlot.setRuleId(currentRule.getId());
        currentSlot.setTeacherId(301L);
        currentSlot.setAllocatedUnits(4); // 2h current allocation snapshot
        currentSlot.setSlotStatus("ACTIVE");

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(oldLesson, currentLesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(oldRecord, currentRecord)));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(currentSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages()).singleElement().satisfies(pkg -> {
            assertThat(pkg.consumedUnits()).isEqualTo(3.0);
            assertThat(pkg.remainingUnits()).isEqualTo(7.0);
            assertThat(pkg.teacherHours()).singleElement()
                    .satisfies(hours -> {
                        assertThat(hours.allocatedUnits()).isEqualTo(2.0);
                        assertThat(hours.deductedUnits()).isEqualTo(1.0);
                        assertThat(hours.remainingUnits()).isEqualTo(1.0);
                    });
            assertThat(pkg.lessons()).hasSize(2);
        });
    }

    @Test
    void shouldResetTeacherHoursWhenLegacyEditReplacesSlotsUnderTheSameRule() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        LocalDateTime lastAllocationEdit = now.minusDays(1);
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setConsumedUnits(4);
        coursePackage.setRemainingUnits(16);

        LessonOccurrenceEntity oldConfirmed = buildOneOnOneOccurrence();
        oldConfirmed.setScheduleRuleId(5001L);
        oldConfirmed.setPlannedStartAt(now.minusDays(4));
        oldConfirmed.setPlanningStatus("MERGED");
        LessonRecordEntity oldRecord = confirmedDeduction(5001L, oldConfirmed, 4);
        oldRecord.setConfirmedAt(now.minusDays(3));

        LessonOccurrenceEntity oldUnconfirmed = buildOneOnOneOccurrence();
        oldUnconfirmed.setId(4002L);
        oldUnconfirmed.setScheduleRuleId(5001L);
        oldUnconfirmed.setPlannedStartAt(now.minusDays(2));

        List<LessonOccurrenceEntity> occurrences = new ArrayList<>(List.of(oldConfirmed, oldUnconfirmed));
        for (int index = 0; index < 4; index++) {
            LessonOccurrenceEntity future = buildOneOnOneOccurrence();
            future.setId(4003L + index);
            future.setScheduleRuleId(5001L);
            future.setScheduleSlotId(6001L);
            future.setPlannedStartAt(now.plusDays(index + 1));
            future.setPlannedEndAt(future.getPlannedStartAt().plusHours(2));
            future.setPlannedUnits(4);
            occurrences.add(future);
        }

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(30));
        currentRule.setAllocationPeriodStartedAt(lastAllocationEdit);
        RecurringScheduleSlotEntity activeSlot = new RecurringScheduleSlotEntity();
        activeSlot.setId(6001L);
        activeSlot.setRuleId(currentRule.getId());
        activeSlot.setTeacherId(301L);
        activeSlot.setAllocatedUnits(16);
        activeSlot.setCreatedAt(lastAllocationEdit);

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(occurrences);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(oldRecord));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(activeSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages()).singleElement().satisfies(pkg -> {
            assertThat(pkg.consumedUnits()).isEqualTo(2.0);
            assertThat(pkg.scheduleChanges()).singleElement().satisfies(change -> {
                assertThat(change.ruleId()).isEqualTo(5001L);
                assertThat(change.changedAt()).isEqualTo(lastAllocationEdit);
                assertThat(change.legacySlotBoundary()).isTrue();
            });
            assertThat(pkg.teacherHours()).singleElement().satisfies(hours -> {
                assertThat(hours.allocatedUnits()).isEqualTo(8.0);
                assertThat(hours.deductedUnits()).isZero();
                assertThat(hours.remainingUnits()).isEqualTo(8.0);
            });
            assertThat(pkg.lessons()).hasSize(6);
        });
    }

    @Test
    void shouldKeepLegacyDeductionAfterBalanceRefreshRebuildsSlots() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity previousLesson = buildOneOnOneOccurrence();
        previousLesson.setScheduleRuleId(5001L);
        previousLesson.setPlannedStartAt(now.minusDays(4));
        LessonRecordEntity previousRecord = confirmedDeduction(5001L, previousLesson, 4);
        previousRecord.setConfirmedAt(now.minusDays(3));

        LessonOccurrenceEntity currentLesson = buildOneOnOneOccurrence();
        currentLesson.setId(4002L);
        currentLesson.setScheduleRuleId(5001L);
        currentLesson.setScheduleSlotId(6001L);
        currentLesson.setPlannedStartAt(now.minusHours(3));
        currentLesson.setPlannedUnits(4);
        LessonRecordEntity currentRecord = confirmedDeduction(5002L, currentLesson, 4);
        currentRecord.setConfirmedAt(now.minusHours(1));
        List<LessonOccurrenceEntity> occurrences = new ArrayList<>(List.of(previousLesson, currentLesson));
        for (int index = 0; index < 3; index++) {
            LessonOccurrenceEntity future = buildOneOnOneOccurrence();
            future.setId(4003L + index);
            future.setScheduleRuleId(5001L);
            future.setScheduleSlotId(6001L);
            future.setPlannedStartAt(now.plusDays(index + 1));
            future.setPlannedEndAt(future.getPlannedStartAt().plusHours(2));
            future.setPlannedUnits(4);
            occurrences.add(future);
        }

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5001L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(30));
        currentRule.setAllocationPeriodStartedAt(now.minusDays(1));
        RecurringScheduleSlotEntity activeSlot = new RecurringScheduleSlotEntity();
        activeSlot.setId(6001L);
        activeSlot.setRuleId(currentRule.getId());
        activeSlot.setTeacherId(301L);
        activeSlot.setAllocatedUnits(16);
        activeSlot.setCreatedAt(now.minusMinutes(30));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(occurrences);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(previousRecord, currentRecord));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(activeSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages()).singleElement().satisfies(pkg ->
                assertThat(pkg.teacherHours()).singleElement().satisfies(hours -> {
                    assertThat(hours.allocatedUnits()).isEqualTo(8.0);
                    assertThat(hours.deductedUnits()).isEqualTo(2.0);
                    assertThat(hours.remainingUnits()).isEqualTo(6.0);
                }));
    }

    @Test
    void shouldUseNewRuleCreationTimeWhenItsSlotsAreRebuiltWithoutScheduleChange() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity lesson = buildOneOnOneOccurrence();
        lesson.setScheduleRuleId(5002L);
        lesson.setScheduleSlotId(6002L);
        lesson.setPlannedStartAt(now.minusDays(3));
        LessonRecordEntity record = confirmedDeduction(5002L, lesson, 2);
        record.setConfirmedAt(now.minusDays(2));

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setSupersedesRuleId(5001L);
        currentRule.setCreatedAt(now.minusDays(5));
        RecurringScheduleSlotEntity rebuiltSlot = new RecurringScheduleSlotEntity();
        rebuiltSlot.setId(6002L);
        rebuiltSlot.setRuleId(currentRule.getId());
        rebuiltSlot.setTeacherId(301L);
        rebuiltSlot.setAllocatedUnits(4);
        rebuiltSlot.setCreatedAt(now.minusDays(1));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(lesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(rebuiltSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages().get(0).teacherHours())
                .singleElement().satisfies(hours -> {
                    assertThat(hours.allocatedUnits()).isEqualTo(2.0);
                    assertThat(hours.deductedUnits()).isEqualTo(1.0);
                    assertThat(hours.remainingUnits()).isEqualTo(1.0);
                });
    }

    @Test
    void shouldKeepCurrentAllocationFixedWhenPreservedLessonDeductsDifferentHours() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity preserved = buildOneOnOneOccurrence();
        preserved.setScheduleRuleId(5001L);
        preserved.setScheduleSlotId(6001L);
        preserved.setPlanningStatus("ADJUSTED");
        preserved.setPlannedStartAt(now.minusDays(1));
        preserved.setPlannedUnits(2);
        LessonRecordEntity record = confirmedDeduction(5001L, preserved, 4);
        record.setConfirmedAt(now.minusHours(1));

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setSupersedesRuleId(5001L);
        currentRule.setCreatedAt(now.minusDays(3));
        RecurringScheduleSlotEntity historicalSlot = new RecurringScheduleSlotEntity();
        historicalSlot.setId(6001L);
        historicalSlot.setRuleId(5001L);
        historicalSlot.setSlotLineageId(7001L);
        historicalSlot.setTeacherId(301L);
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6002L);
        currentSlot.setRuleId(currentRule.getId());
        currentSlot.setSlotLineageId(7001L);
        currentSlot.setTeacherId(301L);
        currentSlot.setAllocatedUnits(4);
        currentSlot.setCreatedAt(now.minusDays(3));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(preserved));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(historicalSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages().get(0).teacherHours())
                .singleElement().satisfies(hours -> {
                    assertThat(hours.allocatedUnits()).isEqualTo(2.0);
                    assertThat(hours.deductedUnits()).isEqualTo(2.0);
                    assertThat(hours.remainingUnits()).isZero();
                });
    }

    @Test
    void shouldNotStartNewAllocationPeriodOrAddHoursWhenFormalLeaveCreatesMakeup() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity completed = buildOneOnOneOccurrence();
        completed.setScheduleRuleId(5002L);
        completed.setScheduleSlotId(6002L);
        completed.setPlannedStartAt(now.minusDays(2));
        LessonRecordEntity completedRecord = confirmedDeduction(7001L, completed, 4);
        completedRecord.setConfirmedAt(now.minusDays(1));

        LessonOccurrenceEntity leaveSource = buildOneOnOneOccurrence();
        leaveSource.setId(4002L);
        leaveSource.setScheduleRuleId(5001L);
        leaveSource.setScheduleSlotId(6001L);
        leaveSource.setPlannedStartAt(now.minusDays(1));
        leaveSource.setPlannedUnits(4);
        leaveSource.setPlanningStatus("MERGED");
        LessonRecordEntity leaveRecord = new LessonRecordEntity();
        leaveRecord.setId(7002L);
        leaveRecord.setOccurrenceId(leaveSource.getId());
        leaveRecord.setPackageId(coursePackage.getId());
        leaveRecord.setConfirmStage("CONFIRMED");
        leaveRecord.setResultStatus("FORMAL_LEAVE");
        leaveRecord.setShouldDeduct(0);
        leaveRecord.setDeductedUnits(0);
        leaveRecord.setConfirmedAt(now.minusHours(1));

        LessonOccurrenceEntity makeup = buildOneOnOneOccurrence();
        makeup.setId(4003L);
        makeup.setScheduleRuleId(5001L);
        makeup.setScheduleSlotId(6001L);
        makeup.setOccurrenceType("MAKEUP");
        makeup.setSourceOccurrenceId(leaveSource.getId());
        makeup.setPlannedStartAt(now.plusDays(1));
        makeup.setPlannedUnits(4);

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setSupersedesRuleId(5001L);
        currentRule.setCreatedAt(now.minusDays(3));
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6002L);
        currentSlot.setRuleId(currentRule.getId());
        currentSlot.setTeacherId(301L);
        currentSlot.setAllocatedUnits(12);
        currentSlot.setCreatedAt(now.minusDays(3));

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(completed, leaveSource, makeup));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(completedRecord, leaveRecord));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages().get(0).teacherHours())
                .singleElement().satisfies(hours -> {
                    assertThat(hours.allocatedUnits()).isEqualTo(6.0);
                    assertThat(hours.deductedUnits()).isEqualTo(2.0);
                    assertThat(hours.remainingUnits()).isEqualTo(4.0);
                });
    }

    @Test
    void shouldCountPreservedFutureExceptionAfterEditButNotLateConfirmedOldLesson() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setConsumedUnits(6);
        coursePackage.setRemainingUnits(14);
        LessonOccurrenceEntity oldLesson = buildOneOnOneOccurrence();
        oldLesson.setScheduleRuleId(5001L);
        oldLesson.setPlannedStartAt(LocalDateTime.of(2026, 9, 20, 15, 30));
        LessonOccurrenceEntity preservedException = buildOneOnOneOccurrence();
        preservedException.setId(4002L);
        preservedException.setLessonNo("O2O202610030001");
        preservedException.setScheduleRuleId(5001L);
        preservedException.setPlanningStatus("ADJUSTED");
        preservedException.setPlannedStartAt(LocalDateTime.of(2026, 10, 3, 15, 30));
        preservedException.setPlannedUnits(2);
        LessonOccurrenceEntity newLesson = buildOneOnOneOccurrence();
        newLesson.setId(4003L);
        newLesson.setLessonNo("O2O202610050001");
        newLesson.setScheduleRuleId(5002L);
        newLesson.setPlannedStartAt(LocalDateTime.of(2026, 10, 5, 15, 30));
        LessonRecordEntity lateOldRecord = confirmedDeduction(5001L, oldLesson, 2);
        lateOldRecord.setConfirmedAt(LocalDateTime.of(2026, 10, 4, 12, 0));
        LessonRecordEntity exceptionRecord = confirmedDeduction(5002L, preservedException, 2);
        exceptionRecord.setConfirmedAt(LocalDateTime.of(2026, 10, 4, 12, 0));
        LessonRecordEntity newRecord = confirmedDeduction(5003L, newLesson, 2);
        newRecord.setConfirmedAt(LocalDateTime.of(2026, 10, 6, 12, 0));
        com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity currentRule =
                new com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setSupersedesRuleId(5001L);
        currentRule.setCreatedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6001L);
        currentSlot.setRuleId(currentRule.getId());
        currentSlot.setTeacherId(301L);
        currentSlot.setAllocatedUnits(2);
        currentSlot.setCreatedAt(LocalDateTime.of(2026, 10, 1, 12, 0, 1));
        newLesson.setScheduleSlotId(currentSlot.getId());
        RecurringScheduleSlotEntity exceptionSlot = new RecurringScheduleSlotEntity();
        exceptionSlot.setId(6002L);
        exceptionSlot.setRuleId(5001L);
        exceptionSlot.setTeacherId(301L);
        exceptionSlot.setWeekday(6);
        exceptionSlot.setStartTime(LocalTime.of(15, 30));
        exceptionSlot.setEndTime(LocalTime.of(17, 0));
        exceptionSlot.setPlannedUnits(2);
        exceptionSlot.setSlotStatus("DELETED");
        preservedException.setScheduleSlotId(exceptionSlot.getId());
        currentSlot.setWeekday(5);
        currentSlot.setStartTime(LocalTime.of(15, 30));
        currentSlot.setEndTime(LocalTime.of(17, 0));
        currentSlot.setPlannedUnits(2);

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(oldLesson, preservedException, newLesson));
        when(lessonRecordMapper.selectList(any()))
                .thenReturn(new ArrayList<>(List.of(lateOldRecord, exceptionRecord, newRecord)));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any()))
                .thenReturn(List.of(currentSlot, exceptionSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        assertThat(teacher.students().get(0).packages()).singleElement().satisfies(pkg -> {
            assertThat(pkg.consumedUnits()).isEqualTo(3.0);
            assertThat(pkg.teacherHours()).singleElement()
                    .satisfies(hours -> {
                        assertThat(hours.allocatedUnits()).isEqualTo(2.0);
                        assertThat(hours.deductedUnits()).isEqualTo(2.0);
                    });
        });
    }

    @Test
    void shouldKeepOneOnOneHoursConsistentAfterEndedUnconfirmedLessonIsReclaimed() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setTotalUnits(120); // 60h purchased
        coursePackage.setOpeningUsedUnits(40); // 20h were used before entering the system
        coursePackage.setConsumedUnits(8); // 4h deducted in the system
        coursePackage.setRemainingUnits(72); // 36h package balance

        RecurringScheduleRuleEntity currentRule = new RecurringScheduleRuleEntity();
        currentRule.setId(5002L);
        currentRule.setPackageId(coursePackage.getId());
        currentRule.setRuleStatus("ACTIVE");
        currentRule.setCreatedAt(now.minusDays(30));
        currentRule.setAllocationPeriodStartedAt(now.minusDays(30));

        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6002L);
        currentSlot.setRuleId(currentRule.getId());
        currentSlot.setSlotLineageId(7002L);
        currentSlot.setTeacherId(301L);
        currentSlot.setAllocatedUnits(24); // 12h allocated in the current period
        currentSlot.setSlotStatus("ACTIVE");

        LessonOccurrenceEntity confirmedLesson = buildOneOnOneOccurrence();
        confirmedLesson.setScheduleRuleId(currentRule.getId());
        confirmedLesson.setScheduleSlotId(currentSlot.getId());
        confirmedLesson.setAllocationOwnerTeacherId(301L);
        confirmedLesson.setPlannedStartAt(now.minusDays(7));
        confirmedLesson.setPlannedEndAt(now.minusDays(7).plusHours(2));
        confirmedLesson.setPlannedUnits(4);
        confirmedLesson.setPlanningStatus("MERGED");
        LessonRecordEntity confirmedRecord = confirmedDeduction(5001L, confirmedLesson, 4);
        confirmedRecord.setConfirmedAt(now.minusDays(6));
        confirmedRecord.setPlannedTeacherId(301L);
        confirmedRecord.setResultStatus("COMPLETED");

        LessonOccurrenceEntity oldPendingLesson = buildOneOnOneOccurrence();
        oldPendingLesson.setId(4003L);
        oldPendingLesson.setLessonNo("O2O-PENDING-4003");
        oldPendingLesson.setScheduleRuleId(currentRule.getId());
        oldPendingLesson.setScheduleSlotId(currentSlot.getId());
        oldPendingLesson.setAllocationOwnerTeacherId(301L);
        oldPendingLesson.setPlannedStartAt(now.minusDays(2));
        oldPendingLesson.setPlannedEndAt(now.minusDays(2).plusHours(2));
        oldPendingLesson.setPlannedUnits(4);
        LessonRecordEntity pendingRecord = new LessonRecordEntity();
        pendingRecord.setId(5002L);
        pendingRecord.setOccurrenceId(oldPendingLesson.getId());
        pendingRecord.setPackageId(coursePackage.getId());
        pendingRecord.setConfirmStage("PENDING_ADMIN");
        pendingRecord.setShouldDeduct(1);
        pendingRecord.setDeductedUnits(4);
        pendingRecord.setPlannedTeacherId(301L);

        LessonOccurrenceEntity futureLesson = buildOneOnOneOccurrence();
        futureLesson.setId(4002L);
        futureLesson.setLessonNo("O2O-FUTURE-4002");
        futureLesson.setScheduleRuleId(currentRule.getId());
        futureLesson.setScheduleSlotId(currentSlot.getId());
        futureLesson.setAllocationOwnerTeacherId(301L);
        futureLesson.setPlannedStartAt(now.plusDays(1));
        futureLesson.setPlannedEndAt(now.plusDays(1).plusHours(6));
        futureLesson.setPlannedUnits(12);

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        // 首次读取模拟修改前；第二次模拟 Edit Schedule 已软删除待处理课次和提交。
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(
                List.of(confirmedLesson, oldPendingLesson, futureLesson),
                List.of(confirmedLesson, futureLesson));
        // 每次页面查询先取课次记录，再取 PENDING_ADMIN 的扣课预留。
        when(lessonRecordMapper.selectList(any())).thenReturn(
                List.of(confirmedRecord, pendingRecord), List.of(pendingRecord),
                List.of(confirmedRecord), Collections.emptyList());
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(currentRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO beforeEdit = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        var beforePackage = beforeEdit.students().get(0).packages().get(0);
        assertThat(beforePackage.pendingDeductionUnits()).isEqualTo(2.0);
        assertThat(beforePackage.lessons()).extracting(lesson -> lesson.occurrenceId())
                .contains(oldPendingLesson.getId());
        assertThat(beforePackage.teacherHours()).singleElement().satisfies(hours -> {
            assertThat(hours.allocatedUnits()).isEqualTo(12.0);
            assertThat(hours.deductedUnits()).isEqualTo(2.0);
            assertThat(hours.remainingUnits()).isEqualTo(10.0);
        });

        OneOnOneTeacherVO afterEdit = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);
        var pkg = afterEdit.students().get(0).packages().get(0);

        // Package header fields retain their ledger meaning; reclaiming an unconfirmed lesson
        // does not add back balance or count it as a deduction.
        assertThat(afterEdit.totalUnits()).isEqualTo(60.0);
        assertThat(afterEdit.consumedUnits()).isEqualTo(4.0);
        assertThat(afterEdit.remainingUnits()).isEqualTo(36.0);
        assertThat(pkg.totalUnits()).isEqualTo(60.0);
        assertThat(pkg.startingHours()).isEqualTo(40.0);
        assertThat(pkg.consumedUnits()).isEqualTo(4.0);
        assertThat(pkg.remainingUnits()).isEqualTo(36.0);
        assertThat(pkg.usableRemainingUnits()).isEqualTo(36.0);
        assertThat(pkg.allocatedUnits()).isEqualTo(12.0);
        assertThat(pkg.pendingDeductionUnits()).isZero();
        assertThat(pkg.scheduledReservedUnits()).isEqualTo(6.0);

        // Teacher fields follow the active schedule period: Allocated = Deducted + Remaining.
        assertThat(pkg.teacherHours()).singleElement().satisfies(hours -> {
            assertThat(hours.allocatedUnits()).isEqualTo(12.0);
            assertThat(hours.deductedUnits()).isEqualTo(2.0);
            assertThat(hours.remainingUnits()).isEqualTo(10.0);
            assertThat(hours.allocatedUnits()).isEqualTo(hours.deductedUnits() + hours.remainingUnits());
        });

        // The finalized history and future lesson remain visible; reclaimed pending history is absent.
        assertThat(pkg.lessons()).extracting(lesson -> lesson.occurrenceId())
                .containsExactlyInAnyOrder(confirmedLesson.getId(), futureLesson.getId());
    }

    private LessonRecordEntity confirmedDeduction(Long id, LessonOccurrenceEntity occurrence, int units) {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setId(id);
        record.setOccurrenceId(occurrence.getId());
        record.setPackageId(occurrence.getPackageId());
        record.setConfirmStage("CONFIRMED");
        record.setShouldDeduct(1);
        record.setDeductedUnits(units);
        return record;
    }

    @Test
    void shouldUseEachParticipantsOwnConfirmationForSharedLessonAllocation() {
        CoursePackageEntity primaryPackage = buildOneOnOnePackage();
        CoursePackageEntity otherPackage = buildOneOnOnePackage();
        otherPackage.setId(3002L);
        otherPackage.setStudentId(102L);
        LessonOccurrenceEntity sharedLesson = buildOneOnOneOccurrence();
        sharedLesson.setPrivateLessonGroupId(7001L);
        sharedLesson.setPackageId(null);
        PrivateLessonGroupMemberEntity primary = buildPrivateGroupMember(1, 101L);
        primary.setPackageId(3001L);
        PrivateLessonGroupMemberEntity other = buildPrivateGroupMember(2, 102L);
        other.setPackageId(3002L);
        LessonRecordEntity primaryRecord = new LessonRecordEntity();
        primaryRecord.setId(5001L);
        primaryRecord.setOccurrenceId(4001L);
        primaryRecord.setPackageId(3001L);
        primaryRecord.setConfirmStage("CONFIRMED");
        LessonRecordEntity otherRecord = new LessonRecordEntity();
        otherRecord.setId(5002L);
        otherRecord.setOccurrenceId(4001L);
        otherRecord.setPackageId(3002L);
        otherRecord.setConfirmStage("PENDING_ADMIN");
        RecurringScheduleRuleEntity sharedRule = new RecurringScheduleRuleEntity();
        sharedRule.setId(5001L);
        sharedRule.setPrivateLessonGroupId(7001L);
        sharedRule.setRuleStatus("ACTIVE");
        sharedRule.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        StudentEntity otherStudent = buildStudent();
        otherStudent.setId(102L);
        otherStudent.setStudentName("Other Student");

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(primaryPackage, otherPackage));
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(otherPackage));
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(List.of(primary, other));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(sharedLesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(primaryRecord, otherRecord));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(sharedRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent(), otherStudent));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        OneOnOneTeacherVO teacher = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null).get(0);

        assertThat(teacher.students()).singleElement().satisfies(student ->
                assertThat(student.packages()).singleElement().satisfies(pkg -> {
                    assertThat(pkg.allocatedUnits()).isZero();
                    assertThat(pkg.lessons()).singleElement()
                            .satisfies(lesson -> assertThat(lesson.confirmStage()).isEqualTo("CONFIRMED"));
                }));
    }

    @Test
    void shouldLimitTeacherDeductionViewToCurrentTeachersLessons() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        LessonOccurrenceEntity ownLesson = buildOneOnOneOccurrence();
        LessonOccurrenceEntity otherTeacherLesson = buildOneOnOneOccurrence();
        otherTeacherLesson.setId(4002L);
        otherTeacherLesson.setLessonNo("O2O202609010002");
        otherTeacherLesson.setPlannedTeacherId(302L);

        OneOnOneDeductionQuery query = new OneOnOneDeductionQuery();
        query.setTeacherId(302L);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        // 首次查询用于限定老师课包；随后加载共享课包中的完整课次，再由返回组别隔离。
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(List.of(ownLesson), List.of(ownLesson, otherTeacherLesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(query, teacherUser());

        assertThat(result).singleElement().satisfies(teacher -> {
            assertThat(teacher.teacherId()).isEqualTo(301L);
            assertThat(teacher.students()).singleElement()
                    .satisfies(student -> assertThat(student.packages()).singleElement()
                            .satisfies(pkg -> assertThat(pkg.lessons()).singleElement()
                                    .satisfies(lesson -> {
                                        assertThat(lesson.occurrenceId()).isEqualTo(ownLesson.getId());
                                        assertThat(lesson.plannedTeacherId()).isEqualTo(301L);
                                    })));
        });
    }

    @Test
    void shouldKeepCompletedPackageLessonHistoryInDeductionQuery() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setPackageStatus("COMPLETED");
        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null);

        assertThat(result).singleElement()
                .satisfies(teacher -> assertThat(teacher.students()).singleElement()
                        .satisfies(student -> assertThat(student.packages()).singleElement()
                                .satisfies(pkg -> assertThat(pkg.lessons()).singleElement()
                                        .satisfies(lesson -> assertThat(lesson.occurrenceId()).isEqualTo(4001L)))));
    }

    @Test
    void shouldUseExplicitPackageBonusInsteadOfSubtractingLatestOrderFromAccumulatedTotal() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage();
        coursePackage.setEnrollmentOrderItemId(9001L);
        coursePackage.setTotalUnits(324); // 162h accumulated across multiple purchases.
        coursePackage.setBonusUnits(4); // 2h explicit bonus.
        EnrollmentOrderItemEntity latestOrderItem = new EnrollmentOrderItemEntity();
        latestOrderItem.setId(9001L);
        latestOrderItem.setOneOnOneHours(new java.math.BigDecimal("40.0"));
        latestOrderItem.setOneOnOneUnits(84); // 42h for latest order, including its 2h bonus.

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(enrollmentOrderItemMapper.selectBatchIds(any())).thenReturn(List.of(latestOrderItem));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(
                new OneOnOneDeductionQuery(), null);

        assertThat(result).singleElement()
                .satisfies(teacher -> assertThat(teacher.students()).singleElement()
                        .satisfies(student -> assertThat(student.packages()).singleElement().satisfies(pkg -> {
                            assertThat(pkg.totalUnits()).isEqualTo(162.0);
                            assertThat(pkg.purchasedUnits()).isEqualTo(160.0);
                            assertThat(pkg.bonusUnits()).isEqualTo(2.0);
                        })));
    }

    @Test
    void shouldExcludeLegacyGroupPackagesFromOneOnOneDeductionQuery() {
        CoursePackageEntity oneOnOnePackage = buildOneOnOnePackage();
        oneOnOnePackage.setCourseProductId(9001L);
        CoursePackageEntity legacyGroupPackage = buildOneOnOnePackage();
        legacyGroupPackage.setId(3002L);
        legacyGroupPackage.setPackageNo("PK3002");
        legacyGroupPackage.setPackageName("Group SHSAT");
        legacyGroupPackage.setStudentId(102L);
        legacyGroupPackage.setCourseProductId(9002L);
        legacyGroupPackage.setTeachingMode(null);

        LessonOccurrenceEntity occurrence = buildOneOnOneOccurrence();
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(oneOnOnePackage, legacyGroupPackage));
        when(courseProductMapper.selectBatchIds(any())).thenReturn(List.of(
                buildCourseProduct(9001L, "1:1 Algebra", "ONE_ON_ONE"),
                buildCourseProduct(9002L, "SHSAT Group", "GROUP")
        ));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence), List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        List<OneOnOneTeacherVO> result = lessonOccurrenceService.listOneOnOneDeductions(new OneOnOneDeductionQuery(), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).students()).singleElement()
                .satisfies(student -> assertThat(student.packages())
                        .singleElement()
                        .satisfies(pkg -> assertThat(pkg.packageId()).isEqualTo(3001L)));
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
    void shouldBackfillSingleOccurrenceClassroomForRoomAndStudentCalendars() {
        LessonOccurrenceEntity pending = buildOneOnOneOccurrence();
        pending.setPlannedClassroomId(null);
        ClassroomEntity classroom = buildClassroom();
        when(classroomMapper.selectByIdForUpdate(601L)).thenReturn(classroom);
        when(lessonOccurrenceMapper.selectById(4001L)).thenReturn(pending);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.updateById(any(LessonOccurrenceEntity.class))).thenReturn(1);
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(buildOneOnOnePackage()));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(buildTeacher()));

        PendingClassroomBackfillRequest request = new PendingClassroomBackfillRequest();
        request.setTargetType("OCCURRENCE");
        request.setTargetId(4001L);
        request.setClassroomId(601L);

        PendingClassroomAssignmentVO result = lessonOccurrenceService.backfillPendingClassroom(request);

        ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor = ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper).updateById(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getValue().getPlannedClassroomId()).isEqualTo(601L);
        assertThat(result.targetType()).isEqualTo("OCCURRENCE");
        assertThat(result.targetId()).isEqualTo(4001L);
    }

    @Test
    void shouldBackfillClassGroupAndFutureOccurrencesForRoomCalendar() {
        ClassGroupEntity classGroup = buildClassGroup();
        LessonOccurrenceEntity first = buildClassOccurrence(LocalDateTime.now().plusDays(2));
        first.setPlannedClassroomId(null);
        LessonOccurrenceEntity second = buildClassOccurrence(LocalDateTime.now().plusDays(9));
        second.setId(4002L);
        second.setPlannedClassroomId(null);
        ClassroomEntity classroom = buildClassroom();
        when(classroomMapper.selectByIdForUpdate(601L)).thenReturn(classroom);
        when(classGroupMapper.selectById(7001L)).thenReturn(classGroup);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(first, second), Collections.emptyList(), Collections.emptyList());
        when(lessonOccurrenceMapper.updateById(any(LessonOccurrenceEntity.class))).thenReturn(1);

        PendingClassroomBackfillRequest request = new PendingClassroomBackfillRequest();
        request.setTargetType("CLASS_GROUP");
        request.setTargetId(7001L);
        request.setClassroomId(601L);

        PendingClassroomAssignmentVO result = lessonOccurrenceService.backfillPendingClassroom(request);

        var lockOrder = inOrder(classGroupMapper, classroomMapper);
        lockOrder.verify(classGroupMapper).selectByIdForUpdate(7001L);
        lockOrder.verify(classroomMapper).selectByIdForUpdate(601L);

        ArgumentCaptor<ClassGroupEntity> classCaptor = ArgumentCaptor.forClass(ClassGroupEntity.class);
        ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor = ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(classGroupMapper).updateById(classCaptor.capture());
        verify(lessonOccurrenceMapper, org.mockito.Mockito.times(2)).updateById(occurrenceCaptor.capture());
        assertThat(classCaptor.getValue().getClassroomId()).isEqualTo(601L);
        assertThat(occurrenceCaptor.getAllValues())
                .extracting(LessonOccurrenceEntity::getPlannedClassroomId)
                .containsExactly(601L, 601L);
        assertThat(result.targetType()).isEqualTo("CLASS_GROUP");
        assertThat(result.targetId()).isEqualTo(7001L);
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

    private PrivateLessonGroupMemberEntity buildPrivateGroupMember(int index, long studentId) {
        PrivateLessonGroupMemberEntity member = new PrivateLessonGroupMemberEntity();
        member.setPrivateLessonGroupId(7001L);
        member.setStudentId(studentId);
        member.setParticipantIndex(index);
        member.setMemberStatus("ACTIVE");
        return member;
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
        return buildCourseProduct(9001L, "SHSAT Prep", null);
    }

    private CourseProductEntity buildCourseProduct(Long id, String productName, String teachingMode) {
        CourseProductEntity product = new CourseProductEntity();
        product.setId(id);
        product.setProductName(productName);
        product.setTeachingMode(teachingMode);
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
