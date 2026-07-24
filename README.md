# Web Project Backend

基于 Spring Boot 的后端服务，提供完整的用户认证、商品管理、兑换码体系等功能。

## 技术栈

- **框架**：Spring Boot 4.0.7
- **安全**：Spring Security + Spring OAuth2 Resource Server + JWT Token 认证
- **ORM**：MyBatis 4.0.1
- **数据库**：MySQL
- **工具**：Lombok、Spring Validation、JUnit Jupiter

## 功能特性

### 认证授权
- 管理员认证（登录、获取当前管理员信息）
- 普通用户认证（登录、获取当前用户信息）
- 基于 JWT Bearer Token 的无状态认证机制
- 双角色权限隔离：`SCOPE_admin`（管理端）与 `SCOPE_user`（用户端）
- 统一的 401/403 异常处理

### 管理员管理
- 管理员账号的 CRUD 及状态管理（启用/禁用）

### 普通用户管理
- 用户账号管理（昵称、状态等）

### 商品与套餐
- 商品管理（商品编码、名称、描述、状态）
- 套餐管理（套餐编码、名称、有效天数、价格、排序）
- 套餐支持兑换码和在线支付开关控制

### 兑换码体系
- 兑换码批次生成（批次编号、数量、销售渠道、过期时间）
- 兑换码安全管理（SHA-256 哈希存储，后台展示脱敏处理）
- 兑换码状态流转：未兑换 → 已兑换 / 已停用
- 兑换记录追踪（用户、IP、时间、套餐快照）

### 用户订阅
- 用户订阅记录管理（订阅有效期、状态）

## 项目结构

```
src/main/java/com/web/project/
├── admin/                    # 管理员模块
│   ├── controller/           # AdminUserController
│   ├── dto/                  # AdminUserQueryDTO
│   ├── entity/               # AdminUser
│   ├── mapper/               # AdminUserMapper
│   ├── service/              # AdminUserService / impl
│   └── vo/                   # AdminUserListVO
│
├── auth/                     # 认证授权模块
│   ├── controller/           # AdminAuthController, UserAuthController
│   ├── dto/                  # LoginDTO, UserLoginDTO
│   ├── service/              # AdminAuthService, UserAuthService, JwtTokenService
│   └── vo/                   # AdminInfoVO, AdminLoginVO, UserLoginVO
│
├── common/                   # 公共组件
│   ├── enums/                # 枚举类
│   ├── error/                # ErrorCode（错误码定义）
│   ├── exception/            # BusinessException, GlobalExceptionHandler
│   ├── result/               # Result（统一响应）, PageResult（分页响应）
│   └── utils/                # 工具类
│
├── config/                   # 配置类
│   ├── properties/           # JwtProperties
│   ├── security/             # RestAccessDeniedHandler, RestAuthenticationEntryPoint
│   ├── JwtConfig.java
│   ├── PasswordEncoderConfig.java
│   └── SecurityConfig.java
│
├── product/                  # 商品与套餐模块
│   ├── controller/           # ProductController
│   ├── dto/                  # CreateProductDTO, CreateProductPlanDTO, ProductQueryDTO
│   ├── entity/               # Product, ProductPlan
│   ├── enums/                # ProductStatus
│   ├── mapper/               # ProductMapper, ProductPlanMapper
│   ├── service/              # ProductService / impl
│   └── vo/                   # ProductDetailVO, ProductListVO, ProductPlanVO
│
├── redeem/                   # 兑换码模块
│   ├── controller/           # RedeemCodeBatchController
│   ├── dto/                  # CreateRedeemCodeBatchDTO
│   ├── entity/               # RedeemCode, RedeemCodeBatch
│   ├── enums/                # RedeemBatchStatus, RedeemCodeStatus
│   ├── mapper/               # RedeemCodeMapper, RedeemCodeBatchMapper
│   ├── service/              # RedeemCodeBatchService / impl
│   ├── support/              # 兑换码生成辅助
│   └── vo/                   # 视图对象
│
├── user/                     # 普通用户模块
│   ├── controller/           # UserAccountController
│   ├── dto/                  # DTO 对象
│   ├── entity/               # UserAccount
│   ├── mapper/               # UserAccountMapper
│   ├── service/              # 服务层 / impl
│   └── vo/                   # UserInfoVO 等
│
├── test/                     # 测试控制器（调试用）
└── WebProjectApplication.java

src/main/resources/
├── mapper/                   # MyBatis XML 映射文件
│   ├── admin/
│   ├── product/
│   ├── redeem/
│   └── user/
└── application.yml           # 应用配置
```

## 数据库表

| 表名 | 说明 |
|------|------|
| `admin_user` | 后台管理员表 |
| `user_account` | 普通用户账号表 |
| `product` | 商品表 |
| `product_plan` | 商品套餐表 |
| `user_subscription` | 用户订阅表 |
| `redeem_code_batch` | 兑换码批次表 |
| `redeem_code` | 兑换码表（存储 SHA-256 哈希） |
| `redeem_record` | 兑换记录表 |

完整的建表 SQL 见项目根目录 `web_project.sql`。

## API 端点概览

### 管理端认证（公开）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/auth/login` | 管理员登录 |

### 管理端接口（需 SCOPE_admin）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/auth/me` | 获取当前管理员信息 |
| GET | `/api/admin/users` | 管理员列表（分页） |
| POST | `/api/admin/users` | 创建管理员 |
| PUT | `/api/admin/users/{id}` | 更新管理员 |
| — | `/api/admin/products/**` | 商品与套餐管理 |
| — | `/api/admin/redeem/**` | 兑换码批次管理 |

### 用户端认证（公开）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user/auth/login` | 用户登录 |

### 用户端接口（需 SCOPE_user）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user/auth/me` | 获取当前用户信息 |
| — | `/api/user/**` | 用户相关操作 |

## 环境要求

- **Java** 25+
- **Maven** 3.6+
- **MySQL** 8.0+

## 快速开始

### 1. 创建数据库

执行项目根目录下的 SQL 脚本：

```bash
mysql -u root -p < web_project.sql
```

### 2. 配置环境变量（可选）

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_URL` | 数据库连接地址 | `jdbc:mysql://localhost:3306/web_project?...` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `123456` |
| `JWT_SECRET` | JWT 签名密钥（Base64） | 内置默认值 |

### 3. 启动应用

```bash
mvnw clean spring-boot:run
```

应用启动后访问 `http://localhost:8080`。

> **注意**：首次启动前需确保数据库中已有管理员账号（密码通过 BCrypt 加密），否则无法登录管理端。

## 安全说明

- 前后端分离架构，关闭了 CSRF、Session、Form Login
- JWT 无状态认证，Access Token 有效期 2 小时
- 兑换码明文不落库，仅存储 SHA-256 哈希值
- 管理员密码使用 BCrypt 加密存储
- 接口按 `SCOPE_admin` / `SCOPE_user` 进行角色隔离
