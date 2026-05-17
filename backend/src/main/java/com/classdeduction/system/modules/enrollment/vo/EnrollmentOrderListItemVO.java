package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报名单列表项响应结构。
 */
@Builder
@Schema(description = "报名单列表项响应结构")
public record EnrollmentOrderListItemVO(
        Long id,
        String enrollmentNo,
        Long studentId,
        String studentName,
        Long classId,
        String className,
        Long courseProductId,
        String courseProductName,
        String handledByName,
        String currencyCode,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal receivedAmount,
        String paymentStatus,
        String orderStatus,
        Integer packageCount,
        LocalDateTime createdAt
) {
}
