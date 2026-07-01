# modules/website-template

Vue 3 + Vite+ 的最小可运行网站模板,作为根 workspace 的 "hello world" 应用。

## 一句话定义

跑 `vp dev` 立刻能起来的最简单 Vue 应用,用于验证根 workspace 的 Vite+ 配置链路是否健康[^src-018]。

## 依赖与脚本

`package.json` 极简[^src-018]:

```json
{
  "name": "website-template",
  "type": "module",
  "scripts": {
    "dev": "vp dev",
    "build": "tsc && vp build",
    "preview": "vp preview"
  },
  "devDependencies": {
    "typescript": "~6.0.2",
    "vite": "catalog:",
    "vite-plus": "catalog:"
  }
}
```

- `vite` / `vite-plus` 走 catalog(根 `pnpm-workspace.yaml` 的 `catalog` 段统一固定)[^src-003]
- `build` 走 `tsc && vp build` —— 先类型检查,再打包
- 没有单元测试入口(模板性质,故意省略)

## 目录约定

未读源码,基于根 vite.config 推断:

- 入口 `index.html` 在 `apps/website-template/` 根(常规 Vite 约定)
- 源码在 `src/`(同样未确认)
- 模板的 `lint-staged` 路径会落入根 vite.config 的 staged 任务,被处理(`vp check --fix`)[^src-004]

> TODO: 待补:确认 `src/` 入口、路由/组件布局、是否有 i18n / 主题。

## 何时修改它

- 想给根 workspace 加新规则(format / lint / build)时,先在这里验证
- 想演示 "Vite+ 在多应用 monorepo 下的标准用法" 时,作为参考实现
- **不是**生产网站用途 —— 名字里就有 `-template`

## 引用

[^src-003]: `pnpm-workspace.yaml`
[^src-004]: `vite.config.ts`
[^src-018]: `apps/website-template/package.json`

## 相关

- [modules/utils-template](utils-template.md) —— 另一个根 workspace 模板(TS 包而非应用)
- [structure.md](../structure.md) —— 顶层目录约定
- [stack.md](../stack.md) —— 工具链清单