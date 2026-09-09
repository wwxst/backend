# 后端工程规范

## 当前业务范围

仅保留系统用户、普通用户、两类账号登录及当前身份查询、账号列表管理。当前实现不包含注册、账号创建、编辑或启停用接口。后续开发以该范围为准，新增业务需有明确需求。

系统用户接口使用 `/api/sys-user/**`，普通用户接口使用 `/api/user/**`。接口路径、请求参数和响应结构以[接口文档](api.md)为准，README 只保留接口速览。

数据库表 `sys_user` 对应的 Java 类型统一使用 `SysUser` 前缀，包括 Controller、DTO、Service、Mapper 和 VO；接口路径统一使用 `/api/sys-user/**`。`admin` 只作为 JWT scope 权限值保留，不作为系统用户领域类型名。

## 最小实现原则

后端以满足当前业务需求的最小正确实现为准，禁止过度防御编程。此原则同样适用于生产代码、测试、脚本和文档。

- 每个分支、抽象和依赖都必须有当前调用方或可达的失败场景；不为假想需求预留框架、兼容层或扩展点。
- 输入格式在 HTTP、配置等外部边界校验一次。内部代码遵循已建立的契约，不逐层重复判空、校验或补默认值。
- 唯一性等规则交给数据库约束执行，在业务边界转换必要的错误；不以重复查询、应用锁或重试叠加同一保证。
- 只捕获需要转换或确实能恢复的异常；不吞异常，不将失败替换为成功、空列表或默认对象。
- 鉴权、业务状态和事务约束按实际行为保留。删除逻辑前检查调用方及 MyBatis 映射，不以行数更少代替正确性。
- 测试覆盖关键行为与真实失败路径，不为已移除的分支、实现细节或覆盖率数字增加测试，也不为测试新增生产抽象。

## Java 运行环境

本项目统一使用 **JDK 25**。Java 版本以 `pom.xml` 中的 `java.version` 为准，当前值为 `25`。

开发、运行、调试和测试统一使用 IntelliJ IDEA 为项目配置的 JDK 25；命令行和 Agent 也复用这套 JDK：

1. 在 `File > Project Structure > Project` 中将 `Project SDK` 设置为 JDK 25。
2. 在 `Settings > Build, Execution, Deployment > Build Tools > Maven > Runner` 中将 `JRE` 设置为同一个 JDK 25。
3. Maven 导入、IDEA 的测试运行器和 Spring Boot 启动配置都必须使用该 JDK 25。

命令行 `mvnw.cmd` 只在 `JAVA_HOME` 指向同一个 JDK 25 时使用。不得用其他 JDK 版本运行本项目的构建或测试，以免 IDEA、Maven 和编译产物使用不同的 Java 版本。

### 命令行与 Agent 执行规则

- `java` 不在 `PATH` 或 `JAVA_HOME` 未配置，不代表机器未安装 JDK。先查看 `.idea/misc.xml` 中的 `project-jdk-name`，再从 IDEA 的 SDK 配置定位对应安装目录。IDEA 下载的 JDK 通常位于 `%USERPROFILE%\.jdks`。
- 使用该目录下的 `bin\java.exe -version` 确认主版本为 25，再为当前命令进程设置 `JAVA_HOME` 并调用项目的 Maven Wrapper。
- 不因终端环境变量缺失而另装 JDK、降级项目 Java 版本或修改系统级环境变量。
- JDK 安装路径属于本机配置，不写入 `pom.xml`。本机已核实 IDEA 的 `temurin-25` 对应 `C:\Users\ww\.jdks\temurin-25.0.4.1`；升级或换机后重新查找。

PowerShell 示例（路径须与本机 IDEA 配置一致）：

```powershell
$env:JAVA_HOME = 'C:\Users\ww\.jdks\temurin-25.0.4.1'
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd test
```

## 构建与验证

在 IntelliJ IDEA 的 Maven 面板中执行：

- `clean`：清理旧的编译产物；
- `test`：运行项目测试；
- `package`：验证可交付的 Spring Boot 构建产物。

涉及业务逻辑、认证授权、数据库映射或配置变更时，至少运行 `test`；涉及依赖、编译配置或发布产物时，再运行 `package`。

命令行统一使用项目 Maven Wrapper，完整验收可执行 `.\mvnw.cmd verify`（包括测试与打包）。测试中的专用密钥通过测试注解注入，不作为应用启动默认值。

现有自动验证的边界：

- `GlobalExceptionHandlerTest`：真实 MVC 参数绑定、400/405/415、业务错误及不泄露内部信息的 500 响应。
- `JwtSecurityTest`：真实 JWT 签名、解码与 Spring Security 过滤链；验证角色隔离、过期、签发者及错误签名。业务 Service 使用 mock，不访问数据库。
- `JwtConfigTest` 和 `PasswordEncoderTest`：无效签名密钥拒绝、密码验证成功与失败。
- `WebProjectApplicationTests`：Spring 上下文装配；不等同于数据库连通性或业务接口验收。

涉及数据库查询或迁移的修改，需要单独在可丢弃的 MySQL 测试库验证；不得将 mock 测试通过当作真实数据库验证。

## 运行配置

本地开发允许使用 `src/main/resources/application.yml` 中的默认 JWT 密钥，IDEA 可直接启动。部署或共享环境应通过 `JWT_SECRET` 覆盖该值。需要生成新密钥时，使用至少 32 个安全随机字节并编码为 Base64，例如在 PowerShell 中生成：

```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

在 IDEA 的 `Run > Edit Configurations > Environment variables` 中配置 `JWT_SECRET` 即可覆盖本地默认值。使用 Maven 面板启动时，将变量配置到 Maven Runner 的环境变量中。更换签名密钥会使旧 Token 失效；已使用旧默认密钥的环境应重新登录。

数据库配置和 Token 有效期以 `src/main/resources/application.yml` 为准。测试不通过打印密码哈希或密钥充当验证；需要实际的成功与失败断言。

## 错误处理约定

- MVC 参数错误返回 400；错误方法、内容类型等保留 Spring 对应的 HTTP 状态和响应头，响应体使用现有 `Result` 格式及 `BAD_REQUEST` 错误码。
- 未预期的系统异常记录服务端日志，响应不包含异常堆栈或数据库细节。

## 代码边界

- Controller 只负责 HTTP 参数、认证上下文和响应转换。
- Service 负责业务规则和事务边界。
- Mapper 负责 MyBatis 数据访问，不在 Controller 中直接访问数据库。
- `web_project.sql` 是新环境数据库结构的事实来源，已有数据库变更通过 `database/migrations/` 中的迁移脚本完成。
