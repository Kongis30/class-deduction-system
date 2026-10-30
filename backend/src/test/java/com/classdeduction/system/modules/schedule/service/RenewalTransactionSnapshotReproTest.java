package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/** 在独立快照库验证自动续排失败后数据库事务回滚。 */
@EnabledIfEnvironmentVariable(named = "ANGELA_REHEARSAL_TEST", matches = "1")
@ActiveProfiles("group-course-v2")
@SpringBootTest(properties = {
        "spring.datasource.url=${ANGELA_REHEARSAL_URL}",
        "spring.datasource.username=${ANGELA_REHEARSAL_USERNAME}",
        "spring.datasource.password=${ANGELA_REHEARSAL_PASSWORD}",
        "app.database.required-name=jin_angela_repair_20261030",
        "app.schedule.renewal-initial-delay-ms=600000", "app.balance.sync-initial-delay-ms=600000"
})
class RenewalTransactionSnapshotReproTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecurringScheduleRuleService service;
    @MockitoSpyBean private RecurringScheduleRuleMapper ruleMapper;
    @MockitoSpyBean private CoursePackageMapper packageMapper;

    @Test
    void failedRuleRollsBackItsDatabaseWriteAndNextRuleStillRuns() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("jin_angela_repair_20261030");
        long slotId = 2105452959699095554L;
        Integer before = jdbc.queryForObject(
                "SELECT allocated_units FROM recurring_schedule_slot WHERE id = ?", Integer.class, slotId);
        RecurringScheduleRuleEntity first = new RecurringScheduleRuleEntity();
        first.setId(1L);
        first.setPackageId(101L);
        RecurringScheduleRuleEntity second = new RecurringScheduleRuleEntity();
        second.setId(2L);
        second.setPackageId(102L);
        doReturn(List.of(first, second)).when(ruleMapper).selectList(any());
        doAnswer(invocation -> {
            jdbc.update("UPDATE recurring_schedule_slot SET allocated_units = allocated_units + 2 WHERE id = ?", slotId);
            throw new IllegalStateException("simulated failure after database write");
        }).when(packageMapper).selectById(101L);
        doReturn(null).when(packageMapper).selectById(102L);

        assertThat(service.renewActiveRuleOccurrences()).isZero();
        assertThat(jdbc.queryForObject("SELECT allocated_units FROM recurring_schedule_slot WHERE id = ?",
                Integer.class, slotId)).isEqualTo(before);
        verify(packageMapper).selectById(102L);
    }
}
