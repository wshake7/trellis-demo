# 为 java-admin 接入 Spotless + palantir-java-format **+ Checkstyle**

## Goal

为 `backend/java-admin`（4 模块 Spring Boot 4.0.7 / Java 17 / Maven 多模块项目）接入两个互补的 Java 工具链：

1. **Spotless + palantir-java-format** —— 自动格式化器（whitespace / 缩进 / import / 换行）。
2. **Checkstyle** —— 静态检查（命名 / 复杂度 / 风格规约 / 强制规范）。

提供 `mvn spotless:apply` 与 `mvn spotless:check`；提供 `mvn checkstyle:check`（必要时 `checkstyle:checkstyle:check`）。lefthook pre-commit 在暂存 Java 文件时自动跑 spotless:apply（格式化），并把变更追加入暂存区。Checkstyle 作为构建期门禁（默认 error 级，build 失败）。

## Confirmed Facts（仓库证据）

- 父 POM：`backend/java-admin/pom.xml`，`<packaging>pom</packaging>`，4 个 module：`common / service / infra / api`。
- Spring Boot 父 POM：`4.0.7`；Java 17。
- 父 POM 已用 `<pluginManagement>` 包裹 `maven-compiler-plugin`（包含 Lombok + Easy-Query APT 路径）。
- `lefthook.yml` 已经有一段 pre-commit 命令：`pnpm exec vp staged`（前端 / TS / 全局 staged 流程）。
- 项目根无 `.github/workflows`（CI 暂不涉及本任务）。
- `.trellis/spec/backend/quality-guidelines.md` 明确写："Lint：项目**不**集成 Checkstyle / SpotBugs（MVP 阶段）；依赖 IDE 提示 + 命名规范"——**本次任务**是用户主动推翻此决策，转为"集成 Spotless + Checkstyle，**不**集成 SpotBugs"。

## Requirements

- **R1**：在父 POM 添加 `com.diffplug.spotless:spotless-maven-plugin`，配置 `palantirJavaFormat`，作用于所有 4 个子模块。
- **R2**：格式化同时覆盖 `src/main/java` 与 `src/test/java`。
- **R3**：保留 `mvn spotless:check` 作为可调用的检查命令（开发自检 + 未来 CI 用）。
- **R4**：在 `lefthook.yml` 接入 pre-commit **自动 apply**（用户选择策略 A）：当暂存中含 `backend/java-admin/**/src/{main,test}/java/**/*.java` 时，`cd backend/java-admin && mvn spotless:apply`，对暂存文件进行格式化，并把变更追加回暂存区（`git add`）。`vp staged` 仍按原状执行；不修改其逻辑。
- **R5**：未格式化或格式未通过的文件 → 钩子失败，提示运行 `mvn spotless:apply`。
- **R6**：允许通过 `LEFTHOOK=0` / `git commit --no-verify` 跳过钩子。
- **R7**：同步更新 `.trellis/spec/backend/quality-guidelines.md`：在"Lint"段加注 "Spotless + Checkstyle 已集成（Spotless 走 palantir-java-format；Checkstyle 走 Palantir 自家规则集；**不**集成 SpotBugs）"，**移除**原 "MVP 阶段不集成 Checkstyle" 的旧声明。
- **R8**：在父 POM 添加 `maven-checkstyle-plugin`，使用 **Palantir 自家 checkstyle 配置**（与 palantir-java-format 零冲突），作用于 4 个子模块。
- **R9**：Checkstyle 同时覆盖 `src/main/java` 与 `src/test/java`。
- **R10**：severity 等级：**Error**（违例 → `mvn verify` / `mvn package` 失败）。
- **R11**：Checkstyle 配置 XML 落到仓库内（`backend/java-admin/build-tools/checkstyle/checkstyle.xml`），版本化、可审计；不依赖外部 URL。
- **R12**：lefthook pre-commit **只**接 Spotless（auto-apply）。Checkstyle 跑在两个地方：`git push` 前（lefthook pre-push 跑 `mvn checkstyle:check`）+ `mvn verify` 阶段（maven-checkstyle-plugin 绑 verify phase）。Pre-commit 阶段不跑 Checkstyle。
- **R13**：存量违例处理：在 `backend/java-admin/build-tools/checkstyle/suppressions.xml` 按文件 / 路径抑制现有违例，新写入的文件不受抑制。

## Acceptance Criteria

- [ ] 父 POM 增加 Spotless + Checkstyle 插件配置（`<plugins>`）。
- [ ] `backend/java-admin/build-tools/checkstyle/checkstyle.xml` 落盘（Palantir 官方配置）。
- [ ] `backend/java-admin/build-tools/checkstyle/suppressions.xml` 落盘（基线现有 4 模块代码路径）。
- [ ] `cd backend/java-admin && mvn spotless:apply` 能跑通，所有 4 个模块的 `*.java` 全部被 palantir-java-format 重新格式化。
- [ ] `mvn spotless:check` 跑通（apply 后必须 exit 0）。
- [ ] `mvn verify` 跑通，Checkstyle 通过（无 error）；新写入的 `*.java` 触发的 Checkstyle 违例必须使构建失败。
- [ ] `lefthook.yml` 新增 `pre-commit.java-spotless`（apply）和 `pre-push.java-checkstyle`（check）两个步骤。
- [ ] 提交一个故意未格式化的 `*.java` 时，pre-commit 自动 apply 并 re-add，提交继续。
- [ ] 在新模块（`/tmp/newfile.java` 之类的全新文件）违反某条 Checkstyle 规则时，`mvn verify` 失败。
- [ ] `mvn -DskipTests package` 仍能成功（确认 Spotless + Checkstyle 不破坏现有构建）。
- [ ] `.trellis/spec/backend/quality-guidelines.md` 的 "Lint" 段落同步更新，注明本任务引入了 Spotless + Checkstyle，移除了"不集成 Checkstyle"的旧声明。
- [ ] `mvn test`（4 模块 40+ 测试）仍然全部通过。

## Out of Scope

- ❌ SpotBugs / Error Prone（明确不在本期）
- ❌ IDE 端（IntelliJ / Eclipse）保存自动格式化 / 检查配置（用户自配）
- ❌ GitHub Actions / CI 集成（项目根暂无 workflows，CI 留给未来任务）
- ❌ `ratchetFrom` 增量格式化（首期全量格式化一次；未来再加）
- ❌ `importOrder` 进一步定制（palantir-java-format 内置处理）
- ❌ SQL / YAML / properties 格式化（仅 Java）
- ❌ 现有 4 模块代码 Checkstyle 违例的逐步修复（**只**走 suppressions.xml 基线；新代码必须严格过关）

## Open Questions

已收敛的：

- ✅ lefthook 策略：自动 apply（用户选定策略 A）
- ✅ 格式化范围：main + test
- ✅ Checkstyle 规则集：Palantir 自家
- ✅ Checkstyle severity：Error（build 失败）
- ✅ 存量代码策略：suppressions.xml 基线
- ✅ Lefthook Checkstyle 时机：pre-push + mvn verify；pre-commit 不接
- ✅ SpotBugs / CI / ratchetFrom：本期不引入

待 implement 阶段确定：

- Spotless 插件具体版本号（落地时取最新 2.x 稳定版）
- palantir-java-format 具体版本号（落地时取最新稳定版）
- maven-checkstyle-plugin 插件版本（落地时取最新稳定版；Spring Boot 父 POM 锁的版本优先）
- suppressions.xml 的具体抑制规则（落地时跑一次全模块扫描，按结果填）

## Notes

- Spotless 插件版本：使用与 Spring Boot 4 + JDK 17 兼容的最新稳定版（2.x 最新）。落地时取最新 release 写入 `<properties>`。
- `palantir-java-format` 自身不发布到 Maven Central，需要通过 `palantir-java-format-2.43.0.jar` 走 `palantirJavaFormat { palantirJavaFormatVersion() }` 引用；最新稳定版落地时确认。
- 子模块 pom 继承父 POM 即可，无需单独引入 Spotless 依赖。
