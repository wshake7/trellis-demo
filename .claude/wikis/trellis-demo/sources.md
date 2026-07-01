# Sources — Trellis Demo Codebase

Every claim in `wiki/` cites a row from this table by `[^src-id]`.

Hashes are SHA256 of file contents at fetch time; re-hash before citing if the file has changed.

| id | type | url / path | title | hash | fetched_at |
|----|------|------------|-------|------|------------|
| src-001 | repo:file | `README.md` | Root README — Vite+ Monorepo Starter | `535bc811313579b95c80f977f838c4eb3573a61173037a00bb0189124038c795` | 2026-07-01 |
| src-002 | repo:file | `package.json` | Root package.json (workspace scripts) | `f9f8a30e403fc19250d6e8028aec89ab32b89739fa18b39210c3543da9be4b4c` | 2026-07-01 |
| src-003 | repo:file | `pnpm-workspace.yaml` | pnpm workspace definition + catalog | `9dc0a42b134157911df6479dbebb968c750cc8e14ee1026737a372f01c8ac13f` | 2026-07-01 |
| src-004 | repo:file | `vite.config.ts` | Vite+ root config (staged/fmt/lint/run tasks) | `8db021f6b849a9dcb793366592fab73c72b750769f86db1825ce5dd3ce9bb46c` | 2026-07-01 |
| src-005 | repo:file | `tsconfig.json` | Root tsconfig (noEmit, nodenext) | `e83ad93940e4b8402236d9dd194d0180173d503801ad899c506e41f0de154f89` | 2026-07-01 |
| src-006 | repo:file | `lefthook.yml` | Root git-hooks (single source of truth) | `d0cc6e49baf52ae212833d87e60c130555418cbc1788e0f2a3d01b07d523ee05` | 2026-07-01 |
| src-007 | repo:file | `Makefile` | Toolchain bootstrap (vp / codegraph; context7 via smart-search subcommands) | `de907404415ab327093fcb03877c6e563dc2cffd21b698f18ba6ca1a000290de` | 2026-07-01 |
| src-008 | repo:file | `AGENTS.md` | Project conventions (Chinese output, smart-search, Vite+, CodeGraph, GitNexus) | `828890ad28a797c222a41f0da67e4f7d18ef5e87c55e30942205f10a2c8b907a` | 2026-07-01 |
| src-009 | repo:file | `CLAUDE.md` | Project entry rule: read AGENTS.md first | `b0341d65f3edb11c65bf09aa93e10f60c450f0bd36db0c768b445b3f720ff9cc` | 2026-07-01 |
| src-010 | repo:file | `apps/vue-vben-admin/package.json` | vue-vben-admin monorepo root | `5efdac1609e0ac608cf7e1bd7789b2043761990c4cd070885a95d4e9a03a0b6c` | 2026-07-01 |
| src-011 | repo:file | `apps/vue-vben-admin/pnpm-workspace.yaml` | vue-vben-admin internal workspace | `d9b17bc231ba77400eb18611b8cd9182ab3f3b188ccc01811dfad0b62529ba78` | 2026-07-01 |
| src-012 | repo:file | `apps/vue-vben-admin/lefthook.yml` | vue-vben-admin git-hooks (sub-tree) | `984134418627d31c0d552becd33354cfa16d36061620fc19d72c7e511f2d4486` | 2026-07-01 |
| src-013 | repo:file | `apps/vue-vben-admin/eslint.config.mjs` | vue-vben-admin ESLint flat config | `ae1aeb90f30ae26f467b5f6486201efcc4090ebd8df2e32f524e1685592e4e6c` | 2026-07-01 |
| src-014 | repo:file | `apps/react-admin/package.json` | react-admin (ant-design-pro based) | `aa402c8976a760221b016630963eb963f60beac377c6596b0550b89e1d7d3775` | 2026-07-01 |
| src-015 | repo:file | `apps/react-admin/pnpm-workspace.yaml` | react-admin internal workspace | `39cba5f02faa833a8c2d4a0fdcc909ec0e69e6757d228919f3a26e1d4d6362d4` | 2026-07-01 |
| src-016 | repo:file | `apps/react-admin/eslint.config.js` | react-admin ESLint flat config | `ea99f64f9f586e78791e9b414f56ec75e08ed8841fcf8ed2e3d7fec55e67e343` | 2026-07-01 |
| src-017 | repo:file | `apps/react-admin/vite.config.ts` | react-admin Vite config | `599c183ff997449c23f27abf2359448255d9a3170a1e397a3eb69417a6b2a7c2` | 2026-07-01 |
| src-018 | repo:file | `apps/website-template/package.json` | website-template (Vue + Vite+) | `c37939322773deb81b04ce3a1cdbc34d6abdd02da7667ebb7af1f9b3e8c52beb` | 2026-07-01 |
| src-019 | repo:file | `apps/backend-mock-template/package.json` | backend-mock-template (Nitro) | `54608c2dc811f7322e7a175804e5747ba05b4f080176b4ca8373ac71b467093d` | 2026-07-01 |
| src-020 | repo:file | `apps/backend-mock-template/nitro.config.ts` | Nitro config (dev port, CORS) | `3399a2fcdbdc4930c92774dcc0dad68a74ff5e886aba5942b0f9259315defb19` | 2026-07-01 |
| src-021 | repo:file | `packages/utils-template/package.json` | utils-template (shared TS package) | `dfe1abe6e5a73dc815e8ceb8c59838a031708aa8f4059afe6aff5c040536b018` | 2026-07-01 |
| src-022 | repo:file | `backend/java-admin/pom.xml` | Java admin backend (Maven, Error Prone) | `179973a2af44d3fc6a84bc20554c1325fb74fbde45649f74d721c6a57277cc86` | 2026-07-01 |

<!-- Append new rows under the header. id format: src-NNN. -->