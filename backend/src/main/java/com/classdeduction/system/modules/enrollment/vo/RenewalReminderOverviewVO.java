package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Renewal reminder workbench overview")
public record RenewalReminderOverviewVO(
        SettingsVO settings,
        Boolean emailReady,
        String senderAddress,
        List<ReminderVO> reminders
) {

    @Schema(description = "Simple reminder thresholds shown directly in the workbench")
    public record SettingsVO(
            Integer expiryReminderDays,
            Integer oneOnOneRemainingLessons
    ) {
    }

    @Schema(description = "One renewal follow-up item and its prepared guardian email")
    public record ReminderVO(
            String reminderId,
            String reminderType,
            Long studentId,
            String studentName,
            String guardianName,
            String guardianEmail,
            Long packageId,
            Long classEnrollmentId,
            Long classId,
            String courseName,
            String courseType,
            LocalDate expectedEndDate,
            Integer daysUntilEnd,
            Double remainingHours,
            Integer remainingLessons,
            String status,
            String reason,
            Boolean emailAvailable,
            String emailSubject,
            String emailBody
    ) {
    }
}
