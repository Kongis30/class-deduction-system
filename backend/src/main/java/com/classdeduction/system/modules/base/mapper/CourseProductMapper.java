package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
