package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 旧报名开单请求：一次请求完成报名、支付、课时包和收据的创建。
 *
 * <p>1对1 的老师、课程和逐时段课时分配统一在排课环节设置，不在报名时设置。
 */
@Data
@Schema(description = "报名开单请求")
public class EnrollmentCreateRequest {

    @NotNull(message = "Student is required")
    @Schema(description = "学生ID", example = "1880000000000000001")
    private Long studentId;

    @NotNull(message = "Course product is required")
    @Schema(description = "课程产品ID", example = "1880000000000000002")
    private Long courseProductId;

    @Schema(description = "来源报名订单明细ID", example = "1880000000000000003")
    private Long enrollmentOrderItemId;

    @Schema(description = "收银员用户ID", example = "1880000000000000004")
    private Long cashierUserId;

    @NotBlank(message = "Handler name is required")
    @Schema(description = "经手人姓名快照", example = "Amy")
    private String handledByName;

    @Schema(description = "币种", example = "USD")
    private String currencyCode;

    @Schema(description = "课时包名称，默认取课程产品名称")
    private String packageName;

    @Schema(description = "开始日期", example = "2026-04-07")
    private LocalDate startDate;

    @Schema(description = "预计结束日期", example = "2026-07-07")
    private LocalDate estimatedEndDate;

    @Schema(description = "支付时间，默认当前时间", example = "2026-04-07T10:00:00")
    private LocalDateTime paidAt;

    @Schema(description = "是否生成收据", example = "true")
    private Boolean issueReceipt;

    @Schema(description = "收据模板编码", example = "DEFAULT_RECEIPT")
    private String templateCode;

    @NotNull(message = "Original amount is required")
    @DecimalMin(value = "0.00", message = "Original amount cannot be negative")
    @Schema(description = "原价", example = "2699.00")
    private BigDecimal originalAmount;

    @NotNull(message = "Discount amount is required")
    @DecimalMin(value = "0.00", message = "Discount amount cannot be negative")
    @Schema(description = "优惠金额", example = "100.00")
    private BigDecimal discountAmount;

    @NotNull(message = "Received amount is required")
    @DecimalMin(value = "0.00", message = "Received amount cannot be negative")
    @Schema(description = "实收金额", example = "2599.00")
    private BigDecimal receivedAmount;

    @NotNull(message = "Purchased hours are required")
    @DecimalMin(value = "0.5", message = "Purchased hours must be at least 0.5")
    private Integer totalUnits;

    @Schema(description = "备注")
    private String remark;

}
