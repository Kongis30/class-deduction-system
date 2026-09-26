package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.DailyRegistrationQuery;
import com.classdeduction.system.modules.enrollment.vo.DailyRegistrationResponseVO;

public interface DailyRegistrationService {

    DailyRegistrationResponseVO query(DailyRegistrationQuery query);
}
