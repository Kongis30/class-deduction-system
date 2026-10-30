package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.schedule.dto.ClassHolidaySaveRequest;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayPreviewVO;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 在本机临时 MySQL 中验证班课假期的实际落库行为；默认不运行，避免误连开发库。 */
@EnabledIfEnvironmentVariable(named = "CLASS_HOLIDAY_MYSQL_TEST", matches = "1")
@ActiveProfiles("group-course-v2")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:13319/class_deduction_system_v2?serverTimezone=America/New_York",
        "spring.datasource.username=root", "spring.datasource.password=",
        "app.schedule.renewal-initial-delay-ms=600000", "app.balance.sync-initial-delay-ms=600000"
})
@Transactional
class ClassHolidayMySqlIntegrationTest {

    private static final long PRODUCT_ID = 987000001L;
    private static final long PROJECT_ID = 987000002L;
    private static final long CAMPUS_ID = 987000003L;
    private static final long TEACHER_ID = 987000004L;
    private static final long ALTERNATE_TEACHER_ID = 987000005L;
    private static final long CLASSROOM_ID = 987000006L;
    private static final long ALTERNATE_CLASSROOM_ID = 987000007L;
    private static final long STUDENT_ID = 987000008L;
    private static final long CLASS_ID = 987000010L;
    private static final long SECOND_CLASS_ID = 987000011L;
    private static final long SECOND_TEACHER_ID = 987000012L;
    private static final long SECOND_CLASSROOM_ID = 987000013L;
    private static final long ENROLLMENT_ID = 987000020L;
    private static final long SLOT_BASE = 987001000L;
    private static final long SECOND_SLOT_BASE = 987002000L;
    private static final LocalDate CLASS_START = LocalDate.of(2037, 9, 10);
    private static final LocalDate CLASS_END = LocalDate.of(2037, 11, 30);
    private static final LocalDate HOLIDAY_START = LocalDate.of(2037, 11, 3);
    private static final LocalDate HOLIDAY_END = LocalDate.of(2037, 11, 5);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClassHolidayService classHolidayService;

    @BeforeEach
    void seedProjectClassAndSlots() {
        jdbc.update("DELETE FROM lesson_record WHERE occurrence_id IN (SELECT id FROM lesson_occurrence WHERE class_group_id IN (?, ?))",
                CLASS_ID, SECOND_CLASS_ID);
        jdbc.update("DELETE FROM lesson_occurrence WHERE class_group_id IN (?, ?)", CLASS_ID, SECOND_CLASS_ID);
        jdbc.update("DELETE FROM class_enrollment WHERE id = ?", ENROLLMENT_ID);
        jdbc.update("DELETE FROM course_holiday WHERE group_project_id = ?", PROJECT_ID);
        jdbc.update("DELETE FROM class_group_schedule_slot WHERE class_group_id IN (?, ?)", CLASS_ID, SECOND_CLASS_ID);
        jdbc.update("DELETE FROM class_group WHERE id IN (?, ?)", CLASS_ID, SECOND_CLASS_ID);
        jdbc.update("DELETE FROM group_course_project WHERE id = ?", PROJECT_ID);
        jdbc.update("DELETE FROM course_product WHERE id = ?", PRODUCT_ID);
        jdbc.update("DELETE FROM teacher WHERE id IN (?, ?, ?)", TEACHER_ID, ALTERNATE_TEACHER_ID, SECOND_TEACHER_ID);
        jdbc.update("DELETE FROM classroom WHERE id IN (?, ?, ?)", CLASSROOM_ID, ALTERNATE_CLASSROOM_ID, SECOND_CLASSROOM_ID);
        jdbc.update("DELETE FROM campus WHERE id = ?", CAMPUS_ID);
        jdbc.update("DELETE FROM student WHERE id = ?", STUDENT_ID);

        jdbc.update("INSERT INTO course_product (id, product_code, product_name, teaching_mode, status) VALUES (?, ?, ?, 'OFFLINE', 'ACTIVE')",
                PRODUCT_ID, "HOL-TEST", "Holiday Test Product");
        jdbc.update("INSERT INTO group_course_project (id, course_product_id, project_code, project_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                PROJECT_ID, PRODUCT_ID, "HOL", "Holiday Test Project");
        jdbc.update("INSERT INTO campus (id, campus_name, status) VALUES (?, ?, 'ACTIVE')", CAMPUS_ID, "Holiday Test Campus");
        jdbc.update("INSERT INTO teacher (id, teacher_no, teacher_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                TEACHER_ID, "HOL-T1", "Holiday Teacher");
        jdbc.update("INSERT INTO teacher (id, teacher_no, teacher_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                ALTERNATE_TEACHER_ID, "HOL-T2", "Alternate Teacher");
        jdbc.update("INSERT INTO teacher (id, teacher_no, teacher_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                SECOND_TEACHER_ID, "HOL-T3", "Second Class Teacher");
        jdbc.update("INSERT INTO classroom (id, room_code, room_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                CLASSROOM_ID, "HOL-R1", "Holiday Room 1");
        jdbc.update("INSERT INTO classroom (id, room_code, room_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                ALTERNATE_CLASSROOM_ID, "HOL-R2", "Holiday Room 2");
        jdbc.update("INSERT INTO classroom (id, room_code, room_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                SECOND_CLASSROOM_ID, "HOL-R3", "Holiday Room 3");
        jdbc.update("INSERT INTO student (id, student_no, student_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                STUDENT_ID, "HOL-S1", "Holiday Student");

        insertClass(CLASS_ID, "Holiday G1", TEACHER_ID, CLASSROOM_ID);
        insertClass(SECOND_CLASS_ID, "Holiday G2", SECOND_TEACHER_ID, SECOND_CLASSROOM_ID);
        for (int weekday = 1; weekday <= 5; weekday++) {
            insertSlot(SLOT_BASE + weekday, CLASS_ID, weekday, TEACHER_ID, CLASSROOM_ID);
            insertSlot(SECOND_SLOT_BASE + weekday, SECOND_CLASS_ID, weekday, SECOND_TEACHER_ID, SECOND_CLASSROOM_ID);
        }
        seedOccurrences(SLOT_BASE, CLASS_START, CLASS_END);
        seedOccurrences(SECOND_SLOT_BASE, CLASS_START, CLASS_END);
    }

    @Test
    void singleDayHolidayCancelsOnlyThatDayAndKeepsClassEnd() {
        long totalBefore = occurrenceCount();
        ClassHolidayVO holiday = createHoliday(HOLIDAY_START, HOLIDAY_START);

        assertThat(statusOn(CLASS_ID, HOLIDAY_START)).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 2))).isEqualTo("SCHEDULED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 4))).isEqualTo("SCHEDULED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 6))).isEqualTo("SCHEDULED");
        assertThat(holiday.id()).isNotNull();
        assertThat(occurrenceCount()).isEqualTo(totalBefore);
        assertClassEndUnchanged();
        assertThat(occurrencesAfterClassEnd()).isZero();
    }

    @Test
    void multiDayHolidayCancelsEveryClassDayInRange() {
        createHoliday(HOLIDAY_START, HOLIDAY_END);

        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 3))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 4))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 5))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 2))).isEqualTo("SCHEDULED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 6))).isEqualTo("SCHEDULED");
        assertClassEndUnchanged();
    }

    @Test
    void fullMonthHolidayDoesNotExtendTheClassOrAddLessons() {
        long totalBefore = occurrenceCount();
        long novemberBefore = occurrenceCountBetween(LocalDate.of(2037, 11, 1), LocalDate.of(2037, 11, 30));

        ClassHolidayPreviewVO preview = classHolidayService.previewHoliday(PROJECT_ID, null,
                request(LocalDate.of(2037, 11, 1), LocalDate.of(2037, 11, 30)));
        assertThat(preview.affectedOccurrenceCount()).isEqualTo(42);
        ClassHolidayVO created = createHoliday(LocalDate.of(2037, 11, 1), LocalDate.of(2037, 11, 30));
        assertThat(created.id()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_holiday WHERE group_project_id = ? AND is_deleted = 0",
                Integer.class, PROJECT_ID)).isEqualTo(1);
        Map<String, Object> holidayRow = jdbc.queryForMap(
                "SELECT start_date, end_date, is_deleted FROM course_holiday WHERE id = ?", created.id());
        assertThat(holidayRow.get("start_date")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2037, 11, 1)));
        assertThat(holidayRow.get("end_date")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2037, 11, 30)));
        assertThat(classHolidayService.listHolidays(PROJECT_ID)).hasSize(1);

        assertThat(nonCancelledOccurrenceCountBetween(LocalDate.of(2037, 11, 1), LocalDate.of(2037, 11, 30))).isZero();
        assertThat(occurrenceCount()).isEqualTo(totalBefore);
        assertThat(novemberBefore).isGreaterThan(0);
        assertThat(occurrencesAfterClassEnd()).isZero();
        assertClassEndUnchanged();
    }

    @Test
    void deletingHolidayRestoresOriginalLessonsWithoutChangingClassEnd() {
        long totalBefore = occurrenceCount();
        ClassHolidayVO holiday = createHoliday(HOLIDAY_START, HOLIDAY_START);
        classHolidayService.deleteHoliday(PROJECT_ID, holiday.id());

        assertThat(statusOn(CLASS_ID, HOLIDAY_START)).isEqualTo("SCHEDULED");
        assertThat(holidayIdOn(CLASS_ID, HOLIDAY_START)).isNull();
        assertThat(occurrenceCount()).isEqualTo(totalBefore);
        assertClassEndUnchanged();
    }

    @Test
    void shrinkingHolidayRestoresDatesRemovedFromTheRange() {
        ClassHolidayVO holiday = createHoliday(HOLIDAY_START, HOLIDAY_END);
        ClassHolidaySaveRequest smaller = request(HOLIDAY_START, LocalDate.of(2037, 11, 4));
        classHolidayService.updateHoliday(PROJECT_ID, holiday.id(), smaller);

        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 3))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 4))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 5))).isEqualTo("SCHEDULED");
        assertClassEndUnchanged();
    }

    @Test
    void extendingHolidayCancelsNewlyAddedDates() {
        ClassHolidayVO holiday = createHoliday(HOLIDAY_START, LocalDate.of(2037, 11, 4));
        classHolidayService.updateHoliday(PROJECT_ID, holiday.id(),
                request(HOLIDAY_START, LocalDate.of(2037, 11, 5)));

        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 3))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 4))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 5))).isEqualTo("CANCELLED");
        assertClassEndUnchanged();
    }

    @Test
    void multipleNonOverlappingHolidaysAreBothApplied() {
        createHoliday(LocalDate.of(2037, 11, 3), LocalDate.of(2037, 11, 3));
        createHoliday(LocalDate.of(2037, 11, 5), LocalDate.of(2037, 11, 5));

        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 3))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 5))).isEqualTo("CANCELLED");
        assertThat(statusOn(CLASS_ID, LocalDate.of(2037, 11, 4))).isEqualTo("SCHEDULED");
    }

    @Test
    void overlappingHolidayIsRejected() {
        createHoliday(HOLIDAY_START, HOLIDAY_END);

        assertThatThrownBy(() -> createHoliday(LocalDate.of(2037, 11, 4), LocalDate.of(2037, 11, 6)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void holidayStartingInThePastIsRejected() {
        assertThatThrownBy(() -> createHoliday(LocalDate.now(AppTime.APP_ZONE_ID).minusDays(1),
                LocalDate.now(AppTime.APP_ZONE_ID).plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("today or later");
    }

    @Test
    void holidayAfterClassEndDoesNothing() {
        long totalBefore = occurrenceCount();
        createHoliday(LocalDate.of(2037, 12, 1), LocalDate.of(2037, 12, 2));

        assertThat(occurrenceCount()).isEqualTo(totalBefore);
        assertThat(occurrencesAfterClassEnd()).isZero();
        assertClassEndUnchanged();
    }

    @Test
    void manuallyCancelledLessonIsNotRestoredWhenHolidayIsDeleted() {
        jdbc.update("UPDATE lesson_occurrence SET planning_status = 'CANCELLED' WHERE class_group_id = ? AND DATE(planned_start_at) = ?",
                CLASS_ID, HOLIDAY_START);
        ClassHolidayVO holiday = createHoliday(HOLIDAY_START, HOLIDAY_START);
        classHolidayService.deleteHoliday(PROJECT_ID, holiday.id());

        assertThat(statusOn(CLASS_ID, HOLIDAY_START)).isEqualTo("CANCELLED");
        assertThat(holidayIdOn(CLASS_ID, HOLIDAY_START)).isNull();
    }

    @Test
    void adjustedLessonIsRestoredToAdjustedStatusWhenHolidayIsDeleted() {
        jdbc.update("UPDATE lesson_occurrence SET planning_status = 'ADJUSTED' WHERE class_group_id = ? AND DATE(planned_start_at) = ?",
                CLASS_ID, HOLIDAY_START);
        ClassHolidayVO holiday = createHoliday(HOLIDAY_START, HOLIDAY_START);
        assertThat(statusOn(CLASS_ID, HOLIDAY_START)).isEqualTo("CANCELLED");

        classHolidayService.deleteHoliday(PROJECT_ID, holiday.id());

        assertThat(statusOn(CLASS_ID, HOLIDAY_START)).isEqualTo("ADJUSTED");
        assertThat(holidayIdOn(CLASS_ID, HOLIDAY_START)).isNull();
    }

    @Test
    void holidayOnNonClassDayDoesNothing() {
        long totalBefore = occurrenceCount();
        ClassHolidayVO holiday = createHoliday(LocalDate.of(2037, 11, 7), LocalDate.of(2037, 11, 7));

        assertThat(holiday.id()).isNotNull();
        assertThat(occurrenceCount()).isEqualTo(totalBefore);
        assertThat(occurrencesOn(LocalDate.of(2037, 11, 7))).isZero();
        assertClassEndUnchanged();
    }

    @Test
    void projectHolidayCancelsEveryClassAndCampusInTheProject() {
        createHoliday(HOLIDAY_START, HOLIDAY_START);

        assertThat(statusOn(CLASS_ID, HOLIDAY_START)).isEqualTo("CANCELLED");
        assertThat(statusOn(SECOND_CLASS_ID, HOLIDAY_START)).isEqualTo("CANCELLED");
    }

    @Test
    void holidayDoesNotChangeEnrollmentExpiryOrStatus() {
        jdbc.update("""
                INSERT INTO class_enrollment
                  (id, class_id, student_id, enrollment_date, expiry_date, billing_type,
                   purchased_quantity, status, group_project_id)
                VALUES (?, ?, ?, ?, ?, 'SESSION', 10, 'ACTIVE', ?)
                """, ENROLLMENT_ID, CLASS_ID, STUDENT_ID, CLASS_START, CLASS_END, PROJECT_ID);
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT expiry_date, status FROM class_enrollment WHERE id = ?", ENROLLMENT_ID);

        createHoliday(HOLIDAY_START, HOLIDAY_END);

        Map<String, Object> after = jdbc.queryForMap(
                "SELECT expiry_date, status FROM class_enrollment WHERE id = ?", ENROLLMENT_ID);
        assertThat(after.get("expiry_date")).isEqualTo(before.get("expiry_date"));
        assertThat(after.get("status")).isEqualTo(before.get("status"));
    }

    @Test
    void previewCountsAffectedLessonsWithoutReportingConflicts() {
        ClassHolidayPreviewVO preview = classHolidayService.previewHoliday(
                PROJECT_ID, null, request(HOLIDAY_START, HOLIDAY_END));

        assertThat(preview.affectedOccurrenceCount()).isEqualTo(6);
        assertThat(preview.conflicts()).isEmpty();
    }

    @Test
    void rejectsHolidayWhenLessonHasAlreadyStarted() {
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        jdbc.update("""
                INSERT INTO lesson_occurrence
                  (id, lesson_no, class_group_id, schedule_slot_id, occurrence_type,
                   planned_teacher_id, subject_name, delivery_mode, planned_classroom_id,
                   planned_start_at, planned_end_at, planned_units, planning_status,
                   conflict_checked, is_deleted)
                VALUES (?, ?, ?, ?, 'REGULAR', ?, 'ELA', 'IN_PERSON', ?, ?, ?, 6, 'SCHEDULED', 1, 0)
                """, 987100001L, "HOL-STARTED", CLASS_ID, SLOT_BASE + 5, TEACHER_ID, CLASSROOM_ID,
                today.atStartOfDay(), today.atTime(1, 0));

        assertThatThrownBy(() -> createHoliday(today, today))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already started");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_holiday WHERE group_project_id = ?", Integer.class, PROJECT_ID)).isZero();
    }

    @Test
    void rejectsHolidayWhenLessonAlreadyHasALessonRecord() {
        LocalDate date = LocalDate.of(2037, 11, 3);
        Long occurrenceId = jdbc.queryForObject(
                "SELECT id FROM lesson_occurrence WHERE class_group_id = ? AND DATE(planned_start_at) = ?",
                Long.class, CLASS_ID, date);
        jdbc.update("""
                INSERT INTO lesson_record
                  (id, occurrence_id, student_id, result_status, actual_units, should_deduct,
                   deducted_units, confirm_stage, record_source, is_deleted)
                VALUES (?, ?, ?, 'COMPLETED', 6, 1, 6, 'CONFIRMED', 'STAFF', 0)
                """, 987100002L, occurrenceId, STUDENT_ID);

        assertThatThrownBy(() -> createHoliday(date, date))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lesson record");
    }

    @Test
    void outsideLessonTeacherSubjectAndClassroomAreNotRewritten() {
        LocalDate outsideDate = LocalDate.of(2037, 11, 10);
        Map<String, Object> before = jdbc.queryForMap("""
                SELECT planned_teacher_id, subject_name, planned_classroom_id
                FROM lesson_occurrence
                WHERE class_group_id = ? AND DATE(planned_start_at) = ?
                """, CLASS_ID, outsideDate);

        createHoliday(HOLIDAY_START, HOLIDAY_END);

        Map<String, Object> after = jdbc.queryForMap("""
                SELECT planned_teacher_id, subject_name, planned_classroom_id
                FROM lesson_occurrence
                WHERE class_group_id = ? AND DATE(planned_start_at) = ?
                """, CLASS_ID, outsideDate);
        assertThat(after).isEqualTo(before);
    }

    private void insertClass(long id, String name, long teacherId, long classroomId) {
        jdbc.update("""
                INSERT INTO class_group
                  (id, class_name, status, product_id, group_project_id, grade_band, actual_class,
                   billing_type, start_date, end_date, schedule_base_end_date, total_sessions,
                   teacher_id, classroom_id, campus_id)
                VALUES (?, ?, 'ACTIVE', ?, ?, 'G1', 'G1', 'MONTH', ?, ?, ?, 60, ?, ?, ?)
                """, id, name, PRODUCT_ID, PROJECT_ID, CLASS_START, CLASS_END, CLASS_END,
                teacherId, classroomId, CAMPUS_ID);
    }

    private void insertSlot(long id, long classId, int weekday, long teacherId, long classroomId) {
        jdbc.update("""
                INSERT INTO class_group_schedule_slot
                  (id, class_group_id, slot_lineage_id, teacher_id, alternate_teacher_id,
                   delivery_mode, classroom_id, subject_name, alternate_subject_name,
                   alternate_delivery_mode, alternate_classroom_id, rotation_anchor_date,
                   weekday, start_time, end_time, effective_start_date, effective_end_date, is_deleted)
                VALUES (?, ?, ?, ?, ?, 'IN_PERSON', ?, 'ELA', 'MATH', 'IN_PERSON', ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, classId, id, teacherId, ALTERNATE_TEACHER_ID, classroomId,
                ALTERNATE_CLASSROOM_ID, LocalDate.of(2037, 10, 5), weekday,
                LocalTime.of(15, 0), LocalTime.of(18, 0), CLASS_START, CLASS_END);
    }

    private void seedOccurrences(long slotBase, LocalDate start, LocalDate end) {
        long id = 987200000L + slotBase;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            if (weekday > 5) continue;
            jdbc.update("""
                    INSERT INTO lesson_occurrence
                      (id, lesson_no, class_group_id, schedule_slot_id, occurrence_type,
                       planned_teacher_id, subject_name, delivery_mode, planned_classroom_id,
                       planned_start_at, planned_end_at, planned_units, planning_status,
                       conflict_checked, is_deleted)
                    VALUES (?, ?, ?, ?, 'REGULAR', ?, 'ELA', 'IN_PERSON', ?, ?, ?, 6, 'SCHEDULED', 1, 0)
                    """, id, "HOL-" + id, slotBase == SLOT_BASE ? CLASS_ID : SECOND_CLASS_ID,
                    slotBase + weekday, slotBase == SLOT_BASE ? TEACHER_ID : SECOND_TEACHER_ID,
                    slotBase == SLOT_BASE ? CLASSROOM_ID : SECOND_CLASSROOM_ID,
                    date.atTime(15, 0), date.atTime(18, 0));
            id++;
        }
    }

    private ClassHolidayVO createHoliday(LocalDate start, LocalDate end) {
        return classHolidayService.createHoliday(PROJECT_ID, request(start, end));
    }

    private ClassHolidaySaveRequest request(LocalDate start, LocalDate end) {
        ClassHolidaySaveRequest request = new ClassHolidaySaveRequest();
        request.setHolidayName("Holiday " + start);
        request.setStartDate(start);
        request.setEndDate(end);
        return request;
    }

    private long occurrenceCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_occurrence WHERE class_group_id IN (?, ?) AND is_deleted = 0",
                Long.class, CLASS_ID, SECOND_CLASS_ID);
    }

    private long occurrencesOn(LocalDate date) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_occurrence WHERE class_group_id IN (?, ?) AND DATE(planned_start_at) = ? AND is_deleted = 0",
                Long.class, CLASS_ID, SECOND_CLASS_ID, date);
    }

    private long occurrenceCountBetween(LocalDate start, LocalDate end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrence
                WHERE class_group_id IN (?, ?) AND is_deleted = 0
                  AND DATE(planned_start_at) BETWEEN ? AND ?
                """, Long.class, CLASS_ID, SECOND_CLASS_ID, start, end);
    }

    private long nonCancelledOccurrenceCountBetween(LocalDate start, LocalDate end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrence
                WHERE class_group_id IN (?, ?) AND is_deleted = 0
                  AND planning_status <> 'CANCELLED'
                  AND DATE(planned_start_at) BETWEEN ? AND ?
                """, Long.class, CLASS_ID, SECOND_CLASS_ID, start, end);
    }

    private long occurrencesAfterClassEnd() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrence
                WHERE class_group_id IN (?, ?) AND is_deleted = 0 AND DATE(planned_start_at) > ?
                """, Long.class, CLASS_ID, SECOND_CLASS_ID, CLASS_END);
    }

    private String statusOn(long classId, LocalDate date) {
        return jdbc.queryForObject("""
                SELECT planning_status FROM lesson_occurrence
                WHERE class_group_id = ? AND DATE(planned_start_at) = ? AND is_deleted = 0
                """, String.class, classId, date);
    }

    private Long holidayIdOn(long classId, LocalDate date) {
        return jdbc.queryForObject("""
                SELECT course_holiday_id FROM lesson_occurrence
                WHERE class_group_id = ? AND DATE(planned_start_at) = ? AND is_deleted = 0
                """, Long.class, classId, date);
    }

    private void assertClassEndUnchanged() {
        Map<String, Object> group = jdbc.queryForMap(
                "SELECT end_date, schedule_base_end_date FROM class_group WHERE id = ?", CLASS_ID);
        assertThat(group.get("end_date")).isEqualTo(java.sql.Date.valueOf(CLASS_END));
        assertThat(group.get("schedule_base_end_date")).isEqualTo(java.sql.Date.valueOf(CLASS_END));
    }
}
