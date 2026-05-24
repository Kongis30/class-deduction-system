package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Student detail response payload.
 */
@Builder
@Schema(description = "Student detail response payload")
public record StudentDetailVO(
        Long id,
        String studentNo,
        String studentName,
        String englishName,
        String gender,
        LocalDate birthDate,
        String sourceChannel,
        String status,
        String notes,
        String guardianName,
        String guardianPhone,
        String guardianEmail,
        String guardianRelationType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
