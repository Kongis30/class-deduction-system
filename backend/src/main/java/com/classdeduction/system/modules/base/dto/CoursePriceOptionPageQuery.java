package com.classdeduction.system.modules.base.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Course price option page query parameters.
 */
@Getter
@Setter
@Schema(description = "Course price option page query")
public class CoursePriceOptionPageQuery extends PageQuery {

    @Schema(description = "Course type", example = "ONE_ON_ONE")
    private String courseType;

    @Schema(description = "Option status", example = "ACTIVE")
    private String status;

    @Schema(description = "Linked course product ID", example = "2001")
    private Long courseProductId;

    @Schema(description = "Keyword for register class, detail, grade, period, or time slot", example = "Saturday")
    private String keyword;
}
