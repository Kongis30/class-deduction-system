package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentCorrectionLogEntity;
import org.apache.ibatis.annotations.Mapper;

/** 保存原报名更正前后的业务快照，供后续追溯。 */
@Mapper
public interface EnrollmentCorrectionLogMapper extends BaseMapper<EnrollmentCorrectionLogEntity> {
}
