# How to use the Go plugin

Compile and run Go programs as flow steps, with full module and dependency support.

## Common properties

`containerImage` defaults to a Go image. Pin a specific version (e.g., `golang:1.22`) for reproducibility. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline Go code defined in the `script` property (wrapped in a `main` package automatically) — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `go run main.go`) against source files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

For module-based projects, include a `go.mod` (and optionally `go.sum`) via namespace files and run `go mod download` in `beforeCommands` before executing your program.
