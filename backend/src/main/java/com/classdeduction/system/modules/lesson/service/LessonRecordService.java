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

    /**
     * 老师初确认：只登记待管理员复核的上课结果，不扣课时、不写台账。
     */
    LessonRecordConfirmResultVO initialConfirmLesson(LessonRecordConfirmRequest request, CurrentUserResponse currentUser);

    /**
     * 管理员终确认：对待复核记录执行最终扣课结算。
     */
    LessonRecordConfirmResultVO adminConfirmLesson(Long recordId, CurrentUserResponse currentUser);

    /**
     * 管理员复核终确认：允许管理员修正实际老师、教室、时间、状态后再执行最终结算。
     */
    LessonRecordConfirmResultVO adminConfirmLesson(Long recordId, LessonRecordConfirmRequest request, CurrentUserResponse currentUser);

    /**
     * 管理员驳回老师初确认，允许老师后续重新提交。
     */
    void rejectLesson(Long recordId, String rejectReason, CurrentUserResponse currentUser);

    /**
     * 撤销已确认的课次记录：回滚课包课时（含账本冲正）、恢复课次为可再次确认状态、作废联动生成的补课课次。
     */
    void revokeLesson(Long recordId, CurrentUserResponse currentUser);
}
