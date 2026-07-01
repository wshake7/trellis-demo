.PHONY: install i init check-vp check-codegraph

install i:
	vp i
	cd apps/vue-vben-admin && vp i

init: check-vp check-codegraph
	@echo ""
	@echo "✔ Toolchain ready: vp, codegraph (context7 via smart-search — no standalone install)"
	@echo "→ Initializing codegraph..."
	@codegraph init || echo "⚠ codegraph init failed (may already be initialized)"

check-vp:
	@if command -v vp >/dev/null 2>&1; then \
		echo "✔ vp already installed ($$(vp --version 2>/dev/null | head -n1))"; \
	else \
		echo "→ Installing vp (Vite+)..."; \
		case "$$(uname -s)" in \
			Darwin|Linux) curl -fsSL https://vite.plus | bash ;; \
			MINGW*|MSYS*|CYGWIN*) powershell -NoProfile -Command "irm https://vite.plus/ps1 | iex" ;; \
			*) echo "✗ Unsupported OS: $$(uname -s). See https://viteplus.dev/guide/"; exit 1 ;; \
		esac; \
	fi

check-codegraph:
	@if command -v codegraph >/dev/null 2>&1; then \
		echo "✔ codegraph already installed ($$(codegraph --version 2>/dev/null | head -n1))"; \
	else \
		echo "→ Installing codegraph..."; \
		case "$$(uname -s)" in \
			Darwin|Linux) curl -fsSL https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.sh | sh ;; \
			MINGW*|MSYS*|CYGWIN*) powershell -NoProfile -Command "irm https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.ps1 | iex" ;; \
			*) echo "✗ Unsupported OS: $$(uname -s). See https://github.com/colbymchenry/codegraph"; exit 1 ;; \
		esac; \
		echo "ℹ Open a new terminal, then run 'codegraph install' (once) and 'codegraph init' (per project)."; \
	fi
