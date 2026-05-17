package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Classroom response payload.
 */
@Builder
@Schema(description = "Classroom response payload")
public record ClassroomVO(
        Long id,
        String roomCode,
        String roomName,
        String campus,
        Integer capacity,
        String status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
