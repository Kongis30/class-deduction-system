package com.classdeduction.system.modules.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Monthly revenue breakdown: month → campus → course product")
public record MonthlyRevenueVO(
        @Schema(description = "Month columns in display order") List<MonthColumnVO> months,
        @Schema(description = "One section per campus, followed by the 1-on-1 section") List<SectionVO> sections,
        @Schema(description = "Payments that could not be matched to a course product and are shown as Unassigned product")
        int unassignedPaymentCount
) {

    @Schema(description = "Month column")
    public record MonthColumnVO(String key, String label) {
    }

    /** 一个分组区块：校区（或 1 对 1）；行按课程产品汇总。 */
    @Schema(description = "Revenue section")
    public record SectionVO(String key, String title, boolean oneOnOne, List<RowVO> rows) {
    }

    /** 产品行；amounts 与 months 一一对应。 */
    @Schema(description = "Revenue row for one course product")
    public record RowVO(Long productId, String productName, List<BigDecimal> amounts) {
    }
}
