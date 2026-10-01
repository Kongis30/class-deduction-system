package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.GroupCourseProjectSaveRequest;
import com.classdeduction.system.modules.base.dto.GroupRegistrationPlanSaveRequest;
import com.classdeduction.system.modules.base.service.GroupCourseModelService;
import com.classdeduction.system.modules.base.vo.GroupCourseProductSetupVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Prices 的班课项目与报名方案管理 API。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/group-course-model")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class GroupCourseModelController {

    private final GroupCourseModelService service;

    @GetMapping("/products/{productId}")
    public ApiResponse<GroupCourseProductSetupVO> getProductSetup(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.success(service.getProductSetup(productId, activeOnly));
    }

    @GetMapping("/classes/{classId}/scheduled-dates")
    public ApiResponse<List<LocalDate>> getScheduledClassDates(
            @PathVariable Long classId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.success(service.getScheduledClassDates(classId, startDate, endDate));
    }

    @PostMapping("/products/{productId}/projects")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Long> createProject(@PathVariable Long productId,
                                           @Valid @RequestBody GroupCourseProjectSaveRequest request) {
        return ApiResponse.success("Teaching project created.", service.createProject(productId, request));
    }

    @PutMapping("/projects/{projectId}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> updateProject(@PathVariable Long projectId,
                                           @Valid @RequestBody GroupCourseProjectSaveRequest request) {
        service.updateProject(projectId, request);
        return ApiResponse.success("Teaching project updated.", null);
    }

    @DeleteMapping("/projects/{projectId}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> deleteProject(@PathVariable Long projectId) {
        service.deleteProject(projectId);
        return ApiResponse.success("Teaching project removed or archived.", null);
    }

    @PostMapping("/projects/{projectId}/plans")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Long> createPlan(@PathVariable Long projectId,
                                        @Valid @RequestBody GroupRegistrationPlanSaveRequest request) {
        return ApiResponse.success("Registration plan created.", service.createPlan(projectId, request));
    }

    @PutMapping("/plans/{planId}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> updatePlan(@PathVariable Long planId,
                                        @Valid @RequestBody GroupRegistrationPlanSaveRequest request) {
        service.updatePlan(planId, request);
        return ApiResponse.success("Registration plan updated.", null);
    }

    @DeleteMapping("/plans/{planId}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> deletePlan(@PathVariable Long planId) {
        service.deletePlan(planId);
        return ApiResponse.success("Registration plan removed or archived.", null);
    }
}
