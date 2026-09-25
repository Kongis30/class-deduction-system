package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 排课页设置课包当前已消课总课时，正式确认记录对应的消课明细仍由系统维护。 */
@Data
@Schema(description = "Update already-deducted hours for a one-on-one package")
public class CoursePackageDeductedHoursRequest {

    @NotNull(message = "Already deducted hours are required")
    @DecimalMin(value = "0.0", message = "Already deducted hours cannot be negative")
    @DecimalMax(value = "400.0", message = "Already deducted hours cannot exceed 400")
    @Schema(description = "Total hours already deducted, including confirmed lessons", example = "25.0")
    private BigDecimal deductedHours;
}
