package com.classdeduction.system.modules.report.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.report.dto.MonthlyRevenueQuery;
import com.classdeduction.system.modules.report.service.MonthlyRevenueService;
import com.classdeduction.system.modules.report.vo.MonthlyRevenueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 月度收入报表：按月 → 校区 → 课程产品汇总实际收款净额。
 *
 * <p>金额来自收据付款流水，属于财务数据，因此对 MANAGER 关闭。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/reports/monthly-revenue")
@Tag(name = "Report - Monthly Revenue")
@RequireRoles({"ADMIN", "STAFF"})
public class MonthlyRevenueController {

    private final MonthlyRevenueService monthlyRevenueService;

    @GetMapping
    @Operation(summary = "Monthly revenue breakdown by campus and course product")
    public ApiResponse<MonthlyRevenueVO> getMonthlyRevenue(@ParameterObject @Valid MonthlyRevenueQuery query) {
        return ApiResponse.success(monthlyRevenueService.getMonthlyRevenue(query));
    }
}
