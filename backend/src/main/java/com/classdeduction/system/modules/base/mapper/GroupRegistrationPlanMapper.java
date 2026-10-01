package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.GroupRegistrationPlanEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GroupRegistrationPlanMapper extends BaseMapper<GroupRegistrationPlanEntity> {

    /** 报名/维护方案共用行锁，避免并发更改价格、课次或状态。 */
    @Select("SELECT * FROM group_registration_plan WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    GroupRegistrationPlanEntity selectByIdForUpdate(@Param("id") Long id);
}
