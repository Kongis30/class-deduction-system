package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface LessonOccurrenceMapper extends BaseMapper<LessonOccurrenceEntity> {

    /** 打印变更历史时包含已软删除课次，避免后续重新排课隐藏早先的调整记录。 */
    @Select("<script>SELECT id, schedule_rule_id FROM lesson_occurrence WHERE schedule_rule_id IN "
            + "<foreach collection='ruleIds' item='ruleId' open='(' separator=',' close=')'>#{ruleId}</foreach></script>")
    List<LessonOccurrenceEntity> selectIncludingDeletedByRuleIds(@Param("ruleIds") List<Long> ruleIds);

    /** 调课、确认、撤销和补录共用课次行锁，避免基于旧状态互相覆盖。 */
    @Select("SELECT * FROM lesson_occurrence WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    LessonOccurrenceEntity selectByIdForUpdate(@Param("id") Long id);

    /** 改排课只能撤销尚未开始且未进入消课流程的普通课次；条件在写入时再次校验。 */
    @Update({"<script>",
            "UPDATE lesson_occurrence lo SET lo.is_deleted = 1, lo.deleted_at = #{effectiveAt},",
            "lo.updated_at = #{effectiveAt}",
            "WHERE lo.id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND lo.is_deleted = 0 AND lo.planning_status = 'SCHEDULED'",
            "AND lo.planned_start_at &gt;= #{effectiveAt}",
            "AND NOT EXISTS (SELECT 1 FROM lesson_record lr",
            "WHERE lr.occurrence_id = lo.id AND lr.is_deleted = 0)",
            "</script>"})
    int softDeleteFutureUnprocessed(@Param("ids") List<Long> ids,
                                    @Param("effectiveAt") LocalDateTime effectiveAt);

    /** 周期排课教室变更时允许显式清空旧教室，同时保留课次的其他单次调整字段。 */
    @Update("UPDATE lesson_occurrence SET planned_classroom_id = #{classroomId}, delivery_mode = #{deliveryMode}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND is_deleted = 0 "
            + "AND planned_start_at >= #{effectiveAt} AND planning_status = 'SCHEDULED' "
            + "AND NOT EXISTS (SELECT 1 FROM lesson_record lr WHERE lr.occurrence_id = lesson_occurrence.id AND lr.is_deleted = 0)")
    int updateInheritedClassroom(@Param("id") Long id,
                                 @Param("classroomId") Long classroomId,
                                 @Param("deliveryMode") String deliveryMode,
                                 @Param("effectiveAt") LocalDateTime effectiveAt);

    /** 周排课信息调整只同步未来普通课次，保留单次教室覆盖和课次身份。 */
    @Update("UPDATE lesson_occurrence SET planned_teacher_id = #{teacherId}, subject_name = #{subjectName}, "
            + "delivery_mode = IF(classroom_overridden = 1, delivery_mode, #{deliveryMode}), "
            + "planned_classroom_id = IF(classroom_overridden = 1, planned_classroom_id, #{classroomId}), "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND is_deleted = 0 "
            + "AND planned_start_at >= #{effectiveAt} AND planning_status = 'SCHEDULED' "
            + "AND NOT EXISTS (SELECT 1 FROM lesson_record lr WHERE lr.occurrence_id = lesson_occurrence.id AND lr.is_deleted = 0)")
    int updateRecurringDetails(@Param("id") Long id,
                               @Param("teacherId") Long teacherId,
                               @Param("subjectName") String subjectName,
                               @Param("classroomId") Long classroomId,
                               @Param("deliveryMode") String deliveryMode,
                               @Param("effectiveAt") LocalDateTime effectiveAt);

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
