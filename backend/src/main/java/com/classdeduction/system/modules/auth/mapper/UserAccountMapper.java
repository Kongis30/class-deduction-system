package com.classdeduction.system.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {

    /** 查询当前可登录且具备管理员身份的账号，供系统级消息逐人入箱。 */
    @Select("""
            SELECT DISTINCT u.*
            FROM sys_user u
            LEFT JOIN sys_user_role ur ON ur.user_id = u.id
            LEFT JOIN sys_role r ON r.id = ur.role_id AND r.is_deleted = 0
            WHERE u.status = 'ACTIVE'
              AND u.is_deleted = 0
              AND (u.user_type = 'ADMIN' OR r.role_code = 'ADMIN')
            ORDER BY u.id
            """)
    List<UserAccountEntity> selectActiveAdmins();

    /**
     * 查询指定类型的已软删除登录账号（绕过 @TableLogic 自动过滤）。
     */
    @Select("SELECT * FROM sys_user WHERE is_deleted = 1 AND user_type = #{userType} "
            + "ORDER BY deleted_at DESC, updated_at DESC")
    List<UserAccountEntity> selectDeletedByType(@Param("userType") String userType);
}
