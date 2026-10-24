package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.systemconfig.service.SystemMessageService;
import com.classdeduction.system.modules.systemconfig.vo.SystemMessageInboxVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员个人站内消息接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/system/messages")
@Tag(name = "System - Messages")
@RequireRoles({"ADMIN"})
public class SystemMessageController {

    private final SystemMessageService systemMessageService;

    @GetMapping
    @Operation(summary = "Get the current administrator's inbox")
    public ApiResponse<SystemMessageInboxVO> getInbox(
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        return ApiResponse.success(systemMessageService.getInbox(currentUser.userId()));
    }

    @PutMapping("/{messageId}/read")
    @Operation(summary = "Mark a message as read")
    public ApiResponse<Void> markRead(
            @PathVariable Long messageId,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        systemMessageService.markRead(messageId, currentUser.userId());
        return ApiResponse.success(null);
    }
}
