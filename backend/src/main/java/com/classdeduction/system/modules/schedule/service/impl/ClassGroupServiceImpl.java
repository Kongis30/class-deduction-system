package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.CourseProductScheduleSlotEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
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
    private static final Set<String> ALLOWED_TEACHER_CONFIRM_STATUSES = Set.of("COMPLETED");
    private static final Set<String> ALLOWED_ADMIN_CONFIRM_STATUSES = Set.of(
            "COMPLETED", "CANCELLED");
    private static final Set<String> CHARGEABLE_CLASS_RESULT_STATUSES = Set.of(
            "COMPLETED", "CANCELLED");
    private static final int TEACHER_FUTURE_CONFIRM_WINDOW_DAYS = 14;

    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseProductMapper courseProductMapper;
    private final CourseProductScheduleSlotMapper courseProductScheduleSlotMapper;

    @Override
    public List<Map<String, Object>> listClasses(CurrentUserResponse currentUser) {
        LambdaQueryWrapper<ClassGroupEntity> query = new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getStatus, "ACTIVE")
                .isNotNull(ClassGroupEntity::getProductId)
                .orderByAsc(ClassGroupEntity::getClassName);
        if (isTeacherUser(currentUser)) {
            query.eq(ClassGroupEntity::getTeacherId, requireTeacherId(currentUser));
        }
        List<ClassGroupEntity> classes = classGroupMapper.selectList(query);
        if (classes.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量统计在册人数
        List<Long> classIds = classes.stream().map(ClassGroupEntity::getId).toList();
        Map<Long, Long> enrollmentCounts = toLongMap(classEnrollmentMapper.countActiveByClassIds(classIds), "class_id", "cnt");

        // 批量加载教师和教室
        Map<Long, TeacherEntity> teacherMap = batchLoadTeachers(classes, ClassGroupEntity::getTeacherId);
        Map<Long, ClassroomEntity> classroomMap = batchLoadClassrooms(classes, ClassGroupEntity::getClassroomId);
        Map<Long, CourseProductEntity> productMap = batchLoadCourseProducts(classes);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassGroupEntity clz : classes) {
            String teacherName = resolveTeacherName(clz.getTeacherId(), teacherMap);
            String classroomName = resolveClassroomName(clz.getClassroomId(), classroomMap);
            CourseProductEntity product = productMap.get(clz.getProductId());
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
            item.put("productName", product == null ? null : product.getProductName());
            item.put("registerClass", product == null ? null : product.getRegisterClass());
            item.put("courseType", product == null ? null : product.getCourseType());
            item.put("status", clz.getStatus());
            item.put("scheduleSlots", toScheduleSlotList(clz.getId(), clz.getScheduleDesc(), clz.getProductId()));
            item.put("activeStudentCount", enrollmentCounts.getOrDefault(clz.getId(), 0L));
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> getClassDetail(Long classId, CurrentUserResponse currentUser) {
        ClassGroupEntity clz = classGroupMapper.selectById(classId);
        if (clz == null) throw new BusinessException("CLASS_NOT_FOUND", "Class not found.");
        validateTeacherClassAccess(clz, currentUser);
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
    public List<Map<String, Object>> listEnrolledStudents(Long classId, CurrentUserResponse currentUser) {
        requireClassForRead(classId, currentUser);
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
        Map<Long, List<LocalDate>> confirmedLessonDatesByStudent = loadConfirmedLessonDatesByStudent(classId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassEnrollmentEntity enrollment : enrollments) {
            StudentEntity student = studentMap.get(enrollment.getStudentId());
            if (student == null) continue;
            Long usedSessions = null;
            Integer remainingSessions = null;
            if ("PER_SESSION".equals(enrollment.getBillingType()) && enrollment.getPurchasedQuantity() != null) {
                usedSessions = countUsedSessions(enrollment, confirmedLessonDatesByStudent);
                remainingSessions = Math.max(enrollment.getPurchasedQuantity() - usedSessions.intValue(), 0);
            }

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
            item.put("billingType", enrollment.getBillingType());
            item.put("purchasedQuantity", enrollment.getPurchasedQuantity());
            item.put("usedSessions", usedSessions);
            item.put("remainingSessions", remainingSessions);
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
    public List<Map<String, Object>> listClassOccurrences(Long classId, CurrentUserResponse currentUser) {
        ClassGroupEntity classGroup = requireClassForRead(classId, currentUser);
        List<LessonOccurrenceEntity> occurrences = listClassOccurrenceEntities(classId);
        // Older grade classes may have weekly slots but no generated lesson rows
        // until a student enrolls. Backfill planned rows so schedules are visible.
        if (occurrences.isEmpty()) {
            List<ClassGroupScheduleSlotEntity> slots = listClassScheduleSlots(classId);
            if (!slots.isEmpty()) {
                generateFutureClassOccurrences(classGroup, slots, Collections.emptySet());
                occurrences = listClassOccurrenceEntities(classId);
            }
            if (occurrences.isEmpty()) {
                return Collections.emptyList();
            }
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

    private List<LessonOccurrenceEntity> listClassOccurrenceEntities(Long classId) {
        return lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
    }

    private List<ClassGroupScheduleSlotEntity> listClassScheduleSlots(Long classId) {
        return classGroupScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId)
                        .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
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
        Map<Long, List<LocalDate>> confirmedLessonDatesByStudent = loadConfirmedLessonDatesByStudent(classId);
        activeEnrollments = activeEnrollments.stream()
                .filter(enrollment -> isEnrollmentEligibleForOccurrence(
                        enrollment, occ, confirmedLessonDatesByStudent))
                .toList();
        if (activeEnrollments.isEmpty()) {
            throw new BusinessException("NO_ACTIVE_STUDENTS", "No active eligible students in this class lesson.");
        }
        // 为每个在册学生创建 lesson_record（班课确认频率低，无需批量优化）
        int confirmedCount = 0;
        boolean teacherConfirm = isTeacherUser(currentUser);
        Map<Long, LessonRecordEntity> existingRecordsByStudent = new LinkedHashMap<>();
        for (ClassEnrollmentEntity enrollment : activeEnrollments) {
            existingRecordsByStudent.put(enrollment.getStudentId(),
                    findLatestClassRecord(occurrenceId, enrollment.getStudentId()));
        }
        LessonRecordEntity pendingRecord = teacherConfirm ? null : existingRecordsByStudent.values().stream()
                .filter(Objects::nonNull)
                .filter(record -> CONFIRM_STAGE_PENDING_ADMIN.equals(record.getConfirmStage()))
                .findFirst()
                .orElse(null);

        // 解析 body 参数（支持 Number 和 String，因为前端 transformResponse 会将大数字 ID 转为字符串防止精度丢失）
        Long actualTeacherId = parseLongParam(body, "actualTeacherId",
                pendingRecord == null ? occ.getPlannedTeacherId() : pendingRecord.getActualTeacherId());
        Long actualClassroomId = parseLongParam(body, "actualClassroomId",
                pendingRecord == null ? occ.getPlannedClassroomId() : pendingRecord.getActualClassroomId());
        if (actualTeacherId == null) {
            throw new BusinessException("CLASS_TEACHER_REQUIRED",
                    "Assign a teacher before confirming this class lesson.");
        }
        Integer actualUnits = body != null && body.get("actualUnits") instanceof Number
                ? Integer.valueOf(((Number) body.get("actualUnits")).intValue())
                : pendingRecord == null ? null : pendingRecord.getActualUnits();
        LocalDateTime actualStartAt = parseDateTimeParam(body, "actualStartAt");
        LocalDateTime actualEndAt = parseDateTimeParam(body, "actualEndAt");
        if (pendingRecord != null) {
            actualStartAt = actualStartAt == null ? pendingRecord.getActualStartAt() : actualStartAt;
            actualEndAt = actualEndAt == null ? pendingRecord.getActualEndAt() : actualEndAt;
        }
        String notes = body != null && body.get("notes") instanceof String
                ? (String) body.get("notes") : pendingRecord == null ? null : pendingRecord.getNotes();
        String resultStatus = body != null && body.get("resultStatus") instanceof String s && !s.isBlank()
                ? s : pendingRecord == null ? "COMPLETED" : pendingRecord.getResultStatus();
        Set<String> allowedStatuses = teacherConfirm
                ? ALLOWED_TEACHER_CONFIRM_STATUSES
                : ALLOWED_ADMIN_CONFIRM_STATUSES;
        if (!allowedStatuses.contains(resultStatus)) {
            throw new BusinessException("CLASS_RESULT_STATUS_NOT_ALLOWED",
                    teacherConfirm
                            ? "Teachers can only submit completed class lessons."
                            : "Class lessons only support completed or cancelled results.");
        }
        if (actualUnits != null && actualUnits <= 0) {
            throw new BusinessException("INVALID_ACTUAL_UNITS", "Actual lesson hours must be greater than zero.");
        }
        if (actualStartAt != null || actualEndAt != null) {
            if (actualStartAt == null || actualEndAt == null || !actualEndAt.isAfter(actualStartAt)) {
                throw new BusinessException("INVALID_ACTUAL_TIME",
                        "Actual lesson start and end time must form a valid time range.");
            }
            long actualMinutes = ChronoUnit.MINUTES.between(actualStartAt, actualEndAt);
            if (actualMinutes % 30 != 0) {
                throw new BusinessException("INVALID_ACTUAL_TIME",
                        "Actual lesson duration must use 0.5-hour increments.");
            }
            int unitsFromTime = (int) (actualMinutes / 30);
            if (actualUnits != null && !actualUnits.equals(unitsFromTime)) {
                throw new BusinessException("ACTUAL_TIME_UNITS_MISMATCH",
                        "Actual lesson hours must match the selected start and end time.");
            }
            actualUnits = unitsFromTime;
        }
        if (teacherConfirm) {
            validateTeacherClassConfirm(occ, resultStatus, currentUser);
        } else {
            ensureNoClassConfirmConflicts(occ, activeEnrollments, actualTeacherId,
                    actualClassroomId, actualStartAt, actualEndAt);
        }

        for (ClassEnrollmentEntity enrollment : activeEnrollments) {
            LessonRecordEntity existing = existingRecordsByStudent.get(enrollment.getStudentId());
            if (existing != null && !CONFIRM_STAGE_REJECTED.equals(existing.getConfirmStage())) {
                if (teacherConfirm || CONFIRM_STAGE_CONFIRMED.equals(existing.getConfirmStage())) {
                    continue;
                }
                applyClassActualValues(existing, occ, resultStatus, actualTeacherId, actualClassroomId, actualUnits, actualStartAt, actualEndAt, notes);
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

    private boolean isEnrollmentEligibleForOccurrence(ClassEnrollmentEntity enrollment,
                                                       LessonOccurrenceEntity occurrence,
                                                       Map<Long, List<LocalDate>> confirmedLessonDatesByStudent) {
        if (occurrence.getPlannedStartAt() == null) {
            return true;
        }
        LocalDate lessonDate = occurrence.getPlannedStartAt().toLocalDate();
        if (enrollment.getEnrollmentDate() != null && lessonDate.isBefore(enrollment.getEnrollmentDate())) {
            return false;
        }
        if (enrollment.getExpiryDate() != null && lessonDate.isAfter(enrollment.getExpiryDate())) {
            return false;
        }
        if (!"PER_SESSION".equals(enrollment.getBillingType()) || enrollment.getPurchasedQuantity() == null) {
            return true;
        }
        long usedSessions = countUsedSessions(enrollment, confirmedLessonDatesByStudent);
        return usedSessions < enrollment.getPurchasedQuantity();
    }

    private long countUsedSessions(ClassEnrollmentEntity enrollment,
                                   Map<Long, List<LocalDate>> confirmedLessonDatesByStudent) {
        return confirmedLessonDatesByStudent
                .getOrDefault(enrollment.getStudentId(), Collections.emptyList())
                .stream()
                .filter(date -> enrollment.getEnrollmentDate() == null || !date.isBefore(enrollment.getEnrollmentDate()))
                .filter(date -> enrollment.getExpiryDate() == null || !date.isAfter(enrollment.getExpiryDate()))
                .count();
    }

    private Map<Long, List<LocalDate>> loadConfirmedLessonDatesByStudent(Long classGroupId) {
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId, LessonOccurrenceEntity::getPlannedStartAt)
                        .eq(LessonOccurrenceEntity::getClassGroupId, classGroupId));
        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LocalDate> lessonDateByOccurrenceId = occurrences.stream()
                .filter(occurrence -> occurrence.getPlannedStartAt() != null)
                .collect(Collectors.toMap(
                        LessonOccurrenceEntity::getId,
                        occurrence -> occurrence.getPlannedStartAt().toLocalDate()));
        if (lessonDateByOccurrenceId.isEmpty()) {
            return Collections.emptyMap();
        }
        return lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, lessonDateByOccurrenceId.keySet())
                        .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_CONFIRMED)
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream()
                .filter(record -> record.getStudentId() != null
                        && lessonDateByOccurrenceId.containsKey(record.getOccurrenceId())
                        && CHARGEABLE_CLASS_RESULT_STATUSES.contains(record.getResultStatus()))
                .collect(Collectors.groupingBy(
                        LessonRecordEntity::getStudentId,
                        Collectors.mapping(
                                record -> lessonDateByOccurrenceId.get(record.getOccurrenceId()),
                                Collectors.toList())
                ));
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
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateClassSchedule(Long classId, Map<String, Object> body) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        Long teacherId = parseLongFromBody(body, "teacherId");
        Long classroomId = parseLongFromBody(body, "classroomId");
        List<ClassGroupScheduleSlotEntity> slots = parseClassScheduleSlots(body.get("slots"));
        if (slots.isEmpty()) {
            throw new BusinessException("CLASS_SCHEDULE_REQUIRED", "Add at least one weekly class time.");
        }
        validateClassScheduleSlots(slots);
        validateTeacher(teacherId);
        validateClassroom(classroomId);
        lockClassScheduleResources(classId, teacherId, classroomId);

        classGroup.setTeacherId(teacherId);
        classGroup.setClassroomId(classroomId);
        classGroup.setScheduleDesc(buildScheduleDesc(slots));
        classGroupMapper.updateById(classGroup);

        classGroupScheduleSlotMapper.delete(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId));
        for (ClassGroupScheduleSlotEntity slot : slots) {
            slot.setClassGroupId(classId);
            classGroupScheduleSlotMapper.insert(slot);
        }

        int cancelledCount = removeFutureUnconfirmedOccurrences(classId);
        Set<LocalDate> preservedDates = futurePreservedClassOccurrenceDates(classId);
        int generatedCount = generateFutureClassOccurrences(classGroup, slots, preservedDates);
        return Map.of("classId", classId, "cancelledFutureOccurrences", cancelledCount, "generatedOccurrences", generatedCount);
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
            item.put("scheduleSlots", toScheduleSlotList(clz.getId(), clz.getScheduleDesc(), clz.getProductId()));
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

    private Map<Long, CourseProductEntity> batchLoadCourseProducts(Collection<ClassGroupEntity> classes) {
        Set<Long> ids = classes.stream()
                .map(ClassGroupEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return courseProductMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CourseProductEntity::getId, product -> product));
    }

    private CurrentUserResponse defaultAdminIfMissing(CurrentUserResponse currentUser) {
        if (currentUser != null) {
            return currentUser;
        }
        return new CurrentUserResponse(2049011197944602625L, "admin", "System Administrator", "ADMIN", null, null, java.util.List.of("ADMIN"));
    }

    private boolean isTeacherUser(CurrentUserResponse currentUser) {
        return currentUser != null
                && (Objects.equals("TEACHER", currentUser.userType())
                || (currentUser.roleCodes() != null && currentUser.roleCodes().contains("TEACHER")));
    }

    private Long requireTeacherId(CurrentUserResponse currentUser) {
        if (currentUser == null || currentUser.teacherId() == null) {
            throw new BusinessException("TEACHER_BINDING_REQUIRED",
                    "This teacher account is not linked to a teacher profile.");
        }
        return currentUser.teacherId();
    }

    private void validateTeacherClassAccess(ClassGroupEntity classGroup, CurrentUserResponse currentUser) {
        if (isTeacherUser(currentUser)
                && !Objects.equals(classGroup.getTeacherId(), requireTeacherId(currentUser))) {
            throw new BusinessException("FORBIDDEN", "Teachers can only view their own classes.");
        }
    }

    private ClassGroupEntity requireClassForRead(Long classId, CurrentUserResponse currentUser) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found.");
        }
        validateTeacherClassAccess(classGroup, currentUser);
        return classGroup;
    }

    private void validateTeacherClassConfirm(LessonOccurrenceEntity occurrence, String resultStatus, CurrentUserResponse currentUser) {
        if (currentUser.teacherId() == null) {
            throw new BusinessException("FORBIDDEN", "Only linked teacher accounts can confirm class lessons as a teacher.");
        }
        if (!currentUser.teacherId().equals(occurrence.getPlannedTeacherId())) {
            throw new BusinessException("FORBIDDEN", "Teachers can only confirm their own planned class lessons.");
        }
        if (!ALLOWED_TEACHER_CONFIRM_STATUSES.contains(resultStatus)) {
            throw new BusinessException("TEACHER_STATUS_NOT_ALLOWED", "Teachers can only confirm completed class lessons.");
        }
        LocalDateTime now = AppTime.nowLocalDateTime();
        if (occurrence.getPlannedStartAt().isAfter(now.plusDays(TEACHER_FUTURE_CONFIRM_WINDOW_DAYS))) {
            throw new BusinessException("TEACHER_CONFIRM_TOO_EARLY", "Lesson confirmation is available 14 days before the planned start time.");
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
                                        String resultStatus,
                                        Long actualTeacherId,
                                        Long actualClassroomId,
                                        Integer actualUnits,
                                        LocalDateTime actualStartAt,
                                        LocalDateTime actualEndAt,
                                        String notes) {
        record.setResultStatus(resultStatus);
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

    private void ensureNoClassConfirmConflicts(LessonOccurrenceEntity source,
                                               List<ClassEnrollmentEntity> enrollments,
                                               Long actualTeacherId,
                                               Long actualClassroomId,
                                               LocalDateTime actualStartAt,
                                               LocalDateTime actualEndAt) {
        LocalDateTime targetStartAt = actualStartAt != null ? actualStartAt : source.getPlannedStartAt();
        LocalDateTime targetEndAt = actualEndAt != null ? actualEndAt : source.getPlannedEndAt();
        if (actualTeacherId != null && hasOccurrenceConflict(source, targetStartAt, targetEndAt,
                wrapper -> wrapper.eq(LessonOccurrenceEntity::getPlannedTeacherId, actualTeacherId))) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Teacher conflict detected for the adjusted class occurrence.");
        }
        if (actualClassroomId != null && hasOccurrenceConflict(source, targetStartAt, targetEndAt,
                wrapper -> wrapper.eq(LessonOccurrenceEntity::getPlannedClassroomId, actualClassroomId))) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Classroom conflict detected for the adjusted class occurrence.");
        }

        for (ClassEnrollmentEntity enrollment : enrollments) {
            if (hasStudentConflict(source, enrollment.getStudentId(), targetStartAt, targetEndAt)) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the adjusted class occurrence.");
            }
        }
    }

    private boolean hasStudentConflict(LessonOccurrenceEntity source,
                                       Long studentId,
                                       LocalDateTime targetStartAt,
                                       LocalDateTime targetEndAt) {
        List<Long> classIds = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream()
                .map(ClassEnrollmentEntity::getClassId)
                .filter(Objects::nonNull)
                .toList();
        return hasOccurrenceConflict(source, targetStartAt, targetEndAt, wrapper -> wrapper
                .and(nested -> nested
                        .eq(LessonOccurrenceEntity::getStudentId, studentId)
                        .or(!classIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getClassGroupId, classIds))));
    }

    private boolean hasOccurrenceConflict(LessonOccurrenceEntity source,
                                          LocalDateTime targetStartAt,
                                          LocalDateTime targetEndAt,
                                          java.util.function.Consumer<LambdaQueryWrapper<LessonOccurrenceEntity>> condition) {
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(LessonOccurrenceEntity::getId, source.getId())
                .lt(LessonOccurrenceEntity::getPlannedStartAt, targetEndAt)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, targetStartAt);
        condition.accept(wrapper);
        Long conflictCount = lessonOccurrenceMapper.selectCount(wrapper);
        return conflictCount != null && conflictCount > 0;
    }

    private List<ClassGroupScheduleSlotEntity> parseClassScheduleSlots(Object value) {
        if (!(value instanceof List<?> rawSlots)) {
            return Collections.emptyList();
        }
        List<ClassGroupScheduleSlotEntity> result = new ArrayList<>();
        for (Object rawSlot : rawSlots) {
            if (!(rawSlot instanceof Map<?, ?> map)) {
                continue;
            }
            Integer weekday = parseInteger(map.get("weekday"));
            LocalTime start = parseTime(map.get("startTime"));
            LocalTime end = parseTime(map.get("endTime"));
            if (weekday == null || start == null || end == null) {
                continue;
            }
            ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
            slot.setWeekday(weekday);
            slot.setStartTime(start);
            slot.setEndTime(end);
            result.add(slot);
        }
        return result;
    }

    private void validateClassScheduleSlots(List<ClassGroupScheduleSlotEntity> slots) {
        Set<String> seen = new HashSet<>();
        for (ClassGroupScheduleSlotEntity slot : slots) {
            if (slot.getWeekday() == null || slot.getWeekday() < 1 || slot.getWeekday() > 7) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE", "Weekday must be between 1 and 7.");
            }
            if (slot.getStartTime() == null || slot.getEndTime() == null || !slot.getEndTime().isAfter(slot.getStartTime())) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE", "Schedule end time must be after start time.");
            }
            String key = slot.getWeekday() + "|" + slot.getStartTime() + "|" + slot.getEndTime();
            if (!seen.add(key)) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE", "Duplicate class schedule slots are not allowed.");
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            ClassGroupScheduleSlotEntity left = slots.get(i);
            for (int j = i + 1; j < slots.size(); j++) {
                ClassGroupScheduleSlotEntity right = slots.get(j);
                if (Objects.equals(left.getWeekday(), right.getWeekday())
                        && left.getStartTime().isBefore(right.getEndTime())
                        && left.getEndTime().isAfter(right.getStartTime())) {
                    throw new BusinessException("INVALID_CLASS_SCHEDULE", "Class schedule slots cannot overlap on the same day.");
                }
            }
        }
    }

    private int removeFutureUnconfirmedOccurrences(Long classId) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, now));
        if (futureOccurrences.isEmpty()) {
            return 0;
        }
        Set<Long> occurrenceIds = futureOccurrences.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        Set<Long> recordedOccurrenceIds = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream()
                .map(LessonRecordEntity::getOccurrenceId)
                .collect(Collectors.toSet());

        List<Long> removableIds = futureOccurrences.stream()
                .map(LessonOccurrenceEntity::getId)
                .filter(id -> !recordedOccurrenceIds.contains(id))
                .toList();
        if (removableIds.isEmpty()) {
            return 0;
        }
        return lessonOccurrenceMapper.delete(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getId, removableIds));
    }

    private Set<LocalDate> futurePreservedClassOccurrenceDates(Long classId) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, now))
                .stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.toSet());
    }

    private int generateFutureClassOccurrences(ClassGroupEntity classGroup,
                                               List<ClassGroupScheduleSlotEntity> slots,
                                               Set<LocalDate> preservedDates) {
        LocalDate startDate = AppTime.nowLocalDateTime().toLocalDate();
        LocalDate endDate = resolveClassScheduleHorizon(classGroup.getId(), startDate);
        int count = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (preservedDates.contains(date)) {
                continue;
            }
            int weekday = date.getDayOfWeek().getValue();
            for (ClassGroupScheduleSlotEntity slot : slots) {
                if (!Objects.equals(slot.getWeekday(), weekday)) {
                    continue;
                }
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(generateClassLessonNo());
                occurrence.setClassGroupId(classGroup.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(classGroup.getTeacherId());
                occurrence.setPlannedClassroomId(classGroup.getClassroomId());
                occurrence.setPlannedStartAt(LocalDateTime.of(date, slot.getStartTime()));
                occurrence.setPlannedEndAt(LocalDateTime.of(date, slot.getEndTime()));
                occurrence.setPlannedUnits((int) (ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime()) / 30));
                occurrence.setPlanningStatus("SCHEDULED");
                ensureNoGeneratedClassOccurrenceConflicts(occurrence);
                occurrence.setConflictChecked(1);
                lessonOccurrenceMapper.insert(occurrence);
                count++;
            }
        }
        return count;
    }

    private void ensureNoGeneratedClassOccurrenceConflicts(LessonOccurrenceEntity occurrence) {
        LocalDateTime startAt = occurrence.getPlannedStartAt();
        LocalDateTime endAt = occurrence.getPlannedEndAt();
        if (occurrence.getPlannedTeacherId() != null && hasClassScheduleConflict(startAt, endAt,
                wrapper -> wrapper.eq(LessonOccurrenceEntity::getPlannedTeacherId, occurrence.getPlannedTeacherId()))) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Teacher conflict detected for the new class schedule.");
        }
        if (occurrence.getPlannedClassroomId() != null && hasClassScheduleConflict(startAt, endAt,
                wrapper -> wrapper.eq(LessonOccurrenceEntity::getPlannedClassroomId, occurrence.getPlannedClassroomId()))) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Classroom conflict detected for the new class schedule.");
        }
        for (Long studentId : activeStudentIdsByClass(occurrence.getClassGroupId())) {
            if (hasStudentConflictForClassSchedule(studentId, startAt, endAt)) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the new class schedule.");
            }
        }
    }

    private boolean hasStudentConflictForClassSchedule(Long studentId, LocalDateTime startAt, LocalDateTime endAt) {
        List<Long> classIds = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream()
                .map(ClassEnrollmentEntity::getClassId)
                .filter(Objects::nonNull)
                .toList();
        return hasClassScheduleConflict(startAt, endAt, wrapper -> wrapper
                .and(nested -> nested
                        .eq(LessonOccurrenceEntity::getStudentId, studentId)
                        .or(!classIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getClassGroupId, classIds))));
    }

    private boolean hasClassScheduleConflict(LocalDateTime startAt,
                                             LocalDateTime endAt,
                                             java.util.function.Consumer<LambdaQueryWrapper<LessonOccurrenceEntity>> condition) {
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, endAt)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, startAt);
        condition.accept(wrapper);
        Long conflictCount = lessonOccurrenceMapper.selectCount(wrapper);
        return conflictCount != null && conflictCount > 0;
    }

    private List<Long> activeStudentIdsByClass(Long classId) {
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream()
                .map(ClassEnrollmentEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void lockClassScheduleResources(Long classId, Long teacherId, Long classroomId) {
        activeStudentIdsByClass(classId).stream()
                .sorted()
                .forEach(studentMapper::selectByIdForUpdate);
        if (teacherId != null) {
            teacherMapper.selectByIdForUpdate(teacherId);
        }
        if (classroomId != null) {
            classroomMapper.selectByIdForUpdate(classroomId);
        }
    }

    private LocalDate resolveClassScheduleHorizon(Long classId, LocalDate fallbackStart) {
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream()
                .map(ClassEnrollmentEntity::getExpiryDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(fallbackStart.plusDays(60));
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long parseLongFromBody(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalTime parseTime(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return LocalTime.parse(text.length() > 5 ? text.substring(0, 5) : text);
        }
        return null;
    }

    private void validateTeacher(Long teacherId) {
        if (teacherId == null) {
            return;
        }
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || !"ACTIVE".equals(teacher.getStatus())) {
            throw new BusinessException("TEACHER_NOT_AVAILABLE", "The selected teacher does not exist or is not active.");
        }
    }

    private void validateClassroom(Long classroomId) {
        if (classroomId == null) {
            return;
        }
        ClassroomEntity classroom = classroomMapper.selectById(classroomId);
        if (classroom == null || !"ACTIVE".equals(classroom.getStatus())) {
            throw new BusinessException("CLASSROOM_NOT_AVAILABLE", "The selected classroom does not exist or is not active.");
        }
    }

    private String buildScheduleDesc(List<ClassGroupScheduleSlotEntity> slots) {
        return slots.stream()
                .sorted(Comparator.comparing(ClassGroupScheduleSlotEntity::getWeekday)
                        .thenComparing(ClassGroupScheduleSlotEntity::getStartTime))
                .map(slot -> weekdayShortLabel(slot.getWeekday()) + " "
                        + slot.getStartTime().toString().substring(0, 5)
                        + "-"
                        + slot.getEndTime().toString().substring(0, 5))
                .collect(Collectors.joining("; "));
    }

    private String weekdayShortLabel(Integer weekday) {
        return switch (weekday == null ? 0 : weekday) {
            case 1 -> "Mon";
            case 2 -> "Tue";
            case 3 -> "Wed";
            case 4 -> "Thu";
            case 5 -> "Fri";
            case 6 -> "Sat";
            case 7 -> "Sun";
            default -> "?";
        };
    }

    private String generateClassLessonNo() {
        return "CLS" + AppTime.nowLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000));
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
        item.put("scheduleSlots", toScheduleSlotList(clz.getId(), clz.getScheduleDesc(), clz.getProductId()));
        return item;
    }

    /**
     * 解析 scheduleDesc 为前端可用的 slot 列表。
     * 优先从产品排课时段表加载（更准确），降级到解析 scheduleDesc 文本。
     */
    private List<Map<String, Object>> toScheduleSlotList(Long classGroupId, String scheduleDesc, Long productId) {
        if (classGroupId != null) {
            List<ClassGroupScheduleSlotEntity> classSlots = classGroupScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                            .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroupId)
                            .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
            if (!classSlots.isEmpty()) {
                return classSlots.stream()
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
        // Fallback to product slots for legacy data.
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
