package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTriggerRuleUpdateRequest;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.NotificationTriggerRuleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知触发规则控制器：管理 LESSON_CONFIRMED / LOW_BALANCE 触发器开关与收件人配置。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/system/notification-trigger-rules")
@Tag(name = "System - Notification Trigger Rules")
@RequireRoles({"ADMIN"})
public class NotificationTriggerRuleController {

    private final SystemNotificationService systemNotificationService;

    @GetMapping
    @Operation(summary = "List all notification trigger rules")
    public ApiResponse<List<NotificationTriggerRuleVO>> listRules() {
        return ApiResponse.success(systemNotificationService.listTriggerRules());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a notification trigger rule")
    public ApiResponse<NotificationTriggerRuleVO> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody NotificationTriggerRuleUpdateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Saved successfully.",
                systemNotificationService.updateTriggerRule(id, request, currentUser.userId()));
    }
}