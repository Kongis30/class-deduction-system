package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Guardian contact request payload nested inside student create and update requests.
 */
@Data
@Schema(description = "Guardian contact request")
public class GuardianContactRequest {

    @Schema(description = "Guardian ID, used when updating", example = "1880000000000000001")
    private Long id;

    @NotBlank(message = "Guardian name is required")
    @Schema(description = "Guardian name", example = "Mei Lin")
    private String contactName;

    @Schema(description = "Relationship type", example = "MOTHER")
    private String relationType;

    @Pattern(regexp = "^$|^[0-9+\\-() ]{6,32}$", message = "Phone number format is invalid")
    @Schema(description = "Phone number", example = "917-555-0101")
    private String mobile;

    @Email(message = "Email format is invalid")
    @Schema(description = "Email address", example = "parent@example.com")
    private String email;

    @Schema(description = "Whether this is the primary guardian", example = "true")
    private Boolean primary;

    @Schema(description = "Contact status", example = "ACTIVE")
    private String status;

    @Schema(description = "Internal notes")
    private String notes;
}
