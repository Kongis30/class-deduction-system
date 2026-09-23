package com.classdeduction.system.modules.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.transport.entity.TransportDriverAssignmentEntity;
import com.classdeduction.system.modules.transport.vo.TransportDriverAssignmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 接送名单司机关联数据访问。 */
@Mapper
public interface TransportDriverAssignmentMapper extends BaseMapper<TransportDriverAssignmentEntity> {

    @Select("SELECT a.assignment_key AS assignmentKey, a.driver_id AS driverId, "
            + "d.driver_name AS driverName, a.transport_type AS transportType "
            + "FROM transport_driver_assignment a "
            + "LEFT JOIN transport_driver d ON d.id = a.driver_id AND d.is_deleted = 0 "
            + "WHERE a.is_deleted = 0 ORDER BY a.id")
    List<TransportDriverAssignmentVO> selectAllWithDriver();

    @Update("UPDATE transport_driver_assignment SET driver_id = NULL, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE driver_id = #{driverId} AND is_deleted = 0")
    int clearDriver(@Param("driverId") Long driverId);
}
