package com.classdeduction.system.modules.lesson.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 课时流水响应。
 */
@Builder
@Schema(description = "Hour ledger response")
public record HourLedgerVO(
        Long id,
        Long packageId,
        String packageNo,
        Long lessonRecordId,
        Long occurrenceId,
        String lessonNo,
        String ledgerType,
        Double changeUnits,
        Double balanceAfterUnits,
        LocalDate businessDate,
        String reasonCode,
        String reasonDesc,
        Long operatedBy,
        LocalDateTime operatedAt
) {
}
