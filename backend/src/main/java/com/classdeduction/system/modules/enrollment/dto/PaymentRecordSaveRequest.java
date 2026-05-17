package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付记录保存请求，支持组合支付（多个支付方式 + 金额）。
 */
@Data
@Schema(description = "支付记录保存请求（支持组合支付）")
public class PaymentRecordSaveRequest {

    @NotNull(message = "Enrollment order ID is required")
    @Schema(description = "报名订单ID", example = "1880000000000000001")
    private Long enrollmentOrderId;

    @NotEmpty(message = "At least one payment split is required")
    @Valid
    @Schema(description = "组合支付明细列表")
    private List<PaymentSplitDTO> splits;

    @PastOrPresent(message = "Payment time cannot be in the future")
    @Schema(description = "支付时间", example = "2026-04-07T10:00:00")
    private LocalDateTime paidAt;

    @Schema(description = "收银员用户ID", example = "1880000000000000002")
    private Long handledByUserId;

    @NotBlank(message = "Handler name is required")
    @Schema(description = "经手人姓名", example = "Amy")
    private String handledByName;

    @Schema(description = "备注", example = "Notes")
    private String remark;
}
