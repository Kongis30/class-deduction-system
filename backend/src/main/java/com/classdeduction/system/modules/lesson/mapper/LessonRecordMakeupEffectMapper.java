package com.classdeduction.system.modules.lesson.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordMakeupEffectEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LessonRecordMakeupEffectMapper extends BaseMapper<LessonRecordMakeupEffectEntity> {

    default List<LessonRecordMakeupEffectEntity> findBySourceLessonRecordId(Long sourceLessonRecordId) {
        return selectList(new LambdaQueryWrapper<LessonRecordMakeupEffectEntity>()
                .eq(LessonRecordMakeupEffectEntity::getSourceLessonRecordId, sourceLessonRecordId)
                .orderByAsc(LessonRecordMakeupEffectEntity::getId));
    }
}
