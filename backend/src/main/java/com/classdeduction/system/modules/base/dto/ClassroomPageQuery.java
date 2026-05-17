package com.classdeduction.system.modules.base.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Classroom list query parameters.
 */
@Getter
@Setter
@Schema(description = "Classroom list query")
public class ClassroomPageQuery extends PageQuery {

    @Schema(description = "Keyword for room code, room name, or campus", example = "A101")
    private String keyword;

    @Schema(description = "Campus name filter", example = "Main Building")
    private String campus;

    @Schema(description = "Classroom status", example = "ACTIVE")
    private String status;
}
