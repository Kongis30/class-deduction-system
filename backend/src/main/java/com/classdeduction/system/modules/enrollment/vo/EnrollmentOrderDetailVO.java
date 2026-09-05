package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        Long campusId,
        String campusNameSnapshot,
        String orderStatus,
        Long cashierUserId,
        String handledByName,
        LocalDate signupDate,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        String studentNameSnapshot,
        String studentClassSnapshot,
        String gradeSnapshot,
        String schoolSnapshot,
        String parentPhoneSnapshot,
        String parentPhone2Snapshot,
        Boolean pickupRequiredSnapshot,
        String pickupAddressSnapshot,
        Boolean dropoffRequiredSnapshot,
        String dropoffAddressSnapshot,
        String currencyCode,
        BigDecimal tuitionSubtotalAmount,
        BigDecimal pickupFeeAmount,
        BigDecimal dropoffFeeAmount,
        BigDecimal extraFeeAmount,
        String extraFeeDetail,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        String discountDetail,
        BigDecimal receivedAmount,
        BigDecimal amountDue,
        BigDecimal balanceAmount,
        String paymentStatus,
        String remark,
        List<EnrollmentOrderItemVO> items,
        List<PaymentRecordVO> payments,
        List<CoursePackageListItemVO> packages,
        List<ReceiptRecordVO> receipts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
