package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录响应结构。
 */
@Builder
@Schema(description = "支付记录响应结构")
public record PaymentRecordVO(
        Long id,
        String paymentNo,
        Integer participantIndex,
        String paymentMethod,
        String paymentType,
        String paymentStatus,
        BigDecimal amount,
        LocalDateTime paidAt,
        String handledByName,
        String remark
) {
}
