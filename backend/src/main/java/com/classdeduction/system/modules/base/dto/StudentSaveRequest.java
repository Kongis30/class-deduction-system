package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * Student save request: flat guardian fields (primary guardian only) for simplicity.
 */
@Data
@Schema(description = "Student save request")
public class StudentSaveRequest {

    @Schema(description = "Student ID (auto-generated if blank)", example = "STU-2026001")
    private String studentNo;

    @NotBlank(message = "Student name is required")
    @Schema(description = "Student name", example = "Ava Lin")
    private String studentName;

    @Schema(description = "English name", example = "Ava")
    private String englishName;

    @Schema(description = "Gender", example = "FEMALE")
    private String gender;

    @Schema(description = "Birth date", example = "2012-08-16")
    private LocalDate birthDate;

    @Schema(description = "Current grade", example = "Grade 6")
    private String grade;

    @Schema(description = "Student class level", example = "G6")
    private String studentClass;

    @Schema(description = "Current school", example = "PS 101")
    private String school;

    @Schema(description = "Lead source", example = "REFERRAL")
    private String sourceChannel;

    @Schema(description = "Student status", example = "ACTIVE")
    private String status;

    @Schema(description = "Internal notes")
    private String notes;

    // ─── Primary Guardian (flat fields) ───────────────────
    @Schema(description = "Guardian name", example = "Mei Lin")
    private String guardianName;

    @Schema(description = "Guardian phone", example = "917-555-0101")
    private String guardianPhone;

    @Schema(description = "Secondary guardian phone", example = "917-555-0102")
    private String guardianPhone2;

    @Schema(description = "Guardian email", example = "parent@example.com")
    private String guardianEmail;

    @Schema(description = "Guardian relation type", example = "MOTHER")
    private String guardianRelationType;

    @Schema(description = "Whether pickup is required", example = "true")
    private Boolean pickupRequired;

    @Schema(description = "Default pickup address", example = "123 Main St")
    private String pickupAddress;

    @Schema(description = "Whether dropoff is required", example = "true")
    private Boolean dropoffRequired;

    @Schema(description = "Default dropoff address", example = "456 Center Ave")
    private String dropoffAddress;

    @Schema(description = "Campus ID", example = "1")
    private Long campusId;
}
