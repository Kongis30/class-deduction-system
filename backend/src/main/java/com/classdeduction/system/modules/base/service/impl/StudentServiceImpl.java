package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.StudentPageQuery;
import com.classdeduction.system.modules.base.dto.StudentSaveRequest;
import com.classdeduction.system.modules.base.entity.GuardianContactEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.GuardianContactMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.service.StudentService;
import com.classdeduction.system.modules.base.vo.GuardianContactVO;
import com.classdeduction.system.modules.base.vo.StudentDetailVO;
import com.classdeduction.system.modules.base.vo.StudentListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.stream.Collectors;

/**
 * Student service that keeps the student profile and guardian contacts in sync.
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final StudentMapper studentMapper;
    private final GuardianContactMapper guardianContactMapper;

    @Override
    public List<StudentListItemVO> listAllActive() {
        List<StudentEntity> students = studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getStatus, "ACTIVE")
                .eq(StudentEntity::getIsDeleted, 0)
                .orderByAsc(StudentEntity::getStudentNo));

        if (students.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<GuardianContactEntity>> guardianMap = guardianContactMapper.selectList(
                        new LambdaQueryWrapper<GuardianContactEntity>()
                                .in(GuardianContactEntity::getStudentId,
                                        students.stream().map(StudentEntity::getId).toList())
                                .orderByDesc(GuardianContactEntity::getIsPrimary)
                                .orderByAsc(GuardianContactEntity::getCreatedAt))
                .stream()
                .collect(Collectors.groupingBy(GuardianContactEntity::getStudentId));

        return students.stream()
                .map(student -> toListItem(student, guardianMap.getOrDefault(student.getId(), Collections.emptyList())))
                .toList();
    }

    @Override
    public PageResponse<StudentListItemVO> pageStudents(StudentPageQuery query) {
        Page<StudentEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<StudentEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(StudentEntity::getStudentName, query.getKeyword())
                        .or()
                        .like(StudentEntity::getStudentNo, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), StudentEntity::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getGuardian()), StudentEntity::getGuardianName, query.getGuardian())
                .orderByDesc(StudentEntity::getCreatedAt);
        IPage<StudentEntity> result = studentMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Map<Long, List<GuardianContactEntity>> guardianMap = guardianContactMapper.selectList(
                        new LambdaQueryWrapper<GuardianContactEntity>()
                                .in(GuardianContactEntity::getStudentId,
                                        result.getRecords().stream().map(StudentEntity::getId).toList())
                                .orderByDesc(GuardianContactEntity::getIsPrimary)
                                .orderByAsc(GuardianContactEntity::getCreatedAt))
                .stream()
                .collect(Collectors.groupingBy(GuardianContactEntity::getStudentId));

        List<StudentListItemVO> list = result.getRecords()
                .stream()
                .map(student -> toListItem(student, guardianMap.getOrDefault(student.getId(), Collections.emptyList())))
                .toList();
        return PageResponse.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public StudentDetailVO getStudent(Long id) {
        StudentEntity student = requireStudent(id);
        List<GuardianContactVO> contacts = listGuardianContacts(id);
        return toDetail(student, contacts);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStudent(StudentSaveRequest request) {
        String studentNo = StringUtils.hasText(request.getStudentNo())
                ? request.getStudentNo()
                : generateUniqueStudentNo();
        request.setStudentNo(studentNo);

        StudentEntity entity = new StudentEntity();
        applyStudent(entity, request);
        studentMapper.insert(entity);
        return entity.getId();
    }

    /**
     * Generate a unique student number based on the max existing number for today.
     * Format: STU + yyyyMMdd + 4-digit sequence
     */
    private String generateUniqueStudentNo() {
        String todayPrefix = "STU" + java.time.LocalDate.now(
                com.classdeduction.system.common.time.AppTime.APP_ZONE_ID)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Find max studentNo with today's prefix (excluding soft-deleted records)
        StudentEntity maxStudent = studentMapper.selectOne(
                new LambdaQueryWrapper<StudentEntity>()
                        .likeRight(StudentEntity::getStudentNo, todayPrefix)
                        .eq(StudentEntity::getIsDeleted, 0)
                        .orderByDesc(StudentEntity::getStudentNo)
                        .last("LIMIT 1"));

        int nextSeq = 1;
        if (maxStudent != null && maxStudent.getStudentNo() != null) {
            String maxNo = maxStudent.getStudentNo();
            if (maxNo.startsWith(todayPrefix)) {
                try {
                    String seqStr = maxNo.substring(todayPrefix.length());
                    nextSeq = Integer.parseInt(seqStr) + 1;
                } catch (NumberFormatException e) {
                    // Fall back to 1 if parsing fails
                    nextSeq = 1;
                }
            }
        }

        return String.format("%s%04d", todayPrefix, nextSeq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStudent(Long id, StudentSaveRequest request) {
        StudentEntity entity = requireStudent(id);
        // Keep original studentNo if not provided
        if (!StringUtils.hasText(request.getStudentNo())) {
            request.setStudentNo(entity.getStudentNo());
        }
        ensureStudentNoUnique(id, request.getStudentNo());

        applyStudent(entity, request);
        studentMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStudent(Long id) {
        requireStudent(id);
        studentMapper.deleteById(id);
        guardianContactMapper.update(
                null,
                new LambdaUpdateWrapper<GuardianContactEntity>()
                        .eq(GuardianContactEntity::getStudentId, id)
                        .set(GuardianContactEntity::getIsDeleted, 1)
        );
    }

    private void ensureStudentNoUnique(Long currentId, String studentNo) {
        StudentEntity existing = studentMapper.selectOne(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getStudentNo, studentNo)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("Student ID already exists.");
        }
    }

    private StudentEntity requireStudent(Long id) {
        StudentEntity student = studentMapper.selectById(id);
        if (student == null) {
            throw new BusinessException("NOT_FOUND", "Student not found.");
        }
        return student;
    }

    private void applyStudent(StudentEntity entity, StudentSaveRequest request) {
        entity.setStudentNo(request.getStudentNo());
        entity.setStudentName(request.getStudentName());
        entity.setEnglishName(request.getEnglishName());
        entity.setGender(request.getGender());
        entity.setBirthDate(request.getBirthDate());
        entity.setSourceChannel(request.getSourceChannel());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setNotes(request.getNotes());
        entity.setGuardianName(request.getGuardianName());
        entity.setGuardianPhone(request.getGuardianPhone());
        entity.setGuardianEmail(request.getGuardianEmail());
        entity.setGuardianRelationType(request.getGuardianRelationType());
    }

    private List<GuardianContactVO> listGuardianContacts(Long studentId) {
        return guardianContactMapper.selectList(new LambdaQueryWrapper<GuardianContactEntity>()
                        .eq(GuardianContactEntity::getStudentId, studentId)
                        .orderByDesc(GuardianContactEntity::getIsPrimary)
                        .orderByAsc(GuardianContactEntity::getCreatedAt))
                .stream()
                .map(this::toGuardianVO)
                .toList();
    }

    private StudentListItemVO toListItem(StudentEntity student, List<GuardianContactEntity> contacts) {
        GuardianContactEntity primary = contacts.stream()
                .max(Comparator.comparing(GuardianContactEntity::getIsPrimary)
                        .thenComparing(GuardianContactEntity::getCreatedAt))
                .orElse(null);
        return StudentListItemVO.builder()
                .id(student.getId())
                .studentNo(student.getStudentNo())
                .studentName(student.getStudentName())
                .englishName(student.getEnglishName())
                .gender(student.getGender())
                .birthDate(student.getBirthDate())
                .sourceChannel(student.getSourceChannel())
                .status(student.getStatus())
                .notes(student.getNotes())
                .primaryGuardianName(primary == null ? null : primary.getContactName())
                .primaryGuardianMobile(primary == null ? null : primary.getMobile())
                .primaryGuardianEmail(primary == null ? null : primary.getEmail())
                .guardianCount(contacts.size())
                // Use flat fields from student entity directly (frontend now expects these)
                .guardianName(student.getGuardianName())
                .phone(student.getGuardianPhone())
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }

    private StudentDetailVO toDetail(StudentEntity student, List<GuardianContactVO> contacts) {
        return StudentDetailVO.builder()
                .id(student.getId())
                .studentNo(student.getStudentNo())
                .studentName(student.getStudentName())
                .englishName(student.getEnglishName())
                .gender(student.getGender())
                .birthDate(student.getBirthDate())
                .sourceChannel(student.getSourceChannel())
                .status(student.getStatus())
                .notes(student.getNotes())
                .guardianContacts(contacts)
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }

    private GuardianContactVO toGuardianVO(GuardianContactEntity entity) {
        return GuardianContactVO.builder()
                .id(entity.getId())
                .contactName(entity.getContactName())
                .relationType(entity.getRelationType())
                .mobile(entity.getMobile())
                .email(entity.getEmail())
                .primary(entity.getIsPrimary() != null && entity.getIsPrimary() == 1)
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
