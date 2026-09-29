package com.classdeduction.system.modules.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设置学生 Credit 目标余额；服务层会把目标余额转换为一条人工调整流水。
 */
@Data
@Schema(description = "学生 Credit 余额调整请求")
public class StudentCreditAdjustmentRequest {

    @NotNull(message = "Credit balance is required")
    @DecimalMin(value = "0.00", message = "Credit balance cannot be negative")
    @Digits(integer = 12, fraction = 0, message = "Credit balance must be a whole dollar amount")
    @Schema(description = "调整后的目标余额", example = "150.00")
    private BigDecimal creditBalance;

    @Size(max = 500, message = "Adjustment note must be at most 500 characters")
    @Schema(description = "调整原因或备注", example = "Manual correction")
    private String remark;
}
