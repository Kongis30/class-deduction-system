package com.classdeduction.system.modules.systemconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 群发请求。
 */
@Data
@Schema(description = "Broadcast notification request")
public class NotificationBroadcastRequest {

    @NotBlank(message = "Subject is required")
    @Schema(description = "Email subject")
    private String subject;

    @NotBlank(message = "Body is required")
    @Schema(description = "Email body")
    private String body;

    @Schema(description = "Manual recipient email addresses (comma-separated or JSON array string)")
    private List<String> recipients;

    @Schema(description = "Recipient preset: ALL_STUDENTS, ALL_ENROLLED_STUDENTS, ALL_TEACHERS, ALL_ADMIN_USERS, CUSTOM")
    private String recipientPreset;

    @Schema(description = "Optional: filter by student name keyword")
    private String studentKeyword;
}