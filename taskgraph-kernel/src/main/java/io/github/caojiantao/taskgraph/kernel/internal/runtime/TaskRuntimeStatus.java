package io.github.caojiantao.taskgraph.kernel.internal.runtime;

/**
 * 任务运行时状态。
 */
public enum TaskRuntimeStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}
