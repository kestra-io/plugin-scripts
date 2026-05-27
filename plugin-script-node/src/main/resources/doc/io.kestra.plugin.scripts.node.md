# How to use the Node.js plugin

Run Node.js scripts and trigger flows from script output as flow steps.

## Common properties

`containerImage` defaults to a Node image. Pin a specific version (e.g., `node:20`) for reproducibility. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline JavaScript defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `node index.js`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install npm packages in `beforeCommands` with `npm install` (from a `package.json` in namespace files) or `npm install <package>` for ad hoc installs. Use `npm ci` with a committed `package-lock.json` for reproducible installs.

`CommandsTrigger` and `ScriptTrigger` run Node.js commands on a schedule and start one execution per run — use them for polling scripts that produce structured output consumed as Kestra trigger outputs.
