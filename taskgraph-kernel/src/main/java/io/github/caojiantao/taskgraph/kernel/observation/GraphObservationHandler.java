package io.github.caojiantao.taskgraph.kernel.observation;

import io.github.caojiantao.taskgraph.kernel.observation.event.GraphObservationEvent;

/**
 * 观测事件处理器。
 *
 * @param <E> 事件类型
 */
public interface GraphObservationHandler<E extends GraphObservationEvent> {

    /**
     * 当前处理器订阅的事件类型。
     *
     * @return 事件类型
     */
    Class<E> eventType();

    /**
     * 处理事件。
     *
     * @param event 事件对象
     */
    void handle(E event);
}
