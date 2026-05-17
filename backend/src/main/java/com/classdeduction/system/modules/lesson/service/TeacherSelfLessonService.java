package com.classdeduction.system.modules.lesson.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.lesson.dto.TeacherLessonConfirmRequest;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;

public interface TeacherSelfLessonService {

    LessonRecordConfirmResultVO confirmOwnLesson(TeacherLessonConfirmRequest request, CurrentUserResponse currentUser);
}
