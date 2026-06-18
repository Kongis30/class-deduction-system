package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 报名订单退款请求：全额退款，记录退款方式、经手人与原因。
 * 退款金额固定取订单当前已收金额（receivedAmount），无需调用方传入。
 */
@Data
@Schema(description = "报名订单退款请求（全额退款）")
public class RefundRequest {

    @NotBlank(message = "Refund method is required")
    @Schema(description = "退款方式", example = "CASH")
    private String paymentMethod;

    @Schema(description = "经手人用户ID", example = "1880000000000000002")
    private Long handledByUserId;

    @NotBlank(message = "Handler name is required")
    @Schema(description = "经手人姓名", example = "Amy")
    private String handledByName;

    @Schema(description = "退款原因/备注", example = "Student withdrew")
    private String remark;
}
