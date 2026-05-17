package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Discount preset save request.
 */
@Data
@Schema(description = "Discount preset save request")
public class DiscountPresetSaveRequest {

    @NotBlank(message = "Preset name is required")
    @Schema(description = "Discount name", example = "Sibling 10%")
    private String presetName;

    @NotBlank(message = "Discount type is required")
    @Schema(description = "Discount type: PERCENTAGE or FIXED", example = "PERCENTAGE")
    private String discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be greater than 0")
    @Schema(description = "Discount value: 10 = 10% off, 100 = $100 off", example = "10")
    private BigDecimal discountValue;

    @Schema(description = "Sort order, smaller first", example = "1")
    private Integer sortOrder;

    @Schema(description = "Status: ACTIVE / INACTIVE", example = "ACTIVE")
    private String status;
}
