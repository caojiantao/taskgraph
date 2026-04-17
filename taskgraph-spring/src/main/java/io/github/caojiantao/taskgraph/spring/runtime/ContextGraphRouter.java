package io.github.caojiantao.taskgraph.spring.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上下文类型到图标识的路由器。
 */
public final class ContextGraphRouter {

    private final Map<Class<?>, String> routeMap = new ConcurrentHashMap<>();

    public void register(Class<?> contextType, String graphId) {
        Objects.requireNonNull(contextType, "contextType must not be null");
        Objects.requireNonNull(graphId, "graphId must not be null");
        String previous = routeMap.putIfAbsent(contextType, graphId);
        if (previous != null && !previous.equals(graphId)) {
            throw new IllegalStateException(
                    "context type [" + contextType.getName() + "] already mapped to graph [" + previous + "]");
        }
    }

    public String route(Class<?> contextType) {
        Objects.requireNonNull(contextType, "contextType must not be null");
        return routeMap.get(contextType);
    }
}
