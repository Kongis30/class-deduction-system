package com.classdeduction.system.modules.systemconfig.service.impl;

import com.classdeduction.system.modules.systemconfig.service.StudentBirthdayChange;
import com.classdeduction.system.modules.systemconfig.service.SystemMessageService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 提交后合并学生资料变化；单线程保证本实例内刷新按顺序完成。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BirthdayRefreshCoordinator {
    private final SystemMessageService messages;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "birthday-message-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private boolean scheduled;
    private boolean dirty;

    @TransactionalEventListener
    public void onStudentChanged(StudentBirthdayChange event) {
        requestRefresh();
    }

    public synchronized void requestRefresh() {
        dirty = true;
        if (!scheduled) {
            scheduled = true;
            executor.schedule(this::refresh, 1, TimeUnit.SECONDS);
        }
    }

    private void refresh() {
        synchronized (this) { dirty = false; }
        try {
            messages.refreshCurrentWeekBirthdayMessages();
        } catch (Exception exception) {
            log.error("Unable to refresh birthday messages; daily refresh will retry", exception);
        } finally {
            synchronized (this) {
                if (dirty) executor.schedule(this::refresh, 1, TimeUnit.SECONDS);
                else scheduled = false;
            }
        }
    }

    @PreDestroy
    public void shutdown() { executor.shutdown(); }
}
