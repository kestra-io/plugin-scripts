# How to use the Shell plugin

Run shell commands and scripts from Kestra flows — the most direct way to invoke system tools, CLIs, and POSIX utilities.

## Common properties

`containerImage` sets the base OS image (`ubuntu:latest`, `alpine:latest`, or any image that includes the tools you need). `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs an inline shell script defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands against script files; use it when your scripts live in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or are cloned from a Git repository with a preceding `Clone` task.

Install OS packages in `beforeCommands` with `apt-get install -y <pkg>` (Debian/Ubuntu images) or `apk add <pkg>` (Alpine images), or use a custom `containerImage` that already includes your tooling.

`CommandsTrigger` and `ScriptTrigger` run shell commands on a schedule and start one execution per run — use them for polling scripts that produce structured output consumed as Kestra trigger outputs.
