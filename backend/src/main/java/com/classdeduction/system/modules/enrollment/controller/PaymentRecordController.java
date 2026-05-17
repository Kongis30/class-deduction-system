package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.service.PaymentRecordService;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 支付记录控制器：负责支付记录的查询和创建。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/enrollment/payments")
@Tag(name = "Enrollment - Payments")
@RequireRoles({"ADMIN", "STAFF"})
public class PaymentRecordController {

    private final PaymentRecordService paymentRecordService;

    @GetMapping("/order/{enrollmentOrderId}")
    @Operation(summary = "根据报名订单ID查询支付记录")
    public ApiResponse<List<PaymentRecordVO>> listByEnrollmentOrderId(
            @Parameter(description = "报名订单ID") @PathVariable Long enrollmentOrderId) {
        return ApiResponse.success(paymentRecordService.listByEnrollmentOrderId(enrollmentOrderId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取支付记录详情")
    public ApiResponse<PaymentRecordVO> getPaymentRecord(
            @Parameter(description = "支付记录ID") @PathVariable Long id) {
        return ApiResponse.success(paymentRecordService.getPaymentRecord(id));
    }

    @PostMapping
    @Operation(summary = "创建支付记录（支持组合支付）并更新报名订单")
    public ApiResponse<Void> createPaymentRecord(@Valid @RequestBody PaymentRecordSaveRequest request) {
        paymentRecordService.createPaymentRecord(request);
        return ApiResponse.success("Payment recorded successfully.", null);
    }

    @GetMapping("/package/{packageId}/balance")
    @Operation(summary = "获取课时包余额")
    public ApiResponse<Integer> getPackageBalance(
            @Parameter(description = "课时包ID") @PathVariable Long packageId) {
        return ApiResponse.success(paymentRecordService.getPackageBalance(packageId));
    }
}
