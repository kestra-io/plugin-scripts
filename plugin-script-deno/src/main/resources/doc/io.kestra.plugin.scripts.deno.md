# How to use the Deno plugin

Run TypeScript and JavaScript with Deno's secure-by-default runtime — imports are URL-based, so no install step is needed for most dependencies.

## Common properties

`containerImage` defaults to `denoland/deno`. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline TypeScript or JavaScript defined in the `script` property — best for short, flow-specific logic. `Commands` runs Deno commands (e.g., `deno run --allow-net main.ts`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Deno resolves imports from URLs at runtime — no install step needed for URL-based imports. For local module graphs, include a `deno.json` import map via namespace files. Pass Deno permission flags (`--allow-net`, `--allow-read`, `--allow-env`, etc.) directly in `commands` to scope what the script can access.
