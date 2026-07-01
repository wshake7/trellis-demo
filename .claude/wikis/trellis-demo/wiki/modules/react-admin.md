# modules/react-admin

外部集成的 React 后台(基于 `ant-design-pro`),同样**不在**根 workspace 里。

> **指针页**——本文档只描述它"在本仓库里是怎么集成的"。完整产品文档请去上游 `tx7do/go-wind-admin` 或 `ant-design-pro` 文档。

## 一句话定义

React 19 + Ant Design Pro 6 + Vite 8 的现代 React 后台模板,以子目录方式嵌在 `apps/react-admin`,**带自己的 `pnpm-workspace.yaml` 但没有独立的 lefthook**,靠根 lefthook 的 `lint:react-admin` / `typecheck:react-admin` 兜底[^src-014] [^src-015] [^src-006]。

## 在本仓库的集成方式

### 1. 主动排除出根 workspace

`pnpm-workspace.yaml` 用 `!apps/react-admin` + `!apps/react-admin/**` 摘出[^src-003]。

### 2. 根 lint/fmt 跳过

根 `vite.config.ts` 的 staged / fmt / lint 都过滤 `apps/react-admin/**`[^src-004]。

### 3. **但**根 lefthook 仍然管它

这是 react-admin 与 vue-vben-admin 的关键区别。`apps/vue-vben-admin` 有自己的 lefthook,根不插手;**`apps/react-admin` 没有自己的 lefthook,完全靠根 lefthook 的两个专用 command**[^src-006]:

```yaml
# lefthook.yml
lint:react-admin:
  glob: "apps/react-admin/**/*.{ts,tsx}"
  run: sh -c 'pnpm -C apps/react-admin exec eslint $(echo "$LEFTHOOK_STAGED_FILES" | tr " " "\n" | sed "s|^apps/react-admin/||")'
typecheck:react-admin:
  glob: "apps/react-admin/**/*.{ts,tsx}"
  run: pnpm -C apps/react-admin typecheck
```

shell 包装层做了件事:把 `$LEFTHOOK_STAGED_FILES` 里 `apps/react-admin/` 前缀剥掉再传给子仓 eslint(`pnpm -C` 会 cd 进去,相对路径才对)。

pre-push 还多一道兜底 `lint:react-admin`(无 glob 限定,全量 eslint)[^src-006]。

### 4. install

与 vue-vben-admin 不同,**`Makefile#install` 不会单独 cd 进 react-admin** 跑 install[^src-007] —— 它的依赖图假设和根 workspace 兼容,但因为已被排除,需要手工执行(或者自己加 Makefile target)。

## 栈细节

`package.json` 关键字段[^src-014]:

- 依赖:`react@^19.2.6` / `react-dom@^19.2.6` / `antd@^6.4.3` / `@ant-design/pro-components@^2.8.10` / `@tiptap/*`(富文本栈)/ `echarts@^6.1.0` / `monaco-editor` / `marked` / `md-editor-rt` / `zustand@^5`
- 开发:`typescript@~6.0.3` / `vite@^8.0.13` / `vitest@^4.1.6` / `unocss@66.5.12` / `eslint@^10.4.0` + `typescript-eslint@^8.59.4`
- `devEngines.packageManager` 强制 `pnpm@11.8.0`,版本不匹配会自动 `onFail: download`

`vite.config.ts` 在 `apps/react-admin/vite.config.ts`,意味着 Vite 是 dev/build 的统一入口[^src-017]。`eslint.config.js` 是 flat config 形式[^src-016]。

## 关键脚本(子仓内 vs 根)

- 根 `pnpm dev:radmin` → `pnpm -C apps/react-admin dev`[^src-002] [^src-014]
- 子仓内还有 `build` / `build:check`(先 `tsc` 再 build)/ `typecheck` / `lint` / `preview`

## 何时修改它

- **改 react-admin 代码**:`cd apps/react-admin`,跑它自己的 `pnpm dev` / `pnpm lint` / `pnpm build:check`
- **改本仓库对 react-admin 的 hook 行为**:动根 `lefthook.yml` 的 `lint:react-admin` / `typecheck:react-admin` 块
- **给 react-admin 加新的门禁**:同样改根 `lefthook.yml`,**不要**新建 `apps/react-admin/lefthook.yml` 覆盖根

> ⚠️ react-admin 没有子仓 lefthook,所以根 lefthook 是它的**唯一**门禁。改根配置前先在 pre-push dry-run 验证。

## 引用

[^src-002]: `package.json`(根,`dev:radmin` 脚本)
[^src-003]: `pnpm-workspace.yaml`
[^src-004]: `vite.config.ts`
[^src-006]: `lefthook.yml`(`lint:react-admin` / `typecheck:react-admin`)
[^src-007]: `Makefile`
[^src-014]: `apps/react-admin/package.json`
[^src-015]: `apps/react-admin/pnpm-workspace.yaml`
[^src-016]: `apps/react-admin/eslint.config.js`
[^src-017]: `apps/react-admin/vite.config.ts`

## 相关

- [decisions/workspace-exclusions.md](../decisions/workspace-exclusions.md)
- [modules/vue-vben-admin](vue-vben-admin.md) —— 另一个被排除的子仓(对比它**有**自己的 lefthook)