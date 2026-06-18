package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductScheduleSlotEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassGroupServiceImpl implements ClassGroupService {

    private static final String CONFIRM_STAGE_PENDING_ADMIN = "PENDING_ADMIN";
    private static final String CONFIRM_STAGE_CONFIRMED = "CONFIRMED";
    private static final String CONFIRM_STAGE_REJECTED = "REJECTED";
    private static final Set<String> ALLOWED_TEACHER_CONFIRM_STATUSES = Set.of("COMPLETED", "NO_SHOW", "FORMAL_LEAVE");
    private static final int TEACHER_PAST_CONFIRM_WINDOW_DAYS = 7;
    private static final int TEACHER_FUTURE_CONFIRM_WINDOW_HOURS = 6;

    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseProductScheduleSlotMapper courseProductScheduleSlotMapper;

    @Override
    public List<Map<String, Object>> listClasses() {
        List<ClassGroupEntity> classes = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .isNotNull(ClassGroupEntity::getProductId)
                        .orderByAsc(ClassGroupEntity::getClassName)
        );
        if (classes.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量统计在册人数
        List<Long> classIds = classes.stream().map(ClassGroupEntity::getId).toList();
        Map<Long, Long> enrollmentCounts = toLongMap(classEnrollmentMapper.countActiveByClassIds(classIds), "class_id", "cnt");

        // 批量加载教师和教室
        Map<Long, TeacherEntity> teacherMap = batchLoadTeachers(classes, ClassGroupEntity::getTeacherId);
        Map<Long, ClassroomEntity> classroomMap = batchLoadClassrooms(classes, ClassGroupEntity::getClassroomId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassGroupEntity clz : classes) {
            String teacherName = resolveTeacherName(clz.getTeacherId(), teacherMap);
            String classroomName = resolveClassroomName(clz.getClassroomId(), classroomMap);
            Map<String, Object> item = new HashMap<>();
            item.put("id", clz.getId());
            item.put("className", clz.getClassName());
            item.put("description", clz.getDescription());
            item.put("scheduleDesc", clz.getScheduleDesc());
            item.put("teacherId", clz.getTeacherId());
            item.put("teacherName", teacherName);
            item.put("classroomId", clz.getClassroomId());
            item.put("classroomName", classroomName);
            item.put("listPrice", clz.getListPrice());
            item.put("productId", clz.getProductId());
            item.put("status", clz.getStatus());
            item.put("activeStudentCount", enrollmentCounts.getOrDefault(clz.getId(), 0L));
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
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量加载学生
        List<Long> studentIds = enrollments.stream()
                .map(ClassEnrollmentEntity::getStudentId)
                .toList();
        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(StudentEntity::getId, s -> s));

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassEnrollmentEntity enrollment : enrollments) {
            StudentEntity student = studentMap.get(enrollment.getStudentId());
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
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt)
        );
        if (occurrences.isEmpty()) {
            return Collections.emptyList();
        }

        // Return the latest confirmation record, including REJECTED, so the UI can show retry state.
        List<Long> occurrenceIds = occurrences.stream().map(LessonOccurrenceEntity::getId).toList();
        List<LessonRecordEntity> activeRecords = lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                        .orderByAsc(LessonRecordEntity::getId));
        Map<Long, LessonRecordEntity> recordByOccurrence = activeRecords.stream().collect(Collectors.toMap(
                LessonRecordEntity::getOccurrenceId,
                item -> item,
                (a, b) -> b
        ));

        // 批量加载教师和教室
        Map<Long, TeacherEntity> teacherMap = batchLoadTeachers(occurrences, LessonOccurrenceEntity::getPlannedTeacherId);
        Map<Long, ClassroomEntity> classroomMap = batchLoadClassrooms(occurrences, LessonOccurrenceEntity::getPlannedClassroomId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (LessonOccurrenceEntity occ : occurrences) {
            LessonRecordEntity record = recordByOccurrence.get(occ.getId());
            boolean hasRecord = record != null && CONFIRM_STAGE_CONFIRMED.equals(record.getConfirmStage());
            String teacherName = resolveTeacherName(occ.getPlannedTeacherId(), teacherMap);
            String classroomName = resolveClassroomName(occ.getPlannedClassroomId(), classroomMap);

            Map<String, Object> item = new HashMap<>();
            item.put("id", occ.getId());
            item.put("lessonNo", occ.getLessonNo());
            item.put("occurrenceType", occ.getOccurrenceType());
            item.put("plannedTeacherId", occ.getPlannedTeacherId());
            item.put("plannedTeacherName", teacherName);
            item.put("plannedClassroomId", occ.getPlannedClassroomId());
            item.put("plannedClassroomName", classroomName);
            item.put("plannedStartAt", occ.getPlannedStartAt());
            item.put("plannedEndAt", occ.getPlannedEndAt());
            item.put("plannedUnits", occ.getPlannedUnits());
            item.put("planningStatus", occ.getPlanningStatus());
            item.put("lessonRecordId", record == null ? null : record.getId());
            item.put("recordId", record == null ? null : record.getId());
            item.put("confirmStage", record == null ? null : record.getConfirmStage());
            item.put("teacherConfirmedAt", record == null ? null : record.getTeacherConfirmedAt());
            item.put("teacherConfirmedByUserId", record == null ? null : record.getTeacherConfirmedByUserId());
            item.put("adminConfirmedAt", record == null ? null : record.getAdminConfirmedAt());
            item.put("adminConfirmedByUserId", record == null ? null : record.getAdminConfirmedByUserId());
            item.put("resultStatus", record == null ? null : record.getResultStatus());
            item.put("rejectReason", record == null ? null : record.getRejectReason());
            item.put("rejectedAt", record == null ? null : record.getRejectedAt());
            item.put("rejectedByUserId", record == null ? null : record.getRejectedByUserId());
            item.put("actualTeacherId", record == null ? null : record.getActualTeacherId());
            item.put("actualClassroomId", record == null ? null : record.getActualClassroomId());
            item.put("actualStartAt", record == null ? null : record.getActualStartAt());
            item.put("actualEndAt", record == null ? null : record.getActualEndAt());
            item.put("actualUnits", record == null ? null : record.getActualUnits());
            item.put("notes", record == null ? null : record.getNotes());
            item.put("confirmed", hasRecord);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmLesson(Long classId, Long occurrenceId, Map<String, Object> body, CurrentUserResponse currentUser) {
        currentUser = defaultAdminIfMissing(currentUser);
        // 获取排课记录
        LessonOccurrenceEntity occ = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occ == null || !classId.equals(occ.getClassGroupId())) {
            throw new BusinessException("OCCURRENCE_NOT_FOUND", "Lesson occurrence not found for this class.");
        }
        if (occ.getPlanningStatus().equals("CANCELLED")) {
            throw new BusinessException("OCCURRENCE_CANCELLED", "Cannot confirm a cancelled occurrence.");
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
        // 为每个在册学生创建 lesson_record（班课确认频率低，无需批量优化）
        int confirmedCount = 0;

        // 解析 body 参数（支持 Number 和 String，因为前端 transformResponse 会将大数字 ID 转为字符串防止精度丢失）
        Long actualTeacherId = parseLongParam(body, "actualTeacherId", occ.getPlannedTeacherId());
        Long actualClassroomId = parseLongParam(body, "actualClassroomId", occ.getPlannedClassroomId());
        Integer actualUnits = body != null && body.get("actualUnits") instanceof Number
                ? Integer.valueOf(((Number) body.get("actualUnits")).intValue()) : null;
        LocalDateTime actualStartAt = parseDateTimeParam(body, "actualStartAt");
        LocalDateTime actualEndAt = parseDateTimeParam(body, "actualEndAt");
        String notes = body != null && body.get("notes") instanceof String
                ? (String) body.get("notes") : null;
        String resultStatus = body != null && body.get("resultStatus") instanceof String s && !s.isBlank()
                ? s : "COMPLETED";
        boolean teacherConfirm = isTeacherUser(currentUser);
        if (teacherConfirm) {
            validateTeacherClassConfirm(occ, resultStatus, currentUser);
        }

        for (ClassEnrollmentEntity enrollment : activeEnrollments) {
            LessonRecordEntity existing = findLatestClassRecord(occurrenceId, enrollment.getStudentId());
            if (existing != null && !CONFIRM_STAGE_REJECTED.equals(existing.getConfirmStage())) {
                if (teacherConfirm || CONFIRM_STAGE_CONFIRMED.equals(existing.getConfirmStage())) {
                    continue;
                }
                applyClassActualValues(existing, occ, actualTeacherId, actualClassroomId, actualUnits, actualStartAt, actualEndAt, notes);
                finalizeClassRecord(existing, currentUser);
                lessonRecordMapper.updateById(existing);
                confirmedCount++;
                continue;
            }
            LessonRecordEntity record = existing == null ? new LessonRecordEntity() : existing;
            record.setOccurrenceId(occurrenceId);
            record.setStudentId(enrollment.getStudentId());
            record.setPackageId(null); // 班课不关联课包
            record.setResultStatus(resultStatus);
            record.setPlannedTeacherId(occ.getPlannedTeacherId()); // 保存原始安排，供审计
            record.setPlannedClassroomId(occ.getPlannedClassroomId());
            record.setActualTeacherId(actualTeacherId);
            record.setActualClassroomId(actualClassroomId);
            record.setActualUnits(actualUnits != null ? actualUnits : occ.getPlannedUnits());
            record.setActualStartAt(actualStartAt);
            record.setActualEndAt(actualEndAt);
            record.setShouldDeduct(0); // 班课不扣课时
            record.setDeductedUnits(0);
            record.setShouldExtendEndDate(0);
            record.setNotes(notes);
            record.setRecordSource(teacherConfirm ? "TEACHER" : "CLASS_CONFIRM");
            record.setRejectReason(null);
            record.setRejectedAt(null);
            record.setRejectedByUserId(null);
            if (teacherConfirm) {
                record.setConfirmStage(CONFIRM_STAGE_PENDING_ADMIN);
                record.setTeacherConfirmedAt(AppTime.nowLocalDateTime());
                record.setTeacherConfirmedByUserId(currentUser.userId());
                record.setConfirmedAt(null);
                record.setConfirmedByUserId(null);
                record.setAdminConfirmedAt(null);
                record.setAdminConfirmedByUserId(null);
            } else {
                record.setConfirmStage(CONFIRM_STAGE_CONFIRMED);
                record.setTeacherConfirmedAt(null);
                record.setTeacherConfirmedByUserId(null);
                finalizeClassRecord(record, currentUser);
            }
            if (record.getId() == null) {
                lessonRecordMapper.insert(record);
            } else {
                lessonRecordMapper.updateById(record);
            }
            confirmedCount++;
        }

        // 老师初确认只登记待复核记录；管理员终确认/兜底确认才同步最终实际值。
        if (!teacherConfirm) {
            occ.setPlannedTeacherId(actualTeacherId);
            occ.setPlannedClassroomId(actualClassroomId);
            if (actualUnits != null) {
                occ.setPlannedUnits(actualUnits);
            }
            if (actualStartAt != null) {
                occ.setPlannedStartAt(actualStartAt);
                occ.setPlannedEndAt(actualEndAt);
            }
            occ.setPlanningStatus("MERGED");
            lessonOccurrenceMapper.updateById(occ);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("confirmedCount", confirmedCount);
        result.put("occurrenceId", occurrenceId);
        result.put("confirmStage", teacherConfirm ? CONFIRM_STAGE_PENDING_ADMIN : CONFIRM_STAGE_CONFIRMED);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectLesson(Long classId, Long occurrenceId, String rejectReason, CurrentUserResponse currentUser) {
        currentUser = defaultAdminIfMissing(currentUser);
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BusinessException("REJECT_REASON_REQUIRED", "Reject reason is required.");
        }
        LessonOccurrenceEntity occ = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occ == null || !classId.equals(occ.getClassGroupId())) {
            throw new BusinessException("OCCURRENCE_NOT_FOUND", "Lesson occurrence not found for this class.");
        }
        List<LessonRecordEntity> pendingRecords = lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .eq(LessonRecordEntity::getOccurrenceId, occurrenceId)
                        .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_PENDING_ADMIN));
        if (pendingRecords.isEmpty()) {
            throw new BusinessException("INVALID_CONFIRM_STAGE", "Only pending class lesson records can be rejected.");
        }
        for (LessonRecordEntity record : pendingRecords) {
            record.setConfirmStage(CONFIRM_STAGE_REJECTED);
            record.setRejectReason(rejectReason);
            record.setRejectedAt(AppTime.nowLocalDateTime());
            record.setRejectedByUserId(currentUser.userId());
            lessonRecordMapper.updateById(record);
        }
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

        Map<Long, ClassGroupEntity> classMap = classGroupMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassGroupEntity::getId, c -> c));

        // 批量加载教师和教室
        Map<Long, TeacherEntity> teacherMap = batchLoadTeachers(classMap.values(), ClassGroupEntity::getTeacherId);
        Map<Long, ClassroomEntity> classroomMap = batchLoadClassrooms(classMap.values(), ClassGroupEntity::getClassroomId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassEnrollmentEntity enrollment : enrollments) {
            ClassGroupEntity clz = classMap.get(enrollment.getClassId());
            if (clz == null || !"ACTIVE".equals(clz.getStatus())) continue;

            String teacherName = resolveTeacherName(clz.getTeacherId(), teacherMap);
            String classroomName = resolveClassroomName(clz.getClassroomId(), classroomMap);

            Map<String, Object> item = new HashMap<>();
            item.put("id", clz.getId());
            item.put("className", clz.getClassName());
            item.put("description", clz.getDescription());
            item.put("scheduleDesc", clz.getScheduleDesc());
            item.put("teacherName", teacherName);
            item.put("classroomName", classroomName);
            item.put("scheduleSlots", toScheduleSlotList(clz.getScheduleDesc(), clz.getProductId()));
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
        if (classes.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量加载后组装
        Map<Long, TeacherEntity> teacherMap = batchLoadTeachers(classes, ClassGroupEntity::getTeacherId);
        Map<Long, ClassroomEntity> classroomMap = batchLoadClassrooms(classes, ClassGroupEntity::getClassroomId);
        return classes.stream()
                .map(clz -> toClassSlotMap(clz, teacherMap, classroomMap))
                .toList();
    }

    @Override
    public List<Map<String, Object>> listByClassroom(Long classroomId) {
        List<ClassGroupEntity> classes = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getClassroomId, classroomId)
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .isNotNull(ClassGroupEntity::getProductId)
        );
        if (classes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, TeacherEntity> teacherMap = batchLoadTeachers(classes, ClassGroupEntity::getTeacherId);
        Map<Long, ClassroomEntity> classroomMap = batchLoadClassrooms(classes, ClassGroupEntity::getClassroomId);
        return classes.stream()
                .map(clz -> toClassSlotMap(clz, teacherMap, classroomMap))
                .toList();
    }

    // ========== 删除班课 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClassGroup(Long classId) {
        // 1. 校验班课存在（selectById 利用 @TableLogic 自动过滤已删除的）
        ClassGroupEntity clz = classGroupMapper.selectById(classId);
        if (clz == null) {
            throw new BusinessException("CLASS_GROUP_NOT_FOUND", "Class group not found or already deleted.");
        }

        LocalDateTime now = AppTime.nowLocalDateTime();

        // 2. 取消未来所有课次（已发生的课次保留历史数据）
        LessonOccurrenceEntity occUpdate = new LessonOccurrenceEntity();
        occUpdate.setPlanningStatus("CANCELLED");
        lessonOccurrenceMapper.update(occUpdate, new LambdaUpdateWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .gt(LessonOccurrenceEntity::getPlannedStartAt, now));

        // 3. 软删除所有活跃报名记录
        ClassEnrollmentEntity enrollUpdate = new ClassEnrollmentEntity();
        enrollUpdate.setIsDeleted(1);
        classEnrollmentMapper.update(enrollUpdate, new LambdaUpdateWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getClassId, classId)
                .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                .eq(ClassEnrollmentEntity::getIsDeleted, 0));

        // 4. 软删除班课本身
        classGroupMapper.deleteById(classId);
    }

    // ========== 批量查询辅助方法 ==========

    /**
     * 从实体集合中提取所有非空 teacherId 并批量查询。
     */
    private <T> Map<Long, TeacherEntity> batchLoadTeachers(Collection<T> entities,
                                                            java.util.function.Function<T, Long> teacherIdFn) {
        Set<Long> ids = entities.stream()
                .map(teacherIdFn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return teacherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TeacherEntity::getId, t -> t));
    }

    /**
     * 从实体集合中提取所有非空 classroomId 并批量查询。
     */
    private <T> Map<Long, ClassroomEntity> batchLoadClassrooms(Collection<T> entities,
                                                                java.util.function.Function<T, Long> classroomIdFn) {
        Set<Long> ids = entities.stream()
                .map(classroomIdFn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return classroomMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ClassroomEntity::getId, c -> c));
    }

    private CurrentUserResponse defaultAdminIfMissing(CurrentUserResponse currentUser) {
        if (currentUser != null) {
            return currentUser;
        }
        return new CurrentUserResponse(2049011197944602625L, "admin", "System Administrator", "ADMIN", null, null, java.util.List.of("ADMIN"));
    }

    private boolean isTeacherUser(CurrentUserResponse currentUser) {
        return currentUser.roleCodes() != null && currentUser.roleCodes().contains("TEACHER");
    }

    private void validateTeacherClassConfirm(LessonOccurrenceEntity occurrence, String resultStatus, CurrentUserResponse currentUser) {
        if (currentUser.teacherId() == null) {
            throw new BusinessException("FORBIDDEN", "Only linked teacher accounts can confirm class lessons as a teacher.");
        }
        if (!currentUser.teacherId().equals(occurrence.getPlannedTeacherId())) {
            throw new BusinessException("FORBIDDEN", "Teachers can only confirm their own planned class lessons.");
        }
        if (!ALLOWED_TEACHER_CONFIRM_STATUSES.contains(resultStatus)) {
            throw new BusinessException("TEACHER_STATUS_NOT_ALLOWED", "Teachers can only confirm completed, no-show, or formal-leave results.");
        }
        LocalDateTime now = AppTime.nowLocalDateTime();
        if (occurrence.getPlannedStartAt().isAfter(now.plusHours(TEACHER_FUTURE_CONFIRM_WINDOW_HOURS))) {
            throw new BusinessException("TEACHER_CONFIRM_TOO_EARLY", "Teachers can only confirm lessons that are happening now or have already happened.");
        }
        if (occurrence.getPlannedEndAt().isBefore(now.minusDays(TEACHER_PAST_CONFIRM_WINDOW_DAYS))) {
            throw new BusinessException("TEACHER_CONFIRM_TOO_LATE", "This lesson is outside the teacher self-confirmation window.");
        }
    }

    private LessonRecordEntity findLatestClassRecord(Long occurrenceId, Long studentId) {
        return lessonRecordMapper.selectOne(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getOccurrenceId, occurrenceId)
                .eq(LessonRecordEntity::getStudentId, studentId)
                .orderByDesc(LessonRecordEntity::getCreatedAt)
                .orderByDesc(LessonRecordEntity::getId)
                .last("LIMIT 1"));
    }

    private void finalizeClassRecord(LessonRecordEntity record, CurrentUserResponse currentUser) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        record.setConfirmStage(CONFIRM_STAGE_CONFIRMED);
        record.setConfirmedAt(now);
        record.setConfirmedByUserId(currentUser.userId());
        record.setAdminConfirmedAt(now);
        record.setAdminConfirmedByUserId(currentUser.userId());
        record.setRejectReason(null);
        record.setRejectedAt(null);
        record.setRejectedByUserId(null);
    }

    private void applyClassActualValues(LessonRecordEntity record,
                                        LessonOccurrenceEntity occurrence,
                                        Long actualTeacherId,
                                        Long actualClassroomId,
                                        Integer actualUnits,
                                        LocalDateTime actualStartAt,
                                        LocalDateTime actualEndAt,
                                        String notes) {
        record.setActualTeacherId(actualTeacherId);
        record.setActualClassroomId(actualClassroomId);
        record.setActualUnits(actualUnits != null ? actualUnits : occurrence.getPlannedUnits());
        record.setActualStartAt(actualStartAt);
        record.setActualEndAt(actualEndAt);
        record.setNotes(notes);
    }

    /**
     * 从 body Map 中解析 Long 参数，支持 Number 和 String 两种类型。
     * 前端 transformResponse 会将大数字 ID 转成字符串以防止 JS 精度丢失，
     * 因此不能仅用 instanceof Number 判断。
     */
    private Long parseLongParam(Map<String, Object> body, String key, Long fallback) {
        if (body == null) return fallback;
        Object value = body.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private LocalDateTime parseDateTimeParam(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        if (value instanceof String s && !s.isBlank()) {
            try {
                return LocalDateTime.parse(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String resolveTeacherName(Long teacherId, Map<Long, TeacherEntity> teacherMap) {
        if (teacherId == null) return null;
        TeacherEntity t = teacherMap.get(teacherId);
        return t != null ? t.getTeacherName() : null;
    }

    private String resolveClassroomName(Long classroomId, Map<Long, ClassroomEntity> classroomMap) {
        if (classroomId == null) return null;
        ClassroomEntity c = classroomMap.get(classroomId);
        return c != null ? c.getRoomName() : null;
    }

    private Map<Long, Long> toLongMap(List<Map<String, Object>> rows, String keyCol, String valCol) {
        Map<Long, Long> result = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object keyObj = row.get(keyCol);
                Object valObj = row.get(valCol);
                if (keyObj != null && valObj != null) {
                    result.put(((Number) keyObj).longValue(), ((Number) valObj).longValue());
                }
            }
        }
        return result;
    }

    /**
     * 将 ClassGroupEntity 转为包含解析时段的前端 Map（使用预加载的 teacher/classroom map）。
     */
    private Map<String, Object> toClassSlotMap(ClassGroupEntity clz,
                                                Map<Long, TeacherEntity> teacherMap,
                                                Map<Long, ClassroomEntity> classroomMap) {
        String teacherName = resolveTeacherName(clz.getTeacherId(), teacherMap);
        String classroomName = resolveClassroomName(clz.getClassroomId(), classroomMap);

        Map<String, Object> item = new HashMap<>();
        item.put("id", clz.getId());
        item.put("className", clz.getClassName());
        item.put("description", clz.getDescription());
        item.put("scheduleDesc", clz.getScheduleDesc());
        item.put("teacherName", teacherName);
        item.put("classroomName", classroomName);
        item.put("listPrice", clz.getListPrice());
        item.put("productId", clz.getProductId());
        item.put("status", clz.getStatus());
        item.put("scheduleSlots", toScheduleSlotList(clz.getScheduleDesc(), clz.getProductId()));
        return item;
    }

    /**
     * 解析 scheduleDesc 为前端可用的 slot 列表。
     * 优先从产品排课时段表加载（更准确），降级到解析 scheduleDesc 文本。
     */
    private List<Map<String, Object>> toScheduleSlotList(String scheduleDesc, Long productId) {
        // Prefer product schedule slots when productId is available (authoritative data)
        if (productId != null) {
            List<CourseProductScheduleSlotEntity> slots = courseProductScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                            .eq(CourseProductScheduleSlotEntity::getProductId, productId)
                            .eq(CourseProductScheduleSlotEntity::getIsDeleted, 0));
            if (!slots.isEmpty()) {
                return slots.stream()
                        .map(slot -> {
                            int minutes = (int) ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
                            return toSlotMap(slot.getWeekday(),
                                    slot.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                                    slot.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                                    minutes > 0 ? minutes / 30 : 0);
                        })
                        .toList();
            }
        }
        // Fallback: parse scheduleDesc text
        if (scheduleDesc != null) {
            return ScheduleDescParser.parse(scheduleDesc).stream()
                    .map(slot -> toSlotMap(slot.weekday(), slot.startTime(), slot.endTime(), slot.plannedUnits()))
                    .toList();
        }
        return Collections.emptyList();
    }

    private Map<String, Object> toSlotMap(int weekday, String startTime, String endTime, int plannedUnits) {
        Map<String, Object> m = new HashMap<>();
        m.put("weekday", weekday);
        m.put("startTime", startTime);
        m.put("endTime", endTime);
        m.put("plannedUnits", plannedUnits);
        return m;
    }
}
