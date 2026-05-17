package com.classdeduction.system.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Shared paging query parameters used by list endpoints.
 */
@Data
@Schema(description = "Shared page query parameters")
public class PageQuery {

    @Min(value = 1, message = "Page number must be at least 1")
    @Schema(description = "Page number, starting from 1", example = "1")
    private long pageNum = 1;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 200, message = "Page size must be at most 200")
    @Schema(description = "Page size, maximum 200", example = "10")
    private long pageSize = 10;
}
