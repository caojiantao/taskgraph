package io.github.caojiantao.taskgraph.spring.fixture.onerror;

import io.github.caojiantao.taskgraph.spring.annotation.EnableTaskGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableTaskGraph(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.onerror")
@ComponentScan(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.onerror")
public class OnErrorGraphConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService onErrorExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}
