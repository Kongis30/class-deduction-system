package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface LessonOccurrenceMapper extends BaseMapper<LessonOccurrenceEntity> {

    /**
     * 按校区统计今日待上课次数（批量GROUP BY JOIN，避免N+1）。
     * 通过 classroom JOIN 推算校区归属，并排除已核销的记录。
     *
     * @param todayStart 今日起始时间
     * @param todayEnd   今日结束时间
     * @param excludeIds 已核销的 occurrence ID 列表（为空时不排除）
     */
    @Select({"<script>",
            "SELECT cr.campus_id, COUNT(*) AS cnt",
            "FROM lesson_occurrence lo",
            "JOIN classroom cr ON lo.planned_classroom_id = cr.id",
            "WHERE lo.planned_start_at &gt;= #{todayStart}",
            "AND lo.planned_end_at &lt; #{todayEnd}",
            "AND lo.planning_status = 'SCHEDULED'",
            "<if test='excludeIds != null and excludeIds.size() &gt; 0'>",
            "AND lo.id NOT IN",
            "<foreach item='id' collection='excludeIds' open='(' separator=',' close=')'>#{id}</foreach>",
            "</if>",
            "GROUP BY cr.campus_id",
            "</script>"})
    List<Map<String, Object>> countTodayByCampus(@Param("todayStart") LocalDateTime todayStart,
                                                  @Param("todayEnd") LocalDateTime todayEnd,
                                                  @Param("excludeIds") List<Long> excludeIds);
}
