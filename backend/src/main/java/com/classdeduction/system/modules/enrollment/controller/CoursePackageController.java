package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageHoursAdjustmentRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageDeductedHoursRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageStartingHoursRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageUpdateRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageRenewalPriorityRequest;
import com.classdeduction.system.modules.enrollment.service.CoursePackageService;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageDetailVO;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassPackageProgressVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassStudentProgressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课时包控制器：负责课时包查询和更新。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/enrollment/packages")
@Tag(name = "Enrollment - Course Packages")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class CoursePackageController {

    private final CoursePackageService coursePackageService;

    @GetMapping
    @Operation(summary = "分页查询课时包")
    public ApiResponse<PageResponse<CoursePackageListItemVO>> pagePackages(@ParameterObject CoursePackagePageQuery query) {
        return ApiResponse.success(coursePackageService.pagePackages(query));
    }

    @GetMapping("/my")
    @RequireRoles({"STUDENT"})
    @Operation(summary = "List current student's own course packages")
    public ApiResponse<PageResponse<CoursePackageListItemVO>> pageMyPackages(
            @ParameterObject CoursePackagePageQuery query,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        if (currentUser.studentId() == null) {
            throw new BusinessException("STUDENT_BINDING_REQUIRED",
                    "Student account is not linked to a student profile.");
        }
        query.setStudentId(currentUser.studentId());
        return ApiResponse.success(coursePackageService.pagePackages(query));
    }

    @GetMapping("/group-classes")
    @Operation(summary = "分页查询班课产品进度")
    public ApiResponse<PageResponse<GroupClassPackageProgressVO>> pageGroupClassPackages(@ParameterObject CoursePackagePageQuery query) {
        return ApiResponse.success(coursePackageService.pageGroupClassPackages(query));
    }

    @GetMapping("/group-classes/{classId}/students")
    @Operation(summary = "查询班课内学生个人进度")
    public ApiResponse<List<GroupClassStudentProgressVO>> listGroupClassStudentProgress(@PathVariable Long classId) {
        return ApiResponse.success(coursePackageService.listGroupClassStudentProgress(classId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询课时包详情")
    public ApiResponse<CoursePackageDetailVO> getPackage(@PathVariable Long id) {
        return ApiResponse.success(coursePackageService.getPackage(id));
    }

    @PutMapping("/{id}/renewal-priority")
    @Operation(summary = "Set a course package's manual renewal priority")
    public ApiResponse<Void> setRenewalPriority(
            @PathVariable Long id,
            @Valid @RequestBody CoursePackageRenewalPriorityRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        coursePackageService.setRenewalPriority(id, request.pinned(), currentUser.userId());
        return ApiResponse.success("Renewal priority updated.", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新课时包")
    public ApiResponse<Void> updatePackage(
            @Parameter(description = "课时包ID") @PathVariable Long id,
            @Valid @RequestBody CoursePackageUpdateRequest request) {
        coursePackageService.updatePackage(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @PostMapping("/{id}/adjust-hours")
    @Operation(summary = "Adjust available package hours without changing order charges")
    public ApiResponse<Void> adjustPackageHours(
            @PathVariable Long id,
            @Valid @RequestBody CoursePackageHoursAdjustmentRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        coursePackageService.adjustPackageHours(id, request, currentUser.userId());
        return ApiResponse.success("Package hours adjusted successfully.", null);
    }

    @PutMapping("/{id}/deducted-hours")
    @Operation(summary = "Update already-deducted 1-on-1 hours and refresh future scheduled lessons")
    public ApiResponse<Void> updateDeductedHours(
            @PathVariable Long id,
            @Valid @RequestBody CoursePackageDeductedHoursRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        coursePackageService.updateDeductedHours(id, request, currentUser.userId());
        return ApiResponse.success("Already deducted hours updated successfully.", null);
    }

    @PutMapping("/{id}/starting-hours")
    @Operation(summary = "Update starting hours using the displayed package version")
    public ApiResponse<Void> updateStartingHours(
            @PathVariable Long id,
            @Valid @RequestBody CoursePackageStartingHoursRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        coursePackageService.updateStartingHours(id, request, currentUser.userId());
        return ApiResponse.success("Starting hours updated successfully.", null);
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause a 1-on-1 package and cancel future unrecorded lessons")
    public ApiResponse<Void> pausePackage(@PathVariable Long id) {
        coursePackageService.pausePackage(id);
        return ApiResponse.success("Package paused successfully.", null);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a paused 1-on-1 package without restoring its previous schedule")
    public ApiResponse<Void> resumePackage(@PathVariable Long id) {
        coursePackageService.resumePackage(id);
        return ApiResponse.success("Package resumed successfully.", null);
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
