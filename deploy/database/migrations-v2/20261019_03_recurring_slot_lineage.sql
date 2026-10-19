-- 为每个周排课时段建立跨版本的稳定身份，支持换老师后由新老师接手完整课程记录。
ALTER TABLE recurring_schedule_slot
    ADD COLUMN slot_lineage_id BIGINT NULL
        COMMENT 'Stable weekly slot identity across schedule versions';

-- 旧 slot 先以自身 ID 作为 lineage；之后换老师保存时，新版本沿用该 ID。
UPDATE recurring_schedule_slot
SET slot_lineage_id = id
WHERE slot_lineage_id IS NULL;

-- 无歧义时将当前 slot 的 lineage 向前传到直接前一版本；应用启动兼容器会继续处理更早版本链。
UPDATE recurring_schedule_slot historical
JOIN recurring_schedule_rule next_rule
  ON next_rule.supersedes_rule_id = historical.rule_id
 AND next_rule.is_deleted = 0
JOIN recurring_schedule_slot current_slot
  ON current_slot.rule_id = next_rule.id
 AND current_slot.is_deleted = 0
 AND current_slot.slot_status = 'ACTIVE'
 AND current_slot.weekday = historical.weekday
 AND current_slot.start_time = historical.start_time
 AND current_slot.end_time = historical.end_time
 AND current_slot.planned_units = historical.planned_units
JOIN (SELECT rule_id, weekday, start_time, end_time, planned_units, COUNT(*) AS slot_count
      FROM recurring_schedule_slot
      GROUP BY rule_id, weekday, start_time, end_time, planned_units) historical_count
  ON historical_count.rule_id = historical.rule_id
 AND historical_count.weekday = historical.weekday
 AND historical_count.start_time = historical.start_time
 AND historical_count.end_time = historical.end_time
 AND historical_count.planned_units = historical.planned_units
 AND historical_count.slot_count = 1
JOIN (SELECT rule_id, weekday, start_time, end_time, planned_units, COUNT(*) AS slot_count
      FROM recurring_schedule_slot
      WHERE is_deleted = 0 AND slot_status = 'ACTIVE'
      GROUP BY rule_id, weekday, start_time, end_time, planned_units) current_count
  ON current_count.rule_id = current_slot.rule_id
 AND current_count.weekday = current_slot.weekday
 AND current_count.start_time = current_slot.start_time
 AND current_count.end_time = current_slot.end_time
 AND current_count.planned_units = current_slot.planned_units
 AND current_count.slot_count = 1
SET historical.slot_lineage_id = current_slot.slot_lineage_id
WHERE historical.slot_lineage_id <> current_slot.slot_lineage_id;

CREATE INDEX idx_schedule_slot_lineage
    ON recurring_schedule_slot (slot_lineage_id, rule_id);
