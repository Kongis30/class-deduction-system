package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 组合支付明细：一条支付方式 + 金额。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "组合支付明细")
public class PaymentSplitDTO {

    @NotBlank(message = "Payment method is required")
    @Schema(description = "支付方式编码", example = "CASH")
    private String paymentMethod;

    @NotNull(message = "Payment split amount is required")
    @DecimalMin(value = "0.01", message = "Split amount must be greater than 0")
    @Schema(description = "该支付方式金额", example = "50.00")
    private BigDecimal amount;
}
