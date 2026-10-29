package com.classdeduction.system.modules.schedule.vo;

import java.time.LocalDateTime;

/** 管理员选择继承目标时需要辨认的未关联历史课次信息。 */
public record UnlinkedLessonHistoryVO(Long occurrenceId, String subjectName, String teacherName,
                                     LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                                     String resultStatus) {}
