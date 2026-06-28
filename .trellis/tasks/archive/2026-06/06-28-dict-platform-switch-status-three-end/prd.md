# 三端字典 platform 与 switch_status 对齐 + hook 优化

## Goal

让 backend mock / vue-vben-admin / react-admin 三端的字典管理在「归属平台」和「开关状态」这两个新字典数据上保持一致:

- 数据库 seed(`backend/db/schema_data.sql`)按 platform 维度拆出 4 组开关状态字典项。
- backend mock 的 seed 跟随 SQL 改造,改用 `sys_switch_status` 字典类型,并按 platform 注入字典项。
- vue-vben / react-admin 的字典管理页面 + 列表请求能正确看到、按平台筛选这些字典项。
- 两端 dict 的 hook(react-admin 已有、vue-vben 新增)都支持 `platform` 参数透传,以便调用方按平台过滤。

## Background(已确认事实)

- `backend/db/schema_data.sql` 已 staged:把 `common_status` 字典类型改为 `sys_switch_status`,并按 `platform ∈ {general, react-admin, vue-admin}` 各注入 `enabled/disabled` 两条字典项;`sys_platform` 字典的 `tag_type` 全部置空。
- `apps/backend-mock-template/api/system/dict-data/list.ts` 已经支持 `?platform=` 与 `?includeGeneral=` 过滤(逻辑:精确匹配;`includeGeneral=true` 且 platform 非 general 时并入 general)。
- `apps/backend-mock-template/api/system/dict-data/list.ts` 接受 `?status=`(0/1)与其它字段。
- `apps/react-admin/src/api/hooks/dict.ts` 的 `useListDictData` / `fetchListDictEntries` 已经自动注入 `platform = VITE_APP_PLATFORM || 'general'`,但 dict 页面还没有引用任何 `sys_switch_status` 字典(目前既无引用)。
- `apps/react-admin/src/hooks/useDictCache.ts` 是一个轻量 no-op 缓存 hook,与本次需求无关,保留即可。
- `apps/vue-vben-admin/apps/web-naive/src/views/system/dict/index.vue` 已经在 `ajax.query` 里显式给 `fetchDictDataListApi` 传 `platform` + `includeGeneral`,**已经**能拿到按平台过滤的字典项;`useDataSearchSchema()` 与表单 drawer 都已支持 platform 字段。
- vue-vben 端没有「dict hook」层,只有 `api/system/dict/index.ts` 提供 request 函数;需求「优化两端 dict 的 hook 支持 platform」对应:react-admin 端检查已有 hook 是否覆盖全部场景,缺失则补全;vue-vben 端考虑新增 `useListDictData` / `useListDictType` 这类 vue-query 风格 hook(或在 request 层加平台注入,与 react-admin 保持同语义)。
- `apps/backend-mock-template/utils/mock-data.ts` 的 `buildDictTypeSeeds()` 仍写死 `sys_common_status` (id=5) / 「正常 / 停用」两条字典项,与 SQL 新约束不一致,需要跟着 SQL 改。
- 前端两端的字典管理页面都没有「字典搜索结果展示平台维度」的副作用 UI 改动;但需要保证:
  1. 平台搜索项切换时,list 接口确实带新值。
  2. dict-data 的 platform 维度默认显示。
  3. 创建/编辑字典项时 platform 字段仍受当前前端平台标识约束(vben 已经实现)。
- mock-data 的种子「系统通知 / 通知公告 / 通用状态」等字典类型 id 已固定;`sys_switch_status` 新字典应替换 id=5 的位置,避免前端 typeId 引用错位。

## Requirements

### R1. schema_data.sql 与 mock-data 字典种子对齐

- `backend/db/schema_data.sql` 已是 staged 状态,内容包含:
  - `dict_type.code = 'sys_switch_status'`,name = `开关状态`
  - platform 字典(`'sys_platform'`)的 3 条字典项,`tag_type` 全部置空(`''`)
  - `sys_switch_status` 下按 platform 4 组,每组 `enabled / disabled` 两条,排序递增
- `apps/backend-mock-template/utils/mock-data.ts` 同步调整:
  - 把 `sys_common_status` (id=5) 改为 `sys_switch_status`,name = `开关状态`
  - 在 `buildDictDataSeeds()` 中新增 4 组开关状态种子,platform 维度同 SQL,字段 `enabled`(`sort` 单调递增)、`disabled`(`is_default = 1`)
  - `sys_common_status` 旧条目(1041/1042 正常/停用)删除

### R2. mock list 接口对 sys_switch_status 的兼容性

- `apps/backend-mock-template/api/system/dict-data/list.ts` 维持现状,只需确认 `platform=general` + `includeGeneral=true` 时返回 4 组中的 general 2 条;不修改逻辑。

### R3. react-admin dict hook 与 dict 页面

- `apps/react-admin/src/api/hooks/dict.ts` 已支持 `platform` 自动注入,无需改造;但需要确认 queryKey 中 `platform` 字段存在性会导致缓存错位(目前 queryKey 用 `merged`,已 OK)。
- `apps/react-admin/src/pages/app/system/dict/index.tsx`:
  - 字典项搜索栏需要带 platform(目前页面是否走 hook 调用 `useListDictData`?)—— 需要在阅读页面源码后决定是否新增 platform 搜索项或与 mock 行为对齐。
- `apps/react-admin/src/pages/app/system/dict/index.tsx` 抽屉表单 platform 字段需与 `DEFAULT_PLATFORM` 对齐,行为与 vue-vben 一致。

### R4. vue-vben dict hook 优化

- 新增 `apps/vue-vben-admin/apps/web-naive/src/api/system/dict/hooks.ts`:
  - 引入 `@tanstack/vue-query` 作为依赖(vue-vben 当前未引入)
  - 导出 `useListDictType`, `useListDictData`, `useCreateDictData`, `useUpdateDictData`, `useDeleteDictData` 等 vue-query 风格 hook
  - `useListDictData` 自动注入 `platform = VITE_APP_PLATFORM || 'general'`,与 react-admin `api/hooks/dict.ts` 行为对齐
  - 支持调用方显式传入 `platform` 覆盖默认
  - queryKey 用最终 merged 查询,避免缓存命中失败
- 在 `apps/vue-vben-admin/apps/web-naive/src/bootstrap.ts`(实际 app init 入口,而非 main.ts 入口壳)中**全局挂载** `VueQueryPlugin` 并创建全局 `QueryClient`,后续其他模块可复用
- index.vue 的 ajax.query 可以保留原有显式传参(不强制改造调用方);新增 hook 作为另一条可选路径,可在后续迭代替换

### R5. 跨端一致性验收

- mock 启动后 `GET /api/system/dict-type/all` 返回 `sys_switch_status` 字典类型
- mock 启动后 `GET /api/system/dict-data/list?typeCode=sys_switch_status&platform=vue-admin&includeGeneral=true` 返回 4 条(general 启用/禁用 + vue-admin 启用/禁用)
- `?platform=react-admin` 不带 `includeGeneral` 时只返回 2 条 react-admin 项
- `?platform=general` 时只返回 general 2 条
- vue-vben / react-admin 在 `VITE_APP_PLATFORM=vue-admin` 时,字典管理页面只看到 vue-admin + general(若 includeGeneral 勾选);切换到 react-admin 平台同理。

### R6. 列表展示策略(平台间状态色)

- `sys_switch_status` 字典项的 tag_type 在列表渲染上按 platform 维度差异化呈现:
  - `general` 组 tag_type 为空,CellTag 渲染为纯文本(label)
  - `react-admin` / `vue-admin` 组 tag_type = `success` / `error`,CellTag 渲染为对应彩色 tag
- 两端字典管理页面 CellTag 实现已经在 `apps/react-admin/src/pages/app/system/dict/index.tsx`(r.tag_type && r.tag_type !== 'default' ? r.tag_type : undefined)和 `apps/vue-vben-admin/apps/web-naive/src/views/system/dict/data.ts` (`NAIVE_TAG_TYPE_SET` 白名单) 中处理,无需新增代码,只需 SQL / mock seed 写入对应 tag_type。

## Acceptance Criteria

- [ ] `apps/backend-mock-template/utils/mock-data.ts` 中不再存在 `sys_common_status` / 「正常 / 停用」字面量;`sys_switch_status` 字典类型 + 6 条字典项种子已就位
- [ ] mock 启动后,字典管理页面能列出 `sys_switch_status` 类型(平台 admin 端),且列表/搜索过滤按预期返回
- [ ] react-admin `api/hooks/dict.ts` 在 `VITE_APP_PLATFORM=react-admin` 时,`useListDictData({ typeCode: 'sys_switch_status' })` 自动注入 platform=react-admin
- [ ] vue-vben 新增 `dict/hooks.ts`(或同名文件),导出 `useListDictData` 等 hook;自动注入 platform 默认值
- [ ] vue-vben `index.vue` 既能走原有的 `fetchDictDataListApi({ platform, includeGeneral })` 路径,也能走新增 hook
- [ ] 字典管理页面在 `includeGeneral=true` + `platform=react-admin` 时,能看到 4 条 sys_switch_status 项;不勾 includeGeneral 时只 2 条
- [ ] 字典项 platform 默认值与 `VITE_APP_PLATFORM` 对齐
- [ ] git status 干净,所有改动落盘;`backend/db/schema_data.sql` 已 staged 的内容与 mock-data / 实际行为一致

## Out of Scope

- 跨端 dict-type 字段(新增/编辑)表单的字段重构(本期只动 dict-data 维度)
- 后端 java-admin 的 SQL 改造(java-admin 用的是独立 SQL,本期不涉及)
- `useDictCache` 改造(它是 no-op 兼容层,不影响主链路)
- 字典项 i18n / 多语言字段(本期不引入)
- 字典项批量操作接口改造(已支持,本期只验证,不重构)

## Open Questions

- OQ1 (已解决):vue-vben 端没有 vue-query,确认引入 `@tanstack/vue-query` 作为新依赖,在 `apps/vue-vben-admin/apps/web-naive/src/api/system/dict/hooks.ts` 提供 vue-query 风格 hook,与 react-admin `api/hooks/dict.ts` 行为对齐。
- OQ2 (已解决):`apps/react-admin/src/pages/app/system/dict/index.tsx` 已接入 platform + includeGeneral,与 react-admin hook 自动注入语义一致;无需在 dict 页面再加搜索项。
- OQ3 (本期 out of scope):mock `dict-data/list.ts` 不新增 tag_type 过滤。
