package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.dto.WeeklyScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.service.ScheduleAdjustmentService;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
import com.classdeduction.system.modules.schedule.vo.WeeklyScheduleAdjustmentResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 调课控制器：负责调课、补课和换老师/教室的后端入口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/schedule/adjustments")
@Tag(name = "Schedule - Adjustments")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class ScheduleAdjustmentController {

    private final ScheduleAdjustmentService scheduleAdjustmentService;

    @PostMapping
    @RequireRoles({"ADMIN", "STAFF", "MANAGER", "TEACHER"})
    @Operation(summary = "Adjust an existing planned lesson or create a makeup lesson")
    public ApiResponse<ScheduleAdjustmentResultVO> adjust(
            @Valid @RequestBody ScheduleAdjustmentRequest request,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser) {
        if (currentUser != null && currentUser.roleCodes() != null && currentUser.roleCodes().contains("TEACHER")) {
            return ApiResponse.success("Adjusted successfully.", scheduleAdjustmentService.adjustByTeacher(request, currentUser));
        }
        return ApiResponse.success("Adjusted successfully.", scheduleAdjustmentService.adjust(request));
    }

    @PostMapping("/weekly")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    @Operation(summary = "Temporarily adjust planned lessons in one natural week")
    public ApiResponse<WeeklyScheduleAdjustmentResultVO> adjustWeekly(
            @Valid @RequestBody WeeklyScheduleAdjustmentRequest request) {
        return ApiResponse.success("Weekly schedule adjusted successfully.", scheduleAdjustmentService.adjustWeekly(request));
    }
}
