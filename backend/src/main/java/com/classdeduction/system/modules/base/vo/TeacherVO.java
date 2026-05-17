package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Teacher response payload.
 */
@Builder
@Schema(description = "Teacher response payload")
public record TeacherVO(
        Long id,
        String teacherNo,
        String teacherName,
        String email,
        String mobile,
        List<String> subjectTags,
        String status,
        LocalDate hireDate,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
