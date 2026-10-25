package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.base.entity.ProductGradeConfigEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.base.mapper.ProductGradeConfigMapper;
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
import com.classdeduction.system.modules.schedule.service.ClassAdjustedSlotReservations;
import com.classdeduction.system.modules.schedule.service.ClassGroupService;
import com.classdeduction.system.modules.schedule.service.ClassHolidayService;
import com.classdeduction.system.modules.schedule.util.ClassScheduleDates;
import com.classdeduction.system.modules.schedule.util.ClassSlotRotation;
import com.classdeduction.system.modules.schedule.util.ScheduleSlotTime;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.schedule.util.ScheduleDescParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
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
    private static final DateTimeFormatter CONFLICT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CONFLICT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final ClassAdjustedSlotReservations adjustedSlotReservations;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final CampusMapper campusMapper;
    private final CourseProductMapper courseProductMapper;
    private final GroupCourseProjectMapper groupCourseProjectMapper;
    private final ProductGradeConfigMapper productGradeConfigMapper;
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
        Set<Long> campusIds = classes.stream().map(ClassGroupEntity::getCampusId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CampusEntity> campusMap = campusIds.isEmpty() ? Map.of() : campusMapper.selectBatchIds(campusIds).stream()
                .collect(Collectors.toMap(CampusEntity::getId, item -> item));
        // Enrollment 等列表按 V2 教学项目分组；批量读取名称，避免逐班查询。
        Set<Long> projectIds = classes.stream().map(ClassGroupEntity::getGroupProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, GroupCourseProjectEntity> projectMap = projectIds.isEmpty() ? Map.of()
                : groupCourseProjectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(GroupCourseProjectEntity::getId, item -> item));
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
            CampusEntity campus = clz.getCampusId() == null ? null : campusMap.get(clz.getCampusId());
            item.put("campusId", clz.getCampusId());
            item.put("campusName", campus == null ? null : campus.getCampusName());
            item.put("listPrice", clz.getListPrice());
            item.put("productId", clz.getProductId());
            item.put("productName", product == null ? null : product.getProductName());
            item.put("groupProjectId", clz.getGroupProjectId());
            GroupCourseProjectEntity project = clz.getGroupProjectId() == null
                    ? null : projectMap.get(clz.getGroupProjectId());
            item.put("groupProjectName", project == null ? null : project.getProjectName());
            item.put("registerClass", product == null ? null : product.getRegisterClass());
            item.put("courseType", product == null ? null : product.getCourseType());
            item.put("gradeBand", clz.getGradeBand());
            item.put("actualClass", StringUtils.hasText(clz.getActualClass()) ? clz.getActualClass() : clz.getGradeBand());
            item.put("startDate", clz.getStartDate());
            item.put("endDate", clz.getEndDate());
            item.put("scheduleBaseEndDate", clz.getScheduleBaseEndDate());
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
        detail.put("scheduleBaseEndDate", clz.getScheduleBaseEndDate());
        detail.put("scheduleSlots", toScheduleSlotList(clz.getId(), clz.getScheduleDesc()));
        detail.put("teacherName", teacher != null ? teacher.getTeacherName() : null);
        detail.put("classroomName", classroom != null ? classroom.getRoomName() : null);
        CampusEntity campus = clz.getCampusId() == null ? null : campusMapper.selectById(clz.getCampusId());
        detail.put("campusId", clz.getCampusId());
        detail.put("campusName", campus == null ? null : campus.getCampusName());
        detail.put("status", clz.getStatus());
        return detail;
    }

    @Override
    public List<Map<String, Object>> listEnrolledStudents(Long classId, CurrentUserResponse currentUser) {
        requireClassForRead(classId, currentUser);
        List<ClassEnrollmentEntity> rosterEnrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        if (rosterEnrollments.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> logicalIds = rosterEnrollments.stream().map(ClassEnrollmentEntity::getLogicalCourseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<ClassEnrollmentEntity> linkedEnrollments = logicalIds.isEmpty() ? List.of()
                : classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .in(ClassEnrollmentEntity::getLogicalCourseId, logicalIds)
                        .in(ClassEnrollmentEntity::getStatus, "ACTIVE", "EXPIRED")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        Map<Long, ClassEnrollmentEntity> allSegmentsById = new LinkedHashMap<>();
        rosterEnrollments.forEach(enrollment -> allSegmentsById.put(enrollment.getId(), enrollment));
        linkedEnrollments.forEach(enrollment -> allSegmentsById.put(enrollment.getId(), enrollment));
        Map<String, List<ClassEnrollmentEntity>> segmentsByCourse = allSegmentsById.values().stream()
                .collect(Collectors.groupingBy(this::logicalEnrollmentKey, LinkedHashMap::new, Collectors.toList()));
        List<Long> studentIds = rosterEnrollments.stream()
                .map(ClassEnrollmentEntity::getStudentId)
                .toList();
        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(StudentEntity::getId, s -> s));

        LocalDate today = LocalDate.now();
        Map<Long, List<LocalDate>> confirmedLessonDatesByStudent = loadConfirmedLessonDatesByStudent(classId);
        Set<Long> segmentClassIds = allSegmentsById.values().stream().map(ClassEnrollmentEntity::getClassId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, List<LocalDate>> confirmedLessonDatesByEntitlement =
                loadConfirmedLessonDatesByEntitlements(segmentClassIds);
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> displayedCourses = new HashSet<>();
        for (ClassEnrollmentEntity enrollment : rosterEnrollments) {
            String courseKey = logicalEnrollmentKey(enrollment);
            if (displayedCourses.contains(courseKey)) continue;
            StudentEntity student = studentMap.get(enrollment.getStudentId());
            if (student == null) continue;
            List<ClassEnrollmentEntity> segments = segmentsByCourse.getOrDefault(courseKey, List.of(enrollment)).stream()
                    .sorted(Comparator.comparing(ClassEnrollmentEntity::getEnrollmentDate,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            ClassEnrollmentEntity latestSegment = segments.get(segments.size() - 1);
            if (!Objects.equals(latestSegment.getClassId(), classId)
                    && enrollment.getExpiryDate() != null && enrollment.getExpiryDate().isBefore(today)) {
                continue;
            }
            displayedCourses.add(courseKey);
            LocalDate firstDate = segments.stream().map(ClassEnrollmentEntity::getEnrollmentDate)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(enrollment.getEnrollmentDate());
            LocalDate lastDate = segments.stream().map(ClassEnrollmentEntity::getExpiryDate)
                    .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(enrollment.getExpiryDate());
            boolean perSession = "PER_SESSION".equals(enrollment.getBillingType());
            int purchased = segments.stream().mapToInt(segment -> safeQuantity(segment.getPurchasedQuantity())).sum();
            long used = perSession ? segments.stream().mapToLong(segment -> {
                List<LocalDate> dates = segment.getGroupProjectId() == null
                        ? confirmedLessonDatesByStudent.getOrDefault(segment.getStudentId(), List.of())
                        : confirmedLessonDatesByEntitlement.getOrDefault(segment.getId(), List.of());
                return countUsedSessions(segment, dates);
            }).sum() : 0;

            String expiryStatus;
            if (lastDate != null && lastDate.isBefore(today)) {
                expiryStatus = "EXPIRED";
            } else if (lastDate != null && lastDate.isBefore(today.plusDays(30))) {
                expiryStatus = "EXPIRING_SOON";
            } else {
                expiryStatus = "NORMAL";
            }
            Map<String, Object> item = new HashMap<>();
            item.put("studentId", enrollment.getStudentId());
            item.put("studentName", student.getStudentName());
            item.put("logicalCourseId", enrollment.getLogicalCourseId() == null ? enrollment.getId() : enrollment.getLogicalCourseId());
            item.put("enrollmentDate", firstDate);
            item.put("expiryDate", lastDate);
            item.put("billingType", enrollment.getBillingType());
            item.put("purchasedQuantity", purchased);
            item.put("usedSessions", perSession ? used : null);
            item.put("remainingSessions", perSession ? Math.max(purchased - used, 0) : null);
            item.put("purchaseSegments", segments.stream().map(this::purchaseSegmentView).toList());
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

    /** 未续课的旧在册独立成课；已续课的各权益段按学生和链根归组。 */
    private String logicalEnrollmentKey(ClassEnrollmentEntity enrollment) {
        return enrollment.getStudentId() + ":" + (enrollment.getLogicalCourseId() == null
                ? enrollment.getId() : enrollment.getLogicalCourseId());
    }

    private int safeQuantity(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }

    private Map<String, Object> purchaseSegmentView(ClassEnrollmentEntity segment) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("classEnrollmentId", segment.getId());
        view.put("classId", segment.getClassId());
        view.put("enrollmentDate", segment.getEnrollmentDate());
        view.put("expiryDate", segment.getExpiryDate());
        view.put("purchasedQuantity", safeQuantity(segment.getPurchasedQuantity()));
        return view;
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
        Map<Long, ClassGroupScheduleSlotEntity> scheduleSlotById = listClassScheduleSlots(classId).stream()
                .collect(Collectors.toMap(ClassGroupScheduleSlotEntity::getId, item -> item));

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
            ClassGroupScheduleSlotEntity scheduleSlot = scheduleSlotById.get(occ.getScheduleSlotId());
            item.put("subjectName", StringUtils.hasText(occ.getSubjectName()) ? occ.getSubjectName()
                    : scheduleSlot == null ? null : scheduleSlot.getSubjectName());
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
        // 报名、退款、假期重排都先锁实际班级；确认也先锁同一行，保证读取到稳定的权益和课次状态。
        ClassGroupEntity confirmedClass = classGroupMapper.selectByIdForUpdate(classId);
        if (confirmedClass == null) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found.");
        }
        // 获取排课记录
        LessonOccurrenceEntity occ = lessonOccurrenceMapper.selectByIdForUpdate(occurrenceId);
        if (occ == null || !classId.equals(occ.getClassGroupId())) {
            throw new BusinessException("OCCURRENCE_NOT_FOUND", "Lesson occurrence not found for this class.");
        }
        if (occ.getCourseHolidayId() != null || "CANCELLED".equals(occ.getPlanningStatus())) {
            throw new BusinessException("CLASS_LESSON_CANCELLED", "This class lesson is cancelled for a school holiday.");
        }
        if ("CANCELLED".equals(occ.getPlanningStatus())) {
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
        if (confirmedClass != null && confirmedClass.getGroupProjectId() != null) {
            activeEnrollments = resolveV2EntitlementsForOccurrence(
                    activeEnrollments, occ, loadConfirmedLessonDatesByEntitlement(classId));
        } else {
            activeEnrollments = activeEnrollments.stream()
                    .filter(enrollment -> isEnrollmentEligibleForOccurrence(
                            enrollment, occ, confirmedLessonDatesByStudent))
                    .toList();
        }
        if (activeEnrollments.isEmpty()) {
            boolean adminCancellation = !isTeacherUser(currentUser) && body != null
                    && "CANCELLED".equals(body.get("resultStatus"));
            if (adminCancellation) {
                occ.setPlanningStatus("MERGED");
                lessonOccurrenceMapper.updateById(occ);
                return Map.of("confirmedCount", 0, "occurrenceId", occurrenceId, "confirmStage", CONFIRM_STAGE_CONFIRMED);
            }
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
        boolean adminCancellation = !teacherConfirm && "CANCELLED".equals(resultStatus);
        if (actualTeacherId == null && !adminCancellation) {
            throw new BusinessException("CLASS_TEACHER_REQUIRED",
                    "Assign a teacher before confirming this class lesson.");
        }
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
        } else if (!adminCancellation) {
            ensureNoClassConfirmConflicts(occ, activeEnrollments, actualTeacherId,
                    actualClassroomId, actualStartAt, actualEndAt);
        }

        for (ClassEnrollmentEntity enrollment : activeEnrollments) {
            LessonRecordEntity existing = existingRecordsByStudent.get(enrollment.getStudentId());
            if (existing != null && !CONFIRM_STAGE_REJECTED.equals(existing.getConfirmStage())) {
                if (teacherConfirm || CONFIRM_STAGE_CONFIRMED.equals(existing.getConfirmStage())) {
                    continue;
                }
                if (existing.getClassEnrollmentId() == null) {
                    existing.setClassEnrollmentId(enrollment.getId());
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
            record.setClassEnrollmentId(enrollment.getId());
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

    /** V2 班课将一次确认明确绑定到唯一订单权益，避免续费权益相互串用。 */
    private List<ClassEnrollmentEntity> resolveV2EntitlementsForOccurrence(
            List<ClassEnrollmentEntity> enrollments,
            LessonOccurrenceEntity occurrence,
            Map<Long, List<LocalDate>> confirmedDatesByEntitlement) {
        if (occurrence.getPlannedStartAt() == null) return Collections.emptyList();
        LocalDate lessonDate = occurrence.getPlannedStartAt().toLocalDate();
        Map<Long, List<ClassEnrollmentEntity>> byStudent = enrollments.stream()
                .filter(enrollment -> enrollment.getGroupProjectId() != null)
                .filter(enrollment -> enrollmentIncludesOccurrence(enrollment, occurrence))
                .filter(enrollment -> enrollment.getEnrollmentDate() == null
                        || !lessonDate.isBefore(enrollment.getEnrollmentDate()))
                .filter(enrollment -> enrollment.getExpiryDate() == null
                        || !lessonDate.isAfter(enrollment.getExpiryDate()))
                .filter(enrollment -> attendanceMatches(enrollment.getAttendanceOption(), occurrence))
                .filter(enrollment -> {
                    if (!"PER_SESSION".equals(enrollment.getBillingType())
                            || enrollment.getPurchasedQuantity() == null) return true;
                    List<LocalDate> usedDates = confirmedDatesByEntitlement
                            .getOrDefault(enrollment.getId(), Collections.emptyList());
                    long used = usedDates.stream()
                            .filter(date -> enrollment.getEnrollmentDate() == null
                                    || !date.isBefore(enrollment.getEnrollmentDate()))
                            .filter(date -> enrollment.getExpiryDate() == null
                                    || !date.isAfter(enrollment.getExpiryDate()))
                            .distinct().count();
                    return used < enrollment.getPurchasedQuantity() || usedDates.contains(lessonDate);
                })
                .collect(Collectors.groupingBy(ClassEnrollmentEntity::getStudentId));
        List<ClassEnrollmentEntity> resolved = new ArrayList<>();
        for (Map.Entry<Long, List<ClassEnrollmentEntity>> entry : byStudent.entrySet()) {
            if (entry.getValue().size() != 1) {
                throw new BusinessException("GROUP_ENTITLEMENT_AMBIGUOUS",
                        "More than one active registration covers this student and class date. Resolve the overlapping registrations before confirming the lesson.");
            }
            resolved.add(entry.getValue().get(0));
        }
        return resolved;
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

    private long countUsedSessions(ClassEnrollmentEntity enrollment, List<LocalDate> dates) {
        return dates.stream()
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

    private Map<Long, List<LocalDate>> loadConfirmedLessonDatesByEntitlement(Long classGroupId) {
        return loadConfirmedLessonDatesByEntitlements(Set.of(classGroupId));
    }

    /** 课程汇总跨实际班级读取消课日期，单次课的原权益关联保持不变。 */
    private Map<Long, List<LocalDate>> loadConfirmedLessonDatesByEntitlements(Set<Long> classGroupIds) {
        if (classGroupIds.isEmpty()) return Collections.emptyMap();
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId, LessonOccurrenceEntity::getPlannedStartAt)
                        .in(LessonOccurrenceEntity::getClassGroupId, classGroupIds));
        if (occurrences.isEmpty()) return Collections.emptyMap();
        Map<Long, LocalDate> datesByOccurrence = occurrences.stream()
                .filter(item -> item.getPlannedStartAt() != null)
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId,
                        item -> item.getPlannedStartAt().toLocalDate(), (left, right) -> left));
        if (datesByOccurrence.isEmpty()) return Collections.emptyMap();
        return lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, datesByOccurrence.keySet())
                        .isNotNull(LessonRecordEntity::getClassEnrollmentId)
                        .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_CONFIRMED)
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream()
                .filter(record -> record.getClassEnrollmentId() != null
                        && datesByOccurrence.containsKey(record.getOccurrenceId())
                        && CHARGEABLE_CLASS_RESULT_STATUSES.contains(record.getResultStatus()))
                .collect(Collectors.groupingBy(LessonRecordEntity::getClassEnrollmentId,
                        Collectors.mapping(record -> datesByOccurrence.get(record.getOccurrenceId()), Collectors.toList())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectLesson(Long classId, Long occurrenceId, String rejectReason, CurrentUserResponse currentUser) {
        currentUser = defaultAdminIfMissing(currentUser);
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BusinessException("REJECT_REASON_REQUIRED", "Reject reason is required.");
        }
        // 拒绝教师提交与确认、退款共用实际班级锁，避免同一批待确认记录被并发改写。
        ClassGroupEntity classGroup = classGroupMapper.selectByIdForUpdate(classId);
        if (classGroup == null) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found.");
        }
        LessonOccurrenceEntity occ = lessonOccurrenceMapper.selectByIdForUpdate(occurrenceId);
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
        ClassGroupEntity classGroup = classGroupMapper.selectByIdForUpdate(classId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        // 保留旧客户端的班级默认值作为兼容回退；新页面按 slot 提交独立资源。
        Long defaultTeacherId = parseLongFromBody(body, "teacherId");
        Long defaultClassroomId = parseLongFromBody(body, "classroomId");
        boolean segmentedRequest = body.containsKey("segments");
        List<ClassGroupScheduleSlotEntity> priorSlots = classGroupScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId));
        if (!segmentedRequest && priorSlots.stream()
                .anyMatch(slot -> slot.getEffectiveStartDate() != null)) {
            throw new BusinessException("CLASS_SCHEDULE_FORMAT_CHANGED",
                    "This class has date-based schedule periods. Refresh the page before editing.");
        }
        List<ClassGroupScheduleSlotEntity> slots = segmentedRequest
                ? parseClassScheduleSegments(body.get("segments"), classGroup, defaultTeacherId, defaultClassroomId)
                : parseClassScheduleSlots(body.get("slots"), defaultTeacherId, defaultClassroomId);
        if (slots.isEmpty()) {
            throw new BusinessException("CLASS_SCHEDULE_REQUIRED", "Add at least one weekly class time.");
        }
        validateClassScheduleSlots(slots);
        Set<Long> validLineages = priorSlots.stream()
                .map(slot -> slot.getSlotLineageId() == null ? slot.getId() : slot.getSlotLineageId())
                .collect(Collectors.toSet());
        for (ClassGroupScheduleSlotEntity slot : slots) {
            if (slot.getSlotLineageId() == null) {
                priorSlots.stream().filter(prior -> Objects.equals(prior.getEffectiveStartDate(), slot.getEffectiveStartDate())
                                && Objects.equals(prior.getEffectiveEndDate(), slot.getEffectiveEndDate())
                                && Objects.equals(prior.getWeekday(), slot.getWeekday())
                                && Objects.equals(prior.getStartTime(), slot.getStartTime())
                                && Objects.equals(prior.getEndTime(), slot.getEndTime()))
                        .findFirst().ifPresent(prior -> slot.setSlotLineageId(
                                prior.getSlotLineageId() == null ? prior.getId() : prior.getSlotLineageId()));
            }
            if (slot.getSlotLineageId() != null && !validLineages.contains(slot.getSlotLineageId())) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE", "Refresh before editing this class schedule.");
            }
        }
        slots.stream()
                .map(ClassGroupScheduleSlotEntity::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::validateTeacher);
        slots.stream()
                .map(ClassGroupScheduleSlotEntity::getAlternateTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::validateTeacher);
        slots.stream()
                .flatMap(slot -> java.util.stream.Stream.of(slot.getClassroomId(), slot.getAlternateClassroomId()))
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::validateClassroom);
        lockClassScheduleResources(classId, slots);

        LocalDateTime rebuildFrom = AppTime.nowLocalDateTime();
        // 班级字段仅保留首个已配置资源供旧列表兼容，课次生成以 slot 字段为准。
        classGroup.setTeacherId(firstTeacherId(slots));
        classGroup.setClassroomId(firstClassroomId(slots));
        classGroup.setScheduleDesc(buildScheduleDesc(slots));
        classGroupMapper.updateById(classGroup);

        classGroupScheduleSlotMapper.delete(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId));
        for (ClassGroupScheduleSlotEntity slot : slots) {
            if ((slot.getAlternateTeacherId() != null || StringUtils.hasText(slot.getAlternateSubjectName()))
                    && slot.getRotationAnchorDate() == null) {
                boolean existingLineage = slot.getSlotLineageId() != null && validLineages.contains(slot.getSlotLineageId());
                slot.setRotationAnchorDate(existingLineage || slot.getEffectiveStartDate() == null
                        ? classGroup.getStartDate() == null ? rebuildFrom.toLocalDate() : classGroup.getStartDate()
                        : slot.getEffectiveStartDate());
            }
            slot.setClassGroupId(classId);
            classGroupScheduleSlotMapper.insert(slot);
            if (slot.getSlotLineageId() == null) {
                slot.setSlotLineageId(slot.getId());
                classGroupScheduleSlotMapper.updateById(slot);
            }
        }

        FutureClassOccurrences previousOccurrences = removeFutureUnconfirmedOccurrences(classId, rebuildFrom);
        List<LessonOccurrenceEntity> retainedOccurrences = retainedClassOccurrences(classId, rebuildFrom);
        int generatedCount = generateFutureClassOccurrences(classGroup, slots, retainedOccurrences,
                previousOccurrences.occurrences(), rebuildFrom);
        classHolidayService.recalculateClassSessionEnrollments(classId);
        return Map.of("classId", classId, "cancelledFutureOccurrences", previousOccurrences.removedCount(),
                "generatedOccurrences", generatedCount);
    }

    /** 预览与正式保存复用同一事务路径，读取候选课次后回滚全部写入。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> previewClassSchedule(Long classId, Map<String, Object> body) {
        LocalDateTime previewAt = AppTime.nowLocalDateTime();
        Set<Long> originalFutureIds = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, previewAt))
                .stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        Map<Long, LocalDate> expiryBefore = new HashMap<>();
        classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .in(ClassEnrollmentEntity::getBillingType, "PER_SESSION", "SESSION"))
                .forEach(item -> expiryBefore.put(item.getId(), item.getExpiryDate()));
        Map<String, Object> change = updateClassSchedule(classId, body);
        List<LessonOccurrenceEntity> future = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, previewAt)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
        List<Map<String, Object>> firstLessons = future.stream().limit(40).map(occ -> {
            Map<String, Object> lesson = new LinkedHashMap<String, Object>();
            lesson.put("startAt", occ.getPlannedStartAt());
            lesson.put("endAt", occ.getPlannedEndAt());
            lesson.put("subjectName", occ.getSubjectName());
            lesson.put("hours", occ.getPlannedUnits() == null ? 0 : occ.getPlannedUnits() * 0.5);
            lesson.put("preserved", originalFutureIds.contains(occ.getId()));
            return lesson;
        }).toList();
        Map<String, Object> preview = new LinkedHashMap<>(change);
        preview.put("futureLessonCount", future.size());
        preview.put("futureHours", future.stream().mapToInt(occ -> occ.getPlannedUnits() == null ? 0 : occ.getPlannedUnits()).sum() * 0.5);
        preview.put("retainedFutureOccurrences", Math.max(0,
                future.size() - (int) change.get("generatedOccurrences")));
        long changedEntitlements = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .in(ClassEnrollmentEntity::getBillingType, "PER_SESSION", "SESSION"))
                .stream().filter(item -> !Objects.equals(expiryBefore.get(item.getId()), item.getExpiryDate()))
                .count();
        preview.put("sessionEntitlementChanges", changedEntitlements);
        preview.put("firstLessons", firstLessons);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createActualClass(Map<String, Object> body) {
        Long productId = parseLongFromBody(body, "productId");
        Long groupProjectId = parseLongFromBody(body, "groupProjectId");
        String gradeBand = requireTextFromBody(body, "gradeBand", "Grade is required.");
        String actualClass = requireTextFromBody(body, "actualClass", "Actual class is required.");
        if (groupProjectId == null) {
            throw new BusinessException("GROUP_PROJECT_REQUIRED",
                    "Create actual classes inside a teaching project so registration plans and schedules stay linked.");
        }
        GroupCourseProjectEntity project = groupCourseProjectMapper.selectByIdForUpdate(groupProjectId);
        if (project == null || !Objects.equals(productId, project.getCourseProductId())
                || Integer.valueOf(1).equals(project.getIsDeleted())) {
            throw new BusinessException("GROUP_PROJECT_INVALID", "Select a teaching project from this product.");
        }
        CourseProductEntity product = productId == null ? null : courseProductMapper.selectByIdForUpdate(productId);
        if (product == null || !"GROUP".equals(product.getTeachingMode()) || product.getArchivedAt() != null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Group course product not found.");
        }
        Long campusId = parseLongFromBody(body, "campusId");
        LocalDate startDate = parseLocalDateBody(body, "startDate");
        LocalDate endDate = parseLocalDateBody(body, "endDate");
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException("CLASS_PERIOD_REQUIRED", "Enter a valid start and end date for this actual class.");
        }
        ensureV2ActualClassUnique(null, groupProjectId, campusId, actualClass);
        gradeBand = actualClass;
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setProductId(productId);
        classGroup.setGroupProjectId(groupProjectId);
        classGroup.setGradeBand(gradeBand);
        classGroup.setActualClass(actualClass);
        classGroup.setClassName(resolveClassName(body, actualClass));
        classGroup.setCampusId(parseLongFromBody(body, "campusId"));
        validateCampus(classGroup.getCampusId());
        classGroup.setStartDate(startDate);
        classGroup.setEndDate(endDate);
        classGroup.setScheduleBaseEndDate(endDate);
        classGroup.setListPrice(java.math.BigDecimal.ZERO);
        classGroup.setStatus("ACTIVE");
        // 班级日期属于项目下的实际班级；不再从旧 Product/Grade 配置继承。
        classGroupMapper.insert(classGroup);
        return toClassInfoMap(classGroup);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateClassInfo(Long classId, Map<String, Object> body) {
        ClassGroupEntity classGroup = classGroupMapper.selectByIdForUpdate(classId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        String actualClass = requireTextFromBody(body, "actualClass", "Actual class is required.");
        if (classGroup.getGroupProjectId() != null) {
            Long campusId = body.containsKey("campusId")
                    ? parseLongFromBody(body, "campusId") : classGroup.getCampusId();
            ensureV2ActualClassUnique(classId, classGroup.getGroupProjectId(), campusId, actualClass);
            classGroup.setGradeBand(actualClass);
        } else {
            ensureActualClassUnique(classId, classGroup.getProductId(), classGroup.getGradeBand(), actualClass);
        }
        classGroup.setActualClass(actualClass);
        classGroup.setClassName(resolveClassName(body, actualClass));
        if (body.containsKey("campusId")) {
            classGroup.setCampusId(parseLongFromBody(body, "campusId"));
            validateCampus(classGroup.getCampusId());
        }
        if (classGroup.getGroupProjectId() != null) {
            LocalDate startDate = body.containsKey("startDate")
                    ? parseLocalDateBody(body, "startDate") : classGroup.getStartDate();
            LocalDate endDate = body.containsKey("endDate")
                    ? parseLocalDateBody(body, "endDate") : classGroup.getEndDate();
            if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
                throw new BusinessException("CLASS_PERIOD_INVALID", "Enter a valid start and end date for this actual class.");
            }
            boolean datesChanged = !Objects.equals(startDate, classGroup.getStartDate())
                    || !Objects.equals(endDate, classGroup.getEndDate());
            if (datesChanged) {
                boolean hasPeriods = classGroupScheduleSlotMapper.selectCount(
                        new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId)
                                .isNotNull(ClassGroupScheduleSlotEntity::getEffectiveStartDate)) > 0;
                if (hasPeriods) {
                    throw new BusinessException("CLASS_PERIOD_HAS_SCHEDULE",
                            "Edit the class schedule periods before changing the class dates.");
                }
                classGroup.setStartDate(startDate);
                classGroup.setEndDate(endDate);
                classGroup.setScheduleBaseEndDate(endDate);
            }
        }
        classGroupMapper.updateById(classGroup);
        return toClassInfoMap(classGroup);
    }

    private void ensureV2ActualClassUnique(Long currentClassId, Long projectId, Long campusId, String actualClass) {
        LambdaQueryWrapper<ClassGroupEntity> query = new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getGroupProjectId, projectId)
                .eq(ClassGroupEntity::getActualClass, actualClass)
                .eq(ClassGroupEntity::getIsDeleted, 0);
        if (campusId == null) query.isNull(ClassGroupEntity::getCampusId);
        else query.eq(ClassGroupEntity::getCampusId, campusId);
        ClassGroupEntity duplicate = classGroupMapper.selectOne(query.last("LIMIT 1"));
        if (duplicate != null && !Objects.equals(duplicate.getId(), currentClassId)) {
            throw new BusinessException("ACTUAL_CLASS_DUPLICATED",
                    "This project already has an actual class with that name at this campus.");
        }
    }

    private LocalDate parseLocalDateBody(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return LocalDate.parse(text.trim());
            } catch (RuntimeException exception) {
                throw new BusinessException("CLASS_PERIOD_INVALID", "Enter dates in YYYY-MM-DD format.");
            }
        }
        return null;
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

    /** 新建实际班时优先沿用同 Grade 班级的计费元数据；无同级班时回退到 Grade 共享日期配置。 */
    private void inheritGradeBillingMetadata(ClassGroupEntity classGroup) {
        ClassGroupEntity sibling = classGroupMapper.selectOne(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, classGroup.getProductId())
                .eq(ClassGroupEntity::getGradeBand, classGroup.getGradeBand())
                .eq(ClassGroupEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (sibling != null) {
            classGroup.setBillingType(sibling.getBillingType());
            classGroup.setStartDate(sibling.getStartDate());
            classGroup.setEndDate(sibling.getEndDate());
            classGroup.setScheduleBaseEndDate(sibling.getScheduleBaseEndDate() == null
                    ? sibling.getEndDate() : sibling.getScheduleBaseEndDate());
            classGroup.setTotalSessions(sibling.getTotalSessions());
            classGroup.setStartMonth(sibling.getStartMonth());
            classGroup.setEndMonth(sibling.getEndMonth());
            return;
        }

        ProductGradeConfigEntity gradeConfig = productGradeConfigMapper.selectOne(
                new LambdaQueryWrapper<ProductGradeConfigEntity>()
                        .eq(ProductGradeConfigEntity::getCourseProductId, classGroup.getProductId())
                        .eq(ProductGradeConfigEntity::getGradeLabel, classGroup.getGradeBand())
                        .eq(ProductGradeConfigEntity::getClassPeriodConfigured, true)
                        .last("LIMIT 1"));
        if (gradeConfig != null) {
            classGroup.setStartDate(gradeConfig.getClassStartDate());
            classGroup.setEndDate(gradeConfig.getClassEndDate());
            classGroup.setScheduleBaseEndDate(gradeConfig.getClassEndDate());
        }
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

    private void validateCampus(Long campusId) {
        if (campusId == null) {
            return;
        }
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null || !"ACTIVE".equals(campus.getStatus())) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "Selected campus is not active.");
        }
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
        item.put("groupProjectId", classGroup.getGroupProjectId());
        item.put("gradeBand", classGroup.getGradeBand());
        item.put("actualClass", StringUtils.hasText(classGroup.getActualClass())
                ? classGroup.getActualClass()
                : classGroup.getGradeBand());
        item.put("teacherId", classGroup.getTeacherId());
        item.put("classroomId", classGroup.getClassroomId());
        item.put("campusId", classGroup.getCampusId());
        CampusEntity campus = classGroup.getCampusId() == null ? null : campusMapper.selectById(classGroup.getCampusId());
        item.put("campusName", campus == null ? null : campus.getCampusName());
        item.put("startDate", classGroup.getStartDate());
        item.put("endDate", classGroup.getEndDate());
        item.put("scheduleDesc", classGroup.getScheduleDesc());
        return item;
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
        CourseProductEntity product = clz.getProductId() == null
                ? null : courseProductMapper.selectById(clz.getProductId());
        // 班课权益必须由收据订单创建，避免旧项目less班级入口产生无方案或无支付归属的报名。
        if (clz.getGroupProjectId() != null || product == null || "GROUP".equals(product.getTeachingMode())) {
            throw new BusinessException("GROUP_V2_REGISTRATION_REQUIRED",
                    "Register this student through a receipt so the course plan and payment are linked correctly.");
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

        LocalDate today = LocalDate.now();
        Map<String, ClassEnrollmentEntity> currentByCourse = new LinkedHashMap<>();
        for (ClassEnrollmentEntity enrollment : enrollments) {
            String key = logicalEnrollmentKey(enrollment);
            ClassEnrollmentEntity previous = currentByCourse.get(key);
            if (previous == null || preferStudentCourseEnrollment(enrollment, previous, today)) {
                currentByCourse.put(key, enrollment);
            }
        }
        enrollments = new ArrayList<>(currentByCourse.values());

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

    /** 学生课表优先显示当前有效班，未来才显示下一班，避免续课换班后出现两门课。 */
    private boolean preferStudentCourseEnrollment(ClassEnrollmentEntity candidate,
                                                  ClassEnrollmentEntity previous, LocalDate today) {
        int candidateRank = enrollmentDateRank(candidate, today);
        int previousRank = enrollmentDateRank(previous, today);
        if (candidateRank != previousRank) return candidateRank < previousRank;
        if (candidateRank == 1) {
            return candidate.getEnrollmentDate().isBefore(previous.getEnrollmentDate());
        }
        return candidate.getExpiryDate().isAfter(previous.getExpiryDate());
    }

    private int enrollmentDateRank(ClassEnrollmentEntity enrollment, LocalDate today) {
        if (!enrollment.getEnrollmentDate().isAfter(today) && !enrollment.getExpiryDate().isBefore(today)) return 0;
        return enrollment.getEnrollmentDate().isAfter(today) ? 1 : 2;
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
        // 1. 与报名和排课编辑共用班级行锁，避免事务并发留下孤立权益或课次。
        ClassGroupEntity clz = classGroupMapper.selectByIdForUpdate(classId);
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

    /** 归档专用终止路径：不同于删除，保留班级、报名和固定课表行供历史追溯。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endClassForProductArchive(Long classId, Long operatorId) {
        ClassGroupEntity classGroup = classGroupMapper.selectByIdForUpdate(classId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            return;
        }
        LocalDateTime now = AppTime.nowLocalDateTime();
        classGroup.setStatus("INACTIVE");
        classGroup.setUpdatedAt(now);
        classGroup.setUpdatedBy(operatorId);
        classGroupMapper.updateById(classGroup);

        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .orderByAsc(ClassEnrollmentEntity::getId)
                        .last("FOR UPDATE"));
        for (ClassEnrollmentEntity enrollment : enrollments) {
            enrollment.setStatus("CANCELLED");
            enrollment.setUpdatedAt(now);
            enrollment.setUpdatedBy(operatorId);
            classEnrollmentMapper.updateById(enrollment);
        }
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
                        .and(wrapper -> wrapper.eq(ClassGroupScheduleSlotEntity::getTeacherId, teacherId)
                                .or().eq(ClassGroupScheduleSlotEntity::getAlternateTeacherId, teacherId))
                        .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0)) > 0;
        if (!assignedToClass) {
            throw new BusinessException("FORBIDDEN", "Teachers can only view their own classes.");
        }
    }

    private List<Long> classIdsBySlotTeacher(Long teacherId) {
        return classGroupScheduleSlotMapper.selectList(
                        new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                                .and(wrapper -> wrapper.eq(ClassGroupScheduleSlotEntity::getTeacherId, teacherId)
                                        .or().eq(ClassGroupScheduleSlotEntity::getAlternateTeacherId, teacherId))
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
            LocalTime end = parseEndTime(map.get("endTime"));
            if (weekday == null || start == null || end == null) {
                continue;
            }
            ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
            slot.setSlotLineageId(parseLongValue(map.get("slotLineageId")));
            Long teacherId = parseLongValue(map.get("teacherId"));
            Long alternateTeacherId = parseLongValue(map.get("alternateTeacherId"));
            Long classroomId = parseLongValue(map.get("classroomId"));
            Long alternateClassroomId = parseLongValue(map.get("alternateClassroomId"));
            String deliveryMode = map.get("deliveryMode") == null ? null : String.valueOf(map.get("deliveryMode"));
            String alternateDeliveryMode = map.get("alternateDeliveryMode") == null
                    ? null : String.valueOf(map.get("alternateDeliveryMode"));
            if ("ONLINE".equalsIgnoreCase(deliveryMode)) {
                if (classroomId != null) {
                    throw new BusinessException("INVALID_SLOT_CLASSROOM", "Online slots cannot have a classroom.");
                }
                slot.setDeliveryMode("ONLINE");
            } else {
                if (deliveryMode != null && !"IN_PERSON".equalsIgnoreCase(deliveryMode)) {
                    throw new BusinessException("INVALID_DELIVERY_MODE", "Delivery mode must be In-person or Online.");
                }
                Long resolvedClassroomId = classroomId != null ? classroomId : defaultClassroomId;
                // 教室可以留空；仍将线下时段标为 IN_PERSON，Online 仍由显式选项单独标记。
                slot.setDeliveryMode("IN_PERSON");
                slot.setClassroomId(resolvedClassroomId);
            }
            slot.setTeacherId(teacherId != null ? teacherId : defaultTeacherId);
            slot.setSubjectName(optionalText(map.get("subjectName")));
            slot.setAlternateTeacherId(alternateTeacherId);
            slot.setAlternateSubjectName(optionalText(map.get("alternateSubjectName")));
            if (alternateDeliveryMode != null && !alternateDeliveryMode.isBlank()) {
                alternateDeliveryMode = alternateDeliveryMode.trim().toUpperCase(Locale.ROOT);
                if (!"ONLINE".equals(alternateDeliveryMode) && !"IN_PERSON".equals(alternateDeliveryMode)) {
                    throw new BusinessException("INVALID_DELIVERY_MODE", "Delivery mode must be In-person or Online.");
                }
            }
            if ("ONLINE".equals(alternateDeliveryMode) && alternateClassroomId != null) {
                throw new BusinessException("INVALID_SLOT_CLASSROOM", "Online slots cannot have a classroom.");
            }
            slot.setAlternateDeliveryMode(alternateDeliveryMode);
            slot.setAlternateClassroomId(alternateClassroomId);
            Object rotationAnchor = map.get("rotationAnchorDate");
            if (rotationAnchor instanceof String text && !text.isBlank()) {
                try {
                    slot.setRotationAnchorDate(LocalDate.parse(text));
                } catch (RuntimeException exception) {
                    throw new BusinessException("INVALID_CLASS_DATE", "Rotation start dates must use YYYY-MM-DD format.");
                }
            }
            slot.setWeekday(weekday);
            slot.setStartTime(start);
            slot.setEndTime(end);
            result.add(slot);
        }
        return result;
    }

    /** 班课创建和编辑共用：把用户配置的互不重叠日期段转成带生效日期的周时段。 */
    private List<ClassGroupScheduleSlotEntity> parseClassScheduleSegments(
            Object value, ClassGroupEntity classGroup, Long defaultTeacherId, Long defaultClassroomId) {
        if (!(value instanceof List<?> rawSegments) || rawSegments.isEmpty()) {
            throw new BusinessException("CLASS_SCHEDULE_REQUIRED", "Add at least one schedule period.");
        }
        LocalDate baseEnd = ClassScheduleDates.resolveBaseEndDate(
                classGroup.getScheduleBaseEndDate(), classGroup.getEndDate());
        List<LocalDate[]> periods = new ArrayList<>();
        List<ClassGroupScheduleSlotEntity> result = new ArrayList<>();
        for (Object rawSegment : rawSegments) {
            if (!(rawSegment instanceof Map<?, ?> segment)) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE", "Complete each schedule period.");
            }
            LocalDate start = parseSchedulePeriodDate(segment.get("startDate"));
            LocalDate end = parseSchedulePeriodDate(segment.get("endDate"));
            if (start == null || end == null || end.isBefore(start)
                    || classGroup.getStartDate() != null && start.isBefore(classGroup.getStartDate())
                    || baseEnd != null && end.isAfter(baseEnd)) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE",
                        "Schedule periods must have valid dates inside the original class period.");
            }
            for (LocalDate[] prior : periods) {
                if (!end.isBefore(prior[0]) && !start.isAfter(prior[1])) {
                    throw new BusinessException("INVALID_CLASS_SCHEDULE", "Schedule periods cannot overlap.");
                }
            }
            List<ClassGroupScheduleSlotEntity> segmentSlots = parseClassScheduleSlots(
                    segment.get("slots"), defaultTeacherId, defaultClassroomId);
            if (segmentSlots.isEmpty()) {
                throw new BusinessException("CLASS_SCHEDULE_REQUIRED", "Add a weekly time to every schedule period.");
            }
            for (ClassGroupScheduleSlotEntity slot : segmentSlots) {
                slot.setEffectiveStartDate(start);
                slot.setEffectiveEndDate(end);
            }
            periods.add(new LocalDate[]{start, end});
            result.addAll(segmentSlots);
        }
        return result;
    }

    private LocalDate parseSchedulePeriodDate(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            try {
                return LocalDate.parse(text);
            } catch (RuntimeException ignored) {
                // 无效输入由调用方统一转成用户可读的日期提示。
            }
        }
        return null;
    }

    private void validateClassScheduleSlots(List<ClassGroupScheduleSlotEntity> slots) {
        Set<String> seen = new HashSet<>();
        for (ClassGroupScheduleSlotEntity slot : slots) {
            if (slot.getWeekday() == null || slot.getWeekday() < 1 || slot.getWeekday() > 7) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE", "Weekday must be between 1 and 7.");
            }
            if (!ScheduleSlotTime.isValidRange(slot.getStartTime(), slot.getEndTime())) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE", "Schedule end time must be after start time.");
            }
            String key = slot.getEffectiveStartDate() + "|" + slot.getEffectiveEndDate()
                    + "|" + slot.getWeekday() + "|" + slot.getStartTime() + "|" + slot.getEndTime();
            if (!seen.add(key)) {
                throw new BusinessException("INVALID_CLASS_SCHEDULE", "Duplicate class schedule slots are not allowed.");
            }
            boolean hasAlternate = slot.getAlternateTeacherId() != null
                    || StringUtils.hasText(slot.getAlternateSubjectName())
                    || slot.getAlternateClassroomId() != null
                    || StringUtils.hasText(slot.getAlternateDeliveryMode());
            validateClassroom(slot.getClassroomId());
            validateClassroom(slot.getAlternateClassroomId());
            if (hasAlternate && (slot.getTeacherId() == null
                    || !StringUtils.hasText(slot.getSubjectName())
                    || slot.getAlternateTeacherId() == null
                    || !StringUtils.hasText(slot.getAlternateSubjectName()))) {
                throw new BusinessException("INVALID_CLASS_ROTATION",
                        "An alternating slot requires a teacher and subject for both lessons.");
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            ClassGroupScheduleSlotEntity left = slots.get(i);
            for (int j = i + 1; j < slots.size(); j++) {
                ClassGroupScheduleSlotEntity right = slots.get(j);
                boolean samePeriod = left.getEffectiveStartDate() == null && right.getEffectiveStartDate() == null
                        || left.getEffectiveStartDate() != null && right.getEffectiveStartDate() != null
                        && !left.getEffectiveEndDate().isBefore(right.getEffectiveStartDate())
                        && !right.getEffectiveEndDate().isBefore(left.getEffectiveStartDate());
                if (samePeriod && Objects.equals(left.getWeekday(), right.getWeekday())
                        && left.getStartTime().toSecondOfDay() / 60
                                < ScheduleSlotTime.endMinutes(right.getStartTime(), right.getEndTime())
                        && right.getStartTime().toSecondOfDay() / 60
                                < ScheduleSlotTime.endMinutes(left.getStartTime(), left.getEndTime())) {
                    throw new BusinessException("INVALID_CLASS_SCHEDULE", "Class schedule slots cannot overlap on the same day.");
                }
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
                .filter(occ -> "SCHEDULED".equals(occ.getPlanningStatus())
                        && "REGULAR".equals(occ.getOccurrenceType()))
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
        Set<ClassAdjustedSlotReservations.Reservation> adjustedReservations = adjustedSlotReservations.load(classGroup.getId());
        LocalDate configuredStart = classGroup.getStartDate();
        LocalDate configuredEnd = classGroup.getEndDate();
        LocalDate startDate = configuredStart == null || configuredStart.isBefore(today) ? today : configuredStart;
        LocalDate endDate = configuredEnd != null
                ? configuredEnd
                : resolveClassScheduleHorizon(classGroup.getId(), startDate);
        List<ClassHolidayVO> holidays = classHolidayService.listHolidays(classGroup.getGroupProjectId());
        if (configuredStart != null) {
            LocalDate baseEnd = ClassScheduleDates.resolveBaseEndDate(classGroup.getScheduleBaseEndDate(), endDate);
            LocalDate requiredEnd = ClassScheduleDates.endDateAfterHolidays(configuredStart, baseEnd,
                    classGroup.getBillingType(), classGroup.getTotalSessions(), slots,
                    date -> classHolidayService.isHoliday(date, holidays));
            boolean needsUpdate = requiredEnd != null && !requiredEnd.equals(endDate);
            if (classGroup.getScheduleBaseEndDate() == null) {
                classGroup.setScheduleBaseEndDate(baseEnd);
                needsUpdate = true;
            }
            if (needsUpdate) {
                if (requiredEnd != null) {
                    endDate = requiredEnd;
                    classGroup.setEndDate(requiredEnd);
                }
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
                if (!ClassScheduleDates.slotAppliesOn(slot, date,
                        ClassScheduleDates.resolveBaseEndDate(classGroup.getScheduleBaseEndDate(), endDate), slots)) {
                    continue;
                }
                Long lineageId = slot.getSlotLineageId() == null ? slot.getId() : slot.getSlotLineageId();
                if (adjustedReservations.contains(new ClassAdjustedSlotReservations.Reservation(date, lineageId))) {
                    continue;
                }
                LocalDateTime plannedStartAt = LocalDateTime.of(date, slot.getStartTime());
                // 已开始的课次由原记录保留；重建当天排课时不能再次生成并与原课次冲突。
                if (plannedStartAt.isBefore(rebuildFrom)) {
                    continue;
                }
                LocalDateTime plannedEndAt = ScheduleSlotTime.endAt(date, slot.getStartTime(), slot.getEndTime());
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
                ClassSlotRotation.Assignment assignment = ClassSlotRotation.resolve(slot, date,
                        classGroup.getStartDate(),
                        ClassScheduleDates.resolveBaseEndDate(classGroup.getScheduleBaseEndDate(), endDate),
                        slots,
                        holidayDate -> classHolidayService.isHoliday(holidayDate, holidays));
                occurrence.setPlannedTeacherId(assignment.teacherId() != null ? assignment.teacherId()
                        : hasSlotTeacher ? slot.getTeacherId() : classGroup.getTeacherId());
                occurrence.setSubjectName(assignment.subjectName());
                Long classroomId = assignment.alternate()
                        ? ("ONLINE".equals(assignment.deliveryMode()) ? null : assignment.classroomId())
                        : "ONLINE".equals(assignment.deliveryMode()) ? null
                                : hasSlotClassroom ? assignment.classroomId() : classGroup.getClassroomId();
                occurrence.setDeliveryMode(assignment.deliveryMode() == null && classroomId != null
                        ? "IN_PERSON" : assignment.deliveryMode());
                occurrence.setPlannedClassroomId(classroomId);
                occurrence.setPlannedStartAt(plannedStartAt);
                occurrence.setPlannedEndAt(plannedEndAt);
                occurrence.setPlannedUnits(ScheduleSlotTime.durationMinutes(slot.getStartTime(), slot.getEndTime()) / 30);
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
        if (occurrence.getPlannedClassroomId() != null) {
            LessonOccurrenceEntity conflict = findClassScheduleConflict(startAt, endAt,
                    wrapper -> wrapper.eq(LessonOccurrenceEntity::getPlannedClassroomId,
                            occurrence.getPlannedClassroomId()));
            if (conflict != null) {
                throw new BusinessException("SCHEDULE_CONFLICT",
                        describeClassroomConflict(occurrence.getPlannedClassroomId(), classGroup, conflict,
                                startAt, endAt));
            }
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

    /** 班课排课保存时说明占用教室、既有课程和双方时段，便于区分同名教室所在校区。 */
    private String describeClassroomConflict(Long classroomId, ClassGroupEntity classGroup,
                                             LessonOccurrenceEntity conflict,
                                             LocalDateTime startAt, LocalDateTime endAt) {
        ClassroomEntity classroom = classroomMapper.selectById(classroomId);
        String roomName = classroom == null || !StringUtils.hasText(classroom.getRoomName())
                ? "Classroom #" + classroomId : classroom.getRoomName();
        String campusName = classroom == null || classroom.getCampusId() == null
                ? null : Optional.ofNullable(campusMapper.selectById(classroom.getCampusId()))
                .map(CampusEntity::getCampusName).filter(StringUtils::hasText).orElse(null);
        String resource = campusName == null ? roomName : roomName + " (" + campusName + ")";
        String existingTime = conflict.getPlannedStartAt().format(CONFLICT_DATE_TIME)
                + "–" + conflict.getPlannedEndAt().format(CONFLICT_TIME);
        String newTime = startAt.format(CONFLICT_DATE_TIME) + "–" + endAt.format(CONFLICT_TIME);
        return "Classroom " + resource + " is already assigned to " + describeConflictingCourse(conflict)
                + " (" + existingTime + " ET); this overlaps " + describeClassGroup(classGroup)
                + " (" + newTime + " ET).";
    }

    private String describeClassGroup(ClassGroupEntity classGroup) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(classGroup.getGradeBand())) {
            parts.add(classGroup.getGradeBand());
        }
        String actualClass = StringUtils.hasText(classGroup.getActualClass())
                ? classGroup.getActualClass() : classGroup.getClassName();
        if (StringUtils.hasText(actualClass)) {
            parts.add(actualClass);
        }
        return parts.isEmpty() ? "the new class" : String.join(" / ", parts);
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

    private LessonOccurrenceEntity findClassScheduleConflict(LocalDateTime startAt,
                                                              LocalDateTime endAt,
                                                              java.util.function.Consumer<LambdaQueryWrapper<LessonOccurrenceEntity>> condition) {
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, endAt)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, startAt);
        condition.accept(wrapper);
        Long conflictCount = lessonOccurrenceMapper.selectCount(wrapper);
        if (conflictCount == null || conflictCount == 0) {
            return null;
        }
        wrapper.orderByAsc(LessonOccurrenceEntity::getPlannedStartAt).last("LIMIT 1");
        List<LessonOccurrenceEntity> conflicts = lessonOccurrenceMapper.selectList(wrapper);
        return conflicts == null || conflicts.isEmpty() ? null : conflicts.get(0);
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

    private LocalTime parseEndTime(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return ScheduleSlotTime.parseEndTime(text);
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
        String description = slots.stream()
                .sorted(Comparator.comparing(ClassGroupScheduleSlotEntity::getEffectiveStartDate,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ClassGroupScheduleSlotEntity::getWeekday)
                        .thenComparing(ClassGroupScheduleSlotEntity::getStartTime))
                .map(slot -> (slot.getEffectiveStartDate() == null ? ""
                        : slot.getEffectiveStartDate() + "–" + slot.getEffectiveEndDate() + " ")
                        + weekdayShortLabel(slot.getWeekday()) + " "
                        + slot.getStartTime().toString().substring(0, 5)
                        + "-"
                        + ScheduleSlotTime.formatEndTime(slot.getStartTime(), slot.getEndTime()))
                .collect(Collectors.joining("; "));
        return description.length() <= 255 ? description : description.substring(0, 252) + "...";
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
        item.put("groupProjectId", clz.getGroupProjectId());
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
                            int minutes = ScheduleSlotTime.durationMinutes(slot.getStartTime(), slot.getEndTime());
                            Map<String, Object> mapped = toSlotMap(slot.getTeacherId(), slot.getClassroomId(), slot.getDeliveryMode(), slot.getWeekday(),
                                    slot.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                                    ScheduleSlotTime.formatEndTime(slot.getStartTime(), slot.getEndTime()),
                                    minutes > 0 ? minutes / 30 : 0,
                                    slot.getSubjectName(), slot.getAlternateTeacherId(),
                                    slot.getAlternateSubjectName(), slot.getAlternateClassroomId(),
                                    slot.getAlternateDeliveryMode(), slot.getRotationAnchorDate());
                            mapped.put("effectiveStartDate", slot.getEffectiveStartDate());
                            mapped.put("effectiveEndDate", slot.getEffectiveEndDate());
                            mapped.put("slotLineageId", slot.getSlotLineageId() == null ? slot.getId() : slot.getSlotLineageId());
                            return mapped;
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
        return toSlotMap(teacherId, classroomId, deliveryMode, weekday, startTime, endTime, plannedUnits,
                null, null, null, null, null, null);
    }

    private Map<String, Object> toSlotMap(Long teacherId, Long classroomId, String deliveryMode, int weekday,
                                          String startTime, String endTime, int plannedUnits,
                                          String subjectName, Long alternateTeacherId,
                                          String alternateSubjectName, Long alternateClassroomId,
                                          String alternateDeliveryMode, LocalDate rotationAnchorDate) {
        Map<String, Object> m = new HashMap<>();
        m.put("teacherId", teacherId);
        m.put("subjectName", subjectName);
        m.put("alternateTeacherId", alternateTeacherId);
        m.put("alternateSubjectName", alternateSubjectName);
        m.put("alternateClassroomId", alternateClassroomId);
        m.put("alternateDeliveryMode", alternateDeliveryMode);
        m.put("rotationAnchorDate", rotationAnchorDate);
        m.put("classroomId", classroomId);
        m.put("deliveryMode", deliveryMode);
        m.put("weekday", weekday);
        m.put("startTime", startTime);
        m.put("endTime", endTime);
        m.put("plannedUnits", plannedUnits);
        return m;
    }

    private String optionalText(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return null;
        return text.trim();
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
