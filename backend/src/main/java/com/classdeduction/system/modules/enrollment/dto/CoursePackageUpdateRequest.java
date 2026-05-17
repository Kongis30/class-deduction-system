package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 课时包更新请求。
 */
@Data
@Schema(description = "课时包更新请求")
public class CoursePackageUpdateRequest {

    @Schema(description = "课时包名称", example = "Math Package A")
    private String packageName;

    @Schema(description = "课时包状态: DRAFT/ACTIVE/COMPLETED/CLOSED", example = "ACTIVE")
    @Pattern(regexp = "^(DRAFT|ACTIVE|COMPLETED|CLOSED)$", message = "课时包状态必须是 DRAFT、ACTIVE、COMPLETED 或 CLOSED")
    private String packageStatus;

    @Schema(description = "开始日期", example = "2026-04-07")
    private LocalDate startDate;

    @Schema(description = "预计结束日期", example = "2026-07-07")
    private LocalDate estimatedEndDate;

    @Schema(description = "备注", example = "备注信息")
    private String remark;
}
