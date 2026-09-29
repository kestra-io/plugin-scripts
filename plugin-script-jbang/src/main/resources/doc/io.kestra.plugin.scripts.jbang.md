# How to use the JBang plugin

Run Java source files directly without a build system — JBang compiles and executes them on the fly.

## Common properties

`containerImage` defaults to a JBang image. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline Java code defined in the `script` property — best for short, flow-specific logic. `Commands` runs JBang commands (e.g., `jbang main.java`) against source files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Declare Maven dependencies directly in the source file using JBang's `//DEPS` directive at the top: `//DEPS com.google.guava:guava:32.0.0-jre`. JBang resolves and caches them automatically at runtime — no `pom.xml` or `build.gradle` required.

## Triggers

### ScriptTrigger

Polls on an interval by running an inline JBang script the same way the `Script` task does, and starts an execution when `exitCondition` matches. The script runs in a fresh container on every poll, so keep it quick.

Required properties:
- `script`: inline JBang script body
- `exitCondition`: either `exit N`, which matches when the script exits with code N, or a regex (with substring fallback) matched against the vars the script emits with `::{"outputs":{...}}::`

Optional:
- `interval`: time between polls, defaults to `PT60S`
- `edge`: defaults to `true`, so the trigger fires only when the condition changes from not matching to matching. The previous result is kept in the namespace KV store under a key starting with `trigger-edge-`. Set to `false` to fire on every matching poll
- `containerImage`: defaults to `jbangdev/jbang-action`

The trigger outputs are available as `{{ trigger.timestamp }}`, `{{ trigger.condition }}`, `{{ trigger.exitCode }}` and `{{ trigger.vars }}`. A failed run has no vars, so only an `exit N` condition can match a failure.

### CommandsTrigger

Same behavior as `ScriptTrigger`, but runs a list of commands the way the `Commands` task does. Required properties are `commands` and `exitCondition`; `interval`, `edge` and `containerImage` work as above.
