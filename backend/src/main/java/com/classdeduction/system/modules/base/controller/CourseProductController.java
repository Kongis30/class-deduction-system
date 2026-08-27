package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.CourseProductPageQuery;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.service.CourseProductService;
import com.classdeduction.system.modules.base.vo.CourseProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Course product controller.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/course-products")
@Tag(name = "Base Data - Course Products")
@RequireRoles({"ADMIN", "STAFF"})
public class CourseProductController {

    private final CourseProductService courseProductService;

    @GetMapping
    @Operation(summary = "List course products")
    public ApiResponse<PageResponse<CourseProductVO>> pageCourseProducts(@ParameterObject @Valid CourseProductPageQuery query) {
        return ApiResponse.success(courseProductService.pageCourseProducts(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get course product detail")
    public ApiResponse<CourseProductVO> getCourseProduct(@PathVariable Long id) {
        return ApiResponse.success(courseProductService.getCourseProduct(id));
    }

    @PostMapping
    @Operation(summary = "Create a course product")
    public ApiResponse<Long> createCourseProduct(@Valid @RequestBody CourseProductSaveRequest request) {
        return ApiResponse.success("Created successfully.", courseProductService.createCourseProduct(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a course product")
    public ApiResponse<Void> updateCourseProduct(@PathVariable Long id,
                                                 @Valid @RequestBody CourseProductSaveRequest request) {
        courseProductService.updateCourseProduct(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a course product")
    public ApiResponse<Void> deleteCourseProduct(@PathVariable Long id) {
        courseProductService.deleteCourseProduct(id);
        return ApiResponse.success("Deleted successfully.", null);
    }

    @PostMapping("/{id}/force-delete")
    @Operation(summary = "Force delete a course product and its classes (admin, legacy cleanup)")
    @RequireRoles({"ADMIN"})
    public ApiResponse<Void> forceDeleteCourseProduct(@PathVariable Long id,
                                                      @RequestBody(required = false) ForceDeleteRequest request) {
        courseProductService.forceDeleteCourseProduct(id, request == null ? null : request.reason());
        return ApiResponse.success("Deleted successfully.", null);
    }

    public record ForceDeleteRequest(String reason) {
    }
}
