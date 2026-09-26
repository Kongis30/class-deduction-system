package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.service.PaymentRecordService;
import com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class PaymentRecordController {

    private final PaymentRecordService paymentRecordService;

    @GetMapping("/order/{enrollmentOrderId}")
    @Operation(summary = "根据报名订单ID查询支付记录")
    public ResponseEntity<ApiResponse<List<PaymentRecordVO>>> listByEnrollmentOrderId(
            @Parameter(description = "报名订单ID") @PathVariable Long enrollmentOrderId) {
        return noStore(ApiResponse.success(paymentRecordService.listByEnrollmentOrderId(enrollmentOrderId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取支付记录详情")
    public ResponseEntity<ApiResponse<PaymentRecordVO>> getPaymentRecord(
            @Parameter(description = "支付记录ID") @PathVariable Long id) {
        return noStore(ApiResponse.success(paymentRecordService.getPaymentRecord(id)));
    }

    @PostMapping
    @Operation(summary = "创建支付记录（支持组合支付）、更新报名订单并生成付款收据")
    public ApiResponse<ReceiptRecordVO> createPaymentRecord(
            @Valid @RequestBody PaymentRecordSaveRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        request.setHandledByUserId(currentUser.userId());
        if (currentUser.roleCodes().contains("MANAGER")) {
            request.setHandledByName(currentUser.displayName());
            request.getSplits().forEach(split -> split.setHandledByName(currentUser.displayName()));
        }
        return ApiResponse.success("Payment recorded successfully.", paymentRecordService.createPaymentRecord(request));
    }

    @PostMapping("/order/{enrollmentOrderId}/allocation-preview")
    @Operation(summary = "预览付款在各课程项上的分摊结果（平均分摊、封顶顺延，不落库）")
    public ApiResponse<List<PaymentAllocationService.AllocationPreview>> previewAllocation(
            @Parameter(description = "报名订单ID") @PathVariable Long enrollmentOrderId,
            @Valid @RequestBody AllocationPreviewRequest request) {
        return ApiResponse.success(paymentRecordService.previewAllocation(enrollmentOrderId, request.getAmount()));
    }

    @GetMapping("/package/{packageId}/balance")
    @Operation(summary = "获取课时包余额")
    public ApiResponse<Integer> getPackageBalance(
            @Parameter(description = "课时包ID") @PathVariable Long packageId) {
        return ApiResponse.success(paymentRecordService.getPackageBalance(packageId));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(body);
    }

    /**
     * 分摊预览请求体。
     */
    @Data
    public static class AllocationPreviewRequest {

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Payment amount must be greater than 0")
        @Digits(integer = 12, fraction = 2, message = "Payment amount must use at most 2 decimal places")
        @Schema(description = "本次付款总额", example = "5000.00")
        private java.math.BigDecimal amount;
    }
}
