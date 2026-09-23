package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 报名订单退款请求：记录退款金额、退款方式、经手人与原因。
 */
@Data
@Schema(description = "报名订单退款请求")
public class RefundRequest {

    @Schema(description = "多人私教订单中的参与学生序号；普通订单为空", example = "2")
    private Integer participantIndex;

    @NotNull(message = "Refund amount is required")
    @Positive(message = "Refund amount must be greater than zero")
    @Schema(description = "退款金额，不能超过订单当前已收金额", example = "200.00")
    private BigDecimal refundAmount;

    @NotBlank(message = "Refund method is required")
    @Schema(description = "退款方式", example = "CASH")
    private String paymentMethod;

    @Schema(description = "退款处理方式：REFUND 实际退款，CREDIT 转为学生 Credit", example = "CREDIT")
    private String disposition = "REFUND";

    @Schema(description = "经手人用户ID", example = "1880000000000000002")
    private Long handledByUserId;

    @NotBlank(message = "Handler name is required")
    @Schema(description = "经手人姓名", example = "Amy")
    private String handledByName;

    @Schema(description = "退款原因/备注", example = "Student withdrew")
    private String remark;
}
