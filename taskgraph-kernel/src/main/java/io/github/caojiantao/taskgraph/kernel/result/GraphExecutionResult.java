package io.github.caojiantao.taskgraph.kernel.result;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * 结构化图执行结果。
 * `state` 字段表示本次图执行的图运行时状态。
 */
@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "of")
public final class GraphExecutionResult {

    @NonNull
    private final GraphRuntimeState state;

    public static GraphExecutionResult success() {
        return of(GraphRuntimeState.SUCCESS);
    }

    public static GraphExecutionResult degraded() {
        return of(GraphRuntimeState.DEGRADED);
    }

    public static GraphExecutionResult timedOut() {
        return of(GraphRuntimeState.TIMED_OUT);
    }
}
