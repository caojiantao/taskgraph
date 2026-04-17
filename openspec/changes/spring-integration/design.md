## Context

`single-node-dag-kernel` 已经归档，当前内核语义已经明确：

- 图定义与运行期状态分离
- 图在构建或注册阶段完成校验
- 执行入口采用同步阻塞模型
- 失败任务跳过后继子图，无关分支继续
- 图超时返回结构化结果

Spring 接入层必须建立在上述事实之上，不能重新定义调度语义。它应当解决的是“如何在 Spring 应用里声明和管理图”，而不是“重新实现一套 DAG 运行时”。

当前阶段以纯 Spring 接入为主，先把基于 `@EnableTaskGraph` 的启用、扫描、编译和注册链路做稳；`Spring Boot Starter` 仅作为后续可演进方向，不进入本次主设计。

## Goals / Non-Goals

**Goals**

- 提供基于注解的 Spring 图声明模型。
- 允许用接口声明图、用实现类声明任务。
- 通过统一接口约束任务执行与错误处理。
- 在 Spring 启动期自动把 DSL 编译成 `TaskGraph` 并注册到 `TaskGraphRegistry`。
- 提供仅通过 `context` 触发图执行的 Spring 门面。

**Non-Goals**

- 不在本次变更中引入分布式图管理。
- 不在本次变更中支持动态刷新图定义。
- 不在本次变更中支持控制台或图可视化管理。
- 不在本次变更中引入第二套执行状态或失败语义。

## Decisions

### 1. 图定义放在接口上，任务定义放在实现类上

Spring DSL 采用以下模型：

- 一个带图定义注解的接口表示一张图
- 一个实现该接口的 Spring Bean 表示图中的一个任务节点
- 任务节点的依赖关系、线程池覆盖、超时配置等通过类级任务注解声明

这样做的原因是：

- 图接口负责表达“这组任务属于同一张图”
- 任务实现类负责表达“某个具体节点怎么执行”
- 图和任务仍然强相关，但不会把所有实现逻辑强行塞进一个类里

### 2. 统一任务执行契约为 `GraphTask<C>`

Spring 接入层引入统一接口，例如：

- `GraphTask<C>`

该接口负责约束每个任务节点的执行与错误处理入口。建议接口至少包含：

- `void handle(C context)`
- `default void onError(C context, Throwable cause)`

这样做的原因是：

- 可以在编译期约束任务执行方法签名
- 可以避免字符串 `handler` 引用
- 可以把错误处理并入同一个任务实现类
- 运行时可直接调用接口方法，而不是再做方法级反射调用
- 图的上下文类型也可以直接从 `GraphTask<C>` 的泛型中推导出来，无需在图注解中重复声明

### 3. Spring 启动期将 DSL 编译成 `TaskGraph`

Spring 接入层在启动阶段应完成以下事情：

1. 扫描全部图定义接口
2. 为每个图接口收集所有实现该接口的任务 Spring Bean
3. 读取任务类上的节点元数据
4. 将任务实现类适配成 `TaskDefinition`
5. 将整张图编译成 `TaskGraph`
6. 注册到统一的 `TaskGraphRegistry`

这意味着：

- Spring 层只负责 DSL 解析和编译
- 真正执行时仍然委托 `taskgraph-kernel`
- Spring 不再维护第二套调度器

同时明确采用：

- 以图接口扫描作为启动期装配入口
- 不采用“先收集全部任务 Bean，再反推图归属”的主流程
- 默认扫描范围为 `@EnableTaskGraph` 所在配置类包及其子包
- `@EnableTaskGraph` 仅提供可选 `basePackages` 参数用于覆盖默认扫描范围
- 首版不提供 `basePackageClasses` 等其他扫描参数

### 3.1 纯 Spring 通过 `@EnableTaskGraph` 显式开启

本次设计聚焦纯 Spring 场景，采用显式开启模式：

- 调用方在配置类上声明 `@EnableTaskGraph`
- `@EnableTaskGraph` 通过 `@Import(...)` 导入框架内部注册逻辑
- 框架内部自动注册基础设施 Bean
- 后续再由启动期注册组件完成图扫描、编译和注册

这样做的原因是：

- 纯 Spring 场景缺少 Spring Boot 那种天然的自动装配入口
- `@EnableTaskGraph` 既能表达“显式开启能力”，又能提供稳定的扫描锚点
- 能把“框架基础设施注册”和“用户图注册”拆成两段，职责更清晰

### 4. 错误处理并入任务实现，不单独引入字符串 errorHandler

由于每个任务实现类都已经实现 `GraphTask<C>`，因此错误处理不再额外通过字符串方法名或第二个 Bean 来声明，而是直接复用：

- `onError(C context, Throwable cause)`

这样可以：

- 避免方法名字符串配置
- 提升重构安全性
- 让成功路径与失败路径天然内聚

### 5. 线程池引用仍使用 Spring Bean 名

对于图级和任务级线程池，仍建议使用 Spring Bean 名而不是类型引用。

原因是：

- 线程池 Bean 在 Spring 中通常通过名称区分
- 多个线程池 Bean 往往具有相同实现类型
- 使用名称更符合 Spring 基础设施 Bean 的常见用法

因此：

- 图级线程池在图注解中声明为 `executor`
- 任务级线程池在节点注解中声明为 `executor`

### 6. 首版仅提供按 `context` 触发的 Spring 门面

即使 Spring 层已经支持注解 DSL，业务方在运行时仍然需要一个统一执行入口。

因此建议保留一个轻量门面，例如：

- `TaskGraphTemplate`

职责为：

- 根据 `context` 类型查找已编译注册的图
- 构造 `GraphExecutionRequest`
- 委托内核中的 `GraphExecutor`

这要求 Spring 启动期额外建立一层索引：

- `contextType -> graphId`

同时保持：

- `TaskGraphRegistry` 继续作为唯一的图定义注册中心，负责维护 `graphId -> TaskGraph`
- Spring 接入层只负责把上下文类型路由到目标 `graphId`

并且在首版中强制约束：

- 同一个 `context` 类型只能绑定一张图
- 运行时仅支持按 `context.getClass()` 进行精确类型匹配

## Proposed DSL

### 图定义注解

建议提供类级注解，例如：

- `@TaskGraphDefinition`

建议字段包括：

- `graphId`
- `executor`
- `timeoutMillis`

### 启用注解

建议提供启用注解：

- `@EnableTaskGraph`

建议规则如下：

- 默认扫描启用配置类所在包及其子包
- 允许通过 `basePackages` 显式覆盖默认扫描范围
- 首版不提供 `basePackageClasses` 等额外扫描参数
- 注解本身通过 `@Import(...)` 导入内部注册类，而不是直接承载启动逻辑

### 任务定义注解

建议提供类级注解，例如：

- `@TaskNodeDefinition`

建议字段包括：

- `taskId`
- `dependsOn`
- `executor`
- `timeoutMillis`

### 图接口与任务实现的关系

建议规则如下：

- 一个图接口上必须声明 `@TaskGraphDefinition`
- 一个图接口必须直接继承 `GraphTask<C>`，其上下文类型从泛型参数中推导
- 一个任务实现类必须且只能实现一个带 `@TaskGraphDefinition` 的图接口
- 一个任务实现类允许额外实现普通业务接口，但这些接口不参与图归属判定
- 一个任务类上必须声明 `@TaskNodeDefinition`
- 一个图接口下可以有多个任务实现类

## Proposed Module Structure

- `taskgraph-kernel`
- `taskgraph-spring`

建议 `taskgraph-spring` 中包含：

- Spring 配置入口
- DSL 注解定义
- `GraphTask<C>` 公共接口
- `TaskGraphImportRegistrar`
- 图接口扫描器
- 任务实现收集器
- DSL 编译器
- `contextType -> graphId` 路由索引构建器
- `TaskGraphRegistry` Bean 定义
- `GraphExecutor` Bean 定义
- `ContextGraphRouter` Bean 定义
- `TaskGraphTemplate`
- `TaskGraphRegistrar`

## Runtime Flow In Spring

建议 Spring 接入后的运行流程为：

1. 调用方定义一个图接口，并在接口上声明图注解
2. 调用方定义多个 Spring Bean，实现该图接口
3. 每个任务实现类上声明任务节点注解
4. 调用方通过 `@EnableTaskGraph` 开启能力，默认扫描当前配置类包及其子包
5. Spring 启动时扫描图接口
6. Spring 启动时按图接口从容器中收集全部实现 Bean
7. 读取目标类上的任务节点注解
8. Spring 接入层校验节点元数据和依赖关系
9. Spring 接入层把这组任务编译成 `TaskGraph`
10. 编译完成的图注册到 `TaskGraphRegistry`
11. Spring 接入层为图建立 `contextType -> graphId` 路由索引
12. 业务代码通过 `TaskGraphTemplate.execute(context)` 发起执行
13. Spring 接入层根据 `context.getClass()` 查找唯一命中的 `graphId`
14. Spring 接入层再通过 `TaskGraphRegistry` 获取目标 `TaskGraph`
15. `GraphExecutor` 委托内核同步执行并返回 `GraphExecutionResult`

## Infrastructure Registration Flow

纯 Spring 方案建议明确分成两段注册流程：

### 第一段：框架基础设施注册

- `@EnableTaskGraph` 通过 `@Import(...)` 导入 `TaskGraphImportRegistrar`
- `TaskGraphImportRegistrar` 负责读取 `basePackages`
- `TaskGraphImportRegistrar` 负责向 Spring 容器注册框架基础设施 Bean

建议至少注册：

- `TaskGraphRegistry`
- `GraphExecutor`
- `ContextGraphRouter`
- `TaskGraphRegistrar`
- `TaskGraphTemplate`

### 第二段：用户图注册

- `TaskGraphRegistrar` 在 Spring 启动期运行
- 负责扫描图接口
- 负责按图接口收集任务实现 Bean
- 负责校验、编译并注册用户定义的 `TaskGraph`
- 收集任务 Bean 时按图接口类型从容器获取实例
- 解析任务注解时基于目标类而不是代理类

这样做的原因是：

- `TaskGraphImportRegistrar` 解决“框架怎么进容器”
- `TaskGraphRegistrar` 解决“用户声明的图怎么进注册表”
- 两者职责完全不同，避免一个组件同时承担导入 Bean 和编译图两类职责

## Validation Rules

建议 Spring 启动期至少校验以下规则：

- 图接口必须声明 `graphId`
- 图接口必须直接继承 `GraphTask<C>`，并且上下文类型必须能够从泛型参数中明确推导
- 图接口配置的图级线程池若存在，必须能在容器中解析
- 同一个上下文类型只能绑定一张图
- 扫描到的图接口必须至少能在容器中收集到一个任务实现 Bean
- 任务实现类必须声明 `taskId`
- 同一张图内 `taskId` 不可重复
- `dependsOn` 不得引用不存在的任务
- 图依赖关系不得成环
- 任务最终必须能解析出有效线程池
- 一个任务实现类必须且只能归属于一个带图注解的图接口
- 任务 Bean 必须是单例，且首版不支持 `prototype` 与 `@Lazy`

## Bean Registration Strategy

框架基础设施 Bean 建议采用“用户优先，框架兜底”的注册策略：

- 当调用方未显式提供对应基础设施 Bean 时，框架注册默认实现
- 当调用方已显式提供可兼容的基础设施 Bean 时，框架不再重复注册默认实现

建议首版至少对以下基础设施采用该策略：

- `GraphExecutor`
- `TaskGraphRegistry`
- `ContextGraphRouter`
- `TaskGraphTemplate`

这样做的原因是：

- 允许调用方按需替换基础设施实现
- 避免框架默认实现和用户自定义实现发生重复注册冲突
- 更符合 Spring 生态下“默认装配 + 用户覆盖”的使用习惯

## Key Roles

为避免命名相近导致理解混淆，建议明确区分以下两个核心角色：

- `TaskGraphImportRegistrar`
  - 负责响应 `@EnableTaskGraph`
  - 负责把框架内部基础设施 Bean 注册进 Spring 容器
  - 不负责扫描和编译用户图

- `TaskGraphRegistrar`
  - 负责在启动期扫描图接口、收集任务 Bean、编译并注册 `TaskGraph`
  - 不负责导入框架基础设施 Bean

## Proxy Handling Strategy

考虑到任务 Bean 可能被 Spring AOP 代理，首版建议明确采用以下规则：

- 任务 Bean 的收集按图接口类型从容器获取
- 任务节点注解的解析基于目标类
- 运行时真正执行任务时仍调用 Spring 容器中的 Bean 实例

这样做的原因是：

- 可以正确读取实现类上的 `@TaskNodeDefinition`
- 不会因为代理类干扰而误判图归属或节点元数据
- 仍然保留 AOP、事务、监控等 Spring 增强能力

## Open Questions

当前建议先不在本次 change 中解决以下问题：

- 一个图接口是否允许存在多个实现类层次
- 是否支持条件化注册任务节点
- 是否支持多注册表隔离
- 是否需要在后续支持按父类或接口匹配上下文类型

## Indexing Strategy

为避免 Spring 层和内核层各自维护一份完整的图对象索引，建议明确采用“两层索引”模型：

- 主索引：`TaskGraphRegistry` 维护 `graphId -> TaskGraph`
- 辅助索引：Spring 门面维护 `contextType -> graphId`

这样做的原因是：

- `graphId` 才是图定义的稳定身份标识
- `contextType` 只是便捷触发入口，不应承担图对象存储职责
- 可以避免 Spring 层再次维护 `contextType -> TaskGraph`，减少重复状态
- 后续如果增加按 `graphId` 管理、观测或调试能力，也仍然可以围绕统一注册表展开

这些问题可以在 Spring 接入第一版落地后，再拆新的 change 继续推进。
