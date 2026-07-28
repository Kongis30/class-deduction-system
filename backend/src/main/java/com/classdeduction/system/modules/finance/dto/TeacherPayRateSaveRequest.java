package com.classdeduction.system.modules.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课时单价保存请求。
 */
@Data
@Schema(description = "Teacher pay rate save request")
public class TeacherPayRateSaveRequest {

    @NotNull(message = "Teacher ID is required")
    private Long teacherId;

    @NotNull(message = "Course product ID is required")
    private Long courseProductId;

    @Schema(description = "Optional student ID for a one-on-one rate override")
    private Long studentId;

    @NotNull(message = "Hourly rate is required")
    @DecimalMin(value = "0.01", message = "Hourly rate must be greater than 0")
    private BigDecimal hourlyRate;

    private String notes;
}
