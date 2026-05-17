package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.SubjectSaveRequest;
import com.classdeduction.system.modules.base.service.SubjectService;
import com.classdeduction.system.modules.base.vo.SubjectVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Subject controller.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/subjects")
@Tag(name = "Base Data - Subjects")
@RequireRoles({"ADMIN", "STAFF"})
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    @Operation(summary = "List all active subjects")
    public ApiResponse<List<SubjectVO>> listSubjects() {
        return ApiResponse.success(subjectService.listAllActive());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subject detail")
    public ApiResponse<SubjectVO> getSubject(@PathVariable Long id) {
        return ApiResponse.success(subjectService.getSubject(id));
    }

    @PostMapping
    @Operation(summary = "Create a subject")
    public ApiResponse<Long> createSubject(@Valid @RequestBody SubjectSaveRequest request) {
        return ApiResponse.success("Created successfully.", subjectService.createSubject(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a subject")
    public ApiResponse<Void> updateSubject(@PathVariable Long id,
                                           @Valid @RequestBody SubjectSaveRequest request) {
        subjectService.updateSubject(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a subject")
    public ApiResponse<Void> deleteSubject(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return ApiResponse.success("Deleted successfully.", null);
    }
}
