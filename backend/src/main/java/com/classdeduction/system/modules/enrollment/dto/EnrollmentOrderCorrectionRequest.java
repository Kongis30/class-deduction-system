package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * Enrollment order correction request.
 *
 * <p>Allows staff to fix registration/receipt details after submission (student
 * name, contact phones, pickup/drop-off info, sign-up date, handler and remark).
 * Financial amounts, courses, and payments are intentionally excluded and can only
 * be changed through cancel/refund flows.</p>
 */
@Data
@Schema(description = "Enrollment order correction request")
public class EnrollmentOrderCorrectionRequest {

    @Schema(description = "Student display name")
    private String studentName;

    @Schema(description = "Student grade")
    private String grade;

    @Schema(description = "Student school")
    private String school;

    @Schema(description = "Guardian phone")
    private String guardianPhone;

    @Schema(description = "Secondary guardian phone")
    private String guardianPhone2;

    @Schema(description = "Whether pickup is required")
    private Boolean pickupRequired;

    @Schema(description = "Pickup address")
    private String pickupAddress;

    @Schema(description = "Whether drop-off is required")
    private Boolean dropoffRequired;

    @Schema(description = "Drop-off address")
    private String dropoffAddress;

    @Schema(description = "Sign-up date")
    private LocalDate signupDate;

    @Schema(description = "Handled by name")
    private String handledByName;

    @Schema(description = "Registration remark")
    private String remark;
}
