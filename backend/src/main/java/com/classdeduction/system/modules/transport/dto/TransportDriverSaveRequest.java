package com.classdeduction.system.modules.transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 司机新增和修改请求。 */
@Data
@Schema(description = "Transport driver save request")
public class TransportDriverSaveRequest {

    @NotBlank(message = "Driver name is required")
    @Size(max = 128, message = "Driver name must be at most 128 characters")
    private String driverName;

    @Min(value = 0, message = "Display order cannot be negative")
    private Integer displayOrder;
}
