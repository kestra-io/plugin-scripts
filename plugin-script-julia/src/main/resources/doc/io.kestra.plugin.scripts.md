# How to use the Julia plugin

Run Julia scripts for numerical computing and data science inside a container as flow steps.

## Common properties

`containerImage` defaults to `julia:latest`. Pin a specific version (e.g., `julia:1.10`) for reproducibility. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline Julia code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `julia main.jl`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

For package dependencies, install them in `beforeCommands` with `julia -e 'using Pkg; Pkg.add("DataFrames")'`. For larger environments, include a `Project.toml` and `Manifest.toml` via namespace files and run `julia --project -e 'using Pkg; Pkg.instantiate()'` in `beforeCommands` to restore the exact package state.
