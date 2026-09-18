package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组合支付明细：一条支付方式 + 金额。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "组合支付明细")
public class PaymentSplitDTO {

    @Schema(description = "Participant index within a shared private lesson", example = "2")
    private Integer participantIndex;

    @Valid
    @Schema(description = "Course allocations for this payment; required for multi-course orders")
    private List<CoursePaymentAllocationDTO> allocations;

    @NotBlank(message = "Payment method is required")
    @Schema(description = "支付方式编码", example = "CASH")
    private String paymentMethod;

    @NotNull(message = "Payment split amount is required")
    @DecimalMin(value = "0.01", message = "Split amount must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "Split amount must use at most 2 decimal places")
    @Schema(description = "该支付方式金额", example = "50.00")
    private BigDecimal amount;

    @PastOrPresent(message = "Payment time cannot be in the future")
    @Schema(description = "分项支付时间；为空时使用请求级支付时间")
    private LocalDateTime paidAt;

    @Schema(description = "分项经手人；为空时使用请求级经手人")
    private String handledByName;

    @Schema(description = "分项备注；为空时使用请求级备注")
    private String remark;

    public PaymentSplitDTO(String paymentMethod, BigDecimal amount) {
        this.paymentMethod = paymentMethod;
        this.amount = amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Course-level allocation for a payment")
    public static class CoursePaymentAllocationDTO {

        @NotNull
        @Schema(description = "Enrollment order item ID")
        private Long enrollmentOrderItemId;

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        @Schema(description = "Amount allocated to the course")
        private BigDecimal amount;
    }
}
