package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 班课报名开单请求：班课报名时同时创建 enrollment_order 和 class_enrollment 记录。
 */
@Data
@Schema(description = "班课报名开单请求")
public class ClassEnrollmentCreateRequest {

    @NotNull(message = "Student is required")
    @Schema(description = "学生ID", example = "1880000000000000001")
    private Long studentId;

    @Schema(description = "班课ID（与 productId 二选一）", example = "1880000000000000005")
    private Long classId;

    @Schema(description = "课程产品ID（GROUP 类型，与 classId 二选一）", example = "1880000000000000003")
    private Long productId;

    @Schema(description = "来源报名订单明细ID", example = "1880000000000000004")
    private Long enrollmentOrderItemId;

    @Schema(description = "课次收费：购买课次数（PER_SESSION 必填）", example = "10")
    private Integer sessions;

    @Schema(description = "月收费：起始月份 yyyy-MM（MONTHLY 必填）", example = "2026-09")
    private String startMonth;

    @Schema(description = "月收费：结束月份 yyyy-MM（MONTHLY 必填）", example = "2026-12")
    private String endMonth;

    @Schema(description = "经手人姓名快照", example = "Amy")
    private String handledByName;

    @Schema(description = "币种", example = "USD")
    private String currencyCode;

    @NotNull(message = "Discount amount is required")
    @DecimalMin(value = "0.00", message = "Discount amount cannot be negative")
    @Schema(description = "优惠金额", example = "100.00")
    private BigDecimal discountAmount;

    @NotNull(message = "Received amount is required")
    @DecimalMin(value = "0.00", message = "Received amount cannot be negative")
    @Schema(description = "实收金额", example = "2599.00")
    private BigDecimal receivedAmount;

    @Schema(description = "备注")
    private String remark;
}
