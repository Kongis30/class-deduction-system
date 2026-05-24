package com.classdeduction.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 教师账号更新请求。
 */
@Data
@Schema(description = "Teacher account update request")
public class TeacherAccountUpdateRequest {

    @Size(max = 64, message = "Username must not exceed 64 characters")
    @Schema(description = "Login username")
    private String username;

    @Size(min = 8, max = 64, message = "Password length must be between 8 and 64 characters")
    @Schema(description = "New password (leave blank to keep current)")
    private String password;

    @Schema(description = "Display name")
    private String displayName;

    @Email(message = "Email format is invalid")
    @Schema(description = "Email address")
    private String email;

    @Schema(description = "Phone number")
    private String mobile;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;
}
