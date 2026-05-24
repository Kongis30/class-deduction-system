package com.classdeduction.system.modules.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 学生账号响应。
 */
@Builder
@Schema(description = "Student account response")
public record StudentAccountVO(
        Long userId,
        Long studentId,
        String studentNo,
        String studentName,
        String username,
        String displayName,
        String email,
        String status,
        LocalDateTime lastLoginAt,
        String remark
) {
}
