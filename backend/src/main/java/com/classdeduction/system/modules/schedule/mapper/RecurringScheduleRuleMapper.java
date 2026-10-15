package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RecurringScheduleRuleMapper extends BaseMapper<RecurringScheduleRuleEntity> {

    /** 清空教室或备注时也要将 NULL 保存到规则。 */
    @Update("UPDATE recurring_schedule_rule SET default_classroom_id = #{classroomId}, notes = #{notes}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND is_deleted = 0")
    int updateCourseDetails(@Param("id") Long id,
                            @Param("classroomId") Long classroomId,
                            @Param("notes") String notes);
}
