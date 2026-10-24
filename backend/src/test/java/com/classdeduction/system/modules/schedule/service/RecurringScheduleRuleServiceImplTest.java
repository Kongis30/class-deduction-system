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
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed");
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
                .hasMessageContaining("remaining package hours");
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
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(buildRecord(9001L, 8)));

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("remaining package hours");
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
                .hasMessageContaining("Teacher conflict");
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
                .hasMessageContaining("another lesson");
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

        assertThat(pausedRule.getRuleStatus()).isEqualTo("PAUSED");
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
        assertThat(rule.getAllocationPeriodStartedAt()).isEqualTo(originalPeriodStart);
        verify(recurringScheduleRuleMapper, org.mockito.Mockito.never()).insert(any(RecurringScheduleRuleEntity.class));
        verify(recurringScheduleSlotMapper).insert(any(RecurringScheduleSlotEntity.class));
    }

    @Test
    void shouldCreateNewSchedulePeriodWhenOnlyTeacherChanges() {
        RecurringScheduleRuleEntity existing = buildExistingRule();
        CoursePackageEntity coursePackage = buildPackage();
        coursePackage.setTotalUnits(28);
        coursePackage.setConsumedUnits(0);
        coursePackage.setRemainingUnits(28);
        RecurringScheduleRuleUpdateRequest request = buildUpdateRequest(4);
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
        doAnswer(invocation -> {
            RecurringScheduleRuleEntity inserted = invocation.getArgument(0);
            inserted.setId(5002L);
            return 1;
        }).when(recurringScheduleRuleMapper).insert(any(RecurringScheduleRuleEntity.class));
        doAnswer(invocation -> {
            RecurringScheduleSlotEntity inserted = invocation.getArgument(0);
            inserted.setId(6002L);
            return 1;
        }).when(recurringScheduleSlotMapper).insert(any(RecurringScheduleSlotEntity.class));

        ScheduleRuleUpdateResultVO result = recurringScheduleRuleService.updateRule(5001L, request);

        assertThat(result.ruleId()).isEqualTo(5002L);
        assertThat(existing.getRuleStatus()).isEqualTo("ENDED");
        assertThat(existing.getSupersededByRuleId()).isEqualTo(5002L);
        verify(recurringScheduleRuleMapper).insert(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getSupersedesRuleId()).isEqualTo(5001L);
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
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("remaining package hours");
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
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(recurringScheduleRuleMapper.updateById(any(RecurringScheduleRuleEntity.class))).thenReturn(1);
        doAnswer(invocation -> {
            RecurringScheduleSlotEntity inserted = invocation.getArgument(0);
            inserted.setId(7001L);
            return 1;
        }).when(recurringScheduleSlotMapper).insert(any(RecurringScheduleSlotEntity.class));

        recurringScheduleRuleService.refreshPackageSchedule(1001L);

        assertThat(rule.getAllocationPeriodStartedAt())
                .isEqualTo(rule.getCreatedAt());
        verify(recurringScheduleSlotMapper).insert(slotCaptor.capture());
        assertThat(slotCaptor.getValue().getAllocatedUnits()).isEqualTo(80);
        verify(lessonOccurrenceMapper, times(12)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues().stream().mapToInt(LessonOccurrenceEntity::getPlannedUnits).sum())
                .isEqualTo(48);
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
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(), List.of(confirmedRecord));
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
            entity.setId(6001L);
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
