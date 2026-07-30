package com.classdeduction.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生账号创建请求：创建学生档案同时创建登录账号。
 */
@Data
@Schema(description = "Student account create request")
public class StudentAccountCreateRequest {

    @Schema(description = "Existing student profile ID. When present, create login access for that profile.")
    private Long studentId;

    @Schema(description = "Student name", example = "Ava Lin")
    private String studentName;

    @Schema(description = "Guardian name", example = "Mei Lin")
    private String guardianName;

    @Schema(description = "Guardian phone", example = "917-555-0101")
    private String guardianPhone;

    @Schema(description = "Guardian email", example = "parent@example.com")
    private String guardianEmail;

    @NotBlank(message = "Username is required")
    @Size(max = 64, message = "Username must not exceed 64 characters")
    @Schema(description = "Login username", example = "student.sophia")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password length must be between 8 and 64 characters")
    @Schema(description = "Initial password", example = "Student@123456")
    private String password;

    @Schema(description = "Display name, defaulting to student name")
    private String displayName;

    @Email(message = "Email format is invalid")
    @Schema(description = "Email address")
    private String email;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    @Schema(description = "Remark")
    private String remark;
}
