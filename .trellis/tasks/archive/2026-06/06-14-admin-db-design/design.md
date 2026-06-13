# 技术设计：后台管理 MySQL Schema

> 本文件是 `.trellis/tasks/06-14-admin-db-design/prd.md` 中所有决策的**技术展开**。本文件**不**包含具体 DDL——DDL 在 `backend/db/schema.sql`，本文件负责解释**为什么这样设计**、**如何衔接**、**权衡与限制**。

---

## 1. 架构与边界

### 1.1 模块边界

```
┌────────────────────────────────────────────────────────────┐
│                  business schema (admin db)                │
│                                                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │  RBAC    │  │  Menu /  │  │  I18n /  │  │ Temporal │    │
│  │  (用户)  │  │  API     │  │  Dict    │  │ 调度     │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  3 × Log tables + 3 × Log archive tables (镜像)    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  casbin_rule （与 casbin/mysql-adapter v2 兼容）     │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
        ↑                                          ↑
        │  go-admin / java-admin 业务查询          │  casbin/mysql-adapter 自动读写
        │                                          │
        │                                          │
   ┌────┴────────────────────┐              ┌──────┴────────┐
   │  admin 后端业务代码      │              │ Casbin 鉴权引擎 │
   │  (gorm / MyBatis-Plus)  │              │               │
   └─────────────────────────┘              └───────────────┘
                                                          ↑
                                                          │ 同步
                                              ┌───────────┴──────────┐
                                              │ Temporal Server      │
                                              │  (temporal schema)   │
                                              │  — 自管，不归本任务  │
                                              └──────────────────────┘
```

**关键边界**：

- admin 业务 schema 与 Temporal server 的 `temporal` schema **隔离**——不同 database 或不同 schema prefix。admin 不写 Temporal 内部表。
- `casbin_rule` 由 `casbin/mysql-adapter` 直接管理；admin 后端只读。
- 三张日志 `_archive` 是 admin 业务 schema 内的同结构副本，TTL 作业**在 admin 进程内**实现。

### 1.2 路径与交付

| 产物       | 路径                                | 备注                                         |
| ---------- | ----------------------------------- | -------------------------------------------- |
| DDL 主文件 | `backend/db/schema.sql`             | 单文件交付，内部 `-- ===== xxx =====` 分块   |
| 设计文档   | `backend/db/docs/db-conventions.md` | 命名、索引、外键、软删、审计约定（独立可读） |
| 字段速查   | `backend/db/docs/tables.md`         | 每张表的核心列、外键、索引列表               |
| ER 关系    | `backend/db/docs/er.md`             | 文字版 ER 图（模块、关联、基数）             |

`backend/db/` 是新目录，独立于 `go-admin` / `java-admin`，可被两个 admin 后端**只读引用**。

---

## 2. 表清单

| #   | 表名                      | 模块     | 类型   | 备注                                           |
| --- | ------------------------- | -------- | ------ | ---------------------------------------------- |
| 1   | `sys_user`                | RBAC     | 核心   | 用户                                           |
| 2   | `sys_role`                | RBAC     | 核心   | 角色                                           |
| 3   | `sys_user_role`           | RBAC     | 关联   | 用户-角色                                      |
| 4   | `sys_api`                 | API 管理 | 核心   | HTTP 接口/权限点                               |
| 5   | `sys_role_api`            | RBAC     | 关联   | 角色-API                                       |
| 6   | `sys_menu`                | 菜单管理 | 核心   | 树形菜单 + 按钮权限码                          |
| 7   | `sys_role_menu`           | RBAC     | 关联   | 角色-菜单                                      |
| 7a  | `sys_menu_api`            | 菜单管理 | 关联   | 菜单-API **结构化绑定**（非授权）              |
| 7b  | `sys_data_permission`     | 数据权限 | 核心   | ABAC 行级授权：主体多态 + 多 action + 多 scope |
| 8   | `i18n_locale`             | I18n     | 核心   | 语言/区域                                      |
| 9   | `i18n_translation`        | I18n     | 核心   | 翻译键-值                                      |
| 10  | `dict_type`               | 字典     | 核心   | 字典类型                                       |
| 11  | `dict_data`               | 字典     | 核心   | 字典数据项                                     |
| 12  | `api_log`                 | 日志     | 记录   | API 调用日志                                   |
| 13  | `api_log_archive`         | 日志     | 归档   | 旧 API 日志                                    |
| 14  | `login_log`               | 日志     | 记录   | 登录日志                                       |
| 15  | `login_log_archive`       | 日志     | 归档   | 旧登录日志                                     |
| 16  | `operation_log`           | 日志     | 记录   | 操作日志                                       |
| 17  | `operation_log_archive`   | 日志     | 归档   | 旧操作日志                                     |
| 18  | `temporal_task_config`    | 任务     | 核心   | Temporal workflow / activity 注册              |
| 19  | `temporal_task_execution` | 任务     | 记录   | Temporal execution 摘要镜像                    |
| 20  | `casbin_rule`             | 鉴权     | casbin | 与 `casbin/mysql-adapter` v2 完全一致          |

**共计 22 张表**。核心表 13 张、关联表 4 张（含 `sys_menu_api`、`sys_data_permission`）、记录表 4 张（日志 3 + execution 1）、归档表 3 张、casbin 1 张。

---

## 3. 命名约定

| 维度      | 约定                                                                                    | 示例                                                   |
| --------- | --------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| 表名      | `snake_case`，业务表以 `sys_` 前缀；casbin 沿用 `casbin_rule`；归档表以 `_archive` 后缀 | `sys_user` / `casbin_rule` / `api_log_archive`         |
| 主键      | 统一 `id BIGINT UNSIGNED AUTO_INCREMENT`                                                |                                                        |
| 业务字段  | `snake_case`，避免缩写                                                                  | `permission_code`（不写 `perm_code`）                  |
| 枚举字段  | `VARCHAR(32)` + `DEFAULT` + 应用层枚举校验                                              | `login_method VARCHAR(32) NOT NULL DEFAULT 'PASSWORD'` |
| 时间字段  | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`                                          | `created_at` / `updated_at`                            |
| 布尔字段  | `TINYINT(1) NOT NULL DEFAULT 0`                                                         | `is_deleted` / `success`                               |
| JSON 字段 | `JSON NULL`（MySQL 8 原生）                                                             | `input_summary` / `result_summary`                     |
| 索引      | `idx_<table>_<col1>_<col2>` 命名；唯一索引 `uniq_<table>_<col>`                         | `idx_sys_user_role_user_id` / `uniq_sys_user_username` |
| 外键      | 字段以 `_id` 结尾；约束名 `fk_<table>_<col>`                                            | `fk_sys_user_role_user_id`                             |
| 软删      | 统一 `is_deleted TINYINT(1) NOT NULL DEFAULT 0`；查询时 WHERE `is_deleted = 0`          |                                                        |
| 审计      | `created_by` / `updated_by` 存用户 id，**不**存用户名（避免改名问题）                   |                                                        |

---

## 4. 公共字段约定

### 4.1 核心表（12 张）

每张核心表**至少**包含以下列：

```sql
id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
is_deleted      TINYINT(1)      NOT NULL DEFAULT 0
created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
created_by      BIGINT UNSIGNED NULL
updated_by      BIGINT UNSIGNED NULL
```

`created_by` / `updated_by` 是**软引用**（不建外键约束），避免删除用户导致级联；查询时 LEFT JOIN `sys_user` 即可。

### 4.2 记录型表（4 张：3 日志 + temporal_task_execution）

只加：

```sql
id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
```

**不**加 `is_deleted`、**不**加 `updated_at`、**不**加 `created_by`/`updated_by`（只增不改）。

### 4.3 归档表（3 张）

结构与对应记录表**完全一致**，外加一个 `archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`（可选）。`schema.sql` 内归档表的列顺序与热表保持一致，便于 `INSERT INTO ...archive SELECT ... FROM ...` 批量归档。

---

## 5. 关键表设计要点

### 5.1 RBAC

- `sys_user.username` 唯一；密码用 `password_hash VARCHAR(128) NOT NULL` + 应用层 salt（不存 salt 列，盐嵌在 hash 中——参考 bcrypt/argon2 输出格式）
- `sys_user.dept_id BIGINT UNSIGNED NULL`：部门 ID，软外键；为 `DEPT` / `DEPT_AND_CHILD` 类数据权限提供查询锚点。本任务不建 `sys_dept` 表
- `sys_role.code` 唯一（如 `admin` / `user`），`name` 展示用
- 关联表 `sys_user_role` / `sys_role_api` / `sys_role_menu` 均为 `(role_id, xxx_id)` 复合主键 + `created_at`（无审计 since 这些是"关系"）

### 5.2 菜单与按钮权限码

- `sys_menu.type` 枚举：`DIR`（目录）/ `MENU`（菜单/路由）/ `BUTTON`（按钮）
- `sys_menu.parent_id` 树形父引用，自引用外键
- `sys_menu.permission_code` 在 `type='BUTTON'` 时必填；路由时可空（路由级权限由 `sys_api` 控制）
- `sys_menu.path` / `sys_menu.component` / `sys_menu.icon` 字段对接 vue-vben-admin 前端
- `sys_menu.sort INT NOT NULL DEFAULT 0` 用于同级排序

#### 5.2.1 菜单-API 快捷绑定（`sys_menu_api`）

**目的**：admin UI 在新增/编辑菜单时，可一键勾选"该菜单会触发的 API"，无需手动逐个 `sys_role_api` 配置。**这是结构化绑定，不是授权委托**。

- 复合主键 `(menu_id, api_id)`：同一菜单对同一 API 只绑定一次
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `created_by BIGINT UNSIGNED NULL`（记录"是谁做的绑定"）
- **不**加 `is_deleted` / `updated_at` / `updated_by`（关联行不可软删；要"解绑"就 `DELETE` 行；想留痕可用 `audit_log` 而非软删）
- `menu_id` / `api_id` 均为外键，约束名 `fk_sys_menu_api_menu_id` / `fk_sys_menu_api_api_id`

**与 `sys_role_api` 的关系**：

- `sys_role_api`：授权，RBAC 主链路
- `sys_menu_api`：UI 提示 / 批量赋权的"模板"
- 例如：勾选"用户管理"菜单 + 勾选"该菜单下的 5 个 API"，admin 后端在用户赋权时可以"按菜单批量赋权"——读取 `sys_menu_api` 取到 5 个 API，**再**写入 `sys_role_api`

#### 5.2.2 数据权限（`sys_data_permission`，ABAC 风格）

**目的**：在 RBAC 授权后，按"主体（用户 / 角色 / 全部用户 / 全部角色）+ 资源（具体表名）+ 动作（多选）+ 作用域（基于某字段的 include/exclude/custom）"四维组合，**行级**控制数据可见性。

```sql
CREATE TABLE sys_data_permission (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    -- 主体多态：USER/ROLE/ANY_USER/ANY_ROLE
    subject_type    VARCHAR(16)     NOT NULL  COMMENT '主体类型(USER/ROLE/ANY_USER/ANY_ROLE)',
    subject_id      BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '主体ID；subject_type 为 ANY_* 时为 0',

    -- 资源：具体业务表名
    resource_table  VARCHAR(32)     NOT NULL  COMMENT '资源表名(如 orders/users)',

    -- 动作：JSON 数组，规范化字符串 action_key 用于唯一约束
    action          JSON            NOT NULL  COMMENT '操作列表(如 ["read","write"])',
    action_key      VARCHAR(64)     NOT NULL DEFAULT 'read'
                                    COMMENT 'action 排序后拼接(如 "read,write"),用于唯一约束',

    -- 作用域：基于某字段的 include/exclude/custom
    scope_type      VARCHAR(32)     NOT NULL DEFAULT 'none'
                                    COMMENT '作用域类型(all/none/include/exclude/custom)',
    scope_field     VARCHAR(64)     NOT NULL DEFAULT 'id'
                                    COMMENT '用于匹配 scope_values 的字段',
    scope_values    JSON            NOT NULL  COMMENT '作用域值列表',

    -- 行过滤条件：自由 JSON map
    conditions      JSON            NOT NULL  COMMENT '行过滤条件(K=V map,应用层解释)',

    -- 冲突优先级
    priority        INT             NOT NULL DEFAULT 0
                                    COMMENT '多主体冲突时的优先级(降序)',

    -- 备注 / 启停
    remark          VARCHAR(512)    NULL      COMMENT '备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1
                                    COMMENT '启用/禁用',

    -- 软删 + 审计
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0
                                    COMMENT '软删(0=未删 1=已删)',
    is_active       TINYINT(1)      GENERATED ALWAYS AS
                                    (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED
                                    COMMENT '软删感知唯一键辅助列(NULL 不参与唯一)',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NULL      COMMENT '创建人(软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NULL      COMMENT '最后修改人(软引用 sys_user.id)',

    PRIMARY KEY (id),

    -- 软删感知唯一：同 (主体,资源,动作) 至多一条"未删"行
    UNIQUE KEY uniq_subject_resource_action_active
        (subject_type, subject_id, resource_table, action_key, is_active),

    -- 高频查询索引
    INDEX idx_subject (subject_type, subject_id),
    INDEX idx_subject_resource (subject_type, subject_id, resource_table),
    INDEX idx_resource (resource_table),
    INDEX idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='数据权限(ABAC):按主体+资源+动作+作用域的行级授权';
```

**关键设计点**：

1. **主体多态**：`subject_type` + `subject_id` 联合表示"作用于谁"；`subject_id = 0` 表示 `ANY_USER` / `ANY_ROLE`（默认策略）。**不**建外键——多态列无法 FK，应用层校验
2. **`action` vs `action_key`**：JSON 数组保留原始顺序便于应用层展示；`action_key` 排序拼接后用于唯一约束，避免"同一权限用不同数组顺序重复插入"
3. **`is_active` 生成列**：`is_deleted = 0` → `is_active = 1`；`is_deleted = 1` → `is_active = NULL`。MySQL 唯一索引中 NULL **不**被视为相等，所以：
   - 同 `(subject_type, subject_id, resource_table, action_key, is_active=1)` 至多一条
   - 软删后重建（先 `UPDATE is_deleted=1` 再 `INSERT`）不会冲突
4. **`scope_*` 表达力**：
   - `all`：不过滤
   - `none`：完全拒绝（黑名单默认）
   - `include`：`scope_field IN scope_values`
   - `exclude`：`scope_field NOT IN scope_values`
   - `custom`：`conditions` 接管，应用层自定义 SQL 片段
5. **`conditions` 自由 JSON**：键值对 map，约定 `column = value` / `column IN (...)` / `column LIKE pattern` 三类；应用层在拼装 WHERE 时校验键白名单（防 SQL 注入）
6. **`priority` 冲突解决**：同 `(subject, resource, action_key)` 多条时，取 `priority` 最高的；`ANY_*` 默认 `priority = 0`；具体用户 / 角色策略应 `priority >= 100`
7. **多角色合并**：用户拥有多个角色时，各角色的 permission 独立查；应用层做 union / intersect 策略（默认 union，与决策 14 早期版本一致）

**与 Casbin 的边界**：

- Casbin 处理"能不能调用这个 endpoint"（菜单 / 按钮 / API 权限）
- `sys_data_permission` 处理"调用后能看到哪些行"（行级过滤）
- 两者**正交**，互不替代

**应用层使用模式**：

1. 业务查询时，先按 Casbin 通过（已登录、有 endpoint 权限）
2. 读取当前用户的所有相关 `sys_data_permission`（按 `subject_type IN (USER, ANY_USER)` + 该用户所属 `ROLE` + `ANY_ROLE`）
3. 多条取 `priority` 最高的作为胜出策略
4. 将 `scope_*` + `conditions` 转成 SQL 片段，注入到业务查询 WHERE 子句

### 5.3 API 管理

- `sys_api.method` 枚举：`GET` / `POST` / `PUT` / `DELETE` / `PATCH` / `OPTIONS` / `HEAD`
- `sys_api.path VARCHAR(255) NOT NULL`（不含 host，支持 `:id` 占位）
- `sys_api.permission_code VARCHAR(128) NOT NULL`，与按钮权限码同构，前端用 `permission_code` 控制按钮可见性、后端用 `permission_code` 做 Casbin 鉴权
- `sys_api.group` 分组字段（如 `用户管理` / `角色管理`），便于管理后台分组展示
- 唯一索引 `(method, path)`：同一 method+path 不能注册两次

### 5.4 I18n

- `i18n_locale.code` 唯一（如 `zh-CN` / `en-US`）
- `i18n_locale.is_default TINYINT(1) NOT NULL DEFAULT 0`——通过应用层保证**最多一条默认**（DB 层不加唯一约束，避免切换默认值时死锁）
- `i18n_translation.locale_id` + `key` 复合唯一：同一语言下翻译键不重复
- `i18n_translation.value TEXT NOT NULL`

### 5.5 字典

- `dict_type.code` 唯一（如 `user_status` / `order_status`）
- `dict_data.type_id` 外键引用 `dict_type.id`
- `dict_data.value` 字典值，`dict_data.label` 展示用，`dict_data.sort` 排序
- 唯一索引 `(type_id, value)`：同一类型下 value 不重复

### 5.6 API 日志

- `request_method`、`request_path`、`request_query TEXT`、`request_body MEDIUMTEXT`（应用层截断 64KB）
- `response_status SMALLINT UNSIGNED`、`response_body MEDIUMTEXT`
- `duration_ms INT UNSIGNED NOT NULL`
- `user_id BIGINT UNSIGNED NULL`（未登录请求为 NULL）
- `client_ip VARCHAR(45)`（IPv6 兼容）、`user_agent VARCHAR(512)`
- `error_message VARCHAR(1024) NULL`
- 索引：`(user_id, created_at)`、`(request_path, created_at)`、`(response_status, created_at)`

### 5.7 登录日志

- 基础：`username VARCHAR(64) NOT NULL`、`client_ip VARCHAR(45)`、`success TINYINT(1) NOT NULL`、`failure_reason VARCHAR(255) NULL`
- 扩展：`user_agent VARCHAR(512)`、`login_method VARCHAR(32) NOT NULL DEFAULT 'PASSWORD'`（PASSWORD / SSO / OAUTH / SMS）
- 设备/系统/浏览器：`device VARCHAR(32)` / `os VARCHAR(64)` / `browser VARCHAR(64)`
- 地理位置：`country VARCHAR(64)` / `province VARCHAR(64)` / `city VARCHAR(64)`（由应用层从 IP 解析；DB 仅存结果）
- 索引：`(username, created_at)`、`(success, created_at)`

### 5.8 操作日志

- `module VARCHAR(64)`（业务模块，如 `user` / `role` / `menu`）
- `action VARCHAR(64)`（动作，如 `create` / `update` / `delete` / `import`）
- `target_id BIGINT UNSIGNED NULL`（被操作对象 id）
- `before_value JSON NULL` / `after_value JSON NULL`（前后数据快照）
- `request_id VARCHAR(64) NULL`（关联 `api_log`，便于跨表追踪）
- `source VARCHAR(16) NOT NULL DEFAULT 'AUTO'`（`AUTO` / `EXPLICIT`）
- 索引：`(user_id, created_at)`、`(module, action, created_at)`

### 5.9 Temporal 任务

**`temporal_task_config`（核心表）**：

- `code VARCHAR(64) UNIQUE`（如 `report_daily` / `cleanup_archive`）
- `workflow_type VARCHAR(128) NOT NULL`（Temporal workflow 类名）
- `task_queue VARCHAR(128) NOT NULL`
- `cron_expr VARCHAR(64) NULL`（NULL 表示仅手动触发）
- `retry_policy JSON NULL`（最大尝试次数、初始间隔、退避系数等）
- `timeout_seconds INT UNSIGNED NULL`
- `is_enabled TINYINT(1) NOT NULL DEFAULT 1`

**`temporal_task_execution`（记录型表）**：

- `workflow_id VARCHAR(128) NOT NULL`（Temporal 原生 ID）
- `run_id VARCHAR(128) NOT NULL`（Temporal 原生 run ID，每次启动唯一）
- `workflow_type VARCHAR(128) NOT NULL`
- `task_queue VARCHAR(128) NOT NULL`
- `status VARCHAR(32) NOT NULL`（`RUNNING` / `COMPLETED` / `FAILED` / `CANCELLED` / `TERMINATED` / `TIMED_OUT`）
- `started_at TIMESTAMP NOT NULL`、`closed_at TIMESTAMP NULL`
- `input_summary JSON NULL` / `result_summary JSON NULL` / `failure_reason VARCHAR(1024) NULL`
- `config_id BIGINT UNSIGNED NULL`（外键引用 `temporal_task_config.id`，应用层写）
- 索引：`(workflow_id, run_id)` 唯一；`(config_id, started_at)`、`(status, started_at)`

### 5.10 Casbin

完全采用 `casbin/mysql-adapter` v2 标准表：

```sql
CREATE TABLE casbin_rule (
    id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ptype VARCHAR(255)    NOT NULL,
    v0    VARCHAR(255)    NULL,
    v1    VARCHAR(255)    NULL,
    v2    VARCHAR(255)    NULL,
    v3    VARCHAR(255)    NULL,
    v4    VARCHAR(255)    NULL,
    v5    VARCHAR(255)    NULL,
    PRIMARY KEY (id),
    INDEX idx_casbin_rule_ptype_v0_v1 (ptype, v0, v1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

admin 业务代码**不**直接 CRUD `casbin_rule`；通过 `casbin.Enforcer.AddPolicy/RemovePolicy` 或 admin UI（间接通过 Enforcer）写入。

---

## 6. ER 关系

```
                       ┌──────────────┐
                       │  sys_user    │
                       │  id, username│
                       └──────┬───────┘
                              │ N
                              │
                              │ M
                       ┌──────┴───────┐ N       1 ┌──────────────┐
                       │ sys_user_role├──────────┤  sys_role    │
                       │              │          │  id, code    │
                       └──────────────┘          └──────┬───────┘
                                                        │
                                       ┌────────────────┼────────────────┐
                                       │ N              │ N              │ N
                                       │                │                │
                                ┌──────┴───────┐ ┌──────┴───────┐ ┌──────┴───────┐
                                │ sys_role_api │ │sys_role_menu │ │ sys_user (隐 │
                                │              │ │              │ │ 式继承)      │
                                └──────┬───────┘ └──────┬───────┘ └──────────────┘
                                       │ N              │ N
                                       │                │
                                 ┌──────┴───────┐ ┌──────┴───────┐
                                 │  sys_api     │ │  sys_menu    │ self-ref
                                 │ method+path  │ │ parent_id    │ ←─┐
                                 │ permission   │ │ permission   │    │
                                 └──────┬───────┘ └──────┬───────┘    │
                                        │                │ M            │
                                        │ N              │              │
                                        │                │              │
                                        │       ┌────────┴─────────┐    │
                                        │       │ sys_menu_api     │    │
                                        │       │ 结构化绑定（无授权）│    │
                                        │       └──────────────────┘    │
                                        ▲                                ▲
                                        └────────────────────────────────┘

                       ┌──────────────┐         ┌────────────────────┐
                       │ i18n_locale  │ 1     N │ i18n_translation   │
                       │ code         ├────────┤ locale_id, key     │
                       └──────────────┘         └────────────────────┘

                       ┌──────────────┐         ┌────────────────────┐
                       │  dict_type   │ 1     N │  dict_data         │
                       │  code        ├────────┤  type_id, value    │
                       └──────────────┘         └────────────────────┘

                       ┌────────────────────┐
                       │ temporal_task_config│ 1     N ┌──────────────────────────┐
                       │ code (unique)      ├─────────┤ temporal_task_execution  │
                       └────────────────────┘         │ workflow_id, run_id      │
                                                      │ config_id, status        │
                                                      └──────────────────────────┘

                       ┌────────────────────┐
                       │  casbin_rule        │  — 与 sys_role 关联是逻辑关联，不建外键
                       │  ptype, v0..v5     │     (Casbin Enforcer 在内存中 join)
                       └────────────────────┘
```

**基数**：

- `sys_user` M:N `sys_role`（经 `sys_user_role`）
- `sys_role` M:N `sys_api`（经 `sys_role_api`）
- `sys_role` M:N `sys_menu`（经 `sys_role_menu`）
- `sys_role` 1:N `sys_data_permission`（当 `subject_type='ROLE'` 时通过 `subject_id` 关联；同一 `subject_id+resource_table+action_key` 至多一条"未删"行）
- `sys_menu` M:N `sys_api`（经 `sys_menu_api`，**结构化绑定**）
- `sys_menu` 自引用 1:N（`parent_id`）
- `i18n_locale` 1:N `i18n_translation`
- `dict_type` 1:N `dict_data`
- `temporal_task_config` 1:N `temporal_task_execution`（`config_id` 软外键）

**不建外键**的场景：

- `created_by` / `updated_by` → `sys_user`：软引用，避免用户删除级联
- `casbin_rule.v0` → `sys_role`/`sys_user`：由 Casbin Enforcer 逻辑保证
- `temporal_task_execution.config_id` → `temporal_task_config.id`：可在 `schema.sql` 内**选择**是否建外键（建议**不**建，因为执行可能先于配置存在）

---

## 7. 索引策略

### 7.1 通用原则

1. **主键索引**自动
2. **唯一索引**只用于业务上确实唯一的字段（`username` / `role.code` / `dict_type.code` / `i18n_locale.code` / `temporal_task_config.code`）
3. **二级索引**遵循"高频查询路径优先"：
   - 日志表：`(user_id, created_at)` 几乎必有
   - 关联表：`(role_id, xxx_id)` / `(xxx_id, role_id)` 任一即可，按查询方向选
4. **不**为 `TEXT` / `MEDIUMTEXT` 列建索引（API 日志的 `request_body` 等）
5. **不**为 `JSON` 列建索引（MySQL 8 支持 functional index，但本任务不引入）
6. 索引数量：单表不超过 6 个（避免写入放大）

### 7.2 各表索引（汇总）

| 表                        | 索引                                                                                                                                                                                                     |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sys_user`                | PK；`uniq_username`                                                                                                                                                                                      |
| `sys_role`                | PK；`uniq_code`                                                                                                                                                                                          |
| `sys_user_role`           | PK（`user_id`, `role_id`）                                                                                                                                                                               |
| `sys_api`                 | PK；`uniq_method_path`；`idx_group`                                                                                                                                                                      |
| `sys_role_api`            | PK（`role_id`, `api_id`）                                                                                                                                                                                |
| `sys_menu`                | PK；`idx_parent_id`；`idx_permission_code`                                                                                                                                                               |
| `sys_role_menu`           | PK（`role_id`, `menu_id`）                                                                                                                                                                               |
| `sys_menu_api`            | PK（`menu_id`, `api_id`）                                                                                                                                                                                |
| `sys_data_permission`     | PK；`uniq_subject_resource_action_active`（`subject_type`, `subject_id`, `resource_table`, `action_key`, `is_active` 软删感知）；`idx_subject`；`idx_subject_resource`；`idx_resource`；`idx_is_deleted` |
| `i18n_locale`             | PK；`uniq_code`                                                                                                                                                                                          |
| `i18n_translation`        | PK；`uniq_locale_id_key`（`locale_id`, `translation_key`）                                                                                                                                               |
| `dict_type`               | PK；`uniq_code`                                                                                                                                                                                          |
| `dict_data`               | PK；`uniq_type_id_value`（`type_id`, `value`）；`idx_type_id_sort`                                                                                                                                       |
| `api_log`                 | PK；`idx_user_id_created_at`；`idx_request_path_created_at`；`idx_response_status_created_at`                                                                                                            |
| `login_log`               | PK；`idx_username_created_at`；`idx_success_created_at`                                                                                                                                                  |
| `operation_log`           | PK；`idx_user_id_created_at`；`idx_module_action_created_at`                                                                                                                                             |
| `temporal_task_config`    | PK；`uniq_code`                                                                                                                                                                                          |
| `temporal_task_execution` | PK；`uniq_workflow_run`（`workflow_id`, `run_id`）；`idx_config_id_started_at`；`idx_status_started_at`                                                                                                  |
| `casbin_rule`             | PK；`idx_casbin_rule_ptype_v0_v1`                                                                                                                                                                        |

归档表与对应热表索引相同。

---

## 8. 软删 / 审计实现细节

- **应用层**负责：所有业务查询 `WHERE is_deleted = 0`；所有写入显式 `created_by` / `updated_by`（由 AOP 拦截器从 session 取当前用户 id 注入）
- **DB 层**不强制 NOT NULL `created_by`，因为系统级操作（如定时任务触发的"清理"）没有用户上下文，留 `NULL`
- **删除**：业务上"删除"是 `UPDATE ... SET is_deleted = 1, updated_at = NOW(), updated_by = ?`；永远不 `DELETE`（除非走归档）
- **硬删**：仅在归档作业的"清空归档表"步骤使用 `TRUNCATE` / `DELETE`，且**至少保留 90 天**（业务建议值，可在 `db-conventions.md` 中明示）

---

## 9. 权衡与已知限制

| 决策                                 | 权衡                                                | 限制 / 风险                                                                             |
| ------------------------------------ | --------------------------------------------------- | --------------------------------------------------------------------------------------- |
| 单表 + 归档（非分区）                | DDL 简单、迁移工具无门槛                            | 热表本身仍会膨胀；归档作业延迟会导致热表变大                                            |
| API 日志存 body（始终）              | 排障方便                                            | 表快速膨胀；需依赖归档作业和监控                                                        |
| 登录日志含 UA 解析                   | 数据丰富                                            | 需要应用层在写入前解析 UA；解析库版本需维护                                             |
| 菜单 + 按钮权限码同表                | 简单                                                | 权限码数量大时单表会大；查询"按钮"需要 `WHERE type = 'BUTTON'`                          |
| 数据权限 ABAC 多态主体               | 表达力强，覆盖 USER/ROLE/ANY_USER/ANY_ROLE 4 类主体 | `subject_id` 无 FK，错误主体值只能应用层发现；`conditions` JSON 需白名单校验防 SQL 注入 |
| 软删感知唯一键（生成列 `is_active`） | 软删后重建不冲突                                    | MySQL 8 专属特性；MySQL 5.7 不支持 `GENERATED ... STORED` + `NULL` 在唯一中的语义       |
| 操作日志自动 + 显式                  | 完整                                                | 自动 AOP 实现复杂；不同语言栈实现成本不同                                               |
| Temporal 仅镜像                      | 不与 Temporal 内部冲突                              | admin UI 显示"完整历史"仍需调 Temporal Server；存在一致性窗口                           |
| Casbin 标准表                        | 复用现成 adapter                                    | `casbin_rule` 列名不友好；排查 policy 较繁琐                                            |
| 不绑迁移工具                         | 灵活                                                | go-admin / java-admin 各选工具，需要约定执行顺序                                        |

---

## 10. 兼容性与 MySQL 8 特性

- **`utf8mb4_0900_ai_ci`**：MySQL 8 默认 collation，使用 unicode 9.0；推荐
- **`JSON` 类型**：原生支持，应用层 `JSON_OBJECT` / `JSON_EXTRACT` 直接读写
- **`TIMESTAMP` 范围**：`1970-01-01 00:00:01 UTC` 到 `2038-01-19 03:14:07 UTC`；如果需要 2038+ 时间，应改 `DATETIME`
- **不依赖** `utf8mb3` 任何特性；不依赖 MySQL 5.7 兼容
- **`sql_mode`**：建议 `STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION`；DDL 不假设具体值
- **不**使用 `GENERATED COLUMN` / `CHECK` 约束（MySQL 8 支持但生态支持度不一致）

---

## 11. 运维考虑

### 11.1 备份与恢复

- 归档表可作为"热恢复"中间层：误删 30 天内数据可从归档表回捞
- `casbin_rule` 与 `sys_role_*` 表存在逻辑冗余：Casbin Enforcer 启动时一般会全量加载 policy 到内存，因此定期 snapshot `casbin_rule` 是有意义的（建议每日）

### 11.2 监控指标（建议）

- `api_log` 行数 / 当日写入速率
- `login_log` 中 `success = 0` 比例
- `operation_log` 当日写入速率
- 归档表行数（异常增长可能意味着归档作业异常）

### 11.3 TTL 作业（由 admin 后端实现，**非本任务交付**）

- 每日 02:00 跑一次
- 将 `created_at < NOW() - INTERVAL 30 DAY` 的热表数据搬运到对应 `_archive`
- 搬运后 DELETE 热表
- 归档表超过 365 天可 TRUNCATE（业务策略）
