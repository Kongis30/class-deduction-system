package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 报名单更新请求。
 */
@Data
@Schema(description = "报名单更新请求")
public class EnrollmentOrderUpdateRequest {

    @Schema(description = "订单状态: DRAFT/CONFIRMED/CANCELLED", example = "CONFIRMED")
    @Pattern(regexp = "^(DRAFT|CONFIRMED|CANCELLED)$", message = "订单状态必须是 DRAFT、CONFIRMED 或 CANCELLED")
    private String orderStatus;

    @Schema(description = "收银员用户ID", example = "1880000000000000001")
    private Long cashierUserId;

    @Schema(description = "备注", example = "备注信息")
    private String remark;
}
