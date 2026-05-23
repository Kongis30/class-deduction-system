package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.service.ScheduleAdjustmentService;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
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
@RequireRoles({"ADMIN", "STAFF", "TEACHER"})
public class ScheduleAdjustmentController {

    private final ScheduleAdjustmentService scheduleAdjustmentService;

    @PostMapping
    @Operation(summary = "Adjust an existing planned lesson or create a makeup lesson")
    public ApiResponse<ScheduleAdjustmentResultVO> adjust(@Valid @RequestBody ScheduleAdjustmentRequest request) {
        return ApiResponse.success("Adjusted successfully.", scheduleAdjustmentService.adjust(request));
    }
}
