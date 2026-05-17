package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 支付方式 VO。
 */
@Builder
@Schema(description = "支付方式 VO")
public record PaymentMethodVO(
        Long id,
        String methodCode,
        String methodName,
        Integer sortOrder,
        String status
) {}
