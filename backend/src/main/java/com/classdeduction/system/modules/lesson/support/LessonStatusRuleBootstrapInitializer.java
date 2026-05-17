package com.classdeduction.system.modules.lesson.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动初始化器：确保 lesson_status_rule 表存在所有核心状态码的规则记录。
 * <p>
 * 如果某条核心规则不存在，则按预设的业务默认值自动插入。
 * 已存在的规则不会被覆盖，保留管理员的自定义配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonStatusRuleBootstrapInitializer implements ApplicationRunner {

    private final LessonStatusRuleMapper lessonStatusRuleMapper;

    /** 核心状态码及其默认配置 */
    private static final RuleSeed[] CORE_RULES = {
            new RuleSeed("COMPLETED",         "已完成",         1, "ACTUAL_UNITS",  0, 1, "正常完成课程"),
            new RuleSeed("FORMAL_LEAVE",      "请假",           0, "NONE",          1, 0, "正式请假，不扣课时，顺延一节"),
            new RuleSeed("TEMP_CANCEL",       "临时取消",       0, "NONE",          0, 1, "临时取消"),
            new RuleSeed("NO_SHOW",           "翘课",           1, "PLANNED_UNITS", 0, 1, "学生缺席，按计划扣课时"),
            new RuleSeed("TEACHER_CANCEL",    "老师取消",       0, "NONE",          0, 1, "老师取消课程"),
            new RuleSeed("MAKEUP_COMPLETED",  "补课完成",       1, "ACTUAL_UNITS",  0, 1, "补课完成"),
            new RuleSeed("SUBSTITUTED",       "代课完成",       1, "ACTUAL_UNITS",  0, 1, "代课完成"),
            new RuleSeed("CANCELLED",         "取消",           0, "NONE",          1, 0, "课程取消，不扣课时，顺延一节"),
    };

    @Override
    public void run(ApplicationArguments args) {
        for (RuleSeed seed : CORE_RULES) {
            ensureRule(seed);
        }
    }

    private void ensureRule(RuleSeed seed) {
        Long count = lessonStatusRuleMapper.selectCount(new LambdaQueryWrapper<LessonStatusRuleEntity>()
                .eq(LessonStatusRuleEntity::getStatusCode, seed.statusCode)
                .eq(LessonStatusRuleEntity::getIsDeleted, 0));
        if (count != null && count > 0) {
            return; // 已存在，保留管理员的自定义配置
        }

        LessonStatusRuleEntity entity = new LessonStatusRuleEntity();
        entity.setStatusCode(seed.statusCode);
        entity.setStatusName(seed.statusName);
        entity.setDefaultShouldDeduct(seed.defaultShouldDeduct);
        entity.setDeductMode(seed.deductMode);
        entity.setDefaultShouldExtend(seed.defaultShouldExtend);
        entity.setAllowManualOverride(seed.allowManualOverride);
        entity.setStatus("ACTIVE");
        entity.setRemark(seed.remark);
        lessonStatusRuleMapper.insert(entity);
        log.info("Bootstrapped lesson status rule: {} ({})", seed.statusCode, seed.statusName);
    }

    private record RuleSeed(
            String statusCode,
            String statusName,
            Integer defaultShouldDeduct,
            String deductMode,
            Integer defaultShouldExtend,
            Integer allowManualOverride,
            String remark
    ) {}
}
