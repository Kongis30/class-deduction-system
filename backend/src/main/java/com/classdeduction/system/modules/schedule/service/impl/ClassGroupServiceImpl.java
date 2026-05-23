package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.ClassGroupService;
import com.classdeduction.system.modules.schedule.util.ScheduleDescParser;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTemplateEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassGroupServiceImpl implements ClassGroupService {

    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final CampusMapper campusMapper;
    private final NotificationTemplateMapper notificationTemplateMapper;

    @Override
    public List<Map<String, Object>> listClasses() {
        List<ClassGroupEntity> classes = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .isNotNull(ClassGroupEntity::getProductId)
                        .orderByAsc(ClassGroupEntity::getClassName)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassGroupEntity clz : classes) {
            // 统计在册人数
            long activeCount = classEnrollmentMapper.selectCount(
                    new LambdaQueryWrapper<ClassEnrollmentEntity>()
                            .eq(ClassEnrollmentEntity::getClassId, clz.getId())
                            .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
            );
            TeacherEntity teacher = clz.getTeacherId() != null
                    ? teacherMapper.selectById(clz.getTeacherId()) : null;
            ClassroomEntity classroom = clz.getClassroomId() != null
                    ? classroomMapper.selectById(clz.getClassroomId()) : null;
            Map<String, Object> item = new HashMap<>();
            item.put("id", clz.getId());
            item.put("className", clz.getClassName());
            item.put("description", clz.getDescription());
            item.put("scheduleDesc", clz.getScheduleDesc());
            item.put("teacherName", teacher != null ? teacher.getTeacherName() : null);
            item.put("classroomName", classroom != null ? classroom.getRoomName() : null);
            item.put("listPrice", clz.getListPrice());
            item.put("productId", clz.getProductId());
            item.put("status", clz.getStatus());
            item.put("activeStudentCount", activeCount);
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> getClassDetail(Long classId) {
        ClassGroupEntity clz = classGroupMapper.selectById(classId);
        if (clz == null) throw new BusinessException("CLASS_NOT_FOUND", "Class not found.");
        TeacherEntity teacher = clz.getTeacherId() != null
                ? teacherMapper.selectById(clz.getTeacherId()) : null;
        ClassroomEntity classroom = clz.getClassroomId() != null
                ? classroomMapper.selectById(clz.getClassroomId()) : null;
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", clz.getId());
        detail.put("className", clz.getClassName());
        detail.put("description", clz.getDescription());
        detail.put("scheduleDesc", clz.getScheduleDesc());
        detail.put("teacherName", teacher != null ? teacher.getTeacherName() : null);
        detail.put("classroomName", classroom != null ? classroom.getRoomName() : null);
        detail.put("status", clz.getStatus());
        return detail;
    }

    @Override
    public List<Map<String, Object>> listEnrolledStudents(Long classId) {
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassEnrollmentEntity enrollment : enrollments) {
            StudentEntity student = studentMapper.selectById(enrollment.getStudentId());
            if (student == null) continue;
            String expiryStatus;
            if (enrollment.getExpiryDate().isBefore(today)) {
                expiryStatus = "EXPIRED";
            } else if (enrollment.getExpiryDate().isBefore(today.plusDays(30))) {
                expiryStatus = "EXPIRING_SOON";
            } else {
                expiryStatus = "NORMAL";
            }
            Map<String, Object> item = new HashMap<>();
            item.put("studentId", enrollment.getStudentId());
            item.put("studentName", student.getStudentName());
            item.put("enrollmentDate", enrollment.getEnrollmentDate());
            item.put("expiryDate", enrollment.getExpiryDate());
            item.put("enrollmentStatus", enrollment.getStatus());
            item.put("expiryStatus", expiryStatus);
            result.add(item);
        }
        // 即将到期排最前
        result.sort((a, b) -> {
            String sa = (String) a.get("expiryStatus");
            String sb = (String) b.get("expiryStatus");
            if (sa.equals("EXPIRED") && !sb.equals("EXPIRED")) return -1;
            if (!sa.equals("EXPIRED") && sb.equals("EXPIRED")) return 1;
            if (sa.equals("EXPIRING_SOON") && !sb.equals("EXPIRING_SOON")) return -1;
            if (!sa.equals("EXPIRING_SOON") && sb.equals("EXPIRING_SOON")) return 1;
            return 0;
        });
        return result;
    }

    @Override
    public List<Map<String, Object>> listClassOccurrences(Long classId) {
        // 查询该班课的所有排课记录
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (LessonOccurrenceEntity occ : occurrences) {
            // 查询核销记录（已确认的所有学生）
            List<LessonRecordEntity> records = lessonRecordMapper.selectList(
                    new LambdaQueryWrapper<LessonRecordEntity>()
                            .eq(LessonRecordEntity::getOccurrenceId, occ.getId())
                            .last("LIMIT 1")
            );
            boolean hasRecord = !records.isEmpty();
            String resultStatus = hasRecord ? records.get(0).getResultStatus() : null;

            // 查询教师/教室名称
            TeacherEntity teacher = occ.getPlannedTeacherId() != null
                    ? teacherMapper.selectById(occ.getPlannedTeacherId()) : null;
            ClassroomEntity classroom = occ.getPlannedClassroomId() != null
                    ? classroomMapper.selectById(occ.getPlannedClassroomId()) : null;

            Map<String, Object> item = new HashMap<>();
            item.put("id", occ.getId());
            item.put("lessonNo", occ.getLessonNo());
            item.put("occurrenceType", occ.getOccurrenceType());
            item.put("plannedTeacherId", occ.getPlannedTeacherId());
            item.put("plannedTeacherName", teacher != null ? teacher.getTeacherName() : null);
            item.put("plannedClassroomId", occ.getPlannedClassroomId());
            item.put("plannedClassroomName", classroom != null ? classroom.getRoomName() : null);
            item.put("plannedStartAt", occ.getPlannedStartAt());
            item.put("plannedEndAt", occ.getPlannedEndAt());
            item.put("plannedUnits", occ.getPlannedUnits());
            item.put("planningStatus", occ.getPlanningStatus());
            item.put("resultStatus", resultStatus);
            item.put("confirmed", hasRecord);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmLesson(Long classId, Long occurrenceId, Map<String, Object> body) {
        // 获取排课记录
        LessonOccurrenceEntity occ = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occ == null || !classId.equals(occ.getClassGroupId())) {
            throw new BusinessException("OCCURRENCE_NOT_FOUND", "Lesson occurrence not found for this class.");
        }
        if (occ.getPlanningStatus().equals("CANCELLED")) {
            throw new BusinessException("OCCURRENCE_CANCELLED", "Cannot confirm a cancelled occurrence.");
        }
        // 检查是否已核销
        long existingCount = lessonRecordMapper.selectCount(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .eq(LessonRecordEntity::getOccurrenceId, occurrenceId)
        );
        if (existingCount > 0) {
            throw new BusinessException("ALREADY_CONFIRMED", "This lesson has already been confirmed.");
        }
        // 获取在册学生
        List<ClassEnrollmentEntity> activeEnrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        if (activeEnrollments.isEmpty()) {
            throw new BusinessException("NO_ACTIVE_STUDENTS", "No active students in this class.");
        }
        // 为每个在册学生创建 lesson_record
        int confirmedCount = 0;

        // 解析 body 参数
        Long actualTeacherId = body != null && body.get("actualTeacherId") instanceof Number
                ? ((Number) body.get("actualTeacherId")).longValue() : occ.getPlannedTeacherId();
        Long actualClassroomId = body != null && body.get("actualClassroomId") instanceof Number
                ? ((Number) body.get("actualClassroomId")).longValue() : occ.getPlannedClassroomId();
        Integer actualUnits = body != null && body.get("actualUnits") instanceof Number
                ? ((Number) body.get("actualUnits")).intValue() : null;
        String notes = body != null && body.get("notes") instanceof String
                ? (String) body.get("notes") : null;

        for (ClassEnrollmentEntity enrollment : activeEnrollments) {
            LessonRecordEntity record = new LessonRecordEntity();
            record.setOccurrenceId(occurrenceId);
            record.setStudentId(enrollment.getStudentId());
            record.setPackageId(null); // 班课不关联课包
            record.setResultStatus("COMPLETED");
            record.setActualTeacherId(actualTeacherId);
            record.setActualClassroomId(actualClassroomId);
            record.setActualUnits(actualUnits != null ? actualUnits : occ.getPlannedUnits());
            record.setShouldDeduct(0); // 班课不扣课时
            record.setShouldExtendEndDate(0);
            record.setRecordSource("CLASS_CONFIRM");
            record.setNotes(notes);
            record.setConfirmedAt(LocalDateTime.now());
            lessonRecordMapper.insert(record);
            confirmedCount++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("confirmedCount", confirmedCount);
        result.put("occurrenceId", occurrenceId);
        return result;
    }

    @Override
    public Map<String, Object> sendExpiryReminders(Long classId) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(30);
        // 查找即将到期和已到期的学生
        List<ClassEnrollmentEntity> expiring = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .le(ClassEnrollmentEntity::getExpiryDate, cutoff)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        if (expiring.isEmpty()) {
            return Map.of("sentCount", 0);
        }
        ClassGroupEntity clz = classGroupMapper.selectById(classId);
        // 发送邮件（简化实现：仅统计，实际发送邮件通过通知模块）
        int sentCount = 0;
        for (ClassEnrollmentEntity enrollment : expiring) {
            StudentEntity student = studentMapper.selectById(enrollment.getStudentId());
            if (student == null || student.getGuardianEmail() == null) continue;
            // TODO: 通过通知模块发送续费提醒邮件
            sentCount++;
        }
        return Map.of("sentCount", sentCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> enrollStudent(Long classId, Long studentId, LocalDate enrollmentDate, LocalDate expiryDate) {
        // 校验班课存在且活跃
        ClassGroupEntity clz = classGroupMapper.selectById(classId);
        if (clz == null || !"ACTIVE".equals(clz.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        // 校验学生存在
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("STUDENT_NOT_FOUND", "Student not found.");
        }
        // 校验未重复报名
        Long existingCount = classEnrollmentMapper.selectCount(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        if (existingCount > 0) {
            throw new BusinessException("ALREADY_ENROLLED", "Student is already enrolled in this class.");
        }
        // 创建报名记录
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setClassId(classId);
        enrollment.setStudentId(studentId);
        enrollment.setEnrollmentDate(enrollmentDate);
        enrollment.setExpiryDate(expiryDate);
        enrollment.setStatus("ACTIVE");
        classEnrollmentMapper.insert(enrollment);

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("studentName", student.getStudentName());
        result.put("enrollmentDate", enrollmentDate);
        result.put("expiryDate", expiryDate);
        return result;
    }

    @Override
    public List<Map<String, Object>> listEnrolledByStudent(Long studentId) {
        // 查询学生已报名的活跃班课
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> classIds = enrollments.stream()
                .map(ClassEnrollmentEntity::getClassId)
                .toList();

        // 批量查询班课信息
        Map<Long, ClassGroupEntity> classMap = classGroupMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassGroupEntity::getId, c -> c));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassEnrollmentEntity enrollment : enrollments) {
            ClassGroupEntity clz = classMap.get(enrollment.getClassId());
            if (clz == null || !"ACTIVE".equals(clz.getStatus())) continue;

            TeacherEntity teacher = clz.getTeacherId() != null
                    ? teacherMapper.selectById(clz.getTeacherId()) : null;
            ClassroomEntity classroom = clz.getClassroomId() != null
                    ? classroomMapper.selectById(clz.getClassroomId()) : null;

            Map<String, Object> item = new HashMap<>();
            item.put("id", clz.getId());
            item.put("className", clz.getClassName());
            item.put("description", clz.getDescription());
            item.put("scheduleDesc", clz.getScheduleDesc());
            item.put("teacherName", teacher != null ? teacher.getTeacherName() : null);
            item.put("classroomName", classroom != null ? classroom.getRoomName() : null);
            item.put("scheduleSlots", toScheduleSlotList(clz.getScheduleDesc()));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listByTeacher(Long teacherId) {
        List<ClassGroupEntity> classes = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getTeacherId, teacherId)
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .isNotNull(ClassGroupEntity::getProductId)
        );
        return classes.stream().map(this::toClassSlotMap).toList();
    }

    @Override
    public List<Map<String, Object>> listByClassroom(Long classroomId) {
        List<ClassGroupEntity> classes = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getClassroomId, classroomId)
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .isNotNull(ClassGroupEntity::getProductId)
        );
        return classes.stream().map(this::toClassSlotMap).toList();
    }

    /**
     * 将 ClassGroupEntity 转为包含解析时段的前端 Map。
     */
    private Map<String, Object> toClassSlotMap(ClassGroupEntity clz) {
        TeacherEntity teacher = clz.getTeacherId() != null
                ? teacherMapper.selectById(clz.getTeacherId()) : null;
        ClassroomEntity classroom = clz.getClassroomId() != null
                ? classroomMapper.selectById(clz.getClassroomId()) : null;

        Map<String, Object> item = new HashMap<>();
        item.put("id", clz.getId());
        item.put("className", clz.getClassName());
        item.put("description", clz.getDescription());
        item.put("scheduleDesc", clz.getScheduleDesc());
        item.put("teacherName", teacher != null ? teacher.getTeacherName() : null);
        item.put("classroomName", classroom != null ? classroom.getRoomName() : null);
        item.put("listPrice", clz.getListPrice());
        item.put("productId", clz.getProductId());
        item.put("status", clz.getStatus());
        item.put("scheduleSlots", toScheduleSlotList(clz.getScheduleDesc()));
        return item;
    }

    /**
     * 解析 scheduleDesc 为前端可用的 slot 列表。
     */
    private List<Map<String, Object>> toScheduleSlotList(String scheduleDesc) {
        return ScheduleDescParser.parse(scheduleDesc).stream()
                .map(slot -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("weekday", slot.weekday());
                    m.put("startTime", slot.startTime());
                    m.put("endTime", slot.endTime());
                    m.put("plannedUnits", slot.plannedUnits());
                    return m;
                })
                .toList();
    }
}
