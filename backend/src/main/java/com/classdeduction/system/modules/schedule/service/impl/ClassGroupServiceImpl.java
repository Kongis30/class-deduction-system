package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.ClassGroupService;
import com.classdeduction.system.modules.schedule.service.ClassHolidayService;
import com.classdeduction.system.modules.schedule.util.ClassScheduleDates;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.schedule.util.ScheduleDescParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter CONFLICT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CONFLICT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseProductMapper courseProductMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final ClassHolidayService classHolidayService;

    @Override
    public List<Map<String, Object>> listClasses(CurrentUserResponse currentUser) {
        return listClasses(currentUser, false);
    }

    @Override
    public List<Map<String, Object>> listClasses(CurrentUserResponse currentUser, boolean includeInactiveProducts) {
        // 非运营用途（Product 配置）才包含停用 Product；教师账号始终只能看到在用课程。
        boolean canViewInactiveProducts = includeInactiveProducts && !isTeacherUser(currentUser);
        LambdaQueryWrapper<ClassGroupEntity> query = new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getStatus, "ACTIVE")
                .isNotNull(ClassGroupEntity::getProductId)
                .orderByAsc(ClassGroupEntity::getClassName);
        if (isTeacherUser(currentUser)) {
            Long teacherId = requireTeacherId(currentUser);
            List<Long> slotClassIds = classIdsBySlotTeacher(teacherId);
            query.and(wrapper -> wrapper
                    .eq(ClassGroupEntity::getTeacherId, teacherId)
                    .or(!slotClassIds.isEmpty(), nested -> nested.in(ClassGroupEntity::getId, slotClassIds)));
        }
        List<ClassGroupEntity> classes = classGroupMapper.selectList(query);
        if (classes.isEmpty()) {
            return Collections.emptyList();
        }

        // 班课列表是当前可运营班级，不应展示已删除、停用或已失去产品关联的遗留 class_group。
        Map<Long, CourseProductEntity> productMap = batchLoadCourseProducts(classes);
        classes = classes.stream()
                .filter(classGroup -> {
                    CourseProductEntity product = productMap.get(classGroup.getProductId());
                    return product != null && (canViewInactiveProducts || "ACTIVE".equals(product.getStatus()));
                })
                .toList();
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
            item.put("gradeBand", clz.getGradeBand());
        item.put("actualClass", StringUtils.hasText(clz.getActualClass()) ? clz.getActualClass() : clz.getGradeBand());
        item.put("startDate", clz.getStartDate());
        item.put("endDate", clz.getEndDate());
            item.put("status", clz.getStatus());
            item.put("scheduleSlots", toScheduleSlotList(clz.getId(), clz.getScheduleDesc()));
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
        detail.put("startDate", clz.getStartDate());
        detail.put("endDate", clz.getEndDate());
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
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }
        enrollments = enrollments.stream()
                .filter(enrollment -> "ACTIVE".equalsIgnoreCase(enrollment.getStatus()))
                .toList();
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
                generateFutureClassOccurrences(classGroup, slots, Collections.emptyList(),
                        Collections.emptyList(), AppTime.nowLocalDateTime());
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
            if ("ONLINE".equals(occ.getDeliveryMode())) {
                classroomName = "Online";
            }

            Map<String, Object> item = new HashMap<>();
            item.put("id", occ.getId());
            item.put("lessonNo", occ.getLessonNo());
            item.put("occurrenceType", occ.getOccurrenceType());
            item.put("plannedTeacherId", occ.getPlannedTeacherId());
            item.put("plannedTeacherName", teacherName);
            item.put("plannedClassroomId", occ.getPlannedClassroomId());
            item.put("plannedClassroomName", classroomName);
            item.put("deliveryMode", occ.getDeliveryMode());
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
                        .isNull(LessonOccurrenceEntity::getCourseHolidayId)
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
        if (occ.getCourseHolidayId() != null || "CANCELLED".equals(occ.getPlanningStatus())) {
            throw new BusinessException("CLASS_LESSON_CANCELLED", "This class lesson is cancelled for a school holiday.");
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
            return attendanceMatches(enrollment.getAttendanceOption(), occurrence);
        }
        LocalDate lessonDate = occurrence.getPlannedStartAt().toLocalDate();
        if (!enrollmentIncludesOccurrence(enrollment, occurrence)) {
            return false;
        }
        if (enrollment.getEnrollmentDate() != null && lessonDate.isBefore(enrollment.getEnrollmentDate())) {
            return false;
        }
        if (enrollment.getExpiryDate() != null && lessonDate.isAfter(enrollment.getExpiryDate())) {
            return false;
        }
        if (!attendanceMatches(enrollment.getAttendanceOption(), occurrence)) {
            return false;
        }
        if (!"PER_SESSION".equals(enrollment.getBillingType()) || enrollment.getPurchasedQuantity() == null) {
            return true;
        }
        long usedSessions = countUsedSessions(enrollment, confirmedLessonDatesByStudent);
        return usedSessions < enrollment.getPurchasedQuantity()
                || hasUsedSessionOnDate(enrollment, confirmedLessonDatesByStudent, lessonDate);
    }

    private boolean enrollmentIncludesOccurrence(ClassEnrollmentEntity enrollment, LessonOccurrenceEntity occurrence) {
        if (enrollment == null || occurrence == null || occurrence.getPlannedStartAt() == null) {
            return false;
        }
        LocalDateTime enrolledAt = enrollment.getCreatedAt();
        return enrolledAt == null || !occurrence.getPlannedStartAt().isBefore(enrolledAt);
    }

    private boolean attendanceMatches(String enrollmentAttendance, LessonOccurrenceEntity occurrence) {
        String attendance = normalizeAttendanceOption(enrollmentAttendance);
        String segment = occurrenceScheduleSegment(occurrence);
        if (!StringUtils.hasText(segment)) {
            return true;
        }
        Set<String> segments = Arrays.stream(segment.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (segments.isEmpty() || segments.contains("ALL")) {
            return true;
        }
        if ("FULL_DAY".equals(attendance)) {
            return segments.contains("FULL_DAY")
                    || segments.contains("MORNING")
                    || segments.contains("AFTERNOON");
        }
        return segments.contains(attendance);
    }

    private String occurrenceScheduleSegment(LessonOccurrenceEntity occurrence) {
        if (occurrence == null || occurrence.getScheduleSlotId() == null) {
            return "ALL";
        }
        ClassGroupScheduleSlotEntity slot = classGroupScheduleSlotMapper.selectById(occurrence.getScheduleSlotId());
        return slot == null || !StringUtils.hasText(slot.getAttendanceOptions())
                ? "ALL"
                : slot.getAttendanceOptions().trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAttendanceOption(String value) {
        if (!StringUtils.hasText(value)) {
            return "FULL_DAY";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MORNING", "AFTERNOON", "FULL_DAY" -> normalized;
            default -> "FULL_DAY";
        };
    }

    private long countUsedSessions(ClassEnrollmentEntity enrollment,
                                   Map<Long, List<LocalDate>> confirmedLessonDatesByStudent) {
        // 班课 SESSION 的业务口径是“上课日”：同一天有上午/下午多个时段，也只消耗 1 个 session。
        return confirmedLessonDatesByStudent
                .getOrDefault(enrollment.getStudentId(), Collections.emptyList())
                .stream()
                .filter(date -> enrollment.getEnrollmentDate() == null || !date.isBefore(enrollment.getEnrollmentDate()))
                .filter(date -> enrollment.getExpiryDate() == null || !date.isAfter(enrollment.getExpiryDate()))
                .distinct()
                .count();
    }

    private boolean hasUsedSessionOnDate(ClassEnrollmentEntity enrollment,
                                         Map<Long, List<LocalDate>> confirmedLessonDatesByStudent,
                                         LocalDate lessonDate) {
        return lessonDate != null && confirmedLessonDatesByStudent
                .getOrDefault(enrollment.getStudentId(), Collections.emptyList())
                .stream()
                .anyMatch(lessonDate::equals);
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
    @Transactional(readOnly = true)
    public void checkClassScheduleEditable(Long classId) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        ensureStartedClassLessonsConfirmed(classId, AppTime.nowLocalDateTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateClassSchedule(Long classId, Map<String, Object> body) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        // 保留旧客户端的班级默认值作为兼容回退；新页面按 slot 提交独立资源。
        Long defaultTeacherId = parseLongFromBody(body, "teacherId");
        Long defaultClassroomId = parseLongFromBody(body, "classroomId");
        LocalDate startDate = body != null && body.containsKey("startDate")
                ? parseDateParam(body, "startDate") : classGroup.getStartDate();
        LocalDate endDate = body != null && body.containsKey("endDate")
                ? parseDateParam(body, "endDate") : classGroup.getEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("INVALID_CLASS_DATE_RANGE", "Class end date cannot be earlier than the start date.");
        }
        List<ClassGroupScheduleSlotEntity> slots = parseClassScheduleSlots(
                body.get("slots"), defaultTeacherId, defaultClassroomId);
        if (slots.isEmpty()) {
            throw new BusinessException("CLASS_SCHEDULE_REQUIRED", "Add at least one weekly class time.");
        }
        validateClassScheduleSlots(slots);
        slots.stream()
                .map(ClassGroupScheduleSlotEntity::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::validateTeacher);
        slots.stream()
                .map(ClassGroupScheduleSlotEntity::getClassroomId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::validateClassroom);
        lockClassScheduleResources(classId, slots);

        LocalDateTime rebuildFrom = AppTime.nowLocalDateTime();
        // 默认阻止未确认的已开始课次被后续操作遗漏；仅用户在界面明确选择后续改课时允许跳过。
        boolean preserveStartedLessons = body != null && Boolean.TRUE.equals(body.get("preserveStartedLessons"));
        if (!preserveStartedLessons) {
            ensureStartedClassLessonsConfirmed(classId, rebuildFrom);
        }

        // 班级字段仅保留首个已配置资源供旧列表兼容，课次生成以 slot 字段为准。
        classGroup.setTeacherId(firstTeacherId(slots));
        classGroup.setClassroomId(firstClassroomId(slots));
        classGroup.setStartDate(startDate);
        classGroup.setEndDate(endDate);
        classGroup.setScheduleDesc(buildScheduleDesc(slots));
        classGroupMapper.updateById(classGroup);

        classGroupScheduleSlotMapper.delete(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId));
        for (ClassGroupScheduleSlotEntity slot : slots) {
            slot.setClassGroupId(classId);
            classGroupScheduleSlotMapper.insert(slot);
        }

        FutureClassOccurrences previousOccurrences = removeFutureUnconfirmedOccurrences(classId, rebuildFrom);
        List<LessonOccurrenceEntity> retainedOccurrences = retainedClassOccurrences(classId, rebuildFrom);
        int generatedCount = generateFutureClassOccurrences(classGroup, slots, retainedOccurrences,
                previousOccurrences.occurrences(), rebuildFrom);
        return Map.of("classId", classId, "cancelledFutureOccurrences", previousOccurrences.removedCount(),
                "generatedOccurrences", generatedCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createActualClass(Map<String, Object> body) {
        Long productId = parseLongFromBody(body, "productId");
        String gradeBand = requireTextFromBody(body, "gradeBand", "Grade is required.");
        String actualClass = requireTextFromBody(body, "actualClass", "Actual class is required.");
        CourseProductEntity product = productId == null ? null : courseProductMapper.selectById(productId);
        if (product == null || !"GROUP".equals(product.getTeachingMode()) || !"ACTIVE".equals(product.getStatus())) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Active group course product not found.");
        }
        ensureActualClassUnique(null, productId, gradeBand, actualClass);

        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setProductId(productId);
        classGroup.setGradeBand(gradeBand);
        classGroup.setActualClass(actualClass);
        classGroup.setClassName(resolveClassName(body, actualClass));
        classGroup.setListPrice(java.math.BigDecimal.ZERO);
        classGroup.setStatus("ACTIVE");
        inheritGradeBillingMetadata(classGroup);
        applyClassDateRange(classGroup, body);
        classGroupMapper.insert(classGroup);
        return toClassInfoMap(classGroup);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateClassInfo(Long classId, Map<String, Object> body) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        String actualClass = requireTextFromBody(body, "actualClass", "Actual class is required.");
        ensureActualClassUnique(classId, classGroup.getProductId(), classGroup.getGradeBand(), actualClass);
        classGroup.setActualClass(actualClass);
        classGroup.setClassName(resolveClassName(body, actualClass));
        applyClassDateRange(classGroup, body);
        classGroupMapper.updateById(classGroup);
        return toClassInfoMap(classGroup);
    }

    private void ensureActualClassUnique(Long currentClassId, Long productId, String gradeBand, String actualClass) {
        ClassGroupEntity duplicate = classGroupMapper.selectOne(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, productId)
                .eq(ClassGroupEntity::getGradeBand, gradeBand)
                .eq(ClassGroupEntity::getActualClass, actualClass)
                .eq(ClassGroupEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (duplicate != null && !Objects.equals(duplicate.getId(), currentClassId)) {
            throw new BusinessException("ACTUAL_CLASS_DUPLICATED",
                    "This actual class already exists under the selected grade.");
        }
    }

    private void inheritGradeBillingMetadata(ClassGroupEntity classGroup) {
        ClassGroupEntity sibling = classGroupMapper.selectOne(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, classGroup.getProductId())
                .eq(ClassGroupEntity::getGradeBand, classGroup.getGradeBand())
                .eq(ClassGroupEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (sibling == null) {
            return;
        }
        classGroup.setBillingType(sibling.getBillingType());
        classGroup.setStartDate(sibling.getStartDate());
        classGroup.setEndDate(sibling.getEndDate());
        classGroup.setTotalSessions(sibling.getTotalSessions());
        classGroup.setStartMonth(sibling.getStartMonth());
        classGroup.setEndMonth(sibling.getEndMonth());
    }

    private String resolveClassName(Map<String, Object> body, String actualClass) {
        String className = textFromBody(body, "className");
        return StringUtils.hasText(className) ? className : actualClass;
    }

    private String requireTextFromBody(Map<String, Object> body, String key, String message) {
        String value = textFromBody(body, key);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("INVALID_CLASS_INFO", message);
        }
        return value;
    }

    private String textFromBody(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> toClassInfoMap(ClassGroupEntity classGroup) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", classGroup.getId());
        item.put("className", classGroup.getClassName());
        item.put("productId", classGroup.getProductId());
        item.put("gradeBand", classGroup.getGradeBand());
        item.put("actualClass", StringUtils.hasText(classGroup.getActualClass())
                ? classGroup.getActualClass()
                : classGroup.getGradeBand());
        item.put("teacherId", classGroup.getTeacherId());
        item.put("classroomId", classGroup.getClassroomId());
        item.put("startDate", classGroup.getStartDate());
        item.put("endDate", classGroup.getEndDate());
        item.put("scheduleDesc", classGroup.getScheduleDesc());
        return item;
    }

    /** Actual class 的周期属于 class 本身，和共享的 registration option 分开保存。 */
    private void applyClassDateRange(ClassGroupEntity classGroup, Map<String, Object> body) {
        LocalDate startDate = body != null && body.containsKey("startDate")
                ? parseDateParam(body, "startDate") : classGroup.getStartDate();
        LocalDate endDate = body != null && body.containsKey("endDate")
                ? parseDateParam(body, "endDate") : classGroup.getEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("INVALID_CLASS_DATE_RANGE", "Class end date cannot be earlier than the start date.");
        }
        classGroup.setStartDate(startDate);
        classGroup.setEndDate(endDate);
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
            item.put("gradeBand", clz.getGradeBand());
            item.put("actualClass", StringUtils.hasText(clz.getActualClass()) ? clz.getActualClass() : clz.getGradeBand());
            item.put("teacherName", teacherName);
            item.put("classroomName", classroomName);
            item.put("scheduleSlots", toScheduleSlotList(clz.getId(), clz.getScheduleDesc()));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listByTeacher(Long teacherId) {
        List<Long> slotClassIds = classIdsBySlotTeacher(teacherId);
        List<ClassGroupEntity> classes = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .and(wrapper -> wrapper
                                .eq(ClassGroupEntity::getTeacherId, teacherId)
                                .or(!slotClassIds.isEmpty(), nested -> nested.in(ClassGroupEntity::getId, slotClassIds)))
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

        // 3. 结束该班级下的报名关系，但不删除学生档案、订单或历史消课记录。
        //    续费/学生列表只看 ACTIVE + 未删除，因此已退出学生不会继续出现在该班级中。
        ClassEnrollmentEntity enrollUpdate = new ClassEnrollmentEntity();
        enrollUpdate.setStatus("CANCELLED");
        enrollUpdate.setIsDeleted(1);
        classEnrollmentMapper.update(enrollUpdate, new LambdaUpdateWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getClassId, classId)
                .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                .eq(ClassEnrollmentEntity::getIsDeleted, 0));

        // 4. 软删除周排课配置，避免班级删除后仍被排课编辑器读到。
        classGroupScheduleSlotMapper.delete(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId)
                .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));

        // 5. 软删除班课本身；订单中的 class_id 保留，历史数据仍可追溯。
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
        if (!isTeacherUser(currentUser)) {
            return;
        }
        Long teacherId = requireTeacherId(currentUser);
        boolean assignedToClass = Objects.equals(classGroup.getTeacherId(), teacherId)
                || classGroupScheduleSlotMapper.selectCount(
                new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroup.getId())
                        .eq(ClassGroupScheduleSlotEntity::getTeacherId, teacherId)
                        .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0)) > 0;
        if (!assignedToClass) {
            throw new BusinessException("FORBIDDEN", "Teachers can only view their own classes.");
        }
    }

    private List<Long> classIdsBySlotTeacher(Long teacherId) {
        return classGroupScheduleSlotMapper.selectList(
                        new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                                .eq(ClassGroupScheduleSlotEntity::getTeacherId, teacherId)
                                .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0))
                .stream()
                .map(ClassGroupScheduleSlotEntity::getClassGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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

    /**
     * 解析 Actual class 的教学周期；空字符串表示清空日期，非法日期直接阻止保存。
     */
    private LocalDate parseDateParam(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text);
            } catch (RuntimeException exception) {
                throw new BusinessException("INVALID_CLASS_DATE", "Class dates must use YYYY-MM-DD format.");
            }
        }
        throw new BusinessException("INVALID_CLASS_DATE", "Class dates must use YYYY-MM-DD format.");
    }

    private void ensureNoClassConfirmConflicts(LessonOccurrenceEntity source,
                                               List<ClassEnrollmentEntity> enrollments,
                                               Long actualTeacherId,
                                               Long actualClassroomId,
                                               LocalDateTime actualStartAt,
                                               LocalDateTime actualEndAt) {
        LocalDateTime targetStartAt = actualStartAt != null ? actualStartAt : source.getPlannedStartAt();
        LocalDateTime targetEndAt = actualEndAt != null ? actualEndAt : source.getPlannedEndAt();
        List<ClassTimeRange> addedTime = newlyOccupiedClassTime(source, targetStartAt, targetEndAt);
        List<ClassTimeRange> teacherRanges = Objects.equals(actualTeacherId, source.getPlannedTeacherId())
                ? addedTime : List.of(new ClassTimeRange(targetStartAt, targetEndAt));
        List<ClassTimeRange> classroomRanges = Objects.equals(actualClassroomId, source.getPlannedClassroomId())
                ? addedTime : List.of(new ClassTimeRange(targetStartAt, targetEndAt));
        if (actualTeacherId != null) {
            for (ClassTimeRange range : teacherRanges) {
                if (hasOccurrenceConflict(source, range.startAt(), range.endAt(),
                        wrapper -> wrapper.eq(LessonOccurrenceEntity::getPlannedTeacherId, actualTeacherId))) {
                    throw new BusinessException("SCHEDULE_CONFLICT", "Teacher conflict detected for the adjusted class occurrence.");
                }
            }
        }
        if (actualClassroomId != null) {
            for (ClassTimeRange range : classroomRanges) {
                if (hasOccurrenceConflict(source, range.startAt(), range.endAt(),
                        wrapper -> wrapper.eq(LessonOccurrenceEntity::getPlannedClassroomId, actualClassroomId))) {
                    throw new BusinessException("SCHEDULE_CONFLICT", "Classroom conflict detected for the adjusted class occurrence.");
                }
            }
        }
        for (ClassEnrollmentEntity enrollment : enrollments) {
            for (ClassTimeRange range : addedTime) {
                if (hasStudentConflict(source, enrollment.getStudentId(), range.startAt(), range.endAt())) {
                    throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the adjusted class occurrence.");
                }
            }
        }
    }

    /** 原计划已经占用的时段不重复拦截；只检查实际延长或挪动后新占用的部分。 */
    private List<ClassTimeRange> newlyOccupiedClassTime(LessonOccurrenceEntity source,
                                                         LocalDateTime targetStartAt, LocalDateTime targetEndAt) {
        LocalDateTime originalStartAt = source.getPlannedStartAt();
        LocalDateTime originalEndAt = source.getPlannedEndAt();
        List<ClassTimeRange> ranges = new ArrayList<>(2);
        if (targetStartAt.isBefore(originalStartAt)) {
            ranges.add(new ClassTimeRange(targetStartAt,
                    targetEndAt.isBefore(originalStartAt) ? targetEndAt : originalStartAt));
        }
        if (targetEndAt.isAfter(originalEndAt)) {
            ranges.add(new ClassTimeRange(targetStartAt.isAfter(originalEndAt) ? targetStartAt : originalEndAt,
                    targetEndAt));
        }
        return ranges;
    }

    private record ClassTimeRange(LocalDateTime startAt, LocalDateTime endAt) {
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

    private List<ClassGroupScheduleSlotEntity> parseClassScheduleSlots(
            Object value, Long defaultTeacherId, Long defaultClassroomId) {
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
            Long teacherId = parseLongValue(map.get("teacherId"));
            Long classroomId = parseLongValue(map.get("classroomId"));
            String deliveryMode = map.get("deliveryMode") == null ? null : String.valueOf(map.get("deliveryMode"));
            if ("ONLINE".equalsIgnoreCase(deliveryMode)) {
                if (classroomId != null) {
                    throw new BusinessException("INVALID_SLOT_CLASSROOM", "Online slots cannot have a classroom.");
                }
                slot.setDeliveryMode("ONLINE");
            } else {
                if (deliveryMode != null && !"IN_PERSON".equalsIgnoreCase(deliveryMode)) {
                    throw new BusinessException("INVALID_DELIVERY_MODE", "Choose a classroom or Online for every slot.");
                }
                Long resolvedClassroomId = classroomId != null ? classroomId : defaultClassroomId;
                if (resolvedClassroomId == null) {
                    throw new BusinessException("SLOT_CLASSROOM_REQUIRED", "Choose a classroom or Online for every slot.");
                }
                slot.setDeliveryMode("IN_PERSON");
                slot.setClassroomId(resolvedClassroomId);
            }
            slot.setTeacherId(teacherId != null ? teacherId : defaultTeacherId);
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

    /** Edit Schedule 的保存门槛：已经开始且有学生参加的课次，必须完成管理员最终消课。 */
    private void ensureStartedClassLessonsConfirmed(Long classId, LocalDateTime editingAt) {
        List<LessonOccurrenceEntity> startedLessons = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "MERGED")
                        .le(LessonOccurrenceEntity::getPlannedStartAt, editingAt)
                        .orderByDesc(LessonOccurrenceEntity::getPlannedStartAt));
        if (startedLessons.isEmpty()) {
            return;
        }
        List<ClassEnrollmentEntity> activeEnrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        if (activeEnrollments.isEmpty()) {
            return;
        }
        Map<Long, List<LocalDate>> confirmedDates = activeEnrollments.stream()
                .anyMatch(enrollment -> "PER_SESSION".equals(enrollment.getBillingType()))
                ? loadConfirmedLessonDatesByStudent(classId) : Collections.emptyMap();
        for (LessonOccurrenceEntity lesson : startedLessons) {
            if (activeEnrollments.stream().anyMatch(enrollment ->
                    isEnrollmentEligibleForOccurrence(enrollment, lesson, confirmedDates))) {
                throw new BusinessException("CLASS_LESSONS_NOT_CONFIRMED",
                        "Confirm all started class lessons before editing the schedule. An unconfirmed lesson starts on "
                                + lesson.getPlannedStartAt().format(CONFLICT_DATE_TIME)
                                + " ET. Lessons already in progress also require confirmation.");
            }
        }
    }

    /** 删除可重建课次，同时保留修改前的时段快照，用于区分既有冲突与本次新增冲突。 */
    private FutureClassOccurrences removeFutureUnconfirmedOccurrences(Long classId, LocalDateTime rebuildFrom) {
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, rebuildFrom));
        if (futureOccurrences.isEmpty()) {
            return new FutureClassOccurrences(0, List.of());
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
            return new FutureClassOccurrences(0, futureOccurrences);
        }
        int removedCount = lessonOccurrenceMapper.delete(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getId, removableIds));
        return new FutureClassOccurrences(removedCount, futureOccurrences);
    }

    private record FutureClassOccurrences(int removedCount, List<LessonOccurrenceEntity> occurrences) {
    }

    /** 重建时保留已开始或已有消课记录的课次，供新时段逐节比对。 */
    private List<LessonOccurrenceEntity> retainedClassOccurrences(Long classId, LocalDateTime rebuildFrom) {
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .gt(LessonOccurrenceEntity::getPlannedEndAt, rebuildFrom));
    }

    private int generateFutureClassOccurrences(ClassGroupEntity classGroup,
                                               List<ClassGroupScheduleSlotEntity> slots,
                                               List<LessonOccurrenceEntity> retainedOccurrences,
                                               List<LessonOccurrenceEntity> previousOccurrences,
                                               LocalDateTime rebuildFrom) {
        LocalDate today = rebuildFrom.toLocalDate();
        Map<LocalDate, List<LessonOccurrenceEntity>> retainedByDate = retainedOccurrences.stream()
                .filter(occurrence -> occurrence.getPlannedStartAt() != null && occurrence.getPlannedEndAt() != null)
                .collect(Collectors.groupingBy(occurrence -> occurrence.getPlannedStartAt().toLocalDate()));
        Map<LocalDate, List<LessonOccurrenceEntity>> previousByDate = previousOccurrences.stream()
                .filter(occurrence -> occurrence.getPlannedStartAt() != null && occurrence.getPlannedEndAt() != null)
                .collect(Collectors.groupingBy(occurrence -> occurrence.getPlannedStartAt().toLocalDate()));
        LocalDate configuredStart = classGroup.getStartDate();
        LocalDate configuredEnd = classGroup.getEndDate();
        LocalDate startDate = configuredStart == null || configuredStart.isBefore(today) ? today : configuredStart;
        LocalDate endDate = configuredEnd != null
                ? configuredEnd
                : resolveClassScheduleHorizon(classGroup.getId(), startDate);
        List<ClassHolidayVO> holidays = classHolidayService.listHolidays(classGroup.getProductId());
        if (configuredStart != null) {
            int requiredSessions = isSessionBilling(classGroup.getBillingType())
                    ? (classGroup.getTotalSessions() == null ? 0 : classGroup.getTotalSessions())
                    : ClassScheduleDates.countScheduledDays(configuredStart, endDate, slots);
            LocalDate requiredEnd = ClassScheduleDates.nthScheduledDay(configuredStart, requiredSessions,
                    slots, date -> classHolidayService.isHoliday(date, holidays));
            if (requiredEnd != null && requiredEnd.isAfter(endDate)) {
                endDate = requiredEnd;
                classGroup.setEndDate(requiredEnd);
                classGroupMapper.updateById(classGroup);
            }
        }
        if (endDate.isBefore(startDate)) {
            return 0;
        }
        // 旧数据的 slot 资源为空时继续使用班级默认值；新数据的授课方式始终逐时段生效。
        boolean hasSlotTeacher = slots.stream().anyMatch(item -> item.getTeacherId() != null);
        boolean hasSlotClassroom = slots.stream().anyMatch(item -> item.getClassroomId() != null
                || "ONLINE".equals(item.getDeliveryMode()));
        int count = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (classHolidayService.isHoliday(date, holidays)) {
                continue;
            }
            int weekday = date.getDayOfWeek().getValue();
            for (ClassGroupScheduleSlotEntity slot : slots) {
                if (!Objects.equals(slot.getWeekday(), weekday)) {
                    continue;
                }
                LocalDateTime plannedStartAt = LocalDateTime.of(date, slot.getStartTime());
                // 已开始的课次由原记录保留；重建当天排课时不能再次生成并与原课次冲突。
                if (plannedStartAt.isBefore(rebuildFrom)) {
                    continue;
                }
                LocalDateTime plannedEndAt = LocalDateTime.of(date, slot.getEndTime());
                List<LessonOccurrenceEntity> retainedOnDate = retainedByDate.getOrDefault(date, Collections.emptyList());
                if (retainedOnDate.stream().anyMatch(existing ->
                        plannedStartAt.equals(existing.getPlannedStartAt())
                                && plannedEndAt.equals(existing.getPlannedEndAt()))) {
                    continue;
                }
                List<LessonOccurrenceEntity> overlappingRetained = retainedOnDate.stream()
                        .filter(existing -> plannedStartAt.isBefore(existing.getPlannedEndAt())
                                && plannedEndAt.isAfter(existing.getPlannedStartAt()))
                        .toList();
                if (!overlappingRetained.isEmpty()) {
                    // 保留当天已开始的旧课；与它重叠的新时段从后续日期生效。
                    if (overlappingRetained.stream().allMatch(existing ->
                            existing.getPlannedStartAt().isBefore(rebuildFrom))) {
                        continue;
                    }
                    throw new BusinessException("SCHEDULE_CONFLICT", "A retained class lesson overlaps the new class schedule.");
                }
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(BusinessNoGenerator.generateClassLessonOccurrenceNo());
                occurrence.setClassGroupId(classGroup.getId());
                occurrence.setScheduleSlotId(slot.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(hasSlotTeacher
                        ? slot.getTeacherId() : classGroup.getTeacherId());
                Long classroomId = "ONLINE".equals(slot.getDeliveryMode()) ? null
                        : hasSlotClassroom ? slot.getClassroomId() : classGroup.getClassroomId();
                occurrence.setDeliveryMode(slot.getDeliveryMode() == null && classroomId != null
                        ? "IN_PERSON" : slot.getDeliveryMode());
                occurrence.setPlannedClassroomId(classroomId);
                occurrence.setPlannedStartAt(plannedStartAt);
                occurrence.setPlannedEndAt(plannedEndAt);
                occurrence.setPlannedUnits((int) (ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime()) / 30));
                occurrence.setPlanningStatus("SCHEDULED");
                ensureNoGeneratedClassOccurrenceConflicts(occurrence, classGroup,
                        previousByDate.getOrDefault(date, Collections.emptyList()));
                occurrence.setConflictChecked(1);
                lessonOccurrenceMapper.insert(occurrence);
                count++;
            }
        }
        return count;
    }

    private boolean isSessionBilling(String billingType) {
        return "SESSION".equalsIgnoreCase(billingType) || "PER_SESSION".equalsIgnoreCase(billingType);
    }

    private void ensureNoGeneratedClassOccurrenceConflicts(LessonOccurrenceEntity occurrence,
                                                           ClassGroupEntity classGroup,
                                                           List<LessonOccurrenceEntity> previousOccurrences) {
        LocalDateTime startAt = occurrence.getPlannedStartAt();
        LocalDateTime endAt = occurrence.getPlannedEndAt();
        if (occurrence.getPlannedTeacherId() != null) {
            LessonOccurrenceEntity conflict = findTeacherConflict(occurrence.getPlannedTeacherId(), startAt, endAt);
            if (conflict != null) {
                throw new BusinessException("SCHEDULE_CONFLICT",
                        describeTeacherConflict(occurrence.getPlannedTeacherId(), classGroup, conflict, startAt, endAt));
            }
        }
        if (occurrence.getPlannedClassroomId() != null && hasClassScheduleConflict(startAt, endAt,
                wrapper -> wrapper.eq(LessonOccurrenceEntity::getPlannedClassroomId, occurrence.getPlannedClassroomId()))) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Classroom conflict detected for the new class schedule.");
        }
        for (Long studentId : activeStudentIdsByClassOnDate(occurrence.getClassGroupId(), startAt.toLocalDate())) {
            for (LessonOccurrenceEntity conflict : findStudentConflictsForClassSchedule(
                    studentId, occurrence.getClassGroupId(), startAt, endAt)) {
                if (!wasStudentOverlapAlreadyScheduled(previousOccurrences, conflict, startAt, endAt)) {
                    StudentEntity student = studentMapper.selectById(studentId);
                    String studentName = student == null ? "#" + studentId : student.getStudentName();
                    throw new BusinessException("SCHEDULE_CONFLICT", "Student " + studentName
                            + " already has " + describeConflictingCourse(conflict) + " ("
                            + conflict.getPlannedStartAt().format(CONFLICT_DATE_TIME) + "–"
                            + conflict.getPlannedEndAt().format(CONFLICT_TIME) + " ET). This overlaps the new class schedule.");
                }
            }
        }
    }

    /** 找出首节重叠课次，供班课保存失败时显示老师和现有课程。 */
    private LessonOccurrenceEntity findTeacherConflict(Long teacherId, LocalDateTime startAt, LocalDateTime endAt) {
        List<LessonOccurrenceEntity> conflicts = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getPlannedTeacherId, teacherId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .lt(LessonOccurrenceEntity::getPlannedStartAt, endAt)
                        .gt(LessonOccurrenceEntity::getPlannedEndAt, startAt)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt)
                        .last("LIMIT 1"));
        return conflicts.isEmpty() ? null : conflicts.get(0);
    }

    /** 将新时段与已占用课次一起写入错误提示，便于管理员直接定位冲突。 */
    private String describeTeacherConflict(Long teacherId, ClassGroupEntity classGroup,
                                           LessonOccurrenceEntity conflict,
                                           LocalDateTime startAt, LocalDateTime endAt) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        String teacherName = teacher != null && StringUtils.hasText(teacher.getTeacherName())
                ? teacher.getTeacherName() : "#" + teacherId;
        String newClass = StringUtils.hasText(classGroup.getClassName())
                ? classGroup.getClassName() : "the new class";
        String existingTime = conflict.getPlannedStartAt().format(CONFLICT_DATE_TIME)
                + "–" + conflict.getPlannedEndAt().format(CONFLICT_TIME);
        String newTime = startAt.format(CONFLICT_DATE_TIME) + "–" + endAt.format(CONFLICT_TIME);
        return "Teacher " + teacherName + " is already assigned to " + describeConflictingCourse(conflict)
                + " (" + existingTime + " ET); this overlaps " + newClass + " (" + newTime + " ET).";
    }

    /** 冲突课次既可能属于班课，也可能属于一对一课包。 */
    private String describeConflictingCourse(LessonOccurrenceEntity conflict) {
        if (conflict.getClassGroupId() != null) {
            ClassGroupEntity existingClass = classGroupMapper.selectById(conflict.getClassGroupId());
            if (existingClass == null) {
                return "group class #" + conflict.getClassGroupId();
            }
            CourseProductEntity product = existingClass.getProductId() == null
                    ? null : courseProductMapper.selectById(existingClass.getProductId());
            List<String> courseParts = new ArrayList<>();
            if (product != null && StringUtils.hasText(product.getProductName())) {
                courseParts.add(product.getProductName());
            }
            if (StringUtils.hasText(existingClass.getGradeBand())) {
                courseParts.add(existingClass.getGradeBand());
            }
            String className = StringUtils.hasText(existingClass.getActualClass())
                    ? existingClass.getActualClass() : existingClass.getClassName();
            if (StringUtils.hasText(className)) {
                courseParts.add(className);
            }
            return courseParts.isEmpty() ? "group class #" + existingClass.getId()
                    : String.join(" / ", courseParts);
        }
        CoursePackageEntity coursePackage = conflict.getPackageId() == null
                ? null : coursePackageMapper.selectById(conflict.getPackageId());
        CourseProductEntity product = coursePackage == null || coursePackage.getCourseProductId() == null
                ? null : courseProductMapper.selectById(coursePackage.getCourseProductId());
        Long studentId = conflict.getStudentId() != null ? conflict.getStudentId()
                : coursePackage == null ? null : coursePackage.getStudentId();
        StudentEntity student = studentId == null ? null : studentMapper.selectById(studentId);
        List<String> courseParts = new ArrayList<>();
        if (product != null && StringUtils.hasText(product.getProductName())) {
            courseParts.add(product.getProductName());
        }
        if (coursePackage != null && StringUtils.hasText(coursePackage.getPackageName())
                && !courseParts.contains(coursePackage.getPackageName())) {
            courseParts.add(coursePackage.getPackageName());
        }
        String courseName = courseParts.isEmpty() ? "1-on-1 lesson" : String.join(" / ", courseParts);
        return student != null && StringUtils.hasText(student.getStudentName())
                ? courseName + " (" + student.getStudentName() + ")" : courseName;
    }

    /** 只查当前班以外的课次；当前班未重建的课次由 retainedOccurrences 单独检查。 */
    private List<LessonOccurrenceEntity> findStudentConflictsForClassSchedule(Long studentId, Long classId,
                                                                               LocalDateTime startAt, LocalDateTime endAt) {
        LocalDate lessonDate = startAt.toLocalDate();
        List<Long> classIds = activeClassIdsByStudentOnDate(studentId, lessonDate).stream()
                .filter(activeClassId -> !activeClassId.equals(classId))
                .toList();
        List<Long> privateGroupIds = privateLessonGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                                .eq(PrivateLessonGroupMemberEntity::getStudentId, studentId)
                                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                .stream()
                .map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, endAt)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, startAt)
                .and(otherClass -> otherClass.isNull(LessonOccurrenceEntity::getClassGroupId)
                        .or().ne(LessonOccurrenceEntity::getClassGroupId, classId))
                .and(nested -> nested
                        .eq(LessonOccurrenceEntity::getStudentId, studentId)
                        .or(!privateGroupIds.isEmpty(), group -> group.in(
                                LessonOccurrenceEntity::getPrivateLessonGroupId, privateGroupIds))
                        .or(!classIds.isEmpty(), other -> other.in(LessonOccurrenceEntity::getClassGroupId, classIds))));
    }

    /** 原时段已经覆盖的重叠属于报名时接受的冲突；仅新增的重叠阻止修改排课。 */
    private boolean wasStudentOverlapAlreadyScheduled(List<LessonOccurrenceEntity> previousOccurrences,
                                                       LessonOccurrenceEntity conflict,
                                                       LocalDateTime startAt, LocalDateTime endAt) {
        LocalDateTime overlapStart = startAt.isAfter(conflict.getPlannedStartAt())
                ? startAt : conflict.getPlannedStartAt();
        LocalDateTime overlapEnd = endAt.isBefore(conflict.getPlannedEndAt())
                ? endAt : conflict.getPlannedEndAt();
        LocalDateTime coveredUntil = overlapStart;
        for (LessonOccurrenceEntity previous : previousOccurrences.stream()
                .filter(item -> item.getPlannedStartAt() != null && item.getPlannedEndAt() != null)
                .filter(item -> item.getPlannedStartAt().isBefore(overlapEnd)
                        && item.getPlannedEndAt().isAfter(overlapStart))
                .sorted(Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt))
                .toList()) {
            if (previous.getPlannedStartAt().isAfter(coveredUntil)) {
                return false;
            }
            if (previous.getPlannedEndAt().isAfter(coveredUntil)) {
                coveredUntil = previous.getPlannedEndAt();
            }
            if (!coveredUntil.isBefore(overlapEnd)) {
                return true;
            }
        }
        return false;
    }

    /** 按课程日期筛选学生当日有效的班课，用于避免未来报名的学生提前触发冲突。 */
    private List<Long> activeClassIdsByStudentOnDate(Long studentId, LocalDate lessonDate) {
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .and(lessonDate != null, wrapper -> wrapper
                                .isNull(ClassEnrollmentEntity::getEnrollmentDate)
                                .or()
                                .le(ClassEnrollmentEntity::getEnrollmentDate, lessonDate))
                        .and(lessonDate != null, wrapper -> wrapper
                                .isNull(ClassEnrollmentEntity::getExpiryDate)
                                .or()
                                .ge(ClassEnrollmentEntity::getExpiryDate, lessonDate)))
                .stream()
                .filter(enrollment -> enrollmentIncludesLessonDate(enrollment, lessonDate))
                .map(ClassEnrollmentEntity::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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

    private List<Long> activeStudentIdsByClassOnDate(Long classId, LocalDate lessonDate) {
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .and(lessonDate != null, wrapper -> wrapper
                                .isNull(ClassEnrollmentEntity::getEnrollmentDate)
                                .or()
                                .le(ClassEnrollmentEntity::getEnrollmentDate, lessonDate))
                        .and(lessonDate != null, wrapper -> wrapper
                                .isNull(ClassEnrollmentEntity::getExpiryDate)
                                .or()
                                .ge(ClassEnrollmentEntity::getExpiryDate, lessonDate)))
                .stream()
                .filter(enrollment -> enrollmentIncludesLessonDate(enrollment, lessonDate))
                .map(ClassEnrollmentEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean enrollmentIncludesLessonDate(ClassEnrollmentEntity enrollment, LocalDate lessonDate) {
        if (enrollment == null || lessonDate == null || enrollment.getCreatedAt() == null) {
            return true;
        }
        return !lessonDate.isBefore(enrollment.getCreatedAt().toLocalDate());
    }

    private void lockClassScheduleResources(Long classId, List<ClassGroupScheduleSlotEntity> slots) {
        activeStudentIdsByClass(classId).stream()
                .sorted()
                .forEach(studentMapper::selectByIdForUpdate);
        slots.stream()
                .map(ClassGroupScheduleSlotEntity::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(teacherMapper::selectByIdForUpdate);
        slots.stream()
                .map(ClassGroupScheduleSlotEntity::getClassroomId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(classroomMapper::selectByIdForUpdate);
    }

    private Long firstTeacherId(List<ClassGroupScheduleSlotEntity> slots) {
        return slots.stream()
                .map(ClassGroupScheduleSlotEntity::getTeacherId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Long firstClassroomId(List<ClassGroupScheduleSlotEntity> slots) {
        return slots.stream()
                .map(ClassGroupScheduleSlotEntity::getClassroomId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
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
        item.put("gradeBand", clz.getGradeBand());
        item.put("actualClass", StringUtils.hasText(clz.getActualClass()) ? clz.getActualClass() : clz.getGradeBand());
        item.put("startDate", clz.getStartDate());
        item.put("endDate", clz.getEndDate());
        item.put("teacherName", teacherName);
        item.put("classroomName", classroomName);
        item.put("listPrice", clz.getListPrice());
        item.put("productId", clz.getProductId());
        item.put("status", clz.getStatus());
        item.put("scheduleSlots", toScheduleSlotList(clz.getId(), clz.getScheduleDesc()));
        return item;
    }

    /**
     * 解析 scheduleDesc 为前端可用的 slot 列表。
     * 优先从班级排课时段表加载，降级到解析班级自己的 scheduleDesc 文本。
     */
    private List<Map<String, Object>> toScheduleSlotList(Long classGroupId, String scheduleDesc) {
        if (classGroupId != null) {
            List<ClassGroupScheduleSlotEntity> classSlots = classGroupScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                            .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroupId)
                            .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
            if (!classSlots.isEmpty()) {
                return classSlots.stream()
                        .map(slot -> {
                            int minutes = (int) ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
                            return toSlotMap(slot.getTeacherId(), slot.getClassroomId(), slot.getDeliveryMode(), slot.getWeekday(),
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
                    .map(slot -> toSlotMap(null, null, null, slot.weekday(), slot.startTime(), slot.endTime(), slot.plannedUnits()))
                    .toList();
        }
        return Collections.emptyList();
    }

    private Map<String, Object> toSlotMap(Long teacherId, Long classroomId, String deliveryMode, int weekday,
                                          String startTime, String endTime, int plannedUnits) {
        Map<String, Object> m = new HashMap<>();
        m.put("teacherId", teacherId);
        m.put("classroomId", classroomId);
        m.put("deliveryMode", deliveryMode);
        m.put("weekday", weekday);
        m.put("startTime", startTime);
        m.put("endTime", endTime);
        m.put("plannedUnits", plannedUnits);
        return m;
    }

    private Long parseLongValue(Object value) {
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
}
