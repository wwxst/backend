# Web Project Backend

这是一个基于Spring Boot的后端项目，提供了完整的用户认证和管理功能。

## 技术栈

- Spring Boot 4.0.7
- Spring Security
- Spring OAuth2 Resource Server
- MyBatis
- MySQL
- JWT Token 认证

## 功能特性

- 管理员身份认证
- JWT Token 认证机制
- 安全配置
- 数据库访问层

## 项目结构

```
src/
├── main/
│   ├── java/com/web/project/
│   │   ├── admin/          # 管理员相关模块
│   │   ├── auth/           # 认证授权模块
│   │   ├── common/         # 公共组件
│   │   ├── config/         # 配置类
│   │   └── WebProjectApplication.java
│   └── resources/
└── test/
```

## 环境要求

- Java 25+
- Maven
- MySQL

## 快速开始

1. 克隆项目
2. 配置数据库连接信息
3. 运行 `mvn clean install`
4. 启动应用

## 配置说明

请确保在 `application.yml` 中正确配置数据库连接、JWT密钥等参数。