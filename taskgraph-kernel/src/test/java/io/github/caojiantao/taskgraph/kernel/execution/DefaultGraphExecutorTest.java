package io.github.caojiantao.taskgraph.kernel.execution;

import io.github.caojiantao.taskgraph.kernel.graph.TaskNode;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import io.github.caojiantao.taskgraph.kernel.result.GraphExecutionResult;
import io.github.caojiantao.taskgraph.kernel.result.GraphRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultGraphExecutorTest {

    @Test
    void shouldReturnSuccessWhenAllTasksSucceed() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<String> order = new CopyOnWriteArrayList<>();
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .handler(context -> order.add("product"))
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("promotion")
                            .dependsOn("product")
                            .handler(context -> order.add("promotion"))
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, new java.util.HashMap<>());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(order).containsExactly("product", "promotion");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldExecuteMultipleRootTasks() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<String> executed = new CopyOnWriteArrayList<>();
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .handler(context -> executed.add("product"))
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("ads")
                            .handler(context -> executed.add("ads"))
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, new java.util.HashMap<>());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(executed).containsExactlyInAnyOrder("product", "ads");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldReleaseJoinTaskOnlyAfterAllUpstreamsSucceed() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Map<String, Object> context = new java.util.concurrent.ConcurrentHashMap<>();
            List<String> executed = new CopyOnWriteArrayList<>();
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .handler(ctx -> {
                                ctx.put("product", Boolean.TRUE);
                                executed.add("product");
                            })
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("inventory")
                            .handler(ctx -> {
                                ctx.put("inventory", Boolean.TRUE);
                                executed.add("inventory");
                            })
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("summary")
                            .dependsOn("product")
                            .dependsOn("inventory")
                            .handler(ctx -> {
                                if (!ctx.containsKey("product") || !ctx.containsKey("inventory")) {
                                    throw new IllegalStateException("upstreams not ready");
                                }
                                executed.add("summary");
                            })
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, context);

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(executed).contains("product", "inventory", "summary");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldSkipDownstreamButKeepUnrelatedBranchAfterFailure() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<String> executed = new CopyOnWriteArrayList<>();
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .handler(context -> {
                                executed.add("product");
                                throw new IllegalStateException("boom");
                            })
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("promotion")
                            .dependsOn("product")
                            .handler(context -> executed.add("promotion"))
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("ads")
                            .handler(context -> executed.add("ads"))
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, new java.util.HashMap<>());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.DEGRADED);
            assertThat(executed).contains("product", "ads");
            assertThat(executed).doesNotContain("promotion");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldTreatSubmissionRejectionAsTaskFailure() {
        ExecutorService graphExecutor = Executors.newFixedThreadPool(2);
        ExecutorService rejectedExecutor = Executors.newSingleThreadExecutor();
        rejectedExecutor.shutdownNow();
        try {
            List<String> executed = new CopyOnWriteArrayList<>();
            AtomicInteger errorHandlerCalls = new AtomicInteger();
            AtomicReference<Throwable> failureRef = new AtomicReference<>();
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(graphExecutor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .executor(rejectedExecutor)
                            .handler(ctx -> executed.add("product"))
                            .errorHandler((ctx, cause) -> {
                                errorHandlerCalls.incrementAndGet();
                                failureRef.set(cause);
                            })
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("promotion")
                            .dependsOn("product")
                            .handler(ctx -> executed.add("promotion"))
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("ads")
                            .handler(ctx -> executed.add("ads"))
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, new java.util.HashMap<>());

            // 这里要明确证明命中的是“提交被线程池拒绝”分支，而不是普通任务执行异常。
            assertThat(result.getState()).isEqualTo(GraphRuntimeState.DEGRADED);
            assertThat(errorHandlerCalls.get()).isEqualTo(1);
            assertThat(failureRef.get()).isInstanceOf(RejectedExecutionException.class);
            assertThat(executed).contains("ads");
            assertThat(executed).doesNotContain("product", "promotion");
        } finally {
            graphExecutor.shutdownNow();
        }
    }

    @Test
    void shouldReturnTimedOutAndKeepPartialContextWrites() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Map<String, Object> context = new java.util.concurrent.ConcurrentHashMap<>();
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(50L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("fast")
                            .handler(ctx -> ctx.put("fast", Boolean.TRUE))
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("slow")
                            .dependsOn("fast")
                            .handler(ctx -> {
                                Thread.sleep(300L);
                                ctx.put("slow", Boolean.TRUE);
                            })
                            .timeoutMillis(500L)
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, context);

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.TIMED_OUT);
            assertThat(context).containsEntry("fast", Boolean.TRUE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldTreatTaskTimeoutAsDegradedAndSkipDownstream() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<String> executed = new CopyOnWriteArrayList<>();
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("slow")
                            .handler(ctx -> {
                                executed.add("slow");
                                Thread.sleep(300L);
                            })
                            .timeoutMillis(50L)
                            .build())
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("downstream")
                            .dependsOn("slow")
                            .handler(ctx -> executed.add("downstream"))
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, new java.util.HashMap<>());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.DEGRADED);
            assertThat(executed).contains("slow");
            assertThat(executed).doesNotContain("downstream");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotInvokeErrorHandlerWhenGraphTimeoutCancelsTask() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AtomicInteger errorHandlerCalls = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(1);
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(50L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("slow")
                            .handler(ctx -> {
                                started.countDown();
                                Thread.sleep(500L);
                            })
                            .errorHandler((ctx, cause) -> errorHandlerCalls.incrementAndGet())
                            .timeoutMillis(500L)
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, new java.util.HashMap<>());

            // 图级超时只负责收口和取消，不应额外触发任务级 errorHandler。
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100L);

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.TIMED_OUT);
            assertThat(errorHandlerCalls.get()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldIgnoreErrorHandlerFailureAndStillReturnDegraded() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .handler(ctx -> {
                                throw new IllegalStateException("boom");
                            })
                            .errorHandler((ctx, cause) -> {
                                throw new IllegalStateException("error-handler-boom");
                            })
                            .build())
                    .build();

            GraphExecutionResult result = execute(graph, new java.util.HashMap<>());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.DEGRADED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowSameGraphToExecuteTwiceWithoutRuntimeStateLeak() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AtomicInteger invocation = new AtomicInteger();
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .handler(ctx -> {
                                if (invocation.incrementAndGet() == 1) {
                                    throw new IllegalStateException("boom");
                                }
                                ctx.put("product", Boolean.TRUE);
                            })
                            .build())
                    .build();

            GraphExecutionResult firstResult = execute(graph, new java.util.HashMap<>());

            Map<String, Object> secondContext = new java.util.HashMap<>();
            // 同一张图重复执行时，第一次的失败状态不能泄漏到第二次执行。
            GraphExecutionResult secondResult = execute(graph, secondContext);

            assertThat(firstResult.getState()).isEqualTo(GraphRuntimeState.DEGRADED);
            assertThat(secondResult.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(secondContext).containsEntry("product", Boolean.TRUE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldBlockCallerThreadUntilGraphCompletes() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .timeoutMillis(1000L)
                    .addTask(TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .handler(ctx -> {
                                Thread.sleep(200L);
                                ctx.put("product", Boolean.TRUE);
                            })
                            .timeoutMillis(500L)
                            .build())
                    .build();

            long startNanos = System.nanoTime();
            Map<String, Object> context = new java.util.HashMap<>();
            GraphExecutionResult result = execute(graph, context);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(context).containsEntry("product", Boolean.TRUE);
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(150L);
        } finally {
            executor.shutdownNow();
        }
    }

    private GraphExecutionResult execute(TaskGraph<Map<String, Object>> graph, Map<String, Object> context) {
        return new DefaultGraphExecutor().execute(GraphExecutionRequest.<Map<String, Object>>builder()
                .graph(graph)
                .context(context)
                .build());
    }
}
