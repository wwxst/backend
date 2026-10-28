# Atlas 头像 OSS Bucket 安全基线与存量审计

## 强制安全基线

当前头像上传通过**服务端 AccessKey** 调用 OSS 写入，公开读取通过 `/files/{id}` 重定向到 OSS 对象 URL。因此：

- Bucket ACL **仅允许 `public-read`**；`public-read-write` 会被后端拒绝，`private` 当前也不兼容公开重定向读取。
- 这条代码检查**只验证 Bucket ACL**。Bucket Policy、RAM 策略和对象级 ACL 可能改变实际匿名权限；**Bucket ACL 为 `public-read` 不等于确认匿名不能写入**。
- RAM/AccessKey 权限按最小权限授予对头像对象前缀（`avatars/`）必要的 `PutObject`、`DeleteObject` 等操作；非必要不授予修改 Bucket ACL/Policy 权限。避免使用主账号 AccessKey。
- 生产环境上传、设为默认空间、切换阿里云存储或更换 AccessKey 前，后端会重新验证实际 Bucket ACL。若后来被改为公共读写，上述操作会拒绝。

## 生产 Bucket ACL / Policy 审计（必须在上线前人工完成）

当前 Atlas **尚无已配置的生产 OSS 凭据或 Bucket 可供应用自动审计**。上线负责人需使用有审计权限的阿里云身份登录 OSS 控制台，逐个检查已经绑定和计划绑定的 Bucket：

1. 检查 Bucket 读写权限（ACL）为 **公共读**，绝不能是公共读写；检查防止公共访问的相关安全设置及 OSS 安全提示。
2. 查看 Bucket Policy 所有 Allow 规则：不能向匿名主体（例如 `Principal: *`）授予 `oss:PutObject`、`oss:DeleteObject`、`oss:*`、`*` 等写入/修改/删除权限；同时审查通过策略继承、通配符资源匹配或条件规则产生的权限。必要时用策略模拟器或受控匿名请求验证无法写入。
3. 检查 Object ACL 是否曾将头像对象单独设置为公开写入，以及 RAM 权限中是否存在权限过大的服务账号、泄露的 AccessKey。
4. 对发现的公共写入权限：**先在 OSS 控制台撤销匿名写授权和危险 Policy，轮换可能泄露的密钥，排查异常对象及访问日志，再在 Atlas 中重新启用该 Bucket。**
5. 定期审核 Bucket ACL、Policy、RAM、对象 ACL 与云审计日志；记录 Bucket 名称、审计日期、负责人和整改结果，发现授权漂移要主动告警。

请勿仅凭页面显示“已绑定”或同步列表判断安全：同步操作只发现 Bucket，**不代表它通过 ACL/Policy 审核**。

## 私有 Bucket 后续方案

如果需要私有 Bucket，应另行实现短时签名 URL 或由后端鉴权代理读取头像，并明确 CDN 缓存、过期和权限要求。在该读取方案完成前，Atlas 故意拒绝 `private`，避免头像 URL 不可用。

**注意：**本仓库不会自动修改云端 Bucket ACL 或 Bucket Policy；把 `public-read-write` 从代码和前端移除也不会自动收紧已经存在的云端权限。
