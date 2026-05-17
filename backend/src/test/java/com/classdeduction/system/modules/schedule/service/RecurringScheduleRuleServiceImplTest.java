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
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleCreateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleSlotRequest;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.impl.RecurringScheduleRuleServiceImpl;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleCreateResultVO;
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
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;

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
                packageTeacherAssignmentMapper,
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
        assertThat(occurrenceCaptor.getAllValues()).hasSize(2);
        assertThat(result.ruleId()).isEqualTo(5001L);
        assertThat(result.generatedOccurrenceCount()).isEqualTo(2);
        assertThat(result.firstGeneratedStartAt()).isBefore(result.lastGeneratedEndAt());
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
                .thenReturn(List.of(buildTeacherConflict()))
                .thenReturn(Collections.emptyList());
        stubGeneratedIds(false);

        assertThatThrownBy(() -> recurringScheduleRuleService.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Teacher conflict");
    }

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
        slot.setWeekday(1);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        slot.setPlannedUnits(4);

        RecurringScheduleRuleCreateRequest request = new RecurringScheduleRuleCreateRequest();
        request.setPackageId(1001L);
        request.setPrimaryTeacherId(3001L);
        request.setDefaultClassroomId(4001L);
        request.setStartDate(nextMonday);
        request.setGenerationHorizonDays(14);
        request.setNotes("Weeknight math");
        request.setSlots(List.of(slot));
        return request;
    }

    private CoursePackageEntity buildPackage() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(1001L);
        coursePackage.setStudentId(2001L);
        coursePackage.setPackageNo("PK-1001");
        coursePackage.setPackageName("SHSAT Math");
        coursePackage.setPackageStatus("ACTIVE");
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
        conflict.setPlannedStartAt(LocalDateTime.of(nextMonday, LocalTime.of(19, 0)));
        conflict.setPlannedEndAt(LocalDateTime.of(nextMonday, LocalTime.of(21, 0)));
        conflict.setPlanningStatus("SCHEDULED");
        return conflict;
    }
}
