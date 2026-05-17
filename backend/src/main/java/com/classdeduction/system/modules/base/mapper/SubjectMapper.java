package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.SubjectEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Subject mapper.
 */
@Mapper
public interface SubjectMapper extends BaseMapper<SubjectEntity> {
}
