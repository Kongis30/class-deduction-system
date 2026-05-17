package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecurringScheduleRuleMapper extends BaseMapper<RecurringScheduleRuleEntity> {
}
