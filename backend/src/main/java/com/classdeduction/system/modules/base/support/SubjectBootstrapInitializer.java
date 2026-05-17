package com.classdeduction.system.modules.base.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Subject table bootstrap initializer.
 * Creates the subject table and inserts initial data on startup if not exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubjectBootstrapInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Check if table exists
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'subject'",
                Integer.class
            );

            if (count == null || count == 0) {
                log.info("Subject table not found, creating...");
                createSubjectTable();
                insertInitialSubjects();
                log.info("Subject table created and initial data inserted.");
            } else {
                // Check if has data
                Long dataCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subject", Long.class);
                if (dataCount == null || dataCount == 0) {
                    log.info("Subject table exists but empty, inserting initial data...");
                    insertInitialSubjects();
                    log.info("Initial subjects inserted.");
                }
            }
        } catch (Exception e) {
            log.error("Failed to bootstrap subject table", e);
        }
    }

    private void createSubjectTable() {
        String sql = """
            CREATE TABLE `subject` (
              `id` BIGINT NOT NULL COMMENT 'Primary key ID',
              `subject_name` VARCHAR(64) NOT NULL COMMENT 'Subject name',
              `display_order` INT NOT NULL DEFAULT 99 COMMENT 'Display order for sorting',
              `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
              `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
              `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
              `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
              `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
              `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
              `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
              `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
              `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
              `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_subject_name` (`subject_name`),
              KEY `idx_subject_status` (`status`),
              KEY `idx_subject_display_order` (`display_order`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course subjects (Math, English, Science, SAT, etc.)'
            """;
        jdbcTemplate.execute(sql);
    }

    private void insertInitialSubjects() {
        String sql = """
            INSERT INTO `subject` (`id`, `subject_name`, `display_order`, `status`, `notes`) VALUES
            (1, 'Math', 1, 'ACTIVE', 'Mathematics courses including algebra, geometry, calculus, SHSAT math, etc.'),
            (2, 'English', 2, 'ACTIVE', 'English language arts, reading, writing, ELA test prep.'),
            (3, 'Science', 3, 'ACTIVE', 'Science courses including physics, chemistry, biology.'),
            (4, 'SAT', 4, 'ACTIVE', 'SAT preparation courses.'),
            (5, 'Music', 5, 'ACTIVE', 'Music lessons including piano, guitar, violin, etc.'),
            (6, 'Reading', 6, 'ACTIVE', 'Reading comprehension and literacy skills.')
            ON DUPLICATE KEY UPDATE
              `subject_name` = VALUES(`subject_name`),
              `display_order` = VALUES(`display_order`),
              `status` = VALUES(`status`),
              `notes` = VALUES(`notes`)
            """;
        jdbcTemplate.execute(sql);
    }
}
