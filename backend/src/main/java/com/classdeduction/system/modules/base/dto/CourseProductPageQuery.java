package com.classdeduction.system.modules.base.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Course product list query parameters.
 */
@Getter
@Setter
@Schema(description = "Course product list query")
public class CourseProductPageQuery extends PageQuery {

    @Schema(description = "Keyword for product code, name, or subject", example = "SHSAT")
    private String keyword;

    @Schema(description = "Course product status", example = "ACTIVE")
    private String status;

    @Schema(description = "Teaching mode", example = "ONE_ON_ONE")
    private String teachingMode;

    @Schema(description = "Course type", example = "SATURDAY")
    private String courseType;

    @Schema(description = "Archive scope: CURRENT, ARCHIVED, or ALL", example = "CURRENT")
    private String archiveScope;
}
