package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result returned by the new registration intake flow.
 */
@Builder
@Schema(description = "Registration intake result")
public record RegistrationIntakeResultVO(
        Long enrollmentOrderId,
        String enrollmentNo,
        Long studentId,
        String studentNo,
        String studentName,
        Boolean studentReused,
        AccountResultVO studentAccount,
        Long privateLessonGroupId,
        List<ParticipantResultVO> participants,
        List<AccountResultVO> teacherAccounts,
        List<CourseArtifactVO> courseArtifacts,
        List<PaymentResultVO> payments,
        Long receiptId,
        String receiptNo,
        Map<String, Object> receiptPreviewData,
        BigDecimal amountDue,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        String paymentStatus
) {

    @Builder
    @Schema(description = "Account creation or reuse result")
    public record AccountResultVO(
            Long userId,
            String username,
            String displayName,
            String userType,
            Boolean created,
            String initialPassword
    ) {
    }

    @Builder
    @Schema(description = "Registration participant result")
    public record ParticipantResultVO(
            Long studentId,
            String studentNo,
            String studentName,
            Boolean studentReused,
            Long packageId,
            String packageNo,
            BigDecimal priceAmount,
            AccountResultVO studentAccount
    ) {
    }

    @Builder
    @Schema(description = "Generated course artifact result")
    public record CourseArtifactVO(
            Long orderItemId,
            Integer lineNo,
            String courseType,
            Long packageId,
            String packageNo,
            Long classEnrollmentId,
            Long classId
    ) {
    }

    @Builder
    @Schema(description = "Payment record result")
    public record PaymentResultVO(
            Long paymentId,
            String paymentNo,
            Integer participantIndex,
            String paymentMethod,
            BigDecimal amount,
            LocalDateTime paidAt,
            String handledByName,
            String remark
    ) {
    }
}
