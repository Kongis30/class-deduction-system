package com.classdeduction.system.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RefundUndoCandidatesQuery {
    @NotNull
    private Long orderId;
    @NotNull
    private Long orderItemId;
    @NotNull
    private Long studentId;
    @NotNull
    @Positive
    private Integer participantIndex;
}
