package com.classdeduction.system.modules.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 未结算课时查询参数。
 */
@Data
@Schema(description = "Unpaid lesson query")
public class UnpaidLessonQuery {

    private Long teacherId;

    private LocalDate startDate;

    private LocalDate endDate;
}
