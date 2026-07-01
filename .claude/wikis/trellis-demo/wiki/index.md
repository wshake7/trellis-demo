# Trellis Demo Codebase

> codebase wiki · created 2026-07-01

`trellis-demo` 是一个**用 Vite+ 搭的多栈 monorepo starter**,根 workspace 用 pnpm catalog 统一依赖;两个成熟产品(admin)以子仓方式嵌入并主动排除出根 workspace。本 wiki 收录其结构、约定和决策,供新人和 agent 快速入门。

<!-- BEGIN GENERATED INDEX -->

## 总览

- [stack.md](stack.md) — 技术栈(Vite+ / pnpm 11 / Nitro / Vue3 / React19 / Java)
- [getting-started.md](getting-started.md) — 从 clone 到 dev server 的最小路径
- [structure.md](structure.md) — 顶层目录约定 + workspace 排除规则

## Modules

- [modules/website-template.md](modules/website-template.md)
- [modules/backend-mock-template.md](modules/backend-mock-template.md)
- [modules/utils-template.md](modules/utils-template.md)
- [modules/vue-vben-admin.md](modules/vue-vben-admin.md) — 指针页
- [modules/react-admin.md](modules/react-admin.md) — 指针页

## Decisions

- [decisions/workspace-exclusions.md](decisions/workspace-exclusions.md) — 为什么两个 admin 子仓主动排除
- [decisions/single-lefthook-source.md](decisions/single-lefthook-source.md) — 为什么用单一 lefthook.yml 而不靠 Vite+ 注入

<!-- END GENERATED INDEX -->

## Open questions

1. `apps/website-template`、`apps/backend-mock-template` 的 `src/` / `api/` / `routes/` / `middleware/` 实际结构未扫,需要补全模块页内 TODO 段。
2. `packages/utils-template` 用 `@typescript/native-preview: 7.0.0-dev.20260509.2` 打包,生产发布前是否替换成 stable `typescript`?
3. `apps/react-admin` 的 install 没在 `Makefile` 里写,需要单独 target 还是手工?
4. `backend/db/` 子目录具体内容(迁移?SQL?schema?)未扫,需要补一份 `backend/db.md`。
5. `backend/java-admin/pom.xml` 的 Error Prone 配置细节与规则集未深扫。

## Sources

See [sources.md](../sources.md)。

## Maintenance log

See [logs/maintenance-log.md](../logs/maintenance-log.md)。