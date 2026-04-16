package io.github.caojiantao.taskgraph.kernel.execution;

/**
 * 图执行器公共工厂方法。
 */
public final class GraphExecutors {

    private static final GraphExecutor DEFAULT_EXECUTOR = new DefaultGraphExecutor();

    private GraphExecutors() {
    }

    /**
     * 返回共享的默认同步执行器。
     *
     * @return 默认执行器
     */
    public static GraphExecutor defaultExecutor() {
        return DEFAULT_EXECUTOR;
    }
}
