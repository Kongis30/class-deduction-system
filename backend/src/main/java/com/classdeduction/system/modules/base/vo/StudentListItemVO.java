package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Student list item response payload.
 */
@Builder
@Schema(description = "Student list item response payload")
public record StudentListItemVO(
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
        String phone,
        String guardianEmail,
        Long campusId,
        String campusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
