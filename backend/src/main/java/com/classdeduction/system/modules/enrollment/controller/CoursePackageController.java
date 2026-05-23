package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageUpdateRequest;
import com.classdeduction.system.modules.enrollment.service.CoursePackageService;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageDetailVO;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 课时包控制器：负责课时包查询和更新。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/enrollment/packages")
@Tag(name = "Enrollment - Course Packages")
@RequireRoles({"ADMIN", "STAFF", "TEACHER"})
public class CoursePackageController {

    private final CoursePackageService coursePackageService;

    @GetMapping
    @Operation(summary = "分页查询课时包")
    public ApiResponse<PageResponse<CoursePackageListItemVO>> pagePackages(@ParameterObject CoursePackagePageQuery query) {
        return ApiResponse.success(coursePackageService.pagePackages(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询课时包详情")
    public ApiResponse<CoursePackageDetailVO> getPackage(@PathVariable Long id) {
        return ApiResponse.success(coursePackageService.getPackage(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新课时包")
    public ApiResponse<Void> updatePackage(
            @Parameter(description = "课时包ID") @PathVariable Long id,
            @Valid @RequestBody CoursePackageUpdateRequest request) {
        coursePackageService.updatePackage(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "获取课时包余额")
    public ApiResponse<Integer> getPackageBalance(@Parameter(description = "课时包ID") @PathVariable Long id) {
        return ApiResponse.success(coursePackageService.getPackageBalance(id));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "关闭课时包")
    public ApiResponse<Void> closePackage(@Parameter(description = "课时包ID") @PathVariable Long id) {
        coursePackageService.closePackage(id);
        return ApiResponse.success("Package closed successfully.", null);
    }
}
