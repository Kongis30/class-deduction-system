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

        @Size(max = 128, message = "Subject must not exceed 128 characters")
        @Schema(description = "Subject assigned to this teacher allocation", example = "Chemistry")
        private String subjectName;

        @NotNull(message = "Allocated hours are required")
        @DecimalMin(value = "0.5", message = "Allocated hours must be at least 0.5")
        @Schema(description = "Hours allocated to this teacher in 0.5-hour increments", example = "30.0")
        private BigDecimal allocatedHours;

        @DecimalMin(value = "0.01", message = "Hourly rate must be greater than 0")
        @Schema(description = "Teacher hourly pay-rate snapshot for this allocation", example = "25.00")
        private BigDecimal hourlyRateSnapshot;

        @Schema(description = "Primary teacher flag")
        private Boolean primary;
    }
}
