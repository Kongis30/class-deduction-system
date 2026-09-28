package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.vo.OneOnOneScheduleCourseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CoursePackageMapper extends BaseMapper<CoursePackageEntity> {

    /** 查询排课侧栏的在读及停课课包；共享课只返回主课包，并附带完整成员信息。 */
    @Select("""
            SELECT s.id AS studentId,
                   s.student_name AS studentName,
                   COALESCE(NULLIF(TRIM(cp.product_name), ''), NULLIF(TRIM(pkg.package_name), ''), 'Unassigned course') AS courseName,
                   COALESCE(NULLIF(TRIM(oi.grade_band), ''), 'General') AS gradeBand,
                   EXISTS (
                       SELECT 1
                       FROM recurring_schedule_rule r
                       WHERE r.package_id = pkg.id
                         AND r.rule_status = 'ACTIVE'
                         AND r.is_deleted = 0
                   ) AS hasSchedule,
                   pkg.id AS packageId,
                   pkg.package_status AS packageStatus,
                   primary_member.private_lesson_group_id AS privateLessonGroupId,
                   CASE WHEN primary_member.id IS NULL THEN 1
                        ELSE COUNT(DISTINCT group_member.student_id)
                   END AS participantCount,
                   COALESCE(GROUP_CONCAT(participant.student_name ORDER BY group_member.participant_index SEPARATOR ' / '), s.student_name) AS participantNames,
                   COALESCE(GROUP_CONCAT(DISTINCT CAST(group_member.student_id AS CHAR) ORDER BY group_member.participant_index SEPARATOR ','), CAST(s.id AS CHAR)) AS participantStudentIds
            FROM course_package pkg
            JOIN student s ON s.id = pkg.student_id
            LEFT JOIN course_product cp ON cp.id = pkg.course_product_id
            LEFT JOIN enrollment_order_item oi ON oi.id = pkg.enrollment_order_item_id AND oi.is_deleted = 0
            LEFT JOIN private_lesson_group_member primary_member
                   ON primary_member.package_id = pkg.id
                  AND primary_member.member_status = 'ACTIVE'
                  AND primary_member.is_deleted = 0
            LEFT JOIN private_lesson_group_member group_member
                   ON group_member.private_lesson_group_id = primary_member.private_lesson_group_id
                  AND group_member.member_status = 'ACTIVE'
                  AND group_member.is_deleted = 0
            LEFT JOIN student participant
                   ON participant.id = group_member.student_id
            WHERE s.status = 'ACTIVE'
              AND s.is_deleted = 0
              AND pkg.teaching_mode = 'ONE_ON_ONE'
              AND pkg.package_status IN ('ACTIVE', 'PAUSED')
              AND pkg.is_deleted = 0
              AND (primary_member.id IS NULL OR primary_member.participant_index = 1)
            GROUP BY s.id, s.student_name, courseName, gradeBand, pkg.id, primary_member.id, primary_member.private_lesson_group_id
            ORDER BY s.student_no, s.id, courseName, gradeBand, pkg.id
            """)
    List<OneOnOneScheduleCourseRow> selectSchedulableOneOnOneStudentCourseGrades();

    /**
     * 按校区统计低余量课包数（通过 student JOIN 推算校区归属，批量GROUP BY，避免N+1）
     */
    @Select("SELECT s.campus_id, COUNT(*) AS cnt " +
            "FROM course_package pkg " +
            "JOIN student s ON pkg.student_id = s.id " +
            "WHERE pkg.package_status = 'ACTIVE' " +
            "AND pkg.remaining_units <= #{threshold} " +
            "GROUP BY s.campus_id")
    List<Map<String, Object>> countLowBalanceByCampus(@Param("threshold") Integer threshold);
}
