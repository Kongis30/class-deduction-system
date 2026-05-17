package com.classdeduction.system.modules.base.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Student list query parameters.
 */
@Getter
@Setter
@Schema(description = "Student list query")
public class StudentPageQuery extends PageQuery {

    @Schema(description = "Keyword for student ID or student name", example = "Ava")
    private String keyword;

    @Schema(description = "Student status", example = "ACTIVE")
    private String status;

    @Schema(description = "Guardian name keyword", example = "Mei Lin")
    private String guardian;
}
