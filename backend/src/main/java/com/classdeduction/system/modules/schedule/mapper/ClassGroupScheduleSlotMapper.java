package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClassGroupScheduleSlotMapper extends BaseMapper<ClassGroupScheduleSlotEntity> {
    @Select("SELECT * FROM class_group_schedule_slot WHERE id = #{id}")
    ClassGroupScheduleSlotEntity selectIncludingDeletedById(Long id);
}
