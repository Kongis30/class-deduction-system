package com.classdeduction.system.modules.base.dto;

import jakarta.validation.constraints.NotBlank;

/** 只允许用户确认刚刚看到的归档影响快照。 */
public record CourseProductArchiveRequest(@NotBlank String previewFingerprint) {
}
