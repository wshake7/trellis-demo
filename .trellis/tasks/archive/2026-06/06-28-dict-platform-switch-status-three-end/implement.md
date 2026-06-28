# Implement: 三端字典 platform 与 switch_status 对齐 + hook 优化

## Review-gate contract: explicit-selection-v1

```
Review-gate contract: explicit-selection-v1
Optional review gates status: configured
Enabled optional review gates: trellis-spec-review, trellis-code-review
Disabled optional review gates: trellis-code-architecture-review, trellis-improve-codebase-architecture, trellis-merge-review

策略:
- 开发模式:subagent(轻量,改 3 个端 5 个文件,无需 worktree 隔离)
- 分支:master(单任务小改动,不另开分支)
- 流程:default(非 TDD)
- 架构审查:disabled
```

> 注:任务涉及三端代码同步,`trellis-spec-review` 用于核对 prd.md / design.md / implement.md 一致性,`trellis-code-review` 用于审实现正确性。其余三个 review 闸门与本期改动的范围不匹配(架构重构 / 深度重构 / merge),故关闭。

## 实施清单

### Step 1: 修改 mock-data.ts 字典种子

**文件**:`apps/backend-mock-template/utils/mock-data.ts`

**改动**:
1. `buildDictTypeSeeds()` 第 5 条:把 `sys_common_status` (id=5) 改为 `sys_switch_status` (id=5),name = `开关状态`
2. `buildDictDataSeeds()`:删除 1041/1042 旧条目,新增 6 条 sys_switch_status 项(沿用 contract 表)

**验收**:mock 启动后 `GET /api/system/dict-type/all` 包含 `sys_switch_status`,无 `sys_common_status`

### Step 2: 引入 @tanstack/vue-query 依赖

**文件**:`apps/vue-vben-admin/apps/web-naive/package.json`

**改动**:`pnpm --filter @vben/web-naive add @tanstack/vue-query`(或在 pnpm-workspace.yaml 里同步)

**验收**:`@tanstack/vue-query` 出现在 dependencies;`pnpm install` 成功

### Step 3: 全局挂载 VueQueryPlugin

**文件**:`apps/vue-vben-admin/apps/web-naive/src/bootstrap.ts`

注:`apps/web-naive/src/main.ts` 只是入口壳(异步调用 bootstrap()),真正的 app init 在 `bootstrap.ts` 内。`app.use(VueQueryPlugin, ...)` 必须在 `createApp(App)` 之后、`app.use(router)` 之前挂载。

**改动**:导入 `VueQueryPlugin` 和 `QueryClient`,创建 `QueryClient`,在 `bootstrap()` 中 `app.use(VueQueryPlugin, { queryClient })`

**验收**:`pnpm --filter @vben/web-naive typecheck` 通过;`pnpm --filter @vben/web-naive build` 通过

### Step 4: 新增 vue-vben dict hooks.ts

**新增文件**:`apps/vue-vben-admin/apps/web-naive/src/api/system/dict/hooks.ts`

**改动**:导出 `useListDictType` / `useListDictData` / `useCreateDictData` / `useUpdateDictData` / `useDeleteDictData`,`useListDictData` 自动注入 platform(与 react-admin 同语义)

**验收**:`pnpm --filter @vben/web-naive typecheck` 通过

### Step 5: 验证 mock / 端到端

**命令**:
```bash
# 启动 mock
pnpm --filter @backend-mock-template dev &
sleep 5

# 验证字典类型
curl -s 'http://localhost:3005/api/system/dict-type/all' | jq '.data[] | select(.code=="sys_switch_status")'

# 验证字典项
curl -s 'http://localhost:3005/api/system/dict-data/list?typeCode=sys_switch_status&platform=vue-admin&includeGeneral=true' | jq '.data.items | length'
# 期望: 4

curl -s 'http://localhost:3005/api/system/dict-data/list?typeCode=sys_switch_status&platform=react-admin' | jq '.data.items | length'
# 期望: 2

curl -s 'http://localhost:3005/api/system/dict-data/list?typeCode=sys_switch_status&platform=general' | jq '.data.items | length'
# 期望: 2
```

**验收**:以上 curl 输出与期望一致

### Step 6: 验证 react-admin / vue-vben 字典管理页面

**步骤**:
1. 启动 react-admin: `pnpm --filter @react-admin dev`
2. 启动 vue-vben: `pnpm --filter @vben/web-naive dev`
3. 在 `VITE_APP_PLATFORM=react-admin` / `VITE_APP_PLATFORM=vue-admin` / `VITE_APP_PLATFORM=general` 三种模式下访问字典管理页面
4. 切换「包含通用」勾选,验证列表条数

**验收**:行为与 design.md R5/R6 一致

## 验证命令

```bash
# 1. 类型检查
pnpm --filter @backend-mock-template typecheck
pnpm --filter @react-admin typecheck
pnpm --filter @vben/web-naive typecheck

# 2. lint
pnpm --filter @backend-mock-template lint
pnpm --filter @react-admin lint
pnpm --filter @vben/web-naive lint

# 3. unit test(若有)
pnpm --filter @backend-mock-template test
pnpm --filter @react-admin test
pnpm --filter @vben/web-naive test

# 4. e2e(若有)
pnpm e2e
```

## 回滚点

- mock-data 改动失败 → 回滚 `apps/backend-mock-template/utils/mock-data.ts` 到 master HEAD
- vue-query 引入失败 → `pnpm --filter @vben/web-naive remove @tanstack/vue-query`,回滚 bootstrap.ts 与 hooks.ts
- 行为不一致 → 检查 SQL staged 是否在 mock 启动前被还原(`git status` 应有 `modified: backend/db/schema_data.sql`)

## 提交策略

- 单个 commit:`feat(dict): align sys_switch_status seed and add platform-aware vue hook`
- 包括:
  - `backend/db/schema_data.sql`(已 staged,确认与 mock 对齐)
  - `apps/backend-mock-template/utils/mock-data.ts`
  - `apps/vue-vben-admin/apps/web-naive/package.json`
  - `apps/vue-vben-admin/apps/web-naive/src/bootstrap.ts`
  - `apps/vue-vben-admin/apps/web-naive/src/api/system/dict/hooks.ts`

## 后续 follow-up

- [ ] 后端 java-admin SQL 同步(本期 out of scope)
- [ ] vue-vben index.vue 切换到 hook 调用(本期保留现状,后续迭代替换)
- [ ] react-admin `useDictCache` 实际加载逻辑补全(本期 out of scope)
- [ ] 字典项 i18n / 多语言字段(本期 out of scope)