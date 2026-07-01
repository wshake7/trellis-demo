# modules/backend-mock-template

Nitro mock 后端,跑在固定 dev 端口 **4000**,配合前端 dev 服务器代理使用。

## 一句话定义

为前端开发提供"有数据、能写、能错"的 mock 服务,Nitro 进程通过 `package.json#start` 起来,固定 `nitro dev --port 4000`[^src-019]。

## 依赖与脚本

```json
{
  "name": "backend-mock-template",
  "scripts": {
    "build": "nitro build",
    "start": "nitro dev --port 4000"
  },
  "dependencies": {
    "@faker-js/faker": "catalog:",
    "jsonwebtoken": "catalog:",
    "nitropack": "catalog:"
  },
  "devDependencies": {
    "@types/jsonwebtoken": "catalog:",
    "h3": "1.15.11"
  }
}
```

- `nitropack` / `@faker-js/faker` / `jsonwebtoken` / `@types/jsonwebtoken` 全部走根 catalog[^src-003] [^src-019]
- `h3` 显式钉到 `1.15.11`,没走 catalog(Nitro 的内部 h3 版本兼容要求)
- 根 package.json 通过 `vp run backend-mock-template#start` 暴露为 `pnpm dev:mock`[^src-002]

## Nitro 配置要点

`nitro.config.ts`[^src-020]:

- `devErrorHandler` / `errorHandler: "~/error"` —— 错误统一走本地 `./error` 处理器
- `devProxy: {}` —— 显式空对象,占位
- `routeRules["/api/**"]` 给所有 API 路由统一打 CORS 头:
  - `cors: true`
  - 显式列出 `Allow-Headers`(包含 `Authorization`、`X-CSRF-TOKEN` 等)
  - `Access-Control-Expose-Headers: "*"` —— 前端可读所有响应头
- **关键注释**:`Access-Control-Allow-Origin` 由 `middleware/1.api.ts` 动态回显请求的 `Origin`,不能同时给 `"*"` + `Allow-Credentials`,否则浏览器会拒绝带 cookie 的请求

> SPECULATION:看到 `middleware/1.api.ts` 命名(数字前缀),推断遵循 Nitro 的"中间件执行顺序按文件名字母序"约定,`1.api.ts` 比 `2.*` 先跑。

## 目录骨架(基于 ls)

```
apps/backend-mock-template/
├── api/              # API 处理器(估计)
├── routes/           # Nitro 文件路由(估计)
├── middleware/       # 中间件,含 1.api.ts
├── utils/            # 工具函数(估计)
├── error.ts          # 顶层错误处理器(被 nitro.config.ts 引用)
├── nitro.config.ts
└── package.json
```

> TODO: 待补:把 `api/` `routes/` `middleware/` `utils/` 的具体子页面列出来。

## 与前端怎么接

- 前端 dev 服务器通过代理把 `/api/**` 转给 `http://localhost:4000/**`
- CORS 在 mock 侧处理,前端无需配 `withCredentials` 之外的额外请求头
- `jsonwebtoken` 暗示有需要 token 校验的 mock 接口;`@faker-js/faker` 提供随机测试数据

## 何时修改它

- 给前端 dev 加新 mock 接口(放 `api/` 或 `routes/`)
- 调整 CORS 头或全局错误处理
- **生产替换**:这玩意定位就是 mock,生产后端应该走 `backend/java-admin` 或别的真后端

## 引用

[^src-002]: `package.json`(根,`dev:mock` 脚本)
[^src-003]: `pnpm-workspace.yaml`(catalog)
[^src-019]: `apps/backend-mock-template/package.json`
[^src-020]: `apps/backend-mock-template/nitro.config.ts`

## 相关

- [structure.md](../structure.md)
- [decisions/workspace-exclusions.md](../decisions/workspace-exclusions.md) —— 为什么 Nitro mock 在根 workspace,而 vue-vben-admin 不在
- [modules/website-template](website-template.md) —— 同一根 workspace 下的另一个模板