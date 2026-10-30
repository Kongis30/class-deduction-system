package com.classdeduction.system.modules.systemconfig.service.impl;

import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.systemconfig.service.StudentBirthdayChange;
import com.classdeduction.system.modules.systemconfig.service.SystemMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.*;

class BirthdayRefreshCoordinatorTest {
    @Test
    void unrelatedChangesDoNotPublishButBirthdayChangesDo() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        StudentEntity student = new StudentEntity();
        student.setStudentName("Student");
        student.setBirthDate(LocalDate.of(2015, 10, 30));
        var before = StudentBirthdayChange.snapshot(student);
        student.setGuardianPhone("123");
        StudentBirthdayChange.publishIfChanged(publisher, before, student);
        verifyNoInteractions(publisher);
        student.setBirthDate(null);
        StudentBirthdayChange.publishIfChanged(publisher, before, student);
        verify(publisher).publishEvent(any(StudentBirthdayChange.class));
    }

    @Test
    void simultaneousRequestsAreCombined() throws Exception {
        SystemMessageService service = mock(SystemMessageService.class);
        BirthdayRefreshCoordinator coordinator = new BirthdayRefreshCoordinator(service);
        try {
            coordinator.requestRefresh();
            coordinator.requestRefresh();
            verify(service, timeout(4000).times(1)).refreshCurrentWeekBirthdayMessages();
            Thread.sleep(1200);
            verify(service, times(1)).refreshCurrentWeekBirthdayMessages();
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void changeDuringRefreshTriggersAnotherRefresh() throws Exception {
        SystemMessageService service = mock(SystemMessageService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.refreshCurrentWeekBirthdayMessages()).thenAnswer(invocation -> {
            started.countDown();
            release.await(4, TimeUnit.SECONDS);
            return 1;
        });
        BirthdayRefreshCoordinator coordinator = new BirthdayRefreshCoordinator(service);
        try {
            coordinator.requestRefresh();
            assertThat(started.await(4, TimeUnit.SECONDS)).isTrue();
            coordinator.requestRefresh();
            release.countDown();
            verify(service, timeout(4000).times(2)).refreshCurrentWeekBirthdayMessages();
        } finally {
            release.countDown();
            coordinator.shutdown();
        }
    }
}
