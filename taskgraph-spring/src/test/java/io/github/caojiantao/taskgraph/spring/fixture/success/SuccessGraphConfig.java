package io.github.caojiantao.taskgraph.spring.fixture.success;

import io.github.caojiantao.taskgraph.spring.annotation.EnableTaskGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableTaskGraph(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.success")
@ComponentScan(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.success")
public class SuccessGraphConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService detailExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
