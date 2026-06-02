# How to use the Lua plugin

Run Lua scripts inside a container as flow steps.

## Common properties

`containerImage` defaults to a Lua image. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline Lua code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `lua main.lua`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install LuaRocks packages in `beforeCommands` with `luarocks install <package>`, or use a custom `containerImage` pre-built with your dependencies.
