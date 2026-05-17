package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Subject response payload.
 */
@Builder
@Schema(description = "Subject response payload")
public record SubjectVO(
        Long id,
        String subjectName,
        Integer displayOrder,
        String status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
