package com.classdeduction.system.modules.auth.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.dto.StudentAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.StudentAccountPageQuery;
import com.classdeduction.system.modules.auth.dto.StudentAccountUpdateRequest;
import com.classdeduction.system.modules.auth.service.StudentAccountService;
import com.classdeduction.system.modules.auth.vo.StudentAccountVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 学生账号控制器：供内部人员管理学生登录账号和学生档案绑定关系。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth/student-accounts")
@Tag(name = "Auth - Student Accounts")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class StudentAccountController {

    private final StudentAccountService studentAccountService;

    @GetMapping
    @Operation(summary = "Page student accounts")
    public ApiResponse<PageResponse<StudentAccountVO>> pageStudentAccounts(@ParameterObject StudentAccountPageQuery query) {
        return ApiResponse.success(studentAccountService.pageStudentAccounts(query));
    }

    @PostMapping
    @Operation(summary = "Create a student login account linked to one student profile")
    public ApiResponse<Long> createStudentAccount(@Valid @RequestBody StudentAccountCreateRequest request) {
        return ApiResponse.success("Created successfully.", studentAccountService.createStudentAccount(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a student account")
    public ApiResponse<Void> updateStudentAccount(@PathVariable Long id,
                                                  @Valid @RequestBody StudentAccountUpdateRequest request) {
        studentAccountService.updateStudentAccount(id, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/deleted")
    @Operation(summary = "List soft-deleted student accounts (with their soft-deleted student profile)")
    public ApiResponse<java.util.List<StudentAccountVO>> listDeletedStudentAccounts() {
        return ApiResponse.success(studentAccountService.listDeletedStudentAccounts());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a student account and cascade soft-delete its linked student profile")
    public ApiResponse<Void> deleteStudentAccount(@PathVariable Long id) {
        studentAccountService.deleteStudentAccount(id);
        return ApiResponse.success(null);
    }
}
