package com.classdeduction.system.modules.schedule.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps legacy schemas compatible with the current schedule model.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleSchemaCompatibilityInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        dropLegacyOccurrenceSlotForeignKey();
        addClassGroupColumnIfMissing("end_date",
                "date DEFAULT NULL COMMENT 'Last date for a fixed-date grade class' AFTER start_date");
        addEnrollmentOrderItemColumnIfMissing("start_date",
                "date DEFAULT NULL COMMENT 'Course line enrollment start date' AFTER time_slot_label");
        addSlotColumnIfMissing("subject_name",
                "varchar(128) DEFAULT NULL COMMENT 'Course/subject name taught in this recurring weekly slot'");
        addSlotColumnIfMissing("allocated_units",
                "int DEFAULT NULL COMMENT 'Units (1 unit = 0.5h) allocated to this slot; existing schedules must be completed before further generation'");
        makeCoursePackageDefaultLessonUnitsOptional();
        addTeacherPayRateColumnIfMissing("grade_band",
                "varchar(64) DEFAULT NULL COMMENT 'Optional grade/level scope for this teacher pay rate' AFTER student_id");
        addTeacherPayRateColumnIfMissing("delivery_mode",
                "varchar(16) DEFAULT NULL COMMENT 'Optional delivery scope: ONLINE, OFFLINE, or NULL for any' AFTER grade_band");
        makeTeacherPayRateProductScopeOptional();
    }

    private void addClassGroupColumnIfMissing(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'class_group'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE class_group ADD COLUMN `" + columnName + "` " + columnDefinition);
        log.info("Added class_group.{} column for fixed-date group classes.", columnName);
    }

    private void addEnrollmentOrderItemColumnIfMissing(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'enrollment_order_item'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE enrollment_order_item ADD COLUMN `" + columnName + "` " + columnDefinition);
        log.info("Added enrollment_order_item.{} column for course-line enrollment start dates.", columnName);
    }

    private void addSlotColumnIfMissing(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'recurring_schedule_slot'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE recurring_schedule_slot ADD COLUMN `" + columnName + "` " + columnDefinition);
        log.info("Added recurring_schedule_slot.{} column for per-slot allocation support.", columnName);
    }

    private void addTeacherPayRateColumnIfMissing(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'teacher_pay_rate'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE teacher_pay_rate ADD COLUMN `" + columnName + "` " + columnDefinition);
        log.info("Added teacher_pay_rate.{} column for grade and delivery scoped pay rates.", columnName);
    }

    private void makeCoursePackageDefaultLessonUnitsOptional() {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'course_package'
                  AND COLUMN_NAME = 'default_lesson_units'
                """, String.class);
        if ("YES".equals(nullable)) {
            return;
        }
        jdbcTemplate.execute("""
                ALTER TABLE course_package
                MODIFY COLUMN default_lesson_units int DEFAULT NULL
                COMMENT 'Legacy default lesson units; new schedules define duration per slot'
                """);
        log.info("Made course_package.default_lesson_units optional for the current 1-on-1 schedule model.");
    }

    private void makeTeacherPayRateProductScopeOptional() {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'teacher_pay_rate'
                  AND COLUMN_NAME = 'course_product_id'
                """, String.class);
        if ("YES".equals(nullable)) {
            return;
        }
        jdbcTemplate.execute("""
                ALTER TABLE teacher_pay_rate
                MODIFY COLUMN course_product_id bigint DEFAULT NULL
                COMMENT 'Optional course product scope; NULL applies to all products'
                """);
        log.info("Made teacher_pay_rate.course_product_id optional for teacher-wide default rates.");
    }

    private void dropLegacyOccurrenceSlotForeignKey() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'lesson_occurrence'
                  AND CONSTRAINT_NAME = 'fk_occurrence_slot'
                  AND REFERENCED_TABLE_NAME = 'recurring_schedule_slot'
                """, Integer.class);
        if (count == null || count == 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE lesson_occurrence DROP FOREIGN KEY fk_occurrence_slot");
        log.info("Dropped legacy fk_occurrence_slot; schedule_slot_id now supports group-class schedule slots.");
    }
}
