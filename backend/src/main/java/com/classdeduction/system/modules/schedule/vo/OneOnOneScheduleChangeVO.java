package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 排课版本切换事件；消课列表据此标出新旧课表的时间边界。 */
@Schema(description = "One-on-one schedule change")
public record OneOnOneScheduleChangeVO(Long ruleId, LocalDateTime changedAt) {
}
