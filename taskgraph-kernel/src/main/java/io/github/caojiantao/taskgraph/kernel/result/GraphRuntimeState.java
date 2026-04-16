package io.github.caojiantao.taskgraph.kernel.result;

/**
 * 图运行时状态。
 */
public enum GraphRuntimeState {
    RUNNING,
    SUCCESS,
    DEGRADED,
    TIMED_OUT
}
