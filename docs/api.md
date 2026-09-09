# 账号接口文档

## 基础约定

- 基础地址：`http://localhost:8080`
- 请求和响应编码：UTF-8
- 登录接口公开访问；其他接口需要 `Authorization: Bearer <token>`。
- 系统用户接口使用 `SCOPE_admin`，普通用户接口使用 `SCOPE_user`。
- 当前只提供登录、当前身份查询和账号列表查询；没有注册、创建、编辑、启停用和退出登录接口。

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
  "nickname": "系统管理员"
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

## 普通用户

### 登录

```http
POST /api/user/auth/login
Content-Type: application/json
```

请求：

```json
{
  "username": "user",
  "password": "password"
}
```

成功响应 `data`：

```json
{
  "token": "<jwt>"
}
```

登录失败返回 `40101`。账号被禁用返回 `40303`。

### 当前普通用户

```http
GET /api/user/auth/me
Authorization: Bearer <jwt>
```

成功响应 `data`：

```json
{
  "id": 1,
  "username": "user",
  "nickname": "普通用户",
  "status": 1,
  "createdAt": "2026-09-10T10:00:00"
}
```

### 普通用户列表

```http
GET /api/sys-user/users?page=1&pageSize=10&keyword=user&status=1
Authorization: Bearer <sys-user-jwt>
```

该接口由系统用户访问，查询参数和分页结构与系统用户列表相同；`keyword` 最多 50 个字符。`records` 字段为 `id`、`username`、`nickname`、`status`、`createdAt`、`updatedAt`，不会返回密码字段。

## 通用错误码

| HTTP | 业务码 | 说明 |
|------|--------|------|
| 400 | `40000` | 请求参数不正确 |
| 401 | `40101` | 账号或密码错误 |
| 401 | `40102` | 登录状态无效或已过期 |
| 403 | `40301` | 没有权限访问该接口 |
| 403 | `40302` | 系统用户已禁用 |
| 403 | `40303` | 普通用户已禁用 |
| 500 | `50000` | 服务器内部异常 |
