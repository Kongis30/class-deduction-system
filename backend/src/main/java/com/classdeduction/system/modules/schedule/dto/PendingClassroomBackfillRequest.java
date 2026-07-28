package com.classdeduction.system.modules.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request for assigning a classroom to a pending class group or occurrence.
 */
@Getter
@Setter
@Schema(description = "Pending classroom backfill request")
public class PendingClassroomBackfillRequest {

    @NotBlank(message = "Target type is required")
    @Schema(description = "Target type: CLASS_GROUP or OCCURRENCE", example = "OCCURRENCE")
    private String targetType;

    @NotNull(message = "Target ID is required")
    @Schema(description = "Class group ID or lesson occurrence ID", example = "1880000000000000001")
    private Long targetId;

    @NotNull(message = "Classroom ID is required")
    @Schema(description = "Classroom ID to assign", example = "1880000000000000003")
    private Long classroomId;
}
