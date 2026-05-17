package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.dto.PaymentMethodSaveRequest;
import com.classdeduction.system.modules.enrollment.service.PaymentMethodService;
import com.classdeduction.system.modules.enrollment.vo.PaymentMethodVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 支付方式控制器：提供支付方式的 CRUD 操作。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/enrollment/payment-methods")
@Tag(name = "Enrollment - Payment Methods")
@RequireRoles({"ADMIN", "STAFF"})
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @GetMapping
    @Operation(summary = "获取所有支付方式（含未激活）")
    public ApiResponse<List<PaymentMethodVO>> listAll() {
        return ApiResponse.success(paymentMethodService.listAllMethods());
    }

    @GetMapping("/active")
    @Operation(summary = "获取所有激活的支付方式")
    public ApiResponse<List<PaymentMethodVO>> listActive() {
        return ApiResponse.success(paymentMethodService.listActiveMethods());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取支付方式详情")
    public ApiResponse<PaymentMethodVO> getMethod(
            @Parameter(description = "支付方式ID") @PathVariable Long id) {
        return ApiResponse.success(paymentMethodService.getMethod(id));
    }

    @PostMapping
    @Operation(summary = "创建支付方式")
    public ApiResponse<Long> createMethod(@Valid @RequestBody PaymentMethodSaveRequest request) {
        return ApiResponse.success("Payment method created.", paymentMethodService.createMethod(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新支付方式")
    public ApiResponse<Void> updateMethod(
            @Parameter(description = "支付方式ID") @PathVariable Long id,
            @Valid @RequestBody PaymentMethodSaveRequest request) {
        paymentMethodService.updateMethod(id, request);
        return ApiResponse.success("Payment method updated.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除支付方式")
    public ApiResponse<Void> deleteMethod(
            @Parameter(description = "支付方式ID") @PathVariable Long id) {
        paymentMethodService.deleteMethod(id);
        return ApiResponse.success("Payment method deleted.", null);
    }
}
