package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.dto.ClassHolidaySaveRequest;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 用本机线上快照复现 G6-G8 Election Day 2026-11-03 假期；仅本地临时库运行。 */
@EnabledIfEnvironmentVariable(named = "CLASS_HOLIDAY_SNAPSHOT_TEST", matches = "1")
@ActiveProfiles("group-course-v2")
@SpringBootTest(properties = {
        "spring.datasource.url=${CLASS_HOLIDAY_SNAPSHOT_URL}",
        "spring.datasource.username=${CLASS_HOLIDAY_SNAPSHOT_USERNAME:root}",
        "spring.datasource.password=${CLASS_HOLIDAY_SNAPSHOT_PASSWORD:}",
        "app.database.required-name=${CLASS_HOLIDAY_SNAPSHOT_DB:jin_holiday_repro_20261030}",
        "app.schedule.renewal-initial-delay-ms=600000", "app.balance.sync-initial-delay-ms=600000"
})
@Transactional
class ClassHolidaySnapshotReproTest {

    private static final LocalDate ELECTION_DAY = LocalDate.of(2026, 11, 3);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClassHolidayService classHolidayService;

    @Test
    void addingElectionDayToG6G8NoLongerReportsAResourceConflict() {
        Long projectId = jdbc.queryForObject(
                "SELECT id FROM group_course_project WHERE project_code = 'G6-G8' AND is_deleted = 0 LIMIT 1",
                Long.class);
        assertThat(projectId).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM course_holiday
                WHERE group_project_id = ? AND is_deleted = 0
                  AND start_date <= ? AND end_date >= ?
                """, Integer.class, projectId, ELECTION_DAY, ELECTION_DAY)).isZero();

        List<Map<String, Object>> classesBefore = jdbc.queryForList("""
                SELECT id, start_date, end_date, schedule_base_end_date
                FROM class_group WHERE group_project_id = ? AND is_deleted = 0 ORDER BY id
                """, projectId);
        long lessonsOnHoliday = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrence o
                JOIN class_group cg ON cg.id = o.class_group_id
                WHERE cg.group_project_id = ? AND o.is_deleted = 0
                  AND DATE(o.planned_start_at) = ?
                """, Long.class, projectId, ELECTION_DAY);
        long lessonsAfterEnd = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrence o
                JOIN class_group cg ON cg.id = o.class_group_id
                WHERE cg.group_project_id = ? AND o.is_deleted = 0
                  AND o.planned_start_at >= DATE_ADD(cg.end_date, INTERVAL 1 DAY)
                """, Long.class, projectId);

        assertThat(lessonsOnHoliday).isGreaterThan(0);

        ClassHolidaySaveRequest request = new ClassHolidaySaveRequest();
        request.setHolidayName("Election Day");
        request.setStartDate(ELECTION_DAY);
        request.setEndDate(ELECTION_DAY);

        ClassHolidayVO holiday = classHolidayService.createHoliday(projectId, request);

        assertThat(holiday.id()).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrence o
                JOIN class_group cg ON cg.id = o.class_group_id
                WHERE cg.group_project_id = ? AND o.is_deleted = 0
                  AND DATE(o.planned_start_at) = ? AND o.planning_status <> 'CANCELLED'
                """, Long.class, projectId, ELECTION_DAY)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrence o
                JOIN class_group cg ON cg.id = o.class_group_id
                WHERE cg.group_project_id = ? AND o.is_deleted = 0
                  AND DATE(o.planned_start_at) = ? AND o.course_holiday_id = ?
                """, Long.class, projectId, ELECTION_DAY, holiday.id())).isEqualTo(lessonsOnHoliday);

        List<Map<String, Object>> classesAfter = jdbc.queryForList("""
                SELECT id, start_date, end_date, schedule_base_end_date
                FROM class_group WHERE group_project_id = ? AND is_deleted = 0 ORDER BY id
                """, projectId);
        assertThat(classesAfter).isEqualTo(classesBefore);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrence o
                JOIN class_group cg ON cg.id = o.class_group_id
                WHERE cg.group_project_id = ? AND o.is_deleted = 0
                  AND o.planned_start_at >= DATE_ADD(cg.end_date, INTERVAL 1 DAY)
                """, Long.class, projectId)).isEqualTo(lessonsAfterEnd);
    }
}
