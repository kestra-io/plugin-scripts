# How to use the PHP plugin

Run PHP scripts inside a container as flow steps.

## Common properties

`containerImage` defaults to the `php` image on `Script` and `Commands` tasks. `taskRunner` defaults to Docker on those tasks and can be overridden for other execution environments. The polling triggers below do not expose `taskRunner`, `env`, `beforeCommands`, `inputFiles`, or `namespaceFiles`; only `containerImage`, the script or commands, `interval`, `edge`, and `exitCondition` are configurable on the trigger.

## Tasks

`Script` runs inline PHP code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `php main.php`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Manage dependencies with Composer — include a `composer.json` via namespace files and run `composer install` in `beforeCommands`. For projects without Composer dependencies, a plain PHP image is sufficient.

## Triggers

### ScriptTrigger

Polls on an interval by running an inline PHP script the same way the `Script` task does, and starts an execution when `exitCondition` matches. The script runs in a fresh container on every poll, so keep it quick.

Required properties:
- `script`: inline PHP script body
- `exitCondition`: either `exit N`, which matches when the script exits with code N, or a regex (with substring fallback) matched against the vars the script emits with `::{"outputs":{...}}::`

Optional:
- `interval`: time between polls, defaults to `PT60S`
- `edge`: defaults to `true`, so the trigger fires only when the condition changes from not matching to matching. The previous result is kept in the namespace KV store under a key starting with `trigger-edge-`. Set to `false` to fire on every matching poll
- `containerImage`: defaults to `php`

The trigger outputs are available as `{{ trigger.timestamp }}`, `{{ trigger.condition }}`, `{{ trigger.exitCode }}` and `{{ trigger.vars }}`. A failed run has no vars, so only an `exit N` condition can match a failure.

### CommandsTrigger

Same behavior as `ScriptTrigger`, but runs a list of shell commands the way the `Commands` task does. Required properties are `commands` and `exitCondition`; `interval`, `edge` and `containerImage` work as above.
