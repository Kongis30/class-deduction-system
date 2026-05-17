package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Campus save request.
 */
@Data
@Schema(description = "Campus save request")
public class CampusSaveRequest {

    @NotBlank(message = "Campus name is required")
    @Schema(description = "Campus name", example = "Main Building")
    private String campusName;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Campus status", example = "ACTIVE")
    private String status;

    @Schema(description = "Internal notes")
    private String notes;
}
