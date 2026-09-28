# 普通用户认证体系重构方案

> 状态：**待验收（已按 2026-09-28 下午最新确认修订）**。本方案经确认后才会开始编码。
> 涉及表结构变更、认证授权、接口新增与文档重写，属较大范围改动。
>
> 本次修订要点：
> - 对外编号由 `user_code` 更名为 **`user_no`**，并补全生成规则（见 4.1.1）与特殊号码过滤规则（见 4.1.2）。
> - 明确**项目为新项目、无历史数据，不做任何兼容设计**（见 8.1）。
> - 新增 **`USER_NO_SECRET`** 独立配置，**不复用 JWT secret**（见 4.1.3）。
> - **启用 Redis**：验证码全部改为 Redis 存储（见 4.2）；Refresh Token 状态也存 Redis（见 5.6）。
> - **取消 `verification_codes`、`auth_channel_config` 两张表**，改为统一 `sys_config`（见 4.3）。
> - 渠道开关**只迁移存储位置，不增加业务粒度**，仍为原来的两级开关语义（见 4.3）。
> - **Refresh 采用标准双 Token 模式**（访问令牌 + 刷新令牌），rotation 轮换、退出登录吊销**当前会话**、**多会话并存**（Web / App / 多浏览器各自独立 sessionId，新登录不覆盖已有会话），见 5.6。
> - 已确认：防刷每日上限 **10 条/天**（自然日）；注册时 `password` **可选**（见 8）。
>
> **状态：待确认事项已全部清零**，可进入编码阶段。

## 1. 背景与目标

现有实现是「账号 + 密码」单通道登录，`user_account` 表以 `username` 作为账号标识。本次重构将普通用户认证改造为**手机号 / 邮箱双渠道体系**，并支持渠道的可配置开关。

重构后的能力范围：

- 手机号、邮箱两种身份渠道，均支持密码登录与验证码登录。
- 用户自助注册。
- 重置密码（前端在登录页称「忘记密码」，在用户主页称「修改密码」）。
- 换绑手机号 / 邮箱。
- 渠道开关后台可配置（存于统一 `sys_config`）。
- 对外用户号码 `user_no`（当前 6 位纯数字，可扩展）。
- 验证码存储与防刷基于 Redis。
- 标准双 Token 会话：访问令牌短有效期 + 刷新令牌长有效期，支持轮换与吊销。
- 退出登录（删除会话）。

**本项目为新项目，无历史数据。** 因此本次改造不做任何向后兼容设计：表结构直接按目标形态重建，旧列不保留，接口路径直接就位、不留兼容别名，也不需要编写数据迁移脚本（只需要结构变更脚本供已建库的开发环境同步）。

## 2. 核心概念定义

### 2.1 身份渠道（Auth Channel）

指用户可用于身份验证的联系方式类型，当前为 `PHONE` 和 `EMAIL` 两种。

### 2.2 渠道配置的两级开关

每种渠道有**两个独立开关**，语义与旧方案完全一致，本次只改存储位置（表 → `sys_config`），**不拆分业务粒度**：

| 开关 | 配置键 | 作用 |
|------|--------|------|
| 渠道总开关 | `auth.phone.enabled` / `auth.email.enabled` | 控制该渠道全部业务能力，包括密码登录、注册、绑定、换绑 |
| 验证码子开关 | `auth.phone.code.enabled` / `auth.email.code.enabled` | 仅控制该渠道的验证码发送与校验能力 |

两个开关的关系：**验证码开关是渠道总开关的下级**。渠道总开关关闭时，验证码功能必然不可用（无论验证码子开关取何值）。

> **设计原则（重要）**：注册、验证码登录、重置密码、换绑等业务能力**全部由这 4 个基础配置推导**，**不再增加** `auth.register.*` / `auth.login.*` / `auth.reset-password.*` / `auth.rebind.*` 这类按场景拆分的独立开关。推导规则见 4.3.2。

### 2.3 可用于身份验证的联系方式

用户进行敏感操作（重置密码、换绑联系方式）时，需通过已绑定的手机号或邮箱完成身份验证。

一个联系方式可作为验证方式，当且仅当同时满足三个条件：

1. 用户**已绑定**该联系方式；
2. 该系统**渠道总开关已启用**；
3. 该渠道**验证码功能开关已启用**。

即：`已绑定 + 系统启用 + 验证码功能启用`。

### 2.4 术语对应（后端与前端命名）

后端只实现**一套**重置密码逻辑，前端根据入口使用不同名称：

| 后端（统一命名） | 前端入口 | 用户可见名称 |
|------------------|----------|--------------|
| `RESET_PASSWORD` 场景与接口 | 登录页 | 忘记密码 |
| `RESET_PASSWORD` 场景与接口 | 用户主页 / 个人中心 | 修改密码 |

底层逻辑、验证码场景值、数据表字段完全共用。

## 3. 关键约束（必须遵守）

以下为需求中的硬性约束，实现时不得违反：

1. **关闭渠道不得删除数据**。停用手机号或邮箱功能时，只停止该渠道的业务能力，**不得删除用户已绑定的手机号或邮箱数据**。
2. **不得同时关闭所有渠道**。若系统当前只有手机号和邮箱两种认证渠道，后台不得将两者的渠道总开关同时关闭（需保留至少一个可用渠道）。
3. **敏感操作必须验证**。重置密码、换绑联系方式均需通过已绑定的联系方式完成验证码验证。
4. **换绑需双向验证**。换绑流程为：旧身份验证通过 → 新联系方式验证码验证通过 → 完成换绑。两个环节缺一不可。
5. 后端只实现一套重置密码逻辑，不因前端入口不同而拆分业务。
6. **`user_no` 不参与登录**。登录标识只有 `phone` / `email` 两种，`user_no` 仅对外展示。
7. **不写死 6 位**。`user_no` 的列宽、生成逻辑、特殊号规则都必须支持将来扩展到 10 / 12 / 16 位，扩展时不需要改表结构。

## 4. 数据库设计

### 4.1 user_account（改造）

本表为**全新结构**，项目为新项目、无历史数据，不做任何兼容设计（不保留旧列、不保留旧接口、不写数据迁移）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT UNSIGNED | PK, NOT NULL | 用户 UID，雪花 ID，服务端生成 |
| `user_no` | VARCHAR(32) | NOT NULL, UK | 对外用户号码，服务端生成，仅对外展示，**不用于登录** |
| `phone` | VARCHAR(20) | NULL, UK | 手机号 |
| `email` | VARCHAR(100) | NULL, UK | 邮箱 |
| `nickname` | VARCHAR(50) | NULL | 用户昵称 |
| `avatar` | VARCHAR(255) | NULL | 头像地址 |
| `password` | VARCHAR(100) | NULL | BCrypt 密文；纯验证码注册的用户为空 |
| `status` | TINYINT | NOT NULL DEFAULT 1 | 0 禁用，1 正常 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

索引：

```sql
PRIMARY KEY (id),
UNIQUE KEY uk_user_account_user_no (user_no),
UNIQUE KEY uk_user_account_phone (phone),
UNIQUE KEY uk_user_account_email (email),
KEY idx_user_account_status (status)
```

**唯一约束说明**：`phone`、`email` 均为可空列且带唯一约束。MySQL 允许多个 NULL 存在于唯一索引中，因此「未绑定手机号的用户可以有任意多个，已绑定的不可重复」这一语义自然成立。

**本表不含**：`username`。登录标识由 `phone` / `email` 承担，`user_no` 不参与登录。

**列宽与长度解耦**：`user_no` 用 `VARCHAR(32)` 而非 `CHAR(6)`。当前实际生成 6 位号码，但列宽、代码都**不得写死只能支持 6 位**，为将来扩展到 10 / 12 / 16 位预留能力。扩展时只需调整生成配置，**不需要改表结构**。

> `nickname` 和 `avatar` 保留。两者均属于用户自身资料，由 `PUT /api/user/me` 维护，不参与身份验证。

### 4.1.1 user_no 生成规则

`user_no` 是**对外展示的用户号码**，当前形态为 6 位纯数字 `PPXXXX`。

| 组成部分 | 长度 | 生成方式 |
|----------|------|----------|
| `PP` | 2 位 | **每日号段**：由「日期 + 固定 secret」确定性计算，映射到 `10~99` |
| `XXXX` | 4 位 | 随机生成 `0000~9999` |

规则细则：

1. `PP` 由日期与 secret 确定性计算得出，**同一天注册的用户使用相同号段**。
2. **不单独建立号段表**，号段由算法得出，不落库。
3. 号段本身**允许重复**（不同日期可能落在同一号段），只要求**完整 `user_no` 全局唯一**。
4. `XXXX` 由随机数生成，取值 `0000~9999`。
5. 生成时**直接过滤特殊号码**，**不单独建靓号表**。
6. 特殊号码的判定规则**集中放在代码配置中**（见 4.1.2），方便后续增删；**不写死在数据库**，数据库只保留 `user_no` 的唯一索引。
7. 最终 `user_no` 必须有**唯一索引**兜底；若插入时发生唯一冲突，**重新生成后 4 位再尝试**。

**校验与冲突处理（与 `docs/engineering.md`「唯一性交给数据库约束」一致）**：

- 唯一性**不靠先查后插**，靠 `uk_user_account_user_no` 兜底。
- 插入捕获唯一键冲突后，重新生成 `XXXX` 并重试；重试次数设上限（建议 5 次），超出则抛出服务端异常，**不得静默失败或返回重复号码**。
- 过滤特殊号码只是**降低重试概率**，不是唯一性保证手段。

> 扩展说明：`PP` 位数、随机位数由配置决定（当前 `2 + 4`）。将来要 10 / 12 / 16 位时，只需调整配置中的随机位数与特殊号规则，`VARCHAR(32)` 足够容纳，生成逻辑无需重写。

### 4.1.2 特殊号码过滤规则（配置化）

以下规则集中配置在代码中，**不写入数据库**。生成时命中任一条即重试。

| 类别 | 说明 | 示例 |
|------|------|------|
| 连号 / 重复号 | 后 4 位（或整体）全部相同 | `0000`、`1111`、`6666`、`8888`、`9999` |
| 顺子 / 倒序 | 连续递增或连续递减 | `1234`、`2345`、`4321`、`9876` |
| 特殊含义组合 | 有明确谐音或通用含义的数字 | `520`、`521`、`1314`、`666`、`888`、`999`、`250` |
| 完整号码特殊组合 | 「完整 user_no」出现上述特殊形态时同样过滤 | 号码整体构成连号/顺子/特殊含义 |

规则以**数据配置**形式维护（列表 + 可见性判定），新增或删除特殊号码只改配置，不改生成逻辑。

### 4.1.3 号段 secret 配置（`USER_NO_SECRET`）

`PP` 号段的确定性计算需要一个 secret。**该 secret 独立配置，不复用 JWT 签名密钥。**

配置项：

```yaml
app:
  user-no:
    # 号段计算密钥，仅用于「日期 + secret → 每日号段 PP」的确定性映射
    secret: ${USER_NO_SECRET:}
    # 号段映射区间（当前 10~99，两位数）
    daily-segment-min: 10
    daily-segment-max: 99
    # 随机部分位数（当前 4 位），扩展位数时只改这里
    random-digit-count: 4
    # 唯一冲突后的重试次数上限
    max-retry: 5
```

**为什么必须与 JWT secret 分开**：

| 维度 | JWT secret | `USER_NO_SECRET` |
|------|-----------|-----------------|
| 用途 | 签发、校验访问令牌 | 计算每日号段 `PP` |
| 轮换频率 | 可轮换（换密钥会让旧 Token 失效，属预期安全操作） | **上线后保持稳定，不参与轮换** |
| 轮换的后果 | 用户需重新登录，可接受 | 同日号段计算值改变，导致**同一天注册的用户号段不一致** |

**关键约束**：

1. `USER_NO_SECRET` **上线后保持稳定**，不得随 JWT 密钥轮换而变更，避免影响用户号段生成。
2. 不得复用 JWT secret 或任何其他已有密钥，避免一处轮换波及另一处职责。
3. 与 `JWT_SECRET` 同样通过环境变量注入，不写入 `pom.xml`；本地开发可给默认值，部署环境必须显式配置。
4. 建议用至少 32 个安全随机字节并编码为 Base64，生成方式与 `JWT_SECRET` 相同（见 `docs/engineering.md`）。

> 补充说明：号段本身**允许重复**，且唯一性由 `uk_user_account_user_no` 兜底，因此即便 secret 意外变更也**不会产生重复 `user_no`**，影响仅限于「同一天内用户的号段不再统一」。该影响可接受，但既然成本极低，仍按「保持稳定」执行。

### 4.2 Redis 存储设计

Redis 在本方案中承担**两块**职责：

1. **验证码**：存储 + 防刷（本节 4.2.1 ~ 4.2.3）
2. **Refresh Token 会话状态**：会话存储 + 吊销（见 5.6.2）

#### 4.2.0 全部 Redis key 一览

统一使用 `auth:` 命名空间，两块职责互不冲突：

| 类别 | 用途 | key | TTL |
|------|------|-----|-----|
| 验证码 | 验证码 | `auth:code:{scene}:{targetType}:{target}` | 5 分钟 |
| 验证码 | 60 秒发送锁 | `auth:code:send-lock:{scene}:{targetType}:{target}` | 60 秒 |
| 验证码 | 每日发送次数 | `auth:code:daily:{targetType}:{target}:{yyyyMMdd}` | 到当日结束（见 4.2.2） |
| 验证码 | 校验失败次数 | `auth:code:fail:{scene}:{targetType}:{target}` | 与验证码同生命周期 |
| 会话 | 会话主记录（**每个 session 一个独立 key**） | `auth:session:{userId}:{sessionId}` | refreshToken 有效期（默认 30 天） |
| 会话 | 用户会话索引（**ZSET**，member=`sessionId`，score=会话过期时间戳） | `auth:session:user:{userId}` | 随会话续期刷新 |

> **会话类 key 的 `sessionId`**：每次成功登录生成，同一用户可有多个（Web / App / 多浏览器各一个），**新登录不覆盖已有会话**（见 5.6.5）。
>
> **不再使用 `auth:session:revoked:{jti}`**：当前 session 记录中保存的有效 `jti` 本身即可判断旧 Refresh Token 是否失效（见 5.6.3），**本轮不额外维护 revoked key**。

#### 4.2.1 验证码操作语义

- **发送**：`SET auth:code:... <摘要> EX 300` 写入验证码；同时 `SET auth:code:send-lock:... 1 EX 60` 落发送锁；`INCR auth:code:daily:...` 累加当日计数。
- **发送前校验**：`send-lock` 存在 → `CODE_SEND_TOO_FREQUENT`；`daily` 计数 ≥ 10 → `CODE_SEND_LIMIT_EXCEEDED`。
- **校验成功**：**原子删除**验证码 key（`DEL`），返回删除数量判断是否抢到本次校验，**删除成功即视为使用完成**，天然防止重复使用。同时清理 `fail` 计数。
- **校验失败**：`INCR auth:code:fail:...`；达到 **5 次**时**立即删除验证码 key**，该验证码作废，用户须重新发送。
- **校验前判断**：验证码 key 不存在 → 视为「错误或已失效」（`VERIFICATION_CODE_INVALID`，已过期也归入此码，不再单独区分过期，因为 Redis 已自动清理）。

> **与原表方案的对比**：原方案用 `used_at IS NULL` 条件更新来判断防重放，Redis 用 `DEL` 的返回数量达到同样效果，且更简洁——不需要 `expires_at`（TTL 自动）、不需要 `used_at`（DEL 即消费）、不需要 `fail_count` 列。

> **验证码不再存 MySQL**，原 `verification_codes` 表**直接删除**，不保留。

**4.2.2 每日上限必须按自然日（重要）**

`auth:code:daily:*` 的 TTL **不能简单设为固定 86400 秒**，否则会变成「首次发送后滚动 24 小时」，与「自然日 10 条」的语义不符。

正确做法：TTL 设为**距当天 24:00 的剩余秒数**（可额外留少量缓冲，例如次日 00:05）。这样每天 00:00 后计数自然归零，且 key 不会长期堆积。

key 内已带 `{yyyyMMdd}`，即便上一日的 key 因故未及时过期，也不会与新一日互相污染——但 TTL 仍按上述方式设置，避免无谓的 key 堆积。

**4.2.3 不落库的取舍（已确认）**

- 验证码发送历史**不落 MySQL**，**不提供历史审计与统计**。这是本轮明确接受的代价。
- 打桩阶段（见 7.1 `LoggingCodeSender`）本身就没有真实发送，因此无审计需求。
- 若将来确实需要审计，**单独增加验证码发送事件日志**（新表或日志采集），**不把验证码主存储重新放回数据库**。
- 因此 Redis 需按「**验证码丢失即用户需重新发送**」来对待：Redis 不做持久化也不会造成数据一致性问题（无历史数据依赖）。

### 4.3 sys_config（新建，统一系统配置表）

**取消独立的 `auth_channel_config` 表**，认证渠道开关合并进统一的 `sys_config`。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 主键 |
| `config_key` | VARCHAR(100) | NOT NULL, UK | 配置键，如 `auth.phone.enabled` |
| `config_value` | VARCHAR(255) | NOT NULL | 配置值，布尔配置统一存 `'1'` / `'0'` |
| `config_group` | VARCHAR(50) | NOT NULL | 分组，如 `AUTH` |
| `remark` | VARCHAR(255) | NULL | 配置说明 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

索引：

- `UNIQUE KEY uk_sys_config_config_key (config_key)`
- `KEY idx_sys_config_config_group (config_group)` —— 按分组批量读取

**4.3.1 初始化数据（4 条，仅此 4 条）**

| config_key | config_value | config_group | remark |
|------------|--------------|--------------|--------|
| `auth.phone.enabled` | `1` | `AUTH` | 手机号渠道总开关，控制该渠道登录、注册、绑定、换绑等全部能力 |
| `auth.phone.code.enabled` | `1` | `AUTH` | 手机号验证码功能开关，仅控制该渠道验证码发送与校验 |
| `auth.email.enabled` | `1` | `AUTH` | 邮箱渠道总开关，控制该渠道登录、注册、绑定、换绑等全部能力 |
| `auth.email.code.enabled` | `1` | `AUTH` | 邮箱验证码功能开关，仅控制该渠道验证码发送与校验 |

**不增加**按场景拆分的键（如 `auth.register.phone.enabled`）。所有业务能力由这 4 个键推导，推导规则见 4.3.2。

**4.3.2 业务能力推导规则**

| 业务能力 | 依赖条件 |
|----------|----------|
| 某渠道注册 | 该渠道总开关启用 **且** 该渠道验证码子开关启用（注册必须验码） |
| 某渠道密码登录 | 该渠道总开关启用 |
| 某渠道验证码登录 | 该渠道总开关启用 **且** 该渠道验证码子开关启用 |
| 某渠道重置密码 | 该渠道总开关启用 **且** 该渠道验证码子开关启用（重置必须验码） |
| 某渠道换绑 | 被换绑的目标渠道：总开关启用 + 验证码子开关启用；旧身份验证渠道：见 5.5 三条件 |

即：**验证码子开关是总开关的下级**，凡依赖验证码的能力都要求两者同时启用。

**4.3.3 「不得同时关闭所有渠道」的校验（简化）**

只检查 `auth.phone.enabled` 与 `auth.email.enabled` **两者不能同时为 `0`**。

**不需要** `auth.rebind.require-available-channel` 之类的额外配置项。验证码子开关（`*.code.enabled`）**不参与**这条校验。

**4.3.4 配置读取方式**

- **本轮 `sys_config` 直接读 MySQL，不做 Redis 缓存**。渠道开关变更频率低、查询量可控，暂不引入缓存一致性成本；后续出现实际性能需求再加缓存。
- `sys_config` **只保存非敏感业务配置**。
- **不得放入 `sys_config` 的敏感配置**：JWT Secret、`USER_NO_SECRET`、Redis 密码、第三方 API Secret 等——这些**仍通过环境变量管理**（见 4.1.3 与 `docs/engineering.md`）。

**4.3.5 管理端配置值约定**

- 布尔配置在数据库统一存 `'1'` / `'0'` 字符串，**不存 `true`/`false`**，与现有 `TINYINT` 开关习惯一致（`status`、`channel_enabled` 均为 0/1）。
- 读写转换在 Service 层完成，对外接口（`/api/sys-user/auth-channels`）仍返回 JSON 布尔值（见 5.5）。

> **备选方案**：若已有统一配置中心，可将配置存于其中而不建表。当前按建表执行。

## 5. 接口设计

### 5.1 接口清单

接口按职责分为两组：**认证类**（`/api/user/auth/**`，只负责注册、登录、验证码、重置密码、Token）和**用户资源类**（`/api/user/me/**`，当前登录用户的信息与账号管理）。

#### 认证类

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| POST | `/api/user/auth/register` | 匿名 | 用户注册（返回双 Token） |
| POST | `/api/user/auth/login` | 匿名 | 登录（密码或验证码，由请求体区分；返回双 Token） |
| POST | `/api/user/auth/send-code` | 匿名 | 发送验证码 |
| POST | `/api/user/auth/reset-password` | 匿名 | 重置密码（前端：忘记密码） |
| POST | `/api/user/auth/refresh` | **匿名** | 用 `refreshToken` 换取新双 Token（见 5.6） |
| POST | `/api/user/auth/logout` | `SCOPE_user` | 退出登录，**只吊销当前会话**（见 5.6.9） |

#### 用户资源类

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| GET | `/api/user/me` | `SCOPE_user` | 获取当前用户信息 |
| PUT | `/api/user/me` | `SCOPE_user` | 修改个人资料（昵称、头像） |
| PUT | `/api/user/me/password` | `SCOPE_user` | 修改密码（前端：修改密码） |
| PUT | `/api/user/me/phone` | `SCOPE_user` | 换绑手机号 |
| PUT | `/api/user/me/email` | `SCOPE_user` | 换绑邮箱 |

#### 管理端

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| GET | `/api/sys-user/auth-channels` | `SCOPE_admin` | 查询渠道配置 |
| PUT | `/api/sys-user/auth-channels/{channel}` | `SCOPE_admin` | 修改渠道配置 |
| GET | `/api/sys-user/users` | `SCOPE_admin` | 普通用户列表（已有） |

#### 设计说明

- **路径归属原则**：`/auth/**` 只承载认证动作；当前登录用户自身的信息与账号管理属于用户资源，归入 `/me/**`。故原 `/api/user/auth/me` 改为 `GET /api/user/me`，**不保留兼容接口**。
- **登录接口合并**：原方案中密码登录与验证码登录分为两个路径，现统一为 `POST /api/user/auth/login`，由请求体字段区分：
  - 携带 `password` → 密码登录
  - 携带 `code` → 验证码登录
  - 两者都传或都不传 → 参数错误。具体判定规则见 5.5。
- **换绑拆分为两个接口**：`/me/phone` 与 `/me/email` 各自独立。原因见 5.5 换绑章节——两个接口的"新联系方式"校验对象不同，拆分后语义清晰，也便于前端按入口直接调用。
- **`refresh` / `logout` 与 Token 机制**：见 5.6。`refresh` **匿名访问**（靠 `refreshToken` 自身鉴权，此时 `accessToken` 通常已过期）；`logout` **需要登录态**。
- **前端同步**：当前前端为开发阶段，路径变更直接同步修改前端，不做兼容层。
- **实际结论**：经实测，前端目前**未接入任何普通用户接口**，因此本次路径变更对现有前端零影响，详见第 10 节。

### 5.2 安全配置调整

**只放行认证类接口，不放开整个 `/api/user/**`。**

```java
// 匿名放行（仅这几个具体路径）
.requestMatchers(HttpMethod.POST,
        "/api/user/auth/register",
        "/api/user/auth/login",
        "/api/user/auth/send-code",
        "/api/user/auth/reset-password",
        "/api/user/auth/refresh")
.permitAll()

// 其余用户端接口仍需 user 权限
.requestMatchers("/api/user/**").hasAuthority("SCOPE_user")
```

顺序很重要：匿名规则必须写在 `/api/user/**` 通配规则**之前**，否则通配规则会使匿名规则失效。

**`/api/user/auth/logout` 不在放行清单内**——它需要 `SCOPE_user` 权限。

> **另一个必须做的安全校验**：资源服务器的 JWT 校验必须**拒绝 `tokenType != access` 的令牌**。否则 `refreshToken` 会被当成有效访问令牌直接调用业务接口，绕过整个会话机制。详见 5.6.10。

### 5.3 接口详细定义

#### POST /api/user/auth/send-code

发送验证码，注册 / 验证码登录 / 重置密码三场景共用。

请求：

```json
{
  "target": "13800138000 或 user@example.com",
  "targetType": "PHONE",
  "scene": "REGISTER"
}
```

响应：统一 `Result<Void>`，不返回验证码本身。

校验规则（按序）：

1. `targetType` 对应渠道总开关必须启用（读 `auth.phone.enabled` / `auth.email.enabled`），否则返回 `AUTH_CHANNEL_DISABLED`。
2. `targetType` 对应验证码子开关必须启用（读 `auth.phone.code.enabled` / `auth.email.code.enabled`），否则返回 `CODE_FUNCTION_DISABLED`。
3. **防刷间隔**：检查 `auth:code:send-lock:{scene}:{targetType}:{target}` 是否存在，存在则返回 `CODE_SEND_TOO_FREQUENT`（60 秒内只能发一次）。
4. **防刷上限（已确认）**：检查 `auth:code:daily:{targetType}:{target}:{yyyyMMdd}` 计数是否 ≥ **10**，超出返回 `CODE_SEND_LIMIT_EXCEEDED`。**按自然日统计**，TTL 到当日结束（见 4.2.2）。
5. 场景与目标的一致性校验：
   - `REGISTER`：该 `target` **不得**已被注册，否则返回 `TARGET_ALREADY_REGISTERED`。
   - `LOGIN`、`RESET_PASSWORD`：该 `target` **必须**已注册，否则返回 `TARGET_NOT_REGISTERED`。
6. 生成验证码并写入 Redis，同时落发送锁、累加当日计数（见 4.2.1）。

> 安全提示：第 5 条会暴露「某手机号是否已注册」，这是注册/登录流程的必要代价，业界普遍接受。若需规避，可统一返回发送成功但不实际发送，代价是用户体验下降。

> 阈值（60 秒、10 条/天）可配置，不写死在代码里，便于上线后按实际短信成本调整。

### 5.4 验证码的通用规则（Redis 实现）

**校验流程**（三个场景共用）：

1. 读取 `auth:code:{scene}:{targetType}:{target}`。
2. key 不存在 → 返回 `VERIFICATION_CODE_INVALID`（**已过期与码错误统一归入此码**，Redis 已自动清理过期 key，不再单独区分 `VERIFICATION_CODE_EXPIRED`，该错误码保留但仅在确需区分时使用）。
3. 码不匹配 → `INCR auth:code:fail:...`，返回 `VERIFICATION_CODE_INVALID`。
4. 码匹配 → **原子 `DEL` 验证码 key**，依据删除数量判断是否抢到本次校验；删除成功即校验通过并消费验证码，同时清理 `fail` 计数。

**防重复使用**：由第 4 步的 `DEL` 保证——**删除成功才视为使用完成**，同一验证码不可能被二次使用。这替代了原表方案的 `used_at IS NULL` 条件更新。

**失败计数规则**（防暴力破解）：

- 每次校验失败，`INCR auth:code:fail:{scene}:{targetType}:{target}`。
- 计数达到 **5 次**时，**立即删除验证码 key**，该验证码作废，后续校验一律失败，用户须重新发送。
- `fail` key 与验证码 key 同生命周期（验证码被删除或过期后，fail 计数一并清理，避免遗留）。

**并发与竞态说明**：Redis 的 `DEL` 是原子操作，天然解决「校验通过到标记已使用之间的窗口」问题，**不需要**原表方案里的「条件更新 + 受影响行数」技巧。这是改用 Redis 带来的实际简化。

**边界情况：验证码与目标变更**

若用户请求发送验证码后、在提交校验前修改了 `target`（例如前端允许换手机号重发），**校验必须以提交时的 `target` + `targetType` + `scene` 拼出的 key 为准**重新定位，不存在「跨 target 复用验证码」的可能。

**边界情况：`password` 为 NULL 的用户**

纯验证码注册的用户 `password` 为 NULL。此时：

- 密码登录一律失败，返回 `PASSWORD_NOT_SET`（`40103`）提示改用验证码登录。
- 重置密码接口可将 `password` 从 NULL 更新为具体值，即完成「补设密码」。
- 判定不可写作 `passwordEncoder.matches(dto.password(), null)`，BCrypt 对 NULL 会抛异常，须先显式判空。

### 5.5 各接口定义

#### POST /api/user/auth/register

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456",
  "password": "可选字段，不传或传空表示仅用验证码登录"
}
```

**`password` 为可选字段（已确认）**：

| `password` | 结果 |
|------------|------|
| 有值 | BCrypt 加密后写入 `user_account.password`，该用户可密码登录或验证码登录 |
| 不传 / 空 | `password` 存 NULL，该用户**只能验证码登录**；后续可用重置密码接口补设密码 |

纯验证码注册的用户在尝试密码登录时返回 `PASSWORD_NOT_SET`（`40103`），提示改用验证码登录。

处理流程：

1. 校验渠道开关与验证码子开关（读 `sys_config`，见 4.3.2）。
2. 校验验证码（Redis 校验并消费，见 5.4）。
3. 校验 `target` 未被注册。
4. 生成雪花 `id`；按 4.1.1 规则生成 `user_no`（号段 + 随机后 4 位，命中特殊号或唯一冲突时重试）。
5. `password` 非空则 BCrypt 加密写入；为空则 `password` 存 NULL。
6. 写入 `user_account`，`status` 默认 1。

> **验证码消费时机**：验证码在步骤 2 校验时即被 Redis `DEL` 消费。若后续步骤（如步骤 3 发现已注册）失败，验证码**不会**回滚——用户需重新获取。这是 Redis 方案的固有特性，且与「验证码一次性」的安全语义一致：不允许同一验证码在失败的请求里被反复尝试。

响应：**注册即登录**，返回双 Token（见 5.6）：

```json
{
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>"
}
```

#### POST /api/user/auth/login

登录接口，**密码登录与验证码登录共用同一路径**，由请求体字段区分。

密码登录：

```json
{ "target": "13800138000", "targetType": "PHONE", "password": "xxx" }
```

验证码登录：

```json
{ "target": "13800138000", "targetType": "PHONE", "code": "123456" }
```

判定规则：

| `password` | `code` | 结果 |
|------------|--------|------|
| 有值 | 为空 | 走密码登录 |
| 为空 | 有值 | 走验证码登录 |
| 都有值 | — | 参数错误（40000），避免歧义 |
| 都为空 | — | 参数错误（40000） |

密码登录校验顺序：渠道总开关 → 用户存在 → 账号未禁用 → 密码匹配。若 `password` 为 NULL（纯验证码注册用户），返回 `PASSWORD_NOT_SET`（40103）。

验证码登录校验顺序：渠道总开关 → 验证码子开关 → 用户存在 → 账号未禁用 → 验证码校验（Redis 消费）。

响应统一为**双 Token**（见 5.6）：

```json
{
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>"
}
```

> 说明：原方案将密码登录与验证码登录拆为 `/login` 与 `/login-by-code` 两个路径，现按用户要求合并为单一 `/login`。合并的依据是两者输入结构一致（均为 `target` + `targetType` + 一个凭证字段），且业务语义都是「登录」。

#### POST /api/user/auth/reset-password（忘记密码，匿名）

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456",
  "newPassword": "newPass123"
}
```

流程：渠道与验证码子开关校验 → 验证码校验（Redis 消费） → 按 `target` 定位用户 → 更新密码。

#### PUT /api/user/me/password（修改密码，需登录）

```json
{ "target": "13800138000", "targetType": "PHONE", "code": "123456", "newPassword": "newPass123" }
```

与上一个接口**共用同一 Service 方法**，差异有两点：

- 身份从 Token 取得，需校验 `target` 确为该用户已绑定的联系方式（防止改他人密码）。
- 身份验证通过后，仅允许修改**当前登录用户自己**的密码。

#### GET /api/user/me

获取当前登录用户信息，从 Token 取用户 ID。响应示例如下，**不返回 `password`**：

```json
{
  "id": 7382910384756100000,
  "userNo": "425186",
  "phone": "13800138000",
  "email": null,
  "nickname": "普通用户",
  "avatar": null,
  "status": 1,
  "createdAt": "2026-09-28T14:00:00"
}
```

字段说明：

| 字段 | 说明 |
|------|------|
| `id` | 内部用户 UID。前端**不要**用它做展示，展示用 `userNo` |
| `userNo` | 对外用户号码，当前 6 位纯数字，如 `425186` |
| `phone` / `email` | 已绑定的联系方式，未绑定为 `null` |
| `nickname` / `avatar` | 用户资料，由 `PUT /api/user/me` 维护 |

> **`userNo` 只用于展示**。不作为登录凭据，也不参与任何鉴权判断。

#### PUT /api/user/me（修改个人资料）

```json
{ "nickname": "新昵称", "avatar": "https://..." }
```

仅允许修改**非身份类**资料（`nickname`、`avatar`）。**手机号与邮箱不通过此接口修改**，必须走 `/me/phone`、`/me/email` 的换绑流程，否则会绕过身份验证。

#### PUT /api/user/me/phone（换绑手机号）


```json
{
  "oldTarget": "13800138000",
  "oldTargetType": "PHONE",
  "oldCode": "123456",
  "newPhone": "13900139000",
  "newCode": "654321"
}
```

流程：

1. 校验旧渠道与新渠道（固定为 `PHONE`）的开关与验证码开关。
2. **旧身份验证**：`oldTarget` 必须是当前用户已绑定的联系方式，且 `oldCode` 校验通过。
3. **新联系方式验证**：`newCode` 校验通过；`newPhone` 不得已被其他用户绑定。
4. 更新 `user_account` 的 `phone` 列。

#### PUT /api/user/me/email（换绑邮箱）

请求体与手机号换绑对称：

```json
{
  "oldTarget": "13800138000",
  "oldTargetType": "PHONE",
  "oldCode": "123456",
  "newEmail": "new@example.com",
  "newCode": "654321"
}
```

流程同上，最终更新 `user_account` 的 `email` 列。

**两个接口的差异**：仅「新联系方式」的校验对象与更新列不同（`newPhone`/`phone` 与 `newEmail`/`email`）。**旧身份验证部分完全共用同一套逻辑。**

#### 换绑的旧身份验证规则（已确认，非推断）

旧身份验证允许使用当前账号已绑定的**手机号或邮箱二选一**，**不要求必须使用正在被更换的那个渠道**。

- 换手机号：可用旧手机号验证码，也可用当前已绑定邮箱的验证码完成旧身份验证；
- 换邮箱：可用旧邮箱验证码，也可用当前已绑定手机号的验证码完成旧身份验证。

即 `oldTarget` / `oldTargetType` 与 `newPhone`（或 `newEmail`）的渠道**可以不同**。

用于旧身份验证的渠道必须**同时**满足三个条件：

1. 当前账号**已绑定**该联系方式；
2. 对应渠道在系统配置中**总开关已启用**；
3. 对应渠道**验证码功能已启用**。

**新联系方式必须单独验证**，不存在「旧身份验证通过就跳过新验证」的情况：

- 换手机号 → 必须验证新手机号；
- 换邮箱 → 必须验证新邮箱。

**完整流程**：

```
旧手机号 / 旧邮箱 二选一验证当前身份
  → 验证新的手机号 / 邮箱
  → 两步都通过
  → 完成换绑
```

**可用旧验证渠道的数量约束**：

- 当前账号**只有一个可用的旧验证渠道**时，只能使用该渠道；
- 当前账号**没有任何可用的旧验证渠道**时，**普通换绑流程不能继续**，返回错误并终止。人工申诉 / 账号恢复流程另行设计，不在本次范围。

> 该场景的实际含义：例如用户只绑定了手机号，而手机号渠道的总开关被后台停用，此时该用户没有任何可用旧验证渠道，换绑会被拒绝。这是渠道停用带来的必然结果，需在运维时知悉。

**换绑的两个关键顺序约束**：

1. **先验证旧身份，再验证新联系方式**。不得颠倒，也不得跳过任一步。
2. **两步都通过后才写入数据库**，整个换绑在一个事务内完成。若新验证码校验失败，旧验证码**不应**被标记为已使用——否则用户需重新走完整流程。

**约束（数据保留）**：换绑只更新对应列的值，**不删除**原联系方式数据行。用户换绑手机号后，`phone` 列被新值覆盖，这与「关闭渠道不得删除数据」是两件事——后者指停用渠道开关时不清理用户已绑定的值。

**并发保护**：`newPhone` / `newEmail` 的唯一性由 `uk_user_account_phone` / `uk_user_account_email` 保证。两人同时换绑到同一手机号时，数据库唯一键会使其中一个失败，须捕获并转换为 `TARGET_ALREADY_BOUND`，不得先查询再插入。

#### POST /api/user/auth/logout（退出登录）

```http
POST /api/user/auth/logout
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "refreshToken": "<refreshToken>" }
```

`refreshToken` 用于定位要吊销的会话。服务端须从 `accessToken` 取 `userId`、从请求体 `refreshToken` 取 `sessionId`，并**校验二者一致**，否则会变成「吊销他人会话」的漏洞。

服务端执行 **`DEL auth:session:{userId}:{sessionId}` + `ZREM auth:session:user:{userId} {sessionId}`**。

响应：`Result<Void>`。**只吊销当前会话**——每个 session 是独立 key，该用户其他会话（其他设备）**不受任何影响**。吊销后再用该 `refreshToken` 调 `/auth/refresh` 会失败。接口**幂等**（会话已过期时仍返回成功）。

> **`accessToken` 的残余有效性问题**：吊销只影响该会话的 `refreshToken`。`accessToken` 是无状态 JWT，在其剩余有效期内（最长 30 分钟）**仍然可用**。这是双 Token 模式的固有代价，短有效期即为压缩该窗口。若要立即失效需引入 access token 黑名单，**本轮不做**。

#### GET / PUT /api/sys-user/auth-channels

查询与修改渠道配置。**数据来源为 `sys_config` 的 4 个 `auth.*` 键**（见 4.3），**不是**独立表。

GET 响应：

```json
{
  "phone": { "channelEnabled": true, "codeEnabled": true },
  "email": { "channelEnabled": true, "codeEnabled": false }
}
```

PUT 请求：

```http
PUT /api/sys-user/auth-channels/PHONE
Content-Type: application/json

{ "channelEnabled": true, "codeEnabled": false }
```

`{channel}` 取值为 `PHONE` / `EMAIL`（大写，与路径风格一致）。对应写入的 `sys_config` 键：

| 请求字段 | 写入的 config_key |
|----------|-------------------|
| `channelEnabled` | `auth.phone.enabled` / `auth.email.enabled` |
| `codeEnabled` | `auth.phone.code.enabled` / `auth.email.code.enabled` |

**约束校验（已简化）**：若本次修改会导致 `auth.phone.enabled` 与 `auth.email.enabled` **同时为 `0`**，则拒绝并返回 `CANNOT_DISABLE_ALL_CHANNELS`，保证至少一个渠道的**总开关**可用。

验证码子开关（`*.code.enabled`）**不参与**这条校验，也不需要 `auth.rebind.require-available-channel` 之类的额外配置项。

> 写入时统一存 `'1'` / `'0'` 字符串（见 4.3.5），对外仍以 JSON 布尔值返回。

### 5.6 双 Token 会话机制（**已确认：标准双 Token 模式**）

采用**标准双 Token 模式**：登录 / 注册成功后同时签发 `accessToken` 与 `refreshToken`。

#### 5.6.1 两种令牌的职责

| 令牌 | 用途 | 有效期（默认） |
|------|------|----------------|
| `accessToken` | **正常接口鉴权**，`Authorization: Bearer <accessToken>` | **30 分钟** |
| `refreshToken` | **仅用于** `POST /api/user/auth/refresh` 换取新令牌 | **30 天** |

两者有效期均为**配置项**，不写死在代码里。

#### 5.6.2 存储设计（Redis，不新增 MySQL 表）

**`refreshToken` 状态存 Redis，不新增 MySQL 表。**

Redis 中**只存 `jti` / 摘要 / 会话必要信息，不存明文 Token**。会话维度为「**用户 + 登录会话**」两级：

```
userId
├── session A（Web）      →  当前 refreshToken / jti
├── session B（App）      →  当前 refreshToken / jti
└── session C（其他设备）  →  当前 refreshToken / jti
```

key 设计（**保留 `userId + sessionId` 维度**）：

| key | 含义 |
|-----|------|
| `auth:session:{userId}:{sessionId}` | **会话主记录**（**每个 session 一个独立 key**），value 为该会话当前有效的 `refreshToken` 摘要与必要信息（`jti`、签发时间等），**不存明文 Token**；TTL = refresh 有效期 |
| `auth:session:user:{userId}` | 该用户的**会话索引 ZSET**：member = `sessionId`，**score = 该会话的过期时间戳**（毫秒）。用于「按用户枚举 / 吊销全部会话 / 清理过期会话」及后续设备管理页面 |

> **为何用 ZSET 而不是 SET**：score 存会话过期时间戳后，可**按分数范围查询与清理已过期会话**（如 `ZRANGEBYSCORE ... 0 <now>` 取出并 `ZREM`），无需逐个 `EXISTS` 探测。这是为后续「过期会话清理」「设备管理页面按最近使用排序」预留的能力。
>
> **不再使用 `auth:session:revoked:{jti}`**：当前 session 记录中的有效 `jti` 已足以判断旧 Refresh Token 是否失效（不一致即失效，见 5.6.3），**无需额外维护 revoked key**——少一个 key 就少一处一致性与清理负担。
>
> **`sessionId` 而非 `deviceId`**：本项目管理的是「**登录会话**」——每次成功登录生成一个**独立 `sessionId`**，不识别真实硬件指纹。这样 Web、App、多浏览器天然各自独立，无需处理「同一设备换浏览器」「指纹漂移」等问题。
>
> **key 命名说明**：`auth:session:*` 与验证码的 `auth:code:*` 同属 `auth:` 命名空间，互不冲突。

#### 5.6.3 Rotation 轮换（核心）

`refresh` 成功后采用 **rotation**，**只轮换当前会话**：

1. **当前会话的旧 `refreshToken` 立即失效**（其他会话不受影响）。
2. **同时签发新的 `accessToken` + `refreshToken`**，`sessionId` **保持不变**（同一会话续期）。
3. 新令牌返回给前端，前端须**同时替换两个令牌**。

**判定旧令牌失效的依据**：该会话主记录中的 `jti` 必须与请求令牌的 `jti` **一致**；若已被轮换（记录中的 `jti` 已变）或记录不存在 → 拒绝。**仅凭这一条即可判定失效，不需要额外的 revoked 标记**。

**原子性要求（Lua / CAS）**：rotation 必须用 **Lua 脚本或 CAS（比较并交换）原子地「比较旧 `jti` → 替换为新 `jti`」**，不允许多步非原子操作。同一 `refreshToken` 被并发提交两次时，只有一个请求能成功替换，另一个因 `jti` 已不匹配而必然失败。

- **`sessionId` 始终不变**，只更新该 session key 内保存的 `jti` 与 TTL。
- 同时**刷新 ZSET 中该 `sessionId` 的 score**（会话过期时间戳随续期后移）。
- **不允许先查后写**（这与验证码用 `DEL` 防重放是同一原则）。

#### 5.6.4 退出登录与会话吊销

**退出登录只吊销当前会话**，不影响该用户的其他会话。

**当前 session logout 执行两步**：

1. **`DEL auth:session:{userId}:{sessionId}`** —— 删除当前会话主记录。
2. **`ZREM auth:session:user:{userId} {sessionId}`** —— 从用户会话 ZSET 中移除该 `sessionId`。

- **每个 session 是独立 key，删除当前 session 只影响它自己，不影响该用户的其他会话。** 若删除主记录时该 key 已因 TTL 过期而不存在（`DEL` 返回 0），属正常情况，**只需保证 `ZREM` 被执行**，接口仍返回成功——即 **logout 应设计为幂等**。
- 吊销后，该会话的 `refreshToken` 无法再换取新令牌；**其他会话照常有效**。
- **后续可扩展「退出全部设备」**：按 ZSET 枚举该用户全部 `sessionId`，逐个 `DEL` 会话记录，最后删除 ZSET 本身。**本轮不实现该接口，但 key 结构已支持**。
- **注意**：`accessToken` 是无状态的 JWT，吊销后在其剩余有效期内（最长 30 分钟）**仍然有效**。这是双 Token 模式的固有代价——短有效期就是为了把这个窗口压到可接受范围。若需立即失效，须引入 access token 黑名单，**本轮不做**。

#### 5.6.5 多会话策略（**已确认：多设备 / 多会话**）

**新登录不得覆盖用户已有会话。**

- 每次成功登录（密码登录 / 验证码登录 / 注册自动登录）都生成**一个全新的 `sessionId`**，写入 `auth:session:{userId}:{sessionId}`，并 `ZADD auth:session:user:{userId} {过期时间戳} {sessionId}`。
- 同一用户可**同时存在多个有效会话**（Web、App、多个浏览器各算一个），**各自是独立 key，互不干扰**。
- **refresh 只轮换当前 session**；**退出登录只吊销当前 session**。
- **当前不强制限制最大设备数**：`maxSessions` 作为后续可配置项预留，本轮不实现、不做超限驱逐。
- 结构上保留以后增加**设备 / 会话管理页面**的能力：读取 `auth:session:user:{userId}` 的 ZSET 即可列出全部会话（含过期时间），必要时再按 `sessionId` 取各会话详情。
- **过期会话清理**：可借助 ZSET 的 score 做 `ZRANGEBYSCORE auth:session:user:{userId} 0 <now>` 找出已过期 `sessionId` 并 `ZREM`，无需逐 key 探测。**本轮不实现定时清理**，因为会话主记录 key 有 TTL 会自动消失，ZSET 中残留的成员只影响枚举结果的准确性（见第 10 节风险）。

> **为何不绑定硬件指纹**：本项目管理的是登录会话本身，不去识别用户真实手机硬件。用 `sessionId` 而非 `deviceId`，可避免「同一手机换浏览器」「Web 与 App 无法拿到稳定设备标识」等场景下的误判，实现也更简单。

#### 5.6.6 Redis 故障的处理

**Redis 故障时 `refresh` 直接失败，不允许绕过校验。**

- Redis 不可用 → `refresh` 返回 `INTERNAL_SERVER_ERROR`（`50000`），**不降级、不放行**。
- **严禁**在无法校验 `refreshToken` 状态时「乐观放行」签发新令牌——那会使吊销机制失效，等同于认证绕过。
- 与验证码的处理约定一致（见第 10 节）。

#### 5.6.7 接口定义

**`POST /api/user/auth/refresh` 只接受 `refreshToken`，不使用 `accessToken` 续期。**

```http
POST /api/user/auth/refresh
Content-Type: application/json

{ "refreshToken": "<refreshToken>" }
```

成功响应 `data`：

```json
{
  "accessToken": "<new accessToken>",
  "refreshToken": "<new refreshToken>"
}
```

**匿名访问**：该接口靠 `refreshToken` 自身鉴权，请求时 `accessToken` 很可能已过期，因此**必须允许匿名访问**（见 5.2 放行清单）。

**校验顺序**：

1. 校验 `refreshToken` 的签名与有效期（JWT 自身校验），并确认 `tokenType == refresh`。
2. 从 claims 取 `userId`、`sessionId`、`jti`。
3. **用 Lua / CAS 原子执行：读取 `auth:session:{userId}:{sessionId}`，比对其中 `jti` 与请求令牌的 `jti`，一致则替换为新 `jti` 并刷新 TTL**。
4. 若 key 不存在，或 `jti` 不一致（已被轮换）→ `REFRESH_TOKEN_INVALID`。
5. 校验用户仍存在且未被禁用（此步在原子替换之后，若用户已禁用则需回滚/吊销该会话）。
6. **刷新 `auth:session:user:{userId}` 中该 `sessionId` 的 score** 为新过期时间戳（`ZADD` 覆盖）。
7. 签发新的 `accessToken` + `refreshToken`（**携带同一 `sessionId`**）并返回。

> **`sessionId` 在 rotation 前后始终不变**，仅 `jti` 与过期时间改变。这保证一次续期**不会新增会话**，ZSET 成员数量稳定。
>
> **步骤 3~4 必须在同一原子操作内完成**：若拆成「先读比对、再写替换」，并发提交同一 `refreshToken` 时两个请求都可能通过比对，从而各自签发一组令牌。这是本方案**唯一必须用 Lua/CAS 而非普通 GET/SET 的地方**。

#### 5.6.8 令牌有效期配置

```yaml
app:
  jwt:
    # 访问令牌有效期，默认 30 分钟
    access-token-expiration: 30m
    # 刷新令牌有效期，默认 30 天
    refresh-token-expiration: 30d
```

> 现有 `application.yml` 中 `app.jwt.access-token-expiration` 当前为 `24h`，本次按 **30m** 调整。刷新令牌有效期为**新增配置项**。

#### 5.6.9 新增接口：退出登录

会话吊销需要一个入口，因此**新增**：

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| POST | `/api/user/auth/logout` | `SCOPE_user` | 退出登录，**只吊销当前会话** |

请求体：`{ "refreshToken": "<refreshToken>" }`（用于定位要吊销的会话）。响应：`Result<Void>`。

**吊销逻辑（当前 session logout = `DEL` + `ZREM`）**：

- 从 `accessToken` 的认证上下文取 `userId`，从请求体的 `refreshToken` 解析出 `sessionId`。
- **必须校验二者一致**（即该 `refreshToken` 确实属于当前登录用户），防止吊销他人会话。
- **`DEL auth:session:{userId}:{sessionId}`** 删除当前会话主记录。
- **`ZREM auth:session:user:{userId} {sessionId}`** 从用户会话 ZSET 移除该会话。
- **每个 session 是独立 key，删除当前 session 不影响该用户其他会话。**
- **接口幂等**：若主记录已因 TTL 过期而消失（`DEL` 返回 0），仍执行 `ZREM` 并返回成功，不报错。

> 也可改为不传请求体、直接用 `accessToken` 的 `sessionId` 定位——两种都能准确定位当前会话。**本轮取「请求体传 `refreshToken`」的方案**，因为它额外完成了一次「客户端确实持有该会话 refreshToken」的校验。
>
> **后续可扩展「退出全部设备」**（如 `POST /api/user/auth/logout-all`）：按 ZSET 枚举全部 `sessionId` 逐个 `DEL`，最后删除 ZSET。**本轮不实现**。

#### 5.6.10 涉及的 JWT 结构变更

`refreshToken` 与 `accessToken` **必须可区分**，避免用 `refreshToken` 直接调用业务接口。claims 设计：

| claim | `accessToken` | `refreshToken` |
|-------|---------------|----------------|
| `tokenType` | `access` | `refresh` |
| `userId` | ✅ | ✅ |
| `sessionId` | ✅ | ✅（**同一个值**） |
| `jti` | — | ✅（当前令牌唯一 ID，用于 rotation 比对） |
| `scope` | ✅（`user` / `admin`） | ❌ **不带业务 scope** |

- **`userId` + `sessionId` 在两个令牌中保持一致**，这样无论是用 `accessToken` 还是 `refreshToken`，都能**准确定位并吊销当前会话**。
- `sessionId` 每次登录新生成（见 5.6.5）；同一会话内 refresh 轮换时 **`sessionId` 不变**，只换 `jti`。
- 资源服务器的 JWT 校验需**拒绝 `tokenType != access` 的令牌**，否则 `refreshToken` 会被当成有效访问令牌使用，形成严重漏洞。

## 6. 错误码设计

沿用现有 `ErrorCode` 枚举与 `Result` 结构。新增错误码如下：

| HTTP | 业务码 | 名称 | 说明 |
|------|--------|------|------|
| 400 | `40001` | `AUTH_CHANNEL_DISABLED` | 该认证渠道已停用 |
| 400 | `40002` | `CODE_FUNCTION_DISABLED` | 该渠道验证码功能已停用 |
| 400 | `40003` | `CODE_SEND_TOO_FREQUENT` | 验证码发送过于频繁 |
| 400 | `40004` | `CODE_SEND_LIMIT_EXCEEDED` | 验证码发送次数已达上限 |
| 400 | `40005` | `VERIFICATION_CODE_INVALID` | 验证码错误或已失效 |
| 400 | `40006` | `VERIFICATION_CODE_EXPIRED` | 验证码已过期（**保留备用**：Redis 方案下过期与错误统一返回 `40005`，见 5.4） |
| 400 | `40007` | `TARGET_ALREADY_REGISTERED` | 该手机号或邮箱已注册 |
| 400 | `40008` | `TARGET_NOT_REGISTERED` | 该手机号或邮箱未注册 |
| 400 | `40009` | `CANNOT_DISABLE_ALL_CHANNELS` | 不允许同时停用所有认证渠道 |
| 400 | `40010` | `CONTACT_NOT_BOUND` | 该联系方式未绑定到当前账号 |
| 400 | `40011` | `TARGET_ALREADY_BOUND` | 该联系方式已被其他账号绑定 |
| 400 | `40012` | `NO_AVAILABLE_VERIFY_CHANNEL` | 当前账号没有可用的身份验证渠道 |
| 400 | `40013` | `LOGIN_METHOD_AMBIGUOUS` | 登录凭证不明确，密码与验证码不可同时提交 |
| 401 | `40101` | `INVALID_CREDENTIALS` | 账号或密码错误（已有） |
| 401 | `40103` | `PASSWORD_NOT_SET` | 该账号未设置密码，请改用验证码登录 |
| 401 | `40104` | `REFRESH_TOKEN_INVALID` | Refresh Token 无效、已过期或已被吊销 |
| 403 | `40303` | `USER_DISABLED` | 普通用户已禁用（已有） |

> 业务码段沿用现有 400xx / 401xx / 403xx 结构，具体号段分配可在实现时按需微调。
>
> **Redis 不可用时的错误码**：Redis 连接失败属基础设施故障，**不新增业务错误码**，按现有 `INTERNAL_SERVER_ERROR`（`50000`）返回，完整异常只记服务端日志、不暴露给前端。详见第 10 节。

## 7. 代码改动清单

### 7.1 新增

| 层次 | 文件 | 说明 |
|------|------|------|
| config | `config/RedisConfig.java` | Redis 连接、`RedisTemplate` 与 key/value 序列化配置 |
| config | `config/properties/AuthCodeProperties.java` | 验证码 TTL、发送间隔、每日上限等配置绑定 |
| store | `auth/store/VerificationCodeStore.java` | **验证码 Redis 存取**：写入、校验并消费、失败计数、发送锁、每日计数 |
| store | `auth/store/RefreshTokenStore.java` | **会话 Redis 存取**：按 `{userId}:{sessionId}` 写入 / 读取会话、**Lua / CAS 原子比对并替换 `jti`**、`ZADD`/`ZREM` 维护用户会话 ZSET（score=过期时间戳）、`DEL` 吊销单个会话、按 ZSET 枚举 / 吊销用户全部会话 |
| service | `auth/service/UserSessionService.java` | 双 Token 签发编排（生成 `sessionId`）、refresh rotation（`sessionId` 保持不变、原子替换 jti）、退出登录 `DEL` + `ZREM` 吊销当前会话（见 5.6） |
| vo | `auth/vo/UserTokenVO.java` | 登录 / 注册 / refresh 统一返回体（`accessToken` + `refreshToken`） |
| dto | `auth/dto/UserRefreshDTO.java` / `auth/dto/UserLogoutDTO.java` | `/auth/refresh`、`/auth/logout` 请求体 |
| entity | `config/entity/SysConfig.java` | `sys_config` 配置实体 |
| mapper | `config/mapper/SysConfigMapper.java` + XML | `sys_config` 数据访问（按 key 查、按 group 批量查、更新） |
| service | `auth/service/AuthChannelConfigService.java` | 渠道开关查询与校验（**读 `sys_config` 的 4 个 `auth.*` 键**，含推导规则见 4.3.2） |
| service | `auth/service/VerificationCodeService.java` | 验证码生成、发送编排、校验、防刷（依赖 `VerificationCodeStore`） |
| service | `auth/service/sender/CodeSender.java` | 发送接口（短信/邮件） |
| service | `auth/service/sender/LoggingCodeSender.java` | **打桩实现**：仅打日志，不真实发送 |
| service | `user/service/UserMeService.java`（或并入既有 Service） | 当前用户信息、资料修改、换绑 |
| service | `user/service/UserNoGenerator.java` | `user_no` 生成：号段 + 随机位 + 特殊号过滤 + 唯一冲突重试 |
| config | `config/properties/UserNoProperties.java` | 号段 secret（`USER_NO_SECRET`）、号段区间、随机位数、重试次数等配置绑定 |
| config | `config/UserNoSpecialNumbers.java`（或同类配置类） | **特殊号码规则集中配置**，不写死在数据库 |
| controller | `user/controller/UserMeController.java` | `/api/user/me/**` 系列接口 |
| controller | `admin/controller/AuthChannelController.java` | `/api/sys-user/auth-channels`（读写 `sys_config`） |
| dto | 注册、登录、发送验证码、重置密码、改密、换绑手机/邮箱、改资料等 DTO | 各接口请求体 |
| vo | 渠道配置 VO | 管理端响应体 |
| sql | `module/sys_config` 建表与 4 条初始化数据 | 见 4.3.1，需同步进 `web_project.sql` 与迁移脚本 |

> **不再新增** `VerificationCode` 实体、`VerificationCodeMapper`、`AuthChannelConfig` 实体、`AuthChannelConfigMapper`——这两张表已取消。

### 7.2 修改

| 文件 | 改动 |
|------|------|
| `user/entity/UserAccount.java` | 移除 `username`；新增 `userNo`/`phone`/`email`/`password`；保留 `nickname`/`avatar`/`status` |
| `mapper/user/UserAccountMapper.xml` | 字段与查询条件全部重写：按 `phone`/`email` 查询，`keyword` 改为搜 `user_no`/`phone`/`email` |
| `user/mapper/UserAccountMapper.java` | 新增按 `phone`/`email` 查询、更新密码、更新手机号/邮箱、更新资料、插入用户 |
| `auth/service/impl/UserAuthServiceImpl.java` | 重写为双登录 + 验证码场景；登录/注册成功后签发双 Token |
| `auth/service/JwtTokenService.java` | `createUserAccessToken` 的 `username` claim 改为 `user_no`；**新增 `createUserRefreshToken`**；claims 增加 `tokenType`（见 5.6.10）；两令牌有效期分别读取配置 |
| `auth/controller/UserAuthController.java` | 路径改为 `/auth/register`、`/auth/login`、`/auth/send-code`、`/auth/reset-password`、`/auth/refresh`、`/auth/logout`；移除 `/auth/me` |
| `config/JwtConfig.java` | `jwtDecoder` 的校验器需**拒绝 `tokenType != access` 的令牌**（见 5.6.10）；新增 refresh 有效期配置绑定 |
| `user/dto/UserAccountQueryDTO.java` | `keyword` 语义变更，`status` 保留 |
| `user/vo/UserAccountListVO.java` | 字段调整为 `user_no`/`phone`/`email`/`nickname`/`status` 等 |
| `user/vo/UserInfoVO.java` | 字段调整；或由新的 `/me` 响应 VO 取代 |
| `config/SecurityConfig.java` | 匿名放行认证类接口（见 5.2）；`logout` 不在放行清单 |
| `common/error/ErrorCode.java` | 新增错误码（见 6） |
| `user/service/impl/UserAccountServiceImpl.java` | 列表查询字段调整 |
| `pom.xml` | 新增 `spring-boot-starter-data-redis` 依赖 |
| `src/main/resources/application.yml` | 新增 `spring.data.redis` 连接配置（地址/端口/密码走环境变量）；新增 `app.user-no` 配置块：`secret`（`USER_NO_SECRET`）、`daily-segment-min/max`、`random-digit-count`、`max-retry`；新增验证码 TTL/间隔/日上限配置；**`app.jwt.access-token-expiration` 由 `24h` 改为 `30m`，新增 `refresh-token-expiration: 30d`** |
| `web_project.sql` | 新增 `sys_config` 建表 + 4 条 `auth.*` 初始化数据；**删除 `verification_codes`、`auth_channel_config` 建表语句** |
| `database/migrations/20260928_user_account_dual_login.sql` | **重写**：去掉 `verification_codes` 与 `auth_channel_config` 建表，改为建 `sys_config` 并插入 4 条数据 |
| `docs/api.md` | 普通用户章节重写：`user_no` 字段、新接口路径、新错误码 |
| `docs/engineering.md` | 「当前业务范围」段落重写（新增注册、双登录、换绑等能力）；运行配置章节补充 `USER_NO_SECRET` 说明与「不随 JWT 轮换」约束、Redis 连接配置、敏感配置边界 |
| `README.md` | 功能说明、接口速览、建表说明、迁移清单同步；补充 Redis 依赖说明 |

### 7.3 删除

（本章节已并入 7.2 的「修改」表；本表仅列删除项。）

| 文件 / 对象 | 说明 |
|------|------|
| `auth/dto/UserLoginDTO.java` | 由新的登录 DTO 取代 |
| `auth/controller/UserAuthController.java` 中的 `/auth/me` | 迁至 `GET /api/user/me`，**不留兼容接口** |
| `database/migrations/` 中既有脚本里的 `user_code` 引用 | 新项目无历史数据，旧脚本同步改名，不保留旧名 |
| **表 `verification_codes`** | 验证码改 Redis 存储，表取消（见 4.2） |
| **表 `auth_channel_config`** | 合并进 `sys_config`，表取消（见 4.3） |
| 原计划新增的 `VerificationCode*` / `AuthChannelConfig*` 实体与 Mapper | 随表取消，不再新增 |

### 7.4 测试影响

| 文件 | 改动 |
|------|------|
| `config/JwtSecurityTest.java` | `new UserAccount()` + `setUsername()` 改为新字段（`setUserNo` 等）；`/api/user/auth/me` 路径改为 `/api/user/me` |
| `common/exception/GlobalExceptionHandlerTest.java` | 构造参数调整 |
| **新增** | 验证码防刷（60 秒锁、每日 10 条）、渠道开关推导、换绑（含无可用渠道场景）、重置密码、登录方式判定的测试 |
| **新增** | `UserNoGenerator` 单元测试：号段映射范围、同日号段稳定、特殊号码全部被过滤、唯一冲突重试、位数可配置 |
| **新增** | `VerificationCodeStore` 测试：验证码写入与 TTL、校验成功后 key 被删除（不可重复使用）、失败 5 次后 key 被删除、每日计数 TTL 落在当日结束而非固定 86400 秒 |
| **新增** | `AuthChannelConfigService` 测试：4 个键的推导规则、「两个总开关不得同时为 0」的守卫 |
| **新增** | `RefreshTokenStore` 测试：会话写入与校验、rotation 后旧 `jti` 失效、**并发提交同一令牌只有一次成功（Lua/CAS）**、**连续 refresh 后 ZSET 成员数保持为 1（sessionId 不变）**、吊销单个会话不影响其他会话、按 ZSET 枚举 / 吊销全部会话 |
| **新增** | 双 Token 测试：`refreshToken` **不能**用于访问业务接口（`tokenType` 校验）、refresh 后旧 refreshToken 立即失效、logout 后该会话 refresh 失败 |
| **新增** | 多会话测试：**同一用户两次登录产生两个独立会话、互不影响**；A 会话 refresh 不影响 B 会话；A 会话 logout 后 B 会话仍可用；**新登录不使旧会话失效** |
| **测试环境要求** | 涉及 Redis 的测试需要可用 Redis 实例（本地已有）。工程规范要求涉及中间件的修改需在真实实例验证，**不得用 mock 通过当作验证通过**（对应 `docs/engineering.md` 对 MySQL 的同类要求） |

## 8. 待确认事项

| # | 事项 | 状态 / 当前倾向 | 影响 |
|---|------|----------------|------|
| 1 | 短信 / 邮件发送渠道 | ✅ **已确认：先打桩**（验证码打日志，不真实发送） | 实现量为接口 + 打桩实现，后续可替换 |
| 2 | 渠道配置存储位置 | ✅ **已确认：统一 `sys_config` 表**（原 `auth_channel_config` 取消） | 已落表结构，见 4.3 |
| 3 | 防刷每日上限具体数值 | ✅ **已确认：10 条/天**（阈值配置化） | 已写入 5.3 |
| 4 | `user_no` 生成规则 | ✅ **已确认**：`PPXXXX`，号段由日期+secret 映射 `10~99`，后 4 位随机；详见 4.1.1 | 已定 |
| 5 | 换绑旧身份验证是否可用另一渠道 | ✅ **已确认：允许二选一**，详见 5.5 换绑章节 | 已定 |
| 6 | `/api/user/auth/me` 改为 `/api/user/me` | ✅ **已确认：改，且不留兼容** | 已定 |
| 7 | `avatar` / `nickname` 是否保留 | ✅ **已确认：保留** | 已在 4.1 节加回 |
| 8 | 注册时 `password` 是否必填 | ✅ **已确认：可选**（不传则纯验证码登录） | 已写入 5.5 注册章节 |
| 9 | `/api/user/auth/refresh` 的语义 | ✅ **已确认：标准双 Token 模式（A）**。rotation 轮换、退出登录吊销当前会话、**多会话并存**、有效期配置化；详见 5.6 | 已定 |
| 10 | 换绑接口是否合为一个 | ✅ 已确认拆为 `/me/phone` 与 `/me/email` | 已定 |
| 11 | 是否需要兼容旧接口/旧字段 | ✅ **已确认：不需要**。新项目、无历史数据，全部直接就位 | 已定 |
| 12 | 号段 secret 是否复用 JWT secret | ✅ **已确认：不复用**。新增独立 `USER_NO_SECRET`，上线后保持稳定，不随 JWT 密钥轮换 | 详见 4.1.3 |
| 13 | Redis 用途范围 | ✅ **已确认**：验证码存储 + 验证码防刷 + 通用 Redis 基础设施 | 详见 4.2 |
| 14 | 验证码是否落 MySQL | ✅ **已确认：不落库**，表取消；不提供历史审计统计，将来需要审计单独加事件日志 | 详见 4.2.3 |
| 15 | `sys_config` 是否加 Redis 缓存 | ✅ **已确认：不加**，本轮直接读 MySQL | 详见 4.3.4 |
| 16 | 渠道开关是否拆细到场景级 | ✅ **已确认：不拆**，只保留 4 个键，业务能力由 4 个键推导 | 详见 4.3.1 / 4.3.2 |
| 17 | refresh token 是否纳入 Redis | ✅ **已确认：纳入**。会话状态存 Redis，不新增 MySQL 表，只存 jti / 摘要，不存明文 | 详见 5.6.2 |
| 18 | refresh 轮换策略 | ✅ **已确认：rotation**，**只轮换当前会话**，旧 refreshToken 立即失效并同时签发新双 Token | 详见 5.6.3 |
| 19 | 单会话还是多设备 | ✅ **已确认：多设备 / 多会话**。每次登录生成独立 `sessionId`，同一用户可并存多个有效会话，**新登录不覆盖已有会话**；本轮不限制最大设备数 | 详见 5.6.5 |
| 20 | 双向令牌有效期 | ✅ **已确认：配置化**，默认 accessToken 30 分钟、refreshToken 30 天 | 详见 5.6.8 |
| 21 | 会话标识用 `sessionId` 还是硬件 `deviceId` | ✅ **已确认：`sessionId`**，管理「登录会话」而非识别真实硬件，不绑定硬件指纹 | 详见 5.6.2 / 5.6.5 |
| 22 | 是否实现「退出全部设备」 | ✅ **已确认：本轮不实现**，key 结构已支持（按 ZSET 枚举全部会话逐个吊销） | 详见 5.6.4 / 5.6.9 |
| 23 | 是否维护 `auth:session:revoked:{jti}` | ✅ **已确认：不维护**，当前 session 保存的有效 `jti` 已足以判断旧令牌失效 | 详见 4.2 / 5.6.3 |
| 24 | 用户会话索引用什么结构 | ✅ **已确认：ZSET**，member=`sessionId`，score=会话过期时间戳，便于过期清理 / logout-all / 设备管理 | 详见 4.2 / 5.6.2 |
| 25 | rotation 的原子性实现方式 | ✅ **已确认：Lua / CAS** 原子比较并替换当前 session 的 `jti`，`sessionId` 始终不变 | 详见 5.6.3 / 5.6.7 |

### 8.1 不做兼容的范围（已确认）

因为项目没有历史数据、也没有已发布的正式客户端，以下**一律不做兼容层**：

| 对象 | 处理方式 |
|------|----------|
| 表字段 `user_code` → `user_no` | 直接改名，不保留 `user_code` 列，不写双写逻辑 |
| 表字段 `username` | 直接删除，不保留过渡列 |
| 表 `verification_codes` | 直接删除，验证码改 Redis |
| 表 `auth_channel_config` | 直接删除，配置合并进 `sys_config` |
| 接口 `/api/user/auth/me` → `GET /api/user/me` | 直接改，不留兼容别名 |
| 接口 `/api/user/auth/code` → `/api/user/auth/send-code` | 直接改，不留兼容别名 |
| 登录接口 `/login` + `/login-by-code` | 直接合并为 `/auth/login`，不保留旧路径 |
| 旧 `UserLoginDTO` 等 DTO | 直接删除，不做废弃标记 |
| 前端调用点 | 已实测无普通用户调用点，无需同步修改 |

唯一保留的「迁移脚本」是为**已经执行过早期脚本的开发库**同步结构用的，属结构变更而非数据兼容。

## 9. 实施顺序建议

1. 确认第 8 节剩余待定事项（**已全部清零**，不再阻塞）。
2. 表结构定稿：`web_project.sql` 新增 `sys_config`、删除两张旧表；迁移脚本同步。
3. 引入 Redis：`pom.xml` 依赖 + `application.yml` 连接配置 + `RedisConfig` 序列化，并**先验证连通性**。
4. 配置落位：`application.yml` 增加 `app.user-no`、验证码 TTL/间隔/日上限、**双 Token 有效期（access 30m / refresh 30d）**配置。
5. `sys_config` 实体与 Mapper，`AuthChannelConfigService`（4 键推导 + 守卫校验）。
6. `UserNoGenerator` 与 `UserNoProperties`（含特殊号配置与重试），并补单元测试。
7. `VerificationCodeStore`（Redis 存取，含 4 类 key 与自然日 TTL），并补测试。
8. `VerificationCodeService`（发送编排 + `CodeSender` 打桩实现）。
9. 认证接口（注册、双登录、重置密码）。
10. 换绑接口。
11. `SecurityConfig` 与错误码。
12. **双 Token 多会话**：`RefreshTokenStore`（按 `{userId}:{sessionId}` DEL/SET + **Lua/CAS 原子比对替换 jti** + 用户会话 **ZSET（`ZADD`/`ZREM`）** 维护 + 枚举/全量吊销）、`JwtTokenService.createUserRefreshToken`（含 `tokenType` / `sessionId` claim）、`JwtConfig` 的 `tokenType != access` 拒绝逻辑、`/auth/refresh`、`/auth/logout`。此步必须在第 9 步（签发）之后，因为它同时改动签发与校验两端。
13. 文档重写（`docs/api.md`、`docs/engineering.md`、`README.md`）。
14. 测试补充，并在**真实 Redis + MySQL** 上验证，最后跑 `clean test`。

> 第 12 步与前序步骤存在交叉：登录/注册（第 9 步）若先只签发 accessToken，则第 12 步需回头改签发逻辑。若希望一次性完成，可将第 9 步与第 12 步合并实施——**代价是单个变更集变大、出错时定位范围变宽**。建议按上述顺序分两步，第 9 步先让「单 accessToken」跑通，第 12 步再补齐 refresh 链路。

## 10. 风险提示

- **代码与表结构当前不一致**：`UserAccountMapper.xml` 仍查询已删除的 `username`，实体类同样是旧字段（`username`/`nickname`/`avatar`/`status` 与表的 `user_no`/`phone`/`email`/`password`/`nickname`/`avatar`/`status` 不一致）。**连真实数据库必然报 `Unknown column`**。现有测试全部使用 mock、不访问数据库，因此 `mvnw clean test` 会显示通过，此为**假绿**，不代表代码可用。
- **本方案改动面较大**，涉及认证授权核心链路 + 新增 Redis 中间件，建议分步实施并在可丢弃的 MySQL 测试库 + 真实 Redis 上做验证（`docs/engineering.md` 已要求：涉及数据库查询的修改需单独在 MySQL 测试库验证）。
- **Redis 成为新的可用性依赖**：验证码发送与校验全部依赖 Redis。若 Redis 不可用，注册、验证码登录、重置密码、换绑都会失败（密码登录不受影响，因其不依赖验证码）。处理约定：
  - Redis 故障按**基础设施故障**处理，返回 `INTERNAL_SERVER_ERROR`（`50000`），**不新增业务错误码**、不伪装成「验证码错误」，避免误导用户反复重试。
  - **禁止**在 Redis 不可用时降级为「跳过验证码校验」——这会造成认证绕过，属严重安全问题。
  - 部署时需明确 Redis 的可用性等级与告警；本地开发依赖本机 Redis。
- **每日上限的 TTL 计算容易写错**：`auth:code:daily:*` 若用固定 `86400` 秒，会变成「首次发送后滚动 24 小时」，与「自然日 10 条」不符（见 4.2.2）。必须按「距当日 24:00 的剩余秒数」设置，且要有测试覆盖。这是本次改动里最容易埋错的一处。
- **验证码不落库带来的可观测性缺口**：无法统计历史发送量、无法追溯某次发送记录（见 4.2.3）。打桩阶段影响有限，但**上线接入真实短信渠道后**，若出现资费异常或用户投诉，将缺少直接的数据依据，只能依赖日志采集。建议届时同步上线发送事件日志。
- **key 命名空间隔离**：Redis key 使用 `auth:` 前缀。若同一 Redis 实例被多个应用或环境共用，需确认前缀不冲突，或将不同环境指向不同 db/实例。
- **短信成本风险**：若不实现防刷或阈值过宽，攻击者可反复触发发送，直接产生短信费用。因此防刷（第 5.3 节 `/api/user/auth/send-code` 第 3、4 条）属于**必做项**而非可选项。注意：当前发送渠道为打桩实现，**上线前必须替换为真实渠道并复核防刷阈值**。
- **渠道开关的运维风险**：若运维误将两个渠道总开关都关闭，且业务校验失效，会导致所有用户无法登录、无法注册、**且无法通过被关闭的渠道找回**。第 4.3.3 节与 `/api/sys-user/auth-channels` 的约束校验是防止此场景的唯一防线，必须有测试覆盖。
- **`sys_config` 的越权写入风险**：`sys_config` 是通用配置表，后续若有其他模块写入，需注意 `auth.*` 键只能由管理端渠道配置接口维护，避免其他入口误改导致认证能力被关闭。同时必须坚持「敏感配置不入库」——一旦有人把密钥类配置写进 `sys_config`，将以明文形式落库。
- **渠道停用导致的换绑死锁风险**：若用户只绑定了一种联系方式，而该渠道被后台停用，则该用户**没有任何可用旧验证渠道，无法换绑**（详见 5.5）。此时唯一的自救路径是后台重新启用该渠道。运维停用渠道前需评估影响面。
- **`user_no` 位数的扩展约束**：当前生成 6 位。将来扩展到 10 / 12 / 16 位时，须同步确认两点——① 特殊号过滤规则随位数调整（例如 10 位时的连号判定窗口）；② 前端展示位宽与 `VARCHAR(32)` 的上限。**扩展本身不需要改表结构**，这是选 `VARCHAR(32)` 而非 `CHAR(6)` 的原因。
- **`user_no` 的号段可被推测**：`PP` 由「日期 + secret」确定性得出，知道算法即可推算当日号段，后 4 位随机空间仅 1 万。因此 `user_no` **只能作为展示标识，不可作为凭据、不可用于登录、不可单独用于鉴权**。这是需求已确认的定位（登录仍靠手机号/邮箱），实现时不得扩大其用途。
- **`USER_NO_SECRET` 的运维约束**：该 secret **上线后必须保持稳定**，不得因安全策略随 JWT 密钥一起轮换。轮换不会造成 `user_no` 重复（唯一性由数据库兜底），但会让同一天注册的用户号段不一致，属可见的异常现象。部署时应把它与 `JWT_SECRET` 分开管理、分开记录轮换计划。若 secret 缺失或为空，**应用启动应直接失败**（参照 `JwtConfig` 对 JWT secret 的处理），不得静默降级为某个默认值。
- **防刷阈值是成本开关**：每日 10 条、间隔 60 秒是按「短信按条计费」预设的保守值。上线接入真实渠道后需按实际资费和用户投诉率复核；阈值可配置，不需要改代码。
- **`refreshToken` 不能当访问令牌用（严重漏洞）**：两类令牌同源同算法，若资源服务器不校验 `tokenType`，则**长效的 refreshToken 可直接用于访问业务接口**，等于把 30 分钟的有效期绕成 30 天，整个双 Token 设计失效。因此 `JwtConfig` 的 `jwtDecoder` **必须拒绝 `tokenType != access` 的令牌**（见 5.6.10），且必须有测试覆盖（用 refreshToken 打业务接口必须 401）。这是本轮改动中**最容易被漏掉、后果最严重**的一处。
- **吊销 refresh 后 `accessToken` 仍有残余有效期**：`accessToken` 无状态，服务端不保存也不查询。退出登录只删除 Redis 中的 refresh 会话，**已签发的 `accessToken` 在其剩余有效期内（最长 30 分钟）依然可用**。这是无状态 JWT 的固有代价，不是缺陷。若业务要求「退出即刻失效」，必须改为有状态校验（每次请求查 Redis 会话），代价是每个请求多一次 Redis 往返——**本轮不采用**。
- **多会话下攻击者可持续持有会话（无最大会话数限制）**：本轮**不限制最大设备数**（见 5.6.5），也**不做超限驱逐**。这意味着若攻击者已取得用户凭据并建立会话，**用户在其他设备登录不会把攻击者挤下线**，攻击者可凭 refreshToken 长期续期（默认 30 天）。缓解手段：① 用户侧「查看登录设备 + 踢出指定会话」页面（key 结构已支持，本轮未实现）；② 「退出全部设备」接口（同上）。**这是多会话策略的固有代价，不是缺陷**，但上线前应确认产品是否接受，并规划会话管理页面的排期。
- **会话记录中保存 `jti` 而非明文令牌**：会话主记录只需保存当前有效 `jti`（用于 rotation 比对）。**严禁把明文 `refreshToken` 存进 Redis**——Redis 一旦被读取或误导出，等同于令牌泄露。5.6.2 已明确「不存明文 Token」。
- **ZSET 中的成员可能残留**：`auth:session:user:{userId}` 是 ZSET，会话主记录 key 到期自动消失，但 **ZSET 成员不会随之移除**。因此枚举结果可能含有「score 已过期但成员仍在」的 `sessionId`。实现时需注意：① 读取时按 score 过滤过期项（`ZRANGEBYSCORE ... <now>`）；② 或定期按 score 清理；③ ZSET 本身设置 TTL（会话续期时刷新）。若不处理，`logout-all` / 设备管理列表会显示已失效会话，**不致命但结果不准确**。
- **Redis 故障时 refresh 必须失败，不得放行**：refresh 的校验依赖 Redis 中的会话状态。若 Redis 不可用时选择「跳过校验直接签发」，等于任何人拿旧 refreshToken 都能无限续期，会话吊销彻底失效。**必须直接返回失败**（按基础设施故障处理）。这会造成「Redis 挂了 → 用户在 accessToken 过期后必须重新登录」，属可接受的降级方向。
- **rotation 的原子性必须靠 Lua / CAS**：rotation 要求「旧 refreshToken 立即失效」，但客户端并发提交同一 refreshToken（如多标签页同时刷新）时，若用「先读比对、再写替换」，两个请求可能都通过比对并各自签发令牌。**必须用 Lua 脚本或 `WATCH/MULTI` (CAS) 把「比对旧 `jti` → 替换为新 `jti`」放在一次原子操作内**（见 5.6.3 / 5.6.7）。前端也应做单飞（single-flight）控制，避免自身触发该竞争。
- **rotation 只影响当前会话（需正确实现）**：多会话下 rotation 必须**只改当前 `sessionId` 对应 key 的 `jti`**，`sessionId` 本身**始终不变**。若实现时误用「删旧 key + 新建 session」，会把一次续期变成「新增一个会话」，**导致 ZSET 成员无限增长、旧会话残留**。这是本方案最容易写错的一处，须有测试覆盖（连续 refresh 多次后，ZSET 成员数应保持为 1）。
- **双 Token 引入了额外的客户端复杂度**：前端必须实现「401 → 自动 refresh → 重放原请求」的拦截器，并处理 refresh 本身失败（跳登录页）。这部分前端工作**不在本次后端方案范围内**，但排期时不能遗漏——否则接口做完了前端仍不可用。当前 `atlas-console` 尚无用户端页面，属新增开发。
- **`WebSocket` / 长连接场景未覆盖**：双 Token 方案只针对 HTTP 请求。若将来引入长连接，`accessToken` 30 分钟过期需额外的心跳续期机制，本轮不涉及。
- **接口路径不兼容**：`/api/user/auth/me` 改为 `/api/user/me`，且 `/auth/code` 更名为 `/auth/send-code`。**已实测前端无任何普通用户接口调用点**（见下条），因此不存在被破坏的调用方。此条仅在将来出现外部调用方时重新评估。
- **前端现状核实（2026-09-28 实测）**：前端工程为 `E:\JavaProjects\web-project\atlas-console`（Vite + React/TS），axios `baseURL` 为 `/api`。**实测结论：前端目前只调用系统用户接口，尚未接入任何普通用户接口**：
  - `POST /sys-user/auth/login`（LoginPage）
  - `GET /sys-user/auth/me`（TopBar、RequireAdmin）
  - `GET /sys-user/sys-users`（SystemUsersPage）
  前端目录（`src/pages/`）也仅有 Dashboard、Login、SystemUsers、Placeholder，**没有普通用户端页面**。
  **因此本次路径变更（`/auth/me` → `/me`、`/auth/code` → `/auth/send-code`）对现有前端零影响**，不存在需要同步修改的调用点。方案中「同步修改前端」的表述仅适用于将来新增的用户端开发。
  另注：`web-project/admin-web` 目录为空；`atlas-console` 中 `/sys-user/auth/me` 未受本次改动影响（系统用户认证不在本次范围）。
- **打桩实现的安全边界**：验证码打桩时若将验证码写入日志，日志将成为敏感信息泄漏渠道。建议仅在本地/开发环境打印，并在实现中明确标注替换点。
