# modules/vue-vben-admin

外部集成的成熟 Vue 后台 monorepo(VbenJS 出品),**不在**本仓库根 workspace 中。

> **指针页**——本文档只描述它"在本仓库里是怎么集成的"。完整产品文档请去上游 `vbenjs/vue-vben-admin`。

## 一句话定义

一个完整的 Vue 3 + Vite + Turbo monorepo,以 git submodule / 子目录方式嵌在 `apps/vue-vben-admin`,自带独立的 `pnpm-workspace.yaml`、catalog、lefthook 和 ESLint 配置[^src-010] [^src-011] [^src-012] [^src-013]。

## 在本仓库的集成方式

### 1. 主动排除出根 workspace

`pnpm-workspace.yaml` 用 `!apps/vue-vben-admin` + `!apps/vue-vben-admin/**` 把整个子树摘出根 workspace[^src-003]。注释说明:vue-vben-admin 自带 catalog,如果并入根 workspace,它的 `catalog:` 引用会被根的 catalog 劫持,版本错位。

### 2. 根 lint/fmt 跳过

根 `vite.config.ts` 的 `fmt.ignorePatterns` / `lint.ignorePatterns` / `staged` 函数都把 `apps/vue-vben-admin/**` 过滤掉[^src-004]。

### 3. 自己的 hook 配置

`apps/vue-vben-admin/lefthook.yml` 是子仓单独的 hook 配置(根 `lefthook.yml` 里也明示"vue-vben-admin 子模块自带 lefthook.yml,根 hook 不再覆盖其代码")[^src-006] [^src-012]。

### 4. 独立安装

`Makefile` 的 `install` target 显式 `cd apps/vue-vben-admin && vp i`[^src-007] —— 根的 `pnpm install` 不会帮它装。

## 关键脚本(子仓内)

根 `package.json` 暴露的脚本都 `-C` 到子目录执行[^src-002] [^src-010]:

- `pnpm dev:vadmin` → `pnpm -C apps/vue-vben-admin dev:naive` —— NaiveUI 风格
- `pnpm dev:vadmin2` → `pnpm -C apps/vue-vben-admin dev:antdv-next` —— Antdv next 风格
- 还有 `dev:docs` / `dev:play` / `build:naive` / `build:analyze` 等子仓内部脚本[^src-010]

## 子仓自己的栈

- **构建**:turbo + vite + vben/vite-config
- **代码质量**:eslint(`@vben/eslint-config`)+ oxlint + oxfmt + cspell + stylelint
- **类型**:typescript + vue-tsc
- **包管理**:pnpm 11.5.2,自维护 catalog
- **测试**:vitest(happy-dom) + playwright(e2e)
- **CHANGELOG**:changesets

详见 `apps/vue-vben-admin/package.json`[^src-010]。

## 何时修改它

- **升级子仓版本**:`cd apps/vue-vben-admin && git pull`(假设是 submodule)或 `cd apps/vue-vben-admin && pnpm update:deps`
- **改本仓库对它的封装**:`apps/` 之外的工作区都别动根 vite.config / lefthook / pnpm-workspace,那三个文件已经把"独立处理"的契约写死
- **改子仓内部的代码/配置**:进子仓目录,跑它自己的 `pnpm dev` / `pnpm lint` / `pnpm check`,不要在根跑

> ⚠️ AGENTS.md / 根 vite.config 的 staged 注释已明确:**不要把 vue-vben-admin 的文件路径交给根的 `vp check --fix`**。

## 引用

[^src-002]: `package.json`(根,`dev:vadmin*` 脚本)
[^src-003]: `pnpm-workspace.yaml`(排除规则)
[^src-004]: `vite.config.ts`(ignorePatterns)
[^src-006]: `lefthook.yml`(根,不覆盖子仓)
[^src-007]: `Makefile`(独立 install)
[^src-010]: `apps/vue-vben-admin/package.json`
[^src-011]: `apps/vue-vben-admin/pnpm-workspace.yaml`
[^src-012]: `apps/vue-vben-admin/lefthook.yml`
[^src-013]: `apps/vue-vben-admin/eslint.config.mjs`

## 相关

- [decisions/workspace-exclusions.md](../decisions/workspace-exclusions.md)
- [modules/react-admin](react-admin.md) —— 另一个被主动排除的子仓
- [structure.md](../structure.md)