package com.classdeduction.system.modules.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.systemconfig.entity.SystemMessageEntity;
import com.classdeduction.system.modules.systemconfig.mapper.SystemMessageMapper;
import com.classdeduction.system.modules.systemconfig.service.SystemMessageService;
import com.classdeduction.system.modules.systemconfig.vo.BirthdayStudentMessageVO;
import com.classdeduction.system.modules.systemconfig.vo.SystemMessageInboxVO;
import com.classdeduction.system.modules.systemconfig.vo.SystemMessageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 管理员生日提醒的生成、去重和个人收件箱读取。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMessageServiceImpl implements SystemMessageService {

    private static final String WEEKLY_BIRTHDAY = "WEEKLY_BIRTHDAY";
    private static final int INBOX_LIMIT = 12;

    private final SystemMessageMapper systemMessageMapper;
    private final StudentMapper studentMapper;
    private final CampusMapper campusMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SystemMessageInboxVO getInbox(Long userId) {
        LocalDate weekStart = currentWeekStart();
        if (systemMessageMapper.selectForWeek(userId, WEEKLY_BIRTHDAY, weekStart) == null) {
            upsertForUser(userId, weekStart, buildBirthdayList(weekStart));
        }
        List<SystemMessageVO> messages = systemMessageMapper.selectRecentForUser(userId, INBOX_LIMIT)
                .stream()
                .map(this::toVO)
                .toList();
        long unreadCount = messages.stream().filter(message -> message.readAt() == null).count();
        return new SystemMessageInboxVO(unreadCount, messages);
    }

    @Override
    @Transactional
    public void markRead(Long messageId, Long userId) {
        systemMessageMapper.markRead(messageId, userId, AppTime.nowLocalDateTime());
    }

    /** 每日刷新当前周同一条消息，补上周内新增或更正的生日资料。 */
    @Override
    @Transactional
    public int refreshCurrentWeekBirthdayMessages() {
        LocalDate weekStart = currentWeekStart();
        List<BirthdayStudentMessageVO> students = buildBirthdayList(weekStart);
        List<UserAccountEntity> admins = userAccountMapper.selectActiveAdmins();
        for (UserAccountEntity admin : admins) {
            upsertForUser(admin.getId(), weekStart, students);
        }
        return admins.size();
    }

    private void upsertForUser(Long userId, LocalDate weekStart, List<BirthdayStudentMessageVO> students) {
        SystemMessageEntity message = new SystemMessageEntity();
        message.setId(IdWorker.getId());
        message.setRecipientUserId(userId);
        message.setMessageType(WEEKLY_BIRTHDAY);
        message.setWeekStartDate(weekStart);
        message.setTitle("Birthdays this week");
        message.setItemCount(students.size());
        message.setStudentsPayload(writeStudents(students));
        message.setUpdatedBy(userId);
        systemMessageMapper.upsertWeeklyMessage(message);
    }

    private List<BirthdayStudentMessageVO> buildBirthdayList(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        List<StudentEntity> candidates = studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getStatus, "ACTIVE")
                .eq(StudentEntity::getIsDeleted, 0)
                .isNotNull(StudentEntity::getBirthDate));
        Set<Long> campusIds = candidates.stream()
                .map(StudentEntity::getCampusId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> campusNames = campusIds.isEmpty()
                ? Map.of()
                : campusMapper.selectBatchIds(campusIds).stream()
                        .collect(Collectors.toMap(CampusEntity::getId, CampusEntity::getCampusName));

        List<BirthdayStudentMessageVO> birthdays = new ArrayList<>();
        for (StudentEntity student : candidates) {
            LocalDate birthday = birthdayInWeek(student.getBirthDate(), weekStart, weekEnd);
            if (birthday == null) continue;
            birthdays.add(new BirthdayStudentMessageVO(
                    String.valueOf(student.getId()),
                    student.getStudentName(),
                    student.getGrade(),
                    campusNames.get(student.getCampusId()),
                    birthday));
        }
        birthdays.sort(Comparator.comparing(BirthdayStudentMessageVO::birthdayDate)
                .thenComparing(BirthdayStudentMessageVO::studentName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(birthdays);
    }

    static LocalDate birthdayInWeek(LocalDate birthDate, LocalDate weekStart, LocalDate weekEnd) {
        MonthDay monthDay = MonthDay.from(birthDate);
        for (int year = weekStart.getYear(); year <= weekEnd.getYear(); year++) {
            LocalDate occurrence = monthDay.equals(MonthDay.of(2, 29)) && !LocalDate.of(year, 1, 1).isLeapYear()
                    ? LocalDate.of(year, 2, 28)
                    : monthDay.atYear(year);
            if (!occurrence.isBefore(weekStart) && !occurrence.isAfter(weekEnd)) return occurrence;
        }
        return null;
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now(AppTime.APP_ZONE_ID).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    private String writeStudents(List<BirthdayStudentMessageVO> students) {
        try {
            return objectMapper.writeValueAsString(students);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize weekly birthday message.", exception);
        }
    }

    private List<BirthdayStudentMessageVO> readStudents(String studentsPayload) {
        try {
            return objectMapper.readValue(studentsPayload, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read weekly birthday message.", exception);
        }
    }

    private SystemMessageVO toVO(SystemMessageEntity message) {
        LocalDate weekStart = message.getWeekStartDate();
        return new SystemMessageVO(
                message.getId(),
                message.getMessageType(),
                message.getTitle(),
                weekStart,
                weekStart.plusDays(6),
                message.getItemCount(),
                readStudents(message.getStudentsPayload()),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
