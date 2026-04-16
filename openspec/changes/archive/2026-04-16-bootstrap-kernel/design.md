## Context

当前 `taskgraph` 仓库是一个新的空工程，设计基线来自旧原型仓库 `concurrent`。原型已经在 `concurrent-core` 与 `concurrent-spring` 中验证了“基于依赖图进行并发执行”这条路线的价值，但它把多个关注点耦合在了一起：

- 任务声明与运行时状态共用同一套模型
- 执行语义部分隐含在代码里，没有被明确写成规范
- 通过全局静态注册表管理模块与节点
- Spring 集成层同时承担了图发现与调度语义

当前目标是在保留原始产品方向——单机 DAG 并发任务调度框架——的前提下，围绕明确的契约构建内核，使其适合长期维护与开源演进。

## Goals / Non-Goals

**Goals:**

- 定义稳定的单机 DAG 调度内核模型。
- 分离定义期图对象与运行期执行状态。
- 让校验、调度、超时与失败语义都变得明确且可测试。
- 支持未来的 Java DSL、Spring 适配层、监控与运维控制能力，但不把它们强塞进内核。
- 在新仓库中沉淀独立内核，同时保留旧原型仓库作为参考来源。

**Non-Goals:**

- 不实现分布式执行、持久化恢复或跨进程协同。
- 不在本次变更中设计最终版 Spring 编程模型。
- 不在首版内核中加入监控面板、线程池管理台或动态配置下发。
- 不以兼容 `v1` 内部类或 API 作为硬约束。

## Decisions

### 1. Build `taskgraph` as an independent repository evolved from the prototype

`taskgraph` 应作为独立工程承载新的内核主线，而旧仓库 `concurrent` 继续保留为原型与历史参考。这样既能保留背景与演化历史，又能让内核在不继承旧实现偶发复杂度的前提下重新设计。

**Alternatives considered**

- 直接在当前原型上原地重构：不采用，因为现有模型和全局状态会强烈影响新设计。
- 继续只在旧仓库内并行维护新旧两条主线：不采用，因为项目命名、模块边界和开源定位都已经收敛到 `taskgraph`，继续混放会长期增加认知负担。

### 2. Make the kernel the only normative runtime

当前阶段，内核应当是唯一的规范性运行时，负责：

- 图定义
- 图校验
- 依赖感知调度
- 运行时状态流转
- 结构化执行结果

Spring 或未来的运维包装层可以把用户侧声明转换成内核输入，但不能重新定义调度语义。

**Alternatives considered**

- 继续把调度放在 Spring 集成层里：不采用，因为这会让核心框架依赖某一种接入方式。

### 3. Split definition-time and runtime models

内核应明确区分以下概念：

- 不可变的任务/图定义
- 每次运行独立的执行期状态
- 最终执行结果

这样可以避免可变图对象不断累积运行状态，也能让重复执行、并发安全和测试都更清晰。

**Alternatives considered**

- 继续复用一套可变节点模型覆盖两个阶段：不采用，因为会让重试、并发保证和可观测性都变复杂。

### 4. Define terminal behavior as an explicit state machine

内核必须显式定义成功、失败、超时等终态，并明确在上游失败或图超时结束时，下游任务如何处理。

对于首版内核，基线执行策略应保持保守且确定：

- 执行前先校验图
- 在所有必需上游成功完成前，绝不提交下游任务
- 任务失败时只跳过其后继子图，不立即中断无关分支
- 图级超时触发后，停止继续提交新的下游任务
- 对外结果保持极简，只暴露图级运行时状态

**Alternatives considered**

- 从第一天开始就支持可配置的失败与超时策略：暂不采用，因为框架首先需要一套清晰且无歧义的默认语义。

### 5. Keep operational features outside the initial kernel

线程监控、线程池遥测、线程池动态调参和管理面控制当然都很有价值，但它们依赖一个稳定的执行模型。因此它们应构建在内核之上，而不是在当前阶段直接揉进内核。

### 6. Standardize project structure and naming for open-source evolution

为了让项目具备长期维护和开源演进的基础，工程结构与命名需要尽早收敛。这里的重点不是“好不好看”，而是：

- Maven 坐标尽量一次定稳，避免后续发布后再迁移
- 模块边界清晰，避免内核与适配层相互污染
- Java 包结构能明确区分稳定 API 与内部实现
- 命名能准确表达“单机 DAG 执行内核”的产品定位

因此本次变更建议把项目目标命名统一到 `taskgraph` 体系：

- 仓库目标名：`taskgraph`
- 根工程 `artifactId`：`taskgraph`
- Maven `groupId`：`io.github.caojiantao.taskgraph`
- 对外定位：`TaskGraph is a single-node DAG execution kernel for Java.`

当前阶段模块拆分保持克制，仅保留：

- `taskgraph-kernel`：唯一的内核实现与规范运行时

当前阶段不引入以下模块：

- `taskgraph-api`
- `taskgraph-bom`
- `taskgraph-testkit`

原因是当前阶段最重要的是先稳定执行语义与核心模型，而不是过早增加工程复杂度。

## Kernel API 草图

首版 `taskgraph` 内核不需要一次性把所有命名锁死，但需要先固定对象分层和职责边界。建议核心对象保持如下关系：

- `TaskDefinition`：任务定义，包含任务标识、依赖列表、处理函数、可选异常回调、可选任务级线程池和可选任务级超时
- `TaskGraph`：不可变任务图，包含唯一的 `graphId`、可选图级线程池、可选图级超时，并在图作用域内聚合全部任务定义及拓扑关系
- `TaskContext`：每次执行时传入的运行输入
- `GraphExecutionRequest`：一次执行请求，仅包含图和上下文
- `GraphExecutor`：内核同步执行入口，负责校验、调度、阻塞等待和结果汇总
- `GraphRuntime`：一次运行中的内部执行对象，用于持有运行态信息
- `GraphExecutionResult`：图级结果对象，首版仅包含图运行时状态 `state`

建议当前模块结构大致如下：

- 根聚合工程 `taskgraph`
- 子模块 `taskgraph-kernel`

建议首版 `taskgraph-kernel` 包结构大致如下：

- `io.github.caojiantao.taskgraph.kernel.graph`
- `io.github.caojiantao.taskgraph.kernel.execution`
- `io.github.caojiantao.taskgraph.kernel.validation`
- `io.github.caojiantao.taskgraph.kernel.result`
- `io.github.caojiantao.taskgraph.kernel.exception`
- `io.github.caojiantao.taskgraph.kernel.spi`
- `io.github.caojiantao.taskgraph.kernel.internal.runtime`
- `io.github.caojiantao.taskgraph.kernel.internal.scheduler`
- `io.github.caojiantao.taskgraph.kernel.internal.timeout`
- `io.github.caojiantao.taskgraph.kernel.internal.support`

其中：

- `graph` 只放定义期对象
- `execution` 放执行请求、执行器接口及默认入口
- `validation` 只放图校验器与校验异常
- `result` 只放图结果对象和状态枚举
- `exception` 放框架自定义异常
- `spi` 只放任务处理函数等可扩展接口
- `internal.*` 只放内部实现细节，不承诺外部兼容性

工程编码层面建议额外约束如下：

- 不再新增泛化的 `entity` 包
- 不再依赖万能 `util` 包承载核心语义
- 接口命名不使用 `I` 前缀
- 枚举命名不使用 `E` 前缀
- Java 包名不写入阶段性版本标记
- 定义期模型与运行期模型必须分包
- `internal` 包默认视为非稳定 API

对外公开 API 建议聚焦为：

- `TaskGraph`
- `TaskGraphRegistry`
- `TaskDefinition`
- `GraphExecutionRequest`
- `GraphExecutionResult`
- `GraphRuntimeState`
- `GraphExecutor`
- `TaskHandler`
- `TaskErrorHandler`

内部运行时实现建议收敛在以下类型附近：

- `GraphRuntime`
- `TaskRuntime`
- `TaskDispatcher`
- `TaskTimeoutWatcher`
- `DefaultGraphExecutor`
- `DefaultTaskGraphDefinitionValidator`
- `DefaultTaskGraphRegistryValidator`

建议至少提供以下框架异常：

- `ConcurrentException`：框架基础异常
- `GraphValidationException`：图校验失败
- `GraphExecutionException`：图执行期通用异常
- `TaskExecutionException`：任务执行失败包装异常

此外，定义期建议补充一个轻量图注册表：

- `TaskGraphRegistry`：承载图定义注册与 `graphId` 唯一性约束

## 核心状态模型

建议图运行时状态和任务运行时状态分开定义。

图运行时状态：

- `RUNNING`
- `SUCCESS`
- `DEGRADED`
- `TIMED_OUT`

约束如下：

- 图运行时状态只有 `RUNNING` 是非终态
- 任务进入失败处理流程后，只要无关分支仍在运行，图运行时状态继续保持 `RUNNING`
- `DEGRADED` 表示“本次图执行已经自然收敛，并且至少出现过一次失败或跳过”
- `TIMED_OUT` 是图级超时触发的立即终态
- 运行期不再额外维护一套“外部图状态 + 内部图状态”，只保留一个图运行时状态
- 任务运行时状态可在内部维护，但首版不强制对外暴露

## 执行流程

建议首版执行流程固定为：

1. 调用方构建不可变 `TaskGraph`
2. 在 `TaskGraph.build()` 或应用启动注册阶段完成完整 DAG 校验
3. 运行期只做轻量请求参数检查
4. 为本次运行创建独立的 `GraphRuntime`、依赖计数器和 `CountDownLatch`
5. 提交所有根任务
6. 当前业务线程调用 `latch.await(timeout, unit)` 并阻塞等待
7. 任务完成后更新本次运行中的依赖计数并执行 `countDown`
8. 仅当下游依赖全部成功满足时释放下游任务
9. 若某任务失败，则跳过该任务的后继子图；无关分支继续运行，图运行时状态继续保持 `RUNNING`
10. 若主线程等待超时，则将图运行时状态切换为 `TIMED_OUT`
11. 图自然收敛后，若任务运行时状态中不存在失败或跳过，则图运行时状态收敛为 `SUCCESS`；否则收敛为 `DEGRADED`
12. 返回极简图结果

这个流程要求“图定义”和“运行期状态”完全分离，避免重跑时复用脏状态。

## 线程池与任务超时模型

线程池选择采用两层模型：

- `TaskGraph` 上可选声明一个图级线程池 `executor`
- `TaskDefinition` 上可选声明任务级线程池 `executor`

调度规则如下：

- 任务未声明线程池时，使用图级线程池
- 任务声明了专属线程池时，优先使用任务自己的线程池
- 若图级和任务级都未提供线程池，则该图在构建或注册阶段直接失败

这样能保证：

- 同一张图可以有统一执行资源
- 慢 RPC、快 RPC、重计算任务可以按任务隔离线程池
- 内核本身不负责线程池治理，只负责选择和调度

任务级超时与图级超时的关系如下：

- 图级超时：来自 `TaskGraph.timeoutMillis`，主线程通过 `CountDownLatch.await(timeout)` 控制整图总等待时间
- 任务级超时：单任务的最长执行时间限制

超时选择同样采用两层模型：

- `TaskGraph` 上可选声明图级超时 `timeoutMillis`
- `TaskDefinition` 上可选声明任务级超时 `timeoutMillis`

规则如下：

- 图未声明超时时，使用框架内置默认图超时常量
- 任务未声明超时时，使用框架内置默认任务超时常量
- 任务声明了超时时，优先使用任务自己的超时
- `TaskGraph.timeoutMillis` 只表示整图总超时，不承担任务默认超时的角色

实现建议上，任务级超时不采用“提交后立即 `Future.get(timeout)` 阻塞等待”的模式，因为那会阻塞调度路径。更合理的方式是：

- 提交任务后保存该任务的 `Future`
- 为该任务注册独立的超时观察逻辑
- 到达任务级超时时，对该任务执行 `future.cancel(true)`
- 将该任务按失败处理，并跳过其后继子图

这里需要明确一点：`future.cancel(true)` 的设计含义是“向执行该任务的线程发送中断请求”。它不是强制杀线程，因此任务能否快速结束，取决于任务代码和底层调用是否正确响应中断。

首版推荐在内核中维护一个全局的任务级超时调度器，例如：

- `ScheduledExecutorService timeoutScheduler`
- 推荐实现：`ScheduledThreadPoolExecutor(1)`

这里的单线程调度器不是“只能监控一个任务”，而是：

- 可以注册很多任务级超时检查
- 所有超时检查进入延时队列
- 到点后由这个单线程依次触发超时回调

设计约束是：

- timeout watcher 的回调逻辑必须保持轻量
- 不在 watcher 中执行重计算、RPC 或复杂阻塞操作
- watcher 只负责触发取消、状态收敛入口和失败传播

这意味着：

- 图级超时仍然保持同步阻塞模型
- 任务级超时是调度层的附加保护
- 实际 RPC 超时仍建议由具体客户端自行设置

watcher 生命周期建议如下：

- 任务提交后，保存该任务对应的 `ScheduledFuture`
- 任务正常结束时，主动执行 `timeoutWatcher.cancel(false)`
- timeout watcher 自身仍保留 CAS 防重逻辑，避免与任务正常结束或图级超时发生竞争

为了减少已取消 watcher 长时间滞留在调度队列中的开销，首版建议对 `ScheduledThreadPoolExecutor` 开启：

- `setRemoveOnCancelPolicy(true)`

这样可以降低大量任务正常完成时无效 timeout watcher 对延时队列的占用。

## 校验时机与唯一性规则

首版把 DAG 校验明确划到定义期，而不是运行主路径：

- 纯 Java 构图场景：在 `TaskGraph.build()` 阶段完成校验
- 启动注册场景：在应用启动时完成图注册与校验
- 运行期 `execute()`：只做请求参数的轻量校验，不重复执行完整 DAG 校验

唯一性规则也按图作用域划分：

- `graphId` 在注册范围内唯一
- `taskId` 只要求在单个图内唯一
- 全局识别一个任务时，可将其视为 `graphId + taskId` 的组合键

这样能保证：

- 错误更早暴露
- 请求主路径更轻
- 相同语义的任务名可以在不同图中复用

## 失败与超时语义

首版建议采用固定失败语义，不在第一阶段暴露可配置失败策略：

- 任务抛异常：先触发该任务的可选异常回调
- 失败任务的所有后继子图不再执行
- 与失败任务无依赖关系的其他分支继续执行
- 只要任意任务失败，就跳过其后继子图，但图运行时状态仍保持 `RUNNING`
- 整张图自然收敛后，只要任务运行时状态中出现过失败或跳过，最终状态就是 `DEGRADED`
- 图超时：图立即进入 `TIMED_OUT`

共同规则：

- 任务失败不会立即中断无关分支
- 一旦图进入 `TIMED_OUT`，不再提交新任务
- 对未完成任务尝试执行 `Future.cancel(true)` 或等价机制，以中断请求方式推动其尽快结束
- 不承诺运行中的任务会立即停止
- 图超时时返回 `GraphExecutionResult`，而不是直接向调用方抛出超时异常
- 调用方传入的共享上下文继续保留已完成任务写入的数据，框架不做回滚

## 结果模型为什么保持极简

结合当前业务目标，调用方真正关心的只有两件事：

- 这张图最终是否成功

调用方本身已经持有共享上下文，因此不需要框架再复制上下文快照；同时首版目标也不是做运行时诊断平台，因此不需要暴露任务级执行明细。于是首版结果模型收敛为：

- `GraphExecutionResult`
  - `state`：图运行时状态

其中：

- `SUCCESS` 表示图完整成功
- `DEGRADED` 表示图执行过程中出现过任务失败或任务跳过，并且整图已经完成自然收敛
- `TIMED_OUT` 表示图在超时时间内未完成

失败细节和超时细节在首版不通过结果对象返回，而是交由：

- 每任务异常回调处理
- 日志与监控系统记录
- 调用方基于共享上下文做业务降级

这样做的好处是：先把最基础的语义做硬，后续再增加可配置策略。

## 为什么失败语义固定为“跳过后继子图”

当前业务场景里，任务依赖关系本身已经表达了数据前置条件。因此当某个任务失败时：

- 继续执行依赖它的下游没有意义
- 直接终止整张图又会误伤无关分支

因此首版内核采用统一语义：

- 失败任务的后继子图全部跳过
- 无关分支继续运行
- 只要出现过任务失败或任务跳过，整图在自然收敛后就进入 `DEGRADED`

这样既符合 DAG 依赖语义，也避免把失败策略设计得过重。

## 为什么超时返回结果而不是抛异常

你的核心业务场景是同步请求线程中的 DAG 聚合，例如商品详情页并发调用多个存在依赖的 RPC。对这种场景来说，“超时”并不一定意味着整个请求完全失败，而更可能意味着：

- 部分任务已经完成
- 共享上下文已经填充了大部分字段
- 页面仍然可以基于已有数据进行降级渲染

因此首版设计选择：

- 用 `GraphRuntimeState.TIMED_OUT` 表达图运行时状态
- 用 `GraphExecutionResult` 返回结构化结果
- 不把超时异常对象直接塞入结果模型

这样既保留了框架级超时语义，也不破坏业务方基于部分结果继续响应的能力。

## 并发与线程模型

首版内核不直接管理线程池策略细节，但要定义与线程池的契约：

- 内核使用图或任务定义中提供的 `ExecutorService`
- 图级线程池可以为空，但最终每个任务都必须解析出有效线程池
- 内核不负责创建、监控或动态调整线程池参数
- 内核不保证共享上下文的线程安全
- 内核必须保证自身运行时状态更新是线程安全的

## Java DSL 方向

首版 DSL 建议统一采用 builder 风格，使图级默认值与任务级覆盖保持一致：

- `TaskGraph.builder(graphId)`
  - `.executor(...)`
  - `.timeoutMillis(...)`
  - `.addTask(TaskDefinition.builder(taskId)...)`
  - `.build()`

- `TaskDefinition.builder(taskId)`
  - `.dependsOn(...)`
  - `.executor(...)`
  - `.timeoutMillis(...)`
  - `.handler(...)`
  - `.errorHandler(...)`
  - `.build()`

- `GraphExecutionRequest.builder()`
  - `.graph(...)`
  - `.context(...)`
  - `.build()`

这样可以保证：

- 线程池与超时的配置位置完全对齐
- 图级配置和任务级覆盖的语义清晰一致
- `GraphExecutionRequest` 保持极简，只承载一次运行真正变化的输入

## Lombok 使用约定

首版 `taskgraph` 内核实现允许并建议使用 Lombok 简化样板代码，尤其是以下类型：

- `TaskDefinition`
- `TaskGraph`
- `GraphExecutionRequest`
- `GraphExecutionResult`
- 内部运行时 DTO / 状态承载对象

推荐使用的 Lombok 注解包括：

- `@Getter`
- `@Builder`
- `@RequiredArgsConstructor`
- `@AllArgsConstructor`
- `@NoArgsConstructor`

使用原则：

- 对定义期和值对象，优先使用 builder 与只读 getter，避免手写大量构造器和访问器
- 对运行时内部状态对象，可按需要使用 `@Getter` / `@Setter`
- 不因为使用 Lombok 而弱化对象边界，是否可变仍然按领域模型语义决定

因此运行时内部建议使用：

- 原子计数器维护剩余依赖数
- 并发安全结构保存任务内部运行状态
- 明确的运行时状态 CAS 或锁机制防止图重复收敛

## 测试策略

实现前就应该明确最低测试集合：

- 合法 DAG 的拓扑执行
- 多根节点执行
- 多依赖汇聚执行
- 缺失依赖校验失败
- 循环依赖校验失败
- 任务异常导致图失败
- 图超时后的终态和结果
- 图超时后保留已写入上下文数据
- 主线程同步阻塞直到完成或超时
- 同一图定义重复执行互不污染

## Risks / Trade-offs

- [原型仓库与新仓库并行存在] → 通过明确区分“`concurrent` 是历史原型、`taskgraph` 是正式主线”降低混淆。
- [先写 spec 看起来会比直接写代码慢] → 将 spec 严格收敛到核心语义，随后快速实现。
- [严格默认语义可能暂时不能满足高级用户] → 预留扩展点，并将高级策略视为后续能力。
- [暂不重做 Spring 设计] → 等内核稳定后，再单独推进接入层变更。
