package io.github.caojiantao.taskgraph.spring.fixture.cycle;

import io.github.caojiantao.taskgraph.spring.annotation.EnableTaskGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableTaskGraph(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.cycle")
@ComponentScan(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.cycle")
public class CycleGraphConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService cycleExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}
