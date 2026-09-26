package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.vo.RegistrationIntakeResultVO;
import com.classdeduction.system.common.dto.CurrentUserResponse;

public interface RegistrationIntakeService {

    RegistrationIntakeResultVO submitIntake(RegistrationIntakeRequest request);

    RegistrationIntakeResultVO submitIntake(RegistrationIntakeRequest request, CurrentUserResponse operator);
}
