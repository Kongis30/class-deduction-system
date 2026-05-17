package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.systemconfig.dto.BrandConfigUpdateRequest;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.BrandConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 品牌配置控制器：维护系统当前品牌与通知发件展示信息。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/system/brand-config/current")
@Tag(name = "System - Brand Config")
@RequireRoles({"ADMIN", "STAFF"})
public class BrandConfigController {

    private final SystemNotificationService systemNotificationService;

    @GetMapping
    @Operation(summary = "Get current brand config")
    public ApiResponse<BrandConfigVO> getCurrentBrandConfig() {
        return ApiResponse.success(systemNotificationService.getCurrentBrandConfig());
    }

    @PutMapping
    @Operation(summary = "Create or update current brand config")
    public ApiResponse<BrandConfigVO> updateCurrentBrandConfig(
            @Valid @RequestBody BrandConfigUpdateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Saved successfully.",
                systemNotificationService.upsertCurrentBrandConfig(request, currentUser.userId()));
    }
}
