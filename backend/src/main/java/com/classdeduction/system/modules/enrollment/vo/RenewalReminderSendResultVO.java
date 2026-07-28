package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Result of administrator-confirmed renewal reminder emails")
public record RenewalReminderSendResultVO(
        Integer requestedCount,
        Integer sentCount,
        Integer failedCount,
        List<SendItemVO> items
) {

    public record SendItemVO(
            String reminderId,
            String studentName,
            String recipient,
            Boolean sent,
            String message
    ) {
    }
}
