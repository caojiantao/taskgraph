package io.github.caojiantao.taskgraph.spring.fixture.missingdep;

import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;

@TaskGraphDefinition(graphId = "missing-dependency-graph", executor = "missingDependencyExecutor")
public interface MissingDependencyGraph extends GraphTask<MissingDependencyGraphContext> {
}
