package com.classdeduction.system.modules.schedule;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/dashboard")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.success(dashboardService.getSummary());
    }
}
