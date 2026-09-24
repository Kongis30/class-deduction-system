package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
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
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleCreateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleUpdateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleSlotRequest;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.impl.RecurringScheduleRuleServiceImpl;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleCreateResultVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleUpdateResultVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleStudentOptionVO;
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
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;

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
    private RecurringScheduleSlotMapper recurringScheduleSlotMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

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
    private LessonRecordMapper lessonRecordMapper;

    @Captor
    private ArgumentCaptor<RecurringScheduleRuleEntity> ruleCaptor;

    @Captor
    private ArgumentCaptor<RecurringScheduleSlotEntity> slotCaptor;

    @Captor
    private ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor;

    private RecurringScheduleRuleServiceImpl recurringScheduleRuleService;

    @BeforeEach
    void setUp() {
        recurringScheduleRuleService = new RecurringScheduleRuleServiceImpl(
                recurringScheduleRuleMapper,
                recurringScheduleSlotMapper,
                lessonOccurrenceMapper,
                lessonRecordMapper,
                coursePackageMapper,
                privateLessonGroupMapper,
                privateLessonGroupMemberMapper,
                studentMapper,
                teacherMapper,
                classroomMapper
        );
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

        verify(recurringScheduleRuleMapper).insert(ruleCaptor.capture());
        verify(recurringScheduleSlotMapper, times(1)).insert(slotCaptor.capture());
        verify(lessonOccurrenceMapper, times(2)).insert(occurrenceCaptor.capture());

        assertThat(ruleCaptor.getValue().getRuleStatus()).isEqualTo("ACTIVE");
        assertThat(slotCaptor.getValue().getWeekday()).isEqualTo(1);
        assertThat(slotCaptor.getValue().getTeacherId()).isEqualTo(3001L);
        assertThat(occurrenceCaptor.getAllValues()).hasSize(2);
        assertThat(occurrenceCaptor.getAllValues())
                .allSatisfy(occurrence -> assertThat(occurrence.getPlannedTeacherId()).isEqualTo(3001L));
        assertThat(result.ruleId()).isEqualTo(5001L);
        assertThat(result.generatedOccurrenceCount()).isEqualTo(2);
        assertThat(result.firstGeneratedStartAt()).isBefore(result.lastGeneratedEndAt());
    }

    @Test
    void shouldReturnActiveOneOnOneStudentsFromSingleLookup() {
        StudentEntity student = new StudentEntity();
        student.setId(2001L);
        student.setStudentName("Alina Chen");
        when(coursePackageMapper.selectActiveOneOnOneStudents()).thenReturn(List.of(student));

        List<ScheduleStudentOptionVO> result = recurringScheduleRuleService.listStudentsWithActiveOneOnOnePackages();

        assertThat(result).containsExactly(new ScheduleStudentOptionVO(2001L, "Alina Chen"));
        verify(coursePackageMapper).selectActiveOneOnOneStudents();
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
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(28, List.of(monday, tuesday));
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
    void shouldScheduleRemainderAsLastClass() {
        // 22h allocated at 1.5h/class => 14 full classes + one 1h remainder class = 15 classes.
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 3, 44);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(126, List.of(monday));
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
    void shouldRejectWhenSlotAllocationsExceedPackageTotal() {
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, 10);
        RecurringScheduleSlotRequest tuesday = buildSlot(2, LocalTime.of(18, 0), 2, 10);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(28, List.of(monday, tuesday));
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
    void shouldRejectWhenSlotAllocationIsMissing() {
        RecurringScheduleSlotRequest monday = buildSlot(1, LocalTime.of(18, 0), 4, null);
        RecurringScheduleRuleCreateRequest request = buildCreateRequestWithSlots(28, List.of(monday));
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
        request.setGenerationHorizonDays(14);
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

    private RecurringScheduleRuleCreateRequest buildCreateRequestWithSlots(int horizonDays, List<RecurringScheduleSlotRequest> slots) {
        LocalDate nextMonday = AppTime.nowLocalDateTime().toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        if (!nextMonday.isAfter(AppTime.nowLocalDateTime().toLocalDate())) {
            nextMonday = nextMonday.plusWeeks(1);
        }
        RecurringScheduleRuleCreateRequest request = new RecurringScheduleRuleCreateRequest();
        request.setPackageId(1001L);
        request.setDefaultClassroomId(4001L);
        request.setStartDate(nextMonday);
        request.setGenerationHorizonDays(horizonDays);
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
        request.setGenerationHorizonDays(28);
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
        rule.setGenerationHorizonDays(28);
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
