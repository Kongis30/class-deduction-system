package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import com.classdeduction.system.modules.schedule.vo.ScheduleStudentOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供排课页面所需的轻量学生选项。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/schedule/students")
@Tag(name = "Schedule - Student Lookups")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class ScheduleStudentController {

    private final RecurringScheduleRuleService recurringScheduleRuleService;

    @GetMapping("/one-on-one")
    @Operation(summary = "List active students with active one-on-one packages")
    public ApiResponse<List<ScheduleStudentOptionVO>> listOneOnOneStudents() {
        return ApiResponse.success(recurringScheduleRuleService.listOneOnOneScheduleStudents());
    }
}
