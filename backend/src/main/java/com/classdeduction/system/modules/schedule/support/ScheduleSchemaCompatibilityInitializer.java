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
        // 兼容旧库：排课服务会读取这个字段，缺列会直接导致注册页的班级查询返回 500。
        boolean baseEndDateAdded = addClassGroupColumnIfMissing("schedule_base_end_date",
                "date DEFAULT NULL COMMENT 'User-configured schedule end before holiday extensions' AFTER end_date");
        if (baseEndDateAdded) {
            jdbcTemplate.update("UPDATE class_group SET schedule_base_end_date = end_date WHERE schedule_base_end_date IS NULL");
        }
        addProductGradeConfigColumnIfMissing("class_start_date",
                "date DEFAULT NULL COMMENT 'Grade-wide actual class period start date' AFTER display_order");
        addProductGradeConfigColumnIfMissing("class_end_date",
                "date DEFAULT NULL COMMENT 'Grade-wide actual class period end date' AFTER class_start_date");
        addProductGradeConfigColumnIfMissing("class_period_configured",
                "tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Whether the grade-wide class period is explicitly configured' AFTER class_end_date");
        addClassGroupColumnIfMissing("actual_class",
                "varchar(64) DEFAULT NULL COMMENT 'Actual class name under a billing grade, e.g. G1/G2' AFTER grade_band");
        addClassGroupColumnIfMissing("campus_id",
                "bigint DEFAULT NULL COMMENT 'Campus assigned to this actual class' AFTER classroom_id");
        addEnrollmentOrderItemColumnIfMissing("start_date",
                "date DEFAULT NULL COMMENT 'Course line enrollment start date' AFTER time_slot_label");
        addEnrollmentOrderColumnIfMissing("student_class_snapshot",
                "varchar(32) DEFAULT NULL COMMENT 'Registration class level snapshot' AFTER student_name_snapshot");
        addSlotColumnIfMissing("subject_name",
                "varchar(128) DEFAULT NULL COMMENT 'Course/subject name taught in this recurring weekly slot'");
        addSlotColumnIfMissing("allocated_units",
                "int DEFAULT NULL COMMENT 'Units (1 unit = 0.5h) allocated to this slot; existing schedules must be completed before further generation'");
        addScheduleColumnIfMissing("recurring_schedule_slot", "delivery_mode",
                "varchar(16) DEFAULT NULL COMMENT 'IN_PERSON or ONLINE' AFTER teacher_id");
        addScheduleColumnIfMissing("recurring_schedule_slot", "classroom_id",
                "bigint DEFAULT NULL COMMENT 'Classroom assigned to this weekly slot' AFTER delivery_mode");
        addScheduleColumnIfMissing("class_group_schedule_slot", "delivery_mode",
                "varchar(16) DEFAULT NULL COMMENT 'IN_PERSON or ONLINE' AFTER teacher_id");
        addScheduleColumnIfMissing("class_group_schedule_slot", "alternate_teacher_id",
                "bigint DEFAULT NULL COMMENT 'Teacher for the alternating lesson in this weekly slot' AFTER teacher_id");
        addScheduleColumnIfMissing("class_group_schedule_slot", "alternate_subject_name",
                "varchar(128) DEFAULT NULL COMMENT 'Subject for the alternating lesson in this weekly slot' AFTER subject_name");
        addScheduleColumnIfMissing("class_group_schedule_slot", "alternate_delivery_mode",
                "varchar(16) DEFAULT NULL COMMENT 'Delivery mode for the alternating lesson' AFTER alternate_subject_name");
        addScheduleColumnIfMissing("class_group_schedule_slot", "alternate_classroom_id",
                "bigint DEFAULT NULL COMMENT 'Classroom for the alternating lesson' AFTER alternate_delivery_mode");
        addScheduleColumnIfMissing("class_group_schedule_slot", "rotation_anchor_date",
                "date DEFAULT NULL COMMENT 'First scheduled date assigned to the primary teacher and subject' AFTER alternate_subject_name");
        addScheduleColumnIfMissing("lesson_occurrence", "delivery_mode",
                "varchar(16) DEFAULT NULL COMMENT 'Delivery mode snapshot: IN_PERSON or ONLINE' AFTER planned_teacher_id");
        addScheduleColumnIfMissing("lesson_occurrence", "subject_name",
                "varchar(128) DEFAULT NULL COMMENT 'Subject snapshot copied from the weekly class slot' AFTER planned_teacher_id");
        jdbcTemplate.update("UPDATE recurring_schedule_slot SET delivery_mode = 'IN_PERSON' WHERE delivery_mode IS NULL AND classroom_id IS NOT NULL");
        jdbcTemplate.update("UPDATE class_group_schedule_slot SET delivery_mode = 'IN_PERSON' WHERE delivery_mode IS NULL AND classroom_id IS NOT NULL");
        jdbcTemplate.update("UPDATE lesson_occurrence SET delivery_mode = 'IN_PERSON' WHERE delivery_mode IS NULL AND planned_classroom_id IS NOT NULL");
        makeCoursePackageDefaultLessonUnitsOptional();
        addTeacherPayRateColumnIfMissing("grade_band",
                "varchar(64) DEFAULT NULL COMMENT 'Optional grade/level scope for this teacher pay rate' AFTER student_id");
        addTeacherPayRateColumnIfMissing("delivery_mode",
                "varchar(16) DEFAULT NULL COMMENT 'Optional delivery scope: ONLINE, OFFLINE, or NULL for any' AFTER grade_band");
        makeTeacherPayRateProductScopeOptional();
    }

    private boolean addClassGroupColumnIfMissing(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'class_group'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (count != null && count > 0) {
            return false;
        }
        jdbcTemplate.execute("ALTER TABLE class_group ADD COLUMN `" + columnName + "` " + columnDefinition);
        log.info("Added class_group.{} column for fixed-date group classes.", columnName);
        return true;
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

    private void addProductGradeConfigColumnIfMissing(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'product_grade_config'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE product_grade_config ADD COLUMN `" + columnName + "` " + columnDefinition);
        log.info("Added product_grade_config.{} for grade-wide actual class periods.", columnName);
    }

    private void addEnrollmentOrderColumnIfMissing(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'enrollment_order'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE enrollment_order ADD COLUMN `" + columnName + "` " + columnDefinition);
        log.info("Added enrollment_order.{} column for registration student class snapshots.", columnName);
    }

    private void addSlotColumnIfMissing(String columnName, String columnDefinition) {
        addScheduleColumnIfMissing("recurring_schedule_slot", columnName, columnDefinition);
    }

    private void addScheduleColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN `" + columnName + "` " + columnDefinition);
        log.info("Added {}.{} for per-slot delivery mode and classroom support.", tableName, columnName);
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
