# Web Project 双前端架构设计

## 目标与边界

本项目建设两个相互独立的 React 19 前端，分别服务管理后台和普通用户端。两个应用独立开发、构建、部署和管理登录状态，不在一个前端工程内做双入口。

第一阶段优先接通现有业务，不实现完整 RBAC、动态菜单、部门和数据权限。后端系统用户表由 `admin_user` 改名为 `sys_user`，普通用户表继续使用 `user_account`。

```text
应用          使用者          后端接口前缀       账号表
admin-web     系统用户        /api/admin/**      sys_user
user-web      普通用户        /api/user/**       user_account
```

## 技术选型

两个应用采用相同的工程基础，降低构建、测试和接口接入成本；组件库根据使用场景分别选择。

```text
基础技术      React 19 + TypeScript + Vite
路由          React Router
服务端状态    TanStack Query
HTTP          Axios
表单校验      React Hook Form + Zod
单元测试      Vitest + Testing Library
端到端测试    Playwright

admin-web     TDesign React
user-web      Arco Design React
```

`admin-web` 以高频管理操作为中心，重点使用数据表格、搜索表单、分页、弹窗和状态标签。`user-web` 以登录、兑换和订阅查看为中心，界面保持轻量，不照搬管理后台布局。

## 工程边界

两个前端分别建立独立 Git 仓库，不直接引用彼此源码，也不让同一个应用同时依赖 TDesign 和 Arco Design。

```text
E:/JavaProjects/web-project/
  backend/       Spring Boot 后端
  admin-web/     React 19 + TDesign 管理后台
  user-web/      React 19 + Arco Design 用户端
```

第一阶段不发布公共 npm UI 包。每个应用在自己的 `src/ui` 下封装带有业务语义的组件，并使用一致的设计变量命名。待两个项目出现稳定、真实的重复后，再提取不依赖具体组件库的 `ui-core`。

```text
admin-web/src/ui
  PermissionButton
  SearchForm
  DataTable
  StatusTag
  ConfirmDialog

user-web/src/ui
  LoginForm
  RedeemCodeInput
  SubscriptionCard
  ResultState
```

不机械封装第三方的基础 `Button` 和 `Input`。只有组件包含权限、分页、状态转换、统一错误展示等项目规则时，才进入本地 UI 层。

## 页面范围

管理后台第一阶段：

```text
登录
仪表盘
普通用户管理
商品与套餐管理
兑换码批次管理
兑换记录查询
用户订阅管理
```

用户端第一阶段：

```text
登录
兑换码兑换
当前订阅信息
```

完整 RBAC 在核心业务闭环稳定后单独设计和实施。第一阶段保留现有 `SCOPE_admin` 与 `SCOPE_user` 隔离，系统用户拥有相同的后台权限。

## 数据流与认证

每个应用维护独立 Axios 实例和 TanStack Query 缓存。登录成功后保存对应作用域的 JWT，请求拦截器统一附加 `Authorization: Bearer <token>`。

```text
页面操作
  -> 业务 API 函数
  -> Axios 实例
  -> Spring Boot /api/admin/** 或 /api/user/**
  -> Result<T> / PageResult<T>
  -> TanStack Query 缓存
  -> 页面状态
```

收到 `401` 时清除当前应用的登录状态并返回登录页；收到 `403` 时保留登录状态并展示无权限结果。业务错误优先展示后端返回的错误信息，未知错误使用统一兜底文案。

## 测试与验收

```text
类型检查      TypeScript 无错误
单元测试      登录状态、请求错误处理、业务 UI 组件
组件测试      搜索、分页、表单校验、兑换结果
端到端测试    登录和核心业务闭环
构建验证      两个应用分别完成 production build
浏览器验收    桌面和移动视口无重叠、溢出和不可操作控件
```

管理后台首个验收闭环为“登录 -> 查询商品/套餐 -> 创建兑换码批次 -> 查询兑换记录”。用户端首个验收闭环为“登录 -> 输入兑换码 -> 兑换成功 -> 查看订阅”。
