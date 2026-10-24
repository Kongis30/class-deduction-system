package com.classdeduction.system.modules.systemconfig.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 系统消息列表项。 */
public record SystemMessageVO(
        Long id,
        String messageType,
        String title,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        Integer itemCount,
        List<BirthdayStudentMessageVO> students,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
}
