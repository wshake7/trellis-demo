# 设计后台管理系统 MySQL 数据库表

> **你（AI）正在执行这个任务，开发者不会直接阅读这个文件。**
> 本文件只记录**需求、约束与验收标准**。技术设计落 `design.md`，执行计划落 `implement.md`。

---

## 1. 目标与用户价值

为后台管理系统输出一套**语言无关**的 MySQL 8 DDL 集合，使得该系统能在 Go、Java（以及任何后续栈）后端中复用，作为未来 `go-admin` / `java-admin` 两个 admin 后端共享的契约层。

DDL 落盘后，能为以下 9 个功能模块以及完整 RBAC、任务调度（基于 Temporal）提供持久化支撑：

1. 菜单管理
2. API 管理（接口/权限点管理）
3. I18n 语言管理
4. 字典管理
5. API 日志
6. 登录日志
7. 操作日志
8. 任务调度配置
9. 任务执行记录

---

## 2. 已确认事实（来自会话前段）

| 维度          | 决策                                                                                                                                                                                                                                                                                     |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 技术栈        | 语言无关 SQL DDL；需同时被 `go-admin` / `java-admin` 复用                                                                                                                                                                                                                                |
| 多租户        | **单租户**，所有表**不**加 `tenant_id`                                                                                                                                                                                                                                                   |
| 鉴权模型      | 完整 RBAC（user / role / 关联表）+ Casbin policy（MySQL 适配器）                                                                                                                                                                                                                         |
| 任务调度      | 使用 **Temporal**；admin 系统只持有**应用层**的配置与执行记录，**不**为 Temporal Server 设计其内部表（Temporal 通过 `temporal-sql-tool` 自管 `temporal` schema）                                                                                                                         |
| 仓库现状      | `backend/go-admin`、`backend/java-admin` 均为空目录；`.trellis/spec/backend/database-guidelines.md` 仍是未填写模板——本次输出将**定义**项目自己的 DB 规范                                                                                                                                 |
| 字符集 / 引擎 | MySQL 8，InnoDB，`utf8mb4` / `utf8mb4_0900_ai_ci`                                                                                                                                                                                                                                        |
| ID 策略       | **所有表主键统一 `BIGINT AUTO_INCREMENT`**                                                                                                                                                                                                                                               |
| 软删 / 审计   | **核心表**（user/role/menu/api/i18n/dict/temporal_config）加 `is_deleted TINYINT` + 4 审计字段（`created_at/updated_at/created_by/updated_by`）；**记录型表**（3 张日志 + `temporal_task_execution`）**不**加软删、**只加** `created_at`（只增不改）                                     |
| I18n 范围     | **只管 UI 翻译键**（`i18n_translation` 只承载 UI 字符串，如 `menu.user.create`）。菜单名 / 字典名等业务字段仍存各自业务表，不走 i18n_translation                                                                                                                                         |
| 日志策略      | **单表 + 归档表**（折中方案）。`api_log`/`login_log`/`operation_log` 三张**热表**为普通表，加 `(user_id, created_at)` 索引；各配一张 `_archive` 同结构归档表（`api_log_archive` / `login_log_archive` / `operation_log_archive`）。后台 TTL 作业定期搬运老数据到归档表，热表保留近期窗口 |
| API 日志体量  | **始终存** `request_body` + `response_body`（TEXT，应用层截断 64KB）                                                                                                                                                                                                                     |
| 登录日志      | **基础 + UA + 地理位置**（丰富版）。`username`/`ip`/`success`/`created_at` + `user_agent` + `login_method`（PASSWORD/SSO/OAUTH 枚举）+ 解析后 `device`/`os`/`browser` + `country`/`province`/`city`（由 IP 解析，应用层负责）                                                            |
| 菜单权限粒度  | **菜单表 + 按钮级权限码**。`sys_menu` 加 `permission_code VARCHAR(128) NULL`；为路由时可空，为按钮时必填。**不**另立 `sys_button` 表                                                                                                                                                     |
| 操作日志写入  | **两者同时保留**。AOP 拦截所有 UPDATE/DELETE 自动写 `operation_log`；同时支持 `@AuditLog` 显式打标。`operation_log` 加 `source` 列区分 `AUTO` / `EXPLICIT`                                                                                                                               |
| Temporal 表   | **仅镜像摘要**。`temporal_task_execution` 只存 `workflow_id`/`run_id`/`workflow_type`/`task_queue`/`status`/`started_at`/`closed_at`/`input_summary`(JSON)/`result_summary`(JSON)/`failure_reason`；activity/signal/timer 等全量状态由 Temporal Server 自管，**不**在 admin 中复刻       |
| Casbin 适配器 | **标准 `casbin_rule`**（ptype/v0..v5）。可直接复用 `casbin/mysql-adapter` v2，不自写 adapter                                                                                                                                                                                             |
| DDL 文件拆分  | **单文件 `schema.sql`**（内部用 `-- ===== xxx =====` 分块）。一个文件即全部表                                                                                                                                                                                                            |
| 迁移工具      | **不绑**。只交付独立 .sql 文件，由后续 go-admin / java-admin 自行选择迁移工具（`golang-migrate` / Flyway / liquibase / 自研）                                                                                                                                                            |
| 现有任务      | `00-bootstrap-guidelines` 仍 in_progress；本任务**不**触碰规范补全，独立推进                                                                                                                                                                                                             |

---

## 3. 模块清单（已确认要包含的表）

### 3.1 RBAC（用户确认"包含完整 RBAC + Casbin 表"）

- 用户表
- 角色表
- 用户-角色关联表
- 角色-API 关联表
- 角色-菜单关联表
- 菜单-API 快捷绑定表（**结构化绑定**，非授权——见 §4 补充决策 13）
- 数据权限表（**ABAC 行级授权**——见 §4 补充决策 14）
- Casbin policy 表（标准 `casbin_rule` 形态）

### 3.2 业务模块 1–9

- 菜单表（含树形结构与按钮级权限码）
- API/接口表（HTTP 路由 + method + 权限码）
- I18n 语言/区域表
- I18n 翻译表
- 字典类型表
- 字典数据表
- API 日志表
- 登录日志表
- 操作日志表
- 任务调度配置表（Temporal workflow/activity 注册）
- 任务执行记录表（应用层对 Temporal execution 的轻量镜像）

---

## 4. 收口决策记录（已全部完成）

| #   | 决策项                      | 收口结果                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| --- | --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | ID 策略                     | `BIGINT AUTO_INCREMENT`，所有表统一                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 2   | 软删 / 审计                 | 核心表加全量（`is_deleted` + 4 审计字段）；记录表只加 `created_at`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 3   | I18n 范围                   | 只管 UI 翻译键；业务字段名不进 `i18n_translation`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 4   | 日志存储                    | **单表 + 归档表**（`api_log` / `login_log` / `operation_log` + `_archive` 同结构表）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| 5   | API 日志体量                | 始终存 `request_body` + `response_body`（TEXT，应用层截断 64KB）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| 6   | 登录日志字段                | 基础 4 字段 + UA + 解析后 device/os/browser + 地理位置                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 7   | 菜单权限粒度                | 菜单表加 `permission_code` 字段，不另立按钮表                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 8   | 操作日志写入                | AOP 自动 + 显式打标共存；`source` 字段区分 `AUTO`/`EXPLICIT`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 9   | Temporal 表                 | 仅镜像摘要；activity/signal/timer 不在 admin 中复刻                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 10  | Casbin 适配器               | 标准 `casbin_rule`（ptype/v0..v5），直接复用 `casbin/mysql-adapter` v2                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 11  | DDL 文件拆分                | 单文件 `schema.sql`（内部 `-- =====` 分块）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| 12  | 迁移工具                    | 不绑；只交付独立 .sql 文件                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| 13  | **补充：菜单-API 快捷绑定** | 新增 `sys_menu_api`（菜单与 API 的 M:N 结构化绑定表）。用于 admin UI 在新增/编辑菜单时勾选"该菜单会触发的 API"，便于快速展示 / 批量赋权。**非授权委托**——授权仍走 `sys_role_api` / `sys_role_menu`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 14  | **数据权限（ABAC 风格）**   | 替换 §13 之前预想的 `sys_role_data_scope`，改为 `sys_data_permission`（ABAC 模型）。字段：`subject_type` (USER/ROLE/ANY*USER/ANY_ROLE) + `subject_id` (ANY*\_ 时为 0) + `resource_table` (业务表名) + `action` (JSON 数组，如 `["read","write"]`) + `action_key` (规范化字符串，唯一约束用) + `scope_type` (all/none/include/exclude/custom) + `scope_field` (默认 `id`) + `scope_values` (JSON 列表) + `conditions` (JSON 过滤条件 map) + `priority` (冲突优先级 INT)。软删用 `is_deleted TINYINT(1)` 配合**生成列 `is_active`** 实现"软删感知唯一约束"。**不**走 Casbin；由应用层在生成 SQL WHERE 时读取。多主体合并：相同 `(subject_type, subject_id, resource_table, action_key)` 多条时取 `priority` 最高的；`ANY\__`默认`priority = 0`（最低）。`sys_user`加`dept_id BIGINT UNSIGNED NULL`作为 DEPT 类 scope 的查询锚点（不建`sys_dept` 表） |

---

## 5. 验收标准（草案，将随澄清收敛）

- [ ] DDL 在 MySQL 8 上无错误执行，包含所有目标表与索引、外键
- [ ] 所有命名遵循统一约定（snake*case、`sys*` 前缀等）
- [ ] 每张业务表拥有明确的主键、审计字段、软删除策略（如适用）
- [ ] 跨表外键引用一致；菜单树形结构、用户-角色、角色-权限三类关系完整
- [ ] `sys_menu_api` 表满足 admin UI 快捷绑定需求；与 `sys_role_api` 授权逻辑解耦
- [ ] `sys_data_permission` 覆盖"主体多态 + 多 action + 多 scope 表达"四类组合；`action_key` + `is_active` 生成列让"软删后重建"不冲突
- [ ] 三个日志表具备高效的按时间范围 / 按用户查询路径
- [ ] Casbin `casbin_rule` 表结构与 `casbin/mysql-adapter` v2 完全兼容
- [ ] Temporal 表不与 Temporal Server 自管表冲突；应用层不直接读写 Temporal 内部表
- [ ] 单文件 `schema.sql` 交付，内部用 `-- =====` 分块；文件头部说明用途与执行顺序
- [ ] 提供 `docs/db-conventions.md`：本次采用的命名约定、索引约定、外键约定、软删 / 审计约定（不进 `.trellis/spec/`，独立可读）
- [ ] 提供 ER 关系说明 + 每张表的字段速查表

---

## 6. 非范围

- 不为 Temporal Server 设计其原生持久化表（`executions` / `task_queues` / `history_*` 等）——由 Temporal Server 自管
- 不为前端 `vue-vben-admin` 的 mock 接口设计表——`apps/vue-vben-admin/apps/backend-mock` 是 mock，不入本次范围
- 不实现具体的 SQL 索引调优、读写分离、分库分表策略——只给基线 DDL 与建议
- 不实现种子数据 / 初始管理员账号脚本（`data/seed.sql`）——可以另起任务
- 不绑定具体迁移工具——本任务只交付独立 .sql 文件
- **不**修改 `.trellis/spec/backend/` 下任何文件——`database-guidelines.md` 的填充分属 `00-bootstrap-guidelines` 任务的职责

---

## 7. 工作流

- 本任务为复杂任务，会同步产出 `design.md`（技术设计）与 `implement.md`（执行计划）
- 在所有澄清项收口、用户 review 通过 `prd.md` / `design.md` / `implement.md` 后，再执行 `task.py start`
