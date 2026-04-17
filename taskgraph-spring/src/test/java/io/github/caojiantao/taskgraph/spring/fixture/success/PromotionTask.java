package io.github.caojiantao.taskgraph.spring.fixture.success;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import org.springframework.stereotype.Component;

@Component
@TaskNodeDefinition(taskId = "promotion", dependsOn = {"product"})
public class PromotionTask implements SuccessGraph {

    @Override
    public void handle(SuccessGraphContext context) {
        context.getValues().put("promotion", Boolean.TRUE);
        context.getExecuted().add("promotion");
    }
}
