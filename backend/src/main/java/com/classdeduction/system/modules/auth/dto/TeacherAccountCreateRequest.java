package com.classdeduction.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 教师账号创建请求：由教务或管理员为老师创建登录账号。
 */
@Data
@Schema(description = "Teacher account create request")
public class TeacherAccountCreateRequest {

    @NotNull(message = "Teacher ID is required")
    @Schema(description = "Linked teacher ID", example = "1880000000000000001")
    private Long teacherId;

    @NotBlank(message = "Username is required")
    @Size(max = 64, message = "Username must not exceed 64 characters")
    @Schema(description = "Login username", example = "emma.wang")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password length must be between 8 and 64 characters")
    @Schema(description = "Initial password", example = "Teacher@123456")
    private String password;

    @Schema(description = "Display name, defaulting to teacher name")
    private String displayName;

    @Schema(description = "Mobile number")
    private String mobile;

    @Email(message = "Email format is invalid")
    @Schema(description = "Email address")
    private String email;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    @Schema(description = "Remark")
    private String remark;
}
