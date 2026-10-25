package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LessonAdjustmentLogMapper extends BaseMapper<LessonAdjustmentLogEntity> {
    /** 旧版班课调课没有原日期字段时，从首次调整快照还原原模板课位。 */
    @Select({"<script>",
            "SELECT log.occurrence_id AS occurrence_id,",
            "JSON_UNQUOTE(JSON_EXTRACT(log.before_snapshot, '$.plannedStartAt')) AS original_start_at,",
            "JSON_UNQUOTE(JSON_EXTRACT(log.before_snapshot, '$.scheduleSlotId')) AS original_slot_id",
            "FROM lesson_adjustment_log log WHERE log.occurrence_id IN",
            "<foreach collection='occurrenceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND log.id = (SELECT first_log.id FROM lesson_adjustment_log first_log",
            "WHERE first_log.occurrence_id = log.occurrence_id",
            "ORDER BY first_log.adjusted_at, first_log.id LIMIT 1)",
            "</script>"})
    List<Map<String, Object>> selectFirstClassAdjustmentSnapshots(@Param("occurrenceIds") List<Long> occurrenceIds);
}
