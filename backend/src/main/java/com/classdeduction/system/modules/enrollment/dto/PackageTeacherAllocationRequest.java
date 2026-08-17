package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Course package teacher-hours allocation request")
public class PackageTeacherAllocationRequest {

    @NotEmpty(message = "At least one teacher allocation is required")
    @Valid
    private List<AllocationItem> allocations;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    @Schema(description = "Optional allocation adjustment note")
    private String reason;

    @Data
    public static class AllocationItem {

        @NotNull(message = "Teacher is required")
        private Long teacherId;

        @NotNull(message = "Allocated hours are required")
        @DecimalMin(value = "0.5", message = "Allocated hours must be at least 0.5")
        @Schema(description = "Hours allocated to this teacher in 0.5-hour increments", example = "30.0")
        private BigDecimal allocatedHours;

        @Schema(description = "Primary teacher flag")
        private Boolean primary;
    }
}
