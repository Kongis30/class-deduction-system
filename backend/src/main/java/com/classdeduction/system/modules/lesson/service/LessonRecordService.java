package com.classdeduction.system.modules.lesson.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.dto.LessonRecordPageQuery;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.lesson.vo.LessonRecordDetailVO;
import com.classdeduction.system.modules.lesson.vo.LessonRecordListItemVO;

public interface LessonRecordService {

    PageResponse<LessonRecordListItemVO> pageRecords(LessonRecordPageQuery query);

    LessonRecordDetailVO getRecord(Long id);

    LessonRecordConfirmResultVO confirmLesson(LessonRecordConfirmRequest request, CurrentUserResponse currentUser);
}
