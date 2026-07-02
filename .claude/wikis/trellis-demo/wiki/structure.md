# 目录结构

> 当你 `ls` 这个仓库根时,会看到顶层是 `apps/`、`packages/`、`backend/` 三个目录 + 几个零散配置。这是**有意的**目录切分:根 workspace 只管"模板/工具/共享包",两个真正的产品级 admin 在自己的子 workspace 里独立演化。

## 顶层一览

```
trellis-demo/
├── apps/                # 应用:web 前台 / admin / 后端 mock
├── packages/            # 共享库(TS 包)
├── backend/             # 重量级后端(目前 Java)
├── AGENTS.md            # 项目惯例(中文输出、smart-search、Vite+、CodeGraph、GitNexus)
├── CLAUDE.md            # 进入提示:先读 AGENTS.md
├── Makefile             # 工具链 bootstrap(vp / codegraph;context7 通过 smart-search 子命令)
├── package.json         # workspace 根脚本(ready / dev:*)
├── pnpm-workspace.yaml  # workspace 边界 + catalog
├── vite.config.ts       # 根 staged/fmt/lint/run 任务
├── tsconfig.json        # noEmit + nodenext
├── lefthook.yml         # 单一 git-hooks 配置(覆盖根 + react-admin 兜底)
└── pnpm-lock.yaml
```

## apps/

| 路径 | 角色 | 是否在根 workspace |
|------|------|--------------------|
| `apps/website-template` | Vue 3 + Vite+ 网站模板 | ✓ |
| `apps/backend-mock-template` | Nitro mock 后端(端口 4000) | ✓ |
| `apps/vue-vben-admin` | 自包含 monorepo(Vue3 + Antdv-next) | ✗ **主动排除** |
| `apps/react-admin` | 自包含 monorepo(React 19 + Ant Design Pro 6) | ✗ **主动排除** |

排除规则在 `pnpm-workspace.yaml` 里写得很直白[^src-003]:

```yaml
- "!apps/vue-vben-admin"
- "!apps/vue-vben-admin/**"
- "!apps/react-admin"
- "!apps/react-admin/**"
```

> 注释里写了原因:vue-vben-admin 是自包含 monorepo,带自己的 `pnpm-workspace.yaml` 和 catalog;如果不排除,根 workspace 的 `catalog:` 引用会被它劫持。详见 [decisions/workspace-exclusions.md](decisions/workspace-exclusions.md)。

## packages/

- `packages/utils-template` —— 唯一已存在的共享 TS 包,exports `./dist/index.mjs`,`vp pack` 出 ESM[^src-021]

## backend/

- `backend/java-admin` —— Maven 项目,详见 [modules 待扩展] / `backend/java-admin/pom.xml`[^src-022]
- `backend/db` —— 数据库相关资产(由 Java 后端使用)

## 排除规则带来的开发约定

**根 lint / fmt / staged 不会扫两个 admin 子仓**[^src-004];但 pre-push 仍然会跑全工作区检查,所以子仓自带的两套 lefthook 必须独立维护[^src-006]:

| 触发 | 根钩子行为 | 子仓兜底 |
|------|-----------|----------|
| 暂存 `apps/vue-vben-admin/**` | `vp staged` 过滤掉,根 lint 不跑 | 由子仓 `apps/vue-vben-admin/lefthook.yml` 处理[^src-012] |
| 暂存 `apps/react-admin/**.{ts,tsx}` | `vp staged` 过滤掉;根 lefthook 额外跑 `lint:react-admin` + `typecheck:react-admin` | (子仓无独立 lefthook,完全靠根兜底)[^src-006] |
| pre-push | `vp check`(只覆盖根 workspace)+ `lint:react-admin` | 推 push 时 react-admin 也跑根钩子,无需额外动作[^src-006] |

## 引用

[^src-002]: `package.json`(根)
[^src-003]: `pnpm-workspace.yaml`
[^src-004]: `vite.config.ts`
[^src-006]: `lefthook.yml`
[^src-021]: `packages/utils-template/package.json`
[^src-022]: `backend/java-admin/pom.xml`