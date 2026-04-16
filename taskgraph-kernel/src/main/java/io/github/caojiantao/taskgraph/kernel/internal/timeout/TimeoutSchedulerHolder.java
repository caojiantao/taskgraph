package io.github.caojiantao.taskgraph.kernel.internal.timeout;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认共享超时调度器持有者。
 */
public final class TimeoutSchedulerHolder {

    private static final ScheduledExecutorService INSTANCE = createScheduler();

    private TimeoutSchedulerHolder() {
    }

    public static ScheduledExecutorService getInstance() {
        return INSTANCE;
    }

    private static ScheduledExecutorService createScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, new TaskGraphThreadFactory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static final class TaskGraphThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("taskgraph-timeout-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
