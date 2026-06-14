# Implement — Spotless + palantir-java-format + Checkstyle 接入

> 任务：`06-14-spotless-palantir-format-integration`

---

## A. 开发策略决策

- **执行模式**：当前主会话 + `trellis-implement` 子代理（Phase 2.1）。改动面较大（4 个文件 + 2 个新配置 + 1 个 spec 同步），主会话透明，子代理可写代码。
- **分支策略**：在 `master` 上拉 `chore/spotless-palantir-format` 分支。改动是 `chore:` 级别，**不**用 worktree（无并发风险）。
- **流程**：默认流程（**非** TDD）。这次改的是工具链配置 + Checkstyle 规则集，**测试不了**（checkstyle 规则是声明式；无法在红绿之间过渡）。TDD 不适用。
- **Pre-development architecture guidance**：**不启用**。改动面集中在工具链层，4 模块架构未受影响。

## B. Review-gate contract: explicit-selection-v1

`Optional review gates status: configured`

- **Enabled optional review gates**:
  - `trellis-spec-review`（spec 文档同步是这次的一部分，需评审；Checkstyle 集成**翻转**了原"不集成 Checkstyle"的决策）
- **Disabled optional review gates**:
  - `trellis-code-review`（改动是配置 + 文档 + 2 个新 XML；逻辑量极小；主会话自检 + `trellis-check` 已覆盖）
  - `trellis-code-architecture-review`（无架构影响；4 模块边界未变）
  - `trellis-improve-codebase-architecture`（不启用 deep-review；前置 `trellis-code-architecture-review` 也未开）
  - `trellis-merge-review`（单 PR 单提交，无需 merge 评审）
- `trellis-check` 固定保留在 enabled 集合外（不计入此 list，Phase 3.1 必跑）

> 保序执行顺序：因 `trellis-spec-review` 单独启用，spec 评审与代码评审**不**重叠；`trellis-check` 在 Phase 3.1 独立执行，不与 `trellis-spec-review` 串行。

---

## C. 有序执行清单

1. **研究 Spotless + palantir-java-format + Checkstyle 集成细节**（必做）
   - 工具：`trellis-research` 子代理，落盘到 `research/spotless-palantir-integration.md`
   - 关键问题：
     - Spotless 2.x 最新稳定版本号
     - palantir-java-format 2.x 最新稳定版本号
     - Spring Boot 父 POM 自带的 `maven-checkstyle-plugin` 版本号（落地时确认）
     - Palantir 官方 checkstyle.xml 的**确切**路径与获取方式
     - Palantir checkstyle 是否依赖 `palantir-java-format` 同仓库附属 jar（避免需要额外依赖）
   - 落盘到 `.trellis/tasks/06-14-spotless-palantir-format-integration/research/spotless-palantir-integration.md`

2. **建 `build-tools/checkstyle/` 目录**
   - 新建 `backend/java-admin/build-tools/checkstyle/checkstyle.xml`（从 Palantir 官方取）
   - 新建 `backend/java-admin/build-tools/checkstyle/suppressions.xml`（按 design.md §3.4 模板）

3. **改 `backend/java-admin/pom.xml`**
   - 新增 `<spotless.version>` + `<palantir-java-format.version>` properties
   - 在 `<build><plugins>` 注册 `spotless-maven-plugin` + `maven-checkstyle-plugin`（参考 design.md §3.1）

4. **改 `lefthook.yml`**
   - `pre-commit.commands` 新增 `java-spotless`（apply）
   - `pre-push.commands` 新增 `java-checkstyle`（check）
   - 都加 `parallel: true` + `glob`

5. **首次 `mvn spotless:apply`**
   - 在 `backend/java-admin` 下跑 `mvn spotless:apply`，让 4 模块的 Java 全部被格式化
   - 跑 `mvn test`，确认 40+ 测试仍全过
   - `git add` + `git commit -m "chore: apply palantir-java-format"`，作为独立提交

6. **首次 `mvn verify` 验证 Checkstyle**
   - 跑 `mvn verify`，确认现有 4 模块代码通过（suppressions.xml 屏蔽后应 0 违例）
   - 在 `backend/java-admin/java-admin-common/src/main/java/.../_ProbeCheckstyleViolation.java` 写一个故意违例的 class（如过长方法 / 缺 javadoc）
   - 跑 `mvn verify`，应失败
   - 删除 `_ProbeCheckstyleViolation.java`（不入提交）

7. **改 `.trellis/spec/backend/quality-guidelines.md`**
   - 第 1 节 "Lint" 段整段替换（见 design.md §5）
   - 第 10 节 "不在范围内" 相应调整

8. **本地端到端验证**：
   - 故意制造一个未格式化的 `*.java` → `git add` → `git commit` → 验证自动 apply + re-add
   - 故意制造一个 Checkstyle 违例的全新 `*.java`（路径**不**在 suppressions.xml 里）→ `git push` → 验证 pre-push 阻断
   - 提交一个无 Java 变更的普通修改 → 预提交不跑 java-spotless（glob 不命中）

9. **commit + push**
   - 提交 `chore(build): integrate Spotless + palantir-java-format + Checkstyle`（父 POM + lefthook + build-tools/ + spec）
   - 上一次 `chore: apply palantir-java-format` 已在 step 5 独立提交

---

## D. 验证命令

```bash
# 1. 单元测试（不破坏现有）
cd backend/java-admin && mvn test

# 2. 完整 verify（包含 Checkstyle 绑 verify phase）
cd backend/java-admin && mvn verify

# 3. Spotless 检查
cd backend/java-admin && mvn spotless:check

# 4. Spotless apply（幂等）
cd backend/java-admin && mvn spotless:apply

# 5. Checkstyle 单独跑（fast feedback）
cd backend/java-admin && mvn checkstyle:check

# 6. 跳过 Checkstyle 的 package（确认 build 链路 OK 不依赖 checkstyle）
cd backend/java-admin && mvn -Dcheckstyle.skip=true -DskipTests package

# 7. 钩子端到端
# 7a. pre-commit 格式化：未格式化 *.java → git add → git commit → 自动 apply + re-add
# 7b. pre-push checkstyle：故意违例新文件 → git add → git push → 阻断
# 7c. glob 不命中：纯前端 / 文档变更 → 预提交不跑 java-spotless
```

## E. 高风险 / 关键文件

| 文件                                                         | 风险点                                                             | 缓解                                                                                   |
| ------------------------------------------------------------ | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------- |
| `backend/java-admin/pom.xml`                                 | Spotless / Checkstyle 配置错导致全模块 build 失败                  | `mvn verify` 立即验证；checkstyle 出错先看 console 报哪条规则                          |
| `backend/java-admin/build-tools/checkstyle/checkstyle.xml`   | Palantir 官方配置路径取错 / 包含与 palantir-java-format 冲突的规则 | 落地时跑 `mvn checkstyle:check`，如遇冲突逐条 `<suppress>`                             |
| `backend/java-admin/build-tools/checkstyle/suppressions.xml` | 抑制范围过宽（未来忘记收窄）                                       | 顶部加注释说明"基线屏蔽，未来按需细化"；spec 同步记录                                  |
| `lefthook.yml`                                               | pre-push checkstyle 阻断正常 push                                  | 提供 `LEFTHOOK=0` 逃生口；规则集应是 Palantir 自家（不会与 palantir-java-format 冲突） |
| 全量 `mvn spotless:apply`                                    | 第一次跑会大改文件                                                 | 单独 `chore:` 提交                                                                     |

## F. 回滚点

| 文件 / 段                                             | 回滚动作     |
| ----------------------------------------------------- | ------------ |
| 父 POM `<plugins>` 段                                 | 整段删除     |
| `backend/java-admin/build-tools/checkstyle/` 目录     | 整目录删除   |
| `lefthook.yml` `java-spotless` + `java-checkstyle` 段 | 整段删除     |
| spec 替换                                             | 还原原文     |
| `chore: apply palantir-java-format` 提交              | `git revert` |
| `chore(build): integrate ...` 提交                    | `git revert` |

回滚**不影响**业务代码。

---

## G. `task.py start` 前最后检查

- [ ] `prd.md` 已 review（R1-R13 完整、acceptance 可测）
- [ ] `design.md` 已落盘（边界 / 配置 / 兼容性 / 风险齐备）
- [ ] `implement.md` 已落盘（策略 + 闸门 + 清单 + 验证齐备）
- [ ] `implement.jsonl` / `check.jsonl` 已添加 spec + research 引用（Phase 1.3）
- [ ] `research/spotless-palantir-integration.md` 已落盘（Phase 1.2）
- [ ] Review-gate contract marker `explicit-selection-v1` 已写入
- [ ] `Optional review gates status: configured` 已写入
- [ ] `Enabled optional review gates:` / `Disabled optional review gates:` 已显式列全 5 个可选 gate
- [ ] 用户已 review 全部三份产物并同意进入实现

---

## H. 提交策略

**两次提交**（顺序很重要）：

1. **`chore: apply palantir-java-format`** — `mvn spotless:apply` 一次跑完所有 Java 改动的纯格式化 commit
2. **`chore(build): integrate Spotless + palantir-java-format + Checkstyle`** — POM 改动 + lefthook 改动 + `build-tools/` 新增 + spec 同步

第一次先把代码格式化干净；第二次再加工具链。**顺序很重要**：先 apply 再接工具链，避免一次性 PR 出现上千行空白变更与几十行配置混在一起。
