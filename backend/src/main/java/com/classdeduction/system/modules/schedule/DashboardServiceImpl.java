package com.classdeduction.system.modules.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTriggerRuleEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final LessonOccurrenceMapper occurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final CampusMapper campusMapper;
    private final CourseProductMapper courseProductMapper;
    private final NotificationTriggerRuleMapper notificationTriggerRuleMapper;

    @Override
    @Cacheable(value = "dashboard:summary", key = "'getSummary'")
    public Map<String, Object> getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        // 获取 LOW_BALANCE 阈值
        Integer lowBalanceThreshold = getLowBalanceThreshold();
        if (lowBalanceThreshold == null || lowBalanceThreshold <= 0) {
            lowBalanceThreshold = 4;
        }

        // 系统级统计
        long totalStudents = studentMapper.selectCount(new LambdaQueryWrapper<>());
        long totalTeachers = teacherMapper.selectCount(new LambdaQueryWrapper<>());
        long totalClassrooms = classroomMapper.selectCount(new LambdaQueryWrapper<>());
        long totalCampuses = campusMapper.selectCount(new LambdaQueryWrapper<>());
        long totalCourseProducts = courseProductMapper.selectCount(new LambdaQueryWrapper<>());

        // 今日已核销的 occurrence ID（仅限今日范围内的排课，避免全表扫描）
        List<Long> todayOccurrenceIds = occurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId)
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, todayStart)
                        .lt(LessonOccurrenceEntity::getPlannedEndAt, todayEnd)
                        .eq(LessonOccurrenceEntity::getPlanningStatus, "SCHEDULED")
        ).stream().map(LessonOccurrenceEntity::getId).toList();

        List<Long> recordedOccurrenceIds = todayOccurrenceIds.isEmpty() ? Collections.emptyList()
                : lessonRecordMapper.selectList(
                        new LambdaQueryWrapper<LessonRecordEntity>()
                                .select(LessonRecordEntity::getOccurrenceId)
                                .in(LessonRecordEntity::getOccurrenceId, todayOccurrenceIds)
                ).stream().map(LessonRecordEntity::getOccurrenceId).distinct().toList();

        long todayLessonsCount = todayOccurrenceIds.size() - recordedOccurrenceIds.size();

        // 系统级低余量课包
        long lowBalancePackages = coursePackageMapper.selectCount(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                        .le(CoursePackageEntity::getRemainingUnits, lowBalanceThreshold)
        );

        // ========= 各校区明细（批量GROUP BY查询，替代N+1）=========

        // 1. 批量查询学生/教师/教室的校区分布
        Map<Long, Long> campusStudentCount = toCampusCountMap(studentMapper.countGroupByCampus());
        Map<Long, Long> campusTeacherCount = toCampusCountMap(teacherMapper.countGroupByCampus());
        Map<Long, Long> campusClassroomCount = toCampusCountMap(classroomMapper.countGroupByCampus());

        // 2. 批量查询今日待上课数的校区分布（JOIN classroom推算，排除已核销记录）
        Map<Long, Long> campusTodayLessons = toCampusCountMap(
                occurrenceMapper.countTodayByCampus(todayStart, todayEnd,
                        recordedOccurrenceIds.isEmpty() ? null : recordedOccurrenceIds));

        // 3. 批量查询低余量课包的校区分布（JOIN student推算）
        Map<Long, Long> campusLowBalance = toCampusCountMap(
                coursePackageMapper.countLowBalanceByCampus(lowBalanceThreshold));

        // 4. 组装校区明细
        List<CampusEntity> campuses = campusMapper.selectList(new LambdaQueryWrapper<>());
        List<Map<String, Object>> campusDetails = new ArrayList<>();
        for (CampusEntity campus : campuses) {
            Long campusId = campus.getId();
            Map<String, Object> detail = new HashMap<>();
            detail.put("campusId", campusId);
            detail.put("campusName", campus.getCampusName());
            detail.put("students", campusStudentCount.getOrDefault(campusId, 0L));
            detail.put("teachers", campusTeacherCount.getOrDefault(campusId, 0L));
            detail.put("classrooms", campusClassroomCount.getOrDefault(campusId, 0L));
            detail.put("todayLessons", campusTodayLessons.getOrDefault(campusId, 0L));
            detail.put("lowBalancePackages", campusLowBalance.getOrDefault(campusId, 0L));
            campusDetails.add(detail);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalStudents", totalStudents);
        result.put("totalTeachers", totalTeachers);
        result.put("totalClassrooms", totalClassrooms);
        result.put("totalCampuses", totalCampuses);
        result.put("totalCourseProducts", totalCourseProducts);
        result.put("todayLessonsCount", todayLessonsCount);
        result.put("lowBalancePackages", lowBalancePackages);
        result.put("campusDetails", campusDetails);
        return result;
    }

    private Integer getLowBalanceThreshold() {
        NotificationTriggerRuleEntity rule = notificationTriggerRuleMapper.selectOne(
                new LambdaQueryWrapper<NotificationTriggerRuleEntity>()
                        .eq(NotificationTriggerRuleEntity::getTriggerType, "LOW_BALANCE")
                        .eq(NotificationTriggerRuleEntity::getEnabled, 1)
                        .eq(NotificationTriggerRuleEntity::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
        return rule != null ? rule.getThresholdValue() : null;
    }

    /**
     * 将 GROUP BY 查询结果（List&lt;Map&lt;String, Object&gt;&gt;）转为 Map&lt;campusId, count&gt;。
     * 各 mapper 的 countGroupBy / countTodayByCampus / countLowBalanceByCampus
     * 均返回包含 "campus_id" 和 "cnt" 键的 Map。
     */
    private Map<Long, Long> toCampusCountMap(List<Map<String, Object>> rows) {
        Map<Long, Long> result = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object campusIdObj = row.get("campus_id");
                Object cntObj = row.get("cnt");
                if (campusIdObj != null && cntObj != null) {
                    result.put(((Number) campusIdObj).longValue(), ((Number) cntObj).longValue());
                }
            }
        }
        return result;
    }
}
