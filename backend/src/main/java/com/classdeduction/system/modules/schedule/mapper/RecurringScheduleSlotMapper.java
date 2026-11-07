package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RecurringScheduleSlotMapper extends BaseMapper<RecurringScheduleSlotEntity> {

    /** 更新时段课程信息时保留时段 ID 和累计分配额。 */
    @Update("UPDATE recurring_schedule_slot SET teacher_id = #{teacherId}, delivery_mode = #{deliveryMode}, "
            + "classroom_id = #{classroomId}, subject_name = #{subjectName}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND is_deleted = 0")
    int updateCourseDetails(@Param("id") Long id,
                            @Param("teacherId") Long teacherId,
                            @Param("deliveryMode") String deliveryMode,
                            @Param("classroomId") Long classroomId,
                            @Param("subjectName") String subjectName);

    /** 旧周期请假释放的课时进入当前课位；原位递增保证并发确认不会覆盖分配额。 */
    @Update("UPDATE recurring_schedule_slot SET allocated_units = COALESCE(allocated_units, 0) + #{units}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{slotId} AND rule_id = #{ruleId} "
            + "AND slot_status = 'ACTIVE' AND is_deleted = 0")
    int addLeaveCarryover(@Param("slotId") Long slotId, @Param("ruleId") Long ruleId,
                          @Param("units") int units);

    /** 撤销同周期旧课请假时，回退对应课位的自动增加额。 */
    @Update("UPDATE recurring_schedule_slot SET allocated_units = COALESCE(allocated_units, 0) - #{units}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{slotId} AND rule_id = #{ruleId} "
            + "AND COALESCE(allocated_units, 0) >= #{units} AND slot_status = 'ACTIVE' AND is_deleted = 0")
    int removeLeaveCarryover(@Param("slotId") Long slotId, @Param("ruleId") Long ruleId,
                             @Param("units") int units);

    /** 同一排课周期内调整分配额，使用旧值做并发保护并保留课位 ID。 */
    @Update("UPDATE recurring_schedule_slot SET allocated_units = #{units}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{slotId} AND rule_id = #{ruleId} "
            + "AND COALESCE(allocated_units, 0) = #{expectedUnits} "
            + "AND slot_status = 'ACTIVE' AND is_deleted = 0")
    int updateAllocatedUnits(@Param("slotId") Long slotId, @Param("ruleId") Long ruleId,
                             @Param("expectedUnits") int expectedUnits, @Param("units") int units);

    /** 重排合并历史课位时，将旧 lineage 指向新周期的唯一继承 lineage。 */
    @Update("UPDATE recurring_schedule_slot SET slot_lineage_id = #{targetLineageId}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE slot_lineage_id = #{sourceLineageId}")
    int reassignSlotLineage(@Param("sourceLineageId") Long sourceLineageId,
                            @Param("targetLineageId") Long targetLineageId);

    /** 读取已软删除的旧时段，供保留课次继续匹配原周期配置。 */
    @Select("""
            <script>
            SELECT *
            FROM recurring_schedule_slot
            WHERE id IN
            <foreach collection="ids" item="slotId" open="(" separator="," close=")">
              #{slotId}
            </foreach>
            </script>
            """)
    List<RecurringScheduleSlotEntity> selectIncludingDeletedByIds(@Param("ids") Collection<Long> ids);

    /** 打印历史必须读取已软删除的旧时段快照。 */
    @Select("<script>SELECT * FROM recurring_schedule_slot WHERE rule_id IN "
            + "<foreach collection='ruleIds' item='ruleId' open='(' separator=',' close=')'>#{ruleId}</foreach> "
            + "ORDER BY weekday, start_time</script>")
    List<RecurringScheduleSlotEntity> selectIncludingDeletedByRuleIds(@Param("ruleIds") Collection<Long> ruleIds);
}
