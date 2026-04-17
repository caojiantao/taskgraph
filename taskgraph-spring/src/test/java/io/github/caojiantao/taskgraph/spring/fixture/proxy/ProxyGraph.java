package io.github.caojiantao.taskgraph.spring.fixture.proxy;

import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;

@TaskGraphDefinition(graphId = "proxy-graph", executor = "proxyExecutor")
public interface ProxyGraph extends GraphTask<ProxyGraphContext> {
}
