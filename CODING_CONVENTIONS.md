# 工程编码约定

本文档用于约束 `taskgraph` 仓库的工程结构、命名和代码组织方式，避免随着功能演进再次回到“模型混杂、职责不清、包名泛化”的状态。

## 1. 包结构约定

- `io.github.caojiantao.taskgraph.kernel.graph`
  只放定义期模型，例如 `TaskGraph`、`TaskNode`、`TaskGraphRegistry`。
- `io.github.caojiantao.taskgraph.kernel.execution`
  只放执行入口、执行请求、执行器工厂等对外执行相关类型。
- `io.github.caojiantao.taskgraph.kernel.result`
  只放结构化结果对象和图运行时状态枚举。
- `io.github.caojiantao.taskgraph.kernel.validation`
  只放定义期校验器、注册期校验器及其契约。
- `io.github.caojiantao.taskgraph.kernel.exception`
  只放框架自定义异常。
- `io.github.caojiantao.taskgraph.kernel.spi`
  只放供调用方实现的处理函数接口。
- `io.github.caojiantao.taskgraph.kernel.internal.*`
  只放内核内部实现细节，例如运行时对象、调度器、超时观察器、默认值等。

## 2. 模型分层约定

- 定义期模型和运行期模型必须分开。
- 定义期模型放在稳定包中，例如 `graph`、`execution`、`result`。
- 运行期内部对象放在 `internal.runtime`、`internal.scheduler`、`internal.timeout` 等包中。
- 不要把运行期状态直接塞回定义期模型。

## 3. `internal` 包约定

- `internal` 包默认视为非稳定 API。
- 外部调用方不应直接依赖 `internal` 包类型。
- 对 `internal` 包中的类进行重构、重命名、移动目录时，不需要承诺兼容性。

## 4. 命名约定

- 接口命名不使用 `I` 前缀，例如使用 `GraphExecutor`，而不是 `IGraphExecutor`。
- 枚举命名不使用 `E` 前缀，例如使用 `GraphRuntimeState`，而不是 `EGraphRuntimeState`。
- 包名中不写阶段性版本标记，例如不写 `v2`、`v3`。
- 命名优先表达职责，而不是表达技术实现细节。

## 5. 禁止的泛化目录

- 不新增泛化 `entity` 包。
- 不新增万能 `util` 包承载核心业务语义。

如果确实需要抽离公共能力，应优先放入职责明确的包中，例如：

- 校验逻辑放入 `validation`
- 异常放入 `exception`
- 执行相关对象放入 `execution`
- 内部支撑能力放入 `internal.support`

## 6. 公共 API 约定

当前公共 API 应保持收敛，优先围绕以下类型演进：

- `TaskGraph`
- `TaskGraphRegistry`
- `TaskNode`
- `GraphExecutionRequest`
- `GraphExecutionResult`
- `GraphRuntimeState`
- `GraphExecutor`
- `TaskHandler`
- `TaskErrorHandler`

新增对外类型前，应先判断是否真的属于稳定公共能力，而不是暂时性的内部实现细节。

## 7. 演进原则

- 先保证语义稳定，再增加外围能力。
- 先保证职责清晰，再增加抽象层次。
- 能通过明确分包解决的问题，不要靠模糊命名硬扛。
- 能通过文档约束减少歧义的问题，不要等到结构失控后再返工。

## 8. 提交信息约定

- `taskgraph` 仓库的提交信息统一使用中文。
- 提交信息推荐使用 `类型: 一句话说明改动` 的格式。
- 一句话说明应直接描述本次提交的主要改动，不写空泛表述。

推荐类型如下：

- `新增`
- `修复`
- `重构`
- `文档`
- `测试`
- `规范`
- `构建`

示例：

- `新增: 初始化 taskgraph 内核基线`
- `修复: 处理任务提交被拒绝后的状态收口`
- `文档: 精简 README 快速开始示例`
- `测试: 收敛执行器单元测试样板代码`
