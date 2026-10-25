package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.GroupCourseProjectSaveRequest;
import com.classdeduction.system.modules.base.dto.GroupRegistrationPlanSaveRequest;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.base.entity.GroupRegistrationPlanEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.base.mapper.GroupRegistrationPlanMapper;
import com.classdeduction.system.modules.base.service.GroupCourseModelService;
import com.classdeduction.system.modules.base.vo.GroupCourseProductSetupVO;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import com.classdeduction.system.modules.schedule.util.ClassScheduleDates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 班课目录与报名方案的唯一维护入口；Prices、Registration 和迁移工具共用此模型。
 */
@Service
@RequiredArgsConstructor
public class GroupCourseModelServiceImpl implements GroupCourseModelService {

    private static final String ACTIVE = "ACTIVE";
    private final CourseProductMapper courseProductMapper;
    private final CampusMapper campusMapper;
    private final GroupCourseProjectMapper projectMapper;
    private final GroupRegistrationPlanMapper planMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassGroupScheduleSlotMapper scheduleSlotMapper;
    private final EnrollmentOrderItemMapper orderItemMapper;
    private final ClassHolidayMapper holidayMapper;

    @Override
    @Transactional(readOnly = true)
    public GroupCourseProductSetupVO getProductSetup(Long productId, boolean activeOnly) {
        CourseProductEntity product = requireGroupProduct(productId);
        LambdaQueryWrapper<GroupCourseProjectEntity> projectQuery = new LambdaQueryWrapper<GroupCourseProjectEntity>()
                .eq(GroupCourseProjectEntity::getCourseProductId, productId)
                .eq(GroupCourseProjectEntity::getIsDeleted, 0)
                .orderByAsc(GroupCourseProjectEntity::getDisplayOrder)
                .orderByAsc(GroupCourseProjectEntity::getProjectName);
        if (activeOnly) projectQuery.eq(GroupCourseProjectEntity::getStatus, ACTIVE);
        List<GroupCourseProductSetupVO.Project> projects = projectMapper.selectList(projectQuery).stream()
                .map(project -> toProject(project, activeOnly))
                .toList();
        return new GroupCourseProductSetupVO(product.getId(), product.getProductName(), projects);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> getScheduledClassDates(Long classId, LocalDate requestedStart, LocalDate requestedEnd) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null || !Integer.valueOf(0).equals(classGroup.getIsDeleted())
                || !ACTIVE.equals(classGroup.getStatus()) || classGroup.getGroupProjectId() == null) {
            throw new BusinessException("GROUP_CLASS_NOT_FOUND", "Active project class not found.");
        }
        LocalDate classStart = classStartDate(classGroup);
        LocalDate classEnd = classEndDate(classGroup);
        LocalDate start = requestedStart == null ? classStart : requestedStart;
        LocalDate end = requestedEnd == null ? classEnd : requestedEnd;
        if (start == null && end != null) start = end.minusYears(3);
        if (end == null && start != null) end = start.plusYears(3);
        if (start == null || end == null || end.isBefore(start)
                || end.isAfter(start.plusYears(5))) {
            throw new BusinessException("GROUP_CLASS_DATE_RANGE_INVALID", "Choose a valid class date range of five years or less.");
        }
        if (classStart != null && start.isBefore(classStart)) start = classStart;
        if (classEnd != null && end.isAfter(classEnd)) end = classEnd;
        if (end.isBefore(start)) return List.of();
        List<ClassGroupScheduleSlotEntity> slots = scheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId)
                        .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
        List<ClassHolidayEntity> holidays = holidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getGroupProjectId, classGroup.getGroupProjectId())
                .eq(ClassHolidayEntity::getIsDeleted, 0)
                .le(ClassHolidayEntity::getStartDate, end)
                .ge(ClassHolidayEntity::getEndDate, start));
        return ClassScheduleDates.scheduledDays(start, end,
                ClassScheduleDates.resolveBaseEndDate(classGroup.getScheduleBaseEndDate(), classEnd),
                slots, date -> holidays.stream()
                .anyMatch(holiday -> !date.isBefore(holiday.getStartDate()) && !date.isAfter(holiday.getEndDate())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(Long productId, GroupCourseProjectSaveRequest request) {
        requireGroupProductForUpdate(productId);
        validateProjectCodeUnique(productId, request.getProjectCode(), null);
        GroupCourseProjectEntity project = new GroupCourseProjectEntity();
        project.setCourseProductId(productId);
        applyProject(project, request);
        projectMapper.insert(project);
        return project.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(Long projectId, GroupCourseProjectSaveRequest request) {
        GroupCourseProjectEntity project = requireProjectForUpdate(projectId);
        validateProjectCodeUnique(project.getCourseProductId(), request.getProjectCode(), projectId);
        applyProject(project, request);
        projectMapper.updateById(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long projectId) {
        GroupCourseProjectEntity project = requireProjectForUpdate(projectId);
        boolean hasOrderHistory = orderItemMapper.selectCount(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .eq(EnrollmentOrderItemEntity::getGroupProjectId, projectId)
                        .eq(EnrollmentOrderItemEntity::getIsDeleted, 0)) > 0;
        boolean referenced = planMapper.selectCount(new LambdaQueryWrapper<GroupRegistrationPlanEntity>()
                .eq(GroupRegistrationPlanEntity::getProjectId, projectId).eq(GroupRegistrationPlanEntity::getIsDeleted, 0)) > 0
                || hasOrderHistory
                || classGroupMapper.selectCount(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getGroupProjectId, projectId).eq(ClassGroupEntity::getIsDeleted, 0)) > 0
                || holidayMapper.selectCount(new LambdaQueryWrapper<com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity>()
                .eq(com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity::getGroupProjectId, projectId)
                .eq(com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity::getIsDeleted, 0)) > 0;
        if (referenced) {
            project.setStatus("INACTIVE");
            projectMapper.updateById(project);
            return;
        }
        projectMapper.deleteById(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPlan(Long projectId, GroupRegistrationPlanSaveRequest request) {
        requireProjectForUpdate(projectId);
        validatePlan(request);
        validatePlanCodeUnique(projectId, request.getPlanCode(), null);
        GroupRegistrationPlanEntity plan = new GroupRegistrationPlanEntity();
        plan.setProjectId(projectId);
        applyPlan(plan, request);
        planMapper.insert(plan);
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(Long planId, GroupRegistrationPlanSaveRequest request) {
        GroupRegistrationPlanEntity plan = requirePlanForUpdate(planId);
        validatePlan(request);
        validatePlanCodeUnique(plan.getProjectId(), request.getPlanCode(), planId);
        applyPlan(plan, request);
        planMapper.updateById(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long planId) {
        GroupRegistrationPlanEntity plan = requirePlanForUpdate(planId);
        long references = orderItemMapper.selectCount(new LambdaQueryWrapper<com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity>()
                .eq(com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity::getGroupRegistrationPlanId, planId)
                .eq(com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity::getIsDeleted, 0));
        if (references > 0) {
            plan.setStatus("INACTIVE");
            planMapper.updateById(plan);
            return;
        }
        planMapper.deleteById(planId);
    }

    private GroupCourseProductSetupVO.Project toProject(GroupCourseProjectEntity project, boolean activeOnly) {
        LambdaQueryWrapper<GroupRegistrationPlanEntity> planQuery = new LambdaQueryWrapper<GroupRegistrationPlanEntity>()
                .eq(GroupRegistrationPlanEntity::getProjectId, project.getId())
                .eq(GroupRegistrationPlanEntity::getIsDeleted, 0)
                .orderByAsc(GroupRegistrationPlanEntity::getDisplayOrder)
                .orderByAsc(GroupRegistrationPlanEntity::getPlanName);
        if (activeOnly) planQuery.eq(GroupRegistrationPlanEntity::getStatus, ACTIVE);
        List<GroupCourseProductSetupVO.Plan> plans = planMapper.selectList(planQuery).stream()
                .map(this::toPlan)
                .toList();
        List<ClassGroupEntity> classEntities = classGroupMapper.selectList(
                        new LambdaQueryWrapper<ClassGroupEntity>()
                                .eq(ClassGroupEntity::getGroupProjectId, project.getId())
                                .eq(ClassGroupEntity::getIsDeleted, 0)
                                .orderByAsc(ClassGroupEntity::getCampusId)
                                .orderByAsc(ClassGroupEntity::getActualClass))
                .stream()
                .filter(group -> !activeOnly || ACTIVE.equals(group.getStatus()))
                .toList();
        Set<Long> campusIds = classEntities.stream().map(ClassGroupEntity::getCampusId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CampusEntity> campusById = campusIds.isEmpty() ? Map.of()
                : campusMapper.selectBatchIds(campusIds).stream().collect(Collectors.toMap(CampusEntity::getId, item -> item));
        List<GroupCourseProductSetupVO.ActualClass> classes = classEntities.stream()
                .map(group -> {
                    CampusEntity campus = group.getCampusId() == null ? null : campusById.get(group.getCampusId());
                    return new GroupCourseProductSetupVO.ActualClass(group.getId(), group.getClassName(),
                            group.getActualClass(), group.getCampusId(), campus == null ? null : campus.getCampusName(),
                            classStartDate(group), classEndDate(group), group.getStatus(),
                            group.getScheduleDesc() != null && !group.getScheduleDesc().isBlank());
                }).toList();
        return new GroupCourseProductSetupVO.Project(project.getId(), project.getProjectCode(), project.getProjectName(),
                project.getDescription(), project.getStatus(), project.getDisplayOrder(), plans, classes);
    }

    private GroupCourseProductSetupVO.Plan toPlan(GroupRegistrationPlanEntity plan) {
        Long references = orderItemMapper.selectCount(new LambdaQueryWrapper<com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity>()
                .eq(com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity::getGroupRegistrationPlanId, plan.getId())
                .eq(com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity::getIsDeleted, 0));
        return new GroupCourseProductSetupVO.Plan(plan.getId(), plan.getPlanCode(), plan.getPlanName(),
                plan.getCoverageType(), plan.getTermCode(), plan.getCoverageStartDate(), plan.getCoverageEndDate(),
                plan.getPackageUnit(), plan.getPackageQuantity(), plan.getFullDayPrice(), plan.getHalfDayPrice(),
                plan.getCurrencyCode(), plan.getStatus(), plan.getDisplayOrder(), references);
    }

    private void applyProject(GroupCourseProjectEntity project, GroupCourseProjectSaveRequest request) {
        project.setProjectCode(request.getProjectCode().trim());
        project.setProjectName(request.getProjectName().trim());
        project.setDescription(clean(request.getDescription()));
        project.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        project.setStatus(status(request.getStatus()));
    }

    private void applyPlan(GroupRegistrationPlanEntity plan, GroupRegistrationPlanSaveRequest request) {
        plan.setPlanCode(request.getPlanCode().trim());
        plan.setPlanName(request.getPlanName().trim());
        plan.setCoverageType(request.getCoverageType().trim().toUpperCase(Locale.ROOT));
        plan.setTermCode(clean(request.getTermCode()));
        plan.setCoverageStartDate(request.getCoverageStartDate());
        plan.setCoverageEndDate(request.getCoverageEndDate());
        plan.setPackageUnit(request.getPackageUnit().trim().toUpperCase(Locale.ROOT));
        plan.setPackageQuantity(request.getPackageQuantity());
        plan.setFullDayPrice(request.getFullDayPrice().setScale(2, RoundingMode.HALF_UP));
        plan.setHalfDayPrice(request.getHalfDayPrice() == null ? null : request.getHalfDayPrice().setScale(2, RoundingMode.HALF_UP));
        plan.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode().trim().toUpperCase(Locale.ROOT) : "USD");
        plan.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        plan.setStatus(status(request.getStatus()));
    }

    private void validatePlan(GroupRegistrationPlanSaveRequest request) {
        String type = request.getCoverageType().trim().toUpperCase(Locale.ROOT);
        String unit = request.getPackageUnit().trim().toUpperCase(Locale.ROOT);
        if (!List.of("FIXED_DATES", "ROLLING_MONTHS", "SESSION_COUNT").contains(type)) {
            throw new BusinessException("GROUP_PLAN_COVERAGE_INVALID", "Choose a supported class registration period.");
        }
        if (!List.of("SESSION", "MONTH").contains(unit)) {
            throw new BusinessException("GROUP_PLAN_UNIT_INVALID", "Class plans support session or month units.");
        }
        if ("FIXED_DATES".equals(type)
                && (request.getCoverageStartDate() == null || request.getCoverageEndDate() == null
                    || request.getCoverageEndDate().isBefore(request.getCoverageStartDate()) || !"SESSION".equals(unit))) {
            throw new BusinessException("GROUP_PLAN_DATES_INVALID", "A fixed-date plan needs a valid date range and session unit.");
        }
        if ("ROLLING_MONTHS".equals(type) && !"MONTH".equals(unit)) {
            throw new BusinessException("GROUP_PLAN_UNIT_INVALID", "A rolling plan must use months.");
        }
        if ("SESSION_COUNT".equals(type) && !"SESSION".equals(unit)) {
            throw new BusinessException("GROUP_PLAN_UNIT_INVALID", "A session-count plan must use sessions.");
        }
        if (request.getPackageQuantity().stripTrailingZeros().scale() > 0
                || request.getFullDayPrice().stripTrailingZeros().scale() > 0
                || (request.getHalfDayPrice() != null && request.getHalfDayPrice().stripTrailingZeros().scale() > 0)) {
            throw new BusinessException("WHOLE_DOLLAR_REQUIRED", "Class plan quantities and prices must be whole numbers.");
        }
    }

    private CourseProductEntity requireGroupProduct(Long id) {
        CourseProductEntity product = courseProductMapper.selectById(id);
        if (product == null || !"GROUP".equals(product.getTeachingMode())) {
            throw new BusinessException("GROUP_PRODUCT_NOT_FOUND", "Group-class product not found.");
        }
        return product;
    }

    /** 新建项目与产品模式切换使用同一行锁，避免并发写出挂在 1-on-1 产品下的班课项目。 */
    private CourseProductEntity requireGroupProductForUpdate(Long id) {
        CourseProductEntity product = courseProductMapper.selectByIdForUpdate(id);
        if (product == null || !"GROUP".equals(product.getTeachingMode())) {
            throw new BusinessException("GROUP_PRODUCT_NOT_FOUND", "Group-class product not found.");
        }
        return product;
    }

    private GroupCourseProjectEntity requireProject(Long id) {
        GroupCourseProjectEntity project = projectMapper.selectById(id);
        if (project == null) throw new BusinessException("GROUP_PROJECT_NOT_FOUND", "Teaching project not found.");
        return project;
    }

    /** 项目修改与报名共用行锁；锁定读取本身不会先建立旧 RR 快照。 */
    private GroupCourseProjectEntity requireProjectForUpdate(Long id) {
        GroupCourseProjectEntity project = projectMapper.selectByIdForUpdate(id);
        if (project == null) throw new BusinessException("GROUP_PROJECT_NOT_FOUND", "Teaching project not found.");
        return project;
    }

    private GroupRegistrationPlanEntity requirePlan(Long id) {
        GroupRegistrationPlanEntity plan = planMapper.selectById(id);
        if (plan == null) throw new BusinessException("GROUP_PLAN_NOT_FOUND", "Registration plan not found.");
        return plan;
    }

    /** 方案修改与报名共用行锁，防止更改进行中的课包定义。 */
    private GroupRegistrationPlanEntity requirePlanForUpdate(Long id) {
        GroupRegistrationPlanEntity plan = planMapper.selectByIdForUpdate(id);
        if (plan == null) throw new BusinessException("GROUP_PLAN_NOT_FOUND", "Registration plan not found.");
        return plan;
    }

    private void validateProjectCodeUnique(Long productId, String code, Long exceptId) {
        LambdaQueryWrapper<GroupCourseProjectEntity> query = new LambdaQueryWrapper<GroupCourseProjectEntity>()
                .eq(GroupCourseProjectEntity::getCourseProductId, productId)
                .eq(GroupCourseProjectEntity::getProjectCode, code.trim())
                .eq(GroupCourseProjectEntity::getIsDeleted, 0)
                .ne(exceptId != null, GroupCourseProjectEntity::getId, exceptId);
        if (projectMapper.selectCount(query) > 0) throw new BusinessException("GROUP_PROJECT_CODE_EXISTS", "Project code already exists.");
    }

    private void validatePlanCodeUnique(Long projectId, String code, Long exceptId) {
        LambdaQueryWrapper<GroupRegistrationPlanEntity> query = new LambdaQueryWrapper<GroupRegistrationPlanEntity>()
                .eq(GroupRegistrationPlanEntity::getProjectId, projectId)
                .eq(GroupRegistrationPlanEntity::getPlanCode, code.trim())
                .eq(GroupRegistrationPlanEntity::getIsDeleted, 0)
                .ne(exceptId != null, GroupRegistrationPlanEntity::getId, exceptId);
        if (planMapper.selectCount(query) > 0) throw new BusinessException("GROUP_PLAN_CODE_EXISTS", "Plan code already exists.");
    }

    private String status(String value) {
        return StringUtils.hasText(value) && "INACTIVE".equalsIgnoreCase(value.trim()) ? "INACTIVE" : ACTIVE;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDate classStartDate(ClassGroupEntity group) {
        return group.getStartDate() != null ? group.getStartDate() : group.getStartMonth();
    }

    private LocalDate classEndDate(ClassGroupEntity group) {
        if (group.getEndDate() != null) return group.getEndDate();
        return group.getEndMonth() == null ? null : group.getEndMonth().withDayOfMonth(group.getEndMonth().lengthOfMonth());
    }
}
