package com.classdeduction.system.modules.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.transport.entity.TransportDriverEntity;
import org.apache.ibatis.annotations.Mapper;

/** 接送司机档案数据访问。 */
@Mapper
public interface TransportDriverMapper extends BaseMapper<TransportDriverEntity> {
}
