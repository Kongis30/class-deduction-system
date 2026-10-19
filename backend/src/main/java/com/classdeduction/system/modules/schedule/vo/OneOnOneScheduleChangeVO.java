package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 排课周期边界；旧规则复用 ID 时，边界必须来自已持久化的周期起点。 */
@Schema(description = "One-on-one schedule change")
public record OneOnOneScheduleChangeVO(Long ruleId, LocalDateTime changedAt, boolean legacySlotBoundary) {
}
