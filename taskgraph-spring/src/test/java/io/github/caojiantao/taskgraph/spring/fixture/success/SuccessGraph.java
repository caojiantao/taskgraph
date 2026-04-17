package io.github.caojiantao.taskgraph.spring.fixture.success;

import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;

@TaskGraphDefinition(graphId = "detail-graph", executor = "detailExecutor", timeoutMillis = 1000L)
public interface SuccessGraph extends GraphTask<SuccessGraphContext> {
}
