package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageStartingHoursRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 仅在专用本机临时 MySQL 中运行，验证课包、流水、排课快照与课次的真实事务。 */
@EnabledIfEnvironmentVariable(named = "STARTING_HOURS_MYSQL_TEST", matches = "1")
@ActiveProfiles("group-course-v2")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:13319/class_deduction_system_v2?serverTimezone=America/New_York",
        "spring.datasource.username=root", "spring.datasource.password=",
        "app.schedule.renewal-initial-delay-ms=600000", "app.balance.sync-initial-delay-ms=600000"
})
class StartingHoursMySqlIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CoursePackageService service;

    @Test
    void zeroBalanceRollsBackOnPendingLessonAndCommitsAfterReview() {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13319);
        jdbc.update("INSERT INTO sys_user (id,username,password_hash,user_type,display_name) VALUES (9001,'hours-test','x','ADMIN','Test')");
        jdbc.update("INSERT INTO student (id,student_no,student_name) VALUES (1001,'HOURS-TEST','Test Student')");
        jdbc.update("INSERT INTO enrollment_order (id,enrollment_no,student_id) VALUES (2001,'HOURS-ORDER',1001)");
        jdbc.update("INSERT INTO course_package (id,package_no,enrollment_order_id,student_id,package_name,teaching_mode,total_units,opening_used_units,consumed_units,remaining_units,package_status,version) VALUES (3001,'HOURS-PACKAGE',2001,1001,'Test 120 hours','ONE_ON_ONE',240,201,235,5,'ACTIVE',0)");
        jdbc.update("INSERT INTO recurring_schedule_rule (id,package_id,rule_status,start_date) VALUES (4001,3001,'ACTIVE','2026-01-01')");
        LocalDateTime now = AppTime.nowLocalDateTime();
        insertLesson(5001, now.plusDays(2));
        insertLesson(5002, now.plusDays(9));
        insertLesson(5003, now.minusDays(2));

        CoursePackageStartingHoursRequest request = new CoursePackageStartingHoursRequest();
        request.setStartingHours(BigDecimal.valueOf(17));
        request.setExpectedVersion(0);
        assertThatThrownBy(() -> service.updateStartingHours(3001L, request, 9001L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ZERO_BALANCE_LESSON_REVIEW_REQUIRED"));
        assertThat(number("SELECT remaining_units FROM course_package WHERE id=3001")).isEqualTo(5);
        assertThat(number("SELECT version FROM course_package WHERE id=3001")).isZero();
        assertThat(number("SELECT COUNT(*) FROM hour_ledger WHERE package_id=3001")).isZero();
        assertThat(number("SELECT COUNT(*) FROM recurring_schedule_starting_balance WHERE package_id=3001")).isZero();
        assertThat(number("SELECT COUNT(*) FROM lesson_occurrence WHERE id IN (5001,5002) AND is_deleted=0")).isEqualTo(2);

        jdbc.update("DELETE FROM lesson_occurrence WHERE id=5003");
        jdbc.update("UPDATE course_package SET version=1 WHERE id=3001");
        assertThatThrownBy(() -> service.updateStartingHours(3001L, request, 9001L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PACKAGE_CONCURRENT_UPDATE"));
        assertThat(number("SELECT COUNT(*) FROM hour_ledger WHERE package_id=3001")).isZero();

        request.setExpectedVersion(1);
        service.updateStartingHours(3001L, request, 9001L);
        assertThat(number("SELECT remaining_units FROM course_package WHERE id=3001")).isZero();
        assertThat(jdbc.queryForObject("SELECT package_status FROM course_package WHERE id=3001", String.class)).isEqualTo("COMPLETED");
        assertThat(number("SELECT COUNT(*) FROM hour_ledger WHERE package_id=3001 AND change_units=-5 AND balance_after_units=0")).isEqualTo(1);
        assertThat(number("SELECT COUNT(*) FROM recurring_schedule_starting_balance WHERE package_id=3001 AND starting_units=34")).isEqualTo(1);
        assertThat(number("SELECT COUNT(*) FROM lesson_occurrence WHERE id IN (5001,5002) AND is_deleted=1")).isEqualTo(2);
    }

    private void insertLesson(long id, LocalDateTime start) {
        jdbc.update("INSERT INTO lesson_occurrence (id,lesson_no,package_id,student_id,schedule_rule_id,occurrence_type,planned_start_at,planned_end_at,planned_units,planning_status) VALUES (?,?,?,?,?,'REGULAR',?,?,4,'SCHEDULED')",
                id, "HOURS-LESSON-" + id, 3001, 1001, 4001, start, start.plusHours(2));
    }

    private int number(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
