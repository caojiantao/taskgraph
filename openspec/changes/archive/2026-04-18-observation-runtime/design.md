## 背景

`single-node-dag-kernel` 已经归档，当前内核语义已经明确：

- 图定义与运行时状态分离
- 图在构建或注册阶段完成校验
- 执行入口采用同步阻塞模型
- 失败任务跳过后继子图，无关分支继续
- 图超时返回结构化结果

本次设计不改变上述语义，只解决“如何把这些运行时语义稳定暴露出来”。

## 目标与非目标

**目标**

- 提供标准运行时观测 SPI。
- 让调用方能够按事件类型扩展日志、指标、Tracing 等外围能力。
- 在内核关键生命周期稳定发出图级与任务级事件。
- 保持内核对外事件模型语义清晰、可扩展、便于后续演进。
- 保持观测逻辑对主执行流程零侵入、best-effort。

**非目标**

- 不在本次变更中内置日志实现。
- 不在本次变更中内置 Micrometer、Prometheus、OpenTelemetry 集成。
- 不在本次变更中内置事件存储、控制台或可视化页面。
- 不在本次变更中把观测明细塞入 `GraphExecutionResult`。
- 不在本次变更中以 `ThreadPoolExecutor.beforeExecute/afterExecute` 作为核心扩展机制。

## 设计决策

### 1. 采用“事件模型 + 泛型处理器 + 分发器”，不采用大而全的监听器接口

首版不采用一个包含大量默认方法的 `GraphObservationListener`，而是采用：

- 事件模型
- 泛型处理器接口
- 内部分发器

建议核心接口为：

- `GraphObservationHandler<E extends GraphObservationEvent>`

建议核心分发器为：

- `GraphObservationDispatcher`

这样做的原因是：

- 调用方只需实现自己关心的事件类型
- 后续新增事件类型时，不需要修改既有处理器 SPI
- 更适合日志、指标、Tracing 等多扩展并存的场景
- 比继承执行器或实现一个超大监听器接口更稳定

### 2. 观测扩展点属于内核 SPI，应定义在 `taskgraph-kernel`

建议新增包：

- `io.github.caojiantao.taskgraph.kernel.observation`
- `io.github.caojiantao.taskgraph.kernel.observation.event`

原因是：

- 图开始、任务失败、任务跳过、图超时等语义只有内核最清楚
- 这些能力属于内核稳定扩展点，应与 `TaskHandler`、`TaskErrorHandler` 同级
- 外围模块应依赖这套 SPI，而不是反过来定义自己的观测协议

### 3. `GraphObservationDispatcher` 挂在 `GraphExecutor` 上，不挂在 `GraphExecutionRequest` 上

建议由 `DefaultGraphExecutor` 持有分发器：

- 默认构造使用 `NoOpGraphObservationDispatcher`
- 允许调用方显式传入自定义 dispatcher

不建议把 dispatcher 放进单次执行请求。

这样做的原因是：

- 观测能力更像执行器能力，而不是一次性请求参数
- 可以让同一个执行器稳定代表一套固定的观测策略
- Spring 接入时也更容易把带观测能力的执行器注册为基础设施 Bean

### 4. `executionId` 由内核在每次执行开始时自动生成

建议在 `DefaultGraphExecutor.execute()` 开始阶段生成唯一 `executionId`，首版不允许调用方覆盖。

建议首版直接使用：

- `UUID.randomUUID().toString()`

这样做的原因是：

- 能先把“一次图执行”的唯一标识稳定下来
- 便于日志串联、问题定位和后续 Tracing 集成
- 不必在首版引入额外请求参数和透传规则

### 5. 对外事件模型统一使用 `Instant` 与 `Duration`

首版建议：

- 时间点字段统一使用 `Instant`
- 时长与超时字段统一使用 `Duration`

内部实现可以继续使用：

- `System.nanoTime()` 计算耗时
- 毫秒级超时配置与调度

但对外事件模型不再混用裸 `millis` / `nanos`。

这样做的原因是：

- `Instant` 更适合表达“事件发生在什么时候”
- `Duration` 更适合表达“执行了多久”与“超时阈值是多少”
- 对外 API 语义更清晰，减少单位误用风险

### 6. 观测处理必须是 best-effort

无论是单个处理器，还是分发链路中的任一节点，一旦观测逻辑本身抛出异常，都不得反向影响图执行主流程。

建议规则：

- 处理器异常必须被吞掉或隔离
- 某个处理器失败不得影响其他处理器
- 观测逻辑不能改变任务状态、图状态和失败传播语义

### 7. 任务超时不单独建事件类型，统一并入任务失败事件

首版不单独保留 `TaskTimedOutEvent`，而是统一通过 `TaskFailedEvent` 表达任务失败。

其中：

- 普通任务失败通过 `TaskFailedEvent(cause = XxxException)` 表达
- 任务超时通过 `TaskFailedEvent(cause = TaskTimeoutException)` 表达

这样做的原因是：

- 任务超时本质上是任务失败的一种特殊原因
- 保持任务事件模型更简单，避免失败事件与超时事件重复
- 调用方仍可通过异常类型稳定区分普通失败与超时失败

因此，建议补充框架自己的超时异常类型：

- `TaskTimeoutException extends TaskExecutionException`

### 8. 不采用 `ThreadPoolExecutor` 模板方法作为核心观测机制

尽管 JDK 在线程池层提供了 `beforeExecute` / `afterExecute` 这类模板方法，但本次变更不以此作为框架主扩展机制。

原因是：

- 这些钩子表达的是线程池执行语义，而不是 DAG 运行语义
- 它们无法天然表达任务跳过、图超时、任务提交失败等 DAG 事件
- 它们更适合线程池技术监控，不适合作为框架对外稳定 SPI

后续如需线程池维度监控，可以作为补充能力叠加，但不进入当前主设计。

### 9. 事件按框架语义区分，异常按语义所有权克制定义

本次变更需要明确区分“事件模型”和“异常模型”的职责：

- 事件负责表达“框架语义上发生了什么”
- 异常负责表达“导致该结果的具体原因是什么”

因此：

- 事件类型必须按框架语义清晰区分
- 异常类型不要求与事件类型一一对应

例如：

- `TaskSkippedEvent` 表达“任务因依赖传播被框架主动跳过”
- `TaskSubmissionFailedEvent` 表达“任务本该执行，但提交到底层执行器失败”

虽然这两类事件都意味着“任务最终没有正常跑起来”，但它们代表两种不同框架语义，因此必须保留独立事件类型。

相对地，异常类型只在框架真正拥有语义所有权时才单独定义。当前阶段：

- `TaskTimeoutException` 由 `taskgraph` 自己的超时观察机制直接产生，因此值得定义为框架异常
- 任务提交失败通常由底层执行器抛出 `RejectedExecutionException` 或其他运行时异常，因此首版不再重复定义 `TaskSubmissionFailedException`
- 外部 RPC、熔断、降级、中断等失败原因，若已有成熟原始异常类型，首版应优先保留原始 `cause`

这样做的原因是：

- 保持事件模型清晰稳定，便于做日志、指标、Tracing 和告警
- 保持异常模型克制，避免无意义地重复包装底层异常语义
- 让框架只为“自己直接产生、且拥有语义所有权”的失败原因定义专属异常

### 10. 事件分发采用“同步 + 按注册顺序 + 支持父类订阅”的规则

首版 `GraphObservationDispatcher` 建议采用最简单、最稳定的分发规则：

- 事件在当前执行线程中同步分发
- 多个处理器按注册顺序依次接收事件
- 若处理器声明的 `eventType` 是当前事件类型的父类，则该处理器也应接收该事件

这意味着：

- 订阅 `TaskObservationEvent` 的处理器可以收到全部任务级事件
- 订阅 `GraphObservationEvent` 的处理器可以收到全部图级和任务级事件
- 订阅具体事件类型的处理器只接收对应具体事件

这样做的原因是：

- 首版不引入额外异步线程与调度复杂度
- 分发顺序确定，便于测试、排障和日志串联
- 通过父类订阅可以减少处理器重复实现

### 11. 图超时后仍需收敛到图结束事件

当图级等待超时时，首版建议事件顺序为：

1. 先发布 `GraphTimedOutEvent`
2. 最终仍发布 `GraphFinishedEvent(state = TIMED_OUT)`

这样做的原因是：

- `GraphTimedOutEvent` 用于表达“图超时这一特殊关键节点已经发生”
- `GraphFinishedEvent` 用于表达“本次图执行已经正式收敛到最终终态”
- 保持图级生命周期完整，避免超时场景下缺失标准结束事件

### 12. 提交失败事件应保留底层原始原因

`TaskSubmissionFailedEvent` 的 `cause` 首版应尽量保留底层提交失败的原始异常作为根因，例如：

- `RejectedExecutionException`
- 其他提交阶段抛出的 `RuntimeException`

首版不再额外定义 `TaskSubmissionFailedException`，也不要求为了事件模型再额外发明一层新的提交失败异常。

这样做的原因是：

- 提交失败的框架语义已经通过 `TaskSubmissionFailedEvent` 本身表达清楚
- 保留原始异常更便于调用方定位底层执行器问题
- 可以避免无意义的重复包装

## 包结构建议

### `observation`

- `GraphObservationHandler<E extends GraphObservationEvent>`
- `GraphObservationDispatcher`
- `NoOpGraphObservationDispatcher`

### `observation.event`

- `GraphObservationEvent`
- `GraphLifecycleEvent`
- `TaskObservationEvent`
- `GraphStartedEvent`
- `GraphFinishedEvent`
- `GraphTimedOutEvent`
- `TaskStartedEvent`
- `TaskSucceededEvent`
- `TaskFailedEvent`
- `TaskSkippedEvent`
- `TaskSubmissionFailedEvent`

## 事件模型建议

### 根事件

建议统一根类：

- `GraphObservationEvent`

建议字段：

- `String executionId`
- `String graphId`
- `Instant occurredAt`

### 图级事件基类

- `GraphLifecycleEvent`

### 任务级事件基类

- `TaskObservationEvent`

建议新增字段：

- `String taskId`

### 具体事件

#### 图级事件

- `GraphStartedEvent`
- `GraphFinishedEvent`
  - `GraphRuntimeState state`
  - `Duration duration`
- `GraphTimedOutEvent`
  - `Duration timeout`

#### 任务级事件

- `TaskStartedEvent`
  - `Duration queueDuration`
- `TaskSucceededEvent`
  - `Duration duration`
- `TaskFailedEvent`
  - `Throwable cause`
  - `Duration duration`
- `TaskSkippedEvent`
  - `String triggeredByTaskId`
- `TaskSubmissionFailedEvent`
  - `Throwable cause`

## 事件触发时机

结合当前执行链路，建议触发时机如下：

1. `DefaultGraphExecutor.execute()` 开始时
   - 发布 `GraphStartedEvent`

2. `DefaultGraphExecutor.runTask()` 真正进入任务处理逻辑前
   - 发布 `TaskStartedEvent`
   - 同时携带从任务提交成功到真正进入执行逻辑之间的 `queueDuration`

3. 任务成功完成并成功写入 `SUCCESS` 状态后
   - 发布 `TaskSucceededEvent`

4. `DefaultGraphExecutor.handleTaskFailure()` 中
   - 发布 `TaskFailedEvent`

5. `DefaultGraphExecutor.onTaskTimeout()` 中
   - 使用 `TaskTimeoutException` 构造失败原因
   - 最终仍发布 `TaskFailedEvent`
   - 任务超时从任务真正进入执行逻辑后开始计时，不包含在线程池中的排队等待时间

6. `TaskDispatcher.submitTask()` 提交线程池失败时
   - 发布 `TaskSubmissionFailedEvent`

7. `DefaultGraphExecutor.skipDescendants()` 将任务标记为 `SKIPPED` 时
   - 发布 `TaskSkippedEvent`

8. 图级等待超时时
   - 先发布 `GraphTimedOutEvent`

9. `DefaultGraphExecutor.execute()` 返回前
   - 发布 `GraphFinishedEvent`

## 运行时数据来源

### `executionId`

- 在 `execute()` 开始时生成
- 整个图执行过程中的全部事件共享同一个 `executionId`

### `occurredAt`

- 在每个事件对象创建时通过 `Instant.now()` 获取

### `duration`

- 建议内部通过 `System.nanoTime()` 记录开始与结束
- 任务级 `duration` 从任务真正进入执行逻辑时开始计算，不包含在线程池中的排队时间
- 事件创建时转换成 `Duration`

### `timeout`

- 直接基于图级或任务级生效超时值创建 `Duration`
- 任务级超时从任务真正进入执行逻辑后开始计时
- 任务在线程池中的排队等待时间不计入任务级超时

### `queueDuration`

- 表示任务提交成功后，到真正进入执行逻辑前的排队等待时间
- 建议仅作为运行时观测字段暴露在 `TaskStartedEvent`
- 不参与任务级超时判定

## 快速开始形态

建议调用方未来的最小接入形态如下：

```java
GraphObservationHandler<TaskFailedEvent> failedHandler = ...;
GraphObservationHandler<GraphFinishedEvent> finishedHandler = ...;

GraphObservationDispatcher dispatcher =
        new GraphObservationDispatcher(Arrays.asList(failedHandler, finishedHandler));

GraphExecutor executor = new DefaultGraphExecutor(dispatcher);

GraphExecutionRequest<MyContext> request = GraphExecutionRequest.<MyContext>builder()
        .graph(graph)
        .context(context)
        .build();

GraphExecutionResult result = executor.execute(request);
```

## 测试建议

建议至少覆盖：

- 图开始与图结束事件正常分发
- 任务成功、失败、超时、跳过、提交失败事件正常分发
- 图超时事件正常分发
- 图超时后仍然发布 `GraphFinishedEvent(state = TIMED_OUT)`
- 任务超时时通过 `TaskFailedEvent` + `TaskTimeoutException` 正常表达
- `executionId` 在同一次执行内保持一致，在不同执行之间不同
- 父类事件处理器可收到子类事件，多个处理器按注册顺序接收事件
- 处理器抛异常时不影响主执行流程
- 多个处理器同时存在时，单个处理器失败不影响其他处理器
