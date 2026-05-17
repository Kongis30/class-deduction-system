package com.classdeduction.system.modules.lesson.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 课时流水分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Hour ledger page query")
public class HourLedgerPageQuery extends PageQuery {

    @Schema(description = "Package ID", example = "1880000000000000001")
    private Long packageId;

    @Schema(description = "Ledger type", example = "DEDUCT")
    private String ledgerType;

    @Schema(description = "Business date from", example = "2026-04-01")
    private LocalDate businessDateFrom;

    @Schema(description = "Business date to", example = "2026-04-30")
    private LocalDate businessDateTo;
}
