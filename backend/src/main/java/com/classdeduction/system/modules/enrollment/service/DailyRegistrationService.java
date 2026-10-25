package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.DailyRegistrationQuery;
import com.classdeduction.system.modules.enrollment.vo.DailyRegistrationResponseVO;
import com.classdeduction.system.modules.enrollment.vo.DailyCashFlowResponseVO;

public interface DailyRegistrationService {

    DailyRegistrationResponseVO query(DailyRegistrationQuery query);

    DailyCashFlowResponseVO queryCashFlow(DailyRegistrationQuery query);
}
