package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.impl.ScheduleAdjustmentServiceImpl;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private JsonHelper jsonHelper;

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
                teacherMapper,
                classroomMapper,
                jsonHelper
        );
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
}
