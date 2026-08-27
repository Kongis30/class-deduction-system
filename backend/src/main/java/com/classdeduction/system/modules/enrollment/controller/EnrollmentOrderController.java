package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.dto.RefundRequest;
import com.classdeduction.system.modules.enrollment.service.EnrollmentOrderService;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 报名单控制器：负责报名开单与报名单查询。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/enrollment/orders")
@Tag(name = "Enrollment - Orders")
@RequireRoles({"ADMIN", "STAFF"})
public class EnrollmentOrderController {

    private final EnrollmentOrderService enrollmentOrderService;

    @GetMapping
    @Operation(summary = "分页查询报名单")
    public ResponseEntity<ApiResponse<PageResponse<EnrollmentOrderListItemVO>>> pageOrders(@ParameterObject @Valid EnrollmentOrderPageQuery query) {
        return noStore(ApiResponse.success(enrollmentOrderService.pageOrders(query)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询报名单详情")
    public ResponseEntity<ApiResponse<EnrollmentOrderDetailVO>> getOrder(@PathVariable Long id) {
        return noStore(ApiResponse.success(enrollmentOrderService.getOrder(id)));
    }

    @PostMapping
    @Operation(summary = "创建报名单并生成课时包")
    public ApiResponse<EnrollmentCreateResultVO> createOrder(@Valid @RequestBody EnrollmentCreateRequest request) {
        return ApiResponse.success("Created successfully.", enrollmentOrderService.createOrder(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新报名单")
    public ApiResponse<Void> updateOrder(
            @Parameter(description = "报名单ID") @PathVariable Long id,
            @Valid @RequestBody EnrollmentOrderUpdateRequest request) {
        enrollmentOrderService.updateOrder(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @PutMapping("/{id}/correction")
    @Operation(summary = "更正报名单联系信息（不改动金额与课程）")
    public ApiResponse<Void> correctOrder(
            @Parameter(description = "报名单ID") @PathVariable Long id,
            @Valid @RequestBody EnrollmentOrderCorrectionRequest request) {
        enrollmentOrderService.correctOrder(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消报名单")
    public ApiResponse<Void> cancelOrder(@Parameter(description = "报名单ID") @PathVariable Long id) {
        enrollmentOrderService.cancelOrder(id);
        return ApiResponse.success("Cancelled successfully.", null);
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "报名单退款（部分退款仅更新财务，全额退款同时退课）")
    public ApiResponse<ReceiptRecordVO> refundOrder(
            @Parameter(description = "报名单ID") @PathVariable Long id,
            @Valid @RequestBody RefundRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        request.setHandledByUserId(currentUser.userId());
        return ApiResponse.success("Refunded successfully.", enrollmentOrderService.refundOrder(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除误创建且未付款的报名单（软删除）")
    public ApiResponse<Void> deleteOrder(@Parameter(description = "报名单ID") @PathVariable Long id) {
        enrollmentOrderService.deleteOrder(id);
        return ApiResponse.success("Deleted successfully.", null);
    }

    @PostMapping("/{id}/force-void")
    @Operation(summary = "管理员强制作废报名单（清理历史脏数据，软删除）")
    @RequireRoles({"ADMIN"})
    public ApiResponse<Void> forceVoidOrder(
            @Parameter(description = "报名单ID") @PathVariable Long id,
            @RequestBody(required = false) ForceVoidRequest request) {
        enrollmentOrderService.forceVoidOrder(id, request == null ? null : request.reason());
        return ApiResponse.success("Voided successfully.", null);
    }

    public record ForceVoidRequest(String reason) {
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(body);
    }
}
