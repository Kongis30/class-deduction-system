package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.util.StudentNoGenerator;
import com.classdeduction.system.modules.base.dto.StudentPageQuery;
import com.classdeduction.system.modules.base.dto.StudentSaveRequest;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.service.StudentService;
import com.classdeduction.system.modules.base.vo.StudentDetailVO;
import com.classdeduction.system.modules.base.vo.StudentListItemVO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Student service managing the student profile (guardian info stored as flat fields on student table).
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final StudentMapper studentMapper;
    private final StudentNoGenerator studentNoGenerator;
    private final CoursePackageMapper coursePackageMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;

    @Override
    public List<StudentListItemVO> listAllActive() {
        List<StudentEntity> students = studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getStatus, "ACTIVE")
                .eq(StudentEntity::getIsDeleted, 0)
                .orderByAsc(StudentEntity::getStudentNo));

        return students.stream()
                .map(this::toListItem)
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

        List<StudentListItemVO> list = result.getRecords()
                .stream()
                .map(this::toListItem)
                .toList();
        return PageResponse.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public StudentDetailVO getStudent(Long id) {
        StudentEntity student = requireStudent(id);
        return toDetail(student);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStudent(StudentSaveRequest request) {
        String studentNo = StringUtils.hasText(request.getStudentNo())
                ? request.getStudentNo()
                : studentNoGenerator.generate();
        request.setStudentNo(studentNo);

        StudentEntity entity = new StudentEntity();
        applyStudent(entity, request);
        studentMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStudent(Long id, StudentSaveRequest request) {
        StudentEntity entity = requireStudent(id);
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
        // 检查未完成的课包（DRAFT 或 ACTIVE）
        Long activePackages = coursePackageMapper.selectCount(new LambdaQueryWrapper<CoursePackageEntity>()
                .eq(CoursePackageEntity::getStudentId, id)
                .in(CoursePackageEntity::getPackageStatus, "DRAFT", "ACTIVE")
                .eq(CoursePackageEntity::getIsDeleted, 0));
        // 检查活跃的班课报名
        Long activeEnrollments = classEnrollmentMapper.selectCount(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getStudentId, id)
                .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        if (activePackages + activeEnrollments > 0) {
            throw new BusinessException("STUDENT_HAS_REFERENCES",
                    "Cannot delete student: there are active course packages or class group enrollments. "
                            + "Set to INACTIVE instead.");
        }
        studentMapper.deleteById(id);
    }

    private void ensureStudentNoUnique(Long currentId, String studentNo) {
        StudentEntity existing = studentMapper.selectOne(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getStudentNo, studentNo)
                .eq(StudentEntity::getIsDeleted, 0)
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

    private StudentListItemVO toListItem(StudentEntity student) {
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
                .guardianName(student.getGuardianName())
                .phone(student.getGuardianPhone())
                .guardianEmail(student.getGuardianEmail())
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }

    private StudentDetailVO toDetail(StudentEntity student) {
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
                .guardianName(student.getGuardianName())
                .guardianPhone(student.getGuardianPhone())
                .guardianEmail(student.getGuardianEmail())
                .guardianRelationType(student.getGuardianRelationType())
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }
}
