# modules/utils-template

仓库里唯一的共享 TS 包,通过 `vp pack`(tsdown)打包成 ESM,被其他应用消费。

## 一句话定义

最小可发布的 TypeScript 库模板,`exports["."]` 指向 `./dist/index.mjs`,跑 `vp pack` 即可出包[^src-021]。

## 依赖与脚本

```json
{
  "name": "utils-template",
  "exports": {
    ".": "./dist/index.mjs",
    "./package.json": "./package.json"
  },
  "publishConfig": { "access": "public" },
  "scripts": {
    "build": "vp pack",
    "dev": "vp pack --watch",
    "test": "vp test",
    "check": "vp check",
    "prepublishOnly": "vp run build"
  },
  "devDependencies": {
    "@typescript/native-preview": "7.0.0-dev.20260509.2",
    "typescript": "^6.0.3",
    "vite-plus": "catalog:"
  }
}
```

- `vp pack` 是 Vite+ 的库打包封装(底层 tsdown),出 `dist/index.mjs`
- `dev` 加 `--watch` 用于边改边构建
- `prepublishOnly` 在发布前强制跑一次 build

> 警告:`@typescript/native-preview: 7.0.0-dev.20260509.2` 是 TS 原生编译预览,**非稳定版本**。在生产发布前应该替换成 `typescript`,或者至少钉到一个具体 snapshot。生产风险见 [open questions](#open-questions)。

## 目录约定(基于 npm 模板约定)

```
packages/utils-template/
├── src/            # 源码,index.ts 暴露
├── dist/           # vp pack 产物,被 gitignore
├── package.json
└── tsconfig.json   # 由根 vite-plus 提供默认
```

> TODO: 待补:确认 src 实际内容(可能只有一个 hello world)、是否有 unit test。

## 何时修改它

- 需要给根 workspace 多个应用共享一段逻辑(比如日期格式化、错误处理 wrapper)
- 想验证 `vp pack` 的产物格式
- **不是**应用的占位符 —— 名字里 `-template` 是模板性质,真要起新 utils 包应该 `cp -r` 一份

## Open questions

- `@typescript/native-preview` 是否需要替换为 stable TS?发布到 npm 前必须决断。

## 引用

[^src-021]: `packages/utils-template/package.json`

## 相关

- [modules/website-template](website-template.md) —— 同样走 catalog 的根 workspace 应用模板
- [stack.md](../stack.md) —— tsdown / vp pack 的来源