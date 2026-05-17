package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报名单详情响应结构。
 */
@Builder
@Schema(description = "报名单详情响应结构")
public record EnrollmentOrderDetailVO(
        Long id,
        String enrollmentNo,
        Long studentId,
        String studentNo,
        String studentName,
        Long classId,
        String className,
        String orderStatus,
        Long cashierUserId,
        String handledByName,
        String currencyCode,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal receivedAmount,
        String paymentStatus,
        String remark,
        List<PaymentRecordVO> payments,
        List<CoursePackageListItemVO> packages,
        List<ReceiptRecordVO> receipts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
