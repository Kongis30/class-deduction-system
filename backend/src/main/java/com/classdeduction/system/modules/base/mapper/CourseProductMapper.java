package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface CourseProductMapper extends BaseMapper<CourseProductEntity> {

    /** 产品模式与其子对象共用此行锁，串行化模式切换和项目/价格等关联创建。 */
    @Select("SELECT * FROM course_product WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    CourseProductEntity selectByIdForUpdate(@Param("id") Long id);

    /**
     * 查询最大的 productCode（绕过 MyBatis-Plus 全局逻辑删除，用于 code 自增生成）。
     * 排除已墓碑化（_DEL_）的软删行，避免其干扰序号自增。
     */
    @Select("SELECT product_code FROM course_product WHERE product_code LIKE CONCAT(#{prefix}, '%') "
            + "AND product_code NOT LIKE '%\\_DEL\\_%' ORDER BY product_code DESC LIMIT 1")
    String selectMaxProductCodeByPrefix(String prefix);

    /** 汇总归档确认窗口的影响范围；1 对 1 未完成课次按计划课时估算 Cancel 扣课。 */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM class_group c
                WHERE c.product_id = #{productId} AND c.status = 'ACTIVE' AND c.is_deleted = 0) AS classCount,
              (SELECT COUNT(*) FROM class_enrollment e
                JOIN class_group c ON c.id = e.class_id AND c.is_deleted = 0 AND c.status = 'ACTIVE'
                WHERE c.product_id = #{productId} AND e.status = 'ACTIVE' AND e.is_deleted = 0) AS activeEnrollmentCount,
              (SELECT COUNT(*) FROM course_package p
                WHERE p.course_product_id = #{productId}
                  AND p.package_status IN ('ACTIVE', 'PAUSED') AND p.is_deleted = 0) AS activePackageCount,
              (SELECT COUNT(*) FROM recurring_schedule_rule r
                JOIN course_package p ON p.id = r.package_id AND p.is_deleted = 0
                WHERE p.course_product_id = #{productId} AND r.rule_status IN ('ACTIVE', 'PAUSED')
                  AND r.is_deleted = 0) AS recurringRuleCount,
              (SELECT COUNT(*) FROM lesson_occurrence o
                JOIN course_package p ON p.id = o.package_id AND p.is_deleted = 0
                WHERE p.course_product_id = #{productId} AND o.is_deleted = 0
                  AND COALESCE(o.planning_status, '') NOT IN ('CANCELLED', 'MERGED'))
              + (SELECT COUNT(*) FROM lesson_occurrence o
                JOIN class_group c ON c.id = o.class_group_id AND c.is_deleted = 0
                WHERE c.product_id = #{productId} AND o.is_deleted = 0
                  AND COALESCE(o.planning_status, '') NOT IN ('CANCELLED', 'MERGED')) AS unfinishedLessonCount,
              ROUND(COALESCE((SELECT SUM(o.planned_units * CASE
                WHEN o.private_lesson_group_id IS NULL THEN 1
                ELSE (SELECT COUNT(*) FROM private_lesson_group_member m
                  WHERE m.private_lesson_group_id = o.private_lesson_group_id
                    AND m.member_status = 'ACTIVE' AND m.is_deleted = 0)
              END) FROM lesson_occurrence o
                JOIN course_package p ON p.id = o.package_id AND p.is_deleted = 0
                WHERE p.course_product_id = #{productId} AND o.is_deleted = 0
                  AND COALESCE(o.planning_status, '') NOT IN ('CANCELLED', 'MERGED')), 0) / 2.0, 1)
                AS estimatedDeductedHours
            """)
    Map<String, Object> selectArchivePreview(@Param("productId") Long productId);

    /** 恢复归档需显式写入 NULL，避免 ORM 默认空值策略保留 archived_at。 */
    @Update("UPDATE course_product SET archived_at = NULL, archived_by = NULL, status = 'INACTIVE', "
            + "updated_by = #{operatorId}, updated_at = #{updatedAt}, version = COALESCE(version, 0) + 1 "
            + "WHERE id = #{id} AND is_deleted = 0")
    int restoreArchivedProduct(@Param("id") Long id,
                               @Param("operatorId") Long operatorId,
                               @Param("updatedAt") LocalDateTime updatedAt);
}
