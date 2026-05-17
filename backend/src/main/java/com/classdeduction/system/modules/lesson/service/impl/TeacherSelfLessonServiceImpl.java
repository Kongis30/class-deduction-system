package com.classdeduction.system.modules.lesson.service.impl;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.dto.TeacherLessonConfirmRequest;
import com.classdeduction.system.modules.lesson.service.LessonRecordService;
import com.classdeduction.system.modules.lesson.service.TeacherSelfLessonService;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 老师自助确认服务：限制老师只能确认自己的课和允许的状态。
 */
@Service
@RequiredArgsConstructor
public class TeacherSelfLessonServiceImpl implements TeacherSelfLessonService {

    private static final Set<String> ALLOWED_SELF_CONFIRM_STATUSES = Set.of("COMPLETED", "NO_SHOW", "FORMAL_LEAVE");
    private static final int PAST_CONFIRM_WINDOW_DAYS = 7;
    private static final int FUTURE_CONFIRM_WINDOW_HOURS = 6;

    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordService lessonRecordService;

    @Override
    public LessonRecordConfirmResultVO confirmOwnLesson(TeacherLessonConfirmRequest request, CurrentUserResponse currentUser) {
        if (currentUser == null || currentUser.teacherId() == null || !currentUser.roleCodes().contains("TEACHER")) {
            throw new BusinessException("FORBIDDEN", "Only linked teacher accounts can use the teacher self-confirmation API.");
        }
        if (!ALLOWED_SELF_CONFIRM_STATUSES.contains(request.getResultStatus())) {
            throw new BusinessException("TEACHER_STATUS_NOT_ALLOWED", "Teachers can only confirm completed, no-show, or formal-leave results.");
        }
        LessonOccurrenceEntity occurrence = requireOwnedOccurrence(request.getOccurrenceId(), currentUser.teacherId());
        validateTeacherConfirmWindow(occurrence);

        LessonRecordConfirmRequest delegateRequest = new LessonRecordConfirmRequest();
        delegateRequest.setOccurrenceId(request.getOccurrenceId());
        delegateRequest.setResultStatus(request.getResultStatus());
        delegateRequest.setActualTeacherId(currentUser.teacherId());
        delegateRequest.setActualClassroomId(occurrence.getPlannedClassroomId());
        delegateRequest.setActualStartAt(request.getActualStartAt());
        delegateRequest.setActualEndAt(request.getActualEndAt());
        delegateRequest.setActualUnits(request.getActualUnits());
        delegateRequest.setNotes(request.getNotes());
        return lessonRecordService.confirmLesson(delegateRequest, currentUser);
    }

    private LessonOccurrenceEntity requireOwnedOccurrence(Long occurrenceId, Long teacherId) {
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        if (!teacherId.equals(occurrence.getPlannedTeacherId())) {
            throw new BusinessException("FORBIDDEN", "Teachers can only confirm their own planned lessons.");
        }
        return occurrence;
    }

    /**
     * 老师端只允许确认最近发生的课，避免未来课误确认或历史课被追溯篡改。
     */
    private void validateTeacherConfirmWindow(LessonOccurrenceEntity occurrence) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        if (occurrence.getPlannedStartAt().isAfter(now.plusHours(FUTURE_CONFIRM_WINDOW_HOURS))) {
            throw new BusinessException("TEACHER_CONFIRM_TOO_EARLY", "Teachers can only confirm lessons that are happening now or have already happened.");
        }
        if (occurrence.getPlannedEndAt().isBefore(now.minusDays(PAST_CONFIRM_WINDOW_DAYS))) {
            throw new BusinessException("TEACHER_CONFIRM_TOO_LATE", "This lesson is outside the teacher self-confirmation window.");
        }
    }
}
