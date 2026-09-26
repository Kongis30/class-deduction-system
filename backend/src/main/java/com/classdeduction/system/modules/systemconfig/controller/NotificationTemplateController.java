package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTemplatePageQuery;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTemplateUpdateRequest;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.NotificationTemplateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知模板控制器：由后台维护邮件模板内容与状态。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/system/notification-templates")
@Tag(name = "System - Notification Templates")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class NotificationTemplateController {

    private final SystemNotificationService systemNotificationService;

    @GetMapping
    @Operation(summary = "Page notification templates")
    public ApiResponse<PageResponse<NotificationTemplateVO>> pageTemplates(@ParameterObject NotificationTemplatePageQuery query) {
        return ApiResponse.success(systemNotificationService.pageTemplates(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification template detail")
    public ApiResponse<NotificationTemplateVO> getTemplate(@PathVariable Long id) {
        return ApiResponse.success(systemNotificationService.getTemplate(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update notification template")
    public ApiResponse<NotificationTemplateVO> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody NotificationTemplateUpdateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Saved successfully.",
                systemNotificationService.updateTemplate(id, request, currentUser.userId()));
    }
}
