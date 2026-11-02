package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.TeacherCalendarRosterService;
import com.classdeduction.system.modules.schedule.vo.TeacherCalendarCategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 汇总老师当前关联的班课产品和 1-on-1 授课方式，供 Teacher Calendar 左侧分类使用。
 * 调用链：TeacherCalendarRosterController -> 本服务 -> 课程、课包及排课关系表。
 */
@Service
@RequiredArgsConstructor
public class TeacherCalendarRosterServiceImpl implements TeacherCalendarRosterService {

    private static final List<String> VISIBLE_RULE_STATUSES = List.of("ACTIVE", "PAUSED");

    private final ClassGroupMapper classGroupMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final CourseProductMapper courseProductMapper;

    @Override
    public List<TeacherCalendarCategoryVO> listCategories() {
        List<ClassGroupEntity> classes = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getStatus, "ACTIVE")
                .isNotNull(ClassGroupEntity::getProductId));
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .in(RecurringScheduleRuleEntity::getRuleStatus, VISIBLE_RULE_STATUSES));
        List<PackageTeacherAssignmentEntity> assignments = packageTeacherAssignmentMapper.selectList(
                new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                        .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE"));

        List<Long> classIds = classes.stream().map(ClassGroupEntity::getId).toList();
        List<ClassGroupScheduleSlotEntity> classSlots = classIds.isEmpty()
                ? List.of()
                : classGroupScheduleSlotMapper.selectList(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .in(ClassGroupScheduleSlotEntity::getClassGroupId, classIds));

        List<Long> ruleIds = rules.stream().map(RecurringScheduleRuleEntity::getId).toList();
        List<RecurringScheduleSlotEntity> privateSlots = ruleIds.isEmpty()
                ? List.of()
                : recurringScheduleSlotMapper.selectList(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .in(RecurringScheduleSlotEntity::getRuleId, ruleIds)
                        .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE"));

        Map<Long, List<Long>> groupPackages = loadActiveGroupPackages(rules);
        Set<Long> packageIds = new HashSet<>();
        assignments.stream().map(PackageTeacherAssignmentEntity::getPackageId)
                .filter(Objects::nonNull).forEach(packageIds::add);
        rules.stream().map(RecurringScheduleRuleEntity::getPackageId)
                .filter(Objects::nonNull).forEach(packageIds::add);
        groupPackages.values().forEach(packageIds::addAll);
        Map<Long, CoursePackageEntity> packages = packageIds.isEmpty()
                ? Map.of()
                : coursePackageMapper.selectBatchIds(packageIds).stream()
                        .collect(Collectors.toMap(CoursePackageEntity::getId, item -> item));

        Set<Long> productIds = new HashSet<>();
        classes.stream().map(ClassGroupEntity::getProductId).filter(Objects::nonNull).forEach(productIds::add);
        packages.values().stream().map(CoursePackageEntity::getCourseProductId)
                .filter(Objects::nonNull).forEach(productIds::add);
        Map<Long, CourseProductEntity> products = productIds.isEmpty()
                ? Map.of()
                : courseProductMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, item -> item));

        Set<TeacherCalendarCategoryVO> categories = new LinkedHashSet<>();
        Map<Long, Set<Long>> scheduledTeacherPackages = new HashMap<>();
        addGroupClassCategories(classes, classSlots, products, categories);
        addPrivateScheduleCategories(rules, privateSlots, groupPackages, packages, products,
                categories, scheduledTeacherPackages);
        addPackageTeacherCategories(assignments, packages, products, categories, scheduledTeacherPackages);
        return List.copyOf(categories);
    }

    private Map<Long, List<Long>> loadActiveGroupPackages(List<RecurringScheduleRuleEntity> rules) {
        List<Long> groupIds = rules.stream().map(RecurringScheduleRuleEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull).distinct().toList();
        if (groupIds.isEmpty()) return Map.of();
        return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupIds)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE"))
                .stream()
                .filter(member -> member.getPrivateLessonGroupId() != null && member.getPackageId() != null)
                .collect(Collectors.groupingBy(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId,
                        Collectors.mapping(PrivateLessonGroupMemberEntity::getPackageId, Collectors.toList())));
    }

    private void addGroupClassCategories(List<ClassGroupEntity> classes,
                                         List<ClassGroupScheduleSlotEntity> slots,
                                         Map<Long, CourseProductEntity> products,
                                         Set<TeacherCalendarCategoryVO> categories) {
        Map<Long, ClassGroupEntity> classById = classes.stream()
                .collect(Collectors.toMap(ClassGroupEntity::getId, item -> item));
        for (ClassGroupEntity classGroup : classes) {
            addCategory(categories, classGroup.getTeacherId(), products.get(classGroup.getProductId()), null, classGroup.getClassroomId());
        }
        for (ClassGroupScheduleSlotEntity slot : slots) {
            ClassGroupEntity classGroup = classById.get(slot.getClassGroupId());
            if (classGroup == null) continue;
            CourseProductEntity product = products.get(classGroup.getProductId());
            addCategory(categories, slot.getTeacherId(), product, slot.getDeliveryMode(), slot.getClassroomId());
            addCategory(categories, slot.getAlternateTeacherId(), product,
                    slot.getAlternateDeliveryMode(), slot.getAlternateClassroomId());
        }
    }

    private void addPrivateScheduleCategories(List<RecurringScheduleRuleEntity> rules,
                                              List<RecurringScheduleSlotEntity> slots,
                                              Map<Long, List<Long>> groupPackages,
                                              Map<Long, CoursePackageEntity> packages,
                                              Map<Long, CourseProductEntity> products,
                                              Set<TeacherCalendarCategoryVO> categories,
                                              Map<Long, Set<Long>> scheduledTeacherPackages) {
        Map<Long, List<RecurringScheduleSlotEntity>> slotsByRule = slots.stream()
                .filter(slot -> slot.getRuleId() != null)
                .collect(Collectors.groupingBy(RecurringScheduleSlotEntity::getRuleId));
        for (RecurringScheduleRuleEntity rule : rules) {
            List<Long> linkedPackageIds = rule.getPackageId() != null
                    ? List.of(rule.getPackageId())
                    : groupPackages.getOrDefault(rule.getPrivateLessonGroupId(), List.of());
            List<RecurringScheduleSlotEntity> ruleSlots = slotsByRule.getOrDefault(rule.getId(), List.of());
            for (Long packageId : linkedPackageIds) {
                CoursePackageEntity coursePackage = packages.get(packageId);
                CourseProductEntity product = coursePackage == null ? null : products.get(coursePackage.getCourseProductId());
                if (!isOneOnOne(product)) continue;
                boolean hasSlotTeacher = false;
                for (RecurringScheduleSlotEntity slot : ruleSlots) {
                    if (slot.getTeacherId() == null) continue;
                    hasSlotTeacher = true;
                    markScheduled(scheduledTeacherPackages, slot.getTeacherId(), packageId);
                    Long classroomId = slot.getClassroomId() != null
                            ? slot.getClassroomId() : rule.getDefaultClassroomId();
                    addCategory(categories, slot.getTeacherId(), product, slot.getDeliveryMode(), classroomId);
                }
                if (!hasSlotTeacher) {
                    markScheduled(scheduledTeacherPackages, rule.getPrimaryTeacherId(), packageId);
                    addCategory(categories, rule.getPrimaryTeacherId(), product, null, rule.getDefaultClassroomId());
                }
            }
        }
    }

    private void addPackageTeacherCategories(List<PackageTeacherAssignmentEntity> assignments,
                                             Map<Long, CoursePackageEntity> packages,
                                             Map<Long, CourseProductEntity> products,
                                             Set<TeacherCalendarCategoryVO> categories,
                                             Map<Long, Set<Long>> scheduledTeacherPackages) {
        for (PackageTeacherAssignmentEntity assignment : assignments) {
            CoursePackageEntity coursePackage = packages.get(assignment.getPackageId());
            CourseProductEntity product = coursePackage == null ? null : products.get(coursePackage.getCourseProductId());
            if (!isOneOnOne(product)) continue;
            boolean hasSchedule = scheduledTeacherPackages.getOrDefault(assignment.getTeacherId(), Set.of())
                    .contains(assignment.getPackageId());
            if (!hasSchedule) addCategory(categories, assignment.getTeacherId(), product, null, null);
        }
    }

    private void addCategory(Set<TeacherCalendarCategoryVO> categories,
                              Long teacherId,
                              CourseProductEntity product,
                              String deliveryMode,
                              Long classroomId) {
        if (teacherId == null || product == null) return;
        if (isOneOnOne(product)) {
            boolean online = "ONLINE".equalsIgnoreCase(deliveryMode);
            boolean inPerson = "IN_PERSON".equalsIgnoreCase(deliveryMode) || classroomId != null;
            String key = online ? "ONE_ON_ONE:ONLINE" : inPerson ? "ONE_ON_ONE:IN_PERSON" : "ONE_ON_ONE:UNSPECIFIED";
            String label = online ? "Online 1-on-1" : inPerson ? "In-person 1-on-1" : "1-on-1 · Mode not set";
            categories.add(new TeacherCalendarCategoryVO(teacherId, key, label,
                    product.getId(), product.getProductName()));
            return;
        }
        String productName = StringUtils.hasText(product.getProductName())
                ? product.getProductName().trim()
                : StringUtils.hasText(product.getCourseType()) ? product.getCourseType().trim() : "Other group class";
        categories.add(new TeacherCalendarCategoryVO(teacherId, "PRODUCT:" + product.getId(), productName,
                product.getId(), productName));
    }

    private boolean isOneOnOne(CourseProductEntity product) {
        return product != null && "ONE_ON_ONE".equalsIgnoreCase(product.getTeachingMode());
    }

    private void markScheduled(Map<Long, Set<Long>> scheduledTeacherPackages, Long teacherId, Long packageId) {
        if (teacherId == null || packageId == null) return;
        scheduledTeacherPackages.computeIfAbsent(teacherId, ignored -> new HashSet<>()).add(packageId);
    }
}
