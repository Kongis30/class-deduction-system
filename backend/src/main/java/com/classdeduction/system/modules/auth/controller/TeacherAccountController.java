package com.classdeduction.system.modules.auth.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.dto.TeacherAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.TeacherAccountPageQuery;
import com.classdeduction.system.modules.auth.dto.TeacherAccountUpdateRequest;
import com.classdeduction.system.modules.auth.service.TeacherAccountService;
import com.classdeduction.system.modules.auth.vo.TeacherAccountVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 教师账号控制器：供内部人员管理老师登录账号和老师档案绑定关系。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth/teacher-accounts")
@Tag(name = "Auth - Teacher Accounts")
@RequireRoles({"ADMIN", "STAFF"})
public class TeacherAccountController {

    private final TeacherAccountService teacherAccountService;

    @GetMapping
    @Operation(summary = "Page teacher accounts")
    public ApiResponse<PageResponse<TeacherAccountVO>> pageTeacherAccounts(@ParameterObject TeacherAccountPageQuery query) {
        return ApiResponse.success(teacherAccountService.pageTeacherAccounts(query));
    }

    @PostMapping
    @Operation(summary = "Create a teacher login account linked to one teacher profile")
    public ApiResponse<Long> createTeacherAccount(@Valid @RequestBody TeacherAccountCreateRequest request) {
        return ApiResponse.success("Created successfully.", teacherAccountService.createTeacherAccount(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a teacher account")
    public ApiResponse<Void> updateTeacherAccount(@PathVariable Long id,
                                                  @Valid @RequestBody TeacherAccountUpdateRequest request) {
        teacherAccountService.updateTeacherAccount(id, request);
        return ApiResponse.success("Updated successfully.");
    }
}
