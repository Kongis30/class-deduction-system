package com.classdeduction.system.modules.auth.vo;

import java.time.LocalDateTime;

public record ManagerAccountVO(
        Long userId,
        String username,
        String displayName,
        String mobile,
        String email,
        String status,
        LocalDateTime lastLoginAt,
        String remark,
        Long registrationCount,
        Long uniqueStudentCount
) {
}
