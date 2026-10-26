package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryClassificationRequest;
import com.classdeduction.system.modules.enrollment.service.DataSummaryService;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/data-summary")
@Tag(name = "Enrollment - Data Summary")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class DataSummaryController {

    private final DataSummaryService dataSummaryService;

    @GetMapping
    @Operation(summary = "Query registration data summary")
    public ApiResponse<DataSummaryResponseVO> getSummary(
            @ParameterObject @Valid DataSummaryQuery query,
            @org.springframework.web.bind.annotation.RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE)
            CurrentUserResponse currentUser) {
        DataSummaryResponseVO response = dataSummaryService.getSummary(query);
        if (currentUser.roleCodes().contains("MANAGER")) {
            response = response.withoutFinancialAmounts();
        }
        return ApiResponse.success(response);
    }

    @PostMapping("/classification")
    @Operation(summary = "Move a Data Summary row to or from the discontinued section")
    public ApiResponse<Void> classifyRecord(@Valid @RequestBody DataSummaryClassificationRequest request) {
        dataSummaryService.classifyRecord(request);
        return ApiResponse.success(null);
    }
}
