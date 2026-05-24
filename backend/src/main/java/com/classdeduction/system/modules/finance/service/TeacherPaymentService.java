package com.classdeduction.system.modules.finance.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.finance.dto.TeacherPaymentCreateRequest;
import com.classdeduction.system.modules.finance.dto.UnpaidLessonQuery;
import com.classdeduction.system.modules.finance.vo.TeacherPaymentVO;
import com.classdeduction.system.modules.finance.vo.UnpaidLessonVO;

import java.util.List;
import java.util.Map;

public interface TeacherPaymentService {

    List<UnpaidLessonVO> queryUnpaidLessons(UnpaidLessonQuery query);

    Map<String, Object> previewPayment(List<Long> lessonRecordIds);

    Long createPayment(TeacherPaymentCreateRequest request, Long userId);

    PageResponse<TeacherPaymentVO> pagePayments(Long teacherId, String status, int pageNum, int pageSize);

    TeacherPaymentVO getPaymentDetail(Long id);

    void confirmPayment(Long id, Long userId);
}
