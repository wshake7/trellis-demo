# 技术栈

`trellis-demo` 是一个用 **Vite+** 搭起来的多栈 monorepo 模板,根 workspace 用 pnpm catalog 统一约束依赖版本,各子应用可以保持各自独立的栈。

## 顶层工具链

| 维度 | 选择 | 出处 |
|------|------|------|
| 构建 / 任务编排 | Vite+ (`vp`):Vite + Rolldown + Vitest + tsdown + Oxlint + Oxfmt + Vite Task | [^src-008] |
| 包管理 | pnpm 11.6.0,catalog mode `prefer` | [^src-002] [^src-003] |
| Node | ≥ 22.12.0(子仓 vue-vben-admin 要 `^22.18.0 \|\| ^24.0.0`) | [^src-002] [^src-010] |
| TS | ~6.0(子仓 react-admin 用 TS ~6.0.3) | [^src-018] [^src-014] [^src-005] |
| Git 钩子 | lefthook 2.1.9(根 + 子仓各一份) | [^src-006] [^src-012] |
| Commit 规范 | Conventional Commits(@commitlint/config-conventional) | [^src-002] [^src-006] |
| Secret 扫描 | gitleaks(可选,缺失则 skip) | [^src-006] |

## 根 vite.config.ts 的核心 task

`vite.config.ts` 把 Vite+ 当作"统一管线"用,几个 task 的关键点[^src-004]:

- `staged` —— lint-staged 的函数式任务,**主动过滤掉** `apps/vue-vben-admin/` 和 `apps/react-admin/` 下所有文件,再交给根的 `vp check --fix`。两个子仓自带 lint/format,根 hook 不应越权。空列表时返回 `[]` 防止 `ignorePatterns` 把入参静默吞掉。
- `fmt` / `lint` —— `ignorePatterns` 同样把两个子仓摘掉;`lint.jsPlugins` 走 `vite-plus/oxlint-plugin`;`rules` 启用 `vite-plus/prefer-vite-plus-imports`(`error` 级);`options.typeAware` + `typeCheck: true` 表示 lint 会跑类型感知检查。
- `run.cache: true` —— `vp run` 启用缓存。

## 子栈矩阵

| 子仓 | 栈 | 端口 / 关键依赖 | 出处 |
|------|----|----------------|------|
| `apps/website-template` | Vue 3 + Vite+(`vp dev`) | 默认 Vite 端口 | [^src-018] |
| `apps/backend-mock-template` | Nitro(nitropack),固定 dev 端口 4000 | jsonwebtoken + @faker-js/faker | [^src-019] [^src-020] |
| `apps/vue-vben-admin` | Vue 3 + Vben(NaiveUI / Antdv next),自带 turbo + lefthook | monorepo,有内部 catalog | [^src-010] [^src-011] [^src-012] |
| `apps/react-admin` | React 19 + Ant Design Pro 6 + Vite 8 | 自带 pnpm workspace | [^src-014] [^src-015] [^src-017] |
| `packages/utils-template` | TS 包(`vp pack` 产 ESM),用 `@typescript/native-preview` | tsdown 出 `dist/index.mjs` | [^src-021] |
| `backend/java-admin` | Maven,palantir-java-format + checkstyle + Error Prone 2.50 | 见 pom.xml | [^src-022] |

> SPECULATION:`@typescript/native-preview` 7.0.0-dev 是 TS 6 原生编译预览,版本号非稳定;生产前需替换回 `typescript`。

## 与本仓库深度集成的工具(本机已配置)

- **CodeGraph** —— `.codegraph/` 不存在,AGENTS.md 提示需要时跑 `codegraph init`[^src-007] [^src-008]
- **GitNexus** —— 已索引 `trellis-demo`(11446 symbols / 19880 relationships / 212 flows),改代码前必须跑 `impact`,commit 前跑 `detect_changes`[^src-008]
- **smart-search** —— 默认 web 搜索入口;`smart-search doctor --format json` 自检。库/SDK/API 文档查询走其 `context7-library` / `context7-docs` 子命令(context7 不再作为独立 CLI 安装)[^src-008]

## 引用

[^src-002]: `package.json`(根)
[^src-003]: `pnpm-workspace.yaml`
[^src-004]: `vite.config.ts`
[^src-005]: `tsconfig.json`
[^src-006]: `lefthook.yml`
[^src-007]: `Makefile`
[^src-008]: `AGENTS.md`
[^src-010]: `apps/vue-vben-admin/package.json`
[^src-011]: `apps/vue-vben-admin/pnpm-workspace.yaml`
[^src-012]: `apps/vue-vben-admin/lefthook.yml`
[^src-014]: `apps/react-admin/package.json`
[^src-015]: `apps/react-admin/pnpm-workspace.yaml`
[^src-017]: `apps/react-admin/vite.config.ts`
[^src-018]: `apps/website-template/package.json`
[^src-019]: `apps/backend-mock-template/package.json`
[^src-020]: `apps/backend-mock-template/nitro.config.ts`
[^src-021]: `packages/utils-template/package.json`
[^src-022]: `backend/java-admin/pom.xml`