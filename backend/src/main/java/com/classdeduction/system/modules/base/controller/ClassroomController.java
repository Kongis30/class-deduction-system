package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.ClassroomPageQuery;
import com.classdeduction.system.modules.base.dto.ClassroomSaveRequest;
import com.classdeduction.system.modules.base.service.ClassroomService;
import com.classdeduction.system.modules.base.vo.ClassroomVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Classroom management controller.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/classrooms")
@Tag(name = "Base Data - Classrooms")
@RequireRoles({"ADMIN", "STAFF"})
public class ClassroomController {

    private final ClassroomService classroomService;

    @GetMapping("/active")
    @Operation(summary = "List all active classrooms")
    public ApiResponse<List<ClassroomVO>> listActiveClassrooms() {
        return ApiResponse.success(classroomService.listAllActive());
    }

    @GetMapping
    @Operation(summary = "List classrooms")
    public ApiResponse<PageResponse<ClassroomVO>> pageClassrooms(@ParameterObject @Valid ClassroomPageQuery query) {
        return ApiResponse.success(classroomService.pageClassrooms(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get classroom detail")
    public ApiResponse<ClassroomVO> getClassroom(@PathVariable Long id) {
        return ApiResponse.success(classroomService.getClassroom(id));
    }

    @PostMapping
    @Operation(summary = "Create a classroom")
    public ApiResponse<Long> createClassroom(@Valid @RequestBody ClassroomSaveRequest request) {
        return ApiResponse.success("Created successfully.", classroomService.createClassroom(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a classroom")
    public ApiResponse<Void> updateClassroom(@PathVariable Long id, @Valid @RequestBody ClassroomSaveRequest request) {
        classroomService.updateClassroom(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a classroom")
    public ApiResponse<Void> deleteClassroom(@PathVariable Long id) {
        classroomService.deleteClassroom(id);
        return ApiResponse.success("Deleted successfully.", null);
    }
}
