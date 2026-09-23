package com.classdeduction.system.modules.transport.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.transport.dto.TransportDriverAssignmentSaveRequest;
import com.classdeduction.system.modules.transport.dto.TransportDriverSaveRequest;
import com.classdeduction.system.modules.transport.entity.TransportDriverAssignmentEntity;
import com.classdeduction.system.modules.transport.entity.TransportDriverEntity;
import com.classdeduction.system.modules.transport.mapper.TransportDriverAssignmentMapper;
import com.classdeduction.system.modules.transport.mapper.TransportDriverMapper;
import com.classdeduction.system.modules.transport.service.TransportDriverService;
import com.classdeduction.system.modules.transport.vo.TransportDriverAssignmentVO;
import com.classdeduction.system.modules.transport.vo.TransportDriverVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/** 接送司机维护及名单分配；司机删除会先清理课程学生的司机引用。 */
@Service
@RequiredArgsConstructor
public class TransportDriverServiceImpl implements TransportDriverService {

    private static final String PICKUP = "PICKUP";
    private static final String DROPOFF = "DROPOFF";
    private static final String BOTH = "BOTH";

    private final TransportDriverMapper driverMapper;
    private final TransportDriverAssignmentMapper assignmentMapper;

    @Override
    public List<TransportDriverVO> listDrivers() {
        return driverMapper.selectList(new LambdaQueryWrapper<TransportDriverEntity>()
                        .eq(TransportDriverEntity::getIsDeleted, 0)
                        .orderByAsc(TransportDriverEntity::getDisplayOrder)
                        .orderByAsc(TransportDriverEntity::getDriverName)
                        .orderByAsc(TransportDriverEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDriver(TransportDriverSaveRequest request) {
        String name = normalizeName(request.getDriverName());
        ensureNameUnique(null, name);
        TransportDriverEntity entity = new TransportDriverEntity();
        entity.setDriverName(name);
        entity.setDisplayOrder(defaultDisplayOrder(request.getDisplayOrder()));
        driverMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDriver(Long id, TransportDriverSaveRequest request) {
        TransportDriverEntity entity = requireDriver(id);
        String name = normalizeName(request.getDriverName());
        ensureNameUnique(id, name);
        entity.setDriverName(name);
        entity.setDisplayOrder(defaultDisplayOrder(request.getDisplayOrder()));
        driverMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDriver(Long id) {
        requireDriver(id);
        assignmentMapper.clearDriver(id);
        driverMapper.deleteById(id);
    }

    @Override
    public List<TransportDriverAssignmentVO> listAssignments() {
        return assignmentMapper.selectAllWithDriver();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAssignment(TransportDriverAssignmentSaveRequest request) {
        int participantIndex = request.getParticipantIndex() == null ? 1 : request.getParticipantIndex();
        if (participantIndex < 1) {
            throw new BusinessException("INVALID_TRANSPORT_ASSIGNMENT", "Participant index must be positive.");
        }
        String transportType = normalizeTransportType(request.getTransportType());
        String baseKey = assignmentBaseKey(request.getOrderId(), request.getOrderItemId(),
                request.getStudentId(), participantIndex);
        String assignmentKey = assignmentKey(baseKey, transportType);
        TransportDriverAssignmentEntity existing = findActiveAssignment(assignmentKey);
        TransportDriverAssignmentEntity legacy = findActiveAssignment(baseKey);
        if (legacy != null && isLegacySharedAssignment(legacy) && existing == null) {
            migrateLegacyAssignment(legacy, baseKey, transportType);
            existing = findActiveAssignment(assignmentKey);
        }
        if (request.getDriverId() == null) {
            if (existing != null) {
                assignmentMapper.deleteById(existing.getId());
            }
            return;
        }

        requireDriver(request.getDriverId());
        if (existing == null) {
            existing = new TransportDriverAssignmentEntity();
            existing.setAssignmentKey(assignmentKey);
            existing.setOrderId(request.getOrderId());
            existing.setOrderItemId(request.getOrderItemId());
            existing.setStudentId(request.getStudentId());
            existing.setParticipantIndex(participantIndex);
            existing.setTransportType(transportType);
            existing.setDriverId(request.getDriverId());
            assignmentMapper.insert(existing);
            return;
        }
        existing.setDriverId(request.getDriverId());
        assignmentMapper.updateById(existing);
    }

    private TransportDriverEntity requireDriver(Long id) {
        TransportDriverEntity entity = driverMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getIsDeleted(), 0)) {
            throw new BusinessException("TRANSPORT_DRIVER_NOT_FOUND", "Transport driver not found.");
        }
        return entity;
    }

    private void ensureNameUnique(Long currentId, String name) {
        TransportDriverEntity existing = driverMapper.selectOne(new LambdaQueryWrapper<TransportDriverEntity>()
                .eq(TransportDriverEntity::getDriverName, name)
                .eq(TransportDriverEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("TRANSPORT_DRIVER_NAME_EXISTS", "A driver with this name already exists.");
        }
    }

    private TransportDriverVO toVO(TransportDriverEntity entity) {
        return new TransportDriverVO(entity.getId(), entity.getDriverName(), entity.getDisplayOrder(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("TRANSPORT_DRIVER_NAME_REQUIRED", "Driver name is required.");
        }
        return name.trim();
    }

    private int defaultDisplayOrder(Integer displayOrder) {
        return displayOrder == null ? 99 : displayOrder;
    }

    private TransportDriverAssignmentEntity findActiveAssignment(String assignmentKey) {
        return assignmentMapper.selectOne(new LambdaQueryWrapper<TransportDriverAssignmentEntity>()
                .eq(TransportDriverAssignmentEntity::getAssignmentKey, assignmentKey)
                .eq(TransportDriverAssignmentEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
    }

    private boolean isLegacySharedAssignment(TransportDriverAssignmentEntity assignment) {
        return !StringUtils.hasText(assignment.getTransportType())
                || BOTH.equalsIgnoreCase(assignment.getTransportType());
    }

    /**
     * 旧数据只有一个共享司机。第一次修改任一方向时，把旧记录迁移为另一方向，
     * 当前方向再按新值单独保存，从而保留旧数据且不会再互相覆盖。
     */
    private void migrateLegacyAssignment(TransportDriverAssignmentEntity legacy, String baseKey,
                                         String requestedType) {
        String oppositeType = oppositeTransportType(requestedType);
        String oppositeKey = assignmentKey(baseKey, oppositeType);
        TransportDriverAssignmentEntity opposite = findActiveAssignment(oppositeKey);
        if (opposite == null) {
            legacy.setAssignmentKey(oppositeKey);
            legacy.setTransportType(oppositeType);
            assignmentMapper.updateById(legacy);
        } else {
            assignmentMapper.deleteById(legacy.getId());
        }
    }

    private String assignmentBaseKey(Long orderId, Long orderItemId, Long studentId, int participantIndex) {
        return orderId + "-" + (orderItemId == null ? "order" : orderItemId)
                + "-" + studentId + "-" + participantIndex;
    }

    private String assignmentKey(String baseKey, String transportType) {
        return baseKey + "-" + transportType.toLowerCase();
    }

    private String normalizeTransportType(String transportType) {
        if (!StringUtils.hasText(transportType)) {
            return PICKUP;
        }
        if (PICKUP.equalsIgnoreCase(transportType)) {
            return PICKUP;
        }
        if (DROPOFF.equalsIgnoreCase(transportType)) {
            return DROPOFF;
        }
        throw new BusinessException("INVALID_TRANSPORT_TYPE", "Transport type must be PICKUP or DROPOFF.");
    }

    private String oppositeTransportType(String transportType) {
        return PICKUP.equals(transportType) ? DROPOFF : PICKUP;
    }
}
