package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;

public interface DataSummaryService {

    DataSummaryResponseVO getSummary(DataSummaryQuery query);
}
