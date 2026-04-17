# Spring 接入规范

## Purpose
定义 `taskgraph` 在 Spring 容器中的注解式图声明、启动期图编译、自动注册与按 `context` 执行能力，并明确该接入层必须复用内核既有的调度、失败传播、超时处理与结果收敛语义。

## Requirements

### Requirement: 纯 Spring 场景必须通过 `@EnableTaskGraph` 显式开启

当前阶段 Spring 接入层 MUST 以纯 Spring 方案为主，并通过 `@EnableTaskGraph` 作为标准启用入口。

#### Scenario: 调用方通过启用注解显式开启能力

- **WHEN** 调用方希望在纯 Spring 容器中启用 `taskgraph`
- **THEN** 系统必须支持通过 `@EnableTaskGraph` 显式开启该能力

#### Scenario: 启用注解通过导入注册类接入框架基础设施

- **WHEN** Spring 处理 `@EnableTaskGraph`
- **THEN** 系统必须通过 `@Import(...)` 导入框架内部注册逻辑
- **AND** 不应要求调用方手工逐个注册基础设施 Bean

### Requirement: Spring 接入层必须复用内核执行语义

Spring 接入层 MUST 委托 `taskgraph-kernel` 执行图，不得重新定义图调度、失败传播、超时处理和结果收敛语义。

#### Scenario: Spring 发起执行时仍由内核负责调度

- **WHEN** 调用方在 Spring 环境中发起一次图执行
- **THEN** Spring 接入层必须委托内核中的 `GraphExecutor` 完成实际执行

#### Scenario: Spring 不得覆盖内核失败语义

- **WHEN** 某个通过 Spring 注册的图在执行过程中发生任务失败
- **THEN** 图的失败传播和结果状态必须仍然遵循内核规范

### Requirement: Spring 必须支持基于接口和实现类的图声明模型

Spring 接入层 MUST 支持用接口声明图，用实现该接口的 Spring Bean 声明任务节点。

#### Scenario: 图定义声明在接口上

- **WHEN** 调用方定义一张图
- **THEN** 系统必须允许调用方在接口上声明图级元数据

#### Scenario: 任务定义声明在实现类上

- **WHEN** 调用方为某张图提供一个任务节点
- **THEN** 系统必须允许调用方在实现图接口的 Spring Bean 类上声明节点级元数据

### Requirement: 任务执行契约必须通过统一接口约束

Spring 接入层 MUST 提供统一的任务执行契约接口，用于约束节点执行与节点错误处理方法签名。

#### Scenario: 任务实现通过统一接口暴露执行逻辑

- **WHEN** 调用方声明一个任务节点实现
- **THEN** 该实现必须通过统一接口暴露执行方法

#### Scenario: 错误处理通过统一接口暴露

- **WHEN** 某个任务节点需要定义失败后的处理逻辑
- **THEN** 该实现必须通过统一接口暴露错误处理方法，而不是额外依赖字符串方法名

### Requirement: Spring 启动期必须将 DSL 编译成 `TaskGraph`

Spring 接入层 MUST 在容器启动阶段完成图接口扫描、任务实现类收集和 DSL 编译，并将结果注册到统一的 `TaskGraphRegistry` 中。

#### Scenario: 启动期自动编译图定义

- **WHEN** Spring 容器中存在图接口及其对应的任务实现类
- **THEN** 系统必须在应用启动阶段将其编译成 `TaskGraph`

#### Scenario: 启动期自动注册图定义

- **WHEN** 一张图被成功编译为 `TaskGraph`
- **THEN** 系统必须在启动阶段将其注册到 `TaskGraphRegistry`

#### Scenario: 以图接口扫描作为启动入口

- **WHEN** Spring 接入层在容器启动阶段装配图定义
- **THEN** 系统必须先扫描带图定义注解的图接口
- **AND** 再按图接口类型从容器中收集对应的任务实现 Bean

#### Scenario: 默认扫描启用配置类所在包

- **WHEN** 调用方通过 `@EnableTaskGraph` 开启 Spring 接入能力
- **THEN** 系统默认应扫描该配置类所在包及其子包中的图接口

#### Scenario: 调用方可通过 `basePackages` 覆盖默认扫描范围

- **WHEN** 调用方在 `@EnableTaskGraph` 上显式配置 `basePackages`
- **THEN** 系统必须按该包路径集合执行图接口扫描

#### Scenario: 首版不提供额外扫描参数

- **WHEN** 调用方使用 `@EnableTaskGraph`
- **THEN** 系统首版只应支持 `basePackages` 作为可选扫描参数
- **AND** 不应提供 `basePackageClasses` 等额外扫描参数

### Requirement: Spring 启动期必须执行 DSL 校验

Spring 接入层 MUST 在启动阶段完成图接口与任务节点的结构校验，包括图标识、节点标识、依赖关系与执行资源配置校验。

#### Scenario: 启动期拒绝同图重复 `taskId`

- **WHEN** 同一张图下存在两个相同的 `taskId`
- **THEN** 应用必须在启动阶段失败

#### Scenario: 启动期拒绝缺失依赖

- **WHEN** 某个节点依赖了一个不存在于同图中的 `taskId`
- **THEN** 应用必须在启动阶段失败

#### Scenario: 启动期拒绝依赖成环

- **WHEN** 图中的节点依赖关系形成了环
- **THEN** 应用必须在启动阶段失败

#### Scenario: 启动期拒绝无法解析的线程池

- **WHEN** 图级或任务级线程池配置在 Spring 容器中无法解析
- **THEN** 应用必须在启动阶段失败

#### Scenario: 启动期拒绝重复上下文类型绑定

- **WHEN** 两张图解析出了相同的上下文类型
- **THEN** 应用必须在启动阶段失败

#### Scenario: 启动期拒绝空图

- **WHEN** 某个被扫描到的图接口在容器中没有任何实现 Bean
- **THEN** 应用必须在启动阶段失败

#### Scenario: 启动期拒绝一个任务实现类归属于多个图接口

- **WHEN** 某个任务实现类同时匹配多个带图定义注解的图接口
- **THEN** 应用必须在启动阶段失败

#### Scenario: 启动期允许任务实现类附带普通业务接口

- **WHEN** 某个任务实现类除图接口外还实现了普通业务接口
- **THEN** 系统应只按带图定义注解的图接口判定其图归属

#### Scenario: 启动期拒绝非单例或懒加载任务 Bean

- **WHEN** 某个任务 Bean 不是单例或被声明为懒加载
- **THEN** 应用必须在启动阶段失败

### Requirement: Spring 容器必须提供标准执行基础 Bean

Spring 接入层 MUST 至少向容器暴露 `TaskGraphRegistry`、`GraphExecutor`、`ContextGraphRouter`、`TaskGraphRegistrar` 与 `TaskGraphTemplate` 等标准基础设施 Bean，供业务方注入和扩展。

#### Scenario: 业务代码可直接注入注册表

- **WHEN** 业务代码需要查找已注册图定义
- **THEN** Spring 容器必须能够提供 `TaskGraphRegistry` Bean

#### Scenario: 业务代码可直接注入执行器

- **WHEN** 业务代码需要直接使用内核执行器
- **THEN** Spring 容器必须能够提供 `GraphExecutor` Bean

#### Scenario: Spring 容器必须提供执行门面

- **WHEN** 业务代码需要按 `context` 发起图执行
- **THEN** Spring 容器必须能够提供 `TaskGraphTemplate` Bean

#### Scenario: Spring 容器必须提供上下文路由组件

- **WHEN** 框架需要根据上下文类型解析目标图
- **THEN** Spring 容器必须能够提供 `ContextGraphRouter` Bean

#### Scenario: Spring 容器必须提供启动期图注册组件

- **WHEN** 框架需要在启动期编译并注册用户声明的图
- **THEN** Spring 容器必须能够提供 `TaskGraphRegistrar` Bean

#### Scenario: 用户显式提供基础设施 Bean 时优先复用用户实现

- **WHEN** 调用方已显式提供兼容的基础设施 Bean
- **THEN** 框架应优先复用用户实现
- **AND** 不应重复注册冲突的默认基础设施 Bean

### Requirement: 基础设施导入与图注册职责必须分离

Spring 接入层 MUST 区分“框架基础设施导入”和“用户图注册”两个阶段，分别由不同组件承担。

#### Scenario: 导入注册类负责注册框架基础设施 Bean

- **WHEN** `@EnableTaskGraph` 被 Spring 处理
- **THEN** `TaskGraphImportRegistrar` 必须负责把框架基础设施 Bean 注册进容器

#### Scenario: 图注册组件负责编译并注册用户图

- **WHEN** Spring 容器完成基础设施装配后
- **THEN** `TaskGraphRegistrar` 必须负责扫描图接口、收集任务实现并注册用户图

### Requirement: Spring 接入层必须正确处理代理任务 Bean

Spring 接入层 MUST 在任务 Bean 可能被 Spring AOP 代理的情况下，正确完成任务元数据解析与任务执行。

#### Scenario: 任务节点注解基于目标类解析

- **WHEN** 某个任务 Bean 被 Spring 代理
- **THEN** 系统必须基于其目标类解析 `@TaskNodeDefinition`

#### Scenario: 图归属基于带图定义注解的接口判定

- **WHEN** 某个任务 Bean 被 Spring 代理
- **THEN** 系统必须按其目标类实现的带图定义注解接口判定图归属

#### Scenario: 运行时仍调用 Spring Bean 实例

- **WHEN** 某个任务节点在运行时被执行
- **THEN** 系统必须调用 Spring 容器中的 Bean 实例，而不是绕过容器直接调用目标类

### Requirement: Spring 接入层必须支持按 `context` 执行图

Spring 接入层 MUST 提供一个轻量执行门面，使调用方能够仅通过 `context` 发起一次图执行。系统 MUST 基于上下文类型查找唯一绑定的图，并在首版中仅支持精确类型匹配。

#### Scenario: 调用方按 `context` 发起执行

- **WHEN** 调用方向 Spring 执行门面传入一个上下文对象
- **THEN** 系统必须能够基于该上下文类型找到唯一绑定的图并发起一次执行

#### Scenario: 上下文类型未绑定图时快速失败

- **WHEN** 调用方向执行门面传入一个未绑定任何图的上下文对象
- **THEN** 系统必须快速失败，而不是静默忽略该请求

#### Scenario: 首版仅支持精确上下文类型匹配

- **WHEN** 调用方向执行门面传入一个上下文对象
- **THEN** 系统在首版中只应按 `context.getClass()` 进行精确类型匹配，而不是按父类或接口模糊匹配

#### Scenario: 执行门面通过路由索引和注册表解析目标图

- **WHEN** 调用方向执行门面传入一个上下文对象
- **THEN** 系统必须先通过 `contextType -> graphId` 路由索引解析出目标 `graphId`
- **AND** 系统必须再通过 `TaskGraphRegistry` 取得对应的 `TaskGraph`
