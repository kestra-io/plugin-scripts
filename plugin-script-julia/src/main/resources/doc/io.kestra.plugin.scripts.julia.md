# How to use the Julia plugin

Run Julia scripts for numerical computing and data science inside a container as flow steps.

## Common properties

`containerImage` defaults to `julia:latest`. Pin a specific version (e.g., `julia:1.10`) for reproducibility. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline Julia code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `julia main.jl`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

For package dependencies, install them in `beforeCommands` with `julia -e 'using Pkg; Pkg.add("DataFrames")'`. For larger environments, include a `Project.toml` and `Manifest.toml` via namespace files and run `julia --project -e 'using Pkg; Pkg.instantiate()'` in `beforeCommands` to restore the exact package state.

## Triggers

`ScriptTrigger` polls by running an inline Julia script on a schedule (default every 60s) and emits a flow execution when `exitCondition` matches (for example `exit 42` to watch for a specific exit code). `CommandsTrigger` works the same way but runs shell commands instead of an inline script.

Both triggers default to **edge mode** (`edge: true`), which fires only on a transition from not matching to matching, so a condition that stays true across multiple polls does not fire on every interval. The previous result is stored in the namespace KV store keyed by flow and trigger id.

Use `exit(42)` in your Julia script to signal a condition you want to watch. The `exitCondition` also accepts a regex (or plain substring) matched against vars emitted via the `::{"outputs":{...}}::` convention on successful runs.
