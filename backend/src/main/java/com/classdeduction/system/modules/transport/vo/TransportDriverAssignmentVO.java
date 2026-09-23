package com.classdeduction.system.modules.transport.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 接送名单行对应的司机设置。 */
@Schema(description = "Transport driver assignment")
public record TransportDriverAssignmentVO(
        String assignmentKey,
        Long driverId,
        String driverName,
        String transportType
) {
}
