# Design — Spotless + palantir-java-format **+ Checkstyle** 接入

> 任务：`06-14-spotless-palantir-format-integration`
> 范围：4 模块 Maven 多模块 Spring Boot 4.0.7 / Java 17 项目 `backend/java-admin`

---

## 1. 目标与边界

### 1.1 目标

为 `backend/java-admin` 接入**两个互补**的 Java 工具链：

1. **Spotless + palantir-java-format** —— 格式化器（whitespace / 缩进 / import / 换行）。
2. **Checkstyle（Palantir 自家配置）** —— 静态检查（命名 / 复杂度 / 风格规约 / 强制规范）。

lefthook pre-commit 自动跑 Spotless apply（暂存 Java 文件）；lefthook pre-push 跑 Checkstyle check；`mvn verify` 阶段 Checkstyle 绑 verify phase 兜底。

### 1.2 边界 / 非目标

- 仅 Java 源码。不格式化 / 检查 SQL、YAML、properties。
- **不**集成 SpotBugs / Error Prone。
- **不**接 GitHub Actions CI（项目根暂无 `.github/workflows`）。
- **不**配置 IDE 端（IntelliJ save action）；用户自行决定。
- **不**修现有 4 模块代码的存量 Checkstyle 违例；走 suppressions.xml 基线屏蔽。

---

## 2. 工具选型

| 项                 | 选                              | 理由                                                                                                  |
| ------------------ | ------------------------------- | ----------------------------------------------------------------------------------------------------- |
| 格式化器           | **Spotless 2.x**                | 业内事实标准；Maven 插件成熟；支持多模块                                                              |
| Formatter          | **palantir-java-format 2.x**    | 用户指定；与 Palantir Checkstyle **零冲突**                                                           |
| Lint               | **maven-checkstyle-plugin 3.x** | Spring Boot 父 POM 已带 `maven-checkstyle-plugin` 的 `pluginManagement`，可继承默认版本；也可用最新版 |
| Checkstyle 规则集  | **Palantir 自家 checkstyle**    | 与 palantir-java-format 同源；零冲突；命名 / 复杂度规则与 formatter 严格分层                          |
| 钩子               | **lefthook**（已存在）          | pre-commit / pre-push 两阶段                                                                          |
| `ratchetFrom`      | **不启用**                      | 首期全量格式化一次                                                                                    |
| `importOrder` step | **不启用**                      | palantir-java-format 自带 import 整理                                                                 |

### 2.1 版本

| 组件                    | 推荐版本                                  | 来源                         |
| ----------------------- | ----------------------------------------- | ---------------------------- |
| spotless-maven-plugin   | 2.x 最新稳定（候选 2.46.x）               | Maven Central                |
| palantir-java-format    | 2.x 最新稳定（候选 2.71.0）               | Palantir GitHub Releases     |
| maven-checkstyle-plugin | Spring Boot 父 POM 锁的版本（默认 3.6.x） | 继承父 POM，无需显式 version |

Spring Boot 父 POM 在 `pluginManagement` 里已经定义了 `maven-checkstyle-plugin` 的版本，子 POM 显式引用即继承该版本。**复用**而非另选。

---

## 3. 配置结构

### 3.1 父 POM `backend/java-admin/pom.xml`

新增 properties：

```xml
<properties>
    ...
    <spotless.version>2.46.0</spotless.version>
    <palantir-java-format.version>2.71.0</palantir-java-format.version>
</properties>
```

在 `<build>` 下新增 `<plugins>` 段（**放 `<plugins>` 而非 `<pluginManagement>`** 让 4 模块都生效）：

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                ... <!-- 已有 -->
            </plugin>
        </plugins>
    </pluginManagement>
    <plugins>
        <!-- Spotless -->
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

        <!-- Checkstyle (绑 verify phase, 强制 error) -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-checkstyle-plugin</artifactId>
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
    </plugins>
</build>
```

**关键点**：

- `configLocation` / `suppressionsLocation` 走相对路径，指向**父 POM 同级的 `build-tools/` 目录**（见 §3.2）。
- `<includeTestSourceDirectory>true</includeTestSourceDirectory>`：同时检查测试代码。
- `<violationSeverity>error</violationSeverity>`：error 级违例计入 `failOnViolation`。
- `<failOnWarning>false</failOnWarning>`：warning 不阻断（防规则集 warning 噪音过多）。
- 绑 verify phase → `mvn verify` / `mvn package` / `mvn install` 都会跑；`mvn test` 不跑。

### 3.2 Checkstyle 配置文件目录

新建 `backend/java-admin/build-tools/checkstyle/`：

```
backend/java-admin/build-tools/
└── checkstyle/
    ├── checkstyle.xml         # Palantir 自家配置
    └── suppressions.xml       # 现有 4 模块代码基线
```

**为什么放 `build-tools/` 而非各模块根**：

- 4 模块共享同一份规则，集中在 `build-tools/` 让所有模块通过 `${project.basedir}/../build-tools/...` 引用。
- 未来新增模块（业务模块、bom 模块）直接继承父 POM 即可。
- 规则集**单一**来源，避免各模块漂移。

### 3.3 `checkstyle.xml`

**首选**：从 Palantir 官方仓库 `palantir-java-format` 仓库的 `gradle/build-logic/src/main/kotlin/...` 或 `style/src/main/resources/checkstyle.xml` 取（具体路径落地时再确认）。**备选**：GitHub `palantir/palantir-java-format` 仓库 `develop` 分支搜 `checkstyle.xml`。

预期包含的规则大类（**不要**包含 whitespace / line length 之类由 palantir-java-format 管的规则）：

- 命名：`ClassDataAbstractionCoupling`、`AbbreviationAsWordInName`、`TypeName`
- 复杂度：`CyclomaticComplexity`、`NPathComplexity`、`JavaNCSS`
- 风格规约：`EmptyBlock`、`NeedBraces`、`ModifierOrder`
- Magic number / 常量：`MagicNumber`、`ConstantName`
- 异常处理：`ThrowsCount`、`IllegalCatch`
- Javadoc：`JavadocMethod`、`JavadocStyle`

落地时**实测**：`mvn checkstyle:check` 后看 console 报的是哪些规则；如某条规则与 palantir-java-format 冲突，加 `<suppress>` 局部屏蔽。

### 3.4 `suppressions.xml`

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

**关键点**：

- 8 个通配抑制每个模块的 main + test，等价于"现有代码 0 违例视为通过"。
- 落到 `suppressions.xml` 而不是修改 `checkstyle.xml`：未来移除基线只需改 `suppressions.xml` 不动规则集。
- **新增的 `*.java` 文件**（不在以上 8 个 glob 里）默认受 Checkstyle 严格约束。
- 落地时跑 `mvn checkstyle:check` 验证：现有代码 exit 0；新写一个故意违例的 `*.java`（在 `/tmp/` 之类）应 exit ≠ 0。

### 3.5 `lefthook.yml`

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

**关键设计**：

- **pre-commit** 仅 spotless apply（快、改动、re-add）。
- **pre-push** 跑 `mvn checkstyle:check`（慢、阻断、check only）。如果暂存没有 Java 改动（`glob` 不命中），不跑。
- `parallel: true` 让 pre-push 与未来其他命令（如 `mvn test`）并行。
- `mvn checkstyle:check` 与 `mvn verify` 里的 checkstyle 阶段**功能重叠**——`mvn verify` 仍绑 phase 作为兜底。Pre-push 是 fast feedback。

### 3.6 退出码与提示

| 阶段                      | 命令                   | 失败行为                                                          |
| ------------------------- | ---------------------- | ----------------------------------------------------------------- |
| pre-commit spotless       | `mvn spotless:apply`   | 不阻断（apply 是修改不是检查）；如 mvn 自身 fail 则阻断           |
| pre-push checkstyle       | `mvn checkstyle:check` | 阻断 push；console 报违例位置                                     |
| `mvn verify`              | checkstyle 绑 phase    | 阻断 build；同上                                                  |
| `mvn -DskipTests package` | 同 verify              | 仍跑 checkstyle（**注意**：用 `-Dcheckstyle.skip=true` 才能跳过） |

---

## 4. 与现有 `vp staged` 的协作

`vp staged` 是 Vite+ 的 staged-file-only 工具，**只针对前端 / TS / 全局 JS**。它不处理 `*.java`。

| 工具                   | 范围                     | 触发                         |
| ---------------------- | ------------------------ | ---------------------------- |
| `vp staged`            | TS / Vue / JS / 配置文件 | 任何 staged 文件             |
| `mvn spotless:apply`   | `*.java`（仅 backend）   | pre-commit，glob 限定        |
| `mvn checkstyle:check` | `*.java`（仅 backend）   | pre-push + `mvn verify` 阶段 |

三者职责清晰，不互相阻塞。

---

## 5. spec 同步

`.trellis/spec/backend/quality-guidelines.md` 当前第 1 节"选型 → Lint"段写：

> Lint：项目**不**集成 Checkstyle / SpotBugs（MVP 阶段）；依赖 IDE 提示 + 命名规范

**改为**（整段替换）：

> Lint：项目集成 **Spotless（palantir-java-format）** + **Checkstyle（Palantir 自家规则集）**。Spotless 负责格式，Checkstyle 负责命名 / 复杂度 / 风格规约（severity=error，绑 `verify` phase）。`mvn verify` 跑 Checkstyle；`mvn spotless:apply` 自动格式化。`SpotBugs` / `Error Prone` 仍**不在本期范围**。现有 4 模块代码以 `suppressions.xml` 基线屏蔽，新写入文件需严格过关。

并把"不在范围内"段（第 10 节）相应调整：移除"❌ 不集成 Checkstyle"暗示（保留 SonarQube / OWASP 仍不在范围）。

---

## 6. 兼容性与回滚

### 6.1 兼容性

- 不修改 Java 编译路径。
- 不引入新运行时依赖（Spotless / Checkstyle 都是 build-time only）。
- 子模块 pom 不变。
- 现有 40+ 测试不受影响。

### 6.2 回滚

| 文件 / 段                                             | 回滚动作     |
| ----------------------------------------------------- | ------------ |
| 父 POM `<plugins>` 段（Spotless + Checkstyle）        | 整段删除     |
| `build-tools/checkstyle/` 目录                        | 整目录删除   |
| `lefthook.yml` `java-spotless` / `java-checkstyle` 段 | 整段删除     |
| spec 加注                                             | 删整段替换   |
| 第一次 `chore: apply palantir-java-format` 提交       | `git revert` |

回滚**不影响**业务代码。

---

## 7. 风险与缓解

| 风险                                           | 可能性 | 影响                    | 缓解                                                                                |
| ---------------------------------------------- | ------ | ----------------------- | ----------------------------------------------------------------------------------- |
| Spotless 首次 apply 大规模改动                 | 高     | PR diff 大              | 独立 `chore:` 提交                                                                  |
| palantir-java-format 与现有代码风格差异大      | 中     | 阅读体验切换            | 用户已指定；接受                                                                    |
| Checkstyle 规则与现有代码违例数超预期          | 中     | suppressions.xml 行数多 | 现状：8 个 glob 一行解决；未来按需细化                                              |
| mvn 启动慢影响 pre-commit 体验                 | 中     | 提交变慢                | 只对 Java 变更触发                                                                  |
| pre-push 跑 checkstyle 后 push 变慢            | 中     | push 体验差             | 接受；与 `mvn verify` 重复（pre-push 是 fast feedback）                             |
| Checkstyle 规则与 Lombok / Easy-Query 注解冲突 | 低     | 编译失败                | 落地时跑 `mvn verify` 验证；如冲突，添加 `SuppressionCommentFilter` 或局部 suppress |
| suppressions.xml 抑制范围过宽（未来忘记收窄）  | 中     | 规则被绕过              | 写在 `suppressions.xml` 顶部注释里强调"基线文件"；spec 同步记录                     |
| 用户用 `--no-verify` 绕过                      | 中     | 长期不规范              | 钩子不阻断 apply 模式；pre-push 阻断 push 但允许 `--no-verify`（罕见情况）          |
