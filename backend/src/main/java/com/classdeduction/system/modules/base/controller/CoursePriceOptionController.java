package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionPageQuery;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionSaveRequest;
import com.classdeduction.system.modules.base.service.CoursePriceOptionService;
import com.classdeduction.system.modules.base.vo.CoursePriceOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Course price options for the registration selector.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/course-price-options")
@Tag(name = "Base Data - Course Price Options")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class CoursePriceOptionController {

    private final CoursePriceOptionService coursePriceOptionService;

    @GetMapping("/active")
    @Operation(summary = "List active course price options")
    public ApiResponse<List<CoursePriceOptionVO>> listActiveOptions(
            @Parameter(description = "Optional course type filter") @RequestParam(required = false) String courseType) {
        return ApiResponse.success(coursePriceOptionService.listActiveOptions(courseType));
    }

    @GetMapping
    @Operation(summary = "Page course price options")
    public ApiResponse<PageResponse<CoursePriceOptionVO>> pageOptions(
            @ParameterObject @Valid CoursePriceOptionPageQuery query) {
        return ApiResponse.success(coursePriceOptionService.pageOptions(query));
    }

    @PostMapping
    @Operation(summary = "Create course price option")
    public ApiResponse<Long> createOption(@Valid @RequestBody CoursePriceOptionSaveRequest request) {
        return ApiResponse.success("Course price option created.", coursePriceOptionService.createOption(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update course price option")
    public ApiResponse<Void> updateOption(
            @Parameter(description = "Course price option ID") @PathVariable Long id,
            @Valid @RequestBody CoursePriceOptionSaveRequest request) {
        coursePriceOptionService.updateOption(id, request);
        return ApiResponse.success("Course price option updated.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete course price option")
    public ApiResponse<Void> deleteOption(
            @Parameter(description = "Course price option ID") @PathVariable Long id) {
        coursePriceOptionService.deleteOption(id);
        return ApiResponse.success("Course price option deleted.", null);
    }
}
