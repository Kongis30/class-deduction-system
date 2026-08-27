-- Migration: make course_package.default_lesson_units nullable with a default.
--
-- Context: production 1-on-1 registration submits failed with 500
--   "Field 'default_lesson_units' doesn't have a default value"
-- because the deployed course_package table still had default_lesson_units as
-- NOT NULL without a default, while CoursePackageEntity does not set the column
-- on insert (it is a legacy field; duration is now defined per schedule slot).
--
-- The current schema (init_schema_current.sql) already declares it as
--   `default_lesson_units` int DEFAULT NULL
-- This migration brings older deployed databases in line so inserts succeed.
--
-- Idempotent: re-running MODIFY to the same definition is a no-op.

ALTER TABLE `course_package`
    MODIFY COLUMN `default_lesson_units` int DEFAULT NULL
    COMMENT 'Legacy default lesson units; new schedules define duration per slot';
