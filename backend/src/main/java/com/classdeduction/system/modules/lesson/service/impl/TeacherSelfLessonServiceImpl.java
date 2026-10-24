package com.classdeduction.system.modules.lesson.service.impl;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.dto.TeacherLessonConfirmRequest;
import com.classdeduction.system.modules.lesson.service.LessonRecordService;
import com.classdeduction.system.modules.lesson.service.TeacherSelfLessonService;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 老师自助确认服务：限制老师只能确认自己的课和允许的状态。
 */
@Service
@RequiredArgsConstructor
public class TeacherSelfLessonServiceImpl implements TeacherSelfLessonService {

    private static final Set<String> ALLOWED_SELF_CONFIRM_STATUSES = Set.of("COMPLETED", "NO_SHOW", "FORMAL_LEAVE");

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
        LessonOccurrenceEntity occurrence = requireOccurrence(request.getOccurrenceId());

        LessonRecordConfirmRequest delegateRequest = new LessonRecordConfirmRequest();
        delegateRequest.setOccurrenceId(request.getOccurrenceId());
        delegateRequest.setResultStatus(request.getResultStatus());
        // 接手老师可以提交旧课次，但不能仅因提交人变更就改写实际授课老师。
        delegateRequest.setActualTeacherId(currentUser.teacherId().equals(occurrence.getPlannedTeacherId())
                ? currentUser.teacherId() : null);
        delegateRequest.setActualClassroomId(occurrence.getPlannedClassroomId());
        delegateRequest.setActualStartAt(request.getActualStartAt());
        delegateRequest.setActualEndAt(request.getActualEndAt());
        delegateRequest.setActualUnits(request.getActualUnits());
        delegateRequest.setNotes(request.getNotes());
        return lessonRecordService.initialConfirmLesson(delegateRequest, currentUser);
    }

    /** 当前接手老师的权限由下游 LessonRecordService 按 slot lineage 统一校验。 */
    private LessonOccurrenceEntity requireOccurrence(Long occurrenceId) {
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        return occurrence;
    }

}
