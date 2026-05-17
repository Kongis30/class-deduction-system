package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Discount preset response payload.
 */
@Builder
@Schema(description = "Discount preset response payload")
public record DiscountPresetVO(
        Long id,
        String presetName,
        String discountType,
        BigDecimal discountValue,
        Integer sortOrder,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
