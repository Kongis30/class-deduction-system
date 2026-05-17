package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Classroom save request.
 */
@Data
@Schema(description = "Classroom save request")
public class ClassroomSaveRequest {

    @Schema(description = "Room code (auto-generated if not provided)", example = "R001")
    private String roomCode;

    @NotBlank(message = "Room name is required")
    @Schema(description = "Room name", example = "Room 101")
    private String roomName;

    @Schema(description = "Campus name", example = "Main Building")
    private String campus;

    @Min(value = 0, message = "Capacity must be 0 or greater")
    @Schema(description = "Classroom capacity", example = "6")
    private Integer capacity;

    @Schema(description = "Classroom status", example = "ACTIVE")
    private String status;

    @Schema(description = "Internal notes")
    private String notes;
}
