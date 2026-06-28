# Design: 三端字典 platform 与 switch_status 对齐 + hook 优化

## Architecture & Boundaries

### 涉及代码层

| 层 | 路径 | 职责 |
| --- | --- | --- |
| SQL seed | `backend/db/schema_data.sql` | 字典类型 + 字典项种子;**已 staged** |
| Mock 数据 | `apps/backend-mock-template/utils/mock-data.ts` | `buildDictTypeSeeds()` / `buildDictDataSeeds()` |
| Mock 接口 | `apps/backend-mock-template/api/system/dict-data/list.ts` | `?platform=` / `?includeGeneral=` 过滤 |
| React hook | `apps/react-admin/src/api/hooks/dict.ts` | `useListDictData` 自动注入 platform |
| React 页面 | `apps/react-admin/src/pages/app/system/dict/index.tsx` | platform / includeGeneral 搜索项 |
| Vue api | `apps/vue-vben-admin/apps/web-naive/src/api/system/dict/index.ts` | request 函数 |
| Vue hook(新增) | `apps/vue-vben-admin/apps/web-naive/src/api/system/dict/hooks.ts` | vue-query 风格 hook,自动注入 platform |
| Vue 入口 | `apps/vue-vben-admin/apps/web-naive/src/bootstrap.ts` | 全局挂载 `VueQueryPlugin` + QueryClient(main.ts 是异步壳,真实 app init 在 bootstrap.ts) |
| Vue 视图 | `apps/vue-vben-admin/apps/web-naive/src/views/system/dict/index.vue` | 保持现状,可选切换到 hook |

### 边界原则

- SQL seed 是**事实之源**(`sys_switch_status` + 4 组平台维度);mock seed 必须严格对齐 SQL,否则 mock 行为与 java-admin 不一致
- mock list 接口逻辑不动;只确认 platform/includeGeneral 行为对 SQL 新种子仍正确
- react-admin hook 已存在并自动注入 platform,本期**只验证**
- vue-vben 新增 hook 与 react-admin 同语义:不调用方传 platform 时,用 `VITE_APP_PLATFORM || 'general'` 作默认值;queryKey 用 merged 查询

## Data Flow

### 字典项 list 请求链路

```
[前端管理页面] → [hook/api] → [GET /system/dict-data/list?platform=&includeGeneral=]
  → [mock list.ts] → [mock-data.ts DictData[] filter] → [pageResponse(items, total)]
  → [hook/api] → [页面 state.items]
```

### platform 注入链路

```
VITE_APP_PLATFORM env
  ├─ react-admin: hooks/dict.ts CURRENT_PLATFORM 常量 → merged.queryKey/queryFn
  └─ vue-vben(新增): hooks.ts DEFAULT_PLATFORM 常量 → merged.queryKey/queryFn
```

### sys_switch_status seed 注入链路

```
schema_data.sql (staged) ─┐
                          ├─ 共同约束:`sys_switch_status` 类型 + 4 组字典项
mock-data.ts (待改) ──────┘
```

## Contracts

### sys_switch_status 字典类型契约

| 字段 | 值 |
| --- | --- |
| type_code | `sys_switch_status` |
| type_name | `开关状态` |
| type_id | 5(继承原 `sys_common_status` 的 id,避免 typeId 引用错位) |

### sys_switch_status 字典项契约(4 组 × 2 条 = 6 条)

| id | value | label | sort | is_default | platform | tag_type | is_enabled |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1041 | enabled | 启用 | 1 | 0 | general | '' | 1 |
| 1042 | disabled | 禁用 | 2 | 1 | general | '' | 1 |
| 1051 | enabled | 启用 | 3 | 0 | react-admin | success | 1 |
| 1052 | disabled | 禁用 | 4 | 1 | react-admin | error | 1 |
| 1061 | enabled | 启用 | 5 | 0 | vue-admin | success | 1 |
| 1062 | disabled | 禁用 | 6 | 1 | vue-admin | error | 1 |

注:mock seed 中 id 可与 SQL 不必完全相同(自增),但 platform/value/label/sort/tag_type/is_default 必须严格匹配 SQL。

### sys_platform 字典契约

| id | value | label | sort | is_default | platform | tag_type | is_enabled |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2001 | general | 通用 | 1 | 1 | general | '' | 1 |
| 2002 | react-admin | React Admin | 2 | 0 | react-admin | '' | 1 |
| 2003 | vue-admin | Vue Admin | 3 | 0 | vue-admin | '' | 1 |

注:与 SQL staged 改动一致,`tag_type` 全部置空(原 default)。

### vue-vben hook 接口契约

```ts
// apps/vue-vben-admin/apps/web-naive/src/api/system/dict/hooks.ts
import type { UseQueryReturnType, UseMutationReturnType } from '@tanstack/vue-query';

export function useListDictData(
  query: MaybeRefOrGetter<DictDataQuery> = {},
  options?: { platform?: string }
): UseQueryReturnType<PageResult<DictData>, Error>;

export function useListDictType(
  query: MaybeRefOrGetter<DictTypeQuery> = {},
  options?: { platform?: string }
): UseQueryReturnType<PageResult<DictType>, Error>;

export function useCreateDictData(): UseMutationReturnType<...>;
export function useUpdateDictData(): UseMutationReturnType<...>;
export function useDeleteDictData(): UseMutationReturnType<...>;
```

- `query.platform` 优先级 > `options.platform` > `DEFAULT_PLATFORM`(env fallback)
- queryKey = `['dict', 'listDictData', mergedQuery]`

### VueQueryPlugin 全局配置

```ts
// bootstrap.ts(节选)
import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 0,
      staleTime: 30_000,
    },
  },
});

// 在 bootstrap() 中:app.use(VueQueryPlugin, { queryClient });
// 必须在 createApp(App) 之后、app.use(router) 之前
```

## Compatibility & Migration Notes

- **type_id 兼容**:`sys_switch_status` 沿用 id=5,所有现存 typeId 引用保持有效
- **种子变更**:`sys_common_status`(id=5) → `sys_switch_status`;`1041 正常 / 1042 停用` → `1041 enabled / 1042 disabled`(value/label 都变)。如果存在任何业务代码按 value="0"/"1" 引用「通用状态」,需要适配(仓库内搜索 `sys_common_status` 无结果)
- **SQL 已 staged**:落地时无需 `git add`(已 add),但其它 mock 改动需要
- **vue-query 新依赖**:vue-vben 当前 package.json 不含 `@tanstack/vue-query`,需 `pnpm add`;由于 vue-vben 是 monorepo,需要确认 pnpm filter 命令(`pnpm --filter @vben/web-naive add @tanstack/vue-query`)
- **react-admin hook 不动**:已对齐,本期只验证

## Trade-offs

### 选 vue-query 全局挂载 vs 模块级

- **全局挂载**:后续其他模块可以复用,代价是新增 1 个全局插件和 1 个 QueryClient 单例(本期用默认值);后续迭代新页面无需再决定是否用 vue-query
- **模块级**:仅 dict 模块用 vue-query,需要单独创建 QueryClient,跨模块复用困难

### 选 sys_switch_status(value=enabled/disabled) vs value=0/1

- **enabled/disabled**:语义化,与「启用/禁用」文案对齐;SQL 已落地此方案;CellTag 渲染依赖 value 字符串
- **0/1**:沿用原 sys_common_status 的编码;数据库列无 enum 约束,改起来成本低;但与文案不一致,搜索时不直观

选择 **enabled/disabled**(SQL 已 staged,无需回退)。

### 选 sort 单调递增 vs 按 platform 分组

- **单调递增**(general 1/2 → react 3/4 → vue 5/6):跨平台合并展示时 sort 自然排;4 组在 list 中不混在一起仍按 sort 升序
- **按 platform 分组**:每组 sort 都从 1 起;跨平台合并时 general 始终在前面(可预测)

选择 **单调递增**(对齐 SQL staged);跨平台合并时同 sort 区间内,id 作为 tie-breaker。

## Rollback Considerations

- SQL 改动已 staged,若需回滚:`git restore --staged backend/db/schema_data.sql && git checkout -- backend/db/schema_data.sql`
- mock-data 改动:回滚 `apps/backend-mock-template/utils/mock-data.ts` 的 `buildDictTypeSeeds` / `buildDictDataSeeds` 函数
- vue-query 依赖:`pnpm --filter @vben/web-naive remove @tanstack/vue-query`
- vue-query 全局挂载:从 `apps/web-naive/src/bootstrap.ts` 移除 `app.use(VueQueryPlugin, ...)` 和 QueryClient 单例
- 新增 hook 文件:删除 `apps/vue-vben-admin/apps/web-naive/src/api/system/dict/hooks.ts`

## Operational Notes

- mock 启动后,直接 `curl http://localhost:3005/api/system/dict-type/all`(mock 默认端口 3005;4000 被占用时自动迁移)应包含 `sys_switch_status`
- `curl http://localhost:3005/api/system/dict-data/list?typeCode=sys_switch_status&platform=vue-admin&includeGeneral=true` 应返回 4 条
- `curl http://localhost:3005/api/system/dict-data/list?typeCode=sys_switch_status&platform=react-admin` 应返回 2 条(无 general)