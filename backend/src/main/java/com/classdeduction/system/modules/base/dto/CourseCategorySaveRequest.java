package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Course category save request. Only display name and sort order are editable.
 */
@Data
@Schema(description = "Course category save request")
public class CourseCategorySaveRequest {

    @NotBlank(message = "Display name is required")
    @Schema(description = "Display name", example = "After School")
    private String displayName;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;
}
