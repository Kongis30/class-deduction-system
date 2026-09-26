package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.service.PackageTeacherAssignmentService;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课时包老师分配控制器。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/enrollment/package-teachers")
@Tag(name = "Enrollment - Package Teachers")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class PackageTeacherAssignmentController {

    private final PackageTeacherAssignmentService packageTeacherAssignmentService;

    @GetMapping("/package/{packageId}")
    @Operation(summary = "根据课时包ID查询老师分配列表")
    public ApiResponse<List<PackageTeacherAssignmentVO>> listByPackageId(
            @Parameter(description = "课时包ID") @PathVariable Long packageId) {
        return ApiResponse.success(packageTeacherAssignmentService.listByPackageId(packageId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取分配详情")
    public ApiResponse<PackageTeacherAssignmentVO> getAssignment(
            @Parameter(description = "分配ID") @PathVariable Long id) {
        return ApiResponse.success(packageTeacherAssignmentService.getAssignment(id));
    }

}
