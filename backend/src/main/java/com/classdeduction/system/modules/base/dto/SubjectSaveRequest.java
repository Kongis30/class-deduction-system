package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Subject save request.
 */
@Data
@Schema(description = "Subject save request")
public class SubjectSaveRequest {

    @NotBlank(message = "Subject name is required")
    @Schema(description = "Subject name", example = "Math")
    private String subjectName;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Subject status", example = "ACTIVE")
    private String status;

    @Schema(description = "Internal notes")
    private String notes;
}
