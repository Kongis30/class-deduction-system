package com.classdeduction.system.modules.auth.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.dto.ManagerAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.ManagerAccountPageQuery;
import com.classdeduction.system.modules.auth.dto.ManagerAccountUpdateRequest;
import com.classdeduction.system.modules.auth.service.ManagerAccountService;
import com.classdeduction.system.modules.auth.vo.ManagerAccountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth/manager-accounts")
@RequireRoles({"ADMIN"})
public class ManagerAccountController {

    private final ManagerAccountService managerAccountService;

    @GetMapping
    public ApiResponse<PageResponse<ManagerAccountVO>> page(@ParameterObject ManagerAccountPageQuery query) {
        return ApiResponse.success(managerAccountService.pageManagerAccounts(query));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody ManagerAccountCreateRequest request) {
        return ApiResponse.success("Manager account created.", managerAccountService.createManagerAccount(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ManagerAccountUpdateRequest request) {
        managerAccountService.updateManagerAccount(id, request);
        return ApiResponse.success("Manager account updated.", null);
    }
}
