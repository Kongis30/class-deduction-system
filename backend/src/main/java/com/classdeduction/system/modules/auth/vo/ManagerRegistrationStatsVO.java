package com.classdeduction.system.modules.auth.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagerRegistrationStatsVO {
    private Long userId;
    private Long registrationCount;
    private Long uniqueStudentCount;
}
