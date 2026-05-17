package com.classdeduction.system.modules.lesson.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.lesson.dto.TeacherLessonConfirmRequest;
import com.classdeduction.system.modules.lesson.service.impl.TeacherSelfLessonServiceImpl;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 老师自助确认测试：确保老师只能确认自己的课次且不能绕过后台约束。
 */
@ExtendWith(MockitoExtension.class)
class TeacherSelfLessonServiceImplTest {

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonRecordService lessonRecordService;

    @Captor
    private ArgumentCaptor<com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest> requestCaptor;

    private TeacherSelfLessonServiceImpl teacherSelfLessonService;

    @BeforeEach
    void setUp() {
        teacherSelfLessonService = new TeacherSelfLessonServiceImpl(lessonOccurrenceMapper, lessonRecordService);
    }

    @Test
    void shouldConfirmOwnLessonUsingTeacherIdentity() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        TeacherLessonConfirmRequest request = new TeacherLessonConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        request.setActualStartAt(now.minusHours(4));
        request.setActualEndAt(now.minusHours(3));
        request.setActualUnits(2);
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence(9001L, now));
        when(lessonRecordService.confirmLesson(any(), any())).thenReturn(LessonRecordConfirmResultVO.builder()
                .lessonRecordId(5001L)
                .occurrenceId(1001L)
                .ledgerId(7001L)
                .deductedUnits(2)
                .remainingUnits(10)
                .packageCompleted(false)
                .build());

        LessonRecordConfirmResultVO result = teacherSelfLessonService.confirmOwnLesson(request, currentTeacherUser());

        verify(lessonRecordService).confirmLesson(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().getActualTeacherId()).isEqualTo(9001L);
        assertThat(requestCaptor.getValue().getActualClassroomId()).isEqualTo(801L);
        assertThat(result.lessonRecordId()).isEqualTo(5001L);
    }

    @Test
    void shouldRejectWhenTeacherConfirmsAnotherTeachersLesson() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        TeacherLessonConfirmRequest request = new TeacherLessonConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence(9111L, now));

        assertThatThrownBy(() -> teacherSelfLessonService.confirmOwnLesson(request, currentTeacherUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Teachers can only confirm their own planned lessons.");
    }

    @Test
    void shouldRejectUnsupportedTeacherStatus() {
        TeacherLessonConfirmRequest request = new TeacherLessonConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("TEACHER_CANCELLED");

        assertThatThrownBy(() -> teacherSelfLessonService.confirmOwnLesson(request, currentTeacherUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Teachers can only confirm completed, no-show, or formal-leave results.");
    }

    private CurrentUserResponse currentTeacherUser() {
        return new CurrentUserResponse(1002L, "teacher", "Emma Wang", "TEACHER", 9001L, List.of("TEACHER"));
    }

    private LessonOccurrenceEntity buildOccurrence(Long teacherId, LocalDateTime baseTime) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(1001L);
        occurrence.setPlannedTeacherId(teacherId);
        occurrence.setPlannedClassroomId(801L);
        occurrence.setPlannedStartAt(baseTime.minusHours(4));
        occurrence.setPlannedEndAt(baseTime.minusHours(3));
        return occurrence;
    }
}
