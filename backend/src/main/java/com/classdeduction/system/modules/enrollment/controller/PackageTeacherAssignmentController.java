package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.dto.PackageTeacherAssignmentRequest;
import com.classdeduction.system.modules.enrollment.service.PackageTeacherAssignmentService;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequireRoles({"ADMIN", "STAFF"})
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

    @PostMapping
    @Operation(summary = "创建老师分配记录")
    public ApiResponse<Long> createAssignment(@Valid @RequestBody PackageTeacherAssignmentRequest request) {
        return ApiResponse.success("分配创建成功。", packageTeacherAssignmentService.createAssignment(request));
    }

    @PutMapping("/{id}/end")
    @Operation(summary = "结束老师分配")
    public ApiResponse<Void> endAssignment(
            @Parameter(description = "分配ID") @PathVariable Long id,
            @Parameter(description = "变更原因") @RequestParam(required = false) String changeReason) {
        packageTeacherAssignmentService.endAssignment(id, changeReason);
        return ApiResponse.success("分配已结束。", null);
    }

    @PutMapping("/package/{packageId}/switch")
    @Operation(summary = "切换主老师")
    public ApiResponse<Void> switchPrimaryTeacher(
            @Parameter(description = "课时包ID") @PathVariable Long packageId,
            @Parameter(description = "新老师ID") @RequestParam Long newTeacherId,
            @Parameter(description = "变更原因") @RequestParam(required = false) String changeReason) {
        packageTeacherAssignmentService.switchPrimaryTeacher(packageId, newTeacherId, changeReason);
        return ApiResponse.success("主老师切换成功。", null);
    }
}
