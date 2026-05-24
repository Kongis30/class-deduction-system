package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseProductMapper extends BaseMapper<CourseProductEntity> {

    /**
     * 查询最大的 productCode（绕过 MyBatis-Plus 全局逻辑删除，用于 code 自增生成）
     */
    @Select("SELECT product_code FROM course_product WHERE product_code LIKE CONCAT(#{prefix}, '%') ORDER BY product_code DESC LIMIT 1")
    String selectMaxProductCodeByPrefix(String prefix);
}
