package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.util.StudentNoGenerator;
import com.classdeduction.system.modules.base.dto.StudentPageQuery;
import com.classdeduction.system.modules.base.dto.StudentReuseQuery;
import com.classdeduction.system.modules.base.dto.StudentSaveRequest;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.service.StudentService;
import com.classdeduction.system.modules.base.vo.StudentDetailVO;
import com.classdeduction.system.modules.base.vo.StudentListItemVO;
import com.classdeduction.system.modules.auth.entity.AuthSessionEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.entity.UserRoleEntity;
import com.classdeduction.system.modules.auth.mapper.AuthSessionMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.finance.entity.TeacherPayRateEntity;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final CampusMapper campusMapper;
    private final UserAccountMapper userAccountMapper;
    private final AuthSessionMapper authSessionMapper;
    private final UserRoleMapper userRoleMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final TeacherPayRateMapper teacherPayRateMapper;

    @Override
    public List<StudentListItemVO> listAllActive() {
        List<StudentEntity> students = studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                .eq(StudentEntity::getStatus, "ACTIVE")
                .eq(StudentEntity::getIsDeleted, 0)
                .orderByAsc(StudentEntity::getStudentNo));

        Map<Long, String> campusNameMap = buildCampusNameMap(students);
        return students.stream()
                .map(s -> toListItem(s, campusNameMap))
                .toList();
    }

    @Override
    public PageResponse<StudentListItemVO> pageStudents(StudentPageQuery query) {
        normalizePageQuery(query);
        Page<Map<String, Object>> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Map<String, Object>> result = studentMapper.selectStudentOperationPage(page, query);
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
    public StudentDetailVO findReusableStudent(StudentReuseQuery query) {
        StudentEntity student = studentMapper.selectReusableStudent(
                query.getStudentName().trim(),
                query.getGuardianPhone().trim());
        return student == null ? null : toDetail(student);
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
        // 报名会先锁学生行再写入 class_enrollment；删除也需先锁同一行，
        // 让并发报名与级联清理串行执行，避免留下已删除学生的有效班课报名。
        StudentEntity student = studentMapper.selectByIdForUpdate(id);
        if (student == null) {
            throw new BusinessException("NOT_FOUND", "Student not found.");
        }
        cascadeDeleteStudentOperationalData(id);
        studentMapper.deleteById(id);
    }

    private void cascadeDeleteStudentOperationalData(Long studentId) {
        List<UserAccountEntity> accounts = userAccountMapper.selectList(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getStudentId, studentId)
                .eq(UserAccountEntity::getIsDeleted, 0));
        List<Long> userIds = accounts.stream().map(UserAccountEntity::getId).filter(Objects::nonNull).toList();
        if (!userIds.isEmpty()) {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                    .in(UserRoleEntity::getUserId, userIds));
            authSessionMapper.delete(new LambdaQueryWrapper<AuthSessionEntity>()
                    .in(AuthSessionEntity::getUserId, userIds));
            userAccountMapper.delete(new LambdaQueryWrapper<UserAccountEntity>()
                    .in(UserAccountEntity::getId, userIds));
        }

        List<CoursePackageEntity> packages = coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                .eq(CoursePackageEntity::getStudentId, studentId)
                .eq(CoursePackageEntity::getIsDeleted, 0));
        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).filter(Objects::nonNull).toList();
        if (!packageIds.isEmpty()) {
            List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                    .in(RecurringScheduleRuleEntity::getPackageId, packageIds)
                    .eq(RecurringScheduleRuleEntity::getIsDeleted, 0));
            List<Long> ruleIds = rules.stream().map(RecurringScheduleRuleEntity::getId).filter(Objects::nonNull).toList();
            if (!ruleIds.isEmpty()) {
                recurringScheduleSlotMapper.delete(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .in(RecurringScheduleSlotEntity::getRuleId, ruleIds));
            }
            recurringScheduleRuleMapper.delete(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                    .in(RecurringScheduleRuleEntity::getPackageId, packageIds));
            packageTeacherAssignmentMapper.delete(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                    .in(PackageTeacherAssignmentEntity::getPackageId, packageIds));
            lessonOccurrenceMapper.delete(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                    .in(LessonOccurrenceEntity::getPackageId, packageIds));
            coursePackageMapper.delete(new LambdaQueryWrapper<CoursePackageEntity>()
                    .eq(CoursePackageEntity::getStudentId, studentId));
        }

        lessonOccurrenceMapper.delete(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getStudentId, studentId));
        classEnrollmentMapper.delete(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getStudentId, studentId));
        teacherPayRateMapper.delete(new LambdaQueryWrapper<TeacherPayRateEntity>()
                .eq(TeacherPayRateEntity::getStudentId, studentId));
    }

    @Override
    public List<StudentListItemVO> listDeleted() {
        List<StudentEntity> students = studentMapper.selectDeleted();
        if (students.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> campusNameMap = buildCampusNameMap(students);
        return students.stream()
                .map(s -> toListItem(s, campusNameMap))
                .toList();
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
        entity.setGrade(request.getGrade());
        entity.setStudentClass(request.getStudentClass());
        entity.setSchool(request.getSchool());
        entity.setSourceChannel(request.getSourceChannel());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setNotes(request.getNotes());
        entity.setGuardianName(request.getGuardianName());
        entity.setGuardianPhone(request.getGuardianPhone());
        entity.setGuardianPhone2(request.getGuardianPhone2());
        entity.setGuardianEmail(request.getGuardianEmail());
        entity.setGuardianRelationType(request.getGuardianRelationType());
        entity.setPickupRequired(Boolean.TRUE.equals(request.getPickupRequired()));
        entity.setPickupAddress(Boolean.TRUE.equals(request.getPickupRequired()) ? trimToNull(request.getPickupAddress()) : null);
        entity.setDropoffRequired(Boolean.TRUE.equals(request.getDropoffRequired()));
        entity.setDropoffAddress(Boolean.TRUE.equals(request.getDropoffRequired()) ? trimToNull(request.getDropoffAddress()) : null);
        entity.setCampusId(request.getCampusId());
    }

    private void normalizePageQuery(StudentPageQuery query) {
        query.setKeyword(trimToNull(query.getKeyword()));
        query.setStatus(trimToNull(query.getStatus()));
        query.setGuardian(trimToNull(query.getGuardian()));
        query.setPaymentStatus(trimToNull(query.getPaymentStatus()));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 从学生列表中提取所有 campusId，批量查询 campus 表，构建 id -> name 映射。
     */
    private Map<Long, String> buildCampusNameMap(List<StudentEntity> students) {
        Set<Long> campusIds = students.stream()
                .map(StudentEntity::getCampusId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (campusIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CampusEntity> campuses = campusMapper.selectBatchIds(campusIds);
        Map<Long, String> map = new HashMap<>();
        for (CampusEntity c : campuses) {
            map.put(c.getId(), c.getCampusName());
        }
        return map;
    }

    private StudentListItemVO toListItem(StudentEntity student, Map<Long, String> campusNameMap) {
        return StudentListItemVO.builder()
                .id(student.getId())
                .studentNo(student.getStudentNo())
                .studentName(student.getStudentName())
                .englishName(student.getEnglishName())
                .gender(student.getGender())
                .birthDate(student.getBirthDate())
                .grade(student.getGrade())
                .studentClass(student.getStudentClass())
                .school(student.getSchool())
                .sourceChannel(student.getSourceChannel())
                .status(student.getStatus())
                .notes(student.getNotes())
                .guardianName(student.getGuardianName())
                .phone(student.getGuardianPhone())
                .guardianPhone2(student.getGuardianPhone2())
                .guardianEmail(student.getGuardianEmail())
                .guardianRelationType(student.getGuardianRelationType())
                .pickupRequired(Boolean.TRUE.equals(student.getPickupRequired()))
                .pickupAddress(student.getPickupAddress())
                .dropoffRequired(Boolean.TRUE.equals(student.getDropoffRequired()))
                .dropoffAddress(student.getDropoffAddress())
                .campusId(student.getCampusId())
                .campusName(student.getCampusId() != null ? campusNameMap.get(student.getCampusId()) : null)
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }

    private StudentListItemVO toListItem(Map<String, Object> row) {
        return StudentListItemVO.builder()
                .id(longValue(row.get("id")))
                .studentNo(stringValue(row.get("studentNo")))
                .studentName(stringValue(row.get("studentName")))
                .englishName(stringValue(row.get("englishName")))
                .gender(stringValue(row.get("gender")))
                .birthDate(localDateValue(row.get("birthDate")))
                .grade(stringValue(row.get("grade")))
                .studentClass(stringValue(row.get("studentClass")))
                .school(stringValue(row.get("school")))
                .sourceChannel(stringValue(row.get("sourceChannel")))
                .status(stringValue(row.get("status")))
                .notes(stringValue(row.get("notes")))
                .guardianName(stringValue(row.get("guardianName")))
                .phone(stringValue(row.get("phone")))
                .guardianPhone2(stringValue(row.get("guardianPhone2")))
                .guardianEmail(stringValue(row.get("guardianEmail")))
                .guardianRelationType(stringValue(row.get("guardianRelationType")))
                .pickupRequired(booleanValue(row.get("pickupRequired")))
                .pickupAddress(stringValue(row.get("pickupAddress")))
                .dropoffRequired(booleanValue(row.get("dropoffRequired")))
                .dropoffAddress(stringValue(row.get("dropoffAddress")))
                .campusId(longValue(row.get("campusId")))
                .campusName(stringValue(row.get("campusName")))
                .latestSignupDate(localDateValue(row.get("latestSignupDate")))
                .enrollmentCount(longValue(row.get("enrollmentCount")))
                .receiptCount(longValue(row.get("receiptCount")))
                .enrollmentAmountDue(decimalValue(row.get("enrollmentAmountDue")))
                .enrollmentPaidAmount(decimalValue(row.get("enrollmentPaidAmount")))
                .enrollmentBalanceAmount(decimalValue(row.get("enrollmentBalanceAmount")))
                .aggregatePaymentStatus(stringValue(row.get("aggregatePaymentStatus")))
                .hasEnrollmentOrder(booleanValue(row.get("hasEnrollmentOrder")))
                .createdAt(localDateTimeValue(row.get("createdAt")))
                .updatedAt(localDateTimeValue(row.get("updatedAt")))
                .build();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }

    private Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString();
        if ("1".equals(text)) {
            return true;
        }
        if ("0".equals(text)) {
            return false;
        }
        return Boolean.valueOf(text);
    }

    private LocalDate localDateValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        String text = value.toString();
        return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
    }

    private LocalDateTime localDateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private StudentDetailVO toDetail(StudentEntity student) {
        String campusName = null;
        if (student.getCampusId() != null) {
            CampusEntity campus = campusMapper.selectById(student.getCampusId());
            campusName = campus != null ? campus.getCampusName() : null;
        }
        return StudentDetailVO.builder()
                .id(student.getId())
                .studentNo(student.getStudentNo())
                .studentName(student.getStudentName())
                .englishName(student.getEnglishName())
                .gender(student.getGender())
                .birthDate(student.getBirthDate())
                .grade(student.getGrade())
                .studentClass(student.getStudentClass())
                .school(student.getSchool())
                .sourceChannel(student.getSourceChannel())
                .status(student.getStatus())
                .notes(student.getNotes())
                .guardianName(student.getGuardianName())
                .guardianPhone(student.getGuardianPhone())
                .guardianPhone2(student.getGuardianPhone2())
                .guardianEmail(student.getGuardianEmail())
                .guardianRelationType(student.getGuardianRelationType())
                .pickupRequired(Boolean.TRUE.equals(student.getPickupRequired()))
                .pickupAddress(student.getPickupAddress())
                .dropoffRequired(Boolean.TRUE.equals(student.getDropoffRequired()))
                .dropoffAddress(student.getDropoffAddress())
                .campusId(student.getCampusId())
                .campusName(campusName)
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }
}
