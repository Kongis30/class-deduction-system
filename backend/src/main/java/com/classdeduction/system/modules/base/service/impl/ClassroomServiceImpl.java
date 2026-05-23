package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.ClassroomPageQuery;
import com.classdeduction.system.modules.base.dto.ClassroomSaveRequest;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.service.ClassroomService;
import com.classdeduction.system.modules.base.vo.ClassroomVO;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Classroom service for CRUD operations on classroom records.
 */
@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final ClassroomMapper classroomMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final CourseProductMapper courseProductMapper;
    private final ClassGroupMapper classGroupMapper;

    @Override
    @Cacheable(value = "reference:classrooms", key = "'listAllActive'")
    public List<ClassroomVO> listAllActive() {
        return classroomMapper.selectList(new LambdaQueryWrapper<ClassroomEntity>()
                        .eq(ClassroomEntity::getStatus, "ACTIVE")
                        .eq(ClassroomEntity::getIsDeleted, 0)
                        .orderByAsc(ClassroomEntity::getRoomName)
                        .orderByAsc(ClassroomEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public PageResponse<ClassroomVO> pageClassrooms(ClassroomPageQuery query) {
        Page<ClassroomEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<ClassroomEntity> wrapper = new LambdaQueryWrapper<ClassroomEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ClassroomEntity::getRoomCode, query.getKeyword())
                        .or()
                        .like(ClassroomEntity::getRoomName, query.getKeyword())
                        .or()
                        .like(ClassroomEntity::getCampus, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), ClassroomEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getCampus()), ClassroomEntity::getCampus, query.getCampus())
                .orderByAsc(ClassroomEntity::getRoomName);
        IPage<ClassroomEntity> result = classroomMapper.selectPage(page, wrapper);
        return PageResponse.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Cacheable(value = "reference:classroom", key = "#id")
    public ClassroomVO getClassroom(Long id) {
        return toVO(requireClassroom(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:classrooms", "reference:classroom"}, allEntries = true)
    public Long createClassroom(ClassroomSaveRequest request) {
        // Auto-generate roomCode if not provided
        String roomCode = StringUtils.hasText(request.getRoomCode())
                ? request.getRoomCode()
                : generateUniqueRoomCode();
        request.setRoomCode(roomCode);
        ensureRoomCodeUnique(null, roomCode);
        ensureRoomNameUnique(null, request.getRoomName(), request.getCampus());
        ClassroomEntity entity = new ClassroomEntity();
        applyClassroom(entity, request);
        classroomMapper.insert(entity);
        return entity.getId();
    }

    /**
     * Generate a unique room code based on max existing code.
     * Format: R + 3-digit sequence (R001, R002, ...)
     */
    private String generateUniqueRoomCode() {
        ClassroomEntity maxClassroom = classroomMapper.selectOne(
                new LambdaQueryWrapper<ClassroomEntity>()
                        .eq(ClassroomEntity::getIsDeleted, 0)
                        .orderByDesc(ClassroomEntity::getRoomCode)
                        .last("LIMIT 1"));

        int nextSeq = 1;
        if (maxClassroom != null && maxClassroom.getRoomCode() != null) {
            String maxCode = maxClassroom.getRoomCode();
            try {
                String numPart = maxCode.replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) {
                    nextSeq = Integer.parseInt(numPart) + 1;
                }
            } catch (NumberFormatException e) {
                nextSeq = 1;
            }
        }
        return String.format("R%03d", nextSeq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:classrooms", "reference:classroom"}, allEntries = true)
    public void updateClassroom(Long id, ClassroomSaveRequest request) {
        ClassroomEntity entity = requireClassroom(id);
        // Keep original roomCode if not provided
        if (!StringUtils.hasText(request.getRoomCode())) {
            request.setRoomCode(entity.getRoomCode());
        }
        ensureRoomCodeUnique(id, request.getRoomCode());
        ensureRoomNameUnique(id, request.getRoomName(), request.getCampus());
        applyClassroom(entity, request);
        classroomMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:classrooms", "reference:classroom"}, allEntries = true)
    public void deleteClassroom(Long id) {
        requireClassroom(id);
        // 检查未来的排课
        Long futureOccurrences = lessonOccurrenceMapper.selectCount(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedClassroomId, id)
                .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                .gt(LessonOccurrenceEntity::getPlannedStartAt, LocalDateTime.now()));
        // 检查活跃的排课规则
        Long activeRules = recurringScheduleRuleMapper.selectCount(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .eq(RecurringScheduleRuleEntity::getDefaultClassroomId, id)
                .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                .eq(RecurringScheduleRuleEntity::getIsDeleted, 0));
        // 检查课程产品
        Long courseProducts = courseProductMapper.selectCount(new LambdaQueryWrapper<CourseProductEntity>()
                .eq(CourseProductEntity::getDefaultClassroomId, id)
                .eq(CourseProductEntity::getStatus, "ACTIVE")
                .eq(CourseProductEntity::getIsDeleted, 0));
        // 检查班级
        Long classGroups = classGroupMapper.selectCount(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getClassroomId, id)
                .eq(ClassGroupEntity::getIsDeleted, 0));
        if (futureOccurrences + activeRules + courseProducts + classGroups > 0) {
            throw new BusinessException("CLASSROOM_HAS_REFERENCES",
                    "Cannot delete classroom: there are future lessons, active schedule rules, "
                            + "course products, or classes using it. Reassign them first.");
        }
        classroomMapper.deleteById(id);
    }

    private void ensureRoomCodeUnique(Long currentId, String roomCode) {
        ClassroomEntity existing = classroomMapper.selectOne(new LambdaQueryWrapper<ClassroomEntity>()
                .eq(ClassroomEntity::getRoomCode, roomCode)
                .eq(ClassroomEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("Room code already exists.");
        }
    }

    private void ensureRoomNameUnique(Long currentId, String roomName, String campus) {
        ClassroomEntity existing = classroomMapper.selectOne(new LambdaQueryWrapper<ClassroomEntity>()
                .eq(ClassroomEntity::getRoomName, roomName)
                .eq(ClassroomEntity::getCampus, campus)
                .eq(ClassroomEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("Classroom name already exists in this campus.");
        }
    }

    private ClassroomEntity requireClassroom(Long id) {
        ClassroomEntity entity = classroomMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("CLASSROOM_NOT_FOUND", "Classroom not found.");
        }
        return entity;
    }

    private void applyClassroom(ClassroomEntity entity, ClassroomSaveRequest request) {
        entity.setRoomCode(request.getRoomCode());
        entity.setRoomName(request.getRoomName());
        entity.setCampus(request.getCampus());
        entity.setCapacity(request.getCapacity());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setNotes(request.getNotes());
    }

    private ClassroomVO toVO(ClassroomEntity entity) {
        return ClassroomVO.builder()
                .id(entity.getId())
                .roomCode(entity.getRoomCode())
                .roomName(entity.getRoomName())
                .campus(entity.getCampus())
                .capacity(entity.getCapacity())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
