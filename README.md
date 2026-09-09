# 账号后端

基于 Spring Boot、Spring Security、MyBatis 和 MySQL，仅提供系统用户与普通用户的现有账号功能。

## 当前功能

- 两类账号分别登录、查询当前登录信息。
- 系统用户分页查询系统用户和普通用户列表，可按关键词、状态筛选。
- BCrypt 密码验证、JWT 认证及 `SCOPE_admin` / `SCOPE_user` 权限隔离。

当前没有注册、账号创建、编辑或启停用接口。登录和当前账号查询会检查已有账号状态。前端退出登录时清除 Token，后端没有退出登录接口。

## 接口

完整请求、响应、参数和错误码说明见[接口文档](docs/api.md)。

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| POST | `/api/sys-user/auth/login` | 公开 | 系统用户登录 |
| GET | `/api/sys-user/auth/me` | `SCOPE_admin` | 当前系统用户 |
| GET | `/api/sys-user/sys-users` | `SCOPE_admin` | 系统用户列表 |
| GET | `/api/sys-user/users` | `SCOPE_admin` | 普通用户列表 |
| POST | `/api/user/auth/login` | 公开 | 普通用户登录 |
| GET | `/api/user/auth/me` | `SCOPE_user` | 当前普通用户 |

登录请求为 `{"username":"账号","password":"密码"}`；列表查询参数为 `page`、`pageSize`、`keyword`、`status`。响应统一为 `Result<T>`，分页数据为 `PageResult<T>`。

## 目录

```text
src/main/java/com/web/project/
├── admin/       系统用户列表、实体与 Mapper
├── user/        普通用户列表、实体与 Mapper
├── auth/        两类账号的登录、当前身份与 JWT 签发
├── common/      错误与响应结构
├── config/      认证、安全与配置
└── WebProjectApplication.java
src/main/resources/mapper/
├── admin/
└── user/
```

## 启动

1. 使用 IntelliJ IDEA 为项目配置的 JDK，具体步骤见[后端工程规范](docs/engineering.md)。
2. 新环境执行 `web_project.sql`，创建 `sys_user` 和 `user_account` 两张账号表，并写入本地开发账号 `admin`，密码为 `123456`。已有数据库可执行 `database/migrations/20260910_seed_default_sys_user.sql` 重置该账号；密码列始终存储 BCrypt 哈希。
3. 本地开发可以直接启动，`src/main/resources/application.yml` 已提供本地 JWT 默认密钥；数据库连接可通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 指定。需要替换密钥时，在 IDEA 启动配置或 Maven Runner 中设置 `JWT_SECRET`。
4. 运行 `WebProjectApplication`，或在 Maven 面板执行 `spring-boot:run`。默认地址为 `http://localhost:8080`。

密钥生成方法及构建验证命令见[后端工程规范](docs/engineering.md)。

## 已有数据库

- 使用旧 `admin_user` 表的环境，先执行 `database/migrations/20260824_rename_admin_user_to_sys_user.sql`。
- 删除旧业务表使用 `database/migrations/20260909_remove_non_account_tables.sql`。该脚本会永久删除六张旧业务表及数据，执行前备份并确认目标数据库；脚本保留两类账号表。

迁移脚本由维护者手动执行，应用启动不会自动执行。
