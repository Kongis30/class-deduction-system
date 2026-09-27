package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Registration intake endpoint for the new form-first enrollment flow.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/registration")
@Tag(name = "Registration - Intake")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class RegistrationIntakeController {

    private final RegistrationIntakeService registrationIntakeService;

    @PostMapping("/intake")
    @Operation(summary = "Submit a full registration intake")
    public ApiResponse<RegistrationIntakeResultVO> submitIntake(
            @Valid @RequestBody RegistrationIntakeRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        if (currentUser.roleCodes().contains("MANAGER")) {
            // 管理者报名的经手人及收银员由登录账号确定，忽略客户端传入值。
            applyManagerHandler(request.getPayments(), currentUser.displayName(), currentUser.userId());
            if (request.getParticipantBillings() != null) {
                request.getParticipantBillings().forEach(billing ->
                        applyManagerHandler(billing.getPayments(), currentUser.displayName(), currentUser.userId()));
            }
            request.setHandledByName(currentUser.displayName());
            request.setCashierUserId(currentUser.userId());
        }
        return ApiResponse.success("Registration submitted successfully.",
                registrationIntakeService.submitIntake(request, currentUser));
    }

    private void applyManagerHandler(List<RegistrationIntakeRequest.PaymentPayload> payments,
                                     String displayName,
                                     Long userId) {
        if (payments == null) return;
        payments.forEach(payment -> {
            payment.setHandledByName(displayName);
            payment.setHandledByUserId(userId);
        });
    }
}
