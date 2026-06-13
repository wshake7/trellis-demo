# 执行计划：后台管理 MySQL Schema

> 本文件是 `.trellis/tasks/06-14-admin-db-design/prd.md` 与 `design.md` 的**执行展开**。在所有 review 闸门通过、`task.py start` 之后，按本计划交付。

---

## 1. 开发策略决策（Review-gate contract: explicit-selection-v1）

| 维度             | 决策                                | 说明                                                              |
| ---------------- | ----------------------------------- | ----------------------------------------------------------------- |
| 开发模式         | **直接交付**                        | 纯 DDL + 文档，无业务逻辑；非 TDD 友好                            |
| 工作区           | **feature branch**（不开 worktree） | 任务边界清晰，DDL 改动 < 1000 行，worktree 反而割裂 review 上下文 |
| 实施流程         | **默认流**                          | 非 TDD                                                            |
| 实施前架构指导   | **不启用**                          | DDL 任务无大型结构调整需要，prd/design 已完成充分设计             |
| Review-gate 契约 | **explicit-selection-v1**           | 见下节                                                            |

### 1.1 Review 闸门（explicit-selection-v1）

```
Review-gate contract: explicit-selection-v1
Optional review gates status: configured
Enabled optional review gates:
  - trellis-spec-review
  - trellis-code-review
  - trellis-code-architecture-review
  - trellis-merge-review
Disabled optional review gates:
  - trellis-improve-codebase-architecture
  （理由：DDL 任务不涉及大型架构调整，prd/design 已充分覆盖）
```

**保留的固定闸门**：`trellis-check`（按 Trellis 默认始终启用，独立于上述可选集）

### 1.2 Review 闸门执行顺序

按 trellis-check 的标准顺序：spec → code → architecture → merge。

1. `trellis-spec-review`：验证 schema 覆盖 prd.md 的所有验收标准与模块清单
2. `trellis-code-review`：验证 schema.sql 语法正确、命名一致、索引合理、外键逻辑无环
3. `trellis-code-architecture-review`：验证 design.md 的关键设计决策是否被实现（归档表、软删、审计、镜像 vs 复刻）
4. `trellis-merge-review`：验证交付物完整、文档齐全

---

## 2. 有序实施清单

按依赖顺序，每步对应一个独立可验证的产物。

### Step 1：建立目录骨架

- 路径：`backend/db/`
- 内容：空目录（待 Step 2/3 落文件）
- 验证：`ls backend/db` 不报错

### Step 2：交付 `backend/db/schema.sql`

**DDL 创建顺序**（考虑外键依赖）：

```
1. casbin_rule                            （无依赖）
2. i18n_locale                            （无依赖）
3. dict_type                              （无依赖）
4. sys_user                               （无依赖）
5. sys_role                               （无依赖）
6. sys_api                                （无依赖）
7. sys_menu                               （自引用 parent_id，先建）
8. i18n_translation                       (FK → i18n_locale)
9. dict_data                              (FK → dict_type)
10. sys_user_role                         (FK → sys_user, sys_role)
11. sys_role_api                          (FK → sys_role, sys_api)
12. sys_role_menu                         (FK → sys_role, sys_menu)
12a. sys_menu_api                          (FK → sys_menu, sys_api)  -- 结构化绑定，无授权
12b. sys_data_permission                   （无 FK；多态主体）   -- ABAC 行级数据权限
13. api_log / api_log_archive             （无外键，纯记录表）
14. login_log / login_log_archive
15. operation_log / operation_log_archive
16. temporal_task_config                  （无外键）
17. temporal_task_execution               （config_id 不建外键）
```

文件内部分块：

```sql
-- ============================================================
-- Section 0: Header / 元信息
-- ============================================================
-- 数据库: <name>，由各 admin 后端自行配置
-- 字符集: utf8mb4 / utf8mb4_0900_ai_ci
-- 引擎:   InnoDB
-- 引擎要求: MySQL 8+
-- 排序:   按依赖顺序；无依赖表先建
-- 部署:   本文件可独立执行；如使用迁移工具，请按本文件顺序
--         切分为多个版本脚本
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;  -- 显式关闭外键检查，便于归档表与热表并行
SET SQL_MODE = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';

-- ============================================================
-- Section 1: sys_* 核心表
-- ============================================================
...
```

**关键工程细节**：

- 文件头注释说明字符集、引擎、版本、执行顺序假设
- `CREATE TABLE` 之间留空行，便于 `git diff` 读
- 每张表加 `COMMENT '...'` 注释（中英文皆可）
- 每张表尾加 `INDEX ...` 显式列出（即使主键已建）—— 增强可读性
- 表注释、列注释均使用英文（更易被工具消费），关键业务表加中文 inline 注释

### Step 3：交付 `backend/db/docs/db-conventions.md`

独立可读的"项目 DB 规范"文档：

- 命名约定（与 `design.md` 第 3 节一致，但更"项目化"）
- 索引策略（高频查询路径示例）
- 软删 / 审计使用规范（含 SQL 示例）
- 枚举字段使用规范（应用层 enum 与 VARCHAR(32) 的映射）
- 字符集与 `sql_mode` 建议
- **不**修改 `.trellis/spec/backend/database-guidelines.md`（属于 `00-bootstrap-guidelines` 任务职责）

### Step 4：交付 `backend/db/docs/tables.md`

每张表的"字段速查"——一张表一节：

```markdown
### sys_user

| 字段          | 类型            | 必填 | 默认           | 说明               |
| ------------- | --------------- | ---- | -------------- | ------------------ |
| id            | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键               |
| username      | VARCHAR(64)     | 是   | -              | 登录名，唯一       |
| password_hash | VARCHAR(128)    | 是   | -              | bcrypt/argon2 哈希 |
| nickname      | VARCHAR(64)     | 否   | NULL           | 展示名             |
| email         | VARCHAR(128)    | 否   | NULL           | 邮箱               |
| phone         | VARCHAR(32)     | 否   | NULL           | 手机号             |
| status        | TINYINT(1)      | 是   | 1              | 1=启用 0=禁用      |
| is_deleted    | TINYINT(1)      | 是   | 0              | 软删               |
| created_at    | TIMESTAMP       | 是   | NOW()          |                    |
| updated_at    | TIMESTAMP       | 是   | NOW()          |                    |
| created_by    | BIGINT UNSIGNED | 否   | NULL           | 软引用 sys_user.id |
| updated_by    | BIGINT UNSIGNED | 否   | NULL           | 软引用 sys_user.id |

外键：无
索引：PK(id); UNIQUE(username)
```

### Step 5：交付 `backend/db/docs/er.md`

文字版 ER 关系图 + 基数说明（与 `design.md` 第 6 节基本一致，但**更面向开发者**）：

```markdown
## ER 关系

### 用户 ↔ 角色（M:N）

- 通过 `sys_user_role` 关联
- 复合主键 (user_id, role_id)
- 删除用户：应用层先删关联

### 角色 ↔ 菜单（M:N）

- 通过 `sys_role_menu` 关联
  ...

### 任务配置 ↔ 执行记录（1:N）

- `temporal_task_execution.config_id` 软外键
- 应用层维护一致性
```

---

## 3. 验证命令

每步完成后跑对应验证：

| 步骤                   | 验证命令                                                | 期望                                               |
| ---------------------- | ------------------------------------------------------- | -------------------------------------------------- |
| Step 2（schema.sql）   | `mysql -h <host> -u <user> -p < db/schema.sql`          | 全部 `CREATE TABLE` 成功；20 张表 + 3 张归档表存在 |
| Step 2（语法静态检查） | 用 `sqlfluff` / `mysql --verbose --execute=...` 跑 lint | 无语法警告                                         |
| Step 2（外键一致性）   | `SHOW CREATE TABLE sys_user_role\G` 等抽查外键          | 引用目标表已存在                                   |
| Step 2（索引统计）     | `SHOW INDEX FROM sys_api;` 等抽查                       | 索引数量与 design.md § 7.2 一致                    |
| Step 3（文档）         | 文本 review                                             | 命名 / 软删 / 审计 / 枚举约定齐全                  |
| Step 4（tables.md）    | 文本 review                                             | 20 + 3 张表全部覆盖                                |
| Step 5（er.md）        | 文本 review                                             | 关系图覆盖所有 1:N / M:N                           |

**集成验证**（推荐，optional）：

```bash
docker run --rm -d --name mysql-test -e MYSQL_ROOT_PASSWORD=root mysql:8
sleep 10
docker exec -i mysql-test mysql -uroot -proot < backend/db/schema.sql
docker exec mysql-test mysql -uroot -proot -e "SHOW TABLES;"
docker stop mysql-test
```

---

## 4. 高风险点与回滚

| 风险                                       | 影响                               | 缓解                                                                                                                                       |
| ------------------------------------------ | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| DDL 语法错误导致无法应用                   | 上线阻塞                           | Step 2 完成后**必须**用真实 MySQL 8 跑一次；syntax 100% 通过才进 Step 3                                                                    |
| 外键约束冲突导致 CREATE 失败               | 上线阻塞                           | 创建顺序严格按 Step 2 列表；归档表与热表**不**互相建外键                                                                                   |
| 字符集 / 排序规则与目标环境不一致          | 索引性能 / 排序结果差异            | 文件头显式 `SET NAMES utf8mb4`；列定义显式 `CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`                                              |
| 字段类型 / 长度低估                        | 后续变更代价高                     | `username VARCHAR(64)` / `email VARCHAR(128)` / `password_hash VARCHAR(128)` / `request_body MEDIUMTEXT` 留足余量                          |
| 软删 + 唯一索引冲突                        | 用户名软删后无法再创建同名         | `sys_user.username` 唯一索引改为 `UNIQUE (username, is_deleted)`（MySQL 8 支持 functional unique index 的等价形式）——**已纳入 schema.sql** |
| `sys_menu_api` 误用为授权                  | 把"快捷绑定"当授权                 | 表注释 + design.md §5.2.1 显式声明"非授权委托"；应用层 service 显式区分 `bindMenuApi` / `grantRoleApi` 两套方法                            |
| `sys_data_permission` 与 Casbin 角色串扰   | 误用 Casbin 鉴权做行级过滤         | design.md §5.2.2 显式声明与 Casbin 正交；应用层 service 显式区分 `checkCasbinPermission` / `applyDataScope` 两阶段                         |
| `sys_data_permission` 多主体合并           | 多角色用户的权限应该是并集还是交集 | 默认**取 `priority` 最高的胜出**；`ANY_*` 默认 `priority = 0`；具体策略 `priority >= 100`；在 `db-conventions.md` 中明示                   |
| `sys_data_permission.conditions` JSON 注入 | 自由 JSON 拼 SQL 易被注入          | 必须在 `db-conventions.md` 明示：白名单键、强制参数化、禁用字符串拼接                                                                      |
| `is_active` 生成列在 MySQL 5.7 不兼容      | 旧 MySQL 不支持生成列 + NULL 唯一  | 文档明示：**仅支持 MySQL 8+**；如需 5.7 兼容，需改用复合唯一键 `(subject_type, subject_id, resource_table, action_key, is_deleted)`        |
| 后续上线 ALTER 困难                        | 字段加列需谨慎                     | 本任务是基线 DDL；后续变更由 go-admin / java-admin 各自用迁移工具管理（不在本任务范围）                                                    |

### 回滚策略

- **本次交付**：`schema.sql` 是基线，不上线到生产；交付后 go-admin / java-admin 各自基于此建迁移
- **如果未来要回滚**（例如：某个 admin 项目已基于本 schema 部署）：需在归档任务中提供 `schema.drop.sql`（**不**在本任务交付，**后续任务**）
- **DDL 内的不可逆操作**：本文件**无** `DROP` / `TRUNCATE` / `ALTER ... DROP COLUMN`，是纯创建

---

## 5. `task.py start` 前必查

- [x] `prd.md` 已收口，12 个决策项全部确认
- [x] `design.md` 已完成（命名、ER、索引、权衡、运维）
- [x] `implement.md` 已完成（本文件，策略 + 清单 + 验证 + 回滚）
- [x] `implement.jsonl` 列出本任务子代理需要的 spec/research 文件
- [x] `check.jsonl` 列出 `trellis-check` 子代理需要的 spec/research 文件
- [x] Review-gate 策略已 stamp `explicit-selection-v1`
- [x] `Optional review gates status: configured` 已写入
- [x] 已征得用户对策略的确认

---

## 6. 交付物清单

执行完成后应交付：

| 路径                                | 类型 | 行数估计    |
| ----------------------------------- | ---- | ----------- |
| `backend/db/schema.sql`             | DDL  | ~600-800 行 |
| `backend/db/docs/db-conventions.md` | 文档 | ~150-200 行 |
| `backend/db/docs/tables.md`         | 文档 | ~250-350 行 |
| `backend/db/docs/er.md`             | 文档 | ~80-120 行  |

---

## 7. 不在本次执行范围

- 不创建 go-admin / java-admin 的 ORM model 代码
- 不创建迁移工具（golang-migrate / Flyway）的目录与配置
- 不创建 casbin 的 policy 文件（`model.conf` / `policy.csv`）
- 不写 seed 数据
- 不创建 docker-compose 启动 MySQL
- 不修改 `.trellis/spec/backend/database-guidelines.md`（属 bootstrap 任务）
- 不创建 `schema.drop.sql`
