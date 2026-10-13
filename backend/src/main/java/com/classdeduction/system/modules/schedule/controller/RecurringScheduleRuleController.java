package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleCreateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRulePageQuery;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleUpdateRequest;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import com.classdeduction.system.modules.schedule.service.PrivateSchedulePrintService;
import com.classdeduction.system.modules.schedule.vo.PrivateSchedulePrintRevisionVO;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleDetailVO;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleListItemVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleCreateResultVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleUpdateResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 周期排课规则控制器：负责规则创建、更新与查询。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/schedule/rules")
@Tag(name = "Schedule - Recurring Rules")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class RecurringScheduleRuleController {

    private final RecurringScheduleRuleService recurringScheduleRuleService;
    private final PrivateSchedulePrintService privateSchedulePrintService;

    @GetMapping
    @Operation(summary = "Page recurring schedule rules")
    public ApiResponse<PageResponse<RecurringScheduleRuleListItemVO>> pageRules(@ParameterObject RecurringScheduleRulePageQuery query) {
        return ApiResponse.success(recurringScheduleRuleService.pageRules(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get recurring schedule rule detail")
    public ApiResponse<RecurringScheduleRuleDetailVO> getRule(@PathVariable Long id) {
        return ApiResponse.success(recurringScheduleRuleService.getRule(id));
    }

    @GetMapping("/package/{packageId}/print-history")
    @Operation(summary = "List all recurring schedule revisions for a private lesson signature sheet")
    public ApiResponse<List<PrivateSchedulePrintRevisionVO>> printHistory(@PathVariable Long packageId) {
        return ApiResponse.success(privateSchedulePrintService.listRevisions(packageId));
    }

    @PostMapping
    @Operation(summary = "Create recurring schedule rule and generate planned lessons")
    public ApiResponse<ScheduleRuleCreateResultVO> createRule(@Valid @RequestBody RecurringScheduleRuleCreateRequest request) {
        return ApiResponse.success("Created successfully.", recurringScheduleRuleService.createRule(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update recurring schedule rule and regenerate future unconfirmed planned lessons")
    public ApiResponse<ScheduleRuleUpdateResultVO> updateRule(@PathVariable Long id,
                                                               @Valid @RequestBody RecurringScheduleRuleUpdateRequest request) {
        return ApiResponse.success("Updated successfully.", recurringScheduleRuleService.updateRule(id, request));
    }
}
