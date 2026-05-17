package com.classdeduction.system.modules.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 教师账号响应。
 */
@Builder
@Schema(description = "Teacher account response")
public record TeacherAccountVO(
        Long userId,
        Long teacherId,
        String teacherNo,
        String teacherName,
        String username,
        String displayName,
        String mobile,
        String email,
        String status,
        LocalDateTime lastLoginAt,
        String remark
) {
}
