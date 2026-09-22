# How to use the Bun plugin

Run TypeScript and JavaScript scripts with Bun's built-in runtime — no transpile step required.

## Common properties

`containerImage` defaults to `oven/bun`. Pin a specific version (e.g., `oven/bun:1`) for reproducibility. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline TypeScript or JavaScript defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `bun run index.ts`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install packages in `beforeCommands` with `bun add <package>`, or include a `bun.lockb` and `package.json` via namespace files and run `bun install` to restore dependencies before execution.

## Triggers

### ScriptTrigger

Polls on an interval by running an inline Bun script the same way the `Script` task does, and starts an execution when `exitCondition` matches. The script runs in a fresh container on every poll, so keep it quick.

Required properties:
- `script`: inline Bun script body
- `exitCondition`: either `exit N`, which matches when the script exits with code N, or a regex (with substring fallback) matched against the vars the script emits with `::{"outputs":{...}}::`

Optional:
- `interval`: time between polls, defaults to `PT60S`
- `edge`: defaults to `true`, so the trigger fires only when the condition changes from not matching to matching. The previous result is kept in the namespace KV store under a key starting with `trigger-edge-`. Set to `false` to fire on every matching poll
- `containerImage`: defaults to `oven/bun`

The trigger outputs are available as `{{ trigger.timestamp }}`, `{{ trigger.condition }}`, `{{ trigger.exitCode }}` and `{{ trigger.vars }}`. A failed run has no vars, so only an `exit N` condition can match a failure.

### CommandsTrigger

Same behavior as `ScriptTrigger`, but runs a list of shell commands the way the `Commands` task does. Required properties are `commands` and `exitCondition`; `interval`, `edge` and `containerImage` work as above.
