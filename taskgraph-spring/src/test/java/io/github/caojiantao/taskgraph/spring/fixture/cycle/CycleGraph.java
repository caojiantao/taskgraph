package io.github.caojiantao.taskgraph.spring.fixture.cycle;

import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;

@TaskGraphDefinition(graphId = "cycle-graph", executor = "cycleExecutor")
public interface CycleGraph extends GraphTask<CycleGraphContext> {
}
