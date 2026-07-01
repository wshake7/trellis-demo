# decisions/workspace-exclusions

为什么 `apps/vue-vben-admin` 和 `apps/react-admin` 主动排除出根 workspace?

## 状态

**Stable** —— 已落地,在 `pnpm-workspace.yaml` 用负向 glob 显式排除。

## 决策内容

`pnpm-workspace.yaml` 显式列出四条负向 glob[^src-003]:

```yaml
packages:
  - apps/*
  - "!apps/vue-vben-admin"
  - "!apps/vue-vben-admin/**"
  - "!apps/react-admin"
  - "!apps/react-admin/**"
  - packages/*
  - tools/*
```

## 为什么

文件顶部注释写了核心原因[^src-003]:

> vue-vben-admin is a self-contained monorepo with its own `pnpm-workspace.yaml` and `pnpm-lock.yaml`; exclude it (and its subtree) from the root workspace so its `catalog:` references resolve against its own catalog instead of the root's.

翻译过来:

1. **catalog 引用会冲突**。pnpm 的 `catalog:` 协议只能解析到"最近的 workspace"。如果 vue-vben-admin 在根 workspace 里,它 `package.json` 里的 `vite: catalog:` 会被解析成根 catalog 里的 `vite: "@voidzero-dev/vite-plus-core@latest"`,而不是子仓自己的 catalog 里钉死的版本。两个 catalog 版本不同时,装出来的依赖图错位。
2. **lockfile 会冲突**。两个 monorepo 各自维护 `pnpm-lock.yaml`。如果并到根 workspace,根的 lockfile 会被污染,且子仓的 `pnpm-lock.yaml` 失去意义。
3. **hooks / lint 链路不一致**。子仓的 ESLint config 与根 vite.config 的 Oxlint 规则不同(子仓走 `@vben/eslint-config`,根走 `vite-plus/oxlint-plugin`);混跑会产生相互矛盾的修复。

## 后果 / 约束

### 根 lint / fmt 不再扫两个子仓

`vite.config.ts` 在 staged / fmt / lint 三个 task 里都用 `ignorePatterns` 把 `apps/vue-vben-admin/**` 和 `apps/react-admin/**` 摘掉[^src-004]:

```ts
fmt: { ignorePatterns: ["apps/vue-vben-admin/**", "apps/vue-vben-admin", "apps/react-admin/**", "apps/react-admin", ...] },
lint: { ignorePatterns: [...同上...], jsPlugins: [{name: "vite-plus", specifier: "vite-plus/oxlint-plugin"}], rules: { "vite-plus/prefer-vite-plus-imports": "error" }, options: { typeAware: true, typeCheck: true } },
```

`staged` 任务更进一步——用函数式任务 `**/*`,先 `filter` 掉两个子目录路径,再交给根 `vp check --fix`;若过滤后为空数组,直接返回 `[]`,**不**退化成无操作(否则 `ignorePatterns` 会静默吞掉所有入参导致 lint 假阳性失败)[^src-004]。

### install 分两次

`Makefile#install` 显式 `cd apps/vue-vben-admin && vp i`,因为根的 `pnpm install` 不会管被排除的子仓[^src-007]。`apps/react-admin` 的 install 没在 Makefile 里写,需要手工或单独加 target。

### 钩子策略不一样

- `apps/vue-vben-admin` 自带 `lefthook.yml`,根 hook 不覆盖[^src-006] [^src-012]
- `apps/react-admin` **没有**独立 lefthook,完全靠根 `lefthook.yml` 的 `lint:react-admin` + `typecheck:react-admin` 兜底[^src-006]

详见 [modules/vue-vben-admin.md](../modules/vue-vben-admin.md) 和 [modules/react-admin.md](../modules/react-admin.md)。

## 反例(为什么不能反过来)

如果把子仓并入根 workspace,而根 catalog 子集化以兼容子仓:

- 根 catalog 会被迫与子仓的 catalog 对齐,失去灵活性(根 catalog 同时服务多个模板应用)
- 子仓的 `@vben/eslint-config` 等 workspace 协议依赖会全部断掉,需要把所有 `@vben/*` 改成具体版本
- pre-commit 时 `vp check` 会扫子仓,跑子仓没装好的 oxlint/vben lint

维护成本远高于现在的"两个独立 workspace"。

## 何时复审

- 子仓里出现与根 vite.config 冲突的 lint 规则 → 考虑换成 "子仓优先",但当前没冲突
- 子仓升版引入新 catalog 项 → 同步两边 catalog,不需要改这个决策

## 引用

[^src-003]: `pnpm-workspace.yaml`
[^src-004]: `vite.config.ts`
[^src-006]: `lefthook.yml`
[^src-007]: `Makefile`
[^src-012]: `apps/vue-vben-admin/lefthook.yml`

## 相关

- [modules/vue-vben-admin.md](../modules/vue-vben-admin.md)
- [modules/react-admin.md](../modules/react-admin.md)
- [structure.md](../structure.md)