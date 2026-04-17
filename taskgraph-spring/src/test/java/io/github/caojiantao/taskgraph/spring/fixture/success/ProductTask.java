package io.github.caojiantao.taskgraph.spring.fixture.success;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import org.springframework.stereotype.Component;

@Component
@TaskNodeDefinition(taskId = "product")
public class ProductTask implements SuccessGraph {

    @Override
    public void handle(SuccessGraphContext context) {
        context.getValues().put("product", Boolean.TRUE);
        context.getExecuted().add("product");
    }
}
