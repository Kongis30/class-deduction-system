package com.classdeduction.system.modules.transport.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.transport.dto.TransportDriverAssignmentSaveRequest;
import com.classdeduction.system.modules.transport.dto.TransportDriverSaveRequest;
import com.classdeduction.system.modules.transport.service.TransportDriverService;
import com.classdeduction.system.modules.transport.vo.TransportDriverAssignmentVO;
import com.classdeduction.system.modules.transport.vo.TransportDriverVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 接送名单司机维护和课程学生司机设置接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/transport")
@Tag(name = "Transport")
@RequireRoles({"ADMIN", "STAFF"})
public class TransportDriverController {

    private final TransportDriverService transportDriverService;

    @GetMapping("/drivers")
    @Operation(summary = "List active transport drivers")
    public ApiResponse<List<TransportDriverVO>> listDrivers() {
        return ApiResponse.success(transportDriverService.listDrivers());
    }

    @PostMapping("/drivers")
    @Operation(summary = "Create a transport driver")
    public ApiResponse<Long> createDriver(@Valid @RequestBody TransportDriverSaveRequest request) {
        return ApiResponse.success("Created successfully.", transportDriverService.createDriver(request));
    }

    @PutMapping("/drivers/{id}")
    @Operation(summary = "Update a transport driver")
    public ApiResponse<Void> updateDriver(@PathVariable Long id,
                                          @Valid @RequestBody TransportDriverSaveRequest request) {
        transportDriverService.updateDriver(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @DeleteMapping("/drivers/{id}")
    @Operation(summary = "Delete a transport driver")
    public ApiResponse<Void> deleteDriver(@PathVariable Long id) {
        transportDriverService.deleteDriver(id);
        return ApiResponse.success("Deleted successfully.", null);
    }

    @GetMapping("/assignments")
    @Operation(summary = "List transport driver assignments")
    public ApiResponse<List<TransportDriverAssignmentVO>> listAssignments() {
        return ApiResponse.success(transportDriverService.listAssignments());
    }

    @PutMapping("/assignments")
    @Operation(summary = "Set a course student's transport driver")
    public ApiResponse<Void> saveAssignment(@Valid @RequestBody TransportDriverAssignmentSaveRequest request) {
        transportDriverService.saveAssignment(request);
        return ApiResponse.success("Assignment saved.", null);
    }
}
