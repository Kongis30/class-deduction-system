package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 班课报名开单请求：班课报名时同时创建 enrollment_order 和 class_enrollment 记录。
 */
@Data
@Schema(description = "班课报名开单请求")
public class ClassEnrollmentCreateRequest {

    @NotNull(message = "学生ID不能为空")
    @Schema(description = "学生ID", example = "1880000000000000001")
    private Long studentId;

    @Schema(description = "班课ID（与 productId 二选一）", example = "1880000000000000005")
    private Long classId;

    @Schema(description = "课程产品ID（GROUP 类型，与 classId 二选一）", example = "1880000000000000003")
    private Long productId;

    @NotBlank(message = "报名日期不能为空")
    @Schema(description = "报名日期 (ISO date)", example = "2026-05-17")
    private String enrollmentDate;

    @NotBlank(message = "到期日期不能为空")
    @Schema(description = "到期日期 (ISO date)", example = "2026-08-17")
    private String expiryDate;

    @Schema(description = "经手人姓名快照", example = "Amy")
    private String handledByName;

    @Schema(description = "币种", example = "USD")
    private String currencyCode;

    @NotNull(message = "原价不能为空")
    @DecimalMin(value = "0.00", message = "原价不能小于0")
    @Schema(description = "原价", example = "2699.00")
    private BigDecimal originalAmount;

    @NotNull(message = "优惠金额不能为空")
    @DecimalMin(value = "0.00", message = "优惠金额不能小于0")
    @Schema(description = "优惠金额", example = "100.00")
    private BigDecimal discountAmount;

    @NotNull(message = "实收金额不能为空")
    @DecimalMin(value = "0.00", message = "实收金额不能小于0")
    @Schema(description = "实收金额", example = "2599.00")
    private BigDecimal receivedAmount;

    @Schema(description = "备注")
    private String remark;
}
