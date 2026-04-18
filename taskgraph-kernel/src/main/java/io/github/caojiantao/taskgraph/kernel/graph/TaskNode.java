package io.github.caojiantao.taskgraph.kernel.graph;

import io.github.caojiantao.taskgraph.kernel.spi.TaskErrorHandler;
import io.github.caojiantao.taskgraph.kernel.spi.TaskHandler;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;

import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * 定义在 {@link TaskGraph} 中的不可变任务节点模型。
 *
 * @param <C> 执行上下文类型
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TaskNode<C> {

    /**
     * 任务标识，只要求在单图内唯一。
     */
    @NonNull
    private final String taskId;

    /**
     * 当前任务运行前必须成功完成的上游任务标识。
     */
    @Singular("dependsOn")
    private final Set<String> dependsOn;

    /**
     * 可选的任务级线程池覆盖配置。
     */
    private final ExecutorService executor;

    /**
     * 可选的任务级超时时间，单位毫秒。
     */
    private final Long timeoutMillis;

    /**
     * 必填的任务处理逻辑。
     */
    @NonNull
    private final TaskHandler<C> handler;

    /**
     * 可选的任务失败回调。
     */
    private final TaskErrorHandler<C> errorHandler;
}
