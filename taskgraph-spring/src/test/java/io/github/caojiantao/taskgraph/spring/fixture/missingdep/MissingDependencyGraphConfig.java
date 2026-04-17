package io.github.caojiantao.taskgraph.spring.fixture.missingdep;

import io.github.caojiantao.taskgraph.spring.annotation.EnableTaskGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableTaskGraph(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.missingdep")
@ComponentScan(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.missingdep")
public class MissingDependencyGraphConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService missingDependencyExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}
