package com.classdeduction.system.common.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.List;

/**
 * Shared paged response payload aligned with the frontend contract.
 */
@Schema(description = "Paged response payload")
public record PageResponse<T>(
        @Schema(description = "Current page records")
        List<T> records,
        @Schema(description = "Total record count")
        long total,
        @Schema(description = "Current page number")
        long pageNum,
        @Schema(description = "Page size")
        long pageSize
) {

    public static <T> PageResponse<T> of(List<T> records, long total, long pageNum, long pageSize) {
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public static <T> PageResponse<T> empty(long pageNum, long pageSize) {
        return new PageResponse<>(Collections.emptyList(), 0, pageNum, pageSize);
    }
}
