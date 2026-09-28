# 普通用户认证体系重构方案

> 状态：**已确认（已按 2026-09-28 评审结论修订）**。方案可进入编码阶段；本文档仍是目标设计，不代表接口已经实现。
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
> - 联系方式在进入数据库查询、写入和 Redis key 前必须统一规范化，防止同一目标以不同文本形式绕过唯一性与防刷限制（见 2.5）。
> - 验证码发送资格通过 Redis Lua 原子占用；渠道总开关更新通过 MySQL 行锁串行化（见 4.2.1、4.3.3）。
> - 换绑采用「旧身份验证码 → 短期 `rebindTicket` → 新联系方式验证码」两阶段流程（见 5.5）。
> - 重置或修改密码成功后吊销该用户全部 Refresh 会话（见 5.5）。
> - Access Token 与 Refresh Token 使用独立校验入口；退出登录只根据 Access Token 中的 `userId + sessionId` 吊销当前会话（见 5.6）。
> - 本轮补齐验证码发送版本、密码并发版本、Refresh 复用检测、输入边界、号码解析依赖及换绑 Ticket 错误码（见 2.6、4.2.1、5.6、6）。
>
> **状态：第 8 节事项已全部确认**，可进入编码阶段。

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

### 2.5 联系方式规范化

所有手机号、邮箱在参与格式校验、数据库查询或写入、唯一性判断、Redis key 拼接之前，必须先由统一的 `ContactNormalizer` 转成规范值。Controller、Service、Mapper 和 Redis Store 不得各自实现不同规则，也不得使用原始输入拼接 Redis key。

- 手机号：去除展示用空格和连字符，结合产品默认地区解析后统一保存为 E.164 格式，例如 `+8613800138000`。
- 邮箱：去除首尾空格，按本产品账号规则将完整地址转为小写；数据库唯一索引、查询和 Redis key 均使用该规范值。
- 日志与错误响应不得回显完整联系方式；需要记录时使用脱敏值。

规范化必须发生在所有业务判断之前，因此 `User@example.com`、`user@example.com` 和带首尾空格的输入会命中同一账号、同一个验证码 key、同一发送锁与同一每日计数。

### 2.6 请求字段约束

所有约束在 Controller DTO 的 HTTP 边界一次完成；Service 只接收已经通过边界校验的值。具体约束如下：

| 字段 | 约束 |
|------|------|
| `target` | 原始输入去首尾空格后，手机号最多 32 个字符，邮箱最多 100 个字符；规范化失败返回 `40000` |
| `targetType` | 只允许 `PHONE` / `EMAIL` |
| `scene` | 只允许 `REGISTER` / `LOGIN` / `RESET_PASSWORD` / `REBIND_OLD` / `REBIND_NEW` |
| `code` / `oldCode` / `newCode` | 必须是 6 位 ASCII 数字 |
| `password` / `newPassword` | 注册时可空；有值时 8~72 个 Unicode 字符，且 UTF-8 编码不超过 72 字节，以匹配 BCrypt 输入上限 |
| `nickname` | 最多 50 个 Unicode 字符 |
| `avatar` | 最多 255 个字符，必须是合法 URL 或明确允许的空值 |
| `rebindTicket` | 非空，最多 128 个 ASCII 字符 |

手机号规范化使用成熟号码解析库（`libphonenumber`），默认地区通过 `app.auth.default-region` 配置，当前默认 `CN`；不能解析或不是有效号码时返回 `40000`，不得用正则自行拼接国家码。该依赖必须在 `pom.xml` 中显式声明并锁定版本。

## 3. 关键约束（必须遵守）

以下为需求中的硬性约束，实现时不得违反：

1. **关闭渠道不得删除数据**。停用手机号或邮箱功能时，只停止该渠道的业务能力，**不得删除用户已绑定的手机号或邮箱数据**。
2. **不得同时关闭所有渠道**。若系统当前只有手机号和邮箱两种认证渠道，后台不得将两者的渠道总开关同时关闭（需保留至少一个可用渠道）。
3. **敏感操作必须验证**。重置密码、换绑联系方式均需通过已绑定的联系方式完成验证码验证。
4. **换绑需双向验证**。换绑流程为：旧身份验证通过 → 新联系方式验证码验证通过 → 完成换绑。两个环节缺一不可。
5. 后端只实现一套重置密码逻辑，不因前端入口不同而拆分业务。
6. **`user_no` 不参与登录**。登录标识只有 `phone` / `email` 两种，`user_no` 仅对外展示。
7. **不写死 6 位**。`user_no` 的列宽、生成逻辑、特殊号规则都必须支持将来扩展到 10 / 12 / 16 位，扩展时不需要改表结构。
8. **联系方式先规范化再使用**。数据库、唯一索引、账号查询和 Redis key 必须使用同一个规范值。
9. **密码变化后吊销会话**。忘记密码重置或登录态修改密码成功后，必须吊销该用户全部 Refresh 会话；本轮统一要求用户重新登录。

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
| `password_version` | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 密码变更版本；注册初始化为 `0`，每次重置或修改密码成功时递增 |
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
7. 最终 `user_no` 必须有**唯一索引**兜底；只有命中 `uk_user_account_user_no` 时才重新生成后 4 位再尝试，手机号或邮箱唯一键冲突必须直接转换为对应业务错误。

**校验与冲突处理（与 `docs/engineering.md`「唯一性交给数据库约束」一致）**：

- 唯一性**不靠先查后插**，靠 `uk_user_account_user_no` 兜底。
- 插入捕获唯一键冲突后，必须先按数据库约束名分类：命中 `uk_user_account_user_no` 才重新生成 `XXXX` 并重试；命中 `uk_user_account_phone` 或 `uk_user_account_email` 时分别转换为 `TARGET_ALREADY_REGISTERED`，不得重新生成 `user_no` 掩盖联系方式并发冲突。`user_no` 重试次数设上限（建议 5 次），超出则抛出服务端异常，**不得静默失败或返回重复号码**。
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

Redis 在本方案中承担**三块**职责：

1. **验证码**：存储 + 防刷（本节 4.2.1 ~ 4.2.3）
2. **Refresh Token 会话状态**：会话存储 + 吊销（见 5.6.2）
3. **换绑临时凭证**：旧身份验证通过后签发短期 `rebindTicket`（见 5.5）

#### 4.2.0 全部 Redis key 一览

统一使用 `auth:` 命名空间，三块职责互不冲突：

| 类别 | 用途 | key | TTL |
|------|------|-----|-----|
| 验证码 | 验证码 | `auth:code:{<targetType>:<target>}:<scene>` | 5 分钟 |
| 验证码 | 60 秒发送锁 | `auth:code:send-lock:{<targetType>:<target>}:<scene>` | 60 秒 |
| 验证码 | 每日发送次数 | `auth:code:daily:{<targetType>:<target>}:<yyyyMMdd>` | 到当日结束（见 4.2.2） |
| 验证码 | 校验失败次数 | `auth:code:fail:{<targetType>:<target>}:<scene>` | 与验证码同生命周期 |
| 验证码 | 本次发送额度预留 | `auth:code:reservation:{<targetType>:<target>}:<scene>:<sendId>` | 60 秒 |
| 换绑 | 旧身份验证凭证 | `auth:rebind:{ticketId}` | 5 分钟 |
| 会话 | 会话主记录（**每个 session 一个独立 key**） | `auth:session:{userId}:{sessionId}` | refreshToken 有效期（默认 30 天） |
| 会话 | 用户会话索引（**ZSET**，member=`sessionId`，score=会话过期时间戳） | `auth:session:user:{userId}` | 随会话续期刷新 |

> **会话类 key 的 `sessionId`**：每次成功登录生成，同一用户可有多个（Web / App / 多浏览器各一个），**新登录不覆盖已有会话**（见 5.6.5）。
>
> **不再使用 `auth:session:revoked:{jti}`**：当前 session 记录中保存的有效 `jti` 与 `passwordVersion` 即可判断旧 Refresh Token 是否失效（见 5.6.3），**本轮不额外维护 revoked key**；检测到 `jti` 复用时直接删除当前 session。
>
> **Redis Cluster hash tag**：验证码相关 key 中的 `{<targetType>:<target>}` 是实际 hash tag，例如 `{PHONE:+8613800138000}`。同一目标的验证码、发送锁、每日计数、失败计数和额度预留必须落在同一 slot，才能由 Lua 原子操作。会话 key 同理使用相同的 `{userId}` hash tag。

#### 4.2.1 验证码操作语义

- **统一输入**：先按 2.5 节规范化 `target`，后续业务查询和全部 Redis key 只使用规范值。
- **原子占用发送资格**：服务端先生成不可预测的 `sendId`，再由一段 Lua 脚本一次完成发送锁检查、每日上限检查、`SET ... NX EX` 发送锁、`INCR` 每日计数、每日计数自然日 TTL、验证码摘要、`sendId` 和本次额度预留 key 写入。验证码记录与发送锁的 value 都必须包含该 `sendId`；脚本任一检查失败时不得修改任何 key，并在成功写入时同步删除旧的 `fail` key，确保每个新验证码重新拥有 5 次失败机会。
- **发送**：Lua 成功后调用 `CodeSender`。明确的本地失败或发送方明确拒绝时，通过补偿脚本仅在验证码记录和发送锁仍同时属于本次 `sendId` 时删除它们；脚本再原子检查并删除本次额度预留 key，只有预留删除成功才对每日计数执行一次 `DECR`。若任一 value 已变化、key 已过期或结果未知，则不补偿，避免旧请求删除较新的验证码或回退较新的发送额度。补偿脚本必须幂等。
- **校验成功**：**原子删除**验证码 key（`DEL`），返回删除数量判断是否抢到本次校验，**删除成功即视为使用完成**，天然防止重复使用。同时清理 `fail` 计数。
- **校验失败**：通过 Lua 原子执行失败次数递增、TTL 对齐与达到 5 次后的验证码删除，避免并发失败请求越过次数限制。
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

该守卫必须在一个 MySQL 事务内执行，并先按固定顺序使用 `SELECT ... FOR UPDATE` 锁定 `auth.phone.enabled`、`auth.email.enabled` 两行，再计算更新后的最终状态并写入目标渠道。不得采用「普通查询后更新」；否则两个并发请求分别关闭 PHONE 与 EMAIL 时可能同时通过检查，最终关闭所有渠道。

若 4 条认证配置缺失或存在重复，更新操作直接失败并记录服务端错误，不得用默认值继续写入。

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
| POST | `/api/user/auth/send-code` | 按场景 | 注册/登录/重置允许匿名；`REBIND_OLD` / `REBIND_NEW` 要求 `SCOPE_user` |
| POST | `/api/user/auth/reset-password` | 匿名 | 重置密码（前端：忘记密码） |
| POST | `/api/user/auth/refresh` | **匿名** | 用 `refreshToken` 换取新双 Token（见 5.6） |
| POST | `/api/user/auth/logout` | `SCOPE_user` | 退出登录，**只吊销当前会话**（见 5.6.9） |

#### 用户资源类

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| GET | `/api/user/me` | `SCOPE_user` | 获取当前用户信息 |
| PUT | `/api/user/me` | `SCOPE_user` | 修改个人资料（昵称、头像） |
| PUT | `/api/user/me/password` | `SCOPE_user` | 修改密码（前端：修改密码） |
| POST | `/api/user/me/rebind-verification` | `SCOPE_user` | 验证旧身份并签发短期 `rebindTicket` |
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
- **换绑采用两阶段凭证**：先调用 `/me/rebind-verification` 消费旧身份验证码并取得 5 分钟有效的 `rebindTicket`，再由 `/me/phone` 或 `/me/email` 验证新联系方式并完成换绑。新验证码失败时 Ticket 仍可在有效期内重试，不要求用户重新验证旧身份。
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

> **另一个必须做的安全校验**：资源服务器使用 `accessJwtDecoder`，必须**拒绝 `tokenType != access` 的令牌**；`/auth/refresh` 使用独立的 `refreshTokenVerifier`，在不进入业务认证链的前提下校验签名、issuer、时间字段和 `tokenType == refresh`。不得用资源服务器 Decoder 验证 Refresh Token，也不得为了 refresh 放宽资源服务器规则。详见 5.6.10。

### 5.3 接口详细定义

#### POST /api/user/auth/send-code

发送验证码，注册、验证码登录、重置密码、换绑旧身份和换绑新联系方式共用。

请求：

```json
{
  "target": "13800138000 或 user@example.com",
  "targetType": "PHONE",
  "scene": "REGISTER"
}
```

`scene=REBIND_NEW` 时额外传入 `"rebindTicket": "<ticketId>"`；其余场景不得传该字段。`REBIND_OLD` 与 `REBIND_NEW` 请求都必须携带有效 Access Token；虽然路径在 Spring Security 中允许匿名进入，但 Service 必须按场景拒绝缺少用户身份的请求。

响应：统一 `Result<Void>`，不返回验证码本身。

校验规则（按序）：

1. 按 2.5 节规范化 `target`，后续只使用规范值。
2. `targetType` 对应渠道总开关必须启用（读 `auth.phone.enabled` / `auth.email.enabled`），否则返回 `AUTH_CHANNEL_DISABLED`。
3. `targetType` 对应验证码子开关必须启用（读 `auth.phone.code.enabled` / `auth.email.code.enabled`），否则返回 `CODE_FUNCTION_DISABLED`。
4. 场景与目标的一致性校验：
   - `REGISTER`：该 `target` **不得**已被注册，否则返回 `TARGET_ALREADY_REGISTERED`。
   - `LOGIN`、`RESET_PASSWORD`：该 `target` **必须**已注册，否则返回 `TARGET_NOT_REGISTERED`。
   - `REBIND_OLD`：必须已登录，且 `target` 必须是当前用户已绑定的可用验证渠道。
   - `REBIND_NEW`：必须已登录，`rebindTicket` 必须有效且其 `userId + sessionId + passwordVersion` 与当前请求和数据库账号状态一致，`target` 不得已被其他账号绑定。
5. 生成验证码，通过 4.2.1 的 Lua 脚本原子检查并占用 60 秒发送锁、每日 10 次额度，同时写入验证码摘要。脚本返回频繁或超限时不得调用发送方。
6. Lua 成功后调用 `CodeSender`；按 4.2.1 的失败补偿规则处理确定失败与未知结果。

> 安全提示：第 4 条的场景一致性校验会暴露「某手机号是否已注册」，这是当前产品确认接受的取舍。若需规避，可统一返回发送成功但不实际发送，代价是用户体验下降。

> 阈值（60 秒、10 条/天）可配置，不写死在代码里，便于上线后按实际短信成本调整。

### 5.4 验证码的通用规则（Redis 实现）

**校验流程**（所有验证码场景共用）：

1. 规范化目标并构造验证码 key 与失败计数 key。
2. 调用一段 Lua 脚本，在同一原子操作中读取验证码摘要并处理本次尝试。
3. 验证码 key 不存在 → 返回 `VERIFICATION_CODE_INVALID`（**已过期与码错误统一归入此码**）。
4. 码不匹配 → 递增失败次数、对齐 TTL；达到 5 次时同时删除验证码 key，返回 `VERIFICATION_CODE_INVALID`。
5. 码匹配 → 同时删除验证码 key 与失败计数 key；只有删除成功的请求视为校验通过，防止并发重复使用。

**防重复使用**：由第 4 步的 `DEL` 保证——**删除成功才视为使用完成**，同一验证码不可能被二次使用。这替代了原表方案的 `used_at IS NULL` 条件更新。

**失败计数规则**（防暴力破解）：

- 每次校验失败，`INCR auth:code:fail:{<targetType>:<target>}:<scene>`。
- 计数达到 **5 次**时，**立即删除验证码 key**，该验证码作废，后续校验一律失败，用户须重新发送。
- `fail` key 与验证码 key 同生命周期（验证码被删除或过期后，fail 计数一并清理，避免遗留）。
- 发送新验证码时由发送资格 Lua 同步删除旧 `fail` key；验证码的失败次数不跨验证码继承。

**并发与竞态说明**：整个比对、失败计数和成功消费由同一 Lua 脚本完成，解决「校验通过到标记已使用之间的窗口」以及并发失败次数丢失问题。

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
3. 校验 `target` 未被注册；该检查只用于提前返回，最终仍依赖 `uk_user_account_phone` / `uk_user_account_email`。插入时若命中联系方式唯一键，统一转换为 `TARGET_ALREADY_REGISTERED`。
4. 生成雪花 `id`；按 4.1.1 规则生成 `user_no`（号段 + 随机后 4 位，命中特殊号或唯一冲突时重试）。
5. `password` 非空则 BCrypt 加密写入；为空则 `password` 存 NULL。
6. 写入 `user_account`，`status` 默认 1，`password_version` 从 `0` 开始。

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

流程：规范化 `target` → 渠道与验证码子开关校验 → 验证码校验（Redis 消费） → 按 `target` 定位用户 → 在 MySQL 事务内使用 `SELECT ... FOR UPDATE` 锁定用户行、更新密码并递增 `password_version` → 提交 MySQL 事务 → 调用 `UserSessionService.revokeAll(userId)` 清理全部 Refresh 会话。

`password_version` 是并发登录和 Redis 清理失败时的最终防线：Refresh 必须在 MySQL 短事务中用 `SELECT status, password_version ... FOR SHARE` 读取当前版本，并保持该读锁直到 Redis Lua CAS 完成；Lua 只有在 `claims.passwordVersion == session.passwordVersion == database.password_version` 时才允许轮换，版本不一致时原子吊销该 Session 并拒绝刷新。这样即使旧密码登录请求在密码事务提交前已通过校验、却在提交后才创建 Session，该 Session 也不能继续刷新。Redis 清理失败不回滚已经提交的密码修改，后续 Refresh 仍会因版本不一致失败，清理任务可安全重试。登录创建 Session 的主记录与用户 ZSET 必须在同一 Redis Lua 脚本中写入。吊销后既有 Access Token 最长仍可使用 30 分钟，这是本轮无状态 Access Token 的已知边界。

#### PUT /api/user/me/password（修改密码，需登录）

```json
{ "target": "13800138000", "targetType": "PHONE", "code": "123456", "newPassword": "newPass123" }
```

与上一个接口**共用同一 Service 方法**，差异有两点：

- 身份从 Token 取得，需校验 `target` 确为该用户已绑定的联系方式（防止改他人密码）。
- 身份验证通过后，仅允许修改**当前登录用户自己**的密码。
- 修改成功后同样先提交 `password_version` 更新，再调用 `UserSessionService.revokeAll(userId)` 清理该用户**全部** Refresh 会话，包括当前会话；本轮统一要求重新登录，不保留当前设备。并发登录与 Refresh 按上一段的版本校验处理。

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

#### POST /api/user/me/rebind-verification（验证旧身份）

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456"
}
```

旧身份允许使用当前账号已绑定且系统启用了验证码能力的手机号或邮箱，不要求与即将换绑的渠道相同。服务端规范化 `target`，确认其属于当前用户，消费 `REBIND_OLD` 验证码后生成一次性随机 `ticketId`，写入：

```text
auth:rebind:{ticketId} = { userId, passwordVersion, sessionId, issuedAt }
TTL = 5 分钟
```

实际 value 为 `{ userId, passwordVersion, sessionId, issuedAt }`。签发时从当前账号读取 `passwordVersion`，并绑定签发该 Ticket 的 Access Token 的 `sessionId`。响应只返回 `rebindTicket`。Ticket 必须使用安全随机值，不包含可由客户端修改的用户信息，不写入日志；只能由同一用户、同一登录会话且密码版本未变化时使用。

#### PUT /api/user/me/phone（换绑手机号）

```json
{
  "rebindTicket": "<ticketId>",
  "newPhone": "13900139000",
  "newCode": "654321"
}
```

#### PUT /api/user/me/email（换绑邮箱）

```json
{
  "rebindTicket": "<ticketId>",
  "newEmail": "new@example.com",
  "newCode": "654321"
}
```

两个换绑接口执行同一流程：规范化新联系方式 → 校验目标渠道开关与 Ticket 归属（比较 `userId + sessionId + passwordVersion`，此时不消费 Ticket）→ 校验并消费 `REBIND_NEW` 验证码 → 用 Lua/CAS 原子校验并删除 `rebindTicket` → 在 MySQL 事务内更新目标列。Ticket 中的 `userId`、`sessionId` 和 `passwordVersion` 必须分别等于当前 Access Token 与数据库当前账号状态；密码变化后旧 Ticket 自动失效。

新验证码错误、渠道不可用或新联系方式已明确被占用时不消费 `rebindTicket`，用户可在 5 分钟有效期内修正后重试。Ticket 过期、已消费、格式非法或不属于当前用户统一返回 `REBIND_TICKET_INVALID`（`40014`），不向客户端区分具体原因。同一 Ticket 的并发请求只能有一个成功删除并进入数据库更新。若 Ticket 消费后数据库因并发唯一键冲突或基础设施故障而更新失败，验证码与 Ticket 均不恢复，用户需重新完成旧身份验证；这是跨 Redis/MySQL 操作选择失败关闭的明确取舍。

当前账号没有任何可用旧验证渠道时，`/rebind-verification` 返回 `NO_AVAILABLE_VERIFY_CHANNEL`。人工申诉 / 账号恢复流程另行设计，不在本次范围。

**约束（数据保留）**：换绑只更新对应列的值，**不删除**原联系方式数据行。用户换绑手机号后，`phone` 列被新值覆盖，这与「关闭渠道不得删除数据」是两件事——后者指停用渠道开关时不清理用户已绑定的值。

**并发保护**：`newPhone` / `newEmail` 的唯一性由 `uk_user_account_phone` / `uk_user_account_email` 保证。两人同时换绑到同一手机号时，数据库唯一键会使其中一个失败，须捕获并转换为 `TARGET_ALREADY_BOUND`，不得先查询再插入。

#### POST /api/user/auth/logout（退出登录）

```http
POST /api/user/auth/logout
Authorization: Bearer <accessToken>
```

接口无请求体。服务端从已经通过资源服务器校验的 `accessToken` 读取 `userId + sessionId`，只允许定位并吊销该 Access Token 所属会话。

服务端通过一段 Lua 脚本原子执行 **`DEL auth:session:{userId}:{sessionId}` + `ZREM auth:session:user:{userId} {sessionId}`**。两个 key 共享 `{userId}` hash tag。

响应：`Result<Void>`。**只吊销当前会话**——每个 session 是独立 key，该用户其他会话（其他设备）**不受任何影响**。吊销后该会话的 Refresh Token 调 `/auth/refresh` 会失败。接口**幂等**（会话已过期时仍返回成功）。

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
PUT /api/sys-user/auth-channels/phone
Content-Type: application/json

{ "channelEnabled": true, "codeEnabled": false }
```

`{channel}` 的文档规范值为小写 `phone` / `email`；Controller 可做大小写兼容，无法转换为已知枚举时返回 `40000`。对应写入的 `sys_config` 键：

| 请求字段 | 写入的 config_key |
|----------|-------------------|
| `channelEnabled` | `auth.phone.enabled` / `auth.email.enabled` |
| `codeEnabled` | `auth.phone.code.enabled` / `auth.email.code.enabled` |

**约束校验（已简化）**：在同一事务中用 `SELECT ... FOR UPDATE` 锁定两个渠道总开关；若本次修改会导致 `auth.phone.enabled` 与 `auth.email.enabled` **同时为 `0`**，则拒绝并返回 `CANNOT_DISABLE_ALL_CHANNELS`，保证至少一个渠道的**总开关**可用。

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
| `auth:session:{userId}:{sessionId}` | **会话主记录**（**每个 session 一个独立 key**），value 为该会话当前有效的 `refreshToken` 摘要与必要信息（`jti`、签发时读取的 `passwordVersion` 等），**不存明文 Token**；TTL = refresh 有效期 |
| `auth:session:user:{userId}` | 该用户的**会话索引 ZSET**：member = `sessionId`，**score = 该会话的过期时间戳**（毫秒）；索引 TTL 在登录和 Refresh 时同步刷新为 refresh 有效期。用于「按用户枚举 / 吊销全部会话 / 清理过期会话」及后续设备管理页面 |

> **为何用 ZSET 而不是 SET**：score 存会话过期时间戳后，可**按分数范围查询与清理已过期会话**（如 `ZRANGEBYSCORE ... 0 <now>` 取出并 `ZREM`），无需逐个 `EXISTS` 探测。这是为后续「过期会话清理」「设备管理页面按到期时间排序」预留的能力。
>
> **不再使用 `auth:session:revoked:{jti}`**：当前 session 记录中的有效 `jti` 与 `passwordVersion` 足以判断旧 Refresh Token 是否失效（见 5.6.3），**无需额外维护 revoked key**；检测到 `jti` 复用时直接删除当前 session。
>
> **`sessionId` 而非 `deviceId`**：本项目管理的是「**登录会话**」——每次成功登录生成一个**独立 `sessionId`**，不识别真实硬件指纹。这样 Web、App、多浏览器天然各自独立，无需处理「同一设备换浏览器」「指纹漂移」等问题。
>
> **key 命名说明**：`auth:session:*` 与验证码的 `auth:code:*` 同属 `auth:` 命名空间，互不冲突。

#### 5.6.3 Rotation 轮换（核心）

`refresh` 成功后采用 **rotation**，**只轮换当前会话**：

1. **当前会话的旧 `refreshToken` 立即失效**（其他会话不受影响）。
2. **同时签发新的 `accessToken` + `refreshToken`**，`sessionId` **保持不变**（同一会话续期）。
3. 新令牌返回给前端，前端须**同时替换两个令牌**。

**判定旧令牌失效的依据**：该会话主记录中的 `jti` 必须与请求令牌的 `jti` **一致**；若记录不存在，返回统一的 `REFRESH_TOKEN_INVALID`。若签名、issuer、时间字段均有效，但记录中的 `jti` 已变，视为 Refresh Token **复用（reuse）**：必须在同一 Lua 脚本中原子删除当前 session 主记录并从用户 ZSET 移除该 session，记录内部安全事件后返回统一的 `REFRESH_TOKEN_INVALID`，不得只拒绝本次请求而保留攻击者可能拿到的新令牌。并发提交同一令牌也会触发该严格策略，因此客户端必须对 refresh 实现 single-flight，避免自身并发请求误吊销会话。

本方案明确接受严格复用策略的取舍：若服务端已经成功轮换但响应丢失，客户端再次提交旧令牌也会触发整个 Session 吊销。客户端不得对无法确认结果的 Refresh 请求盲目重试；收到 `REFRESH_TOKEN_INVALID` 后必须重新登录。

**原子性要求（Lua / CAS）**：rotation 必须用 **Lua 脚本或 CAS（比较并交换）原子地完成「读取 session → 比较旧 `jti` 与 `passwordVersion` → 一致时替换为新 `jti`、刷新主记录 TTL、更新用户会话 ZSET score；`jti` 不一致时删除整个 session」**，不允许多步非原子操作。脚本返回 `ROTATED`、`INVALID` 或 `REUSE_DETECTED` 三种内部结果，外部对后两者统一返回 `REFRESH_TOKEN_INVALID`。

- **`sessionId` 始终不变**，只更新该 session key 内保存的 `jti` 与 TTL；`jti` 或密码版本不匹配时都不得轮换。
- 同一 Lua/CAS 操作同时**刷新 ZSET 中该 `sessionId` 的 score 和索引 TTL**（会话过期时间戳随续期后移，索引 TTL 刷新为 refresh 有效期）。两个 key 必须使用相同的 `{userId}` Redis Cluster hash tag，保证脚本可在同一 slot 执行。
- **不允许先查后写**（这与验证码用 `DEL` 防重放是同一原则）。

#### 5.6.4 退出登录与会话吊销

**退出登录只吊销当前会话**，不影响该用户的其他会话。

**当前 session logout 在同一 Lua 脚本内执行两步**：

1. **`DEL auth:session:{userId}:{sessionId}`** —— 删除当前会话主记录。
2. **`ZREM auth:session:user:{userId} {sessionId}`** —— 从用户会话 ZSET 中移除该 `sessionId`。

- **每个 session 是独立 key，删除当前 session 只影响它自己，不影响该用户的其他会话。** 若删除主记录时该 key 已因 TTL 过期而不存在（`DEL` 返回 0），属正常情况，**只需保证 `ZREM` 被执行**，接口仍返回成功——即 **logout 应设计为幂等**。
- 吊销后，该会话的 `refreshToken` 无法再换取新令牌；**其他会话照常有效**。
- **后续可扩展「退出全部设备」**：按 ZSET 枚举该用户全部 `sessionId`，逐个 `DEL` 会话记录，最后删除 ZSET 本身。**本轮不实现该接口，但 key 结构已支持**。
- **注意**：`accessToken` 是无状态的 JWT，吊销后在其剩余有效期内（最长 30 分钟）**仍然有效**。这是双 Token 模式的固有代价——短有效期就是为了把这个窗口压到可接受范围。若需立即失效，须引入 access token 黑名单，**本轮不做**。

#### 5.6.5 多会话策略（**已确认：多设备 / 多会话**）

**新登录不得覆盖用户已有会话。**

- 每次成功登录（密码登录 / 验证码登录 / 注册自动登录）都生成**一个全新的 `sessionId`**。所有登录流程必须把账号读取时取得的 `passwordVersion` 写入 Access/Refresh claims 和会话主记录；会话主记录、`ZADD auth:session:user:{userId} {过期时间戳} {sessionId}` 和 `PEXPIRE auth:session:user:{userId} <refresh TTL>` 必须由同一 Redis Lua 脚本原子写入。凭据校验完成后不得重新读取版本并覆盖原值，否则会重新打开改密并发窗口。
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

1. 使用独立的 `refreshTokenVerifier` 校验 `refreshToken` 的签名、issuer、时间字段，并确认 `tokenType == refresh`；不得使用只接受 Access Token 的资源服务器 Decoder。
2. 从 claims 取 `userId`、`sessionId`、`jti`、`passwordVersion`。
3. 在短 MySQL 只读事务中用 `SELECT status, password_version FROM user_account WHERE id = ? FOR SHARE` 读取账号当前状态和 `password_version`，并保持该读锁直到第 5 步 Redis Lua/CAS 完成；用户不存在或已禁用时吊销该 session 并拒绝刷新。
4. 生成新 `jti` 并预先签发新的 `accessToken` + `refreshToken`（**携带同一 `sessionId`**）。
5. **用 Lua / CAS 原子执行**：读取 `auth:session:{userId}:{sessionId}`，同时比对旧 `jti`、claims 中的 `passwordVersion` 和第 3 步读取的数据库 `password_version`；三者一致时替换为新 `jti`、刷新主记录 TTL、更新 ZSET score 并刷新 ZSET TTL。若 `jti` 不一致，原子删除整个 session 及 ZSET 成员并返回内部结果 `REUSE_DETECTED`；若任一密码版本不一致，原子删除该 session 并返回 `INVALID`。
6. key 不存在、版本不一致或 `jti` 不一致时，统一返回 `REFRESH_TOKEN_INVALID`；预签发的新令牌不得返回。
7. 原子替换成功后提交第 3 步的只读事务，再返回预签发的新双 Token；任一失败结果都回滚该只读事务并返回错误。

> **`sessionId` 在 rotation 前后始终不变**，仅 `jti` 与过期时间改变。这保证一次续期**不会新增会话**，ZSET 成员数量稳定。
>
> **步骤 5 的比较、替换、TTL 与 ZSET 更新必须在同一原子操作内完成**：若拆成「先读比对、再写替换」，并发提交同一 `refreshToken` 时两个请求都可能通过比对，从而各自签发一组令牌。验证码发送资格和换绑 Ticket 也分别使用各自的 Lua/CAS，三类脚本职责独立。

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

接口无请求体。响应：`Result<Void>`。

**吊销逻辑（当前 session logout = `DEL` + `ZREM`）**：

- 从已经通过 `accessJwtDecoder` 校验的 `accessToken` 认证上下文取 `userId + sessionId`。
- **`DEL auth:session:{userId}:{sessionId}`** 删除当前会话主记录。
- **`ZREM auth:session:user:{userId} {sessionId}`** 从用户会话 ZSET 移除该会话。
- **每个 session 是独立 key，删除当前 session 不影响该用户其他会话。**
- **接口幂等**：若主记录已因 TTL 过期而消失（`DEL` 返回 0），Lua 仍执行 `ZREM` 并返回成功，不报错。

> **后续可扩展「退出全部设备」**（如 `POST /api/user/auth/logout-all`）：按 ZSET 枚举全部 `sessionId` 逐个 `DEL`，最后删除 ZSET。**本轮不实现**。

#### 5.6.10 涉及的 JWT 结构变更

`refreshToken` 与 `accessToken` **必须可区分**，避免用 `refreshToken` 直接调用业务接口。系统用户 Access Token 也必须显式携带 `tokenType=access`，避免启用严格校验后破坏现有管理端登录。claims 设计：

| claim | 系统用户 `accessToken` | 普通用户 `accessToken` | 普通用户 `refreshToken` |
|-------|------------------------|--------------------------|---------------------------|
| `tokenType` | `access` | `access` | `refresh` |
| `userId` | ✅ | ✅ | ✅ |
| `sessionId` | — | ✅ | ✅（与用户 Access Token 相同） |
| `jti` | — | — | ✅（当前令牌唯一 ID，用于 rotation 比对） |
| `passwordVersion` | — | ✅（签发时读取的版本） | ✅（与用户 Access Token 相同） |
| `scope` | `admin` | `user` | ❌ **不带业务 scope** |

- 普通用户两个令牌中的 **`userId + sessionId + passwordVersion` 保持一致**：refresh 用 Refresh Token 定位会话，logout 用 Access Token 定位当前会话；Refresh 还用 `passwordVersion` 检测密码变更后的旧会话。
- `sessionId` 每次登录新生成（见 5.6.5）；同一会话内 refresh 轮换时 **`sessionId` 不变**，只换 `jti`。
- `accessJwtDecoder` 负责资源服务器认证并拒绝 `tokenType != access`；独立的 `refreshTokenVerifier` 只供 `/auth/refresh` 使用并要求 `tokenType == refresh`。两者都执行相同的签名、issuer 和标准时间字段校验。

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
| 400 | `40014` | `REBIND_TICKET_INVALID` | 换绑凭证无效、已过期、已消费或不属于当前用户 |
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
| store | `auth/store/VerificationCodeStore.java` | **验证码 Redis 存取**：Lua 原子占用发送资格、写入 `sendId`/额度预留、校验并消费、失败计数、带版本保护的失败补偿 |
| store | `auth/store/RefreshTokenStore.java` | **会话 Redis 存取**：按 `{userId}:{sessionId}` 写入 / 读取会话、**Lua / CAS 原子比对并替换 `jti` 与 `passwordVersion`**、复用检测后吊销整个会话、`ZADD`/`ZREM`/`PEXPIRE` 维护用户会话 ZSET（score=过期时间戳）、按 ZSET 枚举 / 吊销用户全部会话 |
| store | `auth/store/RebindTicketStore.java` | 5 分钟换绑凭证的签发、归属校验（`userId + sessionId + passwordVersion`）与 Lua/CAS 原子消费；无效、过期、已消费和归属错误统一映射 `REBIND_TICKET_INVALID` |
| service | `auth/service/UserSessionService.java` | 双 Token 签发编排、refresh rotation 与 reuse detection、当前会话退出以及密码变化后的全部会话清理（见 5.6） |
| service | `auth/service/RefreshTokenVerifier.java` | 独立校验 Refresh Token 的签名、issuer、时间字段与 `tokenType=refresh`，不进入资源服务器认证链 |
| vo | `auth/vo/UserTokenVO.java` | 登录 / 注册 / refresh 统一返回体（`accessToken` + `refreshToken`） |
| dto | `auth/dto/UserRefreshDTO.java` | `/auth/refresh` 请求体；`/auth/logout` 无请求体 |
| entity | `config/entity/SysConfig.java` | `sys_config` 配置实体 |
| mapper | `config/mapper/SysConfigMapper.java` + XML | `sys_config` 数据访问（按 key 查、按 group 批量查、更新、`SELECT ... FOR UPDATE` 锁定两个渠道总开关） |
| service | `auth/service/AuthChannelConfigService.java` | 渠道开关查询与校验（**读 `sys_config` 的 4 个 `auth.*` 键**，含推导规则见 4.3.2） |
| service | `auth/service/VerificationCodeService.java` | 验证码生成、发送编排、校验、防刷（依赖 `VerificationCodeStore`） |
| service | `auth/service/sender/CodeSender.java` | 发送接口（短信/邮件） |
| service | `auth/service/sender/LoggingCodeSender.java` | **打桩实现**：仅打日志，不真实发送 |
| service | `user/service/UserMeService.java`（或并入既有 Service） | 当前用户信息、资料修改、换绑 |
| service | `user/service/ContactNormalizer.java` | 手机号 E.164 与邮箱统一小写等规范化；数据库和 Redis 统一使用规范值 |
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
| `user/entity/UserAccount.java` | 移除 `username`；新增 `userNo`/`phone`/`email`/`password`/`passwordVersion`；保留 `nickname`/`avatar`/`status` |
| `mapper/user/UserAccountMapper.xml` | 字段与查询条件全部重写：按 `phone`/`email` 查询，`keyword` 改为搜 `user_no`/`phone`/`email` |
| `user/mapper/UserAccountMapper.java` | 新增按 `phone`/`email` 查询、更新密码并递增 `password_version`、更新手机号/邮箱、更新资料、插入用户；唯一键异常按约束名分类转换 |
| `auth/service/impl/UserAuthServiceImpl.java` | 重写为双登录 + 验证码场景；登录/注册成功后签发双 Token |
| `auth/service/JwtTokenService.java` | `createUserAccessToken` 的 `username` claim 改为 `user_no`；**新增 `createUserRefreshToken`**；普通用户两个令牌携带同一 `passwordVersion`，系统用户与普通用户 Access Token 都增加 `tokenType=access`，Refresh Token 使用 `tokenType=refresh`；两令牌有效期分别读取配置 |
| `auth/controller/UserAuthController.java` | 路径改为 `/auth/register`、`/auth/login`、`/auth/send-code`、`/auth/reset-password`、`/auth/refresh`、`/auth/logout`；移除 `/auth/me` |
| `config/JwtConfig.java` | 提供资源服务器专用 `accessJwtDecoder`（拒绝 `tokenType != access`）及 Refresh Token 基础解码能力；新增 refresh 有效期配置绑定（见 5.6.10） |
| `user/dto/UserAccountQueryDTO.java` | `keyword` 语义变更，`status` 保留 |
| `user/vo/UserAccountListVO.java` | 字段调整为 `user_no`/`phone`/`email`/`nickname`/`status` 等 |
| `user/vo/UserInfoVO.java` | 字段调整；或由新的 `/me` 响应 VO 取代 |
| `config/SecurityConfig.java` | 匿名放行认证类接口（见 5.2）；`logout` 不在放行清单 |
| `common/error/ErrorCode.java` | 新增错误码（见 6） |
| `user/service/impl/UserAccountServiceImpl.java` | 列表查询字段调整 |
| `pom.xml` | 新增 `spring-boot-starter-data-redis` 与锁定版本的 `com.googlecode.libphonenumber:libphonenumber` 依赖 |
| `src/main/resources/application.yml` | 新增 `spring.data.redis` 连接配置（地址/端口/密码走环境变量）；新增 `app.auth.default-region: CN`；新增 `app.user-no` 配置块：`secret`（`USER_NO_SECRET`）、`daily-segment-min/max`、`random-digit-count`、`max-retry`；新增验证码 TTL/间隔/日上限配置；**`app.jwt.access-token-expiration` 由 `24h` 改为 `30m`，新增 `refresh-token-expiration: 30d`** |
| `web_project.sql` | `user_account` 新增 `password_version` 字段；新增 `sys_config` 建表 + 4 条 `auth.*` 初始化数据；**删除 `verification_codes`、`auth_channel_config` 建表语句** |
| `database/migrations/20260928_user_account_dual_login.sql` | **重写**：`user_account` 增加 `password_version`，去掉 `verification_codes` 与 `auth_channel_config` 建表，改为建 `sys_config` 并插入 4 条数据 |
| `docs/api.md` | **只在代码实现并通过验收后**重写普通用户章节；编码期间继续保留当前实现契约，避免把目标方案误写成已上线接口 |
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
| **新增** | `ContactNormalizer` 测试：邮箱大小写/空格与手机号展示格式统一命中同一规范值、合法号码按默认地区转为 E.164、非法号码被拒绝、数据库查询和 Redis key 使用同一规范值 |
| **新增** | 验证码防刷（60 秒锁、每日 10 条）、新验证码重置失败计数、旧 `sendId` 的失败补偿不得删除新验证码或回退新额度、渠道开关推导、两阶段换绑（含 Ticket 并发消费、失败关闭和密码版本变化后失效）、匿名 `REBIND_OLD/REBIND_NEW` 必须返回 401、重置密码、登录方式判定的测试 |
| **新增** | `UserNoGenerator` 单元测试：号段映射范围、同日号段稳定、特殊号码全部被过滤、`user_no` 唯一冲突重试、phone/email 唯一冲突不重试号码、位数可配置 |
| **新增** | `VerificationCodeStore` 测试：并发发送只有一个取得资格、锁与每日计数原子写入、确定发送失败补偿及额度预留幂等、旧 `sendId` 不得删除新验证码、重新发送清理旧失败计数、验证码 TTL、校验成功后不可重复使用、失败 5 次后失效、每日计数 TTL 落在当日结束 |
| **新增** | `AuthChannelConfigService` 测试：4 个键的推导规则、「两个总开关不得同时为 0」的守卫，以及两个并发请求分别关闭 PHONE/EMAIL 时只能一个成功 |
| **新增** | `RefreshTokenStore` 测试：会话写入与 `passwordVersion` 校验、rotation 后旧 `jti` 失效、**并发提交同一令牌只有一次成功且复用会吊销整个 session（Lua/CAS）**、密码版本变化后旧 session 不能 refresh、**连续 refresh 后 ZSET 成员数保持为 1（sessionId 不变）**、吊销单个会话不影响其他会话、按 ZSET 枚举 / 吊销全部会话 |
| **新增** | 双 Token 测试：系统用户和普通用户 Access Token 均通过 `accessJwtDecoder`；`refreshToken` 不能访问业务接口；Access Token 不能用于 refresh；refresh 后旧 Refresh Token 立即失效；无请求体 logout 后该会话 refresh 失败 |
| **新增** | 多会话测试：**同一用户两次登录产生两个独立会话、互不影响**；A 会话 refresh 不影响 B 会话；A 会话 logout 后 B 会话仍可用；**新登录不使旧会话失效** |
| **新增** | 密码安全测试：忘记密码重置与登录态修改密码成功后全部 Refresh 会话失效；旧密码已通过校验但在改密提交后才创建的 Session 因 `passwordVersion` 不一致而不能 refresh；Redis 清理失败时密码版本已提交且旧 Refresh 仍被拒绝，清理可重试 |
| **测试环境要求** | 涉及 Redis 的测试需要可用 Redis 实例（本地已有）。工程规范要求涉及中间件的修改需在真实实例验证，**不得用 mock 通过当作验证通过**（对应 `docs/engineering.md` 对 MySQL 的同类要求） |

## 8. 已确认事项

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
| 13 | Redis 用途范围 | ✅ **已确认**：验证码存储与防刷、Refresh 会话、换绑 Ticket | 详见 4.2 |
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
| 26 | 联系方式是否统一规范化 | ✅ **已确认：统一规范化**，数据库与 Redis 只使用规范值 | 详见 2.5 |
| 27 | 验证码防刷并发策略 | ✅ **已确认：Lua 原子占用**发送锁、每日额度与验证码 key | 详见 4.2.1 |
| 28 | 换绑验证码流程 | ✅ **已确认：两阶段 `rebindTicket`**，新验证失败不要求重做旧身份验证 | 详见 5.5 |
| 29 | 密码变化后的会话策略 | ✅ **已确认：吊销全部 Refresh 会话并要求重新登录** | 详见 5.5 |
| 30 | 渠道总开关并发守卫 | ✅ **已确认：MySQL `SELECT ... FOR UPDATE` 锁定两行** | 详见 4.3.3 |
| 31 | Access / Refresh 校验入口 | ✅ **已确认：独立校验器**，资源服务器只接受 Access Token | 详见 5.6.10 |
| 32 | Logout 如何定位会话 | ✅ **已确认：无请求体，使用 Access Token 的 `userId + sessionId`** | 详见 5.6.9 |
| 33 | 渠道路由大小写 | ✅ **已确认：文档统一小写 `phone` / `email`，Controller 可兼容大小写** | 详见 5.5 管理端接口 |

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

1. 确认第 8 节事项（**已全部清零**，不再阻塞）。
2. 表结构定稿：`web_project.sql` 新增 `sys_config`、删除两张旧表；迁移脚本同步。
3. 引入 Redis：`pom.xml` 依赖 + `application.yml` 连接配置 + `RedisConfig` 序列化，并**先验证连通性**。
4. 配置落位：`application.yml` 增加 `app.user-no`、验证码 TTL/间隔/日上限、**双 Token 有效期（access 30m / refresh 30d）**配置。
5. 实现 `ContactNormalizer`，确保后续数据库与 Redis 功能从第一步就使用规范值。
6. 实现 `sys_config` 实体、Mapper 与 `AuthChannelConfigService`，包含两行 `FOR UPDATE` 并发守卫。
7. 实现 `UserNoGenerator` 与 `UserNoProperties`（含特殊号配置与重试）。
8. 实现 `VerificationCodeStore` 与 `RebindTicketStore` 的 Lua/CAS 原子操作，并先完成并发测试。
9. 实现 `RefreshTokenStore`、`UserSessionService`、双 Token 签发、`accessJwtDecoder` 与 `refreshTokenVerifier`。
10. 实现 `VerificationCodeService`（发送编排 + `CodeSender` 打桩实现）。
11. 实现注册、双登录、refresh、重置密码与无请求体 logout；从首次提交起直接返回完整双 Token，不引入临时单 Token 阶段。
12. 实现 `/me`、改密、两阶段换绑与资料修改接口。
13. 完成 `SecurityConfig` 与错误码，并覆盖系统用户登录回归。
14. 在真实 Redis + MySQL 上执行并发、事务与接口验收，运行 `clean verify`。
15. 验收通过后再重写 `docs/api.md`，并同步 `docs/engineering.md` 与 `README.md`。

## 10. 风险提示

- **目标表结构与当前代码必须在同一实施批次切换**：当前生产代码、Mapper 与 `web_project.sql` 仍共同使用 `username`，彼此一致；实现本方案时必须同时切换为 `user_no` / `phone` / `email`，避免只改 SQL 或只改 Java 后出现 `Unknown column`。Mock 测试不能替代真实 MySQL 验收。
- **本方案改动面较大**，涉及认证授权核心链路 + 新增 Redis 中间件，建议分步实施并在可丢弃的 MySQL 测试库 + 真实 Redis 上做验证（`docs/engineering.md` 已要求：涉及数据库查询的修改需单独在 MySQL 测试库验证）。
- **Redis 成为新的可用性依赖**：验证码发送与校验全部依赖 Redis。若 Redis 不可用，注册、验证码登录、重置密码、换绑都会失败（密码登录不受影响，因其不依赖验证码）。处理约定：
  - Redis 故障按**基础设施故障**处理，返回 `INTERNAL_SERVER_ERROR`（`50000`），**不新增业务错误码**、不伪装成「验证码错误」，避免误导用户反复重试。
  - **禁止**在 Redis 不可用时降级为「跳过验证码校验」——这会造成认证绕过，属严重安全问题。
  - 部署时需明确 Redis 的可用性等级与告警；本地开发依赖本机 Redis。
- **每日上限的 TTL 计算容易写错**：`auth:code:daily:*` 若用固定 `86400` 秒，会变成「首次发送后滚动 24 小时」，与「自然日 10 条」不符（见 4.2.2）。必须按「距当日 24:00 的剩余秒数」设置，且要有测试覆盖。这是本次改动里最容易埋错的一处。
- **验证码不落库带来的可观测性缺口**：无法统计历史发送量、无法追溯某次发送记录（见 4.2.3）。打桩阶段影响有限，但**上线接入真实短信渠道后**，若出现资费异常或用户投诉，将缺少直接的数据依据，只能依赖日志采集。建议届时同步上线发送事件日志。
- **key 命名空间隔离**：Redis key 使用 `auth:` 前缀。若同一 Redis 实例被多个应用或环境共用，需确认前缀不冲突，或将不同环境指向不同 db/实例。
- **短信成本风险**：若不实现防刷或阈值过宽，攻击者可反复触发发送，直接产生短信费用。因此联系方式规范化与第 5.3 节的 Lua 原子发送资格占用属于**必做项**而非可选项。注意：当前发送渠道为打桩实现，**上线前必须替换为真实渠道并复核防刷阈值**。
- **渠道开关的运维风险**：若运维误将两个渠道总开关都关闭，且业务校验失效，会导致所有用户无法登录、无法注册、**且无法通过被关闭的渠道找回**。第 4.3.3 节与 `/api/sys-user/auth-channels` 的约束校验是防止此场景的唯一防线，必须有测试覆盖。
- **`sys_config` 的越权写入风险**：`sys_config` 是通用配置表，后续若有其他模块写入，需注意 `auth.*` 键只能由管理端渠道配置接口维护，避免其他入口误改导致认证能力被关闭。同时必须坚持「敏感配置不入库」——一旦有人把密钥类配置写进 `sys_config`，将以明文形式落库。
- **渠道停用导致的换绑死锁风险**：若用户只绑定了一种联系方式，而该渠道被后台停用，则该用户**没有任何可用旧验证渠道，无法换绑**（详见 5.5）。此时唯一的自救路径是后台重新启用该渠道。运维停用渠道前需评估影响面。
- **`user_no` 位数的扩展约束**：当前生成 6 位。将来扩展到 10 / 12 / 16 位时，须同步确认两点——① 特殊号过滤规则随位数调整（例如 10 位时的连号判定窗口）；② 前端展示位宽与 `VARCHAR(32)` 的上限。**扩展本身不需要改表结构**，这是选 `VARCHAR(32)` 而非 `CHAR(6)` 的原因。
- **`user_no` 的号段可被推测**：`PP` 由「日期 + secret」确定性得出，知道算法即可推算当日号段，后 4 位随机空间仅 1 万。因此 `user_no` **只能作为展示标识，不可作为凭据、不可用于登录、不可单独用于鉴权**。这是需求已确认的定位（登录仍靠手机号/邮箱），实现时不得扩大其用途。
- **`USER_NO_SECRET` 的运维约束**：该 secret **上线后必须保持稳定**，不得因安全策略随 JWT 密钥一起轮换。轮换不会造成 `user_no` 重复（唯一性由数据库兜底），但会让同一天注册的用户号段不一致，属可见的异常现象。部署时应把它与 `JWT_SECRET` 分开管理、分开记录轮换计划。若 secret 缺失或为空，**应用启动应直接失败**（参照 `JwtConfig` 对 JWT secret 的处理），不得静默降级为某个默认值。
- **防刷阈值是成本开关**：每日 10 条、间隔 60 秒是按「短信按条计费」预设的保守值。上线接入真实渠道后需按实际资费和用户投诉率复核；阈值可配置，不需要改代码。
- **`refreshToken` 不能当访问令牌用（严重漏洞）**：两类令牌同源同算法，若资源服务器不校验 `tokenType`，则**长效的 refreshToken 可直接用于访问业务接口**，等于把 30 分钟的有效期绕成 30 天，整个双 Token 设计失效。因此 `accessJwtDecoder` 必须拒绝 `tokenType != access`，`refreshTokenVerifier` 必须只接受 `tokenType=refresh`，且系统用户 Access Token 也要显式带 `tokenType=access`。测试必须覆盖两类令牌的双向误用。
- **吊销 refresh 后 `accessToken` 仍有残余有效期**：`accessToken` 无状态，服务端不保存也不查询。退出登录只删除 Redis 中的 refresh 会话，**已签发的 `accessToken` 在其剩余有效期内（最长 30 分钟）依然可用**。这是无状态 JWT 的固有代价，不是缺陷。若业务要求「退出即刻失效」，必须改为有状态校验（每次请求查 Redis 会话），代价是每个请求多一次 Redis 往返——**本轮不采用**。
- **多会话下新登录不会挤掉旧会话**：本轮**不限制最大设备数**（见 5.6.5），也**不做超限驱逐**。用户正常登录不会使其他会话失效；但忘记密码重置或登录态修改密码会吊销全部 Refresh 会话，作为账号恢复的安全边界。后续仍可增加「查看登录设备」「踢出指定会话」和「退出全部设备」页面。
- **会话记录中保存 `jti` / `passwordVersion` 而非明文令牌**：会话主记录保存当前有效 `jti` 与签发时的 `passwordVersion`，用于 rotation、复用检测和密码变更隔离。**严禁把明文 `refreshToken` 存进 Redis**——Redis 一旦被读取或误导出，等同于令牌泄露。5.6.2 已明确「不存明文 Token」。
- **ZSET 中的成员可能短暂残留**：`auth:session:user:{userId}` 是 ZSET，会话主记录 key 到期自动消失，但成员要等按 score 清理或索引 TTL 到期才移除。登录和 Refresh 必须在同一 Lua 操作中刷新索引 TTL；枚举仍需按 score 过滤过期项（`ZRANGEBYSCORE ... <now>`），避免设备管理显示已失效会话。
- **Redis 故障时 refresh 必须失败，不得放行**：refresh 的校验依赖 Redis 中的会话状态。若 Redis 不可用时选择「跳过校验直接签发」，等于任何人拿旧 refreshToken 都能无限续期，会话吊销彻底失效。**必须直接返回失败**（按基础设施故障处理）。这会造成「Redis 挂了 → 用户在 accessToken 过期后必须重新登录」，属可接受的降级方向。
- **rotation 的原子性必须靠 Lua / CAS**：rotation 要求「旧 refreshToken 立即失效」，但客户端并发提交同一 refreshToken（如多标签页同时刷新）时，若用「先读比对、再写替换」，两个请求可能都通过比对并各自签发令牌。**必须用 Lua 脚本或 `WATCH/MULTI` (CAS) 把「比对旧 `jti` 与 `passwordVersion` → 一致时替换，不一致时按 `INVALID` / `REUSE_DETECTED` 处理并在复用时吊销整个 session」放在一次原子操作内**（见 5.6.3 / 5.6.7）。前端也应做单飞（single-flight）控制，避免自身触发该竞争。
- **rotation 只影响当前会话（需正确实现）**：多会话下 rotation 必须**只改当前 `sessionId` 对应 key 的 `jti`**，`sessionId` 本身**始终不变**。若实现时误用「删旧 key + 新建 session」，会把一次续期变成「新增一个会话」，**导致 ZSET 成员无限增长、旧会话残留**。这是本方案最容易写错的一处，须有测试覆盖（连续 refresh 多次后，ZSET 成员数应保持为 1）。
- **双 Token 引入了额外的客户端复杂度**：前端必须实现「401 → 自动 refresh → 重放原请求」的拦截器，并处理 refresh 本身失败（跳登录页）。这部分前端工作**不在本次后端方案范围内**，但排期时不能遗漏——否则接口做完了前端仍不可用。当前 `atlas-console` 尚无用户端页面，属新增开发。
- **`WebSocket` / 长连接场景未覆盖**：双 Token 方案只针对 HTTP 请求。若将来引入长连接，`accessToken` 30 分钟过期需额外的心跳续期机制，本轮不涉及。
- **接口路径不兼容**：`/api/user/auth/me` 改为 `/api/user/me`，且 `/auth/code` 更名为 `/auth/send-code`。**已实测前端无任何普通用户接口调用点**（见下条），因此不存在被破坏的调用方。此条仅在将来出现外部调用方时重新评估。
- **前端现状核实（2026-09-28）**：前端工程为 `D:\Develop\Projects\atlas\frontend`（Vite + React/TS），axios `baseURL` 为 `/api`。**当前前端只调用系统用户接口，尚未接入任何普通用户接口**：
  - `POST /sys-user/auth/login`（LoginPage）
  - `GET /sys-user/auth/me`（TopBar、RequireAdmin）
  - `GET /sys-user/sys-users`（SystemUsersPage）
  前端目录（`src/pages/`）也仅有 Dashboard、Login、SystemUsers、Placeholder，**没有普通用户端页面**。
  **因此本次路径变更（`/auth/me` → `/me`、`/auth/code` → `/auth/send-code`）对现有前端零影响**，不存在需要同步修改的调用点。方案中「同步修改前端」的表述仅适用于将来新增的用户端开发。
  `atlas-console` 中 `/sys-user/auth/me` 未受本次改动影响（系统用户认证不在本次范围）。
- **打桩实现的安全边界**：验证码打桩时若将验证码写入日志，日志将成为敏感信息泄漏渠道。建议仅在本地/开发环境打印，并在实现中明确标注替换点。
