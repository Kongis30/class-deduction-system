package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.vo.RecurringSlotSubjectHistoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
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

    /** 零余额只能回收未开始的普通私教课，写入时再次排除调课、共享课及新产生的记录。 */
    @Update({"<script>",
            "UPDATE lesson_occurrence lo SET lo.is_deleted = 1, lo.deleted_at = #{effectiveAt},",
            "lo.updated_at = #{effectiveAt}",
            "WHERE lo.id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND lo.is_deleted = 0 AND lo.class_group_id IS NULL AND lo.private_lesson_group_id IS NULL",
            "AND lo.occurrence_type = 'REGULAR' AND lo.planning_status = 'SCHEDULED'",
            "AND lo.planned_start_at &gt;= #{effectiveAt}",
            "AND NOT EXISTS (SELECT 1 FROM lesson_record lr WHERE lr.occurrence_id = lo.id AND lr.is_deleted = 0)",
            "</script>"})
    int softDeleteFutureRegularUnprocessed(@Param("ids") List<Long> ids,
                                           @Param("effectiveAt") LocalDateTime effectiveAt);

    /** 管理员继续改排课时回收已结束的未确认私教课；只软删除且写入时再次排除已确认记录。 */
    @Update({"<script>",
            "UPDATE lesson_occurrence lo SET lo.is_deleted = 1, lo.deleted_at = #{effectiveAt},",
            "lo.updated_at = #{effectiveAt}",
            "WHERE lo.id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND lo.is_deleted = 0 AND lo.class_group_id IS NULL AND lo.planning_status &lt;&gt; 'CANCELLED'",
            "AND lo.planned_end_at &lt;= #{effectiveAt}",
            "AND NOT EXISTS (SELECT 1 FROM lesson_record lr",
            "WHERE lr.occurrence_id = lo.id AND lr.is_deleted = 0)",
            "</script>"})
    int softDeleteEndedUnconfirmedPrivateLessons(@Param("ids") List<Long> ids,
                                                  @Param("effectiveAt") LocalDateTime effectiveAt);

    /** 分配额缩减时移除末尾普通课，同时让后续增额可以在该日期重新生成课次。 */
    @Update("UPDATE lesson_occurrence SET planning_status = 'CANCELLED', is_deleted = 1, deleted_at = #{effectiveAt}, "
            + "updated_at = #{effectiveAt} WHERE id = #{id} AND is_deleted = 0 "
            + "AND planning_status = 'SCHEDULED' AND planned_start_at >= #{effectiveAt} "
            + "AND NOT EXISTS (SELECT 1 FROM lesson_record lr "
            + "WHERE lr.occurrence_id = lesson_occurrence.id AND lr.is_deleted = 0)")
    int softDeleteAllocationTrimmedOccurrence(@Param("id") Long id,
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
    @Update("UPDATE lesson_occurrence SET allocation_owner_teacher_id = IF(planned_teacher_id <=> #{teacherId}, allocation_owner_teacher_id, #{teacherId}), "
            + "planned_teacher_id = #{teacherId}, subject_name = #{subjectName}, "
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

    /** 老师交接时将未来未处理例外并入新规则，保留单课信息并转移额度归属。 */
    @Update("UPDATE lesson_occurrence SET schedule_rule_id = #{targetRuleId}, schedule_slot_id = #{targetSlotId}, "
            + "allocation_anchor_date = #{targetAnchorDate}, "
            + "planned_teacher_id = #{teacherId}, "
            + "allocation_owner_teacher_id = #{teacherId}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND is_deleted = 0 AND planned_start_at >= #{effectiveAt} "
            + "AND planning_status <> 'CANCELLED' "
            + "AND schedule_rule_id = #{expectedRuleId} AND schedule_slot_id = #{expectedSlotId} "
            + "AND planned_teacher_id <=> #{expectedTeacherId} "
            + "AND NOT EXISTS (SELECT 1 FROM lesson_record lr "
            + "WHERE lr.occurrence_id = lesson_occurrence.id AND lr.is_deleted = 0)")
    int handoffFutureOccurrence(@Param("id") Long id,
                                @Param("expectedRuleId") Long expectedRuleId,
                                @Param("expectedSlotId") Long expectedSlotId,
                                @Param("targetRuleId") Long targetRuleId,
                                @Param("targetSlotId") Long targetSlotId,
                                @Param("targetAnchorDate") LocalDate targetAnchorDate,
                                @Param("expectedTeacherId") Long expectedTeacherId,
                                @Param("teacherId") Long teacherId,
                                @Param("effectiveAt") LocalDateTime effectiveAt);

    /** 读取排课时段的课程名称变更链，用于还原快照缺失的历史课次名称。 */
    @Select({"<script>",
            "SELECT occurrence.schedule_slot_id AS schedule_slot_id, log.adjusted_at AS adjusted_at,",
            "JSON_UNQUOTE(JSON_EXTRACT(log.before_snapshot, '$.subjectName')) AS before_subject_name,",
            "JSON_UNQUOTE(JSON_EXTRACT(log.after_snapshot, '$.subjectName')) AS after_subject_name",
            "FROM lesson_adjustment_log log",
            "JOIN lesson_occurrence occurrence ON occurrence.id = log.occurrence_id",
            "WHERE occurrence.schedule_slot_id IN",
            "<foreach collection='slotIds' item='slotId' open='(' separator=',' close=')'>#{slotId}</foreach>",
            "AND log.adjustment_type = 'SCHEDULE_DETAILS_UPDATE'",
            "ORDER BY log.adjusted_at, log.id",
            "</script>"})
    @Results(id = "recurringSlotSubjectHistory", value = {
            @Result(property = "scheduleSlotId", column = "schedule_slot_id"),
            @Result(property = "adjustedAt", column = "adjusted_at"),
            @Result(property = "beforeSubjectName", column = "before_subject_name"),
            @Result(property = "afterSubjectName", column = "after_subject_name")
    })
    List<RecurringSlotSubjectHistoryVO> selectSubjectHistoryByScheduleSlotIds(@Param("slotIds") List<Long> slotIds);

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
