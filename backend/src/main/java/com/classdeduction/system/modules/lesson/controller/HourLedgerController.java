package com.classdeduction.system.modules.lesson.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.lesson.dto.HourLedgerPageQuery;
import com.classdeduction.system.modules.lesson.service.HourLedgerService;
import com.classdeduction.system.modules.lesson.vo.HourLedgerVO;
import com.classdeduction.system.modules.lesson.vo.PackageBalanceSnapshotVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课时流水控制器：负责流水和余额快照查询。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/lessons")
@Tag(name = "Lesson - Ledger")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class HourLedgerController {

    private final HourLedgerService hourLedgerService;

    @GetMapping("/ledger")
    @Operation(summary = "Page hour ledger records")
    public ApiResponse<PageResponse<HourLedgerVO>> pageLedger(@ParameterObject HourLedgerPageQuery query) {
        return ApiResponse.success(hourLedgerService.pageLedger(query));
    }

    @GetMapping("/balance-snapshots/{packageId}")
    @Operation(summary = "Get package balance snapshot")
    public ApiResponse<PackageBalanceSnapshotVO> getBalanceSnapshot(@PathVariable Long packageId) {
        return ApiResponse.success(hourLedgerService.getBalanceSnapshot(packageId));
    }
}
