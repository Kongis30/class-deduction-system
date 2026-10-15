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

    /** 读取已软删除的旧时段，供保留课次继续匹配原周期配置。 */
    @Select("""
            <script>
            SELECT id, teacher_id, subject_name, weekday, start_time, end_time
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
