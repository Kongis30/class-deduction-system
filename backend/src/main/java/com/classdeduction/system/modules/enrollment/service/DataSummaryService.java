package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.dto.RefundUndoCandidatesQuery;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import com.classdeduction.system.modules.enrollment.vo.ParticipantCourseAmountVO;
import com.classdeduction.system.modules.enrollment.vo.RefundUndoCandidatesVO;

import java.util.List;

public interface DataSummaryService {

    DataSummaryResponseVO getSummary(DataSummaryQuery query);

    List<ParticipantCourseAmountVO> getParticipantCourseAmounts(Long orderId, Integer participantIndex);

    RefundUndoCandidatesVO getRefundUndoCandidates(RefundUndoCandidatesQuery query);
}
