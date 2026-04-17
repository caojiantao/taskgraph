package io.github.caojiantao.taskgraph.spring.fixture.duplicate;

import io.github.caojiantao.taskgraph.spring.annotation.EnableTaskGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableTaskGraph(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.duplicate")
@ComponentScan(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.duplicate")
public class DuplicateTaskGraphConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService duplicateExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}
