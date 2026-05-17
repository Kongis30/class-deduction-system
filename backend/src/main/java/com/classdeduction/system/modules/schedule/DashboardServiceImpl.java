package com.classdeduction.system.modules.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

        // 今日所有课程（已核销的排除）
        List<Long> recordedOccurrenceIds = lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .select(LessonRecordEntity::getOccurrenceId)
        ).stream().map(LessonRecordEntity::getOccurrenceId).toList();

        long todayLessonsCount = occurrenceMapper.selectCount(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, todayStart)
                        .lt(LessonOccurrenceEntity::getPlannedEndAt, todayEnd)
                        .eq(LessonOccurrenceEntity::getPlanningStatus, "SCHEDULED")
                        .notIn(!recordedOccurrenceIds.isEmpty(), LessonOccurrenceEntity::getId, recordedOccurrenceIds)
        );

        // 系统级低余量课包
        long lowBalancePackages = coursePackageMapper.selectCount(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                        .le(CoursePackageEntity::getRemainingUnits, lowBalanceThreshold)
        );

        // 各校区明细
        List<CampusEntity> campuses = campusMapper.selectList(new LambdaQueryWrapper<>());
        List<Map<String, Object>> campusDetails = new ArrayList<>();

        for (CampusEntity campus : campuses) {
            Long campusId = campus.getId();

            // 学生数
            long campusStudents = studentMapper.selectCount(
                    new LambdaQueryWrapper<StudentEntity>().eq(StudentEntity::getCampusId, campusId)
            );
            // 教师数
            long campusTeachers = teacherMapper.selectCount(
                    new LambdaQueryWrapper<TeacherEntity>().eq(TeacherEntity::getCampusId, campusId)
            );
            // 教室数
            long campusClassrooms = classroomMapper.selectCount(
                    new LambdaQueryWrapper<ClassroomEntity>().eq(ClassroomEntity::getCampusId, campusId)
            );

            // 今日课数（通过教室归属推算）
            List<Long> classroomIds = classroomMapper.selectList(
                    new LambdaQueryWrapper<ClassroomEntity>()
                            .select(ClassroomEntity::getId)
                            .eq(ClassroomEntity::getCampusId, campusId)
            ).stream().map(ClassroomEntity::getId).toList();

            long campusTodayLessons = 0;
            if (!classroomIds.isEmpty()) {
                campusTodayLessons = occurrenceMapper.selectCount(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .ge(LessonOccurrenceEntity::getPlannedStartAt, todayStart)
                                .lt(LessonOccurrenceEntity::getPlannedEndAt, todayEnd)
                                .eq(LessonOccurrenceEntity::getPlanningStatus, "SCHEDULED")
                                .in(LessonOccurrenceEntity::getPlannedClassroomId, classroomIds)
                                .notIn(!recordedOccurrenceIds.isEmpty(), LessonOccurrenceEntity::getId, recordedOccurrenceIds)
                );
            }

            // 低余量课包（通过学生归属推算）
            List<Long> studentIds = studentMapper.selectList(
                    new LambdaQueryWrapper<StudentEntity>()
                            .select(StudentEntity::getId)
                            .eq(StudentEntity::getCampusId, campusId)
            ).stream().map(StudentEntity::getId).toList();

            long campusLowBalance = 0;
            if (!studentIds.isEmpty()) {
                campusLowBalance = coursePackageMapper.selectCount(
                        new LambdaQueryWrapper<CoursePackageEntity>()
                                .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                                .le(CoursePackageEntity::getRemainingUnits, lowBalanceThreshold)
                                .in(CoursePackageEntity::getStudentId, studentIds)
                );
            }

            Map<String, Object> detail = new HashMap<>();
            detail.put("campusId", campusId);
            detail.put("campusName", campus.getCampusName());
            detail.put("students", campusStudents);
            detail.put("teachers", campusTeachers);
            detail.put("classrooms", campusClassrooms);
            detail.put("todayLessons", campusTodayLessons);
            detail.put("lowBalancePackages", campusLowBalance);
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
}
