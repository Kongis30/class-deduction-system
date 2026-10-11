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

    /** 历史课次仍可能引用已软删除的排课时段，读取其原科目用于签字表和消课列表。 */
    @Select("""
            <script>
            SELECT id, teacher_id, subject_name
            FROM recurring_schedule_slot
            WHERE id IN
            <foreach collection="ids" item="slotId" open="(" separator="," close=")">
              #{slotId}
            </foreach>
            </script>
            """)
    List<RecurringScheduleSlotEntity> selectIncludingDeletedByIds(@Param("ids") Collection<Long> ids);
}
