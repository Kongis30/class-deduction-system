package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收据响应结构。
 */
@Builder
@Schema(description = "收据响应结构")
public record ReceiptRecordVO(
        Long id,
        String receiptNo,
        Long enrollmentOrderId,
        Long studentId,
        Long paymentRecordId,
        boolean enrollmentOrderActive,
        List<Long> activeCourseItemIds,
        String studentName,
        String templateCode,
        String receiptPayload,
        String fileUrl,
        LocalDateTime issuedAt
) {
}
