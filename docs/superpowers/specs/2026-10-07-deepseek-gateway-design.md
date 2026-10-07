# DeepSeek 付费 API 中转设计

## 1. 目标与范围

本期在现有 Spring Boot 单体后端中增加一条 DeepSeek API 中转链路，向已登录的普通用户提供 OpenAI/DeepSeek 兼容的聊天补全接口，并按实际 token 用量扣费。

本期目标：

- 支持 `POST /v1/chat/completions` 和 `GET /v1/models`。
- 支持非流式 JSON 和 SSE 流式响应。
- 使用 DeepSeek 返回的 `usage` 统计输入、输出和总 token。
- 维护普通用户余额、预授权、消费流水、用量记录和余额不足控制。
- 提供管理员配置 DeepSeek、维护模型价格、给用户充值/调整余额和查询账务的接口。
- 保留现有用户、系统用户和 JWT 鉴权边界，不将 DeepSeek API Key 暴露给客户端。

明确不在本期实现：

- DeepSeek 多账号、额度池、账号轮询和故障切换。
- 其他模型供应商的配置与路由。
- 独立网关服务、消息队列和异步账单服务。
- 长期用户 API Key 生命周期。首期沿用普通用户 Access Token；后续需要长期调用凭据时单独设计 API Key 的创建、轮换和撤销。
- 管理端前端页面。先完成后端接口、数据库结构和接口文档，现有控制台后续接入。

## 2. 现有系统约束

- 后端使用 Spring Boot 4、Spring MVC、MyBatis、MySQL 和 Redis。
- 普通用户 Access Token 含 `SCOPE_user`，系统用户管理接口含 `SCOPE_admin`。
- 数据库结构和初始配置的唯一事实来源是 `backend/web_project.sql`，不新增迁移脚本。
- Controller 只处理 HTTP 输入、认证上下文和响应；Service 负责业务规则与事务；Mapper 负责 MyBatis 数据访问。
- 金额不得使用 `float` 或 `double`。本设计使用整数微元保存金额：`1 CNY = 1,000,000 micro-CNY`。
- 供应商密钥沿用现有部署密钥加密能力，数据库和日志均不保存或输出明文。

## 3. 鉴权与对外契约

### 3.1 中转接口

普通用户使用现有 Access Token：

```http
POST /v1/chat/completions
Authorization: Bearer <user-access-token>
Content-Type: application/json
```

客户端可以把 Access Token 作为 OpenAI SDK 的 `api_key` 使用。由于现有 Access Token 默认有效期为 30 分钟，长时间无人值守调用需要由后续 API Key 功能解决。

请求体沿用 OpenAI Chat Completions 结构，首期接受 `model`、`messages`、`stream`、`max_tokens`、`temperature`、`top_p`、`response_format` 等 DeepSeek 支持的字段。网关只校验当前计费和安全所需的字段，并把允许字段转发给 DeepSeek；禁止携带自定义上游 URL 或上游 Authorization。可选的 `Idempotency-Key` 用于客户端重试幂等；未提供时每次请求都视为新的消费请求。

```http
GET /v1/models
Authorization: Bearer <user-access-token>
```

`/v1/models` 只返回管理员启用的 DeepSeek 模型及其基础模型信息，不返回供应商密钥、内部价格或其他用户数据。

### 3.2 用户账务接口

普通用户使用 `SCOPE_user`：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/user/billing/balance` | 查询当前余额、冻结金额和可用余额 |
| GET | `/api/user/billing/transactions` | 分页查询自己的充值、消费、退款和调整流水 |
| GET | `/api/user/billing/usage` | 分页查询自己的模型、token 和费用明细 |
| GET | `/api/user/billing/usage/summary` | 查询时间范围内的 token 与金额汇总 |

所有用户账务查询从 JWT 的 `sub` 读取用户 ID，不接受客户端传入用户 ID。

### 3.3 管理接口

系统用户使用 `SCOPE_admin`：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET/PUT | `/api/sys-user/gateway/deepseek` | 查询或更新 DeepSeek 开关、Base URL 和密钥配置 |
| GET/PUT | `/api/sys-user/gateway/models/{model}` | 查询或更新模型启用状态、输入价格、输出价格和单请求上限 |
| GET | `/api/sys-user/billing/users/{userId}/balance` | 查询指定用户余额 |
| POST | `/api/sys-user/billing/users/{userId}/adjustments` | 充值或调整指定用户余额 |
| GET | `/api/sys-user/billing/transactions` | 按用户、类型和时间分页查询消费流水 |
| GET | `/api/sys-user/billing/usage` | 按用户、模型和时间分页查询用量明细 |

管理员查询只返回密钥是否已配置和脱敏标识，不返回 API Key 明文。价格输入使用 CNY/百万 token，服务端转换为微元保存。

## 4. 业务组件

### 4.1 Gateway 配置

`DeepSeekGatewayConfigService` 负责读取并更新单一 DeepSeek 配置，验证 Base URL 为 HTTPS origin（本地开发可由配置允许 HTTP）、密钥非空、模型上限和价格非负。`DeepSeekClient` 负责向固定 Base URL 发起请求，设置服务端保存的 Authorization，并暴露非流式响应和流式输入流，不参与账务逻辑。

首期不抽象供应商池接口。DeepSeek 配置和客户端属于同一个真实调用方，后续增加供应商时再按实际边界拆分。

### 4.2 Billing Service

`BillingService` 负责钱包读取、预授权、结算、释放和流水查询。预授权与结算均在数据库事务中完成，不能通过 Controller 直接修改余额。

钱包包含可用余额和冻结余额。预授权时原子地执行：

```sql
UPDATE ai_wallet
SET available_micro_cny = available_micro_cny - #{reserveAmount},
    reserved_micro_cny = reserved_micro_cny + #{reserveAmount}
WHERE user_id = #{userId}
  AND available_micro_cny >= #{reserveAmount}
```

受影响行数为 0 时返回稳定的“余额不足”业务错误，不调用 DeepSeek。结算时从冻结余额扣除实际费用，并将差额退回可用余额；上游失败或本地无法建立上游请求时释放全部冻结金额。

### 4.3 Request Orchestrator

`DeepSeekGatewayService` 按以下顺序组织一次请求：

1. 从 JWT 取得普通用户 ID，读取用户状态并校验账户可用。
2. 校验网关启用、模型启用、请求大小、`max_tokens` 和 `stream` 参数。
3. 根据原始请求字节数和输出上限计算保守的预授权 token 数和金额。
4. 以唯一 `request_id` 创建请求记录并冻结余额。
5. 调用 DeepSeek；非流式直接解析 JSON，流式逐段转发 SSE。
6. 从响应 `usage` 读取输入/输出 token，按请求开始时锁定的价格计算实际费用。
7. 在同一账务事务中写入用量记录、消费流水和请求最终状态，释放预授权差额。

预授权输入 token 使用请求 UTF-8 字节数作为保守上界，输出 token 使用请求中的 `max_tokens`，未提供时使用该模型的服务端上限。网关限制请求体大小和输出上限，因此上游实际费用不应超过预授权。实际账单始终以 DeepSeek 的 `usage` 为准；若上游流式响应没有返回 usage，则请求记录标记为 `USAGE_MISSING`，按预授权金额结算并记录异常原因，避免少扣费。

价格在预授权记录中保存快照，管理员修改价格不会改变已发起请求的结算价格。

## 5. 数据模型

所有新表写入 `backend/web_project.sql`，不创建迁移脚本。钱包和成本金额字段使用 `BIGINT UNSIGNED` 保存微元；流水的金额变化字段使用有符号 `BIGINT`，以同时表达充值和扣费；token 字段使用 `BIGINT UNSIGNED`。

### 5.1 `ai_wallet`

每个普通用户一行，以 `user_id` 为主键并关联 `user_account.id`。

| 字段 | 说明 |
| --- | --- |
| `user_id` | 普通用户 ID |
| `available_micro_cny` | 可用余额 |
| `reserved_micro_cny` | 请求预授权冻结金额 |
| `created_at`、`updated_at` | 审计时间 |

注册成功时创建钱包；历史或测试数据在首次访问账务时通过明确的幂等初始化补齐。钱包余额不能直接通过用户资料接口修改。

### 5.2 `ai_gateway_config`

单行 DeepSeek 配置：启用状态、Base URL、加密后的 API Key、更新时间。`provider` 固定为 `deepseek` 并建立唯一约束；本期不保存多个供应商账号。

### 5.3 `ai_gateway_model`

按模型保存启用状态、输入价格微元/百万 token、输出价格微元/百万 token、最大输入字节数和最大输出 token。模型名唯一；删除模型改为停用，避免历史用量失去名称。

### 5.4 `ai_gateway_request`

记录一次中转请求的幂等和预授权状态：

- `request_id` 唯一；同一用户的 `Idempotency-Key`（若提供）建立唯一约束并映射到该请求；
- `user_id`、模型和价格快照；
- 预授权输入/输出 token 上限与金额；
- 实际 token、实际金额和上游 request ID；
- `RESERVED`、`SETTLED`、`RELEASED`、`UPSTREAM_FAILED`、`USAGE_MISSING` 状态；
- 创建和完成时间。

同一 `Idempotency-Key` 的重试返回原请求状态，不重复调用或结算；未提供幂等键的重复提交按独立请求处理。

### 5.5 `ai_usage_record`

一请求一条用量记录，保存输入、输出、总 token，输入/输出价格快照，输入/输出/总成本，模型、用户、状态和时间。用量统计只读取该表，不从流水描述文本反推 token。

### 5.6 `ai_billing_transaction`

不可变账务流水，保存用户、请求 ID（消费/退款时）、类型、正负金额、变更后可用余额、操作人、备注和时间。类型至少包括 `TOP_UP`、`CONSUMPTION`、`REFUND`、`ADJUSTMENT`。消费和退款使用请求 ID 唯一约束，充值/调整使用管理操作 ID 防止重复提交。

## 6. 响应、错误和安全

- 成功 JSON 原样保留 DeepSeek 的 OpenAI 兼容字段，并由网关补充必要的 request ID。
- `stream=true` 使用 `text/event-stream`，逐段转发 `data:` 内容和 `[DONE]`；网关不缓存完整响应。
- DeepSeek 的 4xx/5xx、超时和连接失败转换为稳定网关错误；客户端收到错误时不暴露上游密钥、内部 URL、SQL 或堆栈。
- 余额不足、网关未启用、模型未启用、请求超过上限、上游不可用、usage 缺失分别使用独立业务错误码，前端不依赖中文文案判断状态。
- 日志只记录用户 ID、模型、请求 ID、token 数、金额和状态；不得记录 Authorization、API Key、完整 messages、prompt 或上游响应正文。
- 用户只能读取自己的钱包、流水和用量；管理员可以按用户查询，但不能读取供应商密钥明文。
- 价格、余额和流水写入必须检查影响行数；任何账务事务失败都不能返回成功。

## 7. 事务与故障处理

预授权、请求记录和钱包变更在一个 MySQL 事务中完成。上游调用不放在数据库事务内，避免网络等待长期占用数据库连接；上游成功后的结算是独立事务，并以 `request_id` 幂等。

如果客户端在流式传输中断开，服务端继续关闭上游连接并执行释放/结算逻辑。若进程在上游成功后、结算前崩溃，启动后的轻量定时补偿任务扫描长时间处于 `RESERVED` 的请求：有完整 usage 的按 usage 结算，没有 usage 的按预授权释放并标记为未完成，所有补偿动作写入流水。该任务直接复用 MySQL 幂等状态，不引入消息队列或独立异步账单服务；补偿任务只处理有明确超时条件的请求，避免重复结算。

DeepSeek 返回错误或连接从未建立时，状态为 `UPSTREAM_FAILED`，释放全部冻结金额且不写消费流水。数据库结算失败时不伪造成功响应，保留请求状态供补偿和人工查询。

## 8. 测试与验收

### 单元测试

- 输入/输出 token 价格换算到微元时的舍入和边界。
- 预授权金额计算、余额不足和请求上限校验。
- usage 结算、差额退款和上游失败释放。
- 重复 request ID 不重复扣费。
- 用户与管理员的查询范围和错误码。

### MySQL 集成测试

- 钱包条件更新在余额充足/不足时的受影响行数。
- 同一用户并发预授权时余额不变为负数，成功数量不超过余额可承担金额。
- 消费、退款、充值流水与最终余额一致。
- 唯一约束防止同一请求重复消费。

### HTTP 与启动冒烟

- 用真实应用启动验证 `/v1/models`、非流式中转、流式转发和错误响应。
- 验证普通用户 Token 可访问，系统用户 Token 和匿名请求被拒绝。
- 验证余额不足时 DeepSeek 不收到请求。
- 验证上游超时和 5xx 后余额恢复。
- 验证密钥、Authorization、prompt 和响应正文不进入应用日志。

完成实现后按 `backend/AGENTS.md` 和 `docs/quality-gates.md` 执行规范检查、代码 Review、`mvnw.cmd verify`、数据库集成测试和独立环境 HTTP 冒烟，并同步 `docs/api.md`。

## 9. 后续演进

当实际调用量或产品需求证明需要时，再分别引入：

1. 长期用户 API Key（哈希存储、创建/撤销、最后使用时间和密钥轮换）。
2. 多供应商和 DeepSeek 多账号额度池，按可用余额和限流状态选择账号。
3. 异步账务补偿队列和独立网关服务。
4. 控制台中的余额、价格、用量和流水页面。

这些能力不改变本期 `ai_wallet`、请求幂等和 usage 结算的核心约束。
