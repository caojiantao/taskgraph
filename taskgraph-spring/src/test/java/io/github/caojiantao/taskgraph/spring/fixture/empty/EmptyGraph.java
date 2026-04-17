package io.github.caojiantao.taskgraph.spring.fixture.empty;

import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;

@TaskGraphDefinition(graphId = "empty-graph", executor = "detailExecutor")
public interface EmptyGraph extends GraphTask<EmptyGraphContext> {
}
