package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Query used by registration intake to find an existing student before creating one.
 */
@Getter
@Setter
@Schema(description = "Student reuse query")
public class StudentReuseQuery {

    @NotBlank(message = "Student name is required")
    @Schema(description = "Student name", example = "Ava Lin")
    private String studentName;

    @NotBlank(message = "Guardian phone is required")
    @Schema(description = "Primary guardian phone", example = "917-555-0101")
    private String guardianPhone;
}
