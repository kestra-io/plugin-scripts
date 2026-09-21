# How to use the PowerShell plugin

Run PowerShell scripts for Windows automation, Azure management, and cross-platform scripting from Kestra flows.

## Common properties

`containerImage` defaults to `mcr.microsoft.com/powershell`. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline PowerShell code defined in the `script` property — best for short, flow-specific logic. `Commands` runs PowerShell commands against script files; use it when your scripts live in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or are cloned from a Git repository with a preceding `Clone` task.

Install PowerShell modules in `beforeCommands` with `Install-Module -Name <Module> -Force -AllowClobber`. For Azure automation, `Az` module is the standard; for Microsoft Graph, use `Microsoft.Graph`. Use a custom `containerImage` pre-built with your modules to avoid long install times on each execution.

## Triggers

### ScriptTrigger

Polls on an interval by running an inline PowerShell script the same way the `Script` task does, and starts an execution when `exitCondition` matches. The script runs in a fresh container on every poll, so keep it quick.

Required properties:
- `script`: inline PowerShell script body
- `exitCondition`: either `exit N`, which matches when the script exits with code N, or a regex (with substring fallback) matched against the vars the script emits with `::{"outputs":{...}}::`

Optional:
- `interval`: time between polls, defaults to `PT60S`
- `edge`: defaults to `true`, so the trigger fires only when the condition changes from not matching to matching. The previous result is kept in the namespace KV store under a key starting with `trigger-edge-`. Set to `false` to fire on every matching poll
- `containerImage`: defaults to `ghcr.io/kestra-io/powershell:latest`

The trigger outputs are available as `{{ trigger.timestamp }}`, `{{ trigger.condition }}`, `{{ trigger.exitCode }}` and `{{ trigger.vars }}`. A failed run has no vars, so only an `exit N` condition can match a failure.

### CommandsTrigger

Same behavior as `ScriptTrigger`, but runs a list of shell commands the way the `Commands` task does. Required properties are `commands` and `exitCondition`; `interval`, `edge` and `containerImage` work as above.
