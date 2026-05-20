# How to use the Python plugin

Run Python code inline or from files, with full control over dependencies, the container image, and the execution environment.

## Common properties

`containerImage` sets the Python image used for execution — defaults to a Kestra-managed Python image. Use a custom image (e.g., `python:3.12-slim`) or one pre-built with your dependencies to avoid installing them at runtime.

`taskRunner` controls where the script runs. The default is Docker (isolated container on the local worker). Set it to `Process` to run directly on the worker without a container, or to a [Kubernetes task runner](https://kestra.io/docs/workflow-components/task-runners) to run in a remote cluster.

## Tasks

`Script` runs inline Python code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `python main.py`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install dependencies with `beforeCommands` (e.g., `["pip install pandas requests"]`) or bake them into a custom `containerImage`. For larger dependency sets, store a `requirements.txt` as a namespace file and run `pip install -r requirements.txt` in `beforeCommands`. The same `Commands`/`Script` pattern applies to all other language submodules in this plugin (Shell, Node, R, Go, and others).
