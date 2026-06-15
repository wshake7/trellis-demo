# Knife4j 注解规范

> java-admin 后端 API 文档(Knife4j 4.5 + OpenAPI 3)的注解约定。本文件定义:
> 哪些类必须加注解、各注解用法、依赖管理、与 Result 的配合。

---

## 1. 选型

- **Knife4j 版本**:`4.5.0`(父 pom `knife4j.version`)
- **Starter**:`com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter`(走 OpenAPI 3 + Jakarta 命名空间)
- **注解包**:**统一用 `io.swagger.v3.oas.annotations.*`**(OpenAPI v3);**禁止**用 `io.swagger.annotations.*`(Swagger v2,Knife4j 4.x 不识别)
- **鉴权**:`bearerAuth` securityScheme(类型 HTTP Bearer / header `satoken` / bearerFormat JWT),与 Sa-Token 默认对齐
- **语言**:`knife4j.setting.language=zh_cn`(dev yml 已配;prod 默认关)

### 1.1 依赖分层

| 模块               | 是否依赖 swagger 注解 | 说明                                                                                              |
| ------------------ | --------------------- | ------------------------------------------------------------------------------------------------- |
| `java-admin-common` | ✅ `swagger-annotations-jakarta` | Result / ObjectResult / ListResult 字段需 `@Schema`;纯注解 JAR(~30KB),无业务 starter,**符合** §3.5 "common 不引 spring 业务 starter" |
| `java-admin-infra`  | ✅ 由 knife4j starter 传递 | `@RestControllerAdvice` 上加 `@Tag` / `@Operation` / `@ApiResponse`                                |
| `java-admin-api`    | ✅ 由 knife4j starter 传递 | Controller / DTO / VO 加全套注解;**新增** `OpenApiConfig` 注册 `OpenAPI` bean(title/version/contact/bearerAuth) |
| `java-admin-service` | ❌ 不引               | Service / Repository / Entity 不写文档注解(数据模型已在 DTO/VO 暴露)                                 |

**父 pom `dependencyManagement` 必须包含** `io.swagger.core.v3:swagger-annotations-jakarta`(目前锁 `2.2.19`,与 knife4j 4.5 内置 swagger-core-jakarta 一致)。子模块只引 groupId + artifactId,版本由父 pom 管。

### 1.2 关键决策:Result 字段加注解但**类不加**

```java
public class Result<T> {
    @Schema(description = "业务码;0=成功,非 0 见 ResultCode 枚举", example = "0")
    private int code;
    // ...
}
```

**禁止**在 `Result` / `ObjectResult` / `ListResult` **类级别**加 `@Schema(name=...)`。原因:

- 类级 `@Schema` 会让 SpringDoc 把 `Result` 注册成独立 schema,泛型 `T` 无法正确解析
- 只注解字段,SpringDoc 通过方法返回类型 `Result<LoginResponse>` 自动展开 T,文档里 `data` 字段正确显示为 `LoginResponse` 类型
- `ObjectResult<T>` / `ListResult<T>` 继承 `Result` 的字段注解,无需重复加

---

## 2. 必须加注解的位置

| 类型                                | 必须的注解                                                                                  |
| ----------------------------------- | ------------------------------------------------------------------------------------------- |
| `@RestController` 类                | `@Tag(name = "中文短名", description = "...")`                                              |
| `@RestControllerAdvice` 类          | `@Tag(name = "全局异常", description = "...")`                                              |
| Controller 公共方法                 | `@Operation(summary = "一句话动词短语", description = "...")`                              |
| Controller 鉴权端点方法             | `@SecurityRequirement(name = "bearerAuth")`(`/login` 等不需要鉴权的端点不加)               |
| Controller 入参(`@RequestBody` 等) | `@Parameter(description = "...", required = true)`                                          |
| Controller 方法                     | `@ApiResponses` 含 200/4xx/5xx,错误响应 `content = @Content(schema = @Schema(implementation = Result.class))` |
| `@ExceptionHandler` 方法            | `@Operation` + `@ApiResponse`(否则 SpringDoc 不收录)                                       |
| DTO / VO 类                         | `@Schema(description = "...")`                                                              |
| DTO / VO 字段                       | `@Schema(description = "...", example = "...", requiredMode = REQUIRED)`(能给的都给 example) |

---

## 3. 鉴权(@SecurityRequirement 配对)

`OpenApiConfig` 注册 security scheme:

```java
.addSecuritySchemes("bearerAuth", new SecurityScheme()
    .type(SecurityScheme.Type.HTTP)
    .scheme("bearer")
    .bearerFormat("JWT")
    .in(SecurityScheme.In.HEADER)
    .name("satoken"))
```

- `name = "satoken"`:与 Sa-Token 默认读 header 名一致 → Knife4j 调试页面 token 输入框自动落到 `satoken` header
- 端点上 `@SecurityRequirement(name = "bearerAuth")` 与上面 `addSecuritySchemes("bearerAuth", ...)` 名称必须**完全一致**

**哪些端点要 / 不要加** `@SecurityRequirement`:

| 端点                              | 加? | 理由                                  |
| --------------------------------- | --- | ------------------------------------- |
| `POST /api/v1/auth/login`         | ❌  | 未登录前                              |
| `POST /api/v1/auth/logout`        | ✅  | 需已登录                              |
| `GET  /api/v1/auth/info`          | ✅  | 需已登录                              |
| 其他需登录的端点                  | ✅  | 默认规则                              |

---

## 4. 错误响应(@ApiResponse 复用 Result schema)

错误响应统一引用 `Result` schema:

```java
@ApiResponse(
    responseCode = "400",
    description = "参数错误(code=1001)",
    content = @Content(schema = @Schema(implementation = Result.class)))
```

- `Result.class` 引用会让 SpringDoc 用 `Result` schema(泛型 T 未解析,data 为 null),**符合** error 场景
- 不要在 description 里写完整 JSON example(避免 code / msg 分支太多维护成本)
- 业务码在 description 里标注:`"凭证错误(code=2002)"` → 与 `ResultCode` 枚举同步

---

## 5. OpenApiConfig 的位置与内容

**位置**:`backend/java-admin/java-admin-api/src/main/java/com/wshake/api/config/OpenApiConfig.java`

- 包路径 `com.wshake.api.config`(虽然 directory-structure.md 当前没列 api/config 子目录,但 OpenApiConfig 是 API 层文档配置,放 api 比 infra 更合适)
- 唯一 Bean:`OpenAPI customOpenAPI()` 注入 title / version / contact + securitySchemes
- **不要**在 yml 写 `knife4j.openapi` 字段(避免 knife4j 4.x yml vs bean 谁覆盖谁歧义)

yml 责任:

- `knife4j.enable: true`(dev) / `false`(prod,默认 ${KNIFE4J_ENABLE:false})
- `knife4j.setting.language: zh_cn`(仅 dev)

---

## 6. 禁止做的事

- ❌ 不给 Service / Repository / Entity 加 Swagger 注解(数据模型由 DTO/VO 暴露)
- ❌ 不在 common 之外的模块**直接**引 `swagger-annotations-jakarta`(knife4j starter 已在 api/infra 传递;common 自己加)
- ❌ 不给 `Result` / `ObjectResult` / `ListResult` 类加 `@Schema`(类级注解会破坏泛型展开)
- ❌ 不在 yml `knife4j.openapi` 写 title/version(走 OpenApiConfig Java bean)
- ❌ 不在 Controller 返回值上硬覆盖 `@Schema(implementation = SomeClass.class)`(让 SpringDoc 通过返回类型自动解析)
- ❌ 不写新测试(注解纯文档元数据,运行时无副作用)

---

## 7. yml 与 bean 职责拆分

| 配置项              | 位置               | 理由                                                 |
| ------------------- | ------------------ | ---------------------------------------------------- |
| `knife4j.enable`    | yml                | profile 切换(dev=true / prod=false)                  |
| `knife4j.setting.*` | yml                | UI 行为(语言 / 主题等)                              |
| title / version / contact | Java bean (`OpenApiConfig`) | 不依赖 profile,所有环境统一                          |
| `bearerAuth`        | Java bean          | 与 `@SecurityRequirement(name="bearerAuth")` 配对 |

---

## 8. 验证

| 验证项                                | 命令                                                                                            | 期望                                                |
| ------------------------------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| compile                               | `mvn -DskipTests -pl backend/java-admin -am compile`                                            | 退出 0                                              |
| test                                  | `mvn test`(backend/java-admin)                                                                  | 全部 pass                                           |
| spotless:check                        | `mvn spotless:check`                                                                            | 0 violation                                         |
| checkstyle:check                      | `mvn checkstyle:check`                                                                          | 0 violation                                         |
| verify                                | `mvn verify`                                                                                    | BUILD SUCCESS                                       |
| (有本地 dev 栈时)OpenAPI tags         | `curl -s http://localhost:8080/v3/api-docs \| jq '.tags[].name'`                                | 含 `"鉴权"`、`"全局异常"`                            |
| (有本地 dev 栈时)securitySchemes      | `curl -s http://localhost:8080/v3/api-docs \| jq '.components.securitySchemes \| keys'`          | 含 `"bearerAuth"`                                   |
| (有本地 dev 栈时)schemas              | `curl -s http://localhost:8080/v3/api-docs \| jq '.components.schemas \| keys'`                  | 含 `LoginRequest` / `LoginResponse` / `UserInfoVO` / `Result` |

---

## 9. 常见错误(防回归)

| 错误                                                          | 现象                                          | 规避                                                                                |
| ------------------------------------------------------------- | --------------------------------------------- | ----------------------------------------------------------------------------------- |
| `common` 模块没引 `swagger-annotations-jakarta`               | Result.java 编译失败"找不到符号 Schema"       | 父 pom dependencyManagement + common pom 显式声明(见 §1.1)                          |
| `Result` 类加了 `@Schema(name="Result")`                     | 文档里 data 字段不展开成 `LoginResponse`      | 只在字段加 `@Schema`;类不加(§1.2)                                                   |
| Controller 方法没标 `@ApiResponses`                           | 错误响应不进文档,调试页面看不到 400/401 例子  | 每个公共方法显式 `@ApiResponses` 覆盖正常 + 错误                                    |
| `@RestControllerAdvice` 方法没标 `@Operation`                  | SpringDoc 不收录,文档里看不到错误响应         | 每个 `@ExceptionHandler` 加 `@Operation(summary=...)` + `@ApiResponse`              |
| `@SecurityRequirement(name="...")` 名称拼错                   | 文档页面不显示锁图标                           | name 必须与 OpenApiConfig 的 `addSecuritySchemes("bearerAuth", ...)` 完全一致       |
| yml 与 bean 都配了 title                                      | Knife4j 4.x 行为不确定                        | 二选一;推荐 bean(§7)                                                               |
| 在 yml 写 `knife4j.openapi`                                   | knife4j 4.5 行为不一致                        | yml 只管 enable / setting;title 等走 bean                                           |
| 直接用 swagger v2 注解(`io.swagger.annotations.*`)           | Knife4j 4.x 不识别,文档为空                   | 统一用 `io.swagger.v3.oas.annotations.*`                                            |

---

**本文件由 AI 在 2026-06-15 任务 `06-15-knife4j-annotations` 中首次落盘。**
**AI 后续写 Controller / DTO / VO 前应先读本文件,并在 `implement.jsonl` 中登记。**