package com.classdeduction.system.modules.transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 保存课程学生司机设置；driverId 为空表示清除当前设置。 */
@Data
@Schema(description = "Transport driver assignment request")
public class TransportDriverAssignmentSaveRequest {

    @NotNull
    private Long orderId;

    private Long orderItemId;

    @NotNull
    private Long studentId;

    private Integer participantIndex;

    /** 接送方向；旧客户端未传时按 PICKUP 兼容处理。 */
    private String transportType;

    private Long driverId;
}
