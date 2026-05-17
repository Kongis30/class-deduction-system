package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Guardian contact response payload.
 */
@Builder
@Schema(description = "Guardian contact response payload")
public record GuardianContactVO(
        Long id,
        String contactName,
        String relationType,
        String mobile,
        String email,
        boolean primary,
        String status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
