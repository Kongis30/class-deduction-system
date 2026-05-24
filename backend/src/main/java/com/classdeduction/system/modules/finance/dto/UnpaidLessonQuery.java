package com.classdeduction.system.modules.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 未结算课时查询参数。
 */
@Data
@Schema(description = "Unpaid lesson query")
public class UnpaidLessonQuery {

    @NotNull(message = "Teacher ID is required")
    private Long teacherId;

    private LocalDate startDate;

    private LocalDate endDate;
}
