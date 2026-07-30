package com.classdeduction.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 教师账号创建请求：创建老师档案同时创建登录账号。
 */
@Data
@Schema(description = "Teacher account create request")
public class TeacherAccountCreateRequest {

    @Schema(description = "Existing teacher profile ID. When present, create login access for that profile.")
    private Long teacherId;

    @Schema(description = "Teacher name", example = "Emily Chen")
    private String teacherName;

    @Schema(description = "Phone number", example = "917-555-0201")
    private String mobile;

    @Email(message = "Email format is invalid")
    @Schema(description = "Email address")
    private String email;

    @Schema(description = "Subject tags")
    private List<String> subjectTags;

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

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    @Schema(description = "Remark")
    private String remark;
}
