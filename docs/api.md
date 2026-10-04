# 账号接口文档

> 状态：当前实现。本文档只描述生产代码中已经存在的接口。

## 基础约定

- 基础地址：`http://localhost:8080`
- 请求和响应编码：UTF-8
- 登录、注册、Refresh、发送验证码和重置密码接口公开访问；其他接口需要 `Authorization: Bearer <accessToken>`。
- 系统用户接口使用 `SCOPE_admin`，普通用户接口使用 `SCOPE_user`。
- 资源接口只接受 Access Token，Refresh 接口只接受 Refresh Token。

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

省略 `page`、`pageSize` 时使用默认值；显式传入空值、非数字或越界值返回 400，不转换为服务器错误。

```json
{
  "total": 1,
  "page": 1,
  "pageSize": 10,
  "records": []
}
```

## Token 与会话

两类账号共用一套 Token 机制。

| Token | 默认有效期 | 用途 |
|-------|------------|------|
| Access Token | 30 分钟 | 调用业务接口 |
| Refresh Token | 30 天 | 换取新的 Access Token |

公共 claims：

| claim | 值 |
|-------|----|
| `sub` | 账号主键的字符串形式 |
| `accountType` | `SYS_USER` 或 `USER` |
| `sessionId` | 当前登录会话 |
| `scope` | `admin` 或 `user` |
| `tokenType` | `access` 或 `refresh` |

普通用户 Token 额外包含 `passwordVersion`；系统用户不包含该 claim。

- 每次登录或注册生成新的 `sessionId`，会话写入 `auth:session:{accountType}:{sessionId}`，TTL 为 Refresh Token 有效期。
- Refresh Token 在同一个 Session 生命周期内保持不变，不做轮换。
- 退出登录删除当前 Session；退出后 Refresh Token 不能继续续期，已签发的 Access Token 在剩余有效期内仍可使用。
- 修改或重置密码会递增 `password_version`，旧 Refresh Token 因版本不一致无法续期。

## 系统用户

系统用户以数据库表 `sys_user` 为命名基准，后端请求与响应类型统一使用 `SysUser` 前缀，使用用户名和密码登录。

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
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>"
}
```

登录失败返回 `40101`。账号被禁用返回 `40302`。

登录成功后由管理端再调用 `GET /api/sys-user/auth/me` 获取资料。

### 刷新 Access Token

```http
POST /api/sys-user/auth/refresh
Content-Type: application/json
```

请求：

```json
{ "refreshToken": "<refreshToken>" }
```

成功响应 `data`：

```json
{ "accessToken": "<newAccessToken>" }
```

Refresh Token 无效、过期或会话已失效统一返回 `40104`。

### 退出登录

```http
POST /api/sys-user/auth/logout
Authorization: Bearer <accessToken>
```

无请求体。删除当前会话，接口保持幂等。成功响应 `data` 为 `null`。

### 当前系统用户

```http
GET /api/sys-user/auth/me
Authorization: Bearer <accessToken>
```

成功响应 `data`：

```json
{
  "id": 1,
  "username": "admin",
  "nickname": "系统管理员"
}
```

### 系统用户列表

```http
GET /api/sys-user/sys-users?page=1&pageSize=10&keyword=admin&status=1
Authorization: Bearer <accessToken>
```

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | 否 | `1` | 页码，最小值 `1` |
| `pageSize` | 否 | `10` | 每页数量，范围 `1-100` |
| `keyword` | 否 | 空 | 按账号或昵称模糊搜索，最多 30 个字符 |
| `status` | 否 | 空 | `0` 禁用，`1` 正常；不传查询全部 |
| `createdAtOrder` | 否 | 空 | `asc` 创建时间升序，`desc` 降序；不传按 ID 倒序。同一创建时间按 ID 同向排序 |

`records` 对应 `SysUserListVO`，字段为 `id`、`username`、`nickname`、`avatar`、`status`、`createdAt`、`updatedAt`。不会返回密码字段。

### 新增系统用户

```http
POST /api/sys-user/sys-users
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "username": "operator",
  "password": "password123",
  "nickname": "运营人员",
  "status": 1
}
```

密码必须为 8 到 24 个字符；登录账号由数据库唯一约束保证唯一。

### 编辑系统用户

```http
PUT /api/sys-user/sys-users/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求体为 `username`、`nickname`，不修改状态和密码；状态通过启停用接口维护。

### 上传系统用户头像

```http
POST /api/sys-user/sys-users/{id}/avatar
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

表单字段 `file` 接收 JPG、PNG 或 WEBP 文件，最大 2MB。成功响应返回更新后的系统用户数据，其中 `avatar` 为可直接展示的头像地址。

### 重置系统用户密码

```http
PATCH /api/sys-user/sys-users/{id}/password
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{ "password": "new-password123" }
```

### 删除系统用户

```http
DELETE /api/sys-user/sys-users/{id}
Authorization: Bearer <accessToken>
```

删除和更新目标不存在时返回 `40000`。

### 普通用户列表

```http
GET /api/sys-user/users?page=1&pageSize=10&keyword=138&status=1
Authorization: Bearer <accessToken>
```

由系统用户访问，查询参数为 `page`、`pageSize`、`keyword`、`status`、`createdAtOrder`，分页结构与系统用户列表相同；`keyword` 最多 50 个字符，匹配用户号码、手机号、邮箱或昵称；`createdAtOrder` 支持 `asc` 创建时间升序和 `desc` 降序，不传时按创建时间倒序。同一创建时间按 ID 同向排序。`records` 对应 `UserAccountListVO`，字段为 `id`、`userNo`、`phone`、`email`、`nickname`、`status`、`createdAt`、`updatedAt`，不会返回密码和密码版本字段。

## 渠道配置

`sys_config` 保存 4 个布尔配置：

| key | 说明 |
|-----|------|
| `auth.phone.enabled` | 手机号注册、登录和绑定是否可用 |
| `auth.phone.code.enabled` | 手机验证码是否可用 |
| `auth.email.enabled` | 邮箱注册、登录和绑定是否可用 |
| `auth.email.code.enabled` | 邮箱验证码是否可用 |

渠道总开关关闭时，该渠道所有普通用户认证能力不可用；验证码开关关闭时，只禁止验证码能力，密码登录仍可使用。

配置缺失或格式不正确属于服务器故障，返回 500，不按渠道关闭处理。

### 查询渠道配置

```http
GET /api/sys-user/auth-channels
Authorization: Bearer <accessToken>
```

成功响应 `data`：

```json
{
  "phoneEnabled": true,
  "phoneCodeEnabled": true,
  "emailEnabled": true,
  "emailCodeEnabled": true
}
```

### 修改渠道配置

```http
PUT /api/sys-user/auth-channels/{channel}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

`channel` 取 `phone` 或 `email`。

请求：

```json
{
  "enabled": false,
  "codeEnabled": false
}
```

手机和邮箱总开关不能同时关闭，否则返回 `40018`。

## 普通用户

普通用户使用手机号或邮箱注册、登录，不再使用用户名。联系方式在写入和查询前统一规范化：手机号去除首尾空格并校验 11 位中国大陆手机号；邮箱去除首尾空格并转为小写。

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
  "scene": "LOGIN"
}
```

`targetType` 取 `PHONE` 或 `EMAIL`；`scene` 取 `REGISTER`、`LOGIN`、`RESET_PASSWORD`、`REBIND_OLD`、`REBIND_NEW`。

场景资格：

| scene | 资格 |
|-------|------|
| `REGISTER` | 目标尚未注册 |
| `LOGIN`、`RESET_PASSWORD` | 目标已经注册 |
| `REBIND_OLD` | 目标属于当前正常用户，需携带普通用户 Access Token |
| `REBIND_NEW` | 当前用户状态正常且目标尚未绑定，需携带普通用户 Access Token |

规则：

- 同一 `scene + targetType + target` 60 秒内只能发送一次，否则返回 `40012`；
- 每个 `targetType + target` 每日最多发送 10 次，超过返回 `40013`；
- 验证码 5 分钟有效；校验成功即消费，连续错误 5 次后失效，新发送的验证码重新计算失败次数。

成功响应 `data` 为 `null`。

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
  "password": "optionalPassword"
}
```

`password` 可选；有值时长度为 8～72 个字符且 UTF-8 编码不超过 72 字节。成功响应 `data` 为双 Token：

```json
{
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>"
}
```

目标已注册返回 `40015`。

### 登录

```http
POST /api/user/auth/login
Content-Type: application/json
```

密码登录：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "password": "password"
}
```

验证码登录：

```json
{
  "target": "user@example.com",
  "targetType": "EMAIL",
  "code": "123456"
}
```

`password` 与 `code` 必须且只能提供一个，否则返回 `40000`。成功响应 `data` 为双 Token。

登录失败返回 `40101`；账号被禁用返回 `40303`；没有密码的账号只能使用验证码登录。

密码登录输入最多 72 个字符且 UTF-8 编码不超过 72 字节，超限返回 `40000`；不套用创建密码时的最小长度要求。

### 刷新 Access Token

```http
POST /api/user/auth/refresh
Content-Type: application/json
```

请求与响应结构同系统用户 Refresh；失败返回 `40104`。

### 重置密码（忘记密码）

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
  "newPassword": "newPassword"
}
```

成功响应 `data` 为 `null`。目标未注册返回 `40016`。

### 退出登录

```http
POST /api/user/auth/logout
Authorization: Bearer <accessToken>
```

无请求体，删除当前会话，接口保持幂等。

### 个人资料

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

不返回密码和密码版本。

### 修改个人资料

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

只修改 `nickname` 和 `avatar`；手机号和邮箱必须走换绑接口。`nickname` 必填，最多 50 个字符；`avatar` 最多 255 个字符，可为空。

### 修改密码

```http
PUT /api/user/me/password
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求字段与忘记密码相同（`target`、`targetType`、`code`、`newPassword`），但 `target` 必须属于当前用户。修改成功后旧 Refresh Token 失效。

### 换绑手机号

```http
PUT /api/user/me/phone
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求：

```json
{
  "oldTarget": "old@example.com",
  "oldTargetType": "EMAIL",
  "oldCode": "123456",
  "newPhone": "13900139000",
  "newCode": "654321"
}
```

服务端确认旧联系方式属于当前用户，依次校验并消费两个验证码，然后在数据库事务中更新手机号。新手机号已被绑定返回 `40017`。

### 换绑邮箱

```http
PUT /api/user/me/email
Authorization: Bearer <accessToken>
Content-Type: application/json
```

结构同换绑手机号，将 `newPhone` 改为 `newEmail`。

## 输入约束

| 字段 | 约束 |
|------|------|
| `targetType`、`oldTargetType` | `PHONE` 或 `EMAIL` |
| `code`、`oldCode`、`newCode` | 6 位数字 |
| 注册 `password`、`newPassword` | 8～72 个字符，且 UTF-8 编码不超过 72 字节 |
| 普通用户登录 `password` | 非空且不超过 72 个字符 / UTF-8 字节；短密码参与验证，错误返回 `40101` |
| 系统用户登录 `password` | 非空且不超过 24 个字符 |
| `nickname` | 最多 50 个字符 |
| `avatar` | 最多 255 个字符，可为空 |

## 错误码

| HTTP | 业务码 | 名称 | 说明 |
|------|--------|------|------|
| 400 | `40000` | `BAD_REQUEST` | 请求参数不正确 |
| 400 | `40010` | `AUTH_CHANNEL_DISABLED` | 该登录方式暂不可用 |
| 400 | `40011` | `CODE_FUNCTION_DISABLED` | 验证码功能暂不可用 |
| 400 | `40012` | `CODE_SEND_TOO_FREQUENT` | 验证码发送过于频繁 |
| 400 | `40013` | `CODE_SEND_LIMIT_EXCEEDED` | 今日验证码发送次数已达上限 |
| 400 | `40014` | `VERIFICATION_CODE_INVALID` | 验证码无效或已过期 |
| 400 | `40015` | `TARGET_ALREADY_REGISTERED` | 该手机号或邮箱已注册 |
| 400 | `40016` | `TARGET_NOT_REGISTERED` | 该手机号或邮箱尚未注册 |
| 400 | `40017` | `TARGET_ALREADY_BOUND` | 该手机号或邮箱已被绑定 |
| 400 | `40018` | `CANNOT_DISABLE_ALL_CHANNELS` | 手机号和邮箱不能同时关闭 |
| 401 | `40101` | `INVALID_CREDENTIALS` | 账号或密码错误 |
| 401 | `40102` | `LOGIN_STATUS_INVALID` | 登录状态无效或已过期 |
| 401 | `40104` | `REFRESH_TOKEN_INVALID` | 刷新令牌无效或已过期 |
| 403 | `40301` | `ACCESS_DENIED` | 没有权限访问该接口 |
| 403 | `40302` | `SYS_USER_DISABLED` | 系统用户已禁用 |
| 403 | `40303` | `USER_DISABLED` | 普通用户已禁用 |
| 500 | `50000` | `INTERNAL_SERVER_ERROR` | 服务器内部异常 |
