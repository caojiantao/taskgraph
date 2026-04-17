package io.github.caojiantao.taskgraph.spring.fixture.onerror;

import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;

@TaskGraphDefinition(graphId = "on-error-graph", executor = "onErrorExecutor")
public interface OnErrorGraph extends GraphTask<OnErrorGraphContext> {
}
