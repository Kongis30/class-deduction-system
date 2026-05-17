package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.enrollment.dto.ClassEnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.service.EnrollmentOrderService;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
    public ApiResponse<PageResponse<EnrollmentOrderListItemVO>> pageOrders(@ParameterObject @Valid EnrollmentOrderPageQuery query) {
        return ApiResponse.success(enrollmentOrderService.pageOrders(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询报名单详情")
    public ApiResponse<EnrollmentOrderDetailVO> getOrder(@PathVariable Long id) {
        return ApiResponse.success(enrollmentOrderService.getOrder(id));
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

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消报名单")
    public ApiResponse<Void> cancelOrder(@Parameter(description = "报名单ID") @PathVariable Long id) {
        enrollmentOrderService.cancelOrder(id);
        return ApiResponse.success("Cancelled successfully.", null);
    }

    @PostMapping("/class-enroll")
    @Operation(summary = "创建班课报名订单（同时创建 class_enrollment 记录）")
    public ApiResponse<Void> createClassEnrollmentOrder(@Valid @RequestBody ClassEnrollmentCreateRequest request) {
        enrollmentOrderService.createClassEnrollmentOrder(request);
        return ApiResponse.success("Class enrollment created successfully.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除报名单（软删除）")
    public ApiResponse<Void> deleteOrder(@Parameter(description = "报名单ID") @PathVariable Long id) {
        enrollmentOrderService.deleteOrder(id);
        return ApiResponse.success("Deleted successfully.", null);
    }
}
