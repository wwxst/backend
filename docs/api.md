# 账号接口文档

## 基础约定

- 基础地址：`http://localhost:8080`
- 请求和响应编码：UTF-8
- 公开接口无需认证；其余接口需要 `Authorization: Bearer <accessToken>`。
- 系统用户接口使用 `SCOPE_admin`，普通用户接口使用 `SCOPE_user`。
- 普通用户采用**双 Token**：`accessToken`（30 分钟）用于业务接口鉴权，`refreshToken`（30 天）仅用于换取新令牌。`refreshToken` **不能**访问业务接口，`accessToken` **不能**用于刷新。
- 普通用户支持**多设备登录**：每次登录生成独立会话，互不影响；退出登录只吊销当前会话。

## 统一响应

成功响应：

```json
{
  "code": 200,
  "msg": null,
  "data": {}
}
```

失败响应：

```json
{
  "code": 40101,
  "msg": "账号或密码错误",
  "data": null
}
```

分页接口的 `data` 结构：

```json
{
  "total": 1,
  "page": 1,
  "pageSize": 10,
  "records": []
}
```

## 系统用户

系统用户以数据库表 `sys_user` 为命名基准，后端请求与响应类型统一使用 `SysUser` 前缀。

本地开发初始化账号为 `admin`，密码为 `123456`；生产或共享环境应替换该账号密码。

### 登录

```http
POST /api/sys-user/auth/login
Content-Type: application/json
```

请求：

```json
{
  "username": "admin",
  "password": "password"
}
```

成功响应 `data`：

```json
{
  "id": 1,
  "username": "admin",
  "nickname": "系统管理员",
  "avatar": null,
  "token": "<jwt>"
}
```

登录失败返回 `40101`。账号被禁用返回 `40302`。

### 当前系统用户

```http
GET /api/sys-user/auth/me
Authorization: Bearer <jwt>
```

成功响应 `data`：

```json
{
  "id": 1,
  "username": "admin",
  "nickname": "系统管理员",
  "avatar": null
}
```

### 系统用户列表

```http
GET /api/sys-user/sys-users?page=1&pageSize=10&keyword=admin&status=1
Authorization: Bearer <jwt>
```

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | 否 | `1` | 页码，最小值 `1` |
| `pageSize` | 否 | `10` | 每页数量，范围 `1-100` |
| `keyword` | 否 | 空 | 按账号或昵称模糊搜索，最多 30 个字符 |
| `status` | 否 | 空 | `0` 禁用，`1` 正常；不传查询全部 |

`records` 对应 `SysUserListVO`，字段为 `id`、`username`、`nickname`、`status`、`createdAt`、`updatedAt`。不会返回密码字段。

### 认证渠道开关

系统用户管理手机号 / 邮箱两个认证渠道的开关。渠道总开关控制该渠道的**全部**能力（登录、注册、换绑）；验证码子开关只控制该渠道的**验证码发送与校验**。子开关是总开关的下级，总开关关闭时子开关不起作用。

```http
GET /api/sys-user/auth-channels
Authorization: Bearer <sys-user-jwt>
```

成功响应 `data`：

```json
{
  "phone": { "channelEnabled": true, "codeEnabled": true },
  "email": { "channelEnabled": true, "codeEnabled": true }
}
```

```http
PUT /api/sys-user/auth-channels/{channel}
Authorization: Bearer <sys-user-jwt>
Content-Type: application/json
```

`{channel}` 取 `phone` 或 `email`（大小写不敏感）。

请求：

```json
{
  "channelEnabled": true,
  "codeEnabled": false
}
```

两个渠道总开关**不允许同时关闭**，否则返回 `40009`，避免所有用户既无法登录也无法找回。

## 普通用户

普通用户以手机号或邮箱作为登录标识，没有 `username` 字段。对外展示的编号为 `user_no`（当前为 6 位数字，由号段 + 随机位生成，不含特殊号；长度可通过配置扩展）。

### 发送验证码

```http
POST /api/user/auth/send-code
Content-Type: application/json
```

请求：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "scene": "REGISTER"
}
```

| 字段 | 说明 |
|------|------|
| `target` | 手机号或邮箱 |
| `targetType` | `PHONE` 或 `EMAIL` |
| `scene` | `REGISTER`、`LOGIN`、`RESET_PASSWORD`、`REBIND_OLD`、`REBIND_NEW` |

发送频率限制：同一场景 + 同一目标 **60 秒**内只能发送一次（`40003`）；每个目标每日上限 **10 条**（`40004`，按自然日计）。渠道或验证码功能停用分别返回 `40001`、`40002`。

### 注册

```http
POST /api/user/auth/register
Content-Type: application/json
```

请求：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456",
  "password": "Passw0rd!"
}
```

`password` 可为空：不传即注册为**纯验证码登录**账号，之后用密码登录返回 `40103`，提示改用验证码登录。

成功响应 `data`（**注册即登录**）：

```json
{
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>"
}
```

已注册返回 `40007`。注册要求该渠道总开关与验证码子开关同时启用。

> 验证码在**校验时即被消费**。若后续步骤（如发现已注册）失败，验证码不会回滚，需重新获取。

### 登录

密码登录与验证码登录**共用同一路径**，由请求体字段区分：两者必须**恰好提供其一**，同时提供或都不提供返回 `40013`。

```http
POST /api/user/auth/login
Content-Type: application/json
```

密码登录：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "password": "Passw0rd!"
}
```

验证码登录：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456"
}
```

成功响应 `data`：

```json
{
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>"
}
```

校验顺序：渠道开关 → 用户存在 → 账号未禁用 → 凭据匹配。账号不存在与密码错误统一返回 `40101`，不泄露账号是否存在。账号被禁用返回 `40303`。

密码登录只要求渠道总开关；验证码登录要求总开关 + 验证码子开关。

### 刷新令牌

```http
POST /api/user/auth/refresh
Content-Type: application/json
```

请求：

```json
{
  "refreshToken": "<refreshToken>"
}
```

成功响应 `data` 为新的双 Token：

```json
{
  "accessToken": "<newAccessToken>",
  "refreshToken": "<newRefreshToken>"
}
```

刷新采用 **rotation**：每次刷新都会替换 `refreshToken` 的内部标识，且 `sessionId` 保持不变。同一个 `refreshToken` 只能成功使用一次，旧令牌再次使用返回 `40104`。

### 退出登录

```http
POST /api/user/auth/logout
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求：

```json
{
  "refreshToken": "<refreshToken>"
}
```

只吊销**当前会话**，同账号在其他设备上的会话不受影响。接口幂等：重复退出仍返回成功。匿名调用返回 `40102`。

### 当前普通用户

```http
GET /api/user/me
Authorization: Bearer <accessToken>
```

成功响应 `data`：

```json
{
  "id": 1,
  "userNo": "123456",
  "phone": "13800138000",
  "email": null,
  "nickname": "普通用户",
  "avatar": null,
  "status": 1,
  "createdAt": "2026-09-10T10:00:00"
}
```

### 修改资料

```http
PUT /api/user/me
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求：

```json
{
  "nickname": "新昵称",
  "avatar": "https://example.com/avatar.png"
}
```

只允许修改昵称和头像。手机号与邮箱必须走换绑流程，不能通过本接口直接修改。

### 修改密码（已登录）

```http
PUT /api/user/me/password
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456",
  "newPassword": "NewPassw0rd!"
}
```

`target` 必须是当前登录用户自己已绑定的联系方式，否则返回 `40010`；验证码场景用 `RESET_PASSWORD`。

### 重置密码（忘记密码，匿名）

```http
POST /api/user/auth/reset-password
Content-Type: application/json
```

请求：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456",
  "newPassword": "NewPassw0rd!"
}
```

目标未注册返回 `40008`。

### 换绑手机号

```http
PUT /api/user/me/phone
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求：

```json
{
  "oldTarget": "13800138000",
  "oldTargetType": "PHONE",
  "oldCode": "123456",
  "newPhone": "13900139000",
  "newCode": "654321"
}
```

**两步验证**：先用 `REBIND_OLD` 场景验证旧身份，再用 `REBIND_NEW` 场景验证新联系方式，两步都通过才写库。

- 旧身份允许使用当前账号已绑定的手机号或邮箱**二选一**，不要求是被更换的那个渠道。
- `oldTarget` 与账号已绑定的值不一致返回 `40010`。
- 新联系方式已被其他账号占用返回 `40011`。
- 旧渠道与新渠道都要求总开关 + 验证码子开关启用。

### 换绑邮箱

```http
PUT /api/user/me/email
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求：

```json
{
  "oldTarget": "13800138000",
  "oldTargetType": "PHONE",
  "oldCode": "123456",
  "newEmail": "new@example.com",
  "newCode": "654321"
}
```

规则与换绑手机号一致。

### 普通用户列表

```http
GET /api/sys-user/users?page=1&pageSize=10&keyword=user&status=1
Authorization: Bearer <sys-user-jwt>
```

该接口由系统用户访问，查询参数和分页结构与系统用户列表相同；`keyword` 最多 50 个字符，按 `user_no`、手机号或邮箱模糊匹配。`records` 字段为 `id`、`userNo`、`phone`、`email`、`nickname`、`avatar`、`status`、`createdAt`、`updatedAt`，不会返回密码字段。

## 错误码

### 通用

| HTTP | 业务码 | 说明 |
|------|--------|------|
| 400 | `40000` | 请求参数不正确 |
| 401 | `40101` | 账号或密码错误 |
| 401 | `40102` | 登录状态无效或已过期 |
| 403 | `40301` | 没有权限访问该接口 |
| 403 | `40302` | 系统用户已禁用 |
| 403 | `40303` | 普通用户已禁用 |
| 500 | `50000` | 服务器内部异常 |

### 认证渠道与验证码

| HTTP | 业务码 | 说明 |
|------|--------|------|
| 400 | `40001` | 该认证渠道已停用 |
| 400 | `40002` | 该渠道验证码功能已停用 |
| 400 | `40003` | 验证码发送过于频繁，请稍后再试 |
| 400 | `40004` | 验证码发送次数已达今日上限 |
| 400 | `40005` | 验证码错误或已失效 |
| 400 | `40009` | 不允许同时停用所有认证渠道 |

### 账号与联系方式

| HTTP | 业务码 | 说明 |
|------|--------|------|
| 400 | `40007` | 该手机号或邮箱已注册 |
| 400 | `40008` | 该手机号或邮箱未注册 |
| 400 | `40010` | 该联系方式未绑定到当前账号 |
| 400 | `40011` | 该联系方式已被其他账号绑定 |
| 400 | `40012` | 当前账号没有可用的身份验证渠道 |
| 400 | `40013` | 密码与验证码不可同时提交 |

### 令牌与登录状态

| HTTP | 业务码 | 说明 |
|------|--------|------|
| 401 | `40103` | 该账号未设置密码，请改用验证码登录 |
| 401 | `40104` | 刷新令牌无效、已过期或已被吊销 |
