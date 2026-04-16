package io.github.caojiantao.taskgraph.kernel.execution;

import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * 一次图执行请求，不可变。
 *
 * @param <C> 执行上下文类型
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class GraphExecutionRequest<C> {

    /**
     * 待执行的图定义。
     */
    @NonNull
    private final TaskGraph<C> graph;

    /**
     * 调用方传入的本次执行上下文。
     */
    @NonNull
    private final C context;
}
