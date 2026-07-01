# 起步

把仓库从 clone 状态带到可开发环境的最小路径。

## 一、装工具链

`Makefile` 把 `vp` (Vite+) 和 `codegraph` 两个外部依赖抽成一个幂等 bootstrap。`context7` 不再单独装,它由 `smart-search` 的 `context7-library` / `context7-docs` 子命令提供[^src-007]:

```bash
make install   # 别名:make i
```

实际效果[^src-007]:

1. `check-vp` —— 若 `vp` 缺失则按 OS 装(macOS/Linux 走 `curl -fsSL https://vite.plus | bash`,Windows 走 PowerShell)。
2. `cd apps/vue-vben-admin && vp i` —— vue-vben-admin 自带独立 workspace,根 `pnpm install` 不会帮它装。
3. `init` —— 跑 `check-vp` + `check-codegraph` + `codegraph init`(已索引则跳过)。`check-codegraph` 不在 `install` 默认流程里,只在显式 `make init` 时跑。

## 二、装依赖

```bash
vp install          # 根 workspace(覆盖 website-template、backend-mock-template、utils-template)
# vue-vben-admin 自带 catalog,需要独立装一次:
cd apps/vue-vben-admin && vp install
```

> 提醒:pnpm 11+ 默认拒绝跑 install scripts。本仓库 catalog 已声明 `allowBuilds`,允许 `@parcel/watcher`、`@swc/core`、`esbuild`、`json-editor-vue`、`lefthook`、`vue-demi` 等执行 `postinstall`[^src-003]。

## 三、起开发服务

`package.json` 暴露了 4 个开发入口[^src-002]:

| 脚本 | 作用 | 命令实际展开 |
|------|------|--------------|
| `pnpm dev:mock` | 起后端 mock | `vp run backend-mock-template#start` → Nitro `--port 4000`[^src-019] |
| `pnpm dev:vadmin` | vue-vben-admin NaiveUI 风格 | `pnpm -C apps/vue-vben-admin dev:naive`[^src-010] |
| `pnpm dev:vadmin2` | vue-vben-admin Antdv next 风格 | `pnpm -C apps/vue-vben-admin dev:antdv-next`[^src-010] |
| `pnpm dev:radmin` | react-admin | `pnpm -C apps/react-admin dev`[^src-014] |

## 四、收尾校验

```bash
pnpm ready          # = vp check && vp run -r test && vp run -r build
```

`ready` 是仓库根 CI 等价的入口[^src-002]:

- `vp check` —— 全工作区 fmt + lint + 类型检查[^src-004]
- `vp run -r test` —— 全 workspace 跑测试
- `vp run -r build` —— 全 workspace 打包

> 例外:react-admin 已**主动排除**出根 workspace[^src-003],所以根的 `vp run -r` 不会扫到它,需要单独 `pnpm -C apps/react-admin lint` / `typecheck` / `build`[^src-014]。

## 五、逃逸口

lefthook 钩子万一误伤,有三个口子[^src-006]:

```bash
LEFTHOOK=0 git commit ...     # 关闭整个 lefthook
git commit --no-verify ...    # 跳过 commit-msg 钩子
git push --no-verify ...      # 跳过 pre-push 兜底
```

## 引用

[^src-002]: `package.json`(根)
[^src-003]: `pnpm-workspace.yaml`
[^src-004]: `vite.config.ts`
[^src-007]: `Makefile`
[^src-010]: `apps/vue-vben-admin/package.json`
[^src-014]: `apps/react-admin/package.json`
[^src-019]: `apps/backend-mock-template/package.json`