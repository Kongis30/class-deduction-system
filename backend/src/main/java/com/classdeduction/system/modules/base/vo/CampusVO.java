package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Campus response payload.
 */
@Builder
@Schema(description = "Campus response payload")
public record CampusVO(
        Long id,
        String campusName,
        Integer displayOrder,
        String status,
        BigDecimal defaultPickupFeeAmount,
        BigDecimal defaultDropoffFeeAmount,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
