package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.dto.DailyRegistrationQuery;
import com.classdeduction.system.modules.enrollment.service.DailyRegistrationService;
import com.classdeduction.system.modules.enrollment.vo.DailyRegistrationResponseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/daily-registrations")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class DailyRegistrationController {

    private final DailyRegistrationService dailyRegistrationService;

    @GetMapping
    public ApiResponse<DailyRegistrationResponseVO> query(@ParameterObject @Valid DailyRegistrationQuery query) {
        return ApiResponse.success(dailyRegistrationService.query(query));
    }
}
