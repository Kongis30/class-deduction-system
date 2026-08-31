package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Shared Data Summary Product Tab order submitted by an administrator.
 */
@Data
@Schema(description = "Course product tab order request")
public class CourseProductOrderRequest {

    @NotNull(message = "Product IDs are required")
    @Valid
    @Schema(description = "Product IDs in the desired display order")
    private List<@NotNull(message = "Product ID cannot be null") Long> productIds;
}
