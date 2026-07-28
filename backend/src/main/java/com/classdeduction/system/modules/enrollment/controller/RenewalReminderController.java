package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.enrollment.dto.RenewalReminderSendRequest;
import com.classdeduction.system.modules.enrollment.dto.RenewalReminderSettingsUpdateRequest;
import com.classdeduction.system.modules.enrollment.service.RenewalReminderService;
import com.classdeduction.system.modules.enrollment.vo.RenewalReminderOverviewVO;
import com.classdeduction.system.modules.enrollment.vo.RenewalReminderSendResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 续费提醒工作台：管理员查看名单、调整简单阈值、预览并确认发送。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/renewal-reminders")
@Tag(name = "Enrollment - Renewal Reminders")
@RequireRoles({"ADMIN"})
public class RenewalReminderController {

    private final RenewalReminderService renewalReminderService;

    @GetMapping
    @Operation(summary = "Get renewal reminder workbench")
    public ApiResponse<RenewalReminderOverviewVO> getOverview() {
        return ApiResponse.success(renewalReminderService.getOverview());
    }

    @PutMapping("/settings")
    @Operation(summary = "Update simple renewal reminder thresholds")
    public ApiResponse<RenewalReminderOverviewVO> updateSettings(
            @Valid @RequestBody RenewalReminderSettingsUpdateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Reminder rules saved.",
                renewalReminderService.updateSettings(request, currentUser.userId()));
    }

    @PostMapping("/send")
    @Operation(summary = "Send administrator-confirmed renewal reminder emails")
    public ApiResponse<RenewalReminderSendResultVO> sendReminders(
            @Valid @RequestBody RenewalReminderSendRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Reminder emails processed.",
                renewalReminderService.sendConfirmedReminders(request, currentUser.userId()));
    }
}
