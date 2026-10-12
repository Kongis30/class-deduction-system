package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RecurringScheduleSlotMapper extends BaseMapper<RecurringScheduleSlotEntity> {

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
}
