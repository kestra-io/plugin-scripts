# How to use the Bun plugin

Run TypeScript and JavaScript scripts with Bun's built-in runtime — no transpile step required.

## Common properties

`containerImage` defaults to `oven/bun`. Pin a specific version (e.g., `oven/bun:1`) for reproducibility. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline TypeScript or JavaScript defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `bun run index.ts`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install packages in `beforeCommands` with `bun add <package>`, or include a `bun.lockb` and `package.json` via namespace files and run `bun install` to restore dependencies before execution.
