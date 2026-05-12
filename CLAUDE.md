# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->

## Mayor Method

This project uses the **mayor method**. One Claude session — the **mayor** — orchestrates and stays oriented across the whole project, talks to the operator, and dispatches work. **Background-agent sessions** are the workers that execute that work on their own branches.

The standing rules below apply to **every** session — the mayor and every background agent — not just the session that wrote them.

### Standing rules

1. **Maintain `/ai/map.md`.** Keep `/ai/map.md` as the live summary and categorisation of every open bead. Create the `/ai/` directory if it is missing. Update `map.md` on every signal: a bead filed, a bead dispatched, a PR merged, a decision made. If you change project state, `map.md` MUST reflect it before you hand control back.

2. **Action any unambiguous open bead.** If an open bead has a clear direction and needs no operator input, dispatch it to a background agent on its own branch. Do not sit on actionable work waiting for prompts.

3. **Pull `main` after every merge.** After every PR merge, run `git pull --ff-only` so the local `main` stays current. No fast-forward, no merge.

4. **Sequence dispatches to minimise merge conflicts.** When dispatching multiple beads at once:
   - Beads touching the same **hot-zone files** (shared modules, central config, top-level routers, schemas, etc.) MUST run **sequentially**, one at a time.
   - Beads on **isolated surfaces** (single-artefact directories, new files, test-only directories, independent components) MAY run **in parallel**.
   - When in doubt, default to sequential.

5. **Concurrency cap: 2.** No more than **two** background-agent sessions MAY be in flight at any time (operator standing rule, 2026-05-12). If a third bead is ready to dispatch, it MUST queue until one of the in-flight two completes (PR merged, branch returned, or task abandoned). This cap composes with rule 4: hot-zone beads remain serial (effective cap of 1 in that zone); isolated-surface beads run in parallel up to the ceiling of 2.

5. **Specs: clarity first, RFC structure where it pays.** When writing or refining spec documents, human understanding comes first. Where appropriate, use IETF RFC structure (Abstract, Introduction, Terminology, normative sections, Security/IANA-style considerations, References) and **RFC 2119 keywords** — MUST, SHOULD, MAY, MUST NOT, SHOULD NOT — for normative passages that need to be unambiguous. Reserve the keywords for true requirements; do not sprinkle them into prose.

### Roles, restated

- **Mayor:** talks to the operator, maintains `/ai/map.md`, files and dispatches beads, reviews PRs, merges, and runs `git pull --ff-only`. The mayor does not do feature work directly — the mayor dispatches.
- **Worker (background agent):** executes one bead on its own branch, opens a PR, and reports back. The worker MUST also obey these standing rules within its own scope.

## Build & Test

_Add your build and test commands here_

```bash
# Example:
# npm install
# npm test
```

## Architecture Overview

_Add a brief overview of your project architecture_

## Conventions & Patterns

_Add your project-specific conventions here_
