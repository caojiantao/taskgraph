package io.github.caojiantao.taskgraph.spring.fixture.success;

import io.github.caojiantao.taskgraph.kernel.execution.DefaultGraphExecutor;
import io.github.caojiantao.taskgraph.kernel.execution.GraphExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtraExecutorConfig extends SuccessGraphConfig {

    @Bean
    public GraphExecutor customGraphExecutor() {
        return new DefaultGraphExecutor();
    }
}
