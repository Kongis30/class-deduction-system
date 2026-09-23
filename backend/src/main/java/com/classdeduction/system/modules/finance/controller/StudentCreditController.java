package com.classdeduction.system.modules.finance.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.finance.dto.StudentCreditAdjustmentRequest;
import com.classdeduction.system.modules.finance.service.StudentCreditService;
import com.classdeduction.system.modules.finance.vo.StudentCreditBalanceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestAttribute;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/student-credits")
@Tag(name = "Finance - Student Credit")
@RequireRoles({"ADMIN", "STAFF"})
public class StudentCreditController {

    private final StudentCreditService studentCreditService;

    @GetMapping
    @Operation(summary = "List students with available Credit")
    public ApiResponse<List<StudentCreditBalanceVO>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(studentCreditService.listBalances(keyword));
    }

    @GetMapping("/student/{studentId}/balance")
    @Operation(summary = "Get one student's Credit balance")
    public ApiResponse<BigDecimal> balance(@PathVariable Long studentId) {
        return ApiResponse.success(studentCreditService.getBalance(studentId));
    }

    @PostMapping("/student/{studentId}/adjust")
    @Operation(summary = "Adjust one student's Credit balance")
    public ApiResponse<Void> adjust(
            @PathVariable Long studentId,
            @Valid @RequestBody StudentCreditAdjustmentRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        studentCreditService.adjustBalance(studentId, request.getCreditBalance(),
                currentUser.userId(), currentUser.displayName(), request.getRemark());
        return ApiResponse.success("Credit balance updated successfully.", null);
    }
}
