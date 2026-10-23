package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ClassEnrollmentMapper extends BaseMapper<ClassEnrollmentEntity> {

    /** 续课事务锁定来源权益，同一权益不能并发续接两次。 */
    @Select("SELECT * FROM class_enrollment WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    ClassEnrollmentEntity selectByIdForUpdate(@Param("id") Long id);

    /**
     * 查询班课下所有报名状态，包含已退出的软删除记录，用于进度页状态过滤。
     */
    @Select("""
            SELECT *
            FROM class_enrollment
            WHERE class_id = #{classId}
            ORDER BY enrollment_date ASC, id ASC
            """)
    List<ClassEnrollmentEntity> selectAllStatusesByClassId(@Param("classId") Long classId);

    /**
     * 批量统计指定班级的活跃在册人数。
     */
    @Select({"<script>",
            "SELECT class_id, COUNT(*) AS cnt",
            "FROM class_enrollment",
            "WHERE class_id IN",
            "<foreach item='id' collection='classIds' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND status = 'ACTIVE'",
            "AND is_deleted = 0",
            "GROUP BY class_id",
            "</script>"})
    List<Map<String, Object>> countActiveByClassIds(@Param("classIds") List<Long> classIds);

    /** 退款撤销快照需要读取已软删除的班课报名，才能精确恢复退款前状态。 */
    @Select({"<script>",
            "SELECT * FROM class_enrollment WHERE (",
            "<trim prefixOverrides='OR'>",
            "<if test='itemIds != null and itemIds.size() &gt; 0'>OR enrollment_order_item_id IN",
            "<foreach item='id' collection='itemIds' open='(' separator=',' close=')'>#{id}</foreach>",
            "</if>",
            "<if test='classId != null and studentId != null'>OR (class_id = #{classId} AND student_id = #{studentId} AND enrollment_order_item_id IS NULL)</if>",
            "</trim>",
            ") ORDER BY id ASC",
            "</script>"})
    List<ClassEnrollmentEntity> selectRefundScopeIncludingDeleted(@Param("itemIds") List<Long> itemIds,
                                                                    @Param("classId") Long classId,
                                                                    @Param("studentId") Long studentId);

    /** 恢复退款前的班课报名状态；使用版本号保护退款后被其他操作改动的记录。 */
    @Update("UPDATE class_enrollment SET status = #{status}, expiry_date = #{expiryDate}, is_deleted = #{isDeleted}, "
            + "updated_at = #{updatedAt}, updated_by = #{updatedBy}, version = COALESCE(version, 0) + 1 "
            + "WHERE id = #{id} AND COALESCE(version, 0) = COALESCE(#{expectedVersion}, 0)")
    int restoreRefundSnapshot(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("expiryDate") java.time.LocalDate expiryDate,
                              @Param("isDeleted") Integer isDeleted,
                              @Param("updatedAt") java.time.LocalDateTime updatedAt,
                              @Param("updatedBy") Long updatedBy,
                              @Param("expectedVersion") Integer expectedVersion);

    /** 当前读出目标学生在该实际班级的重叠权益；报名/更正前已锁定学生行，确保并发请求顺序可见。 */
    @Select("""
            SELECT id
            FROM class_enrollment
            WHERE student_id = #{studentId}
              AND class_id = #{classId}
              AND status = 'ACTIVE'
              AND is_deleted = 0
              AND enrollment_date <= #{endDate}
              AND expiry_date >= #{startDate}
            FOR UPDATE
            """)
    List<Long> selectActiveOverlappingForUpdate(@Param("studentId") Long studentId,
                                                @Param("classId") Long classId,
                                                @Param("startDate") java.time.LocalDate startDate,
                                                @Param("endDate") java.time.LocalDate endDate);
}
