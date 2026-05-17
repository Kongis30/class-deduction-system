package com.classdeduction.system.modules.systemconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 余额一致性检查结果。
 */
@Schema(description = "Balance consistency check result")
public record BalanceConsistencyVO(
        @Schema(description = "Total number of checked packages")
        int totalChecked,

        @Schema(description = "Number of packages with discrepancies")
        int discrepancyCount,

        @Schema(description = "List of packages with balance discrepancies")
        List<PackageDiscrepancy> discrepancies
) {
    @Schema(description = "Single package discrepancy detail")
    public record PackageDiscrepancy(
            @Schema(description = "Package ID")
            Long packageId,

            @Schema(description = "Package number")
            String packageNo,

            @Schema(description = "Student ID")
            Long studentId,

            @Schema(description = "Balance in course_package.remaining_units")
            Integer packageBalance,

            @Schema(description = "Balance from the last hour_ledger entry")
            Integer ledgerBalance,

            @Schema(description = "Suggested fix: course_package.remaining_units should be set to this value")
            Integer suggestedRemainingUnits,

            @Schema(description = "Discrepancy type: UNDER_DEBIT (账本多扣), OVER_DEBIT (课包多扣), SNAPSHOT_DIVERGED (快照不一致)")
            String discrepancyType,

            @Schema(description = "Human-readable description")
            String description
    ) {}
}