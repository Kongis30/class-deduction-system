package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.systemconfig.dto.NotificationConfigUpdateRequest;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.NotificationConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 邮件发送方配置控制器：管理发件人地址、授权码、白名单开关等。
 * SMTP 配置涉及授权码等敏感信息，仅管理员可读写与触发测试邮件。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequireRoles({"ADMIN"})
@RequestMapping("/v1/system/notification-config")
@Tag(name = "System - Notification Config")
public class NotificationConfigController {

    private final SystemNotificationService systemNotificationService;

    @GetMapping
    @Operation(summary = "Get current email sending config")
    public ApiResponse<NotificationConfigVO> getConfig() {
        return ApiResponse.success(systemNotificationService.getNotificationConfig());
    }

    @PutMapping
    @Operation(summary = "Update email sending config")
    public ApiResponse<NotificationConfigVO> updateConfig(
            @Valid @RequestBody NotificationConfigUpdateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Saved successfully.",
                systemNotificationService.updateNotificationConfig(request, currentUser.userId()));
    }

    @PostMapping("/test")
    @Operation(summary = "Send a test email to the configured from address")
    public ApiResponse<String> sendTestEmail(
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        systemNotificationService.sendTestEmail(currentUser.userId());
        return ApiResponse.success("Test email queued. It will be sent shortly.", null);
    }
}