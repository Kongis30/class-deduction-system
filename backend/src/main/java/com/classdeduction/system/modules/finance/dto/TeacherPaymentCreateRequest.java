package com.classdeduction.system.modules.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建教师结算请求。
 */
@Data
@Schema(description = "Teacher payment create request")
public class TeacherPaymentCreateRequest {

    @NotNull(message = "Teacher ID is required")
    private Long teacherId;

    @NotNull(message = "Period start is required")
    private LocalDate periodStart;

    @NotNull(message = "Period end is required")
    private LocalDate periodEnd;

    @NotEmpty(message = "At least one lesson record must be selected")
    private List<Long> lessonRecordIds;

    private String notes;
}
