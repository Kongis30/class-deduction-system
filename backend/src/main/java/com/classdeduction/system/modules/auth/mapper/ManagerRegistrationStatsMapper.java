package com.classdeduction.system.modules.auth.mapper;

import com.classdeduction.system.modules.auth.vo.ManagerRegistrationStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ManagerRegistrationStatsMapper {

    /** 按报名经手账号批量统计历史报名单数和去重学生数，避免管理者列表逐行查库。 */
    @Select("""
            <script>
            SELECT registrations.user_id,
                   COUNT(DISTINCT registrations.order_id) AS registration_count,
                   COUNT(DISTINCT registrations.student_id) AS unique_student_count
            FROM (
                SELECT COALESCE(o.registration_operator_user_id, o.cashier_user_id, o.created_by) AS user_id,
                       o.id AS order_id,
                       COALESCE(op.student_id, o.student_id) AS student_id
                FROM enrollment_order o
                LEFT JOIN enrollment_order_participant op
                  ON op.enrollment_order_id = o.id AND op.is_deleted = 0
                WHERE o.is_deleted = 0
                  AND COALESCE(o.registration_operator_user_id, o.cashier_user_id, o.created_by) IN
                  <foreach collection="operatorUserIds" item="userId" open="(" separator="," close=")">
                    #{userId}
                  </foreach>
            ) registrations
            GROUP BY registrations.user_id
            </script>
            """)
    @Results(id = "managerRegistrationStats", value = {
            @Result(column = "user_id", property = "userId"),
            @Result(column = "registration_count", property = "registrationCount"),
            @Result(column = "unique_student_count", property = "uniqueStudentCount")
    })
    List<ManagerRegistrationStatsVO> selectStatsByOperatorUserIds(
            @Param("operatorUserIds") List<Long> operatorUserIds);
}
