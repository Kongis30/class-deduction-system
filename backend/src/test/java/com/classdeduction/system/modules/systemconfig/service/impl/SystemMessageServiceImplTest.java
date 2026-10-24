package com.classdeduction.system.modules.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.systemconfig.entity.SystemMessageEntity;
import com.classdeduction.system.modules.systemconfig.mapper.SystemMessageMapper;
import com.classdeduction.system.modules.systemconfig.vo.BirthdayStudentMessageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemMessageServiceImplTest {

    @Mock private SystemMessageMapper systemMessageMapper;
    @Mock private StudentMapper studentMapper;
    @Mock private CampusMapper campusMapper;
    @Mock private UserAccountMapper userAccountMapper;

    private SystemMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemMessageServiceImpl(systemMessageMapper, studentMapper, campusMapper,
                userAccountMapper, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void refreshCreatesOneSortedWeeklyMessageForEachActiveAdministrator() {
        LocalDate weekStart = LocalDate.now(AppTime.APP_ZONE_ID)
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        UserAccountEntity firstAdmin = admin(101L);
        UserAccountEntity secondAdmin = admin(102L);
        StudentEntity sunday = student(1L, "Ava Lin", weekStart.plusDays(6), 10L);
        StudentEntity monday = student(2L, "Ben Wu", weekStart, 10L);
        StudentEntity nextWeek = student(3L, "Outside Week", weekStart.plusDays(7), 10L);
        CampusEntity campus = new CampusEntity();
        campus.setId(10L);
        campus.setCampusName("North Campus");

        when(userAccountMapper.selectActiveAdmins()).thenReturn(List.of(firstAdmin, secondAdmin));
        when(studentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sunday, nextWeek, monday));
        when(campusMapper.selectBatchIds(anyCollection())).thenReturn(List.of(campus));

        int recipientCount = service.refreshCurrentWeekBirthdayMessages();

        ArgumentCaptor<SystemMessageEntity> captor = ArgumentCaptor.forClass(SystemMessageEntity.class);
        verify(systemMessageMapper, org.mockito.Mockito.times(2)).upsertWeeklyMessage(captor.capture());
        assertThat(recipientCount).isEqualTo(2);
        assertThat(captor.getAllValues()).extracting(SystemMessageEntity::getRecipientUserId)
                .containsExactly(101L, 102L);
        for (SystemMessageEntity message : captor.getAllValues()) {
            assertThat(message.getMessageType()).isEqualTo("WEEKLY_BIRTHDAY");
            assertThat(message.getWeekStartDate()).isEqualTo(weekStart);
            assertThat(message.getItemCount()).isEqualTo(2);
            assertThat(message.getStudentsPayload()).contains("Ben Wu", "Ava Lin", "North Campus")
                    .doesNotContain("Outside Week");
            List<BirthdayStudentMessageVO> students = readStudents(message.getStudentsPayload());
            assertThat(students).extracting(BirthdayStudentMessageVO::studentName)
                    .containsExactly("Ben Wu", "Ava Lin");
            assertThat(students).extracting(BirthdayStudentMessageVO::birthdayDate)
                    .containsExactly(weekStart, weekStart.plusDays(6));
        }
    }

    @Test
    void openingInboxCreatesMissingCurrentWeekMessageAndReturnsItUnread() {
        Long adminId = 101L;
        LocalDate weekStart = LocalDate.now(AppTime.APP_ZONE_ID)
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        when(systemMessageMapper.selectForWeek(adminId, "WEEKLY_BIRTHDAY", weekStart)).thenReturn(null);
        when(studentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(systemMessageMapper.selectRecentForUser(adminId, 12)).thenReturn(List.of());

        var inbox = service.getInbox(adminId);

        ArgumentCaptor<SystemMessageEntity> message = ArgumentCaptor.forClass(SystemMessageEntity.class);
        verify(systemMessageMapper).upsertWeeklyMessage(message.capture());
        assertThat(message.getValue().getRecipientUserId()).isEqualTo(adminId);
        assertThat(message.getValue().getItemCount()).isZero();
        assertThat(inbox.unreadCount()).isZero();
        assertThat(inbox.messages()).isEmpty();
    }

    @Test
    void birthdayInWeekUsesInclusiveDatesAndHandlesLeapDay() {
        LocalDate weekStart = LocalDate.of(2026, 10, 19);
        LocalDate weekEnd = weekStart.plusDays(6);

        assertThat(SystemMessageServiceImpl.birthdayInWeek(LocalDate.of(1990, 10, 19), weekStart, weekEnd))
                .isEqualTo(weekStart);
        assertThat(SystemMessageServiceImpl.birthdayInWeek(LocalDate.of(1990, 10, 25), weekStart, weekEnd))
                .isEqualTo(weekEnd);
        assertThat(SystemMessageServiceImpl.birthdayInWeek(LocalDate.of(1990, 10, 26), weekStart, weekEnd))
                .isNull();
        assertThat(SystemMessageServiceImpl.birthdayInWeek(LocalDate.of(2000, 2, 29),
                LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 5)))
                .isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(SystemMessageServiceImpl.birthdayInWeek(LocalDate.of(2000, 2, 29),
                LocalDate.of(2027, 2, 22), LocalDate.of(2027, 2, 28)))
                .isEqualTo(LocalDate.of(2027, 2, 28));
    }

    private static UserAccountEntity admin(Long id) {
        UserAccountEntity admin = new UserAccountEntity();
        admin.setId(id);
        return admin;
    }

    private static StudentEntity student(Long id, String name, LocalDate birthDate, Long campusId) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setStudentName(name);
        student.setBirthDate(birthDate);
        student.setCampusId(campusId);
        student.setGrade("G5");
        return student;
    }

    private static List<BirthdayStudentMessageVO> readStudents(String payload) {
        try {
            return new ObjectMapper().registerModule(new JavaTimeModule())
                    .readerForListOf(BirthdayStudentMessageVO.class).readValue(payload);
        } catch (Exception exception) {
            throw new AssertionError("Unable to read serialized birthday list", exception);
        }
    }
}
