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
import com.classdeduction.system.modules.enrollment.vo.RefundCoursePreviewVO;
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
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
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
    public ApiResponse<EnrollmentCreateResultVO> createOrder(
            @Valid @RequestBody EnrollmentCreateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        if (currentUser.roleCodes().contains("MANAGER")) {
            request.setHandledByName(currentUser.displayName());
            request.setCashierUserId(currentUser.userId());
        }
        return ApiResponse.success("Created successfully.", enrollmentOrderService.createOrder(request, currentUser));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新报名单")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> updateOrder(
            @Parameter(description = "报名单ID") @PathVariable Long id,
            @Valid @RequestBody EnrollmentOrderUpdateRequest request) {
        enrollmentOrderService.updateOrder(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @PutMapping("/{id}/correction")
    @Operation(summary = "编辑已保存收据并同步报名下游数据")
    @RequireRoles({"ADMIN", "STAFF"})
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
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ReceiptRecordVO> refundOrder(
            @Parameter(description = "报名单ID") @PathVariable Long id,
            @Valid @RequestBody RefundRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        request.setHandledByUserId(currentUser.userId());
        return ApiResponse.success("Refunded successfully.", enrollmentOrderService.refundOrder(id, request));
    }

    @GetMapping("/{id}/refund-preview")
    @Operation(summary = "查询退款涉及课程的已消耗课时")
    public ResponseEntity<ApiResponse<RefundCoursePreviewVO>> previewRefund(
            @Parameter(description = "报名单ID") @PathVariable Long id,
            @RequestParam(required = false) Integer participantIndex) {
        return noStore(ApiResponse.success(enrollmentOrderService.previewRefund(id, participantIndex)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "整单删除（须先退款至净收款为零）")
    public ApiResponse<Void> deleteOrder(@Parameter(description = "报名单ID") @PathVariable Long id) {
        enrollmentOrderService.deleteOrder(id);
        return ApiResponse.success("Deleted successfully.", null);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "删除单门报名课程并清理其个人排课，保留同单其他课程")
    public ApiResponse<Void> deleteOrderItem(@PathVariable Long id, @PathVariable Long itemId) {
        enrollmentOrderService.deleteOrderItem(id, itemId);
        return ApiResponse.success("Course deleted successfully.", null);
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
