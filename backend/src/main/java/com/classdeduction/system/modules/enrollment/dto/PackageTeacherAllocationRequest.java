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

/**
 * Historical payload retained for controlled migration only.
 *
 * <p>There is no active write endpoint for this request. Current 1-on-1
 * teacher and hour allocation is defined by recurring schedule slots.</p>
 */
@Data
@Schema(description = "Legacy course package teacher-hours allocation request")
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

        @Schema(description = "Primary teacher flag")
        private Boolean primary;
    }
}
