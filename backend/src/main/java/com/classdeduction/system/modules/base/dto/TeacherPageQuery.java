package com.classdeduction.system.modules.base.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Teacher list query parameters.
 */
@Getter
@Setter
@Schema(description = "Instructor list query")
public class TeacherPageQuery extends PageQuery {

    @Schema(description = "Keyword for instructor ID or instructor name", example = "Ethan")
    private String keyword;

    @Schema(description = "Subject tag to filter instructors by", example = "Math")
    private String subject;

    @Schema(description = "Instructor status", example = "ACTIVE")
    private String status;
}
