package com.classdeduction.system.modules.enrollment.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 幂等 schema 兼容入口。
 * 线上已有库由应用启动自动补齐结构，完整迁移脚本仍保留在 doc/migrations 供发布审计使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrivateLessonSchemaCompatibilityInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        createTablesIfMissing();
        createTransportTablesIfMissing();
        addColumnIfMissing("recurring_schedule_rule", "private_lesson_group_id",
                "bigint DEFAULT NULL COMMENT 'Shared private lesson group ID' AFTER package_id");
        addColumnIfMissing("lesson_occurrence", "private_lesson_group_id",
                "bigint DEFAULT NULL COMMENT 'Shared private lesson group ID' AFTER package_id");
        addColumnIfMissing("payment_record", "participant_index",
                "int DEFAULT NULL COMMENT 'Shared private lesson participant index' AFTER enrollment_order_id");
        addColumnIfMissing("receipt_record", "payment_record_id",
                "bigint DEFAULT NULL COMMENT 'Linked refund payment record ID' AFTER student_id",
                "independent refund receipt linkage");
        addColumnIfMissing("enrollment_order_participant", "gross_amount",
                "decimal(12,2) DEFAULT NULL COMMENT 'Participant gross amount'");
        addColumnIfMissing("enrollment_order_participant", "discount_amount",
                "decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Participant discount amount'");
        addColumnIfMissing("enrollment_order_participant", "discount_detail",
                "json DEFAULT NULL COMMENT 'Participant discount detail'");
        addColumnIfMissing("enrollment_order_participant", "amount_due",
                "decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Participant amount due'");
        addColumnIfMissing("enrollment_order_participant", "paid_amount",
                "decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Participant paid amount'");
        addColumnIfMissing("enrollment_order_participant", "balance_amount",
                "decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Participant balance amount'");
        addColumnIfMissing("enrollment_order_participant", "student_class_snapshot",
                "varchar(64) DEFAULT NULL COMMENT 'Participant class snapshot'");
        addColumnIfMissing("enrollment_order_participant", "guardian_name_snapshot",
                "varchar(128) DEFAULT NULL COMMENT 'Participant guardian name snapshot'");
        addColumnIfMissing("enrollment_order_participant", "guardian_phone_snapshot",
                "varchar(64) DEFAULT NULL COMMENT 'Participant guardian phone snapshot'");
        addColumnIfMissing("enrollment_order_participant", "guardian_phone2_snapshot",
                "varchar(64) DEFAULT NULL COMMENT 'Participant second guardian phone snapshot'");
        addColumnIfMissing("enrollment_order_participant", "guardian_email_snapshot",
                "varchar(128) DEFAULT NULL COMMENT 'Participant guardian email snapshot'");
        addColumnIfMissing("enrollment_order_participant", "pickup_required_snapshot",
                "tinyint(1) DEFAULT NULL COMMENT 'Participant pickup flag snapshot'");
        addColumnIfMissing("enrollment_order_participant", "pickup_address_snapshot",
                "varchar(255) DEFAULT NULL COMMENT 'Participant pickup address snapshot'");
        addColumnIfMissing("enrollment_order_participant", "dropoff_required_snapshot",
                "tinyint(1) DEFAULT NULL COMMENT 'Participant dropoff flag snapshot'");
        addColumnIfMissing("enrollment_order_participant", "dropoff_address_snapshot",
                "varchar(255) DEFAULT NULL COMMENT 'Participant dropoff address snapshot'");
        // 班课报名单删除级联依赖的列（见 doc/migrations/20260828_sync_local_to_production_schema.sql），
        // hour_ledger 的删除走裸列名 class_enrollment_id，线上缺列会导致删除报名单 500。
        addColumnIfMissing("hour_ledger", "class_enrollment_id",
                "bigint DEFAULT NULL COMMENT 'HOURLY class enrollment affected by this ledger row'",
                "class enrollment cascade support");
        addColumnIfMissing("lesson_record", "class_enrollment_id",
                "bigint DEFAULT NULL COMMENT 'Student class enrollment settled by this record'",
                "class enrollment cascade support");
        boolean openingUsedColumnMissing = !columnExists("course_package", "opening_used_units");
        addColumnIfMissing("course_package", "opening_used_units",
                "int NOT NULL DEFAULT 0 COMMENT 'Hours consumed before lesson tracking, in half-hour units' AFTER consumed_units",
                "editable opening used hours on the schedule page");
        if (openingUsedColumnMissing) {
            // 一次性沿用旧收据的历史值；以后排课页修改的值以 course_package 为准。
            jdbcTemplate.update("""
                    UPDATE course_package cp
                    INNER JOIN enrollment_order_item item ON item.id = cp.enrollment_order_item_id
                    SET cp.opening_used_units = LEAST(
                        GREATEST(ROUND(COALESCE(item.opening_used_hours, 0) * 2), 0),
                        GREATEST(COALESCE(cp.consumed_units, 0), 0)
                    )
                    WHERE cp.teaching_mode = 'ONE_ON_ONE'
                      AND item.opening_used_hours IS NOT NULL
                      AND item.opening_used_hours > 0
                    """);
        }
        // 课程级付款分摊（见 doc/migrations/20260919_payment_allocation.sql）
        createPaymentAllocationTableIfMissing();
        addColumnIfMissing("enrollment_order_item", "discount_amount",
                "decimal(12,2) DEFAULT NULL COMMENT 'Discount allocated to this course item' AFTER manual_price_override",
                "course item discount allocation");
        addColumnIfMissing("enrollment_order_item", "amount_due",
                "decimal(12,2) DEFAULT NULL COMMENT 'Amount due for this course item (gross + allocated fees - allocated discount)' AFTER price_amount",
                "course item payment allocation");
        addColumnIfMissing("enrollment_order_item", "paid_amount",
                "decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Cumulative allocated payments for this course item' AFTER amount_due",
                "course item payment allocation");
        addColumnIfMissing("enrollment_order_item", "balance_amount",
                "decimal(12,2) DEFAULT NULL COMMENT 'amount_due - paid_amount' AFTER paid_amount",
                "course item payment allocation");
        addColumnIfMissing("enrollment_order_item", "pickup_required_snapshot",
                "tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Pickup requirement for this course line' AFTER teacher_id",
                "course transport snapshot");
        addColumnIfMissing("enrollment_order_item", "pickup_address_snapshot",
                "varchar(255) DEFAULT NULL COMMENT 'Pickup address for this course line' AFTER pickup_required_snapshot",
                "course transport snapshot");
        addColumnIfMissing("enrollment_order_item", "dropoff_required_snapshot",
                "tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Drop-off requirement for this course line' AFTER pickup_address_snapshot",
                "course transport snapshot");
        addColumnIfMissing("enrollment_order_item", "dropoff_address_snapshot",
                "varchar(255) DEFAULT NULL COMMENT 'Drop-off address for this course line' AFTER dropoff_required_snapshot",
                "course transport snapshot");
        addColumnIfMissing("enrollment_order_item", "pickup_fee_amount",
                "decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Pickup fee for this course line' AFTER dropoff_address_snapshot",
                "course transport snapshot");
        addColumnIfMissing("enrollment_order_item", "dropoff_fee_amount",
                "decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Drop-off fee for this course line' AFTER pickup_fee_amount",
                "course transport snapshot");
        addColumnIfMissing("enrollment_order_item", "extra_fee_amount",
                "decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Other fees for this course line' AFTER dropoff_fee_amount",
                "course transport snapshot");
        addColumnIfMissing("enrollment_order_item", "extra_fee_detail",
                "json DEFAULT NULL COMMENT 'Other fee detail for this course line' AFTER extra_fee_amount",
                "course transport snapshot");
    }

    private void createTablesIfMissing() {
        // Credit 余额是报名页读取和支付扣款的基础表；旧库没有执行发布迁移时也要由应用自动补齐。
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS student_credit_ledger (
                    id bigint NOT NULL,
                    student_id bigint NOT NULL,
                    entry_type varchar(32) NOT NULL,
                    amount decimal(12,2) NOT NULL,
                    source_enrollment_order_id bigint DEFAULT NULL,
                    source_payment_record_id bigint DEFAULT NULL,
                    reference_no varchar(64) DEFAULT NULL,
                    handled_by_name varchar(64) DEFAULT NULL,
                    handled_by_user_id bigint DEFAULT NULL,
                    occurred_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    remark varchar(500) DEFAULT NULL,
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    created_by bigint DEFAULT NULL,
                    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    updated_by bigint DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT 0,
                    deleted_at datetime(3) DEFAULT NULL,
                    deleted_by bigint DEFAULT NULL,
                    version int NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    KEY idx_student_credit_student_time (student_id, occurred_at DESC),
                    KEY idx_student_credit_order (source_enrollment_order_id),
                    CONSTRAINT fk_student_credit_student FOREIGN KEY (student_id) REFERENCES student (id),
                    CONSTRAINT fk_student_credit_order FOREIGN KEY (source_enrollment_order_id) REFERENCES enrollment_order (id),
                    CONSTRAINT fk_student_credit_payment FOREIGN KEY (source_payment_record_id) REFERENCES payment_record (id),
                    CONSTRAINT fk_student_credit_user FOREIGN KEY (handled_by_user_id) REFERENCES sys_user (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS private_lesson_group (
                    id bigint NOT NULL,
                    group_no varchar(32) NOT NULL,
                    enrollment_order_id bigint NOT NULL,
                    enrollment_order_item_id bigint DEFAULT NULL,
                    course_product_id bigint DEFAULT NULL,
                    grade_band varchar(64) DEFAULT NULL,
                    participant_count int NOT NULL DEFAULT 1,
                    second_participant_multiplier decimal(5,4) NOT NULL DEFAULT 0.5000,
                    third_participant_multiplier decimal(5,4) NOT NULL DEFAULT 0.5000,
                    group_status varchar(32) NOT NULL DEFAULT 'ACTIVE',
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    created_by bigint DEFAULT NULL,
                    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    updated_by bigint DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT 0,
                    deleted_at datetime(3) DEFAULT NULL,
                    deleted_by bigint DEFAULT NULL,
                    version int NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_private_lesson_group_no (group_no, is_deleted),
                    KEY idx_private_lesson_group_order (enrollment_order_id, is_deleted),
                    KEY idx_private_lesson_group_product (course_product_id, group_status, is_deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS private_lesson_group_member (
                    id bigint NOT NULL,
                    private_lesson_group_id bigint NOT NULL,
                    student_id bigint NOT NULL,
                    package_id bigint NOT NULL,
                    participant_index int NOT NULL,
                    price_multiplier decimal(5,4) NOT NULL,
                    price_amount decimal(12,2) NOT NULL DEFAULT 0.00,
                    member_status varchar(32) NOT NULL DEFAULT 'ACTIVE',
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    created_by bigint DEFAULT NULL,
                    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    updated_by bigint DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT 0,
                    deleted_at datetime(3) DEFAULT NULL,
                    deleted_by bigint DEFAULT NULL,
                    version int NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_private_lesson_member_index (private_lesson_group_id, participant_index, is_deleted),
                    UNIQUE KEY uk_private_lesson_member_package (private_lesson_group_id, package_id, is_deleted),
                    KEY idx_private_lesson_member_student (student_id, member_status, is_deleted),
                    KEY idx_private_lesson_member_package (package_id, member_status, is_deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS enrollment_order_participant (
                    id bigint NOT NULL,
                    enrollment_order_id bigint NOT NULL,
                    student_id bigint NOT NULL,
                    participant_index int NOT NULL,
                    student_name_snapshot varchar(128) NOT NULL,
                    student_no_snapshot varchar(32) DEFAULT NULL,
                    grade_snapshot varchar(64) DEFAULT NULL,
                    school_snapshot varchar(128) DEFAULT NULL,
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    created_by bigint DEFAULT NULL,
                    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    updated_by bigint DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT 0,
                    deleted_at datetime(3) DEFAULT NULL,
                    deleted_by bigint DEFAULT NULL,
                    version int NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_order_participant_index (enrollment_order_id, participant_index, is_deleted),
                    KEY idx_order_participant_student (student_id, is_deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }

    private void createPaymentAllocationTableIfMissing() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'payment_allocation'
                """, Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE payment_allocation (
                    id bigint NOT NULL,
                    payment_record_id bigint NOT NULL,
                    enrollment_order_id bigint NOT NULL,
                    enrollment_order_item_id bigint NOT NULL,
                    amount decimal(12,2) NOT NULL,
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    created_by bigint DEFAULT NULL,
                    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    updated_by bigint DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT 0,
                    deleted_at datetime(3) DEFAULT NULL,
                    deleted_by bigint DEFAULT NULL,
                    version int NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    KEY idx_payment_alloc_payment (payment_record_id),
                    KEY idx_payment_alloc_item (enrollment_order_item_id),
                    KEY idx_payment_alloc_order (enrollment_order_id),
                    CONSTRAINT fk_payment_alloc_payment FOREIGN KEY (payment_record_id) REFERENCES payment_record (id),
                    CONSTRAINT fk_payment_alloc_item FOREIGN KEY (enrollment_order_item_id) REFERENCES enrollment_order_item (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        log.info("Created table payment_allocation for course item payment allocation.");
    }

    private void createTransportTablesIfMissing() {
        // 接送司机属于接送名单的配置数据，应用启动时自动补齐，避免新环境漏执行迁移脚本。
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS transport_driver (
                    id bigint NOT NULL,
                    driver_name varchar(128) NOT NULL,
                    display_order int NOT NULL DEFAULT 99,
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    created_by bigint DEFAULT NULL,
                    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    updated_by bigint DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT 0,
                    deleted_at datetime(3) DEFAULT NULL,
                    deleted_by bigint DEFAULT NULL,
                    version int NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_transport_driver_name (driver_name, is_deleted),
                    KEY idx_transport_driver_order (display_order, is_deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS transport_driver_assignment (
                    id bigint NOT NULL,
                    assignment_key varchar(255) NOT NULL,
                    order_id bigint NOT NULL,
                    order_item_id bigint DEFAULT NULL,
                    student_id bigint NOT NULL,
                    participant_index int NOT NULL DEFAULT 1,
                    transport_type varchar(16) NOT NULL DEFAULT 'BOTH',
                    driver_id bigint DEFAULT NULL,
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    created_by bigint DEFAULT NULL,
                    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    updated_by bigint DEFAULT NULL,
                    is_deleted tinyint(1) NOT NULL DEFAULT 0,
                    deleted_at datetime(3) DEFAULT NULL,
                    deleted_by bigint DEFAULT NULL,
                    version int NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_transport_assignment_key (assignment_key, is_deleted),
                    KEY idx_transport_assignment_driver (driver_id, is_deleted),
                    KEY idx_transport_assignment_student (student_id, is_deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        addColumnIfMissing("transport_driver_assignment", "transport_type",
                "varchar(16) NOT NULL DEFAULT 'BOTH' COMMENT 'Pickup or drop-off assignment direction'",
                "independent pickup/drop-off driver assignments");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        addColumnIfMissing(tableName, columnName, definition, "shared private lesson support");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition, String purpose) {
        if (columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN `" + columnName + "` " + definition);
        log.info("Added {}.{} for {}.", tableName, columnName, purpose);
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
