package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.systemconfig.dto.NotificationBroadcastRequest;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.BroadcastResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 群发通知控制器。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/system/notifications")
@Tag(name = "System - Notifications")
@RequireRoles({"ADMIN"})
public class NotificationBroadcastController {

    private final SystemNotificationService systemNotificationService;

    @PostMapping("/broadcast")
    @Operation(summary = "Broadcast a custom notification to selected recipients")
    public ApiResponse<BroadcastResultVO> broadcast(
            @Valid @RequestBody NotificationBroadcastRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Notification queued for dispatch.",
                systemNotificationService.broadcast(request, currentUser.userId()));
    }
}