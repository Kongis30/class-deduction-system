package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.modules.base.dto.StudentPageQuery;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentMapper extends BaseMapper<StudentEntity> {

    @Select("""
            <script>
            SELECT *
            FROM (
                SELECT
                    s.id AS id,
                    s.student_no AS studentNo,
                    s.student_name AS studentName,
                    s.english_name AS englishName,
                    s.gender AS gender,
                    s.birth_date AS birthDate,
                    s.grade AS grade,
                    s.school AS school,
                    s.source_channel AS sourceChannel,
                    s.status AS status,
                    s.notes AS notes,
                    s.guardian_name AS guardianName,
                    s.guardian_phone AS phone,
                    s.guardian_phone_2 AS guardianPhone2,
                    s.guardian_email AS guardianEmail,
                    s.guardian_relation_type AS guardianRelationType,
                    s.pickup_required AS pickupRequired,
                    s.pickup_address AS pickupAddress,
                    s.dropoff_required AS dropoffRequired,
                    s.dropoff_address AS dropoffAddress,
                    s.campus_id AS campusId,
                    c.campus_name AS campusName,
                    agg.latestSignupDate AS latestSignupDate,
                    COALESCE(agg.enrollmentCount, 0) AS enrollmentCount,
                    COALESCE(agg.enrollmentAmountDue, 0.00) AS enrollmentAmountDue,
                    COALESCE(agg.enrollmentPaidAmount, 0.00) AS enrollmentPaidAmount,
                    COALESCE(agg.enrollmentBalanceAmount, 0.00) AS enrollmentBalanceAmount,
                    CASE
                        WHEN agg.enrollmentCount IS NULL THEN 'NO_ENROLLMENT'
                        ELSE agg.aggregatePaymentStatus
                    END AS aggregatePaymentStatus,
                    CASE
                        WHEN agg.enrollmentCount IS NULL THEN 0
                        ELSE 1
                    END AS hasEnrollmentOrder,
                    s.created_at AS createdAt,
                    s.updated_at AS updatedAt
                FROM student s
                LEFT JOIN campus c ON c.id = s.campus_id AND c.is_deleted = 0
                LEFT JOIN (
                    SELECT
                        eo.student_id AS studentId,
                        MAX(COALESCE(eo.signup_date, DATE(eo.created_at))) AS latestSignupDate,
                        COUNT(*) AS enrollmentCount,
                        COALESCE(SUM(eo.amount_due), 0.00) AS enrollmentAmountDue,
                        COALESCE(SUM(eo.received_amount), 0.00) AS enrollmentPaidAmount,
                        COALESCE(SUM(eo.balance_amount), 0.00) AS enrollmentBalanceAmount,
                        CASE
                            WHEN SUM(CASE WHEN eo.payment_status = 'REFUNDED' THEN 1 ELSE 0 END) = COUNT(*) THEN 'REFUNDED'
                            WHEN COALESCE(SUM(eo.balance_amount), 0.00) &gt; 0.00
                                AND COALESCE(SUM(eo.received_amount), 0.00) &gt; 0.00 THEN 'PARTIAL'
                            WHEN COALESCE(SUM(eo.balance_amount), 0.00) &gt; 0.00 THEN 'UNPAID'
                            ELSE 'PAID'
                        END AS aggregatePaymentStatus
                    FROM enrollment_order eo
                    WHERE eo.is_deleted = 0
                        AND eo.order_status &lt;&gt; 'CANCELLED'
                        AND eo.payment_status &lt;&gt; 'REFUNDED'
                    GROUP BY eo.student_id
                ) agg ON agg.studentId = s.id
                WHERE s.is_deleted = 0
                <if test="query.keyword != null">
                    AND (s.student_name LIKE CONCAT('%', #{query.keyword}, '%')
                        OR REPLACE(REPLACE(LOWER(s.student_name), ' ', ''), '-', '')
                            LIKE CONCAT('%', REPLACE(REPLACE(LOWER(#{query.keyword}), ' ', ''), '-', ''), '%')
                        OR s.student_no LIKE CONCAT('%', #{query.keyword}, '%')
                        OR s.guardian_name LIKE CONCAT('%', #{query.keyword}, '%')
                        OR s.guardian_phone LIKE CONCAT('%', #{query.keyword}, '%')
                        OR s.guardian_phone_2 LIKE CONCAT('%', #{query.keyword}, '%'))
                </if>
                <if test="query.status != null">
                    AND s.status = #{query.status}
                </if>
                <if test="query.guardian != null">
                    AND (s.guardian_name LIKE CONCAT('%', #{query.guardian}, '%')
                        OR s.guardian_phone LIKE CONCAT('%', #{query.guardian}, '%')
                        OR s.guardian_phone_2 LIKE CONCAT('%', #{query.guardian}, '%'))
                </if>
                <if test="query.campusId != null">
                    AND s.campus_id = #{query.campusId}
                </if>
            ) student_list
            WHERE 1 = 1
            <if test="query.paymentStatus != null">
                AND student_list.aggregatePaymentStatus = #{query.paymentStatus}
            </if>
            <if test="query.hasEnrollmentOrder != null and query.hasEnrollmentOrder">
                AND student_list.hasEnrollmentOrder = 1
            </if>
            <if test="query.hasEnrollmentOrder != null and !query.hasEnrollmentOrder">
                AND student_list.hasEnrollmentOrder = 0
            </if>
            ORDER BY
                CASE WHEN student_list.enrollmentBalanceAmount &gt; 0.00 THEN 0 ELSE 1 END,
                CASE WHEN student_list.latestSignupDate IS NULL THEN 1 ELSE 0 END,
                student_list.latestSignupDate DESC,
                student_list.createdAt DESC
            </script>
            """)
    IPage<Map<String, Object>> selectStudentOperationPage(Page<Map<String, Object>> page,
                                                          @Param("query") StudentPageQuery query);

    /**
     * 按校区统计学生数（批量GROUP BY，避免N+1）
     */
    @Select("SELECT campus_id, COUNT(*) AS cnt FROM student GROUP BY campus_id")
    List<Map<String, Object>> countGroupByCampus();

    /**
     * 查询指定前缀的最大学号（忽略逻辑删除），用于学号生成。
     */
    @Select("SELECT student_no FROM student WHERE student_no LIKE CONCAT(#{prefix}, '%') ORDER BY student_no DESC LIMIT 1")
    String selectMaxStudentNoByPrefix(@Param("prefix") String prefix);

    /**
     * 查询已软删除的学生档案（绕过 @TableLogic 自动过滤）。
     */
    @Select("SELECT * FROM student WHERE is_deleted = 1 ORDER BY deleted_at DESC, updated_at DESC")
    List<StudentEntity> selectDeleted();

    /**
     * Find the reusable active student for registration intake.
     */
    @Select("""
            SELECT *
            FROM student
            WHERE is_deleted = 0
              AND TRIM(student_name) = TRIM(#{studentName})
              AND REGEXP_REPLACE(guardian_phone, '[^0-9]', '') = #{guardianPhone}
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """)
    StudentEntity selectReusableStudent(@Param("studentName") String studentName,
                                        @Param("guardianPhone") String guardianPhone);

    /**
     * 按 ID 批量查询学生档案，忽略逻辑删除标记，用于展示已删除账号关联的档案信息。
     */
    @Select("<script>"
            + "SELECT * FROM student WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<StudentEntity> selectByIdsIgnoreLogicDelete(@Param("ids") Collection<Long> ids);

    /**
     * 锁定学生行以串行化对同一学生排课时间段的并发调整，避免冲突检查被并发穿透。
     */
    @Select("SELECT * FROM student WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    StudentEntity selectByIdForUpdate(@Param("id") Long id);
}
