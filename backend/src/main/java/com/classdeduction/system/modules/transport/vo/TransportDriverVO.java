package com.classdeduction.system.modules.transport.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 接送司机展示数据。 */
@Schema(description = "Transport driver")
public record TransportDriverVO(
        Long id,
        String driverName,
        Integer displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
