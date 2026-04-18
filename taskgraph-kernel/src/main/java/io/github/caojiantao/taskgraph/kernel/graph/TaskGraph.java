package io.github.caojiantao.taskgraph.kernel.graph;

import io.github.caojiantao.taskgraph.kernel.validation.DefaultTaskGraphValidator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 单机 DAG 一次定义期图模型，不可变，可重复执行。
 *
 * @param <C> 执行上下文类型
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TaskGraph<C> {

    /**
     * 图唯一标识。
     */
    @NonNull
    private final String graphId;

    /**
     * 可选的图级默认线程池。
     */
    private final ExecutorService executor;

    /**
     * 可选的图级超时时间，单位毫秒。
     */
    private final Long timeoutMillis;

    /**
     * 图中声明的全部任务。
     */
    @Singular("addTask")
    private final List<TaskNode<C>> tasks;

    public static class TaskGraphBuilder<C> {

        public TaskGraph<C> build() {
            List<TaskNode<C>> immutableTasks;
            if (this.tasks == null || this.tasks.isEmpty()) {
                immutableTasks = Collections.emptyList();
            } else {
                immutableTasks = Collections.unmodifiableList(new ArrayList<>(this.tasks));
            }

            TaskGraph<C> taskGraph = new TaskGraph<>(this.graphId, this.executor, this.timeoutMillis, immutableTasks);
            // 图一旦构建成功，就要求已经完成定义期校验，避免把结构问题拖到运行期。
            DefaultTaskGraphValidator.getInstance().validate(taskGraph);
            return taskGraph;
        }
    }
}
