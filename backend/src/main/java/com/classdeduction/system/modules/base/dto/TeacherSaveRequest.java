package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Teacher save request.
 */
@Data
@Schema(description = "Instructor save request")
public class TeacherSaveRequest {

    @Schema(description = "Instructor ID (auto-generated if not provided)", example = "INS-2026001")
    private String teacherNo;

    @NotBlank(message = "Instructor name is required")
    @Schema(description = "Instructor name", example = "Ethan Chen")
    private String teacherName;

    @Email(message = "Email format is invalid")
    @Schema(description = "Email address", example = "teacher@example.com")
    private String email;

    @Pattern(regexp = "^$|^[0-9+\\-() ]{6,32}$", message = "Phone number format is invalid")
    @Schema(description = "Phone number", example = "917-555-0201")
    private String mobile;

    @Schema(description = "Subject tags")
    private List<String> subjectTags;

    @Schema(description = "Instructor status", example = "ACTIVE")
    private String status;

    @Schema(description = "Hire date", example = "2026-01-01")
    private LocalDate hireDate;

    @Schema(description = "Internal notes")
    private String notes;
}
