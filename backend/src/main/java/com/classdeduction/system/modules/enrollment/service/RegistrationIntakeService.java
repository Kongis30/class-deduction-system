package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.vo.RegistrationIntakeResultVO;

public interface RegistrationIntakeService {

    RegistrationIntakeResultVO submitIntake(RegistrationIntakeRequest request);
}
