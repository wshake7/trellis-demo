# Research: Spotless + palantir-java-format + Checkstyle 集成

> 任务：`06-14-spotless-palantir-format-integration`
> 落地时间：2026-06-14

---

## 1. Spotless Maven Plugin 最新版本

- **最新稳定版**：`3.6.0`（2026-05-27 发布）
- **下载地址**：<https://github.com/diffplug/spotless/releases/tag/maven/3.6.0>
- **Maven Central**：<https://central.sonatype.com/artifact/com.diffplug.spotless/spotless-maven-plugin>
- **兼容**：JDK 17 + Spring Boot 4.0.7 ✓
- **3.6.0 关键变化**：`spotless:apply` 不再在第一个 lint 文件上中止（改为聚合所有 lint 一次报告）；EclipseJdtFormatter 改善新 Java 特性（records / sealed）解析。
- 备选 3.5.1 也稳定可用。

## 2. palantir-java-format 最新版本

- **最新稳定版**：`2.92.0`（2026-06-10 发布）
- **2.92.0 关键变化**：只对受信任项目运行 formatter（安全修复）。
- **下载**：<https://github.com/palantir/palantir-java-format/releases/tag/2.92.0>
- **兼容**：JDK 17 ✓；与 Spring Boot 4 无冲突
- 备选 2.91.0 / 2.90.0。

## 3. maven-checkstyle-plugin 版本

- **Spring Boot 4.0.7 父 POM 锁的版本**：通过继承 `spring-boot-starter-parent`，`<pluginManagement>` 中已含 `maven-checkstyle-plugin` 的版本（4.0.x 系列对应 3.6.x 插件）。**子 POM 不显式写 `<version>` 即继承父 POM 的版本**。
- **最新独立版**：`3.6.0`（2024+）
- **兼容**：JDK 17 + Checkstyle 10.x ✓
- 落地时不指定 `<version>` 走继承即可。

## 4. Palantir Checkstyle 配置（**关键发现**）

**Palantir 官方 Checkstyle 配置**位于 **`palantir/gradle-baseline`** 仓库：

- **直接 raw URL**：<https://raw.githubusercontent.com/palantir/gradle-baseline/develop/.baseline/checkstyle/checkstyle.xml>
- **本仓库内位置**：`gradle-baseline/.baseline/checkstyle/checkstyle.xml`
- **许可证**：Apache-2.0
- **配套 suppressions**：
  - `checkstyle-suppressions.xml`（baseline 仓库自动生成；用户可覆盖）
  - `custom-suppressions.xml`（用户自定义，**不被 baselineUpdateConfig 覆盖**）
- **内容**：包含 ~80 条规则，覆盖：
  - 命名（`AbbreviationAsWordInName`、`MemberName`、`MethodName`、`ClassTypeParameterName`、`LocalVariableName`、`ParameterName`）
  - import（`AvoidStarImport`、`AvoidStaticImport`、`UnusedImports`、`RedundantImport`、`ImportOrder`）
  - 风格（`EmptyBlock`、`NeedBraces`、`RightCurly`、`OperatorWrap`、`WhitespaceAfter` 等）
  - 复杂度（`CyclomaticComplexity`、`NestedForDepth`、`NestedTryDepth`、`MethodLength`）
  - 异常（`IllegalThrows`、`EmptyCatchBlock`、`MutableException`）
  - 禁止（`BanSystemOut`、`BanSystemErr`、`BanLoggingImplementations`、`BanGuavaCaches`、`IllegalImport` for `junit.framework`）
  - 风格规约（`NoLineWrap`、`SeparatorWrap`、`GenericWhitespace`）
  - Javadoc（`JavadocMethod`、`JavadocStyle`、`AtclauseOrder`、`NonEmptyAtclauseDescription`）
  - TODO 格式（`// TODO(#issue): explanation`）
  - Merge conflict 检测（`<<<<<<<` / `>>>>>>>`）

**与 palantir-java-format 的关系**：

- palantir-java-format 是 formatter（处理 whitespace / import / 换行）
- 这个 checkstyle 是**独立的规则集**，主要管**命名 / 复杂度 / 禁止 / Javadoc**
- **重叠很少**（但有几条重叠需要注意，见 §6）
- 都需要下载配置；checkstyle 不依赖 palantir 内部 jar，**纯规则集**

## 5. 依赖分析

- **Spotless 插件**：需要 `com.diffplug.spotless:spotless-maven-plugin:3.6.0`
- **palantir-java-format**：Spotless 通过 `palantirJavaFormat { version = "2.92.0" }` 自动从 Maven Central 拉取 `com.palantir.javaformat:palantir-java-format:2.92.0`，**不需要在 `<dependencies>` 显式声明**
- **maven-checkstyle-plugin**：Spring Boot 父 POM 已提供 `<pluginManagement>` 配置
- **Checkstyle 规则**：纯 XML，不依赖任何额外 jar
- **结果**：构建**无新增运行时依赖**（spotless / checkstyle 都是 build-time only）

## 6. Spotless palantirJavaFormat 父 POM 配置模板

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>${spotless.version}</version>
    <configuration>
        <java>
            <palantirJavaFormat>
                <version>${palantir-java-format.version}</version>
            </palantirJavaFormat>
        </java>
    </configuration>
</plugin>
```

**多模块 reactor 行为**：

- 父 POM `<plugins>` 注册 → Spotless 自动对**每个子模块的 `src/main/java` + `src/test/java`** 生效
- `mvn spotless:apply` 跑完整个 reactor
- `mvn spotless:check` 同样
- **不需要**在子 POM 重复配置

**已知重叠规则**（palantir-java-format 已处理，checkstyle 可不需）：

- `ImportOrder`：palantir-java-format 自带 import 排序；Palantir checkstyle 也含 `ImportOrder` 规则，**两者一致**（groups=.\*, separated=true, sortStaticImportsAlphabetically=true），无冲突。
- `WhitespaceAfter`、`NoWhitespaceBefore`、`GenericWhitespace` 等 whitespace 规则：palantir-java-format 会改这些，checkstyle 不会报错（因为改完必然符合规则）。**无冲突**。

## 7. maven-checkstyle-plugin 父 POM 配置模板

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <!-- 不写 version, 继承 Spring Boot 父 POM 锁定的版本 -->
    <configuration>
        <configLocation>${project.basedir}/../build-tools/checkstyle/checkstyle.xml</configLocation>
        <suppressionsLocation>${project.basedir}/../build-tools/checkstyle/suppressions.xml</suppressionsLocation>
        <includeTestSourceDirectory>true</includeTestSourceDirectory>
        <failOnViolation>true</failOnViolation>
        <failOnWarning>false</failOnWarning>
        <violationSeverity>error</violationSeverity>
        <consoleOutput>true</consoleOutput>
        <linkXRef>false</linkXRef>
    </configuration>
    <executions>
        <execution>
            <id>checkstyle-verify</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**关键点**：

- `${project.basedir}/../build-tools/...` 从**子模块目录**向**上**一级到父 POM 目录，然后进 `build-tools/`。例如 `java-admin-common/` 是 `${project.basedir}`，父 POM 目录是 `java-admin/`（上一层），`build-tools/` 在 `java-admin/` 下。
- 父 POM `<plugins>` 注册 → 对所有 4 个子模块生效。
- `<includeTestSourceDirectory>true</includeTestSourceDirectory>`：同时检查 `src/test/java`。
- `<violationSeverity>error</violationSeverity>`：只把 error 计入 build 失败。
- 绑 `verify` phase → `mvn verify` / `mvn package` / `mvn install` 都会跑；`mvn test` **不**跑。

## 8. lefthook 配置模板

```yaml
pre-commit:
  parallel: true
  commands:
    staged:
      run: pnpm exec vp staged
    java-spotless:
      glob: "backend/java-admin/**/src/{main,test}/java/**/*.java"
      run: mvn -B -q -f backend/java-admin/pom.xml spotless:apply && git add backend/java-admin
      stage_fixed: true

pre-push:
  parallel: true
  commands:
    java-checkstyle:
      glob: "backend/java-admin/**/src/{main,test}/java/**/*.java"
      run: mvn -B -f backend/java-admin/pom.xml checkstyle:check
```

**关键点**：

- `parallel: true` 让两个命令并行（pre-commit 里 vp staged 与 java-spotless；pre-push 里 java-checkstyle 与未来其他）。
- `glob` 控制命令**何时**触发（暂存有匹配文件才跑）。
- `stage_fixed: true` 是 lefthook 原生机制；与 `git add backend/java-admin` 互为冗余双保险。

## 9. suppressions.xml 基线模板

```xml
<?xml version="1.0"?>
<!DOCTYPE suppressions PUBLIC
    "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
    "https://checkstyle.org/dtds/suppressions_1_2.dtd">
<suppressions>
    <!-- 现有 4 模块代码基线（首期一次性扫描后归档） -->
    <suppress files="java-admin-common/src/main/java/.*" checks=".*"/>
    <suppress files="java-admin-common/src/test/java/.*" checks=".*"/>
    <suppress files="java-admin-service/src/main/java/.*" checks=".*"/>
    <suppress files="java-admin-service/src/test/java/.*" checks=".*"/>
    <suppress files="java-admin-infra/src/main/java/.*" checks=".*"/>
    <suppress files="java-admin-infra/src/test/java/.*" checks=".*"/>
    <suppress files="java-admin-api/src/main/java/.*" checks=".*"/>
    <suppress files="java-admin-api/src/test/java/.*" checks=".*"/>
    <!-- 未来: 新增模块/文件不进 suppressions, 默认严格过关 -->
</suppressions>
```

8 个通配抑制每个模块的 main + test。**新增的 `*.java` 不在以上 8 个 glob 里**，默认受 Checkstyle 严格约束。

## 10. Gotchas / 风险点

1. **首次 `mvn spotless:apply` 大改**：4 模块约 80+ Java 文件全会被 palantir-java-format 改写（whitespace / import 顺序 / Javadoc 换行）。**必须**作为独立 `chore:` 提交。
2. **Spring Boot 父 POM 锁的 maven-checkstyle-plugin 版本可能较旧**：如果落地时跑 `mvn verify` 报 plugin 找不到或版本冲突，**显式写** `<version>3.6.0</version>` 即可。
3. **Lombok 注解**：palantir-java-format 不动注解内文本；Lombok 生成的代码（`@Slf4j`、`@Data`）位于不同 source file 不冲突。但 `JavadocMethod` 要求 public 方法有 Javadoc——可能误报 Lombok 自动生成的方法。**视情况**用 `@SuppressWarnings("checkstyle:JavadocMethod")` 局部抑制。
4. **`Maven verify` 比 `mvn test` 慢**：checkstyle 绑 verify 阶段，`mvn package` 也会跑。开发调试时可用 `mvn -Dcheckstyle.skip=true test` 跳过。
5. **EclipseJdtFormatter 与 palantir 不能混用**：Spotless 配置中**只**用 `palantirJavaFormat` step，不要加 `eclipse` step。
6. **lefthook pre-push 第一次跑会下载大量 Maven 依赖**：第一次 push 比较慢（10-30s）；后续稳定。
7. **`@Slf4j` 与 checkstyle 兼容性**：Checkstyle 不感知 Lombok，但 `@SuppressWarnings("unused")` 加在 `@Slf4j log` 上是常规做法；项目当前已经在用 `@Slf4j`，落地时跑一次看 console 输出确认无冲突。
8. **suppressions.xml 抑制范围过宽**：未来加新模块时**记得**不在 8 个 glob 里（默认严格），**但** 8 个 glob 之外的新代码必须严格过关。
9. **Checkstyle 10.x DTD**：suppressions.xml 用 `https://checkstyle.org/dtds/suppressions_1_2.dtd`（10.x 仍兼容 1.2）。
10. **SB 4 + JDK 17 跑 mvn 时偶发 `CleanerJava24`**：用 JDK 17 跑（spec 文档已说明）。

## 11. 落地清单（直接给 implement 子代理用）

| 文件                                                         | 操作                              | 来源                                                                                                     |
| ------------------------------------------------------------ | --------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `backend/java-admin/pom.xml`                                 | 编辑：properties + `<plugins>` 段 | 见 §6、§7                                                                                                |
| `backend/java-admin/build-tools/checkstyle/checkstyle.xml`   | 新建：拷贝 §4 链接的 raw URL      | <https://raw.githubusercontent.com/palantir/gradle-baseline/develop/.baseline/checkstyle/checkstyle.xml> |
| `backend/java-admin/build-tools/checkstyle/suppressions.xml` | 新建：见 §9                       | 本文件                                                                                                   |
| `lefthook.yml`                                               | 编辑：见 §8                       | 本文件                                                                                                   |
| `.trellis/spec/backend/quality-guidelines.md`                | 编辑：见 design.md §5             | 本任务 design.md                                                                                         |

## 12. 来源 URLs

- Spotless 3.6.0 release：<https://github.com/diffplug/spotless/releases/tag/maven/3.6.0>
- Spotless Maven plugin README：<https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md>
- palantir-java-format 2.92.0 release：<https://github.com/palantir/palantir-java-format/releases/tag/2.92.0>
- Palantir gradle-baseline repo：<https://github.com/palantir/gradle-baseline>
- **Palantir checkstyle.xml raw**（**关键**）：<https://raw.githubusercontent.com/palantir/gradle-baseline/develop/.baseline/checkstyle/checkstyle.xml>
- Spring Boot 4.0.7 parent POM (maven-checkstyle-plugin 版本源)：继承自 `spring-boot-starter-parent:4.0.7`
