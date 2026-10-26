package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryClassificationRequest;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import com.classdeduction.system.modules.enrollment.vo.ParticipantCourseAmountVO;

import java.util.List;

public interface DataSummaryService {

    DataSummaryResponseVO getSummary(DataSummaryQuery query);

    List<ParticipantCourseAmountVO> getParticipantCourseAmounts(Long orderId, Integer participantIndex);

    void classifyRecord(DataSummaryClassificationRequest request);
}
