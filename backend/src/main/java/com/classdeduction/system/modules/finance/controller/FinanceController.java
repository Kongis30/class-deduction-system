package com.classdeduction.system.modules.finance.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.finance.dto.TeacherPayRateSaveRequest;
import com.classdeduction.system.modules.finance.dto.TeacherPaymentCreateRequest;
import com.classdeduction.system.modules.finance.dto.UnpaidLessonQuery;
import com.classdeduction.system.modules.finance.service.TeacherPayRateService;
import com.classdeduction.system.modules.finance.service.TeacherPaymentService;
import com.classdeduction.system.modules.finance.vo.TeacherPayRateVO;
import com.classdeduction.system.modules.finance.vo.TeacherPaymentVO;
import com.classdeduction.system.modules.finance.vo.UnpaidLessonVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/finance")
@Tag(name = "Finance - Teacher Payment")
public class FinanceController {

    private final TeacherPayRateService payRateService;
    private final TeacherPaymentService paymentService;

    // ─── 课时单价管理 ───────────────────────────────

    @GetMapping("/pay-rates")
    @Operation(summary = "List teacher pay rates")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    public ApiResponse<List<TeacherPayRateVO>> listPayRates(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long courseProductId) {
        return ApiResponse.success(payRateService.listPayRates(teacherId, courseProductId));
    }

    @PostMapping("/pay-rates")
    @Operation(summary = "Create or update a pay rate")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    public ApiResponse<Long> savePayRate(@Valid @RequestBody TeacherPayRateSaveRequest request) {
        return ApiResponse.success("Pay rate saved.", payRateService.savePayRate(request));
    }

    @DeleteMapping("/pay-rates/{id}")
    @Operation(summary = "Delete a pay rate")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    public ApiResponse<String> deletePayRate(@PathVariable Long id) {
        payRateService.deletePayRate(id);
        return ApiResponse.success("Pay rate deleted.");
    }

    // ─── 结算管理 ───────────────────────────────────

    @GetMapping("/payments/unpaid-lessons")
    @Operation(summary = "Query unpaid lessons for a teacher in a period")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    public ApiResponse<List<UnpaidLessonVO>> queryUnpaidLessons(@Valid UnpaidLessonQuery query) {
        return ApiResponse.success(paymentService.queryUnpaidLessons(query));
    }

    @PostMapping("/payments")
    @Operation(summary = "Create a payment (settle selected lesson records)")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Long> createPayment(
            @Valid @RequestBody TeacherPaymentCreateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        Long userId = currentUser != null ? currentUser.userId() : null;
        return ApiResponse.success("Payment created.", paymentService.createPayment(request, userId));
    }

    @GetMapping("/payments")
    @Operation(summary = "List payment history")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    public ApiResponse<PageResponse<TeacherPaymentVO>> pagePayments(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(paymentService.pagePayments(teacherId, status, pageNum, pageSize));
    }

    @GetMapping("/payments/{id}")
    @Operation(summary = "Get payment detail with items")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    public ApiResponse<TeacherPaymentVO> getPaymentDetail(@PathVariable Long id) {
        return ApiResponse.success(paymentService.getPaymentDetail(id));
    }

    @PostMapping("/payments/{id}/confirm")
    @Operation(summary = "Confirm a payment (PENDING → PAID)")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<String> confirmPayment(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        Long userId = currentUser != null ? currentUser.userId() : null;
        paymentService.confirmPayment(id, userId);
        return ApiResponse.success("Payment confirmed.");
    }

    @GetMapping("/my-payroll")
    @Operation(summary = "List my payroll history")
    @RequireRoles({"TEACHER"})
    public ApiResponse<PageResponse<TeacherPaymentVO>> pageMyPayroll(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        Long teacherId = currentUser != null ? currentUser.teacherId() : null;
        return ApiResponse.success(paymentService.pagePaymentsForTeacher(teacherId, status, pageNum, pageSize));
    }

    @GetMapping("/my-payroll/{id}")
    @Operation(summary = "Get my payroll detail")
    @RequireRoles({"TEACHER"})
    public ApiResponse<TeacherPaymentVO> getMyPayrollDetail(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser) {
        Long teacherId = currentUser != null ? currentUser.teacherId() : null;
        return ApiResponse.success(paymentService.getPaymentDetailForTeacher(id, teacherId));
    }
}
