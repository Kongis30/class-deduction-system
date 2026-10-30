package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.dto.OneOnOneDeductionQuery;
import com.classdeduction.system.modules.schedule.vo.OneOnOnePackageVO;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** 在独立历史快照库重现课时差异；事务回滚，禁止连接生产数据库。 */
@EnabledIfEnvironmentVariable(named = "ANGELA_REHEARSAL_TEST", matches = "1")
@ActiveProfiles("group-course-v2")
@SpringBootTest(properties = {
        "spring.datasource.url=${ANGELA_REHEARSAL_URL}",
        "spring.datasource.username=${ANGELA_REHEARSAL_USERNAME}",
        "spring.datasource.password=${ANGELA_REHEARSAL_PASSWORD}",
        "app.database.required-name=jin_angela_repair_20261030",
        "app.schedule.renewal-initial-delay-ms=600000", "app.balance.sync-initial-delay-ms=600000"
})
@Transactional
class AngelaAllocationSnapshotReproTest {
    private static final long PACKAGE_ID = 2105336690043879425L;
    private static final long SLOT_ID = 2105452959699095554L;
    private static final long TEACHER_ID = 2102805856267108353L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlSessionTemplate sqlSession;
    @Autowired private LessonOccurrenceService lessonOccurrenceService;

    @Test
    void existingLessonTimelineAndPackageBalanceSurviveAllocationReconciliation() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("jin_angela_repair_20261030");
        assertThat(jdbc.update("UPDATE recurring_schedule_slot SET allocated_units = 8 WHERE id = ?", SLOT_ID))
                .isEqualTo(1);
        OneOnOnePackageVO before = packageView();
        assertThat(before.remainingUnits()).isEqualTo(10.0);
        assertThat(before.lessons()).hasSize(6);
        assertThat(before.teacherHours()).singleElement().satisfies(hours -> {
            assertThat(hours.allocatedUnits()).isEqualTo(10.0);
            assertThat(hours.deductedUnits()).isEqualTo(2.0);
            assertThat(hours.remainingUnits()).isEqualTo(8.0);
        });

        assertThat(jdbc.update("UPDATE recurring_schedule_slot SET allocated_units = 12 WHERE id = ?", SLOT_ID))
                .isEqualTo(1);
        sqlSession.clearCache();
        OneOnOnePackageVO after = packageView();
        assertThat(after.remainingUnits()).isEqualTo(10.0);
        assertThat(after.lessons()).isEqualTo(before.lessons());
        assertThat(after.teacherHours()).singleElement().satisfies(hours -> {
            assertThat(hours.allocatedUnits()).isEqualTo(12.0);
            assertThat(hours.deductedUnits()).isEqualTo(2.0);
            assertThat(hours.remainingUnits()).isEqualTo(10.0);
        });
    }

    private OneOnOnePackageVO packageView() {
        OneOnOneDeductionQuery query = new OneOnOneDeductionQuery();
        query.setPackageId(PACKAGE_ID);
        query.setTeacherId(TEACHER_ID);
        return lessonOccurrenceService.listOneOnOneDeductions(query, null).stream()
                .flatMap(teacher -> teacher.students().stream())
                .flatMap(student -> student.packages().stream())
                .filter(item -> item.packageId().equals(PACKAGE_ID))
                .findFirst().orElseThrow();
    }
}
