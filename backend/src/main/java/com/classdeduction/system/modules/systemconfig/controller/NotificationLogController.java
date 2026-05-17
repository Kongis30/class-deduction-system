package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.systemconfig.dto.NotificationDispatchRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationLogPageQuery;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.NotificationDispatchResultVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知日志控制器：查看邮件日志、手动重试，并由管理员手动触发安全分发。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/system/notification-logs")
@Tag(name = "System - Notification Logs")
@RequireRoles({"ADMIN", "STAFF"})
public class NotificationLogController {

    private final SystemNotificationService systemNotificationService;

    @GetMapping
    @Operation(summary = "Page notification logs")
    public ApiResponse<PageResponse<NotificationLogVO>> pageLogs(@ParameterObject NotificationLogPageQuery query) {
        return ApiResponse.success(systemNotificationService.pageLogs(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification log detail")
    public ApiResponse<NotificationLogVO> getLog(@PathVariable Long id) {
        return ApiResponse.success(systemNotificationService.getLog(id));
    }

    @PostMapping("/{id}/requeue")
    @Operation(summary = "Requeue one failed notification log")
    @RequireRoles({"ADMIN"})
    public ApiResponse<Void> requeueLog(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        systemNotificationService.requeueFailedLog(id, currentUser.userId());
        return ApiResponse.success("Requeued successfully.", null);
    }

    @PostMapping("/dispatch")
    @Operation(summary = "Dispatch one batch of pending email logs manually")
    @RequireRoles({"ADMIN"})
    public ApiResponse<NotificationDispatchResultVO> dispatchPendingEmails(
            @Valid @RequestBody NotificationDispatchRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Dispatch completed.",
                systemNotificationService.dispatchPendingEmails(request, currentUser.userId()));
    }
}
