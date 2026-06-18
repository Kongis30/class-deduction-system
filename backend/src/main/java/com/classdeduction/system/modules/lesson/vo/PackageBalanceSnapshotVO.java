package com.classdeduction.system.modules.lesson.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 课包余额快照响应。
 */
@Builder
@Schema(description = "Package balance snapshot response")
public record PackageBalanceSnapshotVO(
        Long packageId,
        String packageNo,
        Double totalUnits,
        Double consumedUnits,
        Double remainingUnits,
        Long lastLedgerId,
        LocalDateTime snapshotAt
) {
}
