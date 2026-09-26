package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.CourseCategorySaveRequest;
import com.classdeduction.system.modules.base.service.CourseCategoryService;
import com.classdeduction.system.modules.base.vo.CourseCategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Course category controller. The set of categories is fixed; only the display
 * name and display order can be edited.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/course-categories")
@Tag(name = "Base Data - Course Categories")
public class CourseCategoryController {

    private final CourseCategoryService courseCategoryService;

    @GetMapping
    @Operation(summary = "List all course categories")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    public ApiResponse<List<CourseCategoryVO>> listCategories() {
        return ApiResponse.success(courseCategoryService.listAll());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a course category display name and order")
    @RequireRoles({"ADMIN"})
    public ApiResponse<Void> updateCategory(@PathVariable Long id,
                                            @Valid @RequestBody CourseCategorySaveRequest request) {
        courseCategoryService.updateCategory(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }
}
