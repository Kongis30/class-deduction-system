package com.classdeduction.system.modules.schedule.vo;

import java.time.LocalDateTime;

/** 重排前待处理课次及其状态快照，供界面展示与保存时并发核验。 */
public record SchedulePendingLessonVO(Long occurrenceId, String snapshot, String subjectName,
                                      String teacherName, LocalDateTime plannedStartAt,
                                      LocalDateTime plannedEndAt, Double plannedHours,
                                      String status, boolean inProgress) {}
