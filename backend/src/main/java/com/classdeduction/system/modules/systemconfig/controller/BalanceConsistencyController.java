package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.BalanceConsistencyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据一致性校验控制器：检查课包余额与账本是否一致。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/system/balance-consistency")
@Tag(name = "System - Balance Consistency")
@RequireRoles({"ADMIN"})
public class BalanceConsistencyController {

    private final SystemNotificationService systemNotificationService;

    @GetMapping
    @Operation(summary = "Check balance consistency between course_package and hour_ledger")
    public ApiResponse<BalanceConsistencyVO> checkBalanceConsistency() {
        return ApiResponse.success(systemNotificationService.checkBalanceConsistency());
    }
}