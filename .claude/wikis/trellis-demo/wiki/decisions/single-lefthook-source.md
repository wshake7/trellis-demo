# decisions/single-lefthook-source

为什么仓库用单一的根 `lefthook.yml` 而**不**让 Vite+ 自动注入 hook?

## 状态

**Stable** —— 已落地。

## 决策内容

1. 根仓库的 git hooks **不**通过 Vite+ 的 `vp config` 注入
2. 所有钩子在仓库根的 `lefthook.yml` 里集中定义[^src-006]
3. `apps/vue-vben-admin` 单独有自己的 `lefthook.yml`,作为子仓的子集[^src-012]

## 为什么

`lefthook.yml` 文件顶部注释明确写了[^src-006]:

> 取代 Vite+ 的 `vp config` 安装时注入 hook 的方式,改为单一版本受控配置。
> vue-vben-admin 子模块自带 lefthook.yml,根 hook 不再覆盖其代码。

实际动机拆解:

### 1. Vite+ 注入不可见

`vp config` 在 install 时自动改 `.git/hooks/*`,且**没有**声明式配置文件记录改了什么。换台机器或重装 hook 后行为可能不一致。lefthook 的所有行为都在 `lefthook.yml` 里 diff-able。

### 2. 钩子逻辑复杂到需要声明式

根仓库钩子包含:[^src-006]

- 5 类 hooks(pre-commit / post-checkout / post-merge / pre-push / commit-msg)
- pre-commit 里 4 个并行命令(format:staged / lint:react-admin / typecheck:react-admin / secret-scan / format:java)
- 多个 `glob:` 限定
- shell 包装(react-admin 的 `sed` 剥前缀)
- 条件跳过(`command -v gitleaks >/dev/null 2>&1`)

这些用 `vp config` 注入的简单 shell 脚本无法表达。

### 3. 多语言栈需要统一入口

仓库混了 TS/Vue/React/Java(Vite+ 默认只管前端),pre-commit 的 `format:java` 跑 `mvn ... spotless:apply` 与 `vp` 完全无关[^src-006]。

## 实现细节

### pre-commit(并行 4 道)[^src-006]

```yaml
pre-commit:
  parallel: true
  commands:
    format:staged:    { run: pnpm exec vp staged }
    lint:react-admin: { glob: "...", run: sh -c 'pnpm -C apps/react-admin exec eslint ...' }
    typecheck:react-admin: { glob: "...", run: pnpm -C apps/react-admin typecheck }
    secret-scan:      { run: sh -c 'command -v gitleaks >/dev/null 2>&1 && exec gitleaks protect --staged --redact --no-banner; echo skip:gitleaks-not-installed' }
    format:java:      { glob: "...", run: mvn -B -q -f backend/java-admin/pom.xml spotless:apply && git add backend/java-admin; stage_fixed: true }
```

- `format:java` 用 `stage_fixed: true` 让 spotless 重新 stage 改过的文件
- `secret-scan` 用 `command -v && exec ...; echo skip` 模式,**故意**用 `exec` 替换 shell 进程让 gitleaks 的非零退出码不被 `||` 误吞为"skip"

### post-checkout / post-merge 都跑 `codegraph sync`

原因[^src-006]:

> 分支切换后刷新 codegraph 索引(daemon 的文件 watcher 在常规写操作时已能跟上,但 branch 切换 / detached HEAD 可能让 watcher 状态错位,显式 sync 一次最稳)。

### post-merge 还跑 deps install

```yaml
post-merge:
  commands:
    deps:install:
      glob: "pnpm-lock.yaml"
      run: pnpm install --frozen-lockfile=false
```

`frozen-lockfile=false` 是允许 catalog 间接依赖被动更新,不锁死[^src-006]。

### pre-push 全工作区兜底

```yaml
pre-push:
  parallel: true
  commands:
    check:full:          { run: pnpm exec vp check }
    lint:react-admin:    { run: pnpm -C apps/react-admin lint }
    check:java-style:    { glob: "...", run: mvn -B -f backend/java-admin/pom.xml checkstyle:check }
    check:java-types:    { glob: "...", run: mvn -B -q ... -DskipTests compile }
```

`vp check` 在 pre-push 用的是**只读**模式(`--fix` 留给 pre-commit 的 `stage_fixed`),不会改 working tree[^src-006]。

### commit-msg 走 commitlint

```yaml
commit-msg:
  commands:
    commitlint:
      run: pnpm exec commitlint --edit "$1"
```

`$1` 是 commit-msg 钩子入参(commit message 临时文件路径)。子仓 vue-vben-admin 在子目录 commit 时用自己 `.commitlintrc.js`(@vben/commitlint-config),根用 `@commitlint/config-conventional`[^src-006]。

## 逃逸口

```bash
LEFTHOOK=0 git commit ...     # 关闭整个 lefthook
git commit --no-verify ...    # 跳过 commit-msg 钩子
git push --no-verify ...      # 跳过 pre-push 兜底
```

## 反例

如果用 husky:

- husky 的 hooks 是 shell 脚本,无法声明 `parallel: true` / `glob:`
- 复杂命令需要单独写 `.husky/pre-commit` 脚本,可读性差
- 与 Vite+ 的 `vp config` 概念重合,产生两套"装 hook"机制

lefthook 的 Go 单二进制 + YAML 是当前阶段最契合仓库复杂度的方案。

## 何时复审

- 子仓数量再增加且每个都要独立 lint 链路 → 考虑分多个 lefthook 文件,现在还不需要
- lefthook 上游 API 大改 → 评估 `vp config` 是否已经覆盖了 YAML 的功能

## 引用

[^src-006]: `lefthook.yml`
[^src-012]: `apps/vue-vben-admin/lefthook.yml`

## 相关

- [decisions/workspace-exclusions.md](workspace-exclusions.md) —— 解释了为什么 react-admin 没有自己 lefthook 而 vue-vben-admin 有
- [modules/vue-vben-admin.md](../modules/vue-vben-admin.md)
- [modules/react-admin.md](../modules/react-admin.md)