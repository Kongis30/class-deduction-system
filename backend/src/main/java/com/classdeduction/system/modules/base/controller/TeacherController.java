package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.TeacherPageQuery;
import com.classdeduction.system.modules.base.dto.TeacherSaveRequest;
import com.classdeduction.system.modules.base.service.TeacherService;
import com.classdeduction.system.modules.base.vo.TeacherVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Teacher profile controller.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/teachers")
@Tag(name = "Base Data - Instructors")
@RequireRoles({"ADMIN", "STAFF"})
public class TeacherController {

    private final TeacherService teacherService;

    @GetMapping("/active")
    @Operation(summary = "List all active instructors")
    public ApiResponse<List<TeacherVO>> listAllActive() {
        return ApiResponse.success(teacherService.listAllActive());
    }

    @GetMapping("/deleted")
    @Operation(summary = "List soft-deleted instructor profiles")
    public ApiResponse<List<TeacherVO>> listDeleted() {
        return ApiResponse.success(teacherService.listDeleted());
    }

    @GetMapping
    @Operation(summary = "List instructor profiles")
    public ApiResponse<PageResponse<TeacherVO>> pageTeachers(@ParameterObject @Valid TeacherPageQuery query) {
        return ApiResponse.success(teacherService.pageTeachers(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get instructor detail")
    public ApiResponse<TeacherVO> getTeacher(@PathVariable Long id) {
        return ApiResponse.success(teacherService.getTeacher(id));
    }

    @PostMapping
    @Operation(summary = "Create an instructor profile")
    public ApiResponse<Long> createTeacher(@Valid @RequestBody TeacherSaveRequest request) {
        return ApiResponse.success("Created successfully.", teacherService.createTeacher(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an instructor profile")
    public ApiResponse<Void> updateTeacher(@PathVariable Long id, @Valid @RequestBody TeacherSaveRequest request) {
        teacherService.updateTeacher(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an instructor profile")
    public ApiResponse<Void> deleteTeacher(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
        return ApiResponse.success("Deleted successfully.", null);
    }
}
