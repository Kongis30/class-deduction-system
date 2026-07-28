package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.service.RegistrationIntakeService;
import com.classdeduction.system.modules.enrollment.vo.RegistrationIntakeResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration intake endpoint for the new form-first enrollment flow.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/registration")
@Tag(name = "Registration - Intake")
@RequireRoles({"ADMIN", "STAFF"})
public class RegistrationIntakeController {

    private final RegistrationIntakeService registrationIntakeService;

    @PostMapping("/intake")
    @Operation(summary = "Submit a full registration intake")
    public ApiResponse<RegistrationIntakeResultVO> submitIntake(
            @Valid @RequestBody RegistrationIntakeRequest request) {
        return ApiResponse.success("Registration submitted successfully.", registrationIntakeService.submitIntake(request));
    }
}
