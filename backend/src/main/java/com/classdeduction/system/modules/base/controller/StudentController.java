package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.StudentPageQuery;
import com.classdeduction.system.modules.base.dto.StudentSaveRequest;
import com.classdeduction.system.modules.base.service.StudentService;
import com.classdeduction.system.modules.base.vo.StudentDetailVO;
import com.classdeduction.system.modules.base.vo.StudentListItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Student profile controller with nested guardian contact management.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/students")
@Tag(name = "Base Data - Students")
@RequireRoles({"ADMIN", "STAFF", "TEACHER"})
public class StudentController {

    private final StudentService studentService;

    @GetMapping("/all")
    @Operation(summary = "List all active students for dropdown")
    public ApiResponse<java.util.List<StudentListItemVO>> listAllActive() {
        return ApiResponse.success(studentService.listAllActive());
    }

    @GetMapping
    @Operation(summary = "List student profiles")
    public ApiResponse<PageResponse<StudentListItemVO>> pageStudents(@ParameterObject @Valid StudentPageQuery query) {
        return ApiResponse.success(studentService.pageStudents(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get student detail")
    public ApiResponse<StudentDetailVO> getStudent(@PathVariable Long id) {
        return ApiResponse.success(studentService.getStudent(id));
    }

    @PostMapping
    @Operation(summary = "Create a student profile")
    public ApiResponse<Long> createStudent(@Valid @RequestBody StudentSaveRequest request) {
        return ApiResponse.success("Created successfully.", studentService.createStudent(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a student profile")
    public ApiResponse<Void> updateStudent(@PathVariable Long id, @Valid @RequestBody StudentSaveRequest request) {
        studentService.updateStudent(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a student profile")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }
}
