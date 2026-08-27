package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.CourseCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Course category mapper.
 */
@Mapper
public interface CourseCategoryMapper extends BaseMapper<CourseCategoryEntity> {
}
