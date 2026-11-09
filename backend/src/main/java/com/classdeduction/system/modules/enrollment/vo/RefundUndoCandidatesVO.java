package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Refund transactions linked to one Data Summary purchase row")
public record RefundUndoCandidatesVO(
        List<Candidate> candidates,
        String unavailableReason
) {
    @Schema(description = "One refund transaction candidate for a student and course purchase")
    public record Candidate(
            Long receiptId,
            String receiptNo,
            Long paymentId,
            String paymentNo,
            LocalDateTime refundedAt,
            BigDecimal amount,
            String paymentMethod,
            boolean eligible,
            String ineligibleReason
    ) {
    }
}
