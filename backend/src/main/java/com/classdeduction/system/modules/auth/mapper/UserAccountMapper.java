package com.classdeduction.system.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {

    /**
     * 查询指定类型的已软删除登录账号（绕过 @TableLogic 自动过滤）。
     */
    @Select("SELECT * FROM sys_user WHERE is_deleted = 1 AND user_type = #{userType} "
            + "ORDER BY deleted_at DESC, updated_at DESC")
    List<UserAccountEntity> selectDeletedByType(@Param("userType") String userType);
}
