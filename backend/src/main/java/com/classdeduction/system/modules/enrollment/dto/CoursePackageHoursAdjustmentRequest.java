package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Course package available-hours adjustment request")
public class CoursePackageHoursAdjustmentRequest {

    @NotNull(message = "Hour change is required")
    @DecimalMin(value = "-10000.0", message = "Hour change is too small")
    @DecimalMax(value = "10000.0", message = "Hour change is too large")
    @Schema(description = "Hours to add or remove; must use 0.5-hour increments", example = "2.0")
    private BigDecimal changeHours;

    @NotNull(message = "Adjustment type is required")
    @Pattern(regexp = "^(BONUS_HOURS|MANUAL_CORRECTION)$", message = "Adjustment type is invalid")
    @Schema(description = "Adjustment type: BONUS_HOURS/MANUAL_CORRECTION", example = "BONUS_HOURS")
    private String adjustmentType;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    @Schema(description = "Optional adjustment note", example = "Two complimentary hours")
    private String reason;
}
