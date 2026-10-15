# How to use the zx plugin

Run JavaScript shell automation with zx, combining JavaScript logic with shell commands through the `$` helper.

## Common properties

`containerImage` defaults to `ghcr.io/google/zx:8.5.0`, which includes Node.js, zx, and Bash. `taskRunner` defaults to Docker.

The default image is AMD64. ARM64 workers need emulation or an ARM64-compatible custom image.

For a custom image or Process runner with zx installed on PATH, set `executable: zx` on `Script`. Process execution requires Node.js, zx, and a supported shell on the worker.

## Tasks

`Script` runs inline JavaScript from the `script` property and supports top-level `await`. `Commands` runs existing files staged through `inputFiles` or [namespace files](https://kestra.io/docs/concepts/namespace-files); in the default image, use `node /zx/build/cli.js main.mjs`.

Use `beforeCommands` to install additional packages, `env` for environment variables, and `outputFiles` to persist generated files. See the individual task pages for complete flow examples.
