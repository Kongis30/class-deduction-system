package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.dto.ReceiptPageQuery;
import com.classdeduction.system.modules.enrollment.service.ReceiptService;
import com.classdeduction.system.modules.enrollment.service.EnrollmentOrderService;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收据控制器：负责收据查询。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/enrollment/receipts")
@Tag(name = "Enrollment - Receipts")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class ReceiptController {

    private final ReceiptService receiptService;
    private final EnrollmentOrderService enrollmentOrderService;

    @GetMapping
    @Operation(summary = "分页查询收据")
    public ApiResponse<PageResponse<ReceiptRecordVO>> pageReceipts(@ParameterObject ReceiptPageQuery query) {
        return ApiResponse.success(receiptService.pageReceipts(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询收据详情")
    public ApiResponse<ReceiptRecordVO> getReceipt(@PathVariable Long id) {
        return ApiResponse.success(receiptService.getReceipt(id));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    @Operation(summary = "撤销退款收据对应的退款操作")
    public ApiResponse<Void> deleteRefundReceipt(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        enrollmentOrderService.deleteRefundReceipt(id, currentUser);
        return ApiResponse.success("Refund receipt deleted and the refund was undone.", null);
    }
}
