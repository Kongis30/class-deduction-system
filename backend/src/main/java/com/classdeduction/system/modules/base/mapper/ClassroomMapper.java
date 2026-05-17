package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClassroomMapper extends BaseMapper<ClassroomEntity> {
}
