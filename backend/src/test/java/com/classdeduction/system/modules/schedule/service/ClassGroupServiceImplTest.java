package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.impl.ClassGroupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private CourseProductScheduleSlotMapper courseProductScheduleSlotMapper;

    @Captor
    private ArgumentCaptor<LessonRecordEntity> recordCaptor;

    @Captor
    private ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor;

    private ClassGroupServiceImpl classGroupService;

    @BeforeEach
    void setUp() {
        classGroupService = new ClassGroupServiceImpl(
                classGroupMapper,
                classEnrollmentMapper,
                lessonOccurrenceMapper,
                lessonRecordMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                courseProductScheduleSlotMapper
        );
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

    private CurrentUserResponse adminUser() {
        return new CurrentUserResponse(999L, "admin", "Admin", "ADMIN", null, null, List.of("ADMIN"));
    }
}
