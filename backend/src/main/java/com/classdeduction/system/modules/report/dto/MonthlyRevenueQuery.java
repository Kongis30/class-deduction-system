package com.classdeduction.system.modules.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 月度收入报表查询条件；月份格式为 yyyy-MM，按 America/New_York 自然月汇总。 */
@Getter
@Setter
@Schema(description = "Monthly revenue report query")
public class MonthlyRevenueQuery {

    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "fromMonth must use yyyy-MM format")
    @Schema(description = "First month to include, yyyy-MM", example = "2026-07")
    private String fromMonth;

    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "toMonth must use yyyy-MM format")
    @Schema(description = "Last month to include, yyyy-MM", example = "2026-12")
    private String toMonth;
}
