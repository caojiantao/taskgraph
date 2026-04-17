package io.github.caojiantao.taskgraph.spring;

import io.github.caojiantao.taskgraph.kernel.exception.GraphExecutionException;
import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import io.github.caojiantao.taskgraph.kernel.execution.GraphExecutor;
import io.github.caojiantao.taskgraph.kernel.result.GraphRuntimeState;
import io.github.caojiantao.taskgraph.spring.runtime.TaskGraphTemplate;
import io.github.caojiantao.taskgraph.spring.fixture.cycle.CycleGraphConfig;
import io.github.caojiantao.taskgraph.spring.fixture.defaultscan.DefaultScanConfig;
import io.github.caojiantao.taskgraph.spring.fixture.defaultscan.DefaultScanContext;
import io.github.caojiantao.taskgraph.spring.fixture.duplicate.DuplicateTaskGraphConfig;
import io.github.caojiantao.taskgraph.spring.fixture.empty.EmptyGraphConfig;
import io.github.caojiantao.taskgraph.spring.fixture.missingdep.MissingDependencyGraphConfig;
import io.github.caojiantao.taskgraph.spring.fixture.onerror.OnErrorGraphConfig;
import io.github.caojiantao.taskgraph.spring.fixture.onerror.OnErrorGraphContext;
import io.github.caojiantao.taskgraph.spring.fixture.proxy.ProxyGraphConfig;
import io.github.caojiantao.taskgraph.spring.fixture.proxy.ProxyGraphContext;
import io.github.caojiantao.taskgraph.spring.fixture.success.ExtraExecutorConfig;
import io.github.caojiantao.taskgraph.spring.fixture.success.SuccessGraphConfig;
import io.github.caojiantao.taskgraph.spring.fixture.success.SuccessGraphContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskGraphSpringIntegrationTest {

    @Test
    void shouldCompileAndExecuteGraphFromSpringContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SuccessGraphConfig.class);
        try {
            SuccessGraphContext detailContext = new SuccessGraphContext();
            GraphRuntimeState state = context.getBean(TaskGraphTemplate.class).execute(detailContext).getState();

            assertThat(state).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(detailContext.getExecuted()).containsExactly("product", "promotion");
        } finally {
            context.close();
        }
    }

    @Test
    void shouldFailWhenGraphHasNoTaskImplementation() {
        assertThatThrownBy(() -> refreshContext(EmptyGraphConfig.class))
                .hasMessageContaining("has no task implementation beans");
    }

    @Test
    void shouldFailWhenGraphContainsDuplicateTaskIds() {
        assertThatThrownBy(() -> refreshContext(DuplicateTaskGraphConfig.class))
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("duplicate taskId");
    }

    @Test
    void shouldFailWhenGraphContainsMissingDependency() {
        assertThatThrownBy(() -> refreshContext(MissingDependencyGraphConfig.class))
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("missing task");
    }

    @Test
    void shouldFailWhenGraphContainsCycle() {
        assertThatThrownBy(() -> refreshContext(CycleGraphConfig.class))
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("cyclic");
    }

    @Test
    void shouldUseTargetClassMetadataWhenBeanIsProxied() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ProxyGraphConfig.class);
        try {
            ProxyGraphContext proxyGraphContext = new ProxyGraphContext();
            GraphRuntimeState state = context.getBean(TaskGraphTemplate.class).execute(proxyGraphContext).getState();

            assertThat(state).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(proxyGraphContext.getExecuted()).containsExactly("proxied-product");
        } finally {
            context.close();
        }
    }

    @Test
    void shouldSupportDefaultScanFromEnableTaskGraphPackage() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(DefaultScanConfig.class);
        try {
            DefaultScanContext defaultScanContext = new DefaultScanContext();
            GraphRuntimeState state = context.getBean(TaskGraphTemplate.class).execute(defaultScanContext).getState();

            assertThat(state).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(defaultScanContext.getExecuted()).containsExactly("default-scan");
        } finally {
            context.close();
        }
    }

    @Test
    void shouldNotConflictWhenUserDefinesAnotherGraphExecutorBean() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ExtraExecutorConfig.class);
        try {
            SuccessGraphContext detailContext = new SuccessGraphContext();
            GraphRuntimeState state = context.getBean(TaskGraphTemplate.class).execute(detailContext).getState();

            assertThat(state).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(context.getBeansOfType(GraphExecutor.class)).hasSize(2);
        } finally {
            context.close();
        }
    }

    @Test
    void shouldFailFastWhenContextIsNotBound() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SuccessGraphConfig.class);
        try {
            assertThatThrownBy(() -> context.getBean(TaskGraphTemplate.class).execute(new Object()))
                    .isInstanceOf(GraphExecutionException.class)
                    .hasMessageContaining("no task graph bound");
        } finally {
            context.close();
        }
    }

    @Test
    void shouldInvokeOnErrorWhenTaskFails() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OnErrorGraphConfig.class);
        try {
            OnErrorGraphContext graphContext = new OnErrorGraphContext();
            GraphRuntimeState state = context.getBean(TaskGraphTemplate.class).execute(graphContext).getState();

            assertThat(state).isEqualTo(GraphRuntimeState.DEGRADED);
            assertThat(graphContext.getExecuted()).containsExactly("failing");
            assertThat(graphContext.getErrorMessages()).hasSize(1);
            assertThat(graphContext.getErrorMessages().get(0)).contains("execution failed");
        } finally {
            context.close();
        }
    }

    private void refreshContext(Class<?> configurationClass) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.register(configurationClass);
            context.refresh();
        } finally {
            context.close();
        }
    }
}
