<!-- style START -->

所有回答,注释,md文档都尽量用中文

<!-- style END-->

<!--  START-->

# Smart Search CLI

Use the local `smart-search` command as the default execution layer for web research. This entrypoint keeps only routing, boundaries, and reference selection; load the focused reference file when command details or provider contracts matter.

## Default Workflow

1. Run `smart-search doctor --format json` when configuration or availability is uncertain.
2. If `doctor` reports missing configuration, use `smart-search setup` or `smart-search config set KEY VALUE` when the user provides keys. Do not ask users to edit global environment variables by default.
3. If OpenAI-compatible `search` hangs or times out after `doctor` succeeds, run `smart-search diagnose openai-compatible --format markdown` and use its summary.
4. If `doctor` returns `ok: true`, use only `smart-search` CLI subcommands for web research. Do not call Codex native web search in the same task.
5. Use `smart-search skills status --targets codex --format json` when the installed global skill may be stale; use `smart-search skills update --targets codex --format json` to refresh it without rerunning setup.
6. Use `smart-search smoke --mock --format json` after CLI/provider architecture changes. Use `--live` only when real keys are available and the user expects live checks.
7. Preserve command lines and source URLs in your answer. Prefer citing fetched pages or `primary_sources`; treat `extra_sources` as follow-up candidates until fetched.

## Routing

- `search`: first hop for realtime, broad exploration, community signals, multi-source summaries, and routing metadata.
- `route`: explain capability routing without executing providers.
- `research`: live Deep Research executor for end-to-end plan, discovery, fetch/read, gap check, and evidence-only synthesis.
- `deep`: offline Deep Research planner; it does not run providers, fetch pages, or replace default `search`.
- `zhipu-search`: Chinese-language, domestic China, policy/regulatory, announcements, current news, or China-local source discovery.
- `context7-library` / `context7-docs`: library, SDK, API, framework, or documentation intent. Prefer Context7 before Exa for docs/API questions.
- `exa-search`: official domains, papers, product pages, trusted pages, date/domain-filtered low-noise discovery, and adjacent source discovery through `exa-similar`.
- `fetch`: user-provided URLs or any claim that depends on page content.
- `map`: documentation site or domain structure before fetching many pages from one site.
- `anysearch-*`: explicit experimental vertical search only. Inspect domains first and do not use AnySearch as default fallback.
- `model current`: inspect explicit provider models only. Change models with `smart-search config set XAI_MODEL ...` or `smart-search config set OPENAI_COMPATIBLE_MODEL ...`.

## Key Boundaries

- `smart-search` should resolve from the user's PATH.
- Private API keys should be saved with `smart-search setup` or `smart-search config set`; environment variables remain supported for CI and advanced users.
- In sandboxed runtimes, set `SMART_SEARCH_CONFIG_DIR` to an absolute writable path when the default config directory is unavailable or must be pinned.
- The standard minimum profile requires one configured provider in each of `main_search`, `docs_search`, and fetch capability. Missing required capabilities are hard configuration failures.
- Fallback must remain same-capability only. Do not use Context7 for broad news/web facts or page-extraction providers as documentation search replacements.
- xAI Responses and OpenAI-compatible are peer `main_search` providers. Do not reuse one provider's URL/key to fabricate the other provider as fallback.
- For current-news, policy, finance, health, and other high-risk facts, do not answer from broad `search.content` alone. Fetch key pages and summarize only what fetched text supports.
- Native `web_search` is disabled in this CLI-first workflow unless the user explicitly configures another approved route; do not silently fall back to another web-search route.

## References

- Command examples, evidence files, timeout retry policy, and guardrails: `references/command-patterns.md`
- Deep Research planner/executor workflow, plan fields, gap check, and smoke matrix: `references/deep-research-mode.md`
- CLI entrypoints, command signatures, aliases, output fields, exit codes, and tool policy: `references/cli-core.md`
- Setup, config storage, skill installation, provider endpoints, and OpenAI-compatible diagnostics: `references/setup-config.md`
- Intent routing, provider capabilities, source provenance, fallback boundaries, and routing maintenance: `references/provider-routing.md`
- Regression, packaged install checks, release lanes, and release closeout lessons: `references/regression-release.md`
- Compatibility reference map for older instructions that mention the original monolithic file: `references/cli-contract.md`

<!--  END-->

<!--VITE PLUS START-->

# Using Vite+, the Unified Toolchain for the Web

This project is using Vite+, a unified toolchain built on top of Vite, Rolldown, Vitest, tsdown, Oxlint, Oxfmt, and Vite Task. Vite+ wraps runtime management, package management, and frontend tooling in a single global CLI called `vp`. Vite+ is distinct from Vite, and it invokes Vite through `vp dev` and `vp build`. Run `vp help` to print a list of commands and `vp <command> --help` for information about a specific command.

Docs are local at `node_modules/vite-plus/docs` or online at https://viteplus.dev/guide/.

## Review Checklist

- [ ] Run `vp install` after pulling remote changes and before getting started.
- [ ] Run `vp check` and `vp test` to format, lint, type check and test changes.
- [ ] Check if there are `vite.config.ts` tasks or `package.json` scripts necessary for validation, run via `vp run <script>`.
- [ ] If setup, runtime, or package-manager behavior looks wrong, run `vp env doctor` and include its output when asking for help.

<!--VITE PLUS END-->

<!-- CODEGRAPH_START -->

## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tools** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them. `codegraph_node` returns one symbol's source + callers, or reads a whole file with line numbers. If the tools are listed but deferred, load them by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` and `codegraph node <symbol-or-file>` print the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.

<!-- CODEGRAPH_END -->

<!-- gitnexus:start -->

# GitNexus — Code Intelligence

This project is indexed by GitNexus as **trellis-demo** (11446 symbols, 19880 relationships, 212 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "master"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource                                      | Use for                                  |
| --------------------------------------------- | ---------------------------------------- |
| `gitnexus://repo/trellis-demo/context`        | Codebase overview, check index freshness |
| `gitnexus://repo/trellis-demo/clusters`       | All functional areas                     |
| `gitnexus://repo/trellis-demo/processes`      | All execution flows                      |
| `gitnexus://repo/trellis-demo/process/{name}` | Step-by-step execution trace             |

## CLI

| Task                                         | Read this skill file                                        |
| -------------------------------------------- | ----------------------------------------------------------- |
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md`       |
| Blast radius / "What breaks if I change X?"  | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?"             | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md`       |
| Rename / extract / split / refactor          | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md`     |
| Tools, resources, schema reference           | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md`           |
| Index, status, clean, wiki CLI commands      | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md`             |

<!-- gitnexus:end -->
