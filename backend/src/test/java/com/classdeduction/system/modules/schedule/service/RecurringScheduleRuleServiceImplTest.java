package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.vo.OneOnOneScheduleCourseRow;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleCreateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleUpdateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleSlotRequest;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.impl.RecurringScheduleRuleServiceImpl;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleCreateResultVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleUpdateResultVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleAllocationErrorVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleStudentOptionVO;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排课规则服务测试：覆盖规则创建成功和冲突阻断这两个最关键的排课场景。
 */
@ExtendWith(MockitoExtension.class)
class RecurringScheduleRuleServiceImplTest {

    @Mock
    private RecurringScheduleRuleMapper recurringScheduleRuleMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.RecurringScheduleStartingBalanceMapper startingBalanceMapper;

    @Mock
    private RecurringScheduleSlotMapper recurringScheduleSlotMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonAdjustmentLogMapper lessonAdjustmentLogMapper;

    @Mock
    private JsonHelper jsonHelper;

    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PrivateLessonGroupMapper privateLessonGroupMapper;

    @Mock
    private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private com.classdeduction.system.modules.base.mapper.CourseProductMapper courseProductMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Captor
    private ArgumentCaptor<RecurringScheduleRuleEntity> ruleCaptor;

    @Captor
    private ArgumentCaptor<RecurringScheduleSlotEntity> slotCaptor;

    @Captor
    private ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor;

    private RecurringScheduleRuleServiceImpl recurringScheduleRuleService;
    private long nextSlotId;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单元测试不启动 MyBatis，上课记录的新查询需要先注册字段映射。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), LessonRecordEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), LessonOccurrenceEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), PrivateLessonGroupMemberEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), ClassEnrollmentEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), ClassGroupScheduleSlotEntity.class);
    }

    @BeforeEach
    void setUp() {
        nextSlotId = 6001L;
        recurringScheduleRuleService = new RecurringScheduleRuleServiceImpl(
                recurringScheduleRuleMapper,
                startingBalanceMapper,
                recurringScheduleSlotMapper,
                lessonOccurrenceMapper,
                lessonAdjustmentLogMapper,
                classEnrollmentMapper,
                classGroupScheduleSlotMapper,
                lessonRecordMapper,
                coursePackageMapper,
                privateLessonGroupMapper,
                privateLessonGroupMemberMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                jsonHelper,
                courseProductMapper
        );
        org.mockito.Mockito.lenient().when(coursePackageMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> coursePackageMapper.selectById(invocation.getArgument(0)));
    }

    @Test
    void shouldCreateRuleAndGenerateOccurrences() {
        RecurringScheduleRuleCreateRequest request = buildCreateRequest();
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubGeneratedIds(true);

        ScheduleRuleCreateResultVO result = recurringScheduleRuleService.createRule(request);

        verify(coursePackageMapper).selectByIdForUpdate(1001L);
        verify(recurringScheduleRuleMapper).insert(ruleCaptor.capture());
        verify(recurringScheduleSlotMapper, times(1)).insert(slotCaptor.capture());
        verify(lessonOccurrenceMapper, times(2)).insert(occurrenceCaptor.capture());

        assertThat(ruleCaptor.getValue().getRuleStatus()).isEqualTo("ACTIVE");
        assertThat(ruleCaptor.getValue().getAllocationPeriodStartedAt()).isNotNull();
        assertThat(slotCaptor.getValue().getWeekday()).isEqualTo(1);
        assertThat(slotCaptor.getValue().getTeacherId()).isEqualTo(3001L);
        assertThat(occurrenceCaptor.getAllValues()).hasSize(2);
        assertThat(occurrenceCaptor.getAllValues()).allSatisfy(occurrence -> {
            assertThat(occurrence.getPlannedTeacherId()).isEqualTo(3001L);
            assertThat(occurrence.getSubjectName()).isEqualTo("Math");
        });
        assertThat(result.ruleId()).isEqualTo(5001L);
        assertThat(result.generatedOccurrenceCount()).isEqualTo(2);
        assertThat(result.firstGeneratedStartAt()).isBefore(result.lastGeneratedEndAt());
    }

    @Test
    void shouldRejectSecondActiveRuleForSamePackage() {
        RecurringScheduleRuleCreateRequest request = buildCreateRequest();
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(recurringScheduleRuleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Edit the existing schedule");
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never())
                .insert(any(RecurringScheduleRuleEntity.class));
    }

    @Test
    void renewalDoesNotChargePreviousPeriodLessonsAgainstCurrentSlotAllocation() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 10, 8, 10, 0));
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setRuleId(rule.getId());
        slot.setWeekday(6);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setAllocatedUnits(8);

        LessonOccurrenceEntity oldLesson = buildRuleOccurrence(9001L,
                LocalDate.of(2026, 10, 3), 4, "MERGED");
        oldLesson.setPlannedStartAt(LocalDateTime.of(2026, 10, 3, 9, 0));
        LessonOccurrenceEntity currentLesson = buildRuleOccurrence(9002L,
                LocalDate.of(2026, 10, 10), 4, "SCHEDULED");
        currentLesson.setPlannedStartAt(LocalDateTime.of(2026, 10, 10, 9, 0));
        LessonOccurrenceEntity candidate = buildRuleOccurrence(9003L,
                LocalDate.of(2026, 10, 17), 4, "SCHEDULED");
        candidate.setPlannedStartAt(LocalDateTime.of(2026, 10, 17, 9, 0));
        candidate.setScheduleSlotId(slot.getId());

        @SuppressWarnings("unchecked")
        List<LessonOccurrenceEntity> allowed = ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "capGeneratedOccurrencesToRuleAllocation", List.of(candidate),
                List.of(oldLesson, currentLesson), Map.of(), List.of(slot), rule);

        assertThat(allowed).containsExactly(candidate);
    }

    @Test
    void pendingTeacherLeaveDoesNotReleaseCurrentSlotAllocation() {
        LessonOccurrenceEntity occurrence = buildRuleOccurrence(9001L,
                LocalDate.of(2026, 10, 10), 4, "SCHEDULED");
        LessonRecordEntity teacherProposal = buildRecord(9001L, 0);
        teacherProposal.setResultStatus("FORMAL_LEAVE");

        Integer reserved = ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "reservedAllocationUnits", occurrence, teacherProposal);

        assertThat(reserved).isEqualTo(4);
    }

    @Test
    void pendingTeacherLeaveStillReservesHoursWhenRollingScheduleForward() {
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setAllocatedUnits(8);
        LessonOccurrenceEntity occurrence = buildRuleOccurrence(9001L,
                LocalDate.of(2026, 10, 10), 4, "SCHEDULED");
        occurrence.setScheduleSlotId(slot.getId());
        LessonRecordEntity teacherProposal = buildRecord(9001L, 0);
        teacherProposal.setResultStatus("FORMAL_LEAVE");

        Integer remaining = ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "rolloverAllocatedUnits", slot, List.of(occurrence),
                Map.of(occurrence.getId(), teacherProposal), LocalDateTime.of(2026, 10, 11, 0, 0));

        assertThat(remaining).isEqualTo(4);
    }

    @Test
    void pendingTeacherLeaveReservesOriginalHoursAgainstNewPeriodBalance() {
        LessonOccurrenceEntity occurrence = buildRuleOccurrence(9001L,
                LocalDate.of(2026, 10, 10), 4, "SCHEDULED");
        LessonRecordEntity teacherProposal = buildRecord(9001L, 0);
        teacherProposal.setResultStatus("FORMAL_LEAVE");
        teacherProposal.setPackageId(1001L);
        when(lessonRecordMapper.selectList(any()))
                .thenReturn(List.of(teacherProposal), List.of(teacherProposal));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence));

        Integer reserved = ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "pendingDeductionUnits", 1001L);

        assertThat(reserved).isEqualTo(4);
    }

    @Test
    void renewalDoesNotAppendLessonWhenTeacherHasOnlyProposedLeave() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setRuleId(rule.getId());
        slot.setWeekday(6);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setAllocatedUnits(8);

        LessonOccurrenceEntity pending = buildRuleOccurrence(9001L,
                LocalDate.of(2026, 10, 10), 4, "SCHEDULED");
        pending.setScheduleSlotId(slot.getId());
        LessonOccurrenceEntity future = buildRuleOccurrence(9002L,
                LocalDate.of(2026, 10, 17), 4, "SCHEDULED");
        future.setScheduleSlotId(slot.getId());
        LessonOccurrenceEntity candidate = buildRuleOccurrence(9003L,
                LocalDate.of(2026, 10, 24), 4, "SCHEDULED");
        candidate.setScheduleSlotId(slot.getId());
        LessonRecordEntity teacherProposal = buildRecord(pending.getId(), 0);
        teacherProposal.setResultStatus("FORMAL_LEAVE");

        @SuppressWarnings("unchecked")
        List<LessonOccurrenceEntity> allowed = ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "capGeneratedOccurrencesToRuleAllocation", List.of(candidate), List.of(pending, future),
                Map.of(pending.getId(), teacherProposal), List.of(slot), rule);

        assertThat(allowed).isEmpty();
    }

    @Test
    void shouldRejectSavingAllocationWhenGeneratedLessonsAreCappedShort() {
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setSlotStatus("ACTIVE");
        slot.setAllocatedUnits(32);
        LessonOccurrenceEntity generatedOccurrence = new LessonOccurrenceEntity();
        generatedOccurrence.setPlannedUnits(20);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                recurringScheduleRuleService, "ensureAllocatedHoursWereGenerated",
                List.of(slot), List.of(generatedOccurrence)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("do not match the allocated hours");
    }

    @Test
    void shouldRejectNewScheduleWhenPreviousPeriodLessonLeavesInsufficientPackageBalance() {
        RecurringScheduleRuleEntity newRule = buildExistingRule();
        newRule.setId(5002L);
        newRule.setSupersedesRuleId(5001L);
        newRule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 10, 23, 0, 0));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setRemainingUnits(24); // 12h remaining

        LessonOccurrenceEntity previousPeriodLesson = buildRuleOccurrence(9001L,
                LocalDate.of(2026, 10, 19), 4, "SCHEDULED"); // 2h remains reserved
        previousPeriodLesson.setScheduleRuleId(5001L);

        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6002L);
        slot.setSlotStatus("ACTIVE");
        slot.setAllocatedUnits(24); // new schedule allocates all 12h

        LessonOccurrenceEntity firstGenerated = buildRuleOccurrence(9002L,
                LocalDate.of(2026, 10, 24), 12, "SCHEDULED");
        firstGenerated.setScheduleRuleId(newRule.getId());
        firstGenerated.setScheduleSlotId(slot.getId());
        LessonOccurrenceEntity secondGenerated = buildRuleOccurrence(9003L,
                LocalDate.of(2026, 10, 31), 12, "SCHEDULED");
        secondGenerated.setScheduleRuleId(newRule.getId());
        secondGenerated.setScheduleSlotId(slot.getId());

        @SuppressWarnings("unchecked")
        List<LessonOccurrenceEntity> capped = ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "capGeneratedOccurrencesToPackageBalance", List.of(firstGenerated, secondGenerated),
                List.of(previousPeriodLesson), Map.of(), coursePackage);

        assertThat(capped).extracting(LessonOccurrenceEntity::getPlannedUnits).containsExactly(12, 8);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "ensureCurrentPeriodAllocationIsCovered", List.of(slot),
                List.of(previousPeriodLesson, firstGenerated, secondGenerated), Map.of(), newRule))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("after preserving existing lessons");
    }

    @Test
    void shouldAllowScheduleWhenAllAllocatedHoursAreGeneratedDespiteOldPeriodLesson() {
        RecurringScheduleRuleEntity newRule = buildExistingRule();
        newRule.setId(5002L);
        newRule.setSupersedesRuleId(5001L);
        newRule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 10, 23, 0, 0));
        LessonOccurrenceEntity previousPeriodLesson = buildRuleOccurrence(9001L,
                LocalDate.of(2026, 10, 19), 4, "SCHEDULED");
        previousPeriodLesson.setScheduleRuleId(5001L);

        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6002L);
        slot.setSlotStatus("ACTIVE");
        slot.setAllocatedUnits(24);
        LessonOccurrenceEntity generated = buildRuleOccurrence(9002L,
                LocalDate.of(2026, 10, 24), 24, "SCHEDULED");
        generated.setScheduleRuleId(newRule.getId());
        generated.setScheduleSlotId(slot.getId());

        ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "ensureCurrentPeriodAllocationIsCovered", List.of(slot),
                List.of(previousPeriodLesson, generated), Map.of(), newRule);
    }

    @Test
    void shouldGroupActiveOneOnOneStudentCoursesByCourseAndGrade() {
        when(coursePackageMapper.selectSchedulableOneOnOneStudentCourseGrades()).thenReturn(List.of(
                new OneOnOneScheduleCourseRow(2001L, "Alina Chen", "Math", "G6", true,
                        null, "ACTIVE", null, 1, null, null, "6"),
                new OneOnOneScheduleCourseRow(2001L, "Alina Chen", "Math", "G6", true),
                new OneOnOneScheduleCourseRow(2001L, "Alina Chen", "English", "G7", false),
                new OneOnOneScheduleCourseRow(2002L, "Ben Li", "Math", "G6", false),
                new OneOnOneScheduleCourseRow(2003L, "Cara Wu", "Math", "G6", true),
                new OneOnOneScheduleCourseRow(2003L, "Cara Wu", "Math", "G6", false)
        ));

        List<ScheduleStudentOptionVO> result = recurringScheduleRuleService.listOneOnOneScheduleStudents();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(2001L);
        assertThat(result.get(0).studentGrade()).isEqualTo("6");
        assertThat(result.get(0).courseGrades()).containsExactly(
                new ScheduleStudentOptionVO.CourseGradeOption("Math", "G6", true),
                new ScheduleStudentOptionVO.CourseGradeOption("English", "G7", false)
        );
        assertThat(result.get(1).id()).isEqualTo(2002L);
        assertThat(result.get(1).courseGrades()).containsExactly(
                new ScheduleStudentOptionVO.CourseGradeOption("Math", "G6", false)
        );
        assertThat(result.get(2).courseGrades()).containsExactly(
                new ScheduleStudentOptionVO.CourseGradeOption("Math", "G6", false)
        );
        verify(coursePackageMapper).selectSchedulableOneOnOneStudentCourseGrades();
    }

    @Test
    void shouldKeepPausedAndActivePackagesAsSeparateSchedulingUnits() {
        when(coursePackageMapper.selectSchedulableOneOnOneStudentCourseGrades()).thenReturn(List.of(
                new OneOnOneScheduleCourseRow(2001L, "Alina Chen", "Math", "G6", true,
                        101L, "ACTIVE", null, 1, null, null),
                new OneOnOneScheduleCourseRow(2001L, "Alina Chen", "Math", "G6", false,
                        102L, "PAUSED", null, 1, null, null)
        ));

        List<ScheduleStudentOptionVO> result = recurringScheduleRuleService.listOneOnOneScheduleStudents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseGrades()).extracting(
                ScheduleStudentOptionVO.CourseGradeOption::packageId,
                ScheduleStudentOptionVO.CourseGradeOption::packageStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(101L, "ACTIVE"),
                        org.assertj.core.groups.Tuple.tuple(102L, "PAUSED"));
    }

    @Test
    void shouldGenerateOccurrencesWithSlotTeacherOverride() {
        RecurringScheduleRuleCreateRequest request = buildCreateRequest();
        request.getSlots().get(0).setTeacherId(3002L);
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        TeacherEntity slotTeacher = buildTeacher();
        slotTeacher.setId(3002L);
        slotTeacher.setTeacherName("Daniel Chen");
        when(teacherMapper.selectById(3002L)).thenReturn(slotTeacher);
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubGeneratedIds(true);

        recurringScheduleRuleService.createRule(request);

        verify(recurringScheduleSlotMapper).insert(slotCaptor.capture());
        verify(lessonOccurrenceMapper, times(2)).insert(occurrenceCaptor.capture());
        assertThat(slotCaptor.getValue().getTeacherId()).isEqualTo(3002L);
        assertThat(occurrenceCaptor.getAllValues())
                .allSatisfy(occurrence -> assertThat(occurrence.getPlannedTeacherId()).isEqualTo(3002L));
    }

    @Test
    void shouldFillEachSlotIndependentlyToItsAllocation() {
        // Monday: 2h/class, 4h allocated => 2 classes; Tuesday: 1h/class, 3h allocated => 3 classes.
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, 8);
        RecurringScheduleSlotRequest tuesday = buildSlot(2, LocalTime.of(18, 0), 2, 6);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(monday, tuesday));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(100);
        coursePackage.setRemainingUnits(100);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubGeneratedIds(true);

        recurringScheduleRuleService.createRule(request);

        verify(lessonOccurrenceMapper, times(5)).insert(occurrenceCaptor.capture());
        List<LessonOccurrenceEntity> inserted = occurrenceCaptor.getAllValues();
        long mondayCount = inserted.stream().filter(o -> o.getPlannedStartAt().getDayOfWeek() == DayOfWeek.MONDAY).count();
        long tuesdayCount = inserted.stream().filter(o -> o.getPlannedStartAt().getDayOfWeek() == DayOfWeek.TUESDAY).count();
        assertThat(mondayCount).isEqualTo(2);
        assertThat(tuesdayCount).isEqualTo(3);
        assertThat(inserted).allSatisfy(o -> assertThat(o.getPlannedUnits()).isIn(4, 2));
    }

    @Test
    void shouldGenerateAllAllocatedLessonsBeyondTwoYears() {
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, 424);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(monday));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(424);
        coursePackage.setRemainingUnits(424);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubGeneratedIds(true);

        recurringScheduleRuleService.createRule(request);

        verify(lessonOccurrenceMapper, times(106)).insert(occurrenceCaptor.capture());
        LocalDate lastScheduledDate = occurrenceCaptor.getAllValues().get(105).getPlannedStartAt().toLocalDate();
        assertThat(lastScheduledDate).isAfter(request.getStartDate().plusDays(730));
    }

    @Test
    void shouldScheduleRemainderAsLastClass() {
        // 22h allocated at 1.5h/class => 14 full classes + one 1h remainder class = 15 classes.
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 3, 44);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(monday));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(100);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubGeneratedIds(true);

        recurringScheduleRuleService.createRule(request);

        verify(lessonOccurrenceMapper, times(15)).insert(occurrenceCaptor.capture());
        List<LessonOccurrenceEntity> inserted = occurrenceCaptor.getAllValues().stream()
                .sorted(java.util.Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt))
                .toList();
        assertThat(inserted.subList(0, 14)).allSatisfy(o -> assertThat(o.getPlannedUnits()).isEqualTo(3));
        LessonOccurrenceEntity last = inserted.get(14);
        assertThat(last.getPlannedUnits()).isEqualTo(2);
        assertThat(last.getPlannedEndAt()).isEqualTo(last.getPlannedStartAt().plusMinutes(60));
        int totalUnits = inserted.stream().mapToInt(LessonOccurrenceEntity::getPlannedUnits).sum();
        assertThat(totalUnits).isEqualTo(44);
    }

    @Test
    void shouldRejectWhenSlotAllocationsExceedAvailableHours() {
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, 10);
        RecurringScheduleSlotRequest tuesday = buildSlot(2, LocalTime.of(18, 0), 2, 10);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(monday, tuesday));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(15);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("SLOT_ALLOCATION_EXCEEDS_PACKAGE");
                    assertThat(exception.getData()).isInstanceOf(ScheduleAllocationErrorVO.class);
                    ScheduleAllocationErrorVO details = (ScheduleAllocationErrorVO) exception.getData();
                    assertThat(details.packageTotalHours()).isEqualTo(7.5);
                    assertThat(details.requestedAllocationHours()).isEqualTo(10.0);
                    assertThat(details.shortfallHours()).isPositive();
                });
    }

    @Test
    void shouldRejectScheduleAllocationAboveRemainingHoursAfterAlreadyUsed() {
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, 80);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(monday));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(80);
        coursePackage.setConsumedUnits(32);
        coursePackage.setRemainingUnits(48);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("remaining hours after other schedules");
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleRuleEntity.class));
    }

    @Test
    void shouldAllowScheduleAllocationEqualToRemainingHours() {
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, 48);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(monday));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(80);
        coursePackage.setConsumedUnits(32);
        coursePackage.setRemainingUnits(48);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubGeneratedIds(true);

        recurringScheduleRuleService.createRule(request);

        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues().stream().mapToInt(LessonOccurrenceEntity::getPlannedUnits).sum())
                .isEqualTo(48);
    }

    @Test
    void shouldReproduceAllocationErrorWhileCancelledOldPlanRemainsPaused() {
        // 线上案例：余额 54h，已取消课次的旧规则仍以 PAUSED 状态预留 28h + 26h。
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(
                buildSlot(4, LocalTime.of(18, 0), 4, 36),
                buildSlot(5, LocalTime.of(18, 0), 4, 36),
                buildSlot(6, LocalTime.of(16, 0), 4, 36)));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(240);
        coursePackage.setConsumedUnits(132);
        coursePackage.setRemainingUnits(108);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());

        RecurringScheduleRuleEntity oldRule = buildExistingRule();
        oldRule.setRuleStatus("PAUSED");
        RecurringScheduleSlotEntity thursday = new RecurringScheduleSlotEntity();
        thursday.setAllocatedUnits(56);
        thursday.setSlotStatus("ACTIVE");
        RecurringScheduleSlotEntity saturday = new RecurringScheduleSlotEntity();
        saturday.setAllocatedUnits(52);
        saturday.setSlotStatus("ACTIVE");
        List<LessonOccurrenceEntity> cancelledLessons = new ArrayList<>();
        for (long id = 1; id <= 27; id++) {
            LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
            occurrence.setId(id);
            occurrence.setPlanningStatus("CANCELLED");
            cancelledLessons.add(occurrence);
        }
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(oldRule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(thursday, saturday));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(cancelledLessons);

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("after other schedules are accounted for");
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleRuleEntity.class));
    }

    @Test
    void shouldCreateScheduleAfterPackagePauseEndsOldRule() {
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(
                buildSlot(4, LocalTime.of(18, 0), 4, 36),
                buildSlot(5, LocalTime.of(18, 0), 4, 36),
                buildSlot(6, LocalTime.of(16, 0), 4, 36)));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(240);
        coursePackage.setConsumedUnits(132);
        coursePackage.setRemainingUnits(108);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            RecurringScheduleRuleEntity rule = invocation.getArgument(0);
            rule.setId(5001L);
            return 1;
        }).when(recurringScheduleRuleMapper).insert(any(RecurringScheduleRuleEntity.class));
        long[] nextSlotId = {6000L};
        doAnswer(invocation -> {
            RecurringScheduleSlotEntity slot = invocation.getArgument(0);
            slot.setId(++nextSlotId[0]);
            return 1;
        }).when(recurringScheduleSlotMapper).insert(any(RecurringScheduleSlotEntity.class));
        doAnswer(invocation -> 1).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));

        ScheduleRuleCreateResultVO result = recurringScheduleRuleService.createRule(request);

        assertThat(result.slotCount()).isEqualTo(3);
        assertThat(result.generatedOccurrenceCount()).isEqualTo(27);
        verify(lessonOccurrenceMapper, times(27)).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldExcludePendingDeductionsFromScheduleAllocation() {
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, 44);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(monday));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(80);
        coursePackage.setConsumedUnits(32);
        coursePackage.setRemainingUnits(48);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        LessonOccurrenceEntity pendingOccurrence = buildRuleOccurrence(9001L,
                request.getStartDate().minusDays(1), 8, "SCHEDULED");
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(pendingOccurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(buildRecord(9001L, 8)));

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("remaining hours after other schedules");
    }

    @Test
    void shouldRejectWhenSlotAllocationIsMissing() {
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, null);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(List.of(monday));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(8);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectWhenTeacherHasConflictingOccurrence() {
        RecurringScheduleRuleCreateRequest request = buildCreateRequest();
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(buildTeacherConflict()))
                .thenReturn(Collections.emptyList());
        stubGeneratedIds(false);

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conflict type: Teacher schedule conflict")
                .hasMessageContaining("Conflicting course: SHSAT Math")
                .hasMessageContaining("Date: " + request.getStartDate())
                .hasMessageContaining("Time: 19:00–21:00")
                .hasMessageContaining("Teacher: Emma Wang")
                .hasMessageContaining("Student: Olivia Lin");
    }

    @Test
    void shouldRejectWhenStudentHasAnotherSharedPrivateLesson() {
        RecurringScheduleRuleCreateRequest request = buildCreateRequest();
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        PrivateLessonGroupMemberEntity otherGroup = new PrivateLessonGroupMemberEntity();
        otherGroup.setStudentId(2001L);
        otherGroup.setPrivateLessonGroupId(7001L);
        otherGroup.setMemberStatus("ACTIVE");
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(List.of(otherGroup));
        LessonOccurrenceEntity otherSharedLesson = buildTeacherConflict();
        otherSharedLesson.setPlannedTeacherId(null);
        otherSharedLesson.setStudentId(2002L);
        otherSharedLesson.setPrivateLessonGroupId(7001L);
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(otherSharedLesson))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());
        stubGeneratedIds(false);

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conflict type: Student schedule conflict")
                .hasMessageContaining("Conflicting course: SHSAT Math")
                .hasMessageContaining("Time: 19:00–21:00")
                .hasMessageContaining("Student: Olivia Lin");
    }

    @Test
    void shouldCreateOneSharedScheduleForThreeParticipants() {
        RecurringScheduleRuleCreateRequest request = buildCreateRequest();
        PrivateLessonGroupMemberEntity primary = new PrivateLessonGroupMemberEntity();
        primary.setPrivateLessonGroupId(7001L);
        primary.setPackageId(1001L);
        primary.setStudentId(2001L);
        primary.setParticipantIndex(1);
        primary.setMemberStatus("ACTIVE");
        PrivateLessonGroupMemberEntity second = new PrivateLessonGroupMemberEntity();
        second.setPrivateLessonGroupId(7001L);
        second.setPackageId(1002L);
        second.setStudentId(2002L);
        second.setParticipantIndex(2);
        second.setMemberStatus("ACTIVE");
        PrivateLessonGroupMemberEntity third = new PrivateLessonGroupMemberEntity();
        third.setPrivateLessonGroupId(7001L);
        third.setPackageId(1003L);
        third.setStudentId(2003L);
        third.setParticipantIndex(3);
        third.setMemberStatus("ACTIVE");
        when(privateLessonGroupMemberMapper.selectOne(any())).thenReturn(primary);
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(List.of(primary, second, third));
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(coursePackageMapper.selectByIdForUpdate(1002L)).thenReturn(buildPackage());
        when(coursePackageMapper.selectByIdForUpdate(1003L)).thenReturn(buildPackage());
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        stubGeneratedIds(true);

        ScheduleRuleCreateResultVO result = recurringScheduleRuleService.createRule(request);

        verify(lessonOccurrenceMapper, times(2)).insert(occurrenceCaptor.capture());
        assertThat(result.generatedOccurrenceCount()).isEqualTo(2);
        assertThat(occurrenceCaptor.getAllValues()).allSatisfy(occurrence -> {
            assertThat(occurrence.getPrivateLessonGroupId()).isEqualTo(7001L);
            assertThat(occurrence.getStudentId()).isEqualTo(2001L);
        });
    }

    @Test
    void shouldIgnoreClassLessonBeforeStudentEnrollmentDate() {
        RecurringScheduleRuleCreateRequest request = buildCreateRequest();
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setStudentId(2001L);
        enrollment.setClassId(8001L);
        enrollment.setStatus("ACTIVE");
        enrollment.setEnrollmentDate(request.getStartDate().plusWeeks(1));
        LessonOccurrenceEntity classLesson = buildTeacherConflict();
        classLesson.setPlannedTeacherId(null);
        classLesson.setClassGroupId(8001L);
        classLesson.setStudentId(null);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(classLesson))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());
        stubGeneratedIds(true);

        ScheduleRuleCreateResultVO result = recurringScheduleRuleService.createRule(request);

        assertThat(result.generatedOccurrenceCount()).isEqualTo(2);
    }

    @Test
    void shouldUpdatePausedScheduleWithoutGeneratingLessonsUntilPackageResumes() {
        RecurringScheduleRuleEntity pausedRule = buildExistingRule();
        pausedRule.setRuleStatus("PAUSED");
        CoursePackageEntity pausedPackage = buildPackage();
        pausedPackage.setPackageStatus("PAUSED");
        pausedPackage.setTotalUnits(28);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setRuleStatus("PAUSED");

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(pausedRule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(pausedPackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        // 暂停课包编辑旧计划时结束旧规则；恢复后必须由管理员显式创建新计划。
        assertThat(pausedRule.getRuleStatus()).isEqualTo("ENDED");
        assertThat(result.generatedOccurrenceCount()).isZero();
        verify(lessonOccurrenceMapper, times(0)).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldAllowCourseNameAndClassroomEditsWithoutRegeneratingScheduleWhenBalanceIsLow() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setRemainingUnits(20);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        RecurringScheduleSlotRequest requestedSlot = request.getSlots().get(0);
        requestedSlot.setSlotId(6001L);
        requestedSlot.setDeliveryMode("IN_PERSON");
        requestedSlot.setClassroomId(4001L);
        request.setNotes(null);
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6001L);
        currentSlot.setRuleId(rule.getId());
        currentSlot.setTeacherId(3001L);
        currentSlot.setSubjectName("Math");
        currentSlot.setWeekday(requestedSlot.getWeekday());
        currentSlot.setStartTime(requestedSlot.getStartTime());
        currentSlot.setEndTime(requestedSlot.getEndTime());
        currentSlot.setPlannedUnits(requestedSlot.getPlannedUnits());
        currentSlot.setAllocatedUnits(requestedSlot.getAllocatedUnits());
        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThat(recurringScheduleRuleService.updateRule(5001L, request).ruleId()).isEqualTo(5001L);
        requestedSlot.setSubjectName("New course name");
        assertThat(recurringScheduleRuleService.updateRule(5001L, request).ruleId()).isEqualTo(5001L);
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleRuleEntity.class));
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldRequireExplicitChoiceBeforeReclaimingEndedUnconfirmedLessons() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setRuleStatus("PAUSED");
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setRuleStatus("PAUSED");
        LessonOccurrenceEntity oldLesson = buildRuleOccurrence(9101L,
                AppTime.nowLocalDateTime().toLocalDate().minusDays(2), 4, "SCHEDULED");

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(oldLesson));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> recurringScheduleRuleService.updateRule(5001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("explicitly continue and reclaim");

        verify(lessonOccurrenceMapper, org.mockito.Mockito.never())
                .softDeleteEndedUnconfirmedPrivateLessons(any(), any());
    }

    @Test
    void shouldReclaimEndedPendingSubmissionWithoutChangingPackageBalance() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setRuleStatus("PAUSED");
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setRuleStatus("PAUSED");
        request.setReclaimEndedUnconfirmedLessons(true);
        request.setNotes(null);
        LessonOccurrenceEntity oldLesson = buildRuleOccurrence(9102L,
                AppTime.nowLocalDateTime().toLocalDate().minusDays(2), 4, "SCHEDULED");
        oldLesson.setScheduleSlotId(6001L);
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setRuleId(5001L);
        slot.setTeacherId(3001L);
        slot.setSubjectName("Math");
        slot.setWeekday(1);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        slot.setPlannedUnits(4);
        slot.setAllocatedUnits(28);
        slot.setSlotStatus("ACTIVE");
        LessonRecordEntity pending = new LessonRecordEntity();
        pending.setId(9201L);
        pending.setOccurrenceId(oldLesson.getId());
        pending.setPackageId(1001L);
        pending.setConfirmStage("PENDING_ADMIN");
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(100);
        coursePackage.setRemainingUnits(100);

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(oldLesson), Collections.emptyList());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(pending));
        when(lessonOccurrenceMapper.selectByIdForUpdate(oldLesson.getId())).thenReturn(oldLesson);
        when(lessonRecordMapper.selectByOccurrenceIdForUpdate(oldLesson.getId())).thenReturn(List.of(pending));
        when(lessonRecordMapper.softDeleteUnconfirmedByIds(any(), any())).thenReturn(1);
        when(lessonOccurrenceMapper.softDeleteEndedUnconfirmedPrivateLessons(any(), any())).thenReturn(1);
        when(jsonHelper.toJson(any())).thenReturn("{}");

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(result.reclaimedOccurrenceCount()).isEqualTo(1);
        assertThat(result.deletedOccurrenceCount()).isZero();
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(100);
        verify(lessonRecordMapper).softDeleteUnconfirmedByIds(
                org.mockito.ArgumentMatchers.eq(List.of(9201L)), any());
        verify(lessonOccurrenceMapper).softDeleteEndedUnconfirmedPrivateLessons(
                org.mockito.ArgumentMatchers.eq(List.of(9102L)), any());
        verify(lessonAdjustmentLogMapper).insert(any(LessonAdjustmentLogEntity.class));
    }

    @Test
    void shouldKeepSlotAllocationSnapshotWhenOnlyPausingSchedule() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(40);
        coursePackage.setRemainingUnits(40);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setRuleStatus("PAUSED");
        RecurringScheduleSlotRequest requestedSlot = request.getSlots().get(0);
        requestedSlot.setSlotId(6001L);
        requestedSlot.setDeliveryMode("IN_PERSON");
        requestedSlot.setClassroomId(4001L);
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6001L);
        currentSlot.setRuleId(rule.getId());
        currentSlot.setTeacherId(3001L);
        // 旧课位依赖规则默认教室；暂停时不能因此重建课位或清空默认教室。
        currentSlot.setDeliveryMode(null);
        currentSlot.setClassroomId(null);
        currentSlot.setSubjectName("Math");
        currentSlot.setWeekday(requestedSlot.getWeekday());
        currentSlot.setStartTime(requestedSlot.getStartTime());
        currentSlot.setEndTime(requestedSlot.getEndTime());
        currentSlot.setPlannedUnits(requestedSlot.getPlannedUnits());
        currentSlot.setAllocatedUnits(requestedSlot.getAllocatedUnits());

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());

        recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(rule.getRuleStatus()).isEqualTo("PAUSED");
        assertThat(rule.getDefaultClassroomId()).isEqualTo(4001L);
        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleSlotEntity.class));
        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void shouldKeepLegacyPeriodWhenSameScheduleRebuildsSlot() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        LocalDateTime originalPeriodStart = LocalDateTime.of(2026, 9, 1, 9, 0);
        rule.setCreatedAt(originalPeriodStart);
        rule.setRuleStatus("PAUSED");
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setPackageStatus("PAUSED");
        coursePackage.setTotalUnits(40);
        coursePackage.setRemainingUnits(40);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setRuleStatus("PAUSED");
        request.getSlots().get(0).setSubjectName("ELA");
        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6001L);
        currentSlot.setRuleId(rule.getId());
        currentSlot.setTeacherId(3001L);
        currentSlot.setSubjectName("Math");
        currentSlot.setWeekday(1);
        currentSlot.setStartTime(LocalTime.of(18, 0));
        currentSlot.setEndTime(LocalTime.of(20, 0));
        currentSlot.setPlannedUnits(4);
        currentSlot.setAllocatedUnits(28);
        currentSlot.setSlotStatus("ACTIVE");
        currentSlot.setCreatedAt(LocalDateTime.of(2026, 9, 27, 21, 27));

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(recurringScheduleRuleMapper.updateById(any(RecurringScheduleRuleEntity.class))).thenReturn(1);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(result.ruleId()).isEqualTo(5001L);
        assertThat(rule.getAllocationPeriodStartedAt()).isNull();
        assertThat(com.classdeduction.system.modules.schedule.util.PrivateSchedulePeriod.startAt(rule))
                .isEqualTo(originalPeriodStart);
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleRuleEntity.class));
        verify(recurringScheduleSlotMapper).insert(any(RecurringScheduleSlotEntity.class));
    }

    @Test
    void shouldKeepSchedulePeriodWhenOnlyTeacherChanges() {
        RecurringScheduleRuleEntity existing = buildExistingRule();
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(28);
        coursePackage.setConsumedUnits(0);
        coursePackage.setRemainingUnits(28);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setEditOperation("CURRENT");
        request.getSlots().get(0).setSlotId(6001L);
        request.getSlots().get(0).setTeacherId(3002L);
        RecurringScheduleSlotEntity oldSlot = new RecurringScheduleSlotEntity();
        oldSlot.setId(6001L);
        oldSlot.setRuleId(existing.getId());
        oldSlot.setTeacherId(3001L);
        oldSlot.setSubjectName("Math");
        oldSlot.setWeekday(1);
        oldSlot.setStartTime(LocalTime.of(18, 0));
        oldSlot.setEndTime(LocalTime.of(20, 0));
        oldSlot.setPlannedUnits(4);
        oldSlot.setAllocatedUnits(28);
        oldSlot.setSlotStatus("ACTIVE");
        TeacherEntity replacement = buildTeacher();
        replacement.setId(3002L);

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(existing);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3002L)).thenReturn(replacement);
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(oldSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(result.ruleId()).isEqualTo(5001L);
        assertThat(existing.getRuleStatus()).isEqualTo("ACTIVE");
        assertThat(oldSlot.getTeacherId()).isEqualTo(3002L);
        assertThat(oldSlot.getAllocatedUnits()).isEqualTo(28);
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleRuleEntity.class));
    }

    @Test
    void teacherHandoffPreservesHistoryTransfersFutureExceptionAndKeepsHoursBalanced() {
        RecurringScheduleRuleEntity existing = buildExistingRule();
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(28);
        coursePackage.setConsumedUnits(4);
        coursePackage.setRemainingUnits(24);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        // 旧表单重建周课位时可能漏传 slotId；单一课位仍须延续历史时间线。
        request.getSlots().get(0).setTeacherId(3002L);

        RecurringScheduleSlotEntity oldSlot = new RecurringScheduleSlotEntity();
        oldSlot.setId(6001L);
        oldSlot.setRuleId(existing.getId());
        oldSlot.setSlotLineageId(7001L);
        oldSlot.setTeacherId(3001L);
        oldSlot.setSubjectName("Math");
        oldSlot.setWeekday(1);
        oldSlot.setStartTime(LocalTime.of(18, 0));
        oldSlot.setEndTime(LocalTime.of(20, 0));
        oldSlot.setPlannedUnits(4);
        oldSlot.setAllocatedUnits(28);
        oldSlot.setSlotStatus("ACTIVE");

        LessonOccurrenceEntity historical = buildRuleOccurrence(9101L,
                AppTime.nowLocalDateTime().toLocalDate().minusWeeks(1), 4, "SCHEDULED");
        historical.setScheduleSlotId(oldSlot.getId());
        historical.setPlannedTeacherId(3001L);
        LessonOccurrenceEntity ordinaryFuture = buildRuleOccurrence(9102L,
                request.getStartDate(), 4, "SCHEDULED");
        ordinaryFuture.setScheduleSlotId(oldSlot.getId());
        ordinaryFuture.setPlannedTeacherId(3001L);
        LessonOccurrenceEntity adjustedFuture = buildRuleOccurrence(9103L,
                request.getStartDate().plusWeeks(1), 4, "ADJUSTED");
        adjustedFuture.setScheduleSlotId(oldSlot.getId());
        adjustedFuture.setPlannedTeacherId(3001L);
        adjustedFuture.setPlannedClassroomId(4001L);

        LessonRecordEntity historicalRecord = buildRecord(historical.getId(), 4);
        historicalRecord.setPackageId(coursePackage.getId());
        historicalRecord.setConfirmStage("CONFIRMED");
        historicalRecord.setPlannedTeacherId(3001L);
        historicalRecord.setActualTeacherId(3001L);

        TeacherEntity replacement = buildTeacher();
        replacement.setId(3002L);
        List<LessonOccurrenceEntity> sourceOccurrences =
                List.of(historical, ordinaryFuture, adjustedFuture);
        int[] occurrenceQueries = {0};

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(existing);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3002L)).thenReturn(replacement);
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(oldSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation ->
                occurrenceQueries[0]++ < 2 ? sourceOccurrences : Collections.emptyList());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(historicalRecord));
        when(lessonOccurrenceMapper.handoffFutureOccurrence(any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(1);
        when(lessonOccurrenceMapper.updateRecurringDetails(any(), any(), any(), any(), any(), any())).thenReturn(1);

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(result.ruleId()).isEqualTo(5001L);
        assertThat(oldSlot.getTeacherId()).isEqualTo(3002L);
        assertThat(oldSlot.getAllocatedUnits()).isEqualTo(28);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(24);
        assertThat(historical.getPlannedTeacherId()).isEqualTo(3001L);
        assertThat(historicalRecord.getActualTeacherId()).isEqualTo(3001L);
        assertThat(historicalRecord.getDeductedUnits()).isEqualTo(4);
        verify(lessonOccurrenceMapper).updateRecurringDetails(
                org.mockito.ArgumentMatchers.eq(ordinaryFuture.getId()),
                org.mockito.ArgumentMatchers.eq(3002L), any(), any(), any(), any());
        verify(lessonOccurrenceMapper).handoffFutureOccurrence(
                org.mockito.ArgumentMatchers.eq(adjustedFuture.getId()),
                org.mockito.ArgumentMatchers.eq(5001L), org.mockito.ArgumentMatchers.eq(6001L),
                org.mockito.ArgumentMatchers.eq(5001L), org.mockito.ArgumentMatchers.eq(6001L),
                any(), org.mockito.ArgumentMatchers.eq(3001L), org.mockito.ArgumentMatchers.eq(3002L), any());
        verify(lessonRecordMapper, org.mockito.Mockito.never()).updateById(any(LessonRecordEntity.class));
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleRuleEntity.class));
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).softDeleteFutureUnprocessed(any(), any());
    }

    @Test
    void teacherHandoffRejectsFutureLessonWithReviewRecord() {
        RecurringScheduleRuleEntity existing = buildExistingRule();
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(28);
        coursePackage.setRemainingUnits(28);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.getSlots().get(0).setSlotId(6001L);
        request.getSlots().get(0).setTeacherId(3002L);
        RecurringScheduleSlotEntity oldSlot = new RecurringScheduleSlotEntity();
        oldSlot.setId(6001L);
        oldSlot.setRuleId(existing.getId());
        oldSlot.setSlotLineageId(7001L);
        oldSlot.setTeacherId(3001L);
        oldSlot.setSubjectName("Math");
        oldSlot.setWeekday(1);
        oldSlot.setStartTime(LocalTime.of(18, 0));
        oldSlot.setEndTime(LocalTime.of(20, 0));
        oldSlot.setPlannedUnits(4);
        oldSlot.setAllocatedUnits(28);
        oldSlot.setSlotStatus("ACTIVE");
        LessonOccurrenceEntity future = buildRuleOccurrence(9201L,
                request.getStartDate().plusWeeks(1), 4, "ADJUSTED");
        future.setScheduleSlotId(oldSlot.getId());
        future.setPlannedTeacherId(3001L);
        future.setAllocationOwnerTeacherId(3001L);
        LessonRecordEntity futureRecord = buildRecord(future.getId(), 0);
        futureRecord.setPackageId(coursePackage.getId());
        futureRecord.setConfirmStage("PENDING_ADMIN");
        futureRecord.setPlannedTeacherId(3001L);
        TeacherEntity replacement = buildTeacher();
        replacement.setId(3002L);

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(existing);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3002L)).thenReturn(replacement);
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(oldSlot));
        int[] occurrenceQueries = {0};
        when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation ->
                occurrenceQueries[0]++ < 2 ? List.of(future) : Collections.emptyList());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(futureRecord));
        assertThatThrownBy(() -> recurringScheduleRuleService.updateRule(5001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("future lesson already has a review record");
        assertThat(future.getPlannedTeacherId()).isEqualTo(3001L);
        assertThat(futureRecord.getPlannedTeacherId()).isEqualTo(3001L);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(28);
        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never())
                .updateCourseDetails(any(), any(), any(), any(), any());
    }

    @Test
    void increasingSlotAllocationKeepsTheSchedulePeriodAndGeneratesTheAddedHours() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        LocalDateTime originalPeriodStart = LocalDateTime.of(2026, 9, 1, 9, 0);
        rule.setAllocationPeriodStartedAt(originalPeriodStart);
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(40);
        coursePackage.setRemainingUnits(40);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        RecurringScheduleSlotRequest requested = request.getSlots().get(0);
        requested.setSlotId(6001L);
        requested.setAllocatedUnits(32);
        requested.setDeliveryMode("IN_PERSON");
        requested.setClassroomId(4001L);

        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6001L);
        currentSlot.setRuleId(rule.getId());
        currentSlot.setTeacherId(3001L);
        currentSlot.setSubjectName("Math");
        currentSlot.setWeekday(requested.getWeekday());
        currentSlot.setStartTime(requested.getStartTime());
        currentSlot.setEndTime(requested.getEndTime());
        currentSlot.setPlannedUnits(requested.getPlannedUnits());
        currentSlot.setAllocatedUnits(28);
        currentSlot.setDeliveryMode("IN_PERSON");
        currentSlot.setClassroomId(4001L);
        currentSlot.setSlotStatus("ACTIVE");

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        List<LessonOccurrenceEntity> persistedOccurrences = new ArrayList<>();
        when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation -> persistedOccurrences);
        doAnswer(invocation -> {
            persistedOccurrences.add(invocation.getArgument(0));
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleSlotMapper.updateAllocatedUnits(6001L, 5001L, 28, 32))
                .thenAnswer(invocation -> {
                    currentSlot.setAllocatedUnits(32);
                    return 1;
                });

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(result.ruleId()).isEqualTo(5001L);
        assertThat(rule.getAllocationPeriodStartedAt()).isEqualTo(originalPeriodStart);
        assertThat(result.generatedOccurrenceCount()).isEqualTo(8);
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never())
                .insert(any(RecurringScheduleRuleEntity.class));
        verify(lessonOccurrenceMapper, times(8)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues())
                .allSatisfy(occurrence -> assertThat(occurrence.getPlannedTeacherId()).isEqualTo(3001L));
        assertThat(occurrenceCaptor.getAllValues().stream()
                .mapToInt(LessonOccurrenceEntity::getPlannedUnits).sum()).isEqualTo(32);
    }

    @Test
    void increasingSlotAllocationByHalfHourKeepsPeriodAndGeneratesPartialLastLesson() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        LocalDateTime originalPeriodStart = LocalDateTime.of(2026, 9, 1, 9, 0);
        rule.setAllocationPeriodStartedAt(originalPeriodStart);
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(40);
        coursePackage.setRemainingUnits(40);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setEditOperation("CURRENT");
        RecurringScheduleSlotRequest requested = request.getSlots().get(0);
        requested.setSlotId(6001L);
        requested.setAllocatedUnits(29);
        requested.setDeliveryMode("IN_PERSON");
        requested.setClassroomId(4001L);

        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6001L);
        currentSlot.setRuleId(rule.getId());
        currentSlot.setTeacherId(3001L);
        currentSlot.setSubjectName("Math");
        currentSlot.setWeekday(requested.getWeekday());
        currentSlot.setStartTime(requested.getStartTime());
        currentSlot.setEndTime(requested.getEndTime());
        currentSlot.setPlannedUnits(requested.getPlannedUnits());
        currentSlot.setAllocatedUnits(28);
        currentSlot.setDeliveryMode("IN_PERSON");
        currentSlot.setClassroomId(4001L);
        currentSlot.setSlotStatus("ACTIVE");

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        List<LessonOccurrenceEntity> persistedOccurrences = new ArrayList<>();
        when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation -> persistedOccurrences);
        doAnswer(invocation -> {
            persistedOccurrences.add(invocation.getArgument(0));
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleSlotMapper.updateAllocatedUnits(6001L, 5001L, 28, 29))
                .thenAnswer(invocation -> {
                    currentSlot.setAllocatedUnits(29);
                    return 1;
                });

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(result.ruleId()).isEqualTo(5001L);
        assertThat(rule.getAllocationPeriodStartedAt()).isEqualTo(originalPeriodStart);
        assertThat(result.generatedOccurrenceCount()).isEqualTo(8);
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never())
                .insert(any(RecurringScheduleRuleEntity.class));
        verify(lessonOccurrenceMapper, times(8)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues())
                .allSatisfy(occurrence -> assertThat(occurrence.getPlannedTeacherId()).isEqualTo(3001L));
        assertThat(occurrenceCaptor.getAllValues().stream()
                .mapToInt(LessonOccurrenceEntity::getPlannedUnits).sum()).isEqualTo(29);
        assertThat(occurrenceCaptor.getAllValues()).filteredOn(o -> o.getPlannedUnits() == 1).hasSize(1);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(40);
    }

    @Test
    void splitRequiresOneExplicitSuccessorForEachHistoricalSlot() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        RecurringScheduleSlotEntity source = new RecurringScheduleSlotEntity();
        source.setId(6001L);
        source.setWeekday(1);
        source.setStartTime(LocalTime.of(13, 0));
        source.setEndTime(LocalTime.of(15, 0));
        source.setPlannedUnits(4);
        LessonOccurrenceEntity history = buildRuleOccurrence(9301L, LocalDate.of(2026, 9, 24), 4, "SCHEDULED");
        history.setScheduleSlotId(source.getId());
        RecurringScheduleSlotRequest first = new RecurringScheduleSlotRequest();
        first.setWeekday(1); first.setStartTime(LocalTime.of(13, 0)); first.setEndTime(LocalTime.of(14, 0)); first.setPlannedUnits(2);
        RecurringScheduleSlotRequest second = new RecurringScheduleSlotRequest();
        second.setWeekday(1); second.setStartTime(LocalTime.of(14, 0)); second.setEndTime(LocalTime.of(15, 0)); second.setPlannedUnits(2);
        RecurringScheduleRuleUpdateRequest request = new RecurringScheduleRuleUpdateRequest();
        request.setEditOperation("REPLAN");
        request.setStartDate(rule.getStartDate());
        request.setSlots(List.of(first, second));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "validateEditOperation", request, rule, List.of(source), List.of(history)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("MISSING_SLOT_INHERITANCE"));
        first.setSlotId(source.getId());
        ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "validateEditOperation", request, rule, List.of(source), List.of(history));
        second.setSlotId(source.getId());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "validateEditOperation", request, rule, List.of(source), List.of(history)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_SLOT_INHERITANCE"));
    }

    @Test
    void replanSplitCreatesSeparateSlotsAndPreservesChosenHistoryAndPackageBalance() {
        RecurringScheduleRuleEntity oldRule = buildExistingRule();
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(24); coursePackage.setConsumedUnits(12); coursePackage.setRemainingUnits(12);
        RecurringScheduleSlotEntity source = new RecurringScheduleSlotEntity();
        source.setId(6001L); source.setRuleId(5001L); source.setSlotLineageId(7001L);
        source.setTeacherId(3001L); source.setSubjectName("Math"); source.setWeekday(1);
        source.setStartTime(LocalTime.of(18, 0)); source.setEndTime(LocalTime.of(20, 0));
        source.setPlannedUnits(4); source.setAllocatedUnits(24); source.setSlotStatus("ACTIVE");
        LessonOccurrenceEntity history = buildRuleOccurrence(9301L, AppTime.nowLocalDateTime().toLocalDate().minusDays(7), 4, "SCHEDULED");
        history.setScheduleRuleId(5001L); history.setScheduleSlotId(6001L); history.setPlannedTeacherId(3001L);
        LessonRecordEntity record = buildRecord(history.getId(), 4);
        record.setPackageId(1001L); record.setConfirmStage("CONFIRMED"); record.setShouldDeduct(1); record.setActualTeacherId(3001L);
        RecurringScheduleSlotRequest first = buildSlot(1, LocalTime.of(18, 0), 2, 8);
        first.setSlotId(6001L); first.setTeacherId(3002L); first.setDeliveryMode("ONLINE");
        RecurringScheduleSlotRequest second = buildSlot(1, LocalTime.of(19, 0), 2, 4);
        second.setTeacherId(3003L); second.setDeliveryMode("ONLINE");
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setEditOperation("REPLAN"); request.setStartDate(AppTime.nowLocalDateTime().toLocalDate());
        request.setDefaultClassroomId(null); request.setSlots(List.of(first, second));
        TeacherEntity teacherB = buildTeacher(); teacherB.setId(3002L);
        TeacherEntity teacherC = buildTeacher(); teacherC.setId(3003L);
        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(oldRule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3002L)).thenReturn(teacherB);
        when(teacherMapper.selectById(3003L)).thenReturn(teacherC);
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(source));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(source));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(history));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));
        doAnswer(invocation -> { RecurringScheduleRuleEntity rule = invocation.getArgument(0); rule.setId(5002L); rule.setCreatedAt(AppTime.nowLocalDateTime()); return 1; })
                .when(recurringScheduleRuleMapper).insert(any(RecurringScheduleRuleEntity.class));
        doAnswer(invocation -> { RecurringScheduleSlotEntity slot = invocation.getArgument(0); slot.setId(nextSlotId++); return 1; })
                .when(recurringScheduleSlotMapper).insert(any(RecurringScheduleSlotEntity.class));

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(result.ruleId()).isEqualTo(5002L);
        assertThat(result.generatedOccurrenceCount()).isEqualTo(6);
        verify(recurringScheduleRuleMapper).insert(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getAllocationPeriodStartedAt()).isNotNull();
        ArgumentCaptor<RecurringScheduleSlotEntity> slotCaptor = ArgumentCaptor.forClass(RecurringScheduleSlotEntity.class);
        verify(recurringScheduleSlotMapper, times(2)).insert(slotCaptor.capture());
        assertThat(slotCaptor.getAllValues()).anySatisfy(slot -> { assertThat(slot.getTeacherId()).isEqualTo(3002L); assertThat(slot.getSlotLineageId()).isEqualTo(7001L); assertThat(slot.getAllocatedUnits()).isEqualTo(8); });
        assertThat(slotCaptor.getAllValues()).anySatisfy(slot -> { assertThat(slot.getTeacherId()).isEqualTo(3003L); assertThat(slot.getSlotLineageId()).isNotEqualTo(7001L); assertThat(slot.getAllocatedUnits()).isEqualTo(4); });
        verify(lessonOccurrenceMapper, times(6)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues().stream().mapToInt(LessonOccurrenceEntity::getPlannedUnits).sum()).isEqualTo(12);
        assertThat(occurrenceCaptor.getAllValues()).filteredOn(lesson -> Objects.equals(lesson.getPlannedTeacherId(), 3002L))
                .hasSize(4).allSatisfy(lesson -> {
                    assertThat(lesson.getPlannedUnits()).isEqualTo(2);
                    assertThat(lesson.getPlannedStartAt().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
                    assertThat(lesson.getPlannedStartAt().toLocalTime()).isEqualTo(LocalTime.of(18, 0));
                    assertThat(lesson.getPlannedEndAt().toLocalTime()).isEqualTo(LocalTime.of(19, 0));
                });
        assertThat(occurrenceCaptor.getAllValues()).filteredOn(lesson -> Objects.equals(lesson.getPlannedTeacherId(), 3003L))
                .hasSize(2).allSatisfy(lesson -> {
                    assertThat(lesson.getPlannedUnits()).isEqualTo(2);
                    assertThat(lesson.getPlannedStartAt().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
                    assertThat(lesson.getPlannedStartAt().toLocalTime()).isEqualTo(LocalTime.of(19, 0));
                    assertThat(lesson.getPlannedEndAt().toLocalTime()).isEqualTo(LocalTime.of(20, 0));
                });
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(12);
        assertThat(history.getScheduleSlotId()).isEqualTo(6001L);
        assertThat(record.getActualTeacherId()).isEqualTo(3001L);
    }

    @Test
    void futureStartImmediatelyReplacesOldFutureLessonsAndPreservesHistory() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setStartDate(AppTime.nowLocalDateTime().toLocalDate().minusDays(7));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(24); coursePackage.setConsumedUnits(4); coursePackage.setRemainingUnits(8);
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L); slot.setRuleId(rule.getId()); slot.setSlotLineageId(7001L); slot.setTeacherId(3001L);
        slot.setWeekday(1); slot.setStartTime(LocalTime.of(18, 0)); slot.setEndTime(LocalTime.of(20, 0));
        slot.setSubjectName("Math"); slot.setPlannedUnits(4); slot.setAllocatedUnits(12); slot.setSlotStatus("ACTIVE");
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setEditOperation("REPLAN"); request.setStartDate(AppTime.nowLocalDateTime().toLocalDate().plusDays(14));
        request.getSlots().get(0).setSlotId(slot.getId());
        request.getSlots().get(0).setAllocatedUnits(8);
        LessonOccurrenceEntity future = buildRuleOccurrence(9301L,
                AppTime.nowLocalDateTime().toLocalDate().plusDays(2), 4, "SCHEDULED");
        future.setScheduleSlotId(slot.getId()); future.setScheduleRuleId(rule.getId());
        LessonOccurrenceEntity history = buildRuleOccurrence(9302L,
                AppTime.nowLocalDateTime().toLocalDate().minusDays(7), 4, "MERGED");
        history.setScheduleSlotId(slot.getId()); history.setScheduleRuleId(rule.getId()); history.setPlannedTeacherId(3001L);
        LessonRecordEntity confirmed = buildRecord(history.getId(), 4);
        confirmed.setPackageId(1001L); confirmed.setConfirmStage("CONFIRMED"); confirmed.setShouldDeduct(1); confirmed.setActualTeacherId(3001L);
        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(history, future));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(confirmed));
        when(lessonOccurrenceMapper.softDeleteFutureUnprocessed(any(), any())).thenReturn(1);
        doAnswer(invocation -> { RecurringScheduleRuleEntity created = invocation.getArgument(0); created.setId(5002L); return 1; })
                .when(recurringScheduleRuleMapper).insert(any(RecurringScheduleRuleEntity.class));
        doAnswer(invocation -> { RecurringScheduleSlotEntity created = invocation.getArgument(0); created.setId(nextSlotId++); return 1; })
                .when(recurringScheduleSlotMapper).insert(any(RecurringScheduleSlotEntity.class));

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(rule.getRuleStatus()).isEqualTo("ENDED");
        assertThat(result.ruleId()).isEqualTo(5002L);
        verify(lessonOccurrenceMapper).softDeleteFutureUnprocessed(org.mockito.ArgumentMatchers.eq(List.of(9301L)), any());
        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues()).allSatisfy(lesson ->
                assertThat(lesson.getPlannedStartAt().toLocalDate()).isAfterOrEqualTo(request.getStartDate()));
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(8);
        assertThat(confirmed.getActualTeacherId()).isEqualTo(3001L);
        assertThat(confirmed.getDeductedUnits()).isEqualTo(4);
    }

    @Test
    void legacySameRuleSlotCountsConfirmedHoursAgainstCurrentAllocation() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        RecurringScheduleSlotEntity current = new RecurringScheduleSlotEntity();
        current.setId(6001L); current.setRuleId(rule.getId()); current.setSlotStatus("ACTIVE");
        current.setWeekday(4); current.setStartTime(LocalTime.of(18, 0)); current.setEndTime(LocalTime.of(20, 0));
        current.setPlannedUnits(4); current.setAllocatedUnits(8);
        RecurringScheduleSlotEntity old = new RecurringScheduleSlotEntity();
        old.setId(6002L); old.setRuleId(rule.getId()); old.setWeekday(4);
        old.setStartTime(current.getStartTime()); old.setEndTime(current.getEndTime()); old.setPlannedUnits(4);
        LessonOccurrenceEntity historical = buildRuleOccurrence(9301L, LocalDate.of(2026, 9, 24), 4, "MERGED");
        historical.setScheduleSlotId(old.getId()); historical.setScheduleRuleId(rule.getId());
        LessonOccurrenceEntity future = buildRuleOccurrence(9302L, LocalDate.of(2026, 11, 5), 4, "SCHEDULED");
        future.setScheduleSlotId(current.getId()); future.setScheduleRuleId(rule.getId());
        LessonRecordEntity record = buildRecord(historical.getId(), 4);
        record.setConfirmStage("CONFIRMED"); record.setShouldDeduct(1); record.setConfirmedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(old));

        ReflectionTestUtils.invokeMethod(recurringScheduleRuleService, "ensureCurrentPeriodAllocationIsCovered",
                List.of(current), List.of(historical, future), Map.of(historical.getId(), record), rule);

        assertThat(historical.getScheduleSlotId()).isEqualTo(old.getId());
        assertThat(record.getDeductedUnits()).isEqualTo(4);
    }

    @Test
    void legacySlotWithAmbiguousCurrentMatchesIsNotAssigned() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        RecurringScheduleSlotEntity old = new RecurringScheduleSlotEntity();
        old.setId(6002L); old.setRuleId(rule.getId()); old.setWeekday(4);
        old.setStartTime(LocalTime.of(18, 0)); old.setEndTime(LocalTime.of(20, 0)); old.setPlannedUnits(4);
        RecurringScheduleSlotEntity first = new RecurringScheduleSlotEntity();
        first.setId(6001L); first.setRuleId(rule.getId()); first.setWeekday(4);
        first.setStartTime(old.getStartTime()); first.setEndTime(old.getEndTime()); first.setPlannedUnits(4);
        RecurringScheduleSlotEntity second = new RecurringScheduleSlotEntity();
        second.setId(6003L); second.setRuleId(rule.getId()); second.setWeekday(4);
        second.setStartTime(old.getStartTime()); second.setEndTime(old.getEndTime()); second.setPlannedUnits(4);
        LessonOccurrenceEntity historical = buildRuleOccurrence(9301L, LocalDate.of(2026, 9, 24), 4, "MERGED");
        historical.setScheduleSlotId(old.getId()); historical.setScheduleRuleId(rule.getId());
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(old));

        Map<Long, Long> mapping = ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "resolveCurrentSlotIds", rule, List.of(first, second), List.of(historical));

        assertThat(mapping).doesNotContainKey(old.getId());
    }

    @Test
    void ruleDetailsCountOnlyDeductionsInCurrentPeriod() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        LocalDateTime now = AppTime.nowLocalDateTime();
        rule.setAllocationPeriodStartedAt(now.minusDays(30));
        rule.setDefaultClassroomId(null);
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(240);
        coursePackage.setRemainingUnits(5);
        LessonOccurrenceEntity oldLesson = buildRuleOccurrence(9301L, now.minusDays(40).toLocalDate(), 20, "SCHEDULED");
        LessonOccurrenceEntity currentLesson = buildRuleOccurrence(9302L, now.minusDays(7).toLocalDate(), 12, "SCHEDULED");
        LessonRecordEntity oldRecord = buildRecord(oldLesson.getId(), 20);
        oldRecord.setPackageId(coursePackage.getId());
        oldRecord.setConfirmStage("CONFIRMED");
        oldRecord.setConfirmedAt(now.minusDays(39));
        LessonRecordEntity currentRecord = buildRecord(currentLesson.getId(), 12);
        currentRecord.setPackageId(coursePackage.getId());
        currentRecord.setConfirmStage("CONFIRMED");
        currentRecord.setConfirmedAt(now.minusDays(6));
        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(oldLesson, currentLesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(oldRecord, currentRecord));

        var detail = recurringScheduleRuleService.getRule(5001L);

        assertThat(detail.deductedHours()).isEqualTo(6.0);
        assertThat(detail.pendingHours()).isZero();
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(5);
        assertThat(oldRecord.getDeductedUnits()).isEqualTo(20);
        assertThat(currentRecord.getDeductedUnits()).isEqualTo(12);
    }

    @Test
    void reducingAllocationCannotRemoveAnAdjustedFutureLesson() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        CoursePackageEntity coursePackage = buildPackage();
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        RecurringScheduleSlotRequest requested = request.getSlots().get(0);
        requested.setSlotId(6001L);
        requested.setAllocatedUnits(4);
        requested.setDeliveryMode("IN_PERSON");
        requested.setClassroomId(4001L);

        RecurringScheduleSlotEntity currentSlot = new RecurringScheduleSlotEntity();
        currentSlot.setId(6001L);
        currentSlot.setRuleId(rule.getId());
        currentSlot.setTeacherId(3001L);
        currentSlot.setSubjectName("Math");
        currentSlot.setWeekday(requested.getWeekday());
        currentSlot.setStartTime(requested.getStartTime());
        currentSlot.setEndTime(requested.getEndTime());
        currentSlot.setPlannedUnits(requested.getPlannedUnits());
        currentSlot.setAllocatedUnits(8);
        currentSlot.setDeliveryMode("IN_PERSON");
        currentSlot.setClassroomId(4001L);
        currentSlot.setSlotStatus("ACTIVE");
        LessonOccurrenceEntity adjustedFuture = buildRuleOccurrence(9301L,
                request.getStartDate().plusWeeks(1), 8, "ADJUSTED");
        adjustedFuture.setScheduleSlotId(currentSlot.getId());
        adjustedFuture.setPlannedTeacherId(3001L);

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(currentSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(adjustedFuture));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> recurringScheduleRuleService.updateRule(5001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("difference is reserved by started, adjusted, or processed lessons");

        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never())
                .updateAllocatedUnits(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never())
                .softDeleteFutureUnprocessed(any(), any());
    }

    @Test
    void sharedScheduleLocksAllMemberPackagesInStableOrder() {
        PrivateLessonGroupMemberEntity secondary = new PrivateLessonGroupMemberEntity();
        secondary.setPrivateLessonGroupId(7001L); secondary.setPackageId(1002L);
        PrivateLessonGroupMemberEntity primary = new PrivateLessonGroupMemberEntity();
        primary.setPrivateLessonGroupId(7001L); primary.setPackageId(1001L);
        when(privateLessonGroupMemberMapper.selectOne(any())).thenReturn(secondary);
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(List.of(secondary, primary));
        when(coursePackageMapper.selectByIdForUpdate(1001L)).thenReturn(buildPackage());
        when(coursePackageMapper.selectByIdForUpdate(1002L)).thenReturn(buildPackage());

        recurringScheduleRuleService.lockSchedulePackages(1002L);

        org.mockito.InOrder locks = org.mockito.Mockito.inOrder(coursePackageMapper);
        locks.verify(coursePackageMapper).selectByIdForUpdate(1001L);
        locks.verify(coursePackageMapper).selectByIdForUpdate(1002L);
    }

    @Test
    void balanceReductionCannotUndercutAnExistingSlotAllocation() {
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(120);
        coursePackage.setRemainingUnits(106);
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 9, 27, 0, 0));
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setAllocatedUnits(112);
        LessonOccurrenceEntity lesson = buildRuleOccurrence(9001L, LocalDate.of(2026, 10, 3), 6, "MERGED");
        lesson.setScheduleRuleId(rule.getId());
        LessonRecordEntity confirmed = buildRecord(lesson.getId(), 6);
        confirmed.setPackageId(1001L);
        confirmed.setConfirmStage("CONFIRMED");
        confirmed.setShouldDeduct(1);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(lesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(confirmed));
        when(recurringScheduleRuleMapper.selectBatchIds(any())).thenReturn(List.of(rule));

        assertThatThrownBy(() -> recurringScheduleRuleService.validateBalanceReduction(1001L, 105))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("PACKAGE_BALANCE_BELOW_SCHEDULE");
                    assertThat(error.getMessage()).contains("52.5h").contains("53.0h");
                });
        recurringScheduleRuleService.validateBalanceReduction(1001L, 106);
        assertThat(slot.getAllocatedUnits()).isEqualTo(112);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(106);
    }

    @Test
    void balanceReductionCountsPendingOnlyOnceAndReservesUnfinishedOldLesson() {
        CoursePackageEntity coursePackage = buildPackage();
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 9, 27, 0, 0));
        RecurringScheduleRuleEntity ended = new RecurringScheduleRuleEntity();
        ended.setId(4001L);
        ended.setRuleStatus("ENDED");
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setAllocatedUnits(112);
        LessonOccurrenceEntity confirmedLesson = buildRuleOccurrence(9001L, LocalDate.of(2026, 10, 3), 6, "MERGED");
        confirmedLesson.setScheduleRuleId(rule.getId());
        LessonOccurrenceEntity pendingLesson = buildRuleOccurrence(9002L, LocalDate.of(2026, 10, 10), 4, "SCHEDULED");
        pendingLesson.setScheduleRuleId(rule.getId());
        LessonOccurrenceEntity oldLesson = buildRuleOccurrence(9003L, LocalDate.of(2026, 9, 20), 2, "SCHEDULED");
        oldLesson.setScheduleRuleId(ended.getId());
        LessonRecordEntity confirmed = buildRecord(9001L, 6);
        confirmed.setPackageId(1001L); confirmed.setConfirmStage("CONFIRMED"); confirmed.setShouldDeduct(1);
        LessonRecordEntity pending = buildRecord(9002L, 4);
        pending.setPackageId(1001L); pending.setConfirmStage("PENDING_ADMIN");
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(
                List.of(confirmedLesson, pendingLesson), List.of(pendingLesson),
                List.of(confirmedLesson, pendingLesson, oldLesson));
        when(lessonRecordMapper.selectList(any())).thenReturn(
                List.of(confirmed, pending), List.of(pending), List.of(pending), Collections.emptyList());
        when(recurringScheduleRuleMapper.selectBatchIds(any())).thenReturn(List.of(rule, ended));

        assertThatThrownBy(() -> recurringScheduleRuleService.validateBalanceReduction(1001L, 107))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("PACKAGE_BALANCE_BELOW_SCHEDULE");
                    assertThat(error.getMessage()).contains("53.5h").contains("54.0h");
                });
    }

    @Test
    void shouldRejectManualScheduleEditAboveRemainingHours() {
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.getSlots().get(0).setAllocatedUnits(80);
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(80);
        coursePackage.setConsumedUnits(32);
        coursePackage.setRemainingUnits(48);
        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(buildExistingRule());
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());

        assertThatThrownBy(() -> recurringScheduleRuleService.updateRule(5001L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("SLOT_ALLOCATION_EXCEEDS_PACKAGE");
                    assertThat(exception.getMessage()).contains("current package balance after pending lessons");
                    assertThat(exception.getData()).isInstanceOf(ScheduleAllocationErrorVO.class);
                    ScheduleAllocationErrorVO details = (ScheduleAllocationErrorVO) exception.getData();
                    assertThat(details.packageTotalHours()).isEqualTo(40.0);
                    assertThat(details.packageRemainingHours()).isEqualTo(24.0);
                    assertThat(details.requestedAllocationHours()).isEqualTo(40.0);
                    assertThat(details.availableAllocationHours()).isEqualTo(24.0);
                    assertThat(details.shortfallHours()).isEqualTo(16.0);
                    assertThat(details.pendingReviewHours()).isZero();
                    assertThat(details.otherScheduleReservedHours()).isZero();
                    assertThat(details.preservedLessonHours()).isZero();
                    assertThat(details.transferredFutureLessonHours()).isZero();
                });
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never()).updateById(any(RecurringScheduleRuleEntity.class));
    }

    @Test
    void balanceRefreshKeepsExistingTargetsAndCapsFutureLessons() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(80);
        coursePackage.setConsumedUnits(32);
        coursePackage.setRemainingUnits(48);
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(6001L);
        slot.setRuleId(5001L);
        slot.setTeacherId(3001L);
        slot.setSubjectName("Math");
        slot.setWeekday(1);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        slot.setPlannedUnits(4);
        slot.setAllocatedUnits(80);
        slot.setSlotStatus("ACTIVE");
        slot.setCreatedAt(LocalDateTime.of(2026, 10, 3, 10, 30));
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        List<LessonOccurrenceEntity> persistedOccurrences = new ArrayList<>();
        when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation -> persistedOccurrences);
        doAnswer(invocation -> {
            persistedOccurrences.add(invocation.getArgument(0));
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));
        when(recurringScheduleRuleMapper.updateById(any(RecurringScheduleRuleEntity.class))).thenReturn(1);
        recurringScheduleRuleService.refreshPackageSchedule(1001L);

        assertThat(rule.getAllocationPeriodStartedAt()).isNull();
        assertThat(com.classdeduction.system.modules.schedule.util.PrivateSchedulePeriod.startAt(rule))
                .isEqualTo(rule.getCreatedAt());
        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleSlotEntity.class));
        assertThat(slot.getAllocatedUnits()).isEqualTo(80);
        verify(lessonOccurrenceMapper, times(12)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues().stream().mapToInt(LessonOccurrenceEntity::getPlannedUnits).sum())
                .isEqualTo(48);
        assertThat(occurrenceCaptor.getAllValues()).allMatch(occurrence -> Long.valueOf(6001L).equals(occurrence.getScheduleSlotId()));
    }

    @Test
    void balanceRefreshPreservesMultipleSlotsAndTheirConfirmedHistory() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(240);
        coursePackage.setConsumedUnits(236);
        coursePackage.setRemainingUnits(4);
        List<RecurringScheduleSlotEntity> slots = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
            slot.setId(6001L + index);
            slot.setRuleId(5001L);
            slot.setTeacherId(3001L);
            slot.setSubjectName("Math");
            slot.setWeekday(index == 0 ? 1 : 6);
            slot.setStartTime(LocalTime.of(18, 0));
            slot.setEndTime(LocalTime.of(20, 0));
            slot.setPlannedUnits(4);
            slot.setAllocatedUnits(8);
            slot.setSlotStatus("ACTIVE");
            slots.add(slot);
        }
        LessonOccurrenceEntity first = buildRuleOccurrence(9001L, AppTime.nowLocalDateTime().toLocalDate().minusDays(14), 4, "MERGED");
        first.setScheduleSlotId(6001L);
        first.setPlannedTeacherId(3001L);
        first.setSubjectName("Math");
        LessonOccurrenceEntity second = buildRuleOccurrence(9002L, AppTime.nowLocalDateTime().toLocalDate().minusDays(7), 4, "MERGED");
        second.setScheduleSlotId(6002L);
        second.setPlannedTeacherId(3001L);
        second.setSubjectName("Math");
        LessonRecordEntity firstRecord = buildRecord(first.getId(), 4);
        firstRecord.setConfirmStage("CONFIRMED");
        LessonRecordEntity secondRecord = buildRecord(second.getId(), 4);
        secondRecord.setConfirmStage("CONFIRMED");
        RecurringScheduleSlotEntity inactiveSlot = new RecurringScheduleSlotEntity();
        inactiveSlot.setId(6003L);
        inactiveSlot.setRuleId(5001L);
        inactiveSlot.setSlotStatus("INACTIVE");
        LessonOccurrenceEntity inactiveHistory = buildRuleOccurrence(9003L, AppTime.nowLocalDateTime().toLocalDate().minusDays(21), 4, "MERGED");
        inactiveHistory.setScheduleSlotId(6003L);
        LessonRecordEntity inactiveRecord = buildRecord(inactiveHistory.getId(), 4);
        inactiveRecord.setConfirmStage("CONFIRMED");

        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(rule), Collections.emptyList());
        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(slots, List.of(slots.get(0), slots.get(1), inactiveSlot));
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(first, second, inactiveHistory));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(firstRecord, secondRecord, inactiveRecord));
        when(recurringScheduleRuleMapper.updateById(any(RecurringScheduleRuleEntity.class))).thenReturn(1);

        recurringScheduleRuleService.refreshPackageSchedule(1001L);

        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleSlotEntity.class));
        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never()).delete(any());
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).softDeleteFutureUnprocessed(any(), any());
        verify(lessonOccurrenceMapper, org.mockito.Mockito.atLeastOnce()).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues()).allMatch(occurrence ->
                List.of(6001L, 6002L).contains(occurrence.getScheduleSlotId()));
        assertThat(occurrenceCaptor.getAllValues().stream().mapToInt(LessonOccurrenceEntity::getPlannedUnits).sum())
                .isLessThanOrEqualTo(4);
        assertThat(first.getScheduleSlotId()).isEqualTo(6001L);
        assertThat(second.getScheduleSlotId()).isEqualTo(6002L);
        assertThat(inactiveHistory.getScheduleSlotId()).isEqualTo(6003L);
        assertThat(firstRecord.getDeductedUnits()).isEqualTo(4);
        assertThat(secondRecord.getDeductedUnits()).isEqualTo(4);
    }

    @Test
    void manualEditStillRejectsAmbiguousHistoricalSlotMapping() {
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setSlots(List.of(buildSlot(1, LocalTime.of(18, 0), 4, 8),
                buildSlot(6, LocalTime.of(18, 0), 4, 8)));
        RecurringScheduleSlotEntity firstSlot = new RecurringScheduleSlotEntity();
        firstSlot.setId(6001L);
        RecurringScheduleSlotEntity secondSlot = new RecurringScheduleSlotEntity();
        secondSlot.setId(6002L);
        LessonOccurrenceEntity first = buildRuleOccurrence(9001L, AppTime.nowLocalDateTime().toLocalDate().minusDays(14), 4, "MERGED");
        first.setScheduleSlotId(6001L);
        LessonOccurrenceEntity second = buildRuleOccurrence(9002L, AppTime.nowLocalDateTime().toLocalDate().minusDays(7), 4, "MERGED");
        second.setScheduleSlotId(6002L);
        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(buildExistingRule());
        when(coursePackageMapper.selectById(1001L)).thenReturn(buildPackage());
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(firstSlot, secondSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> recurringScheduleRuleService.updateRule(5001L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("AMBIGUOUS_SLOT_LINEAGE"));
        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never()).delete(any());
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).softDeleteFutureUnprocessed(any(), any());
    }

    @Test
    void zeroBalanceRemovesOnlyFutureUnrecordedRegularLessons() {
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setPackageStatus("COMPLETED");
        coursePackage.setRemainingUnits(0);
        LocalDate today = AppTime.nowLocalDateTime().toLocalDate();
        LessonOccurrenceEntity history = buildRuleOccurrence(9001L, today.minusDays(2), 4, "SCHEDULED");
        LessonOccurrenceEntity firstFuture = buildRuleOccurrence(9002L, today.plusDays(2), 4, "SCHEDULED");
        LessonOccurrenceEntity secondFuture = buildRuleOccurrence(9003L, today.plusDays(7), 4, "SCHEDULED");
        firstFuture.setOccurrenceType("REGULAR");
        secondFuture.setOccurrenceType("REGULAR");
        LessonRecordEntity historyRecord = buildRecord(9001L, 4);
        historyRecord.setConfirmStage("CONFIRMED");
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(history, firstFuture, secondFuture));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(historyRecord));
        when(lessonOccurrenceMapper.softDeleteFutureRegularUnprocessed(any(), any())).thenReturn(2);

        recurringScheduleRuleService.refreshPackageSchedule(1001L);

        verify(lessonOccurrenceMapper).softDeleteFutureRegularUnprocessed(org.mockito.ArgumentMatchers.eq(List.of(9002L, 9003L)), any());
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).insert(any(LessonOccurrenceEntity.class));
        verify(recurringScheduleSlotMapper, org.mockito.Mockito.never()).delete(any());
        assertThat(historyRecord.getDeductedUnits()).isEqualTo(4);
    }

    @Test
    void zeroBalanceRejectsUnresolvedPastAdjustedAndMakeupLessons() {
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setPackageStatus("COMPLETED");
        coursePackage.setRemainingUnits(0);
        LocalDate today = AppTime.nowLocalDateTime().toLocalDate();
        LessonOccurrenceEntity past = buildRuleOccurrence(9001L, today.minusDays(2), 4, "SCHEDULED");
        past.setOccurrenceType("REGULAR");
        LessonOccurrenceEntity adjusted = buildRuleOccurrence(9002L, today.plusDays(2), 4, "ADJUSTED");
        adjusted.setOccurrenceType("REGULAR");
        LessonOccurrenceEntity makeup = buildRuleOccurrence(9003L, today.plusDays(3), 4, "SCHEDULED");
        makeup.setOccurrenceType("MAKEUP");
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(past), List.of(adjusted), List.of(makeup));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        for (String expected : List.of("in 1-on-1 Lessons", "was changed", "is a makeup lesson")) {
            assertThatThrownBy(() -> recurringScheduleRuleService.refreshPackageSchedule(1001L))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            {
                                assertThat(exception.getCode()).isEqualTo("ZERO_BALANCE_LESSON_REVIEW_REQUIRED");
                                assertThat(exception.getMessage()).contains(expected).contains("before setting remaining hours to 0");
                            });
        }
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).softDeleteFutureRegularUnprocessed(any(), any());
    }

    @Test
    void zeroBalanceDoesNotCancelAnActiveSharedSchedule() {
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setPackageStatus("COMPLETED");
        coursePackage.setRemainingUnits(0);
        PrivateLessonGroupMemberEntity member = new PrivateLessonGroupMemberEntity();
        member.setPrivateLessonGroupId(42L);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(privateLessonGroupMemberMapper.selectOne(any())).thenReturn(member);
        when(recurringScheduleRuleMapper.selectCount(any())).thenReturn(1L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> recurringScheduleRuleService.refreshPackageSchedule(1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ZERO_BALANCE_SHARED_SCHEDULE"));
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).softDeleteFutureRegularUnprocessed(any(), any());
    }

    @Test
    void zeroBalanceRejectsSharedFutureLessonEvenWhenRuleIsEnded() {
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setPackageStatus("COMPLETED");
        coursePackage.setRemainingUnits(0);
        PrivateLessonGroupMemberEntity member = new PrivateLessonGroupMemberEntity();
        member.setPrivateLessonGroupId(42L);
        LessonOccurrenceEntity shared = buildRuleOccurrence(9001L, AppTime.nowLocalDateTime().toLocalDate().plusDays(2), 4, "SCHEDULED");
        shared.setPrivateLessonGroupId(42L);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(privateLessonGroupMemberMapper.selectOne(any())).thenReturn(member);
        when(recurringScheduleRuleMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(shared));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> recurringScheduleRuleService.refreshPackageSchedule(1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ZERO_BALANCE_SHARED_SCHEDULE"));
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).softDeleteFutureRegularUnprocessed(any(), any());
    }

    @Test
    void zeroBalanceAbortsWhenFutureLessonChangesDuringRemoval() {
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setPackageStatus("COMPLETED");
        coursePackage.setRemainingUnits(0);
        LessonOccurrenceEntity future = buildRuleOccurrence(9001L, AppTime.nowLocalDateTime().toLocalDate().plusDays(2), 4, "SCHEDULED");
        future.setOccurrenceType("REGULAR");
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(future));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.softDeleteFutureRegularUnprocessed(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> recurringScheduleRuleService.refreshPackageSchedule(1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("LESSON_CHANGED"));
    }

    @Test
    void zeroBalanceExplainsThatRejectedFutureRecordNeedsReview() {
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setPackageStatus("COMPLETED");
        coursePackage.setRemainingUnits(0);
        LessonOccurrenceEntity future = buildRuleOccurrence(9001L, AppTime.nowLocalDateTime().toLocalDate().plusDays(2), 4, "SCHEDULED");
        future.setOccurrenceType("REGULAR");
        LessonRecordEntity rejected = buildRecord(9001L, 0);
        rejected.setConfirmStage("REJECTED");
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(future));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(rejected));

        assertThatThrownBy(() -> recurringScheduleRuleService.refreshPackageSchedule(1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("ZERO_BALANCE_LESSON_REVIEW_REQUIRED");
                    assertThat(exception.getMessage()).contains("in 1-on-1 Lessons");
                });
        verify(lessonOccurrenceMapper, org.mockito.Mockito.never()).softDeleteFutureRegularUnprocessed(any(), any());
    }

    @Test
    void updatingSchedulePreservesSubjectOfConfirmedLegacyLesson() {
        RecurringScheduleRuleEntity rule = buildExistingRule();
        rule.setRuleStatus("PAUSED");
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setPackageStatus("PAUSED");
        coursePackage.setTotalUnits(80);
        coursePackage.setRemainingUnits(72);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
        request.setRuleStatus("PAUSED");

        RecurringScheduleSlotEntity oldSlot = new RecurringScheduleSlotEntity();
        oldSlot.setId(6001L);
        oldSlot.setSubjectName("Vocabulary");
        oldSlot.setIsDeleted(1);
        LessonOccurrenceEntity completedLesson = buildRuleOccurrence(9001L,
                AppTime.nowLocalDateTime().toLocalDate().minusDays(1), 4, "MERGED");
        completedLesson.setScheduleSlotId(oldSlot.getId());
        LessonRecordEntity confirmedRecord = buildRecord(completedLesson.getId(), 4);
        confirmedRecord.setConfirmStage("CONFIRMED");

        when(recurringScheduleRuleMapper.selectById(5001L)).thenReturn(rule);
        when(coursePackageMapper.selectById(1001L)).thenReturn(coursePackage);
        when(studentMapper.selectById(2001L)).thenReturn(buildStudent());
        when(teacherMapper.selectById(3001L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(4001L)).thenReturn(buildClassroom());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(confirmedRecord));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(completedLesson));
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(any())).thenReturn(List.of(oldSlot));

        recurringScheduleRuleService.updateRule(5001L, request);

        ArgumentCaptor<LessonOccurrenceEntity> preservedLesson = ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper).updateById(preservedLesson.capture());
        assertThat(preservedLesson.getValue().getId()).isEqualTo(completedLesson.getId());
        assertThat(preservedLesson.getValue().getSubjectName()).isEqualTo("Vocabulary");
        assertThat(completedLesson.getScheduleSlotId()).isEqualTo(oldSlot.getId());
    }

    @Test
    private void stubGeneratedIds(boolean includeOccurrenceInsert) {
        doAnswer(invocation -> {
            RecurringScheduleRuleEntity entity = invocation.getArgument(0);
            entity.setId(5001L);
            return 1;
        }).when(recurringScheduleRuleMapper).insert(any(RecurringScheduleRuleEntity.class));
        doAnswer(invocation -> {
            RecurringScheduleSlotEntity entity = invocation.getArgument(0);
            entity.setId(nextSlotId++);
            return 1;
        }).when(recurringScheduleSlotMapper).insert(any(RecurringScheduleSlotEntity.class));
        if (includeOccurrenceInsert) {
            doAnswer(invocation -> {
                LessonOccurrenceEntity entity = invocation.getArgument(0);
                return 1;
            }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));
        }
    }

    private RecurringScheduleRuleCreateRequest buildCreateRequest() {
        LocalDate nextMonday = AppTime.nowLocalDateTime().toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        if (!nextMonday.isAfter(AppTime.nowLocalDateTime().toLocalDate())) {
            nextMonday = nextMonday.plusWeeks(1);
        }
        RecurringScheduleSlotRequest slot = new RecurringScheduleSlotRequest();
        slot.setTeacherId(3001L);
        slot.setSubjectName("Math");
        slot.setWeekday(1);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        slot.setPlannedUnits(4);
        slot.setAllocatedUnits(8);

        RecurringScheduleRuleCreateRequest request = new RecurringScheduleRuleCreateRequest();
        request.setPackageId(1001L);
        request.setDefaultClassroomId(4001L);
        request.setStartDate(nextMonday);
        request.setNotes("Weeknight math");
        request.setSlots(List.of(slot));
        return request;
    }

    private RecurringScheduleSlotRequest buildSlot(int weekday, LocalTime startTime, int plannedUnits, Integer allocatedUnits) {
        RecurringScheduleSlotRequest slot = new RecurringScheduleSlotRequest();
        slot.setTeacherId(3001L);
        slot.setSubjectName("Math");
        slot.setWeekday(weekday);
        slot.setStartTime(startTime);
        slot.setEndTime(startTime.plusMinutes(plannedUnits * 30L));
        slot.setPlannedUnits(plannedUnits);
        slot.setAllocatedUnits(allocatedUnits);
        return slot;
    }

    private RecurringScheduleRuleCreateRequest buildCreateRequestWithSlots(List<RecurringScheduleSlotRequest> slots) {
        LocalDate nextMonday = AppTime.nowLocalDateTime().toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        if (!nextMonday.isAfter(AppTime.nowLocalDateTime().toLocalDate())) {
            nextMonday = nextMonday.plusWeeks(1);
        }
        RecurringScheduleRuleCreateRequest request = new RecurringScheduleRuleCreateRequest();
        request.setPackageId(1001L);
        request.setDefaultClassroomId(4001L);
        request.setStartDate(nextMonday);
        request.setNotes("Per-slot allocation");
        request.setSlots(slots);
        return request;
    }

    private RecurringScheduleRuleUpdateRequest buildUpdateRequest(int plannedUnits) {
        LocalDate nextMonday = AppTime.nowLocalDateTime().toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        if (!nextMonday.isAfter(AppTime.nowLocalDateTime().toLocalDate())) {
            nextMonday = nextMonday.plusWeeks(1);
        }
        RecurringScheduleSlotRequest slot = new RecurringScheduleSlotRequest();
        slot.setTeacherId(3001L);
        slot.setSubjectName("Math");
        slot.setWeekday(1);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(18, 0).plusMinutes(plannedUnits * 30L));
        slot.setPlannedUnits(plannedUnits);
        slot.setAllocatedUnits(plannedUnits * 7);

        RecurringScheduleRuleUpdateRequest request = new RecurringScheduleRuleUpdateRequest();
        request.setDefaultClassroomId(4001L);
        request.setRuleStatus("ACTIVE");
        request.setStartDate(nextMonday);
        request.setNotes("Updated schedule");
        request.setSlots(List.of(slot));
        return request;
    }

    private RecurringScheduleRuleEntity buildExistingRule() {
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(5001L);
        rule.setPackageId(1001L);
        rule.setPrimaryTeacherId(3001L);
        rule.setDefaultClassroomId(4001L);
        rule.setRuleStatus("ACTIVE");
        rule.setStartDate(buildUpdateRequest(4).getStartDate());
        return rule;
    }

    private CoursePackageEntity buildPackage() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(1001L);
        coursePackage.setStudentId(2001L);
        coursePackage.setPackageNo("PK-1001");
        coursePackage.setPackageName("SHSAT Math");
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setTotalUnits(8);
        return coursePackage;
    }

    private StudentEntity buildStudent() {
        StudentEntity student = new StudentEntity();
        student.setId(2001L);
        student.setStudentName("Olivia Lin");
        student.setStudentNo("STU-2001");
        return student;
    }

    private TeacherEntity buildTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(3001L);
        teacher.setTeacherName("Emma Wang");
        teacher.setStatus("ACTIVE");
        return teacher;
    }

    @Test
    void preservesPreviouslyVisibleLegacySlotHistoryBeforeReplan() {
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(50L);
        RecurringScheduleSlotEntity current = new RecurringScheduleSlotEntity();
        current.setId(10L);
        current.setRuleId(50L);
        current.setSlotLineageId(11L);
        current.setWeekday(3);
        current.setStartTime(LocalTime.of(20, 0));
        current.setEndTime(LocalTime.of(22, 0));
        current.setPlannedUnits(4);
        RecurringScheduleSlotEntity old = new RecurringScheduleSlotEntity();
        old.setId(9L);
        old.setRuleId(50L);
        old.setWeekday(3);
        old.setStartTime(LocalTime.of(20, 0));
        old.setEndTime(LocalTime.of(22, 0));
        old.setPlannedUnits(4);
        LessonOccurrenceEntity historical = new LessonOccurrenceEntity();
        historical.setId(20L);
        historical.setScheduleRuleId(50L);
        historical.setScheduleSlotId(9L);
        when(recurringScheduleSlotMapper.selectIncludingDeletedByIds(List.of(9L))).thenReturn(List.of(old));
        when(lessonOccurrenceMapper.preserveLegacyHistoryOwner(20L, 9L, 11L)).thenReturn(1);
        ReflectionTestUtils.invokeMethod(recurringScheduleRuleService, "preserveLegacyHistoryOwners",
                rule, List.of(current), List.of(historical));
        verify(lessonOccurrenceMapper).preserveLegacyHistoryOwner(20L, 9L, 11L);
        assertThat(historical.getScheduleSlotId()).isEqualTo(9L);
    }

    @Test
    void assignsConfirmedUnlinkedHistoryToChosenCurrentLineage() {
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(10L);
        slot.setSlotLineageId(11L);
        slot.setSlotStatus("ACTIVE");
        RecurringScheduleSlotRequest choice = new RecurringScheduleSlotRequest();
        choice.setSlotId(10L);
        choice.setInheritedOccurrenceIds(List.of(20L));
        RecurringScheduleRuleUpdateRequest request = new RecurringScheduleRuleUpdateRequest();
        request.setEditOperation("CURRENT");
        request.setSlots(List.of(choice));
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(20L);
        occurrence.setPackageId(30L);
        LessonRecordEntity record = new LessonRecordEntity();
        record.setPackageId(30L);
        record.setConfirmStage("CONFIRMED");
        when(lessonOccurrenceMapper.selectByIdForUpdate(20L)).thenReturn(occurrence);
        when(lessonRecordMapper.selectByOccurrenceIdForUpdate(20L)).thenReturn(List.of(record));
        when(lessonOccurrenceMapper.assignUnlinkedHistory(20L, 11L)).thenReturn(1);

        ReflectionTestUtils.invokeMethod(recurringScheduleRuleService, "assignUnlinkedHistory",
                request, List.of(slot), 30L, null);

        verify(lessonOccurrenceMapper).assignUnlinkedHistory(20L, 11L);
        assertThat(occurrence.getScheduleSlotId()).isNull();
        assertThat(record.getConfirmStage()).isEqualTo("CONFIRMED");
    }

    @Test
    void rejectsHistoryFromAnotherPackage() {
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(10L);
        slot.setSlotStatus("ACTIVE");
        RecurringScheduleSlotRequest choice = new RecurringScheduleSlotRequest();
        choice.setSlotId(10L);
        choice.setInheritedOccurrenceIds(List.of(20L));
        RecurringScheduleRuleUpdateRequest request = new RecurringScheduleRuleUpdateRequest();
        request.setEditOperation("CURRENT");
        request.setSlots(List.of(choice));
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setPackageId(99L);
        when(lessonOccurrenceMapper.selectByIdForUpdate(20L)).thenReturn(occurrence);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(recurringScheduleRuleService,
                "assignUnlinkedHistory", request, List.of(slot), 30L, null))
                .isInstanceOf(BusinessException.class);
    }

    private ClassroomEntity buildClassroom() {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(4001L);
        classroom.setRoomName("Room A101");
        classroom.setRoomCode("A101");
        classroom.setStatus("ACTIVE");
        return classroom;
    }

    private LessonOccurrenceEntity buildTeacherConflict() {
        LessonOccurrenceEntity conflict = new LessonOccurrenceEntity();
        LocalDate nextMonday = buildCreateRequest().getStartDate();
        conflict.setId(9001L);
        conflict.setLessonNo("LS-CONFLICT");
        conflict.setPackageId(1001L);
        conflict.setStudentId(2001L);
        conflict.setSubjectName("SHSAT Math");
        conflict.setPlannedTeacherId(3001L);
        conflict.setPlannedStartAt(LocalDateTime.of(nextMonday, LocalTime.of(19, 0)));
        conflict.setPlannedEndAt(LocalDateTime.of(nextMonday, LocalTime.of(21, 0)));
        conflict.setPlanningStatus("SCHEDULED");
        return conflict;
    }

    private LessonOccurrenceEntity buildRuleOccurrence(Long id, LocalDate date, int units, String planningStatus) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(id);
        occurrence.setLessonNo("LS-" + id);
        occurrence.setPackageId(1001L);
        occurrence.setStudentId(2001L);
        occurrence.setScheduleRuleId(5001L);
        occurrence.setPlannedStartAt(LocalDateTime.of(date, LocalTime.of(18, 0)));
        occurrence.setPlannedEndAt(occurrence.getPlannedStartAt().plusMinutes(units * 30L));
        occurrence.setPlannedUnits(units);
        occurrence.setPlanningStatus(planningStatus);
        return occurrence;
    }

    private LessonRecordEntity buildRecord(Long occurrenceId, int deductedUnits) {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setId(8000L + occurrenceId);
        record.setOccurrenceId(occurrenceId);
        record.setShouldDeduct(deductedUnits > 0 ? 1 : 0);
        record.setDeductedUnits(deductedUnits);
        record.setConfirmStage("PENDING_ADMIN");
        return record;
    }
}
