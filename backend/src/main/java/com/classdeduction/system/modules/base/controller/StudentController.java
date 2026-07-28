package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.StudentPageQuery;
import com.classdeduction.system.modules.base.dto.StudentReuseQuery;
import com.classdeduction.system.modules.base.dto.StudentSaveRequest;
import com.classdeduction.system.modules.base.service.StudentService;
import com.classdeduction.system.modules.base.vo.StudentDetailVO;
import com.classdeduction.system.modules.base.vo.StudentListItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
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
@RequireRoles({"ADMIN", "STAFF"})
public class StudentController {

    private final StudentService studentService;

    @GetMapping("/all")
    @Operation(summary = "List all active students for dropdown")
    public ApiResponse<java.util.List<StudentListItemVO>> listAllActive() {
        return ApiResponse.success(studentService.listAllActive());
    }

    @GetMapping("/deleted")
    @Operation(summary = "List soft-deleted student profiles")
    public ApiResponse<java.util.List<StudentListItemVO>> listDeleted() {
        return ApiResponse.success(studentService.listDeleted());
    }

    @GetMapping
    @Operation(summary = "List student profiles")
    public ApiResponse<PageResponse<StudentListItemVO>> pageStudents(@ParameterObject @Valid StudentPageQuery query) {
        return ApiResponse.success(studentService.pageStudents(query));
    }

    @GetMapping("/reuse")
    @Operation(summary = "Find reusable student by name and guardian phone")
    public ApiResponse<StudentDetailVO> findReusableStudent(@ParameterObject @Valid StudentReuseQuery query) {
        return ApiResponse.success(studentService.findReusableStudent(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get student detail")
    public ResponseEntity<ApiResponse<StudentDetailVO>> getStudent(@PathVariable Long id) {
        return noStore(ApiResponse.success(studentService.getStudent(id)));
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

    private static <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(body);
    }
}
