package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Campus save request.
 */
@Data
@Schema(description = "Campus save request")
public class CampusSaveRequest {

    @NotBlank(message = "Campus name is required")
    @Schema(description = "Campus name", example = "Main Building")
    private String campusName;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Campus status", example = "ACTIVE")
    private String status;

    @DecimalMin(value = "0.00", message = "Default pickup fee must not be negative")
    @Schema(description = "Default pickup fee for this campus", example = "25.00")
    private BigDecimal defaultPickupFeeAmount;

    @DecimalMin(value = "0.00", message = "Default drop-off fee must not be negative")
    @Schema(description = "Default drop-off fee for this campus", example = "25.00")
    private BigDecimal defaultDropoffFeeAmount;

    @Schema(description = "Internal notes")
    private String notes;
}
