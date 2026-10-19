package com.classdeduction.system.modules.lesson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordScheduleEffectEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LessonRecordScheduleEffectMapper extends BaseMapper<LessonRecordScheduleEffectEntity> {

    default List<LessonRecordScheduleEffectEntity> findBySourceLessonRecordId(Long sourceLessonRecordId) {
        return selectList(new LambdaQueryWrapper<LessonRecordScheduleEffectEntity>()
                .eq(LessonRecordScheduleEffectEntity::getSourceLessonRecordId, sourceLessonRecordId)
                .orderByAsc(LessonRecordScheduleEffectEntity::getId));
    }
}
