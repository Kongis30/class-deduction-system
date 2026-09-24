package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 排课页修改课包报名前已用课时的请求。 */
@Data
@Schema(description = "Update opening used hours for a one-on-one package")
public class CoursePackageOpeningUsedHoursRequest {

    @NotNull(message = "Already used hours are required")
    @DecimalMin(value = "0.0", message = "Already used hours cannot be negative")
    @DecimalMax(value = "400.0", message = "Already used hours cannot exceed 400")
    @Schema(description = "Hours consumed before this system tracked lessons; uses 0.5-hour increments", example = "8.0")
    private BigDecimal openingUsedHours;
}
