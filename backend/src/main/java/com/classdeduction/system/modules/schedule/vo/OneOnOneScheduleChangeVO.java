package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 排课周期边界；兼容旧规则复用 ID 时，可由新课位快照推断边界。 */
@Schema(description = "One-on-one schedule change")
public record OneOnOneScheduleChangeVO(Long ruleId, LocalDateTime changedAt, boolean legacySlotBoundary) {
}
